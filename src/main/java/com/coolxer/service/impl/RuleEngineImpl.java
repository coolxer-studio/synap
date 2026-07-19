package com.coolxer.service.impl;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.config.TopicDefine;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.punish.Punish;
import com.coolxer.zenvis.businessservice.BusinessServiceEventSeverity;
import com.coolxer.zenvis.businessservice.BusinessServiceReporter;
import com.coolxer.service.PunishConfigService;
import com.coolxer.service.RuleEngine;
import com.coolxer.utils.JacksonUtils;
import com.coolxer.utils.StringConvertUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**

 */
@Slf4j
@Service
public class RuleEngineImpl implements RuleEngine {

    private final PunishConfigService punishConfigService;
    private final BusinessServiceReporter businessServiceReporter;

    private static Map<String,String> FACT_TOPIC_MAP = new ConcurrentHashMap<>();
    static {
        String fieldPrefix = "TOPIC_";
        try {
            Field[] topicFields = TopicDefine.class.getDeclaredFields();
            for (Field topicField:topicFields) {
                topicField.setAccessible(true);
                String topicFieldName = topicField.getName();
                if(topicFieldName.startsWith(fieldPrefix)){
                    String fact = StringConvertUtils.underscoreToCamel(StringUtils.substringAfter(topicFieldName,fieldPrefix),false);
                    String topic = (String)topicField.get(null);
                    FACT_TOPIC_MAP.put(fact,topic);
                }
            }
        } catch (IllegalAccessException e) {
            log.error("获取TopicDefine字段失败", e);
        }
    }

    @Value("${rule.engine.groovy.path}")
    private String groovyScriptPath;

    @Autowired
    private KafkaTemplate kafkaTemplate;

    public RuleEngineImpl(PunishConfigService punishConfigService,
                          BusinessServiceReporter businessServiceReporter) {
        this.punishConfigService = punishConfigService;
        this.businessServiceReporter = businessServiceReporter;
    }

    /**
     * GroovyScriptEngine 缓存池，key 为脚本路径
     */
    private final ConcurrentHashMap<String, GroovyScriptEngine> scriptEngineCache = new ConcurrentHashMap<>();

    @Override
    public Checkpoint run(AbstractFact fact) {
        Checkpoint checkpoint = null;
        String factClassName = fact.getClass().getSimpleName();
        String scriptName = factClassName+"Rule.groovy";
        String topic = FACT_TOPIC_MAP.get(factClassName);
        try {
            // 从缓存中获取或创建 GroovyScriptEngine
            GroovyScriptEngine groovyScriptEngine = scriptEngineCache.computeIfAbsent(groovyScriptPath, key -> {
                try {
                    return new GroovyScriptEngine(key);
                } catch (IOException e) {
                    log.error("创建 GroovyScriptEngine 失败, path: {}", key, e);
                    throw new RuntimeException("创建 GroovyScriptEngine 失败", e);
                }
            });
            Binding binding = new Binding();
            binding.setVariable("fact",fact);
            ArrayList<Agenda> agendaList = (ArrayList<Agenda>)groovyScriptEngine.run(scriptName,binding);

            HashSet<Punish> punishHashSet = hitPunishRule(agendaList);

            checkpoint = new Checkpoint(fact,scriptName,agendaList,punishHashSet,FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss").format(new Date()));

            kafkaTemplate.send(topic,fact.getCommon().getGuid(), JacksonUtils.toJson(checkpoint));
        } catch (IOException e) {
            log.error("执行Groovy脚本时发生IO异常, factClassName:{}, scriptName:{}", factClassName, scriptName, e);
            reportRuleFailure(fact, factClassName, scriptName, e);
        } catch (ScriptException e) {
            log.error("执行Groovy脚本异常, factClassName:{}, scriptName:{}", factClassName, scriptName, e);
            reportRuleFailure(fact, factClassName, scriptName, e);
        } catch (ResourceException e) {
            log.error("获取Groovy脚本资源异常, factClassName:{}, scriptName:{}", factClassName, scriptName, e);
            reportRuleFailure(fact, factClassName, scriptName, e);
        } catch (RuntimeException e) {
            log.error("规则执行发生运行时异常, factClassName:{}, scriptName:{}", factClassName, scriptName, e);
            reportRuleFailure(fact, factClassName, scriptName, e);
            throw e;
        }
        return checkpoint;
    }

    private void reportRuleFailure(AbstractFact fact,
                                   String factClassName,
                                   String scriptName,
                                   Exception exception) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fact_type", factClassName);
        data.put("script_name", scriptName);
        if (fact.getCommon() != null) {
            data.put("platform", fact.getCommon().getPlatform());
        }
        String traceId = fact.getCommon() == null ? null : fact.getCommon().getGuid();
        String message = exception.getClass().getSimpleName()
                + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
        try {
            businessServiceReporter.reportEvent(
                    "RULE_EXECUTION_FAILED",
                    BusinessServiceEventSeverity.ERROR,
                    "Synap 规则执行失败",
                    message,
                    traceId,
                    data);
        } catch (RuntimeException reporterException) {
            log.warn("提交 Zenvis 规则失败事件时发生异常: {}", reporterException.getMessage());
        }
    }

    private HashSet<Punish> hitPunishRule(ArrayList<Agenda> agendas){
        HashSet<Punish> punishHashSet = new HashSet<>();
        // 从服务中获取最新的惩罚规则配置
        List<Punish> punishRule = punishConfigService.getAllPunishRules();
        
        if (punishRule == null || punishRule.isEmpty()) {
            return punishHashSet;
        }
        
        for (Punish punish : punishRule) {
            for (Agenda agenda : agendas) {
                if (punish.getTag().equals(agenda.getTag())) {
                    Pattern pattern = Pattern.compile(punish.getSourceRegex());
                    if (pattern.matcher(agenda.getSource()).matches()) {
                        punishHashSet.add(punish);
                    }
                }
            }
        }

        return punishHashSet;
    }
}

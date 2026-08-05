package com.coolxer.model.fact;

import com.coolxer.config.CheckerConfig;
import com.coolxer.utils.JacksonUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**

 */
@Slf4j
@Getter
public abstract class AbstractFact {

    protected String type;
    protected String buildDate;
    protected Header header;
    protected Common common;
    protected String messageId;
    protected String batchId;
    protected Long observedAt;
    protected Long sequence;
    protected String requestIp;
    protected String requestUserAgent;
    protected String requestOrigin;
    protected String fetchSite;
    protected String fetchMode;
    protected String fetchDest;
    protected String transportTrust = "app-key-codec/untrusted";
    protected String sequenceObservation;
    protected Long serverReceivedAt;
    protected Long clientServerSkewMs;
    protected String requestAsn;
    protected String requestNetworkCategory;
    protected String requestTlsFingerprint;
    protected String requestIpReputation;

    protected AbstractFact(String type, String buildDate, Header header, Common common) {
        this.type = type;
        this.buildDate = buildDate;
        this.header = header;
        this.common = common;
    }

    public void setTransportContext(String messageId, String batchId, Long observedAt, Long sequence) {
        this.messageId = messageId;
        this.batchId = batchId;
        this.observedAt = observedAt;
        this.sequence = sequence;
    }

    public void setRequestContext(String requestIp, String requestUserAgent) {
        this.requestIp = requestIp;
        this.requestUserAgent = requestUserAgent;
    }

    public void setSecurityContext(String requestOrigin, String fetchSite, String fetchMode, String fetchDest,
                                   String transportTrust, String sequenceObservation) {
        this.requestOrigin = requestOrigin;
        this.fetchSite = fetchSite;
        this.fetchMode = fetchMode;
        this.fetchDest = fetchDest;
        this.transportTrust = transportTrust;
        this.sequenceObservation = sequenceObservation;
        this.serverReceivedAt = System.currentTimeMillis();
        this.clientServerSkewMs = observedAt == null ? null : serverReceivedAt - observedAt;
    }

    public void setGatewayContext(String asn, String networkCategory, String tlsFingerprint, String ipReputation) {
        this.requestAsn = asn; this.requestNetworkCategory = networkCategory;
        this.requestTlsFingerprint = tlsFingerprint; this.requestIpReputation = ipReputation;
    }

    protected void build(Map<String,Object> factMap){
        // 获取当前实现类名称
        String className = this.getClass().getSimpleName();
        int index = 1;
        char[] arr = className.toCharArray();
        for (; index < arr.length; index++) {
            // 除第一字符外，碰到大写字母停止
            if (Character.isUpperCase(arr[index])) {
                break;
            }
        };
        String platform = className.substring(0, index);
        Map<String,Object> currentConfigMap  = CheckerConfig.getInstance().getConfigWithFactType(platform,type);
        for (String configKey:currentConfigMap.keySet()) {
            try {
                Field field = this.getClass().getDeclaredField(configKey);
                ValueSet annotation = field.getAnnotation(ValueSet.class);
                if(annotation == null){
                    // 只有加了注解才赋值
                    continue;
                }
                field.setAccessible(true);
                // 集合类型需要根据注解定制
                if(field.getType().equals(ArrayList.class)){
                    if(annotation.valueType().equals(ValueType.KEY)){
                        List<Object> keyConfigList = (List<Object>)currentConfigMap.get(configKey);
                        List<Object> factDataList = (List<Object>)factMap.get(configKey);
                        ArrayList<String> arrayList = new ArrayList<>();
                        for (int i = 0; i < keyConfigList.size(); i++) {
                            Object value = factDataList.get(i);
                            boolean isValueTrue = value instanceof Boolean?(boolean) value:(int)value>0;
                            if(isValueTrue){
                                arrayList.add((String)keyConfigList.get(i));
                            }
                        }
                        field.set(this,arrayList);
                    }else if(annotation.valueType().equals(ValueType.VALUE)){
                        field.set(this,factMap.get(configKey));
                    }else {
                        continue;
                    }
                }else if (field.getType().equals(Map.class)) {
                    Map<String,Object> mergedMap = FactBuilder.mergeValueAndKey((List<String>)currentConfigMap.get(configKey),(List<Object>)factMap.get(configKey));
                    field.set(this,mergedMap);
                }else{
                    Map<String,Object> mergedMap = FactBuilder.mergeValueAndKey((List<String>)currentConfigMap.get(configKey),(List<Object>)factMap.get(configKey));
                    Object valueObject = JacksonUtils.toObject(JacksonUtils.toJson(mergedMap),Class.forName(this.getClass().getName()+"$"+ StringUtils.capitalize(configKey)));
                    field.set(this,valueObject);
                }
            } catch (Exception e) {
                log.error("构建fact时发生异常, configKey:{}, type:{}", configKey, type, e);
            }
        }
    }

}

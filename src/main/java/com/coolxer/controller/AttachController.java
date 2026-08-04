package com.coolxer.controller;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.RejectedItem;
import com.coolxer.model.vo.UploadAck;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.android.AndroidStartFact;
import com.coolxer.model.fact.h5.H5StartFact;
import com.coolxer.model.fact.host.HostStartFact;
import com.coolxer.model.fact.ios.IosStartFact;
import com.coolxer.model.fact.wechat.WechatStartFact;
import com.coolxer.model.punish.Punish;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.UploadMetrics;
import com.coolxer.service.idempotency.ClaimStatus;
import com.coolxer.service.idempotency.IdempotencyStore;
import com.coolxer.service.impl.RuleEngineImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/**
 * 数据通信核心接口
 */
@Slf4j
@RestController
@RequestMapping(value = {"/attach","1"})
public class AttachController extends AbstractController {
    private final RuleEngineImpl ruleEngineImpl;
    private final CheckerConfigService checkerConfigService;
    private final IdempotencyStore idempotencyStore;
    private final UploadMetrics uploadMetrics;
    private final ClientIpResolver clientIpResolver;

    public AttachController(RuleEngineImpl ruleEngineImpl, CheckerConfigService checkerConfigService,
                            IdempotencyStore idempotencyStore, UploadMetrics uploadMetrics,
                            ClientIpResolver clientIpResolver) {
        this.ruleEngineImpl = ruleEngineImpl;
        this.checkerConfigService = checkerConfigService;
        this.idempotencyStore = idempotencyStore;
        this.uploadMetrics = uploadMetrics;
        this.clientIpResolver = clientIpResolver;
    }

    @RequestMapping(value = {"start","/1"}, method =RequestMethod.POST)
    public ResponseVO start(@RequestHeader("code") String code, @RequestBody RequestVO requestVO,
                            HttpServletRequest servletRequest) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        String clientIp = clientIpResolver.resolve(servletRequest);
        common.setWanIp(clientIp);

        ArrayList<Punish> punishList = new ArrayList<>();
        ArrayList<Agenda> agendaList = new ArrayList<>();

        String platform = common.getPlatform();
        // 从服务中获取最新的配置
        Map<String,Object> config = checkerConfigService.getConfigByPlatform(platform);
        if (config == null) {
            return error("未找到平台配置: " + platform, common);
        }
        
        AbstractFact fact = null;
        if("ios".equals(platform)){
            fact = new IosStartFact("StartData",header,common,config);
        }else if("android".equals(platform)){
            fact = new AndroidStartFact("StartData",header,common,config);
        }else if("h5".equals(platform)){
            fact = new H5StartFact("StartData",header,common,config);
        }else if("wechat".equals(platform)){
            fact = new WechatStartFact("StartData",header,common,config);
        }else if("host".equals(platform)){
            fact = new HostStartFact("StartData",header,common,config);
        }else {
            return error("unknow platform:"+platform,common);
        }

        if (Objects.nonNull(fact)){
            fact.setRequestContext(clientIp, servletRequest.getHeader("User-Agent"));
            Checkpoint checkpoint = ruleEngineImpl.run(fact);
            agendaList.addAll(checkpoint.getAgendas());
            //TODO :需要对getPunishes的hashset进行优先级排序，客户端按顺序执行即可
            punishList.addAll(checkpoint.getPunishes());
        }
        return succeed(config,agendaList,punishList);

    }

    @RequestMapping(value = {"upload","/2"}, method = RequestMethod.POST)
    public Object upload(@RequestHeader("code") String code, @RequestBody RequestVO requestVO,
                         HttpServletRequest servletRequest) {
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        String clientIp = clientIpResolver.resolve(servletRequest);
        common.setWanIp(clientIp);

        RequestMeta meta = requestVO.getMeta();
        boolean v2 = meta != null && meta.isV2();
        UploadAck ack = v2 ? new UploadAck(meta.getBatchId()) : null;

        LinkedHashSet<Punish> punishSet = new LinkedHashSet<>();
        ArrayList<Agenda> agendaList = new ArrayList<>();
        LinkedHashMap<String, Agenda> uniqueAgendas = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> hitCounts = new LinkedHashMap<>();
        uploadMetrics.batch();

        if (v2 && !meta.isValidFor(common.getStartId())) {
            for (Map<String, Object> item : requestVO.getData() == null ? new ArrayList<Map<String, Object>>() : requestVO.getData()) {
                String invalidMessageId = item == null ? null : stringValue(item.get("messageId"));
                ack.getRejectedItems().add(new RejectedItem(invalidMessageId, false, "invalid v2 batch metadata"));
                uploadMetrics.rejected();
            }
            return succeedV2(agendaList, punishSet, ack, hitCounts);
        }

        for (Map<String,Object> itemMap: requestVO.getData() == null ? new ArrayList<Map<String,Object>>() : requestVO.getData()) {
            if(itemMap == null){
                if (v2) ack.getRejectedItems().add(new RejectedItem(null, false, "message is null"));
                uploadMetrics.rejected();
                continue;
            }

            String messageId = stringValue(itemMap.get("messageId"));
            String idempotencyKey = null;
            if (v2) {
                if (messageId == null || messageId.isBlank() || messageId.length() > 128
                        || stringValue(itemMap.get("type")) == null
                        || longValue(itemMap.get("sequence")) == null
                        || longValue(itemMap.get("observedAt")) == null) {
                    ack.getRejectedItems().add(new RejectedItem(messageId, false, "invalid message metadata"));
                    uploadMetrics.rejected();
                    continue;
                }
                idempotencyKey = "synap:h5:message:" + common.getAppId() + ":" + common.getStartId() + ":" + messageId;
                ClaimStatus claim = idempotencyStore.claim(idempotencyKey);
                if (claim == ClaimStatus.DONE) {
                    ack.getDuplicateMessageIds().add(messageId);
                    uploadMetrics.duplicate();
                    continue;
                }
                if (claim == ClaimStatus.PROCESSING) {
                    ack.getRejectedItems().add(new RejectedItem(messageId, true, "message is processing"));
                    uploadMetrics.rejected();
                    continue;
                }
            }

            try {
                AbstractFact fact = FactBuilder.buildFact(header,common,itemMap);
                if (Objects.isNull(fact)) {
                    if (v2) {
                        idempotencyStore.complete(idempotencyKey);
                        ack.getRejectedItems().add(new RejectedItem(messageId, false, "unsupported or invalid fact"));
                    }
                    uploadMetrics.rejected();
                    continue;
                }
                if (v2) {
                    fact.setTransportContext(messageId, meta.getBatchId(), longValue(itemMap.get("observedAt")),
                            longValue(itemMap.get("sequence")));
                }
                fact.setRequestContext(clientIp, servletRequest.getHeader("User-Agent"));
                Checkpoint checkpoint = ruleEngineImpl.run(fact);
                if (checkpoint == null) {
                    if (v2) {
                        idempotencyStore.release(idempotencyKey);
                        ack.getRejectedItems().add(new RejectedItem(messageId, true, "rule is unavailable"));
                    }
                    uploadMetrics.rejected();
                    continue;
                }
                for (Agenda agenda : checkpoint.getAgendas()) {
                    String key = agendaKey(agenda);
                    uniqueAgendas.putIfAbsent(key, agenda);
                    hitCounts.merge(key, 1, Integer::sum);
                }
                punishSet.addAll(checkpoint.getPunishes());
                if (v2) {
                    idempotencyStore.complete(idempotencyKey);
                    ack.getAcceptedMessageIds().add(messageId);
                }
                uploadMetrics.accepted(stringValue(itemMap.get("type")));
            } catch (RuntimeException error) {
                if (v2) {
                    idempotencyStore.release(idempotencyKey);
                    ack.getRejectedItems().add(new RejectedItem(messageId, true, "rule processing failed"));
                    uploadMetrics.rejected();
                    log.warn("H5 v2 message processing failed, messageId:{}", messageId, error);
                    continue;
                }
                throw error;
            }
        }
        agendaList.addAll(uniqueAgendas.values());
        if (v2) return succeedV2(agendaList, punishSet, ack, hitCounts);
        return succeed(agendaList, new ArrayList<>(punishSet));
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }




}

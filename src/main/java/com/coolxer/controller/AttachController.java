package com.coolxer.controller;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.RejectedItem;
import com.coolxer.model.vo.UploadAck;
import com.coolxer.model.vo.RiskAssessment;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.android.AndroidStartFact;
import com.coolxer.model.fact.h5.H5StartFact;
import com.coolxer.model.fact.host.HostStartFact;
import com.coolxer.model.fact.host.HostAuditFact;
import com.coolxer.model.fact.ios.IosStartFact;
import com.coolxer.model.fact.wechat.WechatStartFact;
import com.coolxer.model.punish.Punish;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.UploadMetrics;
import com.coolxer.service.RequestValidationService;
import com.coolxer.service.sequence.MessageSequenceStore;
import com.coolxer.service.sequence.SequenceObservation;
import com.coolxer.service.idempotency.ClaimStatus;
import com.coolxer.service.idempotency.IdempotencyStore;
import com.coolxer.service.impl.RuleEngineImpl;
import com.coolxer.utils.JacksonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
@RequestMapping(value = {"/attach", "1", "/risk"})
public class AttachController extends AbstractController {
    private final RuleEngineImpl ruleEngineImpl;
    private final CheckerConfigService checkerConfigService;
    private final IdempotencyStore idempotencyStore;
    private final UploadMetrics uploadMetrics;
    private final ClientIpResolver clientIpResolver;
    private final RequestValidationService requestValidationService;
    private final MessageSequenceStore sequenceStore;

    public AttachController(RuleEngineImpl ruleEngineImpl, CheckerConfigService checkerConfigService,
                            IdempotencyStore idempotencyStore, UploadMetrics uploadMetrics,
                            ClientIpResolver clientIpResolver, RequestValidationService requestValidationService,
                            MessageSequenceStore sequenceStore) {
        this.ruleEngineImpl = ruleEngineImpl;
        this.checkerConfigService = checkerConfigService;
        this.idempotencyStore = idempotencyStore;
        this.uploadMetrics = uploadMetrics;
        this.clientIpResolver = clientIpResolver;
        this.requestValidationService = requestValidationService;
        this.sequenceStore = sequenceStore;
    }

    @RequestMapping(value = {"start","/1"}, method =RequestMethod.POST)
    public ResponseVO start(@RequestHeader("code") String code, @RequestBody RequestVO requestVO,
                            HttpServletRequest servletRequest) {
        String validationError = requestValidationService.validateEnvelope(code, requestVO, servletRequest, false);
        if (validationError != null) {
            uploadMetrics.startRejected();
            return error(validationError, null);
        }
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        String platform = common.getPlatform();
        String clientIp = clientIpResolver.resolve(servletRequest);
        common.setWanIp("host".equalsIgnoreCase(platform)
                ? clientIpResolver.resolveWanIp(common.getWanIp(), clientIp) : clientIp);

        RequestMeta meta = requestVO.getMeta();
        boolean tracked = meta != null && meta.hasStartTracking();
        String idempotencyKey = null;
        SequenceObservation startSequence = null;
        if (tracked) {
            idempotencyKey = "synap:" + platform.toLowerCase() + ":message:" + common.getAppId() + ":"
                    + common.getStartId() + ":" + meta.getMessageId();
            ClaimStatus claim = idempotencyStore.claim(idempotencyKey);
            if (claim == ClaimStatus.DONE) {
                uploadMetrics.startDuplicate();
                String completed = idempotencyStore.completedPayload(idempotencyKey)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                "start response is unavailable"));
                return deserializeStartResponse(completed);
            }
            if (claim == ClaimStatus.PROCESSING) {
                uploadMetrics.startProcessing();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "start request is processing");
            }
            uploadMetrics.startTracked();
            startSequence = sequenceStore.observe(common, meta.getSequence());
        } else {
            uploadMetrics.startLegacy();
        }

        try {
            ArrayList<Punish> punishList = new ArrayList<>();
            ArrayList<Agenda> agendaList = new ArrayList<>();
            Map<String,Object> config = checkerConfigService.getConfigByPlatform(platform);
            if (config == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "未找到平台配置: " + platform);

            AbstractFact fact;
            if("ios".equals(platform)) fact = new IosStartFact("StartData",header,common,config);
            else if("android".equals(platform)) fact = new AndroidStartFact("StartData",header,common,config);
            else if("h5".equals(platform)) fact = new H5StartFact("StartData",header,common,config);
            else if("wechat".equals(platform)) fact = new WechatStartFact("StartData",header,common,config);
            else if("host".equals(platform)) fact = new HostStartFact("StartData",header,common,config);
            else throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown platform:" + platform);

            if (tracked) {
                fact.setTransportContext(meta.getMessageId(), meta.getBatchId(), meta.getObservedAt(), meta.getSequence());
            }
            fact.setRequestContext(clientIp, servletRequest.getHeader("User-Agent"));
            fact.setSecurityContext(requestValidationService.normalizedOrigin(servletRequest),
                    servletRequest.getHeader("Sec-Fetch-Site"), servletRequest.getHeader("Sec-Fetch-Mode"),
                    servletRequest.getHeader("Sec-Fetch-Dest"),
                    transportTrust(servletRequest), tracked ? startSequence.reasonCode() : "START_LEGACY");
            setGatewayContext(fact, servletRequest);
            Checkpoint checkpoint = ruleEngineImpl.run(fact);
            if (checkpoint == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "start rule is unavailable");
            agendaList.addAll(checkpoint.getAgendas());
            punishList.addAll(checkpoint.getPunishes());
            ResponseVO response = succeed(config,agendaList,punishList);
            response.setRisk(checkpoint.getRisk());
            if (tracked) idempotencyStore.complete(idempotencyKey, serializeStartResponse(response));
            return response;
        } catch (RuntimeException error) {
            if (tracked) idempotencyStore.release(idempotencyKey);
            throw error;
        }
    }

    @RequestMapping(value = {"upload","/2"}, method = RequestMethod.POST)
    public Object upload(@RequestHeader("code") String code, @RequestBody RequestVO requestVO,
                         HttpServletRequest servletRequest) {
        String requestPlatform = requestPlatform(requestVO);
        boolean protocolClient = isProtocolRequest(requestPlatform);
        RequestMeta meta = requestVO == null ? null : requestVO.getMeta();
        if ("host".equalsIgnoreCase(requestPlatform) && meta == null) {
            throw new ResponseStatusException(HttpStatus.UPGRADE_REQUIRED,
                    "Host upload tracking metadata is required; upgrade agent-host");
        }
        UploadAck ack = protocolClient ? new UploadAck(meta == null ? null : meta.getBatchId()) : null;
        String validationError = requestValidationService.validateEnvelope(code, requestVO, servletRequest, true);
        if (validationError != null) {
            if (!protocolClient) return error(validationError, null);
            rejectAll(requestVO, ack, validationError);
            return succeedUpload(new ArrayList<>(), new LinkedHashSet<>(), ack, new LinkedHashMap<>());
        }
        Header header = FactBuilder.buildHeader(code);
        Common common = FactBuilder.buildCommon(requestVO.getCommon());
        String platform = common.getPlatform();
        String clientIp = clientIpResolver.resolve(servletRequest);
        common.setWanIp("host".equalsIgnoreCase(platform)
                ? clientIpResolver.resolveWanIp(common.getWanIp(), clientIp) : clientIp);

        LinkedHashSet<Punish> punishSet = new LinkedHashSet<>();
        ArrayList<Agenda> agendaList = new ArrayList<>();
        LinkedHashMap<String, Agenda> uniqueAgendas = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> hitCounts = new LinkedHashMap<>();
        RiskAssessment latestRisk = null;
        uploadMetrics.batch();

        if (protocolClient && !meta.isValidFor(common.getStartId())) {
            for (Map<String, Object> item : requestVO.getData() == null ? new ArrayList<Map<String, Object>>() : requestVO.getData()) {
                String invalidMessageId = item == null ? null : stringValue(item.get("messageId"));
                ack.getRejectedItems().add(new RejectedItem(invalidMessageId, false, "invalid " + platform + " batch metadata"));
                uploadMetrics.rejected();
            }
            return succeedUpload(agendaList, punishSet, ack, hitCounts);
        }

        for (Map<String,Object> itemMap: requestVO.getData() == null ? new ArrayList<Map<String,Object>>() : requestVO.getData()) {
            if(itemMap == null){
                if (protocolClient) ack.getRejectedItems().add(new RejectedItem(null, false, "message is null"));
                uploadMetrics.rejected();
                continue;
            }

            String messageId = stringValue(itemMap.get("messageId"));
            String idempotencyKey = null;
            String hostAuditKey = null;
            SequenceObservation sequenceObservation = null;
            Long messageSequence = null;

            boolean hostAudit = "host".equalsIgnoreCase(platform) && "AuditData".equals(stringValue(itemMap.get("type")));
            if (hostAudit) {
                try {
                    HostAuditFact.validate(itemMap);
                } catch (IllegalArgumentException invalid) {
                    uploadMetrics.rejected();
                    return error(invalid.getMessage(), common);
                }
                String batchId = stringValue(itemMap.get("batchId"));
                hostAuditKey = "synap:host:audit:batch:" + common.getGuid() + ":" + batchId;
            }
            if (protocolClient) {
                messageSequence = longValue(itemMap.get("sequence"));
                Long observedAt = longValue(itemMap.get("observedAt"));
                if (messageId == null || messageId.isBlank() || messageId.length() > 128
                        || stringValue(itemMap.get("type")) == null
                        || messageSequence == null || messageSequence <= 0
                        || observedAt == null || observedAt <= 0) {
                    ack.getRejectedItems().add(new RejectedItem(messageId, false, "invalid message metadata"));
                    uploadMetrics.rejected();
                    continue;
                }
                idempotencyKey = "synap:" + platform.toLowerCase() + ":message:" + common.getAppId() + ":" + common.getStartId() + ":" + messageId;
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
            if (hostAudit) {
                ClaimStatus claim = idempotencyStore.claim(hostAuditKey);
                if (claim == ClaimStatus.DONE) {
                    if (protocolClient) {
                        sequenceStore.observe(common, messageSequence);
                        idempotencyStore.complete(idempotencyKey);
                        ack.getDuplicateMessageIds().add(messageId);
                    }
                    uploadMetrics.duplicate();
                    continue;
                }
                if (claim == ClaimStatus.PROCESSING) {
                    if (protocolClient) {
                        idempotencyStore.release(idempotencyKey);
                        ack.getRejectedItems().add(new RejectedItem(messageId, true, "audit batch is processing"));
                        uploadMetrics.rejected();
                        continue;
                    }
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "audit batch is processing");
                }
            }
            if (protocolClient) {
                sequenceObservation = sequenceStore.observe(common, messageSequence);
            }
            try {
                AbstractFact fact = FactBuilder.buildFact(header,common,itemMap);
                if (Objects.isNull(fact)) {
                    if (hostAudit) idempotencyStore.release(hostAuditKey);
                    if (protocolClient) {
                        idempotencyStore.complete(idempotencyKey);
                        ack.getRejectedItems().add(new RejectedItem(messageId, false, "unsupported or invalid fact"));
                    }
                    uploadMetrics.rejected();
                    continue;
                }
                if (protocolClient) {
                    fact.setTransportContext(messageId, meta.getBatchId(), longValue(itemMap.get("observedAt")),
                            longValue(itemMap.get("sequence")));
                }
                fact.setRequestContext(clientIp, servletRequest.getHeader("User-Agent"));
                fact.setSecurityContext(requestValidationService.normalizedOrigin(servletRequest),
                        servletRequest.getHeader("Sec-Fetch-Site"), servletRequest.getHeader("Sec-Fetch-Mode"),
                        servletRequest.getHeader("Sec-Fetch-Dest"),
                        transportTrust(servletRequest),
                        sequenceObservation == null ? "SEQUENCE_NOT_APPLICABLE" : sequenceObservation.reasonCode());
                setGatewayContext(fact, servletRequest);
                Checkpoint checkpoint = ruleEngineImpl.run(fact);
                if (checkpoint == null) {
                    if (hostAudit) {
                        idempotencyStore.release(hostAuditKey);
                        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "audit rule is unavailable");
                    }
                    if (protocolClient) {
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
                latestRisk = checkpoint.getRisk();
                if (protocolClient) {
                    idempotencyStore.complete(idempotencyKey);
                    ack.getAcceptedMessageIds().add(messageId);
                }
                if (hostAudit) idempotencyStore.complete(hostAuditKey);
                uploadMetrics.accepted(stringValue(itemMap.get("type")));
            } catch (RuntimeException error) {
                if (hostAudit) idempotencyStore.release(hostAuditKey);
                if (protocolClient) {
                    idempotencyStore.release(idempotencyKey);
                    ack.getRejectedItems().add(new RejectedItem(messageId, true, "rule processing failed"));
                    uploadMetrics.rejected();
                    log.warn("{} message processing failed, messageId:{}", platform, messageId, error);
                    continue;
                }
                throw error;
            }
        }
        agendaList.addAll(uniqueAgendas.values());
        if (protocolClient) {
            ResponseVO response = succeedUpload(agendaList, punishSet, ack, hitCounts);
            response.setRisk(latestRisk);
            return response;
        }
        ResponseVO response = succeed(agendaList, new ArrayList<>(punishSet));
        response.setRisk(latestRisk);
        return response;
    }

    private static String requestPlatform(RequestVO requestVO) {
        return requestVO != null && requestVO.getCommon() != null && requestVO.getCommon().size() > 8
                ? String.valueOf(requestVO.getCommon().get(8)) : "";
    }

    private static boolean isProtocolRequest(String platform) {
        return "h5".equalsIgnoreCase(platform) || "wechat".equalsIgnoreCase(platform)
                || "host".equalsIgnoreCase(platform);
    }

    private static String transportTrust(HttpServletRequest request) {
        Object trust = request.getAttribute("sc.transportTrust");
        return trust == null ? "unauthenticated" : String.valueOf(trust);
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private void rejectAll(RequestVO requestVO, UploadAck ack, String reason) {
        if (requestVO == null || requestVO.getData() == null) return;
        for (Map<String, Object> item : requestVO.getData()) {
            ack.getRejectedItems().add(new RejectedItem(item == null ? null : stringValue(item.get("messageId")), false, reason));
            uploadMetrics.rejected();
        }
    }

    private void setGatewayContext(AbstractFact fact, HttpServletRequest request) {
        ClientIpResolver.GatewaySignals signals = clientIpResolver.gatewaySignals(request);
        fact.setGatewayContext(signals.asn(), signals.networkCategory(), signals.tlsFingerprint(), signals.ipReputation());
    }

    private static String serializeStartResponse(ResponseVO response) {
        try { return JacksonUtils.toJson(response); }
        catch (Exception error) { throw new IllegalStateException("start response serialization failed", error); }
    }

    private static ResponseVO deserializeStartResponse(String payload) {
        try { return JacksonUtils.fromSerializedJson(payload, ResponseVO.class); }
        catch (Exception error) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "start response replay failed", error); }
    }




}

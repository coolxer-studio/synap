package com.coolxer.controller;

import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.RequestValidationService;
import com.coolxer.service.UploadMetrics;
import com.coolxer.service.idempotency.InMemoryIdempotencyStore;
import com.coolxer.service.idempotency.IdempotencyStore;
import com.coolxer.service.idempotency.ClaimStatus;
import com.coolxer.service.impl.RuleEngineImpl;
import com.coolxer.service.sequence.InMemoryMessageSequenceStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AttachControllerContractTest {
    @Test
    void tracksAndReplaysStartOnceForEveryPlatform() {
        for (String platform : Arrays.asList("ios", "android", "h5", "wechat", "host")) {
            RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
            when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                    "StartRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
            AttachController controller = controller(ruleEngine);
            RequestVO request = startRequest(platform, true);

            ResponseVO first = controller.start(header(platform), request, requestContext());
            ResponseVO duplicate = controller.start(header(platform), request, requestContext());

            assertEquals(first, duplicate, platform);
            ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
            verify(ruleEngine, times(1)).run(facts.capture());
            AbstractFact fact = facts.getValue();
            assertEquals("start-message-1", fact.getMessageId(), platform);
            assertEquals("start-batch-1", fact.getBatchId(), platform);
            assertEquals(1L, fact.getSequence(), platform);
            assertTrue(fact.getObservedAt() > 0, platform);
            assertEquals("SEQUENCE_OK", fact.getSequenceObservation(), platform);
        }
    }

    @Test
    void acceptsLegacyStartWithoutTracking() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "H5StartFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);

        ResponseVO response = controller.start(header("h5"), startRequest("h5", false), requestContext());

        assertEquals(1, response.getStatus());
        ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine).run(facts.capture());
        assertEquals("START_LEGACY", facts.getValue().getSequenceObservation());
    }

    @Test
    void startSequenceOneMakesFirstUploadSequenceTwoContiguous() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "Rule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);
        controller.start(header("h5"), startRequest("h5", true), requestContext());
        RequestVO upload = request();
        upload.getData().get(0).put("sequence", 2);

        controller.upload(header("h5"), upload, requestContext());

        ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine, times(2)).run(facts.capture());
        assertEquals("SEQUENCE_OK", facts.getAllValues().get(0).getSequenceObservation());
        assertEquals("SEQUENCE_OK", facts.getAllValues().get(1).getSequenceObservation());
    }

    @Test
    void hostStartSequenceOneMakesFirstUploadSequenceTwoContiguous() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "HostRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);
        controller.start(header("host"), startRequest("host", true), requestContext());
        RequestVO upload = request();
        upload.getCommon().set(8, "host");
        upload.getData().get(0).put("sequence", 2);

        ResponseVO response = (ResponseVO) controller.upload(header("host"), upload, requestContext());

        assertEquals(java.util.List.of("message-1"), response.getAck().getAcceptedMessageIds());
        ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine, times(2)).run(facts.capture());
        assertEquals("SEQUENCE_OK", facts.getAllValues().get(0).getSequenceObservation());
        assertEquals("SEQUENCE_OK", facts.getAllValues().get(1).getSequenceObservation());
        assertEquals("message-1", facts.getAllValues().get(1).getMessageId());
    }

    @Test
    void startProcessingIsRetryableAndFailuresReleaseTheClaim() {
        RuleEngineImpl blockedRule = mock(RuleEngineImpl.class);
        IdempotencyStore blockedStore = mock(IdempotencyStore.class);
        when(blockedStore.claim(any())).thenReturn(ClaimStatus.PROCESSING);
        ResponseStatusException processing = assertThrows(ResponseStatusException.class,
                () -> controller(blockedRule, blockedStore).start(header("h5"),
                        startRequest("h5", true), requestContext()));
        assertEquals(503, processing.getStatusCode().value());
        verify(blockedRule, never()).run(any());

        RuleEngineImpl retryRule = mock(RuleEngineImpl.class);
        when(retryRule.run(any())).thenThrow(new RuntimeException("kafka unavailable"))
                .thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                        "H5StartFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController retryController = controller(retryRule);
        RequestVO tracked = startRequest("h5", true);
        assertThrows(RuntimeException.class,
                () -> retryController.start(header("h5"), tracked, requestContext()));
        assertEquals(1, retryController.start(header("h5"), tracked, requestContext()).getStatus());
        verify(retryRule, times(2)).run(any());
    }

    @Test
    void acknowledgesOnceAndMarksRetryAsDuplicate() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "H5UserFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);
        HttpServletRequest servletRequest = requestContext();

        RequestVO request = request();
        ResponseVO first = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, servletRequest);
        ResponseVO duplicate = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, servletRequest);

        assertEquals(Arrays.asList("message-1"), first.getAck().getAcceptedMessageIds());
        assertEquals(Arrays.asList("message-1"), duplicate.getAck().getDuplicateMessageIds());
        ArgumentCaptor<AbstractFact> factCaptor = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine).run(factCaptor.capture());
        assertEquals("203.0.113.8", factCaptor.getValue().getCommon().getWanIp());
        assertEquals("203.0.113.8", factCaptor.getValue().getRequestIp());
        assertEquals("SEQUENCE_OK", factCaptor.getValue().getSequenceObservation());
        assertEquals("sc1-authenticated/P256_HKDF_SHA256_AES256_GCM",
                factCaptor.getValue().getTransportTrust());
    }

    @Test
    void rejectsMissingBatchMetadataWithAck() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        AttachController controller = controller(ruleEngine);
        RequestVO request = request();
        request.setMeta(null);

        ResponseVO response = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, requestContext());

        assertEquals(1, response.getStatus());
        assertEquals(1, response.getAck().getRejectedItems().size());
        assertFalse(response.getAck().getRejectedItems().get(0).isRetryable());
        assertEquals("H5 metadata is required", response.getAck().getRejectedItems().get(0).getReason());
    }

    @Test
    void releasesClaimAfterProcessingFailure() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenThrow(new RuntimeException("kafka unavailable"))
                .thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0), "H5UserFactRule.groovy",
                        new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);

        ResponseVO failed = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request(), requestContext());
        ResponseVO retried = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request(), requestContext());

        assertTrue(failed.getAck().getRejectedItems().get(0).isRetryable());
        assertEquals(Arrays.asList("message-1"), retried.getAck().getAcceptedMessageIds());
    }

    @Test
    void rejectsMissingMessageMetadataPermanently() {
        AttachController controller = controller(mock(RuleEngineImpl.class));
        for (String field : Arrays.asList("messageId", "sequence", "observedAt")) {
            RequestVO request = request();
            request.getData().get(0).remove(field);

            ResponseVO response = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, requestContext());

            assertEquals(1, response.getAck().getRejectedItems().size(), field);
            assertFalse(response.getAck().getRejectedItems().get(0).isRetryable(), field);
        }
    }

    @Test
    void appliesAckIdempotencyAndSequenceContractToWechat() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "WechatUserFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);
        RequestVO request = request();
        request.getCommon().set(6, "wx-test-app");
        request.getCommon().set(8, "wechat");

        ResponseVO first = (ResponseVO) controller.upload("a-b-c-d-e-wechat-1-1.1.0", request, requestContext());
        ResponseVO duplicate = (ResponseVO) controller.upload("a-b-c-d-e-wechat-1-1.1.0", request, requestContext());

        assertEquals(Arrays.asList("message-1"), first.getAck().getAcceptedMessageIds());
        assertEquals(Arrays.asList("message-1"), duplicate.getAck().getDuplicateMessageIds());
        ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine).run(facts.capture());
        assertEquals("wechat", facts.getValue().getCommon().getPlatform());
        assertEquals("sc1-authenticated/P256_HKDF_SHA256_AES256_GCM",
                facts.getValue().getTransportTrust());
        assertEquals("SEQUENCE_OK", facts.getValue().getSequenceObservation());
    }

    @Test
    void appliesAckIdempotencyAndSequenceContractToAndroid() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "AndroidUserFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = controller(ruleEngine);
        RequestVO request = request();
        request.getCommon().set(8, "android");
        request.getCommon().set(15, "8.8.8.8");
        ArrayList<Integer> legacyDetectorCollection = new ArrayList<>();
        for (int index = 0; index < 300; index++) legacyDetectorCollection.add(index);
        request.getData().get(0).put("data", Map.of("apps", legacyDetectorCollection));

        ResponseVO first = (ResponseVO) controller.upload(header("android"), request, requestContext());
        ResponseVO duplicate = (ResponseVO) controller.upload(header("android"), request, requestContext());

        assertEquals(Arrays.asList("message-1"), first.getAck().getAcceptedMessageIds());
        assertEquals(Arrays.asList("message-1"), duplicate.getAck().getDuplicateMessageIds());
        ArgumentCaptor<AbstractFact> facts = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine).run(facts.capture());
        assertEquals("android", facts.getValue().getCommon().getPlatform());
        assertEquals("8.8.8.8", facts.getValue().getCommon().getWanIp());
        assertEquals("message-1", facts.getValue().getMessageId());
        assertEquals("sc1-authenticated/P256_HKDF_SHA256_AES256_GCM",
                facts.getValue().getTransportTrust());
        assertEquals("SEQUENCE_OK", facts.getValue().getSequenceObservation());
    }

    private AttachController controller(RuleEngineImpl ruleEngine) {
        return controller(ruleEngine,
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100));
    }

    private AttachController controller(RuleEngineImpl ruleEngine, IdempotencyStore idempotencyStore) {
        CheckerConfigService checker = mock(CheckerConfigService.class);
        when(checker.getConfigByPlatform(any())).thenReturn(Map.of("startConfig", Arrays.asList(1, 1, 1, 1, 1, 1)));
        return new AttachController(ruleEngine, checker,
                idempotencyStore, new UploadMetrics(),
                new ClientIpResolver("127.0.0.1/32"), validator(),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
    }

    private RequestVO startRequest(String platform, boolean tracked) {
        RequestVO request = new RequestVO();
        ArrayList<String> common = common();
        common.set(8, platform);
        if ("wechat".equals(platform)) common.set(6, "wx-test-app");
        request.setCommon(common);
        RequestMeta meta = new RequestMeta();
        meta.setProtocolVersion(1);
        meta.setAppKeyId("0123456789abcdef0123456789abcdef");
        meta.setDetectorVersion("1.3.0");
        if (tracked) {
            meta.setMessageId("start-message-1");
            meta.setBatchId("start-batch-1");
            meta.setSessionId("100");
            meta.setAttempt(1);
            meta.setCreatedAt(System.currentTimeMillis());
            meta.setObservedAt(System.currentTimeMillis());
            meta.setSequence(1L);
        }
        request.setMeta(meta);
        return request;
    }

    private String header(String platform) {
        return "a-b-c-d-e-" + platform + "-1-1.1.0";
    }

    private HttpServletRequest requestContext() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.8");
        when(request.getHeader("User-Agent")).thenReturn("test-agent");
        when(request.getAttribute("sc.transportTrust"))
                .thenReturn("sc1-authenticated/P256_HKDF_SHA256_AES256_GCM");
        return request;
    }

    private RequestVO request() {
        RequestMeta meta = new RequestMeta();
        meta.setProtocolVersion(1);
        meta.setBatchId("batch-1");
        meta.setSessionId("100");
        meta.setAttempt(1);
        meta.setCreatedAt(System.currentTimeMillis());
        meta.setAppKeyId("0123456789abcdef0123456789abcdef");
        meta.setDetectorVersion("1.3.0");

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "UserData");
        item.put("buildDate", "2026-08-05 10:00:00");
        item.put("messageId", "message-1");
        item.put("sequence", 1);
        item.put("observedAt", System.currentTimeMillis());
        item.put("userId", "user-hash");
        item.put("data", Map.of("scene", "test"));

        RequestVO request = new RequestVO();
        request.setMeta(meta);
        request.setCommon(common());
        request.setData(new ArrayList<>(Arrays.asList(item)));
        return request;
    }

    private RequestValidationService validator() {
        return new RequestValidationService("", "", true, 262144, 100, 8192);
    }

    private ArrayList<String> common() {
        return new ArrayList<>(Arrays.asList("user", "a-b-c-d-e", "100", "1.1.0", "1", "app", "example.test", "-",
                "h5", "", "", "", "", "", "", "", "0", "0", "", "", "", "", "", "2026-08-05 10:00:00"));
    }
}

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
import com.coolxer.service.impl.RuleEngineImpl;
import com.coolxer.service.sequence.InMemoryMessageSequenceStore;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttachControllerContractTest {
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

    private AttachController controller(RuleEngineImpl ruleEngine) {
        return new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver("127.0.0.1/32"), validator(),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
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

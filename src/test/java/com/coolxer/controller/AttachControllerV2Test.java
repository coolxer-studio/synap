package com.coolxer.controller;

import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.UploadMetrics;
import com.coolxer.service.idempotency.InMemoryIdempotencyStore;
import com.coolxer.service.impl.RuleEngineImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import com.coolxer.model.fact.AbstractFact;

class AttachControllerV2Test {
    @Test
    void acknowledgesOnceAndMarksRetryAsDuplicate() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "H5UserFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver("127.0.0.1/32"));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(servletRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.8");
        when(servletRequest.getHeader("User-Agent")).thenReturn("test-agent");

        RequestVO request = v2Request();
        ResponseVO first = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, servletRequest);
        ResponseVO duplicate = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, servletRequest);

        assertEquals(Arrays.asList("message-1"), first.getAck().getAcceptedMessageIds());
        assertEquals(Arrays.asList("message-1"), duplicate.getAck().getDuplicateMessageIds());
        ArgumentCaptor<AbstractFact> factCaptor = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine).run(factCaptor.capture());
        assertEquals("203.0.113.8", factCaptor.getValue().getCommon().getWanIp());
        assertEquals("203.0.113.8", factCaptor.getValue().getRequestIp());
    }

    @Test
    void keepsLegacyRequestCompatible() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenReturn(null);
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""));
        RequestVO request = v2Request();
        request.setMeta(null);

        ResponseVO response = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", request, mock(HttpServletRequest.class));

        assertEquals(1, response.getStatus());
        assertNull(response.getAck());
    }

    @Test
    void releasesClaimAfterProcessingFailure() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenThrow(new RuntimeException("kafka unavailable"))
                .thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0), "H5UserFactRule.groovy",
                        new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);

        ResponseVO failed = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", v2Request(), servletRequest);
        ResponseVO retried = (ResponseVO) controller.upload("a-b-c-d-e-h5-1-1.1.0", v2Request(), servletRequest);

        assertTrue(failed.getAck().getRejectedItems().get(0).isRetryable());
        assertEquals(Arrays.asList("message-1"), retried.getAck().getAcceptedMessageIds());
    }

    private RequestVO v2Request() {
        RequestMeta meta = new RequestMeta();
        meta.setProtocolVersion(2);
        meta.setBatchId("batch-1");
        meta.setSessionId("100");
        meta.setAttempt(1);
        meta.setCreatedAt(System.currentTimeMillis());

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

    private ArrayList<String> common() {
        return new ArrayList<>(Arrays.asList("user", "guid", "100", "1.1.0", "1", "app", "example.test", "-",
                "h5", "", "", "", "", "", "", "", "0", "0", "", "", "", "", "", "2026-08-05 10:00:00"));
    }
}

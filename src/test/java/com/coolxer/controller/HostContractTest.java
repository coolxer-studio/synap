package com.coolxer.controller;

import com.coolxer.config.CheckerConfig;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.host.HostDeviceFact;
import com.coolxer.model.fact.host.HostEventFact;
import com.coolxer.model.fact.host.HostAuditFact;
import com.coolxer.model.fact.host.HostFileFact;
import com.coolxer.model.fact.host.HostNetFact;
import com.coolxer.model.fact.host.HostRuntimeFact;
import com.coolxer.model.vo.RequestVO;
import com.coolxer.model.vo.RequestMeta;
import com.coolxer.model.vo.ResponseVO;
import com.coolxer.service.CheckerConfigService;
import com.coolxer.service.ClientIpResolver;
import com.coolxer.service.RequestValidationService;
import com.coolxer.service.UploadMetrics;
import com.coolxer.service.idempotency.InMemoryIdempotencyStore;
import com.coolxer.service.impl.RuleEngineImpl;
import com.coolxer.service.sequence.InMemoryMessageSequenceStore;
import com.coolxer.utils.JacksonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

class HostContractTest {
    private static Map<String, Object> hostConfig;

    @BeforeAll
    static void loadHostConfig() throws Exception {
        hostConfig = JacksonUtils.toMap(Files.readString(Path.of("deploy/open_config/checker_config/host.json")));
        CheckerConfigService.platformConfigMap.put("host", hostConfig);
        assertNotNull(CheckerConfig.getInstance().getCommonConfig());
    }

    @Test
    void buildsEveryHostFactWithTheConfiguredArrayShape() {
        Header header = new Header("a-b-c-d-e-host-1-2.0.0");
        Common common = FactBuilder.buildCommon(common());
        List<Map<String, Object>> facts = facts();

        assertTrue(header.isConsistentWith(common));
        assertShape(facts.get(0), configSection("deviceConfig"));
        assertShape(facts.get(1), configSection("fileConfig"));
        assertShape(facts.get(2), configSection("runtimeConfig"));
        assertShape(facts.get(3), configSection("netConfig"));

        HostDeviceFact device = assertInstanceOf(HostDeviceFact.class, FactBuilder.buildFact(header, common, facts.get(0)));
        assertSame(common, device.getCommon());
        assertSame(header, device.getHeader());
        assertNotNull(device.getDevice());
        assertNotNull(device.getStorage());
        assertNotNull(assertInstanceOf(HostFileFact.class, FactBuilder.buildFact(header, common, facts.get(1))).getFileList());
        assertNotNull(assertInstanceOf(HostRuntimeFact.class, FactBuilder.buildFact(header, common, facts.get(2))).getEnv());
        assertNotNull(assertInstanceOf(HostNetFact.class, FactBuilder.buildFact(header, common, facts.get(3))).getUds());
        assertNotNull(assertInstanceOf(HostEventFact.class, FactBuilder.buildFact(header, common, facts.get(4))).getSshd());
        HostAuditFact audit = assertInstanceOf(HostAuditFact.class, FactBuilder.buildFact(header, common, facts.get(5)));
        assertEquals("d2719b37-9d9f-41a8-b1bc-9bdc6298c847", audit.getBatchId());
        assertEquals(18, ((List<?>) audit.getEvents().get(0)).size());
    }

    @Test
    void uploadKeepsHeaderContractAndUsesTcpSourceAddress() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "HostFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        CheckerConfigService checker = mock(CheckerConfigService.class);
        when(checker.getConfigByPlatform("host")).thenReturn(hostConfig);
        InMemoryMessageSequenceStore sequences = new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100);
        sequences.observe(FactBuilder.buildCommon(common()), 1);
        AttachController controller = new AttachController(ruleEngine, checker,
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""), new RequestValidationService("", "", true, 262144, 100, 8192),
                sequences);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.9");

        ArrayList<String> uploadCommon = common();
        uploadCommon.set(15, "8.8.8.8");
        RequestVO request = trackedRequest(uploadCommon, facts(), "batch-all");
        ResponseVO response = (ResponseVO) controller.upload("a-b-c-d-e-host-1-2.0.0", request, servletRequest);

        assertEquals(1, response.getStatus());
        assertEquals(6, response.getAck().getAcceptedMessageIds().size());
        ArgumentCaptor<AbstractFact> captor = ArgumentCaptor.forClass(AbstractFact.class);
        verify(ruleEngine, org.mockito.Mockito.times(6)).run(captor.capture());
        captor.getAllValues().forEach(value -> {
            assertEquals("8.8.8.8", value.getCommon().getWanIp());
            assertEquals("203.0.113.9", value.getRequestIp());
            assertNotNull(value.getMessageId());
            assertEquals("batch-all", value.getBatchId());
            assertNotNull(value.getObservedAt());
            assertNotNull(value.getSequence());
        });
        assertEquals("SEQUENCE_OK", captor.getAllValues().get(0).getSequenceObservation());
    }

    @Test
    void duplicateAuditBatchIsAcknowledgedWithoutPublishingAgain() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0),
                "HostAuditFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100);
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class), idempotency,
                new UploadMetrics(), new ClientIpResolver(""),
                new RequestValidationService("", "", true, 262144, 100, 8192),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.9");
        RequestVO request = trackedRequest(common(), List.of(facts().get(5)), "batch-audit-transport");
        RequestVO sameAuditDifferentMessage = trackedRequest(common(), List.of(facts().get(5)), "batch-audit-transport-2");
        sameAuditDifferentMessage.getData().get(0).put("messageId", "host-message-3");
        sameAuditDifferentMessage.getData().get(0).put("sequence", 3);

        ResponseVO first = (ResponseVO) controller.upload("a-b-c-d-e-host-1-2.0.0", request, servletRequest);
        ResponseVO duplicate = (ResponseVO) controller.upload("a-b-c-d-e-host-1-2.0.0", sameAuditDifferentMessage, servletRequest);
        assertEquals(List.of("host-message-2"), first.getAck().getAcceptedMessageIds());
        assertEquals(List.of("host-message-3"), duplicate.getAck().getDuplicateMessageIds());
        verify(ruleEngine, org.mockito.Mockito.times(1)).run(any());
    }

    @Test
    void failedAuditPublishReleasesBatchForRetry() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        when(ruleEngine.run(any())).thenThrow(new RuntimeException("kafka unavailable"))
                .thenAnswer(invocation -> new Checkpoint(invocation.getArgument(0), "HostAuditFactRule.groovy",
                        new ArrayList<>(), new HashSet<>(), "now"));
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""), new RequestValidationService("", "", true, 262144, 100, 8192),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.9");
        RequestVO request = trackedRequest(common(), List.of(facts().get(5)), "batch-audit-retry");

        ResponseVO failed = (ResponseVO) controller.upload("a-b-c-d-e-host-1-2.0.0", request, servletRequest);
        assertTrue(failed.getAck().getRejectedItems().get(0).isRetryable());
        ResponseVO retried = (ResponseVO) controller.upload("a-b-c-d-e-host-1-2.0.0", request, servletRequest);
        assertEquals(List.of("host-message-2"), retried.getAck().getAcceptedMessageIds());
        verify(ruleEngine, org.mockito.Mockito.times(2)).run(any());
    }

    @Test
    void rejectsLegacyHostUploadWithUpgradeRequiredBeforeRuleExecution() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""), new RequestValidationService("", "", true, 262144, 100, 8192),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
        RequestVO legacy = new RequestVO(); legacy.setCommon(common()); legacy.setData(new ArrayList<>(facts()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.upload("a-b-c-d-e-host-1-2.0.0", legacy, mock(HttpServletRequest.class)));
        assertEquals(HttpStatus.UPGRADE_REQUIRED, error.getStatusCode());
        verify(ruleEngine, never()).run(any());
    }

    @Test
    void rejectsPartialAndWrongSessionHostTrackingBeforeRuleExecution() {
        RuleEngineImpl ruleEngine = mock(RuleEngineImpl.class);
        AttachController controller = new AttachController(ruleEngine, mock(CheckerConfigService.class),
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""), new RequestValidationService("", "", true, 262144, 100, 8192),
                new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100));
        RequestVO partial = trackedRequest(common(), List.of(facts().get(0)), "batch-partial");
        partial.getData().get(0).remove("observedAt");
        ResponseVO partialResponse = (ResponseVO) controller.upload(
                "a-b-c-d-e-host-1-2.0.0", partial, mock(HttpServletRequest.class));
        assertEquals("invalid message metadata", partialResponse.getAck().getRejectedItems().get(0).getReason());

        RequestVO wrongSession = trackedRequest(common(), List.of(facts().get(0)), "batch-session");
        wrongSession.getMeta().setSessionId("wrong");
        ResponseVO sessionResponse = (ResponseVO) controller.upload(
                "a-b-c-d-e-host-1-2.0.0", wrongSession, mock(HttpServletRequest.class));
        assertTrue(sessionResponse.getAck().getRejectedItems().get(0).getReason().contains("batch metadata"));
        verify(ruleEngine, never()).run(any());
    }

    @Test
    void keepsCanonicalAndNumericQueryIds() throws Exception {
        List<Object> configs = JacksonUtils.toList(Files.readString(Path.of("deploy/open_config/msg_api_config/msg_api_config.json")));
        List<String> ids = configs.stream().map(value -> String.valueOf(((Map<?, ?>) value).get("id"))).toList();
        assertTrue(ids.containsAll(List.of("info", "1", "label_start", "5", "rating", "6")));
    }

    private static ArrayList<String> common() {
        return new ArrayList<>(Arrays.asList("user", "a-b-c-d-e", "1700000000000", "2.0.0", "1", "x-probe", "agent-host", "2.0.0",
                "host", "vendor", "model", "linux", "6.1", "ethernet", "192.0.2.2", "", "0", "0", "", "", "", "", "", "2026-08-05 10:00:00"));
    }

    private static RequestVO trackedRequest(ArrayList<String> common, List<Map<String, Object>> facts, String batchId) {
        RequestMeta meta = new RequestMeta();
        meta.setProtocolVersion(1); meta.setAppKeyId("1"); meta.setDetectorVersion("2.0.0");
        meta.setBatchId(batchId); meta.setSessionId(common.get(2)); meta.setAttempt(1);
        meta.setCreatedAt(System.currentTimeMillis());
        ArrayList<Map<String, Object>> tracked = new ArrayList<>();
        for (int index = 0; index < facts.size(); index++) {
            Map<String, Object> fact = mutableMap(facts.get(index));
            fact.put("messageId", "host-message-" + (index + 2));
            fact.put("observedAt", System.currentTimeMillis());
            fact.put("sequence", index + 2);
            tracked.add(fact);
        }
        RequestVO request = new RequestVO(); request.setCommon(common); request.setMeta(meta); request.setData(tracked);
        return request;
    }

    private static List<Map<String, Object>> facts() {
        List<Map<String, Object>> values = new ArrayList<>();
        values.add(fact("DeviceData", Map.of(
                "device", List.of("host", "node", "linux", "6.1", "kernel", "model", "", "zh_CN", "CST", "", "1700000000000"),
                "screen", List.of("", "", "", "", ""), "cpu", List.of("8", "2", "cpu", "amd64"),
                "battery", List.of("", ""), "uuid", List.of("aa:bb", "serial", "boot", "a-b-c-d-e"),
                "network", List.of("192.0.2.2", "255.255.255.0", "192.0.2.255", "", "", "ethernet"),
                "storage", List.of("1000", "100", "2000", "500"))));
        values.add(fact("FileData", Map.of("exist", List.of(0, 0, 0, 0), "context", List.of(),
                "fileList", List.of(List.of()), "md5", List.of(), "access", List.of(), "yara", List.of())));
        values.add(fact("RuntimeData", Map.of("process", List.of(""), "env", List.of("", "", "", "", ""), "cmd", List.of())));
        values.add(fact("NetData", Map.of("port", List.of(0, 0), "socket", List.of(0, 0), "uds", List.of(0, 0),
                "netInterface", List.of(""), "httpProxy", List.of(""), "hosts", List.of(""), "certificate", List.of())));
        values.add(fact("EventData", Map.of("sshd", List.of())));
        values.add(fact("AuditData", Map.of(
                "batchId", "d2719b37-9d9f-41a8-b1bc-9bdc6298c847",
                "events", List.of(List.of(
                        "bcd4f8a1d21d4395ad3c3815c01670aa", "2026-08-05T10:00:00Z", "session", "login", "failure",
                        "alice", "alice", "sshd", "42", "sshd", "192.0.2.2:22", "198.51.100.8:50000",
                        "tcp", "", "", "auditd", "boot-id", "{\"method\":\"password\"}")))));
        return values.stream().map(HostContractTest::mutableMap).toList();
    }

    private static Map<String, Object> fact(String type, Map<String, Object> data) {
        Map<String, Object> value = new LinkedHashMap<>(data);
        value.put("type", type);
        value.put("buildDate", "2026-08-05 10:00:00");
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> configSection(String name) {
        return (Map<String, Object>) hostConfig.get(name);
    }

    private static void assertShape(Map<String, Object> fact, Map<String, Object> config) {
        config.forEach((key, value) -> assertEquals(((List<?>) value).size(), ((List<?>) fact.get(key)).size(), key));
    }

    private static Map<String, Object> mutableMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, mutable(value)));
        return result;
    }

    private static Object mutable(Object value) {
        if (value instanceof List<?> list) {
            ArrayList<Object> result = new ArrayList<>();
            list.forEach(item -> result.add(mutable(item)));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), mutable(item)));
            return result;
        }
        return value;
    }
}

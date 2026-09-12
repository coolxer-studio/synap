package com.coolxer.controller;

import com.coolxer.config.CheckerConfig;
import com.coolxer.config.TopicDefine;
import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.FactBuilder;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.server.ServerApiAssetFact;
import com.coolxer.model.fact.server.ServerApplicationFact;
import com.coolxer.model.fact.server.ServerRuntimeFact;
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
import com.coolxer.securecommunication.spi.EnrollmentTokenService;
import com.coolxer.utils.JacksonUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServerContractTest {
    private static Map<String, Object> serverConfig;

    @BeforeAll
    static void configure() throws Exception {
        serverConfig = JacksonUtils.toMap(Files.readString(Path.of("deploy/open_config/checker_config/server.json")));
        CheckerConfigService.platformConfigMap.put("server", serverConfig);
        assertEquals(24, CheckerConfig.getInstance().getCommonConfig().size());
    }

    @Test
    void buildsNamedServerFactsAndRejectsUnknownFields() {
        Header header = new Header("11111111-1111-1111-1111-111111111111-server-1-1.0.0");
        Common common = FactBuilder.buildCommon(common());
        assertTrue(header.isConsistentWith(common));
        assertInstanceOf(ServerApplicationFact.class, FactBuilder.buildFact(header, common, application()));
        assertInstanceOf(ServerRuntimeFact.class, FactBuilder.buildFact(header, common, runtime()));
        assertInstanceOf(ServerApiAssetFact.class, FactBuilder.buildFact(header, common, apiAsset()));

        Map<String, Object> invalid = new LinkedHashMap<>(application()); invalid.put("rawAuthorization", "secret");
        assertThrows(IllegalArgumentException.class, () -> new ServerApplicationFact("ApplicationData", header, common, invalid));
        assertNull(FactBuilder.buildFact(header, common, invalid));
    }

    @Test
    void validatesTrackedServerEnvelopeAndPublishesEveryAcceptedFact() {
        RequestValidationService validation = new RequestValidationService("", "", true, 262144, 100, 8192);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        when(servlet.getRemoteAddr()).thenReturn("203.0.113.10");
        RequestVO request = request(List.of(application(), runtime(), apiAsset()));
        assertNull(validation.validateEnvelope("11111111-1111-1111-1111-111111111111-server-1-1.0.0", request, servlet, true));

        RuleEngineImpl rules = mock(RuleEngineImpl.class);
        when(rules.run(any())).thenAnswer(call -> new Checkpoint(call.getArgument(0), "ServerFactRule.groovy", new ArrayList<>(), new HashSet<>(), "now"));
        CheckerConfigService checker = mock(CheckerConfigService.class); when(checker.getConfigByPlatform("server")).thenReturn(serverConfig);
        InMemoryMessageSequenceStore sequences = new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100);
        sequences.observe(FactBuilder.buildCommon(common()), 1);
        AttachController controller = new AttachController(rules, checker,
                new InMemoryIdempotencyStore(Duration.ofMinutes(5), Duration.ofDays(14), 100), new UploadMetrics(),
                new ClientIpResolver(""), validation, sequences);

        ResponseVO response = (ResponseVO) controller.upload("11111111-1111-1111-1111-111111111111-server-1-1.0.0", request, servlet);
        assertEquals(1, response.getStatus());
        assertEquals(List.of("server-message-2", "server-message-3", "server-message-4"), response.getAck().getAcceptedMessageIds());
        verify(rules, org.mockito.Mockito.times(3)).run(any(AbstractFact.class));
    }

    @Test
    void mapsEveryServerFactAndRejectsLegacyJvmContract() {
        Header header = new Header("11111111-1111-1111-1111-111111111111-server-1-1.0.0");
        Common common = FactBuilder.buildCommon(common());
        Map<String, String> factClasses = Map.ofEntries(
                Map.entry("ApplicationData", "ServerApplicationFact"), Map.entry("RuntimeData", "ServerRuntimeFact"),
                Map.entry("ApiAssetData", "ServerApiAssetFact"), Map.entry("ApiObservationData", "ServerApiObservationFact"),
                Map.entry("SecurityConfigData", "ServerSecurityConfigFact"), Map.entry("DependencyData", "ServerDependencyFact"),
                Map.entry("DangerousCallData", "ServerDangerousCallFact"), Map.entry("SecurityEventData", "ServerSecurityEventFact"),
                Map.entry("AgentHealthData", "ServerAgentHealthFact"), Map.entry("UserData", "ServerUserFact"),
                Map.entry("ActionData", "ServerActionFact"));
        factClasses.forEach((type, expected) -> assertEquals(expected,
                FactBuilder.buildFact(header, common, fact(type, Map.of("schemaVersion", 1))).getClass().getSimpleName()));

        RequestValidationService validation = new RequestValidationService("", "", true, 262144, 100, 8192);
        HttpServletRequest servlet = mock(HttpServletRequest.class);
        RequestVO oldType = request(List.of(fact("JvmRuntimeData", Map.of("schemaVersion", 1))));
        assertEquals("unsupported fact type", validation.validateEnvelope(
                "11111111-1111-1111-1111-111111111111-server-1-1.0.0", oldType, servlet, true));

        RequestVO oldPlatform = request(List.of(runtime()));
        ArrayList<String> legacyCommon = common(); legacyCommon.set(8, "jvm"); oldPlatform.setCommon(legacyCommon);
        assertEquals("unsupported platform", validation.validateEnvelope(
                "11111111-1111-1111-1111-111111111111-jvm-1-1.0.0", oldPlatform, servlet, true));
    }

    @Test
    void enrollmentAcceptsServerAndRejectsLegacyJvmDeviceType() throws Exception {
        EnrollmentTokenService tokens = mock(EnrollmentTokenService.class);
        when(tokens.issue("key-1", "SERVER", Duration.ofSeconds(60))).thenReturn("server-token");
        SecureEnrollmentController controller = new SecureEnrollmentController(tokens, "admin-token");
        assertEquals("server-token", controller.issue("admin-token",
                new SecureEnrollmentController.EnrollmentRequest("key-1", "SERVER", 60)).get("token"));
        assertThrows(ResponseStatusException.class, () -> controller.issue("admin-token",
                new SecureEnrollmentController.EnrollmentRequest("key-1", "JVM", 60)));
    }

    @Test
    void exposesOnlyServerCheckpointTopicsForTheServerContract() {
        assertEquals(List.of("server_fact_start", "server_fact_application", "server_fact_runtime",
                        "server_fact_api_asset", "server_fact_api_observation", "server_fact_security_config",
                        "server_fact_dependency", "server_fact_dangerous_call", "server_fact_security_event",
                        "server_fact_agent_health", "server_fact_user", "server_fact_action"),
                List.of(TopicDefine.TOPIC_SERVER_START_FACT, TopicDefine.TOPIC_SERVER_APPLICATION_FACT,
                        TopicDefine.TOPIC_SERVER_RUNTIME_FACT, TopicDefine.TOPIC_SERVER_API_ASSET_FACT,
                        TopicDefine.TOPIC_SERVER_API_OBSERVATION_FACT, TopicDefine.TOPIC_SERVER_SECURITY_CONFIG_FACT,
                        TopicDefine.TOPIC_SERVER_DEPENDENCY_FACT, TopicDefine.TOPIC_SERVER_DANGEROUS_CALL_FACT,
                        TopicDefine.TOPIC_SERVER_SECURITY_EVENT_FACT, TopicDefine.TOPIC_SERVER_AGENT_HEALTH_FACT,
                        TopicDefine.TOPIC_SERVER_USER_FACT, TopicDefine.TOPIC_SERVER_ACTION_FACT));
    }

    private static RequestVO request(List<Map<String, Object>> values) {
        ArrayList<Map<String, Object>> facts = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, Object> fact = new LinkedHashMap<>(values.get(index));
            fact.put("messageId", "server-message-" + (index + 2)); fact.put("observedAt", System.currentTimeMillis()); fact.put("sequence", index + 2L);
            facts.add(fact);
        }
        RequestMeta meta = new RequestMeta(); meta.setProtocolVersion(1); meta.setAppKeyId("key-1"); meta.setDetectorVersion("synap-server-agent-1.0.0");
        meta.setBatchId("server-batch-1"); meta.setSessionId("1700000000000"); meta.setAttempt(1); meta.setCreatedAt(System.currentTimeMillis());
        RequestVO request = new RequestVO(); request.setCommon(common()); request.setMeta(meta); request.setData(facts); return request;
    }

    private static ArrayList<String> common() {
        return new ArrayList<>(Arrays.asList("", "11111111-1111-1111-1111-111111111111", "1700000000000", "1.0.0", "1",
                "demo", "demo", "1.0.0", "server", "Eclipse Adoptium", "OpenJDK", "Linux", "6.1", "", "192.0.2.10", "",
                "0", "0", "", "", "", "", "", "2026-08-12T00:00:00Z"));
    }

    private static Map<String, Object> application() {
        return fact("ApplicationData", Map.of("schemaVersion", 1, "javaVersion", "17.0.12", "javaVendor", "Eclipse Adoptium",
                "vmName", "OpenJDK 64-Bit Server VM", "osName", "Linux", "osArch", "amd64", "processors", 8,
                "pid", 42, "capabilityMode", "full", "inputArguments", List.of("-Xmx512m")));
    }
    private static Map<String, Object> runtime() {
        return fact("RuntimeData", Map.ofEntries(Map.entry("schemaVersion", 1), Map.entry("heapUsed", 1024),
                Map.entry("heapCommitted", 2048), Map.entry("nonHeapUsed", 256), Map.entry("threadCount", 12),
                Map.entry("peakThreadCount", 15), Map.entry("deadlockedThreadCount", 0), Map.entry("loadedClassCount", 1000),
                Map.entry("totalLoadedClassCount", 1100), Map.entry("uptimeMs", 5000),
                Map.entry("garbageCollectors", List.of(Map.of("name", "G1 Young Generation", "count", 3, "timeMs", 4)))));
    }
    private static Map<String, Object> apiAsset() {
        return fact("ApiAssetData", Map.of("schemaVersion", 1, "assets", List.of(Map.of("assetId", "abc", "method", "GET",
                "route", "/users/{id}", "framework", "spring-mvc", "authorization", "pre-authorize"))));
    }
    private static Map<String, Object> fact(String type, Map<String, Object> payload) {
        Map<String, Object> result = new LinkedHashMap<>(payload); result.put("type", type); result.put("buildDate", "2026-08-12T00:00:00Z"); return result;
    }
}

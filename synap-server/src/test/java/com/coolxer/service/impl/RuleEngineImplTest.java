package com.coolxer.service.impl;

import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.model.fact.Header;
import com.coolxer.model.fact.host.HostAuditFact;
import com.coolxer.service.PunishConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class RuleEngineImplTest {

    @TempDir
    Path tempDir;

    @Test
    void successfulRuleReturnsCheckpoint() throws Exception {
        PunishConfigService punishConfigService = mock(PunishConfigService.class);
        KafkaTemplate<?, ?> kafkaTemplate = mock(KafkaTemplate.class);
        AbstractFact fact = fact();
        String scriptName = fact.getClass().getSimpleName() + "Rule.groovy";
        Files.writeString(tempDir.resolve(scriptName), "return new ArrayList()");
        when(punishConfigService.getAllPunishRules()).thenReturn(Collections.emptyList());

        RuleEngineImpl ruleEngine = ruleEngine(punishConfigService, kafkaTemplate, tempDir);
        Checkpoint checkpoint = ruleEngine.run(fact);

        assertThat(checkpoint).isNotNull();
    }

    @Test
    void missingRuleReturnsNull() {
        PunishConfigService punishConfigService = mock(PunishConfigService.class);
        KafkaTemplate<?, ?> kafkaTemplate = mock(KafkaTemplate.class);
        AbstractFact fact = fact();

        RuleEngineImpl ruleEngine = ruleEngine(punishConfigService, kafkaTemplate, tempDir);
        Checkpoint checkpoint = ruleEngine.run(fact);

        assertThat(checkpoint).isNull();
    }

    @Test
    void auditTopicUsesGuidAndBatchIdAsKafkaKey() throws Exception {
        PunishConfigService punishConfigService = mock(PunishConfigService.class);
        KafkaTemplate kafkaTemplate = mock(KafkaTemplate.class);
        Files.writeString(tempDir.resolve("HostAuditFactRule.groovy"), "return new ArrayList()");
        when(punishConfigService.getAllPunishRules()).thenReturn(Collections.emptyList());
        Common common = new Common(); common.setGuid("host-guid"); common.setPlatform("host");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "AuditData"); data.put("buildDate", "2026-08-05 10:00:00");
        data.put("batchId", "d2719b37-9d9f-41a8-b1bc-9bdc6298c847");
        data.put("events", new ArrayList<>(List.of(new ArrayList<>(List.of(
                "bcd4f8a1d21d4395ad3c3815c01670aa", "2026-08-05T10:00:00Z", "device", "start", "success",
                "", "", "", "", "agent-host", "", "", "", "", "", "agent-host", "boot", "{}")))));
        HostAuditFact fact = new HostAuditFact("AuditData", new Header("a-b-c-d-e-host-1-2.0.0"), common, data);

        assertThat(ruleEngine(punishConfigService, kafkaTemplate, tempDir).run(fact)).isNotNull();
        verify(kafkaTemplate).send(eq("host_fact_audit"), eq("host-guid:d2719b37-9d9f-41a8-b1bc-9bdc6298c847"), any());
    }

    private RuleEngineImpl ruleEngine(PunishConfigService punishConfigService,
                                      KafkaTemplate<?, ?> kafkaTemplate,
                                      Path scriptDirectory) {
        RuleEngineImpl ruleEngine = new RuleEngineImpl(punishConfigService);
        ReflectionTestUtils.setField(
                ruleEngine,
                "groovyScriptPath",
                scriptDirectory.toString() + File.separator);
        ReflectionTestUtils.setField(ruleEngine, "kafkaTemplate", kafkaTemplate);
        return ruleEngine;
    }

    private AbstractFact fact() {
        Common common = new Common();
        common.setGuid("trace-1");
        common.setPlatform("android");
        return new TestFact(common);
    }

    private static class TestFact extends AbstractFact {

        private TestFact(Common common) {
            super("TestData", null, null, common);
        }
    }
}

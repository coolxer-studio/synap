package com.coolxer.service.impl;

import com.coolxer.model.checkpoint.Checkpoint;
import com.coolxer.model.fact.AbstractFact;
import com.coolxer.model.fact.Common;
import com.coolxer.service.PunishConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

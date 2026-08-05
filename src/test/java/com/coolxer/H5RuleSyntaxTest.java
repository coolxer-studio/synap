package com.coolxer;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.agenda.AgendaLevelEnum;
import com.coolxer.model.fact.h5.H5RuntimeFact;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class H5RuleSyntaxTest {
    @Test
    void allH5RulesCompile() throws Exception {
        Path ruleDirectory = Path.of("src/main/java/com/coolxer/rule");
        try (GroovyClassLoader classLoader = new GroovyClassLoader()) {
            try (var paths = Files.list(ruleDirectory)) {
                paths.filter(path -> path.getFileName().toString().startsWith("H5"))
                        .filter(path -> path.getFileName().toString().endsWith("Rule.groovy"))
                        .forEach(path -> assertThatCode(() -> classLoader.parseClass(path.toFile()))
                                .as(path.getFileName().toString()).doesNotThrowAnyException());
            }
        }
    }

    @Test
    void runtimeRuleAddsLowRiskCodeFormattingObservation() throws Exception {
        List<Agenda> formatted = runRuntimeRule(1);
        List<Agenda> compact = runRuntimeRule(0);

        assertThat(formatted).anySatisfy(agenda -> {
            assertThat(agenda.getTag()).isEqualTo("H5观察-代码格式化");
            assertThat(agenda.getSource()).isEqualTo("format:1");
            assertThat(agenda.getLevel()).isEqualTo(AgendaLevelEnum.LOW);
        });
        assertThat(compact).noneMatch(agenda -> "H5观察-代码格式化".equals(agenda.getTag()));
    }

    @SuppressWarnings("unchecked")
    private List<Agenda> runRuntimeRule(int format) throws Exception {
        H5RuntimeFact fact = mock(H5RuntimeFact.class);
        Map<String, Object> custom = new LinkedHashMap<>();
        custom.put("format", format);
        custom.put("getSpider", new ArrayList<>(List.of(0, 0, 0, 0)));
        custom.put("unknownFunctions", new ArrayList<String>());
        when(fact.getCustom()).thenReturn(custom);
        Binding binding = new Binding(Map.of("fact", fact));
        return (List<Agenda>) new GroovyShell(binding)
                .evaluate(Path.of("src/main/java/com/coolxer/rule/H5RuntimeFactRule.groovy").toFile());
    }
}

package com.coolxer;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.agenda.AgendaLevelEnum;
import com.coolxer.model.fact.wechat.WechatBehaviorFact;
import com.coolxer.model.fact.wechat.WechatRuntimeFact;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WechatRuleSyntaxTest {
    @Test
    void allWechatRulesCompile() throws Exception {
        Path rules = Path.of("src/main/java/com/coolxer/rule");
        try (GroovyClassLoader loader = new GroovyClassLoader(); var paths = Files.list(rules)) {
            paths.filter(path -> path.getFileName().toString().startsWith("Wechat"))
                    .filter(path -> path.getFileName().toString().endsWith("Rule.groovy"))
                    .forEach(path -> assertThatCode(() -> loader.parseClass(path.toFile()))
                            .as(path.getFileName().toString()).doesNotThrowAnyException());
        }
    }

    @Test
    void formattingIsLowObservationAndTwoRuntimeSignalsCanBeMedium() throws Exception {
        WechatRuntimeFact fact = mock(WechatRuntimeFact.class);
        when(fact.getData()).thenReturn(Map.of("codeFormatting", 1, "devtools", true,
                "enableDebug", true, "nonReleaseEnv", false, "automationScene", false, "scene", 1001));
        List<Agenda> agendas = evaluate("WechatRuntimeFactRule.groovy", fact);
        assertThat(agendas).anySatisfy(agenda -> {
            assertThat(agenda.getTag()).isEqualTo("微信观察-代码格式化");
            assertThat(agenda.getLevel()).isEqualTo(AgendaLevelEnum.LOW);
        });
        assertThat(agendas).anyMatch(agenda -> agenda.getTag().equals("微信观察-多信号运行环境异常")
                && agenda.getLevel() == AgendaLevelEnum.MEDIUM);
    }

    @Test
    void behaviorNeedsMinimumSamplesAndTwoAnomalyClasses() throws Exception {
        WechatBehaviorFact fact = mock(WechatBehaviorFact.class);
        when(fact.getData()).thenReturn(Map.of("sampleCount", 20, "intervalVariance", 0,
                "speedVariance", 0, "untrustedCount", 0, "impossibleCount", 0, "directionChanges", 4));
        List<Agenda> agendas = evaluate("WechatBehaviorFactRule.groovy", fact);
        assertThat(agendas).anyMatch(agenda -> agenda.getTag().equals("微信观察-自动化行为")
                && agenda.getLevel() == AgendaLevelEnum.MEDIUM);
    }

    @SuppressWarnings("unchecked")
    private List<Agenda> evaluate(String script, Object fact) throws Exception {
        return (List<Agenda>) new GroovyShell(new Binding(Map.of("fact", fact)))
                .evaluate(Path.of("src/main/java/com/coolxer/rule", script).toFile());
    }
}

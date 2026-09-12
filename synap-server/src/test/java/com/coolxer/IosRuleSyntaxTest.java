package com.coolxer;

import com.coolxer.model.agenda.Agenda;
import com.coolxer.model.fact.ios.IosDeviceFact;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IosRuleSyntaxTest {
    @Test
    void deviceRuleReadsHwModelFromSysctlMap() throws Exception {
        IosDeviceFact fact = mock(IosDeviceFact.class);
        IosDeviceFact.Cpu cpu = mock(IosDeviceFact.Cpu.class);
        IosDeviceFact.Device device = mock(IosDeviceFact.Device.class);
        when(cpu.getType()).thenReturn("arm64");
        when(device.getSimulator()).thenReturn(true);
        when(fact.getCpu()).thenReturn(cpu);
        when(fact.getDevice()).thenReturn(device);
        when(fact.getSysctl()).thenReturn(Map.of("hwModel", "Mac15,3"));

        List<Agenda> agendas = evaluate(fact);

        assertThat(agendas).anyMatch(agenda -> "macos".equals(agenda.getTag())
                && "Model:Mac15,3".equals(agenda.getSource()));
        assertThat(agendas).anyMatch(agenda -> "simulator".equals(agenda.getTag()));
    }

    @SuppressWarnings("unchecked")
    private List<Agenda> evaluate(IosDeviceFact fact) throws Exception {
        return (List<Agenda>) new GroovyShell(new Binding(Map.of("fact", fact)))
                .evaluate(Path.of("src/main/java/com/coolxer/rule/IosDeviceFactRule.groovy").toFile());
    }
}

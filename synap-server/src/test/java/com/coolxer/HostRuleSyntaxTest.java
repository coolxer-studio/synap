package com.coolxer;

import groovy.lang.GroovyClassLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;

class HostRuleSyntaxTest {
    @Test
    void allHostRulesCompile() throws Exception {
        Path ruleDirectory = Path.of("src/main/java/com/coolxer/rule");
        try (GroovyClassLoader classLoader = new GroovyClassLoader()) {
            try (var paths = Files.list(ruleDirectory)) {
                paths.filter(path -> path.getFileName().toString().startsWith("Host"))
                        .filter(path -> path.getFileName().toString().endsWith("Rule.groovy"))
                        .forEach(path -> assertThatCode(() -> classLoader.parseClass(path.toFile()))
                                .as(path.getFileName().toString()).doesNotThrowAnyException());
            }
        }
    }
}

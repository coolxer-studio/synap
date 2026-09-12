package com.coolxer;

import org.junit.jupiter.api.Test;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ServerRuleSyntaxTest {
    @Test
    void allServerRulesCompile() throws Exception {
        Path rules = Path.of("src/main/java/com/coolxer/rule");
        try (var paths = Files.list(rules)) {
            List<Path> serverRules = paths.filter(path -> path.getFileName().toString().startsWith("Server"))
                    .filter(path -> path.getFileName().toString().endsWith("Rule.groovy")).toList();
            assertThat(serverRules).hasSize(12);
            serverRules.forEach(path -> assertThatCode(() -> {
                        assertThat(Files.readString(path).toLowerCase()).doesNotContain("jvm");
                        CompilationUnit unit = new CompilationUnit(); unit.addSource(path.toFile()); unit.compile(Phases.CONVERSION);
                    })
                    .as(path.getFileName().toString()).doesNotThrowAnyException());
        }
    }
}

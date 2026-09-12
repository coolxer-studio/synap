package com.coolxer.synap.javaagent;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaAgentForkIT {
    @Test
    void shadedAgentStartsInAForkedJvmAndInterceptsProcessBuilder() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Path agent = Files.list(Path.of("target"))
                .filter(path -> path.getFileName().toString().matches("agent-springboot-javaagent-.*-agent\\.jar"))
                .findFirst().orElseThrow();
        String separator = System.getProperty("path.separator");
        String classpath = Path.of("target", "test-classes").toAbsolutePath() + separator
                + Path.of("..", "agent-springboot-bridge", "target", "classes").toAbsolutePath();
        Process process = new ProcessBuilder(java.toString(), "-Dsynap.agent.debug=true",
                "-Dcom.coolxer.synap.shaded.bytebuddy.experimental=true", "-javaagent:" + agent.toAbsolutePath(), "-cp", classpath,
                JavaAgentSmokeApplication.class.getName()).redirectErrorStream(true).start();
        assertTrue(process.waitFor(20, TimeUnit.SECONDS));
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        assertTrue(output.contains("SYNAP_JAVAAGENT_INSTRUMENTED"), output);
    }
}

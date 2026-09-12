package com.coolxer.synap.testkit;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SensitiveDataAssertions {
    private SensitiveDataAssertions() {}
    public static void assertTreeDoesNotContain(Path root, String canary) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Assertions.assertFalse(Files.readString(path).contains(canary), () -> "sensitive canary found in " + path);
            }
        }
    }
}

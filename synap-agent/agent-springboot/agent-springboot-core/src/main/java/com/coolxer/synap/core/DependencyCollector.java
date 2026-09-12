package com.coolxer.synap.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

final class DependencyCollector {
    private DependencyCollector() {}

    static List<Map<String, Object>> collect() {
        List<Map<String, Object>> values = new ArrayList<>();
        String[] entries = System.getProperty("java.class.path", "").split(System.getProperty("path.separator"));
        for (String entry : entries) {
            Path path = Path.of(entry);
            if (!Files.isRegularFile(path) || !entry.endsWith(".jar")) continue;
            Map<String, Object> dependency = inspect(path);
            if (dependency != null) values.add(dependency);
            inspectNested(path, values);
            if (values.size() >= 2_000) break;
        }
        values.sort(Comparator.comparing(value -> String.valueOf(value.get("purl"))));
        return values;
    }

    private static Map<String, Object> inspect(Path path) {
        try (JarFile jar = new JarFile(path.toFile())) {
            String group = "unknown", artifact = stripVersion(path.getFileName().toString()), version = "unknown";
            var entries = jar.stream().filter(entry -> entry.getName().endsWith("pom.properties")).limit(1).toList();
            if (!entries.isEmpty()) {
                Properties properties = new Properties();
                try (InputStream input = jar.getInputStream(entries.get(0))) { properties.load(input); }
                group = properties.getProperty("groupId", group); artifact = properties.getProperty("artifactId", artifact); version = properties.getProperty("version", version);
            } else if (jar.getManifest() != null) {
                String manifestVersion = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
                String manifestTitle = jar.getManifest().getMainAttributes().getValue("Implementation-Title");
                if (manifestVersion != null && !manifestVersion.isBlank()) version = manifestVersion;
                if (manifestTitle != null && manifestTitle.matches("[A-Za-z0-9_.-]{1,128}")) artifact = manifestTitle;
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("purl", "pkg:maven/" + encode(group) + "/" + encode(artifact) + "@" + encode(version));
            value.put("group", group); value.put("artifact", artifact); value.put("version", version);
            value.put("sha256", digest(path)); value.put("loaded", true); value.put("direct", true);
            return value;
        } catch (Exception ignored) { return null; }
    }

    private static void inspectNested(Path path, List<Map<String, Object>> target) {
        try (JarFile outer = new JarFile(path.toFile())) {
            for (var entry : outer.stream().filter(value -> value.getName().startsWith("BOOT-INF/lib/") && value.getName().endsWith(".jar")).toList()) {
                if (target.size() >= 2_000) return;
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                String group = "unknown", artifact = stripVersion(Path.of(entry.getName()).getFileName().toString()), version = "unknown";
                try (InputStream raw = outer.getInputStream(entry); DigestInputStream hashing = new DigestInputStream(raw, digest); JarInputStream nested = new JarInputStream(hashing)) {
                    java.util.jar.JarEntry nestedEntry;
                    while ((nestedEntry = nested.getNextJarEntry()) != null) {
                        if (nestedEntry.getName().endsWith("pom.properties") && nestedEntry.getSize() <= 64 * 1024) {
                            Properties properties = new Properties(); properties.load(nested);
                            group = properties.getProperty("groupId", group); artifact = properties.getProperty("artifactId", artifact); version = properties.getProperty("version", version);
                        } else nested.transferTo(java.io.OutputStream.nullOutputStream());
                    }
                }
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("purl", "pkg:maven/" + encode(group) + "/" + encode(artifact) + "@" + encode(version));
                value.put("group", group); value.put("artifact", artifact); value.put("version", version);
                value.put("sha256", HexFormat.of().formatHex(digest.digest())); value.put("loaded", true); value.put("direct", false);
                target.add(value);
            }
        } catch (Exception ignored) { }
    }

    private static String digest(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count); }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static String stripVersion(String value) { return value.replaceFirst("\\.jar$", "").replaceFirst("-[0-9].*$", ""); }
    private static String encode(String value) { return value.replace("%", "%25").replace("/", "%2F").replace(" ", "%20"); }
}

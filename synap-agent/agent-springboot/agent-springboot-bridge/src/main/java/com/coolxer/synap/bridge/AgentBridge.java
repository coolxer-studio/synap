package com.coolxer.synap.bridge;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Bootstrap-visible bridge used by application code and bytecode advice. */
public final class AgentBridge {
    public interface Sink {
        void accept(BridgeEvent event);
        default void flush() {}
        default Map<String, Object> health() { return Map.of("status", "running"); }
    }

    public enum Decision { ALLOW, BLOCK }

    private static final int EARLY_LIMIT = 256;
    private static final AtomicReference<Sink> SINK = new AtomicReference<>();
    private static final ArrayDeque<BridgeEvent> EARLY = new ArrayDeque<>();
    private static final ThreadLocal<RequestSecurityContext> REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<Integer> INTERNAL_OPERATION_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SINK_EVALUATION_ACTIVE = new ThreadLocal<>();
    private static volatile boolean protect;
    private static volatile Set<String> enabledRules = Set.of();

    private AgentBridge() {}

    public static synchronized void install(Sink sink) {
        if (sink == null) return;
        SINK.set(sink);
        while (!EARLY.isEmpty()) safeAccept(sink, EARLY.removeFirst());
    }

    public static synchronized void emit(BridgeEvent event) {
        Sink sink = SINK.get();
        if (sink != null) {
            safeAccept(sink, event);
        } else {
            if (EARLY.size() == EARLY_LIMIT) EARLY.removeFirst();
            EARLY.addLast(event);
        }
    }

    public static void configureProtection(boolean enabled, Set<String> ruleIds) {
        protect = enabled;
        enabledRules = ruleIds == null ? Set.of() : Set.copyOf(ruleIds);
    }

    public static RequestSecurityContext openRequest(String assetId, String method, String route,
                                                     boolean authenticated, String sourceIpHash,
                                                     List<String> localInputs) {
        RequestSecurityContext context = new RequestSecurityContext(UUID.randomUUID().toString(), assetId,
                method, route, authenticated, sourceIpHash, sanitizeInputs(localInputs));
        REQUEST.set(context);
        return context;
    }

    public static RequestSecurityContext currentRequest() { return REQUEST.get(); }
    public static void closeRequest() { REQUEST.remove(); }

    /** Suppresses sink instrumentation while the agent performs its own I/O. */
    public static void enterInternalOperation() {
        Integer depth = INTERNAL_OPERATION_DEPTH.get();
        INTERNAL_OPERATION_DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void exitInternalOperation() {
        Integer depth = INTERNAL_OPERATION_DEPTH.get();
        if (depth == null || depth <= 1) INTERNAL_OPERATION_DEPTH.remove();
        else INTERNAL_OPERATION_DEPTH.set(depth - 1);
    }

    public static boolean internalOperationActive() {
        Integer depth = INTERNAL_OPERATION_DEPTH.get();
        return depth != null && depth > 0;
    }

    public static Decision beforeSink(String sinkType, String operation, Object[] arguments) {
        if (internalOperationActive() || Boolean.TRUE.equals(SINK_EVALUATION_ACTIVE.get())) {
            return Decision.ALLOW;
        }
        RequestSecurityContext request = REQUEST.get();
        if (request == null) return Decision.ALLOW;
        SINK_EVALUATION_ACTIVE.set(Boolean.TRUE);
        try {
            String target = summarizeArguments(arguments);
            String ruleId = ruleFor(sinkType, target, request);
            boolean correlated = correlated(target, request);
            boolean block = protect && correlated && ruleId != null && enabledRules.contains(ruleId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("schemaVersion", 1);
            data.put("sinkType", safe(sinkType, 64));
            data.put("operation", safe(operation, 160));
            data.put("targetDigest", sha256(target));
            data.put("stackFingerprint", stackFingerprint());
            data.put("correlated", correlated);
            data.put("confidence", correlated ? "high" : "low");
            data.put("ruleId", ruleId == null ? "SINK_OBSERVED_V1" : ruleId);
            data.put("action", block ? "blocked" : "observed");
            if (request != null) {
                data.put("correlationId", request.correlationId());
                data.put("assetId", request.assetId());
                data.put("authenticated", request.authenticated());
                data.put("sourceIpHash", request.sourceIpHash());
            }
            emit(BridgeEvent.of("DangerousCallData", data));
            return block ? Decision.BLOCK : Decision.ALLOW;
        } catch (Throwable ignored) {
            return Decision.ALLOW;
        } finally {
            SINK_EVALUATION_ACTIVE.remove();
        }
    }

    public static void flush() { Sink sink = SINK.get(); if (sink != null) try { sink.flush(); } catch (Throwable ignored) {} }
    public static Map<String, Object> health() { Sink sink = SINK.get(); return sink == null ? Map.of("status", "degraded") : sink.health(); }
    public static boolean installed() { return SINK.get() != null; }

    private static String ruleFor(String sinkType, String target, RequestSecurityContext request) {
        String kind = sinkType == null ? "" : sinkType.toUpperCase(Locale.ROOT);
        if ("COMMAND".equals(kind) && containsShellMeta(target)) return "COMMAND_INJECTION_V1";
        if ("HTTP".equals(kind) && isPrivateOrMetadataTarget(target)) return "SSRF_PRIVATE_TARGET_V1";
        if ("FILE".equals(kind) && (target.contains("../") || target.contains("..\\"))) return "PATH_TRAVERSAL_V1";
        if ("SQL".equals(kind) && correlated(target, request)) return "SQL_INJECTION_OBSERVED_V1";
        if ("JNDI".equals(kind)) return "JNDI_LOOKUP_OBSERVED_V1";
        if ("DESERIALIZATION".equals(kind)) return "DESERIALIZATION_OBSERVED_V1";
        if ("SPEL".equals(kind)) return "SPEL_EXECUTION_OBSERVED_V1";
        return null;
    }

    private static boolean correlated(String target, RequestSecurityContext request) {
        if (request == null || target == null) return false;
        String normalized = target.toLowerCase(Locale.ROOT);
        for (String input : request.localInputs()) {
            if (input.length() >= 3 && normalized.contains(input.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static List<String> sanitizeInputs(List<String> inputs) {
        if (inputs == null) return List.of();
        List<String> values = new ArrayList<>();
        for (String value : inputs) {
            if (value != null && !value.isBlank()) values.add(safe(value, 512));
            if (values.size() == 64) break;
        }
        return values;
    }

    private static String summarizeArguments(Object[] arguments) {
        if (arguments == null) return "";
        StringBuilder value = new StringBuilder();
        for (Object argument : arguments) {
            if (argument == null) continue;
            if (value.length() > 0) value.append(' ');
            if (argument.getClass().isArray() && argument instanceof Object[] array) {
                for (Object item : array) value.append(item == null ? "" : item).append(' ');
            } else value.append(argument);
            if (value.length() >= 2048) break;
        }
        return safe(value.toString(), 2048);
    }

    private static boolean containsShellMeta(String value) {
        return value.contains(";") || value.contains("&&") || value.contains("||") || value.contains("`")
                || value.contains("$(") || value.contains("\n") || value.contains("\r");
    }

    private static boolean isPrivateOrMetadataTarget(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("169.254.169.254") || lower.contains("metadata.google.internal")
                || lower.matches(".*(?:https?://)?(?:10\\.|127\\.|192\\.168\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.).*");
    }

    private static String stackFingerprint() {
        StringBuilder value = new StringBuilder();
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            String name = frame.getClassName();
            if (name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("com.coolxer.synap.")) continue;
            value.append(name).append('#').append(frame.getMethodName()).append(';');
            if (value.length() > 1024) break;
        }
        return sha256(value.toString());
    }

    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ignored) { return "unavailable"; }
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\r', ' ').replace('\n', ' ');
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static void safeAccept(Sink sink, BridgeEvent event) { try { sink.accept(event); } catch (Throwable ignored) {} }
}

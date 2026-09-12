package com.coolxer.synap.core;

import com.coolxer.synap.bridge.AgentBridge;
import com.coolxer.synap.bridge.BridgeEvent;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Single fail-open runtime shared by starter and javaagent. */
public final class AgentRuntime implements AgentBridge.Sink, AutoCloseable {
    private static volatile AgentRuntime INSTANCE;
    private final AgentConfiguration config;
    private final SpoolStore spool;
    private final SecureCommunicationClient transport;
    private final ScheduledExecutorService executor;
    private final String guid;
    private final long startId = System.currentTimeMillis();
    private final long startCreatedAt = System.currentTimeMillis();
    private final AtomicLong startAttempt = new AtomicLong();
    private final String startMessageId;
    private final String startBatchId;
    private final String code;
    private final List<String> common;
    private final AtomicLong sequence = new AtomicLong(1);
    private final AtomicBoolean startConfirmed = new AtomicBoolean();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final String capabilityMode;

    private AgentRuntime(AgentConfiguration config, String capabilityMode) throws Exception {
        this.config = config; this.capabilityMode = capabilityMode;
        this.spool = new SpoolStore(config.dataDir(), config.spoolMaxItems(), config.spoolMaxBytes());
        this.transport = new SecureCommunicationClient(config); this.guid = loadGuid(config.dataDir());
        this.startMessageId = UUID.nameUUIDFromBytes((guid + ":" + startId + ":start").getBytes(StandardCharsets.UTF_8)).toString();
        this.startBatchId = UUID.nameUUIDFromBytes((guid + ":" + startId + ":batch").getBytes(StandardCharsets.UTF_8)).toString();
        this.code = guid + "-server-" + config.appId() + "-" + AgentConfiguration.VERSION;
        this.common = common();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> { Thread thread = new Thread(runnable, "synap-server-agent-sender"); thread.setDaemon(true); return thread; });
    }

    public static synchronized AgentRuntime start(AgentConfiguration config, String capabilityMode) {
        if (INSTANCE != null) return INSTANCE;
        if (config == null || !config.enabled()) return null;
        if (!config.ready()) {
            System.err.println("[synap-agent] agent disabled: missing or invalid configuration: "
                    + String.join(", ", config.missingRequiredSettings()));
            return null;
        }
        try {
            AgentRuntime runtime = new AgentRuntime(config, capabilityMode == null ? "degraded" : capabilityMode);
            INSTANCE = runtime; AgentBridge.install(runtime);
            AgentBridge.configureProtection(config.mode() == AgentConfiguration.Mode.PROTECT, config.enabledRuleIds());
            runtime.executor.scheduleWithFixedDelay(runtime::sendLoop, 0, 2, TimeUnit.SECONDS);
            runtime.executor.scheduleWithFixedDelay(() -> runtime.accept(BridgeEvent.of("RuntimeData", JavaRuntimeCollector.runtime())),
                    config.observationWindowSeconds(), config.observationWindowSeconds(), TimeUnit.SECONDS);
            runtime.accept(BridgeEvent.of("ApplicationData", JavaRuntimeCollector.application(runtime.capabilityMode)));
            runtime.enqueueDependencies();
            Runtime.getRuntime().addShutdownHook(new Thread(runtime::close, "synap-server-agent-shutdown"));
            return runtime;
        } catch (Throwable error) {
            System.err.println("[synap-agent] agent disabled: " + error.getClass().getSimpleName());
            return null;
        }
    }

    public static AgentRuntime current() { return INSTANCE; }

    @Override public void accept(BridgeEvent event) {
        AgentBridge.enterInternalOperation();
        try {
            Map<String, Object> fact = new LinkedHashMap<>(event.data());
            fact.put("type", event.type()); fact.putIfAbsent("schemaVersion", 1); fact.put("buildDate", iso(event.observedAt()));
            fact.put("messageId", UUID.randomUUID().toString()); fact.put("observedAt", event.observedAt()); fact.put("sequence", sequence.incrementAndGet());
            enqueueFact(fact);
        } catch (Throwable ignored) { dropped.incrementAndGet(); } finally { AgentBridge.exitInternalOperation(); }
    }

    @Override public void flush() { executor.execute(this::sendLoop); }

    @Override public Map<String, Object> health() {
        AgentBridge.enterInternalOperation();
        try {
            Map<String, Object> value = new LinkedHashMap<>(); value.put("status", startConfirmed.get() ? "running" : "starting");
            value.put("capabilityMode", capabilityMode); value.put("queued", spool.queued()); value.put("deadLetters", spool.deadLetters());
            value.put("accepted", accepted.get()); value.put("rejected", rejected.get()); value.put("failures", failures.get()); value.put("dropped", dropped.get()); return Map.copyOf(value);
        } finally { AgentBridge.exitInternalOperation(); }
    }

    private void enqueueDependencies() {
        List<Map<String, Object>> dependencies = DependencyCollector.collect();
        for (int index = 0; index < dependencies.size(); index += 100) {
            accept(BridgeEvent.of("DependencyData", Map.of("schemaVersion", 1, "dependencies", dependencies.subList(index, Math.min(dependencies.size(), index + 100)))));
        }
    }

    private void enqueueFact(Map<String, Object> fact) throws IOException {
        String batchId = UUID.randomUUID().toString();
        Map<String, Object> meta = batchMeta(batchId, 0);
        Map<String, Object> record = new LinkedHashMap<>(); record.put("code", code); record.put("common", common); record.put("meta", meta); record.put("data", List.of(fact));
        spool.enqueue(record);
    }

    private void sendLoop() {
        AgentBridge.enterInternalOperation();
        try {
            if (!startConfirmed.get()) { sendStart(); if (!startConfirmed.get()) return; }
            SpoolStore.Item item = spool.next(); if (item == null) return;
            @SuppressWarnings("unchecked") Map<String, Object> meta = (Map<String, Object>) item.value().get("meta");
            meta.put("attempt", ((Number) meta.getOrDefault("attempt", 0)).intValue() + 1); spool.update(item);
            SecureCommunicationClient.Response response = transport.request("/attach/upload", code, JsonSupport.bytes(Map.of("common", common, "meta", meta, "data", item.value().get("data"))));
            if (response.status() == 429 || response.status() >= 500) { failures.incrementAndGet(); return; }
            if (response.status() >= 400) { rejected.incrementAndGet(); spool.reject(item); return; }
            Map<String, Object> body = JsonSupport.map(response.body());
            if (!Integer.valueOf(1).equals(((Number) body.getOrDefault("status", 0)).intValue())) { rejected.incrementAndGet(); spool.reject(item); return; }
            @SuppressWarnings("unchecked") Map<String, Object> ack = (Map<String, Object>) body.get("ack");
            @SuppressWarnings("unchecked") List<Map<String, Object>> data = (List<Map<String, Object>>) item.value().get("data");
            String messageId = String.valueOf(data.get(0).get("messageId"));
            if (acknowledged(ack, messageId)) { accepted.incrementAndGet(); spool.ack(item); }
            else if (retryable(ack, messageId)) failures.incrementAndGet();
            else { rejected.incrementAndGet(); spool.reject(item); }
        } catch (SecureCommunicationClient.TransportException error) {
            failures.incrementAndGet();
            if (error.unknownSession()) transport.closeSession();
        } catch (Throwable ignored) { failures.incrementAndGet(); } finally { AgentBridge.exitInternalOperation(); }
    }

    private void sendStart() throws Exception {
        Map<String, Object> meta = batchMeta(startBatchId, Math.toIntExact(Math.min(Integer.MAX_VALUE, startAttempt.incrementAndGet())));
        meta.put("createdAt", startCreatedAt); meta.put("messageId", startMessageId); meta.put("observedAt", startId); meta.put("sequence", 1L);
        SecureCommunicationClient.Response response = transport.request("/attach/start", code, JsonSupport.bytes(Map.of("common", common, "meta", meta)));
        if (response.status() / 100 == 2) {
            Map<String, Object> body = JsonSupport.map(response.body()); if (((Number) body.getOrDefault("status", 0)).intValue() == 1) startConfirmed.set(true);
        }
    }

    private Map<String, Object> batchMeta(String batchId, int attempt) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("protocolVersion", 1); value.put("appKeyId", config.appKeyId());
        value.put("detectorVersion", "synap-server-agent-" + AgentConfiguration.VERSION); value.put("batchId", batchId); value.put("sessionId", String.valueOf(startId));
        value.put("attempt", attempt); value.put("createdAt", System.currentTimeMillis()); return value;
    }

    private List<String> common() {
        List<String> values = new ArrayList<>(24);
        values.add(""); values.add(guid); values.add(String.valueOf(startId)); values.add(AgentConfiguration.VERSION); values.add(String.valueOf(config.appId()));
        values.add(config.appName()); values.add(config.appName()); values.add(config.appVersion()); values.add("server"); values.add(System.getProperty("java.vendor", ""));
        values.add(System.getProperty("java.vm.name", "")); values.add(System.getProperty("os.name", "")); values.add(System.getProperty("os.version", "")); values.add("");
        values.add(localAddress()); values.add(""); values.add("0"); values.add("0"); values.add(""); values.add(""); values.add(""); values.add(""); values.add(""); values.add(iso(System.currentTimeMillis()));
        return List.copyOf(values);
    }

    static boolean acknowledged(Map<String, Object> ack, String id) {
        return ack != null && (contains(first(ack, "acceptedMessageIds", "accepted_message_ids"), id)
                || contains(first(ack, "duplicateMessageIds", "duplicate_message_ids"), id));
    }
    private static boolean retryable(Map<String, Object> ack, String id) {
        if (ack == null || !(first(ack, "rejectedItems", "rejected_items") instanceof List<?> values)) return false;
        for (Object value : values) if (value instanceof Map<?, ?> item
                && id.equals(String.valueOf(first(item, "messageId", "message_id")))
                && Boolean.TRUE.equals(item.get("retryable"))) return true;
        return false;
    }
    private static Object first(Map<?, ?> values, String camelCase, String snakeCase) {
        Object value = values.get(camelCase); return value == null ? values.get(snakeCase) : value;
    }
    private static boolean contains(Object values, String id) { return values instanceof List<?> list && list.stream().anyMatch(id::equals); }
    private static String loadGuid(Path root) throws IOException {
        Path path = root.resolve("guid.txt"); if (Files.exists(path)) { String value = Files.readString(path).trim(); if (value.matches("[0-9a-fA-F-]{36}")) return value; }
        String value = UUID.randomUUID().toString(); SpoolStore.atomicWrite(path, value.getBytes(StandardCharsets.UTF_8)); return value;
    }
    private static String localAddress() { try { return InetAddress.getLocalHost().getHostAddress(); } catch (Exception ignored) { return ""; } }
    private static String iso(long epoch) { return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.UTC)); }

    @Override public synchronized void close() {
        if (!executor.isShutdown()) {
            flush(); executor.shutdown();
            try { executor.awaitTermination(2, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        transport.close();
    }
}

package com.coolxer.synap.core;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JavaRuntimeCollector {
    private JavaRuntimeCollector() {}

    static Map<String, Object> application(String capabilityMode) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("javaVersion", System.getProperty("java.version", "unknown"));
        data.put("javaVendor", System.getProperty("java.vendor", "unknown"));
        data.put("vmName", System.getProperty("java.vm.name", "unknown"));
        data.put("osName", System.getProperty("os.name", "unknown"));
        data.put("osArch", System.getProperty("os.arch", "unknown"));
        data.put("processors", Runtime.getRuntime().availableProcessors());
        data.put("pid", ProcessHandle.current().pid());
        data.put("capabilityMode", capabilityMode);
        data.put("startTime", ManagementFactory.getRuntimeMXBean().getStartTime());
        data.put("inputArguments", safeArguments());
        data.put("runtimeAgents", safeArguments().stream().filter(value -> value.startsWith("-javaagent:") || value.startsWith("-agentlib:"))
                .map(value -> value.substring(0, Math.min(value.length(), 256))).toList());
        data.put("cgroup", cgroup());
        return data;
    }

    static Map<String, Object> runtime() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("heapUsed", memory.getHeapMemoryUsage().getUsed());
        data.put("heapCommitted", memory.getHeapMemoryUsage().getCommitted());
        data.put("nonHeapUsed", memory.getNonHeapMemoryUsage().getUsed());
        data.put("threadCount", threads.getThreadCount());
        data.put("peakThreadCount", threads.getPeakThreadCount());
        long[] deadlocked = threads.findDeadlockedThreads();
        data.put("deadlockedThreadCount", deadlocked == null ? 0 : deadlocked.length);
        data.put("loadedClassCount", classes.getLoadedClassCount());
        data.put("totalLoadedClassCount", classes.getTotalLoadedClassCount());
        data.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());
        data.put("daemonThreadCount", threads.getDaemonThreadCount());
        data.put("unloadedClassCount", classes.getUnloadedClassCount());
        java.lang.management.OperatingSystemMXBean operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        data.put("systemLoadAverage", operatingSystem.getSystemLoadAverage());
        if (operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended) {
            data.put("processCpuLoad", extended.getProcessCpuLoad()); data.put("systemCpuLoad", extended.getCpuLoad());
        }
        if (operatingSystem instanceof com.sun.management.UnixOperatingSystemMXBean unix) {
            data.put("openFileDescriptorCount", unix.getOpenFileDescriptorCount()); data.put("maxFileDescriptorCount", unix.getMaxFileDescriptorCount());
        }
        List<Map<String, Object>> collectors = ManagementFactory.getGarbageCollectorMXBeans().stream().map(JavaRuntimeCollector::gc).toList();
        data.put("garbageCollectors", collectors);
        return data;
    }

    private static Map<String, Object> gc(GarbageCollectorMXBean value) {
        return Map.of("name", value.getName(), "collections", value.getCollectionCount(), "collectionTimeMs", value.getCollectionTime());
    }
    private static List<String> safeArguments() {
        List<String> result = new ArrayList<>(); boolean skipNext = false;
        for (String value : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (skipNext) { skipNext = false; continue; }
            String lower = value.toLowerCase();
            if (lower.matches(".*(password|token|secret|credential|authorization|cookie|private.?key).*")) {
                skipNext = !value.contains("="); continue;
            }
            result.add(value.length() > 256 ? value.substring(0, 256) : value);
            if (result.size() == 128) break;
        }
        return List.copyOf(result);
    }
    private static String cgroup() {
        try {
            Path path = Path.of("/proc/self/cgroup"); if (!Files.isRegularFile(path)) return "";
            String value = Files.readString(path).replace('\r', ' ').replace('\n', ';'); return value.substring(0, Math.min(value.length(), 1024));
        } catch (Exception ignored) { return ""; }
    }
}

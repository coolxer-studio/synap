package com.coolxer.synap.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SpoolStore {
    record Item(Path path, Map<String, Object> value) {}
    private final Path queue;
    private final Path dead;
    private final int maxItems;
    private final long maxBytes;

    SpoolStore(Path root, int maxItems, long maxBytes) throws IOException {
        this.queue = root.resolve("spool"); this.dead = root.resolve("dead-letter");
        this.maxItems = maxItems; this.maxBytes = maxBytes;
        secureDirectory(root); secureDirectory(queue); secureDirectory(dead); prune(queue); prune(dead);
    }

    synchronized void enqueue(Map<String, Object> value) throws IOException {
        byte[] encoded = JsonSupport.bytes(value);
        if (encoded.length > 256 * 1024 || encoded.length > maxBytes) throw new IOException("fact envelope exceeds spool limit");
        String name = String.format("%020d-%s.json", System.nanoTime(), UUID.randomUUID());
        atomicWrite(queue.resolve(name), encoded); prune(queue);
    }

    synchronized Item next() throws IOException {
        List<Path> values = files(queue);
        if (values.isEmpty()) return null;
        Path path = values.get(0);
        try { return new Item(path, JsonSupport.map(Files.readAllBytes(path))); }
        catch (IOException invalid) { Files.move(path, dead.resolve(path.getFileName()), StandardCopyOption.REPLACE_EXISTING); throw invalid; }
    }

    synchronized void update(Item item) throws IOException { atomicWrite(item.path(), JsonSupport.bytes(item.value())); }
    synchronized void ack(Item item) throws IOException { Files.deleteIfExists(item.path()); }
    synchronized void reject(Item item) throws IOException { Files.move(item.path(), dead.resolve(item.path().getFileName()), StandardCopyOption.REPLACE_EXISTING); prune(dead); }
    synchronized long queued() { try { return files(queue).size(); } catch (IOException ignored) { return -1; } }
    synchronized long deadLetters() { try { return files(dead).size(); } catch (IOException ignored) { return -1; } }

    private void prune(Path directory) throws IOException {
        List<Path> files = files(directory); long total = 0;
        for (Path path : files) total += Files.size(path);
        Instant cutoff = Instant.now().minus(Duration.ofDays(7));
        while (!files.isEmpty() && (files.size() > maxItems || total > maxBytes || Files.getLastModifiedTime(files.get(0)).toInstant().isBefore(cutoff))) {
            Path oldest = files.remove(0); total -= Files.size(oldest); Files.deleteIfExists(oldest);
        }
    }

    private static List<Path> files(Path directory) throws IOException {
        List<Path> values = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) { for (Path path : stream) values.add(path); }
        values.sort(Comparator.comparing(path -> path.getFileName().toString())); return values;
    }

    static void atomicWrite(Path path, byte[] value) throws IOException {
        secureDirectory(path.getParent()); Path temporary = Files.createTempFile(path.getParent(), ".tmp-", ".json");
        try { Files.write(temporary, value); secureFile(temporary); Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temporary); }
    }
    static void secureDirectory(Path path) throws IOException { Files.createDirectories(path); try { Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)); } catch (UnsupportedOperationException ignored) {} }
    static void secureFile(Path path) throws IOException { try { Files.setPosixFilePermissions(path, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); } catch (UnsupportedOperationException ignored) {} }
}

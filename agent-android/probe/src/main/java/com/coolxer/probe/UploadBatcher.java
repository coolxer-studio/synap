package com.coolxer.probe;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class UploadBatcher {
    private static final String PREFIX = "{\"common\":";
    private static final String META_PREFIX = ",\"meta\":";
    private static final String DATA_PREFIX = ",\"data\":[";
    private static final String SUFFIX = "]}";

    private UploadBatcher() { }

    static Result split(String commonJson, List<String> messages, int maxBodyBytes,
                        MetaFactory metaFactory) {
        if (commonJson == null || messages == null || maxBodyBytes <= 0 || metaFactory == null) {
            throw new IllegalArgumentException("Invalid upload batching arguments");
        }
        List<Batch> batches = new ArrayList<>();
        List<String> oversized = new ArrayList<>();
        List<String> current = new ArrayList<>();
        BatchMeta batchMeta = metaFactory.create(0);
        String bodyPrefix = bodyPrefix(commonJson, batchMeta);
        int fixedBytes = utf8Length(bodyPrefix) + utf8Length(SUFFIX);
        int currentBytes = fixedBytes;

        for (String message : messages) {
            int messageBytes = utf8Length(message);
            if (fixedBytes + messageBytes > maxBodyBytes) {
                oversized.add(message);
                continue;
            }
            int separatorBytes = current.isEmpty() ? 0 : 1;
            if (!current.isEmpty()
                    && currentBytes + separatorBytes + messageBytes > maxBodyBytes) {
                batches.add(new Batch(batchMeta.getBatchId(), bodyPrefix, current));
                current = new ArrayList<>();
                batchMeta = metaFactory.create(batches.size());
                bodyPrefix = bodyPrefix(commonJson, batchMeta);
                fixedBytes = utf8Length(bodyPrefix) + utf8Length(SUFFIX);
                currentBytes = fixedBytes;
                separatorBytes = 0;
                if (fixedBytes + messageBytes > maxBodyBytes) {
                    oversized.add(message);
                    continue;
                }
            }
            current.add(message);
            currentBytes += separatorBytes + messageBytes;
        }
        if (!current.isEmpty()) {
            batches.add(new Batch(batchMeta.getBatchId(), bodyPrefix, current));
        }
        return new Result(batches, oversized);
    }

    private static String bodyPrefix(String commonJson, BatchMeta batchMeta) {
        if (batchMeta == null || batchMeta.getBatchId() == null || batchMeta.getJson() == null) {
            throw new IllegalArgumentException("Upload metadata must include a batch id and JSON");
        }
        return PREFIX + commonJson + META_PREFIX + batchMeta.getJson() + DATA_PREFIX;
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    static final class Batch {
        private final String batchId;
        private final List<String> messages;
        private final String body;

        private Batch(String batchId, String bodyPrefix, List<String> messages) {
            this.batchId = batchId;
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
            this.body = bodyPrefix + String.join(",", messages) + SUFFIX;
        }

        String getBatchId() {
            return batchId;
        }

        List<String> getMessages() {
            return messages;
        }

        String getBody() {
            return body;
        }
    }

    interface MetaFactory {
        BatchMeta create(int batchIndex);
    }

    static final class BatchMeta {
        private final String batchId;
        private final String json;

        BatchMeta(String batchId, String json) {
            this.batchId = batchId;
            this.json = json;
        }

        String getBatchId() {
            return batchId;
        }

        String getJson() {
            return json;
        }
    }

    static final class Result {
        private final List<Batch> batches;
        private final List<String> oversizedMessages;

        private Result(List<Batch> batches, List<String> oversizedMessages) {
            this.batches = Collections.unmodifiableList(new ArrayList<>(batches));
            this.oversizedMessages = Collections.unmodifiableList(
                    new ArrayList<>(oversizedMessages));
        }

        List<Batch> getBatches() {
            return batches;
        }

        List<String> getOversizedMessages() {
            return oversizedMessages;
        }
    }
}

package com.coolxer.probe;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UploadBatcherTest {
    @Test
    public void splitsByUtf8BytesAndPreservesOrder() {
        List<String> messages = Arrays.asList(
                "{\"id\":1,\"value\":\"中文一\"}",
                "{\"id\":2,\"value\":\"中文二\"}",
                "{\"id\":3,\"value\":\"中文三\"}");
        int maxBytes = ("{\"common\":[],\"meta\":{\"batchId\":\"batch-0\"},\"data\":["
                + messages.get(0) + "," + messages.get(1) + "]}")
                .getBytes(StandardCharsets.UTF_8).length;

        UploadBatcher.Result result = UploadBatcher.split("[]", messages, maxBytes,
                index -> meta("batch-" + index));

        assertEquals(2, result.getBatches().size());
        assertEquals(messages.subList(0, 2), result.getBatches().get(0).getMessages());
        assertEquals(messages.subList(2, 3), result.getBatches().get(1).getMessages());
        for (UploadBatcher.Batch batch : result.getBatches()) {
            assertTrue(batch.getBody().getBytes(StandardCharsets.UTF_8).length <= maxBytes);
        }
    }

    @Test
    public void reportsSingleMessageThatCannotFit() {
        List<String> messages = Arrays.asList("{\"value\":\"too-large\"}", "{\"ok\":true}");

        int maxBytes = ("{\"common\":[],\"meta\":{\"batchId\":\"batch-0\"},\"data\":["
                + messages.get(1) + "]}").getBytes(StandardCharsets.UTF_8).length;
        UploadBatcher.Result result = UploadBatcher.split("[]", messages, maxBytes,
                index -> meta("batch-" + index));

        assertEquals(Arrays.asList(messages.get(0)), result.getOversizedMessages());
        assertEquals(Arrays.asList(messages.get(1)), result.getBatches().get(0).getMessages());
    }

    @Test
    public void requeuedMessagesStayAheadOfNewMessages() {
        ThreadSafeQueue.pullMsg();
        ThreadSafeQueue.pushMsg("new");
        ThreadSafeQueue.pushFront(Arrays.asList("old-1", "old-2"));

        ArrayList<String> messages = ThreadSafeQueue.pullMsg();

        assertEquals(Arrays.asList("old-1", "old-2", "new"), messages);
    }

    @Test
    public void createsDistinctMetadataForEachSplitBatch() {
        List<String> messages = Arrays.asList("{\"id\":1}", "{\"id\":2}");
        int maxBytes = ("{\"common\":[],\"meta\":{\"batchId\":\"batch-0\"},\"data\":["
                + messages.get(0) + "]}").getBytes(StandardCharsets.UTF_8).length;

        UploadBatcher.Result result = UploadBatcher.split("[]", messages, maxBytes,
                index -> meta("batch-" + index));

        assertEquals(2, result.getBatches().size());
        assertEquals("batch-0", result.getBatches().get(0).getBatchId());
        assertEquals("batch-1", result.getBatches().get(1).getBatchId());
        assertTrue(result.getBatches().get(1).getBody().contains("\"batchId\":\"batch-1\""));
    }

    private static UploadBatcher.BatchMeta meta(String batchId) {
        return new UploadBatcher.BatchMeta(batchId, "{\"batchId\":\"" + batchId + "\"}");
    }
}

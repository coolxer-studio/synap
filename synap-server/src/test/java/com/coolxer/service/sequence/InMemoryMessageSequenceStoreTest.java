package com.coolxer.service.sequence;

import com.coolxer.model.fact.Common;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InMemoryMessageSequenceStoreTest {
    @Test
    void detectsGapsAndReorderedMessagesPerStartAndGuid() {
        InMemoryMessageSequenceStore store = new InMemoryMessageSequenceStore(Duration.ofMinutes(30), 100);
        Common common = common(100, "guid-1");

        assertEquals("SEQUENCE_OK", store.observe(common, 1).reasonCode());
        assertEquals("SEQUENCE_GAP", store.observe(common, 3).reasonCode());
        SequenceObservation reordered = store.observe(common, 2);
        assertEquals("SEQUENCE_REORDERED", reordered.reasonCode());
        assertEquals(3, reordered.previousSequence());
        assertEquals("SEQUENCE_OK", store.observe(common(101, "guid-1"), 1).reasonCode());
        assertEquals("SEQUENCE_OK", store.observe(common(100, "guid-2"), 1).reasonCode());
    }

    private Common common(long startId, String guid) {
        Common value = new Common();
        value.setAppId(1); value.setStartId(startId); value.setGuid(guid); value.setPlatform("h5");
        return value;
    }
}

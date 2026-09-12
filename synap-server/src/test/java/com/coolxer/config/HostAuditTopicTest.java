package com.coolxer.config;

import org.apache.kafka.common.config.TopicConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostAuditTopicTest {
    @Test
    void auditTopicIsAppendOnlyAndRetainedFor180Days() {
        TopicDefine topics = new TopicDefine();
        ReflectionTestUtils.setField(topics, "partitions", 1);
        ReflectionTestUtils.setField(topics, "replicas", 1);
        var topic = topics.TopicForHostAudit();
        assertEquals("host_fact_audit", topic.name());
        assertEquals(TopicConfig.CLEANUP_POLICY_DELETE, topic.configs().get(TopicConfig.CLEANUP_POLICY_CONFIG));
        assertEquals("15552000000", topic.configs().get(TopicConfig.RETENTION_MS_CONFIG));
    }
}

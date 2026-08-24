#include <stdlib.h>
#include <string.h>

#include "synap_internal.h"
#include "unity.h"

TEST_CASE("business retry increments only attempt", "[synap][payload]")
{
    static const char RECORD[] =
        "{\"kind\":\"upload\",\"path\":\"/attach/upload\","
        "\"code\":\"code\",\"sessionId\":\"session\","
        "\"batchId\":\"batch\",\"messageId\":\"message\","
        "\"body\":{\"common\":[],\"meta\":{\"attempt\":0},\"data\":[]}}";
    char *updated = NULL;
    TEST_ESP_OK(synap_payload_increment_attempt(RECORD, &updated));
    cJSON *root = cJSON_Parse(updated);
    TEST_ASSERT_NOT_NULL(root);
    TEST_ASSERT_EQUAL_STRING("batch", cJSON_GetStringValue(
        cJSON_GetObjectItemCaseSensitive(root, "batchId")));
    TEST_ASSERT_EQUAL_STRING("message", cJSON_GetStringValue(
        cJSON_GetObjectItemCaseSensitive(root, "messageId")));
    cJSON *body = cJSON_GetObjectItemCaseSensitive(root, "body");
    cJSON *meta = cJSON_GetObjectItemCaseSensitive(body, "meta");
    TEST_ASSERT_EQUAL_INT(1, cJSON_GetObjectItemCaseSensitive(meta,
                                                              "attempt")->valueint);
    cJSON_Delete(root);
    free(updated);
}

TEST_CASE("ACK classifier requires one matching outcome", "[synap][payload]")
{
    static const char ACCEPTED[] =
        "{\"status\":1,\"ack\":{\"batchId\":\"batch\","
        "\"acceptedMessageIds\":[\"message\"],"
        "\"duplicateMessageIds\":[],\"rejectedItems\":[]}}";
    static const char RETRYABLE[] =
        "{\"status\":1,\"ack\":{\"batchId\":\"batch\","
        "\"acceptedMessageIds\":[],\"duplicateMessageIds\":[],"
        "\"rejectedItems\":[{\"messageId\":\"message\","
        "\"retryable\":true,\"reason\":\"busy\"}]}}";
    bool accepted, duplicate, retryable;
    char *reason = NULL;
    TEST_ESP_OK(synap_payload_classify_ack(ACCEPTED, "batch", "message",
                                           &accepted, &duplicate, &retryable,
                                           &reason));
    TEST_ASSERT_TRUE(accepted);
    TEST_ASSERT_FALSE(duplicate);
    TEST_ASSERT_FALSE(retryable);
    TEST_ASSERT_NULL(reason);
    TEST_ESP_OK(synap_payload_classify_ack(RETRYABLE, "batch", "message",
                                           &accepted, &duplicate, &retryable,
                                           &reason));
    TEST_ASSERT_TRUE(retryable);
    TEST_ASSERT_EQUAL_STRING("busy", reason);
    free(reason);
}

TEST_CASE("Start config accepts finite fractional seconds", "[synap][payload]")
{
    synap_schedule_t schedule;
    TEST_ESP_OK(synap_payload_parse_start_config(
        "{\"status\":1,\"config\":{\"startConfig\":[1,0.5,60,1.25,120]}}",
        &schedule));
    TEST_ASSERT_TRUE(schedule.enabled);
    TEST_ASSERT_DOUBLE_WITHIN(0.0001, 0.5, schedule.collect_every_seconds);
    TEST_ASSERT_DOUBLE_WITHIN(0.0001, 1.25, schedule.upload_every_seconds);
    TEST_ASSERT_NOT_EQUAL(ESP_OK, synap_payload_parse_start_config(
        "{\"status\":1,\"config\":{\"startConfig\":[0,1,1,1,1]}}",
        &schedule));
}

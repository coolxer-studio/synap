#include "synap_internal.h"

#include <inttypes.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <time.h>

#include "esp_app_desc.h"
#include "esp_chip_info.h"
#include "esp_flash.h"
#include "esp_flash_encrypt.h"
#include "esp_heap_caps.h"
#include "esp_idf_version.h"
#include "esp_netif.h"
#include "esp_ota_ops.h"
#include "esp_partition.h"
#include "esp_secure_boot.h"
#include "esp_system.h"
#include "esp_timer.h"
#include "esp_wifi.h"

static int64_t current_time_ms(void)
{
    struct timeval value;
    gettimeofday(&value, NULL);
    return (int64_t) value.tv_sec * 1000 + value.tv_usec / 1000;
}

static const char *json_string(cJSON *object, const char *name)
{
    cJSON *item = cJSON_GetObjectItemCaseSensitive(object, name);
    return cJSON_IsString(item) && item->valuestring != NULL ? item->valuestring : NULL;
}

static char *random_uuid(void)
{
    uint8_t bytes[16];
    if (synap_crypto_random(bytes, sizeof(bytes)) != ESP_OK) return NULL;
    bytes[6] = (bytes[6] & 0x0f) | 0x40;
    bytes[8] = (bytes[8] & 0x3f) | 0x80;
    char *uuid = malloc(37);
    if (uuid == NULL) return NULL;
    snprintf(uuid, 37,
             "%02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
             bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5],
             bytes[6], bytes[7], bytes[8], bytes[9], bytes[10], bytes[11],
             bytes[12], bytes[13], bytes[14], bytes[15]);
    return uuid;
}

static void formatted_time(char output[24])
{
    time_t now = time(NULL);
    struct tm value;
    localtime_r(&now, &value);
    strftime(output, 24, "%Y-%m-%d %H:%M:%S", &value);
}

static char *lan_ip(void)
{
    esp_netif_t *netif = esp_netif_get_handle_from_ifkey("WIFI_STA_DEF");
    esp_netif_ip_info_t info;
    char buffer[16] = "0.0.0.0";
    if (netif != NULL && esp_netif_get_ip_info(netif, &info) == ESP_OK &&
        info.ip.addr != 0) {
        esp_ip4addr_ntoa(&info.ip, buffer, sizeof(buffer));
    }
    return strdup(buffer);
}

static const char *reset_reason_name(esp_reset_reason_t reason)
{
    switch (reason) {
        case ESP_RST_POWERON: return "power_on";
        case ESP_RST_EXT: return "external";
        case ESP_RST_SW: return "software";
        case ESP_RST_PANIC: return "panic";
        case ESP_RST_INT_WDT: return "interrupt_watchdog";
        case ESP_RST_TASK_WDT: return "task_watchdog";
        case ESP_RST_WDT: return "watchdog";
        case ESP_RST_DEEPSLEEP: return "deep_sleep";
        case ESP_RST_BROWNOUT: return "brownout";
        case ESP_RST_SDIO: return "sdio";
        default: return "unknown";
    }
}

static cJSON *create_common(const synap_provision_t *provision,
                            const char *session_id)
{
    cJSON *common = cJSON_CreateArray();
    char *ip = lan_ip();
    char client_time[24];
    formatted_time(client_time);
    const char *values[24] = {
        "", provision->device_id, session_id, SYNAP_AGENT_SDK_VERSION,
        provision->business_app_id, provision->device_name,
        "agent-esp32", esp_app_get_description()->version,
        SYNAP_AGENT_PLATFORM, "Espressif", CONFIG_IDF_TARGET,
        "ESP-IDF", esp_get_idf_version(), "wifi", ip == NULL ? "0.0.0.0" : ip,
        "0.0.0.0", "", "", "", "", "", "", "", client_time,
    };
    for (size_t index = 0; index < 24; ++index) {
        cJSON_AddItemToArray(common, cJSON_CreateString(values[index]));
    }
    free(ip);
    return common;
}

static char *create_code(const synap_provision_t *provision)
{
    int length = snprintf(NULL, 0, "%s-%s-%s-%s", provision->device_id,
                          SYNAP_AGENT_PLATFORM, provision->business_app_id,
                          SYNAP_AGENT_SDK_VERSION);
    if (length <= 0 || length > 512) return NULL;
    char *code = malloc((size_t) length + 1);
    if (code != NULL) {
        snprintf(code, (size_t) length + 1, "%s-%s-%s-%s",
                 provision->device_id, SYNAP_AGENT_PLATFORM,
                 provision->business_app_id, SYNAP_AGENT_SDK_VERSION);
    }
    return code;
}

static cJSON *create_meta(const synap_provision_t *provision,
                          const char *session_id, const char *batch_id,
                          const char *message_id, uint64_t sequence,
                          bool include_message)
{
    int64_t now = current_time_ms();
    cJSON *meta = cJSON_CreateObject();
    cJSON_AddNumberToObject(meta, "protocolVersion", 1);
    cJSON_AddStringToObject(meta, "appKeyId", provision->sc_app_id);
    cJSON_AddStringToObject(meta, "detectorVersion", SYNAP_AGENT_SDK_VERSION);
    if (include_message) cJSON_AddStringToObject(meta, "messageId", message_id);
    cJSON_AddStringToObject(meta, "batchId", batch_id);
    cJSON_AddStringToObject(meta, "sessionId", session_id);
    cJSON_AddNumberToObject(meta, "attempt", 0);
    cJSON_AddNumberToObject(meta, "createdAt", (double) now);
    if (include_message) {
        cJSON_AddNumberToObject(meta, "observedAt", (double) now);
        cJSON_AddNumberToObject(meta, "sequence", (double) sequence);
    }
    return meta;
}

static esp_err_t wrap_record(const char *kind, const char *path, const char *code,
                             const char *session_id, const char *batch_id,
                             const char *message_id, cJSON *body,
                             char **record_json)
{
    cJSON *record = cJSON_CreateObject();
    cJSON_AddStringToObject(record, "kind", kind);
    cJSON_AddStringToObject(record, "path", path);
    cJSON_AddStringToObject(record, "code", code);
    cJSON_AddStringToObject(record, "sessionId", session_id);
    cJSON_AddStringToObject(record, "batchId", batch_id);
    cJSON_AddStringToObject(record, "messageId", message_id);
    cJSON_AddItemToObject(record, "body", body);
    char *encoded = cJSON_PrintUnformatted(record);
    cJSON_Delete(record);
    if (encoded == NULL) return ESP_ERR_NO_MEM;
    *record_json = encoded;
    return ESP_OK;
}

esp_err_t synap_payload_create_start(const synap_provision_t *provision,
                                     char **session_id, char **record_json)
{
    if (provision == NULL || session_id == NULL || record_json == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    char session_buffer[24];
    snprintf(session_buffer, sizeof(session_buffer), "%" PRId64,
             current_time_ms());
    char *session = strdup(session_buffer);
    char *message_uuid = random_uuid();
    char *batch_uuid = random_uuid();
    char *code = create_code(provision);
    if (session == NULL || message_uuid == NULL || batch_uuid == NULL ||
        code == NULL) {
        free(session); free(message_uuid); free(batch_uuid); free(code);
        return ESP_ERR_NO_MEM;
    }
    char message_id[48];
    char batch_id[48];
    snprintf(message_id, sizeof(message_id), "start-msg-%s", message_uuid);
    snprintf(batch_id, sizeof(batch_id), "start-batch-%s", batch_uuid);
    cJSON *body = cJSON_CreateObject();
    cJSON_AddItemToObject(body, "common", create_common(provision, session));
    cJSON_AddItemToObject(body, "meta", create_meta(provision, session, batch_id,
                                                     message_id, 1, true));
    esp_err_t err = wrap_record("start", SYNAP_START_PATH, code, session,
                                batch_id, message_id, body, record_json);
    free(message_uuid); free(batch_uuid); free(code);
    if (err != ESP_OK) {
        free(session);
        return err;
    }
    *session_id = session;
    return ESP_OK;
}

static cJSON *device_data(void)
{
    esp_chip_info_t chip;
    esp_chip_info(&chip);
    uint32_t flash_bytes = 0;
    esp_flash_get_size(NULL, &flash_bytes);
    const esp_app_desc_t *app = esp_app_get_description();
    cJSON *data = cJSON_CreateObject();
    cJSON_AddStringToObject(data, "chipModel", CONFIG_IDF_TARGET);
    cJSON_AddNumberToObject(data, "chipRevision", chip.revision);
    cJSON_AddNumberToObject(data, "cores", chip.cores);
    cJSON_AddNumberToObject(data, "featureFlags", chip.features);
    cJSON_AddStringToObject(data, "idfVersion", esp_get_idf_version());
    cJSON_AddStringToObject(data, "firmwareProject", app->project_name);
    cJSON_AddStringToObject(data, "firmwareVersion", app->version);
    cJSON_AddStringToObject(data, "firmwareBuildDate", app->date);
    cJSON_AddStringToObject(data, "firmwareBuildTime", app->time);
    cJSON_AddNumberToObject(data, "flashBytes", flash_bytes);
    cJSON_AddNumberToObject(data, "psramBytes",
                            heap_caps_get_total_size(MALLOC_CAP_SPIRAM));
    cJSON_AddStringToObject(data, "resetReason",
                            reset_reason_name(esp_reset_reason()));
    return data;
}

static void hex_encode(const uint8_t *input, size_t length, char *output)
{
    static const char HEX[] = "0123456789abcdef";
    for (size_t index = 0; index < length; ++index) {
        output[index * 2] = HEX[input[index] >> 4];
        output[index * 2 + 1] = HEX[input[index] & 0x0f];
    }
    output[length * 2] = '\0';
}

static cJSON *security_data(void)
{
    const esp_partition_t *running = esp_ota_get_running_partition();
    uint8_t sha[32] = {0};
    bool have_sha = running != NULL && esp_partition_get_sha256(running, sha) == ESP_OK;
    char sha_hex[65] = {0};
    if (have_sha) hex_encode(sha, sizeof(sha), sha_hex);
    cJSON *data = cJSON_CreateObject();
    cJSON_AddBoolToObject(data, "secureBootEnabled", esp_secure_boot_enabled());
    cJSON_AddBoolToObject(data, "flashEncryptionEnabled",
                          esp_flash_encryption_enabled());
#ifdef CONFIG_NVS_ENCRYPTION
    cJSON_AddBoolToObject(data, "nvsEncryptionConfigured",
                          CONFIG_NVS_ENCRYPTION);
#else
    cJSON_AddBoolToObject(data, "nvsEncryptionConfigured", false);
#endif
#ifdef CONFIG_SECURE_DISABLE_JTAG
    cJSON_AddStringToObject(data, "jtagState",
                            CONFIG_SECURE_DISABLE_JTAG ? "disabled_by_build" : "not_enforced");
#else
    cJSON_AddStringToObject(data, "jtagState", "not_probed");
#endif
#ifdef CONFIG_ESP_DEBUG_OCDAWARE
    cJSON_AddBoolToObject(data, "debugOcdAware", CONFIG_ESP_DEBUG_OCDAWARE);
#else
    cJSON_AddBoolToObject(data, "debugOcdAware", false);
#endif
#ifdef CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE
    cJSON_AddBoolToObject(data, "rollbackEnabled",
                          CONFIG_BOOTLOADER_APP_ROLLBACK_ENABLE);
#else
    cJSON_AddBoolToObject(data, "rollbackEnabled", false);
#endif
    cJSON_AddStringToObject(data, "runningPartition",
                            running == NULL ? "unknown" : running->label);
    if (have_sha) cJSON_AddStringToObject(data, "firmwareSha256", sha_hex);
    else cJSON_AddNullToObject(data, "firmwareSha256");
    return data;
}

static cJSON *health_data(const synap_agent_metrics_t *metrics)
{
    wifi_ap_record_t access_point;
    bool wifi_ok = esp_wifi_sta_get_ap_info(&access_point) == ESP_OK;
    char *ip = lan_ip();
    cJSON *data = cJSON_CreateObject();
    cJSON_AddNumberToObject(data, "uptimeMs", esp_timer_get_time() / 1000);
    cJSON_AddNumberToObject(data, "freeHeapBytes", esp_get_free_heap_size());
    cJSON_AddNumberToObject(data, "minimumFreeHeapBytes",
                            esp_get_minimum_free_heap_size());
    cJSON_AddNumberToObject(data, "largestFreeBlockBytes",
                            heap_caps_get_largest_free_block(MALLOC_CAP_8BIT));
    cJSON_AddNumberToObject(data, "internalFreeHeapBytes",
                            heap_caps_get_free_size(MALLOC_CAP_INTERNAL));
    if (wifi_ok) {
        cJSON_AddNumberToObject(data, "wifiRssi", access_point.rssi);
        cJSON_AddNumberToObject(data, "wifiChannel", access_point.primary);
    } else {
        cJSON_AddNullToObject(data, "wifiRssi");
        cJSON_AddNullToObject(data, "wifiChannel");
    }
    cJSON_AddStringToObject(data, "lanIp", ip == NULL ? "0.0.0.0" : ip);
    cJSON_AddNumberToObject(data, "queueDepth", metrics->queue_depth);
    cJSON_AddNumberToObject(data, "deadLetterDepth", metrics->dead_letter_depth);
    cJSON_AddNumberToObject(data, "droppedTotal", (double) metrics->dropped_total);
    cJSON_AddNumberToObject(data, "uploadAcceptedTotal",
                            (double) metrics->upload_accepted_total);
    cJSON_AddNumberToObject(data, "uploadFailureTotal",
                            (double) (metrics->upload_retryable_total +
                                      metrics->upload_rejected_total));
    cJSON_AddStringToObject(data, "lastError", metrics->last_error);
    free(ip);
    return data;
}

esp_err_t synap_payload_create_fact(const synap_provision_t *provision,
                                    const char *session_id, uint64_t sequence,
                                    const char *fact_type,
                                    const synap_agent_metrics_t *metrics,
                                    char **record_json)
{
    if (provision == NULL || session_id == NULL || sequence < 2 ||
        fact_type == NULL || metrics == NULL || record_json == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    char *message_uuid = random_uuid();
    char *batch_uuid = random_uuid();
    char *code = create_code(provision);
    if (message_uuid == NULL || batch_uuid == NULL || code == NULL) {
        free(message_uuid); free(batch_uuid); free(code);
        return ESP_ERR_NO_MEM;
    }
    char message_id[48];
    char batch_id[48];
    snprintf(message_id, sizeof(message_id), "msg-%s", message_uuid);
    snprintf(batch_id, sizeof(batch_id), "batch-%s", batch_uuid);
    int64_t observed_at = current_time_ms();
    char build_date[24];
    formatted_time(build_date);
    cJSON *fact = cJSON_CreateObject();
    cJSON_AddStringToObject(fact, "type", fact_type);
    cJSON_AddNumberToObject(fact, "schemaVersion", 1);
    cJSON_AddStringToObject(fact, "messageId", message_id);
    cJSON_AddNumberToObject(fact, "observedAt", (double) observed_at);
    cJSON_AddNumberToObject(fact, "sequence", (double) sequence);
    cJSON_AddStringToObject(fact, "buildDate", build_date);
    if (strcmp(fact_type, "DeviceData") == 0) {
        cJSON_AddItemToObject(fact, "data", device_data());
    } else if (strcmp(fact_type, "SecurityData") == 0) {
        cJSON_AddItemToObject(fact, "data", security_data());
    } else if (strcmp(fact_type, "HealthData") == 0) {
        cJSON_AddItemToObject(fact, "data", health_data(metrics));
    } else {
        cJSON_Delete(fact);
        free(message_uuid); free(batch_uuid); free(code);
        return ESP_ERR_NOT_SUPPORTED;
    }
    cJSON *body = cJSON_CreateObject();
    cJSON_AddItemToObject(body, "common", create_common(provision, session_id));
    cJSON_AddItemToObject(body, "meta", create_meta(provision, session_id,
                                                     batch_id, message_id,
                                                     sequence, false));
    cJSON *data = cJSON_AddArrayToObject(body, "data");
    cJSON_AddItemToArray(data, fact);
    esp_err_t err = wrap_record("upload", SYNAP_UPLOAD_PATH, code, session_id,
                                batch_id, message_id, body, record_json);
    free(message_uuid); free(batch_uuid); free(code);
    return err;
}

esp_err_t synap_payload_increment_attempt(const char *record_json,
                                          char **updated_json)
{
    if (record_json == NULL || updated_json == NULL) return ESP_ERR_INVALID_ARG;
    cJSON *root = cJSON_Parse(record_json);
    cJSON *body = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root, "body");
    cJSON *meta = body == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(body, "meta");
    cJSON *attempt = meta == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(meta,
                                                                            "attempt");
    if (!cJSON_IsNumber(attempt) || attempt->valueint < 0 ||
        attempt->valueint >= INT32_MAX) {
        cJSON_Delete(root);
        return ESP_ERR_INVALID_ARG;
    }
    cJSON_SetNumberValue(attempt, attempt->valueint + 1);
    char *encoded = cJSON_PrintUnformatted(root);
    cJSON_Delete(root);
    if (encoded == NULL) return ESP_ERR_NO_MEM;
    *updated_json = encoded;
    return ESP_OK;
}

esp_err_t synap_payload_record_fields(const char *record_json,
                                      char **path, char **code,
                                      cJSON **body, char **session_id,
                                      char **batch_id, char **message_id,
                                      bool *is_start)
{
    if (record_json == NULL || path == NULL || code == NULL || body == NULL ||
        session_id == NULL || batch_id == NULL || message_id == NULL ||
        is_start == NULL) return ESP_ERR_INVALID_ARG;
    *path = *code = *session_id = *batch_id = *message_id = NULL;
    *body = NULL;
    cJSON *root = cJSON_Parse(record_json);
    const char *kind_value = root == NULL ? NULL :
        cJSON_GetStringValue(cJSON_GetObjectItemCaseSensitive(root, "kind"));
    const char *path_value = root == NULL ? NULL : json_string(root, "path");
    const char *code_value = root == NULL ? NULL : json_string(root, "code");
    const char *session_value = root == NULL ? NULL : json_string(root, "sessionId");
    const char *batch_value = root == NULL ? NULL : json_string(root, "batchId");
    const char *message_value = root == NULL ? NULL : json_string(root, "messageId");
    cJSON *body_value = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root,
                                                                                "body");
    if (kind_value == NULL || path_value == NULL || code_value == NULL ||
        session_value == NULL || batch_value == NULL || message_value == NULL ||
        !cJSON_IsObject(body_value)) {
        cJSON_Delete(root);
        return ESP_ERR_INVALID_ARG;
    }
    *path = strdup(path_value);
    *code = strdup(code_value);
    *session_id = strdup(session_value);
    *batch_id = strdup(batch_value);
    *message_id = strdup(message_value);
    *body = cJSON_Duplicate(body_value, true);
    *is_start = strcmp(kind_value, "start") == 0;
    cJSON_Delete(root);
    if (*path == NULL || *code == NULL || *session_id == NULL ||
        *batch_id == NULL || *message_id == NULL || *body == NULL) {
        free(*path); free(*code); free(*session_id); free(*batch_id);
        free(*message_id); cJSON_Delete(*body);
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

esp_err_t synap_payload_parse_start_config(const char *response,
                                           synap_schedule_t *schedule)
{
    if (response == NULL || schedule == NULL) return ESP_ERR_INVALID_ARG;
    memset(schedule, 0, sizeof(*schedule));
    cJSON *root = cJSON_Parse(response);
    cJSON *status = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root,
                                                                           "status");
    cJSON *config = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root,
                                                                           "config");
    cJSON *values = config == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(
        config, "startConfig");
    if (!cJSON_IsNumber(status) || status->valueint != 1 ||
        !cJSON_IsArray(values) || cJSON_GetArraySize(values) != 5) {
        cJSON_Delete(root);
        return ESP_ERR_INVALID_RESPONSE;
    }
    double parsed[5];
    for (int index = 0; index < 5; ++index) {
        cJSON *value = cJSON_GetArrayItem(values, index);
        if (!cJSON_IsNumber(value) || !isfinite(value->valuedouble) ||
            value->valuedouble < 0 || value->valuedouble > 31536000.0) {
            cJSON_Delete(root);
            return ESP_ERR_INVALID_RESPONSE;
        }
        parsed[index] = value->valuedouble;
    }
    cJSON_Delete(root);
    if (parsed[0] != 1.0 || parsed[1] < 0.001 || parsed[2] < 0.001 ||
        parsed[3] < 0.001 || parsed[4] < 0.001) return ESP_ERR_INVALID_STATE;
    schedule->enabled = true;
    schedule->collect_every_seconds = parsed[1];
    schedule->collect_for_seconds = parsed[2];
    schedule->upload_every_seconds = parsed[3];
    schedule->upload_for_seconds = parsed[4];
    return ESP_OK;
}

esp_err_t synap_payload_classify_ack(const char *response,
                                     const char *batch_id,
                                     const char *message_id,
                                     bool *accepted, bool *duplicate,
                                     bool *retryable, char **reason)
{
    if (response == NULL || batch_id == NULL || message_id == NULL ||
        accepted == NULL || duplicate == NULL || retryable == NULL ||
        reason == NULL) return ESP_ERR_INVALID_ARG;
    *accepted = *duplicate = *retryable = false;
    *reason = NULL;
    cJSON *root = cJSON_Parse(response);
    cJSON *status = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root,
                                                                           "status");
    cJSON *ack = root == NULL ? NULL : cJSON_GetObjectItemCaseSensitive(root, "ack");
    const char *ack_batch = ack == NULL ? NULL : json_string(ack, "batchId");
    if (!cJSON_IsNumber(status) || status->valueint != 1 || ack_batch == NULL ||
        strcmp(ack_batch, batch_id) != 0) {
        cJSON_Delete(root);
        return ESP_ERR_INVALID_RESPONSE;
    }
    int matches = 0;
    cJSON *accepted_items = cJSON_GetObjectItemCaseSensitive(ack,
                                                              "acceptedMessageIds");
    cJSON *item;
    cJSON_ArrayForEach(item, accepted_items) {
        if (cJSON_IsString(item) && strcmp(item->valuestring, message_id) == 0) {
            *accepted = true; matches++;
        }
    }
    cJSON *duplicate_items = cJSON_GetObjectItemCaseSensitive(
        ack, "duplicateMessageIds");
    cJSON_ArrayForEach(item, duplicate_items) {
        if (cJSON_IsString(item) && strcmp(item->valuestring, message_id) == 0) {
            *duplicate = true; matches++;
        }
    }
    cJSON *rejected_items = cJSON_GetObjectItemCaseSensitive(ack, "rejectedItems");
    cJSON_ArrayForEach(item, rejected_items) {
        const char *rejected_id = json_string(item, "messageId");
        if (rejected_id != NULL && strcmp(rejected_id, message_id) == 0) {
            cJSON *retry = cJSON_GetObjectItemCaseSensitive(item, "retryable");
            const char *rejected_reason = json_string(item, "reason");
            if (!cJSON_IsBool(retry)) {
                cJSON_Delete(root);
                return ESP_ERR_INVALID_RESPONSE;
            }
            *retryable = cJSON_IsTrue(retry);
            *reason = strdup(rejected_reason == NULL ? "rejected" : rejected_reason);
            matches++;
        }
    }
    cJSON_Delete(root);
    return matches == 1 ? ESP_OK : ESP_ERR_INVALID_RESPONSE;
}

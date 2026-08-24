#include "synap_internal.h"

#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_partition.h"
#include "esp_random.h"
#include "esp_timer.h"
#include "freertos/event_groups.h"
#include "freertos/semphr.h"
#include "freertos/task.h"
#include "nvs.h"
#include "nvs_flash.h"

static const char *TAG = "synap-agent";

#define AGENT_STOP_BIT BIT0
#define AGENT_DONE_BIT BIT1
#define AGENT_NETWORK_BIT BIT2

typedef struct {
    bool initialized;
    bool running;
    synap_agent_config_t config;
    char provision_partition[16];
    char runtime_partition[16];
    char spool_partition[16];
    char dead_partition[16];
    synap_sc_client_t secure;
    EventGroupHandle_t events;
    SemaphoreHandle_t mutex;
    TaskHandle_t task;
    esp_event_handler_instance_t ip_handler;
    esp_event_handler_instance_t wifi_handler;
    synap_agent_state_t state;
    synap_agent_metrics_t metrics;
    char *current_session_id;
    synap_schedule_t schedule;
    bool current_start_accepted;
    int64_t schedule_started_ms;
    int64_t next_collect_ms;
    int64_t next_upload_ms;
    uint32_t retry_delay_ms;
} agent_context_t;

static agent_context_t s_agent;

static int64_t monotonic_ms(void)
{
    return esp_timer_get_time() / 1000;
}

static bool clock_ready(void)
{
    return time(NULL) >= 1704067200; /* 2024-01-01 */
}

static bool network_ready(void)
{
    esp_netif_t *netif = esp_netif_get_handle_from_ifkey("WIFI_STA_DEF");
    esp_netif_ip_info_t info;
    return netif != NULL && esp_netif_get_ip_info(netif, &info) == ESP_OK &&
           info.ip.addr != 0;
}

static void copy_last_error(const char *error)
{
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    strlcpy(s_agent.metrics.last_error, error == NULL ? "" : error,
            sizeof(s_agent.metrics.last_error));
    xSemaphoreGive(s_agent.mutex);
}

static void persist_metrics(void)
{
    nvs_handle_t handle;
    if (nvs_open_from_partition(s_agent.runtime_partition, "agent",
                                NVS_READWRITE, &handle) != ESP_OK) return;
    synap_agent_metrics_t snapshot;
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    snapshot = s_agent.metrics;
    xSemaphoreGive(s_agent.mutex);
    if (nvs_set_blob(handle, "metrics", &snapshot, sizeof(snapshot)) == ESP_OK) {
        nvs_commit(handle);
    }
    nvs_close(handle);
}

static void load_metrics(void)
{
    nvs_handle_t handle;
    if (nvs_open_from_partition(s_agent.runtime_partition, "agent",
                                NVS_READONLY, &handle) != ESP_OK) return;
    size_t length = sizeof(s_agent.metrics);
    if (nvs_get_blob(handle, "metrics", &s_agent.metrics, &length) != ESP_OK ||
        length != sizeof(s_agent.metrics)) {
        memset(&s_agent.metrics, 0, sizeof(s_agent.metrics));
    }
    nvs_close(handle);
}

static void refresh_depth_metrics(void)
{
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    s_agent.metrics.queue_depth = synap_store_queue_depth();
    s_agent.metrics.dead_letter_depth = synap_store_dead_letter_depth();
    xSemaphoreGive(s_agent.mutex);
}

static void set_state(synap_agent_state_t state, esp_err_t error,
                      const char *detail)
{
    if (s_agent.state == state && error == ESP_OK) return;
    s_agent.state = state;
    if (error != ESP_OK && detail != NULL) copy_last_error(detail);
    if (s_agent.config.state_callback != NULL) {
        s_agent.config.state_callback(state, error, detail,
                                      s_agent.config.callback_context);
    }
}

static esp_err_t init_partition(const char *name, nvs_sec_cfg_t *security)
{
    esp_err_t err = security == NULL
        ? nvs_flash_init_partition(name)
        : nvs_flash_secure_init_partition(name, security);
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        /* Never erase identity, provisioning or queued evidence automatically. */
        ESP_LOGE(TAG, "NVS partition %s requires operator recovery", name);
    }
    return err;
}

static void network_event(void *argument, esp_event_base_t event_base,
                          int32_t event_id, void *event_data)
{
    (void) argument;
    (void) event_data;
    if (event_base == IP_EVENT && event_id == IP_EVENT_STA_GOT_IP) {
        xEventGroupSetBits(s_agent.events, AGENT_NETWORK_BIT);
    } else if (event_base == WIFI_EVENT && event_id == WIFI_EVENT_STA_DISCONNECTED) {
        xEventGroupClearBits(s_agent.events, AGENT_NETWORK_BIT);
        xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
        s_agent.metrics.reconnect_total++;
        xSemaphoreGive(s_agent.mutex);
    }
}

synap_agent_config_t synap_agent_default_config(void)
{
    return (synap_agent_config_t) {
        .provision_partition = CONFIG_SYNAP_PROVISION_PARTITION,
        .runtime_partition = CONFIG_SYNAP_RUNTIME_PARTITION,
        .spool_partition = CONFIG_SYNAP_SPOOL_PARTITION,
        .dead_letter_partition = CONFIG_SYNAP_DEAD_LETTER_PARTITION,
        .queue_max_records = CONFIG_SYNAP_QUEUE_MAX_RECORDS,
        .dead_letter_max_records = CONFIG_SYNAP_DEAD_LETTER_MAX_RECORDS,
        .queue_max_record_bytes = CONFIG_SYNAP_QUEUE_MAX_RECORD_BYTES,
    };
}

esp_err_t synap_agent_init(const synap_agent_config_t *config)
{
    if (config == NULL || config->provision_partition == NULL ||
        config->runtime_partition == NULL || config->spool_partition == NULL ||
        config->dead_letter_partition == NULL || s_agent.initialized) {
        return s_agent.initialized ? ESP_ERR_INVALID_STATE : ESP_ERR_INVALID_ARG;
    }
    memset(&s_agent, 0, sizeof(s_agent));
    s_agent.config = *config;
    strlcpy(s_agent.provision_partition, config->provision_partition,
            sizeof(s_agent.provision_partition));
    strlcpy(s_agent.runtime_partition, config->runtime_partition,
            sizeof(s_agent.runtime_partition));
    strlcpy(s_agent.spool_partition, config->spool_partition,
            sizeof(s_agent.spool_partition));
    strlcpy(s_agent.dead_partition, config->dead_letter_partition,
            sizeof(s_agent.dead_partition));
    s_agent.config.provision_partition = s_agent.provision_partition;
    s_agent.config.runtime_partition = s_agent.runtime_partition;
    s_agent.config.spool_partition = s_agent.spool_partition;
    s_agent.config.dead_letter_partition = s_agent.dead_partition;
    s_agent.mutex = xSemaphoreCreateMutex();
    s_agent.events = xEventGroupCreate();
    if (s_agent.mutex == NULL || s_agent.events == NULL) return ESP_ERR_NO_MEM;

    nvs_sec_cfg_t security = {0};
    nvs_sec_cfg_t *security_ptr = NULL;
#ifdef CONFIG_NVS_ENCRYPTION
    if (CONFIG_NVS_ENCRYPTION) {
        const esp_partition_t *keys = esp_partition_find_first(
            ESP_PARTITION_TYPE_DATA, ESP_PARTITION_SUBTYPE_DATA_NVS_KEYS,
            "nvs_keys");
        if (keys == NULL) return ESP_ERR_NOT_FOUND;
        esp_err_t security_err = nvs_flash_read_security_cfg(keys, &security);
        if (security_err != ESP_OK) return security_err;
        security_ptr = &security;
    }
#endif
    esp_err_t err = init_partition(s_agent.provision_partition, security_ptr);
    if (err == ESP_OK) {
        err = init_partition(s_agent.runtime_partition, security_ptr);
    }
    if (err == ESP_OK) err = init_partition(s_agent.spool_partition, security_ptr);
    if (err == ESP_OK) err = init_partition(s_agent.dead_partition, security_ptr);
    memset(&security, 0, sizeof(security));
    if (err != ESP_OK) return err;
    synap_provision_t provision;
    err = synap_config_load(s_agent.provision_partition, &provision);
    if (err == ESP_OK) err = synap_store_init(&s_agent.config);
    if (err == ESP_OK) err = synap_sc_init(&s_agent.secure, &provision,
                                           s_agent.provision_partition,
                                           s_agent.runtime_partition);
    if (err != ESP_OK) {
        synap_config_free(&provision);
        return err;
    }
    load_metrics();
    refresh_depth_metrics();
    s_agent.retry_delay_ms = 1000;
    err = esp_event_handler_instance_register(IP_EVENT, IP_EVENT_STA_GOT_IP,
                                              network_event, NULL,
                                              &s_agent.ip_handler);
    if (err == ESP_OK) {
        err = esp_event_handler_instance_register(WIFI_EVENT,
                                                  WIFI_EVENT_STA_DISCONNECTED,
                                                  network_event, NULL,
                                                  &s_agent.wifi_handler);
    }
    if (err != ESP_OK) return err;
    if (network_ready()) xEventGroupSetBits(s_agent.events, AGENT_NETWORK_BIT);
    s_agent.initialized = true;
    s_agent.state = SYNAP_AGENT_STATE_IDLE;
    return ESP_OK;
}

static esp_err_t enqueue_record(char *record)
{
    if (record == NULL) return ESP_ERR_INVALID_ARG;
    esp_err_t err = synap_store_enqueue(record, strlen(record));
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    if (err == ESP_OK) s_agent.metrics.queued_total++;
    else if (err == ESP_ERR_NO_MEM) s_agent.metrics.dropped_total++;
    xSemaphoreGive(s_agent.mutex);
    free(record);
    refresh_depth_metrics();
    persist_metrics();
    return err;
}

static esp_err_t enqueue_current_start(void)
{
    char *record = NULL;
    free(s_agent.current_session_id);
    s_agent.current_session_id = NULL;
    esp_err_t err = synap_payload_create_start(&s_agent.secure.provision,
                                               &s_agent.current_session_id,
                                               &record);
    if (err == ESP_OK) err = enqueue_record(record);
    if (err != ESP_OK) {
        free(s_agent.current_session_id);
        s_agent.current_session_id = NULL;
    }
    return err;
}

static esp_err_t collect_facts(void)
{
    static const char *TYPES[] = {"DeviceData", "SecurityData", "HealthData"};
    synap_agent_metrics_t metrics;
    synap_agent_get_metrics(&metrics);
    for (size_t index = 0; index < sizeof(TYPES) / sizeof(TYPES[0]); ++index) {
        char *record = NULL;
        uint64_t sequence = 0;
        esp_err_t err = synap_store_reserve_business_sequence(
            s_agent.runtime_partition, &sequence);
        if (err != ESP_OK) return err;
        err = synap_payload_create_fact(&s_agent.secure.provision,
                                        s_agent.current_session_id,
                                        sequence, TYPES[index], &metrics,
                                        &record);
        if (err != ESP_OK) return err;
        err = enqueue_record(record);
        if (err != ESP_OK && err != ESP_ERR_NO_MEM) return err;
    }
    return ESP_OK;
}

typedef enum {
    SEND_PROGRESS,
    SEND_EMPTY,
    SEND_RETRY,
    SEND_PERMANENT,
} send_result_t;

static bool retryable_secure_error(const char *error, int status)
{
    if (status == 429 || status >= 500) return true;
    return strcmp(error, "SC_NETWORK_FAILED") == 0 ||
           strcmp(error, "SC_REQUEST_TIMEOUT") == 0 ||
           strcmp(error, "SC_UNKNOWN_SESSION") == 0 ||
           strcmp(error, "SC_REQUEST_EXPIRED") == 0;
}

static send_result_t send_head(void)
{
    synap_queue_item_t item;
    esp_err_t err = synap_store_peek(&item);
    if (err == ESP_ERR_NOT_FOUND) return SEND_EMPTY;
    if (err != ESP_OK) {
        copy_last_error("QUEUE_READ_FAILED");
        return SEND_RETRY;
    }
    char *updated = NULL;
    err = synap_payload_increment_attempt(item.json, &updated);
    if (err == ESP_OK) err = synap_store_replace_head(updated, strlen(updated));
    if (err == ESP_OK) {
        free(item.json);
        item.json = updated;
        item.length = strlen(updated);
        updated = NULL;
    }
    free(updated);
    if (err != ESP_OK) {
        synap_store_free_item(&item);
        copy_last_error("QUEUE_UPDATE_FAILED");
        return SEND_RETRY;
    }
    char *path = NULL, *code = NULL, *session_id = NULL, *batch_id = NULL;
    char *message_id = NULL;
    cJSON *body = NULL;
    bool is_start = false;
    err = synap_payload_record_fields(item.json, &path, &code, &body,
                                      &session_id, &batch_id, &message_id,
                                      &is_start);
    synap_store_free_item(&item);
    char *body_json = err == ESP_OK ? cJSON_PrintUnformatted(body) : NULL;
    cJSON_Delete(body);
    if (err != ESP_OK || body_json == NULL) {
        esp_err_t reject_err = synap_store_reject_head("invalid_local_record");
        free(path); free(code); free(session_id); free(batch_id); free(message_id);
        free(body_json);
        refresh_depth_metrics();
        return reject_err == ESP_OK ? SEND_PROGRESS : SEND_RETRY;
    }
    bool needed_handshake = !s_agent.secure.session.valid;
    int logical_status = 0;
    char *response = NULL;
    char sc_error[48] = {0};
    set_state(needed_handshake ? SYNAP_AGENT_STATE_ENROLLING
                               : (is_start ? SYNAP_AGENT_STATE_STARTING
                                           : SYNAP_AGENT_STATE_ONLINE),
              ESP_OK, NULL);
    err = synap_sc_request(&s_agent.secure, path, code, body_json,
                           &logical_status, &response, sc_error);
    free(path); free(code); free(body_json);
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    if (needed_handshake && err == ESP_OK) s_agent.metrics.handshake_total++;
    s_agent.metrics.last_http_status = logical_status;
    xSemaphoreGive(s_agent.mutex);
    if (err != ESP_OK || logical_status == 429 || logical_status >= 500) {
        xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
        s_agent.metrics.upload_retryable_total++;
        xSemaphoreGive(s_agent.mutex);
        copy_last_error(sc_error[0] == '\0' ? "TRANSPORT_FAILED" : sc_error);
        bool retry = retryable_secure_error(sc_error, logical_status);
        free(response); free(session_id); free(batch_id); free(message_id);
        persist_metrics();
        return retry ? SEND_RETRY : SEND_PERMANENT;
    }
    if (logical_status < 200 || logical_status >= 300) {
        synap_store_reject_head("logical_http_rejection");
        xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
        s_agent.metrics.upload_rejected_total++;
        xSemaphoreGive(s_agent.mutex);
        free(response); free(session_id); free(batch_id); free(message_id);
        refresh_depth_metrics();
        persist_metrics();
        return is_start ? SEND_PERMANENT : SEND_PROGRESS;
    }
    if (is_start) {
        synap_schedule_t schedule;
        err = synap_payload_parse_start_config(response, &schedule);
        if (err != ESP_OK) {
            free(response); free(session_id); free(batch_id); free(message_id);
            copy_last_error("INVALID_START_CONFIG");
            return SEND_PERMANENT;
        }
        if (strcmp(session_id, s_agent.current_session_id) == 0) {
            s_agent.schedule = schedule;
            s_agent.current_start_accepted = true;
            s_agent.schedule_started_ms = monotonic_ms();
            s_agent.next_collect_ms = s_agent.schedule_started_ms;
            s_agent.next_upload_ms = s_agent.schedule_started_ms;
        }
        err = synap_store_ack_head();
    } else {
        bool accepted = false, duplicate = false, retryable = false;
        char *reason = NULL;
        err = synap_payload_classify_ack(response, batch_id, message_id,
                                         &accepted, &duplicate, &retryable,
                                         &reason);
        if (err != ESP_OK || retryable) {
            xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
            s_agent.metrics.upload_retryable_total++;
            xSemaphoreGive(s_agent.mutex);
            free(reason); free(response); free(session_id); free(batch_id);
            free(message_id); persist_metrics();
            return SEND_RETRY;
        }
        if (accepted || duplicate) {
            err = synap_store_ack_head();
            xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
            if (accepted) s_agent.metrics.upload_accepted_total++;
            if (duplicate) s_agent.metrics.upload_duplicate_total++;
            xSemaphoreGive(s_agent.mutex);
        } else {
            err = synap_store_reject_head(reason);
            xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
            s_agent.metrics.upload_rejected_total++;
            xSemaphoreGive(s_agent.mutex);
        }
        free(reason);
    }
    free(response); free(session_id); free(batch_id); free(message_id);
    refresh_depth_metrics();
    persist_metrics();
    if (err != ESP_OK) {
        copy_last_error("QUEUE_COMMIT_FAILED");
        return SEND_RETRY;
    }
    copy_last_error("");
    return SEND_PROGRESS;
}

static void wait_interruptible(uint32_t milliseconds)
{
    xEventGroupWaitBits(s_agent.events, AGENT_STOP_BIT, pdFALSE, pdFALSE,
                        pdMS_TO_TICKS(milliseconds));
}

static void worker(void *argument)
{
    (void) argument;
    bool start_enqueued = false;
    while ((xEventGroupGetBits(s_agent.events) & AGENT_STOP_BIT) == 0) {
        if (!network_ready()) {
            xEventGroupClearBits(s_agent.events, AGENT_NETWORK_BIT);
            set_state(SYNAP_AGENT_STATE_WAITING_NETWORK, ESP_OK, NULL);
            xEventGroupWaitBits(s_agent.events, AGENT_NETWORK_BIT | AGENT_STOP_BIT,
                                pdFALSE, pdFALSE, pdMS_TO_TICKS(1000));
            continue;
        }
        xEventGroupSetBits(s_agent.events, AGENT_NETWORK_BIT);
        if (!clock_ready()) {
            set_state(SYNAP_AGENT_STATE_WAITING_TIME, ESP_OK, NULL);
            wait_interruptible(1000);
            continue;
        }
        if (!start_enqueued) {
            esp_err_t err = enqueue_current_start();
            if (err == ESP_ERR_NO_MEM) {
                /* A full persisted spool must be drained before appending the
                 * new boot's StartData; otherwise a cold reboot can deadlock. */
                send_result_t drain = send_head();
                if (drain == SEND_PROGRESS) continue;
                if (drain == SEND_RETRY) {
                    set_state(SYNAP_AGENT_STATE_BACKOFF, ESP_FAIL,
                              s_agent.metrics.last_error);
                    uint32_t jitter = esp_random() %
                                      (s_agent.retry_delay_ms / 2 + 1);
                    wait_interruptible(s_agent.retry_delay_ms + jitter);
                    if (s_agent.retry_delay_ms < 300000) {
                        s_agent.retry_delay_ms *= 2;
                        if (s_agent.retry_delay_ms > 300000) {
                            s_agent.retry_delay_ms = 300000;
                        }
                    }
                    continue;
                }
                err = drain == SEND_EMPTY ? ESP_ERR_INVALID_STATE : ESP_FAIL;
            }
            if (err != ESP_OK) {
                set_state(SYNAP_AGENT_STATE_PERMANENT_ERROR, err,
                          "START_QUEUE_FAILED");
                break;
            }
            start_enqueued = true;
        }

        int64_t now = monotonic_ms();
        if (s_agent.current_start_accepted && s_agent.schedule.enabled) {
            int64_t collect_until = s_agent.schedule_started_ms +
                (int64_t) (s_agent.schedule.collect_for_seconds * 1000.0);
            if (now <= collect_until && now >= s_agent.next_collect_ms) {
                esp_err_t err = collect_facts();
                if (err != ESP_OK && err != ESP_ERR_NO_MEM) {
                    set_state(SYNAP_AGENT_STATE_PERMANENT_ERROR, err,
                              "COLLECTION_FAILED");
                    break;
                }
                s_agent.next_collect_ms = now +
                    (int64_t) (s_agent.schedule.collect_every_seconds * 1000.0);
            }
        }

        bool upload_allowed = !s_agent.current_start_accepted;
        if (s_agent.current_start_accepted) {
            int64_t upload_until = s_agent.schedule_started_ms +
                (int64_t) (s_agent.schedule.upload_for_seconds * 1000.0);
            upload_allowed = now <= upload_until && now >= s_agent.next_upload_ms;
        }
        if (!upload_allowed) {
            wait_interruptible(250);
            continue;
        }
        send_result_t result = send_head();
        if (result == SEND_RETRY) {
            set_state(SYNAP_AGENT_STATE_BACKOFF, ESP_FAIL,
                      s_agent.metrics.last_error);
            uint32_t jitter = esp_random() % (s_agent.retry_delay_ms / 2 + 1);
            wait_interruptible(s_agent.retry_delay_ms + jitter);
            if (s_agent.retry_delay_ms < 300000) {
                s_agent.retry_delay_ms *= 2;
                if (s_agent.retry_delay_ms > 300000) s_agent.retry_delay_ms = 300000;
            }
            continue;
        }
        if (result == SEND_PERMANENT) {
            set_state(SYNAP_AGENT_STATE_PERMANENT_ERROR, ESP_FAIL,
                      s_agent.metrics.last_error);
            break;
        }
        s_agent.retry_delay_ms = 1000;
        if (s_agent.current_start_accepted) {
            s_agent.next_upload_ms = monotonic_ms() +
                (int64_t) (s_agent.schedule.upload_every_seconds * 1000.0);
            set_state(SYNAP_AGENT_STATE_ONLINE, ESP_OK, NULL);
        }
        if (result == SEND_EMPTY) wait_interruptible(250);
    }
    persist_metrics();
    set_state(SYNAP_AGENT_STATE_STOPPED, ESP_OK, NULL);
    s_agent.running = false;
    s_agent.task = NULL;
    xEventGroupSetBits(s_agent.events, AGENT_DONE_BIT);
    vTaskDelete(NULL);
}

esp_err_t synap_agent_start(void)
{
    if (!s_agent.initialized) return ESP_ERR_INVALID_STATE;
    if (s_agent.running) return ESP_OK;
    xEventGroupClearBits(s_agent.events, AGENT_STOP_BIT | AGENT_DONE_BIT);
    s_agent.running = true;
    BaseType_t created = xTaskCreate(worker, "synap-agent", CONFIG_SYNAP_WORKER_STACK_SIZE,
                                     NULL, 5, &s_agent.task);
    if (created != pdPASS) {
        s_agent.running = false;
        s_agent.task = NULL;
        return ESP_ERR_NO_MEM;
    }
    return ESP_OK;
}

esp_err_t synap_agent_stop(TickType_t timeout)
{
    if (!s_agent.initialized) return ESP_ERR_INVALID_STATE;
    if (!s_agent.running) return ESP_OK;
    xEventGroupSetBits(s_agent.events, AGENT_STOP_BIT);
    EventBits_t bits = xEventGroupWaitBits(s_agent.events, AGENT_DONE_BIT,
                                           pdFALSE, pdTRUE, timeout);
    return (bits & AGENT_DONE_BIT) != 0 ? ESP_OK : ESP_ERR_TIMEOUT;
}

esp_err_t synap_agent_get_metrics(synap_agent_metrics_t *metrics)
{
    if (!s_agent.initialized || metrics == NULL) return ESP_ERR_INVALID_ARG;
    xSemaphoreTake(s_agent.mutex, portMAX_DELAY);
    *metrics = s_agent.metrics;
    xSemaphoreGive(s_agent.mutex);
    return ESP_OK;
}

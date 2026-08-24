#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "esp_err.h"
#include "freertos/FreeRTOS.h"

#ifdef __cplusplus
extern "C" {
#endif

#define SYNAP_AGENT_SDK_VERSION "1.0.0"
#define SYNAP_AGENT_PLATFORM "esp32"
#define SYNAP_AGENT_DEVICE_TYPE "ESP32"

typedef enum {
    SYNAP_AGENT_STATE_IDLE = 0,
    SYNAP_AGENT_STATE_WAITING_NETWORK,
    SYNAP_AGENT_STATE_WAITING_TIME,
    SYNAP_AGENT_STATE_ENROLLING,
    SYNAP_AGENT_STATE_STARTING,
    SYNAP_AGENT_STATE_ONLINE,
    SYNAP_AGENT_STATE_BACKOFF,
    SYNAP_AGENT_STATE_STOPPED,
    SYNAP_AGENT_STATE_PERMANENT_ERROR,
} synap_agent_state_t;

typedef struct {
    uint32_t queue_depth;
    uint32_t dead_letter_depth;
    uint64_t queued_total;
    uint64_t dropped_total;
    uint64_t upload_accepted_total;
    uint64_t upload_duplicate_total;
    uint64_t upload_retryable_total;
    uint64_t upload_rejected_total;
    uint64_t handshake_total;
    uint64_t reconnect_total;
    int32_t last_http_status;
    char last_error[48];
} synap_agent_metrics_t;

typedef void (*synap_agent_state_callback_t)(synap_agent_state_t state,
                                              esp_err_t error,
                                              const char *detail,
                                              void *context);

typedef struct {
    const char *provision_partition;
    const char *runtime_partition;
    const char *spool_partition;
    const char *dead_letter_partition;
    uint16_t queue_max_records;
    uint16_t dead_letter_max_records;
    size_t queue_max_record_bytes;
    synap_agent_state_callback_t state_callback;
    void *callback_context;
} synap_agent_config_t;

/** Populate safe defaults from Kconfig. The returned strings have static life. */
synap_agent_config_t synap_agent_default_config(void);

/** Initialize storage, provisioning, identity and network event observers. */
esp_err_t synap_agent_init(const synap_agent_config_t *config);

/** Start the non-blocking worker. Wi-Fi and SNTP remain owned by the host app. */
esp_err_t synap_agent_start(void);

/** Request shutdown and wait for at most timeout ticks. */
esp_err_t synap_agent_stop(TickType_t timeout);

/** Return an internally consistent metrics snapshot. */
esp_err_t synap_agent_get_metrics(synap_agent_metrics_t *metrics);

#ifdef __cplusplus
}
#endif


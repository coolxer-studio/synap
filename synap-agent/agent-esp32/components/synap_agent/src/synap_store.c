#include "synap_internal.h"

#include <inttypes.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "esp_log.h"
#include "nvs.h"

static const char *TAG = "synap-store";

typedef struct {
    char spool_partition[16];
    char dead_partition[16];
    uint16_t max_records;
    uint16_t max_dead_records;
    size_t max_record_bytes;
    uint32_t head;
    uint32_t tail;
    uint32_t count;
    uint32_t dead_tail;
    uint32_t dead_count;
} store_state_t;

static store_state_t s_store;

static void item_key(char key[16], char prefix, uint32_t sequence)
{
    snprintf(key, 16, "%c%08" PRIx32, prefix, sequence);
}

static esp_err_t load_u32(nvs_handle_t handle, const char *key, uint32_t *value)
{
    esp_err_t err = nvs_get_u32(handle, key, value);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        *value = 0;
        return ESP_OK;
    }
    return err;
}

static esp_err_t save_queue_meta(nvs_handle_t handle)
{
    esp_err_t err = nvs_set_u32(handle, "head", s_store.head);
    if (err == ESP_OK) err = nvs_set_u32(handle, "tail", s_store.tail);
    if (err == ESP_OK) err = nvs_set_u32(handle, "count", s_store.count);
    if (err == ESP_OK) err = nvs_commit(handle);
    return err;
}

esp_err_t synap_store_init(const synap_agent_config_t *config)
{
    if (config == NULL || config->spool_partition == NULL ||
        config->dead_letter_partition == NULL || config->queue_max_records == 0 ||
        config->dead_letter_max_records == 0 ||
        config->queue_max_record_bytes == 0) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(&s_store, 0, sizeof(s_store));
    strlcpy(s_store.spool_partition, config->spool_partition,
            sizeof(s_store.spool_partition));
    strlcpy(s_store.dead_partition, config->dead_letter_partition,
            sizeof(s_store.dead_partition));
    s_store.max_records = config->queue_max_records;
    s_store.max_dead_records = config->dead_letter_max_records;
    s_store.max_record_bytes = config->queue_max_record_bytes;

    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.spool_partition, "queue",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }
    if ((err = load_u32(handle, "head", &s_store.head)) == ESP_OK &&
        (err = load_u32(handle, "tail", &s_store.tail)) == ESP_OK) {
        err = load_u32(handle, "count", &s_store.count);
    }
    nvs_close(handle);
    if (err != ESP_OK) {
        return err;
    }
    if (s_store.count > s_store.max_records) {
        ESP_LOGE(TAG, "queue metadata exceeds configured capacity");
        return ESP_ERR_INVALID_STATE;
    }

    err = nvs_open_from_partition(s_store.dead_partition, "dead", NVS_READWRITE,
                                  &handle);
    if (err != ESP_OK) {
        return err;
    }
    if ((err = load_u32(handle, "tail", &s_store.dead_tail)) == ESP_OK) {
        err = load_u32(handle, "count", &s_store.dead_count);
    }
    nvs_close(handle);
    if (err == ESP_OK && s_store.dead_count > s_store.max_dead_records) {
        s_store.dead_count = s_store.max_dead_records;
    }
    return err;
}

esp_err_t synap_store_enqueue(const char *json, size_t length)
{
    if (json == NULL || length == 0 || length > s_store.max_record_bytes) {
        return length > s_store.max_record_bytes ? ESP_ERR_INVALID_SIZE
                                                  : ESP_ERR_INVALID_ARG;
    }
    if (s_store.count >= s_store.max_records) {
        return ESP_ERR_NO_MEM;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.spool_partition, "queue",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;
    char key[16];
    item_key(key, 'q', s_store.tail);
    err = nvs_set_blob(handle, key, json, length + 1);
    if (err == ESP_OK) {
        s_store.tail++;
        s_store.count++;
        err = save_queue_meta(handle);
        if (err != ESP_OK) {
            s_store.tail--;
            s_store.count--;
        }
    }
    nvs_close(handle);
    if (err == ESP_ERR_NVS_NOT_ENOUGH_SPACE) err = ESP_ERR_NO_MEM;
    return err;
}

esp_err_t synap_store_peek(synap_queue_item_t *item)
{
    if (item == NULL) return ESP_ERR_INVALID_ARG;
    memset(item, 0, sizeof(*item));
    if (s_store.count == 0) return ESP_ERR_NOT_FOUND;
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.spool_partition, "queue",
                                             NVS_READONLY, &handle);
    if (err != ESP_OK) return err;
    char key[16];
    item_key(key, 'q', s_store.head);
    size_t length = 0;
    err = nvs_get_blob(handle, key, NULL, &length);
    if (err == ESP_OK && length > 1 && length <= s_store.max_record_bytes + 1) {
        item->json = calloc(1, length);
        if (item->json == NULL) {
            err = ESP_ERR_NO_MEM;
        } else {
            err = nvs_get_blob(handle, key, item->json, &length);
            item->length = length - 1;
            item->sequence = s_store.head;
        }
    } else if (err == ESP_OK) {
        err = ESP_ERR_INVALID_SIZE;
    }
    nvs_close(handle);
    if (err != ESP_OK) synap_store_free_item(item);
    return err;
}

esp_err_t synap_store_replace_head(const char *json, size_t length)
{
    if (s_store.count == 0 || json == NULL || length == 0 ||
        length > s_store.max_record_bytes) {
        return ESP_ERR_INVALID_ARG;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.spool_partition, "queue",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;
    char key[16];
    item_key(key, 'q', s_store.head);
    err = nvs_set_blob(handle, key, json, length + 1);
    if (err == ESP_OK) err = nvs_commit(handle);
    nvs_close(handle);
    return err;
}

esp_err_t synap_store_ack_head(void)
{
    if (s_store.count == 0) return ESP_ERR_NOT_FOUND;
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.spool_partition, "queue",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;
    char key[16];
    item_key(key, 'q', s_store.head);
    err = nvs_erase_key(handle, key);
    if (err == ESP_OK) {
        s_store.head++;
        s_store.count--;
        err = save_queue_meta(handle);
        if (err != ESP_OK) {
            s_store.head--;
            s_store.count++;
        }
    }
    nvs_close(handle);
    return err;
}

static esp_err_t append_dead_letter(const char *json, const char *reason)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(s_store.dead_partition, "dead",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;
    cJSON *record = cJSON_CreateObject();
    cJSON_AddStringToObject(record, "reason", reason == NULL ? "rejected" : reason);
    cJSON *original = cJSON_Parse(json);
    if (original != NULL) cJSON_AddItemToObject(record, "record", original);
    char *encoded = cJSON_PrintUnformatted(record);
    cJSON_Delete(record);
    if (encoded == NULL) {
        nvs_close(handle);
        return ESP_ERR_NO_MEM;
    }
    uint32_t slot = s_store.dead_tail % s_store.max_dead_records;
    char key[16];
    item_key(key, 'd', slot);
    err = nvs_set_blob(handle, key, encoded, strlen(encoded) + 1);
    free(encoded);
    if (err == ESP_OK) {
        s_store.dead_tail++;
        if (s_store.dead_count < s_store.max_dead_records) s_store.dead_count++;
        err = nvs_set_u32(handle, "tail", s_store.dead_tail);
    }
    if (err == ESP_OK) err = nvs_set_u32(handle, "count", s_store.dead_count);
    if (err == ESP_OK) err = nvs_commit(handle);
    nvs_close(handle);
    return err;
}

esp_err_t synap_store_reject_head(const char *reason)
{
    synap_queue_item_t item;
    esp_err_t err = synap_store_peek(&item);
    if (err != ESP_OK) return err;
    err = append_dead_letter(item.json, reason);
    synap_store_free_item(&item);
    if (err == ESP_OK) err = synap_store_ack_head();
    return err;
}

uint32_t synap_store_queue_depth(void) { return s_store.count; }
uint32_t synap_store_dead_letter_depth(void) { return s_store.dead_count; }

void synap_store_free_item(synap_queue_item_t *item)
{
    if (item != NULL) {
        free(item->json);
        memset(item, 0, sizeof(*item));
    }
}

esp_err_t synap_store_reserve_business_sequence(const char *runtime_partition,
                                                 uint64_t *sequence)
{
    if (runtime_partition == NULL || sequence == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(runtime_partition, "agent",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) {
        return err;
    }
    uint64_t next = 2;
    err = nvs_get_u64(handle, "business_seq", &next);
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        err = ESP_OK;
        next = 2;
    }
    if (err == ESP_OK && (next < 2 || next >= 9007199254740991ULL)) {
        err = ESP_ERR_INVALID_STATE;
    }
    if (err == ESP_OK) {
        err = nvs_set_u64(handle, "business_seq", next + 1);
    }
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    if (err == ESP_OK) {
        *sequence = next;
    }
    return err;
}

esp_err_t synap_store_load_or_create_identity(const char *runtime_partition,
                                               const char *app_id,
                                               mbedtls_pk_context *key,
                                               uint8_t **spki,
                                               size_t *spki_len)
{
    if (runtime_partition == NULL || app_id == NULL || key == NULL ||
        spki == NULL || spki_len == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(runtime_partition, "identity",
                                             NVS_READWRITE, &handle);
    if (err != ESP_OK) return err;

    size_t stored_app_len = 0;
    err = nvs_get_str(handle, "app_id", NULL, &stored_app_len);
    if (err == ESP_OK) {
        char *stored_app = calloc(1, stored_app_len);
        if (stored_app == NULL) {
            nvs_close(handle);
            return ESP_ERR_NO_MEM;
        }
        err = nvs_get_str(handle, "app_id", stored_app, &stored_app_len);
        bool matches = err == ESP_OK && strcmp(stored_app, app_id) == 0;
        free(stored_app);
        if (!matches) {
            nvs_close(handle);
            return ESP_ERR_INVALID_STATE;
        }
        size_t private_len = 0;
        err = nvs_get_blob(handle, "private_der", NULL, &private_len);
        uint8_t *private_der = NULL;
        if (err == ESP_OK && private_len > 0 && private_len <= 512) {
            private_der = malloc(private_len);
            if (private_der == NULL) err = ESP_ERR_NO_MEM;
        }
        if (err == ESP_OK) err = nvs_get_blob(handle, "private_der", private_der,
                                               &private_len);
        if (err == ESP_OK) err = synap_crypto_import_private(private_der,
                                                             private_len, key);
        if (private_der != NULL) {
            memset(private_der, 0, private_len);
            free(private_der);
        }
        if (err == ESP_OK) err = synap_crypto_export_spki(key, spki, spki_len);
        nvs_close(handle);
        return err;
    }
    if (err != ESP_ERR_NVS_NOT_FOUND) {
        nvs_close(handle);
        return err;
    }

    err = synap_crypto_generate_key(key);
    uint8_t *private_der = NULL;
    size_t private_len = 0;
    if (err == ESP_OK) err = synap_crypto_export_private(key, &private_der,
                                                         &private_len);
    if (err == ESP_OK) err = synap_crypto_export_spki(key, spki, spki_len);
    if (err == ESP_OK) err = nvs_set_str(handle, "app_id", app_id);
    if (err == ESP_OK) err = nvs_set_blob(handle, "private_der", private_der,
                                          private_len);
    if (err == ESP_OK) err = nvs_commit(handle);
    if (private_der != NULL) {
        memset(private_der, 0, private_len);
        free(private_der);
    }
    nvs_close(handle);
    return err;
}

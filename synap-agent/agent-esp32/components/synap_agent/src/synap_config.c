#include "synap_internal.h"

#include <stdlib.h>
#include <string.h>

#include "nvs.h"

static esp_err_t read_string(nvs_handle_t handle, const char *key, bool required,
                             char **out)
{
    size_t length = 0;
    esp_err_t err = nvs_get_str(handle, key, NULL, &length);
    if (err == ESP_ERR_NVS_NOT_FOUND && !required) {
        *out = NULL;
        return ESP_OK;
    }
    if (err != ESP_OK || length < 2 || length > 8192) {
        return err == ESP_OK ? ESP_ERR_INVALID_SIZE : err;
    }
    char *value = calloc(1, length);
    if (value == NULL) {
        return ESP_ERR_NO_MEM;
    }
    err = nvs_get_str(handle, key, value, &length);
    if (err != ESP_OK) {
        free(value);
        return err;
    }
    *out = value;
    return ESP_OK;
}

void synap_config_free(synap_provision_t *config)
{
    if (config == NULL) {
        return;
    }
    char **fields[] = {
        &config->base_url, &config->tls_ca_pem, &config->sc_app_id,
        &config->server_key_id,
        &config->server_trust_spki, &config->business_app_id,
        &config->device_id, &config->device_name,
    };
    for (size_t index = 0; index < sizeof(fields) / sizeof(fields[0]); ++index) {
        if (*fields[index] != NULL) {
            free(*fields[index]);
            *fields[index] = NULL;
        }
    }
}

esp_err_t synap_config_load(const char *partition, synap_provision_t *out)
{
    if (partition == NULL || out == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(out, 0, sizeof(*out));
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(partition, "synap", NVS_READONLY,
                                             &handle);
    if (err != ESP_OK) {
        return err;
    }

#define READ_FIELD(key, field, required)                                      \
    do {                                                                       \
        err = read_string(handle, key, required, &out->field);                 \
        if (err != ESP_OK) {                                                   \
            goto cleanup;                                                      \
        }                                                                      \
    } while (0)

    READ_FIELD("server_url", base_url, true);
    READ_FIELD("tls_ca", tls_ca_pem, false);
    READ_FIELD("sc_app_id", sc_app_id, true);
    READ_FIELD("server_kid", server_key_id, true);
    READ_FIELD("trust_spki", server_trust_spki, true);
    READ_FIELD("business_app", business_app_id, true);
    READ_FIELD("device_id", device_id, true);
    READ_FIELD("device_name", device_name, false);

#undef READ_FIELD

    if (out->device_name == NULL) {
        out->device_name = strdup("synap-esp32");
        if (out->device_name == NULL) {
            err = ESP_ERR_NO_MEM;
            goto cleanup;
        }
    }
    if (strlen(out->device_id) < 32 || strlen(out->device_id) > 64 ||
        strlen(out->sc_app_id) > 128 || strlen(out->business_app_id) > 64 ||
        strlen(out->server_key_id) > 128) {
        err = ESP_ERR_INVALID_SIZE;
        goto cleanup;
    }

cleanup:
    nvs_close(handle);
    if (err != ESP_OK) {
        synap_config_free(out);
    }
    return err;
}

esp_err_t synap_config_read_enrollment_token(const char *partition,
                                              char **token)
{
    if (partition == NULL || token == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    *token = NULL;
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(partition, "synap", NVS_READONLY,
                                             &handle);
    if (err != ESP_OK) {
        return err;
    }
    err = read_string(handle, "enroll_token", false, token);
    nvs_close(handle);
    return err;
}

esp_err_t synap_config_clear_enrollment_token(const char *partition)
{
    if (partition == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(partition, "synap", NVS_READWRITE,
                                             &handle);
    if (err != ESP_OK) {
        return err;
    }
    err = nvs_erase_key(handle, "enroll_token");
    if (err == ESP_ERR_NVS_NOT_FOUND) {
        err = ESP_OK;
    }
    if (err == ESP_OK) {
        err = nvs_commit(handle);
    }
    nvs_close(handle);
    return err;
}

#include <stdlib.h>
#include <string.h>

#include "esp_event.h"
#include "esp_log.h"
#include "esp_netif.h"
#include "esp_sntp.h"
#include "esp_wifi.h"
#include "nvs.h"
#include "nvs_flash.h"
#include "synap_agent.h"

static const char *TAG = "synap-example";

static esp_err_t read_provision_string(const char *key, char **output)
{
    nvs_handle_t handle;
    esp_err_t err = nvs_open_from_partition(CONFIG_SYNAP_PROVISION_PARTITION,
                                             "synap", NVS_READONLY, &handle);
    if (err != ESP_OK) return err;
    size_t length = 0;
    err = nvs_get_str(handle, key, NULL, &length);
    char *value = NULL;
    if (err == ESP_OK && length > 1 && length <= 128) {
        value = calloc(1, length);
        if (value == NULL) err = ESP_ERR_NO_MEM;
    }
    if (err == ESP_OK) err = nvs_get_str(handle, key, value, &length);
    nvs_close(handle);
    if (err != ESP_OK) free(value);
    else *output = value;
    return err;
}

static void wifi_event(void *argument, esp_event_base_t base, int32_t event_id,
                       void *event_data)
{
    (void) argument;
    (void) event_data;
    if (base == WIFI_EVENT && (event_id == WIFI_EVENT_STA_START ||
                               event_id == WIFI_EVENT_STA_DISCONNECTED)) {
        esp_wifi_connect();
    }
}

static esp_err_t start_wifi(void)
{
    char *ssid = NULL;
    char *password = NULL;
    esp_err_t err = read_provision_string("wifi_ssid", &ssid);
    if (err == ESP_OK) err = read_provision_string("wifi_pass", &password);
    if (err != ESP_OK) {
        free(ssid); free(password);
        return err;
    }
    if (esp_netif_create_default_wifi_sta() == NULL) {
        err = ESP_ERR_NO_MEM;
    }
    wifi_init_config_t init = WIFI_INIT_CONFIG_DEFAULT();
    if (err == ESP_OK) err = esp_wifi_init(&init);
    if (err == ESP_OK) {
        err = esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID,
                                         wifi_event, NULL);
    }
    wifi_config_t config = {0};
    if (strlen(ssid) > sizeof(config.sta.ssid) - 1 ||
        strlen(password) > sizeof(config.sta.password) - 1) {
        err = ESP_ERR_INVALID_SIZE;
    }
    if (err == ESP_OK) {
        strlcpy((char *) config.sta.ssid, ssid, sizeof(config.sta.ssid));
        strlcpy((char *) config.sta.password, password,
                sizeof(config.sta.password));
        config.sta.threshold.authmode = WIFI_AUTH_WPA2_PSK;
        config.sta.pmf_cfg.capable = true;
        config.sta.pmf_cfg.required = false;
        err = esp_wifi_set_mode(WIFI_MODE_STA);
    }
    if (err == ESP_OK) err = esp_wifi_set_config(WIFI_IF_STA, &config);
    if (err == ESP_OK) err = esp_wifi_start();
    memset(password, 0, strlen(password));
    free(password);
    free(ssid);
    return err;
}

static void state_changed(synap_agent_state_t state, esp_err_t error,
                          const char *detail, void *context)
{
    (void) context;
    if (error == ESP_OK) ESP_LOGI(TAG, "agent state=%d", state);
    else ESP_LOGE(TAG, "agent state=%d error=%s detail=%s", state,
                  esp_err_to_name(error), detail == NULL ? "" : detail);
}

void app_main(void)
{
    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    synap_agent_config_t config = synap_agent_default_config();
    config.state_callback = state_changed;
    ESP_ERROR_CHECK(synap_agent_init(&config));

    ESP_ERROR_CHECK(start_wifi());
    esp_sntp_setoperatingmode(SNTP_OPMODE_POLL);
    esp_sntp_setservername(0, "pool.ntp.org");
    esp_sntp_init();

    ESP_ERROR_CHECK(synap_agent_start());
}

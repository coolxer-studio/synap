idf_component_register(
    SRCS
        "src/synap_agent.c"
        "src/synap_config.c"
        "src/synap_crypto.c"
        "src/synap_payload.c"
        "src/synap_sc.c"
        "src/synap_store.c"
    INCLUDE_DIRS "include"
    PRIV_INCLUDE_DIRS "private"
    REQUIRES
        app_update
        bootloader_support
        esp_app_format
        esp_http_client
        esp_netif
        esp_partition
        esp_system
        esp_timer
        esp_wifi
        json
        mbedtls
        nvs_flash
        spi_flash
)

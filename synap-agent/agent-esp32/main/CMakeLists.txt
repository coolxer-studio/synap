idf_component_register(
    SRCS "app_main.c"
    INCLUDE_DIRS "."
    REQUIRES synap_agent esp_wifi esp_netif esp_event lwip nvs_flash
)

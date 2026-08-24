idf_component_register(
    SRCS "test_main.c" "test_crypto.c" "test_payload.c" "test_store.c"
    INCLUDE_DIRS "."
    PRIV_INCLUDE_DIRS "../../../components/synap_agent/private"
    REQUIRES synap_agent unity nvs_flash
)

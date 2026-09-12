#include <stdlib.h>
#include <string.h>

#include "nvs_flash.h"
#include "synap_internal.h"
#include "unity.h"

static synap_agent_config_t test_config(void)
{
    synap_agent_config_t config = synap_agent_default_config();
    config.provision_partition = "test_provision";
    config.runtime_partition = "test_runtime";
    config.spool_partition = "test_spool";
    config.dead_letter_partition = "test_dead";
    config.queue_max_records = 2;
    config.dead_letter_max_records = 2;
    return config;
}

static void reset_test_storage(void)
{
    TEST_ESP_OK(nvs_flash_erase_partition("test_runtime"));
    TEST_ESP_OK(nvs_flash_erase_partition("test_spool"));
    TEST_ESP_OK(nvs_flash_erase_partition("test_dead"));
    TEST_ESP_OK(nvs_flash_init_partition("test_runtime"));
    TEST_ESP_OK(nvs_flash_init_partition("test_spool"));
    TEST_ESP_OK(nvs_flash_init_partition("test_dead"));
}

TEST_CASE("NVS queue is bounded and preserves FIFO records", "[synap][store]")
{
    reset_test_storage();
    synap_agent_config_t config = test_config();
    TEST_ESP_OK(synap_store_init(&config));
    TEST_ESP_OK(synap_store_enqueue("{\"id\":1}", 8));
    TEST_ESP_OK(synap_store_enqueue("{\"id\":2}", 8));
    TEST_ASSERT_EQUAL(ESP_ERR_NO_MEM,
                      synap_store_enqueue("{\"id\":3}", 8));
    synap_queue_item_t item;
    TEST_ESP_OK(synap_store_peek(&item));
    TEST_ASSERT_EQUAL_STRING("{\"id\":1}", item.json);
    synap_store_free_item(&item);
    TEST_ESP_OK(synap_store_ack_head());
    TEST_ESP_OK(synap_store_peek(&item));
    TEST_ASSERT_EQUAL_STRING("{\"id\":2}", item.json);
    synap_store_free_item(&item);
    TEST_ASSERT_EQUAL_UINT32(1, synap_store_queue_depth());
}

TEST_CASE("installation identity survives NVS reload", "[synap][store]")
{
    reset_test_storage();
    mbedtls_pk_context first;
    uint8_t *first_spki = NULL;
    size_t first_len = 0;
    TEST_ESP_OK(synap_store_load_or_create_identity(
        "test_runtime", "synap-test", &first, &first_spki, &first_len));
    mbedtls_pk_context second;
    uint8_t *second_spki = NULL;
    size_t second_len = 0;
    TEST_ESP_OK(synap_store_load_or_create_identity(
        "test_runtime", "synap-test", &second, &second_spki, &second_len));
    TEST_ASSERT_EQUAL_UINT(first_len, second_len);
    TEST_ASSERT_EQUAL_MEMORY(first_spki, second_spki, first_len);
    free(first_spki);
    free(second_spki);
    mbedtls_pk_free(&first);
    mbedtls_pk_free(&second);
}

TEST_CASE("business sequence is reserved durably", "[synap][store]")
{
    reset_test_storage();
    uint64_t first = 0;
    uint64_t second = 0;
    TEST_ESP_OK(synap_store_reserve_business_sequence("test_runtime", &first));
    TEST_ESP_OK(synap_store_reserve_business_sequence("test_runtime", &second));
    TEST_ASSERT_EQUAL_UINT64(2, first);
    TEST_ASSERT_EQUAL_UINT64(3, second);

    TEST_ESP_OK(nvs_flash_deinit_partition("test_runtime"));
    TEST_ESP_OK(nvs_flash_init_partition("test_runtime"));
    uint64_t after_reload = 0;
    TEST_ESP_OK(synap_store_reserve_business_sequence("test_runtime",
                                                       &after_reload));
    TEST_ASSERT_EQUAL_UINT64(4, after_reload);
}

#include <stdlib.h>
#include <string.h>

#include "synap_internal.h"
#include "unity.h"

TEST_CASE("protocol request AES-GCM vector matches", "[synap][crypto]")
{
    static const uint8_t KEY[32] = {
        0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
        0x08,0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,
        0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
        0x18,0x19,0x1a,0x1b,0x1c,0x1d,0x1e,0x1f,
    };
    static const uint8_t NONCE[12] = {
        0xa0,0xa1,0xa2,0xa3,0,0,0,0,0,0,0,1,
    };
    static const char AAD[] =
        "SC1\nrequest\nP256_HKDF_SHA256_AES256_GCM\n"
        "test-key-2026-01\ntest-session-0001\n1785283200000\n1\n"
        "request-0001\nPOST\n/sc/v1/message\napplication/sc-protected+json";
    static const char PLAINTEXT[] = "{\"message\":\"你好🌍\"}";
    static const char EXPECTED[] =
        "y43V6x8Hp9N--JdIn9atb-inacLk7T5Q9B7Jzez-vufGdNrX0f2xKw";
    uint8_t *cipher = NULL;
    size_t cipher_len = 0;
    TEST_ESP_OK(synap_crypto_gcm_encrypt(KEY, NONCE,
        (const uint8_t *) AAD, strlen(AAD),
        (const uint8_t *) PLAINTEXT, strlen(PLAINTEXT),
        &cipher, &cipher_len));
    char *encoded = NULL;
    TEST_ESP_OK(synap_crypto_base64url_encode(cipher, cipher_len, &encoded));
    TEST_ASSERT_EQUAL_STRING(EXPECTED, encoded);
    uint8_t *opened = NULL;
    size_t opened_len = 0;
    TEST_ESP_OK(synap_crypto_gcm_decrypt(KEY, NONCE,
        (const uint8_t *) AAD, strlen(AAD), cipher, cipher_len,
        &opened, &opened_len));
    TEST_ASSERT_EQUAL_UINT(strlen(PLAINTEXT), opened_len);
    TEST_ASSERT_EQUAL_MEMORY(PLAINTEXT, opened, opened_len);
    free(cipher);
    free(encoded);
    free(opened);
}

TEST_CASE("P-256 installation identity round trips and signs", "[synap][crypto]")
{
    mbedtls_pk_context first;
    TEST_ESP_OK(synap_crypto_generate_key(&first));
    uint8_t *private_der = NULL;
    size_t private_len = 0;
    TEST_ESP_OK(synap_crypto_export_private(&first, &private_der, &private_len));
    mbedtls_pk_context restored;
    TEST_ESP_OK(synap_crypto_import_private(private_der, private_len, &restored));
    uint8_t *spki = NULL;
    size_t spki_len = 0;
    TEST_ESP_OK(synap_crypto_export_spki(&restored, &spki, &spki_len));
    uint8_t transcript_hash[32];
    TEST_ESP_OK(synap_crypto_sha256((const uint8_t *) "transcript", 10,
                                    transcript_hash));
    uint8_t signature[64];
    TEST_ESP_OK(synap_crypto_sign_p1363(&restored, transcript_hash, signature));
    TEST_ESP_OK(synap_crypto_verify_p1363(spki, spki_len, transcript_hash,
                                          signature));
    signature[0] ^= 1;
    TEST_ASSERT_NOT_EQUAL(ESP_OK, synap_crypto_verify_p1363(
        spki, spki_len, transcript_hash, signature));
    memset(private_der, 0, private_len);
    free(private_der);
    free(spki);
    mbedtls_pk_free(&first);
    mbedtls_pk_free(&restored);
}

TEST_CASE("HKDF protocol material matches fixed vector", "[synap][crypto]")
{
    static const uint8_t SECRET[32] = {
        0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07,
        0x08,0x09,0x0a,0x0b,0x0c,0x0d,0x0e,0x0f,
        0x10,0x11,0x12,0x13,0x14,0x15,0x16,0x17,
        0x18,0x19,0x1a,0x1b,0x1c,0x1d,0x1e,0x1f,
    };
    static const uint8_t SALT[32] = {
        0x51,0x72,0x45,0x6c,0x43,0x6f,0x81,0xc1,
        0x92,0xf8,0x42,0xf2,0x89,0xd1,0xa9,0x1c,
        0x7f,0x9c,0xe7,0xcf,0x59,0xce,0xce,0x0b,
        0x6d,0x69,0xa2,0x53,0x2a,0xc4,0x10,0x51,
    };
    static const uint8_t EXPECTED[72] = {
        0x22,0x73,0x5b,0x9e,0x62,0xe2,0x4a,0x9f,
        0x9a,0x9f,0x26,0x3e,0x84,0xa1,0xd9,0xb8,
        0x77,0xcf,0x38,0xad,0xe7,0x5b,0x2b,0xe5,
        0x5a,0x4e,0xeb,0xa7,0xf4,0x1e,0xff,0x80,
        0xfb,0x3e,0x5c,0x63,0x83,0x6c,0xda,0xdb,
        0xc9,0x51,0xcd,0xe8,0xc3,0xb6,0x20,0x08,
        0x9c,0x52,0xdb,0xfc,0x09,0x5f,0x80,0x90,
        0xf9,0xee,0xbc,0x22,0x3b,0x59,0xc4,0x38,
        0x20,0x0f,0x75,0x44,0x3b,0x99,0x0a,0x18,
    };
    static const char INFO[] =
        "SC1/session/P256_HKDF_SHA256_AES256_GCM/sid";
    uint8_t material[72];
    TEST_ESP_OK(synap_crypto_hkdf(SECRET, sizeof(SECRET), SALT, sizeof(SALT),
                                   (const uint8_t *) INFO, strlen(INFO),
                                   material, sizeof(material)));
    TEST_ASSERT_EQUAL_MEMORY(EXPECTED, material, sizeof(material));
}

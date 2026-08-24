#include "synap_internal.h"

#include <stdlib.h>
#include <string.h>

#include "esp_random.h"
#include "mbedtls/base64.h"
#include "mbedtls/ecdh.h"
#include "mbedtls/ecdsa.h"
#include "mbedtls/gcm.h"
#include "mbedtls/hkdf.h"
#include "mbedtls/md.h"
#include "mbedtls/sha256.h"

static int random_callback(void *context, unsigned char *output, size_t length)
{
    (void) context;
    esp_fill_random(output, length);
    return 0;
}

esp_err_t synap_crypto_random(void *output, size_t length)
{
    if (output == NULL || length == 0) return ESP_ERR_INVALID_ARG;
    esp_fill_random(output, length);
    return ESP_OK;
}

esp_err_t synap_crypto_base64url_encode(const uint8_t *input, size_t input_len,
                                        char **output)
{
    if ((input == NULL && input_len != 0) || output == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    size_t capacity = ((input_len + 2) / 3) * 4 + 1;
    char *encoded = calloc(1, capacity);
    if (encoded == NULL) return ESP_ERR_NO_MEM;
    size_t written = 0;
    int rc = mbedtls_base64_encode((unsigned char *) encoded, capacity, &written,
                                   input, input_len);
    if (rc != 0) {
        free(encoded);
        return ESP_FAIL;
    }
    while (written > 0 && encoded[written - 1] == '=') encoded[--written] = '\0';
    for (size_t index = 0; index < written; ++index) {
        if (encoded[index] == '+') encoded[index] = '-';
        if (encoded[index] == '/') encoded[index] = '_';
    }
    *output = encoded;
    return ESP_OK;
}

esp_err_t synap_crypto_base64url_decode(const char *input, uint8_t **output,
                                        size_t *output_len)
{
    if (input == NULL || output == NULL || output_len == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    size_t input_len = strlen(input);
    if (input_len > SYNAP_MAX_HTTP_RESPONSE * 2 || input_len % 4 == 1) {
        return ESP_ERR_INVALID_SIZE;
    }
    size_t padded_len = input_len + ((4 - input_len % 4) % 4);
    char *padded = calloc(1, padded_len + 1);
    if (padded == NULL) return ESP_ERR_NO_MEM;
    memcpy(padded, input, input_len);
    for (size_t index = 0; index < input_len; ++index) {
        if (padded[index] == '-') padded[index] = '+';
        else if (padded[index] == '_') padded[index] = '/';
        else if (!((padded[index] >= 'A' && padded[index] <= 'Z') ||
                   (padded[index] >= 'a' && padded[index] <= 'z') ||
                   (padded[index] >= '0' && padded[index] <= '9'))) {
            free(padded);
            return ESP_ERR_INVALID_ARG;
        }
    }
    for (size_t index = input_len; index < padded_len; ++index) padded[index] = '=';
    size_t capacity = (padded_len / 4) * 3 + 1;
    uint8_t *decoded = calloc(1, capacity);
    if (decoded == NULL) {
        free(padded);
        return ESP_ERR_NO_MEM;
    }
    size_t written = 0;
    int rc = mbedtls_base64_decode(decoded, capacity, &written,
                                   (const unsigned char *) padded, padded_len);
    free(padded);
    if (rc != 0) {
        free(decoded);
        return ESP_ERR_INVALID_ARG;
    }
    *output = decoded;
    *output_len = written;
    return ESP_OK;
}

esp_err_t synap_crypto_sha256(const uint8_t *input, size_t length,
                              uint8_t output[32])
{
    if ((input == NULL && length != 0) || output == NULL) return ESP_ERR_INVALID_ARG;
    return mbedtls_sha256(input, length, output, 0) == 0 ? ESP_OK : ESP_FAIL;
}

esp_err_t synap_crypto_hkdf(const uint8_t *secret, size_t secret_len,
                            const uint8_t *salt, size_t salt_len,
                            const uint8_t *info, size_t info_len,
                            uint8_t *output, size_t output_len)
{
    const mbedtls_md_info_t *md = mbedtls_md_info_from_type(MBEDTLS_MD_SHA256);
    if (md == NULL || secret == NULL || output == NULL) return ESP_ERR_INVALID_ARG;
    return mbedtls_hkdf(md, salt, salt_len, secret, secret_len, info, info_len,
                        output, output_len) == 0 ? ESP_OK : ESP_FAIL;
}

esp_err_t synap_crypto_gcm_encrypt(const uint8_t key[32], const uint8_t nonce[12],
                                   const uint8_t *aad, size_t aad_len,
                                   const uint8_t *plain, size_t plain_len,
                                   uint8_t **cipher, size_t *cipher_len)
{
    if (key == NULL || nonce == NULL || cipher == NULL || cipher_len == NULL ||
        (plain == NULL && plain_len != 0)) return ESP_ERR_INVALID_ARG;
    uint8_t *result = malloc(plain_len + 16);
    if (result == NULL) return ESP_ERR_NO_MEM;
    mbedtls_gcm_context context;
    mbedtls_gcm_init(&context);
    int rc = mbedtls_gcm_setkey(&context, MBEDTLS_CIPHER_ID_AES, key, 256);
    if (rc == 0) {
        rc = mbedtls_gcm_crypt_and_tag(&context, MBEDTLS_GCM_ENCRYPT, plain_len,
                                       nonce, 12, aad, aad_len, plain,
                                       result, 16, result + plain_len);
    }
    mbedtls_gcm_free(&context);
    if (rc != 0) {
        free(result);
        return ESP_FAIL;
    }
    *cipher = result;
    *cipher_len = plain_len + 16;
    return ESP_OK;
}

esp_err_t synap_crypto_gcm_decrypt(const uint8_t key[32], const uint8_t nonce[12],
                                   const uint8_t *aad, size_t aad_len,
                                   const uint8_t *cipher, size_t cipher_len,
                                   uint8_t **plain, size_t *plain_len)
{
    if (key == NULL || nonce == NULL || cipher == NULL || cipher_len < 16 ||
        plain == NULL || plain_len == NULL) return ESP_ERR_INVALID_ARG;
    size_t output_len = cipher_len - 16;
    uint8_t *output = calloc(1, output_len + 1);
    if (output == NULL) return ESP_ERR_NO_MEM;
    mbedtls_gcm_context context;
    mbedtls_gcm_init(&context);
    int rc = mbedtls_gcm_setkey(&context, MBEDTLS_CIPHER_ID_AES, key, 256);
    if (rc == 0) {
        rc = mbedtls_gcm_auth_decrypt(&context, output_len, nonce, 12,
                                      aad, aad_len, cipher + output_len, 16,
                                      cipher, output);
    }
    mbedtls_gcm_free(&context);
    if (rc != 0) {
        memset(output, 0, output_len);
        free(output);
        return ESP_ERR_INVALID_CRC;
    }
    *plain = output;
    *plain_len = output_len;
    return ESP_OK;
}

esp_err_t synap_crypto_generate_key(mbedtls_pk_context *key)
{
    if (key == NULL) return ESP_ERR_INVALID_ARG;
    mbedtls_pk_init(key);
    int rc = mbedtls_pk_setup(key, mbedtls_pk_info_from_type(MBEDTLS_PK_ECKEY));
    if (rc == 0) {
        rc = mbedtls_ecp_gen_key(MBEDTLS_ECP_DP_SECP256R1, mbedtls_pk_ec(*key),
                                 random_callback, NULL);
    }
    if (rc != 0) {
        mbedtls_pk_free(key);
        return ESP_FAIL;
    }
    return ESP_OK;
}

esp_err_t synap_crypto_export_private(mbedtls_pk_context *key,
                                      uint8_t **der, size_t *der_len)
{
    if (key == NULL || der == NULL || der_len == NULL) return ESP_ERR_INVALID_ARG;
    uint8_t scratch[512];
    int written = mbedtls_pk_write_key_der(key, scratch, sizeof(scratch));
    if (written <= 0) return ESP_FAIL;
    uint8_t *result = malloc((size_t) written);
    if (result == NULL) return ESP_ERR_NO_MEM;
    memcpy(result, scratch + sizeof(scratch) - written, (size_t) written);
    memset(scratch, 0, sizeof(scratch));
    *der = result;
    *der_len = (size_t) written;
    return ESP_OK;
}

esp_err_t synap_crypto_import_private(const uint8_t *der, size_t der_len,
                                      mbedtls_pk_context *key)
{
    if (der == NULL || der_len == 0 || key == NULL) return ESP_ERR_INVALID_ARG;
    mbedtls_pk_init(key);
    int rc = mbedtls_pk_parse_key(key, der, der_len, NULL, 0,
                                  random_callback, NULL);
    if (rc != 0 || !mbedtls_pk_can_do(key, MBEDTLS_PK_ECKEY) ||
        mbedtls_pk_ec(*key)->MBEDTLS_PRIVATE(grp).id != MBEDTLS_ECP_DP_SECP256R1) {
        mbedtls_pk_free(key);
        return ESP_ERR_INVALID_ARG;
    }
    return ESP_OK;
}

esp_err_t synap_crypto_export_spki(mbedtls_pk_context *key,
                                   uint8_t **der, size_t *der_len)
{
    if (key == NULL || der == NULL || der_len == NULL) return ESP_ERR_INVALID_ARG;
    uint8_t scratch[256];
    int written = mbedtls_pk_write_pubkey_der(key, scratch, sizeof(scratch));
    if (written <= 0) return ESP_FAIL;
    uint8_t *result = malloc((size_t) written);
    if (result == NULL) return ESP_ERR_NO_MEM;
    memcpy(result, scratch + sizeof(scratch) - written, (size_t) written);
    *der = result;
    *der_len = (size_t) written;
    return ESP_OK;
}

static esp_err_t double_hash(const uint8_t transcript_hash[32], uint8_t digest[32])
{
    return synap_crypto_sha256(transcript_hash, 32, digest);
}

esp_err_t synap_crypto_sign_p1363(mbedtls_pk_context *key,
                                  const uint8_t transcript_hash[32],
                                  uint8_t signature[64])
{
    if (key == NULL || transcript_hash == NULL || signature == NULL ||
        !mbedtls_pk_can_do(key, MBEDTLS_PK_ECKEY)) return ESP_ERR_INVALID_ARG;
    uint8_t digest[32];
    esp_err_t err = double_hash(transcript_hash, digest);
    if (err != ESP_OK) return err;
    mbedtls_mpi r;
    mbedtls_mpi s;
    mbedtls_mpi_init(&r);
    mbedtls_mpi_init(&s);
    mbedtls_ecp_keypair *ec = mbedtls_pk_ec(*key);
    int rc = mbedtls_ecdsa_sign(&ec->MBEDTLS_PRIVATE(grp), &r, &s,
                                &ec->MBEDTLS_PRIVATE(d), digest,
                                sizeof(digest), random_callback, NULL);
    if (rc == 0) rc = mbedtls_mpi_write_binary(&r, signature, 32);
    if (rc == 0) rc = mbedtls_mpi_write_binary(&s, signature + 32, 32);
    mbedtls_mpi_free(&r);
    mbedtls_mpi_free(&s);
    memset(digest, 0, sizeof(digest));
    return rc == 0 ? ESP_OK : ESP_FAIL;
}

esp_err_t synap_crypto_verify_p1363(const uint8_t *spki, size_t spki_len,
                                    const uint8_t transcript_hash[32],
                                    const uint8_t signature[64])
{
    if (spki == NULL || transcript_hash == NULL || signature == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    mbedtls_pk_context public_key;
    mbedtls_pk_init(&public_key);
    int rc = mbedtls_pk_parse_public_key(&public_key, spki, spki_len);
    if (rc != 0 || !mbedtls_pk_can_do(&public_key, MBEDTLS_PK_ECKEY) ||
        mbedtls_pk_ec(public_key)->MBEDTLS_PRIVATE(grp).id != MBEDTLS_ECP_DP_SECP256R1) {
        mbedtls_pk_free(&public_key);
        return ESP_ERR_INVALID_ARG;
    }
    uint8_t digest[32];
    if (double_hash(transcript_hash, digest) != ESP_OK) {
        mbedtls_pk_free(&public_key);
        return ESP_FAIL;
    }
    mbedtls_mpi r;
    mbedtls_mpi s;
    mbedtls_mpi_init(&r);
    mbedtls_mpi_init(&s);
    rc = mbedtls_mpi_read_binary(&r, signature, 32);
    if (rc == 0) rc = mbedtls_mpi_read_binary(&s, signature + 32, 32);
    if (rc == 0) {
        mbedtls_ecp_keypair *ec = mbedtls_pk_ec(public_key);
        rc = mbedtls_ecdsa_verify(&ec->MBEDTLS_PRIVATE(grp), digest,
                                  sizeof(digest), &ec->MBEDTLS_PRIVATE(Q),
                                  &r, &s);
    }
    mbedtls_mpi_free(&r);
    mbedtls_mpi_free(&s);
    mbedtls_pk_free(&public_key);
    memset(digest, 0, sizeof(digest));
    return rc == 0 ? ESP_OK : ESP_ERR_INVALID_CRC;
}

esp_err_t synap_crypto_ecdh(mbedtls_pk_context *local,
                            const uint8_t *peer_spki, size_t peer_spki_len,
                            uint8_t **secret, size_t *secret_len)
{
    if (local == NULL || peer_spki == NULL || secret == NULL ||
        secret_len == NULL || !mbedtls_pk_can_do(local, MBEDTLS_PK_ECKEY)) {
        return ESP_ERR_INVALID_ARG;
    }
    mbedtls_pk_context peer;
    mbedtls_pk_init(&peer);
    int rc = mbedtls_pk_parse_public_key(&peer, peer_spki, peer_spki_len);
    if (rc != 0 || !mbedtls_pk_can_do(&peer, MBEDTLS_PK_ECKEY)) {
        mbedtls_pk_free(&peer);
        return ESP_ERR_INVALID_ARG;
    }
    mbedtls_ecp_keypair *local_ec = mbedtls_pk_ec(*local);
    mbedtls_ecp_keypair *peer_ec = mbedtls_pk_ec(peer);
    if (local_ec->MBEDTLS_PRIVATE(grp).id != MBEDTLS_ECP_DP_SECP256R1 ||
        peer_ec->MBEDTLS_PRIVATE(grp).id != MBEDTLS_ECP_DP_SECP256R1) {
        mbedtls_pk_free(&peer);
        return ESP_ERR_INVALID_ARG;
    }
    mbedtls_mpi shared;
    mbedtls_mpi_init(&shared);
    rc = mbedtls_ecdh_compute_shared(&local_ec->MBEDTLS_PRIVATE(grp), &shared,
                                     &peer_ec->MBEDTLS_PRIVATE(Q),
                                     &local_ec->MBEDTLS_PRIVATE(d),
                                     random_callback, NULL);
    uint8_t *result = NULL;
    if (rc == 0) {
        result = malloc(32);
        if (result == NULL) rc = MBEDTLS_ERR_MPI_ALLOC_FAILED;
    }
    if (rc == 0) rc = mbedtls_mpi_write_binary(&shared, result, 32);
    mbedtls_mpi_free(&shared);
    mbedtls_pk_free(&peer);
    if (rc != 0) {
        free(result);
        return ESP_FAIL;
    }
    *secret = result;
    *secret_len = 32;
    return ESP_OK;
}

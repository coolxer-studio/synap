#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#include "cJSON.h"
#include "esp_err.h"
#include "mbedtls/pk.h"
#include "synap_agent.h"

#define SYNAP_SC_SUITE "P256_HKDF_SHA256_AES256_GCM"
#define SYNAP_SC_ENVELOPE_MEDIA_TYPE "application/sc-envelope+json"
#define SYNAP_SC_PROTECTED_MEDIA_TYPE "application/sc-protected+json"
#define SYNAP_SC_MESSAGE_PATH "/sc/v1/message"
#define SYNAP_START_PATH "/attach/start"
#define SYNAP_UPLOAD_PATH "/attach/upload"
#define SYNAP_MAX_HTTP_RESPONSE (64U * 1024U)

typedef struct {
    char *base_url;
    char *tls_ca_pem;
    char *sc_app_id;
    char *server_key_id;
    char *server_trust_spki;
    char *business_app_id;
    char *device_id;
    char *device_name;
} synap_provision_t;

typedef struct {
    bool valid;
    char kid[129];
    char sid[129];
    uint8_t request_key[32];
    uint8_t response_key[32];
    uint8_t request_prefix[4];
    uint8_t response_prefix[4];
    int64_t expires_at_ms;
    uint64_t next_sequence;
} synap_sc_session_t;

typedef struct {
    synap_provision_t provision;
    char *provision_partition;
    char *runtime_partition;
    mbedtls_pk_context installation_key;
    uint8_t *installation_spki;
    size_t installation_spki_len;
    synap_sc_session_t session;
    char last_error[48];
} synap_sc_client_t;

typedef struct {
    char *json;
    size_t length;
    uint32_t sequence;
} synap_queue_item_t;

typedef struct {
    bool enabled;
    double collect_every_seconds;
    double collect_for_seconds;
    double upload_every_seconds;
    double upload_for_seconds;
} synap_schedule_t;

/* config */
esp_err_t synap_config_load(const char *partition, synap_provision_t *out);
esp_err_t synap_config_read_enrollment_token(const char *partition,
                                              char **token);
esp_err_t synap_config_clear_enrollment_token(const char *partition);
void synap_config_free(synap_provision_t *config);

/* storage */
esp_err_t synap_store_init(const synap_agent_config_t *config);
esp_err_t synap_store_load_or_create_identity(const char *runtime_partition,
                                               const char *app_id,
                                               mbedtls_pk_context *key,
                                               uint8_t **spki,
                                               size_t *spki_len);
esp_err_t synap_store_reserve_business_sequence(const char *runtime_partition,
                                                 uint64_t *sequence);
esp_err_t synap_store_enqueue(const char *json, size_t length);
esp_err_t synap_store_peek(synap_queue_item_t *item);
esp_err_t synap_store_replace_head(const char *json, size_t length);
esp_err_t synap_store_ack_head(void);
esp_err_t synap_store_reject_head(const char *reason);
uint32_t synap_store_queue_depth(void);
uint32_t synap_store_dead_letter_depth(void);
void synap_store_free_item(synap_queue_item_t *item);

/* crypto */
esp_err_t synap_crypto_random(void *output, size_t length);
esp_err_t synap_crypto_base64url_encode(const uint8_t *input, size_t input_len,
                                        char **output);
esp_err_t synap_crypto_base64url_decode(const char *input, uint8_t **output,
                                        size_t *output_len);
esp_err_t synap_crypto_sha256(const uint8_t *input, size_t length,
                              uint8_t output[32]);
esp_err_t synap_crypto_hkdf(const uint8_t *secret, size_t secret_len,
                            const uint8_t *salt, size_t salt_len,
                            const uint8_t *info, size_t info_len,
                            uint8_t *output, size_t output_len);
esp_err_t synap_crypto_gcm_encrypt(const uint8_t key[32], const uint8_t nonce[12],
                                   const uint8_t *aad, size_t aad_len,
                                   const uint8_t *plain, size_t plain_len,
                                   uint8_t **cipher, size_t *cipher_len);
esp_err_t synap_crypto_gcm_decrypt(const uint8_t key[32], const uint8_t nonce[12],
                                   const uint8_t *aad, size_t aad_len,
                                   const uint8_t *cipher, size_t cipher_len,
                                   uint8_t **plain, size_t *plain_len);
esp_err_t synap_crypto_sign_p1363(mbedtls_pk_context *key,
                                  const uint8_t transcript_hash[32],
                                  uint8_t signature[64]);
esp_err_t synap_crypto_verify_p1363(const uint8_t *spki, size_t spki_len,
                                    const uint8_t transcript_hash[32],
                                    const uint8_t signature[64]);
esp_err_t synap_crypto_generate_key(mbedtls_pk_context *key);
esp_err_t synap_crypto_export_private(mbedtls_pk_context *key,
                                      uint8_t **der, size_t *der_len);
esp_err_t synap_crypto_import_private(const uint8_t *der, size_t der_len,
                                      mbedtls_pk_context *key);
esp_err_t synap_crypto_export_spki(mbedtls_pk_context *key,
                                   uint8_t **der, size_t *der_len);
esp_err_t synap_crypto_ecdh(mbedtls_pk_context *local,
                            const uint8_t *peer_spki, size_t peer_spki_len,
                            uint8_t **secret, size_t *secret_len);

/* secure communication */
esp_err_t synap_sc_init(synap_sc_client_t *client, synap_provision_t *provision,
                        const char *provision_partition,
                        const char *runtime_partition);
void synap_sc_close(synap_sc_client_t *client);
void synap_sc_close_session(synap_sc_client_t *client);
esp_err_t synap_sc_initialize(synap_sc_client_t *client);
esp_err_t synap_sc_request(synap_sc_client_t *client, const char *logical_path,
                           const char *code, const char *body,
                           int *logical_status, char **response_body,
                           char error_code[48]);

/* payload */
esp_err_t synap_payload_create_start(const synap_provision_t *provision,
                                     char **session_id, char **record_json);
esp_err_t synap_payload_create_fact(const synap_provision_t *provision,
                                    const char *session_id, uint64_t sequence,
                                    const char *fact_type,
                                    const synap_agent_metrics_t *metrics,
                                    char **record_json);
esp_err_t synap_payload_increment_attempt(const char *record_json,
                                          char **updated_json);
esp_err_t synap_payload_record_fields(const char *record_json,
                                      char **path, char **code,
                                      cJSON **body, char **session_id,
                                      char **batch_id, char **message_id,
                                      bool *is_start);
esp_err_t synap_payload_parse_start_config(const char *response,
                                           synap_schedule_t *schedule);
esp_err_t synap_payload_classify_ack(const char *response,
                                     const char *batch_id,
                                     const char *message_id,
                                     bool *accepted, bool *duplicate,
                                     bool *retryable, char **reason);

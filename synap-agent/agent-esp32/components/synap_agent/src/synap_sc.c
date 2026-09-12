#include "synap_internal.h"

#include <inttypes.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <sys/time.h>
#include <time.h>

#include "esp_crt_bundle.h"
#include "esp_http_client.h"
#include "esp_log.h"

static const char *TAG = "synap-sc";

typedef struct {
    char *data;
    size_t length;
    size_t capacity;
    char content_type[64];
} http_buffer_t;

static int64_t now_ms(void)
{
    struct timeval value;
    gettimeofday(&value, NULL);
    return (int64_t) value.tv_sec * 1000 + value.tv_usec / 1000;
}

static void set_error(char output[48], const char *value)
{
    if (output != NULL) strlcpy(output, value == NULL ? "SC_FAILED" : value, 48);
}

static esp_err_t http_event(esp_http_client_event_t *event)
{
    http_buffer_t *buffer = event->user_data;
    if (buffer == NULL) return ESP_OK;
    if (event->event_id == HTTP_EVENT_ON_HEADER && event->header_key != NULL &&
        event->header_value != NULL && strcasecmp(event->header_key,
                                                   "Content-Type") == 0) {
        strlcpy(buffer->content_type, event->header_value,
                sizeof(buffer->content_type));
    }
    if (event->event_id != HTTP_EVENT_ON_DATA || event->data_len <= 0) return ESP_OK;
    size_t needed = buffer->length + (size_t) event->data_len + 1;
    if (needed > SYNAP_MAX_HTTP_RESPONSE) return ESP_ERR_INVALID_SIZE;
    if (needed > buffer->capacity) {
        size_t next = buffer->capacity == 0 ? 2048 : buffer->capacity;
        while (next < needed) next *= 2;
        if (next > SYNAP_MAX_HTTP_RESPONSE) next = SYNAP_MAX_HTTP_RESPONSE;
        char *resized = realloc(buffer->data, next);
        if (resized == NULL) return ESP_ERR_NO_MEM;
        buffer->data = resized;
        buffer->capacity = next;
    }
    memcpy(buffer->data + buffer->length, event->data, (size_t) event->data_len);
    buffer->length += (size_t) event->data_len;
    buffer->data[buffer->length] = '\0';
    return ESP_OK;
}

static bool starts_with(const char *value, const char *prefix)
{
    return value != NULL && prefix != NULL &&
           strncmp(value, prefix, strlen(prefix)) == 0;
}

static bool valid_base_url(const char *url)
{
    if (url == NULL || strchr(url, '?') != NULL || strchr(url, '#') != NULL ||
        strpbrk(url, "\r\n\t ") != NULL) return false;
    const char *authority = starts_with(url, "https://") ? url + 8 :
                            starts_with(url, "http://") ? url + 7 : NULL;
    if (authority == NULL || *authority == '\0' || strchr(authority, '@') != NULL) {
        return false;
    }
    const char *slash = strchr(authority, '/');
    return slash == NULL || slash[1] == '\0';
}

static esp_err_t join_url(const char *base, const char *path, char **result)
{
    if (base == NULL || path == NULL || result == NULL || path[0] != '/') {
        return ESP_ERR_INVALID_ARG;
    }
    size_t base_len = strlen(base);
    while (base_len > 0 && base[base_len - 1] == '/') base_len--;
    size_t length = base_len + strlen(path) + 1;
    char *joined = malloc(length);
    if (joined == NULL) return ESP_ERR_NO_MEM;
    snprintf(joined, length, "%.*s%s", (int) base_len, base, path);
    *result = joined;
    return ESP_OK;
}

static esp_err_t http_post(synap_sc_client_t *client, const char *path,
                           const char *content_type, const char *accept,
                           const char *body, int *status,
                           http_buffer_t *response)
{
    char *url = NULL;
    esp_err_t err = join_url(client->provision.base_url, path, &url);
    if (err != ESP_OK) return err;
    memset(response, 0, sizeof(*response));
    esp_http_client_config_t config = {
        .url = url,
        .timeout_ms = 15000,
        .event_handler = http_event,
        .user_data = response,
        .disable_auto_redirect = true,
        .keep_alive_enable = true,
    };
    if (client->provision.tls_ca_pem != NULL &&
        client->provision.tls_ca_pem[0] != '\0') {
        config.cert_pem = client->provision.tls_ca_pem;
    } else if (starts_with(client->provision.base_url, "https://")) {
        config.crt_bundle_attach = esp_crt_bundle_attach;
    }
    esp_http_client_handle_t http = esp_http_client_init(&config);
    free(url);
    if (http == NULL) return ESP_ERR_NO_MEM;
    esp_http_client_set_method(http, HTTP_METHOD_POST);
    esp_http_client_set_header(http, "Content-Type", content_type);
    if (accept != NULL) esp_http_client_set_header(http, "Accept", accept);
    esp_http_client_set_header(http, "User-Agent", "synap-esp32/" SYNAP_AGENT_SDK_VERSION);
    esp_http_client_set_post_field(http, body, (int) strlen(body));
    err = esp_http_client_perform(http);
    if (err == ESP_OK) *status = esp_http_client_get_status_code(http);
    esp_http_client_cleanup(http);
    if (err != ESP_OK) {
        free(response->data);
        memset(response, 0, sizeof(*response));
    } else if (response->data == NULL) {
        response->data = strdup("");
        if (response->data == NULL) err = ESP_ERR_NO_MEM;
    }
    return err;
}

static void free_http_buffer(http_buffer_t *buffer)
{
    if (buffer != NULL) {
        free(buffer->data);
        memset(buffer, 0, sizeof(*buffer));
    }
}

static const char *json_string(cJSON *object, const char *name)
{
    cJSON *item = cJSON_GetObjectItemCaseSensitive(object, name);
    return cJSON_IsString(item) && item->valuestring != NULL ? item->valuestring : NULL;
}

static bool json_int64(cJSON *object, const char *name, int64_t *value)
{
    cJSON *item = cJSON_GetObjectItemCaseSensitive(object, name);
    if (!cJSON_IsNumber(item)) return false;
    *value = (int64_t) item->valuedouble;
    return true;
}

static char *json_print(cJSON *object)
{
    char *encoded = cJSON_PrintUnformatted(object);
    cJSON_Delete(object);
    return encoded;
}

static esp_err_t parse_remote_error(const char *body, char error_code[48])
{
    cJSON *root = cJSON_Parse(body == NULL ? "" : body);
    const char *code = root == NULL ? NULL : json_string(root, "code");
    set_error(error_code, code == NULL ? "SC_TRANSPORT_FAILED" : code);
    cJSON_Delete(root);
    return ESP_FAIL;
}

static esp_err_t post_handshake_json(synap_sc_client_t *client, const char *path,
                                     const char *body, cJSON **output,
                                     char error_code[48])
{
    http_buffer_t response;
    int status = 0;
    esp_err_t err = http_post(client, path, "application/json", "application/json",
                              body, &status, &response);
    if (err != ESP_OK) {
        set_error(error_code, "SC_NETWORK_FAILED");
        return err;
    }
    if (status < 200 || status >= 300) {
        if (status == 429 || status >= 500) {
            set_error(error_code, "SC_NETWORK_FAILED");
        } else {
            parse_remote_error(response.data, error_code);
        }
        free_http_buffer(&response);
        return ESP_FAIL;
    }
    cJSON *root = cJSON_Parse(response.data);
    free_http_buffer(&response);
    if (root == NULL || !cJSON_IsObject(root)) {
        cJSON_Delete(root);
        set_error(error_code, "SC_HANDSHAKE_FAILED");
        return ESP_ERR_INVALID_RESPONSE;
    }
    *output = root;
    return ESP_OK;
}

static esp_err_t build_transcript_hash(const synap_sc_client_t *client,
                                       const char *client_ephemeral,
                                       const char *installation,
                                       const char *server_identity,
                                       const char *server_ephemeral,
                                       const char *kid, const char *sid,
                                       int64_t created_at, int64_t expires_at,
                                       uint8_t output[32])
{
    int needed = snprintf(NULL, 0,
        "SC1-HANDSHAKE\n1\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%" PRId64 "\n%" PRId64,
        SYNAP_SC_SUITE, client->provision.sc_app_id,
        client->provision.device_id, SYNAP_AGENT_DEVICE_TYPE,
        client_ephemeral, installation, server_identity, server_ephemeral,
        kid, sid, created_at, expires_at);
    if (needed <= 0 || needed > 8192) return ESP_ERR_INVALID_SIZE;
    char *transcript = malloc((size_t) needed + 1);
    if (transcript == NULL) return ESP_ERR_NO_MEM;
    snprintf(transcript, (size_t) needed + 1,
        "SC1-HANDSHAKE\n1\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n%" PRId64 "\n%" PRId64,
        SYNAP_SC_SUITE, client->provision.sc_app_id,
        client->provision.device_id, SYNAP_AGENT_DEVICE_TYPE,
        client_ephemeral, installation, server_identity, server_ephemeral,
        kid, sid, created_at, expires_at);
    esp_err_t err = synap_crypto_sha256((const uint8_t *) transcript,
                                        (size_t) needed, output);
    free(transcript);
    return err;
}

esp_err_t synap_sc_init(synap_sc_client_t *client, synap_provision_t *provision,
                        const char *provision_partition,
                        const char *runtime_partition)
{
    if (client == NULL || provision == NULL || provision_partition == NULL ||
        runtime_partition == NULL || provision->base_url == NULL ||
        provision->sc_app_id == NULL || provision->server_key_id == NULL ||
        provision->server_trust_spki == NULL || provision->device_id == NULL ||
        !valid_base_url(provision->base_url)) {
        return ESP_ERR_INVALID_ARG;
    }
#if !CONFIG_SYNAP_ALLOW_INSECURE_HTTP
    if (!starts_with(provision->base_url, "https://")) return ESP_ERR_INVALID_ARG;
#else
    if (!starts_with(provision->base_url, "https://") &&
        !starts_with(provision->base_url, "http://")) return ESP_ERR_INVALID_ARG;
#endif
    memset(client, 0, sizeof(*client));
    client->provision = *provision;
    memset(provision, 0, sizeof(*provision));
    client->provision_partition = strdup(provision_partition);
    client->runtime_partition = strdup(runtime_partition);
    if (client->provision_partition == NULL || client->runtime_partition == NULL) {
        synap_sc_close(client);
        return ESP_ERR_NO_MEM;
    }
    return synap_store_load_or_create_identity(runtime_partition,
                                                client->provision.sc_app_id,
                                                &client->installation_key,
                                                &client->installation_spki,
                                                &client->installation_spki_len);
}

void synap_sc_close_session(synap_sc_client_t *client)
{
    if (client == NULL) return;
    memset(&client->session, 0, sizeof(client->session));
    client->session.next_sequence = 1;
}

void synap_sc_close(synap_sc_client_t *client)
{
    if (client == NULL) return;
    synap_config_free(&client->provision);
    free(client->provision_partition);
    free(client->runtime_partition);
    free(client->installation_spki);
    mbedtls_pk_free(&client->installation_key);
    memset(client, 0, sizeof(*client));
}

esp_err_t synap_sc_initialize(synap_sc_client_t *client)
{
    if (client == NULL) return ESP_ERR_INVALID_ARG;
    if (client->session.valid && now_ms() + 5000 < client->session.expires_at_ms) {
        return ESP_OK;
    }
    synap_sc_close_session(client);
    char error_code[48] = {0};
    client->last_error[0] = '\0';
    mbedtls_pk_context ephemeral;
    esp_err_t err = synap_crypto_generate_key(&ephemeral);
    uint8_t *ephemeral_spki = NULL;
    size_t ephemeral_spki_len = 0;
    char *ephemeral_b64 = NULL;
    char *installation_b64 = NULL;
    char *request_body = NULL;
    cJSON *response = NULL;
    uint8_t *server_identity_der = NULL;
    size_t server_identity_len = 0;
    uint8_t *server_ephemeral_der = NULL;
    size_t server_ephemeral_len = 0;
    uint8_t *pinned_der = NULL;
    size_t pinned_len = 0;
    uint8_t *signature = NULL;
    size_t signature_len = 0;
    uint8_t *secret = NULL;
    size_t secret_len = 0;
    uint8_t material[72] = {0};
    char *enrollment_token = NULL;

    if (err == ESP_OK) err = synap_config_read_enrollment_token(
        client->provision_partition, &enrollment_token);
    if (err == ESP_OK) err = synap_crypto_export_spki(&ephemeral, &ephemeral_spki,
                                                       &ephemeral_spki_len);
    if (err == ESP_OK) err = synap_crypto_base64url_encode(ephemeral_spki,
                                                           ephemeral_spki_len,
                                                           &ephemeral_b64);
    if (err == ESP_OK) err = synap_crypto_base64url_encode(client->installation_spki,
                                                           client->installation_spki_len,
                                                           &installation_b64);
    if (err != ESP_OK) goto cleanup;

    cJSON *request = cJSON_CreateObject();
    cJSON_AddNumberToObject(request, "v", 1);
    cJSON_AddStringToObject(request, "suite", SYNAP_SC_SUITE);
    cJSON_AddStringToObject(request, "appId", client->provision.sc_app_id);
    cJSON_AddStringToObject(request, "deviceId", client->provision.device_id);
    cJSON_AddStringToObject(request, "deviceType", SYNAP_AGENT_DEVICE_TYPE);
    cJSON_AddStringToObject(request, "clientEphemeralPublicKey", ephemeral_b64);
    cJSON_AddStringToObject(request, "installationPublicKey", installation_b64);
    if (enrollment_token != NULL && enrollment_token[0] != '\0') {
        cJSON_AddStringToObject(request, "enrollmentToken",
                               enrollment_token);
    }
    cJSON_AddNumberToObject(request, "timestamp", (double) now_ms());
    request_body = json_print(request);
    if (request_body == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    err = post_handshake_json(client, "/sc/v1/handshake", request_body,
                              &response, error_code);
    if (err != ESP_OK) goto cleanup;

    cJSON *version = cJSON_GetObjectItemCaseSensitive(response, "v");
    const char *suite = json_string(response, "suite");
    const char *kid = json_string(response, "kid");
    const char *sid = json_string(response, "sid");
    const char *server_identity = json_string(response, "serverIdentityPublicKey");
    const char *server_ephemeral = json_string(response, "serverEphemeralPublicKey");
    const char *signature_b64 = json_string(response, "signature");
    int64_t created_at = 0;
    int64_t expires_at = 0;
    if (!cJSON_IsNumber(version) || version->valueint != 1 || suite == NULL ||
        strcmp(suite, SYNAP_SC_SUITE) != 0 || kid == NULL || sid == NULL ||
        strcmp(kid, client->provision.server_key_id) != 0 ||
        server_identity == NULL || server_ephemeral == NULL ||
        signature_b64 == NULL || !json_int64(response, "createdAt", &created_at) ||
        !json_int64(response, "expiresAt", &expires_at) || expires_at <= now_ms()) {
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    err = synap_crypto_base64url_decode(server_identity, &server_identity_der,
                                        &server_identity_len);
    if (err == ESP_OK) err = synap_crypto_base64url_decode(
        client->provision.server_trust_spki, &pinned_der, &pinned_len);
    if (err != ESP_OK || pinned_len != server_identity_len ||
        memcmp(pinned_der, server_identity_der, pinned_len) != 0) {
        err = ESP_ERR_INVALID_CRC;
        goto cleanup;
    }
    err = synap_crypto_base64url_decode(server_ephemeral, &server_ephemeral_der,
                                        &server_ephemeral_len);
    if (err == ESP_OK) err = synap_crypto_base64url_decode(signature_b64,
                                                           &signature,
                                                           &signature_len);
    uint8_t transcript_hash[32];
    if (err == ESP_OK) err = build_transcript_hash(client, ephemeral_b64,
                                                    installation_b64,
                                                    server_identity,
                                                    server_ephemeral, kid, sid,
                                                    created_at, expires_at,
                                                    transcript_hash);
    if (err == ESP_OK && signature_len == 64) {
        err = synap_crypto_verify_p1363(server_identity_der, server_identity_len,
                                        transcript_hash, signature);
    } else if (err == ESP_OK) {
        err = ESP_ERR_INVALID_SIZE;
    }
    if (err == ESP_OK) err = synap_crypto_ecdh(&ephemeral, server_ephemeral_der,
                                               server_ephemeral_len, &secret,
                                               &secret_len);
    char info[256];
    int info_len = snprintf(info, sizeof(info), "SC1/session/%s/%s",
                            SYNAP_SC_SUITE, sid);
    if (err == ESP_OK && (info_len <= 0 || (size_t) info_len >= sizeof(info))) {
        err = ESP_ERR_INVALID_SIZE;
    }
    if (err == ESP_OK) err = synap_crypto_hkdf(secret, secret_len,
                                              transcript_hash, 32,
                                              (const uint8_t *) info,
                                              (size_t) info_len,
                                              material, sizeof(material));
    uint8_t proof[64];
    if (err == ESP_OK) err = synap_crypto_sign_p1363(&client->installation_key,
                                                     transcript_hash, proof);
    char *proof_b64 = NULL;
    if (err == ESP_OK) err = synap_crypto_base64url_encode(proof, sizeof(proof),
                                                           &proof_b64);
    if (err != ESP_OK) {
        free(proof_b64);
        goto cleanup;
    }
    cJSON *finish_request = cJSON_CreateObject();
    cJSON_AddStringToObject(finish_request, "kid", kid);
    cJSON_AddStringToObject(finish_request, "sid", sid);
    cJSON_AddStringToObject(finish_request, "proof", proof_b64);
    free(proof_b64);
    char *finish_body = json_print(finish_request);
    cJSON *finish_response = NULL;
    if (finish_body == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    err = post_handshake_json(client, "/sc/v1/handshake/finish", finish_body,
                              &finish_response, error_code);
    free(finish_body);
    if (err != ESP_OK) {
        cJSON_Delete(finish_response);
        goto cleanup;
    }
    cJSON *active = cJSON_GetObjectItemCaseSensitive(finish_response, "active");
    int64_t finish_expires_at = 0;
    if (!cJSON_IsTrue(active) ||
        !json_int64(finish_response, "expiresAt", &finish_expires_at)) {
        cJSON_Delete(finish_response);
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    cJSON_Delete(finish_response);
    strlcpy(client->session.kid, kid, sizeof(client->session.kid));
    strlcpy(client->session.sid, sid, sizeof(client->session.sid));
    memcpy(client->session.request_key, material, 32);
    memcpy(client->session.response_key, material + 32, 32);
    memcpy(client->session.request_prefix, material + 64, 4);
    memcpy(client->session.response_prefix, material + 68, 4);
    client->session.expires_at_ms = finish_expires_at;
    client->session.next_sequence = 1;
    client->session.valid = true;
    if (enrollment_token != NULL) {
        esp_err_t clear_err = synap_config_clear_enrollment_token(
            client->provision_partition);
        if (clear_err != ESP_OK) {
            ESP_LOGE(TAG, "enrollment succeeded but token erasure failed: %s",
                     esp_err_to_name(clear_err));
            err = clear_err;
            goto cleanup;
        }
    }

cleanup:
    if (err != ESP_OK) {
        synap_sc_close_session(client);
        strlcpy(client->last_error,
                error_code[0] == '\0' ? "SC_HANDSHAKE_FAILED" : error_code,
                sizeof(client->last_error));
    } else {
        client->last_error[0] = '\0';
    }
    cJSON_Delete(response);
    free(request_body);
    free(ephemeral_spki);
    free(ephemeral_b64);
    free(installation_b64);
    free(server_identity_der);
    free(server_ephemeral_der);
    free(pinned_der);
    free(signature);
    if (enrollment_token != NULL) {
        memset(enrollment_token, 0, strlen(enrollment_token));
        free(enrollment_token);
    }
    if (secret != NULL) {
        memset(secret, 0, secret_len);
        free(secret);
    }
    memset(material, 0, sizeof(material));
    mbedtls_pk_free(&ephemeral);
    return err;
}

static void make_nonce(const uint8_t prefix[4], uint64_t sequence,
                       uint8_t nonce[12])
{
    memcpy(nonce, prefix, 4);
    for (int index = 0; index < 8; ++index) {
        nonce[4 + index] = (uint8_t) (sequence >> (56 - index * 8));
    }
}

static esp_err_t build_aad(const char *direction, const synap_sc_session_t *session,
                           int64_t timestamp, uint64_t sequence,
                           const char *request_id, int status, char **output)
{
    const char *format_request =
        "SC1\n%s\n%s\n%s\n%s\n%" PRId64 "\n%" PRIu64 "\n%s\nPOST\n%s\n%s";
    const char *format_response =
        "SC1\n%s\n%s\n%s\n%s\n%" PRId64 "\n%" PRIu64 "\n%s\nPOST\n%s\n%s\n%d";
    int length = strcmp(direction, "response") == 0
        ? snprintf(NULL, 0, format_response, direction, SYNAP_SC_SUITE,
                   session->kid, session->sid, timestamp, sequence, request_id,
                   SYNAP_SC_MESSAGE_PATH, SYNAP_SC_PROTECTED_MEDIA_TYPE, status)
        : snprintf(NULL, 0, format_request, direction, SYNAP_SC_SUITE,
                   session->kid, session->sid, timestamp, sequence, request_id,
                   SYNAP_SC_MESSAGE_PATH, SYNAP_SC_PROTECTED_MEDIA_TYPE);
    if (length <= 0 || length > 2048) return ESP_ERR_INVALID_SIZE;
    char *aad = malloc((size_t) length + 1);
    if (aad == NULL) return ESP_ERR_NO_MEM;
    if (strcmp(direction, "response") == 0) {
        snprintf(aad, (size_t) length + 1, format_response, direction,
                 SYNAP_SC_SUITE, session->kid, session->sid, timestamp,
                 sequence, request_id, SYNAP_SC_MESSAGE_PATH,
                 SYNAP_SC_PROTECTED_MEDIA_TYPE, status);
    } else {
        snprintf(aad, (size_t) length + 1, format_request, direction,
                 SYNAP_SC_SUITE, session->kid, session->sid, timestamp,
                 sequence, request_id, SYNAP_SC_MESSAGE_PATH,
                 SYNAP_SC_PROTECTED_MEDIA_TYPE);
    }
    *output = aad;
    return ESP_OK;
}

static bool envelope_string_equals(cJSON *root, const char *name,
                                   const char *expected)
{
    const char *actual = json_string(root, name);
    return actual != NULL && strcmp(actual, expected) == 0;
}

esp_err_t synap_sc_request(synap_sc_client_t *client, const char *logical_path,
                           const char *code, const char *body,
                           int *logical_status, char **response_body,
                           char error_code[48])
{
    if (client == NULL || logical_path == NULL || code == NULL || body == NULL ||
        logical_status == NULL || response_body == NULL || error_code == NULL) {
        return ESP_ERR_INVALID_ARG;
    }
    *response_body = NULL;
    *logical_status = 0;
    error_code[0] = '\0';
    esp_err_t err = synap_sc_initialize(client);
    if (err != ESP_OK) {
        set_error(error_code, client->last_error[0] == '\0'
                              ? "SC_HANDSHAKE_FAILED" : client->last_error);
        return err;
    }
    synap_sc_session_t *session = &client->session;
    if (!session->valid || session->next_sequence == 0 ||
        session->next_sequence > 9007199254740991ULL) {
        set_error(error_code, "SC_SEQUENCE_EXHAUSTED");
        return ESP_ERR_INVALID_STATE;
    }
    uint64_t sequence = session->next_sequence++;
    uint8_t request_id_bytes[16];
    synap_crypto_random(request_id_bytes, sizeof(request_id_bytes));
    char *request_id = NULL;
    char *body_b64 = NULL;
    char *protected_json = NULL;
    char *aad = NULL;
    uint8_t *cipher = NULL;
    size_t cipher_len = 0;
    char *nonce_b64 = NULL;
    char *cipher_b64 = NULL;
    char *envelope_json = NULL;
    cJSON *response_envelope = NULL;
    uint8_t *received_nonce = NULL;
    size_t received_nonce_len = 0;
    uint8_t *received_cipher = NULL;
    size_t received_cipher_len = 0;
    uint8_t *plain = NULL;
    size_t plain_len = 0;
    int64_t timestamp = now_ms();
    uint8_t nonce[12];
    make_nonce(session->request_prefix, sequence, nonce);

    err = synap_crypto_base64url_encode(request_id_bytes,
                                        sizeof(request_id_bytes), &request_id);
    if (err == ESP_OK) err = synap_crypto_base64url_encode(
        (const uint8_t *) body, strlen(body), &body_b64);
    if (err != ESP_OK) goto cleanup;
    cJSON *protected = cJSON_CreateObject();
    cJSON_AddStringToObject(protected, "method", "POST");
    cJSON_AddStringToObject(protected, "path", logical_path);
    cJSON_AddStringToObject(protected, "contentType", "application/json");
    cJSON *headers = cJSON_AddObjectToObject(protected, "headers");
    cJSON_AddStringToObject(headers, "code", code);
    cJSON_AddStringToObject(protected, "body", body_b64);
    protected_json = json_print(protected);
    if (protected_json == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    err = build_aad("request", session, timestamp, sequence, request_id, 0, &aad);
    if (err == ESP_OK) err = synap_crypto_gcm_encrypt(
        session->request_key, nonce, (const uint8_t *) aad, strlen(aad),
        (const uint8_t *) protected_json, strlen(protected_json),
        &cipher, &cipher_len);
    if (err == ESP_OK) err = synap_crypto_base64url_encode(nonce, sizeof(nonce),
                                                           &nonce_b64);
    if (err == ESP_OK) err = synap_crypto_base64url_encode(cipher, cipher_len,
                                                           &cipher_b64);
    if (err != ESP_OK) goto cleanup;
    cJSON *envelope = cJSON_CreateObject();
    cJSON_AddNumberToObject(envelope, "v", 1);
    cJSON_AddStringToObject(envelope, "suite", SYNAP_SC_SUITE);
    cJSON_AddStringToObject(envelope, "kid", session->kid);
    cJSON_AddStringToObject(envelope, "sid", session->sid);
    cJSON_AddNumberToObject(envelope, "ts", (double) timestamp);
    cJSON_AddNumberToObject(envelope, "seq", (double) sequence);
    cJSON_AddStringToObject(envelope, "rid", request_id);
    cJSON_AddStringToObject(envelope, "m", "POST");
    cJSON_AddStringToObject(envelope, "p", SYNAP_SC_MESSAGE_PATH);
    cJSON_AddStringToObject(envelope, "cty", SYNAP_SC_PROTECTED_MEDIA_TYPE);
    cJSON_AddNumberToObject(envelope, "st", 0);
    cJSON_AddStringToObject(envelope, "nonce", nonce_b64);
    cJSON_AddStringToObject(envelope, "ct", cipher_b64);
    envelope_json = json_print(envelope);
    if (envelope_json == NULL) {
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }

    http_buffer_t response;
    int transport_status = 0;
    err = http_post(client, SYNAP_SC_MESSAGE_PATH, SYNAP_SC_ENVELOPE_MEDIA_TYPE,
                    SYNAP_SC_ENVELOPE_MEDIA_TYPE, envelope_json,
                    &transport_status, &response);
    if (err != ESP_OK) {
        set_error(error_code, "SC_NETWORK_FAILED");
        goto cleanup;
    }
    if (!starts_with(response.content_type, SYNAP_SC_ENVELOPE_MEDIA_TYPE)) {
        if (transport_status == 429 || transport_status >= 500) {
            set_error(error_code, "SC_NETWORK_FAILED");
        } else {
            parse_remote_error(response.data, error_code);
        }
        if (strcmp(error_code, "SC_UNKNOWN_SESSION") == 0) {
            synap_sc_close_session(client);
        }
        free_http_buffer(&response);
        err = ESP_FAIL;
        goto cleanup;
    }
    response_envelope = cJSON_Parse(response.data);
    free_http_buffer(&response);
    if (response_envelope == NULL) {
        set_error(error_code, "SC_INVALID_ENVELOPE");
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    cJSON *version = cJSON_GetObjectItemCaseSensitive(response_envelope, "v");
    cJSON *seq_json = cJSON_GetObjectItemCaseSensitive(response_envelope, "seq");
    cJSON *status_json = cJSON_GetObjectItemCaseSensitive(response_envelope, "st");
    int64_t response_timestamp = 0;
    if (!cJSON_IsNumber(version) || version->valueint != 1 ||
        !cJSON_IsNumber(seq_json) || (uint64_t) seq_json->valuedouble != sequence ||
        !cJSON_IsNumber(status_json) || status_json->valueint < 100 ||
        status_json->valueint > 599 ||
        !json_int64(response_envelope, "ts", &response_timestamp) ||
        llabs(now_ms() - response_timestamp) > 120000 ||
        !envelope_string_equals(response_envelope, "suite", SYNAP_SC_SUITE) ||
        !envelope_string_equals(response_envelope, "kid", session->kid) ||
        !envelope_string_equals(response_envelope, "sid", session->sid) ||
        !envelope_string_equals(response_envelope, "rid", request_id) ||
        !envelope_string_equals(response_envelope, "m", "POST") ||
        !envelope_string_equals(response_envelope, "p", SYNAP_SC_MESSAGE_PATH) ||
        !envelope_string_equals(response_envelope, "cty", SYNAP_SC_PROTECTED_MEDIA_TYPE)) {
        set_error(error_code, "SC_ROUTE_MISMATCH");
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    int response_status = status_json->valueint;
    const char *response_nonce_b64 = json_string(response_envelope, "nonce");
    const char *response_cipher_b64 = json_string(response_envelope, "ct");
    if (response_nonce_b64 == NULL || response_cipher_b64 == NULL) {
        set_error(error_code, "SC_INVALID_ENVELOPE");
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    err = synap_crypto_base64url_decode(response_nonce_b64, &received_nonce,
                                        &received_nonce_len);
    uint8_t expected_nonce[12];
    make_nonce(session->response_prefix, sequence, expected_nonce);
    if (err != ESP_OK || received_nonce_len != sizeof(expected_nonce) ||
        memcmp(received_nonce, expected_nonce, sizeof(expected_nonce)) != 0) {
        set_error(error_code, "SC_INVALID_ENVELOPE");
        err = ESP_ERR_INVALID_CRC;
        goto cleanup;
    }
    err = synap_crypto_base64url_decode(response_cipher_b64, &received_cipher,
                                        &received_cipher_len);
    free(aad);
    aad = NULL;
    if (err == ESP_OK) err = build_aad("response", session, response_timestamp,
                                      sequence, request_id, response_status, &aad);
    if (err == ESP_OK) err = synap_crypto_gcm_decrypt(
        session->response_key, expected_nonce, (const uint8_t *) aad, strlen(aad),
        received_cipher, received_cipher_len, &plain, &plain_len);
    if (err != ESP_OK) {
        set_error(error_code, "SC_AUTHENTICATION_FAILED");
        goto cleanup;
    }
    cJSON *protected_response = cJSON_ParseWithLength((const char *) plain,
                                                       plain_len);
    const char *result_content_type = protected_response == NULL ? NULL
        : json_string(protected_response, "contentType");
    const char *result_body_b64 = protected_response == NULL ? NULL
        : json_string(protected_response, "body");
    uint8_t *result_body = NULL;
    size_t result_body_len = 0;
    if (result_content_type == NULL || strcmp(result_content_type,
                                               "application/json") != 0 ||
        result_body_b64 == NULL) {
        cJSON_Delete(protected_response);
        set_error(error_code, "SC_INVALID_ENVELOPE");
        err = ESP_ERR_INVALID_RESPONSE;
        goto cleanup;
    }
    err = synap_crypto_base64url_decode(result_body_b64, &result_body,
                                        &result_body_len);
    cJSON_Delete(protected_response);
    if (err != ESP_OK) {
        set_error(error_code, "SC_INVALID_ENVELOPE");
        goto cleanup;
    }
    char *result = calloc(1, result_body_len + 1);
    if (result == NULL) {
        free(result_body);
        err = ESP_ERR_NO_MEM;
        goto cleanup;
    }
    memcpy(result, result_body, result_body_len);
    free(result_body);
    *logical_status = response_status;
    *response_body = result;
    err = ESP_OK;

cleanup:
    cJSON_Delete(response_envelope);
    free(request_id);
    free(body_b64);
    free(protected_json);
    free(aad);
    free(cipher);
    free(nonce_b64);
    free(cipher_b64);
    free(envelope_json);
    free(received_nonce);
    free(received_cipher);
    if (plain != NULL) {
        memset(plain, 0, plain_len);
        free(plain);
    }
    return err;
}

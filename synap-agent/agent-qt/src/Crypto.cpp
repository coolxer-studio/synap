#include "Crypto.h"

#include <openssl/bn.h>
#include <openssl/crypto.h>
#include <openssl/opensslv.h>
#if OPENSSL_VERSION_NUMBER >= 0x30000000L
#  include <openssl/core_names.h>
#endif
#include <openssl/ec.h>
#include <openssl/ecdsa.h>
#include <openssl/err.h>
#include <openssl/evp.h>
#include <openssl/kdf.h>
#include <openssl/pem.h>
#include <openssl/rand.h>
#include <openssl/x509.h>

namespace Synap::Qt::Internal {
namespace {

QString opensslError(const QString &prefix) {
    const unsigned long code = ERR_get_error();
    if (!code) return prefix;
    char buffer[256] = {};
    ERR_error_string_n(code, buffer, sizeof(buffer));
    return prefix + QStringLiteral(": ") + QString::fromLatin1(buffer);
}

QByteArray derToP1363(const unsigned char *der, int length, QString *error) {
    const unsigned char *cursor = der;
    ECDSA_SIG *signature = d2i_ECDSA_SIG(nullptr, &cursor, length);
    if (!signature) {
        if (error) *error = opensslError(QStringLiteral("invalid ECDSA signature"));
        return {};
    }
    const BIGNUM *r = nullptr;
    const BIGNUM *s = nullptr;
    ECDSA_SIG_get0(signature, &r, &s);
    QByteArray output(64, ::Qt::Uninitialized);
    const bool valid = BN_bn2binpad(r, reinterpret_cast<unsigned char *>(output.data()), 32) == 32
        && BN_bn2binpad(s, reinterpret_cast<unsigned char *>(output.data() + 32), 32) == 32;
    ECDSA_SIG_free(signature);
    if (!valid) {
        if (error) *error = QStringLiteral("ECDSA signature is not P-256 sized");
        return {};
    }
    return output;
}

QByteArray p1363ToDer(const QByteArray &signature, QString *error) {
    if (signature.size() != 64) {
        if (error) *error = QStringLiteral("P1363 signature must contain 64 bytes");
        return {};
    }
    ECDSA_SIG *value = ECDSA_SIG_new();
    BIGNUM *r = BN_bin2bn(reinterpret_cast<const unsigned char *>(signature.constData()), 32, nullptr);
    BIGNUM *s = BN_bin2bn(reinterpret_cast<const unsigned char *>(signature.constData() + 32), 32, nullptr);
    if (!value || !r || !s || ECDSA_SIG_set0(value, r, s) != 1) {
        if (value) ECDSA_SIG_free(value);
        else { BN_free(r); BN_free(s); }
        if (error) *error = opensslError(QStringLiteral("could not encode ECDSA signature"));
        return {};
    }
    const int length = i2d_ECDSA_SIG(value, nullptr);
    QByteArray output(length, ::Qt::Uninitialized);
    unsigned char *cursor = reinterpret_cast<unsigned char *>(output.data());
    if (length <= 0 || i2d_ECDSA_SIG(value, &cursor) != length) output.clear();
    ECDSA_SIG_free(value);
    if (output.isEmpty() && error) *error = opensslError(QStringLiteral("could not encode ECDSA signature"));
    return output;
}

} // namespace

void PKeyDeleter::operator()(EVP_PKEY *key) const { EVP_PKEY_free(key); }

QByteArray base64UrlEncode(const QByteArray &value) {
    return value.toBase64(QByteArray::Base64UrlEncoding | QByteArray::OmitTrailingEquals);
}

QByteArray base64UrlDecode(const QByteArray &value, bool *ok) {
    if (ok) *ok = false;
    if (value.contains('=') || value.contains('+') || value.contains('/')) return {};
    for (const char c : value) {
        if (!(c >= 'A' && c <= 'Z') && !(c >= 'a' && c <= 'z')
            && !(c >= '0' && c <= '9') && c != '-' && c != '_') return {};
    }
#if QT_VERSION >= QT_VERSION_CHECK(5, 15, 0)
    const auto decoded = QByteArray::fromBase64Encoding(value, QByteArray::Base64UrlEncoding);
    if (!decoded) return {};
    if (ok) *ok = true;
    return decoded.decoded;
#else
    const QByteArray decoded = QByteArray::fromBase64(value, QByteArray::Base64UrlEncoding);
    if (decoded.isEmpty() && !value.isEmpty()) return {};
    if (ok) *ok = true;
    return decoded;
#endif
}

bool constantTimeEqual(const QByteArray &left, const QByteArray &right) {
    if (left.size() != right.size()) return false;
    return CRYPTO_memcmp(left.constData(), right.constData(), size_t(left.size())) == 0;
}

QByteArray randomBytes(int size, QString *error) {
    if (size <= 0) return {};
    QByteArray output(size, ::Qt::Uninitialized);
    if (RAND_bytes(reinterpret_cast<unsigned char *>(output.data()), size) != 1) {
        if (error) *error = opensslError(QStringLiteral("secure random generation failed"));
        return {};
    }
    return output;
}

QByteArray sha256(const QByteArray &value) {
    QByteArray output(EVP_MAX_MD_SIZE, ::Qt::Uninitialized);
    unsigned int length = 0;
    if (EVP_Digest(value.constData(), size_t(value.size()),
                   reinterpret_cast<unsigned char *>(output.data()), &length,
                   EVP_sha256(), nullptr) != 1) return {};
    output.resize(int(length));
    return output;
}

PKey generateP256(QString *error) {
    EVP_PKEY_CTX *context = EVP_PKEY_CTX_new_id(EVP_PKEY_EC, nullptr);
    EVP_PKEY *raw = nullptr;
    const bool ok = context && EVP_PKEY_keygen_init(context) == 1
        && EVP_PKEY_CTX_set_ec_paramgen_curve_nid(context, NID_X9_62_prime256v1) == 1
        && EVP_PKEY_keygen(context, &raw) == 1;
    EVP_PKEY_CTX_free(context);
    if (!ok) {
        EVP_PKEY_free(raw);
        if (error) *error = opensslError(QStringLiteral("P-256 key generation failed"));
        return {};
    }
    return PKey(raw);
}

PKey privateKeyFromDer(const QByteArray &der, QString *error) {
    const unsigned char *cursor = reinterpret_cast<const unsigned char *>(der.constData());
    EVP_PKEY *key = d2i_AutoPrivateKey(nullptr, &cursor, der.size());
    if (!key || cursor != reinterpret_cast<const unsigned char *>(der.constData()) + der.size() || !isP256(key)) {
        EVP_PKEY_free(key);
        if (error) *error = opensslError(QStringLiteral("stored identity key is invalid"));
        return {};
    }
    return PKey(key);
}

PKey publicKeyFromSpki(const QByteArray &spki, QString *error) {
    const unsigned char *cursor = reinterpret_cast<const unsigned char *>(spki.constData());
    EVP_PKEY *key = d2i_PUBKEY(nullptr, &cursor, spki.size());
    if (!key || cursor != reinterpret_cast<const unsigned char *>(spki.constData()) + spki.size() || !isP256(key)) {
        EVP_PKEY_free(key);
        if (error) *error = opensslError(QStringLiteral("P-256 SPKI public key is invalid"));
        return {};
    }
    return PKey(key);
}

QByteArray privateKeyDer(EVP_PKEY *key, QString *error) {
    const int length = i2d_PrivateKey(key, nullptr);
    if (length <= 0) {
        if (error) *error = opensslError(QStringLiteral("private key export failed"));
        return {};
    }
    QByteArray output(length, ::Qt::Uninitialized);
    unsigned char *cursor = reinterpret_cast<unsigned char *>(output.data());
    if (i2d_PrivateKey(key, &cursor) != length) {
        if (error) *error = opensslError(QStringLiteral("private key export failed"));
        return {};
    }
    return output;
}

QByteArray publicKeySpki(EVP_PKEY *key, QString *error) {
    const int length = i2d_PUBKEY(key, nullptr);
    if (length <= 0) {
        if (error) *error = opensslError(QStringLiteral("public key export failed"));
        return {};
    }
    QByteArray output(length, ::Qt::Uninitialized);
    unsigned char *cursor = reinterpret_cast<unsigned char *>(output.data());
    if (i2d_PUBKEY(key, &cursor) != length) {
        if (error) *error = opensslError(QStringLiteral("public key export failed"));
        return {};
    }
    return output;
}

bool isP256(EVP_PKEY *key) {
    if (!key || EVP_PKEY_base_id(key) != EVP_PKEY_EC) return false;
#if OPENSSL_VERSION_NUMBER >= 0x30000000L
    char groupName[80] = {};
    size_t length = 0;
    return EVP_PKEY_get_utf8_string_param(key, OSSL_PKEY_PARAM_GROUP_NAME,
        groupName, sizeof(groupName), &length) == 1
        && (QByteArray(groupName, int(length)) == "prime256v1"
            || QByteArray(groupName, int(length)) == "P-256");
#else
    EC_KEY *ec = EVP_PKEY_get1_EC_KEY(key);
    if (!ec) return false;
    const EC_GROUP *group = EC_KEY_get0_group(ec);
    const bool valid = group && EC_GROUP_get_curve_name(group) == NID_X9_62_prime256v1;
    EC_KEY_free(ec);
    return valid;
#endif
}

QByteArray signP1363(EVP_PKEY *privateKey, const QByteArray &message, QString *error) {
    EVP_MD_CTX *context = EVP_MD_CTX_new();
    size_t length = 0;
    bool ok = context && EVP_DigestSignInit(context, nullptr, EVP_sha256(), nullptr, privateKey) == 1
        && EVP_DigestSign(context, nullptr, &length,
                          reinterpret_cast<const unsigned char *>(message.constData()), size_t(message.size())) == 1;
    QByteArray der(int(length), ::Qt::Uninitialized);
    ok = ok && EVP_DigestSign(context, reinterpret_cast<unsigned char *>(der.data()), &length,
                              reinterpret_cast<const unsigned char *>(message.constData()), size_t(message.size())) == 1;
    EVP_MD_CTX_free(context);
    if (!ok) {
        if (error) *error = opensslError(QStringLiteral("ECDSA signing failed"));
        return {};
    }
    der.resize(int(length));
    return derToP1363(reinterpret_cast<const unsigned char *>(der.constData()), der.size(), error);
}

bool verifyP1363(EVP_PKEY *publicKey, const QByteArray &message,
                 const QByteArray &signature, QString *error) {
    const QByteArray der = p1363ToDer(signature, error);
    if (der.isEmpty()) return false;
    EVP_MD_CTX *context = EVP_MD_CTX_new();
    const bool ok = context && EVP_DigestVerifyInit(context, nullptr, EVP_sha256(), nullptr, publicKey) == 1
        && EVP_DigestVerify(context,
            reinterpret_cast<const unsigned char *>(der.constData()), size_t(der.size()),
            reinterpret_cast<const unsigned char *>(message.constData()), size_t(message.size())) == 1;
    EVP_MD_CTX_free(context);
    if (!ok && error && error->isEmpty()) *error = opensslError(QStringLiteral("ECDSA verification failed"));
    return ok;
}

QByteArray deriveEcdh(EVP_PKEY *privateKey, EVP_PKEY *peerPublicKey, QString *error) {
    EVP_PKEY_CTX *context = EVP_PKEY_CTX_new(privateKey, nullptr);
    size_t length = 0;
    bool ok = context && EVP_PKEY_derive_init(context) == 1
        && EVP_PKEY_derive_set_peer(context, peerPublicKey) == 1
        && EVP_PKEY_derive(context, nullptr, &length) == 1;
    QByteArray output(int(length), ::Qt::Uninitialized);
    ok = ok && EVP_PKEY_derive(context, reinterpret_cast<unsigned char *>(output.data()), &length) == 1;
    EVP_PKEY_CTX_free(context);
    if (!ok) {
        if (error) *error = opensslError(QStringLiteral("ECDH key derivation failed"));
        return {};
    }
    output.resize(int(length));
    return output;
}

QByteArray hkdfSha256(const QByteArray &secret, const QByteArray &salt,
                      const QByteArray &info, int length, QString *error) {
    EVP_PKEY_CTX *context = EVP_PKEY_CTX_new_id(EVP_PKEY_HKDF, nullptr);
    QByteArray output(length, ::Qt::Uninitialized);
    size_t outputLength = size_t(length);
    const bool ok = context && EVP_PKEY_derive_init(context) == 1
        && EVP_PKEY_CTX_set_hkdf_md(context, EVP_sha256()) == 1
        && EVP_PKEY_CTX_set1_hkdf_salt(context,
            reinterpret_cast<const unsigned char *>(salt.constData()), salt.size()) == 1
        && EVP_PKEY_CTX_set1_hkdf_key(context,
            reinterpret_cast<const unsigned char *>(secret.constData()), secret.size()) == 1
        && EVP_PKEY_CTX_add1_hkdf_info(context,
            reinterpret_cast<const unsigned char *>(info.constData()), info.size()) == 1
        && EVP_PKEY_derive(context, reinterpret_cast<unsigned char *>(output.data()), &outputLength) == 1;
    EVP_PKEY_CTX_free(context);
    if (!ok || outputLength != size_t(length)) {
        if (error) *error = opensslError(QStringLiteral("HKDF-SHA256 derivation failed"));
        return {};
    }
    return output;
}

Ciphertext aes256GcmEncrypt(const QByteArray &key, const QByteArray &nonce,
                            const QByteArray &aad, const QByteArray &plaintext) {
    Ciphertext result;
    if (key.size() != 32 || nonce.size() != 12) {
        result.error = QStringLiteral("AES-GCM key or nonce length is invalid");
        return result;
    }
    EVP_CIPHER_CTX *context = EVP_CIPHER_CTX_new();
    QByteArray output(plaintext.size() + 16, ::Qt::Uninitialized);
    int written = 0, total = 0;
    bool ok = context && EVP_EncryptInit_ex(context, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1
        && EVP_CIPHER_CTX_ctrl(context, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr) == 1
        && EVP_EncryptInit_ex(context, nullptr, nullptr,
            reinterpret_cast<const unsigned char *>(key.constData()),
            reinterpret_cast<const unsigned char *>(nonce.constData())) == 1
        && EVP_EncryptUpdate(context, nullptr, &written,
            reinterpret_cast<const unsigned char *>(aad.constData()), aad.size()) == 1
        && EVP_EncryptUpdate(context, reinterpret_cast<unsigned char *>(output.data()), &written,
            reinterpret_cast<const unsigned char *>(plaintext.constData()), plaintext.size()) == 1;
    total = written;
    ok = ok && EVP_EncryptFinal_ex(context,
        reinterpret_cast<unsigned char *>(output.data()) + total, &written) == 1;
    total += written;
    ok = ok && EVP_CIPHER_CTX_ctrl(context, EVP_CTRL_GCM_GET_TAG, 16, output.data() + total) == 1;
    EVP_CIPHER_CTX_free(context);
    if (!ok) result.error = opensslError(QStringLiteral("AES-GCM encryption failed"));
    else { output.resize(total + 16); result.bytes = output; }
    return result;
}

Ciphertext aes256GcmDecrypt(const QByteArray &key, const QByteArray &nonce,
                            const QByteArray &aad, const QByteArray &ciphertextAndTag) {
    Ciphertext result;
    if (key.size() != 32 || nonce.size() != 12 || ciphertextAndTag.size() < 16) {
        result.error = QStringLiteral("AES-GCM input length is invalid");
        return result;
    }
    const int cipherLength = ciphertextAndTag.size() - 16;
    EVP_CIPHER_CTX *context = EVP_CIPHER_CTX_new();
    QByteArray output(cipherLength, ::Qt::Uninitialized);
    int written = 0, total = 0;
    bool ok = context && EVP_DecryptInit_ex(context, EVP_aes_256_gcm(), nullptr, nullptr, nullptr) == 1
        && EVP_CIPHER_CTX_ctrl(context, EVP_CTRL_GCM_SET_IVLEN, nonce.size(), nullptr) == 1
        && EVP_DecryptInit_ex(context, nullptr, nullptr,
            reinterpret_cast<const unsigned char *>(key.constData()),
            reinterpret_cast<const unsigned char *>(nonce.constData())) == 1
        && EVP_DecryptUpdate(context, nullptr, &written,
            reinterpret_cast<const unsigned char *>(aad.constData()), aad.size()) == 1
        && EVP_DecryptUpdate(context, reinterpret_cast<unsigned char *>(output.data()), &written,
            reinterpret_cast<const unsigned char *>(ciphertextAndTag.constData()), cipherLength) == 1;
    total = written;
    ok = ok && EVP_CIPHER_CTX_ctrl(context, EVP_CTRL_GCM_SET_TAG, 16,
        const_cast<char *>(ciphertextAndTag.constData() + cipherLength)) == 1
        && EVP_DecryptFinal_ex(context,
            reinterpret_cast<unsigned char *>(output.data()) + total, &written) == 1;
    total += written;
    EVP_CIPHER_CTX_free(context);
    if (!ok) result.error = QStringLiteral("AES-GCM authentication failed");
    else { output.resize(total); result.bytes = output; }
    return result;
}

} // namespace Synap::Qt::Internal

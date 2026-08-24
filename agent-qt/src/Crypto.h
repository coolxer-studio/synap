#pragma once

#include <QtCore/QByteArray>
#include <QtCore/QString>

#include <memory>

typedef struct evp_pkey_st EVP_PKEY;

namespace Synap::Qt::Internal {

struct PKeyDeleter { void operator()(EVP_PKEY *key) const; };
using PKey = std::unique_ptr<EVP_PKEY, PKeyDeleter>;

struct Ciphertext {
    QByteArray bytes;
    QString error;
};

QByteArray base64UrlEncode(const QByteArray &value);
QByteArray base64UrlDecode(const QByteArray &value, bool *ok = nullptr);
bool constantTimeEqual(const QByteArray &left, const QByteArray &right);
QByteArray randomBytes(int size, QString *error = nullptr);
QByteArray sha256(const QByteArray &value);

PKey generateP256(QString *error = nullptr);
PKey privateKeyFromDer(const QByteArray &der, QString *error = nullptr);
PKey publicKeyFromSpki(const QByteArray &spki, QString *error = nullptr);
QByteArray privateKeyDer(EVP_PKEY *key, QString *error = nullptr);
QByteArray publicKeySpki(EVP_PKEY *key, QString *error = nullptr);
bool isP256(EVP_PKEY *key);

QByteArray signP1363(EVP_PKEY *privateKey, const QByteArray &message,
                     QString *error = nullptr);
bool verifyP1363(EVP_PKEY *publicKey, const QByteArray &message,
                 const QByteArray &signature, QString *error = nullptr);
QByteArray deriveEcdh(EVP_PKEY *privateKey, EVP_PKEY *peerPublicKey,
                      QString *error = nullptr);
QByteArray hkdfSha256(const QByteArray &secret, const QByteArray &salt,
                      const QByteArray &info, int length, QString *error = nullptr);

Ciphertext aes256GcmEncrypt(const QByteArray &key, const QByteArray &nonce,
                            const QByteArray &aad, const QByteArray &plaintext);
Ciphertext aes256GcmDecrypt(const QByteArray &key, const QByteArray &nonce,
                            const QByteArray &aad, const QByteArray &ciphertextAndTag);

} // namespace Synap::Qt::Internal


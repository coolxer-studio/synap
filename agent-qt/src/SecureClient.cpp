#include "SecureClient.h"

#include <QtCore/QDateTime>
#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonParseError>
#include <QtCore/QPointer>
#include <QtCore/QRegularExpression>
#include <QtCore/QTimer>
#include <QtCore/QUuid>
#include <QtCore/QtEndian>
#include <QtNetwork/QNetworkAccessManager>
#include <QtNetwork/QNetworkReply>
#include <QtNetwork/QNetworkRequest>
#include <QtNetwork/QSslConfiguration>

#include <openssl/crypto.h>

#include <algorithm>

namespace Synap::Qt::Internal {
namespace {

constexpr auto kSuite = "P256_HKDF_SHA256_AES256_GCM";
constexpr auto kEnvelopeMediaType = "application/sc-envelope+json";
constexpr auto kProtectedMediaType = "application/sc-protected+json";

void cleanse(QByteArray &value) {
    if (!value.isEmpty()) OPENSSL_cleanse(value.data(), size_t(value.size()));
    value.clear();
}

QJsonObject parseObject(const QByteArray &body, QString *error) {
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(body, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        if (error) *error = QStringLiteral("response is not a JSON object");
        return {};
    }
    return document.object();
}

QString mediaType(const QString &value) {
    return value.section(QLatin1Char(';'), 0, 0).trimmed().toLower();
}

bool visibleIdentifier(const QString &value) {
    if (value.isEmpty() || value.size() > 128) return false;
    for (const QChar c : value) if (c.unicode() < 0x21 || c.unicode() > 0x7e) return false;
    return true;
}

bool validLogicalPath(const QString &path) {
    static const QStringList allowed{
        QStringLiteral("/attach/start"), QStringLiteral("/attach/upload"),
        QStringLiteral("/cross/info"), QStringLiteral("/cross/rating"),
        QStringLiteral("/cross/label_start")
    };
    return allowed.contains(path);
}

} // namespace

SecureClient::SecureClient(AgentConfig config, QObject *parent)
    : QObject(parent), config_(std::move(config)), network_(new QNetworkAccessManager(this)) {}

SecureClient::~SecureClient() { closeSession(); }

SecureFailure SecureClient::failure(QString code, QString message, int status, bool retryable) {
    return {std::move(code), std::move(message), status, retryable};
}

void SecureClient::initialize(InitCallback callback) {
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (sessionActive_ && now < session_.expiresAt) {
        callback({});
        return;
    }
    if (sessionActive_) closeSession();
    initCallbacks_.append(std::move(callback));
    if (initializing_) return;
    initializing_ = true;
    beginHandshake();
}

void SecureClient::beginHandshake() {
    QString error;
    IdentityStore store(config_.dataDirectory);
    identity_ = store.loadOrCreate(config_.secureAppId, &error);
    if (!identity_.key) {
        finishInitialization(failure(QStringLiteral("SC_IDENTITY_FAILED"), error));
        return;
    }
    ephemeral_ = generateP256(&error);
    const QByteArray clientEphemeral = publicKeySpki(ephemeral_.get(), &error);
    const QByteArray installation = publicKeySpki(identity_.key.get(), &error);
    if (!ephemeral_ || clientEphemeral.isEmpty() || installation.isEmpty()) {
        finishInitialization(failure(QStringLiteral("SC_CRYPTO_FAILED"), error));
        return;
    }

    QString token;
    if (config_.enrollmentTokenProvider) {
        token = config_.enrollmentTokenProvider().trimmed();
        config_.enrollmentTokenProvider = {};
    }
    const QJsonObject request{
        {QStringLiteral("v"), 1},
        {QStringLiteral("suite"), QString::fromLatin1(kSuite)},
        {QStringLiteral("appId"), config_.secureAppId},
        {QStringLiteral("deviceId"), identity_.deviceId},
        {QStringLiteral("deviceType"), QStringLiteral("HOST")},
        {QStringLiteral("clientEphemeralPublicKey"), QString::fromLatin1(base64UrlEncode(clientEphemeral))},
        {QStringLiteral("installationPublicKey"), QString::fromLatin1(base64UrlEncode(installation))},
        {QStringLiteral("enrollmentToken"), token.isEmpty() ? QJsonValue(QJsonValue::Null) : QJsonValue(token)},
        {QStringLiteral("timestamp"), QJsonValue(double(QDateTime::currentMSecsSinceEpoch()))}
    };
    token.fill(QChar(u'\0'));
    post(QStringLiteral("/sc/v1/handshake"), "application/json", "application/json",
         QJsonDocument(request).toJson(QJsonDocument::Compact),
         [this, clientEphemeral, installation](const HttpResult &result) {
             handleHandshakeStart(result, clientEphemeral, installation);
         });
}

void SecureClient::handleHandshakeStart(const HttpResult &result, const QByteArray &clientEphemeral,
                                        const QByteArray &installation) {
    if (!result.networkError.isEmpty()) {
        finishInitialization(failure(QStringLiteral("SC_NETWORK_FAILED"), result.networkError, 0, true));
        return;
    }
    QString parseError;
    const QJsonObject response = parseObject(result.body, &parseError);
    if (result.status < 200 || result.status >= 300) {
        finishInitialization(failure(response.value(QStringLiteral("code")).toString(QStringLiteral("SC_HANDSHAKE_FAILED")),
            response.value(QStringLiteral("message")).toString(parseError), result.status,
            result.status == 429 || result.status >= 500));
        return;
    }
    const QString kid = response.value(QStringLiteral("kid")).toString();
    const QString sid = response.value(QStringLiteral("sid")).toString();
    const qint64 createdAt = response.value(QStringLiteral("createdAt")).toVariant().toLongLong();
    const qint64 expiresAt = response.value(QStringLiteral("expiresAt")).toVariant().toLongLong();
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (response.value(QStringLiteral("v")).toInt() != 1
        || response.value(QStringLiteral("suite")).toString() != QString::fromLatin1(kSuite)
        || kid != config_.serverKeyId || !visibleIdentifier(kid) || !visibleIdentifier(sid)
        || createdAt <= 0 || expiresAt <= now
        || qAbs(now - createdAt) > config_.allowedClockSkewMillis) {
        finishInitialization(failure(QStringLiteral("SC_HANDSHAKE_FAILED"),
                                     QStringLiteral("handshake metadata or server identity is invalid")));
        return;
    }
    bool anchorOk = false, identityOk = false, ephemeralOk = false, signatureOk = false;
    const QByteArray anchor = base64UrlDecode(config_.trustAnchorBase64Url.toLatin1(), &anchorOk);
    const QByteArray serverIdentity = base64UrlDecode(response.value(QStringLiteral("serverIdentityPublicKey")).toString().toLatin1(), &identityOk);
    const QByteArray serverEphemeral = base64UrlDecode(response.value(QStringLiteral("serverEphemeralPublicKey")).toString().toLatin1(), &ephemeralOk);
    const QByteArray signature = base64UrlDecode(response.value(QStringLiteral("signature")).toString().toLatin1(), &signatureOk);
    if (!anchorOk || !identityOk || !ephemeralOk || !signatureOk
        || !constantTimeEqual(anchor, serverIdentity)) {
        finishInitialization(failure(QStringLiteral("SC_HANDSHAKE_FAILED"),
                                     QStringLiteral("server identity pin does not match")));
        return;
    }

    const QByteArray transcript = QByteArray("SC1-HANDSHAKE\n1\n") + kSuite + '\n'
        + config_.secureAppId.toUtf8() + '\n' + identity_.deviceId.toUtf8() + "\nHOST\n"
        + base64UrlEncode(clientEphemeral) + '\n' + base64UrlEncode(installation) + '\n'
        + base64UrlEncode(serverIdentity) + '\n' + base64UrlEncode(serverEphemeral) + '\n'
        + kid.toUtf8() + '\n' + sid.toUtf8() + '\n' + QByteArray::number(createdAt) + '\n'
        + QByteArray::number(expiresAt);
    const QByteArray transcriptHash = sha256(transcript);
    QString cryptoError;
    PKey signingKey = publicKeyFromSpki(serverIdentity, &cryptoError);
    PKey peerKey = publicKeyFromSpki(serverEphemeral, &cryptoError);
    if (!signingKey || !peerKey
        || !verifyP1363(signingKey.get(), transcriptHash, signature, &cryptoError)) {
        finishInitialization(failure(QStringLiteral("SC_HANDSHAKE_FAILED"),
                                     cryptoError.isEmpty() ? QStringLiteral("server proof is invalid") : cryptoError));
        return;
    }
    QByteArray sharedSecret = deriveEcdh(ephemeral_.get(), peerKey.get(), &cryptoError);
    const QByteArray info = QByteArray("SC1/session/") + kSuite + '/' + sid.toUtf8();
    QByteArray material = hkdfSha256(sharedSecret, transcriptHash, info, 72, &cryptoError);
    cleanse(sharedSecret);
    if (material.size() != 72) {
        finishInitialization(failure(QStringLiteral("SC_HANDSHAKE_FAILED"), cryptoError));
        return;
    }
    session_.kid = kid;
    session_.sid = sid;
    session_.requestKey = material.mid(0, 32);
    session_.responseKey = material.mid(32, 32);
    session_.requestNoncePrefix = material.mid(64, 4);
    session_.responseNoncePrefix = material.mid(68, 4);
    session_.expiresAt = expiresAt;
    session_.nextSequence = 1;
    cleanse(material);

    const QByteArray proof = signP1363(identity_.key.get(), transcriptHash, &cryptoError);
    if (proof.isEmpty()) {
        closeSession();
        finishInitialization(failure(QStringLiteral("SC_HANDSHAKE_FAILED"), cryptoError));
        return;
    }
    const QJsonObject finish{
        {QStringLiteral("kid"), kid}, {QStringLiteral("sid"), sid},
        {QStringLiteral("proof"), QString::fromLatin1(base64UrlEncode(proof))}
    };
    post(QStringLiteral("/sc/v1/handshake/finish"), "application/json", "application/json",
         QJsonDocument(finish).toJson(QJsonDocument::Compact),
         [this](const HttpResult &finishResult) {
             if (!finishResult.networkError.isEmpty()) {
                 closeSession();
                 finishInitialization(failure(QStringLiteral("SC_NETWORK_FAILED"), finishResult.networkError, 0, true));
                 return;
             }
             QString error;
             const QJsonObject object = parseObject(finishResult.body, &error);
             if (finishResult.status < 200 || finishResult.status >= 300
                 || !object.value(QStringLiteral("active")).toBool()) {
                 closeSession();
                 finishInitialization(failure(object.value(QStringLiteral("code")).toString(QStringLiteral("SC_HANDSHAKE_FAILED")),
                     object.value(QStringLiteral("message")).toString(error), finishResult.status,
                     finishResult.status == 429 || finishResult.status >= 500));
                 return;
             }
             sessionActive_ = true;
             ephemeral_.reset();
             finishInitialization();
         });
}

void SecureClient::finishInitialization(const SecureFailure &failureValue) {
    initializing_ = false;
    if (!failureValue.code.isEmpty()) {
        ephemeral_.reset();
        if (!sessionActive_) closeSession();
    }
    const QVector<InitCallback> callbacks = std::move(initCallbacks_);
    initCallbacks_.clear();
    for (const InitCallback &callback : callbacks) callback(failureValue);
}

void SecureClient::request(const QString &logicalPath, const QString &code, const QByteArray &body,
                           RequestCallback callback) {
    if (!validLogicalPath(logicalPath)) {
        callback({}, failure(QStringLiteral("SC_ROUTE_MISMATCH"), QStringLiteral("logical path is not allowed")));
        return;
    }
    initialize([this, logicalPath, code, body, callback = std::move(callback)](const SecureFailure &initFailure) {
        if (!initFailure.code.isEmpty()) { callback({}, initFailure); return; }
        sendRequest(logicalPath, code, body, callback);
    });
}

QByteArray SecureClient::nonce(const QByteArray &prefix, quint64 sequence) {
    if (prefix.size() != 4) return {};
    QByteArray output(12, ::Qt::Uninitialized);
    std::copy(prefix.cbegin(), prefix.cend(), output.begin());
    qToBigEndian(sequence, reinterpret_cast<uchar *>(output.data() + 4));
    return output;
}

QByteArray SecureClient::aad(const QByteArray &direction, const QJsonObject &envelope) {
    QByteArray value = QByteArray("SC1\n") + direction + '\n'
        + envelope.value(QStringLiteral("suite")).toString().toUtf8() + '\n'
        + envelope.value(QStringLiteral("kid")).toString().toUtf8() + '\n'
        + envelope.value(QStringLiteral("sid")).toString().toUtf8() + '\n'
        + QByteArray::number(envelope.value(QStringLiteral("ts")).toVariant().toLongLong()) + '\n'
        + QByteArray::number(envelope.value(QStringLiteral("seq")).toVariant().toULongLong()) + '\n'
        + envelope.value(QStringLiteral("rid")).toString().toUtf8() + '\n'
        + envelope.value(QStringLiteral("m")).toString().toUtf8() + '\n'
        + envelope.value(QStringLiteral("p")).toString().toUtf8() + '\n'
        + envelope.value(QStringLiteral("cty")).toString().toUtf8();
    if (direction == "response") value += '\n' + QByteArray::number(envelope.value(QStringLiteral("st")).toInt());
    return value;
}

void SecureClient::sendRequest(const QString &logicalPath, const QString &code,
                               const QByteArray &body, RequestCallback callback) {
    if (!sessionActive_ || session_.nextSequence == 0) {
        callback({}, failure(QStringLiteral("SC_UNKNOWN_SESSION"), QStringLiteral("secure session is unavailable"), 0, true));
        return;
    }
    const quint64 sequence = session_.nextSequence++;
    const QString requestId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    const QJsonObject protectedPayload{
        {QStringLiteral("method"), QStringLiteral("POST")},
        {QStringLiteral("path"), logicalPath},
        {QStringLiteral("contentType"), QStringLiteral("application/json")},
        {QStringLiteral("headers"), QJsonObject{{QStringLiteral("code"), code}}},
        {QStringLiteral("body"), QString::fromLatin1(base64UrlEncode(body))}
    };
    QJsonObject envelope{
        {QStringLiteral("v"), 1}, {QStringLiteral("suite"), QString::fromLatin1(kSuite)},
        {QStringLiteral("kid"), session_.kid}, {QStringLiteral("sid"), session_.sid},
        {QStringLiteral("ts"), QJsonValue(double(QDateTime::currentMSecsSinceEpoch()))},
        {QStringLiteral("seq"), QJsonValue(double(sequence))}, {QStringLiteral("rid"), requestId},
        {QStringLiteral("m"), QStringLiteral("POST")}, {QStringLiteral("p"), QStringLiteral("/sc/v1/message")},
        {QStringLiteral("cty"), QString::fromLatin1(kProtectedMediaType)},
        {QStringLiteral("st"), 0}, {QStringLiteral("nonce"), QString()}, {QStringLiteral("ct"), QString()}
    };
    const QByteArray requestNonce = nonce(session_.requestNoncePrefix, sequence);
    envelope.insert(QStringLiteral("nonce"), QString::fromLatin1(base64UrlEncode(requestNonce)));
    const Ciphertext encrypted = aes256GcmEncrypt(session_.requestKey, requestNonce, aad("request", envelope),
        QJsonDocument(protectedPayload).toJson(QJsonDocument::Compact));
    if (!encrypted.error.isEmpty()) {
        callback({}, failure(QStringLiteral("SC_CRYPTO_FAILED"), encrypted.error));
        return;
    }
    envelope.insert(QStringLiteral("ct"), QString::fromLatin1(base64UrlEncode(encrypted.bytes)));
    post(QStringLiteral("/sc/v1/message"), kEnvelopeMediaType, kEnvelopeMediaType,
         QJsonDocument(envelope).toJson(QJsonDocument::Compact),
         [this, sequence, requestId, callback = std::move(callback)](const HttpResult &result) {
             if (!result.networkError.isEmpty()) {
                 callback({}, failure(QStringLiteral("SC_NETWORK_FAILED"), result.networkError, 0, true));
                 return;
             }
             if (mediaType(result.contentType) != QString::fromLatin1(kEnvelopeMediaType)) {
                 QString ignored;
                 const QJsonObject object = parseObject(result.body, &ignored);
                 const QString code = object.value(QStringLiteral("code")).toString(QStringLiteral("SC_TRANSPORT_FAILED"));
                 if (code == QStringLiteral("SC_UNKNOWN_SESSION")) closeSession();
                 callback({}, failure(code,
                     object.value(QStringLiteral("message")).toString(QStringLiteral("secure transport failed")),
                     result.status, result.status == 429 || result.status >= 500
                         || code == QStringLiteral("SC_UNKNOWN_SESSION")));
                 return;
             }
             SecureResponse response;
             const SecureFailure decodeFailure = decodeResponse(result.body, sequence, requestId, &response);
             if (decodeFailure.code == QStringLiteral("SC_UNKNOWN_SESSION")) closeSession();
             callback(response, decodeFailure);
         });
}

SecureFailure SecureClient::decodeResponse(const QByteArray &encoded, quint64 expectedSequence,
                                           const QString &requestId, SecureResponse *response) const {
    QString parseError;
    const QJsonObject envelope = parseObject(encoded, &parseError);
    QStringList keys = envelope.keys();
    keys.sort();
    const QStringList expected{QStringLiteral("ct"), QStringLiteral("cty"), QStringLiteral("kid"),
        QStringLiteral("m"), QStringLiteral("nonce"), QStringLiteral("p"), QStringLiteral("rid"),
        QStringLiteral("seq"), QStringLiteral("sid"), QStringLiteral("st"), QStringLiteral("suite"),
        QStringLiteral("ts"), QStringLiteral("v")};
    const qint64 timestamp = envelope.value(QStringLiteral("ts")).toVariant().toLongLong();
    const quint64 sequence = envelope.value(QStringLiteral("seq")).toVariant().toULongLong();
    if (keys != expected || envelope.value(QStringLiteral("v")).toInt() != 1
        || envelope.value(QStringLiteral("suite")).toString() != QString::fromLatin1(kSuite)
        || envelope.value(QStringLiteral("kid")).toString() != session_.kid
        || envelope.value(QStringLiteral("sid")).toString() != session_.sid
        || sequence != expectedSequence || envelope.value(QStringLiteral("rid")).toString() != requestId
        || envelope.value(QStringLiteral("m")).toString() != QStringLiteral("POST")
        || envelope.value(QStringLiteral("p")).toString() != QStringLiteral("/sc/v1/message")
        || envelope.value(QStringLiteral("cty")).toString() != QString::fromLatin1(kProtectedMediaType)
        || qAbs(QDateTime::currentMSecsSinceEpoch() - timestamp) > config_.allowedClockSkewMillis
        || envelope.value(QStringLiteral("st")).toInt() < 100
        || envelope.value(QStringLiteral("st")).toInt() > 599) {
        return failure(QStringLiteral("SC_INVALID_ENVELOPE"),
                       parseError.isEmpty() ? QStringLiteral("response envelope is invalid") : parseError);
    }
    bool nonceOk = false, cipherOk = false;
    const QByteArray responseNonce = base64UrlDecode(envelope.value(QStringLiteral("nonce")).toString().toLatin1(), &nonceOk);
    const QByteArray ciphertext = base64UrlDecode(envelope.value(QStringLiteral("ct")).toString().toLatin1(), &cipherOk);
    if (!nonceOk || !cipherOk || !constantTimeEqual(responseNonce, nonce(session_.responseNoncePrefix, sequence)))
        return failure(QStringLiteral("SC_INVALID_ENVELOPE"), QStringLiteral("response nonce or ciphertext is invalid"));
    const Ciphertext decrypted = aes256GcmDecrypt(session_.responseKey, responseNonce,
                                                  aad("response", envelope), ciphertext);
    if (!decrypted.error.isEmpty())
        return failure(QStringLiteral("SC_AUTHENTICATION_FAILED"), decrypted.error);
    const QJsonObject payload = parseObject(decrypted.bytes, &parseError);
    QStringList payloadKeys = payload.keys();
    payloadKeys.sort();
    bool bodyOk = false;
    const QByteArray decodedBody = base64UrlDecode(payload.value(QStringLiteral("body")).toString().toLatin1(), &bodyOk);
    if (payloadKeys != QStringList{QStringLiteral("body"), QStringLiteral("contentType")}
        || !bodyOk || payload.value(QStringLiteral("contentType")).toString().isEmpty())
        return failure(QStringLiteral("SC_INVALID_ENVELOPE"), QStringLiteral("protected response is invalid"));
    response->status = envelope.value(QStringLiteral("st")).toInt();
    response->contentType = payload.value(QStringLiteral("contentType")).toString();
    response->body = decodedBody;
    return {};
}

void SecureClient::post(const QString &path, const QByteArray &contentType, const QByteArray &accept,
                        const QByteArray &body, HttpCallback callback) {
    QUrl url(config_.baseUrl.toString(QUrl::RemoveQuery | QUrl::RemoveFragment).remove(QRegularExpression(QStringLiteral("/+$"))) + path);
    QNetworkRequest request(url);
    request.setHeader(QNetworkRequest::ContentTypeHeader, contentType);
    request.setRawHeader("Accept", accept);
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
    if (url.scheme().compare(QStringLiteral("https"), ::Qt::CaseInsensitive) == 0) {
        QSslConfiguration ssl = QSslConfiguration::defaultConfiguration();
        ssl.setProtocol(QSsl::TlsV1_2OrLater);
        request.setSslConfiguration(ssl);
    }
    QNetworkReply *reply = network_->post(request, body);
    QTimer *timer = new QTimer(reply);
    timer->setSingleShot(true);
    timer->start(config_.requestTimeoutMillis);
    connect(timer, &QTimer::timeout, reply, [reply] { reply->setProperty("synapTimeout", true); reply->abort(); });
    connect(reply, &QNetworkReply::finished, this, [reply, callback = std::move(callback)] {
        HttpResult result;
        result.status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        result.contentType = QString::fromLatin1(reply->rawHeader("Content-Type"));
        result.body = reply->readAll();
        if (result.status == 0 && reply->error() != QNetworkReply::NoError) {
            result.networkError = reply->property("synapTimeout").toBool()
                ? QStringLiteral("request timed out") : reply->errorString();
        }
        reply->deleteLater();
        callback(result);
    });
}

void SecureClient::closeSession() {
    cleanse(session_.requestKey);
    cleanse(session_.responseKey);
    cleanse(session_.requestNoncePrefix);
    cleanse(session_.responseNoncePrefix);
    session_ = {};
    sessionActive_ = false;
}

} // namespace Synap::Qt::Internal

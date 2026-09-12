#pragma once

#include "Crypto.h"
#include "IdentityStore.h"

#include <synap/qt/AgentConfig.h>

#include <QtCore/QJsonObject>
#include <QtCore/QObject>
#include <QtCore/QVector>

#include <functional>

class QNetworkAccessManager;
class QNetworkReply;

namespace Synap::Qt::Internal {

struct SecureFailure {
    QString code;
    QString message;
    int httpStatus = 0;
    bool retryable = false;
};

struct SecureResponse {
    int status = 0;
    QString contentType;
    QByteArray body;
};

class SecureClient final : public QObject {
    Q_OBJECT
public:
    using InitCallback = std::function<void(const SecureFailure &)>;
    using RequestCallback = std::function<void(const SecureResponse &, const SecureFailure &)>;

    explicit SecureClient(AgentConfig config, QObject *parent = nullptr);
    ~SecureClient() override;

    void initialize(InitCallback callback);
    void request(const QString &logicalPath, const QString &code, const QByteArray &body,
                 RequestCallback callback);
    void closeSession();

private:
    struct HttpResult {
        int status = 0;
        QString contentType;
        QByteArray body;
        QString networkError;
    };
    struct Session {
        QString kid;
        QString sid;
        QByteArray requestKey;
        QByteArray responseKey;
        QByteArray requestNoncePrefix;
        QByteArray responseNoncePrefix;
        qint64 expiresAt = 0;
        quint64 nextSequence = 1;
    };

    using HttpCallback = std::function<void(const HttpResult &)>;

    void post(const QString &path, const QByteArray &contentType, const QByteArray &accept,
              const QByteArray &body, HttpCallback callback);
    void beginHandshake();
    void handleHandshakeStart(const HttpResult &result, const QByteArray &clientEphemeral,
                              const QByteArray &installation);
    void finishInitialization(const SecureFailure &failure = {});
    void sendRequest(const QString &logicalPath, const QString &code, const QByteArray &body,
                     RequestCallback callback);
    SecureFailure decodeResponse(const QByteArray &encoded, quint64 expectedSequence,
                                 const QString &requestId, SecureResponse *response) const;
    static QByteArray nonce(const QByteArray &prefix, quint64 sequence);
    static QByteArray aad(const QByteArray &direction, const QJsonObject &envelope);
    static SecureFailure failure(QString code, QString message, int status = 0,
                                 bool retryable = false);

    AgentConfig config_;
    QNetworkAccessManager *network_ = nullptr;
    InstallationIdentity identity_;
    PKey ephemeral_;
    Session session_;
    bool sessionActive_ = false;
    bool initializing_ = false;
    QVector<InitCallback> initCallbacks_;
};

} // namespace Synap::Qt::Internal

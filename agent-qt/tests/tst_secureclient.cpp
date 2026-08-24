#include "Crypto.h"
#include "SecureClient.h"

#include <QtCore/QDateTime>
#include <QtCore/QEventLoop>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QTemporaryDir>
#include <QtCore/QTimer>
#include <QtCore/QtEndian>
#include <QtNetwork/QHostAddress>
#include <QtNetwork/QTcpServer>
#include <QtNetwork/QTcpSocket>
#include <QtTest/QTest>

using namespace Synap::Qt;
using namespace Synap::Qt::Internal;

namespace {
constexpr auto kSuite = "P256_HKDF_SHA256_AES256_GCM";
constexpr auto kProtected = "application/sc-protected+json";
constexpr auto kEnvelope = "application/sc-envelope+json";

QByteArray protocolNonce(const QByteArray &prefix, quint64 sequence) {
    QByteArray value(12, ::Qt::Uninitialized);
    std::copy(prefix.cbegin(), prefix.cend(), value.begin());
    qToBigEndian(sequence, reinterpret_cast<uchar *>(value.data() + 4));
    return value;
}

QByteArray protocolAad(const QByteArray &direction, const QJsonObject &envelope) {
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

class V1Server final : public QObject {
    Q_OBJECT
public:
    explicit V1Server(QObject *parent = nullptr) : QObject(parent) {
        QString error;
        identity_ = generateP256(&error);
        QVERIFY2(identity_, qPrintable(error));
        connect(&server_, &QTcpServer::newConnection, this, &V1Server::accept);
        QVERIFY(server_.listen(QHostAddress::LocalHost, 0));
    }

    QUrl baseUrl() const {
        return QUrl(QStringLiteral("http://127.0.0.1:%1").arg(server_.serverPort()));
    }
    QString trustAnchor() const {
        QString error;
        return QString::fromLatin1(base64UrlEncode(publicKeySpki(identity_.get(), &error)));
    }
    int messageCount() const { return messageCount_; }

private:
    void accept() {
        while (QTcpSocket *socket = server_.nextPendingConnection()) {
            connect(socket, &QTcpSocket::readyRead, this, [this, socket] { read(socket); });
        }
    }

    void read(QTcpSocket *socket) {
        QByteArray buffer = socket->property("requestBuffer").toByteArray() + socket->readAll();
        const int headerEnd = buffer.indexOf("\r\n\r\n");
        if (headerEnd < 0) { socket->setProperty("requestBuffer", buffer); return; }
        const QList<QByteArray> lines = buffer.left(headerEnd).split('\n');
        int contentLength = 0;
        for (QByteArray line : lines) {
            line = line.trimmed();
            if (line.toLower().startsWith("content-length:"))
                contentLength = line.mid(line.indexOf(':') + 1).trimmed().toInt();
        }
        if (buffer.size() < headerEnd + 4 + contentLength) {
            socket->setProperty("requestBuffer", buffer); return;
        }
        const QByteArray path = lines.first().split(' ').value(1);
        const QByteArray body = buffer.mid(headerEnd + 4, contentLength);
        if (path == "/sc/v1/handshake") handshake(socket, body);
        else if (path == "/sc/v1/handshake/finish") finish(socket, body);
        else if (path == "/sc/v1/message") message(socket, body);
        else respond(socket, 404, "application/json", "{}");
    }

    void handshake(QTcpSocket *socket, const QByteArray &body) {
        const QJsonObject request = QJsonDocument::fromJson(body).object();
        bool clientOk = false, installOk = false;
        const QByteArray clientSpki = base64UrlDecode(
            request.value(QStringLiteral("clientEphemeralPublicKey")).toString().toLatin1(), &clientOk);
        const QByteArray installationSpki = base64UrlDecode(
            request.value(QStringLiteral("installationPublicKey")).toString().toLatin1(), &installOk);
        QString error;
        PKey clientPublic = publicKeyFromSpki(clientSpki, &error);
        installation_ = publicKeyFromSpki(installationSpki, &error);
        ephemeral_ = generateP256(&error);
        QVERIFY2(clientOk && installOk && clientPublic && installation_ && ephemeral_, qPrintable(error));
        const QByteArray serverIdentity = publicKeySpki(identity_.get(), &error);
        const QByteArray serverEphemeral = publicKeySpki(ephemeral_.get(), &error);
        const qint64 now = QDateTime::currentMSecsSinceEpoch();
        createdAt_ = now;
        expiresAt_ = now + 60000;
        const QByteArray transcript = QByteArray("SC1-HANDSHAKE\n1\n") + kSuite + '\n'
            + request.value(QStringLiteral("appId")).toString().toUtf8() + '\n'
            + request.value(QStringLiteral("deviceId")).toString().toUtf8() + '\n'
            + request.value(QStringLiteral("deviceType")).toString().toUtf8() + '\n'
            + base64UrlEncode(clientSpki) + '\n' + base64UrlEncode(installationSpki) + '\n'
            + base64UrlEncode(serverIdentity) + '\n' + base64UrlEncode(serverEphemeral) + '\n'
            + "test-key\ntest-session\n" + QByteArray::number(createdAt_) + '\n'
            + QByteArray::number(expiresAt_);
        transcriptHash_ = sha256(transcript);
        const QByteArray signature = signP1363(identity_.get(), transcriptHash_, &error);
        QByteArray secret = deriveEcdh(ephemeral_.get(), clientPublic.get(), &error);
        material_ = hkdfSha256(secret, transcriptHash_,
            QByteArray("SC1/session/") + kSuite + "/test-session", 72, &error);
        QVERIFY2(material_.size() == 72, qPrintable(error));
        const QJsonObject response{
            {QStringLiteral("v"), 1}, {QStringLiteral("suite"), QString::fromLatin1(kSuite)},
            {QStringLiteral("kid"), QStringLiteral("test-key")},
            {QStringLiteral("sid"), QStringLiteral("test-session")},
            {QStringLiteral("createdAt"), QJsonValue(double(createdAt_))},
            {QStringLiteral("expiresAt"), QJsonValue(double(expiresAt_))},
            {QStringLiteral("serverIdentityPublicKey"), QString::fromLatin1(base64UrlEncode(serverIdentity))},
            {QStringLiteral("serverEphemeralPublicKey"), QString::fromLatin1(base64UrlEncode(serverEphemeral))},
            {QStringLiteral("signature"), QString::fromLatin1(base64UrlEncode(signature))}
        };
        respond(socket, 200, "application/json", QJsonDocument(response).toJson(QJsonDocument::Compact));
    }

    void finish(QTcpSocket *socket, const QByteArray &body) {
        const QJsonObject request = QJsonDocument::fromJson(body).object();
        bool ok = false;
        const QByteArray proof = base64UrlDecode(request.value(QStringLiteral("proof")).toString().toLatin1(), &ok);
        QVERIFY(ok && installation_ && verifyP1363(installation_.get(), transcriptHash_, proof, nullptr));
        respond(socket, 200, "application/json", "{\"active\":true}");
    }

    void message(QTcpSocket *socket, const QByteArray &body) {
        QJsonObject envelope = QJsonDocument::fromJson(body).object();
        const quint64 sequence = envelope.value(QStringLiteral("seq")).toVariant().toULongLong();
        bool nonceOk = false, cipherOk = false;
        const QByteArray requestNonce = base64UrlDecode(
            envelope.value(QStringLiteral("nonce")).toString().toLatin1(), &nonceOk);
        const QByteArray cipher = base64UrlDecode(
            envelope.value(QStringLiteral("ct")).toString().toLatin1(), &cipherOk);
        QVERIFY(nonceOk && cipherOk);
        QCOMPARE(requestNonce, protocolNonce(material_.mid(64, 4), sequence));
        const Ciphertext opened = aes256GcmDecrypt(material_.mid(0, 32), requestNonce,
                                                   protocolAad("request", envelope), cipher);
        QVERIFY2(opened.error.isEmpty(), qPrintable(opened.error));
        const QJsonObject payload = QJsonDocument::fromJson(opened.bytes).object();
        QCOMPARE(payload.value(QStringLiteral("path")).toString(), QStringLiteral("/cross/info"));
        QCOMPARE(payload.value(QStringLiteral("headers")).toObject().value(QStringLiteral("code")).toString(),
                 QStringLiteral("a-b-c-d-e-host-7-1.0.0"));
        ++messageCount_;

        const QByteArray businessBody("{\"status\":1,\"device\":\"ok\"}");
        const QJsonObject protectedResponse{
            {QStringLiteral("contentType"), QStringLiteral("application/json")},
            {QStringLiteral("body"), QString::fromLatin1(base64UrlEncode(businessBody))}
        };
        envelope.insert(QStringLiteral("ts"), QJsonValue(double(QDateTime::currentMSecsSinceEpoch())));
        envelope.insert(QStringLiteral("st"), 200);
        const QByteArray responseNonce = protocolNonce(material_.mid(68, 4), sequence);
        envelope.insert(QStringLiteral("nonce"), QString::fromLatin1(base64UrlEncode(responseNonce)));
        envelope.insert(QStringLiteral("ct"), QString());
        const Ciphertext sealed = aes256GcmEncrypt(material_.mid(32, 32), responseNonce,
            protocolAad("response", envelope),
            QJsonDocument(protectedResponse).toJson(QJsonDocument::Compact));
        QVERIFY2(sealed.error.isEmpty(), qPrintable(sealed.error));
        envelope.insert(QStringLiteral("ct"), QString::fromLatin1(base64UrlEncode(sealed.bytes)));
        respond(socket, 200, kEnvelope, QJsonDocument(envelope).toJson(QJsonDocument::Compact));
    }

    static void respond(QTcpSocket *socket, int status, const QByteArray &contentType,
                        const QByteArray &body) {
        const QByteArray response = "HTTP/1.1 " + QByteArray::number(status) + " OK\r\nContent-Type: "
            + contentType + "\r\nContent-Length: " + QByteArray::number(body.size())
            + "\r\nConnection: close\r\n\r\n" + body;
        socket->write(response);
        socket->disconnectFromHost();
    }

    QTcpServer server_;
    PKey identity_;
    PKey ephemeral_;
    PKey installation_;
    QByteArray transcriptHash_;
    QByteArray material_;
    qint64 createdAt_ = 0;
    qint64 expiresAt_ = 0;
    int messageCount_ = 0;
};
} // namespace

class SecureClientTest final : public QObject {
    Q_OBJECT
private slots:
    void completesV1HandshakeAndProtectedRequest() {
        V1Server server;
        QTemporaryDir directory;
        AgentConfig config;
        config.baseUrl = server.baseUrl();
        config.businessAppId = 7;
        config.secureAppId = QStringLiteral("qt-integration-test");
        config.serverKeyId = QStringLiteral("test-key");
        config.trustAnchorBase64Url = server.trustAnchor();
        config.appName = QStringLiteral("test"); config.appPackage = QStringLiteral("test");
        config.appVersion = QStringLiteral("1"); config.dataDirectory = directory.path();
        config.allowInsecureLocalhost = true;
        config.requestTimeoutMillis = 3000;

        SecureClient client(config);
        SecureResponse received;
        SecureFailure receivedFailure;
        QEventLoop loop;
        client.request(QStringLiteral("/cross/info"), QStringLiteral("a-b-c-d-e-host-7-1.0.0"), "{}",
            [&](const SecureResponse &response, const SecureFailure &failure) {
                received = response; receivedFailure = failure; loop.quit();
            });
        QTimer::singleShot(5000, &loop, &QEventLoop::quit);
        loop.exec();
        QCOMPARE(receivedFailure.code, QString());
        QCOMPARE(received.status, 200);
        QCOMPARE(QJsonDocument::fromJson(received.body).object().value(QStringLiteral("device")).toString(),
                 QStringLiteral("ok"));
        QCOMPARE(server.messageCount(), 1);
    }

    void rejectsWrongTrustAnchorBeforeFinish() {
        V1Server server;
        QTemporaryDir directory;
        QString error;
        PKey other = generateP256(&error);
        AgentConfig config;
        config.baseUrl = server.baseUrl(); config.businessAppId = 7;
        config.secureAppId = QStringLiteral("qt-wrong-anchor");
        config.serverKeyId = QStringLiteral("test-key");
        config.trustAnchorBase64Url = QString::fromLatin1(base64UrlEncode(publicKeySpki(other.get(), &error)));
        config.appName = QStringLiteral("test"); config.appPackage = QStringLiteral("test");
        config.appVersion = QStringLiteral("1"); config.dataDirectory = directory.path();
        config.allowInsecureLocalhost = true; config.requestTimeoutMillis = 3000;
        SecureClient client(config);
        SecureFailure receivedFailure;
        QEventLoop loop;
        client.request(QStringLiteral("/cross/info"), QStringLiteral("a-b-c-d-e-host-7-1.0.0"), "{}",
            [&](const SecureResponse &, const SecureFailure &failure) {
                receivedFailure = failure; loop.quit();
            });
        QTimer::singleShot(5000, &loop, &QEventLoop::quit);
        loop.exec();
        QCOMPARE(receivedFailure.code, QStringLiteral("SC_HANDSHAKE_FAILED"));
        QCOMPARE(server.messageCount(), 0);
    }
};

QTEST_GUILESS_MAIN(SecureClientTest)
#include "tst_secureclient.moc"


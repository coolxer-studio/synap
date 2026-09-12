#include <synap/qt/AgentConfig.h>

#include "Crypto.h"

#include <QtCore/QRegularExpression>
#include <QtNetwork/QHostAddress>

namespace Synap::Qt {

QString AgentConfig::validationError() const {
    if (!baseUrl.isValid() || baseUrl.host().isEmpty() || !baseUrl.userInfo().isEmpty()
        || baseUrl.hasQuery() || baseUrl.hasFragment())
        return QStringLiteral("baseUrl must be an HTTP(S) origin without credentials, query, or fragment");
    const bool https = baseUrl.scheme().compare(QStringLiteral("https"), ::Qt::CaseInsensitive) == 0;
    const bool http = baseUrl.scheme().compare(QStringLiteral("http"), ::Qt::CaseInsensitive) == 0;
    QHostAddress address;
    const bool localhost = baseUrl.host().compare(QStringLiteral("localhost"), ::Qt::CaseInsensitive) == 0
        || (address.setAddress(baseUrl.host()) && address.isLoopback());
    if (!https && !(http && allowInsecureLocalhost && localhost))
        return QStringLiteral("HTTPS is required; HTTP is allowed only for explicit localhost development");
    if (businessAppId <= 0) return QStringLiteral("businessAppId must be a positive integer");
    static const QRegularExpression appIdPattern(QStringLiteral("^[A-Za-z0-9._:@/\\-]{1,128}$"));
    if (!appIdPattern.match(secureAppId).hasMatch()) return QStringLiteral("secureAppId is invalid");
    if (serverKeyId.isEmpty() || serverKeyId.size() > 128) return QStringLiteral("serverKeyId is invalid");
    bool decoded = false;
    const QByteArray anchor = Internal::base64UrlDecode(trustAnchorBase64Url.toLatin1(), &decoded);
    QString cryptoError;
    if (!decoded || !Internal::publicKeyFromSpki(anchor, &cryptoError))
        return QStringLiteral("trustAnchorBase64Url must be an unpadded P-256 SPKI Base64URL value");
    if (appName.isEmpty() || appPackage.isEmpty() || appVersion.isEmpty())
        return QStringLiteral("appName, appPackage, and appVersion are required");
    if (requestTimeoutMillis <= 0 || allowedClockSkewMillis < 0)
        return QStringLiteral("timeout configuration is invalid");
    if (maxQueueBytes <= 0 || maxQueueItems <= 0 || maxQueueAgeDays <= 0)
        return QStringLiteral("queue limits must be positive");
    return {};
}

} // namespace Synap::Qt


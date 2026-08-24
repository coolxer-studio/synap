#pragma once

#include <QtCore/QMetaType>
#include <QtCore/QString>
#include <QtCore/QUrl>

#include <functional>

#include "Export.h"

namespace Synap::Qt {

struct SYNAP_AGENT_QT_EXPORT AgentConfig {
    QUrl baseUrl;
    int businessAppId = 0;
    QString secureAppId;
    QString serverKeyId;
    QString trustAnchorBase64Url;

    QString appName;
    QString appPackage;
    QString appVersion;
    QString userId;
    QString dataDirectory;

    std::function<QString()> enrollmentTokenProvider;

    int requestTimeoutMillis = 15000;
    int allowedClockSkewMillis = 120000;
    qint64 maxQueueBytes = 10 * 1024 * 1024;
    int maxQueueItems = 1000;
    int maxQueueAgeDays = 7;
    bool allowInsecureLocalhost = false;

    QString validationError() const;
};

} // namespace Synap::Qt

Q_DECLARE_METATYPE(Synap::Qt::AgentConfig)


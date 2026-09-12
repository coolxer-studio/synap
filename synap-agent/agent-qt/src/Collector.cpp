#include "Collector.h"

#include "PlatformPermissions.h"

#include <QtCore/QCoreApplication>
#include <QtCore/QDateTime>
#include <QtCore/QDir>
#include <QtCore/QFile>
#include <QtCore/QFileInfo>
#include <QtCore/QJsonArray>
#include <QtCore/QLocale>
#include <QtCore/QRegularExpression>
#include <QtCore/QSaveFile>
#include <QtCore/QStorageInfo>
#include <QtCore/QSysInfo>
#include <QtCore/QThread>
#include <QtCore/QTimeZone>
#include <QtCore/QUuid>
#include <QtGui/QGuiApplication>
#include <QtGui/QScreen>
#include <QtNetwork/QHostAddress>
#include <QtNetwork/QNetworkInterface>
#include <QtNetwork/QNetworkProxy>
#include <QtNetwork/QNetworkProxyFactory>
#include <QtNetwork/QNetworkProxyQuery>

namespace Synap::Qt::Internal {
namespace {

QString buildDate() { return QDateTime::currentDateTime().toString(QStringLiteral("yyyy-MM-dd HH:mm:ss")); }

QJsonArray keys(const QJsonObject &config, const QString &name) {
    return config.value(name).toArray();
}

QString safeProcessName() {
    const QString path = QCoreApplication::applicationFilePath();
    return path.isEmpty() ? QStringLiteral("qt-application") : QFileInfo(path).fileName();
}

QJsonArray mapped(const QJsonArray &requested,
                  const std::function<QString(const QString &)> &value) {
    QJsonArray output;
    for (const QJsonValue &entry : requested) output.append(value(entry.toString()));
    return output;
}

} // namespace

Collector::Collector(AgentConfig config, QString dataDirectory)
    : config_(std::move(config)), dataDirectory_(std::move(dataDirectory)) {
    if (qobject_cast<QGuiApplication *>(QCoreApplication::instance())) {
        if (QScreen *screen = QGuiApplication::primaryScreen()) {
            screenWidth_ = screen->geometry().width();
            screenHeight_ = screen->geometry().height();
            devicePixelRatio_ = screen->devicePixelRatio();
        }
    }
}

bool Collector::initialize(QString *error) {
    if (!hardenDirectory(dataDirectory_, error)) return false;
    const QString path = QDir(dataDirectory_).filePath(QStringLiteral("guid"));
    QFile input(path);
    if (input.exists()) {
        if (!input.open(QIODevice::ReadOnly)) {
            if (error) *error = QStringLiteral("could not read probe guid");
            return false;
        }
        const QString stored = QString::fromLatin1(input.readAll()).trimmed();
        const QUuid parsed(stored);
        if (parsed.isNull() || stored != parsed.toString(QUuid::WithoutBraces)) {
            if (error) *error = QStringLiteral("stored probe guid is invalid");
            return false;
        }
        guid_ = stored;
        return hardenFile(path, error);
    }
    guid_ = QUuid::createUuid().toString(QUuid::WithoutBraces);
    QSaveFile output(path);
    const QByteArray encoded = guid_.toLatin1();
    if (!output.open(QIODevice::WriteOnly) || output.write(encoded) != encoded.size()
        || !output.commit() || !hardenFile(path, error)) {
        if (error && error->isEmpty()) *error = QStringLiteral("could not persist probe guid");
        return false;
    }
    return true;
}

QString Collector::code() const {
    return QStringLiteral("%1-host-%2-1.0.0").arg(guid_).arg(config_.businessAppId);
}

QJsonArray Collector::common(qint64 startId, const QString &userId) const {
    QJsonArray value;
    value.append(userId.isEmpty() ? config_.userId : userId); // 0 userId
    value.append(guid_);                                      // 1 guid
    value.append(QString::number(startId));                   // 2 startId
    value.append(QStringLiteral("1.0.0"));                  // 3 sdkVersion
    value.append(QString::number(config_.businessAppId));     // 4 appId
    value.append(config_.appName);                            // 5 appName
    value.append(config_.appPackage);                         // 6 appPackage
    value.append(config_.appVersion);                         // 7 appVersion
    value.append(QStringLiteral("host"));                   // 8 platform
    value.append(QString());                                  // 9 manufacturer
    value.append(QSysInfo::prettyProductName());              // 10 model
    value.append(QSysInfo::productType());                    // 11 system
    value.append(QSysInfo::productVersion());                 // 12 systemVersion
    value.append(primaryNetworkType());                       // 13 netType
    value.append(primaryAddress());                           // 14 lanIp
    value.append(QString());                                  // 15 wanIp (server derives it)
    value.append(QStringLiteral("0"));                       // 16 latitude
    value.append(QStringLiteral("0"));                       // 17 longitude
    for (int index = 18; index <= 22; ++index) value.append(QString());
    value.append(buildDate());                                // 23 clientTime
    return value;
}

QString Collector::primaryAddress() const {
    for (const QNetworkInterface &interface : QNetworkInterface::allInterfaces()) {
        if (!(interface.flags() & QNetworkInterface::IsUp)
            || (interface.flags() & QNetworkInterface::IsLoopBack)) continue;
        for (const QNetworkAddressEntry &entry : interface.addressEntries()) {
            if (entry.ip().protocol() == QAbstractSocket::IPv4Protocol
                && !entry.ip().isLoopback()) return entry.ip().toString();
        }
    }
    return {};
}

QString Collector::primaryNetworkType() const {
    for (const QNetworkInterface &interface : QNetworkInterface::allInterfaces()) {
        if (!(interface.flags() & QNetworkInterface::IsUp)
            || (interface.flags() & QNetworkInterface::IsLoopBack)) continue;
        const QString name = (interface.name() + QLatin1Char(' ') + interface.humanReadableName()).toLower();
        return name.contains(QStringLiteral("wifi")) || name.contains(QStringLiteral("wlan"))
            || name.contains(QStringLiteral("wireless")) ? QStringLiteral("wifi") : QStringLiteral("ethernet");
    }
    return QStringLiteral("unknown");
}

QList<QJsonObject> Collector::collect(const QJsonObject &serverConfig) const {
    QList<QJsonObject> facts;
    if (serverConfig.value(QStringLiteral("deviceConfig")).isObject())
        facts.append(deviceFact(serverConfig.value(QStringLiteral("deviceConfig")).toObject()));
    if (serverConfig.value(QStringLiteral("runtimeConfig")).isObject())
        facts.append(runtimeFact(serverConfig.value(QStringLiteral("runtimeConfig")).toObject()));
    if (serverConfig.value(QStringLiteral("netConfig")).isObject())
        facts.append(networkFact(serverConfig.value(QStringLiteral("netConfig")).toObject()));
    return facts;
}

QJsonObject Collector::deviceFact(const QJsonObject &config) const {
    const QStorageInfo storage = QStorageInfo::root();
    QJsonObject fact{
        {QStringLiteral("type"), QStringLiteral("DeviceData")},
        {QStringLiteral("buildDate"), buildDate()}
    };
    fact.insert(QStringLiteral("device"), mapped(keys(config, QStringLiteral("device")),
        [&](const QString &key) {
            if (key == QStringLiteral("type")) return QStringLiteral("host");
            if (key == QStringLiteral("name")) return QSysInfo::machineHostName();
            if (key == QStringLiteral("systemName")) return QSysInfo::productType();
            if (key == QStringLiteral("systemVersion")) return QSysInfo::productVersion();
            if (key == QStringLiteral("buildId")) return QSysInfo::kernelVersion();
            if (key == QStringLiteral("modelCode")) return QSysInfo::prettyProductName();
            if (key == QStringLiteral("language")) return QLocale::system().name();
            if (key == QStringLiteral("timeZone")) return QString::fromUtf8(QTimeZone::systemTimeZoneId());
            return QString();
        }));
    fact.insert(QStringLiteral("screen"), mapped(keys(config, QStringLiteral("screen")),
        [&](const QString &key) {
            if (key == QStringLiteral("width")) return screenWidth_ > 0 ? QString::number(screenWidth_) : QString();
            if (key == QStringLiteral("height")) return screenHeight_ > 0 ? QString::number(screenHeight_) : QString();
            if (key == QStringLiteral("density")) return devicePixelRatio_ > 0 ? QString::number(devicePixelRatio_) : QString();
            if (key == QStringLiteral("rotation")) return QStringLiteral("0");
            return QString();
        }));
    fact.insert(QStringLiteral("cpu"), mapped(keys(config, QStringLiteral("cpu")),
        [](const QString &key) {
            if (key == QStringLiteral("totalCore")) return QString::number(qMax(1, QThread::idealThreadCount()));
            if (key == QStringLiteral("type")) return QSysInfo::buildCpuArchitecture();
            if (key == QStringLiteral("abi")) return QSysInfo::currentCpuArchitecture();
            return QString();
        }));
    fact.insert(QStringLiteral("battery"), mapped(keys(config, QStringLiteral("battery")), [](const QString &) { return QString(); }));
    fact.insert(QStringLiteral("uuid"), mapped(keys(config, QStringLiteral("uuid")),
        [&](const QString &key) { return key == QStringLiteral("guid") ? guid_ : QString(); }));
    fact.insert(QStringLiteral("network"), mapped(keys(config, QStringLiteral("network")),
        [&](const QString &key) {
            if (key == QStringLiteral("lanIp")) return primaryAddress();
            if (key == QStringLiteral("type")) return primaryNetworkType();
            return QString();
        }));
    fact.insert(QStringLiteral("storage"), mapped(keys(config, QStringLiteral("storage")),
        [&](const QString &key) {
            if (!storage.isValid() || !storage.isReady()) return QString();
            if (key == QStringLiteral("totalDisk")) return QString::number(storage.bytesTotal());
            if (key == QStringLiteral("usedDisk")) return QString::number(storage.bytesTotal() - storage.bytesAvailable());
            return QString();
        }));
    return fact;
}

QJsonObject Collector::runtimeFact(const QJsonObject &config) const {
    const QString process = safeProcessName();
    return {
        {QStringLiteral("type"), QStringLiteral("RuntimeData")},
        {QStringLiteral("buildDate"), buildDate()},
        {QStringLiteral("process"), mapped(keys(config, QStringLiteral("process")),
            [&](const QString &pattern) {
                const QRegularExpression expression(pattern);
                return expression.isValid() && expression.match(process).hasMatch() ? process : QString();
            })},
        {QStringLiteral("env"), mapped(keys(config, QStringLiteral("env")), [](const QString &) { return QString(); })},
        {QStringLiteral("cmd"), mapped(keys(config, QStringLiteral("cmd")), [](const QString &) { return QString(); })}
    };
}

QJsonObject Collector::networkFact(const QJsonObject &config) const {
    QString interfaceSummary;
    for (const QNetworkInterface &interface : QNetworkInterface::allInterfaces()) {
        if (!(interface.flags() & QNetworkInterface::IsUp)
            || (interface.flags() & QNetworkInterface::IsLoopBack)) continue;
        QStringList addresses;
        for (const QNetworkAddressEntry &entry : interface.addressEntries()) {
            if (!entry.ip().isLoopback()) addresses.append(entry.ip().toString());
        }
        interfaceSummary = interface.name() + QStringLiteral(":") + addresses.join(QLatin1Char(','));
        break;
    }
    QString proxySummary;
    const QList<QNetworkProxy> proxies = QNetworkProxyFactory::systemProxyForQuery(QNetworkProxyQuery());
    for (const QNetworkProxy &proxy : proxies) {
        if (proxy.type() != QNetworkProxy::NoProxy && !proxy.hostName().isEmpty()) {
            proxySummary = proxy.hostName() + QLatin1Char(':') + QString::number(proxy.port());
            break;
        }
    }
    const auto zeros = [](const QJsonArray &values) {
        QJsonArray output; for (int index = 0; index < values.size(); ++index) output.append(0); return output;
    };
    return {
        {QStringLiteral("type"), QStringLiteral("NetData")},
        {QStringLiteral("buildDate"), buildDate()},
        {QStringLiteral("port"), zeros(keys(config, QStringLiteral("port")))},
        {QStringLiteral("socket"), zeros(keys(config, QStringLiteral("socket")))},
        {QStringLiteral("uds"), zeros(keys(config, QStringLiteral("uds")))},
        {QStringLiteral("netInterface"), mapped(keys(config, QStringLiteral("netInterface")),
            [&](const QString &) { return interfaceSummary; })},
        {QStringLiteral("httpProxy"), mapped(keys(config, QStringLiteral("httpProxy")),
            [&](const QString &) { return proxySummary; })},
        {QStringLiteral("hosts"), mapped(keys(config, QStringLiteral("hosts")), [](const QString &) { return QString(); })},
        {QStringLiteral("certificate"), mapped(keys(config, QStringLiteral("certificate")), [](const QString &) { return QString(); })}
    };
}

QJsonObject Collector::userData(const QString &userId, const QJsonObject &data) const {
    return {{QStringLiteral("type"), QStringLiteral("UserData")},
            {QStringLiteral("buildDate"), buildDate()},
            {QStringLiteral("userId"), userId}, {QStringLiteral("data"), data}};
}

} // namespace Synap::Qt::Internal

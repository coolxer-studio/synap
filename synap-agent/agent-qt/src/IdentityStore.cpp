#include "IdentityStore.h"

#include "PlatformPermissions.h"

#include <QtCore/QDir>
#include <QtCore/QFile>
#include <QtCore/QFileInfo>
#include <QtCore/QJsonDocument>
#include <QtCore/QJsonObject>
#include <QtCore/QSaveFile>
#include <QtCore/QUuid>

namespace Synap::Qt::Internal {

IdentityStore::IdentityStore(QString dataDirectory)
    : dataDirectory_(std::move(dataDirectory)) {}

QString IdentityStore::identityPath() const {
    return QDir(dataDirectory_).filePath(QStringLiteral("secure-communication/identity-v2.json"));
}

InstallationIdentity IdentityStore::loadOrCreate(const QString &appId, QString *error) const {
    InstallationIdentity identity;
    const QString path = identityPath();
    const QString directory = QFileInfo(path).absolutePath();
    if (!hardenDirectory(directory, error)) return identity;

    QFile existing(path);
    if (existing.exists()) {
        if (!existing.open(QIODevice::ReadOnly)) {
            if (error) *error = QStringLiteral("could not read installation identity");
            return identity;
        }
        QJsonParseError parseError;
        const QJsonDocument document = QJsonDocument::fromJson(existing.readAll(), &parseError);
        const QJsonObject object = document.object();
        bool decoded = false;
        const QByteArray privateDer = base64UrlDecode(object.value(QStringLiteral("privateKey")).toString().toLatin1(), &decoded);
        if (parseError.error != QJsonParseError::NoError || !document.isObject()
            || object.value(QStringLiteral("version")).toInt() != 2
            || object.value(QStringLiteral("appId")).toString() != appId
            || object.value(QStringLiteral("deviceId")).toString().isEmpty() || !decoded) {
            if (error) *error = QStringLiteral("stored installation identity is invalid or belongs to another appId");
            return identity;
        }
        identity.key = privateKeyFromDer(privateDer, error);
        if (!identity.key) return identity;
        identity.deviceId = object.value(QStringLiteral("deviceId")).toString();
        if (!hardenFile(path, error)) return {};
        return identity;
    }

    identity.key = generateP256(error);
    if (!identity.key) return identity;
    identity.deviceId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    const QByteArray privateDer = privateKeyDer(identity.key.get(), error);
    if (privateDer.isEmpty()) return {};
    QJsonObject object{
        {QStringLiteral("version"), 2},
        {QStringLiteral("appId"), appId},
        {QStringLiteral("deviceId"), identity.deviceId},
        {QStringLiteral("privateKey"), QString::fromLatin1(base64UrlEncode(privateDer))}
    };
    QSaveFile output(path);
    if (!output.open(QIODevice::WriteOnly)
        || output.write(QJsonDocument(object).toJson(QJsonDocument::Compact)) < 0
        || !output.commit() || !hardenFile(path, error)) {
        if (error && error->isEmpty()) *error = QStringLiteral("could not persist installation identity");
        return {};
    }
    return identity;
}

} // namespace Synap::Qt::Internal

#pragma once

#include "Crypto.h"

#include <QtCore/QString>

namespace Synap::Qt::Internal {

struct InstallationIdentity {
    QString deviceId;
    PKey key;

    InstallationIdentity() = default;
    InstallationIdentity(InstallationIdentity &&) noexcept = default;
    InstallationIdentity &operator=(InstallationIdentity &&) noexcept = default;
    InstallationIdentity(const InstallationIdentity &) = delete;
    InstallationIdentity &operator=(const InstallationIdentity &) = delete;
};

class IdentityStore {
public:
    explicit IdentityStore(QString dataDirectory);
    InstallationIdentity loadOrCreate(const QString &appId, QString *error = nullptr) const;
    QString identityPath() const;

private:
    QString dataDirectory_;
};

} // namespace Synap::Qt::Internal


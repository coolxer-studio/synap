#include "PlatformPermissions.h"

#include <QtCore/QDir>
#include <QtCore/QFile>

#ifdef Q_OS_WIN
#  define NOMINMAX
#  include <windows.h>
#  include <aclapi.h>
#endif

namespace Synap::Qt::Internal {

#ifdef Q_OS_WIN
namespace {
bool restrictToCurrentUser(const QString &path, DWORD inheritance, QString *error) {
    HANDLE token = nullptr;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) {
        if (error) *error = QStringLiteral("OpenProcessToken failed: %1").arg(GetLastError());
        return false;
    }
    DWORD size = 0;
    GetTokenInformation(token, TokenUser, nullptr, 0, &size);
    QByteArray storage(int(size), ::Qt::Uninitialized);
    if (!GetTokenInformation(token, TokenUser, storage.data(), size, &size)) {
        if (error) *error = QStringLiteral("GetTokenInformation failed: %1").arg(GetLastError());
        CloseHandle(token);
        return false;
    }
    CloseHandle(token);
    auto *user = reinterpret_cast<TOKEN_USER *>(storage.data());
    EXPLICIT_ACCESSW access = {};
    access.grfAccessPermissions = GENERIC_ALL;
    access.grfAccessMode = SET_ACCESS;
    access.grfInheritance = inheritance;
    access.Trustee.TrusteeForm = TRUSTEE_IS_SID;
    access.Trustee.TrusteeType = TRUSTEE_IS_USER;
    access.Trustee.ptstrName = static_cast<LPWSTR>(user->User.Sid);
    PACL acl = nullptr;
    DWORD status = SetEntriesInAclW(1, &access, nullptr, &acl);
    if (status == ERROR_SUCCESS) {
        const std::wstring native = QDir::toNativeSeparators(path).toStdWString();
        status = SetNamedSecurityInfoW(const_cast<LPWSTR>(native.c_str()), SE_FILE_OBJECT,
            DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
            nullptr, nullptr, acl, nullptr);
    }
    if (acl) LocalFree(acl);
    if (status != ERROR_SUCCESS) {
        if (error) *error = QStringLiteral("setting current-user DACL failed: %1").arg(status);
        return false;
    }
    return true;
}
} // namespace
#endif

bool hardenDirectory(const QString &path, QString *error) {
    QDir directory;
    if (!directory.mkpath(path)) {
        if (error) *error = QStringLiteral("could not create protected directory");
        return false;
    }
#ifdef Q_OS_WIN
    return restrictToCurrentUser(path, SUB_CONTAINERS_AND_OBJECTS_INHERIT, error);
#else
    if (!QFile::setPermissions(path, QFileDevice::ReadOwner | QFileDevice::WriteOwner
            | QFileDevice::ExeOwner)) {
        if (error) *error = QStringLiteral("could not set directory permissions to 0700");
        return false;
    }
    return true;
#endif
}

bool hardenFile(const QString &path, QString *error) {
#ifdef Q_OS_WIN
    return restrictToCurrentUser(path, NO_INHERITANCE, error);
#else
    if (!QFile::setPermissions(path, QFileDevice::ReadOwner | QFileDevice::WriteOwner)) {
        if (error) *error = QStringLiteral("could not set file permissions to 0600");
        return false;
    }
    return true;
#endif
}

} // namespace Synap::Qt::Internal

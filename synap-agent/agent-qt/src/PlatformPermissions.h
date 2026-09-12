#pragma once

#include <QtCore/QString>

namespace Synap::Qt::Internal {

bool hardenDirectory(const QString &path, QString *error = nullptr);
bool hardenFile(const QString &path, QString *error = nullptr);

} // namespace Synap::Qt::Internal


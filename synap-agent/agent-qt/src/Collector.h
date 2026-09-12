#pragma once

#include <synap/qt/AgentConfig.h>

#include <QtCore/QJsonArray>
#include <QtCore/QJsonObject>
#include <QtCore/QString>

#include <functional>

namespace Synap::Qt::Internal {

class Collector {
public:
    Collector(AgentConfig config, QString dataDirectory);

    bool initialize(QString *error = nullptr);
    QJsonArray common(qint64 startId, const QString &userId = {}) const;
    QString code() const;
    QList<QJsonObject> collect(const QJsonObject &serverConfig) const;
    QJsonObject userData(const QString &userId, const QJsonObject &data) const;

private:
    QString primaryAddress() const;
    QString primaryNetworkType() const;
    QJsonObject deviceFact(const QJsonObject &config) const;
    QJsonObject runtimeFact(const QJsonObject &config) const;
    QJsonObject networkFact(const QJsonObject &config) const;

    AgentConfig config_;
    QString dataDirectory_;
    QString guid_;
    int screenWidth_ = 0;
    int screenHeight_ = 0;
    qreal devicePixelRatio_ = 0;
};

} // namespace Synap::Qt::Internal

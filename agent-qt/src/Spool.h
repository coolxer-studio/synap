#pragma once

#include <synap/qt/AgentConfig.h>

#include <QtCore/QJsonArray>
#include <QtCore/QJsonObject>
#include <QtCore/QString>

namespace Synap::Qt::Internal {

struct QueueRecord {
    QString path;
    QString code;
    QJsonArray common;
    QJsonObject fact;
    QJsonObject meta;
};

class Spool {
public:
    Spool(QString dataDirectory, const AgentConfig &config);

    bool initialize(QString *error = nullptr);
    bool enqueue(const QString &code, const QJsonArray &common, QJsonObject fact,
                 const QString &appKeyId, QString *error = nullptr);
    bool next(QueueRecord *record, QString *error = nullptr);
    bool prepareAttempt(QueueRecord *record, QString *error = nullptr);
    bool acknowledge(const QueueRecord &record, QString *error = nullptr);
    bool reject(const QueueRecord &record, QString *error = nullptr);
    int depth() const;

private:
    bool save(const QueueRecord &record, QString *error) const;
    bool prune(QString *error);
    QStringList files(const QString &directory) const;

    QString root_;
    QString queueDirectory_;
    QString deadDirectory_;
    qint64 maxBytes_;
    int maxItems_;
    int maxAgeDays_;
};

} // namespace Synap::Qt::Internal


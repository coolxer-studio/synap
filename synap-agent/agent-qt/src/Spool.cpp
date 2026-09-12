#include "Spool.h"

#include "PlatformPermissions.h"

#include <QtCore/QDateTime>
#include <QtCore/QDir>
#include <QtCore/QFile>
#include <QtCore/QFileInfo>
#include <QtCore/QJsonDocument>
#include <QtCore/QSaveFile>
#include <QtCore/QUuid>

namespace Synap::Qt::Internal {
namespace {

constexpr qint64 kMaxEnvelopeBytes = 256 * 1024;

QJsonObject encode(const QueueRecord &record) {
    return {
        {QStringLiteral("code"), record.code},
        {QStringLiteral("common"), record.common},
        {QStringLiteral("fact"), record.fact},
        {QStringLiteral("meta"), record.meta}
    };
}

bool decode(const QString &path, const QByteArray &data, QueueRecord *record) {
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(data, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) return false;
    const QJsonObject object = document.object();
    if (!object.value(QStringLiteral("common")).isArray()
        || !object.value(QStringLiteral("fact")).isObject()
        || !object.value(QStringLiteral("meta")).isObject()
        || object.value(QStringLiteral("code")).toString().isEmpty()) return false;
    record->path = path;
    record->code = object.value(QStringLiteral("code")).toString();
    record->common = object.value(QStringLiteral("common")).toArray();
    record->fact = object.value(QStringLiteral("fact")).toObject();
    record->meta = object.value(QStringLiteral("meta")).toObject();
    return true;
}

} // namespace

Spool::Spool(QString dataDirectory, const AgentConfig &config)
    : root_(QDir(std::move(dataDirectory)).filePath(QStringLiteral("spool-v1"))),
      queueDirectory_(QDir(root_).filePath(QStringLiteral("queue"))),
      deadDirectory_(QDir(root_).filePath(QStringLiteral("dead-letter"))),
      maxBytes_(config.maxQueueBytes), maxItems_(config.maxQueueItems),
      maxAgeDays_(config.maxQueueAgeDays) {}

bool Spool::initialize(QString *error) {
    return hardenDirectory(root_, error) && hardenDirectory(queueDirectory_, error)
        && hardenDirectory(deadDirectory_, error) && prune(error);
}

bool Spool::enqueue(const QString &code, const QJsonArray &common, QJsonObject fact,
                    const QString &appKeyId, QString *error) {
    if (common.size() != 24 || code.isEmpty()) {
        if (error) *error = QStringLiteral("queue record identity is invalid");
        return false;
    }
    if (fact.value(QStringLiteral("messageId")).toString().isEmpty()
        || fact.value(QStringLiteral("sequence")).toVariant().toLongLong() < 2
        || fact.value(QStringLiteral("observedAt")).toVariant().toLongLong() <= 0) {
        if (error) *error = QStringLiteral("tracked fact metadata is invalid");
        return false;
    }
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    QueueRecord record;
    record.path = QDir(queueDirectory_).filePath(
        QStringLiteral("%1-%2.json").arg(now, 20, 10, QLatin1Char('0')).arg(
            QUuid::createUuid().toString(QUuid::WithoutBraces)));
    record.code = code;
    record.common = common;
    record.fact = std::move(fact);
    record.meta = {
        {QStringLiteral("protocolVersion"), 1},
        {QStringLiteral("appKeyId"), appKeyId},
        {QStringLiteral("detectorVersion"), QStringLiteral("1.0.0")},
        {QStringLiteral("batchId"), QStringLiteral("batch-") + QUuid::createUuid().toString(QUuid::WithoutBraces)},
        {QStringLiteral("sessionId"), common.at(2).toString()},
        {QStringLiteral("attempt"), 0},
        {QStringLiteral("createdAt"), QJsonValue(double(now))}
    };
    const QByteArray encoded = QJsonDocument(encode(record)).toJson(QJsonDocument::Compact);
    if (encoded.size() > kMaxEnvelopeBytes) {
        if (error) *error = QStringLiteral("queue record exceeds 256 KiB");
        return false;
    }
    if (!save(record, error)) return false;
    return prune(error);
}

bool Spool::next(QueueRecord *record, QString *error) {
    if (!record) return false;
    const QStringList names = files(queueDirectory_);
    if (names.isEmpty()) { *record = {}; return true; }
    const QString path = QDir(queueDirectory_).filePath(names.first());
    QFile input(path);
    if (!input.open(QIODevice::ReadOnly)) {
        if (error) *error = QStringLiteral("could not read queued record");
        return false;
    }
    if (!decode(path, input.readAll(), record)) {
        const QString target = QDir(deadDirectory_).filePath(QFileInfo(path).fileName());
        QFile::rename(path, target);
        hardenFile(target, nullptr);
        if (error) *error = QStringLiteral("invalid queued record moved to dead-letter");
        return false;
    }
    return true;
}

bool Spool::prepareAttempt(QueueRecord *record, QString *error) {
    if (!record || record->path.isEmpty()) return false;
    record->meta.insert(QStringLiteral("attempt"),
                        record->meta.value(QStringLiteral("attempt")).toInt() + 1);
    return save(*record, error);
}

bool Spool::acknowledge(const QueueRecord &record, QString *error) {
    if (QFileInfo(record.path).absolutePath() != queueDirectory_) {
        if (error) *error = QStringLiteral("queue record path is invalid");
        return false;
    }
    if (!QFile::remove(record.path)) {
        if (error) *error = QStringLiteral("could not acknowledge queued record");
        return false;
    }
    return true;
}

bool Spool::reject(const QueueRecord &record, QString *error) {
    if (QFileInfo(record.path).absolutePath() != queueDirectory_) {
        if (error) *error = QStringLiteral("queue record path is invalid");
        return false;
    }
    const QString target = QDir(deadDirectory_).filePath(QFileInfo(record.path).fileName());
    if (!QFile::rename(record.path, target) || !hardenFile(target, error)) {
        if (error && error->isEmpty()) *error = QStringLiteral("could not move record to dead-letter");
        return false;
    }
    return prune(error);
}

int Spool::depth() const { return files(queueDirectory_).size(); }

bool Spool::save(const QueueRecord &record, QString *error) const {
    QSaveFile output(record.path);
    const QByteArray data = QJsonDocument(encode(record)).toJson(QJsonDocument::Compact);
    if (!output.open(QIODevice::WriteOnly) || output.write(data) != data.size() || !output.commit()) {
        if (error) *error = QStringLiteral("could not atomically persist queued record");
        return false;
    }
    return hardenFile(record.path, error);
}

QStringList Spool::files(const QString &directory) const {
    return QDir(directory).entryList({QStringLiteral("*.json")}, QDir::Files, QDir::Name);
}

bool Spool::prune(QString *error) {
    for (const QString &directory : {queueDirectory_, deadDirectory_}) {
        QStringList names = files(directory);
        qint64 total = 0;
        for (const QString &name : names) total += QFileInfo(QDir(directory).filePath(name)).size();
        const QDateTime cutoff = QDateTime::currentDateTimeUtc().addDays(-maxAgeDays_);
        while (!names.isEmpty()) {
            const QFileInfo oldest(QDir(directory).filePath(names.first()));
            if (names.size() <= maxItems_ && total <= maxBytes_ && oldest.lastModified().toUTC() >= cutoff) break;
            total -= oldest.size();
            if (!QFile::remove(oldest.absoluteFilePath())) {
                if (error) *error = QStringLiteral("could not prune bounded queue");
                return false;
            }
            names.removeFirst();
        }
    }
    return true;
}

} // namespace Synap::Qt::Internal

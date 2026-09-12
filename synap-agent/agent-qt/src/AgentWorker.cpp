#include "AgentWorker.h"

#include <QtCore/QDateTime>
#include <QtCore/QDir>
#include <QtCore/QJsonArray>
#include <QtCore/QJsonDocument>
#include <QtCore/QRandomGenerator>
#include <QtCore/QStandardPaths>
#include <QtCore/QUuid>

#include <cmath>

namespace Synap::Qt {
namespace {

constexpr int kMaxDurationMs = 24 * 60 * 60 * 1000;

QJsonObject objectFromResponse(const Internal::SecureResponse &response, QString *error) {
    QJsonParseError parseError;
    const QJsonDocument document = QJsonDocument::fromJson(response.body, &parseError);
    if (parseError.error != QJsonParseError::NoError || !document.isObject()) {
        if (error) *error = QStringLiteral("server response is not a JSON object");
        return {};
    }
    return document.object();
}

} // namespace

AgentWorker::AgentWorker(AgentConfig config) : config_(std::move(config)) {
    dataDirectory_ = config_.dataDirectory;
    if (dataDirectory_.isEmpty()) {
        dataDirectory_ = QDir(QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation))
            .filePath(QStringLiteral("synap-agent-qt/%1").arg(config_.businessAppId));
        config_.dataDirectory = dataDirectory_;
    }
    collector_ = std::make_unique<Internal::Collector>(config_, dataDirectory_);
    spool_ = std::make_unique<Internal::Spool>(dataDirectory_, config_);
    collectTimer_ = new QTimer(this);
    uploadTimer_ = new QTimer(this);
    stopDeadline_ = new QTimer(this);
    connect(collectTimer_, &QTimer::timeout, this, &AgentWorker::collectOnce);
    connect(uploadTimer_, &QTimer::timeout, this, &AgentWorker::uploadOnce);
    stopDeadline_->setSingleShot(true);
    connect(stopDeadline_, &QTimer::timeout, this, &AgentWorker::finishStop);
}

void AgentWorker::start() {
    if (running_ || stopping_) return;
    if (!secure_) secure_ = std::make_unique<Internal::SecureClient>(config_);
    emit stateChanged(Agent::Starting);
    QString error;
    if (!collector_->initialize(&error) || !spool_->initialize(&error)) {
        emit errorOccurred(QStringLiteral("AGENT_STORAGE_FAILED"), error, false);
        emit stateChanged(Agent::Error);
        return;
    }
    emitQueueDepth();
    startId_ = QDateTime::currentMSecsSinceEpoch();
    common_ = collector_->common(startId_);
    startMeta_ = {};
    const QJsonObject body{{QStringLiteral("common"), common_},
                           {QStringLiteral("meta"), startMeta()}};
    secure_->request(QStringLiteral("/attach/start"), collector_->code(),
        QJsonDocument(body).toJson(QJsonDocument::Compact),
        [this](const Internal::SecureResponse &response, const Internal::SecureFailure &failure) {
            if (!failure.code.isEmpty()) {
                emitFailure(failure); emit stateChanged(Agent::Error); return;
            }
            QString error;
            const QJsonObject object = objectFromResponse(response, &error);
            if (response.status < 200 || response.status >= 300
                || object.value(QStringLiteral("status")).toInt() != 1
                || !object.value(QStringLiteral("config")).isObject()) {
                emit errorOccurred(QStringLiteral("AGENT_START_REJECTED"),
                    error.isEmpty() ? QStringLiteral("server rejected probe start") : error,
                    response.status == 429 || response.status >= 500);
                emit stateChanged(Agent::Error);
                return;
            }
            serverConfig_ = object.value(QStringLiteral("config")).toObject();
            if (!parseSchedule(serverConfig_.value(QStringLiteral("startConfig")).toArray(), &error)) {
                emit errorOccurred(QStringLiteral("AGENT_CONFIG_INVALID"), error, false);
                emit stateChanged(Agent::Error);
                return;
            }
            running_ = true;
            retryDelayMs_ = 1000;
            collectTimer_->start(collectIntervalMs_);
            uploadTimer_->start(uploadIntervalMs_);
            QTimer::singleShot(collectWindowMs_, this, [this] { collectTimer_->stop(); });
            QTimer::singleShot(uploadWindowMs_, this, [this] { uploadTimer_->stop(); });
            collectOnce();
            uploadOnce();
            emit stateChanged(Agent::Running);
            emit started();
        });
}

QJsonObject AgentWorker::startMeta() {
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (startMeta_.isEmpty()) {
        startMeta_ = {
            {QStringLiteral("protocolVersion"), 1},
            {QStringLiteral("appKeyId"), QString::number(config_.businessAppId)},
            {QStringLiteral("detectorVersion"), QStringLiteral("1.0.0")},
            {QStringLiteral("messageId"), QStringLiteral("start-msg-") + QUuid::createUuid().toString(QUuid::WithoutBraces)},
            {QStringLiteral("batchId"), QStringLiteral("start-batch-") + QUuid::createUuid().toString(QUuid::WithoutBraces)},
            {QStringLiteral("sessionId"), QString::number(startId_)},
            {QStringLiteral("attempt"), 0}, {QStringLiteral("createdAt"), QJsonValue(double(now))},
            {QStringLiteral("observedAt"), QJsonValue(double(now))}, {QStringLiteral("sequence"), 1}
        };
    }
    startMeta_.insert(QStringLiteral("attempt"), startMeta_.value(QStringLiteral("attempt")).toInt() + 1);
    return startMeta_;
}

bool AgentWorker::parseSchedule(const QJsonArray &values, QString *error) {
    if (values.size() != 5 || values.at(0).toDouble() != 1.0) {
        if (error) *error = QStringLiteral("startConfig must enable collection and contain 5 values");
        return false;
    }
    int *outputs[] = {&collectIntervalMs_, &collectWindowMs_, &uploadIntervalMs_, &uploadWindowMs_};
    for (int index = 1; index < 5; ++index) {
        const double seconds = values.at(index).toDouble(-1);
        if (!std::isfinite(seconds) || seconds <= 0 || seconds * 1000 > kMaxDurationMs) {
            if (error) *error = QStringLiteral("startConfig intervals must be between 1 ms and 24 hours");
            return false;
        }
        *outputs[index - 1] = qMax(1, int(seconds * 1000));
    }
    return true;
}

void AgentWorker::collectOnce() {
    if (!running_ || stopping_) return;
    const QList<QJsonObject> facts = collector_->collect(serverConfig_);
    for (QJsonObject fact : facts) enqueue(std::move(fact));
}

void AgentWorker::enqueue(QJsonObject fact, const QString &userId) {
    fact.insert(QStringLiteral("messageId"), QStringLiteral("msg-") + QUuid::createUuid().toString(QUuid::WithoutBraces));
    fact.insert(QStringLiteral("observedAt"), QJsonValue(double(QDateTime::currentMSecsSinceEpoch())));
    fact.insert(QStringLiteral("sequence"), QJsonValue(double(nextSequence_++)));
    QString error;
    const QJsonArray recordCommon = userId.isEmpty() ? common_ : collector_->common(startId_, userId);
    if (!spool_->enqueue(collector_->code(), recordCommon, std::move(fact),
                         QString::number(config_.businessAppId), &error))
        emit errorOccurred(QStringLiteral("AGENT_QUEUE_FAILED"), error, false);
    emitQueueDepth();
}

void AgentWorker::submitUserData(const QString &userId, const QVariantMap &data) {
    if (!running_ || stopping_) {
        emit errorOccurred(QStringLiteral("AGENT_NOT_RUNNING"), QStringLiteral("probe is not running"), false);
        return;
    }
    enqueue(collector_->userData(userId, QJsonObject::fromVariantMap(data)), userId);
    if (!uploadTimer_->isActive()) uploadOnce();
}

void AgentWorker::uploadOnce() {
    if ((!running_ && !stopping_) || uploading_) return;
    Internal::QueueRecord record;
    QString error;
    if (!spool_->next(&record, &error)) {
        emit errorOccurred(QStringLiteral("AGENT_QUEUE_FAILED"), error, true);
        scheduleUploadRetry();
        return;
    }
    if (record.path.isEmpty()) {
        if (stopping_) finishStop();
        return;
    }
    if (!spool_->prepareAttempt(&record, &error)) {
        emit errorOccurred(QStringLiteral("AGENT_QUEUE_FAILED"), error, true);
        scheduleUploadRetry();
        return;
    }
    const QJsonObject envelope{{QStringLiteral("common"), record.common},
        {QStringLiteral("meta"), record.meta},
        {QStringLiteral("data"), QJsonArray{record.fact}}};
    uploading_ = true;
    secure_->request(QStringLiteral("/attach/upload"), record.code,
        QJsonDocument(envelope).toJson(QJsonDocument::Compact),
        [this, record](const Internal::SecureResponse &response, const Internal::SecureFailure &failure) {
            uploading_ = false;
            handleUpload(record, response, failure);
        });
}

void AgentWorker::handleUpload(const Internal::QueueRecord &record,
                               const Internal::SecureResponse &response,
                               const Internal::SecureFailure &failure) {
    if (!failure.code.isEmpty()) {
        emitFailure(failure);
        if (failure.retryable) scheduleUploadRetry();
        else {
            QString error; spool_->reject(record, &error); emitQueueDepth();
            if (stopping_) uploadOnce();
        }
        return;
    }
    QString error;
    const QJsonObject object = objectFromResponse(response, &error);
    const QJsonObject ack = object.value(QStringLiteral("ack")).toObject();
    const QString messageId = record.fact.value(QStringLiteral("messageId")).toString();
    const QString batchId = record.meta.value(QStringLiteral("batchId")).toString();
    bool completed = false, retryable = false, permanent = false;
    if (response.status >= 200 && response.status < 300
        && object.value(QStringLiteral("status")).toInt() == 1
        && ack.value(QStringLiteral("batchId")).toString() == batchId) {
        for (const QString &name : {QStringLiteral("acceptedMessageIds"), QStringLiteral("duplicateMessageIds")}) {
            for (const QJsonValue &value : ack.value(name).toArray()) if (value.toString() == messageId) completed = true;
        }
        for (const QJsonValue &value : ack.value(QStringLiteral("rejectedItems")).toArray()) {
            const QJsonObject rejected = value.toObject();
            if (rejected.value(QStringLiteral("messageId")).toString() == messageId) {
                retryable = rejected.value(QStringLiteral("retryable")).toBool();
                permanent = !retryable;
            }
        }
    }
    if ((completed ? 1 : 0) + (retryable ? 1 : 0) + (permanent ? 1 : 0) != 1) {
        emit errorOccurred(QStringLiteral("AGENT_ACK_INVALID"),
                           error.isEmpty() ? QStringLiteral("missing or ambiguous upload ACK") : error, true);
        scheduleUploadRetry();
        return;
    }
    if (completed) spool_->acknowledge(record, &error);
    else if (permanent) spool_->reject(record, &error);
    if (!error.isEmpty()) emit errorOccurred(QStringLiteral("AGENT_QUEUE_FAILED"), error, true);
    for (const QJsonValue &value : object.value(QStringLiteral("punishes")).toArray()) {
        const QJsonArray reminder = value.toArray();
        if (reminder.size() >= 3 && reminder.at(0).toInt() == 1)
            emit reminderReceived(reminder.at(1).toString(), reminder.at(2).toString());
    }
    emitQueueDepth();
    retryDelayMs_ = 1000;
    if (retryable) scheduleUploadRetry();
    else if (stopping_ || spool_->depth() > 0) QTimer::singleShot(0, this, &AgentWorker::uploadOnce);
}

void AgentWorker::scheduleUploadRetry() {
    uploadTimer_->stop();
    const int jitter = QRandomGenerator::global()->bounded(qMax(1, retryDelayMs_ / 2));
    const int wait = retryDelayMs_ + jitter;
    retryDelayMs_ = qMin(5 * 60 * 1000, retryDelayMs_ * 2);
    QTimer::singleShot(wait, this, [this] {
        if (!stopping_ && running_) uploadTimer_->start(uploadIntervalMs_);
        uploadOnce();
    });
}

void AgentWorker::query(Agent::QueryKind kind, const QString &requestId) {
    if (!running_ || stopping_) {
        emit errorOccurred(QStringLiteral("AGENT_NOT_RUNNING"), QStringLiteral("probe is not running"), false);
        emit queryFinished(kind, requestId, {});
        return;
    }
    QString path;
    switch (kind) {
    case Agent::DeviceInfo: path = QStringLiteral("/cross/info"); break;
    case Agent::Rating: path = QStringLiteral("/cross/rating"); break;
    case Agent::Labels: path = QStringLiteral("/cross/label_start"); break;
    }
    const QJsonObject body{{QStringLiteral("common"), common_}};
    secure_->request(path, collector_->code(), QJsonDocument(body).toJson(QJsonDocument::Compact),
        [this, kind, requestId](const Internal::SecureResponse &response,
                                const Internal::SecureFailure &failure) {
            if (!failure.code.isEmpty()) { emitFailure(failure); emit queryFinished(kind, requestId, {}); return; }
            QString error;
            const QJsonObject object = objectFromResponse(response, &error);
            if (!error.isEmpty()) emit errorOccurred(QStringLiteral("AGENT_RESPONSE_INVALID"), error, false);
            emit queryFinished(kind, requestId, object.toVariantMap());
        });
}

void AgentWorker::stop() {
    if (stopping_) return;
    stopping_ = true;
    running_ = false;
    collectTimer_->stop();
    uploadTimer_->stop();
    emit stateChanged(Agent::Stopping);
    stopDeadline_->start(5000);
    if (!uploading_ && spool_ && spool_->depth() > 0) uploadOnce();
    else if (!uploading_) finishStop();
}

void AgentWorker::finishStop() {
    if (!stopping_) return;
    stopDeadline_->stop();
    stopping_ = false;
    if (secure_) secure_->closeSession();
    emit stateChanged(Agent::Idle);
    emit stopped();
}

void AgentWorker::emitFailure(const Internal::SecureFailure &failure) {
    emit errorOccurred(failure.code, failure.message, failure.retryable);
}

void AgentWorker::emitQueueDepth() { emit queueDepthChanged(spool_ ? spool_->depth() : 0); }

} // namespace Synap::Qt

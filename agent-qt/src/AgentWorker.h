#pragma once

#include "Collector.h"
#include "SecureClient.h"
#include "Spool.h"

#include <synap/qt/Agent.h>

#include <QtCore/QJsonObject>
#include <QtCore/QObject>
#include <QtCore/QTimer>

#include <memory>

namespace Synap::Qt {

class AgentWorker final : public QObject {
    Q_OBJECT
public:
    explicit AgentWorker(AgentConfig config);

public slots:
    void start();
    void stop();
    void submitUserData(const QString &userId, const QVariantMap &data);
    void query(Agent::QueryKind kind, const QString &requestId);

signals:
    void stateChanged(Synap::Qt::Agent::State state);
    void started();
    void stopped();
    void errorOccurred(const QString &code, const QString &message, bool retryable);
    void queryFinished(Synap::Qt::Agent::QueryKind kind, const QString &requestId,
                       const QVariantMap &payload);
    void queueDepthChanged(int depth);
    void reminderReceived(const QString &title, const QString &message);

private:
    bool parseSchedule(const QJsonArray &values, QString *error);
    void collectOnce();
    void enqueue(QJsonObject fact, const QString &userId = {});
    void uploadOnce();
    void handleUpload(const Internal::QueueRecord &record,
                      const Internal::SecureResponse &response,
                      const Internal::SecureFailure &failure);
    void scheduleUploadRetry();
    void finishStop();
    void emitQueueDepth();
    void emitFailure(const Internal::SecureFailure &failure);
    QJsonObject startMeta();

    AgentConfig config_;
    QString dataDirectory_;
    std::unique_ptr<Internal::Collector> collector_;
    std::unique_ptr<Internal::Spool> spool_;
    std::unique_ptr<Internal::SecureClient> secure_;
    QJsonArray common_;
    QJsonObject serverConfig_;
    QJsonObject startMeta_;
    qint64 startId_ = 0;
    qint64 nextSequence_ = 2;
    int collectIntervalMs_ = 0;
    int collectWindowMs_ = 0;
    int uploadIntervalMs_ = 0;
    int uploadWindowMs_ = 0;
    int retryDelayMs_ = 1000;
    bool running_ = false;
    bool stopping_ = false;
    bool uploading_ = false;
    QTimer *collectTimer_ = nullptr;
    QTimer *uploadTimer_ = nullptr;
    QTimer *stopDeadline_ = nullptr;
};

} // namespace Synap::Qt

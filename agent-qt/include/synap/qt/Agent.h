#pragma once

#include <QtCore/QObject>
#include <QtCore/QString>
#include <QtCore/QVariantMap>

#include "AgentConfig.h"
#include "Export.h"

class QThread;

namespace Synap::Qt {

class AgentWorker;

class SYNAP_AGENT_QT_EXPORT Agent final : public QObject {
    Q_OBJECT
    Q_PROPERTY(State state READ state NOTIFY stateChanged)
    Q_PROPERTY(int queueDepth READ queueDepth NOTIFY queueDepthChanged)

public:
    enum State { Idle, Starting, Running, Stopping, Error };
    Q_ENUM(State)

    enum QueryKind { DeviceInfo, Rating, Labels };
    Q_ENUM(QueryKind)

    explicit Agent(QObject *parent = nullptr);
    ~Agent() override;

    bool configure(const AgentConfig &config, QString *error = nullptr);
    State state() const;
    int queueDepth() const;

    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE bool submitUserData(const QString &userId, const QVariantMap &data);
    Q_INVOKABLE QString requestDeviceInfo();
    Q_INVOKABLE QString requestRating();
    Q_INVOKABLE QString requestLabels();

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
    QString request(QueryKind kind);
    void setState(State state);
    void destroyWorker();

    AgentConfig config_;
    State state_ = Idle;
    int queueDepth_ = 0;
    QThread *thread_ = nullptr;
    AgentWorker *worker_ = nullptr;
};

} // namespace Synap::Qt


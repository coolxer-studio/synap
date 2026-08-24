#include <synap/qt/Agent.h>

#include "AgentWorker.h"

#include <QtCore/QMetaObject>
#include <QtCore/QThread>
#include <QtCore/QUuid>

namespace Synap::Qt {

Agent::Agent(QObject *parent) : QObject(parent) {
    qRegisterMetaType<Agent::State>();
    qRegisterMetaType<Agent::QueryKind>();
}

Agent::~Agent() {
    if (worker_ && thread_ && thread_->isRunning())
        QMetaObject::invokeMethod(worker_, "stop", ::Qt::BlockingQueuedConnection);
    if (thread_) {
        if (!thread_->wait(5500)) {
            thread_->quit();
            thread_->wait(1000);
        }
        delete thread_;
    }
}

bool Agent::configure(const AgentConfig &config, QString *error) {
    if (worker_ && thread_ && !thread_->isRunning()) destroyWorker();
    if (state_ != Idle || worker_) {
        if (error) *error = QStringLiteral("agent can only be configured while idle");
        return false;
    }
    const QString validation = config.validationError();
    if (!validation.isEmpty()) {
        if (error) *error = validation;
        return false;
    }
    config_ = config;
    return true;
}

Agent::State Agent::state() const { return state_; }
int Agent::queueDepth() const { return queueDepth_; }

void Agent::start() {
    if (state_ == Starting || state_ == Running || state_ == Stopping) return;
    const QString validation = config_.validationError();
    if (!validation.isEmpty()) {
        setState(Error);
        emit errorOccurred(QStringLiteral("AGENT_CONFIG_INVALID"), validation, false);
        return;
    }
    if (worker_) destroyWorker();
    thread_ = new QThread;
    worker_ = new AgentWorker(config_);
    worker_->moveToThread(thread_);
    connect(thread_, &QThread::finished, worker_, &QObject::deleteLater);
    connect(worker_, &AgentWorker::stateChanged, this, &Agent::setState);
    connect(worker_, &AgentWorker::started, this, &Agent::started);
    connect(worker_, &AgentWorker::stopped, thread_, &QThread::quit, ::Qt::DirectConnection);
    connect(worker_, &AgentWorker::stopped, this, [this] {
        emit stopped();
    });
    connect(worker_, &AgentWorker::errorOccurred, this, &Agent::errorOccurred);
    connect(worker_, &AgentWorker::queryFinished, this, &Agent::queryFinished);
    connect(worker_, &AgentWorker::reminderReceived, this, &Agent::reminderReceived);
    connect(worker_, &AgentWorker::queueDepthChanged, this, [this](int depth) {
        if (queueDepth_ == depth) return;
        queueDepth_ = depth;
        emit queueDepthChanged(depth);
    });
    thread_->start();
    setState(Starting);
    QMetaObject::invokeMethod(worker_, "start", ::Qt::QueuedConnection);
}

void Agent::stop() {
    if (!worker_) { if (state_ != Idle) setState(Idle); return; }
    if (!thread_ || !thread_->isRunning()) {
        destroyWorker();
        setState(Idle);
        return;
    }
    if (state_ == Stopping) return;
    setState(Stopping);
    QMetaObject::invokeMethod(worker_, "stop", ::Qt::QueuedConnection);
}

bool Agent::submitUserData(const QString &userId, const QVariantMap &data) {
    if (!worker_ || state_ != Running || userId.trimmed().isEmpty()) return false;
    QMetaObject::invokeMethod(worker_, [worker = worker_, userId, data] {
        worker->submitUserData(userId, data);
    }, ::Qt::QueuedConnection);
    return true;
}

QString Agent::requestDeviceInfo() { return request(DeviceInfo); }
QString Agent::requestRating() { return request(Rating); }
QString Agent::requestLabels() { return request(Labels); }

QString Agent::request(QueryKind kind) {
    if (!worker_ || state_ != Running) return {};
    const QString requestId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    QMetaObject::invokeMethod(worker_, [worker = worker_, kind, requestId] {
        worker->query(kind, requestId);
    }, ::Qt::QueuedConnection);
    return requestId;
}

void Agent::setState(State state) {
    if (state_ == state) return;
    state_ = state;
    emit stateChanged(state);
}

void Agent::destroyWorker() {
    if (!thread_) { worker_ = nullptr; return; }
    if (worker_ && thread_->isRunning())
        QMetaObject::invokeMethod(worker_, "stop", ::Qt::BlockingQueuedConnection);
    if (!thread_->wait(5500)) {
        thread_->quit();
        thread_->wait(1000);
    }
    delete thread_;
    thread_ = nullptr;
    worker_ = nullptr;
}

} // namespace Synap::Qt

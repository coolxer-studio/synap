#include <synap/qt/Agent.h>

#include <QtCore/QDebug>
#include <QtCore/QProcessEnvironment>
#include <QtGui/QGuiApplication>

using Synap::Qt::Agent;
using Synap::Qt::AgentConfig;

int main(int argc, char **argv) {
    QGuiApplication application(argc, argv);
    const QProcessEnvironment environment = QProcessEnvironment::systemEnvironment();

    AgentConfig config;
    config.baseUrl = QUrl(environment.value(QStringLiteral("SYNAP_SC_BASE_URL")));
    config.businessAppId = environment.value(QStringLiteral("SYNAP_HOST_APP_ID"), QStringLiteral("1")).toInt();
    config.secureAppId = environment.value(QStringLiteral("SYNAP_SC_APP_ID"));
    config.serverKeyId = environment.value(QStringLiteral("SYNAP_SC_SERVER_KEY_ID"));
    config.trustAnchorBase64Url = environment.value(QStringLiteral("SYNAP_SC_TRUST_ANCHOR"));
    config.appName = QCoreApplication::applicationName().isEmpty()
        ? QStringLiteral("synap-qt-example") : QCoreApplication::applicationName();
    config.appPackage = QStringLiteral("com.coolxer.synap.qt.example");
    config.appVersion = QStringLiteral("1.0.0");
    config.enrollmentTokenProvider = [environment] {
        return environment.value(QStringLiteral("SYNAP_SC_ENROLLMENT_TOKEN"));
    };

    Agent agent;
    QObject::connect(&agent, &Agent::errorOccurred, [](const QString &code,
            const QString &message, bool retryable) {
        qWarning() << "Synap Agent" << code << message << "retryable=" << retryable;
    });
    QObject::connect(&agent, &Agent::reminderReceived,
                     [](const QString &title, const QString &message) {
        qInfo() << "Synap reminder:" << title << message;
    });
    QObject::connect(&application, &QCoreApplication::aboutToQuit, &agent, &Agent::stop);

    QString error;
    if (!agent.configure(config, &error)) {
        qCritical() << error;
        return 2;
    }
    agent.start();
    return application.exec();
}


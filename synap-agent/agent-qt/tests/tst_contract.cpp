#include "Collector.h"
#include "Crypto.h"
#include "Spool.h"

#include <QtCore/QJsonArray>
#include <QtCore/QTemporaryDir>
#include <QtTest/QTest>

using namespace Synap::Qt;
using namespace Synap::Qt::Internal;

class ContractTest final : public QObject {
    Q_OBJECT
private:
    static AgentConfig config(const QString &directory) {
        QString error;
        PKey trust = generateP256(&error);
        AgentConfig value;
        value.baseUrl = QUrl(QStringLiteral("https://risk.example.test"));
        value.businessAppId = 7;
        value.secureAppId = QStringLiteral("synap-qt-test");
        value.serverKeyId = QStringLiteral("server-key-test");
        value.trustAnchorBase64Url = QString::fromLatin1(base64UrlEncode(publicKeySpki(trust.get(), &error)));
        value.appName = QStringLiteral("Qt Test");
        value.appPackage = QStringLiteral("com.coolxer.test");
        value.appVersion = QStringLiteral("1.2.3");
        value.dataDirectory = directory;
        return value;
    }

private slots:
    void validatesSecurityAndLocalHttpPolicy() {
        QTemporaryDir directory;
        AgentConfig value = config(directory.path());
        QCOMPARE(value.validationError(), QString());
        value.baseUrl = QUrl(QStringLiteral("http://example.test"));
        QVERIFY(!value.validationError().isEmpty());
        value.baseUrl = QUrl(QStringLiteral("http://127.0.0.1:11099"));
        value.allowInsecureLocalhost = true;
        QCOMPARE(value.validationError(), QString());
    }

    void createsHostCommonAndLightweightFactShapes() {
        QTemporaryDir directory;
        AgentConfig value = config(directory.path());
        Collector collector(value, directory.path());
        QString error;
        QVERIFY2(collector.initialize(&error), qPrintable(error));
        const QJsonArray common = collector.common(1700000000000LL);
        QCOMPARE(common.size(), 24);
        QCOMPARE(common.at(2).toString(), QStringLiteral("1700000000000"));
        QCOMPARE(common.at(8).toString(), QStringLiteral("host"));
        QCOMPARE(collector.code().split(QLatin1Char('-')).size(), 8);

        const QJsonObject serverConfig{
            {QStringLiteral("deviceConfig"), QJsonObject{
                {QStringLiteral("device"), QJsonArray{QStringLiteral("type"), QStringLiteral("name")}},
                {QStringLiteral("screen"), QJsonArray{QStringLiteral("width")}},
                {QStringLiteral("cpu"), QJsonArray{QStringLiteral("totalCore"), QStringLiteral("abi")}},
                {QStringLiteral("battery"), QJsonArray{QStringLiteral("capacity")}},
                {QStringLiteral("uuid"), QJsonArray{QStringLiteral("macEth0"), QStringLiteral("guid")}},
                {QStringLiteral("network"), QJsonArray{QStringLiteral("lanIp"), QStringLiteral("type")}},
                {QStringLiteral("storage"), QJsonArray{QStringLiteral("totalDisk")}}}},
            {QStringLiteral("runtimeConfig"), QJsonObject{
                {QStringLiteral("process"), QJsonArray{QStringLiteral(".*")}},
                {QStringLiteral("env"), QJsonArray{QStringLiteral("SECRET")}},
                {QStringLiteral("cmd"), QJsonArray{QStringLiteral(".*")}}}},
            {QStringLiteral("netConfig"), QJsonObject{
                {QStringLiteral("port"), QJsonArray{QStringLiteral("22")}},
                {QStringLiteral("socket"), QJsonArray{}}, {QStringLiteral("uds"), QJsonArray{}},
                {QStringLiteral("netInterface"), QJsonArray{QStringLiteral(".*")}},
                {QStringLiteral("httpProxy"), QJsonArray{QStringLiteral(".*")}},
                {QStringLiteral("hosts"), QJsonArray{QStringLiteral("hosts")}},
                {QStringLiteral("certificate"), QJsonArray{QStringLiteral("cert")}}}}
        };
        const QList<QJsonObject> facts = collector.collect(serverConfig);
        QCOMPARE(facts.size(), 3);
        QCOMPARE(facts.at(0).value(QStringLiteral("uuid")).toArray().size(), 2);
        QCOMPARE(facts.at(1).value(QStringLiteral("env")).toArray().at(0).toString(), QString());
        QCOMPARE(facts.at(2).value(QStringLiteral("port")).toArray().at(0).toInt(), 0);
    }

    void persistsStableQueueMetadata() {
        QTemporaryDir directory;
        AgentConfig value = config(directory.path());
        value.maxQueueItems = 2;
        Spool spool(directory.path(), value);
        QString error;
        QVERIFY2(spool.initialize(&error), qPrintable(error));
        QJsonArray common; for (int i = 0; i < 24; ++i) common.append(QString());
        common[2] = QStringLiteral("1700000000000");
        QJsonObject fact{{QStringLiteral("type"), QStringLiteral("UserData")},
                         {QStringLiteral("messageId"), QStringLiteral("message-1")},
                         {QStringLiteral("observedAt"), 1700000000000.0},
                         {QStringLiteral("sequence"), 2}};
        QVERIFY2(spool.enqueue(QStringLiteral("a-b-c-d-e-host-7-1.0.0"), common, fact,
                               QStringLiteral("7"), &error), qPrintable(error));
        QueueRecord record;
        QVERIFY(spool.next(&record, &error));
        const QString batchId = record.meta.value(QStringLiteral("batchId")).toString();
        QVERIFY(spool.prepareAttempt(&record, &error));
        QCOMPARE(record.meta.value(QStringLiteral("attempt")).toInt(), 1);
        QueueRecord reloaded;
        QVERIFY(spool.next(&reloaded, &error));
        QCOMPARE(reloaded.meta.value(QStringLiteral("batchId")).toString(), batchId);
        QCOMPARE(reloaded.fact.value(QStringLiteral("messageId")).toString(), QStringLiteral("message-1"));
        QVERIFY(spool.acknowledge(reloaded, &error));
        QCOMPARE(spool.depth(), 0);
    }
};

QTEST_GUILESS_MAIN(ContractTest)
#include "tst_contract.moc"

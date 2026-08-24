#include "Crypto.h"

#include <QtTest/QTest>

using namespace Synap::Qt::Internal;

class CryptoTest final : public QObject {
    Q_OBJECT
private slots:
    void base64UrlIsUnpaddedAndStrict() {
        const QByteArray encoded = base64UrlEncode(QByteArray::fromHex("fbff00"));
        QCOMPARE(encoded, QByteArray("-_8A"));
        bool ok = false;
        QCOMPARE(base64UrlDecode(encoded, &ok), QByteArray::fromHex("fbff00"));
        QVERIFY(ok);
        base64UrlDecode("-_8A=", &ok);
        QVERIFY(!ok);
    }

    void implementsRfc5869Sha256Vector() {
        const QByteArray ikm = QByteArray::fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        const QByteArray salt = QByteArray::fromHex("000102030405060708090a0b0c");
        const QByteArray info = QByteArray::fromHex("f0f1f2f3f4f5f6f7f8f9");
        QString error;
        const QByteArray output = hkdfSha256(ikm, salt, info, 42, &error);
        QCOMPARE(error, QString());
        QCOMPARE(output.toHex(), QByteArray("3cb25f25faacd57a90434f64d0362f2a"
                                             "2d2d0a90cf1a5a4c5db02d56ecc4c5bf"
                                             "34007208d5b887185865"));
    }

    void signsP1363AndDerivesEcdh() {
        QString error;
        PKey left = generateP256(&error);
        PKey right = generateP256(&error);
        QVERIFY2(left && right, qPrintable(error));
        const QByteArray message("transcript-hash");
        const QByteArray signature = signP1363(left.get(), message, &error);
        QCOMPARE(signature.size(), 64);
        QVERIFY2(verifyP1363(left.get(), message, signature, &error), qPrintable(error));
        QVERIFY(!verifyP1363(right.get(), message, signature, nullptr));
        QCOMPARE(deriveEcdh(left.get(), right.get(), &error),
                 deriveEcdh(right.get(), left.get(), &error));
    }

    void authenticatesAesGcm() {
        const QByteArray key(32, '\x11');
        const QByteArray nonce(12, '\x22');
        const QByteArray aadValue("SC1\nrequest");
        const QByteArray plaintext("protected payload");
        Ciphertext sealed = aes256GcmEncrypt(key, nonce, aadValue, plaintext);
        QVERIFY2(sealed.error.isEmpty(), qPrintable(sealed.error));
        Ciphertext opened = aes256GcmDecrypt(key, nonce, aadValue, sealed.bytes);
        QCOMPARE(opened.bytes, plaintext);
        sealed.bytes[0] = char(sealed.bytes[0] ^ 1);
        QVERIFY(!aes256GcmDecrypt(key, nonce, aadValue, sealed.bytes).error.isEmpty());
    }
};

QTEST_GUILESS_MAIN(CryptoTest)
#include "tst_crypto.moc"

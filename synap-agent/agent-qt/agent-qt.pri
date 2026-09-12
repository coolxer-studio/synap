CONFIG += c++17
QT += core network gui

INCLUDEPATH += $$PWD/include $$PWD/src
DEPENDPATH += $$PWD/include $$PWD/src

HEADERS += \
    $$PWD/include/synap/qt/Agent.h \
    $$PWD/include/synap/qt/AgentConfig.h \
    $$PWD/include/synap/qt/Export.h \
    $$PWD/src/AgentWorker.h \
    $$PWD/src/Collector.h \
    $$PWD/src/Crypto.h \
    $$PWD/src/IdentityStore.h \
    $$PWD/src/PlatformPermissions.h \
    $$PWD/src/SecureClient.h \
    $$PWD/src/Spool.h

SOURCES += \
    $$PWD/src/Agent.cpp \
    $$PWD/src/AgentConfig.cpp \
    $$PWD/src/AgentWorker.cpp \
    $$PWD/src/Collector.cpp \
    $$PWD/src/Crypto.cpp \
    $$PWD/src/IdentityStore.cpp \
    $$PWD/src/PlatformPermissions.cpp \
    $$PWD/src/SecureClient.cpp \
    $$PWD/src/Spool.cpp

DEFINES += SYNAP_AGENT_QT_STATIC

SYNAP_OPENSSL_ROOT = $$(OPENSSL_ROOT_DIR)
!isEmpty(SYNAP_OPENSSL_ROOT) {
    INCLUDEPATH += $$SYNAP_OPENSSL_ROOT/include
    LIBS += -L$$SYNAP_OPENSSL_ROOT/lib -lssl -lcrypto
} else:macx:exists(/opt/homebrew/opt/openssl@3/include/openssl/evp.h) {
    INCLUDEPATH += /opt/homebrew/opt/openssl@3/include
    LIBS += -L/opt/homebrew/opt/openssl@3/lib -lssl -lcrypto
} else:macx:exists(/usr/local/opt/openssl@3/include/openssl/evp.h) {
    INCLUDEPATH += /usr/local/opt/openssl@3/include
    LIBS += -L/usr/local/opt/openssl@3/lib -lssl -lcrypto
} else {
    LIBS += -lssl -lcrypto
}
win32:LIBS += -ladvapi32

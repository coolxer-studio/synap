add_executable(tst_crypto
    tst_crypto.cpp
    ../src/Crypto.cpp
    ../src/Crypto.h
)
target_include_directories(tst_crypto PRIVATE ../src)
target_link_libraries(tst_crypto PRIVATE Qt${QT_VERSION_MAJOR}::Test OpenSSL::Crypto)
add_test(NAME agent-qt-crypto COMMAND tst_crypto)

add_executable(tst_contract
    tst_contract.cpp
    ../src/AgentConfig.cpp
    ../src/Collector.cpp
    ../src/Collector.h
    ../src/Crypto.cpp
    ../src/Crypto.h
    ../src/PlatformPermissions.cpp
    ../src/PlatformPermissions.h
    ../src/Spool.cpp
    ../src/Spool.h
)
target_compile_definitions(tst_contract PRIVATE SYNAP_AGENT_QT_STATIC)
target_include_directories(tst_contract PRIVATE ../include ../src)
target_link_libraries(tst_contract PRIVATE
    Qt${QT_VERSION_MAJOR}::Test Qt${QT_VERSION_MAJOR}::Network Qt${QT_VERSION_MAJOR}::Gui
    OpenSSL::Crypto
)
if(WIN32)
    target_link_libraries(tst_contract PRIVATE advapi32)
endif()
add_test(NAME agent-qt-contract COMMAND tst_contract)

add_executable(tst_secureclient
    tst_secureclient.cpp
    ../src/Crypto.cpp
    ../src/Crypto.h
    ../src/IdentityStore.cpp
    ../src/IdentityStore.h
    ../src/PlatformPermissions.cpp
    ../src/PlatformPermissions.h
    ../src/SecureClient.cpp
    ../src/SecureClient.h
)
target_compile_definitions(tst_secureclient PRIVATE SYNAP_AGENT_QT_STATIC)
target_include_directories(tst_secureclient PRIVATE ../include ../src)
target_link_libraries(tst_secureclient PRIVATE
    Qt${QT_VERSION_MAJOR}::Test Qt${QT_VERSION_MAJOR}::Network OpenSSL::Crypto OpenSSL::SSL
)
if(WIN32)
    target_link_libraries(tst_secureclient PRIVATE advapi32)
endif()
add_test(NAME agent-qt-secureclient COMMAND tst_secureclient)

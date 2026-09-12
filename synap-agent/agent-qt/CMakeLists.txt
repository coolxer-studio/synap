cmake_minimum_required(VERSION 3.16)

project(SynapAgentQt VERSION 1.0.0 LANGUAGES CXX)

include(GNUInstallDirs)
include(CMakePackageConfigHelpers)

option(SYNAP_AGENT_QT_BUILD_EXAMPLES "Build the integration example" ON)
option(SYNAP_AGENT_QT_BUILD_TESTS "Build unit tests" ON)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_AUTOMOC ON)

find_package(QT NAMES Qt6 Qt5 REQUIRED COMPONENTS Core Network Gui)
find_package(Qt${QT_VERSION_MAJOR} REQUIRED COMPONENTS Core Network Gui)
find_package(OpenSSL 1.1.1 REQUIRED COMPONENTS Crypto SSL)

add_library(SynapAgentQt
    include/synap/qt/Agent.h
    include/synap/qt/AgentConfig.h
    include/synap/qt/Export.h
    src/Agent.cpp
    src/AgentConfig.cpp
    src/AgentWorker.cpp
    src/AgentWorker.h
    src/Collector.cpp
    src/Collector.h
    src/Crypto.cpp
    src/Crypto.h
    src/IdentityStore.cpp
    src/IdentityStore.h
    src/PlatformPermissions.cpp
    src/PlatformPermissions.h
    src/SecureClient.cpp
    src/SecureClient.h
    src/Spool.cpp
    src/Spool.h
)
add_library(Synap::AgentQt ALIAS SynapAgentQt)

if(BUILD_SHARED_LIBS)
    target_compile_definitions(SynapAgentQt PRIVATE SYNAP_AGENT_QT_BUILDING_LIBRARY)
else()
    target_compile_definitions(SynapAgentQt PUBLIC SYNAP_AGENT_QT_STATIC)
endif()
target_include_directories(SynapAgentQt
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
        $<INSTALL_INTERFACE:${CMAKE_INSTALL_INCLUDEDIR}>
    PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src
)
target_link_libraries(SynapAgentQt
    PUBLIC Qt${QT_VERSION_MAJOR}::Core
    PRIVATE
        Qt${QT_VERSION_MAJOR}::Network
        Qt${QT_VERSION_MAJOR}::Gui
        OpenSSL::Crypto
        OpenSSL::SSL
)
if(WIN32)
    target_link_libraries(SynapAgentQt PRIVATE advapi32)
endif()
set_target_properties(SynapAgentQt PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION ${PROJECT_VERSION_MAJOR}
    EXPORT_NAME AgentQt
)

if(SYNAP_AGENT_QT_BUILD_EXAMPLES)
    add_subdirectory(examples/basic)
endif()

if(SYNAP_AGENT_QT_BUILD_TESTS)
    include(CTest)
    if(BUILD_TESTING)
        find_package(Qt${QT_VERSION_MAJOR} REQUIRED COMPONENTS Test)
        add_subdirectory(tests)
    endif()
endif()

install(TARGETS SynapAgentQt EXPORT SynapAgentQtTargets
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR}
    INCLUDES DESTINATION ${CMAKE_INSTALL_INCLUDEDIR}
)
install(DIRECTORY include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
install(EXPORT SynapAgentQtTargets
    FILE SynapAgentQtTargets.cmake
    NAMESPACE Synap::
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/SynapAgentQt
)

configure_package_config_file(
    cmake/SynapAgentQtConfig.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/SynapAgentQtConfig.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/SynapAgentQt
)
write_basic_package_version_file(
    ${CMAKE_CURRENT_BINARY_DIR}/SynapAgentQtConfigVersion.cmake
    VERSION ${PROJECT_VERSION}
    COMPATIBILITY SameMajorVersion
)
install(FILES
    ${CMAKE_CURRENT_BINARY_DIR}/SynapAgentQtConfig.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/SynapAgentQtConfigVersion.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/SynapAgentQt
)

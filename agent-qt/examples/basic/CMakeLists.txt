add_executable(synap-agent-qt-example main.cpp)
target_link_libraries(synap-agent-qt-example PRIVATE Synap::AgentQt Qt${QT_VERSION_MAJOR}::Gui)


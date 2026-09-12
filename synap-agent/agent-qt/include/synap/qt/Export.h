#pragma once

#include <QtCore/qglobal.h>

#if defined(SYNAP_AGENT_QT_STATIC)
#  define SYNAP_AGENT_QT_EXPORT
#elif defined(SYNAP_AGENT_QT_BUILDING_LIBRARY)
#  define SYNAP_AGENT_QT_EXPORT Q_DECL_EXPORT
#else
#  define SYNAP_AGENT_QT_EXPORT Q_DECL_IMPORT
#endif

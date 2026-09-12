#include <jni.h>

// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("demo");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("demo")
//      }
//    }
extern "C"
JNIEXPORT jint JNICALL
Java_com_coolxer_probe_demo_ui_tool_CrashActivity_testCCrash(JNIEnv *env, jclass clazz) {
    int *a = NULL;
    *a = 1;
}
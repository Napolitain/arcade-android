#include <android/log.h>
#include <android_native_app_glue.h>

namespace {
constexpr const char* kLogTag = "arcade-native";

void handleAppCommand(android_app* app, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "Window initialized");
            break;
        case APP_CMD_TERM_WINDOW:
            __android_log_print(ANDROID_LOG_INFO, kLogTag, "Window terminated");
            break;
        default:
            break;
    }
}

int32_t handleInputEvent(android_app* /*app*/, AInputEvent* event) {
    if (AInputEvent_getType(event) == AINPUT_EVENT_TYPE_MOTION) {
        __android_log_print(ANDROID_LOG_VERBOSE, kLogTag, "Motion input event");
    }
    return 0;
}
}  // namespace

void android_main(android_app* app) {
    app->onAppCmd = handleAppCommand;
    app->onInputEvent = handleInputEvent;

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "Native loop started");

    int events;
    android_poll_source* source;

    while (true) {
        while (ALooper_pollOnce(0, nullptr, &events, reinterpret_cast<void**>(&source)) >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }

            if (app->destroyRequested != 0) {
                __android_log_print(ANDROID_LOG_INFO, kLogTag, "Destroy requested, exiting");
                return;
            }
        }

        // Runtime/render loop will be extended from here.
    }
}

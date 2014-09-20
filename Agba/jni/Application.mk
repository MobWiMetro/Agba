APP_BUILD_SCRIPT = $(APP_PROJECT_PATH)/Android.mk
APP_PLATFORM := android-9
APP_CFLAGS += -Wno-error=format-security

JNI_H_INCLUDE = $(APP_PROJECT_PATH)/../common/libnativehelper/include/

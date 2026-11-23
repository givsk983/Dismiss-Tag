LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := appprivacy_timezone
LOCAL_SRC_FILES := native_timezone.c
LOCAL_CFLAGS := -std=c11 -Wall -Wextra -Werror -fvisibility=hidden
LOCAL_LDFLAGS := -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384
include $(BUILD_SHARED_LIBRARY)

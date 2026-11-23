#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <sched.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/system_properties.h>
#include <time.h>

typedef int (*HookFunction)(void *, void *, void **);
typedef int (*UnhookFunction)(void *);
typedef void (*LibraryLoaded)(const char *, void *);

struct NativeApi {
    uint32_t version;
    HookFunction hook;
    UnhookFunction unhook;
};

typedef int (*PropertyGet)(const char *, char *);
typedef int (*PropertyRead)(const prop_info *, char *, char *);
typedef void (*PropertyCallback)(void *, const char *, const char *, uint32_t);
typedef void (*PropertyReadCallback)(const prop_info *, PropertyCallback, void *);

struct CallbackContext {
    PropertyCallback callback;
    void *cookie;
};

static const char timezone_property[] = "persist.sys.timezone";
static const prop_info *timezone_info;
static char timezone_id[PROP_VALUE_MAX];
static HookFunction hook_function;
static int installed_hooks;
static _Atomic(void *) original_get;
static _Atomic(void *) original_read;
static _Atomic(void *) original_read_callback;
static int local_time_configured;

static void *get_original(_Atomic(void *) *slot) {
    // A caller on another thread can enter after the patch but before LSPosed returns its trampoline.
    void *original;
    while ((original = atomic_load_explicit(slot, memory_order_acquire)) == NULL) {
        sched_yield();
    }
    return original;
}

static int get_property(const char *name, char *value) {
    PropertyGet original = get_original(&original_get);
    if (strcmp(name, timezone_property) == 0) {
        strcpy(value, timezone_id);
        return (int)strlen(timezone_id);
    }
    return original(name, value);
}

static int read_property(const prop_info *property, char *name, char *value) {
    PropertyRead original = get_original(&original_read);
    int length = original(property, name, value);
    if (property == timezone_info && length >= 0) {
        strcpy(value, timezone_id);
        return (int)strlen(timezone_id);
    }
    return length;
}

static void replace_callback_value(void *cookie, const char *name, const char *value, uint32_t serial) {
    (void)value;
    struct CallbackContext *context = cookie;
    context->callback(context->cookie, name, timezone_id, serial);
}

static void read_property_callback(const prop_info *property, PropertyCallback callback, void *cookie) {
    PropertyReadCallback original = get_original(&original_read_callback);
    if (property == timezone_info) {
        struct CallbackContext context = {callback, cookie};
        original(property, replace_callback_value, &context);
        return;
    }
    original(property, callback, cookie);
}

static int install_hook(void *target, void *replacement, _Atomic(void *) *slot) {
    void *original = NULL;
    if (hook_function(target, replacement, &original) != 0 || original == NULL) {
        return 0;
    }
    atomic_store_explicit(slot, original, memory_order_release);
    return 1;
}

__attribute__((visibility("default"), used))
LibraryLoaded native_init(const struct NativeApi *api) {
    hook_function = api->hook;
    return NULL;
}

JNIEXPORT jint JNICALL
Java_io_github_appprivacyprofile_PrivacyModule_configureNativeTimeZone(
        JNIEnv *env, jclass type, jstring id) {
    (void)type;
    if (timezone_id[0] != '\0') {
        return installed_hooks * 2 + local_time_configured;
    }

    jsize length = (*env)->GetStringUTFLength(env, id);
    if (length <= 0 || (size_t)length >= sizeof(timezone_id)) {
        return 0;
    }
    const char *chars = (*env)->GetStringUTFChars(env, id, NULL);
    if (chars == NULL) {
        return 0;
    }
    memcpy(timezone_id, chars, (size_t)length + 1);
    (*env)->ReleaseStringUTFChars(env, id, chars);

    // Set the process environment once during package startup, before the app runs its own code.
    if (setenv("TZ", timezone_id, 1) == 0) {
        tzset();
        local_time_configured = 1;
    }

    if (hook_function != NULL) {
        timezone_info = __system_property_find(timezone_property);
        installed_hooks += install_hook((void *)__system_property_get, (void *)get_property, &original_get);
        if (timezone_info != NULL) {
            installed_hooks += install_hook((void *)__system_property_read, (void *)read_property, &original_read);
            installed_hooks += install_hook((void *)__system_property_read_callback,
                    (void *)read_property_callback, &original_read_callback);
        }
    }
    // The low bit reports libc setup separately from the number of property hooks.
    return installed_hooks * 2 + local_time_configured;
}

#include <android/log.h>
#include <jni.h>
#include <link.h>
#include <cstring>
#include <string>
#include <unordered_map>
#include <mutex>
#include <atomic>
#include <sys/mman.h>
#include <unistd.h>

// These offsets and ARM instructions belong only to Netflix TV 8.3.11 build 12041.
// Refuse other binaries; the Java extension alone must never hide native captions.
static uintptr_t image;
static JavaVM* vm;
static jclass bridge;
static jmethodID cue_method;
static std::atomic<bool> enabled{false};
static bool installed;
static void (*original_text)(void*, const std::string&);
static void (*original_render)(void*, void*, const void*, unsigned);
struct Cue { std::string markup; bool shown = false; bool dirty = true; };
static std::unordered_map<void*, Cue> nodes;
static std::mutex node_mutex;

// Both inspected entry points begin with two ordinary ARM stack instructions.
// Relocate only those validated instructions; no general instruction decoder is used.
static int hook(uint32_t* address, void* replacement, void** original) {
    auto* trampoline = static_cast<uint32_t*>(mmap(nullptr, 4096, PROT_READ | PROT_WRITE,
            MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
    if (trampoline == MAP_FAILED) return -1;
    trampoline[0] = address[0]; trampoline[1] = address[1];
    trampoline[2] = 0xe51ff004;
    trampoline[3] = reinterpret_cast<uintptr_t>(address + 2);
    __builtin___clear_cache(reinterpret_cast<char*>(trampoline), reinterpret_cast<char*>(trampoline + 4));
    if (mprotect(trampoline, 4096, PROT_READ | PROT_EXEC)) { munmap(trampoline, 4096); return -2; }
    uintptr_t page_size = sysconf(_SC_PAGESIZE);
    void* page = reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(address) & ~(page_size - 1));
    if (mprotect(page, page_size, PROT_READ | PROT_WRITE | PROT_EXEC)) { munmap(trampoline, 4096); return -3; }
    *original = trampoline;
    address[1] = reinterpret_cast<uintptr_t>(replacement);
    address[0] = 0xe51ff004;
    __builtin___clear_cache(reinterpret_cast<char*>(address), reinterpret_cast<char*>(address + 2));
    mprotect(page, page_size, PROT_READ | PROT_EXEC);
    __android_log_print(ANDROID_LOG_INFO, "AimalNetflix", "ARM bridge %p words=%08x,%08x target=%p", address, address[0], address[1], replacement);
    return 0;
}

static bool send(void* object, const std::string& text) {
    if (!vm || !bridge || !cue_method) return false;
    JNIEnv* env = nullptr;
    bool attached = vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED;
    if (attached && vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return false;
    jbyteArray bytes = env->NewByteArray(text.size());
    if (bytes) {
        env->SetByteArrayRegion(bytes, 0, text.size(), reinterpret_cast<const jbyte*>(text.data()));
        env->CallStaticVoidMethod(bridge, cue_method, static_cast<jlong>(reinterpret_cast<uintptr_t>(object)), bytes);
        env->DeleteLocalRef(bytes);
    }
    bool ok = !env->ExceptionCheck();
    if (!ok) {
        env->ExceptionClear();
        enabled = false;
        __android_log_print(ANDROID_LOG_ERROR, "AimalNetflix", "Caption bridge failed; restoring native rendering");
    }
    if (attached) vm->DetachCurrentThread();
    return ok;
}

static bool subtitle_markup(const std::string& text) {
    return text.rfind("<span italic=\"", 0) == 0
        && text.find(" underline=\"") != std::string::npos
        && text.find(" background=\"") != std::string::npos
        && text.find(" font_family=\"") != std::string::npos;
}

static void text_hook(void* object, const std::string& text) {
    bool clear = false;
    {
        std::lock_guard<std::mutex> lock(node_mutex);
        if (subtitle_markup(text)) {
            auto& cue = nodes[object];
            cue.markup = text;
            cue.dirty = true;
        } else {
            auto it = nodes.find(object);
            if (it != nodes.end()) { clear = it->second.shown; nodes.erase(it); }
        }
    }
    if (clear) send(object, "");
    original_text(object, text);
}

static void render_hook(void* object, void* engine, const void* rect, unsigned flags) {
    bool candidate = false, update = false;
    std::string markup;
    {
        std::lock_guard<std::mutex> lock(node_mutex);
        auto it = nodes.find(object);
        if (it != nodes.end()) {
            candidate = true;
            auto& cue = it->second;
            if (cue.dirty || !cue.shown) { update = true; markup = cue.markup; cue.dirty = false; }
            cue.shown = true;
        }
    }
    if (update) {
        if (send(object, markup))
            __android_log_print(ANDROID_LOG_INFO, "AimalNetflix", "Rendered subtitle node=%p bytes=%zu custom=%d", object, markup.size(), enabled.load());
    }
    if (!candidate || !enabled.load()) original_render(object, engine, rect, flags);
}

static int find_image(dl_phdr_info* info, size_t, void*) {
    if (info->dlpi_name && strstr(info->dlpi_name, "libnetflix.so")) { image = info->dlpi_addr; return 1; }
    return 0;
}

__attribute__((constructor)) static void install() {
    dl_iterate_phdr(find_image, nullptr);
    if (!image) return;
    auto* text = reinterpret_cast<uint32_t*>(image + 0x4c62a8);
    auto* render = reinterpret_cast<uint32_t*>(image + 0x4c3e88);
    if (text[0] != 0xe92d4bf0 || text[1] != 0xe28db018 || render[0] != 0xe92d4ff0) {
        __android_log_print(ANDROID_LOG_WARN, "AimalNetflix", "Unsupported native binary; captions unchanged");
        return;
    }
    int a = hook(text, reinterpret_cast<void*>(text_hook), reinterpret_cast<void**>(&original_text));
    int b = hook(render, reinterpret_cast<void*>(render_hook), reinterpret_cast<void**>(&original_render));
    installed = a == 0 && b == 0;
    __android_log_print(ANDROID_LOG_INFO, "AimalNetflix", "Caption hooks text=%d render=%d", a, b);
}

extern "C" JNIEXPORT void JNICALL Java_app_aimal_extension_netflix_NetflixSubtitles_setEnabled(JNIEnv*, jclass, jboolean value) {
    enabled = installed && value;
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* java_vm, void*) {
    vm = java_vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass local = env->FindClass("app/aimal/extension/netflix/NetflixSubtitles");
    if (!local) return JNI_ERR;
    bridge = static_cast<jclass>(env->NewGlobalRef(local));
    env->DeleteLocalRef(local);
    cue_method = env->GetStaticMethodID(bridge, "onCue", "(J[B)V");
    jmethodID loaded = env->GetStaticMethodID(bridge, "nativeLoaded", "()V");
    if (!cue_method || !loaded) return JNI_ERR;
    if (installed) env->CallStaticVoidMethod(bridge, loaded);
    return JNI_VERSION_1_6;
}

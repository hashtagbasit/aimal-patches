#include <jni.h>

/* Real JNI entry point: renaming renderFrame must break this regression test. */
JNIEXPORT jobject JNICALL
Java_com_crunchyroll_subtitles_SubtitlesRendererImpl_renderFrame(
        JNIEnv *env, jobject renderer, jlong track, jlong milliseconds) {
    jclass owner = (*env)->GetObjectClass(env, renderer);
    jfieldID calls = (*env)->GetFieldID(env, owner, "nativeCalls", "I");
    (*env)->SetIntField(env, renderer, calls, (*env)->GetIntField(env, renderer, calls) + 1);
    jclass frames = (*env)->FindClass(env, "com/crunchyroll/subtitles/data/AssFrames");
    jmethodID constructor = (*env)->GetMethodID(env, frames, "<init>", "(J)V");
    return (*env)->NewObject(env, frames, constructor, milliseconds);
}

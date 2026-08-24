package app.aimal.patches.crunchyroll

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

// ── Speed control ──

object PlaybackSpeedConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("PlaybackSpeedConfigurationImpl(isEnabled="),
    ),
)

object PlayerSettingsViewModelConstructorFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        string("storage"),
        string("subtitlesSettingsViewModel"),
        string("audioSettingsViewModel"),
    ),
)

// ── Player view ──

object InternalPlayerViewLayoutClassFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z"),
    filters = listOf(
        string("getAdViewGroup(...)"),
    ),
)

object ShowControlsFingerprint : Fingerprint(
    classFingerprint = InternalPlayerViewLayoutClassFingerprint,
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    custom = { method, _ -> method.name == "showControls" },
)

object HideControlsFingerprint : Fingerprint(
    classFingerprint = InternalPlayerViewLayoutClassFingerprint,
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    custom = { method, _ -> method.name == "hideControls" },
)

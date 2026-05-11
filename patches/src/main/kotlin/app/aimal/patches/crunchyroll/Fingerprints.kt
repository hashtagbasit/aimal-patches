package app.aimal.patches.crunchyroll

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

// ── Speed Control ──

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

// ── Feature Flags ──

object SvodPreRollConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("SvodPreRollConfigImpl(isEnabled="),
    ),
)

object PipConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("PipConfigImpl(_isEnabled="),
    ),
)

object InAppReviewConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("InAppReviewConfigImpl(isEnabled="),
    ),
)

object InAppUpdatesConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("InAppUpdatesConfigImpl(isEnabled="),
    ),
)

object ChromecastSkipEventsConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ChromecastSkipEventsConfigImpl(isEnabled="),
    ),
)

object ContentLabelsConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ContentLabelsConfigImpl(isEnabled="),
    ),
)

object ShareRedesignConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ShareRedesignConfigImpl(isEnabled="),
    ),
)

object MangaConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("MangaConfigImpl(isEnabled="),
    ),
)

object HomeFeedHeroCarouselConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("HomeFeedHeroCarouselConfigImpl(isEnabled="),
    ),
)

// ── Aspect Ratio ──

object PlayerViewSetupFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Z", "Z", "L", "L", "L", "L"),
    filters = listOf(
        string("buttonDataProviderLiveData"),
        string("notifyActivity"),
    ),
)

// ── Geo Spoof ──

object CountryCodeUpdateFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
    custom = { _, classDef ->
        classDef.type == "Lcom/ellation/crunchyroll/api/etp/auth/CountryCodeProviderImpl;"
    },
)

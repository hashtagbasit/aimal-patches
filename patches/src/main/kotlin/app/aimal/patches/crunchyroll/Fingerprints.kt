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

// ── Disable Pre-Roll Ads ──

object SvodPreRollConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("SvodPreRollConfigImpl(isEnabled="),
    ),
)

// ── Enable PiP ──

object PipConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("PipConfigImpl(_isEnabled="),
    ),
)

// ── Disable In-App Review ──

object InAppReviewConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("InAppReviewConfigImpl(isEnabled="),
    ),
)

// ── Disable In-App Updates ──

object InAppUpdatesConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("InAppUpdatesConfigImpl(isEnabled="),
    ),
)

// ── Enable Chromecast Skip Events ──

object ChromecastSkipEventsConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ChromecastSkipEventsConfigImpl(isEnabled="),
    ),
)

// ── Enable Content Labels ──

object ContentLabelsConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ContentLabelsConfigImpl(isEnabled="),
    ),
)

// ── Enable Share Redesign ──

object ShareRedesignConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("ShareRedesignConfigImpl(isEnabled="),
    ),
)

// ── Enable Manga ──

object MangaConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("MangaConfigImpl(isEnabled="),
    ),
)

// ── Enable Home Feed Hero Carousel ──

object HomeFeedHeroCarouselConfigToStringFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(),
    filters = listOf(
        string("HomeFeedHeroCarouselConfigImpl(isEnabled="),
    ),
)

// ── Aspect Ratio ──

object PlayerViewConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/crunchyroll/player/presentation/playerview/InternalPlayerViewLayout;",
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Landroid/util/AttributeSet;",
    ),
    filters = listOf(
        string("layout_internal_player"),
    ),
)

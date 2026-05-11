package app.aimal.patches.crunchyroll

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import app.morphe.patcher.patch.BytecodePatchContext

/**
 * Helper: finds the isEnabled() boolean method in the same class
 * as the given toString fingerprint, and forces it to return the
 * specified value.
 */
context(BytecodePatchContext)
private fun forceConfigFlag(
    toStringFingerprint: Fingerprint,
    returnValue: Boolean,
) {
    val configClass = toStringFingerprint.classDef.type
    val isEnabledFingerprint = Fingerprint(
        definingClass = configClass,
        returnType = "Z",
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
        parameters = listOf(),
    )
    val value = if (returnValue) 1 else 0
    isEnabledFingerprint.method.addInstructions(
        0,
        """
            const/4 v0, 0x$value
            return v0
        """,
    )
}

// ── Disable Pre-Roll Ads ──

@Suppress("unused")
val disablePreRollAdsPatch = bytecodePatch(
    name = "Disable pre-roll ads",
    description = "Disables pre-roll video ads for subscribers.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(SvodPreRollConfigToStringFingerprint, false)
    }
}

// ── Enable PiP ──

@Suppress("unused")
val enablePipPatch = bytecodePatch(
    name = "Enable PiP",
    description = "Enables Picture-in-Picture mode on all devices.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(PipConfigToStringFingerprint, true)
    }
}

// ── Disable In-App Review Popups ──

@Suppress("unused")
val disableInAppReviewPatch = bytecodePatch(
    name = "Disable review popups",
    description = "Stops the \"Rate us\" popup from appearing.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(InAppReviewConfigToStringFingerprint, false)
    }
}

// ── Disable In-App Update Nags ──

@Suppress("unused")
val disableInAppUpdatesPatch = bytecodePatch(
    name = "Disable update nags",
    description = "Stops forced in-app update prompts.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(InAppUpdatesConfigToStringFingerprint, false)
    }
}

// ── Enable Chromecast Skip Events ──

@Suppress("unused")
val enableChromecastSkipPatch = bytecodePatch(
    name = "Enable Chromecast skip",
    description = "Enables skip intro/outro buttons during Chromecast.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(ChromecastSkipEventsConfigToStringFingerprint, true)
    }
}

// ── Enable Content Labels ──

@Suppress("unused")
val enableContentLabelsPatch = bytecodePatch(
    name = "Enable content labels",
    description = "Shows content warning labels on episodes.",
    default = false,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(ContentLabelsConfigToStringFingerprint, true)
    }
}

// ── Enable Share Redesign ──

@Suppress("unused")
val enableShareRedesignPatch = bytecodePatch(
    name = "Enable share redesign",
    description = "Enables the newer share UI.",
    default = false,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(ShareRedesignConfigToStringFingerprint, true)
    }
}

// ── Enable Manga ──

@Suppress("unused")
val enableMangaPatch = bytecodePatch(
    name = "Enable manga",
    description = "Enables the manga reader section (may be region-locked).",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(MangaConfigToStringFingerprint, true)
    }
}

// ── Enable Home Feed Hero Carousel ──

@Suppress("unused")
val enableHeroCarouselPatch = bytecodePatch(
    name = "Enable hero carousel",
    description = "Enables the hero carousel on the home feed.",
    default = false,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        forceConfigFlag(HomeFeedHeroCarouselConfigToStringFingerprint, true)
    }
}

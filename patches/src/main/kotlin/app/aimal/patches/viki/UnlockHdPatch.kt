package app.aimal.patches.viki

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

/**
 * Unlocks Viki's "High" video quality, which the app otherwise hides behind
 * the `hd` entitlement.
 *
 * Viki's quality enum has exactly two entries, "Standard" and "High". Whether
 * "High" can be picked - in the video-quality setting and when the player
 * chooses a track for a new stream - is decided by a single one-line
 * entitlement check, `SessionManagerImpl.hasHdFeature()`, which this forces to
 * true.
 *
 * A caveat worth stating plainly: this lifts the *client-side* cap. The
 * resolutions actually offered still come from the manifest the server returns
 * for the account and the title, so this cannot conjure a 1080p rendition that
 * the stream does not contain. What it does is stop the app from refusing to
 * ask for, or select, the higher-quality tracks when they are there.
 *
 * Kept separate from [removeAdsPatch] so removing ads does not silently change
 * playback quality, and vice versa.
 */
@Suppress("unused")
val unlockHdPatch = bytecodePatch(
    name = "Unlock HD quality",
    description = "Enables the \"High\" (1080p) video quality option.",
    default = true,
) {
    compatibleWith(VIKI)

    execute {
        val hasHd = SessionManagerHasHdFingerprint.methodOrNull
            ?: throw PatchException(
                "Could not find the HD entitlement check. Viki has probably " +
                    "reworked com.viki.library.beans.Features."
            )

        hasHd.addInstructions(
            0,
            """
                const/4 v0, 0x1
                return v0
            """,
        )
    }
}

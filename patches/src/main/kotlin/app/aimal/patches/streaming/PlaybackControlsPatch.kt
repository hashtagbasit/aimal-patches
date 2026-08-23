package app.aimal.patches.streaming

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private const val CONTROLS = "$EXTENSION_STREAMING/Controls;"
private const val PLAYER_BRIDGE = "$EXTENSION_STREAMING/PlayerBridge;"

/**
 * Adds a floating panel with playback speed (1x / 1.25x / 1.5x / 2x) and an
 * aspect-ratio toggle (fit, stretch, crop and two fixed zoom steps) to HBO Max
 * and Disney+.
 *
 * Only two things are injected, both one instruction long:
 *
 *  1. The application context, so the extension can attach its panel.
 *  2. The player instance, captured as each ExoPlayer is constructed.
 *
 * Everything else happens at runtime in the extension: the video surface is
 * found by walking the view tree, the speed is set through media3's own
 * setPlaybackSpeed, and the picture is reshaped through media3's
 * AspectRatioFrameLayout.setResizeMode - all of which survive the apps'
 * obfuscation. That is why this needs no per-screen, per-layout or
 * per-version fingerprints, and why one patch covers two apps that share
 * nothing but their media stack.
 *
 * Nothing here touches DRM, licensing, entitlement or ad code.
 */
@Suppress("unused")
val playbackControlsPatch = bytecodePatch(
    name = "Playback speed and aspect ratio",
    description = "Adds a floating panel to change playback speed and stretch, crop or zoom the picture.",
    default = true,
) {
    compatibleWith(HBO_MAX, DISNEY_PLUS)

    extendWith("extensions/extension.mpe")

    execute {
        // 1. Hand the extension a context.
        //
        // HBO Max declares its own Application.onCreate, which is the earliest
        // and safest place. Disney+ does not - its Application inherits
        // onCreate from an obfuscated base class - so its main Activity is
        // used instead. Exactly one of these matches, depending on which app
        // is being patched.
        val contextHook = HboMaxApplicationFingerprint.methodOrNull
            ?: DisneyPlusMainActivityFingerprint.methodOrNull
            ?: throw PatchException(
                "Could not find a context hook. This patch targets HBO Max " +
                    "(com.wbd.stream) and Disney+ (com.disney.disneyplus)."
            )

        // Range form because a large method can push p0 past the 4-bit
        // register limit of a plain invoke-static.
        contextHook.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $CONTROLS->setContext(Landroid/content/Context;)V"
        )

        // 2. Capture every ExoPlayer as it is built.
        val playerConstructors = ExoPlayerConstructorFingerprint.matchAllOrNull()
            ?: throw PatchException(
                "No androidx.media3 ExoPlayer implementation found. The app " +
                    "has probably changed its media stack."
            )

        playerConstructors.forEach { match ->
            val method = match.method

            // Must not be instruction 0: a constructor has to reach its super
            // constructor before `this` is usable, and passing a half-built
            // object out fails dex verification. Inserting before the trailing
            // return-void puts the call after super() and after every field
            // assignment.
            val returnIndex = method.implementation!!.instructions.count() - 1

            method.addInstruction(
                returnIndex,
                "invoke-static/range { p0 .. p0 }, $PLAYER_BRIDGE->onPlayerCreated(Ljava/lang/Object;)V"
            )
        }
    }
}

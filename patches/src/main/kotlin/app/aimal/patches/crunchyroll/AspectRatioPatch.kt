package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val aspectRatioPatch = bytecodePatch(
    name = "Aspect ratio control",
    description = "Adds an aspect ratio toggle button to the player (Fit/Fill/Crop/16:9).",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    extendWith("extensions/extension.mpe")

    execute {
        // Use S3() as hook - fires when player controls visibility changes
        // Only 5 registers, safe for injection
        // p0 = this (InternalPlayerViewLayout)
        val method = InternalPlayerViewLayoutClassFingerprint.method
        val lastIndex = method.implementation!!.instructions.toList().size - 1

        method.addInstructions(
            lastIndex,
            """
                invoke-static {p0}, Lapp/aimal/extension/crunchyroll/AspectRatioHelper;->addAspectRatioButton(Landroid/view/View;)V
            """,
        )
    }
}

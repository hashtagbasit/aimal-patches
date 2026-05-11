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
        val method = PlayerViewOnAttachedFingerprint.method
        val lastIndex = method.implementation!!.instructions.size - 1

        method.addInstructions(
            lastIndex,
            """
                invoke-static {p0}, Lapp/aimal/extension/crunchyroll/AspectRatioHelper;->addAspectRatioButton(Landroid/view/View;)V
            """,
        )
    }
}

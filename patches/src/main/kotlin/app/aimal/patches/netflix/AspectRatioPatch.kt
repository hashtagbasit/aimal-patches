package app.aimal.patches.netflix

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val aspectRatioPatch = bytecodePatch(
    name = "Aspect ratio control",
    description = "Adds an aspect ratio toggle button to the Netflix player (Fit/Fill/Zoom/Stretch).",
    default = true,
) {
    compatibleWith(NETFLIX)

    extendWith("extensions/extension.mpe")

    execute {
        val method = PlaylistVideoViewConstructorFingerprint.method
        val lastIndex = method.implementation!!.instructions.toList().size - 1

        method.addInstructions(
            lastIndex,
            """
                invoke-static {p0}, Lapp/aimal/extension/netflix/NetflixAspectRatioHelper;->addAspectRatioButton(Landroid/view/View;)V
            """,
        )
    }
}

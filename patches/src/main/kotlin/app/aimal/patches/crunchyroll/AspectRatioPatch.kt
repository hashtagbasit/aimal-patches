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
        val method = InternalPlayerViewLayoutClassFingerprint.method
        val instructions = method.implementation!!.instructions.toList()
        val lastIndex = instructions.size - 1

        // S3(boolean) — p0=this, p1=boolean (true=show, false=hide)
        // At the start: add button to player if not added yet
        // Pass p1 to setButtonVisible so button follows controls visibility
        method.addInstructions(
            0,
            """
                invoke-static {p0}, Lapp/aimal/extension/crunchyroll/AspectRatioHelper;->addAspectRatioButton(Landroid/view/View;)V
            """,
        )

        method.addInstructions(
            lastIndex + 1,
            """
                invoke-static {p1}, Lapp/aimal/extension/crunchyroll/AspectRatioHelper;->setButtonVisible(Z)V
            """,
        )
    }
}

package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val addFastSpeedsPatch = bytecodePatch(
    name = "Add fast playback speeds",
    description = "Adds 1.25x, 1.5x, 1.75x, and 2.0x playback speed options.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    extendWith("extensions/extension.mpe")

    execute {
        val method = PlayerSettingsViewModelConstructorFingerprint.method
        val instructions = method.implementation!!.instructions.toList()
        val lastIndex = instructions.size - 1

        method.addInstructions(
            lastIndex,
            """
                invoke-static {p0}, Lapp/aimal/extension/crunchyroll/SpeedHelper;->replaceSpeedList(Ljava/lang/Object;)V
            """,
        )
    }
}

package app.aimal.patches.crunchyroll

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val addFastSpeedsPatch = bytecodePatch(
    name = "Add fast playback speeds",
    description = "Adds 1.25x, 1.5x, 1.75x, and 2.0x playback speed options.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    extendWith("extensions/extension.mpe")

    execute {
        val viewModelClass = PlayerSettingsViewModelConstructorFingerprint.classDef.type

        val speedListMethodFingerprint = Fingerprint(
            definingClass = viewModelClass,
            returnType = "Landroidx/lifecycle/L;",
            accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
            parameters = listOf(),
        )

        speedListMethodFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, Lapp/aimal/extension/crunchyroll/SpeedHelper;->getSpeedLiveData()Landroidx/lifecycle/L;
                move-result-object v0
                return-object v0
            """,
        )
    }
}

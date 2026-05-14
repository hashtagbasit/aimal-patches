package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

@Suppress("unused")
val geoSpoofPatch = bytecodePatch(
    name = "Spoof country to US",
    description = "Sets the app region to US for a larger content library. VPN still needed for playback.",
    default = false,
) {
    compatibleWith(CRUNCHYROLL)

    extendWith("extensions/extension.mpe")

    execute {
        CountryCodeGetterFingerprint.method.addInstructions(
            0,
            """
                const-string v0, "US"
                return-object v0
            """,
        )

        CountryCodeUpdateFingerprint.method.addInstructions(
            0,
            """
                const-string p1, "US"
            """,
        )

        spoofLocaleInterceptor(LocaleInterceptorFingerprint)
        spoofLocaleInterceptor(LocalePathInterceptorFingerprint)
    }
}

private fun spoofLocaleInterceptor(fingerprint: app.morphe.patcher.Fingerprint) {
    val method = fingerprint.method
    val instructions = method.implementation!!.instructions.toList()

    val tagIndex = instructions.indexOfFirst {
        it.opcode == Opcode.INVOKE_VIRTUAL &&
            (it as? ReferenceInstruction)?.reference?.toString()?.contains("toLanguageTag") == true
    }

    if (tagIndex == -1) return

    val moveResult = instructions[tagIndex + 1]
    val register = (moveResult as OneRegisterInstruction).registerA

    method.addInstructions(
        tagIndex + 2,
        """
            const-string v$register, "en-US"
        """,
    )
}

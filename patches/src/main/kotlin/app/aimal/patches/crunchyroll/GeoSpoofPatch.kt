package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

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
    }
}

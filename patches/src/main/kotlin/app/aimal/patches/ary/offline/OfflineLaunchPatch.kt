package app.aimal.patches.ary.offline

import app.aimal.patches.ary.shared.Constants.COMPATIBILITY_ARY
import app.aimal.patches.ary.shared.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private const val OFFLINE_MODE = "$EXTENSION_PACKAGE/downloads/OfflineMode;"

/**
 * The app's shared connectivity helper.
 *
 * aryzap_splash calls this three times during startup and sends the user to the
 * NoInternet screen when it returns false, so with no network the Downloads tab
 * is unreachable. MainPage and LiveStreams use the same helper.
 *
 * Not obfuscated, so matching on the exact class and method name is stable.
 */
object NetworkUtilIsConnectedFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Utils/NetworkUtil;",
    name = "isConnected",
    returnType = "Z",
    parameters = listOf("Landroid/content/Context;")
)

/**
 * Lets the app start offline so downloaded episodes stay watchable.
 *
 * The whole method body is replaced rather than injected into: the original
 * builds a NetworkInfo and branches on it, and the extension reproduces that
 * logic exactly, only also returning true when the device is offline but
 * completed downloads exist.
 */
@Suppress("unused")
val offlineLaunchPatch = bytecodePatch(
    name = "Allow offline launch",
    description = "Lets ARY Plus open without a connection when downloaded episodes are available, " +
        "instead of stopping at the No Internet screen.",
    default = true
) {
    compatibleWith(COMPATIBILITY_ARY)

    extendWith("extensions/extension.mpe")

    execute {
        NetworkUtilIsConnectedFingerprint.matchOrNull()?.method?.addInstructions(
            0,
            """
                invoke-static { p0 }, $OFFLINE_MODE->isConnected(Landroid/content/Context;)Z
                move-result v0
                return v0
            """
        )
    }
}

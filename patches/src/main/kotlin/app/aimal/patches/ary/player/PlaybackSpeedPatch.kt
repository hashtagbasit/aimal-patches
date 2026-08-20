package app.aimal.patches.ary.player

import app.aimal.patches.ary.shared.Constants.COMPATIBILITY_ARY
import app.aimal.patches.ary.shared.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.removeInstructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction

private const val EXTENSION_CLASS = "$EXTENSION_PACKAGE/PlayerSpeed;"

/**
 * Adds a 1.25x option to the player's speed selector.
 *
 * The stock table is {.25, .50, 1.0 Normal, 1.5, 2.0, 4.0} - so 1.5x already
 * exists and only 1.25x is missing.
 *
 * Rather than rewriting the two array literals in <init> (which would mean
 * rebuilding a filled-new-array sequence), this redirects the two places the
 * arrays are *read*:
 *
 *   showSettingsOverlay()          Arrays.asList(speedOptions) -> PlayerSpeed.labels()
 *   lambda$showSettingsOverlay$NN  speedValues[i]             -> PlayerSpeed.value(i)
 *
 * Both players keep `selectedSpeedIndex = 2` as their hardcoded default and
 * never re-derive it, so PlayerSpeed keeps 1.0x at index 2 and inserts 1.25x at
 * index 3. The default speed is therefore unchanged.
 */
@Suppress("unused")
val playbackSpeedPatch = bytecodePatch(
    name = "Add 1.25x playback speed",
    description = "Adds a 1.25x option to the speed selector in both the standard and CDN video players.",
    default = true
) {
    compatibleWith(COMPATIBILITY_ARY)

    extendWith("extensions/extension.mpe")

    execute {
        // speedValues[i] -> PlayerSpeed.value(i)
        listOf(
            PlayerActivitySpeedValueFingerprint,
            CdnPlayerSpeedValueFingerprint
        ).forEach { fingerprint ->
            // matchOrNull so a table that moved in one player does not abort the
            // whole patch and take the other player's fix down with it.
            val match = fingerprint.matchOrNull() ?: return@forEach
            val method = match.method
            val fieldIndex = match.instructionMatches[0].index
            val agetIndex = match.instructionMatches[1].index

            // aget vDest, vArray, vIndex
            val aget = method.getInstruction<ThreeRegisterInstruction>(agetIndex)
            val destinationRegister = aget.registerA
            val indexRegister = aget.registerC

            // Drop the field read and the array access together.
            method.removeInstructions(fieldIndex, agetIndex - fieldIndex + 1)
            method.addInstructions(
                fieldIndex,
                """
                    invoke-static { v$indexRegister }, $EXTENSION_CLASS->value(I)F
                    move-result v$destinationRegister
                """
            )
        }

        // Arrays.asList(speedOptions) -> PlayerSpeed.labels()
        listOf(
            PlayerActivitySpeedLabelsFingerprint,
            CdnPlayerSpeedLabelsFingerprint
        ).forEach { fingerprint ->
            val match = fingerprint.matchOrNull() ?: return@forEach
            val method = match.method
            val fieldIndex = match.instructionMatches[0].index
            val asListIndex = match.instructionMatches[1].index

            // The existing `move-result-object` after the call is left in place
            // and now receives the extension's list instead.
            method.removeInstructions(fieldIndex, asListIndex - fieldIndex + 1)
            method.addInstructions(
                fieldIndex,
                "invoke-static { }, $EXTENSION_CLASS->labels()Ljava/util/List;"
            )
        }
    }
}

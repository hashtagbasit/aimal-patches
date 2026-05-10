package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

@Suppress("unused")
val addFastSpeedsPatch = bytecodePatch(
    name = "Add fast playback speeds",
    description = "Adds 1.25x, 1.5x, 1.75x, and 2.0x playback speed options.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)

    execute {
        val method = PlayerSettingsViewModelConstructorFingerprint.method
        val instructions = method.implementation!!.instructions.toList()

        var newArrayIndex = -1
        var arraySizeRegister = -1
        var arrayRegister = -1

        for (i in 1 until instructions.size) {
            val inst = instructions[i]
            if (inst.opcode != Opcode.NEW_ARRAY) continue

            val prev = instructions[i - 1]
            if (prev.opcode != Opcode.CONST_4 && prev.opcode != Opcode.CONST_16) continue

            val size = (prev as NarrowLiteralInstruction).narrowLiteral
            if (size != 3) continue

            val lookAhead = instructions.subList(i, minOf(i + 30, instructions.size))
            val has05f = lookAhead.any { la ->
                la is WideLiteralInstruction && la.wideLiteral.toInt() == 0x3F000000
            }
            if (!has05f) continue

            newArrayIndex = i
            arraySizeRegister = (prev as OneRegisterInstruction).registerA
            arrayRegister = (inst as OneRegisterInstruction).registerA
            break
        }

        if (newArrayIndex == -1) return@execute

        method.replaceInstruction(
            newArrayIndex - 1,
            "const/4 v$arraySizeRegister, 0x7",
        )

        var aputCount = 0
        var lastAputIndex = newArrayIndex
        for (i in newArrayIndex until minOf(newArrayIndex + 40, instructions.size)) {
            if (instructions[i].opcode == Opcode.APUT_OBJECT) {
                aputCount++
                lastAputIndex = i
                if (aputCount == 3) break
            }
        }

        method.addInstructions(
            lastAputIndex + 1,
            """
                const/4 v0, 0x3
                const v1, 0x3FA00000
                invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
                move-result-object v1
                aput-object v1, v$arrayRegister, v0

                const/4 v0, 0x4
                const v1, 0x3FC00000
                invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
                move-result-object v1
                aput-object v1, v$arrayRegister, v0

                const/4 v0, 0x5
                const v1, 0x3FE00000
                invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
                move-result-object v1
                aput-object v1, v$arrayRegister, v0

                const/4 v0, 0x6
                const v1, 0x40000000
                invoke-static {v1}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
                move-result-object v1
                aput-object v1, v$arrayRegister, v0
            """,
        )
    }
}

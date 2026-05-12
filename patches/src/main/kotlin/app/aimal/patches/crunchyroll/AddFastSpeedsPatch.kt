package app.aimal.patches.crunchyroll

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

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

        // Find FILLED_NEW_ARRAY for Float[] with size 3
        // Pattern: const r3=1.0f, Float.valueOf, const r4=0.75f, Float.valueOf,
        //          const r5=0.5f, Float.valueOf, filled-new-array {r3,r4,r5}, [Ljava/lang/Float;
        var filledArrayIndex = -1

        for (i in instructions.indices) {
            val inst = instructions[i]
            if (inst.opcode != Opcode.FILLED_NEW_ARRAY) continue

            // Verify it's a Float array
            val ref = (inst as ReferenceInstruction).reference
            if (ref.toString() != "[Ljava/lang/Float;") continue

            // Verify preceded by 0.5f constant nearby
            val lookBehind = instructions.subList(maxOf(0, i - 10), i)
            val has05f = lookBehind.any { la ->
                la is WideLiteralInstruction && la.wideLiteral.toInt() == 0x3F000000
            }
            if (!has05f) continue

            filledArrayIndex = i
            break
        }

        if (filledArrayIndex == -1) return@execute

        // Replace FILLED_NEW_ARRAY {r3,r4,r5}, [Float; (3 elements)
        // with our 7-element version using extension helper
        // Strategy: after the filled-new-array + move-result-object + ku.n.t() + L.<init>
        // find the iput-object that stores f17449l and inject after it

        // Find the iput-object that stores the speed LiveData field
        // (comes after: filled-new-array → move-result-object → invoke-static ku.n.t
        //  → move-result-object → new-instance L → invoke-direct L.<init>)
        var iputIndex = -1
        for (i in filledArrayIndex until minOf(filledArrayIndex + 15, instructions.size)) {
            if (instructions[i].opcode == Opcode.IPUT_OBJECT) {
                iputIndex = i
                break
            }
        }

        if (iputIndex == -1) return@execute

        // Get the register that holds the ViewModel (p0 or first param after iput)
        val iputInst = instructions[iputIndex] as com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
        val objectReg = iputInst.registerB // the object the field belongs to

        // Inject after the iput-object: replace the 3-element list with 7 elements
        method.addInstructions(
            iputIndex + 1,
            """
                iget-object v0, v$objectReg, ${method.definingClass}->${iputInst.let {
                    (it as ReferenceInstruction).reference.toString().split("->")[1]
                }}
                const/4 v1, 0x2
                const v2, 0x3FA00000
                invoke-static {v2}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;
                move-result-object v2
                invoke-virtual {v0, v2}, Landroidx/lifecycle/L;->postValue(Ljava/lang/Object;)V
            """,
        )
    }
}

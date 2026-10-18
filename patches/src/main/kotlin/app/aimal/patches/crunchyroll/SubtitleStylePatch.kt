package app.aimal.patches.crunchyroll

import app.aimal.patches.subtitles.hookCaptionDraw
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val STYLER = "Lapp/aimal/extension/crunchyroll/SubtitleStyler;"

@Suppress("unused")
val subtitleStylePatch = bytecodePatch(
    name = "Subtitle styling",
    description = "Adds all Noir subtitle style options, a live example and immediate updates during playback.",
    default = true,
) {
    compatibleWith(CRUNCHYROLL)
    dependsOn(aspectRatioPatch)
    extendWith("extensions/extension.mpe")

    execute {
        val load = SubtitlesLoadTrackFingerprint.method
        val renderer = mutableClassDefBy(load.definingClass)
        load.setName("aimalOriginalLoadTrack")
        val loadWrapper = MutableMethod(ImmutableMethod(renderer.type, "loadTrack", load.parameters,
            load.returnType, load.accessFlags, load.annotations, load.hiddenApiRestrictions,
            ImmutableMethodImplementation(4, emptyList(), emptyList(), emptyList())))
        loadWrapper.addInstructions("""
            invoke-virtual/range { p0 .. p1 }, ${renderer.type}->aimalOriginalLoadTrack(Ljava/lang/String;)J
            move-result-wide v0
            invoke-static { p0, p1, v0, v1 }, $STYLER->onLoaded(Ljava/lang/Object;Ljava/lang/String;J)V
            return-wide v0
        """)
        renderer.methods.add(loadWrapper)
        // 3.117.0 declares renderFrame as JNI: keep its name/signature untouched.
        // Capture the exact receiver, track pointer and milliseconds at its callers.
        val render = renderer.methods.singleOrNull {
            it.name == "renderFrame" && it.parameterTypes == listOf("J", "J") &&
                it.returnType == "Lcom/crunchyroll/subtitles/data/AssFrames;" &&
                !AccessFlags.STATIC.isSet(it.accessFlags)
        } ?: throw PatchException("Cannot identify Crunchyroll's ASS render clock signature.")
        val owners = setOf(renderer.type, "Lcom/crunchyroll/subtitles/SubtitlesRenderer;")
        val callers = mutableListOf<String>()
        classDefForEach { clazz ->
            if (clazz.methods.any { method -> method.implementation?.instructions?.any { instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ref != null && ref.definingClass in owners && ref.name == render.name &&
                    ref.parameterTypes == render.parameterTypes && ref.returnType == render.returnType
            } == true }) callers.add(clazz.type)
        }
        var hookedCalls = 0
        callers.forEach { type ->
            mutableClassDefBy(type).methods.forEach methodLoop@ { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@methodLoop
                instructions.withIndex().reversed().forEach instructionLoop@ { (index, instruction) ->
                    val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    if (ref == null || ref.definingClass !in owners || ref.name != render.name ||
                        ref.parameterTypes != render.parameterTypes || ref.returnType != render.returnType) return@instructionLoop
                    val hook = when (instruction) {
                        is RegisterRangeInstruction -> {
                            check(instruction.registerCount == 5)
                            "invoke-static/range { v${instruction.startRegister} .. v${instruction.startRegister + 4} }"
                        }
                        is FiveRegisterInstruction -> {
                            check(instruction.registerCount == 5)
                            "invoke-static { v${instruction.registerC}, v${instruction.registerD}, v${instruction.registerE}, v${instruction.registerF}, v${instruction.registerG} }"
                        }
                        else -> throw PatchException("Unsupported ASS clock invocation: ${instruction.opcode}")
                    }
                    method.addInstruction(index, "$hook, $STYLER->onRender(Ljava/lang/Object;JJ)V")
                    hookedCalls++
                }
            }
        }
        if (hookedCalls == 0) throw PatchException("Cannot find callers of Crunchyroll's ASS render clock.")
        renderer.methods.filter { it.name in setOf("release", "releaseTrack", "unloadTrack", "clearTrack", "close", "destroy") &&
            it.implementation != null && !AccessFlags.STATIC.isSet(it.accessFlags) }.forEach {
            if (it.parameterTypes == listOf("J")) it.addInstruction(0,
                "invoke-static/range { p0 .. p2 }, $STYLER->clearTrack(Ljava/lang/Object;J)V")
            else it.addInstruction(0, "invoke-static/range { p0 .. p0 }, $STYLER->clear(Ljava/lang/Object;)V")
        }
        val captionView = mutableClassDefByOrNull { clazz ->
            clazz.fields.any { it.type.contains("AssFrame") } &&
                clazz.methods.any { it.name == "onDraw" && it.parameterTypes == listOf("Landroid/graphics/Canvas;") }
        } ?: throw PatchException("Cannot find Crunchyroll's ASS caption View. This version needs a new draw fingerprint.")
        captionView.hookCaptionDraw("onDraw", STYLER)
    }
}

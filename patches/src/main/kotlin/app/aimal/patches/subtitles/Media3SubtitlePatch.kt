package app.aimal.patches.subtitles

import app.aimal.patches.streaming.HBO_MAX
import app.aimal.patches.streaming.DISNEY_PLUS
import app.aimal.patches.viki.VIKI
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private const val CAPTIONS = "Lapp/aimal/extension/subtitles/Media3Captions;"

val media3SubtitlePatch = bytecodePatch {
    execute {
        val subtitleView = mutableClassDefByOrNull { clazz ->
            clazz.methods.any { it.name == "setApplyEmbeddedStyles" && it.parameterTypes == listOf("Z") } &&
                clazz.methods.any { it.name == "setCues" && it.parameterTypes == listOf("Ljava/util/List;") }
        } ?: throw PatchException("Cannot find media3 SubtitleView. The app may have changed its caption renderer.")
        val setCues = subtitleView.methods.single {
            it.name == "setCues" && it.parameterTypes == listOf("Ljava/util/List;")
        }
        setCues.addInstruction(0,
            "invoke-static/range { p0 .. p1 }, $CAPTIONS->onCues(Landroid/view/View;Ljava/util/List;)V")
        subtitleView.hookCaptionDraw("dispatchDraw", CAPTIONS)
    }
}

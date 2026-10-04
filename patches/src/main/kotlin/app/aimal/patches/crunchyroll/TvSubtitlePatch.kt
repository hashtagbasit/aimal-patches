package app.aimal.patches.crunchyroll

import app.aimal.patches.subtitles.media3SubtitlePatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.Opcode

val crunchyrollTvSubtitlesPatch = bytecodePatch(
    name = "Subtitle styling (Android TV)",
    description = "All Noir subtitle options with live preview and hot reload. Hold OK or press Menu during playback to open the editor; includes Fit/Stretch.",
    default = false,
) {
    compatibleWith(CRUNCHYROLL_TV)
    dependsOn(media3SubtitlePatch)
    extendWith("extensions/extension.mpe")
    execute {
        val routing = "Lapp/aimal/extension/crunchyroll/TvPlaybackSubtitles;"
        val cms = mutableClassDefByOrNull { it.type == "Lcom/crunchyroll/cms/component/CMSComponent;" }
            ?: throw PatchException("Cannot find TV stream selection component.")
        val constructors = cms.methods.filter { it.name == "<init>" && it.implementation != null }
        constructors.forEach { method ->
            method.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
                .map { it.index }.reversed().forEach { index ->
                    method.addInstruction(index, "invoke-static/range { p0 .. p0 }, $routing->register(Ljava/lang/Object;)V")
                }
        }
        val event = mutableClassDefByOrNull {
            it.type == "Lcom/crunchyroll/player/eventbus/events/Topic\$CMSEvent\$VideoUrlReady;"
        } ?: throw PatchException("Cannot find TV video URL event.")
        // The ordinary constructor runs after default arguments are resolved, before publication.
        val eventConstructor = event.methods.single { it.name == "<init>" && it.parameterTypes.size == 18 }
        eventConstructor.implementation!!.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }
            .map { it.index }.reversed().forEach { index ->
                eventConstructor.addInstruction(index, "invoke-static/range { p0 .. p0 }, $routing->onVideo(Ljava/lang/Object;)V")
            }
        val builder = mutableClassDefByOrNull { it.type == "Landroidx/media3/common/MediaItem\$Builder;" }
            ?: throw PatchException("Cannot find TV MediaItem builder.")
        val build = builder.methods.single { it.parameterTypes.isEmpty() && it.returnType == "Landroidx/media3/common/MediaItem;" }
        build.addInstruction(0, "invoke-static/range { p0 .. p0 }, $routing->onBuild(Ljava/lang/Object;)V")
        val activity = mutableClassDefByOrNull {
            it.type == "Lcom/crunchyroll/crunchyroid/player/ui/PlayerActivity;"
        } ?: throw PatchException("Cannot find Crunchyroll Android TV PlayerActivity.")
        val original = activity.methods.singleOrNull {
            it.name == "dispatchKeyEvent" && it.parameterTypes == listOf("Landroid/view/KeyEvent;") && it.returnType == "Z"
        }
        original?.setName("aimalOriginalDispatchKeyEvent")
        val fallback = if (original == null) {
            "invoke-super { p0, p1 }, ${activity.superclass}->dispatchKeyEvent(Landroid/view/KeyEvent;)Z"
        } else {
            "invoke-virtual { p0, p1 }, ${activity.type}->aimalOriginalDispatchKeyEvent(Landroid/view/KeyEvent;)Z"
        }
        val wrapper = MutableMethod(ImmutableMethod(activity.type, "dispatchKeyEvent",
            listOf(ImmutableMethodParameter("Landroid/view/KeyEvent;", emptySet(), null)),
            "Z", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
            ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList())))
        wrapper.addInstructions("""
            invoke-static { p0, p1 }, Lapp/aimal/extension/crunchyroll/TvSubtitleHelper;->onKey(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
            move-result v0
            if-eqz v0, :native
            return v0
            :native
            $fallback
            move-result v0
            return v0
        """)
        activity.methods.add(wrapper)
    }
}

package app.aimal.patches.subtitles

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

/** A wrapper keeps every original register, branch, try block and super call intact. */
fun MutableClass.hookCaptionDraw(methodName: String, extension: String) {
    val original = methods.singleOrNull {
        it.name == methodName && it.parameterTypes == listOf("Landroid/graphics/Canvas;") && it.returnType == "V"
    }
    val originalName = "aimalOriginal" + methodName.replaceFirstChar { it.uppercaseChar() }
    val flags = original?.accessFlags ?: AccessFlags.PROTECTED.value
    original?.setName(originalName)
    val wrapper = MutableMethod(
        ImmutableMethod(type, methodName,
            listOf(ImmutableMethodParameter("Landroid/graphics/Canvas;", emptySet(), null)),
            "V", flags, emptySet(), emptySet(),
            ImmutableMethodImplementation(3, emptyList(), emptyList(), emptyList()))
    )
    val fallback = if (original != null) {
        "invoke-virtual/range { p0 .. p1 }, $type->$originalName(Landroid/graphics/Canvas;)V"
    } else {
        "invoke-super/range { p0 .. p1 }, $superclass->$methodName(Landroid/graphics/Canvas;)V"
    }
    wrapper.addInstructions(
        """
            invoke-static { p0, p1 }, $extension->draw(Landroid/view/View;Landroid/graphics/Canvas;)Z
            move-result v0
            if-eqz v0, :original
            return-void
            :original
            $fallback
            return-void
        """
    )
    methods.add(wrapper)
}

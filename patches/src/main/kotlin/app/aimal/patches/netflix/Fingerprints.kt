package app.aimal.patches.netflix

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string

object PlaylistVideoViewConstructorFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
    filters = listOf(
        string("notifyUiStateChanged"),
    ),
)

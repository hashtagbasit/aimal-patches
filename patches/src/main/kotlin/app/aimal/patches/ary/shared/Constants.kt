package app.aimal.patches.ary.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    const val EXTENSION_PACKAGE = "Lapp/aimal/extension/ary"

    /**
     * ARY Plus. Developed and verified against 3.6.6 (versionCode 166).
     *
     * The app's own code (com.material.components.aryzap) is not obfuscated, so
     * fingerprints here match on real class and field names. That makes them
     * comparatively stable across versions, hence the experimental "any version"
     * target alongside the confirmed one.
     */
    val COMPATIBILITY_ARY = Compatibility(
        name = "ARY Plus",
        packageName = "com.release.arylive",
        apkFileType = ApkFileType.APK,
        appIconColor = 0xD32027,
        targets = listOf(
            AppTarget(version = "3.6.6"),
            AppTarget(version = null, isExperimental = true)
        )
    )
}

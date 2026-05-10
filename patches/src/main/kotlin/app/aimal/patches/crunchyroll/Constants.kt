package app.aimal.patches.crunchyroll

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

val CRUNCHYROLL = Compatibility(
    name = "Crunchyroll",
    packageName = "com.crunchyroll.crunchyroid",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xF47521,
    targets = listOf(
        AppTarget(version = null),
    )
)

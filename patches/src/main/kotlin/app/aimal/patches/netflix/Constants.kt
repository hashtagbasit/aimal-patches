package app.aimal.patches.netflix

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

val NETFLIX = Compatibility(
    name = "Netflix",
    packageName = "com.netflix.mediaclient",
    apkFileType = ApkFileType.APK,
    appIconColor = 0xE50914,
    targets = listOf(
        AppTarget(version = null),
    ),
)

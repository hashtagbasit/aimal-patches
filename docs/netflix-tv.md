# Netflix Android TV 8.3.11

This separate APK integration connects Netflix's text subtitles to the same
subtitle editor used by Crunchyroll. It includes the 17 shared style controls,
font import, Default and Anime Classic presets, saved styles, and a live preview.
Hold **OK / Enter** for one second and release it, or press **Menu**, to open the
editor. A short OK press retains Netflix's action. Select a preset for a complete
style, or adjust individual controls to keep the rest of your current settings.

Use the [Netflix TV ARM32 APK in the fork's v1.4.0 release](https://github.com/neura-neura/aimal-patches/releases/download/v1.4.0/Netflix-TV-8.3.11-Aimal-1.4.0-ARM32.apk).
It is already signed and does not require Morphe on the TV. Netflix support is
provided by the scripts in `tools/netflix`, not by the published `.mpp` bundle.

## Compatibility and installation

The integration targets **Netflix TV 8.3.11, build 12041, ARM32**. It was tested
on a Xiaomi Mi Box 4 running Android 9. Other Netflix versions and architectures
are unsupported; the build scripts reject a different native library. The
13.2.1 APK did not run reliably on that test device.

The APK uses the independent package `app.aimal.netflix.compat` and appears as
**Netflix Aimal compatibility test** in the launcher. It can coexist with the
factory Netflix app. Sign in to this copy once; the factory app's session is not
transferred. Later updates signed with the same key can retain this copy's data.

Transfer the APK to the TV and open it in a file manager, or use an authorized
ADB connection:

```powershell
adb -s YOUR_TV_SERIAL install -r Netflix-TV-8.3.11-Aimal-1.4.0-ARM32.apk
```

Keep the original Netflix app available. This copy still requires your Netflix
account and the subscription needed for the selected content.

## Subtitle behavior and validation

The integration forwards subtitle nodes only after Netflix renders them. Text
used to measure subtitle layout is excluded. Custom drawing replaces the native
subtitle text while enabled, and retains Netflix's timing and clear events.
The shared editor saves changes and invalidates the current overlay immediately.
Individual subtitle positioning and rich-text emphasis are flattened into plain
text by the custom renderer.

Authenticated episode playback with video, audio, and Spanish subtitles was
confirmed on the Mi Box. The user confirmed correct operation after installation;
device diagnostics independently recorded active subtitle nodes using custom
rendering. The editor was also opened through the remote input hook.

The editor includes Fit/Stretch through Netflix's video surface API. Its visual
effect, style changes while paused, long-session behavior, and episode transitions
have not been separately verified for Netflix. Crunchyroll's broader fixture
coverage does not establish those results for this integration.

## Build from your own APK on Windows

Prerequisites: Python 3, JDK 21, Apktool 3.0.3, Android SDK platform 35,
build-tools 35.0.1, and NDK 27.0.12077973. Keep the signing key used for updates
outside the repository. Obtain its public certificate SHA-256 with `keytool`
or `apksigner verify --print-certs` on a previous APK signed with that key.

```powershell
java -jar C:/Tools/apktool/apktool.jar d Netflix-TV-8.3.11.apk -o out/netflix/source
python tools/netflix/prepare.py --source out/netflix/source --target out/netflix/project --signer-sha256 YOUR_CERTIFICATE_SHA256
python tools/netflix/build_extension.py --project out/netflix/project --sdk C:/Android/Sdk --apktool C:/Tools/apktool/apktool.jar --out out/netflix/build
C:/Android/Sdk/build-tools/35.0.1/zipalign.exe -P 16 -f 4 out/netflix/build/netflix-unsigned.apk out/netflix/build/netflix-aligned.apk
C:/Android/Sdk/build-tools/35.0.1/apksigner.bat sign --ks C:/Private/update.keystore --out out/netflix/build/Netflix-Aimal.apk out/netflix/build/netflix-aligned.apk
C:/Android/Sdk/build-tools/35.0.1/apksigner.bat verify out/netflix/build/Netflix-Aimal.apk
```

The preparation script changes package identity, provider authorities, local
permissions, and the app's certificate self-check to match your signer. It adds
the native loader; the build script adds lifecycle/input hooks and extension DEX.
The original native libraries remain unchanged in the APK. The additional ARM
bridge checks the expected function prologues before installing its hooks.

The supported original `libnetflix.so` SHA-256 is
`062832e1160a1014a435f4102790d306cc86e8c60f96bac5bec65ecb28677e17`.
Original APKs, signed APKs, account data, and private signing keys are excluded
from the Git source tree. The ready-to-install APK is a release attachment.

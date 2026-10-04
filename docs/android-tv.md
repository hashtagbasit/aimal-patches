# Crunchyroll Android TV 3.74.0

**Subtitle styling (Android TV)** adds all 15 Noir style options, a live preview,
and immediate updates to text subtitles. It targets the TV variant of
`com.crunchyroll.crunchyroid` **3.74.0 (22364)**. Image cues keep native drawing.

When the selected language has clean video and an external subtitle track,
the patch uses that pair instead of video with burned-in captions. The original
player decodes the ASS track and sends its cues to the custom renderer.
Changing language or turning subtitles off updates the selection. If only
burned-in subtitles are available, the original video is preserved.

The legacy TV flow stores ASS subtitles separately from closed captions. The
patch reads both maps; reading only closed captions would leave the preview
working while the episode still showed burned-in subtitles.

After updating an older patched APK, close and reopen the episode once.
Subsequent style changes apply immediately, including while playback is paused.

## Remote controls

During an episode, hold **OK / Enter** for at least one second, then release it.
**Menu** also opens the editor on remotes with that button. There is no permanent
chip over the TV video. A short OK press keeps the player's original action;
the action is delivered when you release the button.

The editor initially focuses **Close**. Use arrows to move between controls,
**left/right** to adjust sliders, and **OK** to activate buttons and switches.
Numeric fields accept exact values with the TV keyboard. **Back** closes the
editor; changes are already saved. **FIT / STRETCH** changes picture fit.

**Choose or import font → Load GothamPro from Noir** downloads the real font
weights, including Medium (500) and Black (900). The initial download requires
Internet access; imported fonts are then cached for offline use.

**Playback diagnostics** shows track and cue counters for troubleshooting.
It does not display playback URLs or account credentials.

## Patch on a phone, install on the TV

Morphe does not need to be installed on the TV.

1. In Morphe Manager on your phone, select a patch source containing this
   feature and the original **Crunchyroll Android TV 3.74.0 APK/APKM**.
2. Enable only **Subtitle styling (Android TV)**. The mobile Crunchyroll patches
   target a different player and version.
3. Apply the patch, export the signed APK, and transfer it to the TV using a USB
   drive or your usual file transfer method.
4. Open the APK in a TV file manager. If Android requests permission to install
   unknown apps, grant it to that file manager.
5. Open Crunchyroll from the TV launcher, sign in, and play an episode with
   subtitles. Hold OK to open the editor.

The official app has a different signature. Remove it before installing the
patched APK; uninstalling clears local data and requires signing in again.
For later updates without reinstalling, use the same signing key and patching
method. Morphe Manager and a separately generated desktop APK can use different
keys.

The supplied TV APKM contains both 32-bit and 64-bit ARM libraries. Keep both
splits when merging it. The app requires Android 6.0 or later. On a Xiaomi Mi Box
4 with Android 9, installation permission is granted to the file manager.

For an already authorized ADB connection:

```powershell
adb -s YOUR_TV_SERIAL install -r "Crunchyroll-TV-patched.apk"
```

Public releases contain the `.mpp` patch bundle, not the commercial APK or any
private signing key.

## Validation

Instrumentation uses the original 3.74.0 classes and ExoPlayer to verify modern
and legacy routing, ASS tracks with an empty closed-caption map, separate ASS
playback, paused style updates, cue expiration, and native fallback.

The legacy fix was also tested with authenticated playback on a Xiaomi Mi Box 4
running Android 9. The player received the Spanish ASS track, and the user
confirmed that style changes appeared in the episode.

To repeat the original-player test on an isolated emulator using your own APK:

```powershell
java -jar morphe-desktop-1.18.0-all.jar patch -p patches.mpp --exclusive -e "Subtitle styling (Android TV)" Crunchyroll-TV-3.74.0.apkm -o patched-tv.apk --unsigned
python tests/run_vendor_crunchyroll.py --tv --device emulator-5554 --sdk C:/Android/Sdk --apk patched-tv.apk --out C:/Temp/aimal-tv-test
```

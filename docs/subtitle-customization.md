# Subtitle customization

The **SUBTITLES** editor uses the same renderer for its live sample and active
subtitles. Changes are saved as you adjust controls and apply immediately,
including to a paused subtitle. Turn off **Use custom styling** to restore the
app's native subtitle drawing.

On mobile, the **FIT / SUBTITLES** chips appear with the player's controls and
disappear completely when those controls hide. Tap the player to show them
again. Both buttons support D-pad focus and activation. The separate Crunchyroll
TV patch uses remote shortcuts; see the [TV guide](android-tv.md).

## Style options

The reference is the `SubtitleStyle` schema in
[Noir Player at revision 29759cd](https://github.com/neura-neura/noir-player/blob/29759cd00029f94bf8f0bc214a5ef3974d1cafad/src/lib/subtitle-styles.ts).
All 17 properties are included:

| Noir property | Control | Range |
| --- | --- | --- |
| `fontFamily` | Installed fonts, search, pagination, local path, or CSS import | Android and imported fonts |
| `fontSize` | Size | 8–200 px |
| `fontWeight` | Weight | 100–900 |
| `textColor` | Text color | `#RRGGBB` |
| `backgroundColor` | Background color | `#RRGGBB` |
| `backgroundOpacity` | Background opacity | 0–100% |
| `bottomOffset` | Bottom offset | 0–100% |
| `useCustomMaxWidth` | Limit maximum width | On/off |
| `maxWidth` | Maximum width | 10–100% |
| `paddingX` | Horizontal padding | 0–80 px |
| `paddingY` | Vertical padding | 0–80 px |
| `borderRadius` | Corner radius | 0–80 px |
| `lineHeight` | Line height | 0.5–3 |
| `letterSpacing` | Letter spacing | −5–10 px |
| `textShadow` | Text shadow | On/off |
| `outlineWidth` | Outline width | 0–8 px |
| `outlineColor` | Outline color | `#RRGGBB` |

Numeric controls accept sliders or exact typed values. **Reset style** restores
defaults. The default font is GothamPro;
**Load GothamPro from Noir** can retry its download. Pixels are
Android screen pixels. Changing maximum width changes wrapping while preserving
font size. Percentages use the actual subtitle drawing area.

## Saved styles

Open **Saved styles** in the editor. **Save as new** stores every setting,
including the font and its CSS address. Select a style to update the preview
and active subtitles. Under **Manage**, rename it, replace its settings with
the current style, or delete it. Deleting a saved style keeps the current
subtitle settings. The library persists on the device; it is not synced.

Two protected styles are included. **Default** uses the contributor's requested
TV configuration: GothamPro, 54 px, weight 500, white text, a 23% black background,
4% bottom offset, padding 12/8 px, corners 8 px, line height 1.18, letter spacing
0.2 px, shadow enabled, and no outline. Maximum width is unrestricted (78% if enabled).
**Anime Classic** follows Noir's preset: Arial/Helvetica with Android sans-serif
fallback, 46 px, weight 700, no background or padding, 10% bottom offset, line
height 1.23, no letter spacing or shadow, and a black 2.4 px outline. Import
Arial to use that exact font on Android.

Save a new copy to customize an included style. **Reset style** applies Default
without deleting the library. Upgrades preserve existing active settings.
Uncached CSS fonts require Internet access; imported fonts are available offline.

## Install with Morphe Manager

1. Select a patch source containing this feature. The development fork is
   [neura-neura/aimal-patches](https://github.com/neura-neura/aimal-patches), which
   can be added through [Morphe's source link](https://morphe.software/add-source?github=neura-neura/aimal-patches).
2. Update the source. Select one source per app; original and fork bundles
   modify the same methods.
3. Select your original APK/APKM. Reference versions are Crunchyroll mobile
   **3.117.0**, HBO Max **7.9.0.84**, Disney+ **26.14.1+rc2-2026.08.20**, and
   Viki **26.5.0**. HBO Max uses `com.wbd.stream`. Crunchyroll TV **3.74.0** uses
   its [separate patch](android-tv.md).
4. For mobile Crunchyroll, enable **Subtitle styling**, which depends on
   **Aspect ratio control**. For other apps, enable
   **Playback speed and aspect ratio**.
5. Apply the patches and install the resulting APK. If the official app's
   signature prevents installation, preserve anything you need before removing
   it; uninstalling clears local app data.
6. Play a video with text subtitles selected. Open **SUBTITLES**, change size or
   color, and check the preview and active subtitles. Pause on a line to test
   changes without waiting for the next subtitle.

Alternatively, download a `.mpp` from your selected source's releases and import
it as a local bundle.

## Install with Morphe Desktop

```powershell
java -jar morphe-desktop-1.18.0-all.jar patch -p patches.mpp app.apkm
```

Manager and Desktop support merging split APK bundles. See the official
[patch source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md)
and [Desktop documentation](https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md).

## Fonts and rendering limits

The installed font list reflects Android fonts, rather than Windows fonts.
CSS import downloads TTF/OTF fonts over HTTPS, validates them with `Typeface`,
and caches them in private app storage. CSS containing only WOFF/WOFF2 needs
TTF/OTF alternatives. Local paths must be readable by the app. Import errors
appear in the editor and preserve the previous style.

The importer preserves each normal font weight declared in CSS. GothamPro uses
its real Light (300), Regular (400), Medium (500), Bold (700), and Black (900)
files. Older single-file imports upgrade in the background when that family is
used with Internet access. Offline, the existing font remains available; use
**Load GothamPro from Noir** to retry manually.

Letters burned into video pixels cannot be restyled. The TV patch selects clean
video with an external track when both are available. Media3 image cues keep
their native drawing. Mobile Crunchyroll keeps original ASS scripts and libass
handles and uses the renderer clock for custom text. Like Noir's text overlay,
custom ASS rendering does not preserve karaoke, vector drawings, or individual
sign positioning. Disable **Use custom styling** to restore native ASS on mobile.

## Validation and remaining coverage

GitHub Actions builds the bundle and patches Android fixtures with Morphe
Desktop 1.18.0. Checks cover injected DEX hooks, all 17 visual properties,
paused updates, persistence, overlapping cues, line breaks, backward seeks,
expiration, track disposal, and native fallback. Font tests cover real Gotham
weights and migration of older caches. Controller tests check complete hiding,
restoration, and D-pad activation. TV tests cover modern/legacy routing,
subtitles-off, short OK delivery, long OK/Menu shortcuts, and initial focus.
Saved-style checks cover detached snapshots, included-style protection,
create/rename/update/delete, bounded storage values, older settings, damaged
storage, and focus after applying a preset on TV.

The panel is checked in portrait and landscape:
[portrait](screenshots/subtitles-portrait.png) and
[landscape](screenshots/subtitles-landscape.png).

Instrumentation in the original mobile Crunchyroll **3.117.0 (1175)** APK checks
libass JNI, track loading, renderer timing, drawing, paused color changes,
expiration, native ASS restoration, and track cleanup. `renderFrame(JJ)` keeps
its original JNI name, signature, and implementation; hooks capture timestamps
at interface and concrete call sites. Regression fixtures exercise both forms
with a real JNI method.

The user confirmed mobile playback and authenticated TV playback on a Xiaomi
Mi Box 4 with Android 9. Original-player TV tests exercise clean-video selection
and separate ASS playback. Commercial HBO Max, Disney+, and Viki APKs have not
been validated; their coverage is limited to shared media3 fixtures.

## Repeat the tests

Use an isolated emulator: fixtures use Crunchyroll and HBO Max package names.
You need JDK 21+, Android SDK platform 35, build tools 35.0.1, NDK 27.0.12077973,
`adb`, the patch bundle, and Morphe Desktop 1.18.0.

```powershell
python tests/run_fixtures.py --sdk C:/Android/Sdk --bundle patches.mpp --morphe morphe-desktop-1.18.0-all.jar --out C:/Temp/aimal-fixtures --fonts
```

Results are written to `fixture-result.txt`, `font-result.txt`,
`controls-result.txt`, `tv-result.txt`, and `patch-result.json` as applicable.
Fixtures are test apps, not commercial app releases.

To test the original mobile Crunchyroll renderer using your own APKM:

```powershell
java -jar morphe-desktop-1.18.0-all.jar patch -p patches.mpp --exclusive -e "Subtitle styling" -e "Playback speed" -e "Aspect ratio control" Crunchyroll-3.117.0.apkm -o patched.apk --unsigned
python tests/run_vendor_crunchyroll.py --device emulator-5554 --sdk C:/Android/Sdk --apk patched.apk --out C:/Temp/aimal-vendor
```

The vendor runner accepts only an explicitly selected isolated emulator. It
installs the app and instrumentation with a temporary test signature. Remove
conflicting test installs first. It does not sign in or play remote content.
Commercial APKs and private signing keys are excluded from the repository and
public releases.

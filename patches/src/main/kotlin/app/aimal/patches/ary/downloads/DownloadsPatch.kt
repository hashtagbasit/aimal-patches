package app.aimal.patches.ary.downloads

import app.aimal.patches.ary.shared.Constants.COMPATIBILITY_ARY
import app.aimal.patches.ary.shared.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import org.w3c.dom.Element

// EXTENSION_PACKAGE already carries the leading "L" of a smali type descriptor.
private const val DOWNLOADS = "$EXTENSION_PACKAGE/downloads"
private const val EPISODE_ROW = "$DOWNLOADS/EpisodeRow;"
private const val DOWNLOADS_TAB = "$DOWNLOADS/DownloadsTab;"
private const val ARY_DOWNLOADS = "$DOWNLOADS/AryDownloads;"
private const val SERIES_BUTTON = "$DOWNLOADS/SeriesDownloadButton;"

private const val MANIFEST_FILE = "AndroidManifest.xml"
private const val DOWNLOAD_SERVICE = "app.aimal.extension.ary.downloads.AryDownloadService"
private const val DATA_SYNC_PERMISSION = "android.permission.FOREGROUND_SERVICE_DATA_SYNC"
private const val DOWNLOAD_RESTART_ACTION =
    "androidx.media3.exoplayer.downloadService.action.RESTART"

private const val MENU_FILE = "res/menu/aryzap_bottom_navigation_menu.xml"
private const val IDS_FILE = "res/values/ids.xml"
private const val TAB_ID = "aryDownloadsTab"

/**
 * Adds the "Downloads" entry to the bottom navigation menu.
 *
 * The id is declared in res/values/ids.xml and referenced as @id rather than
 * @+id so the extension can resolve it at runtime with
 * Resources.getIdentifier("aryDownloadsTab", ...) - that keeps the bytecode side
 * free of any generated resource constant.
 *
 * Reuses the framework's stat_sys_download icon so no drawable has to be added.
 */
val downloadsTabResourcePatch = resourcePatch(
    description = "Adds a Downloads item to the bottom navigation menu."
) {
    execute {
        document(IDS_FILE).use { document ->
            val resources = document.documentElement
            val alreadyPresent = (0 until resources.childNodes.length)
                .mapNotNull { resources.childNodes.item(it) as? Element }
                .any { it.getAttribute("name") == TAB_ID }

            if (!alreadyPresent) {
                val item = document.createElement("item")
                item.setAttribute("name", TAB_ID)
                item.setAttribute("type", "id")
                resources.appendChild(item)
            }
        }

        document(MENU_FILE).use { document ->
            val menu = document.documentElement
            val item = document.createElement("item")
            item.setAttribute("android:id", "@id/$TAB_ID")
            item.setAttribute("android:icon", "@android:drawable/stat_sys_download")
            item.setAttribute("android:title", "Downloads")
            menu.appendChild(item)
        }

        // Register the foreground download service.
        //
        // The app already declares FOREGROUND_SERVICE and POST_NOTIFICATIONS,
        // but it targets SDK 36, where a typed foreground service also needs the
        // matching FOREGROUND_SERVICE_DATA_SYNC permission or startForeground
        // throws.
        document(MANIFEST_FILE).use { document ->
            val manifest = document.documentElement

            val hasPermission = (0 until manifest.childNodes.length)
                .mapNotNull { manifest.childNodes.item(it) as? Element }
                .any {
                    it.tagName == "uses-permission" &&
                        it.getAttribute("android:name") == DATA_SYNC_PERMISSION
                }

            if (!hasPermission) {
                val permission = document.createElement("uses-permission")
                permission.setAttribute("android:name", DATA_SYNC_PERMISSION)
                manifest.insertBefore(permission, manifest.firstChild)
            }

            val application = (0 until manifest.childNodes.length)
                .mapNotNull { manifest.childNodes.item(it) as? Element }
                .first { it.tagName == "application" }

            val service = document.createElement("service")
            service.setAttribute("android:name", DOWNLOAD_SERVICE)
            service.setAttribute("android:exported", "false")
            service.setAttribute("android:foregroundServiceType", "dataSync")

            // Media3 restarts unfinished downloads by broadcasting this action.
            val filter = document.createElement("intent-filter")
            val action = document.createElement("action")
            action.setAttribute("android:name", DOWNLOAD_RESTART_ACTION)
            filter.appendChild(action)
            val category = document.createElement("category")
            category.setAttribute("android:name", "android.intent.category.DEFAULT")
            filter.appendChild(category)
            service.appendChild(filter)

            application.appendChild(service)
        }
    }
}

/**
 * Episode downloads, a Downloads tab, and offline playback.
 *
 * Four injection points:
 *
 *  1. AdapterYtProfile.onBindViewHolder - a download control on every episode
 *     row. This one adapter serves both EpisodesViewAll and VideoProfile, so a
 *     single patch covers every episode list in the app.
 *
 *  2. AdapterYtProfile.<init> - the "Download all" control, injected here for
 *     the same reason.
 *
 *  3. MainPage's navigation listener - routes the new menu item to
 *     DownloadsFragment, hosted in the app's existing R.id.fragment_container.
 *
 *  4. CdnPlayer's DefaultDataSource.Factory - wrapped with a CacheDataSource so
 *     downloaded episodes play back through the app's own player.
 */
@Suppress("unused")
val downloadsPatch = bytecodePatch(
    name = "Episode downloads",
    description = "Download individual episodes or a whole show, browse them in a Downloads tab, " +
        "and play them offline in the app's own video player.",
    default = true
) {
    compatibleWith(COMPATIBILITY_ARY)

    dependsOn(downloadsTabResourcePatch)

    extendWith("extensions/extension.mpe")

    execute {
        // 1. Download control on every episode row.
        //
        // Injected immediately after the check-cast, where p2 holds the
        // EpisodeElement and p1 still holds the view holder. v0/v1 are safe to
        // clobber here: the app's next instructions write both before reading.
        // Each injection resolves through matchOrNull so one fingerprint that
        // drifts in a future app build degrades to "that feature is missing"
        // rather than throwing and taking the whole patch down with it.
        EpisodeBindFingerprint.matchOrNull()?.let { match ->
            val checkCastIndex = match.instructionMatches[2].index
            match.method.addInstructions(
                checkCastIndex + 1,
                """
                    iget-object v0, p1, Landroidx/recyclerview/widget/RecyclerView${'$'}ViewHolder;->itemView:Landroid/view/View;
                    const/4 v1, 0x0
                    invoke-static { v0, p2, v1 }, $EPISODE_ROW->attach(Landroid/view/View;Ljava/lang/Object;Ljava/lang/String;)V
                """
            )
        }

        // 2. "Download all" control on every screen that lists a show's episodes.
        //
        // p1 is the Models.Episode holding the list, p2 the hosting Context.
        //
        // This MUST NOT be injected at instruction 0. A constructor has to invoke
        // its super constructor before anything else; code placed ahead of that
        // fails dex verification, and a VerifyError on AdapterYtProfile takes out
        // every episode list in the app. Injecting before the trailing return-void
        // puts the call after both super() and all field assignments.
        AdapterConstructorFingerprint.matchOrNull()?.let { match ->
            val method = match.method
            val returnIndex = method.implementation!!.instructions.count() - 1
            method.addInstructions(
                returnIndex,
                "invoke-static { p2, p1 }, $SERIES_BUTTON->attach(Landroid/content/Context;Ljava/lang/Object;)V"
            )
        }

        // 3. Route the new tab before the app's own switch runs.
        //
        // Returns false like every other branch of the app's listener, so the
        // navigation bar's selected state behaves exactly as it does today.
        MainPageNavigationFingerprint.matchOrNull()?.let { match ->
            val navigationMethod = match.method
            navigationMethod.addInstructionsWithLabels(
                0,
                """
                    invoke-static { p0, p1 }, $DOWNLOADS_TAB->handle(Landroid/app/Activity;Landroid/view/MenuItem;)Z
                    move-result v0
                    if-eqz v0, :not_downloads
                    const/4 v0, 0x0
                    return v0
                """,
                ExternalLabel("not_downloads", navigationMethod.getInstruction(0))
            )
        }

        // 4. Serve downloaded media from the offline cache.
        CdnPlayerDataSourceFingerprint.matchOrNull()?.let { match ->
            val factoryIndex = match.instructionMatches[0].index
            val factoryRegister = match.method
                .getInstruction<FiveRegisterInstruction>(factoryIndex).registerC

            match.method.addInstructions(
                factoryIndex + 1,
                """
                    invoke-static { v$factoryRegister, p0 }, $ARY_DOWNLOADS->wrap(Landroidx/media3/datasource/DataSource${'$'}Factory;Landroid/content/Context;)Landroidx/media3/datasource/DataSource${'$'}Factory;
                    move-result-object v$factoryRegister
                """
            )
        }
    }
}

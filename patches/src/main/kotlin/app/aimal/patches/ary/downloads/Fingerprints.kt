package app.aimal.patches.ary.downloads

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * Injection points for the downloads feature, verified against ARY Plus 3.6.6.
 */

/**
 * AdapterYtProfile backs the episode list on BOTH EpisodesViewAll and
 * VideoProfile, so patching this single bind method puts a download control on
 * every episode row in the app.
 *
 *   invoke-interface {v0, p2}, Ljava/util/List;->get(I)Ljava/lang/Object;
 *   move-result-object p2
 *   check-cast p2, Lcom/material/components/aryzap/Models/Episode$EpisodeElement;
 */
object EpisodeBindFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Adapters/AdapterYtProfile;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf("L", "I"),
    filters = listOf(
        methodCall(smali = "Lcom/material/components/aryzap/Models/Episode;->getEpisodes()Ljava/util/List;"),
        methodCall(smali = "Ljava/util/List;->get(I)Ljava/lang/Object;"),
        opcode(Opcode.CHECK_CAST)
    )
)

/**
 * MainPage's bottom-navigation listener. It switches on the selected menu item
 * id and swaps fragments into R.id.fragment_container.
 *
 * The Downloads check is injected at instruction 0 so it runs before the app's
 * own switch, which has no case for the new item and would otherwise fall
 * through and leave the previous tab on screen.
 */
object MainPageNavigationFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/MainPage;",
    returnType = "Z",
    parameters = listOf("Landroid/view/MenuItem;"),
    filters = listOf(
        methodCall(smali = "Landroid/view/MenuItem;->getItemId()I"),
        opcode(Opcode.SPARSE_SWITCH)
    )
)

/**
 * Where CdnPlayer builds the data source its ExoPlayer instance reads through:
 *
 *   new-instance v0, Landroidx/media3/datasource/DefaultDataSource$Factory;
 *   invoke-direct {v0, p0}, ...DefaultDataSource$Factory;-><init>(Landroid/content/Context;)V
 *
 * Wrapping the result with a CacheDataSource is what makes a downloaded episode
 * play back through the app's own player.
 */
object CdnPlayerDataSourceFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/CdnPlayer;",
    filters = listOf(
        methodCall(
            smali = "Landroidx/media3/datasource/DefaultDataSource\$Factory;-><init>(Landroid/content/Context;)V"
        ),
        methodCall(
            smali = "Landroidx/media3/exoplayer/source/DefaultMediaSourceFactory;-><init>(Landroidx/media3/datasource/DataSource\$Factory;)V"
        )
    )
)

/**
 * AdapterYtProfile's constructor, which receives the show's full episode list
 * and the hosting Context.
 *
 * Used to attach the "Download all" control. Injecting here rather than at the
 * construction sites avoids matching compiler-generated inner classes
 * (VideoProfile$9, VideoProfile$19$1) whose names are not stable across builds,
 * and whose invoke-direct/range call shape makes register handling awkward.
 */
object AdapterConstructorFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Adapters/AdapterYtProfile;",
    name = "<init>",
    returnType = "V",
    parameters = listOf(
        "Lcom/material/components/aryzap/Models/Episode;",
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "Ljava/util/List;"
    )
)

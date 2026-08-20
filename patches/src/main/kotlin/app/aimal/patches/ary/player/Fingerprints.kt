package app.aimal.patches.ary.player

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * ARY Plus ships two VOD players that each carry their own private copy of the
 * playback speed table:
 *
 *   PlayerActivity - single videos / non-CDN playback
 *   CdnPlayer      - episode playback launched from EpisodesViewAll
 *
 * Both declare, verified against 3.6.6:
 *   speedOptions:[Ljava/lang/String; = {".25", ".50", "1.0 Normal", "1.5", "2.0", "4.0"}
 *   speedValues:[F                   = {0.25, 0.5, 1.0, 1.5, 2.0, 4.0}
 *
 * The lambda applying a selected speed is named lambda$showSettingsOverlay$24 in
 * PlayerActivity but lambda$showSettingsOverlay$34 in CdnPlayer, so these
 * fingerprints match on instruction content rather than on method name.
 */

/**
 * Matches the speed-selection lambda body:
 *
 *   iget-object v1, p0, PlayerActivity;->speedValues:[F
 *   aget p1, v1, p1
 *   invoke-interface {v0, p1}, ExoPlayer;->setPlaybackSpeed(F)V
 */
object PlayerActivitySpeedValueFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/PlayerActivity;",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        fieldAccess(smali = "Lcom/material/components/aryzap/Activities/PlayerActivity;->speedValues:[F"),
        opcode(Opcode.AGET, MatchAfterImmediately()),
        methodCall(smali = "Landroidx/media3/exoplayer/ExoPlayer;->setPlaybackSpeed(F)V")
    )
)

object CdnPlayerSpeedValueFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/CdnPlayer;",
    returnType = "V",
    parameters = listOf("I"),
    filters = listOf(
        fieldAccess(smali = "Lcom/material/components/aryzap/Activities/CdnPlayer;->speedValues:[F"),
        opcode(Opcode.AGET, MatchAfterImmediately()),
        methodCall(smali = "Landroidx/media3/exoplayer/ExoPlayer;->setPlaybackSpeed(F)V")
    )
)

/**
 * Matches inside showSettingsOverlay():
 *
 *   iget-object v1, p0, PlayerActivity;->speedOptions:[Ljava/lang/String;
 *   invoke-static {v1}, Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;
 *   move-result-object v1
 */
object PlayerActivitySpeedLabelsFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/PlayerActivity;",
    name = "showSettingsOverlay",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(smali = "Lcom/material/components/aryzap/Activities/PlayerActivity;->speedOptions:[Ljava/lang/String;"),
        methodCall(smali = "Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;")
    )
)

object CdnPlayerSpeedLabelsFingerprint : Fingerprint(
    definingClass = "Lcom/material/components/aryzap/Activities/CdnPlayer;",
    name = "showSettingsOverlay",
    returnType = "V",
    parameters = listOf(),
    filters = listOf(
        fieldAccess(smali = "Lcom/material/components/aryzap/Activities/CdnPlayer;->speedOptions:[Ljava/lang/String;"),
        methodCall(smali = "Ljava/util/Arrays;->asList([Ljava/lang/Object;)Ljava/util/List;")
    )
)

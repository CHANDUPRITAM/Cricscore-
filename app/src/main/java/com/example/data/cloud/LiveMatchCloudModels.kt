package com.example.data.cloud

import androidx.annotation.Keep
import com.example.domain.MatchSnapshot
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val clubName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LiveMatchDoc(
    @DocumentId val shareCode: String = "",
    val adminId: String = "",
    val adminName: String = "",
    val localMatchId: Long = 0L,
    val hostTeam: String = "",
    val visitorTeam: String = "",
    val tossSummary: String = "",
    val matchStatus: String = "LIVE",
    val sharingEnabled: Boolean = true,
    val totalOvers: Long = 10L,
    val currentInningsNumber: Long = 1L,
    val battingTeam: String = "",
    val bowlingTeam: String = "",
    val scoreRuns: Long = 0L,
    val scoreWickets: Long = 0L,
    val oversDisplay: String = "0.0",
    val crr: String = "0.00",
    val rrr: String = "",
    val targetRuns: Long = 0L,
    val chaseSummary: String = "",
    val resultSummary: String = "",
    val strikerName: String = "",
    val strikerStats: String = "",
    val nonStrikerName: String = "",
    val nonStrikerStats: String = "",
    val bowlerName: String = "",
    val bowlerStats: String = "",
    val partnershipSummary: String = "",
    val lastWicketSummary: String = "",
    val lastBallBadge: String = "",
    val lastShotZone: String = "",
    val currentOverBalls: List<String> = emptyList(),
    val recentBalls: List<String> = emptyList(),
    val innings1Summary: String = "",
    val innings2Summary: String = "",
    val battingCardLines: List<String> = emptyList(),
    val bowlingCardLines: List<String> = emptyList(),
    val ballSequence: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface CloudUiState<out T> {
    data object Idle : CloudUiState<Nothing>
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

object LiveMatchMapper {
    fun buildMutableFieldsMap(
        snapshot: MatchSnapshot,
        adminName: String,
        sharingEnabled: Boolean,
        ballSequence: Long
    ): Map<String, Any> {
        val inn = snapshot.currentInnings
        val status = when {
            snapshot.isMatchCompleted -> "COMPLETED"
            snapshot.innings1.isCompleted && !snapshot.innings2.isStarted -> "INNINGS_BREAK"
            else -> "LIVE"
        }

        val strikerStat = inn.batterStat(inn.striker)
        val nonStrikerStat = inn.batterStat(inn.nonStriker)
        val bowlerStat = inn.bowlerStat(inn.currentBowler)

        val strikerLine = strikerStat?.let {
            "${it.runs} (${it.balls}) • ${it.fours}x4 ${it.sixes}x6 • SR ${"%.1f".format(it.strikeRate)}"
        }.orEmpty()

        val nonStrikerLine = nonStrikerStat?.let {
            "${it.runs} (${it.balls}) • ${it.fours}x4 ${it.sixes}x6 • SR ${"%.1f".format(it.strikeRate)}"
        }.orEmpty()

        val bowlerLine = bowlerStat?.let {
            "${it.oversDisplay}-${it.maidens}-${it.runsConceded}-${it.wickets} • ER ${"%.1f".format(it.economy)}"
        }.orEmpty()

        val partnershipLine = inn.currentPartnership?.let {
            "${it.runs} runs (${it.balls} balls)"
        }.orEmpty()

        val chaseBanner = when {
            snapshot.isMatchCompleted -> snapshot.resultSummary
            inn.target != null -> "Target ${inn.target} • Need ${inn.runsNeeded ?: 0} off ${inn.ballsRemaining} balls"
            else -> snapshot.tossSummary
        }

        val allDeliveredBalls = (
            inn.overs.flatMap { ov -> ov.balls.map { chip -> ov.bowlerName to chip } } +
                inn.currentOverBalls.map { chip -> inn.currentBowler to chip }
            ).distinctBy { it.second.eventId }
        val lastBall = allDeliveredBalls.lastOrNull()?.second
        val lastShot = inn.shots.lastOrNull()

        val currentOverList = inn.currentOverBalls.takeLast(24).map { it.label.take(12) }
        val recentBallsList = allDeliveredBalls.takeLast(12).reversed().map { (bowler, chip) ->
            val overPrefix = "${chip.overNumber}.${chip.ballInOver} "
            val zonePart = chip.shotZone?.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
            val bowlerPart = if (bowler.isNotBlank()) " • $bowler" else ""
            "$overPrefix${chip.label}$bowlerPart$zonePart".take(90)
        }

        val inn1Summary = if (snapshot.innings1.isStarted) {
            "${snapshot.innings1.battingTeam}: ${snapshot.innings1.totalRuns}/${snapshot.innings1.wickets} (${snapshot.innings1.oversDisplay} ov)"
        } else ""

        val inn2Summary = if (snapshot.innings2.isStarted) {
            "${snapshot.innings2.battingTeam}: ${snapshot.innings2.totalRuns}/${snapshot.innings2.wickets} (${snapshot.innings2.oversDisplay} ov)"
        } else ""

        val battingLines = inn.batters.take(20).map { b ->
            val star = if (!b.isOut && b.name == inn.striker) "*" else ""
            val statusTxt = if (b.isOut) b.dismissalText else "not out"
            "${b.name}$star|${b.runs}|${b.balls}|${b.fours}|${b.sixes}|${"%.1f".format(b.strikeRate)}|$statusTxt".take(100)
        }

        val bowlingLines = inn.bowlers.take(20).map { bw ->
            "${bw.name}|${bw.oversDisplay}|${bw.maidens}|${bw.runsConceded}|${bw.wickets}|${"%.1f".format(bw.economy)}".take(100)
        }

        return mapOf(
            "adminName" to adminName.trim().ifEmpty { "Match Admin" }.take(80),
            "hostTeam" to snapshot.hostTeam.trim().ifEmpty { "Team A" }.take(60),
            "visitorTeam" to snapshot.visitorTeam.trim().ifEmpty { "Team B" }.take(60),
            "tossSummary" to snapshot.tossSummary.take(140),
            "matchStatus" to status,
            "sharingEnabled" to sharingEnabled,
            "totalOvers" to snapshot.rules.totalOvers.coerceIn(1, 99).toLong(),
            "currentInningsNumber" to snapshot.currentInningsNumber.coerceIn(1, 2).toLong(),
            "battingTeam" to inn.battingTeam.trim().ifEmpty { snapshot.hostTeam }.take(60),
            "bowlingTeam" to inn.bowlingTeam.trim().ifEmpty { snapshot.visitorTeam }.take(60),
            "scoreRuns" to inn.totalRuns.coerceIn(0, 5000).toLong(),
            "scoreWickets" to inn.wickets.coerceIn(0, 20).toLong(),
            "oversDisplay" to inn.oversDisplay.ifEmpty { "0.0" }.take(16),
            "crr" to "%.2f".format(inn.currentRunRate).take(16),
            "rrr" to (inn.requiredRunRate?.let { "%.2f".format(it) }.orEmpty()).take(16),
            "targetRuns" to (inn.target ?: 0).coerceIn(0, 5000).toLong(),
            "chaseSummary" to chaseBanner.take(160),
            "resultSummary" to snapshot.resultSummary.take(180),
            "strikerName" to inn.striker.take(60),
            "strikerStats" to strikerLine.take(80),
            "nonStrikerName" to inn.nonStriker.take(60),
            "nonStrikerStats" to nonStrikerLine.take(80),
            "bowlerName" to inn.currentBowler.take(60),
            "bowlerStats" to bowlerLine.take(80),
            "partnershipSummary" to partnershipLine.take(80),
            "lastWicketSummary" to (inn.lastWicketSummary ?: "").take(100),
            "lastBallBadge" to (lastBall?.label ?: "").take(24),
            "lastShotZone" to (lastShot?.shotZone ?: "").take(40),
            "currentOverBalls" to currentOverList,
            "recentBalls" to recentBallsList,
            "innings1Summary" to inn1Summary.take(140),
            "innings2Summary" to inn2Summary.take(140),
            "battingCardLines" to battingLines,
            "bowlingCardLines" to bowlingLines,
            "ballSequence" to ballSequence.coerceIn(0L, 100000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun buildCreatePayload(
        snapshot: MatchSnapshot,
        adminId: String,
        adminName: String,
        sharingEnabled: Boolean,
        ballSequence: Long
    ): Map<String, Any> {
        val base = buildMutableFieldsMap(snapshot, adminName, sharingEnabled, ballSequence).toMutableMap()
        base["adminId"] = adminId
        base["localMatchId"] = snapshot.matchId.coerceAtLeast(0L)
        base["createdAt"] = FieldValue.serverTimestamp()
        return base
    }
}

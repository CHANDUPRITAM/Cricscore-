package com.example.data.cloud

import androidx.annotation.Keep
import com.example.domain.BallChip
import com.example.domain.InningsState
import com.example.domain.MatchSnapshot
import com.example.domain.OverSummary
import com.example.domain.PlayerCareerStats
import com.example.domain.PlayerMatchPerformance
import com.example.domain.RunDistribution
import com.example.domain.ShotRecord
import com.example.domain.WinProbabilityEngine
import com.example.domain.WinProbabilityPoint
import com.example.domain.WinProbabilitySummary
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

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
data class AvatarDoc(
    @DocumentId val docId: String = "",
    val ownerUid: String = "",
    val entityType: String = "TEAM",
    val entityName: String = "",
    val teamName: String = "",
    val photoBase64: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun docIdFor(entityType: String, entityName: String, teamName: String = ""): String {
            val cleanType = if (entityType.equals("TEAM", ignoreCase = true)) "TEAM" else "PLAYER"
            val normName = entityName.trim().lowercase()
            val normTeam = teamName.trim().lowercase()
            val slug = "${normName}_${normTeam}"
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifEmpty { "item" }
            val hash = "${cleanType}|$normName|$normTeam".hashCode().toUInt().toString(16)
            return "${cleanType}_${slug}_${hash}".take(120)
        }
    }
}

@Keep
@IgnoreExtraProperties
data class SavedTeamDoc(
    @DocumentId val docId: String = "",
    val ownerUid: String = "",
    val teamName: String = "",
    val shortCode: String = "",
    val photoBase64: String = "",
    val playerEntries: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun docIdFor(teamName: String): String {
            val norm = teamName.trim().lowercase()
            val slug = norm
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifEmpty { "team" }
            val hash = norm.hashCode().toUInt().toString(16)
            return "team_${slug}_${hash}".take(120)
        }

        fun encodePlayerEntry(name: String, role: String): String {
            val cleanName = name.replace("|", " ").trim().take(60)
            val cleanRole = role.replace("|", " ").trim().ifEmpty { "All-Rounder" }.take(30)
            return "$cleanName|$cleanRole"
        }

        fun decodePlayerEntry(entry: String): Pair<String, String>? {
            val parts = entry.split("|", limit = 2)
            val name = parts.getOrNull(0)?.trim().orEmpty()
            if (name.isEmpty()) return null
            val role = parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "All-Rounder" }
            return name to role
        }
    }
}

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
    val potmSummary: String = "",
    val innings1Meta: String = "",
    val innings2Meta: String = "",
    val innings1Extras: String = "",
    val innings2Extras: String = "",
    val innings1BattingLines: List<String> = emptyList(),
    val innings1BowlingLines: List<String> = emptyList(),
    val innings1FowLines: List<String> = emptyList(),
    val innings1PartnershipLines: List<String> = emptyList(),
    val innings2BattingLines: List<String> = emptyList(),
    val innings2BowlingLines: List<String> = emptyList(),
    val innings2FowLines: List<String> = emptyList(),
    val innings2PartnershipLines: List<String> = emptyList(),
    val hostTeamPhoto: String = "",
    val visitorTeamPhoto: String = "",
    val analyticsLines: List<String> = emptyList(),
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
        ballSequence: Long,
        hostTeamPhoto: String = "",
        visitorTeamPhoto: String = ""
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

        val battingLines = buildInningsBattingLines(inn)
        val bowlingLines = buildInningsBowlingLines(inn)
        val inn1 = snapshot.innings1
        val inn2 = snapshot.innings2
        val potmText = snapshot.playerOfMatch?.let { "${it.playerName} (${it.summary})" }.orEmpty()
        val analyticsLines = buildAnalyticsLines(snapshot)

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
            "potmSummary" to potmText.take(140),
            "innings1Meta" to buildInningsMeta(inn1),
            "innings2Meta" to buildInningsMeta(inn2),
            "innings1Extras" to if (inn1.isStarted) inn1.extras.summary().take(80) else "",
            "innings2Extras" to if (inn2.isStarted) inn2.extras.summary().take(80) else "",
            "innings1BattingLines" to if (inn1.isStarted) buildInningsBattingLines(inn1) else emptyList(),
            "innings1BowlingLines" to if (inn1.isStarted) buildInningsBowlingLines(inn1) else emptyList(),
            "innings1FowLines" to if (inn1.isStarted) buildInningsFowLines(inn1) else emptyList(),
            "innings1PartnershipLines" to if (inn1.isStarted) buildInningsPartnershipLines(inn1) else emptyList(),
            "innings2BattingLines" to if (inn2.isStarted) buildInningsBattingLines(inn2) else emptyList(),
            "innings2BowlingLines" to if (inn2.isStarted) buildInningsBowlingLines(inn2) else emptyList(),
            "innings2FowLines" to if (inn2.isStarted) buildInningsFowLines(inn2) else emptyList(),
            "innings2PartnershipLines" to if (inn2.isStarted) buildInningsPartnershipLines(inn2) else emptyList(),
            "hostTeamPhoto" to hostTeamPhoto.take(32000),
            "visitorTeamPhoto" to visitorTeamPhoto.take(32000),
            "analyticsLines" to analyticsLines,
            "ballSequence" to ballSequence.coerceIn(0L, 100000L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    private fun String.safePipe(): String = replace("|", "/")
    private fun String.safeSub(): String = replace("|", "/").replace("~", "-").replace(";", ",")

    private fun buildAnalyticsLines(snapshot: MatchSnapshot): List<String> {
        val out = mutableListOf<String>()
        val inn1 = snapshot.innings1
        val inn2 = snapshot.innings2

        if (inn1.isStarted) {
            val d1 = inn1.runDistribution
            out.add("D1|${d1.dots}|${d1.singlesTwosThreesRuns}|${d1.foursRuns}|${d1.sixesRuns}|${d1.extrasRuns}".take(60))
        }
        if (inn2.isStarted) {
            val d2 = inn2.runDistribution
            out.add("D2|${d2.dots}|${d2.singlesTwosThreesRuns}|${d2.foursRuns}|${d2.sixesRuns}|${d2.extrasRuns}".take(60))
        }
        WinProbabilityEngine.calculateFromSnapshot(snapshot).points.takeLast(100).forEach { pt ->
            out.add("W|${pt.encode()}".take(80))
        }
        if (inn1.isStarted) {
            buildOverLines("O1", inn1).forEach { out.add(it) }
            buildShotLines("S1", inn1).forEach { out.add(it) }
        }
        if (inn2.isStarted) {
            buildOverLines("O2", inn2).forEach { out.add(it) }
            buildShotLines("S2", inn2).forEach { out.add(it) }
        }
        return out.take(600)
    }

    private fun buildOverLines(prefix: String, inn: InningsState): List<String> {
        return inn.overs.take(99).map { ov ->
            val packedBalls = ov.balls.take(24).joinToString(";") { c ->
                val w = if (c.isWicket) 1 else 0
                val wd = if (c.isWide) 1 else 0
                val nb = if (c.isNoBall) 1 else 0
                val b = if (c.isBye) 1 else 0
                val lb = if (c.isLegBye) 1 else 0
                val f = if (c.isFour) 1 else 0
                val s = if (c.isSix) 1 else 0
                val dt = if (c.isDot) 1 else 0
                "${c.label.safeSub()}~${c.totalRuns}~$w~$wd~$nb~$b~$lb~$f~$s~$dt~${c.ballInOver}"
            }
            "$prefix|${ov.overNumber}|${ov.bowlerName.safePipe()}|${ov.runsInOver}|${ov.wicketsInOver}|${ov.cumulativeRuns}|${ov.cumulativeWickets}|${ov.isCompleted}|$packedBalls".take(500)
        }
    }

    private fun buildShotLines(prefix: String, inn: InningsState): List<String> {
        return inn.shots.takeLast(150).map { s ->
            val f = if (s.isFour) 1 else 0
            val sx = if (s.isSix) 1 else 0
            "$prefix|${s.overBallDisplay.safePipe()}|${s.batterName.safePipe()}|${s.bowlerName.safePipe()}|${s.runs}|${"%.3f".format(s.shotX)}|${"%.3f".format(s.shotY)}|${s.shotZone.safePipe()}|$f|$sx".take(160)
        }
    }

    private fun buildInningsMeta(inn: InningsState): String {
        if (!inn.isStarted) return ""
        return listOf(
            inn.battingTeam.safePipe(),
            inn.bowlingTeam.safePipe(),
            inn.totalRuns.toString(),
            inn.wickets.toString(),
            inn.oversDisplay,
            "%.2f".format(inn.currentRunRate),
            inn.isStarted.toString(),
            inn.isCompleted.toString()
        ).joinToString("|").take(160)
    }

    private fun buildInningsBattingLines(inn: InningsState): List<String> {
        return inn.batters.take(20).map { b ->
            val star = if (!b.isOut && !b.isRetiredHurt) " *" else ""
            val isHighlighted = !b.isOut && !b.isRetiredHurt && !inn.isCompleted
            val statusTxt = b.dismissalText.safePipe()
            "${b.name.safePipe()}$star|${b.runs}|${b.balls}|${b.fours}|${b.sixes}|${"%.1f".format(b.strikeRate)}|$statusTxt|$isHighlighted".take(120)
        }
    }

    private fun buildInningsBowlingLines(inn: InningsState): List<String> {
        return inn.bowlers.take(20).map { bw ->
            "${bw.name.safePipe()}|${bw.oversDisplay}|${bw.maidens}|${bw.runsConceded}|${bw.wickets}|${"%.1f".format(bw.economy)}|${bw.wides}|${bw.noBalls}".take(120)
        }
    }

    private fun buildInningsFowLines(inn: InningsState): List<String> {
        return inn.fallOfWickets.take(20).map { fow ->
            "${fow.runsAtFall}-${fow.wicketNumber} (${fow.batterName}, ${fow.overDisplay})".take(100)
        }
    }

    private fun buildInningsPartnershipLines(inn: InningsState): List<String> {
        return inn.partnerships.take(20).map { p ->
            "${p.batter1.safePipe()}|${p.batter1Runs}|${p.batter1Balls}|${p.batter2.safePipe()}|${p.batter2Runs}|${p.batter2Balls}|${p.runs}|${p.balls}|${p.isUnbroken}".take(140)
        }
    }

    fun buildCreatePayload(
        snapshot: MatchSnapshot,
        adminId: String,
        adminName: String,
        sharingEnabled: Boolean,
        ballSequence: Long,
        hostTeamPhoto: String = "",
        visitorTeamPhoto: String = ""
    ): Map<String, Any> {
        val base = buildMutableFieldsMap(
            snapshot = snapshot,
            adminName = adminName,
            sharingEnabled = sharingEnabled,
            ballSequence = ballSequence,
            hostTeamPhoto = hostTeamPhoto,
            visitorTeamPhoto = visitorTeamPhoto
        ).toMutableMap()
        base["adminId"] = adminId
        base["localMatchId"] = snapshot.matchId.coerceAtLeast(0L)
        base["createdAt"] = FieldValue.serverTimestamp()
        return base
    }
}

data class SpectatorBatterLine(
    val name: String,
    val runs: String,
    val balls: String,
    val fours: String,
    val sixes: String,
    val strikeRate: String,
    val dismissalText: String,
    val isHighlighted: Boolean
)

data class SpectatorBowlerLine(
    val name: String,
    val overs: String,
    val maidens: String,
    val runs: String,
    val wickets: String,
    val economy: String,
    val extrasSubtitle: String?
)

data class SpectatorPartnershipLine(
    val batter1: String,
    val batter1Runs: Int,
    val batter1Balls: Int,
    val batter2: String,
    val batter2Runs: Int,
    val batter2Balls: Int,
    val totalRuns: Int,
    val totalBalls: Int,
    val isUnbroken: Boolean
)

data class SpectatorInningsScorecard(
    val inningsNumber: Int,
    val battingTeam: String,
    val bowlingTeam: String,
    val totalRuns: String,
    val wickets: String,
    val oversDisplay: String,
    val crr: String,
    val isStarted: Boolean,
    val isCompleted: Boolean,
    val extrasSummary: String,
    val batters: List<SpectatorBatterLine>,
    val bowlers: List<SpectatorBowlerLine>,
    val fallOfWickets: List<String>,
    val partnerships: List<SpectatorPartnershipLine>
)

fun LiveMatchDoc.toInningsScorecard(inningsNumber: Int): SpectatorInningsScorecard {
    val isFirst = inningsNumber == 1
    val metaRaw = if (isFirst) innings1Meta else innings2Meta
    val metaParts = metaRaw.split("|")
    val rawBatters = if (isFirst) innings1BattingLines else innings2BattingLines
    val rawBowlers = if (isFirst) innings1BowlingLines else innings2BowlingLines
    val rawFow = if (isFirst) innings1FowLines else innings2FowLines
    val rawPartnerships = if (isFirst) innings1PartnershipLines else innings2PartnershipLines
    val rawExtras = if (isFirst) innings1Extras else innings2Extras

    val isCurrentInnings = currentInningsNumber.toInt() == inningsNumber
    val useLegacyFallback = rawBatters.isEmpty() && isCurrentInnings && battingCardLines.isNotEmpty()

    val effectiveBattersRaw = if (useLegacyFallback) battingCardLines else rawBatters
    val effectiveBowlersRaw = if (rawBowlers.isEmpty() && isCurrentInnings) bowlingCardLines else rawBowlers

    val battingTeamName = metaParts.getOrNull(0)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) battingTeam
        else if (isFirst) innings1Summary.substringBefore(":").trim().ifBlank { hostTeam }
        else innings2Summary.substringBefore(":").trim().ifBlank { visitorTeam }

    val bowlingTeamName = metaParts.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) bowlingTeam
        else if (battingTeamName.equals(hostTeam, ignoreCase = true)) visitorTeam else hostTeam

    val runsStr = metaParts.getOrNull(2)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) scoreRuns.toString() else "0"
    val wicketsStr = metaParts.getOrNull(3)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) scoreWickets.toString() else "0"
    val oversStr = metaParts.getOrNull(4)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) oversDisplay else "0.0"
    val crrStr = metaParts.getOrNull(5)?.takeIf { it.isNotBlank() }
        ?: if (isCurrentInnings) crr else "0.00"
    val started = metaParts.getOrNull(6)?.toBooleanStrictOrNull()
        ?: (isCurrentInnings || effectiveBattersRaw.isNotEmpty() || (if (isFirst) innings1Summary else innings2Summary).isNotBlank())
    val completed = metaParts.getOrNull(7)?.toBooleanStrictOrNull()
        ?: (matchStatus == "COMPLETED" || (!isFirst && currentInningsNumber == 2L && false) || (isFirst && currentInningsNumber == 2L))

    val parsedBatters = effectiveBattersRaw.mapNotNull { line ->
        val p = line.split("|")
        if (p.size < 7) null
        else {
            val highlighted = p.getOrNull(7)?.toBooleanStrictOrNull() ?: (p[0].endsWith("*") && !completed)
            SpectatorBatterLine(
                name = p[0],
                runs = p[1],
                balls = p[2],
                fours = p[3],
                sixes = p[4],
                strikeRate = p[5],
                dismissalText = p[6],
                isHighlighted = highlighted
            )
        }
    }

    val parsedBowlers = effectiveBowlersRaw.mapNotNull { line ->
        val p = line.split("|")
        if (p.size < 6) null
        else {
            val wides = p.getOrNull(6)?.toIntOrNull() ?: 0
            val noBalls = p.getOrNull(7)?.toIntOrNull() ?: 0
            val sub = if (wides > 0 || noBalls > 0) "WD: $wides • NB: $noBalls" else null
            SpectatorBowlerLine(
                name = p[0],
                overs = p[1],
                maidens = p[2],
                runs = p[3],
                wickets = p[4],
                economy = p[5],
                extrasSubtitle = sub
            )
        }
    }

    val parsedPartnerships = rawPartnerships.mapNotNull { line ->
        val p = line.split("|")
        if (p.size < 9) null
        else SpectatorPartnershipLine(
            batter1 = p[0],
            batter1Runs = p[1].toIntOrNull() ?: 0,
            batter1Balls = p[2].toIntOrNull() ?: 0,
            batter2 = p[3],
            batter2Runs = p[4].toIntOrNull() ?: 0,
            batter2Balls = p[5].toIntOrNull() ?: 0,
            totalRuns = p[6].toIntOrNull() ?: 0,
            totalBalls = p[7].toIntOrNull() ?: 0,
            isUnbroken = p[8].toBooleanStrictOrNull() ?: false
        )
    }

    return SpectatorInningsScorecard(
        inningsNumber = inningsNumber,
        battingTeam = battingTeamName,
        bowlingTeam = bowlingTeamName,
        totalRuns = runsStr,
        wickets = wicketsStr,
        oversDisplay = oversStr,
        crr = crrStr,
        isStarted = started,
        isCompleted = completed,
        extrasSummary = rawExtras.ifBlank { "0 (b 0, lb 0, wd 0, nb 0)" },
        batters = parsedBatters,
        bowlers = parsedBowlers,
        fallOfWickets = rawFow,
        partnerships = parsedPartnerships
    )
}

fun LiveMatchDoc.toWinProbabilitySummary(): WinProbabilitySummary {
    val inn1MetaParts = innings1Meta.split("|")
    val team1Name = inn1MetaParts.getOrNull(0)?.takeIf { it.isNotBlank() }
        ?: if (currentInningsNumber <= 1L) battingTeam.ifBlank { hostTeam }
        else bowlingTeam.ifBlank { hostTeam }
    val team2Name = inn1MetaParts.getOrNull(1)?.takeIf { it.isNotBlank() }
        ?: if (team1Name.equals(hostTeam, ignoreCase = true)) visitorTeam else hostTeam

    val oversTotal = totalOvers.toInt().coerceAtLeast(1)
    val par = WinProbabilityEngine.parRunRateForOvers(oversTotal)
    val crrVal = crr.toDoubleOrNull() ?: 0.0
    val rrrVal = rrr.takeIf { it.isNotBlank() }?.toDoubleOrNull()
    val decodedPoints = analyticsLines
        .filter { it.startsWith("W|") }
        .mapNotNull { WinProbabilityPoint.decode(it.removePrefix("W|")) }

    val legalBalls = WinProbabilityEngine.parseOversDisplayToLegalBalls(oversDisplay)
    val isSecondInn = currentInningsNumber >= 2L || targetRuns > 0L
    val isCompleted = matchStatus == "COMPLETED"

    val computedPoints = if (decodedPoints.isNotEmpty()) {
        decodedPoints
    } else {
        val inn1Crr = inn1MetaParts.getOrNull(5)?.toDoubleOrNull() ?: par
        val currentProb = if (isSecondInn && targetRuns > 0L) {
            WinProbabilityEngine.computeChaseTeam1Prob(
                runs = scoreRuns.toInt(),
                wickets = scoreWickets.toInt(),
                legalBalls = legalBalls,
                target = targetRuns.toInt(),
                totalOvers = oversTotal,
                maxWickets = 10,
                crr = crrVal,
                rrr = rrrVal,
                firstInningsCrr = inn1Crr
            )
        } else {
            WinProbabilityEngine.computeFirstInningsTeam1Prob(
                crr = crrVal,
                wickets = scoreWickets.toInt(),
                legalBalls = legalBalls,
                totalOvers = oversTotal,
                maxWickets = 10
            )
        }
        listOf(
            WinProbabilityPoint("Start", 50f, 0.0, if (isSecondInn) rrrVal else null, 1),
            WinProbabilityPoint(oversDisplay, currentProb, crrVal, rrrVal, currentInningsNumber.toInt())
        )
    }

    val latestProb = computedPoints.lastOrNull()?.team1WinProb ?: 50f
    return WinProbabilitySummary(
        team1Name = team1Name,
        team2Name = team2Name,
        team1WinProb = latestProb,
        currentCrr = crrVal,
        requiredRrr = rrrVal,
        parRunRate = par,
        isSecondInnings = isSecondInn,
        isMatchCompleted = isCompleted,
        points = computedPoints
    )
}

fun LiveMatchDoc.toInningsState(inningsNumber: Int): InningsState {
    val scorecard = toInningsScorecard(inningsNumber)
    val isFirst = inningsNumber == 1
    val overPrefix = if (isFirst) "O1|" else "O2|"
    val shotPrefix = if (isFirst) "S1|" else "S2|"
    val distPrefix = if (isFirst) "D1|" else "D2|"

    val rawOvers = analyticsLines.filter { it.startsWith(overPrefix) }.map { it.removePrefix(overPrefix) }
    val rawShots = analyticsLines.filter { it.startsWith(shotPrefix) }.map { it.removePrefix(shotPrefix) }
    val rawRunDist = analyticsLines.firstOrNull { it.startsWith(distPrefix) }?.removePrefix(distPrefix).orEmpty()

    var syntheticBallId = inningsNumber * 100000L
    val parsedOvers = rawOvers.mapNotNull { line ->
        val p = line.split("|")
        if (p.size < 7) null
        else {
            val overNum = p[0].toIntOrNull() ?: return@mapNotNull null
            val bowlerName = p[1]
            val runsInOver = p[2].toIntOrNull() ?: 0
            val wicketsInOver = p[3].toIntOrNull() ?: 0
            val cumRuns = p[4].toIntOrNull() ?: 0
            val cumWickets = p[5].toIntOrNull() ?: 0
            val completed = p[6].toBooleanStrictOrNull() ?: true
            val packedBalls = p.getOrNull(7).orEmpty()
            val chips = if (packedBalls.isBlank()) emptyList() else {
                packedBalls.split(";").mapNotNull { bRaw ->
                    val bp = bRaw.split("~")
                    if (bp.size < 11) null
                    else {
                        syntheticBallId += 1L
                        BallChip(
                            eventId = syntheticBallId,
                            label = bp[0],
                            totalRuns = bp[1].toIntOrNull() ?: 0,
                            isWicket = bp[2] == "1",
                            isWide = bp[3] == "1",
                            isNoBall = bp[4] == "1",
                            isBye = bp[5] == "1",
                            isLegBye = bp[6] == "1",
                            isFour = bp[7] == "1",
                            isSix = bp[8] == "1",
                            isDot = bp[9] == "1",
                            overNumber = overNum,
                            ballInOver = bp[10].toIntOrNull() ?: 1
                        )
                    }
                }
            }
            OverSummary(
                overNumber = overNum,
                bowlerName = bowlerName,
                balls = chips,
                runsInOver = runsInOver,
                wicketsInOver = wicketsInOver,
                cumulativeRuns = cumRuns,
                cumulativeWickets = cumWickets,
                isCompleted = completed
            )
        }
    }

    val parsedShots = rawShots.mapIndexedNotNull { idx, line ->
        val p = line.split("|")
        if (p.size < 9) null
        else ShotRecord(
            eventId = inningsNumber * 1000000L + idx.toLong(),
            inningsNumber = inningsNumber,
            overBallDisplay = p[0],
            batterName = p[1],
            bowlerName = p[2],
            runs = p[3].toIntOrNull() ?: 0,
            shotX = p[4].toFloatOrNull() ?: 0f,
            shotY = p[5].toFloatOrNull() ?: 0f,
            shotZone = p[6],
            isFour = p[7] == "1",
            isSix = p[8] == "1"
        )
    }

    val distParts = rawRunDist.split("|")
    val runDistribution = if (distParts.size >= 5) {
        RunDistribution(
            dots = distParts[0].toIntOrNull() ?: 0,
            singlesTwosThreesRuns = distParts[1].toIntOrNull() ?: 0,
            foursRuns = distParts[2].toIntOrNull() ?: 0,
            sixesRuns = distParts[3].toIntOrNull() ?: 0,
            extrasRuns = distParts[4].toIntOrNull() ?: 0
        )
    } else {
        RunDistribution()
    }

    return InningsState(
        inningsNumber = inningsNumber,
        battingTeam = scorecard.battingTeam,
        bowlingTeam = scorecard.bowlingTeam,
        totalRuns = scorecard.totalRuns.toIntOrNull() ?: 0,
        wickets = scorecard.wickets.toIntOrNull() ?: 0,
        legalBalls = WinProbabilityEngine.parseOversDisplayToLegalBalls(scorecard.oversDisplay),
        maxOvers = totalOvers.toInt().coerceAtLeast(1),
        overs = parsedOvers,
        shots = parsedShots,
        runDistribution = runDistribution,
        isStarted = scorecard.isStarted,
        isCompleted = scorecard.isCompleted
    )
}

@Keep
@IgnoreExtraProperties
data class PlayerCareerDoc(
    @DocumentId val docId: String = "",
    val adminId: String = "",
    val shareCode: String = "",
    val playerName: String = "",
    val teamName: String = "",
    val role: String = "All-Rounder",
    val photoBase64: String = "",
    val matchesPlayed: Long = 0L,
    val battingStatsLine: String = "",
    val bowlingStatsLine: String = "",
    val recentPerformancesLines: List<String> = emptyList(),
    val careerShotLines: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    companion object {
        fun cleanName(rawName: String): String {
            return rawName.trim().removeSuffix("*").trim()
        }

        fun docIdFor(playerName: String): String {
            val clean = cleanName(playerName)
            val norm = clean.lowercase()
            val slug = norm
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(48)
                .ifEmpty { "player" }
            val hash = norm.hashCode().toUInt().toString(16)
            return "player_${slug}_${hash}".take(120)
        }
    }
}

data class PlayerCareerPayload(
    val docId: String,
    val fields: Map<String, Any>,
    val signature: Int
)

object PlayerCareerMapper {
    private fun String.safePipe(): String = replace("|", "/").replace("\n", " ").trim()

    fun buildPayload(
        adminId: String,
        shareCode: String,
        playerName: String,
        teamName: String,
        role: String,
        photoBase64: String,
        stats: PlayerCareerStats?
    ): PlayerCareerPayload {
        val cleanName = PlayerCareerDoc.cleanName(playerName).ifEmpty { "Player" }.take(80)
        val cleanTeam = (stats?.teamName?.takeIf { it.isNotBlank() } ?: teamName).trim().ifEmpty { "Squad" }.take(80)
        val cleanRole = role.trim().ifEmpty { "All-Rounder" }.take(40)
        val cleanPhoto = photoBase64.take(32000)
        val matchesPlayed = (stats?.matchesPlayed ?: 0).coerceIn(0, 10000).toLong()

        val battingLine = if (stats == null) {
            "0|0|0|0|0|0|0|0|0|0|0|0"
        } else {
            listOf(
                stats.inningsBatted,
                stats.notOuts,
                stats.runsScored,
                stats.ballsFaced,
                stats.fours,
                stats.sixes,
                stats.highestScore,
                if (stats.highestNotOut) 1 else 0,
                stats.thirties,
                stats.fifties,
                stats.hundreds,
                stats.ducks
            ).joinToString("|").take(120)
        }

        val bowlingLine = if (stats == null) {
            "0|0|0|0|0|0|0|0|0|0|0|-1"
        } else {
            val bestRuns = if (stats.bestBowlingRuns == Int.MAX_VALUE) -1 else stats.bestBowlingRuns
            listOf(
                stats.inningsBowled,
                stats.ballsBowled,
                stats.runsConceded,
                stats.wicketsTaken,
                stats.maidens,
                stats.dotBallsBowled,
                stats.widesBowled,
                stats.noBallsBowled,
                stats.threeWicketHauls,
                stats.fiveWicketHauls,
                stats.bestBowlingWickets,
                bestRuns
            ).joinToString("|").take(120)
        }

        val perfLines = stats?.recentPerformances.orEmpty().take(30).map { p ->
            listOf(
                p.matchId.toString(),
                p.matchTitle.safePipe().take(60),
                p.opponentTeam.safePipe().take(40),
                p.matchDate.toString(),
                if (p.batted) "1" else "0",
                p.battingRuns.toString(),
                p.battingBalls.toString(),
                if (p.isOut) "1" else "0",
                p.battingFours.toString(),
                p.battingSixes.toString(),
                if (p.bowled) "1" else "0",
                p.bowlingLegalBalls.toString(),
                p.bowlingRuns.toString(),
                p.bowlingWickets.toString(),
                p.resultSummary.safePipe().take(70)
            ).joinToString("|").take(260)
        }

        val shotLines = stats?.careerShots.orEmpty().takeLast(150).map { s ->
            val f = if (s.isFour) 1 else 0
            val sx = if (s.isSix) 1 else 0
            "${s.overBallDisplay.safePipe()}|${s.batterName.safePipe()}|${s.bowlerName.safePipe()}|${s.runs}|${String.format(Locale.US, "%.3f", s.shotX)}|${String.format(Locale.US, "%.3f", s.shotY)}|${s.shotZone.safePipe()}|$f|$sx".take(160)
        }

        var sig = cleanName.hashCode()
        sig = 31 * sig + cleanTeam.hashCode()
        sig = 31 * sig + cleanRole.hashCode()
        sig = 31 * sig + cleanPhoto.hashCode()
        sig = 31 * sig + matchesPlayed.hashCode()
        sig = 31 * sig + battingLine.hashCode()
        sig = 31 * sig + bowlingLine.hashCode()
        sig = 31 * sig + perfLines.hashCode()
        sig = 31 * sig + shotLines.hashCode()

        val fields = mapOf(
            "adminId" to adminId,
            "shareCode" to shareCode,
            "playerName" to cleanName,
            "teamName" to cleanTeam,
            "role" to cleanRole,
            "photoBase64" to cleanPhoto,
            "matchesPlayed" to matchesPlayed,
            "battingStatsLine" to battingLine,
            "bowlingStatsLine" to bowlingLine,
            "recentPerformancesLines" to perfLines,
            "careerShotLines" to shotLines,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return PlayerCareerPayload(
            docId = PlayerCareerDoc.docIdFor(cleanName),
            fields = fields,
            signature = sig
        )
    }
}

fun PlayerCareerDoc.toDomainCareerStats(): PlayerCareerStats {
    val bat = battingStatsLine.split("|")
    val bowl = bowlingStatsLine.split("|")

    val parsedPerfs = recentPerformancesLines.mapNotNull { line ->
        val p = line.split("|")
        if (p.size < 14) null
        else PlayerMatchPerformance(
            matchId = p[0].toLongOrNull() ?: 0L,
            matchTitle = p[1],
            opponentTeam = p[2],
            matchDate = p[3].toLongOrNull() ?: 0L,
            batted = p[4] == "1",
            battingRuns = p[5].toIntOrNull() ?: 0,
            battingBalls = p[6].toIntOrNull() ?: 0,
            isOut = p[7] == "1",
            battingFours = p[8].toIntOrNull() ?: 0,
            battingSixes = p[9].toIntOrNull() ?: 0,
            bowled = p[10] == "1",
            bowlingLegalBalls = p[11].toIntOrNull() ?: 0,
            bowlingRuns = p[12].toIntOrNull() ?: 0,
            bowlingWickets = p[13].toIntOrNull() ?: 0,
            resultSummary = p.getOrNull(14).orEmpty()
        )
    }

    val parsedShots = careerShotLines.mapIndexedNotNull { idx, line ->
        val p = line.split("|")
        if (p.size < 9) null
        else ShotRecord(
            eventId = 9000000L + idx.toLong(),
            inningsNumber = 1,
            overBallDisplay = p[0],
            batterName = p[1],
            bowlerName = p[2],
            runs = p[3].toIntOrNull() ?: 0,
            shotX = p[4].toFloatOrNull() ?: 0f,
            shotY = p[5].toFloatOrNull() ?: 0f,
            shotZone = p[6],
            isFour = p[7] == "1",
            isSix = p[8] == "1"
        )
    }

    val bestRunsRaw = bowl.getOrNull(11)?.toIntOrNull() ?: -1
    return PlayerCareerStats(
        playerName = playerName,
        teamName = teamName,
        matchesPlayed = matchesPlayed.toInt(),
        inningsBatted = bat.getOrNull(0)?.toIntOrNull() ?: 0,
        notOuts = bat.getOrNull(1)?.toIntOrNull() ?: 0,
        runsScored = bat.getOrNull(2)?.toIntOrNull() ?: 0,
        ballsFaced = bat.getOrNull(3)?.toIntOrNull() ?: 0,
        fours = bat.getOrNull(4)?.toIntOrNull() ?: 0,
        sixes = bat.getOrNull(5)?.toIntOrNull() ?: 0,
        highestScore = bat.getOrNull(6)?.toIntOrNull() ?: 0,
        highestNotOut = bat.getOrNull(7) == "1",
        thirties = bat.getOrNull(8)?.toIntOrNull() ?: 0,
        fifties = bat.getOrNull(9)?.toIntOrNull() ?: 0,
        hundreds = bat.getOrNull(10)?.toIntOrNull() ?: 0,
        ducks = bat.getOrNull(11)?.toIntOrNull() ?: 0,
        inningsBowled = bowl.getOrNull(0)?.toIntOrNull() ?: 0,
        ballsBowled = bowl.getOrNull(1)?.toIntOrNull() ?: 0,
        runsConceded = bowl.getOrNull(2)?.toIntOrNull() ?: 0,
        wicketsTaken = bowl.getOrNull(3)?.toIntOrNull() ?: 0,
        maidens = bowl.getOrNull(4)?.toIntOrNull() ?: 0,
        dotBallsBowled = bowl.getOrNull(5)?.toIntOrNull() ?: 0,
        widesBowled = bowl.getOrNull(6)?.toIntOrNull() ?: 0,
        noBallsBowled = bowl.getOrNull(7)?.toIntOrNull() ?: 0,
        threeWicketHauls = bowl.getOrNull(8)?.toIntOrNull() ?: 0,
        fiveWicketHauls = bowl.getOrNull(9)?.toIntOrNull() ?: 0,
        bestBowlingWickets = bowl.getOrNull(10)?.toIntOrNull() ?: 0,
        bestBowlingRuns = if (bestRunsRaw < 0) Int.MAX_VALUE else bestRunsRaw,
        recentPerformances = parsedPerfs,
        careerShots = parsedShots
    )
}

fun LiveMatchDoc.buildFallbackCareerStats(rawPlayerName: String): PlayerCareerStats? {
    val cleanName = PlayerCareerDoc.cleanName(rawPlayerName)
    if (cleanName.isBlank()) return null

    val inn1 = toInningsScorecard(1)
    val inn2 = toInningsScorecard(2)
    val inn1State = toInningsState(1)
    val inn2State = toInningsState(2)

    val bat1 = inn1.batters.firstOrNull { PlayerCareerDoc.cleanName(it.name).equals(cleanName, ignoreCase = true) }
    val bat2 = inn2.batters.firstOrNull { PlayerCareerDoc.cleanName(it.name).equals(cleanName, ignoreCase = true) }
    val bowl1 = inn1.bowlers.firstOrNull { PlayerCareerDoc.cleanName(it.name).equals(cleanName, ignoreCase = true) }
    val bowl2 = inn2.bowlers.firstOrNull { PlayerCareerDoc.cleanName(it.name).equals(cleanName, ignoreCase = true) }

    val batEntries = listOfNotNull(
        bat1?.let { inn1.battingTeam to it },
        bat2?.let { inn2.battingTeam to it }
    )
    val bowlEntries = listOfNotNull(
        bowl1?.let { inn1.bowlingTeam to it },
        bowl2?.let { inn2.bowlingTeam to it }
    )
    val shots = (inn1State.shots + inn2State.shots)
        .filter { it.batterName.equals(cleanName, ignoreCase = true) }

    if (batEntries.isEmpty() && bowlEntries.isEmpty() && shots.isEmpty()) {
        return null
    }

    val teamName = batEntries.firstOrNull()?.first
        ?: bowlEntries.firstOrNull()?.first
        ?: hostTeam

    val runsScored = batEntries.sumOf { it.second.runs.toIntOrNull() ?: 0 }
    val ballsFaced = batEntries.sumOf { it.second.balls.toIntOrNull() ?: 0 }
    val fours = batEntries.sumOf { it.second.fours.toIntOrNull() ?: 0 }
    val sixes = batEntries.sumOf { it.second.sixes.toIntOrNull() ?: 0 }
    val notOuts = batEntries.count { it.second.dismissalText.equals("not out", ignoreCase = true) }
    val highestPair = batEntries.maxByOrNull { it.second.runs.toIntOrNull() ?: 0 }?.second
    val highestRuns = highestPair?.runs?.toIntOrNull() ?: 0
    val highestNotOut = highestPair?.dismissalText.equals("not out", ignoreCase = true)

    var ballsBowled = 0
    var runsConceded = 0
    var wicketsTaken = 0
    var maidens = 0
    bowlEntries.forEach { (_, bw) ->
        ballsBowled += WinProbabilityEngine.parseOversDisplayToLegalBalls(bw.overs)
        runsConceded += bw.runs.toIntOrNull() ?: 0
        wicketsTaken += bw.wickets.toIntOrNull() ?: 0
        maidens += bw.maidens.toIntOrNull() ?: 0
    }

    val opponent = if (teamName.equals(hostTeam, ignoreCase = true)) visitorTeam else hostTeam
    val perf = PlayerMatchPerformance(
        matchId = localMatchId,
        matchTitle = "$hostTeam vs $visitorTeam",
        opponentTeam = opponent,
        matchDate = updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
        batted = batEntries.isNotEmpty(),
        battingRuns = runsScored,
        battingBalls = ballsFaced,
        isOut = notOuts == 0 && batEntries.isNotEmpty(),
        battingFours = fours,
        battingSixes = sixes,
        bowled = bowlEntries.isNotEmpty(),
        bowlingLegalBalls = ballsBowled,
        bowlingRuns = runsConceded,
        bowlingWickets = wicketsTaken,
        resultSummary = resultSummary.ifBlank { chaseSummary }
    )

    return PlayerCareerStats(
        playerName = cleanName,
        teamName = teamName,
        matchesPlayed = 1,
        inningsBatted = batEntries.size,
        runsScored = runsScored,
        ballsFaced = ballsFaced,
        fours = fours,
        sixes = sixes,
        highestScore = highestRuns,
        highestNotOut = highestNotOut,
        thirties = if (highestRuns in 30..49) 1 else 0,
        fifties = if (highestRuns in 50..99) 1 else 0,
        hundreds = if (highestRuns >= 100) 1 else 0,
        ducks = if (batEntries.isNotEmpty() && runsScored == 0 && notOuts == 0) 1 else 0,
        notOuts = notOuts,
        inningsBowled = bowlEntries.size,
        ballsBowled = ballsBowled,
        runsConceded = runsConceded,
        wicketsTaken = wicketsTaken,
        maidens = maidens,
        threeWicketHauls = if (wicketsTaken in 3..4) 1 else 0,
        fiveWicketHauls = if (wicketsTaken >= 5) 1 else 0,
        bestBowlingWickets = wicketsTaken,
        bestBowlingRuns = if (bowlEntries.isNotEmpty()) runsConceded else Int.MAX_VALUE,
        recentPerformances = listOf(perf),
        careerShots = shots
    )
}

data class LiveMatchPlayerHint(
    val playerName: String,
    val teamName: String,
    val role: String,
    val quickSummary: String
)

fun LiveMatchDoc.extractPlayerHints(): List<LiveMatchPlayerHint> {
    val map = linkedMapOf<String, LiveMatchPlayerHint>()
    fun upsert(rawName: String, team: String, role: String, summary: String) {
        val clean = PlayerCareerDoc.cleanName(rawName)
        if (clean.isBlank()) return
        val key = clean.lowercase()
        val prev = map[key]
        if (prev == null) {
            map[key] = LiveMatchPlayerHint(
                playerName = clean,
                teamName = team.ifBlank { hostTeam },
                role = role,
                quickSummary = summary
            )
        } else {
            val combinedSummary = if (prev.quickSummary.isBlank()) summary
            else if (summary.isBlank() || prev.quickSummary == summary) prev.quickSummary
            else "${prev.quickSummary} • $summary"
            val resolvedRole = if (prev.role == role) prev.role else "All-Rounder"
            map[key] = prev.copy(
                teamName = prev.teamName.ifBlank { team },
                role = resolvedRole,
                quickSummary = combinedSummary
            )
        }
    }

    val inn1 = toInningsScorecard(1)
    val inn2 = toInningsScorecard(2)
    listOf(inn1, inn2).filter { it.isStarted }.forEach { inn ->
        inn.batters.forEach { b ->
            upsert(
                rawName = b.name,
                team = inn.battingTeam,
                role = "Batter",
                summary = "${b.runs}(${b.balls})"
            )
        }
        inn.bowlers.forEach { bw ->
            upsert(
                rawName = bw.name,
                team = inn.bowlingTeam,
                role = "Bowler",
                summary = "${bw.wickets}/${bw.runs} (${bw.overs})"
            )
        }
    }
    upsert(strikerName, battingTeam, "Batter", strikerStats)
    upsert(nonStrikerName, battingTeam, "Batter", nonStrikerStats)
    upsert(bowlerName, bowlingTeam, "Bowler", bowlerStats)
    return map.values.toList()
}




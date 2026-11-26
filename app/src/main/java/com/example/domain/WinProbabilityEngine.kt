package com.example.domain

import kotlin.math.exp
import kotlin.math.roundToInt

data class WinProbabilityPoint(
    val label: String,
    val team1WinProb: Float, // 0f..100f for innings1 batting team (team1)
    val crr: Double,
    val rrr: Double?,
    val inningsNumber: Int,
    val hasWicket: Boolean = false
) {
    val team2WinProb: Float
        get() = (100f - team1WinProb).coerceIn(0f, 100f)

    fun encode(): String {
        val safeLabel = label.replace("|", "/").take(12)
        val rrrText = rrr?.let { "%.2f".format(it) }.orEmpty()
        return "$safeLabel|${"%.1f".format(team1WinProb)}|${"%.2f".format(crr)}|$rrrText|$inningsNumber|$hasWicket"
    }

    companion object {
        fun decode(raw: String): WinProbabilityPoint? {
            val parts = raw.split("|")
            if (parts.size < 5) return null
            val prob = parts[1].toFloatOrNull()?.coerceIn(0f, 100f) ?: return null
            val crr = parts[2].toDoubleOrNull() ?: 0.0
            val rrr = parts[3].takeIf { it.isNotBlank() }?.toDoubleOrNull()
            val inn = parts[4].toIntOrNull()?.coerceIn(1, 2) ?: 1
            val hasWkt = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: false
            return WinProbabilityPoint(
                label = parts[0],
                team1WinProb = prob,
                crr = crr,
                rrr = rrr,
                inningsNumber = inn,
                hasWicket = hasWkt
            )
        }
    }
}

data class WinProbabilitySummary(
    val team1Name: String,
    val team2Name: String,
    val team1WinProb: Float,
    val currentCrr: Double,
    val requiredRrr: Double?,
    val parRunRate: Double,
    val isSecondInnings: Boolean,
    val isMatchCompleted: Boolean,
    val points: List<WinProbabilityPoint>
) {
    val team2WinProb: Float
        get() = (100f - team1WinProb).coerceIn(0f, 100f)
    val team1PercentInt: Int
        get() = team1WinProb.roundToInt().coerceIn(0, 100)
    val team2PercentInt: Int
        get() = (100 - team1PercentInt).coerceIn(0, 100)
}

object WinProbabilityEngine {

    fun parRunRateForOvers(totalOvers: Int): Double = when {
        totalOvers <= 8 -> 8.5
        totalOvers <= 12 -> 8.0
        totalOvers <= 20 -> 7.5
        else -> 5.5
    }

    /**
     * Computes Team 1 (1st-innings batting side) win probability (0f..100f) during 1st innings
     * using current run rate (CRR) vs format Par Run Rate and wickets lost.
     */
    fun computeFirstInningsTeam1Prob(
        crr: Double,
        wickets: Int,
        legalBalls: Int,
        totalOvers: Int,
        maxWickets: Int
    ): Float {
        if (legalBalls <= 0) return 50f
        val par = parRunRateForOvers(totalOvers)
        val totalBalls = (totalOvers * 6).coerceAtLeast(6)
        val progress = (legalBalls.toDouble() / totalBalls.toDouble()).coerceIn(0.0, 1.0)
        val sampleWeight = (legalBalls.toDouble() / 6.0).coerceIn(0.25, 1.0)
        val rateEdge = crr - par
        val wicketFactor = (wickets.toDouble() / maxWickets.coerceAtLeast(1).toDouble()) * 10.0
        val z = (rateEdge * 0.34 * (0.45 + 0.65 * progress) * sampleWeight) - (wicketFactor * 0.16) + 0.12
        val raw = (1.0 / (1.0 + exp(-z))) * 100.0
        return raw.toFloat().coerceIn(8f, 92f)
    }

    /**
     * Computes Team 1 (defending side) win probability (0f..100f) during 2nd innings chase
     * directly from Current Run Rate (CRR) vs Required Run Rate (RRR), wickets in hand, and balls left.
     */
    fun computeChaseTeam1Prob(
        runs: Int,
        wickets: Int,
        legalBalls: Int,
        target: Int,
        totalOvers: Int,
        maxWickets: Int,
        crr: Double,
        rrr: Double?,
        firstInningsCrr: Double
    ): Float {
        if (target <= 0) return 50f
        if (runs >= target) return 0f // Chasing team (Team 2) won
        val totalBalls = (totalOvers * 6).coerceAtLeast(6)
        val ballsLeft = (totalBalls - legalBalls).coerceAtLeast(0)
        if (wickets >= maxWickets || ballsLeft <= 0) {
            return if (runs == target - 1) 50f else 100f
        }

        val initialRrr = target.toDouble() / totalOvers.coerceAtLeast(1).toDouble()
        val effectiveRrr = rrr ?: initialRrr
        if (legalBalls <= 0) {
            val parDiff = parRunRateForOvers(totalOvers) - initialRrr
            val startChase = (1.0 / (1.0 + exp(-(parDiff * 0.25)))) * 100.0
            return (100.0 - startChase).toFloat().coerceIn(25f, 75f)
        }

        val progress = (legalBalls.toDouble() / totalBalls.toDouble()).coerceIn(0.0, 1.0)
        val sampleWeight = (legalBalls.toDouble() / 6.0).coerceIn(0.30, 1.0)
        val urgency = 0.50 + 0.85 * progress
        val effectiveCrr = if (legalBalls < 3) (crr * 0.65 + firstInningsCrr * 0.35) else crr
        val rateEdge = effectiveCrr - effectiveRrr
        val wicketRatio = (wickets.toDouble() / maxWickets.coerceAtLeast(1).toDouble()) * 10.0

        // Positive z favors Team 2 (chasing side)
        val zChase = (rateEdge * 0.44 * urgency * sampleWeight) - (wicketRatio * 0.18) + 0.12
        val team2Prob = ((1.0 / (1.0 + exp(-zChase))) * 100.0).coerceIn(1.0, 99.0)
        return (100.0 - team2Prob).toFloat().coerceIn(1f, 99f)
    }

    fun calculateFromSnapshot(snapshot: MatchSnapshot): WinProbabilitySummary {
        val team1 = snapshot.innings1.battingTeam.ifBlank { snapshot.hostTeam }
        val team2 = snapshot.innings1.bowlingTeam.ifBlank { snapshot.visitorTeam }
        val totalOvers = snapshot.rules.totalOvers.coerceAtLeast(1)
        val par = parRunRateForOvers(totalOvers)
        val inn1 = snapshot.innings1
        val inn2 = snapshot.innings2

        val points = mutableListOf<WinProbabilityPoint>()
        points.add(
            WinProbabilityPoint(
                label = "Start",
                team1WinProb = 50f,
                crr = 0.0,
                rrr = null,
                inningsNumber = 1
            )
        )

        // 1st innings over-by-over progression
        inn1.overs.forEach { ov ->
            val balls = ov.overNumber * 6
            val crrAtOver = if (balls > 0) ov.cumulativeRuns.toDouble() / (balls / 6.0) else 0.0
            val prob = computeFirstInningsTeam1Prob(
                crr = crrAtOver,
                wickets = ov.cumulativeWickets,
                legalBalls = balls,
                totalOvers = totalOvers,
                maxWickets = inn1.maxWickets
            )
            points.add(
                WinProbabilityPoint(
                    label = "${ov.overNumber}.0",
                    team1WinProb = prob,
                    crr = crrAtOver,
                    rrr = null,
                    inningsNumber = 1,
                    hasWicket = ov.wicketsInOver > 0
                )
            )
        }

        // Current live 1st innings point (if still in progress in an unfinished over)
        if (inn1.isStarted && !inn1.isCompleted && (inn1.legalBalls % 6 != 0 || inn1.overs.isEmpty()) && inn1.legalBalls > 0) {
            val prob = computeFirstInningsTeam1Prob(
                crr = inn1.currentRunRate,
                wickets = inn1.wickets,
                legalBalls = inn1.legalBalls,
                totalOvers = totalOvers,
                maxWickets = inn1.maxWickets
            )
            points.add(
                WinProbabilityPoint(
                    label = inn1.oversDisplay,
                    team1WinProb = prob,
                    crr = inn1.currentRunRate,
                    rrr = null,
                    inningsNumber = 1,
                    hasWicket = inn1.currentOverBalls.any { it.isWicket }
                )
            )
        }

        // 2nd innings chase progression (CRR vs RRR)
        if (inn2.isStarted) {
            val target = inn2.target ?: (inn1.totalRuns + 1)
            val totalBalls = totalOvers * 6
            val inn1Crr = inn1.currentRunRate.takeIf { it > 0.0 } ?: par

            val startChaseProb = computeChaseTeam1Prob(
                runs = 0,
                wickets = 0,
                legalBalls = 0,
                target = target,
                totalOvers = totalOvers,
                maxWickets = inn2.maxWickets,
                crr = 0.0,
                rrr = target.toDouble() / totalOvers.toDouble(),
                firstInningsCrr = inn1Crr
            )
            points.add(
                WinProbabilityPoint(
                    label = "Inn2",
                    team1WinProb = startChaseProb,
                    crr = 0.0,
                    rrr = target.toDouble() / totalOvers.toDouble(),
                    inningsNumber = 2
                )
            )

            inn2.overs.forEach { ov ->
                val balls = ov.overNumber * 6
                val crrAtOver = if (balls > 0) ov.cumulativeRuns.toDouble() / (balls / 6.0) else 0.0
                val needed = (target - ov.cumulativeRuns).coerceAtLeast(0)
                val ballsLeft = (totalBalls - balls).coerceAtLeast(0)
                val rrrAtOver = when {
                    needed <= 0 -> 0.0
                    ballsLeft <= 0 -> null
                    else -> needed.toDouble() / (ballsLeft / 6.0)
                }
                val prob = computeChaseTeam1Prob(
                    runs = ov.cumulativeRuns,
                    wickets = ov.cumulativeWickets,
                    legalBalls = balls,
                    target = target,
                    totalOvers = totalOvers,
                    maxWickets = inn2.maxWickets,
                    crr = crrAtOver,
                    rrr = rrrAtOver,
                    firstInningsCrr = inn1Crr
                )
                points.add(
                    WinProbabilityPoint(
                        label = "${ov.overNumber}.0",
                        team1WinProb = prob,
                        crr = crrAtOver,
                        rrr = rrrAtOver,
                        inningsNumber = 2,
                        hasWicket = ov.wicketsInOver > 0
                    )
                )
            }

            // Live unfinished over or final ball in 2nd innings
            if (inn2.legalBalls % 6 != 0 || inn2.isCompleted || snapshot.isMatchCompleted) {
                val lastLabel = points.lastOrNull()?.label
                if (lastLabel != inn2.oversDisplay || snapshot.isMatchCompleted) {
                    val liveProb = computeChaseTeam1Prob(
                        runs = inn2.totalRuns,
                        wickets = inn2.wickets,
                        legalBalls = inn2.legalBalls,
                        target = target,
                        totalOvers = totalOvers,
                        maxWickets = inn2.maxWickets,
                        crr = inn2.currentRunRate,
                        rrr = inn2.requiredRunRate,
                        firstInningsCrr = inn1Crr
                    )
                    val resolvedProb = if (snapshot.isMatchCompleted) {
                        when {
                            snapshot.winnerTeam.equals(team1, ignoreCase = true) -> 100f
                            snapshot.winnerTeam.equals(team2, ignoreCase = true) -> 0f
                            else -> 50f
                        }
                    } else {
                        liveProb
                    }
                    points.add(
                        WinProbabilityPoint(
                            label = if (snapshot.isMatchCompleted) "Final" else inn2.oversDisplay,
                            team1WinProb = resolvedProb,
                            crr = inn2.currentRunRate,
                            rrr = inn2.requiredRunRate,
                            inningsNumber = 2,
                            hasWicket = inn2.currentOverBalls.any { it.isWicket }
                        )
                    )
                }
            }
        }

        val activeInn = snapshot.currentInnings
        val finalTeam1Prob = if (snapshot.isMatchCompleted) {
            when {
                snapshot.winnerTeam.equals(team1, ignoreCase = true) -> 100f
                snapshot.winnerTeam.equals(team2, ignoreCase = true) -> 0f
                else -> 50f
            }
        } else {
            points.lastOrNull()?.team1WinProb ?: 50f
        }

        return WinProbabilitySummary(
            team1Name = team1,
            team2Name = team2,
            team1WinProb = finalTeam1Prob,
            currentCrr = activeInn.currentRunRate,
            requiredRrr = activeInn.requiredRunRate,
            parRunRate = par,
            isSecondInnings = inn2.isStarted,
            isMatchCompleted = snapshot.isMatchCompleted,
            points = points
        )
    }

    fun parseOversDisplayToLegalBalls(oversDisplay: String): Int {
        val parts = oversDisplay.trim().split(".")
        val completedOvers = parts.getOrNull(0)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val balls = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 5) ?: 0
        return completedOvers * 6 + balls
    }
}

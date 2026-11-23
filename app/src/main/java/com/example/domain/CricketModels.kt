package com.example.domain

enum class DismissalType(val label: String, val shortLabel: String, val creditBowler: Boolean, val needsFielder: Boolean) {
    BOWLED("Bowled", "b", true, false),
    CAUGHT("Caught", "c", true, true),
    LBW("LBW", "lbw", true, false),
    RUN_OUT("Run Out", "run out", false, true),
    STUMPED("Stumped", "st", true, true),
    HIT_WICKET("Hit Wicket", "hit wkt", true, false),
    RETIRED_OUT("Retired Out", "retd out", false, false);

    companion object {
        fun fromName(name: String?): DismissalType =
            entries.firstOrNull { it.name == name } ?: BOWLED
    }
}

enum class EventType {
    START_INNINGS,
    DELIVERY,
    NEW_BATSMAN,
    NEW_BOWLER,
    SWAP_STRIKE,
    RETIRE_BATTER,
    END_INNINGS
}

data class MatchRules(
    val totalOvers: Int = 10,
    val playersPerTeam: Int = 11,
    val wideReball: Boolean = true,
    val wideRuns: Int = 1,
    val noBallReball: Boolean = true,
    val noBallRuns: Int = 1,
    val lastManStands: Boolean = false
)

data class BatterStats(
    val name: String,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val isOut: Boolean = false,
    val isRetiredHurt: Boolean = false,
    val dismissalText: String = "not out",
    val battingOrder: Int = 0
) {
    val strikeRate: Double
        get() = if (balls > 0) (runs.toDouble() / balls) * 100.0 else 0.0
}

data class BowlerStats(
    val name: String,
    val legalBalls: Int = 0,
    val maidens: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val wides: Int = 0,
    val noBalls: Int = 0,
    val dotBalls: Int = 0
) {
    val oversDisplay: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"

    val economy: Double
        get() = if (legalBalls > 0) (runsConceded.toDouble() / (legalBalls / 6.0)) else 0.0
}

data class BallChip(
    val eventId: Long,
    val label: String,
    val totalRuns: Int,
    val isWicket: Boolean,
    val isWide: Boolean,
    val isNoBall: Boolean,
    val isBye: Boolean,
    val isLegBye: Boolean,
    val isFour: Boolean,
    val isSix: Boolean,
    val isDot: Boolean,
    val overNumber: Int,
    val ballInOver: Int,
    val shotZone: String? = null
)

data class ShotRecord(
    val eventId: Long,
    val inningsNumber: Int,
    val overBallDisplay: String,
    val batterName: String,
    val bowlerName: String,
    val runs: Int,
    val shotX: Float,
    val shotY: Float,
    val shotZone: String,
    val isFour: Boolean,
    val isSix: Boolean
)

data class OverSummary(
    val overNumber: Int, // 1-indexed
    val bowlerName: String,
    val balls: List<BallChip>,
    val runsInOver: Int,
    val wicketsInOver: Int,
    val cumulativeRuns: Int,
    val cumulativeWickets: Int,
    val isCompleted: Boolean
)

data class FallOfWicket(
    val wicketNumber: Int,
    val runsAtFall: Int,
    val batterName: String,
    val overDisplay: String
)

data class Partnership(
    val batter1: String,
    val batter2: String,
    val runs: Int,
    val balls: Int,
    val batter1Runs: Int,
    val batter1Balls: Int,
    val batter2Runs: Int,
    val batter2Balls: Int,
    val isUnbroken: Boolean
)

data class ExtrasBreakdown(
    val wides: Int = 0,
    val noBalls: Int = 0,
    val byes: Int = 0,
    val legByes: Int = 0
) {
    val total: Int get() = wides + noBalls + byes + legByes
    fun summary(): String = "$total (b $byes, lb $legByes, wd $wides, nb $noBalls)"
}

data class RunDistribution(
    val dots: Int = 0,
    val singlesTwosThreesRuns: Int = 0,
    val foursRuns: Int = 0,
    val sixesRuns: Int = 0,
    val extrasRuns: Int = 0
)

data class InningsState(
    val inningsNumber: Int,
    val battingTeam: String,
    val bowlingTeam: String,
    val totalRuns: Int = 0,
    val wickets: Int = 0,
    val legalBalls: Int = 0,
    val maxOvers: Int = 10,
    val maxWickets: Int = 10,
    val striker: String = "",
    val nonStriker: String = "",
    val currentBowler: String = "",
    val lastCompletedOverBowler: String = "",
    val batters: List<BatterStats> = emptyList(),
    val bowlers: List<BowlerStats> = emptyList(),
    val fallOfWickets: List<FallOfWicket> = emptyList(),
    val overs: List<OverSummary> = emptyList(),
    val currentOverBalls: List<BallChip> = emptyList(),
    val partnerships: List<Partnership> = emptyList(),
    val currentPartnership: Partnership? = null,
    val extras: ExtrasBreakdown = ExtrasBreakdown(),
    val runDistribution: RunDistribution = RunDistribution(),
    val shots: List<ShotRecord> = emptyList(),
    val isStarted: Boolean = false,
    val isCompleted: Boolean = false,
    val needsNewBatsman: Boolean = false,
    val needsNewBowler: Boolean = false,
    val target: Int? = null,
    val lastWicketSummary: String? = null
) {
    val oversDisplay: String
        get() = "${legalBalls / 6}.${legalBalls % 6}"

    val currentRunRate: Double
        get() = if (legalBalls > 0) totalRuns.toDouble() / (legalBalls / 6.0) else 0.0

    val ballsRemaining: Int
        get() = ((maxOvers * 6) - legalBalls).coerceAtLeast(0)

    val runsNeeded: Int?
        get() = target?.let { (it - totalRuns).coerceAtLeast(0) }

    val requiredRunRate: Double?
        get() {
            val needed = runsNeeded ?: return null
            if (needed <= 0) return 0.0
            if (ballsRemaining <= 0) return null
            return needed.toDouble() / (ballsRemaining / 6.0)
        }

    fun batterStat(name: String): BatterStats? = batters.firstOrNull { it.name == name }
    fun bowlerStat(name: String): BowlerStats? = bowlers.firstOrNull { it.name == name }
}

data class PlayerOfMatch(
    val playerName: String,
    val teamName: String,
    val summary: String,
    val points: Double
)

data class MatchSnapshot(
    val matchId: Long,
    val hostTeam: String,
    val visitorTeam: String,
    val tossWinner: String,
    val tossDecision: String, // "BAT" or "BOWL"
    val rules: MatchRules,
    val createdAt: Long,
    val innings1: InningsState,
    val innings2: InningsState,
    val currentInningsNumber: Int, // 1 or 2
    val isMatchCompleted: Boolean,
    val resultSummary: String,
    val winnerTeam: String?,
    val playerOfMatch: PlayerOfMatch?,
    val totalEventCount: Int
) {
    val currentInnings: InningsState
        get() = if (currentInningsNumber == 1) innings1 else innings2

    val tossSummary: String
        get() = "$tossWinner won the toss & elected to ${tossDecision.lowercase()}"
}

data class PlayerMatchPerformance(
    val matchId: Long,
    val matchTitle: String,
    val opponentTeam: String,
    val matchDate: Long,
    val batted: Boolean = false,
    val battingRuns: Int = 0,
    val battingBalls: Int = 0,
    val isOut: Boolean = false,
    val battingFours: Int = 0,
    val battingSixes: Int = 0,
    val bowled: Boolean = false,
    val bowlingLegalBalls: Int = 0,
    val bowlingRuns: Int = 0,
    val bowlingWickets: Int = 0,
    val resultSummary: String = ""
) {
    val bowlingOversDisplay: String
        get() = "${bowlingLegalBalls / 6}.${bowlingLegalBalls % 6}"
}

data class PlayerCareerStats(
    val playerName: String,
    val teamName: String,
    val matchesPlayed: Int = 0,
    val inningsBatted: Int = 0,
    val runsScored: Int = 0,
    val ballsFaced: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val highestScore: Int = 0,
    val highestNotOut: Boolean = false,
    val thirties: Int = 0,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ducks: Int = 0,
    val notOuts: Int = 0,
    val inningsBowled: Int = 0,
    val ballsBowled: Int = 0,
    val runsConceded: Int = 0,
    val wicketsTaken: Int = 0,
    val maidens: Int = 0,
    val dotBallsBowled: Int = 0,
    val widesBowled: Int = 0,
    val noBallsBowled: Int = 0,
    val threeWicketHauls: Int = 0,
    val fiveWicketHauls: Int = 0,
    val bestBowlingWickets: Int = 0,
    val bestBowlingRuns: Int = Int.MAX_VALUE,
    val recentPerformances: List<PlayerMatchPerformance> = emptyList(),
    val careerShots: List<ShotRecord> = emptyList()
) {
    val battingAverage: Double
        get() {
            val dismissals = (inningsBatted - notOuts).coerceAtLeast(0)
            return if (dismissals > 0) runsScored.toDouble() / dismissals else runsScored.toDouble()
        }

    val strikeRate: Double
        get() = if (ballsFaced > 0) (runsScored.toDouble() / ballsFaced) * 100.0 else 0.0

    val oversBowledDisplay: String
        get() = "${ballsBowled / 6}.${ballsBowled % 6}"

    val bowlingEconomy: Double
        get() = if (ballsBowled > 0) runsConceded.toDouble() / (ballsBowled / 6.0) else 0.0

    val bowlingAverage: Double
        get() = if (wicketsTaken > 0) runsConceded.toDouble() / wicketsTaken else 0.0

    val bowlingStrikeRate: Double
        get() = if (wicketsTaken > 0) ballsBowled.toDouble() / wicketsTaken else 0.0

    val bestBowlingDisplay: String
        get() = if (bestBowlingWickets > 0 || bestBowlingRuns < Int.MAX_VALUE) {
            "$bestBowlingWickets/$bestBowlingRuns"
        } else "-"
}

data class LeaderboardData(
    val totalMatches: Int = 0,
    val completedMatches: Int = 0,
    val totalRunsAcrossApp: Int = 0,
    val totalWicketsAcrossApp: Int = 0,
    val topBatters: List<PlayerCareerStats> = emptyList(),
    val topBowlers: List<PlayerCareerStats> = emptyList(),
    val topSixHitters: List<PlayerCareerStats> = emptyList()
)

package com.example.domain

import com.example.data.local.BallEventEntity
import com.example.data.local.MatchEntity
import kotlin.math.max

/**
 * Pure, deterministic event-sourced Cricket Scoring Engine.
 * Replays [BallEventEntity] records for a [MatchEntity] so live state, scorecards,
 * partnerships, fall of wickets, overs, charts, and infinite undo remain 100% accurate.
 */
object CricketEngine {

    fun computeMatchSnapshot(
        match: MatchEntity,
        events: List<BallEventEntity>
    ): MatchSnapshot {
        val rules = MatchRules(
            totalOvers = match.totalOvers,
            playersPerTeam = match.playersPerTeam,
            wideReball = match.wideReball,
            wideRuns = match.wideRuns,
            noBallReball = match.noBallReball,
            noBallRuns = match.noBallRuns,
            lastManStands = match.lastManStands
        )

        val batFirstTeam = if (match.tossDecision.equals("BAT", ignoreCase = true)) {
            match.tossWinner
        } else {
            if (match.tossWinner == match.hostTeam) match.visitorTeam else match.hostTeam
        }
        val bowlFirstTeam = if (batFirstTeam == match.hostTeam) match.visitorTeam else match.hostTeam

        val sortedEvents = events.sortedBy { it.sequence }
        val inn1Events = sortedEvents.filter { it.inningsNumber == 1 }
        val inn2Events = sortedEvents.filter { it.inningsNumber == 2 }

        val innings1 = replayInnings(
            inningsNumber = 1,
            battingTeam = batFirstTeam,
            bowlingTeam = bowlFirstTeam,
            rules = rules,
            events = inn1Events,
            target = null
        )

        val target = if (innings1.isCompleted) innings1.totalRuns + 1 else null

        val innings2 = replayInnings(
            inningsNumber = 2,
            battingTeam = bowlFirstTeam,
            bowlingTeam = batFirstTeam,
            rules = rules,
            events = inn2Events,
            target = target
        )

        val currentInningsNumber = when {
            innings2.isStarted || innings1.isCompleted -> 2
            else -> 1
        }

        val isMatchCompleted = innings1.isCompleted && innings2.isCompleted
        val (resultSummary, winnerTeam) = computeMatchResult(
            innings1 = innings1,
            innings2 = innings2,
            rules = rules,
            isMatchCompleted = isMatchCompleted
        )

        val potm = if (isMatchCompleted) computePlayerOfMatch(innings1, innings2) else null

        return MatchSnapshot(
            matchId = match.id,
            hostTeam = match.hostTeam,
            visitorTeam = match.visitorTeam,
            tossWinner = match.tossWinner,
            tossDecision = match.tossDecision,
            rules = rules,
            createdAt = match.createdAt,
            innings1 = innings1,
            innings2 = innings2,
            currentInningsNumber = currentInningsNumber,
            isMatchCompleted = isMatchCompleted,
            resultSummary = resultSummary,
            winnerTeam = winnerTeam,
            playerOfMatch = potm,
            totalEventCount = sortedEvents.size
        )
    }

    private data class MutableBatter(
        val name: String,
        var runs: Int = 0,
        var balls: Int = 0,
        var fours: Int = 0,
        var sixes: Int = 0,
        var isOut: Boolean = false,
        var isRetiredHurt: Boolean = false,
        var dismissalText: String = "not out",
        val battingOrder: Int
    ) {
        fun toImmutable() = BatterStats(
            name = name,
            runs = runs,
            balls = balls,
            fours = fours,
            sixes = sixes,
            isOut = isOut,
            isRetiredHurt = isRetiredHurt,
            dismissalText = dismissalText,
            battingOrder = battingOrder
        )
    }

    private data class MutableBowler(
        val name: String,
        var legalBalls: Int = 0,
        var maidens: Int = 0,
        var runsConceded: Int = 0,
        var wickets: Int = 0,
        var wides: Int = 0,
        var noBalls: Int = 0,
        var dotBalls: Int = 0
    ) {
        fun toImmutable() = BowlerStats(
            name = name,
            legalBalls = legalBalls,
            maidens = maidens,
            runsConceded = runsConceded,
            wickets = wickets,
            wides = wides,
            noBalls = noBalls,
            dotBalls = dotBalls
        )
    }

    private data class MutablePartnership(
        val batter1: String,
        val batter2: String,
        var runs: Int = 0,
        var balls: Int = 0,
        var batter1Runs: Int = 0,
        var batter1Balls: Int = 0,
        var batter2Runs: Int = 0,
        var batter2Balls: Int = 0
    ) {
        fun toImmutable(isUnbroken: Boolean) = Partnership(
            batter1 = batter1,
            batter2 = batter2,
            runs = runs,
            balls = balls,
            batter1Runs = batter1Runs,
            batter1Balls = batter1Balls,
            batter2Runs = batter2Runs,
            batter2Balls = batter2Balls,
            isUnbroken = isUnbroken
        )
    }

    private fun replayInnings(
        inningsNumber: Int,
        battingTeam: String,
        bowlingTeam: String,
        rules: MatchRules,
        events: List<BallEventEntity>,
        target: Int?
    ): InningsState {
        val maxWickets = if (rules.lastManStands) {
            rules.playersPerTeam.coerceAtLeast(1)
        } else {
            (rules.playersPerTeam - 1).coerceAtLeast(1)
        }

        if (events.isEmpty()) {
            return InningsState(
                inningsNumber = inningsNumber,
                battingTeam = battingTeam,
                bowlingTeam = bowlingTeam,
                maxOvers = rules.totalOvers,
                maxWickets = maxWickets,
                target = target
            )
        }

        val battersMap = LinkedHashMap<String, MutableBatter>()
        val bowlersMap = LinkedHashMap<String, MutableBowler>()
        val fallOfWickets = mutableListOf<FallOfWicket>()
        val completedOvers = mutableListOf<OverSummary>()
        val partnerships = mutableListOf<Partnership>()
        val recordedShots = mutableListOf<ShotRecord>()

        var orderCounter = 0
        fun getOrCreateBatter(name: String): MutableBatter {
            val clean = name.trim().ifEmpty { "Batter ${orderCounter + 1}" }
            return battersMap.getOrPut(clean) {
                orderCounter++
                MutableBatter(name = clean, battingOrder = orderCounter)
            }
        }

        fun getOrCreateBowler(name: String): MutableBowler {
            val clean = name.trim().ifEmpty { "Bowler" }
            return bowlersMap.getOrPut(clean) { MutableBowler(name = clean) }
        }

        var isStarted = false
        var isCompleted = false
        var striker = ""
        var nonStriker = ""
        var currentBowler = ""
        var lastCompletedOverBowler = ""
        var totalRuns = 0
        var wickets = 0
        var legalBalls = 0

        var widesTotal = 0
        var noBallsTotal = 0
        var byesTotal = 0
        var legByesTotal = 0

        var distDots = 0
        var distSmallRuns = 0
        var distFours = 0
        var distSixes = 0
        var distExtras = 0

        var currentOverNumber = 1
        var currentOverLegalBalls = 0
        var currentOverTotalRuns = 0
        var currentOverBowlerRuns = 0
        var currentOverWickets = 0
        val currentOverChips = mutableListOf<BallChip>()

        var needsNewBatsman = false
        var needsNewBowler = false
        var lastWicketSummary: String? = null
        var activePartnership: MutablePartnership? = null

        for (event in events) {
            val type = runCatching { EventType.valueOf(event.eventType) }.getOrDefault(EventType.DELIVERY)

            when (type) {
                EventType.START_INNINGS -> {
                    isStarted = true
                    striker = event.strikerName.trim().ifEmpty { "Batter 1" }
                    nonStriker = event.nonStrikerName.trim().ifEmpty { "Batter 2" }
                    currentBowler = event.bowlerName.trim().ifEmpty { "Bowler 1" }
                    getOrCreateBatter(striker)
                    if (nonStriker.isNotBlank() && nonStriker != striker) {
                        getOrCreateBatter(nonStriker)
                    }
                    getOrCreateBowler(currentBowler)
                    activePartnership = MutablePartnership(striker, nonStriker)
                    needsNewBatsman = false
                    needsNewBowler = false
                }

                EventType.SWAP_STRIKE -> {
                    val temp = striker
                    striker = nonStriker
                    nonStriker = temp
                }

                EventType.NEW_BATSMAN -> {
                    val incoming = event.newBatsmanName?.trim().orEmpty()
                    if (incoming.isNotEmpty()) {
                        val b = getOrCreateBatter(incoming)
                        b.isRetiredHurt = false
                        b.dismissalText = "not out"
                        if (striker.isEmpty() || battersMap[striker]?.isOut == true || battersMap[striker]?.isRetiredHurt == true) {
                            striker = b.name
                        } else {
                            nonStriker = b.name
                        }
                        if (event.swapAfterEvent) {
                            val t = striker
                            striker = nonStriker
                            nonStriker = t
                        }
                        needsNewBatsman = false
                        activePartnership = MutablePartnership(striker, nonStriker)
                    }
                }

                EventType.RETIRE_BATTER -> {
                    val outName = event.outBatsmanName?.trim().orEmpty().ifEmpty { striker }
                    val replacement = event.newBatsmanName?.trim().orEmpty()
                    val batter = getOrCreateBatter(outName)
                    batter.isRetiredHurt = true
                    batter.dismissalText = "retired hurt"
                    activePartnership?.let { partnerships.add(it.toImmutable(false)) }
                    if (replacement.isNotEmpty()) {
                        val incoming = getOrCreateBatter(replacement)
                        incoming.isRetiredHurt = false
                        incoming.dismissalText = "not out"
                        if (outName == striker) striker = incoming.name else nonStriker = incoming.name
                        activePartnership = MutablePartnership(striker, nonStriker)
                        needsNewBatsman = false
                    } else {
                        if (outName == striker) striker = "" else nonStriker = ""
                        needsNewBatsman = true
                    }
                }

                EventType.NEW_BOWLER -> {
                    val nextBowler = event.bowlerName.trim()
                    if (nextBowler.isNotEmpty()) {
                        currentBowler = getOrCreateBowler(nextBowler).name
                        needsNewBowler = false
                    }
                }

                EventType.END_INNINGS -> {
                    if (currentOverChips.isNotEmpty()) {
                        completedOvers.add(
                            OverSummary(
                                overNumber = currentOverNumber,
                                bowlerName = currentBowler,
                                balls = currentOverChips.toList(),
                                runsInOver = currentOverTotalRuns,
                                wicketsInOver = currentOverWickets,
                                cumulativeRuns = totalRuns,
                                cumulativeWickets = wickets,
                                isCompleted = false
                            )
                        )
                        currentOverChips.clear()
                    }
                    isCompleted = true
                    needsNewBatsman = false
                    needsNewBowler = false
                }

                EventType.DELIVERY -> {
                    if (!isStarted) {
                        isStarted = true
                        if (striker.isEmpty()) striker = event.strikerName.ifEmpty { "Batter 1" }
                        if (nonStriker.isEmpty()) nonStriker = event.nonStrikerName.ifEmpty { "Batter 2" }
                        if (currentBowler.isEmpty()) currentBowler = event.bowlerName.ifEmpty { "Bowler 1" }
                    }
                    val activeStrikerName = event.strikerName.ifEmpty { striker }
                    val activeBowlerName = event.bowlerName.ifEmpty { currentBowler }
                    val batter = getOrCreateBatter(activeStrikerName)
                    val bowler = getOrCreateBowler(activeBowlerName)
                    if (striker.isEmpty()) striker = batter.name
                    if (currentBowler.isEmpty()) currentBowler = bowler.name

                    val isWide = event.isWide
                    val isNoBall = event.isNoBall
                    val isBye = event.isBye
                    val isLegBye = event.isLegBye
                    val runsOffBatOrRunning = event.runsScored

                    // Extra penalty runs per delivery override or match rules (0 = optional/no run penalty)
                    val widePenalty = if (isWide) {
                        if (event.penaltyRuns >= 0) event.penaltyRuns else rules.wideRuns
                    } else 0
                    val noBallPenalty = if (isNoBall) {
                        if (event.penaltyRuns >= 0) event.penaltyRuns else rules.noBallRuns
                    } else 0

                    // Is this a legal delivery (counts towards over)?
                    val isLegalDelivery = when {
                        isWide && rules.wideReball -> false
                        isNoBall && rules.noBallReball -> false
                        else -> true
                    }

                    // Batter faces ball on legal deliveries OR no-balls (not on wides)
                    val batterFacedBall = !isWide
                    // Runs credited to batter (not on wide, bye, leg bye)
                    val runsToBatter = if (!isWide && !isBye && !isLegBye) runsOffBatOrRunning else 0
                    val deliveryTotalRuns = runsOffBatOrRunning + widePenalty + noBallPenalty

                    // Bowler conceded runs (byes and leg-byes do not count against bowler)
                    val bowlerConcededRuns = when {
                        isWide -> widePenalty + runsOffBatOrRunning
                        isNoBall && (isBye || isLegBye) -> noBallPenalty
                        isNoBall -> noBallPenalty + runsOffBatOrRunning
                        isBye || isLegBye -> 0
                        else -> runsOffBatOrRunning
                    }

                    // Update extras breakdown
                    if (isWide) {
                        widesTotal += widePenalty + runsOffBatOrRunning
                        bowler.wides += 1
                    }
                    if (isNoBall) {
                        noBallsTotal += noBallPenalty
                        bowler.noBalls += 1
                    }
                    if (!isWide && isBye) {
                        byesTotal += runsOffBatOrRunning
                    }
                    if (!isWide && isLegBye) {
                        legByesTotal += runsOffBatOrRunning
                    }

                    // Update batter stats
                    if (batterFacedBall) {
                        batter.balls += 1
                    }
                    batter.runs += runsToBatter
                    val isFour = runsToBatter == 4
                    val isSix = runsToBatter == 6
                    if (isFour) batter.fours += 1
                    if (isSix) batter.sixes += 1

                    // Distribution stats
                    when {
                        isWide || isNoBall || isBye || isLegBye -> {
                            distExtras += deliveryTotalRuns - runsToBatter
                            if (runsToBatter == 4) distFours += 4
                            else if (runsToBatter == 6) distSixes += 6
                            else if (runsToBatter > 0) distSmallRuns += runsToBatter
                        }
                        runsToBatter == 0 && !event.isWicket -> distDots += 1
                        runsToBatter == 4 -> distFours += 4
                        runsToBatter == 6 -> distSixes += 6
                        runsToBatter > 0 -> distSmallRuns += runsToBatter
                    }

                    // Update team & bowler stats
                    totalRuns += deliveryTotalRuns
                    bowler.runsConceded += bowlerConcededRuns
                    currentOverTotalRuns += deliveryTotalRuns
                    currentOverBowlerRuns += bowlerConcededRuns

                    if (isLegalDelivery) {
                        legalBalls += 1
                        bowler.legalBalls += 1
                        currentOverLegalBalls += 1
                        if (deliveryTotalRuns == 0) {
                            bowler.dotBalls += 1
                        }
                    }

                    // Update partnership
                    val part = activePartnership ?: MutablePartnership(striker, nonStriker).also {
                        activePartnership = it
                    }
                    part.runs += deliveryTotalRuns
                    if (batterFacedBall) {
                        part.balls += 1
                        if (batter.name == part.batter1) {
                            part.batter1Balls += 1
                        } else {
                            part.batter2Balls += 1
                        }
                    }
                    if (runsToBatter > 0) {
                        if (batter.name == part.batter1) {
                            part.batter1Runs += runsToBatter
                        } else {
                            part.batter2Runs += runsToBatter
                        }
                    }

                    // Build ball label for "This Over"
                    val chipLabel = formatBallLabel(
                        runs = runsOffBatOrRunning,
                        widePenalty = widePenalty,
                        noBallPenalty = noBallPenalty,
                        isWide = isWide,
                        isNoBall = isNoBall,
                        isBye = isBye,
                        isLegBye = isLegBye,
                        isWicket = event.isWicket
                    )

                    val chip = BallChip(
                        eventId = event.id,
                        label = chipLabel,
                        totalRuns = deliveryTotalRuns,
                        isWicket = event.isWicket,
                        isWide = isWide,
                        isNoBall = isNoBall,
                        isBye = isBye,
                        isLegBye = isLegBye,
                        isFour = isFour,
                        isSix = isSix,
                        isDot = isLegalDelivery && deliveryTotalRuns == 0 && !event.isWicket,
                        overNumber = currentOverNumber,
                        ballInOver = currentOverLegalBalls,
                        shotZone = event.shotZone
                    )
                    currentOverChips.add(chip)

                    if (event.shotX != null && event.shotY != null && !event.shotZone.isNullOrBlank()) {
                        val overBallLabel = "${currentOverNumber - 1}.${currentOverLegalBalls.coerceAtLeast(1)}"
                        recordedShots.add(
                            ShotRecord(
                                eventId = event.id,
                                inningsNumber = inningsNumber,
                                overBallDisplay = overBallLabel,
                                batterName = batter.name,
                                bowlerName = bowler.name,
                                runs = runsToBatter.takeIf { it > 0 } ?: deliveryTotalRuns,
                                shotX = event.shotX,
                                shotY = event.shotY,
                                shotZone = event.shotZone,
                                isFour = isFour,
                                isSix = isSix
                            )
                        )
                    }

                    // Strike rotation for runs run by batters (odd running runs)
                    if (runsOffBatOrRunning % 2 == 1 && nonStriker.isNotBlank()) {
                        val t = striker
                        striker = nonStriker
                        nonStriker = t
                    }

                    // Handle wicket
                    if (event.isWicket) {
                        wickets += 1
                        currentOverWickets += 1
                        val dismissalType = DismissalType.fromName(event.dismissalType)
                        if (dismissalType.creditBowler) {
                            bowler.wickets += 1
                        }

                        val outName = event.outBatsmanName?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: activeStrikerName
                        val outBatter = getOrCreateBatter(outName)
                        outBatter.isOut = true
                        outBatter.dismissalText = formatDismissal(
                            type = dismissalType,
                            bowlerName = bowler.name,
                            fielderName = event.fielderName
                        )

                        val overAtFall = "${legalBalls / 6}.${legalBalls % 6}"
                        fallOfWickets.add(
                            FallOfWicket(
                                wicketNumber = wickets,
                                runsAtFall = totalRuns,
                                batterName = outBatter.name,
                                overDisplay = overAtFall
                            )
                        )
                        lastWicketSummary = "${outBatter.name} ${outBatter.runs}(${outBatter.balls}) • $totalRuns/$wickets ($overAtFall)"

                        // Close current partnership
                        activePartnership?.let { partnerships.add(it.toImmutable(false)) }
                        activePartnership = null

                        // Handle replacement or last-man-stands
                        val incoming = event.newBatsmanName?.trim().orEmpty()
                        val inningsEndedByWickets = wickets >= maxWickets

                        if (inningsEndedByWickets) {
                            if (outBatter.name == striker) striker = "" else nonStriker = ""
                            needsNewBatsman = false
                        } else if (incoming.isNotEmpty()) {
                            val newBat = getOrCreateBatter(incoming)
                            newBat.isRetiredHurt = false
                            newBat.dismissalText = "not out"
                            if (outBatter.name == striker) {
                                striker = newBat.name
                            } else {
                                nonStriker = newBat.name
                            }
                            activePartnership = MutablePartnership(striker, nonStriker)
                            needsNewBatsman = false
                        } else {
                            // If last man stands and only 1 batter left
                            if (rules.lastManStands && wickets == rules.playersPerTeam - 1) {
                                val survivor = if (outBatter.name == striker) nonStriker else striker
                                striker = survivor
                                nonStriker = ""
                                needsNewBatsman = false
                            } else {
                                if (outBatter.name == striker) striker = "" else nonStriker = ""
                                needsNewBatsman = true
                            }
                        }
                    }

                    // Check target chased or all out or overs finished
                    val targetChased = target != null && totalRuns >= target
                    val allOut = wickets >= maxWickets
                    val overFinished = currentOverLegalBalls >= 6
                    val oversComplete = legalBalls >= rules.totalOvers * 6

                    if (overFinished) {
                        if (currentOverBowlerRuns == 0) {
                            bowler.maidens += 1
                        }
                        completedOvers.add(
                            OverSummary(
                                overNumber = currentOverNumber,
                                bowlerName = bowler.name,
                                balls = currentOverChips.toList(),
                                runsInOver = currentOverTotalRuns,
                                wicketsInOver = currentOverWickets,
                                cumulativeRuns = totalRuns,
                                cumulativeWickets = wickets,
                                isCompleted = true
                            )
                        )
                        lastCompletedOverBowler = bowler.name
                        currentOverChips.clear()
                        currentOverNumber += 1
                        currentOverLegalBalls = 0
                        currentOverTotalRuns = 0
                        currentOverBowlerRuns = 0
                        currentOverWickets = 0

                        // End-of-over strike rotation (if both batters present)
                        if (!targetChased && !allOut && !oversComplete && striker.isNotBlank() && nonStriker.isNotBlank()) {
                            val t = striker
                            striker = nonStriker
                            nonStriker = t
                        }
                        if (!targetChased && !allOut && !oversComplete) {
                            needsNewBowler = true
                        }
                    }

                    if (targetChased || allOut || oversComplete) {
                        if (currentOverChips.isNotEmpty()) {
                            completedOvers.add(
                                OverSummary(
                                    overNumber = currentOverNumber,
                                    bowlerName = bowler.name,
                                    balls = currentOverChips.toList(),
                                    runsInOver = currentOverTotalRuns,
                                    wicketsInOver = currentOverWickets,
                                    cumulativeRuns = totalRuns,
                                    cumulativeWickets = wickets,
                                    isCompleted = false
                                )
                            )
                            currentOverChips.clear()
                        }
                        isCompleted = true
                        needsNewBatsman = false
                        needsNewBowler = false
                    }
                }
            }
        }

        // Include unbroken partnership at end
        val finalPartnerships = partnerships.toMutableList()
        val currentPartImmutable = activePartnership?.toImmutable(isUnbroken = true)
        if (currentPartImmutable != null && (currentPartImmutable.runs > 0 || currentPartImmutable.balls > 0 || !isCompleted)) {
            finalPartnerships.add(currentPartImmutable)
        }

        // Include in-progress over in overs list for graphs/overs tab
        val allOvers = completedOvers.toMutableList()
        if (currentOverChips.isNotEmpty()) {
            allOvers.add(
                OverSummary(
                    overNumber = currentOverNumber,
                    bowlerName = currentBowler,
                    balls = currentOverChips.toList(),
                    runsInOver = currentOverTotalRuns,
                    wicketsInOver = currentOverWickets,
                    cumulativeRuns = totalRuns,
                    cumulativeWickets = wickets,
                    isCompleted = false
                )
            )
        }

        return InningsState(
            inningsNumber = inningsNumber,
            battingTeam = battingTeam,
            bowlingTeam = bowlingTeam,
            totalRuns = totalRuns,
            wickets = wickets,
            legalBalls = legalBalls,
            maxOvers = rules.totalOvers,
            maxWickets = maxWickets,
            striker = striker,
            nonStriker = nonStriker,
            currentBowler = currentBowler,
            lastCompletedOverBowler = lastCompletedOverBowler,
            batters = battersMap.values.map { it.toImmutable() },
            bowlers = bowlersMap.values.map { it.toImmutable() },
            fallOfWickets = fallOfWickets,
            overs = allOvers,
            currentOverBalls = currentOverChips.toList(),
            partnerships = finalPartnerships,
            currentPartnership = currentPartImmutable,
            extras = ExtrasBreakdown(
                wides = widesTotal,
                noBalls = noBallsTotal,
                byes = byesTotal,
                legByes = legByesTotal
            ),
            runDistribution = RunDistribution(
                dots = distDots,
                singlesTwosThreesRuns = distSmallRuns,
                foursRuns = distFours,
                sixesRuns = distSixes,
                extrasRuns = distExtras
            ),
            shots = recordedShots,
            isStarted = isStarted,
            isCompleted = isCompleted,
            needsNewBatsman = needsNewBatsman,
            needsNewBowler = needsNewBowler,
            target = target,
            lastWicketSummary = lastWicketSummary
        )
    }

    private fun formatBallLabel(
        runs: Int,
        widePenalty: Int,
        noBallPenalty: Int,
        isWide: Boolean,
        isNoBall: Boolean,
        isBye: Boolean,
        isLegBye: Boolean,
        isWicket: Boolean
    ): String {
        val parts = mutableListOf<String>()
        if (isWide) {
            parts.add(
                when {
                    runs > 0 -> "${runs}WD"
                    widePenalty == 0 -> "0WD"
                    else -> "WD"
                }
            )
        } else if (isNoBall) {
            val extraTag = when {
                isBye -> "B"
                isLegBye -> "LB"
                else -> ""
            }
            parts.add(
                when {
                    runs > 0 -> "${runs}${extraTag}NB"
                    noBallPenalty == 0 -> "0NB"
                    else -> "NB"
                }
            )
        } else if (isBye) {
            parts.add("${runs}B")
        } else if (isLegBye) {
            parts.add("${runs}LB")
        } else if (!isWicket) {
            parts.add(runs.toString())
        } else if (runs > 0) {
            parts.add(runs.toString())
        }

        if (isWicket) {
            parts.add("W")
        }
        return parts.joinToString("+").ifEmpty { "0" }
    }

    private fun formatDismissal(
        type: DismissalType,
        bowlerName: String,
        fielderName: String?
    ): String {
        val fielder = fielderName?.trim().orEmpty()
        return when (type) {
            DismissalType.BOWLED -> "b $bowlerName"
            DismissalType.CAUGHT -> if (fielder.isNotEmpty()) {
                if (fielder.equals(bowlerName, ignoreCase = true)) "c & b $bowlerName"
                else "c $fielder b $bowlerName"
            } else {
                "c sub b $bowlerName"
            }
            DismissalType.LBW -> "lbw b $bowlerName"
            DismissalType.RUN_OUT -> if (fielder.isNotEmpty()) "run out ($fielder)" else "run out"
            DismissalType.STUMPED -> if (fielder.isNotEmpty()) "st $fielder b $bowlerName" else "st b $bowlerName"
            DismissalType.HIT_WICKET -> "hit wkt b $bowlerName"
            DismissalType.RETIRED_OUT -> "retired out"
        }
    }

    private fun computeMatchResult(
        innings1: InningsState,
        innings2: InningsState,
        rules: MatchRules,
        isMatchCompleted: Boolean
    ): Pair<String, String?> {
        if (!isMatchCompleted) {
            return when {
                innings2.isStarted -> {
                    val needed = innings2.runsNeeded ?: 0
                    "${innings2.battingTeam} need $needed runs in ${innings2.ballsRemaining} balls" to null
                }
                innings1.isCompleted -> {
                    "Innings Break • Target ${innings1.totalRuns + 1}" to null
                }
                innings1.isStarted -> {
                    "${innings1.battingTeam} batting • ${innings1.totalRuns}/${innings1.wickets} (${innings1.oversDisplay})" to null
                }
                else -> "Match Ready" to null
            }
        }

        val score1 = innings1.totalRuns
        val score2 = innings2.totalRuns
        return when {
            score2 > score1 -> {
                val maxWkts = if (rules.lastManStands) rules.playersPerTeam else (rules.playersPerTeam - 1).coerceAtLeast(1)
                val wicketsLeft = (maxWkts - innings2.wickets).coerceAtLeast(1)
                val wLabel = if (wicketsLeft == 1) "wicket" else "wickets"
                "${innings2.battingTeam} won by $wicketsLeft $wLabel" to innings2.battingTeam
            }
            score1 > score2 -> {
                val runDiff = score1 - score2
                val rLabel = if (runDiff == 1) "run" else "runs"
                "${innings1.battingTeam} won by $runDiff $rLabel" to innings1.battingTeam
            }
            else -> "Match Tied!" to null
        }
    }

    private fun computePlayerOfMatch(
        innings1: InningsState,
        innings2: InningsState
    ): PlayerOfMatch? {
        data class Impact(
            val team: String,
            var points: Double = 0.0,
            var runs: Int = 0,
            var balls: Int = 0,
            var wickets: Int = 0,
            var runsConceded: Int = 0
        )

        val map = mutableMapOf<String, Impact>()

        fun processInnings(inn: InningsState) {
            for (b in inn.batters) {
                val item = map.getOrPut(b.name) { Impact(team = inn.battingTeam) }
                item.runs += b.runs
                item.balls += b.balls
                item.points += b.runs * 1.0 + b.fours * 1.5 + b.sixes * 2.5 + if (b.runs >= 50) 15.0 else 0.0
            }
            for (bw in inn.bowlers) {
                val item = map.getOrPut(bw.name) { Impact(team = inn.bowlingTeam) }
                item.wickets += bw.wickets
                item.runsConceded += bw.runsConceded
                item.points += bw.wickets * 25.0 + bw.maidens * 10.0 + bw.dotBalls * 0.5
            }
        }

        processInnings(innings1)
        processInnings(innings2)

        val best = map.entries.maxByOrNull { it.value.points } ?: return null
        val stats = best.value
        val parts = mutableListOf<String>()
        if (stats.balls > 0 || stats.runs > 0) parts.add("${stats.runs} (${stats.balls})")
        if (stats.wickets > 0) parts.add("${stats.wickets}/${stats.runsConceded}")
        return PlayerOfMatch(
            playerName = best.key,
            teamName = stats.team,
            summary = parts.joinToString(" & ").ifEmpty { "Match Impact" },
            points = stats.points
        )
    }

    fun computeLeaderboardAndPlayerStats(
        matchesWithEvents: List<Pair<MatchEntity, List<BallEventEntity>>>
    ): Pair<LeaderboardData, Map<String, PlayerCareerStats>> {
        val playerMap = mutableMapOf<String, PlayerCareerStats>()
        var totalRunsApp = 0
        var totalWicketsApp = 0
        var completedCount = 0

        for ((match, events) in matchesWithEvents) {
            val snap = computeMatchSnapshot(match, events)
            if (snap.isMatchCompleted) completedCount++
            totalRunsApp += snap.innings1.totalRuns + snap.innings2.totalRuns
            totalWicketsApp += snap.innings1.wickets + snap.innings2.wickets

            val matchPerfByPlayer = mutableMapOf<String, PlayerMatchPerformance>()

            fun accumulateInnings(inn: InningsState) {
                val shotsByBatter = inn.shots.groupBy { it.batterName.lowercase() }
                for (b in inn.batters) {
                    val key = b.name.lowercase()
                    val cur = playerMap[key] ?: PlayerCareerStats(playerName = b.name, teamName = inn.battingTeam)
                    val newHigh = if (b.runs > cur.highestScore || (b.runs == cur.highestScore && !b.isOut && !cur.highestNotOut)) {
                        b.runs to !b.isOut
                    } else {
                        cur.highestScore to cur.highestNotOut
                    }
                    val isDuck = b.isOut && b.runs == 0
                    playerMap[key] = cur.copy(
                        teamName = inn.battingTeam,
                        inningsBatted = cur.inningsBatted + 1,
                        runsScored = cur.runsScored + b.runs,
                        ballsFaced = cur.ballsFaced + b.balls,
                        fours = cur.fours + b.fours,
                        sixes = cur.sixes + b.sixes,
                        highestScore = newHigh.first,
                        highestNotOut = newHigh.second,
                        thirties = cur.thirties + if (b.runs in 30..49) 1 else 0,
                        fifties = cur.fifties + if (b.runs in 50..99) 1 else 0,
                        hundreds = cur.hundreds + if (b.runs >= 100) 1 else 0,
                        ducks = cur.ducks + if (isDuck) 1 else 0,
                        notOuts = cur.notOuts + if (!b.isOut) 1 else 0,
                        careerShots = cur.careerShots + shotsByBatter[key].orEmpty()
                    )
                    val existingPerf = matchPerfByPlayer[key] ?: PlayerMatchPerformance(
                        matchId = match.id,
                        matchTitle = "${match.hostTeam} vs ${match.visitorTeam}",
                        opponentTeam = inn.bowlingTeam,
                        matchDate = match.createdAt,
                        resultSummary = snap.resultSummary
                    )
                    matchPerfByPlayer[key] = existingPerf.copy(
                        batted = true,
                        battingRuns = b.runs,
                        battingBalls = b.balls,
                        isOut = b.isOut,
                        battingFours = b.fours,
                        battingSixes = b.sixes
                    )
                }

                for (bw in inn.bowlers) {
                    val key = bw.name.lowercase()
                    val cur = playerMap[key] ?: PlayerCareerStats(playerName = bw.name, teamName = inn.bowlingTeam)
                    val isBetterSpell = bw.wickets > cur.bestBowlingWickets ||
                        (bw.wickets == cur.bestBowlingWickets && bw.runsConceded < cur.bestBowlingRuns)
                    playerMap[key] = cur.copy(
                        inningsBowled = cur.inningsBowled + 1,
                        ballsBowled = cur.ballsBowled + bw.legalBalls,
                        runsConceded = cur.runsConceded + bw.runsConceded,
                        wicketsTaken = cur.wicketsTaken + bw.wickets,
                        maidens = cur.maidens + bw.maidens,
                        dotBallsBowled = cur.dotBallsBowled + bw.dotBalls,
                        widesBowled = cur.widesBowled + bw.wides,
                        noBallsBowled = cur.noBallsBowled + bw.noBalls,
                        threeWicketHauls = cur.threeWicketHauls + if (bw.wickets in 3..4) 1 else 0,
                        fiveWicketHauls = cur.fiveWicketHauls + if (bw.wickets >= 5) 1 else 0,
                        bestBowlingWickets = if (isBetterSpell) bw.wickets else cur.bestBowlingWickets,
                        bestBowlingRuns = if (isBetterSpell) bw.runsConceded else cur.bestBowlingRuns
                    )
                    val existingPerf = matchPerfByPlayer[key] ?: PlayerMatchPerformance(
                        matchId = match.id,
                        matchTitle = "${match.hostTeam} vs ${match.visitorTeam}",
                        opponentTeam = inn.battingTeam,
                        matchDate = match.createdAt,
                        resultSummary = snap.resultSummary
                    )
                    matchPerfByPlayer[key] = existingPerf.copy(
                        bowled = true,
                        bowlingLegalBalls = bw.legalBalls,
                        bowlingRuns = bw.runsConceded,
                        bowlingWickets = bw.wickets
                    )
                }
            }

            if (snap.innings1.isStarted) accumulateInnings(snap.innings1)
            if (snap.innings2.isStarted) accumulateInnings(snap.innings2)

            for ((key, perf) in matchPerfByPlayer) {
                playerMap[key]?.let {
                    playerMap[key] = it.copy(
                        matchesPlayed = it.matchesPlayed + 1,
                        recentPerformances = (it.recentPerformances + perf).sortedByDescending { p -> p.matchDate }
                    )
                }
            }
        }

        val allStats = playerMap.values.toList()
        val leaderboard = LeaderboardData(
            totalMatches = matchesWithEvents.size,
            completedMatches = completedCount,
            totalRunsAcrossApp = totalRunsApp,
            totalWicketsAcrossApp = totalWicketsApp,
            topBatters = allStats.filter { it.runsScored > 0 }
                .sortedWith(compareByDescending<PlayerCareerStats> { it.runsScored }.thenByDescending { it.strikeRate })
                .take(10),
            topBowlers = allStats.filter { it.wicketsTaken > 0 }
                .sortedWith(compareByDescending<PlayerCareerStats> { it.wicketsTaken }.thenBy { it.bowlingEconomy })
                .take(10),
            topSixHitters = allStats.filter { it.sixes > 0 }
                .sortedWith(compareByDescending<PlayerCareerStats> { it.sixes }.thenByDescending { it.runsScored })
                .take(10)
        )

        return leaderboard to playerMap
    }

    fun formatShareablePlayerCareer(stats: PlayerCareerStats): String {
        val sb = StringBuilder()
        sb.appendLine("🏏 CRICSCORE CAREER PROFILE")
        sb.appendLine("${stats.playerName} (${stats.teamName})")
        sb.appendLine("Matches: ${stats.matchesPlayed}")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("BATTING")
        sb.appendLine("• Runs: ${stats.runsScored} (${stats.ballsFaced} balls) • Inn: ${stats.inningsBatted}")
        sb.appendLine("• High Score: ${stats.highestScore}${if (stats.highestNotOut) "*" else ""} • Avg: ${"%.2f".format(stats.battingAverage)} • SR: ${"%.1f".format(stats.strikeRate)}")
        sb.appendLine("• 4s: ${stats.fours} • 6s: ${stats.sixes} • 30s/50s/100s: ${stats.thirties}/${stats.fifties}/${stats.hundreds}")
        sb.appendLine()
        sb.appendLine("BOWLING")
        sb.appendLine("• Wickets: ${stats.wicketsTaken} • Overs: ${stats.oversBowledDisplay} • Runs: ${stats.runsConceded}")
        sb.appendLine("• Best: ${stats.bestBowlingDisplay} • Economy: ${"%.2f".format(stats.bowlingEconomy)} • Maidens: ${stats.maidens}")
        sb.appendLine("Tracked with Cricscore")
        return sb.toString()
    }

    fun formatShareableScorecard(snapshot: MatchSnapshot): String {
        val sb = StringBuilder()
        sb.appendLine("🏏 CRICSCORE MATCH SUMMARY")
        sb.appendLine("${snapshot.hostTeam} vs ${snapshot.visitorTeam} (${snapshot.rules.totalOvers} Overs)")
        sb.appendLine(snapshot.tossSummary)
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━")

        fun appendInnings(inn: InningsState) {
            if (!inn.isStarted) return
            sb.appendLine("${inn.battingTeam}: ${inn.totalRuns}/${inn.wickets} (${inn.oversDisplay} ov) • CRR ${"%.2f".format(inn.currentRunRate)}")
            inn.batters.sortedByDescending { it.runs }.take(3).forEach { b ->
                val star = if (!b.isOut) "*" else ""
                sb.appendLine("  • ${b.name}$star: ${b.runs} (${b.balls}) [${b.fours}x4, ${b.sixes}x6]")
            }
            inn.bowlers.sortedWith(compareByDescending<BowlerStats> { it.wickets }.thenBy { it.runsConceded })
                .take(3).forEach { bw ->
                    sb.appendLine("  ⚾ ${bw.name}: ${bw.wickets}/${bw.runsConceded} (${bw.oversDisplay} ov)")
                }
            sb.appendLine()
        }

        appendInnings(snapshot.innings1)
        appendInnings(snapshot.innings2)

        sb.appendLine("🏆 ${snapshot.resultSummary}")
        snapshot.playerOfMatch?.let {
            sb.appendLine("⭐ Player of the Match: ${it.playerName} (${it.summary})")
        }
        sb.appendLine("Scored with Cricscore")
        return sb.toString()
    }
}

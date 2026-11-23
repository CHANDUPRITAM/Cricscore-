package com.example

import com.example.data.local.BallEventEntity
import com.example.data.local.MatchEntity
import com.example.domain.CricketEngine
import com.example.domain.DismissalType
import com.example.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CricketEngineTest {

    @Test
    fun `scoring over, extras, wicket, chase and result compute accurately`() {
        val match = MatchEntity(
            id = 1L,
            hostTeam = "Mumbai Strikers",
            visitorTeam = "Delhi Blazers",
            tossWinner = "Mumbai Strikers",
            tossDecision = "BAT",
            totalOvers = 1,
            playersPerTeam = 3,
            wideReball = true,
            wideRuns = 1,
            noBallReball = true,
            noBallRuns = 1,
            lastManStands = false
        )

        val events = listOf(
            BallEventEntity(id = 1, matchId = 1, inningsNumber = 1, sequence = 1, eventType = EventType.START_INNINGS.name, strikerName = "Rohit", nonStrikerName = "Virat", bowlerName = "Bumrah"),
            // Ball 1: 4 runs
            BallEventEntity(id = 2, matchId = 1, inningsNumber = 1, sequence = 2, eventType = EventType.DELIVERY.name, strikerName = "Rohit", nonStrikerName = "Virat", bowlerName = "Bumrah", runsScored = 4),
            // Wide + 1 run (does not count as legal ball, adds 2 runs total, swaps strike)
            BallEventEntity(id = 3, matchId = 1, inningsNumber = 1, sequence = 3, eventType = EventType.DELIVERY.name, strikerName = "Rohit", nonStrikerName = "Virat", bowlerName = "Bumrah", runsScored = 1, isWide = true),
            // Ball 2: Virat hits 6
            BallEventEntity(id = 4, matchId = 1, inningsNumber = 1, sequence = 4, eventType = EventType.DELIVERY.name, strikerName = "Virat", nonStrikerName = "Rohit", bowlerName = "Bumrah", runsScored = 6),
            // Ball 3: Wicket (Virat bowled, Gill comes in)
            BallEventEntity(id = 5, matchId = 1, inningsNumber = 1, sequence = 5, eventType = EventType.DELIVERY.name, strikerName = "Virat", nonStrikerName = "Rohit", bowlerName = "Bumrah", isWicket = true, dismissalType = DismissalType.BOWLED.name, outBatsmanName = "Virat", newBatsmanName = "Gill"),
            // Ball 4: Wicket (Gill caught -> All Out for 3-player team, maxWickets = 2!)
            BallEventEntity(id = 6, matchId = 1, inningsNumber = 1, sequence = 6, eventType = EventType.DELIVERY.name, strikerName = "Gill", nonStrikerName = "Rohit", bowlerName = "Bumrah", isWicket = true, dismissalType = DismissalType.CAUGHT.name, outBatsmanName = "Gill", fielderName = "Pant")
        )

        val snap1 = CricketEngine.computeMatchSnapshot(match, events)
        assertEquals(12, snap1.innings1.totalRuns) // 4 + 2(wide+1) + 6
        assertEquals(2, snap1.innings1.wickets)
        assertEquals("0.4", snap1.innings1.oversDisplay)
        assertTrue(snap1.innings1.isCompleted)
        assertEquals(13, snap1.innings2.target)
        assertFalse(snap1.isMatchCompleted)

        // Start 2nd innings and chase target in 3 balls (6 + 6 + 1)
        val events2 = events + listOf(
            BallEventEntity(id = 7, matchId = 1, inningsNumber = 2, sequence = 7, eventType = EventType.START_INNINGS.name, strikerName = "Pant", nonStrikerName = "Warner", bowlerName = "Shami"),
            BallEventEntity(id = 8, matchId = 1, inningsNumber = 2, sequence = 8, eventType = EventType.DELIVERY.name, strikerName = "Pant", nonStrikerName = "Warner", bowlerName = "Shami", runsScored = 6),
            BallEventEntity(id = 9, matchId = 1, inningsNumber = 2, sequence = 9, eventType = EventType.DELIVERY.name, strikerName = "Pant", nonStrikerName = "Warner", bowlerName = "Shami", runsScored = 6),
            BallEventEntity(id = 10, matchId = 1, inningsNumber = 2, sequence = 10, eventType = EventType.DELIVERY.name, strikerName = "Pant", nonStrikerName = "Warner", bowlerName = "Shami", runsScored = 1)
        )

        val snap2 = CricketEngine.computeMatchSnapshot(match, events2)
        assertTrue(snap2.isMatchCompleted)
        assertEquals("Delhi Blazers", snap2.winnerTeam)
        assertEquals("Delhi Blazers won by 2 wickets", snap2.resultSummary)
        assertEquals(13, snap2.innings2.totalRuns)
        assertEquals("0.3", snap2.innings2.oversDisplay)
    }

    @Test
    fun `byes do not count against bowler and preserve maiden over`() {
        val match = MatchEntity(
            id = 2L,
            hostTeam = "Alpha",
            visitorTeam = "Beta",
            tossWinner = "Alpha",
            tossDecision = "BAT",
            totalOvers = 2,
            playersPerTeam = 11
        )
        val events = mutableListOf(
            BallEventEntity(id = 1, matchId = 2, inningsNumber = 1, sequence = 1, eventType = EventType.START_INNINGS.name, strikerName = "A1", nonStrikerName = "A2", bowlerName = "B1")
        )
        // 5 dots + 1 bye run = 1 team run, 0 bowler runs, 1 maiden over!
        for (i in 1..5) {
            events.add(
                BallEventEntity(id = (i + 1).toLong(), matchId = 2, inningsNumber = 1, sequence = i + 1, eventType = EventType.DELIVERY.name, strikerName = "A1", nonStrikerName = "A2", bowlerName = "B1", runsScored = 0)
            )
        }
        events.add(
            BallEventEntity(id = 7, matchId = 2, inningsNumber = 1, sequence = 7, eventType = EventType.DELIVERY.name, strikerName = "A1", nonStrikerName = "A2", bowlerName = "B1", runsScored = 1, isBye = true)
        )

        val snap = CricketEngine.computeMatchSnapshot(match, events)
        assertEquals(1, snap.innings1.totalRuns)
        assertEquals("1.0", snap.innings1.oversDisplay)
        assertTrue(snap.innings1.needsNewBowler)
        val bowler = snap.innings1.bowlerStat("B1")!!
        assertEquals(0, bowler.runsConceded)
        assertEquals(1, bowler.maidens)
    }

    @Test
    fun `optional wide and no-ball zero penalty runs and player career stats work accurately`() {
        val match = MatchEntity(
            id = 3L,
            hostTeam = "Gully XI",
            visitorTeam = "Street Stars",
            tossWinner = "Gully XI",
            tossDecision = "BAT",
            totalOvers = 2,
            playersPerTeam = 5,
            wideReball = true,
            wideRuns = 0, // Optional wide run OFF
            noBallReball = true,
            noBallRuns = 0 // Optional no-ball run OFF
        )
        val events = listOf(
            BallEventEntity(id = 1, matchId = 3, inningsNumber = 1, sequence = 1, eventType = EventType.START_INNINGS.name, strikerName = "Raju", nonStrikerName = "Sonu", bowlerName = "Vicky"),
            // Plain wide with 0 penalty runs
            BallEventEntity(id = 2, matchId = 3, inningsNumber = 1, sequence = 2, eventType = EventType.DELIVERY.name, strikerName = "Raju", nonStrikerName = "Sonu", bowlerName = "Vicky", runsScored = 0, isWide = true, penaltyRuns = 0),
            // Plain no-ball with 0 penalty runs
            BallEventEntity(id = 3, matchId = 3, inningsNumber = 1, sequence = 3, eventType = EventType.DELIVERY.name, strikerName = "Raju", nonStrikerName = "Sonu", bowlerName = "Vicky", runsScored = 0, isNoBall = true, penaltyRuns = 0),
            // Per-delivery override: Wide WITH +1 run turned ON
            BallEventEntity(id = 4, matchId = 3, inningsNumber = 1, sequence = 4, eventType = EventType.DELIVERY.name, strikerName = "Raju", nonStrikerName = "Sonu", bowlerName = "Vicky", runsScored = 0, isWide = true, penaltyRuns = 1),
            // Raju hits a 6
            BallEventEntity(id = 5, matchId = 3, inningsNumber = 1, sequence = 5, eventType = EventType.DELIVERY.name, strikerName = "Raju", nonStrikerName = "Sonu", bowlerName = "Vicky", runsScored = 6)
        )

        val snap = CricketEngine.computeMatchSnapshot(match, events)
        assertEquals(7, snap.innings1.totalRuns) // 0 (wide) + 0 (nb) + 1 (wide override) + 6
        assertEquals("0.1", snap.innings1.oversDisplay)
        assertEquals("0WD", snap.innings1.currentOverBalls[0].label)
        assertEquals("0NB", snap.innings1.currentOverBalls[1].label)
        assertEquals("WD", snap.innings1.currentOverBalls[2].label)

        val (_, statsMap) = CricketEngine.computeLeaderboardAndPlayerStats(listOf(match to events))
        val rajuStats = statsMap["raju"]!!
        assertEquals(1, rajuStats.matchesPlayed)
        assertEquals(6, rajuStats.runsScored)
        assertEquals(1, rajuStats.sixes)
        assertEquals(1, rajuStats.recentPerformances.size)
        assertEquals("Street Stars", rajuStats.recentPerformances.first().opponentTeam)
    }

    @Test
    fun `wagon wheel shots record coordinates, resolve fielding zones, and aggregate into player career`() {
        val match = MatchEntity(
            id = 4L,
            hostTeam = "Super Kings",
            visitorTeam = "Titans",
            tossWinner = "Super Kings",
            tossDecision = "BAT",
            totalOvers = 5,
            playersPerTeam = 11
        )
        val zoneCovers = com.example.ui.components.CricketFieldZones.resolveZone(0.8f, 0.3f)
        val zoneMidWicket = com.example.ui.components.CricketFieldZones.resolveZone(-0.8f, 0.3f)
        assertEquals("Covers", zoneCovers)
        assertEquals("Mid-Wicket", zoneMidWicket)

        val events = listOf(
            BallEventEntity(id = 1, matchId = 4, inningsNumber = 1, sequence = 1, eventType = EventType.START_INNINGS.name, strikerName = "Dhoni", nonStrikerName = "Jadeja", bowlerName = "Rashid"),
            BallEventEntity(id = 2, matchId = 4, inningsNumber = 1, sequence = 2, eventType = EventType.DELIVERY.name, strikerName = "Dhoni", nonStrikerName = "Jadeja", bowlerName = "Rashid", runsScored = 4, shotX = 0.8f, shotY = 0.3f, shotZone = zoneCovers),
            BallEventEntity(id = 3, matchId = 4, inningsNumber = 1, sequence = 3, eventType = EventType.DELIVERY.name, strikerName = "Dhoni", nonStrikerName = "Jadeja", bowlerName = "Rashid", runsScored = 6, shotX = -0.8f, shotY = 0.3f, shotZone = zoneMidWicket)
        )

        val snap = CricketEngine.computeMatchSnapshot(match, events)
        assertEquals(2, snap.innings1.shots.size)
        assertEquals("Covers", snap.innings1.shots[0].shotZone)
        assertEquals("0.1", snap.innings1.shots[0].overBallDisplay)
        assertTrue(snap.innings1.shots[0].isFour)
        assertEquals("Mid-Wicket", snap.innings1.shots[1].shotZone)
        assertTrue(snap.innings1.shots[1].isSix)

        val (_, statsMap) = CricketEngine.computeLeaderboardAndPlayerStats(listOf(match to events))
        val dhoniStats = statsMap["dhoni"]!!
        assertEquals(2, dhoniStats.careerShots.size)
    }
}

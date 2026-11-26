package com.example.data.repository

import com.example.data.cloud.AvatarDoc
import com.example.data.cloud.SavedTeamDoc
import com.example.data.local.BallEventEntity
import com.example.data.local.CricscoreDao
import com.example.data.local.DefaultMatchPreferences
import com.example.data.local.MatchEntity
import com.example.data.local.PlayerEntity
import com.example.data.local.PreferencesManager
import com.example.data.local.TeamEntity
import com.example.domain.CricketEngine
import com.example.domain.DismissalType
import com.example.domain.EventType
import com.example.domain.LeaderboardData
import com.example.domain.MatchRules
import com.example.domain.MatchSnapshot
import com.example.domain.PlayerCareerStats
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TeamWithPlayers(
    val team: TeamEntity,
    val players: List<PlayerEntity>
)

data class PlayerWithTeam(
    val player: PlayerEntity,
    val teamName: String,
    val teamShortCode: String
)

class CricscoreRepository(
    private val dao: CricscoreDao,
    private val prefs: PreferencesManager
) {

    val preferencesFlow: Flow<DefaultMatchPreferences> = prefs.preferencesFlow

    val teamsWithPlayersFlow: Flow<List<TeamWithPlayers>> = combine(
        dao.observeAllTeams(),
        dao.observeAllPlayers()
    ) { teams, players ->
        val byTeam = players.groupBy { it.teamId }
        teams.map { team ->
            TeamWithPlayers(team = team, players = byTeam[team.id].orEmpty())
        }
    }

    val allPlayersWithTeamFlow: Flow<List<PlayerWithTeam>> = combine(
        dao.observeAllTeams(),
        dao.observeAllPlayers()
    ) { teams, players ->
        val teamMap = teams.associateBy { it.id }
        players.map { p ->
            val t = teamMap[p.teamId]
            PlayerWithTeam(
                player = p,
                teamName = t?.name ?: "My Squad",
                teamShortCode = t?.shortCode ?: "SQD"
            )
        }
    }

    val allMatchSnapshotsFlow: Flow<List<MatchSnapshot>> = combine(
        dao.observeAllMatches(),
        dao.observeAllEvents()
    ) { matches, events ->
        val eventsByMatch = events.groupBy { it.matchId }
        matches.map { match ->
            CricketEngine.computeMatchSnapshot(match, eventsByMatch[match.id].orEmpty())
        }
    }

    val leaderboardAndCareerStatsFlow: Flow<Pair<LeaderboardData, Map<String, PlayerCareerStats>>> = combine(
        dao.observeAllMatches(),
        dao.observeAllEvents()
    ) { matches, events ->
        val eventsByMatch = events.groupBy { it.matchId }
        val pairs = matches.map { match -> match to eventsByMatch[match.id].orEmpty() }
        CricketEngine.computeLeaderboardAndPlayerStats(pairs)
    }

    fun observeMatchSnapshot(matchId: Long): Flow<MatchSnapshot?> = combine(
        dao.observeMatch(matchId),
        dao.observeEventsForMatch(matchId)
    ) { match, events ->
        match?.let { CricketEngine.computeMatchSnapshot(it, events) }
    }

    fun observePlayersByTeamName(teamName: String): Flow<List<String>> = teamsWithPlayersFlow.map { list ->
        list.firstOrNull { it.team.name.equals(teamName.trim(), ignoreCase = true) }
            ?.players
            ?.map { it.name }
            .orEmpty()
    }

    suspend fun setThemeMode(mode: ThemeMode) = prefs.setThemeMode(mode)

    suspend fun setAskShotDirection(enabled: Boolean) = prefs.setAskShotDirection(enabled)

    suspend fun updateDefaultRules(
        overs: Int,
        players: Int,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) = prefs.updateDefaultRules(
        overs = overs,
        players = players,
        wideReball = wideReball,
        wideRuns = wideRuns,
        noBallReball = noBallReball,
        noBallRuns = noBallRuns,
        lastManStands = lastManStands
    )

    suspend fun saveTeam(name: String, shortCode: String? = null, photoBase64: String = ""): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return -1L
        val existing = dao.getTeamByName(clean)
        if (existing != null) {
            if (photoBase64.isNotBlank() && existing.photoBase64 != photoBase64) {
                dao.updateTeamPhoto(existing.id, photoBase64)
            }
            return existing.id
        }
        val code = shortCode?.trim()?.uppercase()?.take(4).takeUnless { it.isNullOrEmpty() }
            ?: generateShortCode(clean)
        return dao.insertTeam(TeamEntity(name = clean, shortCode = code, photoBase64 = photoBase64))
    }

    suspend fun renameTeam(
        team: TeamEntity,
        newName: String,
        newShortCode: String,
        newPhotoBase64: String = team.photoBase64
    ) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        dao.updateTeam(
            team.copy(
                name = clean,
                shortCode = newShortCode.trim().uppercase().take(4).ifEmpty { generateShortCode(clean) },
                photoBase64 = newPhotoBase64
            )
        )
    }

    suspend fun updateTeamPhoto(teamId: Long, photoBase64: String) {
        dao.updateTeamPhoto(teamId, photoBase64)
    }

    suspend fun getTeamById(teamId: Long): TeamEntity? = dao.getTeamById(teamId)

    suspend fun getTeamWithPlayersByName(teamName: String): TeamWithPlayers? {
        val clean = teamName.trim()
        if (clean.isEmpty()) return null
        val team = dao.getTeamByName(clean) ?: return null
        val players = dao.getAllPlayersOnce().filter { it.teamId == team.id }
        return TeamWithPlayers(team = team, players = players)
    }

    suspend fun getAllTeamsWithPlayersOnce(): List<TeamWithPlayers> {
        val teams = dao.getAllTeamsOnce()
        val playersByTeam = dao.getAllPlayersOnce().groupBy { it.teamId }
        return teams.map { t -> TeamWithPlayers(team = t, players = playersByTeam[t.id].orEmpty()) }
    }

    suspend fun deleteTeam(teamId: Long) = dao.deleteTeam(teamId)

    suspend fun addPlayerToTeam(
        teamId: Long,
        playerName: String,
        role: String = "All-Rounder",
        photoBase64: String = ""
    ) {
        val clean = playerName.trim()
        if (clean.isEmpty()) return
        val existing = dao.getPlayerInTeam(teamId, clean)
        if (existing == null) {
            dao.insertPlayer(PlayerEntity(teamId = teamId, name = clean, role = role, photoBase64 = photoBase64))
        } else if (photoBase64.isNotBlank() && existing.photoBase64 != photoBase64) {
            dao.updatePlayerPhoto(existing.id, photoBase64)
        }
    }

    suspend fun addPlayerByTeamName(
        playerName: String,
        role: String,
        teamName: String,
        photoBase64: String = ""
    ) {
        val cleanPlayer = playerName.trim()
        if (cleanPlayer.isEmpty()) return
        val cleanTeam = teamName.trim().ifEmpty { "My Squad" }
        val teamId = saveTeam(cleanTeam)
        if (teamId > 0) {
            addPlayerToTeam(teamId, cleanPlayer, role, photoBase64)
        }
    }

    suspend fun updatePlayer(
        player: PlayerEntity,
        newName: String,
        newRole: String,
        newTeamName: String,
        newPhotoBase64: String = player.photoBase64
    ) {
        val cleanName = newName.trim()
        if (cleanName.isEmpty()) return
        val teamId = if (newTeamName.isNotBlank()) saveTeam(newTeamName.trim()) else player.teamId
        dao.updatePlayer(
            player.copy(
                name = cleanName,
                role = newRole,
                teamId = if (teamId > 0) teamId else player.teamId,
                photoBase64 = newPhotoBase64
            )
        )
    }

    suspend fun updatePlayerPhoto(playerId: Long, photoBase64: String) {
        dao.updatePlayerPhoto(playerId, photoBase64)
    }

    suspend fun hydrateAvatarsFromCloud(avatars: List<AvatarDoc>) {
        if (avatars.isEmpty()) return
        val localTeams = dao.getAllTeamsOnce().associateBy { it.name.trim().lowercase() }
        val localPlayers = dao.getAllPlayersOnce()
        val teamsById = localTeams.values.associateBy { it.id }

        avatars.forEach { avatar ->
            if (avatar.photoBase64.isBlank()) return@forEach
            if (avatar.entityType.equals("TEAM", ignoreCase = true)) {
                val existingTeam = localTeams[avatar.entityName.trim().lowercase()]
                if (existingTeam != null) {
                    if (existingTeam.photoBase64.isBlank()) {
                        dao.updateTeamPhoto(existingTeam.id, avatar.photoBase64)
                    }
                } else {
                    saveTeam(avatar.entityName, photoBase64 = avatar.photoBase64)
                }
            } else if (avatar.entityType.equals("PLAYER", ignoreCase = true)) {
                val matchingPlayers = localPlayers.filter { p ->
                    val sameName = p.name.equals(avatar.entityName.trim(), ignoreCase = true)
                    val tName = teamsById[p.teamId]?.name.orEmpty()
                    sameName && (avatar.teamName.isBlank() || tName.equals(avatar.teamName.trim(), ignoreCase = true))
                }
                if (matchingPlayers.isNotEmpty()) {
                    matchingPlayers.forEach { p ->
                        if (p.photoBase64.isBlank()) {
                            dao.updatePlayerPhoto(p.id, avatar.photoBase64)
                        }
                    }
                } else if (avatar.teamName.isNotBlank()) {
                    addPlayerByTeamName(
                        playerName = avatar.entityName,
                        role = "All-Rounder",
                        teamName = avatar.teamName,
                        photoBase64 = avatar.photoBase64
                    )
                }
            }
        }
    }

    suspend fun hydrateSavedTeamsFromCloud(savedTeams: List<SavedTeamDoc>) {
        if (savedTeams.isEmpty()) return
        savedTeams.forEach { doc ->
            val cleanTeamName = doc.teamName.trim()
            if (cleanTeamName.isEmpty()) return@forEach
            val existing = dao.getTeamByName(cleanTeamName)
            val teamId = if (existing == null) {
                saveTeam(
                    name = cleanTeamName,
                    shortCode = doc.shortCode.ifBlank { null },
                    photoBase64 = doc.photoBase64
                )
            } else {
                val needPhoto = existing.photoBase64.isBlank() && doc.photoBase64.isNotBlank()
                val needCode = existing.shortCode.isBlank() && doc.shortCode.isNotBlank()
                if (needPhoto || needCode) {
                    dao.updateTeam(
                        existing.copy(
                            shortCode = if (needCode) doc.shortCode.take(4).uppercase() else existing.shortCode,
                            photoBase64 = if (needPhoto) doc.photoBase64 else existing.photoBase64
                        )
                    )
                }
                existing.id
            }
            if (teamId > 0) {
                doc.playerEntries.forEach { entry ->
                    val (playerName, role) = SavedTeamDoc.decodePlayerEntry(entry) ?: return@forEach
                    addPlayerToTeam(teamId = teamId, playerName = playerName, role = role)
                }
            }
        }
    }

    suspend fun ensurePlayerSaved(teamName: String, playerName: String) {
        val cleanTeam = teamName.trim()
        val cleanPlayer = playerName.trim()
        if (cleanTeam.isEmpty() || cleanPlayer.isEmpty()) return
        val teamId = saveTeam(cleanTeam)
        if (teamId > 0) {
            addPlayerToTeam(teamId, cleanPlayer)
        }
    }

    suspend fun deletePlayer(playerId: Long) = dao.deletePlayer(playerId)

    suspend fun createMatch(
        hostTeam: String,
        visitorTeam: String,
        tossWinner: String,
        tossDecision: String,
        rules: MatchRules,
        openingStriker: String,
        openingNonStriker: String,
        openingBowler: String
    ): Long {
        val host = hostTeam.trim().ifEmpty { "Team A" }
        val visitor = visitorTeam.trim().ifEmpty { "Team B" }
        saveTeam(host)
        saveTeam(visitor)

        val matchId = dao.insertMatch(
            MatchEntity(
                hostTeam = host,
                visitorTeam = visitor,
                tossWinner = tossWinner,
                tossDecision = tossDecision,
                totalOvers = rules.totalOvers,
                playersPerTeam = rules.playersPerTeam,
                wideReball = rules.wideReball,
                wideRuns = rules.wideRuns,
                noBallReball = rules.noBallReball,
                noBallRuns = rules.noBallRuns,
                lastManStands = rules.lastManStands
            )
        )

        val batFirst = if (tossDecision.equals("BAT", ignoreCase = true)) {
            tossWinner
        } else {
            if (tossWinner == host) visitor else host
        }
        val bowlFirst = if (batFirst == host) visitor else host

        startInnings(
            matchId = matchId,
            inningsNumber = 1,
            battingTeam = batFirst,
            bowlingTeam = bowlFirst,
            striker = openingStriker,
            nonStriker = openingNonStriker,
            bowler = openingBowler
        )
        return matchId
    }

    suspend fun startInnings(
        matchId: Long,
        inningsNumber: Int,
        battingTeam: String,
        bowlingTeam: String,
        striker: String,
        nonStriker: String,
        bowler: String
    ) {
        val cleanStriker = striker.trim().ifEmpty { "Batter 1" }
        val cleanNonStriker = nonStriker.trim().ifEmpty { "Batter 2" }
        val cleanBowler = bowler.trim().ifEmpty { "Bowler 1" }

        ensurePlayerSaved(battingTeam, cleanStriker)
        ensurePlayerSaved(battingTeam, cleanNonStriker)
        ensurePlayerSaved(bowlingTeam, cleanBowler)

        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.START_INNINGS.name,
                strikerName = cleanStriker,
                nonStrikerName = cleanNonStriker,
                bowlerName = cleanBowler
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun recordDelivery(
        matchId: Long,
        inningsNumber: Int,
        battingTeam: String,
        bowlingTeam: String,
        striker: String,
        nonStriker: String,
        bowler: String,
        runsScored: Int,
        isWide: Boolean = false,
        isNoBall: Boolean = false,
        isBye: Boolean = false,
        isLegBye: Boolean = false,
        isWicket: Boolean = false,
        dismissalType: DismissalType? = null,
        outBatsmanName: String? = null,
        fielderName: String? = null,
        newBatsmanName: String? = null,
        penaltyRuns: Int = -1,
        shotX: Float? = null,
        shotY: Float? = null,
        shotZone: String? = null
    ) {
        ensurePlayerSaved(battingTeam, striker)
        ensurePlayerSaved(battingTeam, nonStriker)
        ensurePlayerSaved(bowlingTeam, bowler)
        if (!fielderName.isNullOrBlank()) {
            ensurePlayerSaved(bowlingTeam, fielderName)
        }
        if (!newBatsmanName.isNullOrBlank()) {
            ensurePlayerSaved(battingTeam, newBatsmanName)
        }
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.DELIVERY.name,
                strikerName = striker,
                nonStrikerName = nonStriker,
                bowlerName = bowler,
                runsScored = runsScored,
                isWide = isWide,
                isNoBall = isNoBall,
                isBye = isBye,
                isLegBye = isLegBye,
                isWicket = isWicket,
                dismissalType = dismissalType?.name,
                outBatsmanName = outBatsmanName,
                fielderName = fielderName?.trim()?.takeIf { it.isNotEmpty() },
                newBatsmanName = newBatsmanName?.trim()?.takeIf { it.isNotEmpty() },
                penaltyRuns = penaltyRuns,
                shotX = shotX,
                shotY = shotY,
                shotZone = shotZone
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun swapStrike(matchId: Long, inningsNumber: Int) {
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.SWAP_STRIKE.name
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun setNewBowler(matchId: Long, inningsNumber: Int, bowlingTeam: String, bowlerName: String) {
        val clean = bowlerName.trim()
        if (clean.isEmpty()) return
        ensurePlayerSaved(bowlingTeam, clean)
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.NEW_BOWLER.name,
                bowlerName = clean
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun setNewBatsman(
        matchId: Long,
        inningsNumber: Int,
        battingTeam: String,
        batsmanName: String,
        swapAfter: Boolean = false
    ) {
        val clean = batsmanName.trim()
        if (clean.isEmpty()) return
        ensurePlayerSaved(battingTeam, clean)
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.NEW_BATSMAN.name,
                newBatsmanName = clean,
                swapAfterEvent = swapAfter
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun retireBatter(
        matchId: Long,
        inningsNumber: Int,
        battingTeam: String,
        retiringBatter: String,
        replacementBatter: String
    ) {
        val cleanReplacement = replacementBatter.trim()
        if (cleanReplacement.isNotEmpty()) {
            ensurePlayerSaved(battingTeam, cleanReplacement)
        }
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.RETIRE_BATTER.name,
                outBatsmanName = retiringBatter,
                newBatsmanName = cleanReplacement
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun endInningsEarly(matchId: Long, inningsNumber: Int) {
        val nextSeq = dao.getMaxSequence(matchId) + 1
        dao.insertEvent(
            BallEventEntity(
                matchId = matchId,
                inningsNumber = inningsNumber,
                sequence = nextSeq,
                eventType = EventType.END_INNINGS.name
            )
        )
        syncMatchStatus(matchId)
    }

    suspend fun updateTotalOvers(matchId: Long, newOvers: Int) {
        dao.updateMatchOvers(matchId, newOvers.coerceIn(1, 99))
        syncMatchStatus(matchId)
    }

    suspend fun updateMatchRules(
        matchId: Long,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) {
        dao.updateMatchRules(
            matchId = matchId,
            wideReball = wideReball,
            wideRuns = wideRuns.coerceIn(0, 4),
            noBallReball = noBallReball,
            noBallRuns = noBallRuns.coerceIn(0, 4),
            lastManStands = lastManStands
        )
        syncMatchStatus(matchId)
    }

    suspend fun undoLastEvent(matchId: Long): Boolean {
        val last = dao.getLastEventForMatch(matchId) ?: return false
        // Keep the initial START_INNINGS event for innings 1 so match remains valid
        if (last.inningsNumber == 1 && last.eventType == EventType.START_INNINGS.name) {
            return false
        }
        dao.deleteEvent(last.id)
        syncMatchStatus(matchId)
        return true
    }

    suspend fun deleteMatch(matchId: Long) = dao.deleteMatch(matchId)

    private suspend fun syncMatchStatus(matchId: Long) {
        val match = dao.getMatchById(matchId) ?: return
        val events = dao.getEventsForMatch(matchId)
        val snap = CricketEngine.computeMatchSnapshot(match, events)
        dao.updateMatchStatus(
            matchId = matchId,
            isCompleted = snap.isMatchCompleted,
            summary = snap.resultSummary
        )
    }

    private fun generateShortCode(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.size >= 2 -> words.take(3).map { it.first().uppercaseChar() }.joinToString("")
            words.size == 1 -> words[0].take(3).uppercase()
            else -> "TM"
        }
    }
}

package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.DefaultMatchPreferences
import com.example.data.local.PlayerEntity
import com.example.data.local.TeamEntity
import com.example.data.repository.CricscoreRepository
import com.example.data.repository.PlayerWithTeam
import com.example.data.repository.TeamWithPlayers
import com.example.domain.LeaderboardData
import com.example.domain.MatchRules
import com.example.domain.MatchSnapshot
import com.example.domain.PlayerCareerStats
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewMatchFormState(
    val hostTeam: String = "Team A",
    val visitorTeam: String = "Team B",
    val tossWonByHost: Boolean = true,
    val optedToBat: Boolean = true,
    val totalOvers: Int = 10,
    val playersPerTeam: Int = 11,
    val wideReball: Boolean = true,
    val wideRuns: Int = 1,
    val noBallReball: Boolean = true,
    val noBallRuns: Int = 1,
    val lastManStands: Boolean = false
)

class CricscoreViewModel(
    private val repository: CricscoreRepository
) : ViewModel() {

    val preferences: StateFlow<DefaultMatchPreferences> = repository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultMatchPreferences())

    val teamsWithPlayers: StateFlow<List<TeamWithPlayers>> = repository.teamsWithPlayersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlayersWithTeam: StateFlow<List<PlayerWithTeam>> = repository.allPlayersWithTeamFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val matches: StateFlow<List<MatchSnapshot>> = repository.allMatchSnapshotsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val leaderboardAndStats: StateFlow<Pair<LeaderboardData, Map<String, PlayerCareerStats>>> =
        repository.leaderboardAndCareerStatsFlow
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                LeaderboardData() to emptyMap()
            )

    private val _formState = MutableStateFlow(NewMatchFormState())
    val formState: StateFlow<NewMatchFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = repository.preferencesFlow.first()
            _formState.update {
                it.copy(
                    totalOvers = saved.defaultOvers,
                    playersPerTeam = saved.defaultPlayers,
                    wideReball = saved.defaultWideReball,
                    wideRuns = saved.defaultWideRuns,
                    noBallReball = saved.defaultNoBallReball,
                    noBallRuns = saved.defaultNoBallRuns,
                    lastManStands = saved.defaultLastManStands
                )
            }
        }
    }

    fun updateHostTeam(name: String) = _formState.update { it.copy(hostTeam = name) }
    fun updateVisitorTeam(name: String) = _formState.update { it.copy(visitorTeam = name) }

    fun swapTeams() = _formState.update {
        it.copy(hostTeam = it.visitorTeam, visitorTeam = it.hostTeam)
    }

    fun setTossWonByHost(wonByHost: Boolean) = _formState.update { it.copy(tossWonByHost = wonByHost) }
    fun setOptedToBat(bat: Boolean) = _formState.update { it.copy(optedToBat = bat) }
    fun setOvers(overs: Int) = _formState.update { it.copy(totalOvers = overs.coerceIn(1, 99)) }
    fun setPlayersPerTeam(count: Int) = _formState.update { it.copy(playersPerTeam = count.coerceIn(2, 15)) }
    fun setWideReball(enabled: Boolean) = _formState.update { it.copy(wideReball = enabled) }
    fun setWideRunsEnabled(enabled: Boolean) = _formState.update {
        it.copy(wideRuns = if (enabled) it.wideRuns.coerceAtLeast(1) else 0)
    }
    fun setWideRuns(runs: Int) = _formState.update { it.copy(wideRuns = runs.coerceIn(0, 4)) }
    fun setNoBallReball(enabled: Boolean) = _formState.update { it.copy(noBallReball = enabled) }
    fun setNoBallRunsEnabled(enabled: Boolean) = _formState.update {
        it.copy(noBallRuns = if (enabled) it.noBallRuns.coerceAtLeast(1) else 0)
    }
    fun setNoBallRuns(runs: Int) = _formState.update { it.copy(noBallRuns = runs.coerceIn(0, 4)) }
    fun setLastManStands(enabled: Boolean) = _formState.update { it.copy(lastManStands = enabled) }

    fun startNewMatch(
        openingStriker: String,
        openingNonStriker: String,
        openingBowler: String,
        onCreated: (Long) -> Unit
    ) {
        val form = _formState.value
        val host = form.hostTeam.trim().ifEmpty { "Team A" }
        val visitor = form.visitorTeam.trim().ifEmpty { "Team B" }
        val tossWinner = if (form.tossWonByHost) host else visitor
        val tossDecision = if (form.optedToBat) "BAT" else "BOWL"
        val rules = MatchRules(
            totalOvers = form.totalOvers,
            playersPerTeam = form.playersPerTeam,
            wideReball = form.wideReball,
            wideRuns = form.wideRuns,
            noBallReball = form.noBallReball,
            noBallRuns = form.noBallRuns,
            lastManStands = form.lastManStands
        )
        viewModelScope.launch {
            val matchId = repository.createMatch(
                hostTeam = host,
                visitorTeam = visitor,
                tossWinner = tossWinner,
                tossDecision = tossDecision,
                rules = rules,
                openingStriker = openingStriker,
                openingNonStriker = openingNonStriker,
                openingBowler = openingBowler
            )
            onCreated(matchId)
        }
    }

    fun deleteMatch(matchId: Long) {
        viewModelScope.launch { repository.deleteMatch(matchId) }
    }

    fun addTeam(name: String, shortCode: String) {
        viewModelScope.launch { repository.saveTeam(name, shortCode) }
    }

    fun renameTeam(team: TeamEntity, newName: String, newShortCode: String) {
        viewModelScope.launch { repository.renameTeam(team, newName, newShortCode) }
    }

    fun deleteTeam(teamId: Long) {
        viewModelScope.launch { repository.deleteTeam(teamId) }
    }

    fun addPlayer(teamId: Long, playerName: String, role: String) {
        viewModelScope.launch { repository.addPlayerToTeam(teamId, playerName, role) }
    }

    fun addPlayerByTeamName(playerName: String, role: String, teamName: String) {
        viewModelScope.launch { repository.addPlayerByTeamName(playerName, role, teamName) }
    }

    fun updatePlayer(player: PlayerEntity, newName: String, newRole: String, newTeamName: String) {
        viewModelScope.launch { repository.updatePlayer(player, newName, newRole, newTeamName) }
    }

    fun deletePlayer(playerId: Long) {
        viewModelScope.launch { repository.deletePlayer(playerId) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setAskShotDirection(enabled: Boolean) {
        viewModelScope.launch { repository.setAskShotDirection(enabled) }
    }

    fun saveDefaultRules(
        overs: Int,
        players: Int,
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) {
        viewModelScope.launch {
            repository.updateDefaultRules(
                overs = overs,
                players = players,
                wideReball = wideReball,
                wideRuns = wideRuns,
                noBallReball = noBallReball,
                noBallRuns = noBallRuns,
                lastManStands = lastManStands
            )
        }
    }

    companion object {
        fun factory(repository: CricscoreRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CricscoreViewModel(repository) as T
                }
            }
    }
}

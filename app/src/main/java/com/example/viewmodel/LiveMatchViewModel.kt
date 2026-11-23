package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.LiveBroadcastRepository
import com.example.data.repository.CricscoreRepository
import com.example.data.repository.PlayerWithTeam
import com.example.data.repository.TeamWithPlayers
import com.example.domain.DismissalType
import com.example.domain.MatchSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeypadModifiers(
    val isWide: Boolean = false,
    val isNoBall: Boolean = false,
    val isBye: Boolean = false,
    val isLegBye: Boolean = false,
    val isWicket: Boolean = false,
    val includeExtraRunPenalty: Boolean = true
)

class LiveMatchViewModel(
    private val matchId: Long,
    private val repository: CricscoreRepository,
    private val broadcastRepository: LiveBroadcastRepository? = null
) : ViewModel() {

    val snapshot: StateFlow<MatchSnapshot?> = repository.observeMatchSnapshot(matchId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val teamsWithPlayers: StateFlow<List<TeamWithPlayers>> = repository.teamsWithPlayersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlayersWithTeam: StateFlow<List<PlayerWithTeam>> = repository.allPlayersWithTeamFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val askShotDirection: StateFlow<Boolean> = repository.preferencesFlow
        .let { flow ->
            kotlinx.coroutines.flow.MutableStateFlow(true).also { state ->
                viewModelScope.launch {
                    flow.collect { state.value = it.askShotDirection }
                }
            }
        }

    private val _modifiers = MutableStateFlow(KeypadModifiers())
    val modifiers: StateFlow<KeypadModifiers> = _modifiers.asStateFlow()

    private val _pendingWicketRuns = MutableStateFlow<Int?>(null)
    val pendingWicketRuns: StateFlow<Int?> = _pendingWicketRuns.asStateFlow()

    private val _pendingShotRuns = MutableStateFlow<Int?>(null)
    val pendingShotRuns: StateFlow<Int?> = _pendingShotRuns.asStateFlow()

    private val _liveShareCode = MutableStateFlow<String?>(null)
    val liveShareCode: StateFlow<String?> = _liveShareCode.asStateFlow()

    private val _isLiveSharingEnabled = MutableStateFlow(false)
    val isLiveSharingEnabled: StateFlow<Boolean> = _isLiveSharingEnabled.asStateFlow()

    private val _isSyncingLive = MutableStateFlow(false)
    val isSyncingLive: StateFlow<Boolean> = _isSyncingLive.asStateFlow()

    private val _liveSyncMessage = MutableStateFlow<String?>(null)
    val liveSyncMessage: StateFlow<String?> = _liveSyncMessage.asStateFlow()

    private var ballSeqCounter = 0L
    private var autoPublishAttempted = false

    init {
        if (broadcastRepository != null) {
            // Auto-reconnect to existing live share code, or auto-publish new active match with Admin's uid
            viewModelScope.launch {
                val existing = broadcastRepository.findExistingShareCodeForLocalMatch(matchId).getOrNull()
                if (!existing.isNullOrBlank()) {
                    _liveShareCode.value = existing
                    _isLiveSharingEnabled.value = true
                    autoPublishAttempted = true
                }
            }
            // Instant zero-lag push to Firestore whenever the match snapshot updates
            viewModelScope.launch {
                snapshot.collectLatest { snap ->
                    if (snap == null) return@collectLatest
                    val existingCode = _liveShareCode.value
                    if (existingCode.isNullOrBlank() && !autoPublishAttempted && !snap.isMatchCompleted) {
                        autoPublishAttempted = true
                        val newCode = broadcastRepository.generateShareCode()
                        ballSeqCounter += 1L
                        val res = broadcastRepository.publishOrUpdateLiveMatch(
                            shareCode = newCode,
                            snapshot = snap,
                            sharingEnabled = true,
                            ballSequence = ballSeqCounter
                        )
                        res.onSuccess { publishedCode ->
                            _liveShareCode.value = publishedCode
                            _isLiveSharingEnabled.value = true
                        }
                    } else if (!existingCode.isNullOrBlank() && _isLiveSharingEnabled.value) {
                        ballSeqCounter += 1L
                        broadcastRepository.publishOrUpdateLiveMatch(
                            shareCode = existingCode,
                            snapshot = snap,
                            sharingEnabled = true,
                            ballSequence = ballSeqCounter
                        )
                    }
                }
            }
        }
    }

    fun startOrResumeLiveBroadcast() {
        val repo = broadcastRepository ?: return
        val snap = snapshot.value ?: return
        viewModelScope.launch {
            _isSyncingLive.value = true
            val code = _liveShareCode.value ?: repo.generateShareCode()
            ballSeqCounter += 1L
            val result = repo.publishOrUpdateLiveMatch(
                shareCode = code,
                snapshot = snap,
                sharingEnabled = true,
                ballSequence = ballSeqCounter
            )
            _isSyncingLive.value = false
            result.onSuccess { publishedCode ->
                _liveShareCode.value = publishedCode
                _isLiveSharingEnabled.value = true
                _liveSyncMessage.value = "Live Broadcast Active! Share Code: $publishedCode"
            }.onFailure { err ->
                _liveSyncMessage.value = err.localizedMessage ?: "Failed to start live broadcast"
            }
        }
    }

    fun toggleLiveSharing(enabled: Boolean) {
        val repo = broadcastRepository ?: return
        val code = _liveShareCode.value ?: run {
            if (enabled) startOrResumeLiveBroadcast()
            return
        }
        viewModelScope.launch {
            _isSyncingLive.value = true
            val result = repo.setSharingEnabled(code, enabled)
            _isSyncingLive.value = false
            result.onSuccess {
                _isLiveSharingEnabled.value = enabled
                if (enabled) {
                    snapshot.value?.let { snap ->
                        ballSeqCounter += 1L
                        repo.publishOrUpdateLiveMatch(code, snap, true, ballSeqCounter)
                    }
                }
                _liveSyncMessage.value = if (enabled) "Live sharing ON ($code)" else "Live sharing paused"
            }.onFailure { err ->
                _liveSyncMessage.value = err.localizedMessage ?: "Failed to update live sharing"
            }
        }
    }

    fun clearLiveSyncMessage() {
        _liveSyncMessage.value = null
    }

    fun setAskShotDirection(enabled: Boolean) {
        viewModelScope.launch { repository.setAskShotDirection(enabled) }
    }

    fun toggleWide() {
        val defaultWideRunOn = (snapshot.value?.rules?.wideRuns ?: 1) > 0
        _modifiers.update {
            val next = !it.isWide
            it.copy(
                isWide = next,
                isNoBall = if (next) false else it.isNoBall,
                isBye = if (next) false else it.isBye,
                isLegBye = if (next) false else it.isLegBye,
                includeExtraRunPenalty = if (next) defaultWideRunOn else it.includeExtraRunPenalty
            )
        }
    }

    fun toggleNoBall() {
        val defaultNbRunOn = (snapshot.value?.rules?.noBallRuns ?: 1) > 0
        _modifiers.update {
            val next = !it.isNoBall
            it.copy(
                isNoBall = next,
                isWide = if (next) false else it.isWide,
                includeExtraRunPenalty = if (next) defaultNbRunOn else it.includeExtraRunPenalty
            )
        }
    }

    fun toggleExtraRunPenalty() = _modifiers.update {
        it.copy(includeExtraRunPenalty = !it.includeExtraRunPenalty)
    }

    fun toggleBye() = _modifiers.update {
        val next = !it.isBye
        it.copy(
            isBye = next,
            isLegBye = if (next) false else it.isLegBye,
            isWide = if (next) false else it.isWide
        )
    }

    fun toggleLegBye() = _modifiers.update {
        val next = !it.isLegBye
        it.copy(
            isLegBye = next,
            isBye = if (next) false else it.isBye,
            isWide = if (next) false else it.isWide
        )
    }

    fun toggleWicket() = _modifiers.update { it.copy(isWicket = !it.isWicket) }

    fun clearModifiers() {
        _modifiers.value = KeypadModifiers()
    }

    fun onRunButtonTapped(runs: Int) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (snap.isMatchCompleted || !inn.isStarted || inn.isCompleted) return
        if (inn.needsNewBatsman || inn.needsNewBowler) return

        val mods = _modifiers.value
        if (mods.isWicket) {
            _pendingWicketRuns.value = runs
            return
        }

        if (runs > 0 && askShotDirection.value) {
            _pendingShotRuns.value = runs
            return
        }

        recordDeliveryInternal(runs = runs, shotX = null, shotY = null, shotZone = null)
    }

    fun dismissShotDialog() {
        _pendingShotRuns.value = null
    }

    fun confirmShotAndRecord(shotX: Float?, shotY: Float?, shotZone: String?) {
        val runs = _pendingShotRuns.value ?: return
        _pendingShotRuns.value = null
        recordDeliveryInternal(runs = runs, shotX = shotX, shotY = shotY, shotZone = shotZone)
    }

    private fun recordDeliveryInternal(
        runs: Int,
        shotX: Float?,
        shotY: Float?,
        shotZone: String?
    ) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        val mods = _modifiers.value
        val effectivePenalty = resolvePenaltyRuns(mods, snap)

        viewModelScope.launch {
            repository.recordDelivery(
                matchId = matchId,
                inningsNumber = snap.currentInningsNumber,
                battingTeam = inn.battingTeam,
                bowlingTeam = inn.bowlingTeam,
                striker = inn.striker,
                nonStriker = inn.nonStriker,
                bowler = inn.currentBowler,
                runsScored = runs,
                isWide = mods.isWide,
                isNoBall = mods.isNoBall,
                isBye = mods.isBye,
                isLegBye = mods.isLegBye,
                penaltyRuns = effectivePenalty,
                shotX = shotX,
                shotY = shotY,
                shotZone = shotZone
            )
            clearModifiers()
        }
    }

    private fun resolvePenaltyRuns(mods: KeypadModifiers, snap: MatchSnapshot): Int {
        return when {
            mods.isWide -> if (mods.includeExtraRunPenalty) snap.rules.wideRuns.coerceAtLeast(1) else 0
            mods.isNoBall -> if (mods.includeExtraRunPenalty) snap.rules.noBallRuns.coerceAtLeast(1) else 0
            else -> -1
        }
    }

    fun openWicketDirectly() {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (snap.isMatchCompleted || !inn.isStarted || inn.isCompleted) return
        if (inn.needsNewBatsman || inn.needsNewBowler) return
        _pendingWicketRuns.value = 0
    }

    fun dismissWicketDialog() {
        _pendingWicketRuns.value = null
    }

    fun confirmWicket(
        runsScored: Int,
        dismissalType: DismissalType,
        outBatsmanName: String,
        fielderName: String,
        newBatsmanName: String
    ) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        val mods = _modifiers.value
        val effectivePenalty = resolvePenaltyRuns(mods, snap)
        _pendingWicketRuns.value = null
        viewModelScope.launch {
            repository.recordDelivery(
                matchId = matchId,
                inningsNumber = snap.currentInningsNumber,
                battingTeam = inn.battingTeam,
                bowlingTeam = inn.bowlingTeam,
                striker = inn.striker,
                nonStriker = inn.nonStriker,
                bowler = inn.currentBowler,
                runsScored = runsScored,
                isWide = mods.isWide,
                isNoBall = mods.isNoBall,
                isBye = mods.isBye,
                isLegBye = mods.isLegBye,
                isWicket = true,
                dismissalType = dismissalType,
                outBatsmanName = outBatsmanName,
                fielderName = fielderName,
                newBatsmanName = newBatsmanName,
                penaltyRuns = effectivePenalty
            )
            clearModifiers()
        }
    }

    fun swapStrike() {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (!inn.isStarted || inn.isCompleted || inn.nonStriker.isBlank()) return
        viewModelScope.launch {
            repository.swapStrike(matchId, snap.currentInningsNumber)
        }
    }

    fun setNextBowler(bowlerName: String) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (bowlerName.isBlank()) return
        viewModelScope.launch {
            repository.setNewBowler(matchId, snap.currentInningsNumber, inn.bowlingTeam, bowlerName)
        }
    }

    fun setNextBatsman(batsmanName: String) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (batsmanName.isBlank()) return
        viewModelScope.launch {
            repository.setNewBatsman(matchId, snap.currentInningsNumber, inn.battingTeam, batsmanName)
        }
    }

    fun retireBatter(retiringBatter: String, replacementBatter: String) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (retiringBatter.isBlank()) return
        viewModelScope.launch {
            repository.retireBatter(
                matchId = matchId,
                inningsNumber = snap.currentInningsNumber,
                battingTeam = inn.battingTeam,
                retiringBatter = retiringBatter,
                replacementBatter = replacementBatter
            )
        }
    }

    fun startSecondInnings(striker: String, nonStriker: String, bowler: String) {
        val snap = snapshot.value ?: return
        val inn2 = snap.innings2
        viewModelScope.launch {
            repository.startInnings(
                matchId = matchId,
                inningsNumber = 2,
                battingTeam = inn2.battingTeam,
                bowlingTeam = inn2.bowlingTeam,
                striker = striker,
                nonStriker = nonStriker,
                bowler = bowler
            )
        }
    }

    fun endCurrentInningsEarly() {
        val snap = snapshot.value ?: return
        viewModelScope.launch {
            repository.endInningsEarly(matchId, snap.currentInningsNumber)
        }
    }

    fun updateTotalOvers(newOvers: Int) {
        viewModelScope.launch {
            repository.updateTotalOvers(matchId, newOvers)
        }
    }

    fun updateMatchRules(
        wideReball: Boolean,
        wideRuns: Int,
        noBallReball: Boolean,
        noBallRuns: Int,
        lastManStands: Boolean
    ) {
        viewModelScope.launch {
            repository.updateMatchRules(
                matchId = matchId,
                wideReball = wideReball,
                wideRuns = wideRuns,
                noBallReball = noBallReball,
                noBallRuns = noBallRuns,
                lastManStands = lastManStands
            )
        }
    }

    fun undoLastBall(onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val undone = repository.undoLastEvent(matchId)
            clearModifiers()
            onResult(undone)
        }
    }

    fun rosterForTeam(teamName: String): List<String> {
        return teamsWithPlayers.value
            .firstOrNull { it.team.name.equals(teamName.trim(), ignoreCase = true) }
            ?.players
            ?.map { it.name }
            .orEmpty()
    }

    companion object {
        fun factory(
            matchId: Long,
            repository: CricscoreRepository,
            broadcastRepository: LiveBroadcastRepository? = null
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LiveMatchViewModel(matchId, repository, broadcastRepository) as T
                }
            }
    }
}

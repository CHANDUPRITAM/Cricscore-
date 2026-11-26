package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.LiveBroadcastRepository
import com.example.data.cloud.PlayerCareerDoc
import com.example.data.cloud.PlayerCareerSyncInput
import com.example.data.cloud.refreshAdminVerificationStatus
import com.example.data.cloud.sendAdminVerificationEmail
import com.example.data.repository.CricscoreRepository
import com.example.data.repository.PlayerWithTeam
import com.example.data.repository.TeamWithPlayers
import com.example.domain.DismissalType
import com.example.domain.MatchSnapshot
import com.example.domain.PlayerCareerStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
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

    private val _isAdminEmailVerified = MutableStateFlow(
        broadcastRepository?.isCurrentAdminEmailVerified() ?: false
    )
    val isAdminEmailVerified: StateFlow<Boolean> = _isAdminEmailVerified.asStateFlow()

    private val _requiresEmailVerificationPrompt = MutableStateFlow(false)
    val requiresEmailVerificationPrompt: StateFlow<Boolean> = _requiresEmailVerificationPrompt.asStateFlow()

    private val _isVerificationBusy = MutableStateFlow(false)
    val isVerificationBusy: StateFlow<Boolean> = _isVerificationBusy.asStateFlow()

    private var ballSeqCounter = 0L
    private var autoPublishAttempted = false

    private fun teamPhotoFor(teamName: String): String {
        return teamsWithPlayers.value
            .firstOrNull { it.team.name.equals(teamName.trim(), ignoreCase = true) }
            ?.team?.photoBase64
            .orEmpty()
    }

    init {
        if (broadcastRepository != null) {
            // Auto-reconnect to existing live share code, and only auto-publish new matches once Admin email is verified
            viewModelScope.launch {
                val verified = broadcastRepository.refreshCurrentAdminVerification()
                _isAdminEmailVerified.value = verified

                val existing = broadcastRepository.findExistingShareCodeForLocalMatch(matchId).getOrNull()
                if (!existing.isNullOrBlank()) {
                    _liveShareCode.value = existing
                    _isLiveSharingEnabled.value = true
                    autoPublishAttempted = true
                }
                snapshot.collectLatest { snap ->
                    if (snap == null) return@collectLatest
                    val existingCode = _liveShareCode.value
                    val hostPhoto = teamPhotoFor(snap.hostTeam)
                    val visitorPhoto = teamPhotoFor(snap.visitorTeam)
                    val currentlyVerified = _isAdminEmailVerified.value || broadcastRepository.isCurrentAdminEmailVerified()
                    if (currentlyVerified != _isAdminEmailVerified.value) {
                        _isAdminEmailVerified.value = currentlyVerified
                    }
                    if (existingCode.isNullOrBlank() && !autoPublishAttempted && !snap.isMatchCompleted) {
                        if (!currentlyVerified) {
                            return@collectLatest
                        }
                        autoPublishAttempted = true
                        val newCode = broadcastRepository.generateShareCode()
                        ballSeqCounter += 1L
                        val res = broadcastRepository.publishOrUpdateLiveMatch(
                            shareCode = newCode,
                            snapshot = snap,
                            sharingEnabled = true,
                            ballSequence = ballSeqCounter,
                            hostTeamPhoto = hostPhoto,
                            visitorTeamPhoto = visitorPhoto
                        )
                        res.onSuccess { publishedCode ->
                            _liveShareCode.value = publishedCode
                            _isLiveSharingEnabled.value = true
                        }
                    } else if (!existingCode.isNullOrBlank() && _isLiveSharingEnabled.value && currentlyVerified) {
                        ballSeqCounter += 1L
                        broadcastRepository.publishOrUpdateLiveMatch(
                            shareCode = existingCode,
                            snapshot = snap,
                            sharingEnabled = true,
                            ballSequence = ballSeqCounter,
                            hostTeamPhoto = hostPhoto,
                            visitorTeamPhoto = visitorPhoto
                        )
                    }
                }
            }

            // Sync complete career profiles (batting, bowling, wagon wheel, history, photo) for all match & squad players
            viewModelScope.launch {
                combine(
                    _liveShareCode,
                    _isLiveSharingEnabled,
                    snapshot,
                    repository.leaderboardAndCareerStatsFlow,
                    allPlayersWithTeam
                ) { shareCode, sharingEnabled, snap, leaderboardPair, playersList ->
                    CareerSyncBundle(
                        shareCode = shareCode,
                        sharingEnabled = sharingEnabled,
                        snapshot = snap,
                        statsMap = leaderboardPair.second,
                        players = playersList
                    )
                }.collectLatest { bundle ->
                    val code = bundle.shareCode ?: return@collectLatest
                    val snap = bundle.snapshot ?: return@collectLatest
                    if (!bundle.sharingEnabled) return@collectLatest
                    val verified = _isAdminEmailVerified.value || broadcastRepository.isCurrentAdminEmailVerified()
                    if (!verified) return@collectLatest

                    val inputs = buildMatchPlayerCareerInputs(
                        snap = snap,
                        statsMap = bundle.statsMap,
                        playersList = bundle.players
                    )
                    if (inputs.isNotEmpty()) {
                        broadcastRepository.syncMatchPlayerCareers(code, inputs)
                    }
                }
            }
        }
    }

    private data class CareerSyncBundle(
        val shareCode: String?,
        val sharingEnabled: Boolean,
        val snapshot: MatchSnapshot?,
        val statsMap: Map<String, PlayerCareerStats>,
        val players: List<PlayerWithTeam>
    )

    private fun buildMatchPlayerCareerInputs(
        snap: MatchSnapshot,
        statsMap: Map<String, PlayerCareerStats>,
        playersList: List<PlayerWithTeam>
    ): List<PlayerCareerSyncInput> {
        val playerLookup = playersList.associateBy {
            PlayerCareerDoc.cleanName(it.player.name).lowercase()
        }
        val namesWithTeam = linkedMapOf<String, Pair<String, String>>() // lowercase -> (cleanName, fallbackTeam)

        fun register(rawName: String?, fallbackTeam: String) {
            val clean = PlayerCareerDoc.cleanName(rawName.orEmpty())
            if (clean.isNotBlank()) {
                namesWithTeam.putIfAbsent(clean.lowercase(), clean to fallbackTeam)
            }
        }

        // Squad rosters
        playersList.filter { it.teamName.equals(snap.hostTeam, ignoreCase = true) }
            .forEach { register(it.player.name, snap.hostTeam) }
        playersList.filter { it.teamName.equals(snap.visitorTeam, ignoreCase = true) }
            .forEach { register(it.player.name, snap.visitorTeam) }

        // Innings 1 & 2 active/historical players in this match
        listOf(snap.innings1, snap.innings2).forEach { inn ->
            inn.batters.forEach { register(it.name, inn.battingTeam) }
            inn.bowlers.forEach { register(it.name, inn.bowlingTeam) }
            register(inn.striker, inn.battingTeam)
            register(inn.nonStriker, inn.battingTeam)
            register(inn.currentBowler, inn.bowlingTeam)
            inn.fallOfWickets.forEach { fow ->
                register(fow.batterName, inn.battingTeam)
            }
        }
        register(snap.playerOfMatch?.playerName, snap.hostTeam)

        return namesWithTeam.map { (lowerKey, pair) ->
            val (cleanName, fallbackTeam) = pair
            val entity = playerLookup[lowerKey]
            val stats = statsMap[lowerKey]
            PlayerCareerSyncInput(
                playerName = entity?.player?.name ?: stats?.playerName ?: cleanName,
                teamName = entity?.teamName ?: stats?.teamName ?: fallbackTeam,
                role = entity?.player?.role ?: "All-Rounder",
                photoBase64 = entity?.player?.photoBase64.orEmpty(),
                stats = stats
            )
        }
    }

    fun startOrResumeLiveBroadcast() {
        val repo = broadcastRepository ?: return
        val snap = snapshot.value ?: return
        viewModelScope.launch {
            _isSyncingLive.value = true
            val verified = repo.refreshCurrentAdminVerification()
            _isAdminEmailVerified.value = verified
            if (!verified) {
                _isSyncingLive.value = false
                _requiresEmailVerificationPrompt.value = true
                _liveSyncMessage.value = "Verify your Admin email before going live with a new match."
                return@launch
            }

            val code = _liveShareCode.value ?: repo.generateShareCode()
            ballSeqCounter += 1L
            val result = repo.publishOrUpdateLiveMatch(
                shareCode = code,
                snapshot = snap,
                sharingEnabled = true,
                ballSequence = ballSeqCounter,
                hostTeamPhoto = teamPhotoFor(snap.hostTeam),
                visitorTeamPhoto = teamPhotoFor(snap.visitorTeam)
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
            if (enabled) {
                val verified = repo.refreshCurrentAdminVerification()
                _isAdminEmailVerified.value = verified
                if (!verified) {
                    _requiresEmailVerificationPrompt.value = true
                    _liveSyncMessage.value = "Verify your Admin email before enabling live broadcast."
                    return@launch
                }
            }
            _isSyncingLive.value = true
            val result = repo.setSharingEnabled(code, enabled)
            _isSyncingLive.value = false
            result.onSuccess {
                _isLiveSharingEnabled.value = enabled
                if (enabled) {
                    snapshot.value?.let { snap ->
                        ballSeqCounter += 1L
                        repo.publishOrUpdateLiveMatch(
                            shareCode = code,
                            snapshot = snap,
                            sharingEnabled = true,
                            ballSequence = ballSeqCounter,
                            hostTeamPhoto = teamPhotoFor(snap.hostTeam),
                            visitorTeamPhoto = teamPhotoFor(snap.visitorTeam)
                        )
                    }
                }
                _liveSyncMessage.value = if (enabled) "Live sharing ON ($code)" else "Live sharing paused"
            }.onFailure { err ->
                _liveSyncMessage.value = err.localizedMessage ?: "Failed to update live sharing"
            }
        }
    }

    fun dismissEmailVerificationPrompt() {
        _requiresEmailVerificationPrompt.value = false
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _isVerificationBusy.value = true
            val res = sendAdminVerificationEmail()
            _isVerificationBusy.value = false
            res.onSuccess {
                _liveSyncMessage.value = "Verification link sent! Please check your inbox and spam folder."
            }.onFailure { err ->
                _liveSyncMessage.value = err.message ?: "Failed to send verification email."
            }
        }
    }

    fun recheckEmailVerificationAndGoLive() {
        viewModelScope.launch {
            _isVerificationBusy.value = true
            val res = refreshAdminVerificationStatus()
            _isVerificationBusy.value = false
            res.onSuccess { verified ->
                _isAdminEmailVerified.value = verified
                if (verified) {
                    _requiresEmailVerificationPrompt.value = false
                    startOrResumeLiveBroadcast()
                } else {
                    _liveSyncMessage.value = "Email is still not verified. Please click the link in your inbox first."
                }
            }.onFailure { err ->
                _liveSyncMessage.value = err.message ?: "Could not check verification status."
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
            if (newBatsmanName.isNotBlank()) syncTeamByNameToCloud(inn.battingTeam)
            if (fielderName.isNotBlank()) syncTeamByNameToCloud(inn.bowlingTeam)
            clearModifiers()
        }
    }

    private suspend fun syncTeamByNameToCloud(teamName: String) {
        val cloud = broadcastRepository ?: return
        val twp = repository.getTeamWithPlayersByName(teamName) ?: return
        cloud.syncSavedTeam(
            teamName = twp.team.name,
            shortCode = twp.team.shortCode,
            photoBase64 = twp.team.photoBase64,
            playerEntries = twp.players.map { it.name to it.role }
        )
    }

    fun addSavedPlayer(
        playerName: String,
        role: String,
        teamName: String,
        photoBase64: String = ""
    ) {
        val cleanName = playerName.trim()
        if (cleanName.isEmpty()) return
        val resolvedTeam = teamName.trim().ifEmpty { "My Squad" }
        viewModelScope.launch {
            repository.addPlayerByTeamName(cleanName, role, resolvedTeam, photoBase64)
            syncTeamByNameToCloud(resolvedTeam)
            if (photoBase64.isNotBlank()) {
                broadcastRepository?.syncAvatarPhoto(
                    entityType = "PLAYER",
                    entityName = cleanName,
                    teamName = resolvedTeam,
                    photoBase64 = photoBase64
                )
            }
            _liveSyncMessage.value = "$cleanName saved to $resolvedTeam"
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
            syncTeamByNameToCloud(inn.bowlingTeam)
        }
    }

    fun setNextBatsman(batsmanName: String) {
        val snap = snapshot.value ?: return
        val inn = snap.currentInnings
        if (batsmanName.isBlank()) return
        viewModelScope.launch {
            repository.setNewBatsman(matchId, snap.currentInningsNumber, inn.battingTeam, batsmanName)
            syncTeamByNameToCloud(inn.battingTeam)
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
            if (replacementBatter.isNotBlank()) syncTeamByNameToCloud(inn.battingTeam)
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
            syncTeamByNameToCloud(inn2.battingTeam)
            syncTeamByNameToCloud(inn2.bowlingTeam)
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

    fun deleteCurrentMatch(onDeleted: () -> Unit) {
        val existingCode = _liveShareCode.value
        viewModelScope.launch {
            if (!existingCode.isNullOrBlank()) {
                broadcastRepository?.deleteLiveBroadcast(existingCode)
            } else {
                broadcastRepository?.deleteLiveBroadcastByLocalMatchId(matchId)
            }
            _liveShareCode.value = null
            _isLiveSharingEnabled.value = false
            repository.deleteMatch(matchId)
            onDeleted()
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

package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudUiState
import com.example.data.cloud.LiveBroadcastRepository
import com.example.data.cloud.LiveMatchDoc
import com.example.data.cloud.PlayerCareerDoc
import com.example.data.cloud.buildFallbackCareerStats
import com.example.data.cloud.extractPlayerHints
import com.example.data.cloud.toDomainCareerStats
import com.example.domain.PlayerCareerStats
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SpectatorSearchPlayerItem(
    val playerName: String,
    val teamName: String,
    val role: String,
    val photoBase64: String,
    val shareCode: String,
    val matchTitle: String,
    val quickStatsSummary: String,
    val matchesPlayed: Int = 0,
    val runsScored: Int = 0,
    val wicketsTaken: Int = 0,
    val isFromWatchedMatch: Boolean
)

sealed interface SpectatorPlayerCareerState {
    val playerName: String

    data class Loading(override val playerName: String) : SpectatorPlayerCareerState
    data class Ready(
        override val playerName: String,
        val teamName: String,
        val role: String,
        val photoBase64: String,
        val stats: PlayerCareerStats?,
        val isLiveMatchFallbackOnly: Boolean = false
    ) : SpectatorPlayerCareerState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSpectatorViewModel(
    private val broadcastRepository: LiveBroadcastRepository,
    private val currentUserId: String?
) : ViewModel() {

    val isSignedInAdmin: Boolean
        get() = !currentUserId.isNullOrBlank()

    private val _activeShareCode = MutableStateFlow("")
    val activeShareCode: StateFlow<String> = _activeShareCode.asStateFlow()

    private val _codeInputError = MutableStateFlow<String?>(null)
    val codeInputError: StateFlow<String?> = _codeInputError.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _playerSearchQuery = MutableStateFlow("")
    val playerSearchQuery: StateFlow<String> = _playerSearchQuery.asStateFlow()

    // Pair of (cleanPlayerName, targetShareCode)
    private val _selectedPlayerTarget = MutableStateFlow<Pair<String, String>?>(null)

    /**
     * Public real-time feed of all shared live matches (works without logging in!).
     */
    val publicLiveMatches: StateFlow<CloudUiState<List<LiveMatchDoc>>> =
        broadcastRepository.observePublicLiveMatches()
            .map<List<LiveMatchDoc>, CloudUiState<List<LiveMatchDoc>>> { CloudUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing public live matches", rootError)
                emit(CloudUiState.Error(rootError.message ?: "Unable to load live matches"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    /**
     * Signed-in Admin's own live broadcasts (empty when viewing as unauthenticated spectator).
     */
    val myBroadcasts: StateFlow<CloudUiState<List<LiveMatchDoc>>> =
        if (currentUserId.isNullOrBlank()) {
            flowOf<CloudUiState<List<LiveMatchDoc>>>(CloudUiState.Success(emptyList()))
        } else {
            broadcastRepository.observeMyBroadcasts(currentUserId)
                .map<List<LiveMatchDoc>, CloudUiState<List<LiveMatchDoc>>> { CloudUiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing admin broadcasts", rootError)
                    emit(CloudUiState.Error(rootError.message ?: "Unable to load your broadcasts"))
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (currentUserId.isNullOrBlank()) CloudUiState.Success(emptyList()) else CloudUiState.Loading
        )

    val watchedMatchState: StateFlow<CloudUiState<LiveMatchDoc?>> = _activeShareCode
        .flatMapLatest { code ->
            if (code.isBlank()) {
                flowOf(CloudUiState.Idle)
            } else {
                broadcastRepository.observeLiveMatch(code)
                    .map<LiveMatchDoc?, CloudUiState<LiveMatchDoc?>> { doc ->
                        CloudUiState.Success(doc)
                    }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val rootError = firestoreError ?: error
                        Log.w(TAG, "Error watching live match $code", rootError)
                        val friendly = if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            "Live sharing is turned off by the Match Admin for code '$code'."
                        } else {
                            rootError.message ?: "Failed to connect to live match"
                        }
                        emit(CloudUiState.Error(friendly))
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = CloudUiState.Idle
        )

    private val allKnownMatchCareerDocs: StateFlow<List<PlayerCareerDoc>> =
        combine(
            _activeShareCode,
            publicLiveMatches,
            myBroadcasts
        ) { activeCode, publicState, adminState ->
            val publicCodes = (publicState as? CloudUiState.Success)?.data?.map { it.shareCode }.orEmpty()
            val adminCodes = (adminState as? CloudUiState.Success)?.data?.map { it.shareCode }.orEmpty()
            (listOf(activeCode) + publicCodes + adminCodes)
                .filter { it.isNotBlank() }
                .distinct()
        }.flatMapLatest { codes ->
            broadcastRepository.observePlayerCareersForShareCodes(codes)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val allSearchablePlayers: StateFlow<List<SpectatorSearchPlayerItem>> =
        combine(
            watchedMatchState,
            allKnownMatchCareerDocs,
            publicLiveMatches,
            myBroadcasts,
            _activeShareCode
        ) { watched, careerDocs, publicState, adminState, activeCode ->
            val itemsByName = linkedMapOf<String, SpectatorSearchPlayerItem>()

            val watchedDoc = (watched as? CloudUiState.Success)?.data
            val watchedCode = watchedDoc?.shareCode?.ifBlank { activeCode } ?: activeCode
            val publicDocs = (publicState as? CloudUiState.Success)?.data.orEmpty()
            val adminDocs = (adminState as? CloudUiState.Success)?.data.orEmpty()
            val matchTitlesByCode = (listOfNotNull(watchedDoc) + publicDocs + adminDocs)
                .associate { it.shareCode.uppercase() to "${it.hostTeam} v ${it.visitorTeam}" }

            fun upsertItem(item: SpectatorSearchPlayerItem) {
                val key = item.playerName.lowercase()
                val existing = itemsByName[key]
                if (existing == null) {
                    itemsByName[key] = item
                } else {
                    val preferNewStats = item.matchesPlayed > existing.matchesPlayed ||
                        (item.matchesPlayed == existing.matchesPlayed &&
                            (item.runsScored + item.wicketsTaken * 20) > (existing.runsScored + existing.wicketsTaken * 20))
                    itemsByName[key] = existing.copy(
                        teamName = if (preferNewStats && item.teamName.isNotBlank()) item.teamName else existing.teamName.ifBlank { item.teamName },
                        role = if (existing.role == "All-Rounder" && item.role != "All-Rounder") item.role else existing.role,
                        photoBase64 = existing.photoBase64.ifBlank { item.photoBase64 },
                        shareCode = if (preferNewStats && item.shareCode.isNotBlank()) item.shareCode
                        else if (item.isFromWatchedMatch && !existing.isFromWatchedMatch) item.shareCode
                        else existing.shareCode,
                        matchTitle = existing.matchTitle.ifBlank { item.matchTitle },
                        quickStatsSummary = if (preferNewStats && item.quickStatsSummary.isNotBlank()) item.quickStatsSummary
                        else existing.quickStatsSummary.ifBlank { item.quickStatsSummary },
                        matchesPlayed = maxOf(existing.matchesPlayed, item.matchesPlayed),
                        runsScored = maxOf(existing.runsScored, item.runsScored),
                        wicketsTaken = maxOf(existing.wicketsTaken, item.wicketsTaken),
                        isFromWatchedMatch = existing.isFromWatchedMatch || item.isFromWatchedMatch
                    )
                }
            }

            // 1. All full career-synced players across watched, public, and admin live matches
            careerDocs.forEach { doc ->
                val clean = PlayerCareerDoc.cleanName(doc.playerName)
                if (clean.isNotBlank()) {
                    val domain = doc.toDomainCareerStats()
                    val code = doc.shareCode.ifBlank { watchedCode }
                    val summary = buildString {
                        if (doc.matchesPlayed > 0L) append("${doc.matchesPlayed}M")
                        if (domain.runsScored > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${domain.runsScored} runs")
                        }
                        if (domain.wicketsTaken > 0) {
                            if (isNotEmpty()) append(" • ")
                            append("${domain.wicketsTaken} wkts")
                        }
                    }
                    upsertItem(
                        SpectatorSearchPlayerItem(
                            playerName = clean,
                            teamName = doc.teamName.ifBlank { watchedDoc?.hostTeam ?: "Squad" },
                            role = doc.role.ifBlank { "All-Rounder" },
                            photoBase64 = doc.photoBase64,
                            shareCode = code,
                            matchTitle = matchTitlesByCode[code.uppercase()].orEmpty(),
                            quickStatsSummary = summary,
                            matchesPlayed = doc.matchesPlayed.toInt(),
                            runsScored = domain.runsScored,
                            wicketsTaken = domain.wicketsTaken,
                            isFromWatchedMatch = code.equals(watchedCode, ignoreCase = true) && watchedCode.isNotBlank()
                        )
                    )
                }
            }

            // 2. Active & scorecard players directly from watched + public + admin LiveMatchDocs
            (listOfNotNull(watchedDoc) + publicDocs + adminDocs).forEach { matchDoc ->
                val code = matchDoc.shareCode.ifBlank { watchedCode }
                val matchTitle = "${matchDoc.hostTeam} v ${matchDoc.visitorTeam}"
                matchDoc.extractPlayerHints().forEach { hint ->
                    upsertItem(
                        SpectatorSearchPlayerItem(
                            playerName = hint.playerName,
                            teamName = hint.teamName,
                            role = hint.role,
                            photoBase64 = "",
                            shareCode = code,
                            matchTitle = matchTitle,
                            quickStatsSummary = hint.quickSummary,
                            isFromWatchedMatch = code.equals(watchedCode, ignoreCase = true) && watchedCode.isNotBlank()
                        )
                    )
                }
            }

            itemsByName.values.sortedWith(
                compareByDescending<SpectatorSearchPlayerItem> { it.isFromWatchedMatch }
                    .thenByDescending { it.matchesPlayed }
                    .thenBy { it.playerName.lowercase() }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val filteredSearchPlayers: StateFlow<List<SpectatorSearchPlayerItem>> =
        combine(allSearchablePlayers, _playerSearchQuery) { players, rawQuery ->
            val q = rawQuery.trim().lowercase()
            if (q.isEmpty()) {
                players
            } else {
                players.filter { item ->
                    item.playerName.lowercase().contains(q) ||
                        item.teamName.lowercase().contains(q) ||
                        item.role.lowercase().contains(q) ||
                        item.matchTitle.lowercase().contains(q)
                }.sortedWith(
                    compareByDescending<SpectatorSearchPlayerItem> { it.playerName.lowercase().startsWith(q) }
                        .thenByDescending { it.isFromWatchedMatch }
                        .thenBy { it.playerName.lowercase() }
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val selectedPlayerCareerState: StateFlow<SpectatorPlayerCareerState?> =
        _selectedPlayerTarget.flatMapLatest { target ->
            val cleanName = target?.first?.let { PlayerCareerDoc.cleanName(it) }.orEmpty()
            val targetCode = target?.second?.trim().orEmpty()
            if (targetCode.isBlank() || cleanName.isBlank()) {
                flowOf(null)
            } else {
                broadcastRepository.observePlayerCareer(targetCode, cleanName)
                    .combine<PlayerCareerDoc?, LiveMatchDoc?, SpectatorPlayerCareerState>(
                        combine(watchedMatchState, publicLiveMatches) { watched, publicState ->
                            val watchedDoc = (watched as? CloudUiState.Success)?.data
                            if (watchedDoc != null && watchedDoc.shareCode.equals(targetCode, ignoreCase = true)) {
                                watchedDoc
                            } else {
                                (publicState as? CloudUiState.Success)?.data
                                    ?.firstOrNull { it.shareCode.equals(targetCode, ignoreCase = true) }
                                    ?: watchedDoc
                            }
                        }
                    ) { careerDoc, liveDoc ->
                        if (careerDoc != null) {
                            val domainStats = careerDoc.toDomainCareerStats()
                            SpectatorPlayerCareerState.Ready(
                                playerName = careerDoc.playerName.ifBlank { cleanName },
                                teamName = careerDoc.teamName.ifBlank { liveDoc?.hostTeam ?: "Squad" },
                                role = careerDoc.role.ifBlank { "All-Rounder" },
                                photoBase64 = careerDoc.photoBase64,
                                stats = domainStats,
                                isLiveMatchFallbackOnly = false
                            )
                        } else {
                            val fallback = liveDoc?.buildFallbackCareerStats(cleanName)
                            SpectatorPlayerCareerState.Ready(
                                playerName = fallback?.playerName ?: cleanName,
                                teamName = fallback?.teamName ?: liveDoc?.hostTeam ?: "Squad",
                                role = "All-Rounder",
                                photoBase64 = "",
                                stats = fallback,
                                isLiveMatchFallbackOnly = true
                            )
                        }
                    }
                    .onStart { emit(SpectatorPlayerCareerState.Loading(cleanName)) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Fallback player career load for $cleanName", firestoreError ?: error)
                        val watchedDoc = (watchedMatchState.value as? CloudUiState.Success)?.data
                        val fallbackDoc = if (watchedDoc?.shareCode.equals(targetCode, ignoreCase = true)) {
                            watchedDoc
                        } else {
                            (publicLiveMatches.value as? CloudUiState.Success)?.data
                                ?.firstOrNull { it.shareCode.equals(targetCode, ignoreCase = true) }
                                ?: watchedDoc
                        }
                        val fallback = fallbackDoc?.buildFallbackCareerStats(cleanName)
                        emit(
                            SpectatorPlayerCareerState.Ready(
                                playerName = fallback?.playerName ?: cleanName,
                                teamName = fallback?.teamName ?: fallbackDoc?.hostTeam ?: "Squad",
                                role = "All-Rounder",
                                photoBase64 = "",
                                stats = fallback,
                                isLiveMatchFallbackOnly = true
                            )
                        )
                    }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    fun updatePlayerSearchQuery(query: String) {
        _playerSearchQuery.value = query.take(60)
    }

    fun openPlayerCareer(rawPlayerName: String, shareCodeOverride: String? = null) {
        val clean = PlayerCareerDoc.cleanName(rawPlayerName)
        val resolvedCode = shareCodeOverride?.takeIf { it.isNotBlank() } ?: _activeShareCode.value
        if (clean.isNotBlank() && resolvedCode.isNotBlank()) {
            _selectedPlayerTarget.value = clean to resolvedCode
        }
    }

    fun closePlayerCareer() {
        _selectedPlayerTarget.value = null
    }

    fun watchMatchByCode(rawCode: String) {
        val normalized = broadcastRepository.normalizeShareCode(rawCode)
        if (normalized.length < 6) {
            _codeInputError.value = "Enter a valid 6-character Match Code (e.g. CS4829)."
            return
        }
        _codeInputError.value = null
        _selectedPlayerTarget.value = null
        _activeShareCode.value = normalized
    }

    fun stopWatching() {
        _selectedPlayerTarget.value = null
        _activeShareCode.value = ""
        _codeInputError.value = null
    }

    fun toggleBroadcastSharing(shareCode: String, enabled: Boolean) {
        if (!isSignedInAdmin) return
        viewModelScope.launch {
            val result = broadcastRepository.setSharingEnabled(shareCode, enabled)
            _actionMessage.value = if (result.isSuccess) {
                if (enabled) "Live sharing enabled for $shareCode" else "Live sharing paused for $shareCode"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Could not update live sharing"
            }
        }
    }

    fun deleteBroadcast(shareCode: String) {
        if (!isSignedInAdmin) return
        viewModelScope.launch {
            val result = broadcastRepository.deleteLiveBroadcast(shareCode)
            if (result.isSuccess && _activeShareCode.value.equals(shareCode, ignoreCase = true)) {
                stopWatching()
            }
            _actionMessage.value = if (result.isSuccess) {
                "Broadcast $shareCode deleted"
            } else {
                result.exceptionOrNull()?.localizedMessage ?: "Could not delete broadcast"
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    companion object {
        private const val TAG = "LiveSpectatorVM"

        fun factory(
            broadcastRepository: LiveBroadcastRepository,
            currentUserId: String?
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LiveSpectatorViewModel(broadcastRepository, currentUserId) as T
            }
        }
    }
}

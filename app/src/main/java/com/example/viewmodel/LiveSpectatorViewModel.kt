package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudUiState
import com.example.data.cloud.LiveBroadcastRepository
import com.example.data.cloud.LiveMatchDoc
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    fun watchMatchByCode(rawCode: String) {
        val normalized = broadcastRepository.normalizeShareCode(rawCode)
        if (normalized.length < 6) {
            _codeInputError.value = "Enter a valid 6-character Match Code (e.g. CS4829)."
            return
        }
        _codeInputError.value = null
        _activeShareCode.value = normalized
    }

    fun stopWatching() {
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

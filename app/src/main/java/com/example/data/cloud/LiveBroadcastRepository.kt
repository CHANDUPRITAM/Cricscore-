package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.domain.MatchSnapshot
import com.example.domain.PlayerCareerStats
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

data class PlayerCareerSyncInput(
    val playerName: String,
    val teamName: String,
    val role: String,
    val photoBase64: String,
    val stats: PlayerCareerStats?
)

class LiveBroadcastRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val createdShareCodes = mutableSetOf<String>()
    private val syncedCareerSignatures = mutableMapOf<String, MutableMap<String, Int>>()

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Admin must be signed in to publish or update live match scores.")
    }

    fun isCurrentAdminEmailVerified(): Boolean {
        return auth.currentUser?.isEmailVerified == true
    }

    suspend fun refreshCurrentAdminVerification(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            user.reload().await()
            val refreshed = auth.currentUser ?: return false
            if (refreshed.isEmailVerified) {
                refreshed.getIdToken(true).await()
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            auth.currentUser?.isEmailVerified == true
        }
    }

    fun generateShareCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val suffix = buildString(4) {
            repeat(4) { append(alphabet[Random.nextInt(alphabet.length)]) }
        }
        return "CS$suffix"
    }

    fun normalizeShareCode(raw: String): String {
        // Also support pasting full share messages containing "Code: CS7K4M"
        val match = Regex("\\b(CS[A-Z0-9]{4,14})\\b", RegexOption.IGNORE_CASE).find(raw)
        val candidate = match?.groupValues?.getOrNull(1) ?: raw
        return candidate.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(16)
    }

    suspend fun syncAdminProfile(
        displayName: String,
        email: String?,
        clubName: String? = null
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existingSnap = docRef.get().await()
            val cleanName = displayName.trim().ifEmpty { "Cricscore Admin" }.take(80)
            val cleanEmail = email?.trim()?.take(120)?.takeIf { it.isNotEmpty() }
            val cleanClub = clubName?.trim()?.take(80)?.takeIf { it.isNotEmpty() }

            if (!existingSnap.exists()) {
                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to cleanName,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail != null) payload["email"] = cleanEmail
                if (cleanClub != null) payload["clubName"] = cleanClub
                docRef.set(payload).await()
            } else {
                val updatePayload = mutableMapOf<String, Any>(
                    "displayName" to cleanName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (cleanEmail != null) updatePayload["email"] = cleanEmail
                if (cleanClub != null) updatePayload["clubName"] = cleanClub
                docRef.update(updatePayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun findExistingShareCodeForLocalMatch(localMatchId: Long): Result<String?> {
        val path = "live_matches"
        return try {
            val uid = requireUserId()
            val snap = db.collection(path)
                .whereEqualTo("adminId", uid)
                .get()
                .await()
            val existing = snap.documents
                .mapNotNull { it.toObject(LiveMatchDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .firstOrNull { it.localMatchId == localMatchId }
            if (existing != null) {
                createdShareCodes.add(existing.shareCode)
            }
            Result.success(existing?.shareCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }

    suspend fun syncAvatarPhoto(
        entityType: String,
        entityName: String,
        teamName: String = "",
        photoBase64: String
    ): Result<Unit> {
        val cleanName = entityName.trim().take(80)
        if (cleanName.isEmpty()) return Result.success(Unit)
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val docId = AvatarDoc.docIdFor(entityType, cleanName, teamName)
        val docRef = db.collection("users").document(uid).collection("avatars").document(docId)
        return try {
            if (photoBase64.isBlank()) {
                docRef.delete().await()
            } else {
                val payload = mapOf(
                    "ownerUid" to uid,
                    "entityType" to if (entityType.equals("TEAM", ignoreCase = true)) "TEAM" else "PLAYER",
                    "entityName" to cleanName,
                    "teamName" to teamName.trim().take(80),
                    "photoBase64" to photoBase64.take(32000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchMyAvatars(): Result<List<AvatarDoc>> {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(emptyList())
        val colRef = db.collection("users").document(uid).collection("avatars")
        return try {
            val snap = colRef.get().await()
            val list = snap.documents.mapNotNull {
                it.toObject(AvatarDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncSavedTeam(
        teamName: String,
        shortCode: String,
        photoBase64: String,
        playerEntries: List<Pair<String, String>>
    ): Result<Unit> {
        val cleanName = teamName.trim().take(80)
        if (cleanName.isEmpty()) return Result.success(Unit)
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val docId = SavedTeamDoc.docIdFor(cleanName)
        val docRef = db.collection("users").document(uid).collection("saved_teams").document(docId)
        val encodedPlayers = playerEntries
            .distinctBy { it.first.trim().lowercase() }
            .filter { it.first.trim().isNotEmpty() }
            .take(60)
            .map { (name, role) -> SavedTeamDoc.encodePlayerEntry(name, role) }
        return try {
            val payload = mapOf(
                "ownerUid" to uid,
                "teamName" to cleanName,
                "shortCode" to shortCode.trim().uppercase().take(8),
                "photoBase64" to photoBase64.take(32000),
                "playerEntries" to encodedPlayers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSavedTeam(teamName: String): Result<Unit> {
        val cleanName = teamName.trim()
        if (cleanName.isEmpty()) return Result.success(Unit)
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val docId = SavedTeamDoc.docIdFor(cleanName)
        val docRef = db.collection("users").document(uid).collection("saved_teams").document(docId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchMySavedTeams(): Result<List<SavedTeamDoc>> {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(emptyList())
        val colRef = db.collection("users").document(uid).collection("saved_teams")
        return try {
            val snap = colRef.get().await()
            val list = snap.documents.mapNotNull {
                it.toObject(SavedTeamDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            Result.success(list)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun publishOrUpdateLiveMatch(
        shareCode: String,
        snapshot: MatchSnapshot,
        sharingEnabled: Boolean = true,
        ballSequence: Long = 0L,
        hostTeamPhoto: String = "",
        visitorTeamPhoto: String = ""
    ): Result<String> {
        val normalized = normalizeShareCode(shareCode)
        val docPath = "live_matches/$normalized"
        return try {
            val uid = requireUserId()
            require(normalized.length in 6..16) { "Invalid share code format" }
            val adminName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: auth.currentUser?.email?.substringBefore("@")
                ?: "Match Admin"
            val docRef = db.collection("live_matches").document(normalized)

            val alreadyExists = if (normalized in createdShareCodes) {
                true
            } else {
                val snap = docRef.get().await()
                if (snap.exists()) {
                    createdShareCodes.add(normalized)
                    true
                } else {
                    false
                }
            }

            if (!alreadyExists) {
                val isVerified = isCurrentAdminEmailVerified() || refreshCurrentAdminVerification()
                if (!isVerified) {
                    val emailLabel = auth.currentUser?.email ?: "your email"
                    throw IllegalStateException(
                        "Please verify $emailLabel from your inbox before going live with a new match."
                    )
                }
                val createPayload = LiveMatchMapper.buildCreatePayload(
                    snapshot = snapshot,
                    adminId = uid,
                    adminName = adminName,
                    sharingEnabled = sharingEnabled,
                    ballSequence = ballSequence,
                    hostTeamPhoto = hostTeamPhoto,
                    visitorTeamPhoto = visitorTeamPhoto
                )
                docRef.set(createPayload).await()
                createdShareCodes.add(normalized)
            } else {
                val updatePayload = LiveMatchMapper.buildMutableFieldsMap(
                    snapshot = snapshot,
                    adminName = adminName,
                    sharingEnabled = sharingEnabled,
                    ballSequence = ballSequence,
                    hostTeamPhoto = hostTeamPhoto,
                    visitorTeamPhoto = visitorTeamPhoto
                )
                docRef.update(updatePayload).await()
            }
            Result.success(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun setSharingEnabled(shareCode: String, enabled: Boolean): Result<Unit> {
        val normalized = normalizeShareCode(shareCode)
        val docPath = "live_matches/$normalized"
        return try {
            requireUserId()
            require(normalized.length in 6..16) { "Invalid share code format" }
            db.collection("live_matches")
                .document(normalized)
                .update(
                    mapOf(
                        "sharingEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Zero-lag real-time spectator stream via direct single-document Firestore snapshots().
     * Works WITHOUT login for any match where sharingEnabled == true!
     */
    fun observeLiveMatch(shareCode: String): Flow<LiveMatchDoc?> = flow {
        val normalized = normalizeShareCode(shareCode)
        require(normalized.length in 6..16) { "Enter a valid 6-character Match Code (e.g. CS4829)." }
        val docPath = "live_matches/$normalized"
        emitAll(
            db.collection("live_matches")
                .document(normalized)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(LiveMatchDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        )
    }

    /**
     * Public feed of all currently shared live matches — accessible by spectators WITHOUT logging in.
     * Strictly aligned with `allow list: if resource.data.sharingEnabled == true`.
     */
    fun observePublicLiveMatches(): Flow<List<LiveMatchDoc>> = flow {
        val path = "live_matches"
        emitAll(
            db.collection(path)
                .whereEqualTo("sharingEnabled", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(LiveMatchDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<LiveMatchDoc> { if (it.matchStatus == "LIVE") 0 else if (it.matchStatus == "INNINGS_BREAK") 1 else 2 }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Admin's own live broadcasts list — strictly aligned with `allow list: if resource.data.adminId == request.auth.uid`.
     */
    fun observeMyBroadcasts(adminId: String?): Flow<List<LiveMatchDoc>> {
        val uid = auth.currentUser?.uid
        if (adminId.isNullOrBlank() || uid == null || uid != adminId) {
            return flowOf(emptyList())
        }
        val path = "live_matches"
        return db.collection(path)
            .whereEqualTo("adminId", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LiveMatchDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.updatedAt?.seconds ?: 0L }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun syncMatchPlayerCareers(
        shareCode: String,
        players: List<PlayerCareerSyncInput>
    ): Result<Unit> {
        val normalized = normalizeShareCode(shareCode)
        val colPath = "live_matches/$normalized/player_careers"
        return try {
            val uid = requireUserId()
            require(normalized.length in 6..16) { "Invalid share code format" }
            if (!(isCurrentAdminEmailVerified() || refreshCurrentAdminVerification())) {
                return Result.success(Unit)
            }

            val cache = syncedCareerSignatures.getOrPut(normalized) { mutableMapOf() }
            val changedPayloads = players
                .distinctBy { PlayerCareerDoc.cleanName(it.playerName).lowercase() }
                .filter { PlayerCareerDoc.cleanName(it.playerName).isNotBlank() }
                .map { input ->
                    PlayerCareerMapper.buildPayload(
                        adminId = uid,
                        shareCode = normalized,
                        playerName = input.playerName,
                        teamName = input.teamName,
                        role = input.role,
                        photoBase64 = input.photoBase64,
                        stats = input.stats
                    )
                }
                .filter { payload -> cache[payload.docId] != payload.signature }

            if (changedPayloads.isEmpty()) {
                return Result.success(Unit)
            }

            val colRef = db.collection("live_matches").document(normalized).collection("player_careers")
            changedPayloads.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { payload ->
                    batch.set(colRef.document(payload.docId), payload.fields)
                }
                batch.commit().await()
                chunk.forEach { payload ->
                    cache[payload.docId] = payload.signature
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, colPath)
            Result.failure(e)
        }
    }

    fun observePlayerCareer(shareCode: String, playerName: String): Flow<PlayerCareerDoc?> = flow {
        val normalized = normalizeShareCode(shareCode)
        val cleanName = PlayerCareerDoc.cleanName(playerName)
        require(normalized.length in 6..16) { "Invalid match code" }
        require(cleanName.isNotBlank()) { "Invalid player name" }
        val docId = PlayerCareerDoc.docIdFor(cleanName)
        val docPath = "live_matches/$normalized/player_careers/$docId"
        emitAll(
            db.collection("live_matches")
                .document(normalized)
                .collection("player_careers")
                .document(docId)
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) null
                    else snapshot.toObject(PlayerCareerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        )
    }

    fun observeMatchPlayerCareers(shareCode: String): Flow<List<PlayerCareerDoc>> = flow {
        val normalized = normalizeShareCode(shareCode)
        if (normalized.length !in 6..16) {
            emit(emptyList())
            return@flow
        }
        val colPath = "live_matches/$normalized/player_careers"
        emitAll(
            db.collection("live_matches")
                .document(normalized)
                .collection("player_careers")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents
                        .mapNotNull { it.toObject(PlayerCareerDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                        .sortedBy { it.playerName.lowercase() }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colPath)
                    }
                    emit(emptyList())
                }
        )
    }

    fun observePlayerCareersForShareCodes(shareCodes: List<String>): Flow<List<PlayerCareerDoc>> {
        val codes = shareCodes
            .map { normalizeShareCode(it) }
            .filter { it.length in 6..16 }
            .distinct()
            .take(12)
        if (codes.isEmpty()) return flowOf(emptyList())
        return kotlinx.coroutines.flow.combine(codes.map { observeMatchPlayerCareers(it) }) { lists ->
            lists.flatMap { it }
        }
    }

    suspend fun deleteLiveBroadcast(shareCode: String): Result<Unit> {
        val normalized = normalizeShareCode(shareCode)
        if (normalized.length !in 6..16) return Result.success(Unit)
        val docRef = db.collection("live_matches").document(normalized)
        return try {
            requireUserId()
            // Clean up player_careers subcollection best-effort before deleting parent live match document
            try {
                val careerSnap = docRef.collection("player_careers").get().await()
                careerSnap.documents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { d -> batch.delete(d.reference) }
                    batch.commit().await()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Proceed to delete main broadcast doc even if subcollection cleanup had partial error
            }
            docRef.delete().await()
            createdShareCodes.remove(normalized)
            syncedCareerSignatures.remove(normalized)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteLiveBroadcastByLocalMatchId(localMatchId: Long): Result<Unit> {
        if (auth.currentUser?.uid.isNullOrBlank()) return Result.success(Unit)
        return try {
            val code = findExistingShareCodeForLocalMatch(localMatchId).getOrNull()
            if (!code.isNullOrBlank()) {
                deleteLiveBroadcast(code)
            } else {
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.domain.MatchSnapshot
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

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Admin must be signed in to publish or update live match scores.")
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

    suspend fun publishOrUpdateLiveMatch(
        shareCode: String,
        snapshot: MatchSnapshot,
        sharingEnabled: Boolean = true,
        ballSequence: Long = 0L
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
                val createPayload = LiveMatchMapper.buildCreatePayload(
                    snapshot = snapshot,
                    adminId = uid,
                    adminName = adminName,
                    sharingEnabled = sharingEnabled,
                    ballSequence = ballSequence
                )
                docRef.set(createPayload).await()
                createdShareCodes.add(normalized)
            } else {
                val updatePayload = LiveMatchMapper.buildMutableFieldsMap(
                    snapshot = snapshot,
                    adminName = adminName,
                    sharingEnabled = sharingEnabled,
                    ballSequence = ballSequence
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
}

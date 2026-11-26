package com.example.data.cloud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.util.Patterns
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val AUTH_TAG = "Auth"

data class SignUpOutcome(
    val user: FirebaseUser,
    val verificationEmailSent: Boolean,
    val verificationWarning: String? = null
)

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun friendlyAuthError(e: Exception): String {
    if (e is FirebaseAuthException) {
        return when (e.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" ->
                "Invalid email or password. Please check and try again."
            "ERROR_USER_NOT_FOUND" ->
                "No Admin account found with this email. Tap 'Create Account' to sign up."
            "ERROR_EMAIL_ALREADY_IN_USE" ->
                "This email is already registered. Please switch to 'Admin Login'."
            "ERROR_WEAK_PASSWORD" ->
                "Password is too weak. Please use at least 6 characters."
            "ERROR_OPERATION_NOT_ALLOWED" ->
                "Email/Password sign-in is disabled in Firebase Console. Please enable it under Authentication -> Sign-in method."
            "ERROR_TOO_MANY_REQUESTS" ->
                "Too many attempts. Please wait a moment and try again."
            "ERROR_NETWORK_REQUEST_FAILED" ->
                "No internet connection. Please check your network and try again."
            "ERROR_USER_DISABLED" ->
                "This Admin account has been disabled."
            "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                "An account already exists with this email using another sign-in method."
            else -> e.localizedMessage ?: "Authentication failed (${e.errorCode})."
        }
    }
    return e.localizedMessage ?: "Authentication failed. Please try again."
}

/**
 * Email & Password Admin Login.
 */
suspend fun signInAdminWithEmail(
    email: String,
    password: String
): Result<FirebaseUser> {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
        return Result.failure(IllegalArgumentException("Please enter a valid email address."))
    }
    if (password.length < 6) {
        return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
    }
    return try {
        val result = Firebase.auth.signInWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user
            ?: return Result.failure(IllegalStateException("Signed in, but user session was empty."))
        runCatching {
            user.reload().await()
            if (user.isEmailVerified) {
                user.getIdToken(true).await()
            }
        }
        Result.success(Firebase.auth.currentUser ?: user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Email/Password sign-in failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
    }
}

/**
 * Create a new Admin Account (Sign Up) using Email & Password, save displayName,
 * and trigger Firebase sendEmailVerification().
 */
suspend fun signUpAdminWithEmail(
    fullName: String,
    email: String,
    password: String,
    confirmPassword: String
): Result<SignUpOutcome> {
    val cleanName = fullName.trim()
    val cleanEmail = email.trim()
    if (cleanName.length < 2) {
        return Result.failure(IllegalArgumentException("Please enter your Admin name (at least 2 characters)."))
    }
    if (cleanEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
        return Result.failure(IllegalArgumentException("Please enter a valid email address."))
    }
    if (password.length < 6) {
        return Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
    }
    if (password != confirmPassword) {
        return Result.failure(IllegalArgumentException("Passwords do not match."))
    }
    return try {
        val result = Firebase.auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val user = result.user
            ?: return Result.failure(IllegalStateException("Account created, but user session was empty."))
        try {
            val profileUpdates = userProfileChangeRequest {
                displayName = cleanName.take(80)
            }
            user.updateProfile(profileUpdates).await()
        } catch (profileErr: Exception) {
            Log.w(AUTH_TAG, "Profile displayName update warning: ${profileErr.message}")
        }

        var verificationSent = false
        var verificationWarning: String? = null
        try {
            user.sendEmailVerification().await()
            verificationSent = true
        } catch (verifyErr: Exception) {
            Log.w(AUTH_TAG, "sendEmailVerification warning: ${verifyErr.message}", verifyErr)
            verificationWarning = friendlyAuthError(verifyErr)
        }

        runCatching { user.reload().await() }
        Result.success(
            SignUpOutcome(
                user = Firebase.auth.currentUser ?: user,
                verificationEmailSent = verificationSent,
                verificationWarning = verificationWarning
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Email/Password sign-up failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
    }
}

/**
 * Send (or resend) Firebase email verification link to the signed-in Admin.
 */
suspend fun sendAdminVerificationEmail(): Result<Unit> {
    val user = Firebase.auth.currentUser
        ?: return Result.failure(IllegalStateException("Please sign in first."))
    return try {
        user.reload().await()
        val refreshed = Firebase.auth.currentUser ?: user
        if (refreshed.isEmailVerified) {
            refreshed.getIdToken(true).await()
            return Result.success(Unit)
        }
        refreshed.sendEmailVerification().await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "sendEmailVerification failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
    }
}

/**
 * Reload current user from Firebase and force-refresh the JWT when verified so
 * Firestore security rules immediately observe `request.auth.token.email_verified == true`.
 */
suspend fun refreshAdminVerificationStatus(): Result<Boolean> {
    val user = Firebase.auth.currentUser
        ?: return Result.failure(IllegalStateException("Not signed in."))
    return try {
        user.reload().await()
        val refreshed = Firebase.auth.currentUser ?: user
        if (refreshed.isEmailVerified) {
            refreshed.getIdToken(true).await()
            Result.success(true)
        } else {
            Result.success(false)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Verification status refresh failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
    }
}

/**
 * Send a secure password reset email via Firebase sendPasswordResetEmail().
 * Uses neutral messaging on unknown accounts to prevent email enumeration.
 */
suspend fun sendAdminPasswordResetEmail(email: String): Result<Unit> {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
        return Result.failure(IllegalArgumentException("Please enter a valid email address."))
    }
    return try {
        Firebase.auth.sendPasswordResetEmail(cleanEmail).await()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e is FirebaseAuthException && e.errorCode == "ERROR_USER_NOT_FOUND") {
            // Do not leak whether an email is registered
            Result.success(Unit)
        } else {
            Log.e(AUTH_TAG, "Password reset email failed", e)
            Result.failure(IllegalStateException(friendlyAuthError(e), e))
        }
    }
}

/**
 * Silent auto sign-in using GetGoogleIdOption ONLY (single credential option per request).
 */
fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Exception) {
        onUnauthenticated()
        return
    }
    if (clientId.isBlank() || clientId == "placeholder_client_id") {
        onUnauthenticated()
        return
    }
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(clientId)
        .setAutoSelectEnabled(true)
        .build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

    scope.launch {
        try {
            val activity = context.findActivity() ?: run {
                onUnauthenticated()
                return@launch
            }
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                if (idToken.isBlank()) {
                    onUnauthenticated()
                    return@launch
                }
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

/**
 * Interactive Google Sign-In using GetSignInWithGoogleOption ONLY (single credential option per request).
 */
fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id).trim()
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found.")
        return
    }
    if (clientId.isBlank() || clientId == "placeholder_client_id" || !clientId.endsWith(".apps.googleusercontent.com")) {
        onAuthError("Google Sign-In is not configured for this build (missing valid Web Client ID).")
        return
    }
    val activity = context.findActivity()
    if (activity == null) {
        onAuthError("Unable to launch Google Sign-In from current screen.")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()

    scope.launch {
        try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleCredential.idToken
                if (idToken.isBlank()) {
                    onAuthError("Google Sign-In returned an empty ID token. Please try again.")
                    return@launch
                }
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                runCatching { Firebase.auth.currentUser?.getIdToken(false)?.await() }
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received from Google.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: NoCredentialException) {
            Log.w(AUTH_TAG, "No Google credentials available on device: ${e.message}", e)
            onAuthError("No Google account found on this device. Add a Google account in device Settings or sign in with Email.")
        } catch (e: GetCredentialProviderConfigurationException) {
            Log.e(AUTH_TAG, "Credential provider configuration error: ${e.message}", e)
            onAuthError("Google Play Services Credential Manager is not configured properly on this device.")
        } catch (e: GetCredentialUnsupportedException) {
            Log.e(AUTH_TAG, "Credential Manager unsupported: ${e.message}", e)
            onAuthError("Google Sign-In is not supported on this device. Please use Email & Password login.")
        } catch (e: GetCredentialException) {
            Log.e(AUTH_TAG, "Credential Manager error: ${e.type} - ${e.message}", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In could not complete. Please try again.")
        } catch (e: FirebaseAuthException) {
            Log.e(AUTH_TAG, "Firebase Google credential exchange failed", e)
            onAuthError(friendlyAuthError(e))
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Google Sign-In failed. Please try again.")
        }
    }
}

/**
 * Safe sign-out wrapping Credential Manager cleanup and Firebase sign-out in NonCancellable.
 */
fun signOutWithCleanup(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    beforeSignOut: suspend () -> Unit = {},
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                beforeSignOut()
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Pre-signout cleanup warning: ${e.message}")
            }
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(AUTH_TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}

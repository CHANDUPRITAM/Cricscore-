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
        Result.success(user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Email/Password sign-in failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
    }
}

/**
 * Create a new Admin Account (Sign Up) using Email & Password and save displayName.
 */
suspend fun signUpAdminWithEmail(
    fullName: String,
    email: String,
    password: String,
    confirmPassword: String
): Result<FirebaseUser> {
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
            user.reload().await()
        } catch (profileErr: Exception) {
            Log.w(AUTH_TAG, "Profile displayName update warning: ${profileErr.message}")
        }
        Result.success(Firebase.auth.currentUser ?: user)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(AUTH_TAG, "Email/Password sign-up failed", e)
        Result.failure(IllegalStateException(friendlyAuthError(e), e))
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
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
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
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
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
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
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
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type received.")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(AUTH_TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(AUTH_TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
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

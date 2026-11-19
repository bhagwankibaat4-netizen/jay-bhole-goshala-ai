package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.LoginPortal
import com.example.data.model.StaffAccount
import com.example.data.model.StaffInvite
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private const val BOOTSTRAPPED_ADMIN_EMAIL = "bhagwankibaat4@gmail.com"
private const val TAG = "AuthSessionRepo"

sealed interface SessionResolution {
    data class Authorized(val session: UserSession) : SessionResolution
    data class UnauthorizedPortal(
        val messageHi: String,
        val messageEn: String,
        val uid: String,
        val email: String
    ) : SessionResolution
}

class AuthSessionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun resolveVerifiedSession(
        user: FirebaseUser,
        intendedPortal: LoginPortal? = null
    ): Result<SessionResolution> {
        val uid = user.uid
        val email = (
            user.email?.takeIf { it.isNotBlank() }
                ?: user.providerData.firstOrNull { !it.email.isNullOrBlank() }?.email
            )?.trim()?.lowercase().orEmpty()
        val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: email.substringBefore('@').ifBlank { "भक्त / User" }
        val photoUrl = user.photoUrl?.toString()?.take(500)

        return try {
            withTimeout(15_000L) {
                // 1. Ensure /users/{uid} exists (always PUBLIC on self-create; rules forbid client role escalation)
                val userDocRef = db.collection("users").document(uid)
                val userSnap = userDocRef.get().await()
                if (!userSnap.exists()) {
                    val payload = mapOf(
                        "userId" to uid,
                        "displayName" to displayName.take(100),
                        "email" to (email.ifBlank { "$uid@users.local" }).take(120),
                        "photoUrl" to photoUrl,
                        "role" to "PUBLIC",
                        "preferredLanguage" to "hi",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    withTimeoutOrNull(4_000L) { userDocRef.set(payload).await() }
                } else {
                    val updateFields = mutableMapOf<String, Any>(
                        "displayName" to displayName.take(100),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (photoUrl != null) {
                        updateFields["photoUrl"] = photoUrl
                    }
                    try {
                        withTimeoutOrNull(3_000L) { userDocRef.update(updateFields).await() }
                    } catch (e: Exception) {
                        Log.w(TAG, "Non-fatal profile refresh skipped while offline/busy: ${e.message}")
                    }
                }

                // 2. Check trusted Admin authorization (/admins/{uid} active OR bootstrapped verified owner email)
                val isBootstrapped = user.isEmailVerified && email == BOOTSTRAPPED_ADMIN_EMAIL
                val adminRef = db.collection("admins").document(uid)
                val adminSnap = adminRef.get().await()
                val hasActiveAdminRecord = adminSnap.exists() && (adminSnap.getBoolean("active") != false)

                if (isBootstrapped || hasActiveAdminRecord) {
                    // Ensure bootstrapped owner has an authoritative /admins/{uid} document
                    if (isBootstrapped && !adminSnap.exists()) {
                        try {
                            withTimeoutOrNull(4_000L) {
                                adminRef.set(
                                    mapOf(
                                        "uid" to uid,
                                        "email" to email.take(120),
                                        "displayName" to displayName.take(100),
                                        "active" to true,
                                        "createdAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Non-fatal admin record sync notice: ${e.message}")
                        }
                    }
                    // Keep /users/{uid} role synced for Admin (permitted by Firestore Rules when isAdmin() is true)
                    try {
                        if (userSnap.getString("role") != "ADMIN") {
                            withTimeoutOrNull(3_000L) {
                                userDocRef.update(
                                    mapOf(
                                        "role" to "ADMIN",
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                ).await()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Non-fatal user profile admin sync notice: ${e.message}")
                    }

                    return@withTimeout Result.success(
                        SessionResolution.Authorized(
                            UserSession(
                                uid = uid,
                                email = email,
                                displayName = displayName,
                                photoUrl = photoUrl,
                                role = UserRole.ADMIN,
                                staffActive = true
                            )
                        )
                    )
                }

                // Strict Admin Portal Check: Never fall back silently to Public Dashboard if Admin login was requested
                if (intendedPortal == LoginPortal.ADMIN) {
                    return@withTimeout Result.success(
                        SessionResolution.UnauthorizedPortal(
                            messageHi = "इस खाते को Admin की अनुमति नहीं मिली है। कृपया गौशाला प्रशासक से संपर्क करें।",
                            messageEn = "This account is not authorized as Admin. Please contact the Goshala Administrator.",
                            uid = uid,
                            email = email
                        )
                    )
                }

                // 3. Check Staff account (/staffAccounts/{uid}); if absent, check Admin-issued invite for verified email
                val staffRef = db.collection("staffAccounts").document(uid)
                var staffSnap = staffRef.get().await()
                if (!staffSnap.exists() && user.isEmailVerified && email.isNotBlank()) {
                    val inviteSnap = try {
                        db.collection("staffInvites").document(email).get().await()
                    } catch (_: Exception) {
                        null
                    }
                    if (inviteSnap != null && inviteSnap.exists()) {
                        val invite = inviteSnap.toObject(StaffInvite::class.java)
                        if (invite != null) {
                            val claimPayload = mapOf(
                                "uid" to uid,
                                "email" to email,
                                "name" to invite.name.ifBlank { displayName },
                                "phone" to invite.phone,
                                "designation" to invite.designation.ifBlank { "गौ सेवक" },
                                "monthlySalary" to invite.monthlySalary,
                                "active" to true,
                                "permissions" to invite.permissions,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            staffRef.set(claimPayload).await()
                            staffSnap = staffRef.get().await()
                        }
                    }
                }

                if (staffSnap.exists()) {
                    val staff = staffSnap.toObject(StaffAccount::class.java)
                    if (staff != null && staff.active) {
                        return@withTimeout Result.success(
                            SessionResolution.Authorized(
                                UserSession(
                                    uid = uid,
                                    email = email,
                                    displayName = staff.name.ifBlank { displayName },
                                    photoUrl = photoUrl,
                                    role = UserRole.STAFF,
                                    permissions = staff.permissions,
                                    staffActive = true
                                )
                            )
                        )
                    }
                }

                if (intendedPortal == LoginPortal.STAFF) {
                    return@withTimeout Result.success(
                        SessionResolution.UnauthorizedPortal(
                            messageHi = "इस खाते को कर्मचारी (Staff) की अनुमति नहीं मिली है। कृपया गौशाला प्रशासक से संपर्क करें।",
                            messageEn = "This account is not authorized as Staff. Please contact the Goshala Administrator.",
                            uid = uid,
                            email = email
                        )
                    )
                }

                // 4. Default verified role: PUBLIC USER
                val profile = userSnap.toObject(UserProfile::class.java)
                Result.success(
                    SessionResolution.Authorized(
                        UserSession(
                            uid = uid,
                            email = email,
                            displayName = profile?.displayName?.ifBlank { displayName } ?: displayName,
                            photoUrl = photoUrl,
                            role = UserRole.PUBLIC
                        )
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Log.e(TAG, "Timed out resolving verified session (offline/slow network)", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Log.e(TAG, "Failed to resolve verified session", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }
}

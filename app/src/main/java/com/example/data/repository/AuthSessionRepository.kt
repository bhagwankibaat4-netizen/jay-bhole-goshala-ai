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
import com.example.ui.auth.AuthActionResult
import com.example.ui.auth.AuthValidator
import com.example.ui.auth.EmailAuthGateway
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

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

sealed interface LiveStaffUpdate {
    data class Updated(val session: UserSession) : LiveStaffUpdate
    data class Deactivated(
        val messageHi: String,
        val messageEn: String
    ) : LiveStaffUpdate
}

internal fun isStaffDocActive(
    activeField: Boolean?,
    approvedField: Boolean?,
    statusField: String?
): Boolean {
    if (activeField == false) return false
    if (approvedField == false) return false
    val normalizedStatus = statusField?.trim()?.uppercase()
    if (normalizedStatus != null && normalizedStatus in setOf(
            "INACTIVE", "DISABLED", "BLOCKED", "REVOKED", "REJECTED", "SUSPENDED"
        )
    ) {
        return false
    }
    return true
}

class AuthSessionRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : EmailAuthGateway {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val pendingNameByEmail = ConcurrentHashMap<String, String>()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signUpWithEmail(
        name: String,
        email: String,
        password: String
    ): AuthActionResult {
        val cleanName = name.trim().take(100)
        val cleanEmail = AuthValidator.normalizeEmail(email)
        if (cleanName.length < 2) {
            return AuthActionResult.Failure(
                messageHi = "कृपया अपना पूरा नाम दर्ज करें।",
                messageEn = "Please enter your full name."
            )
        }
        if (!AuthValidator.isValidEmailFormat(cleanEmail)) {
            return AuthActionResult.Failure(
                messageHi = "कृपया सही ईमेल / Gmail पता दर्ज करें।",
                messageEn = "Please enter a valid email / Gmail address.",
                errorCode = "ERROR_INVALID_EMAIL"
            )
        }
        if (password.length < AuthValidator.MIN_PASSWORD_LENGTH) {
            return AuthActionResult.Failure(
                messageHi = "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए।",
                messageEn = "Password must be at least 6 characters long.",
                errorCode = "ERROR_WEAK_PASSWORD"
            )
        }

        // Store pending name BEFORE Firebase createUser fires AuthStateListener so Firestore profile never loses the user's name
        pendingNameByEmail[cleanEmail] = cleanName
        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val createdUser = result.user
            if (createdUser != null) {
                try {
                    createdUser.updateProfile(
                        userProfileChangeRequest { displayName = cleanName }
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal Firebase profile displayName update notice: ${e.message}")
                }
            }
            AuthActionResult.Success(
                messageHi = "खाता सफलतापूर्वक बन गया है। डैशबोर्ड खुल रहा है...",
                messageEn = "Account created successfully. Opening dashboard..."
            )
        } catch (e: CancellationException) {
            pendingNameByEmail.remove(cleanEmail)
            throw e
        } catch (e: Exception) {
            pendingNameByEmail.remove(cleanEmail)
            Log.e(TAG, "Email/Password sign-up failed", e)
            AuthValidator.mapFirebaseAuthException(e)
        }
    }

    override suspend fun signInWithEmail(
        email: String,
        password: String
    ): AuthActionResult {
        val cleanEmail = AuthValidator.normalizeEmail(email)
        if (!AuthValidator.isValidEmailFormat(cleanEmail)) {
            return AuthActionResult.Failure(
                messageHi = "कृपया सही ईमेल / Gmail पता दर्ज करें।",
                messageEn = "Please enter a valid email / Gmail address.",
                errorCode = "ERROR_INVALID_EMAIL"
            )
        }
        if (password.isBlank()) {
            return AuthActionResult.Failure(
                messageHi = "कृपया पासवर्ड दर्ज करें।",
                messageEn = "Please enter your password."
            )
        }
        return try {
            auth.signInWithEmailAndPassword(cleanEmail, password).await()
            AuthActionResult.Success(
                messageHi = "लॉगिन सफल रहा। डैशबोर्ड खुल रहा है...",
                messageEn = "Login successful. Opening dashboard..."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Email/Password sign-in failed", e)
            AuthValidator.mapFirebaseAuthException(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): AuthActionResult {
        val cleanEmail = AuthValidator.normalizeEmail(email)
        if (!AuthValidator.isValidEmailFormat(cleanEmail)) {
            return AuthActionResult.Failure(
                messageHi = "कृपया सही ईमेल / Gmail पता दर्ज करें।",
                messageEn = "Please enter a valid email / Gmail address.",
                errorCode = "ERROR_INVALID_EMAIL"
            )
        }
        return try {
            auth.sendPasswordResetEmail(cleanEmail).await()
            AuthActionResult.Success(
                messageHi = "पासवर्ड रीसेट लिंक आपके Gmail ($cleanEmail) पर भेज दिया गया है। कृपया इनबॉक्स या Spam फ़ोल्डर जांचें।",
                messageEn = "Password reset email sent to $cleanEmail. Please check your inbox or Spam folder."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Password reset email failed", e)
            AuthValidator.mapFirebaseAuthException(e)
        }
    }

    private suspend fun getDocumentFresh(ref: DocumentReference): DocumentSnapshot {
        return try {
            ref.get(Source.SERVER).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                ref.get(Source.DEFAULT).await()
            } else {
                throw e
            }
        }
    }

    private fun extractExactPermissions(snap: DocumentSnapshot): Map<String, List<String>> {
        val raw = snap.get("permissions") as? Map<*, *> ?: return emptyMap()
        val result = linkedMapOf<String, List<String>>()
        for ((k, v) in raw) {
            val key = k as? String ?: continue
            val actions = (v as? List<*>)?.mapNotNull { it as? String } ?: continue
            result[key] = actions
        }
        return result
    }

    private suspend fun findStaffInviteFresh(
        user: FirebaseUser,
        normalizedEmail: String,
        rawEmail: String
    ): DocumentSnapshot? {
        suspend fun lookupOnce(): DocumentSnapshot? {
            val primarySnap = getDocumentFresh(db.collection("staffInvites").document(normalizedEmail))
            if (primarySnap.exists()) return primarySnap

            if (rawEmail.isNotBlank() && rawEmail != normalizedEmail) {
                val rawSnap = getDocumentFresh(db.collection("staffInvites").document(rawEmail))
                if (rawSnap.exists()) return rawSnap
            }

            // Fallback query by email field (handles invites created with non-email doc IDs)
            val querySnap = try {
                db.collection("staffInvites")
                    .whereEqualTo("email", normalizedEmail)
                    .limit(1)
                    .get(Source.SERVER)
                    .await()
            } catch (e: FirebaseFirestoreException) {
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                    db.collection("staffInvites")
                        .whereEqualTo("email", normalizedEmail)
                        .limit(1)
                        .get(Source.DEFAULT)
                        .await()
                } else {
                    Log.w(TAG, "Staff invite fallback query skipped: ${e.code} ${e.message}")
                    null
                }
            }
            return querySnap?.documents?.firstOrNull()
        }

        return try {
            lookupOnce()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                // Newly signed-in/created accounts can have a brief ID token email-claim propagation delay; refresh once and retry
                Log.w(TAG, "Retrying staffInvites lookup after ID token refresh: ${e.message}")
                try {
                    user.getIdToken(true).await()
                    lookupOnce()
                } catch (retryErr: Exception) {
                    Log.e(TAG, "staffInvites lookup still denied after token refresh", retryErr)
                    throw retryErr
                }
            } else {
                Log.w(TAG, "Non-fatal staffInvites lookup error: ${e.code} ${e.message}")
                null
            }
        }
    }

    suspend fun resolveVerifiedSession(
        user: FirebaseUser,
        intendedPortal: LoginPortal? = null
    ): Result<SessionResolution> {
        val uid = user.uid
        val rawEmail = (
            user.email?.takeIf { it.isNotBlank() }
                ?: user.providerData.firstOrNull { !it.email.isNullOrBlank() }?.email
            )?.trim().orEmpty()
        val email = rawEmail.lowercase()
        val pendingName = if (email.isNotBlank()) pendingNameByEmail.remove(email) else null
        val photoUrl = user.photoUrl?.toString()?.take(500)

        return try {
            withTimeout(15_000L) {
                // 1. Ensure /users/{uid} exists (always PUBLIC on self-create; rules forbid client role escalation)
                val userDocRef = db.collection("users").document(uid)
                val userSnap = userDocRef.get().await()
                val existingProfileName = if (userSnap.exists()) {
                    userSnap.getString("displayName")?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                val displayName = pendingName
                    ?: user.displayName?.takeIf { it.isNotBlank() }
                    ?: existingProfileName
                    ?: email.substringBefore('@').ifBlank { "भक्त / User" }

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
                val adminSnap = getDocumentFresh(adminRef)
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

                // 3. Check Staff account (/staffAccounts/{uid}); if absent, check Admin-issued invite for matching email
                // Works for BOTH Google Sign-In and Email/Password staff accounts
                val staffRef = db.collection("staffAccounts").document(uid)
                var staffSnap = getDocumentFresh(staffRef)
                if (!staffSnap.exists() && email.isNotBlank()) {
                    val inviteSnap = findStaffInviteFresh(user, email, rawEmail)
                    if (inviteSnap != null && inviteSnap.exists()) {
                        val inviteActive = isStaffDocActive(
                            activeField = inviteSnap.getBoolean("active"),
                            approvedField = inviteSnap.getBoolean("approved"),
                            statusField = inviteSnap.getString("status")
                        )
                        if (!inviteActive) {
                            return@withTimeout Result.success(
                                SessionResolution.UnauthorizedPortal(
                                    messageHi = "आपका कर्मचारी खाता प्रशासक द्वारा निष्क्रिय (Inactive) रखा गया है। कृपया गौशाला प्रशासक से संपर्क करें।",
                                    messageEn = "Your employee account is marked inactive by the Administrator. Please contact the Goshala Admin.",
                                    uid = uid,
                                    email = email
                                )
                            )
                        }
                        val invite = inviteSnap.toObject(StaffInvite::class.java)
                        val exactEmployeeId = (inviteSnap.getString("employeeId") ?: "").take(40)
                        val exactPermissions = extractExactPermissions(inviteSnap)
                        val inviteName = invite?.name?.takeIf { it.isNotBlank() } ?: displayName
                        val invitePhone = invite?.phone.orEmpty().take(20)
                        val inviteDesignation = invite?.designation?.takeIf { it.isNotBlank() } ?: "गौ सेवक"
                        val inviteSalary = (invite?.monthlySalary ?: 0.0).coerceIn(0.0, 1_000_000.0)
                        val claimPayload = mapOf(
                            "uid" to uid,
                            "employeeId" to exactEmployeeId,
                            "email" to email,
                            "name" to inviteName.take(100),
                            "phone" to invitePhone,
                            "designation" to inviteDesignation.take(80),
                            "monthlySalary" to inviteSalary,
                            "active" to true,
                            "permissions" to exactPermissions,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                        try {
                            staffRef.set(claimPayload).await()
                        } catch (e: FirebaseFirestoreException) {
                            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                Log.w(TAG, "Retrying staffAccounts claim after ID token refresh: ${e.message}")
                                user.getIdToken(true).await()
                                staffRef.set(claimPayload).await()
                            } else {
                                throw e
                            }
                        }
                        staffSnap = getDocumentFresh(staffRef)
                    }
                }

                if (staffSnap.exists()) {
                    val staff = staffSnap.toObject(StaffAccount::class.java)
                    val isActiveRecord = isStaffDocActive(
                        activeField = staffSnap.getBoolean("active"),
                        approvedField = staffSnap.getBoolean("approved"),
                        statusField = staffSnap.getString("status")
                    )
                    if (!isActiveRecord) {
                        return@withTimeout Result.success(
                            SessionResolution.UnauthorizedPortal(
                                messageHi = "आपका कर्मचारी खाता प्रशासक द्वारा निष्क्रिय (Inactive) कर दिया गया है। कृपया गौशाला प्रशासक से संपर्क करें।",
                                messageEn = "Your employee account has been deactivated by the Administrator. Please contact the Goshala Admin.",
                                uid = uid,
                                email = email
                            )
                        )
                    }
                    val resolvedPermissions = extractExactPermissions(staffSnap).ifEmpty {
                        staff?.permissions.orEmpty()
                    }
                    return@withTimeout Result.success(
                        SessionResolution.Authorized(
                            UserSession(
                                uid = uid,
                                email = email,
                                displayName = staff?.name?.takeIf { it.isNotBlank() } ?: displayName,
                                photoUrl = photoUrl,
                                role = UserRole.STAFF,
                                permissions = resolvedPermissions,
                                staffActive = true,
                                employeeId = staff?.employeeId.orEmpty(),
                                designation = staff?.designation.orEmpty()
                            )
                        )
                    )
                }

                if (intendedPortal == LoginPortal.STAFF) {
                    // Diagnostic check: detect if a staffAccounts record exists for this email under a different UID
                    val mismatchedDoc = if (email.isNotBlank()) {
                        try {
                            db.collection("staffAccounts")
                                .whereEqualTo("email", email)
                                .limit(1)
                                .get(Source.SERVER)
                                .await()
                                .documents
                                .firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                    if (mismatchedDoc != null && mismatchedDoc.id != uid) {
                        val existingActive = isStaffDocActive(
                            activeField = mismatchedDoc.getBoolean("active"),
                            approvedField = mismatchedDoc.getBoolean("approved"),
                            statusField = mismatchedDoc.getString("status")
                        )
                        if (!existingActive) {
                            return@withTimeout Result.success(
                                SessionResolution.UnauthorizedPortal(
                                    messageHi = "आपका कर्मचारी खाता प्रशासक द्वारा निष्क्रिय (Inactive) कर दिया गया है। कृपया गौशाला प्रशासक से संपर्क करें।",
                                    messageEn = "Your employee account has been deactivated by the Administrator. Please contact the Goshala Admin.",
                                    uid = uid,
                                    email = email
                                )
                            )
                        }
                        return@withTimeout Result.success(
                            SessionResolution.UnauthorizedPortal(
                                messageHi = "कर्मचारी रिकॉर्ड की UID इस लॉगिन खाते से मेल नहीं खा रही है। कृपया प्रशासक से Staff Management में इस ईमेल को पुनः सेव करने को कहें।",
                                messageEn = "Staff record UID does not match this login account. Please ask the Admin to re-save this employee email in Staff Management.",
                                uid = uid,
                                email = email
                            )
                        )
                    }

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

    fun observeLiveStaffSession(uid: String, baseSession: UserSession): Flow<LiveStaffUpdate> =
        db.collection("staffAccounts").document(uid)
            .snapshots()
            .map { snap ->
                val staff = snap.toObject(StaffAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                val isActiveRecord = snap.exists() && isStaffDocActive(
                    activeField = snap.getBoolean("active"),
                    approvedField = snap.getBoolean("approved"),
                    statusField = snap.getString("status")
                )
                if (!isActiveRecord || staff == null) {
                    LiveStaffUpdate.Deactivated(
                        messageHi = "आपका कर्मचारी खाता प्रशासक द्वारा निष्क्रिय या हटा दिया गया है। पुनः प्रवेश हेतु प्रशासक से संपर्क करें।",
                        messageEn = "Your employee account was deactivated or removed by the Administrator. Please contact Admin."
                    )
                } else {
                    val livePermissions = extractExactPermissions(snap).ifEmpty { staff.permissions }
                    LiveStaffUpdate.Updated(
                        baseSession.copy(
                            displayName = staff.name.ifBlank { baseSession.displayName },
                            permissions = livePermissions,
                            staffActive = true,
                            employeeId = staff.employeeId,
                            designation = staff.designation
                        )
                    )
                }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                Log.w(TAG, "Live staff session stream notice", fsErr ?: error)
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

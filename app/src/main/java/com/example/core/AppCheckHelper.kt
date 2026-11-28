package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and AI Studio runtime harness.
internal const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
internal const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
internal const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"

// Firebase App Check returns a base64-encoded JSON {"error":...} dummy token when attestation fails.
private const val DUMMY_ERROR_TOKEN_PREFIX = "eyJlcnJvciI6"

// Rate-limit guards to prevent Firebase App Check "Too many attempts." lockouts.
internal const val MIN_FORCE_REFRESH_INTERVAL_MS = 15_000L
internal const val RATE_LIMIT_COOLDOWN_MS = 60_000L

internal enum class AppCheckProviderKind {
    NONE,
    DEBUG,
    PLAY_INTEGRITY
}

@Volatile
private var appCheckAppContext: Context? = null

@Volatile
internal var activeAppCheckProviderKind: AppCheckProviderKind = AppCheckProviderKind.NONE
    private set

@Volatile
internal var lastAppCheckDiagnostic: String? = null
    private set

@Volatile
private var lastForceRefreshAttemptMs: Long = 0L

@Volatile
private var rateLimitBlockedUntilMs: Long = 0L

private val tokenRefreshMutex = Mutex()

/**
 * Resets internal throttle timers (used strictly by deterministic unit tests).
 */
internal fun resetAppCheckThrottleForTest() {
    lastForceRefreshAttemptMs = 0L
    rateLimitBlockedUntilMs = 0L
    lastAppCheckDiagnostic = null
}

/**
 * Returns a concise, user-safe diagnostic note describing the last App Check verification failure
 * (e.g., provider type and root cause category) so the UI can show actionable guidance with Retry.
 */
fun describeAppCheckDiagnostic(language: AppLanguage): String? {
    val diag = lastAppCheckDiagnostic?.trim().takeIf { !it.isNullOrBlank() } ?: return null
    val lower = diag.lowercase()
    return when {
        lower.contains("too many attempts") || lower.contains("rate_limited") -> if (language == AppLanguage.HINDI) {
            "कारण: App Check टोकन के बार-बार प्रयास के कारण अस्थायी सीमा (Too many attempts) लागू है। कृपया 1–2 मिनट प्रतीक्षा कर पुनः प्रयास करें।"
        } else {
            "Cause: Firebase App Check rate limit ('Too many attempts'). Please wait 1-2 minutes before retrying."
        }
        lower.contains("debug_unregistered") || (lower.contains("debug") && (lower.contains("403") || lower.contains("401"))) -> if (language == AppLanguage.HINDI) {
            "कारण: इस Debug डिवाइस का Debug Token Firebase Console → App Check → Manage debug tokens में रजिस्टर नहीं है।"
        } else {
            "Cause: This device's Debug App Check token is not registered in Firebase Console → App Check → Manage debug tokens."
        }
        lower.contains("play_integrity") || lower.contains("integrity") -> if (language == AppLanguage.HINDI) {
            "कारण: Play Integrity सत्यापन पूरा नहीं हो सका। Google Play Services, इंटरनेट और Firebase Console में Release SHA-256 जाँचें।"
        } else {
            "Cause: Play Integrity attestation did not complete. Check Google Play Services, internet, and Release SHA-256 in Firebase Console."
        }
        else -> if (language == AppLanguage.HINDI) {
            "विवरण: $diag"
        } else {
            "Details: $diag"
        }
    }
}

/**
 * Detects Android emulator / virtual device runtimes (including AVD, Genymotion, and Google Cloud
 * Cuttlefish / AI Studio Streaming Emulators) for diagnostics.
 */
internal fun isAndroidEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
    val model = Build.MODEL.orEmpty().lowercase()
    val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
    val brand = Build.BRAND.orEmpty().lowercase()
    val device = Build.DEVICE.orEmpty().lowercase()
    val product = Build.PRODUCT.orEmpty().lowercase()
    val hardware = Build.HARDWARE.orEmpty().lowercase()
    return isEmulatorBuildProperties(
        fingerprint = fingerprint,
        model = model,
        manufacturer = manufacturer,
        brand = brand,
        device = device,
        product = product,
        hardware = hardware
    )
}

internal fun isEmulatorBuildProperties(
    fingerprint: String,
    model: String,
    manufacturer: String,
    brand: String,
    device: String,
    product: String,
    hardware: String
): Boolean {
    val fp = fingerprint.lowercase()
    val mdl = model.lowercase()
    val mfg = manufacturer.lowercase()
    val br = brand.lowercase()
    val dev = device.lowercase()
    val prd = product.lowercase()
    val hw = hardware.lowercase()
    return fp.startsWith("generic") ||
        fp.startsWith("unknown") ||
        fp.contains("emulator") ||
        fp.contains("sdk_gphone") ||
        fp.contains("cuttlefish") ||
        fp.contains("aosp_cf") ||
        fp.contains("cf_x86") ||
        fp.contains("cf_arm") ||
        fp.contains("vsoc") ||
        mdl.contains("google_sdk") ||
        mdl.contains("emulator") ||
        mdl.contains("android sdk built for x86") ||
        mdl.contains("sdk_gphone") ||
        mdl.contains("cuttlefish") ||
        mdl.contains("aosp on") ||
        mfg.contains("genymotion") ||
        hw.contains("goldfish") ||
        hw.contains("ranchu") ||
        hw.contains("cutf") ||
        hw.contains("virtio") ||
        hw.contains("crosvm") ||
        hw.contains("qemu") ||
        dev.contains("vsoc") ||
        dev.contains("emu64") ||
        prd.contains("sdk_gphone") ||
        prd.contains("emulator") ||
        prd.contains("simulator") ||
        prd.contains("cuttlefish") ||
        prd.contains("aosp_cf") ||
        prd.contains("cf_x86") ||
        prd.contains("cf_arm") ||
        prd.contains("vsoc") ||
        prd.contains("vbox") ||
        (br.startsWith("generic") && dev.startsWith("generic"))
}

/**
 * Persists an explicitly supplied Firebase App Check debug secret synchronously using commit()
 * BEFORE FirebaseAppCheck / DebugAppCheckProviderFactory reads SharedPreferences.
 */
@SuppressLint("ApplySharedPref")
internal fun persistDebugAppCheckToken(
    context: Context,
    persistenceKey: String,
    debugToken: String
): Boolean {
    val cleanToken = debugToken.trim()
    val debugPrefs = context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    // CRITICAL: MUST use commit() instead of apply() so the secret is written synchronously
    // before DebugAppCheckProviderFactory reads SharedPreferences.
    return debugPrefs.edit()
        .putString(DEBUG_SECRET_KEY, cleanToken)
        .commit()
}

/**
 * Clears a stored debug secret from SharedPreferences (used only in tests/cleanup).
 */
@SuppressLint("ApplySharedPref")
internal fun clearStoredDebugAppCheckToken(context: Context, persistenceKey: String) {
    context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
        .edit()
        .remove(DEBUG_SECRET_KEY)
        .commit()
}

/**
 * Strict, unambiguous App Check provider selection for Android:
 * - Debug builds (`BuildConfig.DEBUG == true`): ALWAYS `DEBUG` (`DebugAppCheckProviderFactory`),
 *   both on emulators and on physical developer phones (debug-signed APKs cannot pass Play Integrity
 *   and must never be misrouted to Web/reCAPTCHA Enterprise).
 * - Release/Production builds (`BuildConfig.DEBUG == false`): ALWAYS `PLAY_INTEGRITY`
 *   (`PlayIntegrityAppCheckProviderFactory`), matching the Firebase Console Play Integrity setup.
 */
internal fun decideAppCheckProviderKind(isDebugBuild: Boolean): AppCheckProviderKind =
    if (isDebugBuild) AppCheckProviderKind.DEBUG else AppCheckProviderKind.PLAY_INTEGRITY

/**
 * Configures Firebase App Check synchronously in MainActivity.onCreate() before setContent,
 * ViewModels, or any Firebase operations. Safe to call again from onNewIntent().
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        appCheckAppContext = appContext

        // 1. Ensure FirebaseApp is initialized before touching App Check
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        val firebaseApp = FirebaseApp.getInstance()
        val persistenceKey = firebaseApp.persistenceKey
        val intentDebugToken = if (BuildConfig.DEBUG) {
            intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val targetKind = decideAppCheckProviderKind(isDebugBuild = BuildConfig.DEBUG)

        // 2. Debug build: persist Intent debug secret synchronously BEFORE calling FirebaseAppCheck.getInstance()
        if (targetKind == AppCheckProviderKind.DEBUG && !intentDebugToken.isNullOrEmpty()) {
            val persisted = persistDebugAppCheckToken(appContext, persistenceKey, intentDebugToken)
            Log.d(TAG, "App Check debug secret committed synchronously from Intent: $persisted")
        }

        // 3. Install build-type provider factory once (or reinstall only if a new Intent debug token arrived)
        val shouldInstall = activeAppCheckProviderKind != targetKind || !intentDebugToken.isNullOrEmpty()
        if (shouldInstall) {
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
            val installedKind = installPlatformAppCheckProvider(appCheck)
            activeAppCheckProviderKind = installedKind
            lastAppCheckDiagnostic = null
            Log.d(
                TAG,
                "Firebase App Check installed: provider=$installedKind, debug=${BuildConfig.DEBUG}, emulator=${isAndroidEmulator()}"
            )
        }
    } catch (e: Exception) {
        lastAppCheckDiagnostic = e.message?.take(120) ?: "init_failed"
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Guarantees App Check has been initialized before any Firebase AI Logic / Gemini call.
 */
fun ensureAppCheckInitialized(context: Context? = null) {
    if (activeAppCheckProviderKind != AppCheckProviderKind.NONE) return
    val ctx = context?.applicationContext
        ?: appCheckAppContext
        ?: runCatching { FirebaseApp.getInstance().applicationContext }.getOrNull()
    if (ctx != null) {
        configureAppCheck(ctx, null)
    }
}

/**
 * Checks whether an obtained App Check token string is a real non-empty JWT and NOT the
 * Firebase SDK dummy error placeholder token.
 */
internal fun isValidAppCheckJwt(token: String?): Boolean {
    if (token.isNullOrBlank()) return false
    if (token.startsWith(DUMMY_ERROR_TOKEN_PREFIX)) return false
    return token.count { it == '.' } >= 2
}

internal fun isRateLimitMessage(message: String?): Boolean {
    val lower = message.orEmpty().lowercase()
    return lower.contains("too many attempts") ||
        lower.contains("rate limit") ||
        lower.contains("quota exceeded") ||
        lower.contains("429")
}

internal fun canAttemptForceRefreshNow(
    nowMs: Long,
    lastForceAttemptMs: Long,
    blockedUntilMs: Long
): Boolean {
    if (nowMs < blockedUntilMs) return false
    if (lastForceAttemptMs > 0L && (nowMs - lastForceAttemptMs) < MIN_FORCE_REFRESH_INTERVAL_MS) return false
    return true
}

/**
 * Fetches a valid Firebase App Check JWT safely using the cached token first and applying
 * strict cooldown/backoff on forced refreshes so we never trigger "Too many attempts.".
 * Never logs the raw token string.
 */
suspend fun fetchValidAppCheckToken(forceRefreshOnInvalid: Boolean = false): Result<String> =
    tokenRefreshMutex.withLock {
        runCatching {
            ensureAppCheckInitialized()
            val firebaseApp = FirebaseApp.getInstance()
            val appCheck = FirebaseAppCheck.getInstance(firebaseApp)

            // 1. Prefer SDK's cached valid token (auto-refreshed in background) to avoid extra attestation hits
            val cachedResult = runCatching { appCheck.getAppCheckToken(false).await() }
            val cachedToken = cachedResult.getOrNull()?.token?.trim()
            if (isValidAppCheckJwt(cachedToken)) {
                lastAppCheckDiagnostic = null
                return@runCatching cachedToken!!
            }

            val cachedErr = cachedResult.exceptionOrNull()?.message
            if (isRateLimitMessage(cachedErr)) {
                rateLimitBlockedUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
                lastAppCheckDiagnostic = "${activeAppCheckProviderKind.name.lowercase()}: Too many attempts."
                throw IllegalStateException("Firebase App Check rate-limited: Too many attempts.")
            }

            if (!forceRefreshOnInvalid) {
                recordTokenFailure(cachedResult.exceptionOrNull())
                throw cachedResult.exceptionOrNull()
                    ?: IllegalStateException("App Check returned an invalid or dummy token.")
            }

            // 2. Respect forced-refresh cooldown so retries never cause "Too many attempts."
            val nowMs = System.currentTimeMillis()
            if (!canAttemptForceRefreshNow(nowMs, lastForceRefreshAttemptMs, rateLimitBlockedUntilMs)) {
                if (nowMs < rateLimitBlockedUntilMs) {
                    lastAppCheckDiagnostic = "${activeAppCheckProviderKind.name.lowercase()}: Too many attempts."
                    throw IllegalStateException("Firebase App Check cooldown active (Too many attempts).")
                }
                throw cachedResult.exceptionOrNull()
                    ?: IllegalStateException("App Check refresh throttled; please retry shortly.")
            }

            lastForceRefreshAttemptMs = nowMs
            val forcedResult = runCatching { appCheck.getAppCheckToken(true).await() }
            val forcedToken = forcedResult.getOrNull()?.token?.trim()
            if (isValidAppCheckJwt(forcedToken)) {
                lastAppCheckDiagnostic = null
                return@runCatching forcedToken!!
            }

            val forcedErr = forcedResult.exceptionOrNull()
            if (isRateLimitMessage(forcedErr?.message)) {
                rateLimitBlockedUntilMs = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            }
            recordTokenFailure(forcedErr)
            throw forcedErr ?: IllegalStateException("App Check token refresh returned an invalid token.")
        }
    }

private fun recordTokenFailure(error: Throwable?) {
    val errMsg = error?.message?.take(100)
    lastAppCheckDiagnostic = when (activeAppCheckProviderKind) {
        AppCheckProviderKind.DEBUG -> "debug_unregistered: ${errMsg ?: "invalid_token"}"
        AppCheckProviderKind.PLAY_INTEGRITY -> "play_integrity: ${errMsg ?: "invalid_token"}"
        AppCheckProviderKind.NONE -> "not_initialized: ${errMsg ?: "provider_missing"}"
    }
    Log.w(
        TAG,
        "App Check token unavailable (provider=$activeAppCheckProviderKind, emulator=${isAndroidEmulator()}, diag=$lastAppCheckDiagnostic)."
    )
}

/**
 * Refreshes the active Firebase App Check token safely with mutex + cooldown protection.
 * Returns true if a real, valid App Check JWT is now available.
 */
suspend fun refreshAppCheckToken(forceRefresh: Boolean = true): Boolean {
    return fetchValidAppCheckToken(forceRefreshOnInvalid = forceRefresh).isSuccess
}

/**
 * Returns a safe, secret-free diagnostic summary of the current App Check configuration
 * (provider kind and build mode) for error logs. Never exposes any token or secret.
 */
fun describeAppCheckProviderForLog(): String {
    val provider = activeAppCheckProviderKind.name
    val mode = if (BuildConfig.DEBUG) "DEBUG" else "RELEASE"
    val emulator = isAndroidEmulator()
    val diag = lastAppCheckDiagnostic?.take(80)
    return if (diag.isNullOrBlank()) {
        "AppCheck[mode=$mode,provider=$provider,emulator=$emulator]"
    } else {
        "AppCheck[mode=$mode,provider=$provider,emulator=$emulator,note=$diag]"
    }
}

/**
 * Identifies App Check / HTTP 401 / HTTP 403 attestation token errors so AI calls can attempt a
 * single throttled refresh, while strictly excluding API-disabled / billing / invalid-key errors.
 */
fun isAppCheckTokenError(throwable: Throwable?): Boolean {
    val chainText = buildString {
        var current = throwable
        while (current != null) {
            append(current.javaClass.simpleName).append(' ')
            append(current.message.orEmpty()).append(' ')
            current = current.cause
        }
    }.lowercase()

    if (chainText.isBlank()) return false

    // Never misclassify API-disabled, billing, or invalid-API-key failures as App Check token errors
    val isExplicitApiOrBillingError =
        chainText.contains("service_disabled") ||
            chainText.contains("api has not been used") ||
            chainText.contains("not enabled") ||
            chainText.contains("api_key_invalid") ||
            chainText.contains("billing")
    if (isExplicitApiOrBillingError && !chainText.contains("app check") && !chainText.contains("appcheck")) {
        return false
    }

    return chainText.contains("app check") ||
        chainText.contains("appcheck") ||
        chainText.contains("attestation") ||
        chainText.contains("too many attempts") ||
        chainText.contains("firebase app check token is invalid") ||
        chainText.contains("unauthenticated") ||
        chainText.contains("401") ||
        chainText.contains("403")
}

package com.example.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

// App Check contract keys defined by the Firebase SDK and AI Studio runtime harness.
internal const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
internal const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
internal const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

private val providerInstalled = AtomicBoolean(false)

/**
 * Persists the Firebase App Check debug secret synchronously using commit() so that
 * DebugAppCheckProviderFactory reads the registered token immediately without race conditions.
 */
@SuppressLint("ApplySharedPref")
internal fun persistDebugAppCheckToken(
    context: Context,
    persistenceKey: String,
    debugToken: String
): Boolean {
    val prefs = context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    // CRITICAL: MUST use commit() instead of apply() so the secret is written synchronously
    // before DebugAppCheckProviderFactory reads SharedPreferences.
    return prefs.edit()
        .putString(DEBUG_SECRET_KEY, debugToken.trim())
        .commit()
}

/**
 * Configures Firebase App Check synchronously in MainActivity.onCreate() before setContent,
 * ViewModels, or any Firebase operations. Also safe to call from onNewIntent().
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        val appContext = context.applicationContext ?: context
        // 1. Ensure FirebaseApp is initialized
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        val firebaseApp = FirebaseApp.getInstance()
        val appCheck = FirebaseAppCheck.getInstance(firebaseApp)

        if (BuildConfig.DEBUG) {
            // 2. Debug: Read debug token from launch Intent extra and commit synchronously
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)?.trim()
            val tokenUpdated = if (!debugToken.isNullOrEmpty()) {
                val persisted = persistDebugAppCheckToken(
                    context = appContext,
                    persistenceKey = firebaseApp.persistenceKey,
                    debugToken = debugToken
                )
                Log.d(TAG, "App Check debug secret persisted synchronously: $persisted")
                persisted
            } else {
                Log.w(TAG, "No FIREBASE_APPCHECK_DEBUG_TOKEN found in Intent extra; reusing stored secret if available.")
                false
            }

            if (providerInstalled.compareAndSet(false, true) || tokenUpdated) {
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                appCheck.setTokenAutoRefreshEnabled(true)
                Log.d(TAG, "DebugAppCheckProviderFactory installed with auto-refresh enabled.")
            }
        } else {
            // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
            if (providerInstalled.compareAndSet(false, true)) {
                val resId = appContext.resources.getIdentifier(
                    RECAPTCHA_SITE_KEY_RES,
                    "string",
                    appContext.packageName
                )
                if (resId != 0) {
                    val siteKey = appContext.getString(resId)
                    if (siteKey.isNotBlank()) {
                        appCheck.installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        appCheck.setTokenAutoRefreshEnabled(true)
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed with auto-refresh enabled.")
                    } else {
                        providerInstalled.set(false)
                        Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    }
                } else {
                    providerInstalled.set(false)
                    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                }
            }
        }
    } catch (e: Exception) {
        // Log explicitly to keep App Check attestation failures visible
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

/**
 * Validates and optionally force-refreshes the Firebase App Check token before or after
 * an attestation error. Returns true if a non-empty valid token was obtained.
 */
suspend fun refreshAppCheckToken(forceRefresh: Boolean = false): Boolean {
    return try {
        val tokenResult = FirebaseAppCheck.getInstance().getAppCheckToken(forceRefresh).await()
        val valid = tokenResult.token.isNotBlank()
        Log.d(TAG, "App Check token fetch (forceRefresh=$forceRefresh) valid=$valid")
        valid
    } catch (e: Exception) {
        Log.e(TAG, "App Check token refresh failed (forceRefresh=$forceRefresh)", e)
        false
    }
}

/**
 * Identifies App Check / HTTP 403 attestation token errors so AI calls can retry once after
 * forcing a token refresh and display an actionable diagnostic message if still rejected.
 */
fun isAppCheckTokenError(throwable: Throwable?): Boolean {
    var current = throwable
    while (current != null) {
        val msg = current.message.orEmpty().lowercase()
        if (
            msg.contains("app check") ||
            msg.contains("appcheck") ||
            msg.contains("attestation") ||
            msg.contains("firebase app check token is invalid") ||
            msg.contains("403")
        ) {
            return true
        }
        current = current.cause
    }
    return false
}

package com.example.data.repository

import android.util.Log
import com.example.core.AppLanguage
import com.example.core.describeAppCheckDiagnostic
import com.example.core.describeAppCheckProviderForLog
import com.example.core.isValidAppCheckJwt
import com.example.core.refreshAppCheckToken
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Structured, strictly factual diagnostic result for the Admin "Test AI Connection" button.
 *
 * Tests BOTH:
 * 1. Live Firebase AI Logic (`Firebase.ai` + App Check) — the actual zero-key backend powering
 *    the Android AI Goshala Assistant right now.
 * 2. The optional server-side `testGeminiConnection` Cloud Function (when deployed) with Admin
 *    Firebase ID Token + App Check Token.
 *
 * Never fabricates success results and never exposes tokens or API keys.
 */
data class AdminAiDiagnosticResult(
    val testPassed: Boolean,
    val geminiWorking: Boolean,
    val httpStatus: Int,
    val errorCodeCategory: String,
    val rootCause: String,
    val verdict: String,
    val errorDetails: String,
    val appCheckValid: Boolean,
    val appCheckStatus: String,
    val model: String,
    val latencyMs: Long,
    val replyPreview: String,
    val responseSnippet: String,
    val timestamp: String,
    val backendChannel: String = "FIREBASE_AI_LOGIC",
    val cloudFunctionHttpStatus: Int? = null,
    val cloudFunctionSummary: String = ""
)

data class DiagnosticHttpResponse(
    val statusCode: Int,
    val body: String
)

data class FirebaseAiLogicProbeOutcome(
    val ok: Boolean,
    val httpStatus: Int,
    val model: String,
    val replyText: String,
    val errorMessage: String = ""
)

/**
 * Admin-only diagnostic client verifying:
 * 1. Authenticated Admin role (`UserRole.ADMIN`) + live Firebase Auth ID Token
 * 2. Live Firebase App Check JWT (`X-Firebase-AppCheck`)
 * 3. Live Gemini API response via Firebase AI Logic (`Firebase.ai`, zero client API key)
 * 4. Independent probe of `testGeminiConnection` Cloud Function (reporting exact HTTP status,
 *    e.g., 200 when deployed or 404 when not yet deployed — never faked).
 */
class AdminAiDiagnosticRepository(
    private val endpointUrl: String = DEFAULT_ENDPOINT_URL,
    private val idTokenProvider: suspend () -> Result<String> = { fetchLiveFirebaseIdToken() },
    private val appCheckTokenProvider: suspend () -> Result<String> = { fetchLiveAppCheckToken() },
    private val firebaseAiLogicProbe: suspend (prompt: String) -> FirebaseAiLogicProbeOutcome = { prompt ->
        executeLiveFirebaseAiLogicProbe(prompt)
    },
    private val httpPostExecutor: suspend (
        url: String,
        headers: Map<String, String>,
        jsonBody: String
    ) -> DiagnosticHttpResponse = { url, headers, body -> executeHttpsPost(url, headers, body) }
) {

    suspend fun testGeminiConnection(
        session: UserSession,
        language: AppLanguage,
        prompt: String = DEFAULT_PROMPT
    ): AdminAiDiagnosticResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()

        // 1. Strict Admin role check (client + server both enforce Admin-only access)
        if (session.role != UserRole.ADMIN) {
            return@withContext AdminAiDiagnosticResult(
                testPassed = false,
                geminiWorking = false,
                httpStatus = 403,
                errorCodeCategory = "HTTP_403",
                rootCause = "ADMIN_ONLY_FORBIDDEN",
                verdict = if (language == AppLanguage.HINDI) {
                    "असफल: यह जाँच केवल अधिकृत Admin के लिए है।"
                } else {
                    "FAIL: Only authorized Admin users can run this diagnostic test."
                },
                errorDetails = if (language == AppLanguage.HINDI) {
                    "HTTP 403: वर्तमान उपयोगकर्ता भूमिका (${session.role.name}) को AI सर्वर कनेक्शन टेस्ट की अनुमति नहीं है।"
                } else {
                    "HTTP 403: Current role (${session.role.name}) is not authorized to test AI backend connection."
                },
                appCheckValid = false,
                appCheckStatus = "SKIPPED",
                model = "",
                latencyMs = System.currentTimeMillis() - startedAt,
                replyPreview = "",
                responseSnippet = "",
                timestamp = ""
            )
        }

        // 2. Obtain fresh Firebase Auth ID Token for signed-in Admin
        val idTokenResult = runCatching { idTokenProvider() }.getOrElse { Result.failure(it) }
        val idToken = idTokenResult.getOrNull()?.trim().orEmpty()
        if (idToken.isBlank()) {
            val safeReason = redactSensitiveText(idTokenResult.exceptionOrNull()?.message.orEmpty())
            Log.w(TAG, "Admin ID token unavailable for testGeminiConnection (reason=$safeReason)")
            return@withContext AdminAiDiagnosticResult(
                testPassed = false,
                geminiWorking = false,
                httpStatus = 401,
                errorCodeCategory = "HTTP_401",
                rootCause = "FIREBASE_ID_TOKEN_UNAVAILABLE",
                verdict = if (language == AppLanguage.HINDI) {
                    "असफल: Admin का Firebase ID Token प्राप्त नहीं हो सका।"
                } else {
                    "FAIL: Unable to retrieve Admin Firebase ID Token."
                },
                errorDetails = if (language == AppLanguage.HINDI) {
                    "HTTP 401: कृपया Admin Google खाते से पुनः लॉगिन करें। ${if (safeReason.isNotBlank()) "विवरण: $safeReason" else ""}".trim()
                } else {
                    "HTTP 401: Please sign in again with the Admin Google account. ${if (safeReason.isNotBlank()) "Details: $safeReason" else ""}".trim()
                },
                appCheckValid = false,
                appCheckStatus = "NOT_CHECKED",
                model = "",
                latencyMs = System.currentTimeMillis() - startedAt,
                replyPreview = "",
                responseSnippet = "",
                timestamp = ""
            )
        }

        // 3. Obtain valid Firebase App Check Token (never bypassed)
        val appCheckResult = runCatching { appCheckTokenProvider() }.getOrElse { Result.failure(it) }
        val appCheckToken = appCheckResult.getOrNull()?.trim().orEmpty()
        if (appCheckToken.isBlank() || !isValidAppCheckJwt(appCheckToken)) {
            val safeReason = redactSensitiveText(appCheckResult.exceptionOrNull()?.message.orEmpty())
            val appCheckDiag = describeAppCheckDiagnostic(language).orEmpty()
            val providerSummary = describeAppCheckProviderForLog()
            Log.w(TAG, "App Check token unavailable before diagnostic test ($providerSummary)")
            return@withContext AdminAiDiagnosticResult(
                testPassed = false,
                geminiWorking = false,
                httpStatus = 401,
                errorCodeCategory = "HTTP_401",
                rootCause = "APP_CHECK_TOKEN_UNAVAILABLE",
                verdict = if (language == AppLanguage.HINDI) {
                    "असफल: वैध Firebase App Check Token प्राप्त नहीं हुआ।"
                } else {
                    "FAIL: Could not obtain a valid Firebase App Check token."
                },
                errorDetails = buildString {
                    if (language == AppLanguage.HINDI) {
                        append("HTTP 401 (App Check त्रुटि): डिवाइस से वैध App Check टोकन नहीं बना। ")
                        append("Debug बिल्ड में रजिस्टर्ड Debug Token और Release में Google Play Services / reCAPTCHA जाँचें।")
                    } else {
                        append("HTTP 401 (App Check Error): Device did not issue a valid App Check JWT. ")
                        append("Verify registered Debug Token (Debug) or Play Services / reCAPTCHA (Release).")
                    }
                    if (appCheckDiag.isNotBlank()) {
                        append('\n').append(appCheckDiag)
                    } else if (safeReason.isNotBlank()) {
                        append('\n').append(safeReason)
                    }
                },
                appCheckValid = false,
                appCheckStatus = "INVALID_OR_MISSING",
                model = "",
                latencyMs = System.currentTimeMillis() - startedAt,
                replyPreview = "",
                responseSnippet = providerSummary,
                timestamp = ""
            )
        }

        val safePrompt = prompt.trim().ifBlank { DEFAULT_PROMPT }.take(200)
        val sensitiveTokens = listOf(idToken, appCheckToken)

        // 4. Probe Cloud Function `testGeminiConnection` first (when deployed, returns server-verified result)
        val cfOutcome = probeCloudFunction(
            safePrompt = safePrompt,
            idToken = idToken,
            appCheckToken = appCheckToken,
            language = language,
            startedAt = startedAt
        )

        if (cfOutcome.testPassed && cfOutcome.geminiWorking) {
            Log.i(
                TAG,
                "testGeminiConnection Cloud Function PASSED: httpStatus=${cfOutcome.httpStatus}, " +
                    "model=${cfOutcome.model}, latencyMs=${cfOutcome.latencyMs}"
            )
            return@withContext cfOutcome.copy(
                backendChannel = "CLOUD_FUNCTION",
                cloudFunctionHttpStatus = cfOutcome.httpStatus,
                cloudFunctionSummary = if (language == AppLanguage.HINDI) {
                    "Cloud Function (testGeminiConnection): LIVE (HTTP ${cfOutcome.httpStatus})"
                } else {
                    "Cloud Function (testGeminiConnection): LIVE (HTTP ${cfOutcome.httpStatus})"
                }
            )
        }

        // 5. If Cloud Function is NOT deployed (HTTP 404), run the live Firebase AI Logic (`Firebase.ai`)
        //    check — the official provisioned Android Gemini backend — while still transparently
        //    reporting that the optional Cloud Function returned HTTP 404 (not deployed).
        if (cfOutcome.httpStatus == 404) {
            val aiStart = System.currentTimeMillis()
            val aiProbe = try {
                firebaseAiLogicProbe(safePrompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                classifyFirebaseAiException(e)
            }
            val aiLatencyMs = System.currentTimeMillis() - aiStart
            val cleanReply = redactSensitiveText(aiProbe.replyText, sensitiveTokens).trim().take(240)
            val cleanErr = redactSensitiveText(aiProbe.errorMessage, sensitiveTokens).trim().take(360)
            val cfStatusNote = if (language == AppLanguage.HINDI) {
                "Cloud Function (testGeminiConnection): अभी Deploy नहीं है (HTTP 404)"
            } else {
                "Cloud Function (testGeminiConnection): Not deployed yet (HTTP 404)"
            }

            val aiPassed = aiProbe.ok && cleanReply.isNotBlank()
            val result = if (aiPassed) {
                AdminAiDiagnosticResult(
                    testPassed = true,
                    geminiWorking = true,
                    httpStatus = 200,
                    errorCodeCategory = "OK",
                    rootCause = "HEALTHY_FIREBASE_AI_LOGIC",
                    verdict = if (language == AppLanguage.HINDI) {
                        "सफल: Firebase AI Logic से Gemini API वास्तविक उत्तर दे रही है और App Check वैध है।"
                    } else {
                        "PASS: Gemini API is responding live via Firebase AI Logic with valid App Check."
                    },
                    errorDetails = "",
                    appCheckValid = true,
                    appCheckStatus = "VERIFIED",
                    model = aiProbe.model,
                    latencyMs = aiLatencyMs,
                    replyPreview = cleanReply,
                    responseSnippet = "",
                    timestamp = "",
                    backendChannel = "FIREBASE_AI_LOGIC",
                    cloudFunctionHttpStatus = 404,
                    cloudFunctionSummary = cfStatusNote
                )
            } else {
                val status = aiProbe.httpStatus
                AdminAiDiagnosticResult(
                    testPassed = false,
                    geminiWorking = false,
                    httpStatus = status,
                    errorCodeCategory = if (status > 0) "HTTP_$status" else "NETWORK_ERROR",
                    rootCause = rootCauseForStatus(status),
                    verdict = if (language == AppLanguage.HINDI) {
                        "असफल: Firebase AI Logic से कार्यशील उत्तर नहीं मिला (${if (status > 0) "HTTP $status" else "Network Error"})।"
                    } else {
                        "FAIL: Firebase AI Logic did not return a working response (${if (status > 0) "HTTP $status" else "Network Error"})."
                    },
                    errorDetails = buildErrorDetails(status, cleanErr, language),
                    appCheckValid = status != 401 && status != 403,
                    appCheckStatus = if (status == 401 || status == 403) "REJECTED_BY_BACKEND" else "ISSUED",
                    model = aiProbe.model,
                    latencyMs = aiLatencyMs,
                    replyPreview = "",
                    responseSnippet = cleanErr.ifBlank { describeAppCheckProviderForLog() },
                    timestamp = "",
                    backendChannel = "FIREBASE_AI_LOGIC",
                    cloudFunctionHttpStatus = 404,
                    cloudFunctionSummary = cfStatusNote
                )
            }

            Log.i(
                TAG,
                "Admin AI Logic probe completed: httpStatus=${result.httpStatus}, " +
                    "geminiWorking=${result.geminiWorking}, cfStatus=404, latencyMs=${result.latencyMs}"
            )
            return@withContext result
        }

        // Cloud Function returned a real non-404 response (e.g., 401, 403, 429, 500, or Network Error)
        Log.i(
            TAG,
            "testGeminiConnection Cloud Function returned non-200: httpStatus=${cfOutcome.httpStatus}, " +
                "rootCause=${cfOutcome.rootCause}, latencyMs=${cfOutcome.latencyMs}"
        )
        cfOutcome.copy(
            backendChannel = "CLOUD_FUNCTION",
            cloudFunctionHttpStatus = cfOutcome.httpStatus,
            cloudFunctionSummary = if (language == AppLanguage.HINDI) {
                "Cloud Function (testGeminiConnection): HTTP ${cfOutcome.httpStatus} (${cfOutcome.rootCause})"
            } else {
                "Cloud Function (testGeminiConnection): HTTP ${cfOutcome.httpStatus} (${cfOutcome.rootCause})"
            }
        )
    }

    private suspend fun probeCloudFunction(
        safePrompt: String,
        idToken: String,
        appCheckToken: String,
        language: AppLanguage,
        startedAt: Long
    ): AdminAiDiagnosticResult {
        val payload = JSONObject().put("prompt", safePrompt).toString()
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Accept" to "application/json",
            "Authorization" to "Bearer $idToken",
            "X-Firebase-AppCheck" to appCheckToken
        )

        val response = try {
            httpPostExecutor(endpointUrl, headers, payload)
        } catch (e: Exception) {
            val latencyMs = System.currentTimeMillis() - startedAt
            val safeMsg = redactSensitiveText(e.message.orEmpty(), listOf(idToken, appCheckToken))
            val isTimeout = e is SocketTimeoutException
            val isOffline = e is UnknownHostException || e is ConnectException
            val rootCause = when {
                isTimeout -> "NETWORK_TIMEOUT"
                isOffline -> "NETWORK_UNREACHABLE"
                else -> "NETWORK_IO_ERROR"
            }
            return AdminAiDiagnosticResult(
                testPassed = false,
                geminiWorking = false,
                httpStatus = 0,
                errorCodeCategory = "NETWORK_ERROR",
                rootCause = rootCause,
                verdict = if (language == AppLanguage.HINDI) {
                    "असफल: सर्वर से कनेक्शन स्थापित नहीं हो सका।"
                } else {
                    "FAIL: Could not reach the server."
                },
                errorDetails = if (language == AppLanguage.HINDI) {
                    when {
                        isTimeout -> "Timeout Error: सर्वर से उत्तर आने में समय समाप्त हो गया। इंटरनेट जाँचकर पुनः प्रयास करें।"
                        isOffline -> "Network Error: इंटरनेट कनेक्शन नहीं मिल रहा या सर्वर होस्ट तक पहुँच नहीं है ($safeMsg)।"
                        else -> "Network Error: ${safeMsg.ifBlank { e.javaClass.simpleName }}"
                    }
                } else {
                    when {
                        isTimeout -> "Timeout Error: Request timed out. Check network and retry."
                        isOffline -> "Network Error: Unable to reach server host ($safeMsg)."
                        else -> "Network Error: ${safeMsg.ifBlank { e.javaClass.simpleName }}"
                    }
                },
                appCheckValid = true,
                appCheckStatus = "SENT_NOT_VERIFIED_OFFLINE",
                model = "",
                latencyMs = latencyMs,
                replyPreview = "",
                responseSnippet = safeMsg,
                timestamp = ""
            )
        }

        val totalLatencyMs = System.currentTimeMillis() - startedAt
        return parseServerResponse(
            httpCode = response.statusCode,
            rawBody = response.body,
            language = language,
            fallbackLatencyMs = totalLatencyMs,
            sensitiveTokens = listOf(idToken, appCheckToken)
        )
    }

    companion object {
        private const val TAG = "AdminAiDiagnostic"
        const val DEFAULT_ENDPOINT_URL =
            "https://us-central1-gen-lang-client-0821187064.cloudfunctions.net/testGeminiConnection"
        const val DEFAULT_PROMPT = "Hello Goshala"
        const val PRIMARY_AI_LOGIC_MODEL = "gemini-3.8-flash"
        const val FALLBACK_AI_LOGIC_MODEL = "gemini-3.5-flash-lite"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 25_000
        private const val AI_LOGIC_TIMEOUT_MS = 30_000L

        /**
         * Redacts any API keys, Bearer tokens, or JWTs so secrets are never logged or shown in UI.
         */
        fun redactSensitiveText(value: String, exactSecrets: List<String> = emptyList()): String {
            var out = value
            for (secret in exactSecrets) {
                val trimmed = secret.trim()
                if (trimmed.length >= 8) {
                    out = out.replace(trimmed, "[REDACTED_SECRET]")
                }
            }
            return out
                .replace(Regex("AIza[0-9A-Za-z_-]{16,}"), "[REDACTED_API_KEY]")
                .replace(Regex("Bearer\\s+[A-Za-z0-9._-]{12,}", RegexOption.IGNORE_CASE), "Bearer [REDACTED_TOKEN]")
                .replace(Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}"), "[REDACTED_JWT]")
        }

        private suspend fun fetchLiveFirebaseIdToken(): Result<String> = runCatching {
            val user = FirebaseAuth.getInstance().currentUser
                ?: throw IllegalStateException("No signed-in Firebase user.")
            val tokenResult = user.getIdToken(true).await()
            val token = tokenResult.token?.trim().orEmpty()
            if (token.isBlank()) throw IllegalStateException("Firebase ID token was empty.")
            token
        }

        private suspend fun fetchLiveAppCheckToken(): Result<String> = runCatching {
            val app = FirebaseApp.getInstance()
            val appCheck = FirebaseAppCheck.getInstance(app)
            val first = runCatching { appCheck.getAppCheckToken(false).await().token }.getOrNull()
            if (isValidAppCheckJwt(first)) {
                return@runCatching first!!.trim()
            }
            refreshAppCheckToken(forceRefresh = true)
            val refreshed = appCheck.getAppCheckToken(true).await().token
            if (!isValidAppCheckJwt(refreshed)) {
                throw IllegalStateException("App Check returned an invalid or dummy token.")
            }
            refreshed.trim()
        }

        private suspend fun executeLiveFirebaseAiLogicProbe(prompt: String): FirebaseAiLogicProbeOutcome {
            val models = listOf(PRIMARY_AI_LOGIC_MODEL, FALLBACK_AI_LOGIC_MODEL)
            var lastFailure: FirebaseAiLogicProbeOutcome? = null

            for ((index, modelName) in models.withIndex()) {
                try {
                    val response = withTimeout(AI_LOGIC_TIMEOUT_MS) {
                        Firebase.ai.generativeModel(modelName = modelName).generateContent(prompt)
                    }
                    val text = response.text?.trim().orEmpty()
                    if (text.isNotBlank()) {
                        return FirebaseAiLogicProbeOutcome(
                            ok = true,
                            httpStatus = 200,
                            model = modelName,
                            replyText = text
                        )
                    }
                    lastFailure = FirebaseAiLogicProbeOutcome(
                        ok = false,
                        httpStatus = 500,
                        model = modelName,
                        replyText = "",
                        errorMessage = "Empty response returned from $modelName."
                    )
                } catch (e: CancellationException) {
                    if (e is TimeoutCancellationException) {
                        return FirebaseAiLogicProbeOutcome(
                            ok = false,
                            httpStatus = 0,
                            model = modelName,
                            replyText = "",
                            errorMessage = "Request timed out after ${AI_LOGIC_TIMEOUT_MS / 1000}s."
                        )
                    }
                    throw e
                } catch (e: Exception) {
                    val outcome = classifyFirebaseAiException(e, modelName)
                    lastFailure = outcome
                    // Only try fallback model on 404/429/5xx, never hide 401/403 App Check issues
                    val canFallback = index == 0 && (outcome.httpStatus == 404 || outcome.httpStatus == 429 || outcome.httpStatus >= 500)
                    if (!canFallback) {
                        return outcome
                    }
                }
            }
            return lastFailure ?: FirebaseAiLogicProbeOutcome(
                ok = false,
                httpStatus = 500,
                model = PRIMARY_AI_LOGIC_MODEL,
                replyText = "",
                errorMessage = "No response from Firebase AI Logic."
            )
        }

        internal fun classifyFirebaseAiException(
            e: Throwable,
            modelName: String = PRIMARY_AI_LOGIC_MODEL
        ): FirebaseAiLogicProbeOutcome {
            val combined = buildString {
                var cur: Throwable? = e
                var depth = 0
                while (cur != null && depth < 4) {
                    append(cur.javaClass.simpleName).append(": ").append(cur.message.orEmpty()).append(" | ")
                    cur = cur.cause
                    depth++
                }
            }
            val lower = combined.lowercase()
            val status = when {
                lower.contains("unable to resolve host") ||
                    lower.contains("unknownhost") ||
                    lower.contains("connectexception") ||
                    lower.contains("timeout") ||
                    lower.contains("timed out") -> 0
                lower.contains("401") || lower.contains("unauthenticated") || lower.contains("app check token is invalid") -> 401
                lower.contains("403") || lower.contains("permission_denied") || lower.contains("service_disabled") || lower.contains("forbidden") -> 403
                lower.contains("404") || lower.contains("not_found") || lower.contains("not found") -> 404
                lower.contains("429") || lower.contains("resource_exhausted") || lower.contains("quota") -> 429
                lower.contains("503") -> 503
                lower.contains("500") || lower.contains("internal") || lower.contains("unavailable") -> 500
                else -> 500
            }
            return FirebaseAiLogicProbeOutcome(
                ok = false,
                httpStatus = status,
                model = modelName,
                replyText = "",
                errorMessage = redactSensitiveText(combined).take(320)
            )
        }

        private fun rootCauseForStatus(status: Int): String = when (status) {
            0 -> "NETWORK_OR_TIMEOUT"
            401 -> "APP_CHECK_OR_AUTH_401"
            403 -> "PERMISSION_OR_APP_CHECK_403"
            404 -> "MODEL_OR_ENDPOINT_NOT_FOUND_404"
            429 -> "QUOTA_OR_RATE_LIMIT_429"
            in 500..599 -> "SERVER_ERROR_5XX"
            else -> "HTTP_$status"
        }

        private fun buildErrorDetails(status: Int, safeErr: String, language: AppLanguage): String {
            val base = when (status) {
                0 -> if (language == AppLanguage.HINDI) {
                    "Network / Timeout Error: इंटरनेट कनेक्शन या टाइमआउट समस्या।"
                } else {
                    "Network / Timeout Error: Could not reach Firebase AI backend."
                }
                401 -> if (language == AppLanguage.HINDI) {
                    "HTTP 401 (App Check / Auth त्रुटि): Firebase App Check टोकन अस्वीकृत हुआ। मोबाइल का समय (Auto Date & Time) और App Check टोकन जाँचें।"
                } else {
                    "HTTP 401 (App Check / Auth Error): Firebase App Check token was rejected."
                }
                403 -> if (language == AppLanguage.HINDI) {
                    "HTTP 403 (Permission / App Check त्रुटि): Firebase AI Logic अनुमति या App Check सत्यापन अस्वीकृत हुआ।"
                } else {
                    "HTTP 403 (Permission / App Check Error): Firebase AI Logic permission or App Check attestation rejected."
                }
                404 -> if (language == AppLanguage.HINDI) {
                    "HTTP 404 (Model / Endpoint त्रुटि): अनुरोधित Gemini मॉडल उपलब्ध नहीं मिला।"
                } else {
                    "HTTP 404 (Model / Endpoint Error): Requested Gemini model was not found."
                }
                429 -> if (language == AppLanguage.HINDI) {
                    "HTTP 429 (Quota / Rate Limit): API अनुरोध सीमा पूरी हो गई है। कुछ सेकंड बाद पुनः प्रयास करें।"
                } else {
                    "HTTP 429 (Quota / Rate Limit): API rate limit or quota exceeded."
                }
                else -> if (language == AppLanguage.HINDI) {
                    "HTTP $status (Server Error): Gemini बैकएंड में अस्थायी त्रुटि।"
                } else {
                    "HTTP $status (Server Error): Temporary Gemini backend error."
                }
            }
            return if (safeErr.isBlank()) base else "$base\n$safeErr"
        }

        private fun executeHttpsPost(
            urlStr: String,
            headers: Map<String, String>,
            jsonBody: String
        ): DiagnosticHttpResponse {
            val url = URL(urlStr)
            require(url.protocol.equals("https", ignoreCase = true)) {
                "Diagnostic endpoint must use HTTPS."
            }
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                useCaches = false
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                conn.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                    os.flush()
                }
                val statusCode = conn.responseCode
                val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
                val body = if (stream != null) {
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    ""
                }
                return DiagnosticHttpResponse(statusCode = statusCode, body = body)
            } finally {
                conn.disconnect()
            }
        }

        internal fun parseServerResponse(
            httpCode: Int,
            rawBody: String,
            language: AppLanguage,
            fallbackLatencyMs: Long,
            sensitiveTokens: List<String> = emptyList()
        ): AdminAiDiagnosticResult {
            val safeRaw = redactSensitiveText(rawBody, sensitiveTokens)
            val json = runCatching { JSONObject(safeRaw) }.getOrNull()

            if (json != null && (json.has("geminiWorking") || json.has("testPassed") || json.has("httpStatus"))) {
                val reportedStatus = json.optInt("httpStatus", httpCode).let { if (it > 0) it else httpCode }
                val geminiWorking = json.optBoolean("geminiWorking", false) && reportedStatus in 200..299
                val appCheckObj = json.optJSONObject("appCheck")
                val appCheckValid = appCheckObj?.optBoolean("valid", false) ?: false
                val appCheckStatus = appCheckObj?.optString("status", if (appCheckValid) "VERIFIED" else "UNKNOWN")
                    ?.ifBlank { "UNKNOWN" } ?: "UNKNOWN"
                val testPassed = json.optBoolean("testPassed", false) && geminiWorking && appCheckValid
                val verdict = if (language == AppLanguage.HINDI) {
                    json.optString("verdictHi").ifBlank { json.optString("verdictEn") }
                } else {
                    json.optString("verdictEn").ifBlank { json.optString("verdictHi") }
                }
                val errorDetails = if (language == AppLanguage.HINDI) {
                    json.optString("errorDetailsHi").ifBlank { json.optString("errorDetailsEn") }
                } else {
                    json.optString("errorDetailsEn").ifBlank { json.optString("errorDetailsHi") }
                }
                val replyPreview = redactSensitiveText(json.optString("replyPreview", ""), sensitiveTokens)
                val responseSnippet = redactSensitiveText(json.optString("responseSnippet", ""), sensitiveTokens)
                return AdminAiDiagnosticResult(
                    testPassed = testPassed,
                    geminiWorking = geminiWorking,
                    httpStatus = reportedStatus,
                    errorCodeCategory = json.optString("errorCodeCategory", "HTTP_$reportedStatus"),
                    rootCause = json.optString("rootCause", if (testPassed) "HEALTHY" else "HTTP_$reportedStatus"),
                    verdict = verdict.ifBlank {
                        if (testPassed) {
                            if (language == AppLanguage.HINDI) "सफल: Gemini API काम कर रही है।" else "PASS: Gemini API is working."
                        } else {
                            if (language == AppLanguage.HINDI) "असफल: HTTP $reportedStatus त्रुटि।" else "FAIL: HTTP $reportedStatus error."
                        }
                    },
                    errorDetails = errorDetails,
                    appCheckValid = appCheckValid,
                    appCheckStatus = appCheckStatus,
                    model = json.optString("model", ""),
                    latencyMs = json.optLong("latencyMs", fallbackLatencyMs),
                    replyPreview = replyPreview,
                    responseSnippet = responseSnippet.take(400),
                    timestamp = json.optString("timestamp", "")
                )
            }

            val isNotDeployed404 = httpCode == 404
            val errorDetails = when (httpCode) {
                401 -> if (language == AppLanguage.HINDI) {
                    "HTTP 401 (Authentication / App Check त्रुटि): सर्वर ने Admin ID Token या App Check Token अस्वीकार किया।"
                } else {
                    "HTTP 401 (Authentication / App Check Error): Server rejected Admin ID or App Check token."
                }
                403 -> if (language == AppLanguage.HINDI) {
                    "HTTP 403 (अनुमति अस्वीकृत): केवल अधिकृत Admin को अनुमति है या Cloud Function IAM / API अनुमति बाधित है।"
                } else {
                    "HTTP 403 (Forbidden): Admin permission required or Cloud Function IAM/API permission denied."
                }
                404 -> if (language == AppLanguage.HINDI) {
                    "HTTP 404 (Function Deploy नहीं मिला): Firebase पर `testGeminiConnection` Cloud Function अभी Deploy नहीं है।"
                } else {
                    "HTTP 404 (Not Found): `testGeminiConnection` Cloud Function is not deployed yet on Firebase."
                }
                429 -> if (language == AppLanguage.HINDI) {
                    "HTTP 429 (Quota / Rate Limit): API अनुरोध सीमा पूरी हो गई है। कुछ देर बाद पुनः प्रयास करें।"
                } else {
                    "HTTP 429 (Quota / Rate Limit): API rate limit or quota exceeded. Retry shortly."
                }
                in 500..599 -> if (language == AppLanguage.HINDI) {
                    "HTTP $httpCode (Server Error): Cloud Function या Gemini सर्वर में अस्थायी त्रुटि आई है।"
                } else {
                    "HTTP $httpCode (Server Error): Cloud Function or Gemini backend returned a server error."
                }
                else -> if (language == AppLanguage.HINDI) {
                    "HTTP $httpCode: सर्वर से अप्रत्याशित उत्तर प्राप्त हुआ।"
                } else {
                    "HTTP $httpCode: Unexpected non-JSON response from server."
                }
            }

            return AdminAiDiagnosticResult(
                testPassed = false,
                geminiWorking = false,
                httpStatus = httpCode,
                errorCodeCategory = "HTTP_$httpCode",
                rootCause = if (isNotDeployed404) "FUNCTION_NOT_DEPLOYED_404" else "UNEXPECTED_HTTP_$httpCode",
                verdict = if (language == AppLanguage.HINDI) {
                    "असफल: सर्वर से HTTP $httpCode प्रतिक्रिया प्राप्त हुई।"
                } else {
                    "FAIL: Server returned HTTP $httpCode."
                },
                errorDetails = errorDetails,
                appCheckValid = false,
                appCheckStatus = if (isNotDeployed404) "ENDPOINT_NOT_DEPLOYED" else "UNKNOWN",
                model = "",
                latencyMs = fallbackLatencyMs,
                replyPreview = "",
                responseSnippet = safeRaw.take(400),
                timestamp = ""
            )
        }
    }
}

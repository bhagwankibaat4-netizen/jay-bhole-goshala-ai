package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.core.AppLanguage
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.repository.AdminAiDiagnosticRepository
import com.example.data.repository.AdminAiDiagnosticResult
import com.example.data.repository.DiagnosticHttpResponse
import com.example.ui.screens.AdminAiDiagnosticCard
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.AdminAiDiagnosticUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
class AdminAiDiagnosticTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin_uid_1",
        email = "bhagwankibaat4@gmail.com",
        displayName = "Goshala Admin",
        role = UserRole.ADMIN
    )

    private val staffSession = UserSession(
        uid = "staff_uid_1",
        email = "staff@example.com",
        displayName = "Goshala Staff",
        role = UserRole.STAFF
    )

    private val validJwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.validSignature123456"

    @Test
    fun nonAdminUserIsBlockedBeforeAnyNetworkCall() = runBlocking {
        var httpCalled = false
        val repo = AdminAiDiagnosticRepository(
            idTokenProvider = { Result.success(validJwt) },
            appCheckTokenProvider = { Result.success(validJwt) },
            httpPostExecutor = { _, _, _ ->
                httpCalled = true
                DiagnosticHttpResponse(200, """{"testPassed":true,"geminiWorking":true}""")
            }
        )

        val result = repo.testGeminiConnection(staffSession, AppLanguage.HINDI)
        assertFalse(httpCalled)
        assertFalse(result.testPassed)
        assertFalse(result.geminiWorking)
        assertEquals(403, result.httpStatus)
        assertEquals("ADMIN_ONLY_FORBIDDEN", result.rootCause)
    }

    @Test
    fun missingOrDummyAppCheckTokenFailsHonestlyBeforeServerCall() = runBlocking {
        var httpCalled = false
        val repo = AdminAiDiagnosticRepository(
            idTokenProvider = { Result.success(validJwt) },
            appCheckTokenProvider = { Result.success("eyJlcnJvciI6InVuYXV0aG9yaXplZCJ9") },
            httpPostExecutor = { _, _, _ ->
                httpCalled = true
                DiagnosticHttpResponse(200, """{"testPassed":true,"geminiWorking":true}""")
            }
        )

        val result = repo.testGeminiConnection(adminSession, AppLanguage.HINDI)
        assertFalse(httpCalled)
        assertFalse(result.testPassed)
        assertFalse(result.appCheckValid)
        assertEquals(401, result.httpStatus)
        assertEquals("APP_CHECK_TOKEN_UNAVAILABLE", result.rootCause)
    }

    @Test
    fun validAdminAndAppCheckSendsHeadersAndParsesRealGeminiReply() = runBlocking {
        var capturedHeaders: Map<String, String> = emptyMap()
        var capturedBody = ""
        val repo = AdminAiDiagnosticRepository(
            idTokenProvider = { Result.success("id-token-header-secret") },
            appCheckTokenProvider = { Result.success(validJwt) },
            httpPostExecutor = { _, headers, body ->
                capturedHeaders = headers
                capturedBody = body
                DiagnosticHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "testPassed": true,
                          "geminiWorking": true,
                          "httpStatus": 200,
                          "errorCodeCategory": "OK",
                          "rootCause": "HEALTHY",
                          "verdictHi": "सफल: Gemini API वास्तव में काम कर रही है और App Check टोकन वैध है।",
                          "verdictEn": "PASS: Gemini API is working and App Check token is valid.",
                          "errorDetailsHi": "HTTP 200 सफल: Gemini API से वास्तविक उत्तर प्राप्त हुआ है।",
                          "appCheck": { "valid": true, "status": "VERIFIED" },
                          "model": "gemini-3.8-flash",
                          "latencyMs": 312,
                          "replyPreview": "नमस्ते गौशाला! मैं आपकी सहायता के लिए तैयार हूँ।"
                        }
                    """.trimIndent()
                )
            }
        )

        val result = repo.testGeminiConnection(adminSession, AppLanguage.HINDI)
        assertEquals("Bearer id-token-header-secret", capturedHeaders["Authorization"])
        assertEquals(validJwt, capturedHeaders["X-Firebase-AppCheck"])
        assertTrue(capturedBody.contains("Hello Goshala"))
        assertTrue(result.testPassed)
        assertTrue(result.geminiWorking)
        assertTrue(result.appCheckValid)
        assertEquals(200, result.httpStatus)
        assertEquals("gemini-3.8-flash", result.model)
        assertTrue(result.replyPreview.contains("नमस्ते गौशाला"))
    }

    @Test
    fun errorResponses401_403_404_429_500AndOfflineReportRealRootCausesAndRedactSecrets() = runBlocking {
        val leakedApiKey = "AIzaSyEchoedSecretKeyValue1234567890"
        val errorCases = listOf(401, 403, 404, 429, 500)

        for (code in errorCases) {
            val repo = AdminAiDiagnosticRepository(
                idTokenProvider = { Result.success("id-token-header-secret") },
                appCheckTokenProvider = { Result.success(validJwt) },
                firebaseAiLogicProbe = {
                    com.example.data.repository.FirebaseAiLogicProbeOutcome(
                        ok = false,
                        httpStatus = code,
                        model = "gemini-3.8-flash",
                        replyText = "",
                        errorMessage = "Firebase AI Logic error HTTP $code ($leakedApiKey)"
                    )
                },
                httpPostExecutor = { _, _, _ ->
                    DiagnosticHttpResponse(
                        statusCode = code,
                        body = """
                            {
                              "testPassed": false,
                              "geminiWorking": false,
                              "httpStatus": $code,
                              "errorCodeCategory": "HTTP_$code",
                              "rootCause": "UPSTREAM_$code",
                              "verdictHi": "असफल: HTTP $code",
                              "errorDetailsHi": "HTTP $code त्रुटि ($leakedApiKey)",
                              "appCheck": { "valid": true, "status": "VERIFIED" },
                              "responseSnippet": "Upstream error with $leakedApiKey and $validJwt"
                            }
                        """.trimIndent()
                    )
                }
            )
            val result = repo.testGeminiConnection(adminSession, AppLanguage.HINDI)
            assertFalse(result.testPassed)
            assertFalse(result.geminiWorking)
            assertEquals(code, result.httpStatus)
            assertFalse(result.errorDetails.contains(leakedApiKey))
            assertFalse(result.responseSnippet.contains(leakedApiKey))
            assertFalse(result.responseSnippet.contains(validJwt))
        }

        // Cloud Function 404 (not deployed) + live Firebase AI Logic 200 reports BOTH honestly
        val aiLogicRepo = AdminAiDiagnosticRepository(
            idTokenProvider = { Result.success("id-token-header-secret") },
            appCheckTokenProvider = { Result.success(validJwt) },
            firebaseAiLogicProbe = {
                com.example.data.repository.FirebaseAiLogicProbeOutcome(
                    ok = true,
                    httpStatus = 200,
                    model = "gemini-3.8-flash",
                    replyText = "नमस्ते गौशाला! Firebase AI Logic सक्रिय है।"
                )
            },
            httpPostExecutor = { _, _, _ -> DiagnosticHttpResponse(404, "Not Found") }
        )
        val aiLogicResult = aiLogicRepo.testGeminiConnection(adminSession, AppLanguage.HINDI)
        assertTrue(aiLogicResult.testPassed)
        assertTrue(aiLogicResult.geminiWorking)
        assertEquals(200, aiLogicResult.httpStatus)
        assertEquals(404, aiLogicResult.cloudFunctionHttpStatus)
        assertEquals("FIREBASE_AI_LOGIC", aiLogicResult.backendChannel)
        assertTrue(aiLogicResult.replyPreview.contains("नमस्ते गौशाला"))

        // Network offline test
        val offlineRepo = AdminAiDiagnosticRepository(
            idTokenProvider = { Result.success("id-token-header-secret") },
            appCheckTokenProvider = { Result.success(validJwt) },
            httpPostExecutor = { _, _, _ -> throw UnknownHostException("Unable to resolve host") }
        )
        val offlineResult = offlineRepo.testGeminiConnection(adminSession, AppLanguage.HINDI)
        assertFalse(offlineResult.testPassed)
        assertEquals(0, offlineResult.httpStatus)
        assertEquals("NETWORK_UNREACHABLE", offlineResult.rootCause)
    }

    @Test
    fun adminCardShowsButtonAndRealResultForAdminAndHidesForNonAdmin() {
        var clicked = false
        val failReport = AdminAiDiagnosticResult(
            testPassed = false,
            geminiWorking = false,
            httpStatus = 404,
            errorCodeCategory = "HTTP_404",
            rootCause = "FUNCTION_NOT_DEPLOYED_404",
            verdict = "असफल: सर्वर से HTTP 404 प्रतिक्रिया प्राप्त हुई।",
            errorDetails = "HTTP 404 (Function Deploy नहीं मिला)",
            appCheckValid = false,
            appCheckStatus = "ENDPOINT_NOT_DEPLOYED",
            model = "",
            latencyMs = 180,
            replyPreview = "",
            responseSnippet = "",
            timestamp = ""
        )

        composeRule.setContent {
            GosalaTheme {
                AdminAiDiagnosticCard(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    diagnosticState = AdminAiDiagnosticUiState.Result(failReport),
                    onRunTest = { clicked = true },
                    onClearResult = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_test_ai_connection_btn").assertIsDisplayed().performClick()
        assertTrue(clicked)
        composeRule.onNodeWithTag("admin_ai_diagnostic_result").assertIsDisplayed()
        composeRule.onNodeWithText("HTTP 404").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_ai_error_details_text").assertIsDisplayed()
    }

    @Test
    fun adminCardDoesNotRenderForStaffRole() {
        composeRule.setContent {
            GosalaTheme {
                AdminAiDiagnosticCard(
                    language = AppLanguage.HINDI,
                    session = staffSession,
                    diagnosticState = AdminAiDiagnosticUiState.Idle,
                    onRunTest = {},
                    onClearResult = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_test_ai_connection_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("admin_ai_diagnostic_card").assertDoesNotExist()
    }
}

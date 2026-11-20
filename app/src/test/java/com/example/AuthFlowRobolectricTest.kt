package com.example

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.core.AppLanguage
import com.example.data.model.LoginPortal
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.auth.AuthActionResult
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthValidator
import com.example.ui.auth.EmailAuthGateway
import com.example.ui.navigation.AuthNavigationController
import com.example.ui.navigation.AuthNavigationState
import com.example.ui.navigation.GosalaRootNavHost
import com.example.ui.navigation.rememberAuthNavigationController
import com.example.ui.screens.Routes
import com.example.ui.theme.GosalaTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuthFlowRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeEmailAuthGateway(
        var onSignUp: suspend (String, String, String) -> AuthActionResult = { _, _, _ ->
            AuthActionResult.Success()
        },
        var onSignIn: suspend (String, String) -> AuthActionResult = { _, _ ->
            AuthActionResult.Success()
        },
        var onReset: suspend (String) -> AuthActionResult = { email ->
            AuthActionResult.Success(
                messageHi = "पासवर्ड रीसेट लिंक आपके Gmail ($email) पर भेज दिया गया है।",
                messageEn = "Password reset link sent to $email."
            )
        }
    ) : EmailAuthGateway {
        var signUpCalls = 0
        var signInCalls = 0
        var resetCalls = 0

        override suspend fun signUpWithEmail(name: String, email: String, password: String): AuthActionResult {
            signUpCalls++
            return onSignUp(name, email, password)
        }

        override suspend fun signInWithEmail(email: String, password: String): AuthActionResult {
            signInCalls++
            return onSignIn(email, password)
        }

        override suspend fun sendPasswordReset(email: String): AuthActionResult {
            resetCalls++
            return onReset(email)
        }
    }

    @Test
    fun `1 signup validates name email and 6-char password and creates account cleanly`() {
        var capturedName = ""
        var capturedEmail = ""
        var capturedPortal: LoginPortal? = null
        var authSucceeded = false

        val fakeGateway = FakeEmailAuthGateway(
            onSignUp = { name, email, _ ->
                capturedName = name
                capturedEmail = email
                AuthActionResult.Success()
            }
        )

        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = LoginPortal.ADMIN,
                    onSelectPortal = {},
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    onAuthAttemptStart = { capturedPortal = it },
                    emailAuthGateway = fakeGateway,
                    onAuthSuccess = { authSucceeded = true }
                )
            }
        }

        // Switch to Signup mode
        composeRule.onNodeWithTag("auth_mode_signup").performScrollTo().performClick()
        composeRule.waitForIdle()

        // Invalid inputs: blank name, bad email, short password (<6 chars)
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("invalid-email")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("12345")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(0, fakeGateway.signUpCalls)
        composeRule.onNodeWithTag("auth_name_input").performScrollTo()
        composeRule.onNodeWithTag("auth_name_error", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_input").performScrollTo()
        composeRule.onNodeWithTag("auth_email_error", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("auth_password_input").performScrollTo()
        composeRule.onNodeWithTag("auth_password_error", useUnmergedTree = true).assertIsDisplayed()

        // Valid inputs -> Signup succeeds and forces PUBLIC portal (never Admin escalation)
        composeRule.onNodeWithTag("auth_name_input").performScrollTo().performTextInput("राम शर्मा")
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextReplacement("ram.sharma@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextReplacement("goshala123")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeGateway.signUpCalls)
        assertEquals("राम शर्मा", capturedName)
        assertEquals("ram.sharma@gmail.com", capturedEmail)
        assertEquals(LoginPortal.PUBLIC, capturedPortal)
        assertTrue(authSucceeded)
    }

    @Test
    fun `2 duplicate email signup shows clear Hindi error`() {
        val fakeGateway = FakeEmailAuthGateway(
            onSignUp = { _, _, _ ->
                AuthValidator.mapAuthErrorCode("ERROR_EMAIL_ALREADY_IN_USE")
            }
        )

        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = LoginPortal.PUBLIC,
                    onSelectPortal = {},
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    emailAuthGateway = fakeGateway,
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("auth_mode_signup").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth_name_input").performScrollTo().performTextInput("मोहन लाल")
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("existing@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("secret123")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeGateway.signUpCalls)
        composeRule.onNodeWithTag("auth_error_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("यह ईमेल पहले से पंजीकृत है। कृपया लॉगिन करें या दूसरा Gmail पता उपयोग करें।")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `3 login succeeds and blocks duplicate request while in flight`() {
        val gate = CompletableDeferred<AuthActionResult>()
        var authSucceeded = false
        val fakeGateway = FakeEmailAuthGateway(
            onSignIn = { _, _ -> gate.await() }
        )

        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = LoginPortal.PUBLIC,
                    onSelectPortal = {},
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    emailAuthGateway = fakeGateway,
                    onAuthSuccess = { authSucceeded = true }
                )
            }
        }

        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("bhakt@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("password123")

        // Click Login button twice rapidly while first request is still pending
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("email_auth_submit_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("email_auth_submit_button").performClick()

        // Only ONE request should have been dispatched
        assertEquals(1, fakeGateway.signInCalls)
        assertFalse(authSucceeded)

        // Complete the in-flight request
        gate.complete(AuthActionResult.Success())
        composeRule.waitForIdle()

        assertEquals(1, fakeGateway.signInCalls)
        assertTrue(authSucceeded)
    }

    @Test
    fun `4 wrong password login shows clear Hindi error`() {
        val fakeGateway = FakeEmailAuthGateway(
            onSignIn = { _, _ ->
                AuthValidator.mapAuthErrorCode("ERROR_WRONG_PASSWORD")
            }
        )

        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = LoginPortal.PUBLIC,
                    onSelectPortal = {},
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    emailAuthGateway = fakeGateway,
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("bhakt@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("wrongpass")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(1, fakeGateway.signInCalls)
        composeRule.onNodeWithTag("auth_error_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("ईमेल या पासवर्ड गलत है। कृपया सही जानकारी दर्ज करें या 'पासवर्ड भूल गए?' का उपयोग करें।")
            .performScrollTo()
            .assertIsDisplayed()

        // Verify error mapping for additional Firebase error codes
        val notAllowed = AuthValidator.mapAuthErrorCode("ERROR_OPERATION_NOT_ALLOWED")
        assertTrue(notAllowed.messageHi.contains("Email/Password"))
        val userNotFound = AuthValidator.mapAuthErrorCode("ERROR_USER_NOT_FOUND")
        assertTrue(userNotFound.messageHi.contains("कोई खाता नहीं मिला"))
    }

    @Test
    fun `5 forgot password validates email handles failure and shows Hindi success banner`() {
        var shouldFail = true
        val fakeGateway = FakeEmailAuthGateway(
            onReset = { email ->
                if (shouldFail) {
                    AuthValidator.mapAuthErrorCode("ERROR_USER_NOT_FOUND")
                } else {
                    AuthActionResult.Success(
                        messageHi = "पासवर्ड रीसेट लिंक आपके Gmail ($email) पर भेज दिया गया है।",
                        messageEn = "Password reset link sent to $email."
                    )
                }
            }
        )

        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = LoginPortal.PUBLIC,
                    onSelectPortal = {},
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    emailAuthGateway = fakeGateway,
                    onAuthSuccess = {}
                )
            }
        }

        // Open Forgot Password dialog
        composeRule.onNodeWithTag("forgot_password_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("forgot_password_dialog").assertIsDisplayed()

        // 1. Invalid email -> inline validation error, 0 calls
        composeRule.onNodeWithTag("forgot_password_email_input").performTextInput("bad-email")
        composeRule.onNodeWithTag("forgot_password_send_button").performClick()
        composeRule.waitForIdle()
        assertEquals(0, fakeGateway.resetCalls)
        composeRule.onNodeWithTag("forgot_password_error").assertIsDisplayed()

        // 2. Valid email but Firebase returns user-not-found -> dialog displays error
        composeRule.onNodeWithTag("forgot_password_email_input").performTextReplacement("unknown@gmail.com")
        composeRule.onNodeWithTag("forgot_password_send_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, fakeGateway.resetCalls)
        composeRule.onNodeWithTag("forgot_password_error").assertIsDisplayed()

        // 3. Valid registered email -> dialog closes and success banner displays on AuthScreen
        shouldFail = false
        composeRule.onNodeWithTag("forgot_password_email_input").performTextReplacement("bhakt@gmail.com")
        composeRule.onNodeWithTag("forgot_password_send_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, fakeGateway.resetCalls)
        composeRule.onNodeWithTag("forgot_password_dialog").assertDoesNotExist()
        composeRule.onNodeWithTag("auth_success_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("पासवर्ड रीसेट लिंक आपके Gmail (bhakt@gmail.com) पर भेज दिया गया है।")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `6 login routes to role dashboard and logout followed by re-login works cleanly without blank screen`() {
        var authState by mutableStateOf<AuthNavigationState>(AuthNavigationState.Unauthenticated())
        var activePortal by mutableStateOf(LoginPortal.PUBLIC)
        lateinit var controller: AuthNavigationController

        val fakeGateway = FakeEmailAuthGateway(
            onSignIn = { email, _ ->
                val session = when {
                    email.startsWith("admin") -> UserSession(
                        uid = "uid_admin",
                        email = email,
                        displayName = "प्रशासक",
                        role = UserRole.ADMIN,
                        staffActive = true
                    )
                    email.startsWith("staff") -> UserSession(
                        uid = "uid_staff",
                        email = email,
                        displayName = "कर्मचारी",
                        role = UserRole.STAFF,
                        staffActive = true
                    )
                    else -> UserSession(
                        uid = "uid_public",
                        email = email,
                        displayName = "गौ भक्त",
                        role = UserRole.PUBLIC
                    )
                }
                authState = AuthNavigationState.Authorized(session)
                AuthActionResult.Success()
            }
        )

        composeRule.setContent {
            GosalaTheme {
                controller = rememberAuthNavigationController()
                GosalaRootNavHost(
                    authState = authState,
                    authNavController = controller,
                    loadingContent = { Text("Loading", modifier = Modifier.testTag("root_loading")) },
                    loginContent = { err, note ->
                        AuthScreen(
                            language = AppLanguage.HINDI,
                            onToggleLanguage = {},
                            selectedPortal = activePortal,
                            onSelectPortal = { activePortal = it },
                            sessionError = err,
                            rejectedAccountNote = note,
                            skipAutoSignIn = true,
                            isResolvingSession = false,
                            emailAuthGateway = fakeGateway,
                            onAuthSuccess = {}
                        )
                    },
                    sessionErrorContent = { Text(it, modifier = Modifier.testTag("root_error")) },
                    adminDashboardContent = { Text("Admin:${it.email}", modifier = Modifier.testTag("dash_admin")) },
                    staffDashboardContent = { Text("Staff:${it.email}", modifier = Modifier.testTag("dash_staff")) },
                    publicDashboardContent = { Text("Public:${it.email}", modifier = Modifier.testTag("dash_public")) }
                )
            }
        }

        // Step A: Starts on Login Screen
        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)

        // Step B: Public Login -> opens Public Dashboard
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("user@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("pass123")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(Routes.PUBLIC_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("dash_public").assertIsDisplayed()

        // Step C: Logout -> returns cleanly to Login Screen
        authState = AuthNavigationState.Unauthenticated()
        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)
        composeRule.onNodeWithTag("dash_public").assertDoesNotExist()
        composeRule.onNodeWithTag("email_auth_card").performScrollTo().assertIsDisplayed()

        // Step D: Re-login as Staff -> opens Staff Dashboard
        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("staff@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("staff123")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(Routes.STAFF_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("dash_staff").assertIsDisplayed()

        // Step E: Logout & Re-login as Admin -> opens Admin Dashboard
        authState = AuthNavigationState.Unauthenticated()
        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)

        composeRule.onNodeWithTag("auth_email_input").performScrollTo().performTextInput("admin@gmail.com")
        composeRule.onNodeWithTag("auth_password_input").performScrollTo().performTextInput("admin123")
        composeRule.onNodeWithTag("email_auth_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals(Routes.ADMIN_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("dash_admin").assertIsDisplayed()
    }
}

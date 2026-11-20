package com.example.ui.auth

import com.example.core.AppLanguage
import com.example.core.str
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

enum class EmailAuthMode {
    LOGIN,
    SIGNUP
}

sealed interface AuthActionResult {
    data class Success(
        val messageHi: String? = null,
        val messageEn: String? = null
    ) : AuthActionResult

    data class Failure(
        val messageHi: String,
        val messageEn: String,
        val errorCode: String? = null
    ) : AuthActionResult
}

interface EmailAuthGateway {
    suspend fun signUpWithEmail(name: String, email: String, password: String): AuthActionResult
    suspend fun signInWithEmail(email: String, password: String): AuthActionResult
    suspend fun sendPasswordReset(email: String): AuthActionResult
}

data class AuthFormValidation(
    val nameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null
) {
    val isValid: Boolean
        get() = nameError == null && emailError == null && passwordError == null
}

object AuthValidator {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_PASSWORD_LENGTH = 6

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun isValidEmailFormat(raw: String): Boolean {
        val email = normalizeEmail(raw)
        return email.length in 5..120 && !email.contains("..") && EMAIL_REGEX.matches(email)
    }

    fun validateName(name: String, language: AppLanguage): String? {
        val trimmed = name.trim()
        return when {
            trimmed.isBlank() -> str(language, "कृपया अपना पूरा नाम दर्ज करें।", "Please enter your full name.")
            trimmed.length < 2 -> str(language, "नाम कम से कम 2 अक्षरों का होना चाहिए।", "Name must be at least 2 characters.")
            trimmed.length > 100 -> str(language, "नाम 100 अक्षरों से अधिक नहीं हो सकता।", "Name cannot exceed 100 characters.")
            else -> null
        }
    }

    fun validateEmail(email: String, language: AppLanguage): String? {
        val trimmed = email.trim()
        return when {
            trimmed.isBlank() -> str(language, "कृपया अपना ईमेल / Gmail पता दर्ज करें।", "Please enter your email / Gmail address.")
            !isValidEmailFormat(trimmed) -> str(
                language,
                "कृपया सही ईमेल / Gmail पता दर्ज करें (उदा. name@gmail.com)।",
                "Please enter a valid email / Gmail address (e.g. name@gmail.com)."
            )
            else -> null
        }
    }

    fun validatePassword(password: String, language: AppLanguage): String? {
        return when {
            password.isBlank() -> str(language, "कृपया पासवर्ड दर्ज करें।", "Please enter your password.")
            password.length < MIN_PASSWORD_LENGTH -> str(
                language,
                "पासवर्ड कम से कम 6 अक्षरों का होना चाहिए।",
                "Password must be at least 6 characters long."
            )
            else -> null
        }
    }

    fun validateLoginForm(email: String, password: String, language: AppLanguage): AuthFormValidation {
        return AuthFormValidation(
            emailError = validateEmail(email, language),
            passwordError = validatePassword(password, language)
        )
    }

    fun validateSignupForm(
        name: String,
        email: String,
        password: String,
        language: AppLanguage
    ): AuthFormValidation {
        return AuthFormValidation(
            nameError = validateName(name, language),
            emailError = validateEmail(email, language),
            passwordError = validatePassword(password, language)
        )
    }

    fun mapAuthErrorCode(errorCode: String?, fallbackMessage: String? = null): AuthActionResult.Failure {
        val code = errorCode?.uppercase().orEmpty()
        val msg = fallbackMessage.orEmpty()
        return when {
            code == "ERROR_EMAIL_ALREADY_IN_USE" ||
                msg.contains("EMAIL_EXISTS", ignoreCase = true) ||
                msg.contains("already in use", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "यह ईमेल पहले से पंजीकृत है। कृपया लॉगिन करें या दूसरा Gmail पता उपयोग करें।",
                messageEn = "This email is already registered. Please sign in or use another email address.",
                errorCode = "ERROR_EMAIL_ALREADY_IN_USE"
            )

            code == "ERROR_WRONG_PASSWORD" ||
                code == "ERROR_INVALID_CREDENTIAL" ||
                code == "INVALID_LOGIN_CREDENTIALS" ||
                msg.contains("INVALID_LOGIN_CREDENTIALS", ignoreCase = true) ||
                msg.contains("wrong password", ignoreCase = true) ||
                msg.contains("supplied auth credential is incorrect", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "ईमेल या पासवर्ड गलत है। कृपया सही जानकारी दर्ज करें या 'पासवर्ड भूल गए?' का उपयोग करें।",
                messageEn = "Incorrect email or password. Please verify your credentials or use Forgot Password.",
                errorCode = code.ifBlank { "ERROR_WRONG_PASSWORD" }
            )

            code == "ERROR_USER_NOT_FOUND" ||
                msg.contains("USER_NOT_FOUND", ignoreCase = true) ||
                msg.contains("no user record", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "इस ईमेल से कोई खाता नहीं मिला। कृपया नया अकाउंट (Signup) बनाएं।",
                messageEn = "No account found with this email. Please create a new account (Sign Up).",
                errorCode = "ERROR_USER_NOT_FOUND"
            )

            code == "ERROR_INVALID_EMAIL" -> AuthActionResult.Failure(
                messageHi = "कृपया मान्य ईमेल / Gmail पता दर्ज करें।",
                messageEn = "Please enter a valid email / Gmail address.",
                errorCode = "ERROR_INVALID_EMAIL"
            )

            code == "ERROR_WEAK_PASSWORD" -> AuthActionResult.Failure(
                messageHi = "पासवर्ड कमजोर है। कृपया कम से कम 6 अक्षरों का सुरक्षित पासवर्ड चुनें।",
                messageEn = "Weak password. Please choose a stronger password with at least 6 characters.",
                errorCode = "ERROR_WEAK_PASSWORD"
            )

            code == "ERROR_USER_DISABLED" -> AuthActionResult.Failure(
                messageHi = "यह खाता निष्क्रिय कर दिया गया है। कृपया गौशाला प्रशासक से संपर्क करें।",
                messageEn = "This account has been disabled. Please contact the Goshala Administrator.",
                errorCode = "ERROR_USER_DISABLED"
            )

            code == "ERROR_TOO_MANY_REQUESTS" ||
                msg.contains("TOO_MANY_ATTEMPTS", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "बहुत अधिक बार प्रयास किया गया है। कृपया कुछ मिनट बाद पुनः प्रयास करें।",
                messageEn = "Too many attempts. Please wait a few minutes and try again.",
                errorCode = "ERROR_TOO_MANY_REQUESTS"
            )

            code == "ERROR_OPERATION_NOT_ALLOWED" ||
                msg.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "Firebase में Email/Password लॉगिन चालू नहीं है। कृपया Firebase Console → Authentication → Sign-in method में Email/Password सक्षम करें।",
                messageEn = "Email/Password sign-in is disabled in Firebase Console. Enable Email/Password under Authentication → Sign-in method.",
                errorCode = "ERROR_OPERATION_NOT_ALLOWED"
            )

            code == "ERROR_NETWORK_REQUEST_FAILED" ||
                msg.contains("network", ignoreCase = true) ||
                msg.contains("timeout", ignoreCase = true) -> AuthActionResult.Failure(
                messageHi = "इंटरनेट कनेक्शन नहीं मिल रहा है। कृपया नेटवर्क जांचकर पुनः प्रयास करें।",
                messageEn = "Network error. Please check your internet connection and try again.",
                errorCode = "ERROR_NETWORK_REQUEST_FAILED"
            )

            else -> AuthActionResult.Failure(
                messageHi = "लॉगिन/साइनअप में समस्या आई। कृपया जानकारी जांचकर पुनः प्रयास करें।",
                messageEn = fallbackMessage?.takeIf { it.isNotBlank() } ?: "Authentication failed. Please try again.",
                errorCode = code.ifBlank { null }
            )
        }
    }

    fun mapFirebaseAuthException(exception: Throwable): AuthActionResult.Failure {
        return when (exception) {
            is FirebaseNetworkException -> mapAuthErrorCode("ERROR_NETWORK_REQUEST_FAILED", exception.message)
            is FirebaseAuthUserCollisionException -> mapAuthErrorCode("ERROR_EMAIL_ALREADY_IN_USE", exception.message)
            is FirebaseAuthWeakPasswordException -> mapAuthErrorCode("ERROR_WEAK_PASSWORD", exception.message)
            is FirebaseAuthInvalidUserException -> mapAuthErrorCode(exception.errorCode, exception.message)
            is FirebaseAuthInvalidCredentialsException -> mapAuthErrorCode(exception.errorCode, exception.message)
            is FirebaseAuthException -> mapAuthErrorCode(exception.errorCode, exception.message)
            else -> mapAuthErrorCode(null, exception.localizedMessage ?: exception.message)
        }
    }
}

package com.example.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.str
import com.example.data.model.LoginPortal
import com.example.ui.theme.ForestGreen
import kotlinx.coroutines.launch

@Composable
fun EmailAuthCard(
    language: AppLanguage,
    selectedPortal: LoginPortal,
    isExternalBusy: Boolean,
    emailAuthGateway: EmailAuthGateway?,
    onAuthAttemptStart: (LoginPortal?) -> Unit,
    onAuthSuccess: () -> Unit,
    onAuthError: (String?) -> Unit,
    onSuccessBanner: (String?) -> Unit,
    onBusyChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var mode by remember { mutableStateOf(EmailAuthMode.LOGIN) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }

    val isBusy = isSubmitting || isExternalBusy

    fun switchMode(target: EmailAuthMode) {
        if (isBusy || mode == target) return
        mode = target
        nameError = null
        emailError = null
        passwordError = null
        onAuthError(null)
        onSuccessBanner(null)
    }

    fun submitForm() {
        // Strict single-request guard: prevent double clicks / duplicate Firebase requests
        if (isBusy) return
        focusManager.clearFocus()
        onAuthError(null)
        onSuccessBanner(null)

        val validation = if (mode == EmailAuthMode.SIGNUP) {
            AuthValidator.validateSignupForm(fullName, email, password, language)
        } else {
            AuthValidator.validateLoginForm(email, password, language)
        }
        nameError = validation.nameError
        emailError = validation.emailError
        passwordError = validation.passwordError
        if (!validation.isValid) return

        val gateway = emailAuthGateway
        if (gateway == null) {
            onAuthError(
                str(
                    language,
                    "Firebase Authentication तैयार हो रहा है। कृपया एक क्षण बाद पुनः प्रयास करें।",
                    "Firebase Authentication is initializing. Please try again in a moment."
                )
            )
            return
        }

        isSubmitting = true
        onBusyChange(true)
        // New signups always start as PUBLIC (server rules forbid self-escalation to Admin)
        val portalForAttempt = if (mode == EmailAuthMode.SIGNUP) LoginPortal.PUBLIC else selectedPortal
        onAuthAttemptStart(portalForAttempt)

        scope.launch {
            val result = try {
                if (mode == EmailAuthMode.SIGNUP) {
                    gateway.signUpWithEmail(fullName.trim(), email.trim(), password)
                } else {
                    gateway.signInWithEmail(email.trim(), password)
                }
            } finally {
                isSubmitting = false
                onBusyChange(false)
            }

            when (result) {
                is AuthActionResult.Success -> {
                    onAuthError(null)
                    onAuthSuccess()
                }
                is AuthActionResult.Failure -> {
                    onAuthAttemptStart(null)
                    onAuthError(str(language, result.messageHi, result.messageEn))
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 540.dp)
            .testTag("email_auth_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Login / Signup Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isLogin = mode == EmailAuthMode.LOGIN
                FilledTonalButton(
                    onClick = { switchMode(EmailAuthMode.LOGIN) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isLogin) ForestGreen else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isLogin) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("auth_mode_login")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str(language, "लॉगिन", "Login"),
                        fontWeight = FontWeight.Bold
                    )
                }

                FilledTonalButton(
                    onClick = { switchMode(EmailAuthMode.SIGNUP) },
                    enabled = !isBusy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (!isLogin) ForestGreen else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!isLogin) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("auth_mode_signup")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str(language, "नया अकाउंट", "Sign Up"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = if (mode == EmailAuthMode.LOGIN) {
                    str(language, "Gmail / ईमेल और पासवर्ड से लॉगिन करें", "Sign in with Email / Gmail & Password")
                } else {
                    str(language, "Gmail / ईमेल से नया खाता बनाएं", "Create a new account with Email / Gmail")
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (mode == EmailAuthMode.SIGNUP) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (nameError != null) nameError = null
                    },
                    enabled = !isBusy,
                    singleLine = true,
                    isError = nameError != null,
                    label = { Text(str(language, "पूरा नाम", "Full Name")) },
                    placeholder = { Text(str(language, "उदा. राम शर्मा", "e.g. Ram Sharma")) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    supportingText = nameError?.let { err ->
                        { Text(err, modifier = Modifier.testTag("auth_name_error")) }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_name_input")
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (emailError != null) emailError = null
                },
                enabled = !isBusy,
                singleLine = true,
                isError = emailError != null,
                label = { Text(str(language, "ईमेल / Gmail पता", "Email / Gmail Address")) },
                placeholder = { Text("example@gmail.com") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                supportingText = emailError?.let { err ->
                    { Text(err, modifier = Modifier.testTag("auth_email_error")) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_email_input")
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    if (passwordError != null) passwordError = null
                },
                enabled = !isBusy,
                singleLine = true,
                isError = passwordError != null,
                label = {
                    Text(
                        if (mode == EmailAuthMode.SIGNUP) {
                            str(language, "पासवर्ड (कम से कम 6 अक्षर)", "Password (min 6 characters)")
                        } else {
                            str(language, "पासवर्ड", "Password")
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("auth_password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = str(
                                language,
                                if (passwordVisible) "पासवर्ड छिपाएं" else "पासवर्ड दिखाएं",
                                if (passwordVisible) "Hide password" else "Show password"
                            )
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = passwordError?.let { err ->
                    { Text(err, modifier = Modifier.testTag("auth_password_error")) }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { submitForm() }),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("auth_password_input")
            )

            if (mode == EmailAuthMode.LOGIN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { showForgotDialog = true },
                        enabled = !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("forgot_password_button")
                    ) {
                        Text(
                            text = str(language, "पासवर्ड भूल गए?", "Forgot Password?"),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                Text(
                    text = str(
                        language,
                        "नोट: नया खाता सार्वजनिक उपयोगकर्ता (Public User) के रूप में बनता है। Admin/Staff अधिकार केवल स्वीकृत खातों को ही मिलते हैं।",
                        "Note: New accounts are created as Public Users. Admin/Staff permissions are granted only to authorized accounts."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { submitForm() },
                enabled = !isBusy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("email_auth_submit_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (mode == EmailAuthMode.SIGNUP) {
                            str(language, "खाता बन रहा है...", "Creating account...")
                        } else {
                            str(language, "लॉगिन हो रहा है...", "Signing in...")
                        },
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = if (mode == EmailAuthMode.SIGNUP) Icons.Default.PersonAdd else Icons.AutoMirrored.Filled.Login,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (mode == EmailAuthMode.SIGNUP) {
                            str(language, "नया अकाउंट बनाएं", "Create Account")
                        } else {
                            str(language, "ईमेल से लॉगिन करें", "Sign In with Email")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        switchMode(if (mode == EmailAuthMode.LOGIN) EmailAuthMode.SIGNUP else EmailAuthMode.LOGIN)
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("auth_mode_switch_link")
                ) {
                    Text(
                        text = if (mode == EmailAuthMode.LOGIN) {
                            str(language, "खाता नहीं है? नया अकाउंट बनाएं", "Don't have an account? Sign Up")
                        } else {
                            str(language, "पहले से खाता है? लॉगिन करें", "Already have an account? Sign In")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            language = language,
            initialEmail = email,
            emailAuthGateway = emailAuthGateway,
            onDismiss = { showForgotDialog = false },
            onResetEmailSent = { successMsg ->
                showForgotDialog = false
                onAuthError(null)
                onSuccessBanner(successMsg)
            }
        )
    }
}

@Composable
private fun ForgotPasswordDialog(
    language: AppLanguage,
    initialEmail: String,
    emailAuthGateway: EmailAuthGateway?,
    onDismiss: () -> Unit,
    onResetEmailSent: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var resetEmail by remember(initialEmail) { mutableStateOf(initialEmail) }
    var dialogError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    fun sendReset() {
        if (isSending) return
        val emailValidationError = AuthValidator.validateEmail(resetEmail, language)
        if (emailValidationError != null) {
            dialogError = emailValidationError
            return
        }
        val gateway = emailAuthGateway
        if (gateway == null) {
            dialogError = str(
                language,
                "Firebase तैयार हो रहा है। कृपया थोड़ी देर बाद प्रयास करें।",
                "Firebase is initializing. Please retry in a moment."
            )
            return
        }
        dialogError = null
        isSending = true
        scope.launch {
            val result = try {
                gateway.sendPasswordReset(resetEmail.trim())
            } finally {
                isSending = false
            }
            when (result) {
                is AuthActionResult.Success -> {
                    val msg = str(
                        language,
                        result.messageHi ?: "पासवर्ड रीसेट ईमेल भेज दिया गया है।",
                        result.messageEn ?: "Password reset email sent."
                    )
                    onResetEmailSent(msg)
                }
                is AuthActionResult.Failure -> {
                    dialogError = str(language, result.messageHi, result.messageEn)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        modifier = Modifier.testTag("forgot_password_dialog"),
        icon = { Icon(Icons.Default.LockReset, contentDescription = null, tint = ForestGreen) },
        title = {
            Text(
                text = str(language, "पासवर्ड रीसेट करें", "Reset Password"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = str(
                        language,
                        "अपना पंजीकृत Gmail / ईमेल पता दर्ज करें। हम आपको पासवर्ड बदलने का सुरक्षित लिंक भेजेंगे।",
                        "Enter your registered Gmail / email address. We will send you a secure password reset link."
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = resetEmail,
                    onValueChange = {
                        resetEmail = it
                        if (dialogError != null) dialogError = null
                    },
                    enabled = !isSending,
                    singleLine = true,
                    isError = dialogError != null,
                    label = { Text(str(language, "पंजीकृत ईमेल / Gmail", "Registered Email / Gmail")) },
                    placeholder = { Text("example@gmail.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { sendReset() }),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("forgot_password_email_input")
                )
                if (!dialogError.isNullOrBlank()) {
                    Text(
                        text = dialogError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("forgot_password_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { sendReset() },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("forgot_password_send_button")
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(str(language, "रीसेट लिंक भेजें", "Send Reset Link"))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSending,
                modifier = Modifier.testTag("forgot_password_cancel_button")
            ) {
                Text(str(language, "रद्द करें", "Cancel"))
            }
        }
    )
}

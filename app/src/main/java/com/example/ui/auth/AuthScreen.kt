package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.str
import com.example.data.model.LoginPortal
import com.example.ui.components.GosalaAsyncResourceImage
import com.example.ui.components.GosalaOfficialLogo
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.Golden
import com.example.ui.theme.Saffron
import com.example.ui.theme.SoftGoldTint
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "Auth"

@Composable
fun AuthScreen(
    language: AppLanguage,
    onToggleLanguage: () -> Unit,
    selectedPortal: LoginPortal,
    onSelectPortal: (LoginPortal) -> Unit,
    sessionError: String?,
    rejectedAccountNote: String?,
    skipAutoSignIn: Boolean,
    isResolvingSession: Boolean,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(skipAutoSignIn) {
        if (!skipAutoSignIn && selectedPortal == LoginPortal.PUBLIC && sessionError.isNullOrBlank()) {
            delay(550)
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = onAuthSuccess,
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Language Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledTonalButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("auth_language_toggle")
                ) {
                    Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (language == AppLanguage.HINDI) "English" else "हिंदी")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Devotional Hero Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(215.dp)) {
                    GosalaAsyncResourceImage(
                        resId = R.drawable.img_hero_goshala,
                        contentDescription = str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                        contentScale = ContentScale.Crop,
                        placeholderColor = ForestGreen,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ForestGreen.copy(alpha = 0.45f),
                                        ForestGreen.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GosalaOfficialLogo(
                                size = 60.dp,
                                innerPadding = 2.dp,
                                borderWidth = 2.dp,
                                testTag = "auth_official_logo"
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = str(language, GosalaBrand.SUPPORTING_SLOGAN_HI, GosalaBrand.SUPPORTING_SLOGAN_EN),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Golden
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = str(language, GosalaBrand.MAIN_SLOGAN_HI_1, "Grace of Mahadev, Service to Gaumata"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = str(language, GosalaBrand.MAIN_SLOGAN_HI_2, GosalaBrand.MAIN_SLOGAN_EN),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = str(language, GosalaBrand.ADDRESS_HI, GosalaBrand.ADDRESS_EN),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // 3-Role Login Portal Selector (Intent only — server verifies role from Firestore)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = str(language, "प्रवेश पोर्टल चुनें (केवल 3 भूमिकाएँ)", "Select Login Portal (3 Roles Only)"),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple(LoginPortal.PUBLIC, str(language, "सार्वजनिक", "Public"), Icons.Default.VolunteerActivism),
                            Triple(LoginPortal.STAFF, str(language, "कर्मचारी", "Staff"), Icons.Default.Badge),
                            Triple(LoginPortal.ADMIN, str(language, "प्रशासक", "Admin"), Icons.Default.AdminPanelSettings)
                        ).forEach { (portal, label, icon) ->
                            val selected = selectedPortal == portal
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    authError = null
                                    onSelectPortal(portal)
                                },
                                leadingIcon = {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("portal_chip_${portal.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    when (selectedPortal) {
                        LoginPortal.ADMIN -> RoleInfoRow(
                            icon = Icons.Default.AdminPanelSettings,
                            tint = ForestGreen,
                            title = str(language, "प्रशासक लॉगिन (Admin Verification)", "Admin Login (Verified UID Required)"),
                            desc = str(
                                language,
                                "केवल Firebase द्वारा सत्यापित अधिकृत Admin खाता ही प्रशासक डैशबोर्ड खोल सकता है।",
                                "Only a Firebase-authorized Admin account may access the Admin Dashboard."
                            )
                        )
                        LoginPortal.STAFF -> RoleInfoRow(
                            icon = Icons.Default.Badge,
                            tint = Saffron,
                            title = str(language, "कर्मचारी लॉगिन (Staff Access)", "Staff Login (Admin-Authorized)"),
                            desc = str(
                                language,
                                "केवल प्रशासक द्वारा स्वीकृत कर्मचारी खाते ही अपने निर्धारित मॉड्यूल देख सकते हैं।",
                                "Only admin-invited staff accounts can access assigned goshala modules."
                            )
                        )
                        LoginPortal.PUBLIC -> RoleInfoRow(
                            icon = Icons.Default.VolunteerActivism,
                            tint = MaterialTheme.colorScheme.secondary,
                            title = str(language, "सार्वजनिक उपयोगकर्ता (Public User)", "Public User"),
                            desc = str(
                                language,
                                "गौशाला दर्शन, दान रसीद एवं गौ-गोद (प्रायोजन) आवेदन हेतु साइन इन करें।",
                                "Sign in to view approved cattle, donate, and apply for cow sponsorship."
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val displayedError = authError ?: sessionError
            if (!displayedError.isNullOrBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp)
                        .testTag("auth_error_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = displayedError,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        if (!rejectedAccountNote.isNullOrBlank()) {
                            Text(
                                text = rejectedAccountNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            val buttonLabel = when (selectedPortal) {
                LoginPortal.ADMIN -> str(language, "Admin Google खाते से साइन इन करें", "Sign in as Admin with Google")
                LoginPortal.STAFF -> str(language, "Staff Google खाते से साइन इन करें", "Sign in as Staff with Google")
                LoginPortal.PUBLIC -> str(language, "Google से साइन इन करें", "Sign in with Google")
            }

            Button(
                onClick = {
                    authError = null
                    isLoading = true
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            authError = msg
                        },
                        scope = scope,
                        onAuthCancelled = {
                            isLoading = false
                        }
                    )
                },
                enabled = !isLoading && !isResolvingSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedPortal == LoginPortal.ADMIN) ForestGreen else MaterialTheme.colorScheme.primary
                )
            ) {
                if (isLoading || isResolvingSession) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(str(language, "सत्यापन हो रहा है...", "Verifying account..."))
                } else {
                    Icon(
                        imageVector = if (selectedPortal == LoginPortal.ADMIN) Icons.Default.AdminPanelSettings else Icons.AutoMirrored.Filled.Login,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = buttonLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RoleInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.14f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope
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
            val cred = credentialManager.getCredential(context, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    scope.launch {
        try {
            val activityContext = context as? Activity ?: context
            val cred = credentialManager.getCredential(activityContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onAuthError("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

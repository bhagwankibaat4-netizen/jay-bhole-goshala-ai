package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.str
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.data.repository.AdminAiDiagnosticResult
import com.example.ui.theme.AlertRed
import com.example.ui.theme.DarkText
import com.example.ui.theme.ForestGreen
import com.example.ui.theme.Golden
import com.example.ui.theme.GoshalaGreen
import com.example.ui.theme.Saffron
import com.example.ui.theme.SoftGoldTint
import com.example.ui.theme.SoftGreenTint
import com.example.ui.theme.SoftRedTint
import com.example.ui.viewmodel.AdminAiDiagnosticUiState

/**
 * Strictly Admin-only diagnostic panel inside AdminDashboardScreen.
 * Triggers the real server-side `testGeminiConnection` Cloud Function with the signed-in Admin's
 * Firebase ID Token and Firebase App Check Token, displaying the actual HTTP Status, Gemini reply,
 * and exact error root cause without ever fabricating success or leaking tokens/keys.
 */
@Composable
fun AdminAiDiagnosticCard(
    language: AppLanguage,
    session: UserSession,
    diagnosticState: AdminAiDiagnosticUiState,
    onRunTest: () -> Unit,
    onClearResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Hard UI guard: never render for non-Admin roles (server also enforces Admin auth independently)
    if (session.role != UserRole.ADMIN) return

    val isLoading = diagnosticState is AdminAiDiagnosticUiState.Loading
    val report = (diagnosticState as? AdminAiDiagnosticUiState.Result)?.report

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, Golden.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
            .testTag("admin_ai_diagnostic_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftGreenTint,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NetworkCheck,
                        contentDescription = str(language, "AI सर्वर जांच", "AI Server Diagnostics"),
                        tint = ForestGreen,
                        modifier = Modifier.padding(9.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(language, "AI बैकएंड कनेक्शन जाँच (केवल Admin)", "AI Backend Connection Test (Admin Only)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = str(
                            language,
                            "Admin ID Token + App Check + सर्वर Gemini API का वास्तविक परीक्षण",
                            "Live check of Admin ID Token + App Check + Server Gemini API"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (report != null && !isLoading) {
                    IconButton(
                        onClick = onClearResult,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("admin_clear_ai_test_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = str(language, "परिणाम बंद करें", "Dismiss Result"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onRunTest,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("admin_test_ai_connection_btn"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ForestGreen,
                    contentColor = Color.White
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Golden
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = str(language, "Gemini सर्वर की जाँच हो रही है...", "Testing Gemini Connection..."),
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = Golden,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str(language, "Test AI Connection (AI जाँच चलाएँ)", "Test AI Connection"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AnimatedVisibility(visible = report != null) {
                report?.let { result ->
                    AdminDiagnosticResultPanel(
                        language = language,
                        result = result
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminDiagnosticResultPanel(
    language: AppLanguage,
    result: AdminAiDiagnosticResult
) {
    val isPass = result.testPassed && result.geminiWorking
    val containerColor = if (isPass) SoftGreenTint else SoftRedTint
    val accentColor = if (isPass) GoshalaGreen else AlertRed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .testTag("admin_ai_diagnostic_result"),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Verdict Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isPass) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = if (isPass) "PASS" else "FAIL",
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = result.verdict,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    modifier = Modifier.weight(1f)
                )
            }

            // Status Badges: Actual HTTP Status, App Check, Latency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DiagnosticMetaBadge(
                    label = if (result.httpStatus > 0) "HTTP ${result.httpStatus}" else "NETWORK ERROR",
                    color = accentColor,
                    modifier = Modifier.testTag("admin_ai_http_status_badge")
                )
                DiagnosticMetaBadge(
                    label = "AppCheck: ${result.appCheckStatus}",
                    color = if (result.appCheckValid) ForestGreen else Saffron,
                    modifier = Modifier.testTag("admin_ai_appcheck_badge")
                )
                if (result.latencyMs > 0) {
                    DiagnosticMetaBadge(
                        label = "${result.latencyMs} ms",
                        color = ForestGreen
                    )
                }
            }

            if (result.model.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = str(language, "मॉडल: ${result.model} (${result.backendChannel})", "Model: ${result.model} (${result.backendChannel})"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkText
                    )
                }
            }

            if (result.cloudFunctionSummary.isNotBlank()) {
                Text(
                    text = result.cloudFunctionSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (result.cloudFunctionHttpStatus == 200) ForestGreen else Saffron,
                    modifier = Modifier.testTag("admin_ai_cloud_function_status_text")
                )
            }

            // Real Gemini Reply (Only shown when server actually returned one)
            if (result.replyPreview.isNotBlank()) {
                HorizontalDivider(color = accentColor.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = str(language, "Gemini का वास्तविक उत्तर:", "Live Gemini Reply:"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_ai_reply_text")
                    ) {
                        Text(
                            text = result.replyPreview,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkText,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            // Actual Error Cause & Server Details
            if (!isPass) {
                HorizontalDivider(color = accentColor.copy(alpha = 0.25f))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = str(
                            language,
                            "वास्तविक कारण (${result.rootCause}):",
                            "Actual Error Cause (${result.rootCause}):"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AlertRed
                    )
                    if (result.errorDetails.isNotBlank()) {
                        Text(
                            text = result.errorDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkText,
                            modifier = Modifier.testTag("admin_ai_error_details_text")
                        )
                    }
                    if (result.responseSnippet.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftGoldTint,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = result.responseSnippet,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = DarkText,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetaBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.VetRiskLevel
import com.example.core.VetTriageAnswers
import com.example.core.VetTriageEngine
import com.example.core.str
import com.example.data.model.*
import com.example.data.repository.AiWorkPlan
import com.example.data.repository.VoiceEntryDraft
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.AiWorkPlanUiState
import kotlinx.coroutines.delay

internal fun copyTextToClipboard(context: Context, label: String, text: String): Boolean {
    return runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        clipboard != null
    }.getOrDefault(false)
}

internal fun shareAiText(context: Context, subject: String, text: String): Boolean {
    return runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(sendIntent, subject).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}

internal fun formatWorkPlanForClipboard(plan: AiWorkPlan): String = buildString {
    appendLine("${plan.title} (${plan.dateLabel})")
    if (plan.summary.isNotBlank()) {
        appendLine(plan.summary)
    }
    appendLine()
    plan.tasks.forEachIndexed { idx, item ->
        appendLine("${idx + 1}. [${item.period} | ${item.timeSlot}] ${item.task}")
        appendLine("   जिम्मेदार: ${item.responsiblePerson} | प्राथमिकता: ${item.priority}")
    }
}.trim()

@Composable
internal fun WorkPlanPanel(
    language: AppLanguage,
    workPlanState: AiWorkPlanUiState,
    onGeneratePlan: (focusTopic: String, planHorizon: String) -> Unit
) {
    val context = LocalContext.current
    var selectedHorizon by remember { mutableStateOf("DAILY") }
    var selectedFocus by remember { mutableStateOf("") }
    var copied by remember(workPlanState) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val horizonOptions = remember(language) {
        listOf(
            Triple("DAILY", str(language, "दैनिक", "Daily"), "ai_plan_horizon_daily"),
            Triple("WEEKLY", str(language, "साप्ताहिक", "Weekly"), "ai_plan_horizon_weekly"),
            Triple("MONTHLY", str(language, "मासिक", "Monthly"), "ai_plan_horizon_monthly")
        )
    }

    val focusTopics = remember(language) {
        listOf(
            "" to str(language, "पूर्ण गौशाला योजना", "Full Goshala Plan"),
            "सुबह के कार्य, चारा, पानी और गोशाला सफाई" to str(language, "चारा व सफाई", "Fodder & Cleaning"),
            "गायों का निरीक्षण, बीमार गायों की देखभाल और टीकाकरण" to str(language, "गाय निरीक्षण", "Cow Inspection"),
            "गोबर संग्रह, खाद निर्माण और बिक्री एवं आय के कार्य" to str(language, "खाद व बिक्री", "Compost & Sales"),
            "कर्मचारियों की जिम्मेदारी, खर्च नियंत्रण और शाम का निरीक्षण" to str(language, "स्टाफ व खर्च", "Staff & Cost")
        )
    }

    val generateButtonLabel = when (selectedHorizon) {
        "WEEKLY" -> str(language, "साप्ताहिक AI कार्य योजना बनाएँ", "Generate Weekly AI Work Plan")
        "MONTHLY" -> str(language, "मासिक AI कार्य योजना बनाएँ", "Generate Monthly AI Work Plan")
        else -> str(language, "दैनिक AI कार्य योजना बनाएँ", "Generate Daily AI Work Plan")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_work_plan_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, tint = ForestGreen)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(language, "AI कार्य योजना (दैनिक / साप्ताहिक / मासिक)", "AI Work Plan (Daily / Weekly / Monthly)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = str(
                            language,
                            "सुबह कार्य, चारा-पानी, सफाई, गाय निरीक्षण, गोबर-खाद, स्टाफ जिम्मेदारी व बिक्री सहित",
                            "Covers morning tasks, fodder, cleaning, inspection, dung/compost, staff & sales"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Plan Horizon Selector (Daily / Weekly / Monthly)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                horizonOptions.forEach { (horizonKey, label, tag) ->
                    FilterChip(
                        selected = selectedHorizon == horizonKey,
                        onClick = { selectedHorizon = horizonKey },
                        label = { Text(label, maxLines = 1) },
                        modifier = Modifier.testTag(tag)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                focusTopics.forEach { (topicValue, label) ->
                    FilterChip(
                        selected = selectedFocus == topicValue,
                        onClick = { selectedFocus = topicValue },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }

            Button(
                onClick = { onGeneratePlan(selectedFocus, selectedHorizon) },
                enabled = workPlanState !is AiWorkPlanUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai_generate_work_plan_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = generateButtonLabel,
                    fontWeight = FontWeight.Bold
                )
            }

            when (workPlanState) {
                is AiWorkPlanUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_work_plan_loading")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = str(language, "AI जवाब तैयार कर रहा है...", "AI is preparing the work plan..."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is AiWorkPlanUiState.Error -> {
                    AiDiagnosticErrorCard(
                        language = language,
                        message = workPlanState.message,
                        diagnosticDetail = workPlanState.diagnosticDetail,
                        containerTag = "ai_work_plan_error",
                        retryTag = "ai_work_plan_retry_btn",
                        retryLabel = str(language, "पुनः प्रयास करें", "Retry"),
                        onRetry = { onGeneratePlan(selectedFocus, selectedHorizon) }
                    )
                }
                is AiWorkPlanUiState.Success -> {
                    val plan = workPlanState.plan
                    val formattedPlanText = remember(plan) { formatWorkPlanForClipboard(plan) }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftGreenTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ForestGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .testTag("ai_work_plan_result")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Checklist, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = plan.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreen
                                    )
                                    Text(
                                        text = str(language, "दिनांक: ${plan.dateLabel}", "Date: ${plan.dateLabel}"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = DarkText
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (copyTextToClipboard(context, "AI Work Plan", formattedPlanText)) {
                                            copied = true
                                        }
                                    },
                                    modifier = Modifier.testTag("ai_work_plan_copy_btn")
                                ) {
                                    Icon(
                                        if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = str(language, "कार्य योजना कॉपी करें", "Copy work plan"),
                                        modifier = Modifier.size(16.dp),
                                        tint = ForestGreen
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (copied) str(language, "कॉपी हो गया", "Copied") else str(language, "कॉपी", "Copy"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestGreen
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        shareAiText(context, plan.title, formattedPlanText)
                                    },
                                    modifier = Modifier.testTag("ai_work_plan_share_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = str(language, "कार्य योजना शेयर करें", "Share work plan"),
                                        modifier = Modifier.size(16.dp),
                                        tint = ForestGreen
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = str(language, "शेयर", "Share"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestGreen
                                    )
                                }
                            }

                            if (plan.summary.isNotBlank()) {
                                Text(
                                    text = plan.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkText
                                )
                            }

                            plan.tasks.forEachIndexed { idx, item ->
                                val priorityColor = when {
                                    item.priority.contains("उच्च") || item.priority.equals("HIGH", ignoreCase = true) -> AlertRed
                                    item.priority.contains("मध्यम") || item.priority.equals("MEDIUM", ignoreCase = true) -> Saffron
                                    else -> ForestGreen
                                }
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("ai_work_plan_item_$idx")
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Default.Schedule,
                                                    contentDescription = null,
                                                    tint = ForestGreen,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "${item.period} • ${item.timeSlot}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ForestGreen
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = priorityColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = str(language, "प्राथमिकता: ${item.priority}", "Priority: ${item.priority}"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = priorityColor,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${idx + 1}. ${item.task}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = DarkText
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Saffron,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = str(
                                                    language,
                                                    "जिम्मेदार: ${item.responsiblePerson}",
                                                    "Responsible: ${item.responsiblePerson}"
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color(0xFF4E4E4E)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AiWorkPlanUiState.Idle -> Unit
            }
        }
    }
}

@Composable
internal fun CowContextSelectorCard(
    language: AppLanguage,
    cattle: List<Cattle>,
    health: List<HealthRecord>,
    selectedCow: Cattle?,
    isPublic: Boolean,
    onSelectCow: (Cattle?) -> Unit
) {
    if (cattle.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_cow_selector_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Pets, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = str(
                        language,
                        "गाय रिकॉर्ड चुनें (वैकल्पिक — केवल पढ़ने हेतु)",
                        "Select Cow Record (Optional — Read-only)"
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag("ai_cow_selector_strip"),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCow == null,
                    onClick = { onSelectCow(null) },
                    label = { Text(str(language, "सामान्य (सभी)", "General")) },
                    modifier = Modifier.testTag("ai_select_cow_none")
                )
                cattle.take(20).forEach { cow ->
                    val isSelected = selectedCow?.id == cow.id && selectedCow.animalCode == cow.animalCode
                    val statusTag = if (!cow.healthStatus.equals("HEALTHY", ignoreCase = true)) " • !" else ""
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCow(if (isSelected) null else cow) },
                        label = { Text("${cow.name} (${cow.animalCode})$statusTag", maxLines = 1) },
                        modifier = Modifier.testTag("ai_select_cow_${cow.animalCode}")
                    )
                }
            }
            if (selectedCow != null) {
                val matchedHealthCount = if (isPublic) 0 else health.count { rec ->
                    (selectedCow.id.isNotBlank() && rec.cattleId == selectedCow.id) ||
                        (selectedCow.animalCode.isNotBlank() && rec.animalCode.equals(selectedCow.animalCode, ignoreCase = true))
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftGreenTint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_selected_cow_summary")
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${selectedCow.name} [${selectedCow.animalCode}] • ${selectedCow.breed.ifBlank { "-" }} • ${selectedCow.ageText.ifBlank { "-" }} • ${if (selectedCow.weightKg > 0) "${selectedCow.weightKg}kg" else "-"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Text(
                            text = str(
                                language,
                                "स्वास्थ्य: ${selectedCow.healthStatus} | टीकाकरण: ${selectedCow.vaccinationSummary?.takeIf { it.isNotBlank() } ?: "दर्ज नहीं"}" +
                                    if (!isPublic) " | स्वास्थ्य रिकॉर्ड: $matchedHealthCount" else "",
                                "Health: ${selectedCow.healthStatus} | Vaccination: ${selectedCow.vaccinationSummary?.takeIf { it.isNotBlank() } ?: "Not recorded"}" +
                                    if (!isPublic) " | Health Logs: $matchedHealthCount" else ""
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = DarkText
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PhotoHealthCheckCard(
    language: AppLanguage,
    selectedCow: Cattle?,
    selectedBitmap: Bitmap?,
    notes: String,
    cameraError: String?,
    photoState: AiUiState,
    onNotesChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onCapturePhoto: () -> Unit,
    onClearPhoto: () -> Unit,
    onAnalyze: () -> Unit,
    triageAnswers: VetTriageAnswers = VetTriageAnswers(),
    onTriageAnswersChange: (VetTriageAnswers) -> Unit = {},
    vetPhoneNumber: String = "",
    onSaveVetPhone: (String) -> Unit = {},
    canSaveVetReport: Boolean = false,
    onSaveVetReport: ((AiVetHealthReport) -> Unit)? = null,
    onVoiceSymptomClick: (() -> Unit)? = null,
    session: UserSession? = null
) {
    val context = LocalContext.current
    var reportCopied by remember(photoState) { mutableStateOf(false) }
    var reportSaved by remember(photoState, selectedCow?.id) { mutableStateOf(false) }

    LaunchedEffect(reportCopied) {
        if (reportCopied) {
            delay(2000)
            reportCopied = false
        }
    }

    val liveAssessment = remember(notes, triageAnswers, selectedCow) {
        if (notes.isNotBlank() || triageAnswers.hasAnyAnswer()) {
            VetTriageEngine.evaluate(notes, triageAnswers, selectedCow)
        } else {
            null
        }
    }

    val quickSymptoms = remember(language) {
        listOf(
            str(language, "गाय खड़ी नहीं हो पा रही है", "Cow unable to stand"),
            str(language, "चारा ठीक से नहीं खा रही है", "Not eating fodder properly"),
            str(language, "गाय को बुखार है", "Cow has fever"),
            str(language, "पेट में सूजन / आफरा है", "Swollen abdomen / Bloat"),
            str(language, "सांस लेने में परेशानी है", "Difficulty breathing"),
            str(language, "दस्त / कमजोरी", "Diarrhea / Weak"),
            str(language, "घाव / सूजन", "Wound / Swelling"),
            str(language, "लंगड़ा रही है", "Limping")
        )
    }

    val cowHasSavedPhoto = selectedCow != null &&
        (!selectedCow.photoUri.isNullOrBlank() || !selectedCow.photoStoragePath.isNullOrBlank())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_photo_health_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = ForestGreen)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(language, "एडवांस AI पशु चिकित्सा सहायक", "Advanced AI Veterinary Assistant"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedCow != null) {
                            str(
                                language,
                                "चयनित गाय: ${selectedCow.name} (${selectedCow.animalCode}) — केवल इसी गाय का रिकॉर्ड व फोटो उपयोग होगा",
                                "Selected Cow: ${selectedCow.name} (${selectedCow.animalCode}) — Strictly isolated record & photo"
                            )
                        } else {
                            str(
                                language,
                                "Google Search Grounded (DAHD/ICAR/IVRI/NDDB) • फोटो, लक्षण व वॉइस जाँच",
                                "Google Search Grounded (DAHD/ICAR/IVRI/NDDB) • Photo, symptom & voice triage"
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Immediate Red-Flag Emergency Alert if live typed/selected symptoms indicate danger
            if (liveAssessment?.riskLevel == VetRiskLevel.EMERGENCY && photoState !is AiUiState.Success) {
                EmergencyVetCallBanner(
                    language = language,
                    assessment = liveAssessment,
                    vetPhoneNumber = vetPhoneNumber,
                    onSaveVetPhone = onSaveVetPhone
                )
            }

            // Camera & Gallery Pick Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCapturePhoto,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("camera_cattle_photo_ai_btn")
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "कैमरा", "Camera"), maxLines = 1)
                }
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pick_cattle_photo_ai_btn")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "गैलरी", "Gallery"), maxLines = 1)
                }
            }

            if (selectedBitmap == null && cowHasSavedPhoto) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoftGreenTint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = str(
                                language,
                                "${selectedCow.name} की Firebase में सेव असली फोटो रिपोर्ट में स्वतः उपयोग होगी (या नई फोटो चुनें)।",
                                "${selectedCow.name}'s saved Firebase photo will be used automatically (or upload a fresh photo)."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = ForestGreen
                        )
                    }
                }
            }

            if (!cameraError.isNullOrBlank()) {
                Text(
                    text = cameraError,
                    style = MaterialTheme.typography.labelSmall,
                    color = AlertRed,
                    modifier = Modifier.testTag("ai_camera_error_text")
                )
            }

            if (selectedBitmap != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        bitmap = selectedBitmap.asImageBitmap(),
                        contentDescription = str(language, "चयनित गाय की फोटो", "Selected cow photo"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(185.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .testTag("ai_selected_photo_preview")
                    )
                    FilledTonalIconButton(
                        onClick = onClearPhoto,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .testTag("ai_clear_photo_btn")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = str(language, "फोटो हटाएँ", "Remove photo")
                        )
                    }
                }
            }

            // Quick symptom chips — available WITH OR WITHOUT a photo so caregivers can generate reports anytime
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickSymptoms.forEach { symptom ->
                    AssistChip(
                        onClick = {
                            val next = if (notes.isBlank()) symptom else "$notes, $symptom"
                            onNotesChange(next)
                        },
                        label = { Text(symptom, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = {
                    Text(
                        str(
                            language,
                            "लक्षण लिखें या बोलें (हिंदी / Hinglish — जैसे: चारा नहीं खा रही, बुखार, आफरा)",
                            "Describe or speak symptoms (Hindi / Hinglish — e.g., not eating, fever, bloat)"
                        )
                    )
                },
                trailingIcon = if (onVoiceSymptomClick != null) {
                    {
                        IconButton(
                            onClick = onVoiceSymptomClick,
                            modifier = Modifier.testTag("ai_symptom_mic_btn")
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = str(language, "लक्षण बोलकर दर्ज करें", "Speak symptoms"),
                                tint = Saffron
                            )
                        }
                    }
                } else null,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_photo_notes_input")
            )

            // Interactive 10-question clinical triage checklist
            VetTriageChecklistCard(
                language = language,
                answers = triageAnswers,
                selectedCow = selectedCow,
                onAnswersChange = onTriageAnswersChange
            )

            Button(
                onClick = onAnalyze,
                enabled = (selectedBitmap != null || notes.isNotBlank() || triageAnswers.hasAnyAnswer()) && photoState !is AiUiState.Loading,
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("analyze_cattle_photo_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    str(language, "AI स्वास्थ्य रिपोर्ट तैयार करें", "Generate AI Health Report"),
                    fontWeight = FontWeight.Bold
                )
            }

            // Mandatory Vet Safety Disclaimer Strip
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = SoftSaffronTint,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null, tint = Saffron, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str(
                            language,
                            "केवल फोटो देखकर पक्का निदान नहीं किया जाता। बिना पशु चिकित्सक की जांच के एंटीबायोटिक, इंजेक्शन या दवा की खुराक न दें। आपातकाल में तुरंत पशु चिकित्सक को बुलाएँ।",
                            "A photo alone cannot confirm a diagnosis. Never administer antibiotics, injections, or doses without a veterinarian's exam. Call a vet immediately in emergencies."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText
                    )
                }
            }

            // Loading / Success / Error States for Veterinary Report
            when (photoState) {
                is AiUiState.Loading -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_photo_loading")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                str(language, "AI पशु चिकित्सा रिपोर्ट व विश्वसनीय स्रोत तैयार कर रहा है...", "AI is preparing veterinary report & grounded sources..."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is AiUiState.Success -> {
                    val reportRisk = photoState.riskLevel ?: liveAssessment?.riskLevel
                    val effectiveAssessment = photoState.assessment ?: liveAssessment
                    if (reportRisk == VetRiskLevel.EMERGENCY && effectiveAssessment != null) {
                        EmergencyVetCallBanner(
                            language = language,
                            assessment = effectiveAssessment.copy(riskLevel = VetRiskLevel.EMERGENCY),
                            vetPhoneNumber = vetPhoneNumber,
                            onSaveVetPhone = onSaveVetPhone
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (reportRisk == VetRiskLevel.EMERGENCY) Color(0xFFFFF8F6) else SoftGreenTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.2.dp,
                                if (reportRisk == VetRiskLevel.EMERGENCY) AlertRed.copy(alpha = 0.55f) else ForestGreen.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp)
                            )
                            .testTag("ai_photo_success_report")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (reportRisk == VetRiskLevel.EMERGENCY) Icons.Default.Emergency else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (reportRisk == VetRiskLevel.EMERGENCY) AlertRed else ForestGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = str(language, "AI पशु चिकित्सा स्वास्थ्य रिपोर्ट तैयार", "AI Veterinary Health Report Ready"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (reportRisk == VetRiskLevel.EMERGENCY) AlertRed else ForestGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (reportRisk != null) {
                                VetRiskBadge(riskLevel = reportRisk, language = language)
                            }
                            if (photoState.usedFallbackModel && photoState.modelUsed.isNotBlank()) {
                                Text(
                                    text = str(
                                        language,
                                        "बैकअप मॉडल सक्रिय: ${photoState.modelUsed}",
                                        "Fallback Model Active: ${photoState.modelUsed}"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Saffron,
                                    modifier = Modifier.testTag("ai_fallback_model_badge")
                                )
                            }
                            if (photoState.usedCowRecordPhoto) {
                                Text(
                                    text = str(
                                        language,
                                        "चयनित गाय की सेव Firebase फोटो का विश्लेषण शामिल है।",
                                        "Included visual analysis of selected cow's saved Firebase photo."
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestGreen
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        if (copyTextToClipboard(context, "AI Veterinary Health Report", photoState.text)) {
                                            reportCopied = true
                                        }
                                    },
                                    modifier = Modifier.testTag("ai_photo_copy_btn")
                                ) {
                                    Icon(
                                        if (reportCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = str(language, "रिपोर्ट कॉपी करें", "Copy report"),
                                        modifier = Modifier.size(16.dp),
                                        tint = ForestGreen
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (reportCopied) str(language, "कॉपी हो गया", "Copied") else str(language, "कॉपी", "Copy"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestGreen
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        shareAiText(
                                            context,
                                            str(language, "AI गाय स्वास्थ्य रिपोर्ट", "AI Cow Health Report"),
                                            photoState.text
                                        )
                                    },
                                    modifier = Modifier.testTag("ai_photo_share_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = str(language, "रिपोर्ट शेयर करें", "Share report"),
                                        modifier = Modifier.size(16.dp),
                                        tint = ForestGreen
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = str(language, "शेयर", "Share"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ForestGreen
                                    )
                                }
                            }
                            Text(
                                text = photoState.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkText
                            )
                            VetSourcesCard(
                                language = language,
                                sources = photoState.sources,
                                usedGoogleSearch = photoState.usedGoogleSearch
                            )
                            if (canSaveVetReport && selectedCow != null && onSaveVetReport != null) {
                                Button(
                                    onClick = {
                                        val reportToSave = AiVetHealthReport(
                                            cattleId = selectedCow.id.ifBlank { selectedCow.animalCode },
                                            animalCode = selectedCow.animalCode,
                                            cattleName = selectedCow.name,
                                            symptoms = notes.trim().ifBlank {
                                                str(language, "फोटो व लक्षण निरीक्षण", "Photo & symptom inspection")
                                            },
                                            triageSummary = triageAnswers.toReadableSummary(language),
                                            riskLevel = (reportRisk ?: VetRiskLevel.CAUTION).name,
                                            reportText = photoState.text,
                                            sourceLinks = photoState.sources.map { it.serialize() },
                                            usedPhoto = selectedBitmap != null || photoState.usedCowRecordPhoto,
                                            dateStr = todayIsoDateString(),
                                            recordedBy = session?.uid.orEmpty(),
                                            recordedByName = session?.displayName.orEmpty()
                                        )
                                        onSaveVetReport(reportToSave)
                                        reportSaved = true
                                    },
                                    enabled = !reportSaved,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (reportSaved) ForestGreen.copy(alpha = 0.6f) else Saffron
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("ai_save_vet_report_btn")
                                ) {
                                    Icon(
                                        if (reportSaved) Icons.Default.CheckCircle else Icons.Default.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (reportSaved) {
                                            str(language, "रिपोर्ट गाय के रिकॉर्ड में सेव हो गई", "Report Saved to Cow Record")
                                        } else {
                                            str(language, "रिपोर्ट गाय के रिकॉर्ड में सेव करें", "Save Report to Cow Record")
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                is AiUiState.Error -> {
                    AiDiagnosticErrorCard(
                        language = language,
                        message = photoState.message,
                        diagnosticDetail = photoState.diagnosticDetail,
                        containerTag = "ai_photo_error",
                        retryTag = if (selectedBitmap != null || notes.isNotBlank() || triageAnswers.hasAnyAnswer()) "ai_photo_retry_btn" else null,
                        retryLabel = str(language, "पुनः जाँच करें", "Retry Analysis"),
                        onRetry = if (selectedBitmap != null || notes.isNotBlank() || triageAnswers.hasAnyAnswer()) onAnalyze else null
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
internal fun VoiceDraftPanel(
    language: AppLanguage,
    session: UserSession,
    voiceInputText: String,
    onVoiceTextChange: (String) -> Unit,
    speechError: String?,
    isLoading: Boolean,
    draft: VoiceEntryDraft?,
    onMicClick: () -> Unit,
    onGenerateDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onConfirmDraft: (VoiceEntryDraft) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = Saffron)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = str(language, "हिंदी वॉइस एंट्री सहायक (ड्राफ्ट मोड)", "Hindi Voice Entry Drafts"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = str(
                    language,
                    "माइक दबाकर बोलें जैसे: \"आज सुबह 150 किलो हरा चारा डाला\" या \"दवाई के 850 रुपये खर्च हुए\"। आपकी पुष्टि के बिना कोई रिकॉर्ड सेव नहीं होता।",
                    "Tap mic and speak an entry. Nothing is saved without your explicit confirmation."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onMicClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Saffron),
                    modifier = Modifier.testTag("voice_draft_mic_btn")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "बोलें (हिंदी)", "Speak"))
                }
                OutlinedButton(
                    onClick = onGenerateDraft,
                    enabled = voiceInputText.isNotBlank() && !isLoading,
                    modifier = Modifier.testTag("voice_draft_parse_btn")
                ) {
                    Text(str(language, "ड्राफ्ट बनाएं", "Prepare Draft"))
                }
            }

            OutlinedTextField(
                value = voiceInputText,
                onValueChange = onVoiceTextChange,
                label = { Text(str(language, "बोला गया या लिखा गया पाठ", "Spoken or typed entry text")) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            if (!speechError.isNullOrBlank()) {
                Text(speechError, style = MaterialTheme.typography.labelSmall, color = AlertRed)
            }

            if (draft != null) {
                var editTitle by remember(draft) { mutableStateOf(draft.title) }
                var editQty by remember(draft) { mutableStateOf(if (draft.quantity > 0) draft.quantity.toString() else "0") }
                var editAmt by remember(draft) { mutableStateOf(if (draft.amountInr > 0) draft.amountInr.toString() else "0") }
                var editNotes by remember(draft) { mutableStateOf(draft.notes) }

                val permittedForDraft = when (draft.draftType) {
                    "CATTLE_ENTRY" -> session.canAdd(AppModule.CATTLE)
                    "FEED_LOG" -> session.canAdd(AppModule.FEED)
                    "EXPENSE" -> session.canAdd(AppModule.FINANCE)
                    "HEALTH_NOTE" -> session.canAdd(AppModule.HEALTH)
                    "SEARCH" -> session.canView(AppModule.CATTLE)
                    else -> false
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Golden, RoundedCornerShape(14.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = str(language, "पुष्टि हेतु ड्राफ्ट (${draft.draftType})", "Review Draft (${draft.draftType})"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        OutlinedTextField(
                            value = editTitle,
                            onValueChange = { editTitle = it },
                            label = { Text(str(language, "शीर्षक / विवरण", "Title")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editQty,
                                onValueChange = { editQty = it },
                                label = { Text(str(language, "मात्रा (Kg)", "Quantity")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editAmt,
                                onValueChange = { editAmt = it },
                                label = { Text(str(language, "राशि (₹)", "Amount ₹")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        OutlinedTextField(
                            value = editNotes,
                            onValueChange = { editNotes = it },
                            label = { Text(str(language, "टिप्पणी", "Notes")) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (!permittedForDraft) {
                            Text(
                                text = str(language, "आपको इस मॉड्यूल में जोड़ने की अनुमति नहीं है।", "You do not have ADD permission for this module."),
                                style = MaterialTheme.typography.labelSmall,
                                color = AlertRed
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onConfirmDraft(
                                        draft.copy(
                                            title = editTitle.trim(),
                                            quantity = editQty.toDoubleOrNull() ?: 0.0,
                                            amountInr = editAmt.toDoubleOrNull() ?: 0.0,
                                            notes = editNotes.trim()
                                        )
                                    )
                                },
                                enabled = permittedForDraft,
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                modifier = Modifier.testTag("confirm_voice_draft_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (draft.draftType == "SEARCH") str(language, "खोजें", "Search")
                                    else str(language, "पुष्टि करें व सहेजें", "Confirm & Save")
                                )
                            }
                            OutlinedButton(onClick = onDiscardDraft) {
                                Text(str(language, "रद्द करें", "Discard"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ChatBubble(
    message: AiChatMessage,
    language: AppLanguage = AppLanguage.HINDI,
    copyTag: String? = null,
    shareTag: String? = null,
    vetPhoneNumber: String = "",
    onSaveVetPhone: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var copied by remember(message.text) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 4.dp,
                bottomEnd = if (message.isUser) 4.dp else 18.dp
            ),
            color = if (message.isUser) ForestGreen else MaterialTheme.colorScheme.surface,
            tonalElevation = if (message.isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 350.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.isUser && message.modeLabel.isNotBlank()) {
                    Text(
                        text = buildString {
                            append(message.modeLabel)
                            if (message.cowContextLabel.isNotBlank()) {
                                append(" • ")
                                append(message.cowContextLabel)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Saffron,
                        fontWeight = FontWeight.Bold
                    )
                } else if (message.isUser && message.cowContextLabel.isNotBlank()) {
                    Text(
                        text = message.cowContextLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Golden,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (!message.isUser && message.usedFallbackModel && message.modelUsed.isNotBlank()) {
                    Text(
                        text = str(
                            language,
                            "बैकअप मॉडल: ${message.modelUsed}",
                            "Fallback Model: ${message.modelUsed}"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Saffron,
                        modifier = Modifier.testTag("ai_chat_fallback_badge")
                    )
                }
                if (!message.isUser && message.riskLevel != null) {
                    VetRiskBadge(riskLevel = message.riskLevel, language = language)
                }
                if (!message.isUser && message.requiresImmediateVetCall) {
                    val emergencyAssessment = remember(message.text) {
                        VetTriageEngine.evaluate(message.text).copy(
                            riskLevel = VetRiskLevel.EMERGENCY,
                            requiresImmediateVetCall = true
                        )
                    }
                    EmergencyVetCallBanner(
                        language = language,
                        assessment = emergencyAssessment,
                        vetPhoneNumber = vetPhoneNumber,
                        onSaveVetPhone = onSaveVetPhone
                    )
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                if (!message.isUser && (message.sources.isNotEmpty() || message.usedGoogleSearch)) {
                    VetSourcesCard(
                        language = language,
                        sources = message.sources,
                        usedGoogleSearch = message.usedGoogleSearch
                    )
                }
                if (!message.isUser && (copyTag != null || shareTag != null)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (copyTag != null) {
                            TextButton(
                                onClick = {
                                    if (copyTextToClipboard(context, "AI Goshala Reply", message.text)) {
                                        copied = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag(copyTag)
                            ) {
                                Icon(
                                    if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = str(language, "उत्तर कॉपी करें", "Copy response"),
                                    modifier = Modifier.size(15.dp),
                                    tint = ForestGreen
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (copied) str(language, "कॉपी हो गया", "Copied") else str(language, "कॉपी करें", "Copy"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestGreen
                                )
                            }
                        }
                        if (shareTag != null) {
                            TextButton(
                                onClick = {
                                    shareAiText(
                                        context,
                                        str(language, "AI गौशाला सहायक उत्तर", "AI Goshala Assistant Reply"),
                                        message.text
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .heightIn(min = 36.dp)
                                    .testTag(shareTag)
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = str(language, "उत्तर शेयर करें", "Share response"),
                                    modifier = Modifier.size(15.dp),
                                    tint = ForestGreen
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = str(language, "शेयर", "Share"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

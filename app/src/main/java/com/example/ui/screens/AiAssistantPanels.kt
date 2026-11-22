package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    onGeneratePlan: (focusTopic: String) -> Unit
) {
    val context = LocalContext.current
    var selectedFocus by remember { mutableStateOf("") }
    var copied by remember(workPlanState) { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val focusTopics = remember(language) {
        listOf(
            "" to str(language, "पूर्ण दैनिक योजना", "Full Daily Plan"),
            "दैनिक चारा, पानी और शेड सफाई" to str(language, "चारा व सफाई", "Fodder & Cleaning"),
            "बीमार गायों की देखभाल और स्वास्थ्य जांच" to str(language, "स्वास्थ्य देखभाल", "Health Care"),
            "गोबर से खाद, कंडे, गोबर लकड़ी और बिक्री" to str(language, "खाद व उत्पाद", "Compost & Products"),
            "गौशाला खर्च नियंत्रण और स्टाफ कार्य वितरण" to str(language, "खर्च व स्टाफ", "Cost & Staff")
        )
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
                        text = str(language, "AI दैनिक कार्य योजना (Work Plan)", "AI Daily Work Plan"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = str(
                            language,
                            "समय, कार्य, जिम्मेदार व्यक्ति और प्राथमिकता सहित व्यवस्थित योजना",
                            "Structured schedule with time, task, responsible person & priority"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                onClick = { onGeneratePlan(selectedFocus) },
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
                    text = str(language, "आज की AI कार्य योजना बनाएँ", "Generate Today's AI Work Plan"),
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
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_work_plan_error")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = workPlanState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedButton(
                                onClick = { onGeneratePlan(selectedFocus) },
                                modifier = Modifier.testTag("ai_work_plan_retry_btn")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(str(language, "पुनः प्रयास करें", "Retry"))
                            }
                        }
                    }
                }
                is AiWorkPlanUiState.Success -> {
                    val plan = workPlanState.plan
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
                                TextButton(
                                    onClick = {
                                        if (copyTextToClipboard(context, "AI Work Plan", formatWorkPlanForClipboard(plan))) {
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
                                            text = item.task,
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
    onAnalyze: () -> Unit
) {
    val context = LocalContext.current
    var reportCopied by remember(photoState) { mutableStateOf(false) }

    LaunchedEffect(reportCopied) {
        if (reportCopied) {
            delay(2000)
            reportCopied = false
        }
    }

    val quickSymptoms = remember(language) {
        listOf(
            str(language, "सुस्त / उदास", "Dull / Lethargic"),
            str(language, "खाना नहीं खा रही", "Not eating"),
            str(language, "घाव / सूजन", "Wound / Swelling"),
            str(language, "लंगड़ा रही है", "Limping"),
            str(language, "दस्त / कमजोरी", "Diarrhea / Weak")
        )
    }

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
                        text = str(language, "गाय फोटो स्वास्थ्य सहायता (AI)", "Cow Photo Health Check (AI)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (selectedCow != null) {
                            str(
                                language,
                                "चयनित गाय: ${selectedCow.name} (${selectedCow.animalCode}) — रिकॉर्ड सहित रिपोर्ट बनेगी",
                                "Selected Cow: ${selectedCow.name} (${selectedCow.animalCode}) — Report includes record"
                            )
                        } else {
                            str(
                                language,
                                "शरीर, आँख, नाक, मुँह, त्वचा, घाव, सूजन व खड़े होने की स्थिति की प्रारंभिक जाँच",
                                "Preliminary visual check of body, eyes, nose, mouth, skin, wounds & posture"
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

                // Quick symptom chips
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
                                "लक्षण लिखें (जैसे: सुस्ती, खाना न खाना, सूजन, घाव)",
                                "Observed symptoms/notes (e.g., dull, not eating, swelling)"
                            )
                        )
                    },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_photo_notes_input")
                )

                Button(
                    onClick = onAnalyze,
                    enabled = photoState !is AiUiState.Loading,
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
            }

            // Mandatory Vet Disclaimer Strip
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
                            "फोटो आधारित सुझाव केवल प्रारंभिक हैं। गंभीर लक्षण, प्रसव समस्या, साँस में तकलीफ, खून या उठ न पाने पर तुरंत पशु चिकित्सक को बुलाएँ।",
                            "Photo insights are preliminary only. Call a veterinarian immediately for severe illness, calving trouble, bleeding, or breathing issues."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText
                    )
                }
            }

            // Loading / Success / Error States for Photo Analysis
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
                                str(language, "AI जवाब तैयार कर रहा है...", "AI is analyzing cow photo & records..."),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                is AiUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SoftGreenTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ForestGreen.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .testTag("ai_photo_success_report")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = str(language, "प्रारंभिक AI स्वास्थ्य रिपोर्ट तैयार", "Preliminary AI Health Report Ready"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = {
                                        if (copyTextToClipboard(context, "AI Health Report", photoState.text)) {
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
                            }
                            Text(
                                text = photoState.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkText
                            )
                        }
                    }
                }
                is AiUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_photo_error")
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = photoState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (selectedBitmap != null) {
                                OutlinedButton(
                                    onClick = onAnalyze,
                                    modifier = Modifier.testTag("ai_photo_retry_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(str(language, "पुनः जाँच करें", "Retry Analysis"))
                                }
                            }
                        }
                    }
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
    copyTag: String? = null
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
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
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
                    Spacer(Modifier.height(3.dp))
                } else if (message.isUser && message.cowContextLabel.isNotBlank()) {
                    Text(
                        text = message.cowContextLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Golden,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
                if (!message.isUser && copyTag != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
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
                }
            }
        }
    }
}

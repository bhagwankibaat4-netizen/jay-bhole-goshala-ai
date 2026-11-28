package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.VetRiskLevel
import com.example.core.VetSourceReference
import com.example.core.VetTriageAnswers
import com.example.core.VetTriageAssessment
import com.example.core.str
import com.example.data.model.AiVetHealthReport
import com.example.data.model.Cattle
import com.example.ui.theme.*

internal fun openExternalUrl(context: Context, rawUrl: String): Boolean {
    val clean = rawUrl.trim()
    if (!clean.startsWith("https://", ignoreCase = true) && !clean.startsWith("http://", ignoreCase = true)) {
        return false
    }
    return runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(clean)).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

/**
 * Launches Android's native dialer using zero-permission ACTION_DIAL (Google Play Policy compliant —
 * never requires CALL_PHONE permission). If no saved vet phone is configured yet, opens the dialer
 * cleanly so staff can dial their local veterinarian immediately.
 */
internal fun dialVetNumber(context: Context, phoneNumber: String): Boolean {
    val clean = phoneNumber.filter { it.isDigit() || it == '+' }.take(16)
    val uri = if (clean.isNotBlank()) Uri.parse("tel:$clean") else Uri.parse("tel:")
    return runCatching {
        val intent = Intent(Intent.ACTION_DIAL, uri).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}

@Composable
internal fun VetRiskBadge(
    riskLevel: VetRiskLevel,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val (bgColor, fgColor, icon) = when (riskLevel) {
        VetRiskLevel.EMERGENCY -> Triple(AlertRed, Color.White, Icons.Default.Emergency)
        VetRiskLevel.CAUTION -> Triple(Saffron.copy(alpha = 0.18f), Saffron, Icons.Default.WarningAmber)
        VetRiskLevel.NORMAL -> Triple(ForestGreen.copy(alpha = 0.14f), ForestGreen, Icons.Default.VerifiedUser)
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bgColor,
        modifier = modifier.testTag("vet_risk_badge_${riskLevel.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = str(
                    language,
                    "खतरे का स्तर: ${riskLevel.labelHi}",
                    "Risk Level: ${riskLevel.labelEn}"
                ),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = fgColor
            )
        }
    }
}

@Composable
internal fun EmergencyVetCallBanner(
    language: AppLanguage,
    assessment: VetTriageAssessment?,
    vetPhoneNumber: String,
    onSaveVetPhone: (String) -> Unit,
    showCompactWhenNormal: Boolean = false
) {
    val context = LocalContext.current
    var showPhoneDialog by remember { mutableStateOf(false) }
    var phoneDraft by remember(vetPhoneNumber) { mutableStateOf(vetPhoneNumber) }

    val isEmergency = assessment?.riskLevel == VetRiskLevel.EMERGENCY
    if (!isEmergency && !showCompactWhenNormal) return

    if (showPhoneDialog) {
        AlertDialog(
            onDismissRequest = { showPhoneDialog = false },
            title = {
                Text(
                    str(language, "पशु चिकित्सक नंबर सेव करें", "Save Veterinarian Number"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        str(
                            language,
                            "गौशाला के नियमित पशु चिकित्सक या सरकारी पशु अस्पताल हेल्पलाइन का मोबाइल नंबर दर्ज करें ताकि आपातकाल में तुरंत कॉल हो सके।",
                            "Enter your Goshala veterinarian or Govt. vet helpline phone number for 1-tap emergency dialing."
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = phoneDraft,
                        onValueChange = { phoneDraft = it.filter { ch -> ch.isDigit() || ch == '+' || ch == '-' || ch == ' ' }.take(18) },
                        label = { Text(str(language, "पशु चिकित्सक मोबाइल नंबर", "Veterinarian Phone Number")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_vet_phone_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveVetPhone(phoneDraft.trim())
                        showPhoneDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("ai_save_vet_phone_confirm")
                ) {
                    Text(str(language, "सेव करें", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhoneDialog = false }) {
                    Text(str(language, "रद्द करें", "Cancel"))
                }
            }
        )
    }

    if (isEmergency && assessment != null) {
        val emergencyAssessment = assessment
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ai_emergency_vet_banner"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
            border = BorderStroke(1.5.dp, AlertRed)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Emergency,
                        contentDescription = str(language, "आपातकाल चेतावनी", "Emergency Alert"),
                        tint = AlertRed,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str(language, "आपातकाल — तुरंत पशु चिकित्सक को बुलाएँ!", "EMERGENCY — Call a Veterinarian Immediately!"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = AlertRed,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = if (language == AppLanguage.HINDI) emergencyAssessment.summaryBannerHi else emergencyAssessment.summaryBannerEn,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF7F0000)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { dialVetNumber(context, vetPhoneNumber) },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ai_call_vet_btn")
                    ) {
                        Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (vetPhoneNumber.isNotBlank()) {
                                str(language, "डॉक्टर को कॉल ($vetPhoneNumber)", "Call Vet ($vetPhoneNumber)")
                            } else {
                                str(language, "पशु चिकित्सक को कॉल करें", "Call Veterinarian")
                            },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedIconButton(
                        onClick = { showPhoneDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_edit_vet_phone_btn")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = str(language, "डॉक्टर का नंबर बदलें", "Set veterinarian phone"),
                            tint = AlertRed
                        )
                    }
                }
            }
        }
    } else if (showCompactWhenNormal) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SoftSaffronTint,
            border = BorderStroke(1.dp, Saffron.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocalHospital, contentDescription = null, tint = Saffron, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(language, "पशु चिकित्सक त्वरित कॉल", "Quick Veterinarian Call"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = DarkText
                    )
                    Text(
                        text = if (vetPhoneNumber.isNotBlank()) {
                            str(language, "सेव नंबर: $vetPhoneNumber", "Saved number: $vetPhoneNumber")
                        } else {
                            str(language, "नंबर सेव करें या डायलर खोलें", "Save vet number or open dialer")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { dialVetNumber(context, vetPhoneNumber) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ai_call_vet_btn")
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(str(language, "कॉल", "Call"), color = ForestGreen, fontWeight = FontWeight.Bold)
                }
                IconButton(
                    onClick = { showPhoneDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ai_edit_vet_phone_btn")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = str(language, "डॉक्टर का नंबर सेव करें", "Edit vet number"),
                        tint = Saffron
                    )
                }
            }
        }
    }
}

@Composable
internal fun VetTriageChecklistCard(
    language: AppLanguage,
    answers: VetTriageAnswers,
    selectedCow: Cattle?,
    onAnswersChange: (VetTriageAnswers) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val filledCount = remember(answers) {
        listOf(
            answers.ageText, answers.durationText, answers.temperatureText,
            answers.eatingFodder, answers.drinkingWater, answers.dungUrineNormal,
            answers.ruminating, answers.ableToStand, answers.breathingNormal,
            answers.pregnantStatus
        ).count { it.isNotBlank() }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_vet_triage_card")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
                    .testTag("ai_vet_triage_toggle"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(
                            language,
                            "त्वरित लक्षण जाँच प्रश्न ($filledCount/10 चयनित)",
                            "Quick Clinical Triage Checklist ($filledCount/10 filled)"
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Text(
                        text = str(
                            language,
                            "सटीक रिपोर्ट के लिए उम्र, अवधि, तापमान, चारा, पानी, जुगाली, खड़े होने व सांस की जानकारी चुनें",
                            "Tap to select age, duration, temperature, fodder, water, rumination, standing & breathing"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = str(language, "प्रश्न खोलें या बंद करें", "Toggle triage checklist"),
                    tint = ForestGreen
                )
            }

            if (expanded) {
                TriageChoiceRow(
                    label = str(language, "1. गाय की उम्र", "1. Cow Age"),
                    selectedValue = answers.ageText.ifBlank { selectedCow?.ageText.orEmpty() },
                    options = listOf(
                        str(language, "बछड़ा (<1 वर्ष)", "Calf (<1 yr)"),
                        str(language, "1-3 वर्ष", "1-3 yrs"),
                        str(language, "4-7 वर्ष", "4-7 yrs"),
                        str(language, "8+ वर्ष (वृद्ध)", "8+ yrs (Senior)")
                    ),
                    onSelect = { onAnswersChange(answers.copy(ageText = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "2. लक्षण कितने दिन से हैं?", "2. Duration of symptoms?"),
                    selectedValue = answers.durationText,
                    options = listOf(
                        str(language, "आज / कुछ घंटे", "Today / Few hrs"),
                        str(language, "1-2 दिन", "1-2 days"),
                        str(language, "3-5 दिन", "3-5 days"),
                        str(language, "1 सप्ताह से अधिक", ">1 week")
                    ),
                    onSelect = { onAnswersChange(answers.copy(durationText = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "3. शरीर का तापमान / बुखार?", "3. Body Temp / Fever?"),
                    selectedValue = answers.temperatureText,
                    options = listOf(
                        str(language, "सामान्य (~101.5°F)", "Normal (~101.5°F)"),
                        str(language, "हल्का बुखार (103°F)", "Mild Fever (103°F)"),
                        str(language, "तेज बुखार (105°F+)", "High Fever (105°F+)"),
                        str(language, "नापा नहीं", "Not measured")
                    ),
                    onSelect = { onAnswersChange(answers.copy(temperatureText = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "4. क्या गाय चारा खा रही है?", "4. Eating fodder?"),
                    selectedValue = answers.eatingFodder,
                    options = listOf(
                        str(language, "हाँ", "Yes"),
                        str(language, "कम खा रही", "Eating less"),
                        str(language, "नहीं", "No")
                    ),
                    onSelect = { onAnswersChange(answers.copy(eatingFodder = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "5. क्या पानी पी रही है?", "5. Drinking water?"),
                    selectedValue = answers.drinkingWater,
                    options = listOf(
                        str(language, "हाँ", "Yes"),
                        str(language, "कम", "Less"),
                        str(language, "नहीं", "No")
                    ),
                    onSelect = { onAnswersChange(answers.copy(drinkingWater = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "6. गोबर और पेशाब सामान्य?", "6. Dung & urine normal?"),
                    selectedValue = answers.dungUrineNormal,
                    options = listOf(
                        str(language, "सामान्य", "Normal"),
                        str(language, "दस्त / पतला", "Diarrhea"),
                        str(language, "कब्ज / बंद", "Constipation / Blocked"),
                        str(language, "असामान्य रंग", "Abnormal color")
                    ),
                    onSelect = { onAnswersChange(answers.copy(dungUrineNormal = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "7. क्या जुगाली कर रही है?", "7. Ruminating (Jugali)?"),
                    selectedValue = answers.ruminating,
                    options = listOf(
                        str(language, "हाँ", "Yes"),
                        str(language, "कम", "Reduced"),
                        str(language, "नहीं", "No")
                    ),
                    onSelect = { onAnswersChange(answers.copy(ruminating = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "8. क्या खड़ी हो पा रही है?", "8. Able to stand?"),
                    selectedValue = answers.ableToStand,
                    options = listOf(
                        str(language, "हाँ", "Yes"),
                        str(language, "कठिनाई से", "With difficulty"),
                        str(language, "नहीं", "No")
                    ),
                    onSelect = { onAnswersChange(answers.copy(ableToStand = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "9. क्या सांस सामान्य है?", "9. Breathing normal?"),
                    selectedValue = answers.breathingNormal,
                    options = listOf(
                        str(language, "सामान्य", "Normal"),
                        str(language, "तेज / तकलीफ", "Fast / Trouble")
                    ),
                    onSelect = { onAnswersChange(answers.copy(breathingNormal = it)) }
                )
                TriageChoiceRow(
                    label = str(language, "10. क्या गर्भवती (गाभिन) है?", "10. Pregnant?"),
                    selectedValue = answers.pregnantStatus,
                    options = listOf(
                        str(language, "नहीं", "No"),
                        str(language, "हाँ (गाभिन)", "Yes (Pregnant)"),
                        str(language, "पता नहीं", "Unknown")
                    ),
                    onSelect = { onAnswersChange(answers.copy(pregnantStatus = it)) }
                )
                if (filledCount > 0) {
                    TextButton(
                        onClick = { onAnswersChange(VetTriageAnswers()) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(str(language, "प्रश्न रीसेट करें", "Reset checklist"), color = AlertRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun TriageChoiceRow(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = DarkText
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { option ->
                val selected = selectedValue == option
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(if (selected) "" else option) },
                    label = { Text(option, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                )
            }
        }
    }
}

@Composable
internal fun VetSourcesCard(
    language: AppLanguage,
    sources: List<VetSourceReference>,
    usedGoogleSearch: Boolean
) {
    if (sources.isEmpty() && !usedGoogleSearch) return
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF1F8E9),
        border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.28f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_vet_sources_card")
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Public, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = str(
                        language,
                        "विश्वसनीय स्रोत (Google Search Grounded • DAHD / ICAR / IVRI / NDDB)",
                        "Trusted Sources (Google Search Grounded • DAHD / ICAR / IVRI / NDDB)"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreen
                )
            }
            if (sources.isEmpty()) {
                Text(
                    text = str(
                        language,
                        "Google Search जांच सक्रिय थी। विशिष्ट सरकारी लिंक उपलब्ध न होने पर स्थानीय पशु चिकित्सक से पुष्टि करें।",
                        "Google Search grounding was active. Confirm details with a qualified veterinarian."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                sources.forEachIndexed { idx, src ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openExternalUrl(context, src.url) }
                            .padding(vertical = 4.dp)
                            .testTag("ai_vet_source_$idx"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = str(language, "स्रोत लिंक खोलें", "Open source link"),
                            tint = ForestGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = src.title,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = ForestGreen,
                                textDecoration = TextDecoration.Underline,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = src.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreen.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (src.domain.isNotBlank()) append(src.domain)
                                    if (src.retrievedDate.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(str(language, "तिथि: ${src.retrievedDate}", "Date: ${src.retrievedDate}"))
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedCowVetReportsCard(
    language: AppLanguage,
    selectedCow: Cattle?,
    reportsForCow: List<AiVetHealthReport>
) {
    if (selectedCow == null) return
    var expanded by remember(selectedCow.id, selectedCow.animalCode) { mutableStateOf(false) }
    var expandedReportId by remember(selectedCow.id, selectedCow.animalCode) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_saved_vet_reports_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
                    .testTag("ai_view_past_reports_btn"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(
                            language,
                            "पुरानी स्वास्थ्य रिपोर्ट देखें (${reportsForCow.size})",
                            "View Past AI Health Reports (${reportsForCow.size})"
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Text(
                        text = str(
                            language,
                            "केवल ${selectedCow.name} (${selectedCow.animalCode}) का रिकॉर्ड",
                            "Strictly for ${selectedCow.name} (${selectedCow.animalCode})"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = str(language, "पुरानी रिपोर्ट खोलें", "Toggle past reports"),
                    tint = ForestGreen
                )
            }

            if (expanded) {
                if (reportsForCow.isEmpty()) {
                    Text(
                        text = str(
                            language,
                            "इस गाय के लिए अभी कोई सेव AI स्वास्थ्य रिपोर्ट नहीं है। ऊपर रिपोर्ट बनाकर 'रिपोर्ट गाय के रिकॉर्ड में सेव करें' दबाएँ।",
                            "No saved AI health reports for this cow yet. Generate a report above and save it to this cow's record."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    reportsForCow.take(10).forEachIndexed { idx, rep ->
                        val itemKey = rep.id.ifBlank { "${rep.dateStr}_$idx" }
                        val isOpen = expandedReportId == itemKey
                        val risk = VetRiskLevel.fromRaw(rep.riskLevel)
                        val parsedSources = remember(rep.sourceLinks) {
                            rep.sourceLinks.mapNotNull { VetSourceReference.parse(it) }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SoftGreenTint.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, ForestGreen.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expandedReportId = if (isOpen) null else itemKey }
                                .testTag("ai_saved_report_item_$idx")
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = rep.dateStr.ifBlank { "-" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreen
                                    )
                                    VetRiskBadge(riskLevel = risk, language = language)
                                }
                                if (rep.symptoms.isNotBlank()) {
                                    Text(
                                        text = str(language, "लक्षण: ${rep.symptoms}", "Symptoms: ${rep.symptoms}"),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText,
                                        maxLines = if (isOpen) Int.MAX_VALUE else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isOpen) {
                                    if (rep.triageSummary.isNotBlank()) {
                                        Text(
                                            text = rep.triageSummary,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = rep.reportText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = DarkText
                                    )
                                    if (parsedSources.isNotEmpty()) {
                                        VetSourcesCard(
                                            language = language,
                                            sources = parsedSources,
                                            usedGoogleSearch = true
                                        )
                                    }
                                    if (rep.recordedByName.isNotBlank()) {
                                        Text(
                                            text = str(
                                                language,
                                                "सेव करने वाले: ${rep.recordedByName}",
                                                "Saved by: ${rep.recordedByName}"
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(
                                        text = str(language, "पूरी रिपोर्ट देखने के लिए टैप करें", "Tap to view full report"),
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
}

@Composable
internal fun AiDiagnosticErrorCard(
    language: AppLanguage,
    message: String,
    diagnosticDetail: String?,
    containerTag: String,
    retryTag: String?,
    retryLabel: String,
    onRetry: (() -> Unit)?
) {
    var showLog by remember(diagnosticDetail) { mutableStateOf(true) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(containerTag)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!diagnosticDetail.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLog = !showLog },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = str(language, "वास्तविक Error Log (Diagnostic)", "Actual Error Log (Diagnostic)"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Icon(
                                imageVector = if (showLog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (showLog) {
                            Text(
                                text = diagnosticDetail,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = DarkText,
                                modifier = Modifier.testTag("ai_error_diagnostic_log")
                            )
                        }
                    }
                }
            }
            if (onRetry != null && retryTag != null) {
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(retryTag)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(retryLabel)
                }
            }
        }
    }
}

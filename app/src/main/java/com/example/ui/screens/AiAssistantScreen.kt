package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
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
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.*
import com.example.data.repository.VoiceEntryDraft
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.AiViewModel
import java.util.Locale

enum class AiMode(val labelHi: String, val labelEn: String) {
    VOICE_DRAFT("वॉइस ड्राफ्ट", "Voice Draft"),
    MANAGEMENT("प्रबंधन AI", "Management"),
    ANIMAL_CARE("पशु स्वास्थ्य व फोटो", "Animal Care & Photo"),
    BUSINESS("व्यापार व खाद लागत", "Business & Compost"),
    ADMIN_SUMMARY("प्रशासक सारांश", "Admin Summary")
}

@Composable
fun AiAssistantScreen(
    language: AppLanguage,
    session: UserSession,
    summary: PublicSummary,
    cattle: List<Cattle>,
    health: List<HealthRecord>,
    feedLogs: List<FeedAndOperationLog>,
    finance: List<FinancialTransaction>,
    farmers: List<FarmerRecord>,
    farmerTx: List<FarmerTransaction>,
    compost: List<CompostBatch>,
    donations: List<Donation>,
    aiViewModel: AiViewModel,
    initialMode: AiMode = AiMode.MANAGEMENT,
    onSaveCattleDraft: (Cattle) -> Unit,
    onSaveFeedDraft: (FeedAndOperationLog) -> Unit,
    onSaveExpenseDraft: (FinancialTransaction) -> Unit,
    onSaveHealthDraft: (HealthRecord) -> Unit,
    onNavigateToCattleSearch: () -> Unit
) {
    val context = LocalContext.current
    val uiState by aiViewModel.uiState.collectAsState()
    val messages by aiViewModel.messages.collectAsState()
    val voiceDraft by aiViewModel.voiceDraft.collectAsState()
    val photoState by aiViewModel.photoAnalysisState.collectAsState()

    val isPublic = session.role == UserRole.PUBLIC
    val availableModes = remember(session) {
        buildList {
            if (!isPublic) add(AiMode.VOICE_DRAFT)
            add(AiMode.MANAGEMENT)
            add(AiMode.ANIMAL_CARE)
            if (!isPublic && (session.canView(AppModule.PRODUCTION) || session.canView(AppModule.FINANCE))) {
                add(AiMode.BUSINESS)
            }
            if (session.role == UserRole.ADMIN) {
                add(AiMode.ADMIN_SUMMARY)
            }
        }
    }

    var selectedMode by remember(availableModes) {
        mutableStateOf(if (initialMode in availableModes) initialMode else availableModes.first())
    }
    var promptText by remember { mutableStateOf("") }
    var voiceInputText by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf<String?>(null) }

    // Photo picker state for Cattle Health Photo Check
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoNotes by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedBitmap = decodeScaledBitmap(context, uri, maxDim = 1024)
        }
    }

    // Speech recognizer launcher (Hindi hi-IN default)
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                voiceInputText = spoken
                speechError = null
                if (selectedMode == AiMode.VOICE_DRAFT) {
                    aiViewModel.createVoiceDraft(spoken, language)
                } else {
                    promptText = spoken
                }
            }
        }
    }

    fun launchSpeechRecognizer() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (language == AppLanguage.HINDI) "hi-IN" else Locale.ENGLISH.toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_PROMPT,
                str(language, "जय भोले गौशाला — कृपया हिंदी में बोलें", "Please speak your Goshala entry or question")
            )
        }
        try {
            speechLauncher.launch(intent)
        } catch (_: Exception) {
            speechError = str(
                language,
                "इस डिवाइस पर वॉइस सेवा उपलब्ध नहीं है। कृपया नीचे टाइप करें।",
                "Voice recognition service is unavailable on this device. Please type below."
            )
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognizer()
        } else {
            speechError = str(
                language,
                "माइक्रोफ़ोन अनुमति अस्वीकृत। आप नीचे लिखकर भी ड्राफ्ट बना सकते हैं।",
                "Microphone permission denied. You can still type below."
            )
        }
    }

    fun onMicClicked() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchSpeechRecognizer()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun sendPrompt(text: String = promptText) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        promptText = ""
        aiViewModel.askAssistant(
            prompt = trimmed,
            modeLabel = str(language, selectedMode.labelHi, selectedMode.labelEn),
            language = language,
            session = session,
            summary = summary,
            cattle = cattle,
            health = health,
            feedLogs = feedLogs,
            finance = finance,
            farmers = farmers,
            farmerTx = farmerTx,
            compost = compost,
            donations = donations
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Role & Safety Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ForestGreen)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Saffron,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = str(language, "AI गौशाला सहायक (Gemini)", "AI Goshala Assistant"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isPublic) {
                            str(language, "सार्वजनिक सहायता मोड: केवल स्वीकृत गौवंश व सेवा जानकारी", "Public Mode: Approved cattle & donation guidance only")
                        } else {
                            str(language, "कोई भी डेटा आपकी पुष्टि के बिना नहीं सहेजा जाता।", "AI drafts always require your explicit confirmation before saving.")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Golden
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(onClick = { aiViewModel.clearChat() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = str(language, "चैट साफ़ करें", "Clear chat"), tint = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Mode Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            availableModes.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(str(language, mode.labelHi, mode.labelEn)) },
                    modifier = Modifier.testTag("ai_mode_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            // 1. VOICE DRAFT MODE PANEL
            if (selectedMode == AiMode.VOICE_DRAFT && !isPublic) {
                item {
                    VoiceDraftPanel(
                        language = language,
                        session = session,
                        voiceInputText = voiceInputText,
                        onVoiceTextChange = { voiceInputText = it },
                        speechError = speechError,
                        isLoading = uiState is AiUiState.Loading,
                        draft = voiceDraft,
                        onMicClick = { onMicClicked() },
                        onGenerateDraft = { aiViewModel.createVoiceDraft(voiceInputText, language) },
                        onDiscardDraft = { aiViewModel.clearVoiceDraft() },
                        onConfirmDraft = { confirmed ->
                            val today = QrAndExportUtils.todayIso()
                            when (confirmed.draftType) {
                                "CATTLE_ENTRY" -> {
                                    onSaveCattleDraft(
                                        Cattle(
                                            animalCode = "JBG-${System.currentTimeMillis().toString().takeLast(4)}",
                                            name = confirmed.title.ifBlank { "गौवंश" },
                                            category = "COW",
                                            weightKg = confirmed.quantity,
                                            entryDate = today,
                                            notes = confirmed.notes,
                                            createdBy = session.uid
                                        )
                                    )
                                }
                                "FEED_LOG" -> {
                                    onSaveFeedDraft(
                                        FeedAndOperationLog(
                                            logDate = today,
                                            greenFodderKg = confirmed.quantity,
                                            taskTitle = confirmed.title,
                                            notes = confirmed.notes,
                                            assignedStaffName = session.displayName,
                                            recordedBy = session.uid
                                        )
                                    )
                                }
                                "EXPENSE" -> {
                                    onSaveExpenseDraft(
                                        FinancialTransaction(
                                            txType = "EXPENSE",
                                            category = "OTHER",
                                            amountInr = confirmed.amountInr,
                                            partyName = confirmed.title,
                                            description = confirmed.notes,
                                            dateStr = today,
                                            recordedBy = session.uid
                                        )
                                    )
                                }
                                "HEALTH_NOTE" -> {
                                    val firstCattle = cattle.firstOrNull()
                                    onSaveHealthDraft(
                                        HealthRecord(
                                            cattleId = firstCattle?.id ?: "GENERAL",
                                            cattleName = firstCattle?.name ?: confirmed.title,
                                            animalCode = firstCattle?.animalCode ?: "",
                                            recordType = "TREATMENT",
                                            title = confirmed.title,
                                            costInr = confirmed.amountInr,
                                            notes = confirmed.notes,
                                            recordedBy = session.uid
                                        )
                                    )
                                }
                                "SEARCH" -> {
                                    onNavigateToCattleSearch()
                                }
                            }
                            aiViewModel.clearVoiceDraft()
                        }
                    )
                }
            }

            // 2. ANIMAL CARE PHOTO HEALTH CHECK PANEL
            if (selectedMode == AiMode.ANIMAL_CARE) {
                item {
                    PhotoHealthCheckCard(
                        language = language,
                        selectedBitmap = selectedBitmap,
                        notes = photoNotes,
                        onNotesChange = { photoNotes = it },
                        photoState = photoState,
                        onPickPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onAnalyze = {
                            selectedBitmap?.let { bmp ->
                                aiViewModel.analyzeCattlePhoto(bmp, photoNotes, language)
                            }
                        }
                    )
                }
            }

            // 3. QUICK SUGGESTION CHIPS FOR CURRENT MODE
            item {
                val quickPrompts = remember(selectedMode, language, isPublic) {
                    when (selectedMode) {
                        AiMode.VOICE_DRAFT -> listOf(
                            str(language, "आज 120 किलो हरा चारा दिया गया", "Logged 120 kg green fodder today"),
                            str(language, "चारे के लिए 1500 रुपये खर्च हुए", "Spent 1500 INR on fodder")
                        )
                        AiMode.MANAGEMENT -> if (isPublic) {
                            listOf(
                                str(language, "गौशाला में गोद लेने के लिए कौन-सी गाय उपलब्ध हैं?", "Which cows are available for sponsorship?"),
                                str(language, "मैं गौशाला को किस प्रकार दान या सहयोग कर सकता हूँ?", "How can I donate or support the goshala?")
                            )
                        } else {
                            listOf(
                                str(language, "आज गौशाला की कुल स्थिति बताएं", "Give today's goshala summary"),
                                str(language, "किन गायों को स्वास्थ्य देखभाल या टीकाकरण की ज़रूरत है?", "Which cattle need medical attention or follow-up?")
                            )
                        }
                        AiMode.ANIMAL_CARE -> listOf(
                            str(language, "गौवंश को लू और गर्मी से बचाने के उपाय बताएं", "Summer heatstroke care tips for cows"),
                            str(language, "बछड़ों के पोषण और टीकाकरण का सामान्य क्रम क्या है?", "Basic calf nutrition & vaccination care tips")
                        )
                        AiMode.BUSINESS -> listOf(
                            str(language, "वर्मी कंपोस्ट खाद के बैच का लाभ-हानि विश्लेषण करें", "Analyze vermicompost batch cost and profit margin"),
                            str(language, "किसान सहयोग व्यापार में बकाया व बिक्री सारांश दें", "Summarize farmer fodder purchases & product sales")
                        )
                        AiMode.ADMIN_SUMMARY -> listOf(
                            str(language, "मासिक आय, व्यय और दान का कार्यकारी सारांश बनाएं", "Executive summary of monthly income, expense & donations"),
                            str(language, "चारा स्टॉक और लंबित भुगतानों पर सलाह दें", "Advise on fodder stock and pending payments")
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickPrompts.forEach { q ->
                        SuggestionChip(
                            onClick = {
                                if (selectedMode == AiMode.VOICE_DRAFT) {
                                    voiceInputText = q
                                    aiViewModel.createVoiceDraft(q, language)
                                } else {
                                    sendPrompt(q)
                                }
                            },
                            label = { Text(q, maxLines = 1) }
                        )
                    }
                }
            }

            // 4. CHAT HISTORY
            items(messages) { msg ->
                ChatBubble(msg)
            }

            // Streaming / Loading / Error State
            when (val state = uiState) {
                is AiUiState.Loading -> {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(str(language, "AI विचार कर रहा है...", "AI is thinking..."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is AiUiState.Streaming -> {
                    item {
                        ChatBubble(AiChatMessage(isUser = false, text = state.text, modeLabel = str(language, selectedMode.labelHi, selectedMode.labelEn)))
                    }
                }
                is AiUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        // Bottom Input Bar
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onMicClicked() },
                    modifier = Modifier.testTag("ai_mic_button")
                ) {
                    Icon(Icons.Default.Mic, contentDescription = str(language, "हिंदी में बोलें", "Voice input"), tint = Saffron)
                }
                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    placeholder = {
                        Text(
                            str(language, "गौशाला से जुड़ा प्रश्न पूछें...", "Ask about cattle, feed, or care..."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = { sendPrompt() },
                    enabled = promptText.isNotBlank() && uiState !is AiUiState.Loading,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = str(language, "भेजें", "Send"), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun VoiceDraftPanel(
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
                    "माइक दबाकर बोलें जैसे: \"आज सुबह 150 किलो हरा चारा डाला\" या \"दवाई के 850 रुपये खर्च हुए\"।",
                    "Tap mic and speak an entry (e.g. 'Logged 150 kg green fodder' or 'Spent 850 INR on medicine')."
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
private fun PhotoHealthCheckCard(
    language: AppLanguage,
    selectedBitmap: Bitmap?,
    notes: String,
    onNotesChange: (String) -> Unit,
    photoState: AiUiState,
    onPickPhoto: () -> Unit,
    onAnalyze: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = ForestGreen)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = str(language, "गाय/बछड़ा फोटो स्वास्थ्य जांच (AI)", "Cattle Photo Health Check (AI)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = str(
                    language,
                    "पशु की तस्वीर चुनें और प्राथमिक देखभाल सलाह पाएं। गंभीर लक्षण होने पर पशु चिकित्सक से तुरंत परामर्श लें।",
                    "Pick a cattle photo for preliminary visual care tips. Always consult a qualified veterinarian for diagnosis."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onPickPhoto,
                    modifier = Modifier.testTag("pick_cattle_photo_ai_btn")
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "फोटो चुनें", "Pick Photo"))
                }
                Button(
                    onClick = onAnalyze,
                    enabled = selectedBitmap != null && photoState !is AiUiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("analyze_cattle_photo_btn")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "जांच करें", "Analyze"))
                }
            }

            if (selectedBitmap != null) {
                Image(
                    bitmap = selectedBitmap.asImageBitmap(),
                    contentDescription = str(language, "चयनित पशु फोटो", "Selected cattle photo"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(str(language, "लक्षण या टिप्पणी (वैकल्पिक)", "Observed symptoms/notes (optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when (photoState) {
                is AiUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(str(language, "फोटो का विश्लेषण हो रहा है...", "Analyzing photo..."), style = MaterialTheme.typography.bodySmall)
                    }
                }
                is AiUiState.Success -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftGreenTint
                    ) {
                        Text(
                            text = photoState.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkText,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is AiUiState.Error -> {
                    Text(photoState.message, style = MaterialTheme.typography.bodySmall, color = AlertRed)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiChatMessage) {
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
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isUser && message.modeLabel.isNotBlank()) {
                    Text(
                        text = message.modeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Saffron,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message.isUser) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun decodeScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    return try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        val width = raw.width
        val height = raw.height
        val maxSide = maxOf(width, height)
        if (maxSide <= maxDim) return raw
        val ratio = maxDim.toFloat() / maxSide.toFloat()
        Bitmap.createScaledBitmap(raw, (width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1), true)
    } catch (_: Exception) {
        null
    }
}

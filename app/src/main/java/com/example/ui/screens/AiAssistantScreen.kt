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
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.CattlePhotoStorage
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.AiChatMessage
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.AiViewModel
import java.util.Locale

enum class AiMode(val labelHi: String, val labelEn: String) {
    ANIMAL_CARE("गाय स्वास्थ्य व फोटो", "Cow Health & Photo"),
    MANAGEMENT("गौशाला सवाल-जवाब", "Goshala Q&A"),
    ACTION_PLAN("AI कार्य योजना", "AI Action Plan"),
    BUSINESS("खाद, उत्पाद व आय", "Compost & Business"),
    ADMIN_SUMMARY("प्रशासक सारांश", "Admin Summary"),
    VOICE_DRAFT("वॉइस ड्राफ्ट", "Voice Draft")
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
    onNavigateToCattleSearch: () -> Unit,
    products: List<GoshalaProduct> = emptyList()
) {
    val context = LocalContext.current
    val uiState by aiViewModel.uiState.collectAsState()
    val messages by aiViewModel.messages.collectAsState()
    val voiceDraft by aiViewModel.voiceDraft.collectAsState()
    val photoState by aiViewModel.photoAnalysisState.collectAsState()

    val isPublic = session.role == UserRole.PUBLIC
    val availableModes = remember(session) {
        buildList {
            add(AiMode.ANIMAL_CARE)
            add(AiMode.MANAGEMENT)
            add(AiMode.ACTION_PLAN)
            if (!isPublic && (session.canView(AppModule.PRODUCTION) || session.canView(AppModule.FINANCE))) {
                add(AiMode.BUSINESS)
            }
            if (session.role == UserRole.ADMIN) {
                add(AiMode.ADMIN_SUMMARY)
            }
            if (!isPublic) add(AiMode.VOICE_DRAFT)
        }
    }

    var selectedMode by remember(availableModes) {
        mutableStateOf(if (initialMode in availableModes) initialMode else availableModes.first())
    }
    var selectedCow by remember(cattle) { mutableStateOf<Cattle?>(null) }
    var promptText by remember { mutableStateOf("") }
    var lastSentPrompt by remember { mutableStateOf("") }
    var voiceInputText by remember { mutableStateOf("") }
    var speechError by remember { mutableStateOf<String?>(null) }

    // Photo picker & camera capture state for Cow Photo Health Check
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoNotes by remember { mutableStateOf("") }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            cameraError = null
            selectedBitmap = decodeScaledBitmap(context, uri, maxDim = 1024)
            aiViewModel.clearPhotoAnalysis()
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            cameraError = null
            selectedBitmap = decodeScaledBitmap(context, uri, maxDim = 1024)
            aiViewModel.clearPhotoAnalysis()
        } else if (!success && uri != null) {
            cameraError = null
        }
    }

    fun launchCameraCapture() {
        try {
            val uri = CattlePhotoStorage.createCameraCaptureUri(context)
            pendingCameraUri = uri
            cameraError = null
            takePictureLauncher.launch(uri)
        } catch (_: Exception) {
            cameraError = str(
                language,
                "कैमरा शुरू नहीं हो सका। कृपया गैलरी से गाय की फोटो चुनें।",
                "Unable to open camera. Please pick a photo from Gallery."
            )
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            cameraError = str(
                language,
                "कैमरा अनुमति आवश्यक है। आप गैलरी से भी गाय की फोटो चुन सकते हैं।",
                "Camera permission is required. You can also select a photo from Gallery."
            )
        }
    }

    fun onCameraClicked() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                "माइक्रोफ़ोन अनुमति अस्वीकृत। आप नीचे लिखकर भी प्रश्न पूछ सकते हैं।",
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

    val isBusy = uiState is AiUiState.Loading || uiState is AiUiState.Streaming

    fun sendPrompt(text: String = promptText) {
        val trimmed = text.trim()
        if (trimmed.isBlank() || isBusy) return
        lastSentPrompt = trimmed
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
            donations = donations,
            products = products,
            selectedCow = selectedCow
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("ai_assistant_screen_root")
    ) {
        // Role & Strict Read-Only Safety Banner
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
                            str(
                                language,
                                "सार्वजनिक सहायता: गाय देखभाल, फोटो जाँच व स्वीकृत गौशाला जानकारी (केवल सलाह)",
                                "Public Mode: Cow care, photo check & approved goshala guidance (Read-only)"
                            )
                        } else {
                            str(
                                language,
                                "केवल सलाह मोड: AI किसी भी रिकॉर्ड को अपने आप बदलता या डिलीट नहीं करता।",
                                "Read-only Guidance: AI never edits, deletes, or saves records automatically."
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Golden
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { aiViewModel.clearChat() },
                        modifier = Modifier.testTag("ai_clear_chat_btn")
                    ) {
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
                .fillMaxWidth()
                .testTag("ai_content_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            // 1. OPTIONAL COW RECORD SELECTOR (Read-only context for Photo Health & Health/Management Q&A)
            if (selectedMode == AiMode.ANIMAL_CARE || selectedMode == AiMode.MANAGEMENT) {
                item {
                    CowContextSelectorCard(
                        language = language,
                        cattle = cattle,
                        health = health,
                        selectedCow = selectedCow,
                        isPublic = isPublic,
                        onSelectCow = { cow -> selectedCow = cow }
                    )
                }
            }

            // 2. ANIMAL CARE PHOTO HEALTH CHECK PANEL (Camera + Gallery + Selected Cow Record)
            if (selectedMode == AiMode.ANIMAL_CARE) {
                item {
                    PhotoHealthCheckCard(
                        language = language,
                        selectedCow = selectedCow,
                        selectedBitmap = selectedBitmap,
                        notes = photoNotes,
                        cameraError = cameraError,
                        photoState = photoState,
                        onNotesChange = { photoNotes = it },
                        onPickPhoto = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onCapturePhoto = { onCameraClicked() },
                        onClearPhoto = {
                            selectedBitmap = null
                            photoNotes = ""
                            cameraError = null
                            aiViewModel.clearPhotoAnalysis()
                        },
                        onAnalyze = {
                            selectedBitmap?.let { bmp ->
                                aiViewModel.analyzeCattlePhoto(
                                    bitmap = bmp,
                                    notes = photoNotes,
                                    language = language,
                                    selectedCow = selectedCow,
                                    cowHealthRecords = health,
                                    isPublic = isPublic
                                )
                            }
                        }
                    )
                }
            }

            // 3. VOICE DRAFT MODE PANEL (Explicit user confirmation required before saving)
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
                                    val firstCattle = selectedCow ?: cattle.firstOrNull()
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
                                "SEARCH" -> onNavigateToCattleSearch()
                            }
                            aiViewModel.clearVoiceDraft()
                        }
                    )
                }
            }

            // 4. QUICK SUGGESTION CHIPS FOR CURRENT MODE (All 13 Goshala Q&A topics + Action Plan)
            item {
                val quickPrompts = remember(selectedMode, language, isPublic, selectedCow) {
                    when (selectedMode) {
                        AiMode.ANIMAL_CARE -> buildList {
                            if (selectedCow != null) {
                                add(
                                    str(
                                        language,
                                        "${selectedCow!!.name} (${selectedCow!!.animalCode}) की स्वास्थ्य रिपोर्ट, पिछला उपचार और टीकाकरण स्थिति बताएं",
                                        "Summarize health report, past treatment & vaccination for ${selectedCow!!.name}"
                                    )
                                )
                            }
                            add(str(language, "बीमार गाय की देखभाल कैसे करें?", "How to care for a sick cow?"))
                            add(str(language, "गाय को दस्त होने पर क्या सावधानी रखें?", "Precautions when a cow has diarrhea"))
                            add(str(language, "गाय खाना क्यों नहीं खा रही?", "Why is a cow not eating?"))
                            add(str(language, "बछड़े की देखभाल कैसे करें?", "How to care for a newborn calf?"))
                            add(str(language, "टीकाकरण कब कराना चाहिए?", "When should vaccination be done?"))
                            add(str(language, "गर्मी और सर्दी में गायों की देखभाल कैसे करें?", "Summer & winter care for cows"))
                        }
                        AiMode.MANAGEMENT -> if (isPublic) {
                            listOf(
                                str(language, "गौशाला में गोद लेने के लिए कौन-सी गाय उपलब्ध हैं?", "Which cows are available for adoption?"),
                                str(language, "गोबर से खाद, कंडे और धूपबत्ती कैसे बनाएँ?", "How to make compost, kande & dhoop from dung?"),
                                str(language, "गौशाला में साफ-सफाई और गायों की देखभाल कैसे रखें?", "How to maintain cleanliness & cow care?"),
                                str(language, "गर्मी और सर्दी में गायों की देखभाल कैसे करें?", "Summer & winter care for cows")
                            )
                        } else {
                            listOf(
                                str(language, "आज गायों को कितना चारा देना चाहिए?", "How much fodder should be given today?"),
                                str(language, "कुल गाय, बैल, बछड़े और बीमार गायों की वास्तविक स्थिति बताएं", "Real status of cows, bulls, calves & sick cattle"),
                                str(language, "गौशाला में साफ-सफाई कैसे रखें?", "How to keep the goshala clean?"),
                                str(language, "किसान से चारा कैसे खरीदें और गुणवत्ता कैसे जाँचें?", "How to buy fodder from farmers?")
                            )
                        }
                        AiMode.ACTION_PLAN -> listOf(
                            str(language, "गौशाला के लिए आज के जरूरी कार्य की चरणबद्ध कार्य योजना बनाएँ", "Create today's priority action plan"),
                            str(language, "गाय की देखभाल, चारा प्रबंधन और सफाई की दैनिक कार्य योजना दें", "Daily cow care, fodder & cleaning plan"),
                            str(language, "गोबर से खाद, कंडे, गोबर की लकड़ी और धूपबत्ती निर्माण कार्य योजना बनाएँ", "Dung compost, kande, gau-kashtha & dhoop plan"),
                            str(language, "गौशाला के उत्पादों की बिक्री बढ़ाने और खर्च कम करने की कार्य योजना दें", "Plan to grow product sales & cut expenses")
                        )
                        AiMode.BUSINESS -> listOf(
                            str(language, "गोबर से खाद कैसे बनाएँ?", "How to make organic compost from cow dung?"),
                            str(language, "गोबर से कंडे, गोबर की लकड़ी और धूपबत्ती कैसे बनाएँ?", "How to make kande, gau-kashtha & dhoop?"),
                            str(language, "गौशाला के उत्पाद कैसे तैयार करें और बेचें?", "How to prepare and sell goshala products?"),
                            str(language, "गौशाला के खर्च कम और आमदनी को कैसे बेहतर करें?", "How to reduce expenses and improve income?"),
                            str(language, "किसान से चारा खरीद और उत्पाद बिक्री लेन-देन का सारांश दें", "Summarize farmer fodder purchases & product sales")
                        )
                        AiMode.ADMIN_SUMMARY -> listOf(
                            str(language, "कुल गाय-बैल-बछड़े, बीमार गायें, आज का चारा, स्टॉक, आय-व्यय और दान का वास्तविक सारांश दें", "Full verified summary of cattle, feed, finance & donations"),
                            str(language, "गोबर उत्पादन, खाद-उत्पाद स्टॉक और किसान लेन-देन की स्थिति बताएं", "Dung production, compost stock & farmer ledger status"),
                            str(language, "खर्च कम करने और आज के जरूरी कार्यों की प्रशासक कार्य योजना बनाएँ", "Admin cost-saving & today's priority plan")
                        )
                        AiMode.VOICE_DRAFT -> listOf(
                            str(language, "आज 120 किलो हरा चारा दिया गया", "Logged 120 kg green fodder today"),
                            str(language, "चारे के लिए 1500 रुपये खर्च हुए", "Spent 1500 INR on fodder")
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

            // 5. CHAT HISTORY
            items(messages) { msg ->
                ChatBubble(msg)
            }

            // 6. STREAMING / LOADING / SUCCESS / ERROR FEEDBACK
            when (val state = uiState) {
                is AiUiState.Loading -> {
                    item {
                        Row(
                            modifier = Modifier
                                .padding(8.dp)
                                .testTag("ai_chat_loading"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(str(language, "AI गौशाला सहायक उत्तर तैयार कर रहा है...", "AI Goshala Assistant is thinking..."), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                is AiUiState.Streaming -> {
                    item {
                        Box(modifier = Modifier.testTag("ai_chat_streaming")) {
                            ChatBubble(
                                AiChatMessage(
                                    isUser = false,
                                    text = state.text,
                                    modeLabel = str(language, selectedMode.labelHi, selectedMode.labelEn),
                                    cowContextLabel = selectedCow?.let { "${it.name} (${it.animalCode})" }.orEmpty()
                                )
                            )
                        }
                    }
                }
                is AiUiState.Success -> {
                    if (messages.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .testTag("ai_chat_success_badge"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = str(language, "AI उत्तर सफलतापूर्वक प्राप्त हुआ (केवल मार्गदर्शन हेतु)", "AI response received (guidance only)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ForestGreen
                                )
                            }
                        }
                    }
                }
                is AiUiState.Error -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("ai_chat_error")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (lastSentPrompt.isNotBlank()) {
                                    OutlinedButton(
                                        onClick = { sendPrompt(lastSentPrompt) },
                                        modifier = Modifier.testTag("ai_chat_retry_btn")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(str(language, "पुनः प्रयास करें", "Retry"))
                                    }
                                }
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
                            str(language, "गौशाला, चारा, गाय देखभाल या कार्य योजना पूछें...", "Ask about cattle, fodder, care or plan..."),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendPrompt() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_prompt_input"),
                    shape = RoundedCornerShape(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                FilledIconButton(
                    onClick = { sendPrompt() },
                    enabled = promptText.isNotBlank() && !isBusy,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = str(language, "भेजें", "Send"), tint = Color.White)
                }
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

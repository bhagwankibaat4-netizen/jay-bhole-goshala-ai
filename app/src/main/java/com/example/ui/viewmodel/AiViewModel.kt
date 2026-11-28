package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.CattlePhotoStorage
import com.example.core.VetRiskLevel
import com.example.core.VetSourceReference
import com.example.core.VetTriageAnswers
import com.example.core.VetTriageAssessment
import com.example.core.describeAppCheckDiagnostic
import com.example.core.describeAppCheckProviderForLog
import com.example.core.isAppCheckTokenError
import com.example.data.model.*
import com.example.data.repository.AiWorkPlan
import com.example.data.repository.GeminiAiRepository
import com.example.data.repository.VoiceEntryDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Streaming(
        val text: String,
        val sources: List<VetSourceReference> = emptyList(),
        val riskLevel: VetRiskLevel? = null,
        val assessment: VetTriageAssessment? = null,
        val usedGoogleSearch: Boolean = false,
        val modelUsed: String = "",
        val usedFallbackModel: Boolean = false
    ) : AiUiState

    data class Success(
        val text: String,
        val sources: List<VetSourceReference> = emptyList(),
        val riskLevel: VetRiskLevel? = null,
        val assessment: VetTriageAssessment? = null,
        val usedGoogleSearch: Boolean = false,
        val usedCowRecordPhoto: Boolean = false,
        val modelUsed: String = "",
        val usedFallbackModel: Boolean = false
    ) : AiUiState

    data class Error(
        val message: String,
        val diagnosticDetail: String? = null
    ) : AiUiState
}

sealed interface AiWorkPlanUiState {
    data object Idle : AiWorkPlanUiState
    data object Loading : AiWorkPlanUiState
    data class Success(val plan: AiWorkPlan) : AiWorkPlanUiState
    data class Error(
        val message: String,
        val diagnosticDetail: String? = null
    ) : AiWorkPlanUiState
}

data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
    val modeLabel: String = "",
    val cowContextLabel: String = "",
    val sources: List<VetSourceReference> = emptyList(),
    val riskLevel: VetRiskLevel? = null,
    val requiresImmediateVetCall: Boolean = false,
    val usedGoogleSearch: Boolean = false,
    val modelUsed: String = "",
    val usedFallbackModel: Boolean = false
)

class AiViewModel(
    private val aiRepository: GeminiAiRepository = GeminiAiRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val messages: StateFlow<List<AiChatMessage>> = _messages.asStateFlow()

    private val _voiceDraft = MutableStateFlow<VoiceEntryDraft?>(null)
    val voiceDraft: StateFlow<VoiceEntryDraft?> = _voiceDraft.asStateFlow()

    private val _photoAnalysisState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val photoAnalysisState: StateFlow<AiUiState> = _photoAnalysisState.asStateFlow()

    private val _workPlanState = MutableStateFlow<AiWorkPlanUiState>(AiWorkPlanUiState.Idle)
    val workPlanState: StateFlow<AiWorkPlanUiState> = _workPlanState.asStateFlow()

    private var lastSubmittedPrompt: String = ""
    private var lastSubmittedAtMs: Long = 0L

    fun askAssistant(
        prompt: String,
        modeLabel: String,
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
        products: List<GoshalaProduct> = emptyList(),
        selectedCow: Cattle? = null,
        isRetry: Boolean = false,
        staffAccounts: List<StaffAccount> = emptyList(),
        savedVetReports: List<AiVetHealthReport> = emptyList()
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        if (_uiState.value is AiUiState.Loading || _uiState.value is AiUiState.Streaming) return

        val now = System.currentTimeMillis()
        if (!isRetry && trimmed == lastSubmittedPrompt && (now - lastSubmittedAtMs) < DUPLICATE_GUARD_MS) {
            return
        }
        lastSubmittedPrompt = trimmed
        lastSubmittedAtMs = now

        val roleContext = aiRepository.buildRoleScopedContext(
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
            selectedCow = selectedCow,
            staffAccounts = staffAccounts,
            savedVetReports = savedVetReports
        )
        val cowTag = selectedCow?.let { "${it.name} (${it.animalCode})" }.orEmpty()
        val priorHistory = _messages.value.takeLast(6).map { it.isUser to it.text }

        val lastMsg = _messages.value.lastOrNull()
        val isDuplicateRetryOfLastUserMsg = isRetry && lastMsg?.isUser == true && lastMsg.text == trimmed
        if (!isDuplicateRetryOfLastUserMsg) {
            _messages.value = _messages.value + AiChatMessage(
                isUser = true,
                text = trimmed,
                modeLabel = modeLabel,
                cowContextLabel = cowTag
            )
        }

        _uiState.value = AiUiState.Loading
        viewModelScope.launch {
            var finalText = ""
            var finalSources: List<VetSourceReference> = emptyList()
            var finalRisk: VetRiskLevel? = null
            var finalAssessment: VetTriageAssessment? = null
            var usedSearch = false
            var modelUsed = ""
            var usedFallback = false
            try {
                aiRepository.streamAssistantUpdates(
                    userPrompt = trimmed,
                    roleContext = roleContext,
                    modeLabel = modeLabel,
                    language = language,
                    recentHistory = priorHistory,
                    selectedCow = selectedCow
                ).collect { update ->
                    finalText = update.text
                    finalSources = update.sources
                    finalRisk = update.riskLevel
                    finalAssessment = update.assessment
                    usedSearch = update.usedGoogleSearch
                    modelUsed = update.modelUsed
                    usedFallback = update.usedFallbackModel
                    _uiState.value = AiUiState.Streaming(
                        text = update.text,
                        sources = update.sources,
                        riskLevel = update.riskLevel,
                        assessment = update.assessment,
                        usedGoogleSearch = update.usedGoogleSearch,
                        modelUsed = update.modelUsed,
                        usedFallbackModel = update.usedFallbackModel
                    )
                }
                _uiState.value = AiUiState.Success(
                    text = finalText,
                    sources = finalSources,
                    riskLevel = finalRisk,
                    assessment = finalAssessment,
                    usedGoogleSearch = usedSearch,
                    modelUsed = modelUsed,
                    usedFallbackModel = usedFallback
                )
                _messages.value = _messages.value + AiChatMessage(
                    isUser = false,
                    text = finalText,
                    modeLabel = modeLabel,
                    cowContextLabel = cowTag,
                    sources = finalSources,
                    riskLevel = finalRisk,
                    requiresImmediateVetCall = finalRisk == VetRiskLevel.EMERGENCY,
                    usedGoogleSearch = usedSearch,
                    modelUsed = modelUsed,
                    usedFallbackModel = usedFallback
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _uiState.value = AiUiState.Error(
                    message = if (language == AppLanguage.HINDI) {
                        "AI उत्तर आने में समय अधिक लग गया (60 सेकंड)। कृपया इंटरनेट जांचकर पुनः प्रयास करें।"
                    } else {
                        "AI request timed out after 60 seconds. Please check network and retry."
                    },
                    diagnosticDetail = buildDiagnosticLog(e)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed: ${buildDiagnosticLog(e)}", e)
                _uiState.value = AiUiState.Error(
                    message = formatAiErrorMessage(e, language),
                    diagnosticDetail = buildDiagnosticLog(e)
                )
            }
        }
    }

    fun generateDailyWorkPlan(
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
        products: List<GoshalaProduct> = emptyList(),
        selectedCow: Cattle? = null,
        focusTopic: String = "",
        planHorizon: String = "DAILY",
        staffAccounts: List<StaffAccount> = emptyList()
    ) {
        if (_workPlanState.value is AiWorkPlanUiState.Loading) return
        val roleContext = aiRepository.buildRoleScopedContext(
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
            selectedCow = selectedCow,
            staffAccounts = staffAccounts
        )
        _workPlanState.value = AiWorkPlanUiState.Loading
        viewModelScope.launch {
            val res = aiRepository.generateWorkPlan(
                roleContext = roleContext,
                language = language,
                focusTopic = focusTopic,
                planHorizon = planHorizon
            )
            res.fold(
                onSuccess = { plan ->
                    _workPlanState.value = AiWorkPlanUiState.Success(plan)
                },
                onFailure = { e ->
                    Log.e(TAG, "Work plan generation failed (horizon=$planHorizon): ${buildDiagnosticLog(e)}", e)
                    _workPlanState.value = AiWorkPlanUiState.Error(
                        message = formatAiErrorMessage(e, language),
                        diagnosticDetail = buildDiagnosticLog(e)
                    )
                }
            )
        }
    }

    fun clearWorkPlan() {
        _workPlanState.value = AiWorkPlanUiState.Idle
    }

    fun analyzeHealthReport(
        bitmap: Bitmap?,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false,
        triageAnswers: VetTriageAnswers = VetTriageAnswers(),
        savedVetReports: List<AiVetHealthReport> = emptyList(),
        useCowSavedPhotoIfAvailable: Boolean = true
    ) {
        if (bitmap == null && notes.isBlank() && !triageAnswers.hasAnyAnswer()) return
        if (_photoAnalysisState.value is AiUiState.Loading) return
        _photoAnalysisState.value = AiUiState.Loading
        viewModelScope.launch {
            var effectiveBitmap = bitmap
            var loadedFromCowRecord = false
            if (effectiveBitmap == null &&
                useCowSavedPhotoIfAvailable &&
                selectedCow != null &&
                (!selectedCow.photoUri.isNullOrBlank() || !selectedCow.photoStoragePath.isNullOrBlank())
            ) {
                effectiveBitmap = runCatching {
                    CattlePhotoStorage.loadCowBitmapForAi(
                        cattleId = selectedCow.id,
                        photoUri = selectedCow.photoUri,
                        photoStoragePath = selectedCow.photoStoragePath
                    )
                }.getOrNull()
                loadedFromCowRecord = effectiveBitmap != null
            }

            val res = aiRepository.generateVetHealthReport(
                bitmap = effectiveBitmap,
                notes = notes,
                language = language,
                selectedCow = selectedCow,
                cowHealthRecords = cowHealthRecords,
                savedVetReports = savedVetReports,
                triageAnswers = triageAnswers,
                isPublic = isPublic
            )
            res.fold(
                onSuccess = { report ->
                    _photoAnalysisState.value = AiUiState.Success(
                        text = report.text,
                        sources = report.sources,
                        riskLevel = report.riskLevel,
                        assessment = report.assessment,
                        usedGoogleSearch = report.usedGoogleSearch,
                        usedCowRecordPhoto = loadedFromCowRecord,
                        modelUsed = report.modelUsed,
                        usedFallbackModel = report.usedFallbackModel
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "Vet health report analysis failed: ${buildDiagnosticLog(e)}", e)
                    _photoAnalysisState.value = AiUiState.Error(
                        message = formatAiErrorMessage(e, language),
                        diagnosticDetail = buildDiagnosticLog(e)
                    )
                }
            )
        }
    }

    fun analyzeCattlePhoto(
        bitmap: Bitmap,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false
    ) {
        analyzeHealthReport(
            bitmap = bitmap,
            notes = notes,
            language = language,
            selectedCow = selectedCow,
            cowHealthRecords = cowHealthRecords,
            isPublic = isPublic,
            useCowSavedPhotoIfAvailable = false
        )
    }

    fun clearPhotoAnalysis() {
        _photoAnalysisState.value = AiUiState.Idle
    }

    fun createVoiceDraft(spokenText: String, language: AppLanguage) {
        if (spokenText.isBlank()) return
        if (_uiState.value is AiUiState.Loading || _uiState.value is AiUiState.Streaming) return
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            val res = aiRepository.extractVoiceEntryDraft(spokenText)
            res.fold(
                onSuccess = { draft ->
                    _voiceDraft.value = draft
                    _uiState.value = AiUiState.Success(
                        if (language == AppLanguage.HINDI) {
                            "वॉइस से ड्राफ्ट तैयार है। सहेजने से पहले विवरण जांचें।"
                        } else {
                            "Voice draft prepared. Please review and confirm before saving."
                        }
                    )
                },
                onFailure = { e ->
                    Log.e(TAG, "Voice draft failed: ${buildDiagnosticLog(e)}", e)
                    _uiState.value = AiUiState.Error(
                        message = formatAiErrorMessage(e, language),
                        diagnosticDetail = buildDiagnosticLog(e)
                    )
                }
            )
        }
    }

    fun clearVoiceDraft() {
        _voiceDraft.value = null
    }

    fun clearChat() {
        _messages.value = emptyList()
        _uiState.value = AiUiState.Idle
        lastSubmittedPrompt = ""
        lastSubmittedAtMs = 0L
    }

    internal fun buildDiagnosticLog(e: Throwable): String {
        val parts = mutableListOf<String>()
        var cur: Throwable? = e
        var depth = 0
        while (cur != null && depth < 3) {
            val name = cur.javaClass.simpleName.ifBlank { "Error" }
            val msg = cur.message?.trim().orEmpty()
                .replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_KEY]")
                .take(220)
            parts.add(if (msg.isNotBlank()) "$name: $msg" else name)
            cur = cur.cause
            depth++
        }
        val errorChain = parts.joinToString(" -> ")
        val appCheckMeta = describeAppCheckProviderForLog()
        return "$errorChain | $appCheckMeta".take(420)
    }

    internal fun formatAiErrorMessage(e: Throwable, language: AppLanguage): String {
        val rawLower = buildString {
            var cur: Throwable? = e
            while (cur != null) {
                append(cur.javaClass.simpleName).append(' ')
                append(cur.message.orEmpty()).append(' ')
                cur = cur.cause
            }
        }.lowercase()

        if (isAppCheckTokenError(e)) {
            val diag = describeAppCheckDiagnostic(language)
            val base = if (language == AppLanguage.HINDI) {
                "ऐप सुरक्षा सत्यापन (App Check) में अस्थायी समस्या आई है। कृपया इंटरनेट व समय जांचकर 'पुनः प्रयास करें' दबाएँ या ऐप दोबारा खोलें।"
            } else {
                "Temporary App Check verification issue. Please check network/device time and tap Retry, or reopen the app."
            }
            return if (diag.isNullOrBlank()) base else "$base\n$diag"
        }
        if (
            rawLower.contains("unable to resolve host") ||
            rawLower.contains("no address associated") ||
            rawLower.contains("unknownhost") ||
            rawLower.contains("network") ||
            rawLower.contains("offline") ||
            rawLower.contains("failed to connect") ||
            rawLower.contains("socket")
        ) {
            return if (language == AppLanguage.HINDI) {
                "इंटरनेट कनेक्शन नहीं मिल रहा है। कृपया नेटवर्क जांचकर पुनः प्रयास करें।"
            } else {
                "No internet connection detected. Please check your network and retry."
            }
        }
        if (
            rawLower.contains("429") ||
            rawLower.contains("quota") ||
            rawLower.contains("rate limit") ||
            rawLower.contains("too many requests") ||
            rawLower.contains("resource exhausted")
        ) {
            return if (language == AppLanguage.HINDI) {
                "अभी AI सेवा पर बहुत अधिक अनुरोध हैं (Rate Limit)। कृपया कुछ सेकंड रुककर पुनः प्रयास करें।"
            } else {
                "AI service rate limit reached. Please wait a few seconds and retry."
            }
        }
        if (rawLower.contains("timeout") || rawLower.contains("timed out") || rawLower.contains("समय समाप्त")) {
            return if (language == AppLanguage.HINDI) {
                "AI उत्तर आने में समय अधिक लग गया। कृपया इंटरनेट जांचकर पुनः प्रयास करें।"
            } else {
                "AI response took too long. Please check your connection and retry."
            }
        }
        if (
            rawLower.contains("api key") ||
            rawLower.contains("permission") ||
            rawLower.contains("service_disabled") ||
            rawLower.contains("not enabled") ||
            rawLower.contains("unauthorized") ||
            rawLower.contains("401")
        ) {
            return if (language == AppLanguage.HINDI) {
                "Firebase AI Logic सेवा की अनुमति (API Permission) में समस्या है। नीचे दिया गया वास्तविक Error Log देखें और पुनः प्रयास करें।"
            } else {
                "Firebase AI Logic API permission error. Check the real diagnostic log below and retry."
            }
        }
        if (rawLower.contains("404") || rawLower.contains("not found") || rawLower.contains("model")) {
            return if (language == AppLanguage.HINDI) {
                "चयनित Gemini मॉडल अभी उपलब्ध नहीं है। नीचे वास्तविक Error Log देखें और पुनः प्रयास करें।"
            } else {
                "Requested Gemini model is currently unavailable. Review diagnostic log below and retry."
            }
        }
        if (rawLower.contains("safety") || rawLower.contains("blocked")) {
            return if (language == AppLanguage.HINDI) {
                "सुरक्षा नीति के कारण AI उत्तर रोक दिया गया। कृपया लक्षण स्पष्ट शब्दों में लिखकर पुनः प्रयास करें।"
            } else {
                "AI output was filtered for safety. Please rephrase symptoms clearly and retry."
            }
        }
        if (rawLower.contains("500") || rawLower.contains("503") || rawLower.contains("server") || rawLower.contains("overloaded")) {
            return if (language == AppLanguage.HINDI) {
                "Gemini सर्वर अभी व्यस्त या अस्थायी रूप से अनुपलब्ध है। कृपया कुछ सेकंड बाद पुनः प्रयास करें।"
            } else {
                "Gemini server is temporarily busy or unavailable. Please retry in a few seconds."
            }
        }
        val cleanCause = e.message?.trim()
            ?.replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED]")
            ?.take(140)
            .orEmpty()
        return if (language == AppLanguage.HINDI) {
            if (cleanCause.isNotBlank()) {
                "AI सेवा से उत्तर प्राप्त नहीं हो सका। कारण: $cleanCause"
            } else {
                "AI सेवा से अभी उत्तर प्राप्त नहीं हो सका। कृपया नीचे दिया गया Error Log जांचें और पुनः प्रयास करें।"
            }
        } else {
            if (cleanCause.isNotBlank()) {
                "Unable to get an AI response. Cause: $cleanCause"
            } else {
                "Unable to get an AI response right now. Check the diagnostic log below and retry."
            }
        }
    }

    private companion object {
        const val TAG = "AiViewModel"
        const val DUPLICATE_GUARD_MS = 2_500L
    }
}

package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.describeAppCheckDiagnostic
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
    data class Streaming(val text: String) : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

sealed interface AiWorkPlanUiState {
    data object Idle : AiWorkPlanUiState
    data object Loading : AiWorkPlanUiState
    data class Success(val plan: AiWorkPlan) : AiWorkPlanUiState
    data class Error(val message: String) : AiWorkPlanUiState
}

data class AiChatMessage(
    val isUser: Boolean,
    val text: String,
    val modeLabel: String = "",
    val cowContextLabel: String = ""
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
        staffAccounts: List<StaffAccount> = emptyList()
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
        // Prevent overlapping duplicate requests while a response is already in flight
        if (_uiState.value is AiUiState.Loading || _uiState.value is AiUiState.Streaming) return

        // Anti-duplicate guard for rapid double-taps on identical non-retry prompts
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
            staffAccounts = staffAccounts
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
            try {
                aiRepository.streamAssistantResponse(
                    userPrompt = trimmed,
                    roleContext = roleContext,
                    modeLabel = modeLabel,
                    language = language,
                    recentHistory = priorHistory
                ).collect { partial ->
                    finalText = partial
                    _uiState.value = AiUiState.Streaming(partial)
                }
                _uiState.value = AiUiState.Success(finalText)
                _messages.value = _messages.value + AiChatMessage(
                    isUser = false,
                    text = finalText,
                    modeLabel = modeLabel,
                    cowContextLabel = cowTag
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI request timed out", e)
                _uiState.value = AiUiState.Error(
                    if (language == AppLanguage.HINDI) {
                        "AI उत्तर आने में समय अधिक लग गया। कृपया इंटरनेट जांचकर पुनः प्रयास करें।"
                    } else {
                        "AI request timed out after 60 seconds. Please check network and retry."
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _uiState.value = AiUiState.Error(formatAiErrorMessage(e, language))
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
                    Log.e(TAG, "Work plan generation failed (horizon=$planHorizon)", e)
                    _workPlanState.value = AiWorkPlanUiState.Error(formatAiErrorMessage(e, language))
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
        isPublic: Boolean = false
    ) {
        if (bitmap == null && notes.isBlank()) return
        if (_photoAnalysisState.value is AiUiState.Loading) return
        _photoAnalysisState.value = AiUiState.Loading
        viewModelScope.launch {
            val res = aiRepository.analyzeHealthGuidance(
                bitmap = bitmap,
                notes = notes,
                language = language,
                selectedCow = selectedCow,
                cowHealthRecords = cowHealthRecords,
                isPublic = isPublic
            )
            res.fold(
                onSuccess = { text ->
                    _photoAnalysisState.value = AiUiState.Success(text)
                },
                onFailure = { e ->
                    Log.e(TAG, "Health guidance analysis failed", e)
                    _photoAnalysisState.value = AiUiState.Error(formatAiErrorMessage(e, language))
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
            isPublic = isPublic
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
                    Log.e(TAG, "Voice draft failed", e)
                    _uiState.value = AiUiState.Error(formatAiErrorMessage(e, language))
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

    private fun formatAiErrorMessage(e: Throwable, language: AppLanguage): String {
        val rawLower = buildString {
            var cur: Throwable? = e
            while (cur != null) {
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
        return if (language == AppLanguage.HINDI) {
            "AI सेवा से अभी उत्तर प्राप्त नहीं हो सका। कृपया कुछ सेकंड बाद पुनः प्रयास करें।"
        } else {
            "Unable to get an AI response right now. Please retry in a moment."
        }
    }

    private companion object {
        const val TAG = "AiViewModel"
        const val DUPLICATE_GUARD_MS = 2_500L
    }
}

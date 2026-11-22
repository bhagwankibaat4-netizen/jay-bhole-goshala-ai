package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.isAppCheckTokenError
import com.example.data.model.*
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
        selectedCow: Cattle? = null
    ) {
        val trimmed = prompt.trim()
        if (trimmed.isBlank()) return
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
            selectedCow = selectedCow
        )
        val cowTag = selectedCow?.let { "${it.name} (${it.animalCode})" }.orEmpty()
        _messages.value = _messages.value + AiChatMessage(
            isUser = true,
            text = trimmed,
            modeLabel = modeLabel,
            cowContextLabel = cowTag
        )

        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            var finalText = ""
            try {
                aiRepository.streamAssistantResponse(trimmed, roleContext, modeLabel, language).collect { partial ->
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
                        "AI सहायक का समय समाप्त हो गया (60 सेकंड)। कृपया नेटवर्क जांचकर पुनः प्रयास करें।"
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

    fun analyzeCattlePhoto(
        bitmap: Bitmap,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false
    ) {
        viewModelScope.launch {
            _photoAnalysisState.value = AiUiState.Loading
            val res = aiRepository.analyzeCattlePhoto(
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
                    Log.e(TAG, "Photo analysis failed", e)
                    _photoAnalysisState.value = AiUiState.Error(formatAiErrorMessage(e, language))
                }
            )
        }
    }

    fun clearPhotoAnalysis() {
        _photoAnalysisState.value = AiUiState.Idle
    }

    fun createVoiceDraft(spokenText: String, language: AppLanguage) {
        if (spokenText.isBlank()) return
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
    }

    private fun formatAiErrorMessage(e: Throwable, language: AppLanguage): String {
        val raw = e.localizedMessage?.trim().orEmpty()
        if (isAppCheckTokenError(e)) {
            return if (language == AppLanguage.HINDI) {
                "Firebase App Check सत्यापन में त्रुटि आई है। कृपया ऐप को पुनः खोलें या कुछ सेकंड बाद पुनः प्रयास करें। ($raw)"
            } else {
                "Firebase App Check verification failed. Please relaunch the app or retry in a moment. ($raw)"
            }
        }
        return if (raw.isNotBlank()) {
            raw
        } else if (language == AppLanguage.HINDI) {
            "AI सेवा अभी उपलब्ध नहीं है। कृपया इंटरनेट कनेक्शन जांचकर पुनः प्रयास करें।"
        } else {
            "AI service is currently unavailable. Please check your connection and retry."
        }
    }

    private companion object {
        const val TAG = "AiViewModel"
    }
}

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
import com.example.data.repository.AdminAiDiagnosticRepository
import com.example.data.repository.AdminAiDiagnosticResult
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
        val searchAttempted: Boolean = false,
        val modelUsed: String = "",
        val usedFallbackModel: Boolean = false
    ) : AiUiState

    data class Success(
        val text: String,
        val sources: List<VetSourceReference> = emptyList(),
        val riskLevel: VetRiskLevel? = null,
        val assessment: VetTriageAssessment? = null,
        val usedGoogleSearch: Boolean = false,
        val searchAttempted: Boolean = false,
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
    val searchAttempted: Boolean = false,
    val modelUsed: String = "",
    val usedFallbackModel: Boolean = false
)

sealed interface AdminAiDiagnosticUiState {
    data object Idle : AdminAiDiagnosticUiState
    data object Loading : AdminAiDiagnosticUiState
    data class Result(val report: AdminAiDiagnosticResult) : AdminAiDiagnosticUiState
}

class AiViewModel(
    private val aiRepository: GeminiAiRepository = GeminiAiRepository(),
    private val adminDiagnosticRepository: AdminAiDiagnosticRepository = AdminAiDiagnosticRepository()
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

    private val _adminDiagnosticState = MutableStateFlow<AdminAiDiagnosticUiState>(AdminAiDiagnosticUiState.Idle)
    val adminDiagnosticState: StateFlow<AdminAiDiagnosticUiState> = _adminDiagnosticState.asStateFlow()

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
            var searchAttempted = false
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
                    searchAttempted = update.searchAttempted
                    modelUsed = update.modelUsed
                    usedFallback = update.usedFallbackModel
                    _uiState.value = AiUiState.Streaming(
                        text = update.text,
                        sources = update.sources,
                        riskLevel = update.riskLevel,
                        assessment = update.assessment,
                        usedGoogleSearch = update.usedGoogleSearch,
                        searchAttempted = update.searchAttempted,
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
                    searchAttempted = searchAttempted,
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
                    searchAttempted = searchAttempted,
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
                        searchAttempted = report.searchAttempted,
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

    /**
     * Runs the real server-side `testGeminiConnection` Cloud Function diagnostic (strictly Admin-only).
     * Never fabricates success results and never logs tokens or secrets.
     */
    fun runAdminAiConnectionTest(
        session: UserSession,
        language: AppLanguage
    ) {
        if (_adminDiagnosticState.value is AdminAiDiagnosticUiState.Loading) return
        _adminDiagnosticState.value = AdminAiDiagnosticUiState.Loading
        viewModelScope.launch {
            val result = adminDiagnosticRepository.testGeminiConnection(
                session = session,
                language = language
            )
            _adminDiagnosticState.value = AdminAiDiagnosticUiState.Result(result)
        }
    }

    fun clearAdminDiagnosticResult() {
        _adminDiagnosticState.value = AdminAiDiagnosticUiState.Idle
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

        // 1. Network / Connectivity Error
        if (
            rawLower.contains("unable to resolve host") ||
            rawLower.contains("no address associated") ||
            rawLower.contains("unknownhost") ||
            rawLower.contains("connectexception") ||
            rawLower.contains("network") ||
            rawLower.contains("offline") ||
            rawLower.contains("failed to connect") ||
            rawLower.contains("socket")
        ) {
            return if (language == AppLanguage.HINDI) {
                "Network Error: इंटरनेट कनेक्शन नहीं मिल रहा है। कृपया मोबाइल डेटा / Wi-Fi जाँचें और 'पुनः प्रयास करें' दबाएँ।"
            } else {
                "Network Error: No internet connection detected. Please check mobile data/Wi-Fi and retry."
            }
        }

        // 2. App Check / API Rate Limit ('Too many attempts' or HTTP 429)
        if (rawLower.contains("too many attempts")) {
            return if (language == AppLanguage.HINDI) {
                "App Check अस्थायी सीमा (Too many attempts): टोकन के बार-बार प्रयास के कारण अनुरोध कुछ समय के लिए रुके हैं। कृपया 1–2 मिनट प्रतीक्षा करें और फिर एक बार प्रयास करें।"
            } else {
                "App Check Rate Limit (Too many attempts): Too many token verification attempts. Please wait 1-2 minutes before retrying once."
            }
        }

        if (
            rawLower.contains("429") ||
            rawLower.contains("quotaexceeded") ||
            rawLower.contains("quota") ||
            rawLower.contains("rate limit") ||
            rawLower.contains("too many requests") ||
            rawLower.contains("resource_exhausted") ||
            rawLower.contains("resource exhausted")
        ) {
            return if (language == AppLanguage.HINDI) {
                "HTTP 429 (API Quota / Rate Limit): अभी AI सेवा पर बहुत अधिक अनुरोध हैं या दैनिक सीमा पूरी हो गई है। कृपया 30–60 सेकंड रुककर पुनः प्रयास करें।"
            } else {
                "HTTP 429 (API Quota / Rate Limit): Too many requests or quota exceeded. Please wait 30-60 seconds and retry."
            }
        }

        // 3. HTTP 404 — Wrong Endpoint or Model Not Found
        if (
            rawLower.contains("404") ||
            rawLower.contains("not_found") ||
            rawLower.contains("not found") ||
            rawLower.contains("model is not supported") ||
            rawLower.contains("unsupported model")
        ) {
            return if (language == AppLanguage.HINDI) {
                "HTTP 404 (गलत Endpoint या Model त्रुटि): चयनित Gemini मॉडल अभी उपलब्ध नहीं है। कृपया नीचे दिया गया वास्तविक Error Log देखें और पुनः प्रयास करें।"
            } else {
                "HTTP 404 (Endpoint / Model Error): Requested Gemini model or endpoint was not found. Review the diagnostic log below and retry."
            }
        }

        // 4. HTTP 401 — Authentication / Invalid App Check Token / API Key Error
        if (
            rawLower.contains("401") ||
            rawLower.contains("unauthenticated") ||
            rawLower.contains("unauthorized") ||
            rawLower.contains("invalidapikey") ||
            rawLower.contains("api_key_invalid") ||
            rawLower.contains("firebase app check token is invalid")
        ) {
            val diag = describeAppCheckDiagnostic(language)
            val base = if (language == AppLanguage.HINDI) {
                "HTTP 401 (Authentication / App Check Token त्रुटि): ऐप सुरक्षा सत्यापन टोकन मान्य नहीं हुआ। कृपया मोबाइल की स्वचालित तारीख-समय (Auto Date & Time) व इंटरनेट जाँचें, ऐप बंद करके दोबारा खोलें और 'पुनः प्रयास करें' दबाएँ।"
            } else {
                "HTTP 401 (Authentication / App Check Token Error): App verification token was rejected. Check Auto Date & Time and network, reopen the app, and retry."
            }
            return if (diag.isNullOrBlank()) base else "$base\n$diag"
        }

        // 5. HTTP 403 — Permission Denied / API Disabled / App Check Attestation Error
        if (
            rawLower.contains("403") ||
            rawLower.contains("servicedisabled") ||
            rawLower.contains("service_disabled") ||
            rawLower.contains("not enabled") ||
            rawLower.contains("permission_denied") ||
            rawLower.contains("permission denied") ||
            rawLower.contains("forbidden") ||
            rawLower.contains("billing") ||
            rawLower.contains("api key") ||
            isAppCheckTokenError(e)
        ) {
            val isServiceOrBilling = rawLower.contains("servicedisabled") ||
                rawLower.contains("service_disabled") ||
                rawLower.contains("not enabled") ||
                rawLower.contains("api has not been used") ||
                rawLower.contains("billing")
            if (isServiceOrBilling) {
                return if (language == AppLanguage.HINDI) {
                    "HTTP 403 (API Permission त्रुटि): Firebase AI Logic सेवा या प्रोजेक्ट अनुमति सक्षम नहीं है। नीचे वास्तविक Error Log देखें और एडमिन से सत्यापित कराएँ।"
                } else {
                    "HTTP 403 (API Permission Error): Firebase AI Logic service or project permission is disabled. Review the real diagnostic log below."
                }
            }
            val diag = describeAppCheckDiagnostic(language)
            val base = if (language == AppLanguage.HINDI) {
                "HTTP 403 (Permission / App Check सुरक्षा सत्यापन त्रुटि): Firebase App Check सत्यापन अस्वीकृत हुआ। कृपया Google Play Services, इंटरनेट व ऐप का नवीनतम वर्ज़न जाँचकर 'पुनः प्रयास करें' दबाएँ।"
            } else {
                "HTTP 403 (Permission / App Check Error): Firebase App Check attestation was rejected. Check Google Play Services, internet, and retry."
            }
            return if (diag.isNullOrBlank()) base else "$base\n$diag"
        }

        // 6. Request Timeout
        if (
            rawLower.contains("timeout") ||
            rawLower.contains("timed out") ||
            rawLower.contains("requesttimeout") ||
            rawLower.contains("समय समाप्त")
        ) {
            return if (language == AppLanguage.HINDI) {
                "Timeout Error: AI सर्वर से उत्तर आने में 60 सेकंड से अधिक समय लग गया। कृपया स्थिर इंटरनेट के साथ 'पुनः प्रयास करें' दबाएँ।"
            } else {
                "Timeout Error: AI response timed out after 60 seconds. Please check your connection and retry."
            }
        }

        // 7. HTTP 500 / 503 — Backend / Server Error
        if (
            rawLower.contains("500") ||
            rawLower.contains("502") ||
            rawLower.contains("503") ||
            rawLower.contains("504") ||
            rawLower.contains("serverexception") ||
            rawLower.contains("internal") ||
            rawLower.contains("unavailable") ||
            rawLower.contains("overloaded") ||
            rawLower.contains("backend")
        ) {
            return if (language == AppLanguage.HINDI) {
                "HTTP 500/503 (Backend Server त्रुटि): Gemini AI सर्वर अभी व्यस्त या अस्थायी रूप से अनुपलब्ध है। कृपया कुछ सेकंड बाद पुनः प्रयास करें।"
            } else {
                "HTTP 500/503 (Backend Server Error): Gemini AI server is temporarily busy or unavailable. Please retry shortly."
            }
        }

        // 8. Safety / Content Filter
        if (
            rawLower.contains("safety") ||
            rawLower.contains("blocked") ||
            rawLower.contains("promptblocked") ||
            rawLower.contains("responsestopped")
        ) {
            return if (language == AppLanguage.HINDI) {
                "सुरक्षा नीति के कारण AI उत्तर रोक दिया गया। कृपया गाय के लक्षण स्पष्ट और सरल शब्दों में लिखकर पुनः प्रयास करें।"
            } else {
                "AI output was filtered for safety. Please rephrase symptoms clearly and retry."
            }
        }

        val cleanCause = e.message?.trim()
            ?.replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_KEY]")
            ?.take(140)
            .orEmpty()
        return if (language == AppLanguage.HINDI) {
            if (cleanCause.isNotBlank()) {
                "AI सेवा त्रुटि ($cleanCause) — कृपया नीचे दिया गया वास्तविक Error Log देखें और पुनः प्रयास करें।"
            } else {
                "AI सेवा से उत्तर प्राप्त नहीं हो सका (${e.javaClass.simpleName})। कृपया नीचे दिया गया वास्तविक Error Log देखें और पुनः प्रयास करें।"
            }
        } else {
            if (cleanCause.isNotBlank()) {
                "AI service error ($cleanCause) — review the actual diagnostic log below and retry."
            } else {
                "Unable to get an AI response (${e.javaClass.simpleName}). Review the diagnostic log below and retry."
            }
        }
    }

    private companion object {
        const val TAG = "AiViewModel"
        const val DUPLICATE_GUARD_MS = 2_500L
    }
}

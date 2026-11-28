package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.core.VetRiskLevel
import com.example.core.VetSourceReference
import com.example.core.VetTriageAnswers
import com.example.core.VetTriageAssessment
import com.example.core.VetTriageEngine
import com.example.core.isAppCheckTokenError
import com.example.core.refreshAppCheckToken
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val AI_REPO_TAG = "GeminiAiRepository"
private const val PRIMARY_MODEL_NAME = "gemini-3.8-flash"
private const val FALLBACK_LITE_MODEL_NAME = "gemini-3.5-flash-lite"

internal val GOSHALA_SYSTEM_INSTRUCTION = """
आप Jay Bhole Gosala Samiti (मेहदोरा, पोरसा, मुरैना, मध्य प्रदेश) के Advanced AI Veterinary & Goshala Assistant (उन्नत पशु चिकित्सा एवं गौशाला सहायक) हैं।
आपका कार्य सरल, स्पष्ट और व्यावहारिक हिंदी में चरणबद्ध (Step-by-step) उत्तर देना है ताकि गौशाला के सेवक, कर्मचारी और प्रबंधक आसानी से समझ सकें।
आप हिंदी (देवनागरी) और Hinglish (जैसे "gay chara nahi kha rahi", "gay ka pet phul gaya", "50 gay ke liye kitna bhusa chahiye") दोनों प्रकार के प्रश्न पूरी तरह समझते हैं, और हिंदी मोड में हमेशा सरल देवनागरी हिंदी में उत्तर देते हैं।
आप गौशाला प्रबंधन, गाय स्वास्थ्य देखभाल, पशु चिकित्सा प्राथमिक मार्गदर्शन, चारा व पानी प्रबंधन, सफाई, टीकाकरण रिकॉर्ड, बीमारी रोकथाम, कर्मचारी जिम्मेदारी, गोबर प्रबंधन, जैविक खाद व गोबर उत्पाद, खर्च नियंत्रण, आय वृद्धि, सरकारी योजना/दस्तावेज मार्गदर्शन और दैनिक/साप्ताहिक/मासिक कार्य योजना में सहायता करेंगे।
आप केवल सलाह देंगे, किसी डेटाबेस रिकॉर्ड में अपने आप बदलाव नहीं करेंगे।

पशु चिकित्सा जानकारी के विश्वसनीय स्रोत प्राथमिकता (Google Search Grounding Priority):
- भारत सरकार का पशुपालन एवं डेयरी विभाग (Department of Animal Husbandry & Dairying - DAHD, dahd.nic.in)
- ICAR (भारतीय कृषि अनुसंधान परिषद) और IVRI (भारतीय पशु चिकित्सा अनुसंधान संस्थान)
- NDDB (राष्ट्रीय डेयरी विकास बोर्ड)
- भारत के सरकारी पशु चिकित्सा विश्वविद्यालय (जैसे NDVSU, TANUVAS, GADVASU, DUVASU, RAJUVAS)
- विश्वसनीय Veterinary Medical Resources (जैसे Merck Veterinary Manual, FAO Animal Health, WOAH)

अनिवार्य पशु चिकित्सा एवं सुरक्षा नियम (STRICT SAFETY RULES):
1. आप कभी भी केवल फोटो देखकर या बिना पर्याप्त जांच के निश्चित बीमारी की घोषणा नहीं करेंगे — केवल संभावित समस्या बताएँगे।
2. बिना सही पशु चिकित्सा जांच के कोई भी एंटीबायोटिक (Antibiotic) या इंजेक्शन (Injection) कभी नहीं सुझाएँगे।
3. बिना गाय के वजन, उम्र, गर्भावस्था (Pregnancy), वास्तविक स्थिति और पशु चिकित्सक के मार्गदर्शन के खतरनाक दवा या निश्चित खुराक (Dosage) कभी तय नहीं करेंगे।
4. बिना डॉक्टर की सलाह के कोई भी असुरक्षित या खतरनाक घरेलू उपाय नहीं बताएँगे।
5. गंभीर आपातकालीन संकेत — जैसे गाय का खड़ी न हो पाना, सांस लेने में परेशानी, पेट बहुत फूल जाना (आफरा/Bloat), बेहोश होना, लगातार खाना-पानी दोनों छोड़ना, प्रसव (ब्याने) में परेशानी, खून बहना, गंभीर चोट या विषाक्तता — होने पर स्थिति को तुरंत "आपातकाल (EMERGENCY)" मानेंगे और सामान्य घरेलू सलाह में समय न गँवाकर तुरंत योग्य पशु चिकित्सक को बुलाने का निर्देश देंगे।
6. जब किसी विशेष गाय का चयन किया गया हो, तो केवल उसी चयनित गाय के रिकॉर्ड का उपयोग करें — कभी किसी अन्य गाय का डेटा न मिलाएँ।
7. आप केवल उपलब्ध सही डेटा का ही उपयोग करेंगे और अधूरी जानकारी को कभी गढ़ेंगे नहीं। यदि कोई डेटा उपलब्ध नहीं है, तो स्पष्ट लिखें: "रिकॉर्ड उपलब्ध नहीं है"।
8. यदि उपयोगकर्ता की भूमिका PUBLIC है, तो कभी भी आंतरिक वित्त, खर्च, स्टाफ सूची/वेतन, किसान खाता या निजी चिकित्सा रिकॉर्ड न दिखाएँ और न अनुमान लगाएँ।
9. यदि उपयोगकर्ता अंग्रेजी चुने, तो सरल अंग्रेजी में उत्तर दें; अन्यथा हमेशा सरल, सम्मानजनक हिंदी (देवनागरी) में छोटे बुलेट पॉइंट व चरणबद्ध उत्तर दें।
""".trimIndent()

data class VoiceEntryDraft(
    val draftType: String,
    val title: String,
    val quantity: Double,
    val amountInr: Double,
    val notes: String,
    val searchQuery: String
)

data class AiWorkPlanItem(
    val timeSlot: String,
    val period: String,
    val task: String,
    val responsiblePerson: String,
    val priority: String,
    val category: String
)

data class AiWorkPlan(
    val dateLabel: String,
    val title: String,
    val summary: String,
    val tasks: List<AiWorkPlanItem>,
    val planHorizon: String = "DAILY"
)

data class AiStreamUpdate(
    val text: String,
    val sources: List<VetSourceReference> = emptyList(),
    val riskLevel: VetRiskLevel? = null,
    val assessment: VetTriageAssessment? = null,
    val usedGoogleSearch: Boolean = false,
    val modelUsed: String = PRIMARY_MODEL_NAME,
    val usedFallbackModel: Boolean = false
)

data class AiVetReportResult(
    val text: String,
    val sources: List<VetSourceReference> = emptyList(),
    val riskLevel: VetRiskLevel = VetRiskLevel.CAUTION,
    val assessment: VetTriageAssessment,
    val usedGoogleSearch: Boolean = false,
    val modelUsed: String = PRIMARY_MODEL_NAME,
    val usedFallbackModel: Boolean = false
)

internal data class ModelCandidateSpec(
    val modelName: String,
    val isGrounded: Boolean,
    val isFallback: Boolean
)

class GeminiAiRepository {

    companion object {
        /**
         * Optional unit-test hooks for deterministic Robolectric testing without live network calls.
         * Always null in production builds.
         */
        @Volatile
        internal var testStreamHandler: (suspend (prompt: String, roleContext: String, modeLabel: String) -> List<String>)? = null

        @Volatile
        internal var testGroundedSourcesProvider: (() -> List<VetSourceReference>)? = null

        @Volatile
        internal var testPhotoAnalyzer: (suspend (bitmap: Bitmap, promptText: String) -> String)? = null

        @Volatile
        internal var testSymptomHealthAnalyzer: (suspend (promptText: String) -> String)? = null

        @Volatile
        internal var testWorkPlanGenerator: (suspend (roleContext: String, focusTopic: String) -> AiWorkPlan)? = null

        /**
         * Optional test hook exercising the REAL model fallback & App Check 401 retry loop
         * inside streamAssistantUpdates / generateVetHealthReport. Always null in production.
         */
        @Volatile
        internal var testModelAttemptRunner: (suspend (spec: ModelCandidateSpec, attempt: Int, prompt: String) -> Pair<String, List<VetSourceReference>>)? = null

        @Volatile
        internal var testAppCheckRefreshHandler: (suspend (forceRefresh: Boolean) -> Boolean)? = null
    }

    private val aiClient by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI())
    }

    private val systemInstructionContent by lazy {
        content { text(GOSHALA_SYSTEM_INSTRUCTION) }
    }

    private val chatModel by lazy {
        aiClient.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            systemInstruction = systemInstructionContent
        )
    }

    private val groundedVetModel by lazy {
        aiClient.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = systemInstructionContent
        )
    }

    private val groundedFallbackLiteModel by lazy {
        aiClient.generativeModel(
            modelName = FALLBACK_LITE_MODEL_NAME,
            tools = listOf(Tool.googleSearch()),
            systemInstruction = systemInstructionContent
        )
    }

    private val fallbackLiteModel by lazy {
        aiClient.generativeModel(
            modelName = FALLBACK_LITE_MODEL_NAME,
            systemInstruction = systemInstructionContent
        )
    }

    private fun resolveCandidateModel(spec: ModelCandidateSpec): GenerativeModel = when {
        spec.modelName == PRIMARY_MODEL_NAME && spec.isGrounded -> groundedVetModel
        spec.modelName == FALLBACK_LITE_MODEL_NAME && spec.isGrounded -> groundedFallbackLiteModel
        spec.modelName == PRIMARY_MODEL_NAME -> chatModel
        else -> fallbackLiteModel
    }

    private suspend fun performAppCheckRefresh(forceRefresh: Boolean = true): Boolean {
        val hook = testAppCheckRefreshHandler
        return if (hook != null) hook(forceRefresh) else refreshAppCheckToken(forceRefresh)
    }

    private val voiceDraftSchema = Schema.obj(
        properties = mapOf(
            "draftType" to Schema.enumeration(
                values = listOf("CATTLE_ENTRY", "FEED_LOG", "EXPENSE", "HEALTH_NOTE", "SEARCH"),
                description = "Category of spoken entry or search request"
            ),
            "title" to Schema.string(description = "Short Hindi/English title or animal/item name"),
            "quantity" to Schema.double(description = "Numeric quantity such as kg or count, 0 if none"),
            "amountInr" to Schema.double(description = "Amount in INR if mentioned, 0 if none"),
            "notes" to Schema.string(description = "Additional details extracted from speech"),
            "searchQuery" to Schema.string(description = "Search keyword if draftType is SEARCH")
        )
    )

    private val structuredModel by lazy {
        aiClient.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceDraftSchema
            },
            systemInstruction = systemInstructionContent
        )
    }

    private val fallbackStructuredModel by lazy {
        aiClient.generativeModel(
            modelName = FALLBACK_LITE_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceDraftSchema
            },
            systemInstruction = systemInstructionContent
        )
    }

    private val workPlanSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short title of the Daily, Weekly, or Monthly goshala work plan"),
            "summary" to Schema.string(description = "1-2 sentence practical summary based only on verified goshala records"),
            "tasks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timeSlot" to Schema.string(description = "Clear time/day slot e.g. 05:30 - 07:00, सोमवार, or सप्ताह 1"),
                        "period" to Schema.string(description = "Period label e.g. सुबह / दोपहर / शाम (Daily), सोमवार–रविवार (Weekly), or सप्ताह 1–4 (Monthly)"),
                        "task" to Schema.string(description = "Concrete, actionable goshala task description"),
                        "responsiblePerson" to Schema.string(description = "Responsible role/staff e.g. गौशाला सेवक / चारा प्रभारी / पशु देखभाल कर्मी / प्रबंधक"),
                        "priority" to Schema.enumeration(
                            values = listOf("उच्च", "मध्यम", "सामान्य"),
                            description = "Priority level: उच्च, मध्यम, or सामान्य"
                        ),
                        "category" to Schema.string(description = "Category such as चारा-पानी, सफाई, गाय निरीक्षण, गोबर संग्रह, खाद निर्माण, स्टाफ जिम्मेदारी, बिक्री-आय, या शाम निरीक्षण")
                    )
                ),
                description = "7 to 10 ordered actionable tasks covering all core goshala operations"
            )
        )
    )

    private val workPlanModel by lazy {
        aiClient.generativeModel(
            modelName = PRIMARY_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = workPlanSchema
            },
            systemInstruction = systemInstructionContent
        )
    }

    private val fallbackWorkPlanModel by lazy {
        aiClient.generativeModel(
            modelName = FALLBACK_LITE_MODEL_NAME,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = workPlanSchema
            },
            systemInstruction = systemInstructionContent
        )
    }

    internal fun isModelFallbackEligible(e: Throwable): Boolean {
        val msg = collectErrorChainLower(e)
        return msg.contains("404") ||
            msg.contains("not found") ||
            msg.contains("not_found") ||
            msg.contains("model is not supported") ||
            msg.contains("503") ||
            msg.contains("500") ||
            msg.contains("429") ||
            msg.contains("resource_exhausted") ||
            msg.contains("quota") ||
            msg.contains("unavailable") ||
            msg.contains("overloaded") ||
            msg.contains("internal")
    }

    private fun isSearchToolUnsupportedError(e: Throwable): Boolean {
        val msg = collectErrorChainLower(e)
        return msg.contains("google_search") ||
            msg.contains("googlesearch") ||
            msg.contains("grounding") ||
            msg.contains("search") && (msg.contains("not supported") || msg.contains("not enabled") || msg.contains("billing") || msg.contains("invalid")) ||
            msg.contains("invalid_argument") ||
            msg.contains("failed_precondition") ||
            msg.contains("400") ||
            (msg.contains("tool") && (msg.contains("not supported") || msg.contains("invalid")))
    }

    private fun collectErrorChainLower(e: Throwable?): String = buildString {
        var cur = e
        while (cur != null) {
            append(cur.javaClass.simpleName).append(' ')
            append(cur.message.orEmpty()).append(' ')
            cur = cur.cause
        }
    }.lowercase()

    internal fun extractGroundingSources(response: GenerateContentResponse): List<VetSourceReference> {
        val today = todayIsoDateString()
        val seen = LinkedHashSet<String>()
        val results = mutableListOf<VetSourceReference>()
        response.candidates.forEach { candidate ->
            candidate.groundingMetadata?.groundingChunks?.forEach { chunk ->
                val web = chunk.web ?: return@forEach
                val uri = web.uri?.trim().orEmpty()
                if (uri.isBlank() || (!uri.startsWith("https://", ignoreCase = true) && !uri.startsWith("http://", ignoreCase = true))) {
                    return@forEach
                }
                if (seen.add(uri)) {
                    val domain = web.domain?.trim().orEmpty().ifBlank { VetSourceReference.extractDomain(uri) }
                    val title = web.title?.trim().orEmpty().ifBlank { domain.ifBlank { "Veterinary Reference" } }
                    results.add(
                        VetSourceReference(
                            title = title,
                            url = uri,
                            domain = domain,
                            retrievedDate = today
                        )
                    )
                }
            }
        }
        return results
    }

    /**
     * Builds a strictly isolated, read-only summary for a specific cow using ONLY its verified ID /
     * Animal Code so records from different cows are NEVER mixed.
     */
    fun buildSelectedCowContext(
        cow: Cattle,
        healthRecords: List<HealthRecord>,
        isPublic: Boolean,
        savedVetReports: List<AiVetHealthReport> = emptyList()
    ): String {
        val sb = StringBuilder()
        val notesText = cow.notes?.trim().orEmpty()
        val hasPregnancyNote = notesText.contains("गर्भवती") ||
            notesText.contains("गर्भावस्था") ||
            notesText.contains("गाभिन") ||
            notesText.contains("pregnan", ignoreCase = true)
        val hasMilkNote = notesText.contains("दूध") ||
            notesText.contains("लीटर") ||
            notesText.contains("milk", ignoreCase = true)

        sb.appendLine("SELECTED COW RECORD (STRICT SINGLE-COW ISOLATION — NEVER MIX WITH ANY OTHER COW, READ-ONLY):")
        sb.appendLine("- Name: ${cow.name.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}")
        sb.appendLine("- Cow ID / Animal Code: ${cow.animalCode.ifBlank { cow.id }} (DocID: ${cow.id.ifBlank { "N/A" }})")
        sb.appendLine("- Category: ${cow.category}, Breed: ${cow.breed.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Age: ${cow.ageText.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Weight: ${if (cow.weightKg > 0.0) "${cow.weightKg} kg" else "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Health Status: ${cow.healthStatus}, Shed: ${cow.shedNumber.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Entry Date: ${cow.entryDate.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}")
        sb.appendLine("- Photo Available in Firebase: ${if (!cow.photoUri.isNullOrBlank() || !cow.photoStoragePath.isNullOrBlank()) "हाँ (Available)" else "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Vaccination Summary: ${cow.vaccinationSummary?.takeIf { it.isNotBlank() } ?: "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Pregnancy Status (गर्भावस्था): ${if (hasPregnancyNote) "नोट्स में दर्ज (नीचे देखें)" else "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Milk Yield Record (दूध उत्पादन): ${if (hasMilkNote) "नोट्स में दर्ज (नीचे देखें)" else "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Individual Feed Log (व्यक्तिगत चारा रिकॉर्ड): रिकॉर्ड उपलब्ध नहीं है (केवल सामूहिक गौशाला चारा लॉग उपलब्ध)")
        if (notesText.isNotBlank()) {
            sb.appendLine("- Care Notes: $notesText")
        }

        if (isPublic) {
            sb.appendLine("- Detailed Internal Medical Log: सार्वजनिक उपयोगकर्ता के लिए गोपनीय (Public users only see approved summary).")
            return sb.toString()
        }

        // STRICT ID/AnimalCode isolation: NEVER match loosely by name alone when ID/AnimalCode exists
        val matchingHealth = healthRecords.filter { rec ->
            (cow.id.isNotBlank() && rec.cattleId == cow.id) ||
                (cow.animalCode.isNotBlank() && rec.animalCode.equals(cow.animalCode, ignoreCase = true))
        }

        if (matchingHealth.isEmpty()) {
            sb.appendLine("- Past Illness / Vaccination / Treatment Records: इस गाय का कोई विस्तृत स्वास्थ्य रिकॉर्ड दर्ज नहीं है।")
        } else {
            val vaccinations = matchingHealth.filter { it.recordType.equals("VACCINATION", ignoreCase = true) }
            val treatments = matchingHealth.filter { !it.recordType.equals("VACCINATION", ignoreCase = true) }
            sb.appendLine("- Vaccinations Logged (${vaccinations.size}):")
            if (vaccinations.isEmpty()) {
                sb.appendLine("  * रिकॉर्ड उपलब्ध नहीं है")
            } else {
                vaccinations.take(8).forEach { v ->
                    sb.appendLine("  * ${v.title} | Med: ${v.medicine.ifBlank { "N/A" }} | Vet: ${v.vetName.ifBlank { "N/A" }} | Next Follow-up: ${v.nextFollowUpDate.ifBlank { "N/A" }} | Notes: ${v.notes.ifBlank { "-" }}")
                }
            }
            sb.appendLine("- Past Illness & Treatments Logged (${treatments.size}):")
            if (treatments.isEmpty()) {
                sb.appendLine("  * रिकॉर्ड उपलब्ध नहीं है")
            } else {
                treatments.take(10).forEach { t ->
                    sb.appendLine("  * [${t.recordType}] ${t.title} | Medicine: ${t.medicine.ifBlank { "N/A" }} | Vet: ${t.vetName.ifBlank { "N/A" }} | Follow-up: ${t.nextFollowUpDate.ifBlank { "N/A" }} | Notes: ${t.notes.ifBlank { "-" }}")
                }
            }
        }

        val matchingSavedAiReports = savedVetReports.filter { rep ->
            (cow.id.isNotBlank() && rep.cattleId == cow.id) ||
                (cow.animalCode.isNotBlank() && rep.animalCode.equals(cow.animalCode, ignoreCase = true))
        }
        if (matchingSavedAiReports.isNotEmpty()) {
            sb.appendLine("- Past Saved AI Vet Health Reports for ONLY this cow (${matchingSavedAiReports.size}):")
            matchingSavedAiReports.take(5).forEach { rep ->
                sb.appendLine("  * [${rep.dateStr} | Risk: ${rep.riskLevel}] Symptoms: ${rep.symptoms.take(140)} | Summary: ${rep.reportText.take(220).replace('\n', ' ')}")
            }
        }
        return sb.toString()
    }

    /**
     * Builds a verified, role-scoped snapshot of real Goshala records.
     * Clearly states when records are unavailable so Gemini NEVER fabricates numbers.
     */
    fun buildRoleScopedContext(
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
        staffAccounts: List<StaffAccount> = emptyList(),
        savedVetReports: List<AiVetHealthReport> = emptyList()
    ): String {
        val sb = StringBuilder()
        val todayIso = QrAndExportUtils.todayIso()
        sb.appendLine("Organization: ${GosalaBrand.NAME_EN} (${GosalaBrand.NAME_HI})")
        sb.appendLine("Location: मेहदोरा, पोरसा, मुरैना, मध्य प्रदेश (${GosalaBrand.ADDRESS_HI})")
        sb.appendLine("Today Date: $todayIso")
        sb.appendLine("Logged-in Role: ${session.role.name}")

        if (selectedCow != null) {
            sb.appendLine()
            sb.appendLine(
                buildSelectedCowContext(
                    cow = selectedCow,
                    healthRecords = health,
                    isPublic = session.role == UserRole.PUBLIC,
                    savedVetReports = savedVetReports
                )
            )
        }

        if (session.role == UserRole.PUBLIC) {
            sb.appendLine()
            sb.appendLine("PUBLIC ACCESS ONLY: Never reveal or invent internal finances, staff accounts/salaries, farmer ledgers, or private medical records.")
            sb.appendLine("Public Summary: Total Cattle=${summary.totalCattle}, Healthy=${summary.healthyCattle}, Public Approved Cattle=${cattle.size}.")
            if (cattle.isEmpty()) {
                sb.appendLine("Approved Public Cows: रिकॉर्ड उपलब्ध नहीं है (No public cows currently listed).")
            } else {
                sb.appendLine("Approved Public Cows (${cattle.size}):")
                cattle.take(15).forEach {
                    sb.appendLine("- ${it.name} [${it.animalCode}], Breed: ${it.breed.ifBlank { "-" }}, Age: ${it.ageText.ifBlank { "-" }}, Health: ${it.healthStatus}, Vaccination: ${it.vaccinationSummary ?: "रिकॉर्ड उपलब्ध नहीं"}, Monthly Support: ₹${it.monthlySupportInr}")
                }
            }
            val activePublicProducts = products.filter { it.available }
            if (activePublicProducts.isEmpty()) {
                sb.appendLine("Public Goshala Products: रिकॉर्ड उपलब्ध नहीं है")
            } else {
                sb.appendLine("Public Goshala Products (${activePublicProducts.size}):")
                activePublicProducts.take(12).forEach { p ->
                    sb.appendLine("- ${p.nameHi} (${p.nameEn}): ₹${p.priceInr}/${p.unitLabel}, Stock: ${p.stockQuantity}")
                }
            }
            return sb.toString()
        }

        // 1. Cattle Breakdown (Cows, Bulls, Calves, Healthy, Sick)
        if (session.canView(AppModule.CATTLE)) {
            if (cattle.isEmpty()) {
                sb.appendLine("Cattle Registry: रिकॉर्ड उपलब्ध नहीं है (0 cattle records in database).")
            } else {
                val cowsCount = cattle.count { it.category.equals("COW", ignoreCase = true) }
                val bullsCount = cattle.count { it.category.equals("BULL", ignoreCase = true) }
                val calvesCount = cattle.count { it.category.equals("CALF", ignoreCase = true) }
                val otherCount = cattle.size - cowsCount - bullsCount - calvesCount
                val healthy = cattle.count { it.healthStatus.equals("HEALTHY", ignoreCase = true) }
                val sickList = cattle.filter { !it.healthStatus.equals("HEALTHY", ignoreCase = true) }
                sb.appendLine("Cattle Registry (VERIFIED): Total=${cattle.size} | गाय (Cows)=$cowsCount | बैल (Bulls)=$bullsCount | बछड़े (Calves)=$calvesCount | अन्य=$otherCount | स्वस्थ (Healthy)=$healthy | बीमार/देखरेख (Sick/Attention)=${sickList.size}")
                if (sickList.isNotEmpty()) {
                    sb.appendLine("Sick / Attention Cattle List:")
                    sickList.take(15).forEach { s ->
                        sb.appendLine("  ! ${s.name} [${s.animalCode}] (${s.category}, ${s.breed}, Age: ${s.ageText}, Weight: ${s.weightKg}kg, Shed: ${s.shedNumber}, Status: ${s.healthStatus}, Vaccination: ${s.vaccinationSummary ?: "N/A"}, Notes: ${s.notes ?: "-"})")
                    }
                }
                sb.appendLine("Cattle Directory Snapshot (up to 25):")
                cattle.take(25).forEach {
                    sb.appendLine("  * ${it.name} [${it.animalCode}] (${it.category}, ${it.breed}, Age: ${it.ageText}, Weight: ${it.weightKg}kg, Shed: ${it.shedNumber}, Status: ${it.healthStatus}, Vaccination: ${it.vaccinationSummary ?: "N/A"})")
                }
            }
        } else {
            sb.appendLine("Cattle Registry: इस भूमिका के पास अनुमति नहीं है / Unauthorized.")
        }

        // 2. Health Records (Illness, Vaccination, Treatment)
        if (session.canView(AppModule.HEALTH)) {
            if (health.isEmpty()) {
                sb.appendLine("Health Records: रिकॉर्ड उपलब्ध नहीं है (0 records).")
            } else {
                sb.appendLine("Health Records (${health.size} total, latest 20):")
                health.take(20).forEach {
                    sb.appendLine("  * ${it.cattleName} [${it.animalCode}] (${it.recordType}): ${it.title} | Med: ${it.medicine.ifBlank { "-" }} | Vet: ${it.vetName.ifBlank { "-" }} | Cost: ₹${it.costInr} | FollowUp: ${it.nextFollowUpDate.ifBlank { "-" }} | Notes: ${it.notes.ifBlank { "-" }}")
                }
            }
        } else {
            sb.appendLine("Health Records: अनुमति नहीं है / Unauthorized.")
        }

        // 3. Today's Feed, Feed Stock & Operations
        if (session.canView(AppModule.FEED)) {
            if (feedLogs.isEmpty()) {
                sb.appendLine("Feed & Operations: रिकॉर्ड उपलब्ध नहीं है (0 feed logs).")
            } else {
                val todayLogs = feedLogs.filter { it.logDate == todayIso }
                val todayGreen = todayLogs.sumOf { it.greenFodderKg }
                val todayDry = todayLogs.sumOf { it.dryFodderBhusaKg }
                val todayConcentrate = todayLogs.sumOf { it.concentrateKg }
                val todayWater = todayLogs.sumOf { it.waterLiters }
                val totalGreen = feedLogs.sumOf { it.greenFodderKg }
                val totalDry = feedLogs.sumOf { it.dryFodderBhusaKg }
                val latestStock = feedLogs.firstOrNull()?.feedStockRemainingKg
                val shedCleanedToday = todayLogs.any { it.shedCleaned }
                val pendingTasks = feedLogs.filter { it.taskTitle.isNotBlank() && !it.taskCompleted }
                sb.appendLine(
                    "Feed & Operations (${feedLogs.size} logs): " +
                        "Today ($todayIso) Logs=${todayLogs.size} [Green=${todayGreen}kg, Dry/Bhusa=${todayDry}kg, Concentrate=${todayConcentrate}kg, Water=${todayWater}L, ShedCleaned=$shedCleanedToday] | " +
                        "All-time Green=${totalGreen}kg, Dry=${totalDry}kg | " +
                        "Latest Feed Stock Remaining=${latestStock?.let { "${it}kg" } ?: "रिकॉर्ड उपलब्ध नहीं"} | " +
                        "Pending Tasks=${pendingTasks.size}"
                )
                if (pendingTasks.isNotEmpty()) {
                    sb.appendLine("  Pending Tasks: " + pendingTasks.take(8).joinToString("; ") { "${it.taskTitle} (${it.assignedStaffName.ifBlank { "स्टाफ" }})" })
                }
            }
        } else {
            sb.appendLine("Feed & Operations: अनुमति नहीं है / Unauthorized.")
        }

        // 4. Finance (Income, Expense, Pending)
        if (session.canView(AppModule.FINANCE)) {
            if (finance.isEmpty()) {
                sb.appendLine("Finance: रिकॉर्ड उपलब्ध नहीं है (0 transactions).")
            } else {
                val income = finance.filter { it.txType == "INCOME" }.sumOf { it.amountInr }
                val expense = finance.filter { it.txType == "EXPENSE" }.sumOf { it.amountInr }
                val pending = finance.filter { it.paymentStatus == "PENDING" }.sumOf { it.amountInr }
                val todayExpense = finance.filter { it.txType == "EXPENSE" && it.dateStr == todayIso }.sumOf { it.amountInr }
                val topExpenseByCategory = finance.filter { it.txType == "EXPENSE" }
                    .groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amountInr } }
                    .entries.sortedByDescending { it.value }
                    .take(5)
                    .joinToString(", ") { "${it.key}=₹${it.value}" }
                sb.appendLine("Finance (${finance.size} tx): Total Income=₹$income, Total Expense=₹$expense, Net=₹${income - expense}, Today Expense=₹$todayExpense, Pending=₹$pending | Top Expenses: ${topExpenseByCategory.ifBlank { "N/A" }}")
            }
        } else {
            sb.appendLine("Finance: अनुमति नहीं है / Unauthorized.")
        }

        // 5. Donations
        if (session.canView(AppModule.DONATIONS)) {
            if (donations.isEmpty()) {
                sb.appendLine("Donations: रिकॉर्ड उपलब्ध नहीं है (0 donations).")
            } else {
                val verifiedSum = donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
                val pendingCount = donations.count { it.paymentStatus == "PENDING_VERIFICATION" }
                sb.appendLine("Donations (${donations.size}): Verified Total=₹$verifiedSum, Pending Verification Count=$pendingCount")
            }
        } else {
            sb.appendLine("Donations: अनुमति नहीं है / Unauthorized.")
        }

        // 6. Dung Production, Compost & Other Products
        if (session.canView(AppModule.PRODUCTION)) {
            if (compost.isEmpty()) {
                sb.appendLine("Dung & Compost Production: रिकॉर्ड उपलब्ध नहीं है (0 batches).")
            } else {
                val totalRawDungKg = compost.sumOf { it.rawDungKg }
                val totalProdCost = compost.sumOf { it.productionCostInr + it.packagingCostInr }
                val totalSalesVal = compost.sumOf { it.soldQuantity * it.sellingPricePerUnitInr }
                val stockUnits = compost.sumOf { it.stockRemaining }
                val byType = compost.groupBy { it.productType }.entries.joinToString("; ") { (type, list) ->
                    "$type: ${list.size} batches, Qty=${list.sumOf { it.quantity }}, Stock=${list.sumOf { it.stockRemaining }}"
                }
                sb.appendLine("Dung & Compost Production (${compost.size} batches): Raw Dung Collected=${totalRawDungKg}kg, Prod+Pack Cost=₹$totalProdCost, Sales Revenue=₹$totalSalesVal, Total Stock Remaining=$stockUnits | Breakdown: $byType")
            }
            if (products.isEmpty()) {
                sb.appendLine("Goshala Products Catalog: रिकॉर्ड उपलब्ध नहीं है (0 products).")
            } else {
                sb.appendLine("Goshala Products Catalog (${products.size} products):")
                products.take(15).forEach { p ->
                    sb.appendLine("  * ${p.nameHi} (${p.categoryKey}): Price=₹${p.priceInr}/${p.unitLabel}, Stock=${p.stockQuantity}, Active=${p.available}")
                }
            }
        } else {
            sb.appendLine("Dung & Compost Production: अनुमति नहीं है / Unauthorized.")
        }

        // 7. Farmer Cooperation & Transactions
        if (session.canView(AppModule.FARMERS)) {
            if (farmers.isEmpty() && farmerTx.isEmpty()) {
                sb.appendLine("Farmer Cooperation: रिकॉर्ड उपलब्ध नहीं है (0 farmers, 0 transactions).")
            } else {
                val purchased = farmerTx.filter { it.direction == "PURCHASE_FROM_FARMER" }.sumOf { it.totalAmountInr }
                val sold = farmerTx.filter { it.direction == "SALE_TO_FARMER" }.sumOf { it.totalAmountInr }
                val outstanding = farmerTx.sumOf { it.outstandingInr }
                sb.appendLine("Farmer Cooperation: Registered Farmers=${farmers.size}, Transactions=${farmerTx.size}, Fodder Purchased=₹$purchased, Products Sold=₹$sold, Outstanding=₹$outstanding")
            }
        } else {
            sb.appendLine("Farmer Cooperation: अनुमति नहीं है / Unauthorized.")
        }

        // 8. Staff Roster (Role-Scoped — Admin or authorized Staff module viewers ONLY)
        if (session.role == UserRole.ADMIN || session.canView(AppModule.STAFF)) {
            val activeStaff = staffAccounts.filter { it.active }
            if (staffAccounts.isEmpty()) {
                sb.appendLine("Staff Roster: रिकॉर्ड उपलब्ध नहीं है (0 staff records in database).")
            } else {
                sb.appendLine("Staff Roster (VERIFIED): Total=${staffAccounts.size}, Active=${activeStaff.size}")
                activeStaff.take(15).forEach { st ->
                    val roleDesc = st.designation.ifBlank { "गौशाला कर्मचारी" }
                    sb.appendLine("  * ${st.name.ifBlank { "स्टाफ" }} ($roleDesc) | Active=${st.active}")
                }
            }
        } else {
            sb.appendLine("Staff Roster: अनुमति नहीं है / Unauthorized.")
        }

        return sb.toString()
    }

    internal fun isHealthSymptomQuestion(modeLabel: String, userPrompt: String): Boolean {
        val modeLower = modeLabel.lowercase()
        val promptLower = userPrompt.lowercase()
        if (modeLower.contains("स्वास्थ्य") || modeLower.contains("health") || modeLower.contains("vet")) return true
        val healthMarkers = listOf(
            "चारा नहीं", "चारा ठीक से नहीं", "खाना नहीं", "बीमार", "बुखार", "दस्त", "सूजन",
            "घाव", "लंगड़ा", "सुस्त", "उदास", "कमजोरी", "खून", "साँस", "सांस", "प्रसव", "थनैला",
            "आफरा", "अफारा", "पेट फूल", "खड़ी नहीं", "खड़े न हो", "उठ नहीं", "बेहोश", "टीका", "जुगाली",
            "chara nahi", "khana nahi", "bimar", "bukhar", "dast", "sujan", "ghav", "sust",
            "afara", "aafra", "pet phul", "saans", "khadi nahi", "behosh",
            "not eating", "sick", "fever", "diarrhea", "swelling", "wound", "limping", "symptom",
            "bloat", "breathing", "cannot stand", "calving", "mastitis", "vaccine"
        )
        return healthMarkers.any { promptLower.contains(it) }
    }

    private fun buildVetEightSectionRules(
        assessment: VetTriageAssessment,
        language: AppLanguage,
        hasPhoto: Boolean
    ): String {
        val emergencyDirective = if (assessment.requiresImmediateVetCall) {
            """
            CRITICAL EMERGENCY OVERRIDE DETECTED (${assessment.detectedRedFlagsHi.joinToString("; ")}):
            - You MUST set Section 3 (खतरे का स्तर) to "आपातकाल (EMERGENCY)".
            - Do NOT delay with lengthy home advice — instruct calling a qualified veterinarian IMMEDIATELY at the very top of Section 4 & Section 6.
            """.trimIndent()
        } else {
            "Baseline Triage Risk: ${assessment.riskLevel.labelHi} (${assessment.riskLevel.name}). Escalate to आपातकाल (EMERGENCY) if symptoms imply danger."
        }

        val followUpPrompt = if (assessment.missingQuestionsHi.isNotEmpty()) {
            val missingList = if (language == AppLanguage.HINDI) {
                assessment.missingQuestionsHi.take(5).joinToString(" | ")
            } else {
                assessment.missingQuestionsEn.take(5).joinToString(" | ")
            }
            "Relevant Follow-up Questions to ask caregiver concisely (ONLY what is missing): $missingList"
        } else {
            "All key clinical triage questions were already answered."
        }

        val photoSafetyNote = if (hasPhoto) {
            "PHOTO SAFETY: State clearly that photo analysis gives only possible observations — NEVER declare a definite/confirmed diagnosis from a photo alone ('यह फोटो आधारित विश्लेषण केवल प्रारंभिक सुझाव है, निश्चित बीमारी की घोषणा नहीं है')."
        } else {
            "NO-PHOTO SAFETY: State clearly that symptom analysis is preliminary guidance, not a confirmed medical diagnosis."
        }

        return """
            $emergencyDirective
            $followUpPrompt
            $photoSafetyNote

            GOOGLE SEARCH GROUNDING REQUIREMENT:
            Use Google Search to ground veterinary medical information prioritizing:
            1) Department of Animal Husbandry & Dairying (DAHD, Govt. of India - dahd.nic.in)
            2) ICAR & IVRI (icar.org.in, ivri.nic.in)
            3) NDDB (nddb.coop)
            4) Government Veterinary Universities in India
            5) Trusted Veterinary Medical Resources (Merck Veterinary Manual, FAO, WOAH)

            MANDATORY 8-SECTION VETERINARY HEALTH REPORT STRUCTURE (INCLUDE ALL 8 CLEAR NUMBERED HEADINGS):
            1. संभावित समस्या (Possible Problem — list possible conditions only; NEVER declare a definite diagnosis without vet clinical examination)
            2. इसके संभावित कारण (Possible Causes — practical cattle causes based on symptoms/records)
            3. खतरे का स्तर: सामान्य / सावधानी / आपातकाल (Risk Level — explicitly write one of: सामान्य / सावधानी / आपातकाल)
            4. अभी क्या प्राथमिक देखभाल करनी है (Immediate First Aid & Safe Primary Care — rest, shade, clean water/hygiene; if आपातकाल, prioritize calling vet immediately)
            5. क्या नहीं करना है (What NOT to Do — NEVER suggest antibiotics or injections without proper clinical diagnosis; NEVER set medication dosage without weight, age, pregnancy & vet supervision; NEVER suggest unsafe home remedies or force-feeding)
            6. पशु चिकित्सक को कब बुलाना है (When to Call Veterinarian — clear urgent signs & timeframe; mention प्रसव समस्या, सांस की तकलीफ, पेट फूलना/आफरा, खड़ी न हो पाना, बेहोशी, खून बहना)
            7. डॉक्टर से कौन-कौन सी जाँच करवानी चाहिए (Recommended Vet Diagnostic Tests — e.g., rectal temperature, rumen motility/auscultation, blood smear/CBC, dung/urine test, CMT for mastitis, calcium/metabolic check)
            8. विश्वसनीय जानकारी के स्रोत (Trusted Sources — cite authority name like DAHD/ICAR/IVRI/NDDB/Veterinary University, reference link if grounded, and date ${todayIsoDateString()})

            After Section 8, if important clinical details are still missing, add a brief "पशु की बेहतर जाँच हेतु अगले प्रश्न (Follow-up Questions)" list of 2-4 concise questions from the missing triage items above.
        """.trimIndent()
    }

    /**
     * Rich streaming response returning progressive text + live Google Search grounding citations
     * + deterministic triage risk level.
     */
    fun streamAssistantUpdates(
        userPrompt: String,
        roleContext: String,
        modeLabel: String,
        language: AppLanguage,
        recentHistory: List<Pair<Boolean, String>> = emptyList(),
        selectedCow: Cattle? = null
    ): Flow<AiStreamUpdate> = flow {
        val isHealth = isHealthSymptomQuestion(modeLabel, userPrompt)
        val assessment = if (isHealth) {
            VetTriageEngine.evaluate(symptomsText = userPrompt, selectedCow = selectedCow)
        } else null

        val langInstruction = if (language == AppLanguage.HINDI) {
            "हमेशा सरल, स्पष्ट और व्यावहारिक हिंदी (देवनागरी) में उत्तर दें (भले ही प्रश्न Hinglish या रोमन हिंदी में पूछा गया हो) ताकि गौशाला का कर्मचारी भी आसानी से समझ सके। छोटे बुलेट पॉइंट और चरणबद्ध (Step-by-step) तरीका अपनाएँ।"
        } else {
            "Respond clearly and concisely in simple English with respectful Indian Goshala terminology, using short bullet points and step-by-step guidance. Understand Hindi, Hinglish, and English inputs accurately."
        }
        val historyBlock = if (recentHistory.isNotEmpty()) {
            buildString {
                appendLine("RECENT CONVERSATION HISTORY:")
                recentHistory.takeLast(6).forEach { (isUser, text) ->
                    val roleTag = if (isUser) "User" else "Assistant"
                    appendLine("- $roleTag: ${text.take(400)}")
                }
            }
        } else ""

        val vetBlock = if (assessment != null) {
            buildVetEightSectionRules(assessment, language, hasPhoto = false)
        } else ""

        val fullPrompt = """
            Active Mode: $modeLabel
            $langInstruction
            $vetBlock

            CORE GOSHALA EXPERTISE AREAS (answer practically & step-by-step):
            1. गायों की वास्तविक संख्या के अनुसार दैनिक चारा प्रबंधन (हरा चारा, सूखा चारा/भूसा, दाना/खली, खनिज मिश्रण)
            2. प्रतिदिन स्वच्छ पानी की व्यवस्था व मौसम (गर्मी/सर्दी/बरसात) के अनुसार देखभाल
            3. गोशाला व शेड की नियमित सफाई, नाली प्रबंधन व मक्खी-मच्छर नियंत्रण
            4. टीकाकरण समय-सारणी व स्वास्थ्य रिकॉर्ड प्रबंधन
            5. बीमारी की रोकथाम, बीमार गाय व नवजात बछड़े की देखभाल
            6. कर्मचारियों की जिम्मेदारी, शिफ्ट व कार्य वितरण
            7. गोबर प्रबंधन, जैविक खाद / वर्मीकम्पोस्ट, कंडे, गौ-काष्ठ व धूपबत्ती निर्माण
            8. गौशाला खर्च कम करने व उत्पाद बिक्री से आय वृद्धि तथा सरकारी योजना मार्गदर्शन

            AUTHORIZED CONTEXT (VERIFIED FIREBASE APP DATA):
            $roleContext

            $historyBlock
            USER QUESTION:
            $userPrompt
        """.trimIndent()

        // Test hook path for deterministic Robolectric tests
        val hook = testStreamHandler
        if (hook != null) {
            val chunks = hook(userPrompt, roleContext, modeLabel)
            val testSources = testGroundedSourcesProvider?.invoke().orEmpty()
            val acc = StringBuilder()
            for (c in chunks) {
                acc.append(c)
                val finalRisk = assessment?.let { VetTriageEngine.resolveFinalRiskLevel(acc.toString(), it.riskLevel) }
                emit(
                    AiStreamUpdate(
                        text = acc.toString(),
                        sources = testSources,
                        riskLevel = finalRisk,
                        assessment = assessment,
                        usedGoogleSearch = isHealth || testSources.isNotEmpty()
                    )
                )
            }
            if (acc.isBlank()) {
                throw IllegalStateException("AI मॉडल से कोई उत्तर प्राप्त नहीं हुआ / Empty AI response.")
            }
            return@flow
        }

        // Choose grounded model for veterinary/health or scheme questions, with automatic graceful fallback
        val useGroundedFirst = isHealth ||
            userPrompt.contains("योजना", ignoreCase = true) ||
            userPrompt.contains("सरकारी", ignoreCase = true) ||
            userPrompt.contains("scheme", ignoreCase = true) ||
            userPrompt.contains("vaccine", ignoreCase = true) ||
            userPrompt.contains("टीका", ignoreCase = true)

        val candidateSpecs: List<ModelCandidateSpec> = if (useGroundedFirst) {
            listOf(
                ModelCandidateSpec(PRIMARY_MODEL_NAME, isGrounded = true, isFallback = false),
                ModelCandidateSpec(FALLBACK_LITE_MODEL_NAME, isGrounded = true, isFallback = true),
                ModelCandidateSpec(PRIMARY_MODEL_NAME, isGrounded = false, isFallback = false),
                ModelCandidateSpec(FALLBACK_LITE_MODEL_NAME, isGrounded = false, isFallback = true)
            )
        } else {
            listOf(
                ModelCandidateSpec(PRIMARY_MODEL_NAME, isGrounded = false, isFallback = false),
                ModelCandidateSpec(FALLBACK_LITE_MODEL_NAME, isGrounded = false, isFallback = true)
            )
        }

        var lastError: Throwable? = null
        var skipGroundedBecauseUnsupported = false
        for ((modelIndex, spec) in candidateSpecs.withIndex()) {
            if (skipGroundedBecauseUnsupported && spec.isGrounded) continue
            var appCheckRetried = false
            var attemptNumber = 0
            while (true) {
                attemptNumber++
                val accumulated = StringBuilder()
                val collectedSources = LinkedHashMap<String, VetSourceReference>()
                try {
                    val attemptRunner = testModelAttemptRunner
                    if (attemptRunner != null) {
                        val (textResult, srcList) = attemptRunner(spec, attemptNumber, fullPrompt)
                        srcList.forEach { collectedSources.putIfAbsent(it.url, it) }
                        accumulated.append(textResult)
                    } else {
                        val model = resolveCandidateModel(spec)
                        withTimeout(60_000L) {
                            model.generateContentStream(fullPrompt).collect { chunk ->
                                extractGroundingSources(chunk).forEach { src ->
                                    collectedSources.putIfAbsent(src.url, src)
                                }
                                val piece = chunk.text.orEmpty()
                                if (piece.isNotEmpty()) {
                                    accumulated.append(piece)
                                    val finalRisk = assessment?.let {
                                        VetTriageEngine.resolveFinalRiskLevel(accumulated.toString(), it.riskLevel)
                                    }
                                    emit(
                                        AiStreamUpdate(
                                            text = accumulated.toString(),
                                            sources = collectedSources.values.toList(),
                                            riskLevel = finalRisk,
                                            assessment = assessment,
                                            usedGoogleSearch = spec.isGrounded,
                                            modelUsed = spec.modelName,
                                            usedFallbackModel = spec.isFallback
                                        )
                                    )
                                }
                            }
                        }
                    }
                    if (accumulated.isBlank()) {
                        throw IllegalStateException("AI मॉडल से कोई उत्तर प्राप्त नहीं हुआ / Empty AI response.")
                    }
                    val finalRisk = assessment?.let {
                        VetTriageEngine.resolveFinalRiskLevel(accumulated.toString(), it.riskLevel)
                    }
                    emit(
                        AiStreamUpdate(
                            text = accumulated.toString(),
                            sources = collectedSources.values.toList(),
                            riskLevel = finalRisk,
                            assessment = assessment,
                            usedGoogleSearch = spec.isGrounded,
                            modelUsed = spec.modelName,
                            usedFallbackModel = spec.isFallback
                        )
                    )
                    return@flow
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (!appCheckRetried && accumulated.isEmpty() && isAppCheckTokenError(e)) {
                        Log.w(AI_REPO_TAG, "App Check token error during stream (model=${spec.modelName}, grounded=${spec.isGrounded}); forcing refresh and retrying once.", e)
                        performAppCheckRefresh(forceRefresh = true)
                        appCheckRetried = true
                        continue
                    }
                    val searchUnsupported = spec.isGrounded && isSearchToolUnsupportedError(e)
                    if (searchUnsupported) {
                        skipGroundedBecauseUnsupported = true
                    }
                    val hasNextModel = candidateSpecs.drop(modelIndex + 1).any { !skipGroundedBecauseUnsupported || !it.isGrounded }
                    if (accumulated.isEmpty() && hasNextModel && (searchUnsupported || isModelFallbackEligible(e))) {
                        Log.w(AI_REPO_TAG, "Model/tool fallback triggered from ${spec.modelName} (grounded=${spec.isGrounded}): ${e.javaClass.simpleName}: ${e.message}")
                        break
                    }
                    throw e
                }
            }
        }
        throw lastError ?: IllegalStateException("AI मॉडल से उत्तर प्राप्त नहीं हो सका।")
    }

    /**
     * Backwards-compatible text-only streaming API.
     */
    fun streamAssistantResponse(
        userPrompt: String,
        roleContext: String,
        modeLabel: String,
        language: AppLanguage,
        recentHistory: List<Pair<Boolean, String>> = emptyList()
    ): Flow<String> = flow {
        streamAssistantUpdates(
            userPrompt = userPrompt,
            roleContext = roleContext,
            modeLabel = modeLabel,
            language = language,
            recentHistory = recentHistory
        ).collect { update ->
            emit(update.text)
        }
    }

    /**
     * Generates a structured Daily, Weekly, or Monthly AI Work Plan with explicit Time/Slot, Period,
     * Task, Responsible Person, and Priority using Firebase AI Logic JSON Schema output.
     */
    suspend fun generateWorkPlan(
        roleContext: String,
        language: AppLanguage,
        focusTopic: String = "",
        planHorizon: String = "DAILY"
    ): Result<AiWorkPlan> {
        val normalizedHorizon = when (planHorizon.uppercase()) {
            "WEEKLY" -> "WEEKLY"
            "MONTHLY" -> "MONTHLY"
            else -> "DAILY"
        }
        val todayIso = QrAndExportUtils.todayIso()
        val hook = testWorkPlanGenerator
        if (hook != null) {
            return try {
                Result.success(hook(roleContext, "$normalizedHorizon:$focusTopic").copy(planHorizon = normalizedHorizon))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val langInstruction = if (language == AppLanguage.HINDI) {
            "सभी शीर्षक, सारांश, अवधि, कार्य और जिम्मेदार भूमिका सरल, व्यावहारिक हिंदी (देवनागरी) में लिखें।"
        } else {
            "Write title, summary, period, tasks, and responsible roles in simple, practical English (keeping priority schema enum values valid)."
        }

        val horizonInstruction = when (normalizedHorizon) {
            "WEEKLY" -> "7 दिनों की व्यवस्थित साप्ताहिक गौशाला कार्य योजना (सोमवार से रविवार) बनाएँ।"
            "MONTHLY" -> "4 सप्ताहों की व्यवस्थित मासिक गौशाला कार्य योजना (सप्ताह 1 से सप्ताह 4) बनाएँ।"
            else -> "आज के लिए सुबह, दोपहर और शाम की व्यवस्थित दैनिक गौशाला कार्य योजना बनाएँ।"
        }

        val focusInstruction = if (focusTopic.isNotBlank()) {
            "विशेष प्राथमिकता विषय (User Focus): $focusTopic"
        } else {
            "योजना में सभी 9 मुख्य गौशाला कार्य अवश्य शामिल करें: (1) सुबह के कार्य, (2) चारा और पानी, (3) गोशाला सफाई, (4) गायों का निरीक्षण, (5) गोबर संग्रह, (6) खाद निर्माण, (7) कर्मचारियों की जिम्मेदारी, (8) बिक्री एवं आय के कार्य, और (9) शाम का निरीक्षण।"
        }

        val prompt = """
            $langInstruction
            योजना प्रकार: $normalizedHorizon — $horizonInstruction
            $focusInstruction
            केवल नीचे दिए गए सत्यापित गौशाला रिकॉर्ड (गायों की संख्या, स्टाफ, चारा, उत्पाद) का उपयोग करें। यदि कोई डेटा उपलब्ध न हो तो उसे गढ़ें नहीं।
            हर कार्य में स्पष्ट समय/दिन (timeSlot), अवधि (period), कार्य (task), जिम्मेदार व्यक्ति (responsiblePerson), और प्राथमिकता (उच्च/मध्यम/सामान्य) दें।

            VERIFIED GOSHALA CONTEXT:
            $roleContext
        """.trimIndent()

        val defaultTitle = when (normalizedHorizon) {
            "WEEKLY" -> if (language == AppLanguage.HINDI) "साप्ताहिक AI गौशाला कार्य योजना" else "Weekly AI Goshala Work Plan"
            "MONTHLY" -> if (language == AppLanguage.HINDI) "मासिक AI गौशाला कार्य योजना" else "Monthly AI Goshala Work Plan"
            else -> if (language == AppLanguage.HINDI) "आज की AI गौशाला कार्य योजना" else "Daily AI Goshala Work Plan"
        }

        val workPlanModels = listOf(workPlanModel, fallbackWorkPlanModel)
        var lastError: Throwable? = null
        for ((idx, model) in workPlanModels.withIndex()) {
            var attempt = 0
            while (true) {
                try {
                    val response = withTimeout(60_000L) {
                        model.generateContent(prompt)
                    }
                    val rawJson = response.text ?: throw IllegalStateException("AI कार्य योजना से कोई उत्तर प्राप्त नहीं हुआ।")
                    val json = org.json.JSONObject(rawJson)
                    val title = json.optString("title", defaultTitle).ifBlank { defaultTitle }
                    val summary = json.optString("summary", "")
                    val tasksArray = json.optJSONArray("tasks")
                    val tasks = buildList {
                        if (tasksArray != null) {
                            for (i in 0 until tasksArray.length()) {
                                val obj = tasksArray.optJSONObject(i) ?: continue
                                val taskText = obj.optString("task").trim()
                                if (taskText.isBlank()) continue
                                add(
                                    AiWorkPlanItem(
                                        timeSlot = obj.optString("timeSlot", "-").trim(),
                                        period = obj.optString("period", if (normalizedHorizon == "DAILY") "सुबह" else "चरण ${i + 1}").trim(),
                                        task = taskText,
                                        responsiblePerson = obj.optString("responsiblePerson", "गौशाला स्टाफ").trim(),
                                        priority = obj.optString("priority", "मध्यम").trim(),
                                        category = obj.optString("category", "प्रबंधन").trim()
                                    )
                                )
                            }
                        }
                    }
                    if (tasks.isEmpty()) {
                        throw IllegalStateException("कार्य योजना में कोई कार्य प्राप्त नहीं हुआ। कृपया पुनः प्रयास करें।")
                    }
                    return Result.success(
                        AiWorkPlan(
                            dateLabel = todayIso,
                            title = title,
                            summary = summary,
                            tasks = tasks,
                            planHorizon = normalizedHorizon
                        )
                    )
                } catch (e: TimeoutCancellationException) {
                    return Result.failure(Exception("AI कार्य योजना बनाने में समय समाप्त हो गया (60 सेकंड)। कृपया पुनः प्रयास करें।", e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 0 && isAppCheckTokenError(e)) {
                        Log.w(AI_REPO_TAG, "App Check token error during work plan; forcing refresh and retrying once.", e)
                        refreshAppCheckToken(forceRefresh = true)
                        attempt++
                        continue
                    }
                    if (idx < workPlanModels.lastIndex && isModelFallbackEligible(e)) {
                        Log.w(AI_REPO_TAG, "Work plan falling back to lite model: ${e.message}")
                        break
                    }
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("AI कार्य योजना प्राप्त नहीं हो सकी।"))
    }

    /**
     * Generates a complete 8-section Google-Search-grounded AI Veterinary Health Report
     * combining: optional cow photo + symptoms + 10-question triage answers + strictly isolated
     * selected cow Firebase history + deterministic emergency red-flag safety checks.
     */
    suspend fun generateVetHealthReport(
        bitmap: Bitmap?,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        savedVetReports: List<AiVetHealthReport> = emptyList(),
        triageAnswers: VetTriageAnswers = VetTriageAnswers(),
        isPublic: Boolean = false
    ): Result<AiVetReportResult> {
        val cleanNotes = notes.trim()
        val triageSummary = triageAnswers.toReadableSummary(language)
        if (bitmap == null && cleanNotes.isBlank() && triageSummary.isBlank()) {
            return Result.failure(IllegalArgumentException("कृपया गाय के लक्षण लिखें, जाँच बिंदु चुनें या फोटो अपलोड करें।"))
        }

        val assessment = VetTriageEngine.evaluate(
            symptomsText = cleanNotes,
            triageAnswers = triageAnswers,
            selectedCow = selectedCow
        )

        val langPrompt = if (language == AppLanguage.HINDI) {
            "पूरा उत्तर सरल, स्पष्ट और आसान हिंदी (देवनागरी) में दें (भले ही लक्षण Hinglish में लिखे हों) ताकि गौशाला कर्मचारी आसानी से समझ सके।"
        } else {
            "Provide the complete report in clear, simple English."
        }
        val cowRecordBlock = if (selectedCow != null) {
            buildSelectedCowContext(
                cow = selectedCow,
                healthRecords = cowHealthRecords,
                isPublic = isPublic,
                savedVetReports = savedVetReports
            )
        } else {
            "SELECTED COW RECORD: कोई गाय रिकॉर्ड चयनित नहीं किया गया है (Only caregiver symptoms/photo provided)."
        }

        val photoChecklistBlock = if (bitmap != null) {
            """
            VISUAL INSPECTION CHECKLIST FOR PROVIDED COW PHOTO:
            - शरीर की स्थिति व कमजोरी (Body condition / weakness / dehydration)
            - आँख (Eyes - discharge, redness, sunken eyes, dullness)
            - नाक व मुँह (Nose & mouth - discharge, frothing, lesions, drooling)
            - त्वचा व बाल (Skin & coat - lumps, LSD nodules, rashes, ticks, hair loss)
            - पेट / बायाँ भाग (Abdomen / left flank distension for bloat/आफरा)
            - घाव या सूजन (Wounds, bleeding, udder/joint/leg swelling)
            - खड़े होने या चलने की स्थिति (Posture, drooping head/ears, lameness, inability to stand)
            """.trimIndent()
        } else ""

        val eightSectionsBlock = buildVetEightSectionRules(
            assessment = assessment,
            language = language,
            hasPhoto = bitmap != null
        )

        val promptText = """
            You are the Advanced AI Veterinary Assistant for ${GosalaBrand.NAME_EN} (${GosalaBrand.NAME_HI}, मेहदोरा, पोरसा, मुरैना, मध्य प्रदेश).
            $langPrompt

            $cowRecordBlock

            CAREGIVER SYMPTOMS / NOTES:
            ${cleanNotes.ifBlank { "केवल फोटो / जाँच बिंदु प्रदान किए गए हैं" }}

            ${if (triageSummary.isNotBlank()) "CLINICAL TRIAGE ANSWERS:\n$triageSummary" else ""}

            $photoChecklistBlock

            $eightSectionsBlock
        """.trimIndent()

        // Test hooks for deterministic Robolectric unit testing
        if (bitmap != null) {
            testPhotoAnalyzer?.let { hook ->
                return try {
                    val text = hook(bitmap, promptText)
                    if (text.isBlank()) Result.failure(IllegalStateException("AI returned an empty image analysis."))
                    else {
                        val finalRisk = VetTriageEngine.resolveFinalRiskLevel(text, assessment.riskLevel)
                        Result.success(
                            AiVetReportResult(
                                text = text,
                                sources = testGroundedSourcesProvider?.invoke().orEmpty(),
                                riskLevel = finalRisk,
                                assessment = assessment.copy(
                                    riskLevel = finalRisk,
                                    requiresImmediateVetCall = finalRisk == VetRiskLevel.EMERGENCY
                                ),
                                usedGoogleSearch = true
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        } else {
            testSymptomHealthAnalyzer?.let { hook ->
                return try {
                    val text = hook(promptText)
                    if (text.isBlank()) Result.failure(IllegalStateException("AI स्वास्थ्य रिपोर्ट से कोई उत्तर प्राप्त नहीं हुआ।"))
                    else {
                        val finalRisk = VetTriageEngine.resolveFinalRiskLevel(text, assessment.riskLevel)
                        Result.success(
                            AiVetReportResult(
                                text = text,
                                sources = testGroundedSourcesProvider?.invoke().orEmpty(),
                                riskLevel = finalRisk,
                                assessment = assessment.copy(
                                    riskLevel = finalRisk,
                                    requiresImmediateVetCall = finalRisk == VetRiskLevel.EMERGENCY
                                ),
                                usedGoogleSearch = true
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }

        // Live Firebase AI Logic call with Google Search Grounding + graceful model/tool/AppCheck fallback
        val candidateSpecs: List<ModelCandidateSpec> = listOf(
            ModelCandidateSpec(PRIMARY_MODEL_NAME, isGrounded = true, isFallback = false),
            ModelCandidateSpec(FALLBACK_LITE_MODEL_NAME, isGrounded = true, isFallback = true),
            ModelCandidateSpec(PRIMARY_MODEL_NAME, isGrounded = false, isFallback = false),
            ModelCandidateSpec(FALLBACK_LITE_MODEL_NAME, isGrounded = false, isFallback = true)
        )
        var lastError: Throwable? = null
        var skipGroundedBecauseUnsupported = false
        for ((idx, spec) in candidateSpecs.withIndex()) {
            if (skipGroundedBecauseUnsupported && spec.isGrounded) continue
            var appCheckRetried = false
            var attemptNumber = 0
            while (true) {
                attemptNumber++
                try {
                    val attemptRunner = testModelAttemptRunner
                    val text: String
                    val sources: List<VetSourceReference>
                    if (attemptRunner != null) {
                        val (resText, resSources) = attemptRunner(spec, attemptNumber, promptText)
                        text = resText
                        sources = resSources
                    } else {
                        val model = resolveCandidateModel(spec)
                        val response = withTimeout(60_000L) {
                            if (bitmap != null) {
                                model.generateContent(
                                    content {
                                        image(bitmap)
                                        text(promptText)
                                    }
                                )
                            } else {
                                model.generateContent(promptText)
                            }
                        }
                        text = response.text.orEmpty()
                        sources = extractGroundingSources(response)
                    }
                    if (text.isBlank()) {
                        throw IllegalStateException("AI स्वास्थ्य रिपोर्ट से कोई उत्तर प्राप्त नहीं हुआ।")
                    }
                    val finalRisk = VetTriageEngine.resolveFinalRiskLevel(text, assessment.riskLevel)
                    return Result.success(
                        AiVetReportResult(
                            text = text,
                            sources = sources,
                            riskLevel = finalRisk,
                            assessment = assessment.copy(
                                riskLevel = finalRisk,
                                requiresImmediateVetCall = finalRisk == VetRiskLevel.EMERGENCY
                            ),
                            usedGoogleSearch = spec.isGrounded,
                            modelUsed = spec.modelName,
                            usedFallbackModel = spec.isFallback
                        )
                    )
                } catch (e: TimeoutCancellationException) {
                    return Result.failure(Exception("AI स्वास्थ्य रिपोर्ट तैयार करने में समय समाप्त हो गया (60 सेकंड)। कृपया पुनः प्रयास करें।", e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (!appCheckRetried && isAppCheckTokenError(e)) {
                        Log.w(AI_REPO_TAG, "App Check token error during vet report (model=${spec.modelName}); forcing refresh and retrying once.", e)
                        performAppCheckRefresh(forceRefresh = true)
                        appCheckRetried = true
                        continue
                    }
                    val searchUnsupported = spec.isGrounded && isSearchToolUnsupportedError(e)
                    if (searchUnsupported) {
                        skipGroundedBecauseUnsupported = true
                    }
                    val hasNext = candidateSpecs.drop(idx + 1).any { !skipGroundedBecauseUnsupported || !it.isGrounded }
                    if (hasNext && (searchUnsupported || isModelFallbackEligible(e))) {
                        Log.w(AI_REPO_TAG, "Vet report model/tool fallback from ${spec.modelName} (grounded=${spec.isGrounded}): ${e.javaClass.simpleName}: ${e.message}")
                        break
                    }
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("AI स्वास्थ्य रिपोर्ट प्राप्त नहीं हो सकी।"))
    }

    /** Backwards-compatible wrapper returning plain text Result<String>. */
    suspend fun analyzeHealthGuidance(
        bitmap: Bitmap?,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false
    ): Result<String> = generateVetHealthReport(
        bitmap = bitmap,
        notes = notes,
        language = language,
        selectedCow = selectedCow,
        cowHealthRecords = cowHealthRecords,
        isPublic = isPublic
    ).map { it.text }

    /** Backwards-compatible wrapper for photo-based veterinary analysis. */
    suspend fun analyzeCattlePhoto(
        bitmap: Bitmap,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false
    ): Result<String> = generateVetHealthReport(
        bitmap = bitmap,
        notes = notes,
        language = language,
        selectedCow = selectedCow,
        cowHealthRecords = cowHealthRecords,
        isPublic = isPublic
    ).map { it.text }

    suspend fun extractVoiceEntryDraft(spokenText: String): Result<VoiceEntryDraft> {
        val models = listOf(structuredModel, fallbackStructuredModel)
        var lastError: Throwable? = null
        for ((idx, model) in models.withIndex()) {
            var attempt = 0
            while (true) {
                try {
                    val prompt = """
                        Convert this Hindi/English spoken Goshala input into a structured draft JSON.
                        Do not invent numbers that were not spoken; use 0 if unspecified.
                        Spoken text: "$spokenText"
                    """.trimIndent()
                    val response = withTimeout(60_000L) {
                        model.generateContent(prompt)
                    }
                    val rawJson = response.text ?: throw IllegalStateException("Empty structured response")
                    val json = org.json.JSONObject(rawJson)
                    return Result.success(
                        VoiceEntryDraft(
                            draftType = json.optString("draftType", "FEED_LOG"),
                            title = json.optString("title", spokenText.take(60)),
                            quantity = json.optDouble("quantity", 0.0),
                            amountInr = json.optDouble("amountInr", 0.0),
                            notes = json.optString("notes", spokenText),
                            searchQuery = json.optString("searchQuery", "")
                        )
                    )
                } catch (e: TimeoutCancellationException) {
                    return Result.failure(Exception("वॉइस ड्राफ्ट बनाने में समय समाप्त हुआ।", e))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                    if (attempt == 0 && isAppCheckTokenError(e)) {
                        Log.w(AI_REPO_TAG, "App Check token error during voice draft; forcing refresh and retrying once.", e)
                        refreshAppCheckToken(forceRefresh = true)
                        attempt++
                        continue
                    }
                    if (idx < models.lastIndex && isModelFallbackEligible(e)) {
                        Log.w(AI_REPO_TAG, "Voice draft falling back to lite model: ${e.message}")
                        break
                    }
                    return Result.failure(e)
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("वॉइस ड्राफ्ट तैयार नहीं हो सका।"))
    }
}

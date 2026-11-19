package com.example.data.repository

import android.graphics.Bitmap
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

data class VoiceEntryDraft(
    val draftType: String,
    val title: String,
    val quantity: Double,
    val amountInr: Double,
    val notes: String,
    val searchQuery: String
)

class GeminiAiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
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
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = voiceDraftSchema
            }
        )
    }

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
        donations: List<Donation>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("Organization: ${GosalaBrand.NAME_HI} (${GosalaBrand.NAME_EN})")
        sb.appendLine("Address: ${GosalaBrand.ADDRESS_HI}")
        sb.appendLine("Logged-in Role: ${session.role.name}")

        if (session.role == UserRole.PUBLIC) {
            sb.appendLine("PUBLIC ACCESS ONLY: Never reveal or invent internal finances, staff salaries, farmer ledgers, or private medical records.")
            sb.appendLine("Public Summary: Total Cattle=${summary.totalCattle}, Healthy=${summary.healthyCattle}, Public Approved Cattle=${cattle.size}.")
            if (cattle.isNotEmpty()) {
                sb.appendLine("Approved Public Cows for Sponsorship:")
                cattle.take(15).forEach {
                    sb.appendLine("- ${it.name} (${it.animalCode}), Breed: ${it.breed}, Age: ${it.ageText}, Monthly Support: ₹${it.monthlySupportInr}")
                }
            }
            return sb.toString()
        }

        if (session.canView(AppModule.CATTLE)) {
            val healthy = cattle.count { it.healthStatus == "HEALTHY" }
            val sick = cattle.count { it.healthStatus != "HEALTHY" }
            sb.appendLine("Cattle Registry: Total=${cattle.size}, Healthy=$healthy, Attention/Sick=$sick")
            cattle.take(20).forEach {
                sb.appendLine("  * ${it.name} [${it.animalCode}] (${it.category}, ${it.breed}, Shed ${it.shedNumber}, Status: ${it.healthStatus}, Weight: ${it.weightKg}kg)")
            }
        }
        if (session.canView(AppModule.HEALTH)) {
            sb.appendLine("Health Records (${health.size} total):")
            health.take(15).forEach {
                sb.appendLine("  * ${it.cattleName} (${it.recordType}): ${it.title}, Med: ${it.medicine}, Cost: ₹${it.costInr}, FollowUp: ${it.nextFollowUpDate}")
            }
        }
        if (session.canView(AppModule.FEED)) {
            val totalGreen = feedLogs.sumOf { it.greenFodderKg }
            val totalDry = feedLogs.sumOf { it.dryFodderBhusaKg }
            val latestStock = feedLogs.firstOrNull()?.feedStockRemainingKg ?: 0.0
            val pendingTasks = feedLogs.count { it.taskTitle.isNotBlank() && !it.taskCompleted }
            sb.appendLine("Feed & Operations (${feedLogs.size} logs): Total Green=${totalGreen}kg, Total Dry/Bhusa=${totalDry}kg, Latest Stock=${latestStock}kg, Pending Tasks=$pendingTasks")
        }
        if (session.canView(AppModule.FINANCE)) {
            val income = finance.filter { it.txType == "INCOME" }.sumOf { it.amountInr }
            val expense = finance.filter { it.txType == "EXPENSE" }.sumOf { it.amountInr }
            val pending = finance.filter { it.paymentStatus == "PENDING" }.sumOf { it.amountInr }
            sb.appendLine("Finance (${finance.size} tx): Total Income=₹$income, Total Expense=₹$expense, Pending=₹$pending")
        }
        if (session.canView(AppModule.FARMERS)) {
            val purchased = farmerTx.filter { it.direction == "PURCHASE_FROM_FARMER" }.sumOf { it.totalAmountInr }
            val sold = farmerTx.filter { it.direction == "SALE_TO_FARMER" }.sumOf { it.totalAmountInr }
            sb.appendLine("Farmer Cooperation: Farmers=${farmers.size}, Fodder Purchased=₹$purchased, Products Sold=₹$sold")
        }
        if (session.canView(AppModule.PRODUCTION)) {
            val totalProdCost = compost.sumOf { it.productionCostInr + it.packagingCostInr }
            val totalSalesVal = compost.sumOf { it.soldQuantity * it.sellingPricePerUnitInr }
            val stockUnits = compost.sumOf { it.stockRemaining }
            sb.appendLine("Compost & Production (${compost.size} batches): Prod+Pack Cost=₹$totalProdCost, Sales Revenue=₹$totalSalesVal, Stock Remaining=$stockUnits")
        }
        if (session.canView(AppModule.DONATIONS)) {
            val verifiedSum = donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
            val pendingCount = donations.count { it.paymentStatus == "PENDING_VERIFICATION" }
            sb.appendLine("Donations (${donations.size}): Verified=₹$verifiedSum, Pending Verification=$pendingCount")
        }
        return sb.toString()
    }

    fun streamAssistantResponse(
        userPrompt: String,
        roleContext: String,
        modeLabel: String,
        language: AppLanguage
    ): Flow<String> = flow {
        val langInstruction = if (language == AppLanguage.HINDI) {
            "Respond clearly and respectfully in simple Hindi (Devanagari). Use bullet points."
        } else {
            "Respond clearly and concisely in English with respectful Indian Goshala terminology."
        }
        val fullPrompt = """
            You are the AI Assistant for ${GosalaBrand.NAME_HI} (${GosalaBrand.ADDRESS_HI}).
            Mode: $modeLabel
            $langInstruction
            STRICT SAFETY RULES:
            1. Use ONLY the authorized Goshala data below. Never invent cattle records, amounts, payments, or medical facts.
            2. If data for a question is missing or unauthorized for this role, clearly say so.
            3. Never claim to delete records, approve donations, or modify permissions automatically.
            4. For animal health questions, always recommend consulting a qualified veterinarian when symptoms are concerning.

            AUTHORIZED CONTEXT:
            $roleContext

            USER QUESTION:
            $userPrompt
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                chatModel.generateContentStream(fullPrompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("AI मॉडल से कोई उत्तर प्राप्त नहीं हुआ / Empty AI response.")
            }
        } catch (e: CancellationException) {
            throw e
        }
    }

    suspend fun analyzeCattlePhoto(
        bitmap: Bitmap,
        notes: String,
        language: AppLanguage
    ): Result<String> {
        return try {
            val langPrompt = if (language == AppLanguage.HINDI) {
                "सरल हिंदी (देवनागरी) में उत्तर दें।"
            } else {
                "Respond in clear English."
            }
            val response = withTimeout(60_000L) {
                chatModel.generateContent(
                    content {
                        image(bitmap)
                        text(
                            """
                            You are assisting cattle caregivers at ${GosalaBrand.NAME_HI}.
                            Inspect this cow/calf photograph carefully. $langPrompt
                            1. Describe visible physical observations calmly.
                            2. Mention possible visible concerns if any (skin, wound, posture, swelling, eyes, cleanliness) AND clearly explain uncertainty because a photo cannot replace clinical examination.
                            3. Provide safe first-aid/hygiene care tips.
                            4. Strongly advise veterinary doctor consultation if symptoms look concerning or persist.
                            Caregiver notes: $notes
                            """.trimIndent()
                        )
                    }
                )
            }
            val text = response.text
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("AI returned an empty image analysis."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI विश्लेषण का समय समाप्त हो गया (60s)। कृपया पुनः प्रयास करें।", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractVoiceEntryDraft(spokenText: String): Result<VoiceEntryDraft> {
        return try {
            val prompt = """
                Convert this Hindi/English spoken Goshala input into a structured draft JSON.
                Do not invent numbers that were not spoken; use 0 if unspecified.
                Spoken text: "$spokenText"
            """.trimIndent()
            val response = withTimeout(60_000L) {
                structuredModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("Empty structured response")
            val json = org.json.JSONObject(rawJson)
            Result.success(
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
            Result.failure(Exception("वॉइस ड्राफ्ट बनाने में समय समाप्त हुआ।", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

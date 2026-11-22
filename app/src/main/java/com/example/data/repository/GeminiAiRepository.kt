package com.example.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.core.isAppCheckTokenError
import com.example.core.refreshAppCheckToken
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

private const val AI_REPO_TAG = "GeminiAiRepository"

internal val GOSHALA_SYSTEM_INSTRUCTION = """
आप Jay Bhole Gosala Samiti के AI गौशाला प्रबंधन सहायक हैं।
आपका कार्य सरल, स्पष्ट और व्यावहारिक हिंदी में उत्तर देना है।
आप गौशाला प्रबंधन, गाय देखभाल, चारा प्रबंधन, सफाई, स्वास्थ्य सावधानी, गोबर उत्पाद, खर्च नियंत्रण और दैनिक कार्य योजना में सहायता करेंगे।
आप केवल सलाह देंगे, किसी डेटाबेस रिकॉर्ड में अपने आप बदलाव नहीं करेंगे।
गंभीर बीमारी, चोट, रक्तस्राव, सांस लेने में दिक्कत या प्रसव संबंधी समस्या में हमेशा योग्य पशु चिकित्सक से तुरंत संपर्क करने की सलाह देंगे।
आप कभी भी खतरनाक दवा या निश्चित खुराक तय नहीं करेंगे।
आप केवल उपलब्ध सही डेटा का ही उपयोग करेंगे और अधूरी जानकारी को कभी गढ़ेंगे नहीं।

अतिरिक्त अनिवार्य नियम:
- यदि कोई डेटा या रिकॉर्ड उपलब्ध नहीं है, तो स्पष्ट लिखें: "रिकॉर्ड उपलब्ध नहीं है"। कभी अनुमान से आँकड़े न बनाएँ।
- यदि उपयोगकर्ता की भूमिका PUBLIC है, तो कभी भी आंतरिक वित्त, खर्च, स्टाफ वेतन, किसान खाता या निजी चिकित्सा रिकॉर्ड न दिखाएँ और न अनुमान लगाएँ।
- यदि उपयोगकर्ता अंग्रेजी चुने, तो सरल अंग्रेजी में उत्तर दें; अन्यथा हमेशा सरल, सम्मानजनक हिंदी (देवनागरी) में छोटे बुलेट पॉइंट व चरणबद्ध उत्तर दें।
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
    val tasks: List<AiWorkPlanItem>
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
        internal var testPhotoAnalyzer: (suspend (bitmap: Bitmap, promptText: String) -> String)? = null

        @Volatile
        internal var testWorkPlanGenerator: (suspend (roleContext: String, focusTopic: String) -> AiWorkPlan)? = null
    }

    private val systemInstructionContent by lazy {
        content { text(GOSHALA_SYSTEM_INSTRUCTION) }
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = systemInstructionContent
        )
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
            },
            systemInstruction = systemInstructionContent
        )
    }

    private val workPlanSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short title of the daily goshala work plan"),
            "summary" to Schema.string(description = "1-2 sentence practical summary based only on verified goshala records"),
            "tasks" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "timeSlot" to Schema.string(description = "Clear time slot e.g. 05:30 - 07:00"),
                        "period" to Schema.enumeration(
                            values = listOf("सुबह", "दोपहर", "शाम"),
                            description = "Part of day: सुबह, दोपहर, or शाम"
                        ),
                        "task" to Schema.string(description = "Concrete, actionable goshala task description"),
                        "responsiblePerson" to Schema.string(description = "Responsible role/staff e.g. गौशाला सेवक / चारा प्रभारी / पशु देखभाल कर्मी / प्रबंधक"),
                        "priority" to Schema.enumeration(
                            values = listOf("उच्च", "मध्यम", "सामान्य"),
                            description = "Priority level: उच्च, मध्यम, or सामान्य"
                        ),
                        "category" to Schema.string(description = "Category such as चारा, सफाई, स्वास्थ्य, खाद-उत्पाद, या जांच")
                    )
                ),
                description = "5 to 8 ordered daily tasks covering morning, afternoon, and evening"
            )
        )
    )

    private val workPlanModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = workPlanSchema
            },
            systemInstruction = systemInstructionContent
        )
    }

    /**
     * Builds a strictly read-only summary for a specific cow so AI can answer health/history
     * questions or combine cow records with photo analysis without ever modifying or inventing records.
     */
    fun buildSelectedCowContext(
        cow: Cattle,
        healthRecords: List<HealthRecord>,
        isPublic: Boolean
    ): String {
        val sb = StringBuilder()
        val notesText = cow.notes?.trim().orEmpty()
        val hasPregnancyNote = notesText.contains("गर्भवती") ||
            notesText.contains("गर्भावस्था") ||
            notesText.contains("pregnan", ignoreCase = true)
        val hasMilkNote = notesText.contains("दूध") ||
            notesText.contains("लीटर") ||
            notesText.contains("milk", ignoreCase = true)

        sb.appendLine("SELECTED COW RECORD (READ-ONLY — NEVER MODIFY OR FABRICATE):")
        sb.appendLine("- Name: ${cow.name.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}")
        sb.appendLine("- Cow ID / Animal Code: ${cow.animalCode.ifBlank { cow.id }} (DocID: ${cow.id.ifBlank { "N/A" }})")
        sb.appendLine("- Category: ${cow.category}, Breed: ${cow.breed.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Age: ${cow.ageText.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Weight: ${if (cow.weightKg > 0.0) "${cow.weightKg} kg" else "रिकॉर्ड उपलब्ध नहीं है"}")
        sb.appendLine("- Health Status: ${cow.healthStatus}, Shed: ${cow.shedNumber.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}, Entry Date: ${cow.entryDate.ifBlank { "रिकॉर्ड उपलब्ध नहीं है" }}")
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

        val matchingHealth = healthRecords.filter { rec ->
            (cow.id.isNotBlank() && rec.cattleId == cow.id) ||
                (cow.animalCode.isNotBlank() && rec.animalCode.equals(cow.animalCode, ignoreCase = true)) ||
                (cow.name.isNotBlank() && rec.cattleName.equals(cow.name, ignoreCase = true))
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
        selectedCow: Cattle? = null
    ): String {
        val sb = StringBuilder()
        val todayIso = QrAndExportUtils.todayIso()
        sb.appendLine("Organization: ${GosalaBrand.NAME_HI} (${GosalaBrand.NAME_EN})")
        sb.appendLine("Address: ${GosalaBrand.ADDRESS_HI}")
        sb.appendLine("Today Date: $todayIso")
        sb.appendLine("Logged-in Role: ${session.role.name}")

        if (selectedCow != null) {
            sb.appendLine()
            sb.appendLine(buildSelectedCowContext(selectedCow, health, isPublic = session.role == UserRole.PUBLIC))
        }

        if (session.role == UserRole.PUBLIC) {
            sb.appendLine()
            sb.appendLine("PUBLIC ACCESS ONLY: Never reveal or invent internal finances, staff salaries, farmer ledgers, or private medical records.")
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

        return sb.toString()
    }

    fun streamAssistantResponse(
        userPrompt: String,
        roleContext: String,
        modeLabel: String,
        language: AppLanguage,
        recentHistory: List<Pair<Boolean, String>> = emptyList()
    ): Flow<String> = flow {
        val langInstruction = if (language == AppLanguage.HINDI) {
            "हमेशा सरल, स्पष्ट और व्यावहारिक हिंदी (देवनागरी) में उत्तर दें ताकि गौशाला का कर्मचारी भी आसानी से समझ सके। छोटे बुलेट पॉइंट और चरणबद्ध (Step-by-step) तरीका अपनाएँ।"
        } else {
            "Respond clearly and concisely in simple English with respectful Indian Goshala terminology, using short bullet points and step-by-step guidance."
        }
        val historyBlock = if (recentHistory.isNotEmpty()) {
            buildString {
                appendLine("RECENT CONVERSATION HISTORY:")
                recentHistory.takeLast(6).forEach { (isUser, text) ->
                    val roleTag = if (isUser) "User" else "Assistant"
                    appendLine("- $roleTag: ${text.take(400)}")
                }
            }
        } else {
            ""
        }

        val fullPrompt = """
            Active Mode: $modeLabel
            $langInstruction

            CORE EXPERTISE AREAS:
            - Daily fodder calculation (हरा चारा, सूखा चारा/भूसा, दाना, पानी) based on cow/bull/calf count and season
            - Sick cow care, diarrhea precautions, loss of appetite causes, calf care, vaccination schedule, shed cleanliness, summer/winter care
            - Cow dung utilization: vermicompost/organic manure, kande (गोबर के कंडे), gau-kashtha (गोबर की लकड़ी), natural dhoopbatti, packaging & sales growth
            - Farmer fodder purchasing tips, cost reduction, daily routine & staff task planning
            - Reading selected cow's profile, past illness, vaccination & treatment records when available

            AUTHORIZED CONTEXT (VERIFIED APP DATA):
            $roleContext

            $historyBlock
            USER QUESTION:
            $userPrompt
        """.trimIndent()

        // Test hook path for deterministic Robolectric tests
        val hook = testStreamHandler
        if (hook != null) {
            val chunks = hook(userPrompt, roleContext, modeLabel)
            val acc = StringBuilder()
            for (c in chunks) {
                acc.append(c)
                emit(acc.toString())
            }
            if (acc.isBlank()) {
                throw IllegalStateException("AI मॉडल से कोई उत्तर प्राप्त नहीं हुआ / Empty AI response.")
            }
            return@flow
        }

        // Live Firebase AI Logic streaming — let SDK use installed App Check provider natively,
        // and only force-refresh & retry once if an App Check token error occurs.
        var attempt = 0
        while (true) {
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
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0 && accumulated.isEmpty() && isAppCheckTokenError(e)) {
                    Log.w(AI_REPO_TAG, "App Check token error during chat stream; forcing token refresh and retrying once.", e)
                    refreshAppCheckToken(forceRefresh = true)
                    attempt++
                    continue
                }
                throw e
            }
        }
    }

    /**
     * Generates a structured daily AI Work Plan with explicit Time, Period, Task, Responsible Person,
     * and Priority using Firebase AI Logic JSON Schema output (`responseSchema`).
     */
    suspend fun generateWorkPlan(
        roleContext: String,
        language: AppLanguage,
        focusTopic: String = ""
    ): Result<AiWorkPlan> {
        val todayIso = QrAndExportUtils.todayIso()
        val hook = testWorkPlanGenerator
        if (hook != null) {
            return try {
                Result.success(hook(roleContext, focusTopic))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        val langInstruction = if (language == AppLanguage.HINDI) {
            "सभी शीर्षक, सारांश, कार्य और जिम्मेदार भूमिका सरल, व्यावहारिक हिंदी (देवनागरी) में लिखें।"
        } else {
            "Write title, summary, tasks, and responsible roles in simple, practical English (keeping period/priority schema enum values valid)."
        }

        val focusInstruction = if (focusTopic.isNotBlank()) {
            "विशेष प्राथमिकता विषय (User Focus): $focusTopic"
        } else {
            "आज के लिए सुबह, दोपहर और शाम की पूर्ण गौशाला दैनिक कार्य योजना बनाएँ (चारा, पानी, सफाई, बीमार गाय जांच, गोबर खाद/उत्पाद, और शाम की जांच शामिल करें)।"
        }

        val prompt = """
            $langInstruction
            $focusInstruction
            केवल नीचे दिए गए सत्यापित गौशाला रिकॉर्ड का उपयोग करें। यदि कोई डेटा उपलब्ध न हो तो उसे गढ़ें नहीं।
            हर कार्य में स्पष्ट समय (timeSlot), अवधि (सुबह/दोपहर/शाम), कार्य (task), जिम्मेदार व्यक्ति (responsiblePerson), और प्राथमिकता (उच्च/मध्यम/सामान्य) दें।

            VERIFIED GOSHALA CONTEXT:
            $roleContext
        """.trimIndent()

        var attempt = 0
        while (true) {
            try {
                val response = withTimeout(60_000L) {
                    workPlanModel.generateContent(prompt)
                }
                val rawJson = response.text ?: throw IllegalStateException("AI कार्य योजना से कोई उत्तर प्राप्त नहीं हुआ।")
                val json = org.json.JSONObject(rawJson)
                val title = json.optString("title", "आज की AI गौशाला कार्य योजना").ifBlank { "आज की AI गौशाला कार्य योजना" }
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
                                    period = obj.optString("period", "सुबह").trim(),
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
                        tasks = tasks
                    )
                )
            } catch (e: TimeoutCancellationException) {
                return Result.failure(Exception("AI कार्य योजना बनाने में समय समाप्त हो गया (60 सेकंड)। कृपया पुनः प्रयास करें।", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0 && isAppCheckTokenError(e)) {
                    Log.w(AI_REPO_TAG, "App Check token error during work plan; forcing refresh and retrying once.", e)
                    refreshAppCheckToken(forceRefresh = true)
                    attempt++
                    continue
                }
                return Result.failure(e)
            }
        }
    }

    suspend fun analyzeCattlePhoto(
        bitmap: Bitmap,
        notes: String,
        language: AppLanguage,
        selectedCow: Cattle? = null,
        cowHealthRecords: List<HealthRecord> = emptyList(),
        isPublic: Boolean = false
    ): Result<String> {
        val langPrompt = if (language == AppLanguage.HINDI) {
            "पूरा उत्तर सरल, स्पष्ट और आसान हिंदी (देवनागरी) में दें ताकि गौशाला कर्मचारी आसानी से समझ सके।"
        } else {
            "Provide the complete report in clear, simple English."
        }
        val cowRecordBlock = if (selectedCow != null) {
            buildSelectedCowContext(selectedCow, cowHealthRecords, isPublic)
        } else {
            "SELECTED COW RECORD: कोई गाय रिकॉर्ड चयनित नहीं किया गया है (Only photo & caregiver notes provided)."
        }
        val promptText = """
            You are assisting cattle caregivers at ${GosalaBrand.NAME_HI}.
            Carefully inspect this cow/bull/calf photograph and combine it with the available read-only cow record below.
            $langPrompt

            $cowRecordBlock

            Caregiver Symptoms / Notes: ${notes.ifBlank { "कोई अतिरिक्त टिप्पणी नहीं दी गई" }}

            ANALYZE THESE VISIBLE AREAS CAREFULLY:
            - शरीर की स्थिति व कमजोरी (Body condition / weakness)
            - आँख (Eyes - discharge, redness, dullness)
            - नाक व मुँह (Nose & mouth - discharge, frothing, lesions)
            - त्वचा व बाल (Skin & coat - lumps, rashes, ticks, hair loss)
            - घाव या सूजन (Wounds, bleeding, udder/joint/leg swelling)
            - खड़े होने या चलने की स्थिति (Posture, drooping head/ears, lameness, inability to stand)
            - व्यवहार के संकेत (उदासी, सुस्ती, खाना न खाना या असामान्य स्थिति)

            REQUIRED OUTPUT STRUCTURE (USE THESE CLEAR HEADINGS):
            1. गाय व रिकॉर्ड सारांश (Cow ID, Name & available history; say "रिकॉर्ड चयनित नहीं" if none)
            2. फोटो में दिखाई देने वाले लक्षण (Visible Observations)
            3. संभावित समस्या / बीमारी (Possible Conditions — NEVER declare a definite/confirmed diagnosis from a photo)
            4. गंभीरता स्तर (Severity): स्पष्ट लिखें — [कम / मध्यम / आपातकाल]
            5. सुरक्षित शुरुआती देखभाल (Safe First-Aid, Hygiene, Water/Rest Care — NEVER prescribe definite medicines or exact drug dosages)
            6. पशु चिकित्सक को कब बुलाएँ (When to Call Veterinarian — explicitly warn to call a vet IMMEDIATELY for serious illness, calving trouble/प्रसव समस्या, breathing difficulty/साँस की तकलीफ, bleeding/खून, inability to stand/उठ न पाना, or poisoning/विषाक्तता)
            7. महत्वपूर्ण अस्वीकरण (Mandatory Disclaimer): स्पष्ट लिखें कि "यह फोटो आधारित विश्लेषण केवल प्रारंभिक सुझाव है, निश्चित बीमारी की घोषणा नहीं है। सटीक निदान व उपचार के लिए योग्य पशु चिकित्सक की जाँच आवश्यक है। AI किसी रिकॉर्ड में स्वतः बदलाव नहीं करता।"
        """.trimIndent()

        // Test hook path for deterministic Robolectric tests
        val hook = testPhotoAnalyzer
        if (hook != null) {
            return try {
                val text = hook(bitmap, promptText)
                if (text.isBlank()) Result.failure(IllegalStateException("AI returned an empty image analysis."))
                else Result.success(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Live Firebase AI Logic multimodal analysis with automatic App Check refresh retry
        var attempt = 0
        while (true) {
            try {
                val response = withTimeout(60_000L) {
                    chatModel.generateContent(
                        content {
                            image(bitmap)
                            text(promptText)
                        }
                    )
                }
                val text = response.text
                return if (text.isNullOrBlank()) {
                    Result.failure(IllegalStateException("AI से फोटो विश्लेषण का उत्तर प्राप्त नहीं हुआ।"))
                } else {
                    Result.success(text)
                }
            } catch (e: TimeoutCancellationException) {
                return Result.failure(Exception("AI फोटो विश्लेषण का समय समाप्त हो गया (60 सेकंड)। कृपया पुनः प्रयास करें।", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == 0 && isAppCheckTokenError(e)) {
                    Log.w(AI_REPO_TAG, "App Check token error during photo analysis; forcing refresh and retrying once.", e)
                    refreshAppCheckToken(forceRefresh = true)
                    attempt++
                    continue
                }
                return Result.failure(e)
            }
        }
    }

    suspend fun extractVoiceEntryDraft(spokenText: String): Result<VoiceEntryDraft> {
        var attempt = 0
        while (true) {
            try {
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
                if (attempt == 0 && isAppCheckTokenError(e)) {
                    Log.w(AI_REPO_TAG, "App Check token error during voice draft; forcing refresh and retrying once.", e)
                    refreshAppCheckToken(forceRefresh = true)
                    attempt++
                    continue
                }
                return Result.failure(e)
            }
        }
    }
}

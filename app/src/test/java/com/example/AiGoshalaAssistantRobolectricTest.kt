package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.core.*
import com.example.data.model.*
import com.example.data.repository.GeminiAiRepository
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AiMode
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.AiViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiGoshalaAssistantRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin_ai_1",
        displayName = "गौशाला अध्यक्ष",
        email = "bhagwankibaat4@gmail.com",
        role = UserRole.ADMIN,
        staffActive = true
    )

    private val publicSession = UserSession(
        uid = "public_ai_1",
        displayName = "गोभक्त",
        email = "bhakt@example.com",
        role = UserRole.PUBLIC
    )

    private val sampleCow = Cattle(
        id = "cow_doc_1",
        animalCode = "JBGS-G-001",
        name = "नंदिनी",
        category = "COW",
        breed = "गिर",
        ageText = "5 वर्ष",
        weightKg = 410.0,
        healthStatus = "ATTENTION",
        shedNumber = "Shed-A2",
        vaccinationSummary = "FMD व HS टीका पूर्ण (2026-10-15)",
        notes = "सुबह से चारा कम खा रही है",
        publicApproved = true
    )

    private val sampleBull = Cattle(
        id = "bull_doc_1",
        animalCode = "JBGS-B-002",
        name = "नंदी",
        category = "BULL",
        breed = "साहीवाल",
        ageText = "6 वर्ष",
        weightKg = 520.0,
        healthStatus = "HEALTHY",
        shedNumber = "Shed-B1"
    )

    private val sampleCalf = Cattle(
        id = "calf_doc_1",
        animalCode = "JBGS-C-003",
        name = "गोपाल बछड़ा",
        category = "CALF",
        breed = "गिर",
        ageText = "8 माह",
        weightKg = 95.0,
        healthStatus = "HEALTHY",
        shedNumber = "Shed-C1"
    )

    private val sampleHealthRecords = listOf(
        HealthRecord(
            id = "hr_1",
            cattleId = "cow_doc_1",
            cattleName = "नंदिनी",
            animalCode = "JBGS-G-001",
            recordType = "VACCINATION",
            title = "FMD खुरपका टीका",
            medicine = "FMD Vaccine",
            vetName = "डॉ. शर्मा",
            nextFollowUpDate = "2027-04-15"
        ),
        HealthRecord(
            id = "hr_2",
            cattleId = "cow_doc_1",
            cattleName = "नंदिनी",
            animalCode = "JBGS-G-001",
            recordType = "TREATMENT",
            title = "हल्का बुखार व अपच",
            medicine = "हर्बल पाचन चूर्ण",
            vetName = "डॉ. शर्मा",
            costInr = 350.0,
            notes = "पशु चिकित्सक द्वारा जाँच की गई"
        )
    )

    @Before
    fun setUp() {
        GeminiAiRepository.testStreamHandler = null
        GeminiAiRepository.testPhotoAnalyzer = null
    }

    @After
    fun tearDown() {
        GeminiAiRepository.testStreamHandler = null
        GeminiAiRepository.testPhotoAnalyzer = null
    }

    @Test
    fun `app check debug token persists synchronously via commit, standalone debug APK uses provisioned reCAPTCHA, and dummy tokens are rejected`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistenceKey = "test_goshala_app"
        val testToken = "12345678-ABCD-EF01-2345-6789ABCDEF01"

        // 1. Provisioned reCAPTCHA Enterprise site key must resolve cleanly from resources
        val siteKey = resolveRecaptchaSiteKey(context)
        assertFalse(siteKey.isNullOrBlank())

        // 2. Debug token commits synchronously to both Firebase debug store and explicit meta store
        val committed = persistDebugAppCheckToken(context, persistenceKey, testToken)
        assertTrue(committed)
        val stored = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        ).getString(DEBUG_SECRET_KEY, null)
        assertEquals(testToken, stored)

        // 3. Provider selection matrix prevents unregistered random UUIDs on standalone phones
        assertEquals(
            AppCheckProviderKind.DEBUG,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = testToken,
                storedExplicitDebugToken = null,
                storedSdkDebugSecret = null,
                recaptchaSiteKey = siteKey
            )
        )
        // Standalone Debug APK installed on user's phone (no intent token, stale random UUID) -> RECAPTCHA!
        assertEquals(
            AppCheckProviderKind.RECAPTCHA,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = null,
                storedExplicitDebugToken = null,
                storedSdkDebugSecret = "unregistered-random-uuid",
                recaptchaSiteKey = siteKey
            )
        )
        // AI Studio emulator relaunched with matching explicit harness token -> DEBUG!
        assertEquals(
            AppCheckProviderKind.DEBUG,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = null,
                storedExplicitDebugToken = testToken,
                storedSdkDebugSecret = testToken,
                recaptchaSiteKey = siteKey
            )
        )
        // Release build -> ALWAYS RECAPTCHA!
        assertEquals(
            AppCheckProviderKind.RECAPTCHA,
            decideAppCheckProviderKind(
                isDebugBuild = false,
                intentDebugToken = testToken,
                storedExplicitDebugToken = testToken,
                storedSdkDebugSecret = testToken,
                recaptchaSiteKey = siteKey
            )
        )

        // 4. Stale token cleanup works synchronously
        clearStoredDebugAppCheckToken(context, persistenceKey)
        assertNull(
            context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
                .getString(DEBUG_SECRET_KEY, null)
        )

        // 5. JWT validator rejects Firebase dummy error token and accepts real JWTs
        assertFalse(isValidAppCheckJwt(null))
        assertFalse(isValidAppCheckJwt(""))
        assertFalse(isValidAppCheckJwt("eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="))
        assertTrue(isValidAppCheckJwt("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhcHAifQ.c2lnbmF0dXJl"))

        // 6. Error classifier accurately detects App Check token failures
        assertTrue(isAppCheckTokenError(Exception("Firebase App Check token is invalid.")))
        assertTrue(isAppCheckTokenError(Exception("HTTP 403 attestation failure")))
        assertFalse(isAppCheckTokenError(Exception("Network socket closed")))
    }

    @Test
    fun `role scoped context accurately reflects verified real data and reports missing records without inventing numbers`() {
        val repo = GeminiAiRepository()
        val today = QrAndExportUtils.todayIso()

        val fullContext = repo.buildRoleScopedContext(
            session = adminSession,
            summary = PublicSummary(totalCattle = 3, healthyCattle = 2, sickCattle = 1),
            cattle = listOf(sampleCow, sampleBull, sampleCalf),
            health = sampleHealthRecords,
            feedLogs = listOf(
                FeedAndOperationLog(
                    id = "f1",
                    logDate = today,
                    greenFodderKg = 180.0,
                    dryFodderBhusaKg = 120.0,
                    concentrateKg = 25.0,
                    waterLiters = 400.0,
                    shedCleaned = true,
                    feedStockRemainingKg = 950.0,
                    taskTitle = "शाम की सफाई",
                    taskCompleted = false
                )
            ),
            finance = listOf(
                FinancialTransaction(id = "tx1", txType = "INCOME", category = "DONATION", amountInr = 15000.0, dateStr = today),
                FinancialTransaction(id = "tx2", txType = "EXPENSE", category = "FEED_PURCHASE", amountInr = 4200.0, dateStr = today)
            ),
            farmers = listOf(FarmerRecord(id = "fm1", name = "रामलाल किसान", village = "रामनगर")),
            farmerTx = listOf(
                FarmerTransaction(id = "ft1", direction = "PURCHASE_FROM_FARMER", totalAmountInr = 6500.0, outstandingInr = 1500.0)
            ),
            compost = listOf(
                CompostBatch(id = "cb1", productType = "VERMICOMPOST", rawDungKg = 600.0, quantity = 250.0, stockRemaining = 140.0, sellingPricePerUnitInr = 25.0, soldQuantity = 110.0)
            ),
            donations = listOf(
                Donation(id = "dn1", amountInr = 5100.0, paymentStatus = "VERIFIED_SUCCESS")
            ),
            products = listOf(
                GoshalaProduct(id = "pr1", nameHi = "गोबर के कंडे", priceInr = 120.0, unitLabel = "पैकेट", stockQuantity = 40L, available = true)
            ),
            selectedCow = sampleCow
        )

        // Verify exact real numbers & selected cow history
        assertTrue(fullContext.contains("Total=3 | गाय (Cows)=1 | बैल (Bulls)=1 | बछड़े (Calves)=1"))
        assertTrue(fullContext.contains("बीमार/देखरेख (Sick/Attention)=1"))
        assertTrue(fullContext.contains("नंदिनी [JBGS-G-001]"))
        assertTrue(fullContext.contains("FMD खुरपका टीका"))
        assertTrue(fullContext.contains("हल्का बुखार व अपच"))
        assertTrue(fullContext.contains("Green=180.0kg"))
        assertTrue(fullContext.contains("Latest Feed Stock Remaining=950.0kg"))
        assertTrue(fullContext.contains("Total Income=₹15000.0, Total Expense=₹4200.0"))
        assertTrue(fullContext.contains("Verified Total=₹5100.0"))
        assertTrue(fullContext.contains("Raw Dung Collected=600.0kg"))
        assertTrue(fullContext.contains("गोबर के कंडे"))
        assertTrue(fullContext.contains("Fodder Purchased=₹6500.0"))

        // Empty data explicitly reports missing records
        val emptyContext = repo.buildRoleScopedContext(
            session = adminSession,
            summary = PublicSummary(),
            cattle = emptyList(),
            health = emptyList(),
            feedLogs = emptyList(),
            finance = emptyList(),
            farmers = emptyList(),
            farmerTx = emptyList(),
            compost = emptyList(),
            donations = emptyList(),
            products = emptyList()
        )
        assertTrue(emptyContext.contains("Cattle Registry: रिकॉर्ड उपलब्ध नहीं है"))
        assertTrue(emptyContext.contains("Feed & Operations: रिकॉर्ड उपलब्ध नहीं है"))
        assertTrue(emptyContext.contains("Finance: रिकॉर्ड उपलब्ध नहीं है"))
        assertTrue(emptyContext.contains("Donations: रिकॉर्ड उपलब्ध नहीं है"))

        // Public role strictly blocks internal finances & private medical details
        val publicContext = repo.buildRoleScopedContext(
            session = publicSession,
            summary = PublicSummary(totalCattle = 3, healthyCattle = 2),
            cattle = listOf(sampleCow),
            health = sampleHealthRecords,
            feedLogs = emptyList(),
            finance = listOf(FinancialTransaction(txType = "EXPENSE", amountInr = 99999.0)),
            farmers = emptyList(),
            farmerTx = emptyList(),
            compost = emptyList(),
            donations = emptyList(),
            selectedCow = sampleCow
        )
        assertTrue(publicContext.contains("PUBLIC ACCESS ONLY"))
        assertFalse(publicContext.contains("99999.0"))
        assertFalse(publicContext.contains("हल्का बुखार व अपच"))
    }

    @Test
    fun `photo health analysis includes cow ID, health record, visual checklist and mandatory vet disclaimer`() = runBlocking {
        val repo = GeminiAiRepository()
        var capturedPrompt = ""
        GeminiAiRepository.testPhotoAnalyzer = { _, prompt ->
            capturedPrompt = prompt
            "1. गाय व रिकॉर्ड सारांश: नंदिनी (JBGS-G-001)\n4. गंभीरता स्तर: मध्यम\n7. महत्वपूर्ण अस्वीकरण: पशु चिकित्सक की जाँच आवश्यक है।"
        }

        val bmp = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        val result = repo.analyzeCattlePhoto(
            bitmap = bmp,
            notes = "सुस्त दिख रही है और खाना नहीं खा रही",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow,
            cowHealthRecords = sampleHealthRecords,
            isPublic = false
        )

        assertTrue(result.isSuccess)
        assertTrue(capturedPrompt.contains("JBGS-G-001"))
        assertTrue(capturedPrompt.contains("नंदिनी"))
        assertTrue(capturedPrompt.contains("FMD खुरपका टीका"))
        assertTrue(capturedPrompt.contains("हल्का बुखार व अपच"))
        assertTrue(capturedPrompt.contains("कम / मध्यम / आपातकाल"))
        assertTrue(capturedPrompt.contains("प्रसव समस्या"))
        assertTrue(capturedPrompt.contains("निश्चित बीमारी की घोषणा नहीं है"))
    }

    @Test
    fun `AI chat send button, cow selector, action plan mode, success and error retry work without auto-modifying records`() {
        var capturedContext = ""
        var failFirstAttempt = true
        var mutatedAnyRecord = false

        GeminiAiRepository.testStreamHandler = { prompt, context, _ ->
            capturedContext = context
            if (failFirstAttempt) {
                failFirstAttempt = false
                throw IllegalStateException("Firebase App Check token is invalid.")
            }
            listOf("आज 3 गोवंश के लिए लगभग 60-75 किलो हरा चारा और पर्याप्त भूसा दें। (प्रश्न: $prompt)")
        }

        val vm = AiViewModel()

        composeRule.setContent {
            GosalaTheme {
                AiAssistantScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    summary = PublicSummary(totalCattle = 3, healthyCattle = 2, sickCattle = 1),
                    cattle = listOf(sampleCow, sampleBull, sampleCalf),
                    health = sampleHealthRecords,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    farmers = emptyList(),
                    farmerTx = emptyList(),
                    compost = emptyList(),
                    donations = emptyList(),
                    products = emptyList(),
                    aiViewModel = vm,
                    initialMode = AiMode.MANAGEMENT,
                    onSaveCattleDraft = { mutatedAnyRecord = true },
                    onSaveFeedDraft = { mutatedAnyRecord = true },
                    onSaveExpenseDraft = { mutatedAnyRecord = true },
                    onSaveHealthDraft = { mutatedAnyRecord = true },
                    onNavigateToCattleSearch = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Select cow record (read-only context)
        composeRule.onNodeWithTag("ai_select_cow_JBGS-G-001").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_selected_cow_summary").assertIsDisplayed()

        // 2. Verify Action Plan chip exists
        composeRule.onNodeWithTag("ai_mode_action_plan").assertIsDisplayed()

        // 3. Send question -> first attempt triggers App Check error UI with Retry button
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("आज गायों को कितना चारा देना चाहिए?")
        composeRule.onNodeWithTag("ai_send_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_chat_error"))
        composeRule.onNodeWithTag("ai_chat_error").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_chat_retry_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // 4. Retry succeeds and displays response + success badge + selected cow was passed in context
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_chat_success_badge"))
        composeRule.onNodeWithTag("ai_chat_success_badge").assertIsDisplayed()
        assertTrue(capturedContext.contains("SELECTED COW RECORD"))
        assertTrue(capturedContext.contains("JBGS-G-001"))

        // 5. Verify Retry did NOT create duplicate user messages (exactly 1 user msg + 1 AI reply)
        assertEquals(1, vm.messages.value.count { it.isUser })
        assertEquals(1, vm.messages.value.count { !it.isUser })

        // 6. Verify AI never auto-mutated any record
        assertFalse(mutatedAnyRecord)
    }

    @Test
    fun `photo health check UI shows camera and gallery buttons and displays AI health report`() {
        GeminiAiRepository.testPhotoAnalyzer = { _, _ ->
            "गंभीरता स्तर: मध्यम\nप्रारंभिक सुझाव: स्वच्छ पानी व छाया दें और पशु चिकित्सक से जाँच कराएँ।"
        }
        val vm = AiViewModel()

        composeRule.setContent {
            GosalaTheme {
                AiAssistantScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    summary = PublicSummary(totalCattle = 1, healthyCattle = 0, sickCattle = 1),
                    cattle = listOf(sampleCow),
                    health = sampleHealthRecords,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    farmers = emptyList(),
                    farmerTx = emptyList(),
                    compost = emptyList(),
                    donations = emptyList(),
                    products = emptyList(),
                    aiViewModel = vm,
                    initialMode = AiMode.ANIMAL_CARE,
                    onSaveCattleDraft = {},
                    onSaveFeedDraft = {},
                    onSaveExpenseDraft = {},
                    onSaveHealthDraft = {},
                    onNavigateToCattleSearch = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_content_list")
            .performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("camera_cattle_photo_ai_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("pick_cattle_photo_ai_btn").assertIsDisplayed()

        // Trigger photo analysis via ViewModel and verify success report renders in UI
        val bmp = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        vm.analyzeCattlePhoto(
            bitmap = bmp,
            notes = "आँख में पानी और सुस्ती",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow,
            cowHealthRecords = sampleHealthRecords,
            isPublic = false
        )
        composeRule.waitForIdle()

        assertTrue(vm.photoAnalysisState.value is AiUiState.Success)
        composeRule.onNodeWithTag("ai_photo_success_report").assertExists()
    }
}

package com.example

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.core.*
import com.example.data.model.*
import com.example.data.repository.AiWorkPlan
import com.example.data.repository.AiWorkPlanItem
import com.example.data.repository.GOSHALA_SYSTEM_INSTRUCTION
import com.example.data.repository.GeminiAiRepository
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AiMode
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.AiUiState
import com.example.ui.viewmodel.AiViewModel
import com.example.ui.viewmodel.AiWorkPlanUiState
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

    private val limitedStaffSession = UserSession(
        uid = "staff_ai_1",
        displayName = "रामू सेवक",
        email = "staff@example.com",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(
            AppModule.CATTLE.key to listOf(CrudAction.VIEW.name),
            AppModule.FEED.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
            AppModule.AI.key to listOf(CrudAction.VIEW.name)
        )
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
        GeminiAiRepository.testSymptomHealthAnalyzer = null
        GeminiAiRepository.testWorkPlanGenerator = null
    }

    @After
    fun tearDown() {
        GeminiAiRepository.testStreamHandler = null
        GeminiAiRepository.testPhotoAnalyzer = null
        GeminiAiRepository.testSymptomHealthAnalyzer = null
        GeminiAiRepository.testWorkPlanGenerator = null
    }

    @Test
    fun `app check keeps DEBUG on emulators, uses reCAPTCHA on standalone physical phones and release, commits synchronously, and rejects dummy JWTs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistenceKey = "test_goshala_app"
        val testToken = "12345678-ABCD-EF01-2345-6789ABCDEF01"

        // 1. Provisioned reCAPTCHA Enterprise site key resolves cleanly from resources
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

        // 3. Provider selection matrix
        // Debug with explicit Intent token -> DEBUG
        assertEquals(
            AppCheckProviderKind.DEBUG,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = testToken,
                storedExplicitDebugToken = null,
                storedSdkDebugSecret = null,
                recaptchaSiteKey = siteKey,
                isEmulator = true
            )
        )
        // Debug on Android Emulator WITHOUT Intent token (e.g. launcher relaunch) -> MUST STAY DEBUG (never RECAPTCHA!)
        assertEquals(
            AppCheckProviderKind.DEBUG,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = null,
                storedExplicitDebugToken = null,
                storedSdkDebugSecret = null,
                recaptchaSiteKey = siteKey,
                isEmulator = true
            )
        )
        // Standalone Debug APK on real physical phone (no intent token, stale random UUID) -> RECAPTCHA!
        assertEquals(
            AppCheckProviderKind.RECAPTCHA,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = null,
                storedExplicitDebugToken = null,
                storedSdkDebugSecret = "unregistered-random-uuid",
                recaptchaSiteKey = siteKey,
                isEmulator = false
            )
        )
        // Relaunched Debug build with matching explicit harness token -> DEBUG!
        assertEquals(
            AppCheckProviderKind.DEBUG,
            decideAppCheckProviderKind(
                isDebugBuild = true,
                intentDebugToken = null,
                storedExplicitDebugToken = testToken,
                storedSdkDebugSecret = testToken,
                recaptchaSiteKey = siteKey,
                isEmulator = false
            )
        )
        // Release build -> ALWAYS RECAPTCHA (even if token present)!
        assertEquals(
            AppCheckProviderKind.RECAPTCHA,
            decideAppCheckProviderKind(
                isDebugBuild = false,
                intentDebugToken = testToken,
                storedExplicitDebugToken = testToken,
                storedSdkDebugSecret = testToken,
                recaptchaSiteKey = siteKey,
                isEmulator = false
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

        // 7. Emulator detection recognizes Google Cloud Cuttlefish / AI Studio Streaming Emulators
        assertTrue(
            isEmulatorBuildProperties(
                fingerprint = "google/aosp_cf_x86_64_phone/vsoc_x86_64:14/AP2A/12345:userdebug/test-keys",
                model = "Cuttlefish x86_64 phone",
                manufacturer = "Google",
                brand = "google",
                device = "vsoc_x86_64",
                product = "aosp_cf_x86_64_phone",
                hardware = "cutf_cvm"
            )
        )
        // Physical Android phone MUST NOT be misclassified as an emulator
        assertFalse(
            isEmulatorBuildProperties(
                fingerprint = "samsung/dm1qxxx/dm1q:14/UP1A/S911B:user/release-keys",
                model = "SM-S911B",
                manufacturer = "samsung",
                brand = "samsung",
                device = "dm1q",
                product = "dm1qxxx",
                hardware = "qcom"
            )
        )
    }

    @Test
    fun `system instruction and role-scoped context enforce exact RBAC isolation for Admin, limited Staff, and Public users without fabricating missing cow fields`() {
        // Verify official Jay Bhole Gosala Samiti Hindi system instructions
        assertTrue(GOSHALA_SYSTEM_INSTRUCTION.contains("Jay Bhole Gosala Samiti"))
        assertTrue(GOSHALA_SYSTEM_INSTRUCTION.contains("केवल सलाह देंगे"))
        assertTrue(GOSHALA_SYSTEM_INSTRUCTION.contains("योग्य पशु चिकित्सक"))
        assertTrue(GOSHALA_SYSTEM_INSTRUCTION.contains("खतरनाक दवा या निश्चित खुराक"))
        assertTrue(GOSHALA_SYSTEM_INSTRUCTION.contains("अधूरी जानकारी को कभी गढ़ेंगे नहीं"))

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
            selectedCow = sampleCow,
            staffAccounts = listOf(
                StaffAccount(id = "st1", name = "महेश सेवक", designation = "चारा प्रभारी", active = true),
                StaffAccount(id = "st2", name = "सुरेश जी", designation = "पशु देखभाल", active = true)
            )
        )

        // Verify exact real numbers, organization address, staff roster & selected cow history
        assertTrue(fullContext.contains("Jay Bhole Gosala Samiti"))
        assertTrue(fullContext.contains("मेहदोरा, पोरसा, मुरैना, मध्य प्रदेश"))
        assertTrue(fullContext.contains("Staff Roster (VERIFIED): Total=2, Active=2"))
        assertTrue(fullContext.contains("महेश सेवक (चारा प्रभारी)"))
        assertTrue(fullContext.contains("Total=3 | गाय (Cows)=1 | बैल (Bulls)=1 | बछड़े (Calves)=1"))
        assertTrue(fullContext.contains("बीमार/देखरेख (Sick/Attention)=1"))
        assertTrue(fullContext.contains("नंदिनी [JBGS-G-001]"))
        assertTrue(fullContext.contains("Pregnancy Status (गर्भावस्था): रिकॉर्ड उपलब्ध नहीं है"))
        assertTrue(fullContext.contains("Milk Yield Record (दूध उत्पादन): रिकॉर्ड उपलब्ध नहीं है"))
        assertTrue(fullContext.contains("FMD खुरपका टीका"))
        assertTrue(fullContext.contains("हल्का बुखार व अपच"))
        assertTrue(fullContext.contains("Green=180.0kg"))
        assertTrue(fullContext.contains("Latest Feed Stock Remaining=950.0kg"))
        assertTrue(fullContext.contains("Total Income=₹15000.0, Total Expense=₹4200.0"))
        assertTrue(fullContext.contains("Verified Total=₹5100.0"))
        assertTrue(fullContext.contains("Raw Dung Collected=600.0kg"))
        assertTrue(fullContext.contains("गोबर के कंडे"))
        assertTrue(fullContext.contains("Fodder Purchased=₹6500.0"))

        // Staff with limited permissions (only CATTLE + FEED) MUST NOT receive FINANCE or HEALTH details
        val staffContext = repo.buildRoleScopedContext(
            session = limitedStaffSession,
            summary = PublicSummary(totalCattle = 3, healthyCattle = 2, sickCattle = 1),
            cattle = listOf(sampleCow),
            health = sampleHealthRecords,
            feedLogs = emptyList(),
            finance = listOf(FinancialTransaction(txType = "EXPENSE", amountInr = 77777.0)),
            farmers = emptyList(),
            farmerTx = emptyList(),
            compost = emptyList(),
            donations = emptyList()
        )
        assertTrue(staffContext.contains("Cattle Registry (VERIFIED)"))
        assertTrue(staffContext.contains("Finance: अनुमति नहीं है / Unauthorized"))
        assertTrue(staffContext.contains("Health Records: अनुमति नहीं है / Unauthorized"))
        assertFalse(staffContext.contains("77777.0"))

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
            selectedCow = sampleCow,
            staffAccounts = listOf(StaffAccount(id = "st1", name = "महेश सेवक", designation = "चारा प्रभारी", active = true))
        )
        assertTrue(publicContext.contains("PUBLIC ACCESS ONLY"))
        assertFalse(publicContext.contains("99999.0"))
        assertFalse(publicContext.contains("हल्का बुखार व अपच"))
        assertFalse(publicContext.contains("महेश सेवक"))
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
        assertTrue(capturedPrompt.contains("कम / मध्यम / गंभीर / आपातकाल"))
        assertTrue(capturedPrompt.contains("प्रसव समस्या"))
        assertTrue(capturedPrompt.contains("निश्चित बीमारी की घोषणा नहीं है"))
    }

    @Test
    fun `text-only symptom health report works without uploading a photo and enforces all 7 mandatory sections`() = runBlocking {
        val repo = GeminiAiRepository()
        var capturedPrompt = ""
        GeminiAiRepository.testSymptomHealthAnalyzer = { prompt ->
            capturedPrompt = prompt
            "1. संभावित कारण: अपच या हल्का तनाव\n2. लक्षणों की गंभीरता: मध्यम\n3. तत्काल देखभाल: छाया व आराम दें\n4. पानी और चारे से संबंधित सावधानी: स्वच्छ ताजा पानी दें\n5. क्या नहीं करना चाहिए: बिना डॉक्टर की सलाह कोई दवा न दें\n6. पशु चिकित्सक को कब बुलाना चाहिए: 12 घंटे में सुधार न होने पर\n7. आपातकालीन चेतावनी: पेट फूलने या बुखार पर तुरंत डॉक्टर बुलाएँ।"
        }

        val result = repo.analyzeHealthGuidance(
            bitmap = null,
            notes = "चारा ठीक से नहीं खा रही है",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow,
            cowHealthRecords = sampleHealthRecords,
            isPublic = false
        )

        assertTrue(result.isSuccess)
        assertTrue(capturedPrompt.contains("चारा ठीक से नहीं खा रही है"))
        assertTrue(capturedPrompt.contains("संभावित कारण"))
        assertTrue(capturedPrompt.contains("लक्षणों की गंभीरता"))
        assertTrue(capturedPrompt.contains("तत्काल देखभाल"))
        assertTrue(capturedPrompt.contains("पानी और चारे से संबंधित सावधानी"))
        assertTrue(capturedPrompt.contains("क्या नहीं करना चाहिए"))
        assertTrue(capturedPrompt.contains("पशु चिकित्सक को कब बुलाना चाहिए"))
        assertTrue(capturedPrompt.contains("आपातकालीन चेतावनी"))
    }

    @Test
    fun `blank input blocked, chat send + error retry + copy button + anti-duplicate guard + clear chat work cleanly`() {
        var capturedContext = ""
        var callCount = 0
        var failFirstAttempt = true
        var mutatedAnyRecord = false
        val expectedReply = "50 गायों के लिए प्रतिदिन लगभग 750-1000 किलो हरा चारा, 300-400 किलो सूखा भूसा और 2000-2500 लीटर स्वच्छ पानी आवश्यक होता है।"

        GeminiAiRepository.testStreamHandler = { _, context, _ ->
            callCount++
            capturedContext = context
            if (failFirstAttempt) {
                failFirstAttempt = false
                throw IllegalStateException("Firebase App Check token is invalid.")
            }
            listOf(expectedReply)
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

        // 1. Blank input MUST keep Send disabled and NOT trigger API
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("   ")
        composeRule.onNodeWithTag("ai_send_button").assertIsNotEnabled()
        assertEquals(0, callCount)

        // 2. Select cow record (read-only context)
        composeRule.onNodeWithTag("ai_select_cow_JBGS-G-001").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_selected_cow_summary").assertIsDisplayed()

        // 3. Send Hindi question -> first attempt triggers App Check error with clean Hindi message + Retry button
        composeRule.onNodeWithTag("ai_prompt_input").performTextClearance()
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("50 गायों के लिए रोज़ कितना चारा चाहिए?")
        composeRule.onNodeWithTag("ai_send_button").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_chat_error"))
        composeRule.onNodeWithTag("ai_chat_error").assertIsDisplayed()
        val errMsg = (vm.uiState.value as AiUiState.Error).message
        assertTrue(errMsg.contains("सुरक्षा सत्यापन") || errMsg.contains("App Check"))
        assertFalse(errMsg.contains("Exception"))

        // 4. Tap Retry -> succeeds without duplicating user message
        composeRule.onNodeWithTag("ai_chat_retry_btn").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_chat_success_badge"))
        composeRule.onNodeWithTag("ai_chat_success_badge").assertIsDisplayed()
        assertTrue(capturedContext.contains("SELECTED COW RECORD"))
        assertTrue(capturedContext.contains("JBGS-G-001"))
        assertEquals(1, vm.messages.value.count { it.isUser })
        assertEquals(1, vm.messages.value.count { !it.isUser })

        // 5. Copy AI reply button copies full Hindi text to system clipboard
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_copy_msg_1"))
        composeRule.onNodeWithTag("ai_copy_msg_1").performClick()
        composeRule.waitForIdle()
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(expectedReply, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // 6. Anti-duplicate guard blocks immediate duplicate non-retry submission of the exact same prompt
        val callsBeforeDuplicate = callCount
        vm.askAssistant(
            prompt = "50 गायों के लिए रोज़ कितना चारा चाहिए?",
            modeLabel = "गौशाला सवाल-जवाब",
            language = AppLanguage.HINDI,
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
            isRetry = false
        )
        assertEquals(callsBeforeDuplicate, callCount)

        // 7. Clear Chat clears messages cleanly
        composeRule.onNodeWithTag("ai_clear_chat_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(vm.messages.value.isEmpty())
        assertTrue(vm.uiState.value is AiUiState.Idle)

        // 8. Verify AI never auto-mutated any record
        assertFalse(mutatedAnyRecord)
    }

    @Test
    fun `AI Work Plan mode renders dedicated structured card with time, task, responsible person, priority, error retry, and copy`() {
        var failFirst = true
        GeminiAiRepository.testWorkPlanGenerator = { _, _ ->
            if (failFirst) {
                failFirst = false
                throw IllegalStateException("network offline")
            }
            AiWorkPlan(
                dateLabel = "2026-11-22",
                title = "आज की गौशाला कार्य योजना",
                summary = "3 गोवंश (1 देखरेख में) के लिए सुबह, दोपहर और शाम की व्यावहारिक कार्य सूची।",
                tasks = listOf(
                    AiWorkPlanItem(
                        timeSlot = "05:30 - 07:00",
                        period = "सुबह",
                        task = "शेड की सफाई व गोबर संग्रहण, बीमार गाय नंदिनी की जाँच",
                        responsiblePerson = "गौशाला सेवक",
                        priority = "उच्च",
                        category = "स्वास्थ्य व सफाई"
                    ),
                    AiWorkPlanItem(
                        timeSlot = "12:00 - 13:30",
                        period = "दोपहर",
                        task = "स्वच्छ पेयजल भरना और छाया व्यवस्था जाँचना",
                        responsiblePerson = "चारा प्रभारी",
                        priority = "मध्यम",
                        category = "पानी व देखभाल"
                    )
                )
            )
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
                    initialMode = AiMode.ACTION_PLAN,
                    onSaveCattleDraft = {},
                    onSaveFeedDraft = {},
                    onSaveExpenseDraft = {},
                    onSaveHealthDraft = {},
                    onNavigateToCattleSearch = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Dedicated Work Plan card is displayed in ACTION_PLAN mode
        composeRule.onNodeWithTag("ai_work_plan_card").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_generate_work_plan_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 2. First call fails with clear Hindi network error + Retry button
        assertTrue(vm.workPlanState.value is AiWorkPlanUiState.Error)
        composeRule.onNodeWithTag("ai_work_plan_error").assertExists()
        composeRule.onNodeWithTag("ai_work_plan_retry_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        // 3. Retry succeeds and shows structured Work Plan items (Time, Task, Responsible Person, Priority)
        assertTrue(vm.workPlanState.value is AiWorkPlanUiState.Success)
        composeRule.onNodeWithTag("ai_work_plan_result").assertExists()
        composeRule.onNodeWithTag("ai_work_plan_item_0").assertExists()
        composeRule.onNodeWithTag("ai_work_plan_item_1").assertExists()

        // 4. Copy & Share Work Plan buttons + Weekly/Monthly horizon chips work cleanly
        composeRule.onNodeWithTag("ai_work_plan_copy_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedText.contains("05:30 - 07:00"))
        assertTrue(copiedText.contains("गौशाला सेवक"))
        assertTrue(copiedText.contains("उच्च"))
        composeRule.onNodeWithTag("ai_work_plan_share_btn").assertExists()

        // 5. Switch to Weekly & Monthly plan horizons and verify generation
        composeRule.onNodeWithTag("ai_plan_horizon_weekly").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_generate_work_plan_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("WEEKLY", (vm.workPlanState.value as AiWorkPlanUiState.Success).plan.planHorizon)

        composeRule.onNodeWithTag("ai_plan_horizon_monthly").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_generate_work_plan_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("MONTHLY", (vm.workPlanState.value as AiWorkPlanUiState.Success).plan.planHorizon)
    }

    @Test
    fun `photo and text-only health check UI works with copy and share, preserves chat history across Hinglish Q&A, and handles rate-limit errors`() {
        val reportText = "1. संभावित कारण: अपच\n2. लक्षणों की गंभीरता: मध्यम\n6. पशु चिकित्सक को कब बुलाना चाहिए: 12 घंटे में सुधार न होने पर।"
        GeminiAiRepository.testSymptomHealthAnalyzer = { reportText }
        GeminiAiRepository.testPhotoAnalyzer = { _, _ -> reportText }
        var streamAttempt = 0
        GeminiAiRepository.testStreamHandler = { prompt, _, _ ->
            streamAttempt++
            when (streamAttempt) {
                1 -> listOf("50 गायों के लिए प्रतिदिन 350 किलो भूसा व 750 किलो हरा चारा चाहिए।")
                2 -> throw IllegalStateException("HTTP 429 quota resource exhausted")
                else -> listOf("सरकारी योजना व पंजीकरण के लिए पशुपालन विभाग से सत्यापित दस्तावेज तैयार रखें। ($prompt)")
            }
        }
        val vm = AiViewModel()

        composeRule.setContent {
            GosalaTheme {
                AiAssistantScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
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

        // Public user MUST NOT see internal admin/business/voice-draft modes
        composeRule.onNodeWithTag("ai_mode_animal_care").assertExists()
        composeRule.onNodeWithTag("ai_mode_management").assertExists()
        composeRule.onNodeWithTag("ai_mode_action_plan").assertExists()
        composeRule.onNodeWithTag("ai_mode_admin_summary").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_mode_business").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_mode_voice_draft").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_mic_button").assertExists()

        // Symptom input & AI Health Report button work even WITHOUT picking a photo!
        composeRule.onNodeWithTag("ai_content_list")
            .performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("camera_cattle_photo_ai_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("pick_cattle_photo_ai_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_photo_notes_input").performScrollTo().performTextInput("चारा ठीक से नहीं खा रही है")
        composeRule.onNodeWithTag("analyze_cattle_photo_btn").performScrollTo().assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertTrue(vm.photoAnalysisState.value is AiUiState.Success)
        composeRule.onNodeWithTag("ai_photo_success_report").assertExists()
        composeRule.onNodeWithTag("ai_photo_share_btn").assertExists()
        composeRule.onNodeWithTag("ai_photo_copy_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(reportText, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // Switch to Q&A tab, send Hinglish question, verify Share button & Rate-Limit (429) handling + Retry preserving history
        composeRule.onNodeWithTag("ai_mode_management").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("50 gay ke liye kitna bhusa aur hara chara chahiye?")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        composeRule.waitForIdle()
        assertEquals(2, vm.messages.value.size)
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_share_msg_1"))
        composeRule.onNodeWithTag("ai_share_msg_1").assertExists()

        // Second question hits 429 Rate Limit -> clean Hindi message -> Retry succeeds & keeps previous Q&A preserved!
        composeRule.onNodeWithTag("ai_prompt_input").performTextInput("गौशाला के लिए सरकारी योजना बताएं")
        composeRule.onNodeWithTag("ai_send_button").performClick()
        composeRule.waitForIdle()
        val rateLimitErr = (vm.uiState.value as AiUiState.Error).message
        assertTrue(rateLimitErr.contains("Rate Limit") || rateLimitErr.contains("बहुत अधिक अनुरोध"))
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_chat_retry_btn"))
        composeRule.onNodeWithTag("ai_chat_retry_btn").performClick()
        composeRule.waitForIdle()
        assertEquals(4, vm.messages.value.size)
        assertTrue(vm.messages.value[0].text.contains("50 gay ke liye"))
        assertTrue(vm.messages.value[3].text.contains("सरकारी योजना"))
    }
}

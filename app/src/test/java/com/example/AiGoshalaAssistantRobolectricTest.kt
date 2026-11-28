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
        GeminiAiRepository.testGroundedSourcesProvider = null
    }

    @After
    fun tearDown() {
        GeminiAiRepository.testStreamHandler = null
        GeminiAiRepository.testPhotoAnalyzer = null
        GeminiAiRepository.testSymptomHealthAnalyzer = null
        GeminiAiRepository.testWorkPlanGenerator = null
        GeminiAiRepository.testGroundedSourcesProvider = null
        GeminiAiRepository.testModelAttemptRunner = null
        GeminiAiRepository.testAppCheckRefreshHandler = null
    }

    @Test
    fun `app check selects DEBUG in debug builds and PLAY_INTEGRITY in release builds, enforces cooldowns, commits synchronously, and rejects dummy JWTs`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val persistenceKey = "test_goshala_app"
        val testToken = "12345678-ABCD-EF01-2345-6789ABCDEF01"

        // 1. Debug token commits synchronously to Firebase App Check debug SharedPreferences store
        val committed = persistDebugAppCheckToken(context, persistenceKey, testToken)
        assertTrue(committed)
        val stored = context.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        ).getString(DEBUG_SECRET_KEY, null)
        assertEquals(testToken, stored)

        // 2. Strict build-type provider selection: DEBUG in debug builds, PLAY_INTEGRITY in release builds (never RECAPTCHA)
        assertEquals(AppCheckProviderKind.DEBUG, decideAppCheckProviderKind(isDebugBuild = true))
        assertEquals(AppCheckProviderKind.PLAY_INTEGRITY, decideAppCheckProviderKind(isDebugBuild = false))

        // 3. Stale token cleanup works synchronously
        clearStoredDebugAppCheckToken(context, persistenceKey)
        assertNull(
            context.getSharedPreferences("$DEBUG_STORE_PREFIX$persistenceKey", Context.MODE_PRIVATE)
                .getString(DEBUG_SECRET_KEY, null)
        )

        // 4. JWT validator rejects Firebase dummy error token and accepts real JWTs
        assertFalse(isValidAppCheckJwt(null))
        assertFalse(isValidAppCheckJwt(""))
        assertFalse(isValidAppCheckJwt("eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="))
        assertTrue(isValidAppCheckJwt("eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJhcHAifQ.c2lnbmF0dXJl"))

        // 5. Rate-limit detector and forced-refresh cooldown prevent "Too many attempts." retry storms
        assertTrue(isRateLimitMessage("recaptcha: Too many attempts."))
        assertTrue(isRateLimitMessage("Too many attempts."))
        assertFalse(isRateLimitMessage("Firebase App Check token is invalid."))

        val now = 1_000_000L
        assertTrue(canAttemptForceRefreshNow(nowMs = now, lastForceAttemptMs = 0L, blockedUntilMs = 0L))
        // Within 15s minimum interval -> blocked
        assertFalse(
            canAttemptForceRefreshNow(
                nowMs = now,
                lastForceAttemptMs = now - (MIN_FORCE_REFRESH_INTERVAL_MS - 1_000L),
                blockedUntilMs = 0L
            )
        )
        // After 15s minimum interval -> allowed
        assertTrue(
            canAttemptForceRefreshNow(
                nowMs = now,
                lastForceAttemptMs = now - (MIN_FORCE_REFRESH_INTERVAL_MS + 1_000L),
                blockedUntilMs = 0L
            )
        )
        // During 60s rate-limit lockout -> blocked
        assertFalse(
            canAttemptForceRefreshNow(
                nowMs = now,
                lastForceAttemptMs = 0L,
                blockedUntilMs = now + 30_000L
            )
        )

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
            "1. संभावित समस्या: सुस्ती व अपच\n3. खतरे का स्तर: सावधानी\n6. पशु चिकित्सक को कब बुलाना है: सुधार न होने पर तुरंत।"
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
        assertTrue(capturedPrompt.contains("सामान्य / सावधानी / आपातकाल"))
        assertTrue(capturedPrompt.contains("निश्चित बीमारी की घोषणा नहीं है"))
        assertTrue(capturedPrompt.contains("प्रसव समस्या"))
        assertTrue(capturedPrompt.contains("DAHD") || capturedPrompt.contains("ICAR"))
    }

    @Test
    fun `text-only symptom health report works without uploading a photo and enforces all 8 mandatory veterinary sections`() = runBlocking {
        val repo = GeminiAiRepository()
        var capturedPrompt = ""
        GeminiAiRepository.testSymptomHealthAnalyzer = { prompt ->
            capturedPrompt = prompt
            "1. संभावित समस्या: भूख कम होना / अपच\n2. इसके संभावित कारण: चारा परिवर्तन या हल्का तनाव\n3. खतरे का स्तर: सावधानी\n4. अभी क्या प्राथमिक देखभाल करनी है: छाया व स्वच्छ पानी दें\n5. क्या नहीं करना है: बिना पशु चिकित्सक जांच एंटीबायोटिक या इंजेक्शन न दें\n6. पशु चिकित्सक को कब बुलाना है: 12 घंटे में सुधार न होने पर\n7. डॉक्टर से कौन-कौन सी जाँच करवानी चाहिए: तापमान, रुमेन गतिशीलता व रक्त जाँच\n8. विश्वसनीय जानकारी के स्रोत: DAHD (https://dahd.nic.in/)"
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
        assertTrue(capturedPrompt.contains("संभावित समस्या"))
        assertTrue(capturedPrompt.contains("इसके संभावित कारण"))
        assertTrue(capturedPrompt.contains("खतरे का स्तर"))
        assertTrue(capturedPrompt.contains("अभी क्या प्राथमिक देखभाल करनी है"))
        assertTrue(capturedPrompt.contains("क्या नहीं करना है"))
        assertTrue(capturedPrompt.contains("पशु चिकित्सक को कब बुलाना है"))
        assertTrue(capturedPrompt.contains("डॉक्टर से कौन-कौन सी जाँच करवानी चाहिए"))
        assertTrue(capturedPrompt.contains("विश्वसनीय जानकारी के स्रोत"))
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

    @Test
    fun `all 5 required clinical veterinary scenarios classify exact risk level, enforce emergency rules, and isolate selected cow records`() = runBlocking {
        // 1. गाय के खड़े न हो पाने की समस्या -> EMERGENCY
        val scenario1 = VetTriageEngine.evaluate("गाय सुबह से खड़ी नहीं हो पा रही है", selectedCow = sampleCow)
        assertEquals(VetRiskLevel.EMERGENCY, scenario1.riskLevel)
        assertTrue(scenario1.requiresImmediateVetCall)
        assertTrue(scenario1.detectedRedFlagsHi.any { it.contains("खड़ी नहीं") })

        // 2. गाय का चारा नहीं खाना -> CAUTION + asks missing triage follow-ups
        val scenario2 = VetTriageEngine.evaluate("गाय सुबह से चारा नहीं खा रही है", selectedCow = sampleCow)
        assertEquals(VetRiskLevel.CAUTION, scenario2.riskLevel)
        assertFalse(scenario2.requiresImmediateVetCall)
        assertTrue(scenario2.missingQuestionsHi.any { it.contains("जुगाली") })
        assertTrue(scenario2.missingQuestionsHi.any { it.contains("पानी") })

        // 3. गाय को बुखार -> CAUTION (and high fever 105.5°F escalates to EMERGENCY)
        val scenario3 = VetTriageEngine.evaluate("गाय को बुखार है और सुस्त है", selectedCow = sampleCow)
        assertEquals(VetRiskLevel.CAUTION, scenario3.riskLevel)
        val scenario3High = VetTriageEngine.evaluate(
            "गाय को तेज बुखार है",
            triageAnswers = VetTriageAnswers(temperatureText = "105.5°F"),
            selectedCow = sampleCow
        )
        assertEquals(VetRiskLevel.EMERGENCY, scenario3High.riskLevel)

        // 4. गाय के पेट में सूजन / आफरा -> EMERGENCY
        val scenario4 = VetTriageEngine.evaluate("गाय के पेट में सूजन और आफरा है", selectedCow = sampleCow)
        assertEquals(VetRiskLevel.EMERGENCY, scenario4.riskLevel)
        assertTrue(scenario4.requiresImmediateVetCall)
        assertTrue(scenario4.detectedRedFlagsHi.any { it.contains("आफरा") || it.contains("सूजन") })

        // 5. गाय की सांस लेने में परेशानी -> EMERGENCY
        val scenario5 = VetTriageEngine.evaluate("गाय की सांस लेने में परेशानी हो रही है", selectedCow = sampleCow)
        assertEquals(VetRiskLevel.EMERGENCY, scenario5.riskLevel)
        assertTrue(scenario5.requiresImmediateVetCall)
        assertTrue(scenario5.detectedRedFlagsHi.any { it.contains("सांस") })

        // AI text can NEVER downgrade a deterministic EMERGENCY to CAUTION or NORMAL
        assertEquals(
            VetRiskLevel.EMERGENCY,
            VetTriageEngine.resolveFinalRiskLevel("3. खतरे का स्तर: सामान्य", VetRiskLevel.EMERGENCY)
        )

        // Strict Single-Cow Isolation: another cow's illness & saved report must NEVER leak into selectedCow prompt
        val otherCow = Cattle(
            id = "cow_other_999",
            animalCode = "JBGS-G-999",
            name = "कपिला",
            breed = "थारपारकर",
            healthStatus = "SICK"
        )
        val otherCowHealth = HealthRecord(
            id = "hr_other",
            cattleId = otherCow.id,
            animalCode = otherCow.animalCode,
            cattleName = otherCow.name,
            recordType = "TREATMENT",
            title = "OTHER_COW_SECRET_DISEASE_XYZ",
            medicine = "OTHER_COW_SECRET_MED"
        )
        val selectedCowPastReport = AiVetHealthReport(
            id = "rep_selected",
            cattleId = sampleCow.id,
            animalCode = sampleCow.animalCode,
            cattleName = sampleCow.name,
            symptoms = "नंदिनी का पुराना हल्का अपच",
            riskLevel = "CAUTION",
            dateStr = "2026-11-20"
        )
        val otherCowPastReport = AiVetHealthReport(
            id = "rep_other",
            cattleId = otherCow.id,
            animalCode = otherCow.animalCode,
            cattleName = otherCow.name,
            symptoms = "OTHER_COW_PAST_REPORT_SECRET",
            riskLevel = "EMERGENCY",
            dateStr = "2026-11-21"
        )

        val repo = GeminiAiRepository()
        var capturedPrompt = ""
        GeminiAiRepository.testSymptomHealthAnalyzer = { prompt ->
            capturedPrompt = prompt
            "1. संभावित समस्या: तीव्र आफरा (Bloat)\n3. खतरे का स्तर: आपातकाल\n6. पशु चिकित्सक को कब बुलाना है: तुरंत बुलाएँ।"
        }
        GeminiAiRepository.testGroundedSourcesProvider = {
            listOf(
                VetSourceReference(
                    title = "DAHD Livestock Health Advisory",
                    url = "https://dahd.nic.in/",
                    domain = "dahd.nic.in",
                    retrievedDate = "2026-11-28"
                )
            )
        }

        val reportRes = repo.generateVetHealthReport(
            bitmap = null,
            notes = "गाय के पेट में सूजन और आफरा है",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow,
            cowHealthRecords = sampleHealthRecords + otherCowHealth,
            savedVetReports = listOf(selectedCowPastReport, otherCowPastReport),
            triageAnswers = VetTriageAnswers(ruminating = "नहीं", breathingNormal = "तेज / तकलीफ"),
            isPublic = false
        )
        assertTrue(reportRes.isSuccess)
        val report = reportRes.getOrThrow()
        assertEquals(VetRiskLevel.EMERGENCY, report.riskLevel)
        assertTrue(report.sources.any { it.url == "https://dahd.nic.in/" })
        assertTrue(capturedPrompt.contains("JBGS-G-001"))
        assertTrue(capturedPrompt.contains("नंदिनी का पुराना हल्का अपच"))
        assertFalse(capturedPrompt.contains("OTHER_COW_SECRET_DISEASE_XYZ"))
        assertFalse(capturedPrompt.contains("OTHER_COW_PAST_REPORT_SECRET"))
    }

    @Test
    fun `advanced vet UI shows emergency call banner, grounded source links, saves report to cow record, views past reports, and shows real diagnostic error log`() {
        var failOnce = true
        val savedReports = mutableListOf<AiVetHealthReport>()
        val existingPastReport = AiVetHealthReport(
            id = "rep_old_1",
            cattleId = sampleCow.id,
            animalCode = sampleCow.animalCode,
            cattleName = sampleCow.name,
            symptoms = "पुराना हल्का बुखार",
            riskLevel = "CAUTION",
            reportText = "1. संभावित समस्या: हल्का बुखार\n3. खतरे का स्तर: सावधानी",
            sourceLinks = listOf("ICAR Cattle Health|https://icar.org.in/|2026-11-20"),
            dateStr = "2026-11-20",
            recordedBy = adminSession.uid,
            recordedByName = adminSession.displayName
        )

        GeminiAiRepository.testSymptomHealthAnalyzer = {
            if (failOnce) {
                failOnce = false
                throw IllegalStateException("HTTP 503 Vertex backend temporarily unavailable")
            }
            "1. संभावित समस्या: तीव्र आफरा (Bloat)\n3. खतरे का स्तर: आपातकाल\n5. क्या नहीं करना है: बिना पशु चिकित्सक की जांच कोई एंटीबायोटिक या इंजेक्शन न दें।\n6. पशु चिकित्सक को कब बुलाना है: तुरंत बुलाएँ।"
        }
        GeminiAiRepository.testGroundedSourcesProvider = {
            listOf(
                VetSourceReference(
                    title = "DAHD Govt of India",
                    url = "https://dahd.nic.in/",
                    domain = "dahd.nic.in",
                    retrievedDate = "2026-11-28"
                ),
                VetSourceReference(
                    title = "IVRI Veterinary Research",
                    url = "https://www.ivri.nic.in/",
                    domain = "ivri.nic.in",
                    retrievedDate = "2026-11-28"
                )
            )
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
                    savedVetReports = listOf(existingPastReport),
                    vetPhoneNumber = "9876543210",
                    onSaveVetPhone = {},
                    onSaveVetReport = { savedReports.add(it) },
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

        // 1. Select cow -> Saved past reports card appears and opens cleanly
        composeRule.onNodeWithTag("ai_select_cow_JBGS-G-001").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_saved_vet_reports_card"))
        composeRule.onNodeWithTag("ai_view_past_reports_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_saved_report_item_0").assertExists()

        // 2. Enter emergency bloat symptom -> Immediate Emergency Vet Call Banner appears!
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("ai_photo_notes_input").performScrollTo().performTextInput("गाय के पेट में सूजन और आफरा है")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_emergency_vet_banner").assertExists()

        // 3. Tap Generate Report -> First call fails -> Real Diagnostic Error Log MUST be visible!
        composeRule.onNodeWithTag("analyze_cattle_photo_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_photo_error").assertExists()
        composeRule.onNodeWithTag("ai_error_diagnostic_log").assertExists()
        val diag = (vm.photoAnalysisState.value as AiUiState.Error).diagnosticDetail.orEmpty()
        assertTrue(diag.contains("503"))

        // 4. Tap Retry -> Succeeds with EMERGENCY badge, clickable Google Search sources, and Save Report button
        composeRule.onNodeWithTag("ai_photo_retry_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(vm.photoAnalysisState.value is AiUiState.Success)
        composeRule.onNodeWithTag("vet_risk_badge_emergency").assertExists()
        composeRule.onNodeWithTag("ai_vet_sources_card").assertExists()
        composeRule.onNodeWithTag("ai_vet_source_0").assertExists()

        // 5. Save Report to Cow Record triggers onSaveVetReport with accurate cow ID & EMERGENCY risk level
        composeRule.onNodeWithTag("ai_save_vet_report_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, savedReports.size)
        assertEquals("JBGS-G-001", savedReports.first().animalCode)
        assertEquals("EMERGENCY", savedReports.first().riskLevel)
        assertTrue(savedReports.first().sourceLinks.any { it.contains("https://dahd.nic.in/") })
    }

    @Test
    fun `Hindi and Hinglish triage, offline and API-disabled error messages, diagnostic redaction, and view-only Staff UI permissions are enforced`() {
        val repo = GeminiAiRepository()
        val vm = AiViewModel()

        // 1. Hinglish & Hindi detection + risk classification
        assertTrue(repo.isHealthSymptomQuestion("गौशाला सवाल-जवाब", "gay ka pet phul gaya hai aur saans lene me taklif hai"))
        assertTrue(repo.isHealthSymptomQuestion("गौशाला सवाल-जवाब", "gay chara nahi kha rahi aur bukhar hai"))
        val hinglishEmergency = VetTriageEngine.evaluate("gay khadi nahi ho pa rahi aur pet me sujan hai")
        assertEquals(VetRiskLevel.EMERGENCY, hinglishEmergency.riskLevel)
        assertTrue(hinglishEmergency.requiresImmediateVetCall)
        val hinglishCaution = VetTriageEngine.evaluate("gay chara nahi kha rahi hai aur bukhar hai")
        assertEquals(VetRiskLevel.CAUTION, hinglishCaution.riskLevel)
        assertFalse(hinglishCaution.requiresImmediateVetCall)

        // 2. Network offline, API disabled, App Check 401, Quota 429, Timeout error formatting & key redaction
        val offlineMsg = vm.formatAiErrorMessage(
            java.net.UnknownHostException("Unable to resolve host \"firebasevertexai.googleapis.com\": No address associated with hostname"),
            AppLanguage.HINDI
        )
        assertTrue(offlineMsg.contains("इंटरनेट"))

        val apiDisabledMsg = vm.formatAiErrorMessage(
            IllegalStateException("PERMISSION_DENIED: Firebase Vertex AI API has not been used in project or is SERVICE_DISABLED"),
            AppLanguage.HINDI
        )
        assertTrue(apiDisabledMsg.contains("API Permission") || apiDisabledMsg.contains("अनुमति"))

        val appCheck401Msg = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 401: Firebase App Check token is invalid."),
            AppLanguage.HINDI
        )
        assertTrue(appCheck401Msg.contains("App Check") || appCheck401Msg.contains("सुरक्षा सत्यापन"))

        val redactedLog = vm.buildDiagnosticLog(
            IllegalStateException("Request failed with AIzaSySecretExampleKey123456789012345 token")
        )
        assertFalse(redactedLog.contains("AIzaSySecretExampleKey"))
        assertTrue(redactedLog.contains("[REDACTED_KEY]"))

        // 3. View-only Staff (cattle:VIEW, ai:VIEW, NO health:ADD / NO ai:ADD / NO cattle:EDIT):
        //    Can view saved past reports for the selected cow, CANNOT see Save Report button!
        val cow2 = Cattle(
            id = "cow_357",
            animalCode = "JBGS-G-357",
            name = "लक्ष्मी",
            breed = "साहीवाल",
            ageText = "6 वर्ष",
            healthStatus = "HEALTHY"
        )
        val reportCow1 = AiVetHealthReport(
            id = "r1",
            cattleId = sampleCow.id,
            animalCode = sampleCow.animalCode,
            cattleName = sampleCow.name,
            symptoms = "नंदिनी का लक्षण",
            riskLevel = "CAUTION",
            reportText = "नंदिनी रिपोर्ट",
            dateStr = "2026-11-28"
        )
        val reportCow2 = AiVetHealthReport(
            id = "r2",
            cattleId = cow2.id,
            animalCode = cow2.animalCode,
            cattleName = cow2.name,
            symptoms = "लक्ष्मी का लक्षण",
            riskLevel = "NORMAL",
            reportText = "लक्ष्मी रिपोर्ट",
            dateStr = "2026-11-28"
        )
        GeminiAiRepository.testSymptomHealthAnalyzer = {
            "1. संभावित समस्या: हल्का अपच\n3. खतरे का स्तर: सावधानी"
        }

        composeRule.setContent {
            GosalaTheme {
                AiAssistantScreen(
                    language = AppLanguage.HINDI,
                    session = limitedStaffSession,
                    summary = PublicSummary(totalCattle = 2, healthyCattle = 1, sickCattle = 1),
                    cattle = listOf(sampleCow, cow2),
                    health = sampleHealthRecords,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    farmers = emptyList(),
                    farmerTx = emptyList(),
                    compost = emptyList(),
                    donations = emptyList(),
                    products = emptyList(),
                    savedVetReports = listOf(reportCow1, reportCow2),
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

        // Select लक्ष्मी (JBGS-G-357) -> Past reports show ONLY लक्ष्मी (1), NOT नंदिनी
        composeRule.onNodeWithTag("ai_select_cow_JBGS-G-357").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_saved_vet_reports_card"))
        composeRule.onNodeWithTag("ai_view_past_reports_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_saved_report_item_0").assertExists()
        composeRule.onNodeWithTag("ai_saved_report_item_1").assertDoesNotExist()

        // Generate report as view-only staff -> report renders, but Save button is strictly hidden
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("ai_photo_notes_input").performScrollTo().performTextInput("gay chara nahi kha rahi")
        composeRule.onNodeWithTag("analyze_cattle_photo_btn").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_photo_success_report").assertExists()
        composeRule.onNodeWithTag("ai_save_vet_report_btn").assertDoesNotExist()
    }

    @Test
    fun `App Check HTTP 401 auto-refreshes token and retries, while 403 SERVICE_DISABLED is never misclassified`() = runBlocking {
        // 1. Exact classification: 401 UNAUTHENTICATED is App Check; 403 SERVICE_DISABLED is NOT App Check
        assertTrue(isAppCheckTokenError(IllegalStateException("HTTP 401 UNAUTHENTICATED: Firebase App Check token is invalid.")))
        assertTrue(isAppCheckTokenError(IllegalStateException("HTTP 401 UNAUTHENTICATED")))
        assertFalse(isAppCheckTokenError(IllegalStateException("HTTP 403 PERMISSION_DENIED: SERVICE_DISABLED API has not been used in project")))
        assertFalse(isAppCheckTokenError(IllegalStateException("HTTP 403 billing not enabled")))

        // 2. Real retry loop: first attempt fails with HTTP 401 -> refreshAppCheckToken(forceRefresh=true) runs -> second attempt succeeds
        var refreshCalls = 0
        val attemptedModels = mutableListOf<String>()
        GeminiAiRepository.testAppCheckRefreshHandler = { force ->
            assertTrue(force)
            refreshCalls++
            true
        }
        GeminiAiRepository.testModelAttemptRunner = { spec, attempt, _ ->
            attemptedModels.add("${spec.modelName}:grounded=${spec.isGrounded}:attempt=$attempt")
            if (attempt == 1) {
                throw IllegalStateException("HTTP 401 UNAUTHENTICATED: Firebase App Check token is invalid.")
            }
            "1. संभावित समस्या: हल्का अपच\n3. खतरे का स्तर: सावधानी\n8. विश्वसनीय जानकारी के स्रोत: DAHD" to listOf(
                VetSourceReference(
                    title = "DAHD Livestock Health Portal",
                    url = "https://dahd.nic.in/schemes-programmes/animal-health",
                    domain = "dahd.nic.in",
                    retrievedDate = "2026-11-28"
                )
            )
        }

        val repo = GeminiAiRepository()
        val result = repo.generateVetHealthReport(
            bitmap = null,
            notes = "गाय चारा कम खा रही है",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow
        )
        assertTrue("Expected retry after App Check 401 refresh to succeed", result.isSuccess)
        val report = result.getOrThrow()
        assertEquals(1, refreshCalls)
        assertEquals("gemini-3.8-flash", report.modelUsed)
        assertFalse(report.usedFallbackModel)
        assertTrue(report.usedGoogleSearch)
        assertEquals(1, report.sources.size)
        assertEquals("https://dahd.nic.in/schemes-programmes/animal-health", report.sources.first().url)
        assertEquals("2026-11-28", report.sources.first().retrievedDate)
        assertEquals(
            listOf(
                "gemini-3.8-flash:grounded=true:attempt=1",
                "gemini-3.8-flash:grounded=true:attempt=2"
            ),
            attemptedModels
        )
    }

    @Test
    fun `Fallback model gemini-3_5-flash-lite activates on primary 503 failure while preserving Google Search sources and UI badge`() {
        val attemptedSpecs = mutableListOf<String>()
        GeminiAiRepository.testModelAttemptRunner = { spec, _, _ ->
            attemptedSpecs.add("${spec.modelName}:grounded=${spec.isGrounded}:fallback=${spec.isFallback}")
            if (spec.modelName == "gemini-3.8-flash") {
                throw IllegalStateException("HTTP 503 Service Unavailable: gemini-3.8-flash temporarily overloaded")
            }
            "1. संभावित समस्या: हल्का बुखार व कमजोरी\n3. खतरे का स्तर: सावधानी\n8. विश्वसनीय जानकारी के स्रोत: ICAR-IVRI" to listOf(
                VetSourceReference(
                    title = "ICAR-IVRI Cattle Health Advisory",
                    url = "https://www.ivri.nic.in/advisory/cattle-fever",
                    domain = "ivri.nic.in",
                    retrievedDate = "2026-11-28"
                )
            )
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
                    savedVetReports = emptyList(),
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

        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("ai_photo_notes_input").performScrollTo().performTextInput("gay ko bukhar hai")
        composeRule.onNodeWithTag("analyze_cattle_photo_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        val state = vm.photoAnalysisState.value as? AiUiState.Success
        assertNotNull("Expected fallback model to produce Success state", state)
        assertEquals("gemini-3.5-flash-lite", state!!.modelUsed)
        assertTrue(state.usedFallbackModel)
        assertTrue(state.usedGoogleSearch)
        assertEquals(1, state.sources.size)
        assertEquals("https://www.ivri.nic.in/advisory/cattle-fever", state.sources.first().url)
        assertEquals("2026-11-28", state.sources.first().retrievedDate)

        // Verify exact fallback sequence: primary grounded -> fallback lite grounded
        assertEquals(
            listOf(
                "gemini-3.8-flash:grounded=true:fallback=false",
                "gemini-3.5-flash-lite:grounded=true:fallback=true"
            ),
            attemptedSpecs
        )

        // UI displays fallback badge, source URL, and retrieved date
        composeRule.onNodeWithTag("ai_fallback_model_badge").assertExists()
        composeRule.onNodeWithTag("ai_vet_sources_card").assertExists()
        composeRule.onNodeWithText("https://www.ivri.nic.in/advisory/cattle-fever", substring = true).assertExists()
        composeRule.onNodeWithText("2026-11-28", substring = true).assertExists()
    }

    @Test
    fun `Search tool 400 unsupported error gracefully falls back to non-grounded primary model`() = runBlocking {
        val sequence = mutableListOf<String>()
        GeminiAiRepository.testModelAttemptRunner = { spec, _, _ ->
            sequence.add("${spec.modelName}:grounded=${spec.isGrounded}")
            if (spec.isGrounded) {
                throw IllegalStateException("HTTP 400 INVALID_ARGUMENT: google_search grounding tool is not enabled for this tier")
            }
            "1. संभावित समस्या: सामान्य अपच\n3. खतरे का स्तर: सावधानी" to emptyList()
        }

        val repo = GeminiAiRepository()
        val result = repo.generateVetHealthReport(
            bitmap = null,
            notes = "गाय सुस्त है",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow
        )
        assertTrue(result.isSuccess)
        val report = result.getOrThrow()
        assertEquals("gemini-3.8-flash", report.modelUsed)
        assertFalse(report.usedGoogleSearch)
        // Must skip grounded fallback lite when Search tool itself is unsupported and jump straight to non-grounded primary
        assertEquals(
            listOf("gemini-3.8-flash:grounded=true", "gemini-3.8-flash:grounded=false"),
            sequence
        )
    }

    @Test
    fun `Step 7 distinct HTTP error codes (401, 403, 404, 429, 500, Network) and Step 5 honest ungrounded UI never claim false Google Search`() = runBlocking {
        val vm = AiViewModel()

        // 1. All 6 distinct HTTP / Network error classifications in Hindi
        val msg401 = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 401 UNAUTHENTICATED: Firebase App Check token is invalid."),
            AppLanguage.HINDI
        )
        assertTrue(msg401.contains("HTTP 401"))
        assertTrue(msg401.contains("App Check"))

        val msg403AppCheck = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 403 PERMISSION_DENIED: App Check attestation failed"),
            AppLanguage.HINDI
        )
        assertTrue(msg403AppCheck.contains("HTTP 403"))
        assertTrue(msg403AppCheck.contains("App Check"))

        val msg403Disabled = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 403 PERMISSION_DENIED: SERVICE_DISABLED API has not been used"),
            AppLanguage.HINDI
        )
        assertTrue(msg403Disabled.contains("HTTP 403"))
        assertTrue(msg403Disabled.contains("API Permission"))

        val msg404 = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 404 NOT_FOUND: models/gemini-unknown is not found"),
            AppLanguage.HINDI
        )
        assertTrue(msg404.contains("HTTP 404"))
        assertTrue(msg404.contains("Model") || msg404.contains("Endpoint"))

        val msg429 = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 429 RESOURCE_EXHAUSTED: Quota exceeded"),
            AppLanguage.HINDI
        )
        assertTrue(msg429.contains("HTTP 429"))
        assertTrue(msg429.contains("Quota"))

        val msg500 = vm.formatAiErrorMessage(
            IllegalStateException("HTTP 500 INTERNAL: Backend server error"),
            AppLanguage.HINDI
        )
        assertTrue(msg500.contains("HTTP 500/503"))
        assertTrue(msg500.contains("Backend Server"))

        val msgNet = vm.formatAiErrorMessage(
            java.net.UnknownHostException("Unable to resolve host"),
            AppLanguage.HINDI
        )
        assertTrue(msgNet.contains("Network Error"))
        assertTrue(msgNet.contains("इंटरनेट"))

        // 2. Step 5 Honesty: Non-grounded fallback prompt omits Google Search claim & forbids fake URLs,
        //    and 0 returned sources sets usedGoogleSearch=false (never falsely claims Google Search succeeded!)
        var capturedFallbackPrompt = ""
        GeminiAiRepository.testModelAttemptRunner = { spec, _, prompt ->
            if (spec.isGrounded) {
                throw IllegalStateException("HTTP 400 INVALID_ARGUMENT: google_search not supported")
            }
            capturedFallbackPrompt = prompt
            "1. संभावित समस्या: हल्का बुखार\n3. खतरे का स्तर: सावधानी\n8. जानकारी का आधार: लाइव वेब खोज उपलब्ध नहीं थी।" to emptyList()
        }

        val repo = GeminiAiRepository()
        val ungroundedRes = repo.generateVetHealthReport(
            bitmap = null,
            notes = "गाय को बुखार है",
            language = AppLanguage.HINDI,
            selectedCow = sampleCow
        )
        assertTrue(ungroundedRes.isSuccess)
        val report = ungroundedRes.getOrThrow()
        assertFalse(report.usedGoogleSearch)
        assertTrue(report.searchAttempted)
        assertTrue(report.sources.isEmpty())
        assertFalse(capturedFallbackPrompt.contains("GOOGLE SEARCH GROUNDING REQUIREMENT"))
        assertTrue(capturedFallbackPrompt.contains("NO LIVE WEB SEARCH AVAILABLE"))

        // 3. UI renders honest unavailable card (NOT "Google Search Grounded") when 0 sources returned
        composeRule.setContent {
            GosalaTheme {
                AiAssistantScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    summary = PublicSummary(totalCattle = 1, healthyCattle = 1),
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
        composeRule.onNodeWithTag("ai_content_list").performScrollToNode(hasTestTag("ai_photo_health_card"))
        composeRule.onNodeWithTag("ai_photo_notes_input").performScrollTo().performTextInput("गाय को बुखार है")
        composeRule.onNodeWithTag("analyze_cattle_photo_btn").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_vet_sources_unavailable_card").assertExists()
        composeRule.onNodeWithTag("ai_vet_sources_card").assertDoesNotExist()
    }
}



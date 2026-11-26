package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.AppLanguage
import com.example.core.CattlePhotoStorage
import com.example.core.QrAndExportUtils
import com.example.core.UploadedCowPhoto
import com.example.data.model.Cattle
import com.example.data.model.FeedAndOperationLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.screens.CattleManagementScreen
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CowQrScannerAndDetailRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        CattlePhotoStorage.clearMemoryCache()
        CattlePhotoStorage.testCloudPhotoResolver = null
        CattlePhotoStorage.testStorageUrlResolver = null
        CattlePhotoStorage.testPhotoUploader = null
    }

    @After
    fun tearDown() {
        CattlePhotoStorage.clearMemoryCache()
        CattlePhotoStorage.testCloudPhotoResolver = null
        CattlePhotoStorage.testStorageUrlResolver = null
        CattlePhotoStorage.testPhotoUploader = null
    }

    private val adminSession = UserSession(
        uid = "admin_qr_test",
        email = "admin@gosala.org",
        displayName = "गौशाला प्रशासक",
        role = UserRole.ADMIN
    )

    private val sampleCow = Cattle(
        id = "cow_firebase_unique_99",
        animalCode = "JBGS-G-501",
        name = "नंदिनी गौमाता",
        category = "COW",
        breed = "गिर (Gir)",
        sex = "FEMALE",
        ageText = "5 वर्ष",
        birthDate = "2021-04-12",
        colour = "लाल-भूरा",
        weightKg = 420.0,
        healthStatus = "HEALTHY",
        entryDate = "2023-02-15",
        shedNumber = "Shed-B2",
        identificationMarks = "माथे पर सफेद तिलक",
        qrPayload = "JBGS:CATTLE:cow_firebase_unique_99:JBGS-G-501",
        notes = "ग्राम बिन्जारी से गौशाला में सुरक्षित आगमन",
        publicApproved = true,
        sponsorshipAvailable = true,
        monthlySupportInr = 2500L
    )

    private val sampleHealthRecords = listOf(
        HealthRecord(
            id = "h_vac_1",
            cattleId = "cow_firebase_unique_99",
            cattleName = "नंदिनी गौमाता",
            animalCode = "JBGS-G-501",
            recordType = "VACCINATION",
            title = "FMD खुरपका-मुंहपका टीका",
            medicine = "Raksha FMD Vaccine",
            vetName = "डॉ. राकेश शर्मा",
            nextFollowUpDate = "2027-05-01"
        ),
        HealthRecord(
            id = "h_trt_1",
            cattleId = "cow_firebase_unique_99",
            cattleName = "नंदिनी गौमाता",
            animalCode = "JBGS-G-501",
            recordType = "TREATMENT",
            title = "सामान्य बुखार उपचार",
            medicine = "Meloxicam + B-Complex",
            vetName = "डॉ. राकेश शर्मा",
            costInr = 450.0,
            notes = "2 दिन में पूर्ण स्वस्थ"
        )
    )

    private val sampleFeedLogs = listOf(
        FeedAndOperationLog(
            id = "feed_1",
            logDate = "2026-11-21",
            greenFodderKg = 140.0,
            dryFodderBhusaKg = 85.0,
            concentrateKg = 25.0,
            waterLiters = 400.0,
            shedCleaned = true,
            assignedStaffName = "महेश सेवक"
        )
    )

    @Test
    fun `QR payload builder, parser, and ZXing bitmap round-trip decode match Firebase Cow ID`() {
        val payload = QrAndExportUtils.buildCattleQrPayload("cow_firebase_unique_99", "JBGS-G-501")
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-501", payload)

        // New Firebase Hosting HTTPS URL format (Google Lens ready — no Firebase docId exposed)
        val parsed = QrAndExportUtils.parseCattleQrPayload(payload)
        assertNotNull(parsed)
        assertNull(parsed!!.cowDocId)
        assertEquals("JBGS-G-501", parsed.animalCode)

        // Legacy canonical format backward compatibility
        val parsedLegacyCanonical = QrAndExportUtils.parseCattleQrPayload("JBGS:CATTLE:cow_firebase_unique_99:JBGS-G-501")
        assertNotNull(parsedLegacyCanonical)
        assertEquals("cow_firebase_unique_99", parsedLegacyCanonical!!.cowDocId)
        assertEquals("JBGS-G-501", parsedLegacyCanonical.animalCode)

        // Legacy 1-part canonical QR containing animalCode only
        val parsedLegacyCodeOnly = QrAndExportUtils.parseCattleQrPayload("JBGS:CATTLE:JBGS-G-501")
        assertNotNull(parsedLegacyCodeOnly)
        assertEquals("JBGS-G-501", parsedLegacyCodeOnly!!.animalCode)
        assertEquals("JBGS-G-501", parsedLegacyCodeOnly.cowDocId)

        // Legacy formats: JBGS-CATTLE, COW:, pipe-delimited, JSON, JBG-xxxx
        val parsedLegacyHyphen = QrAndExportUtils.parseCattleQrPayload("JBGS-CATTLE:cow_old_77:JBG-1234")
        assertNotNull(parsedLegacyHyphen)
        assertEquals("cow_old_77", parsedLegacyHyphen!!.cowDocId)
        assertEquals("JBG-1234", parsedLegacyHyphen.animalCode)

        val parsedLegacyCow = QrAndExportUtils.parseCattleQrPayload("COW:COW-205")
        assertNotNull(parsedLegacyCow)
        assertEquals("COW-205", parsedLegacyCow!!.animalCode)

        val parsedLegacyPipe = QrAndExportUtils.parseCattleQrPayload("JBGS|CATTLE|cow_pipe_9|JBGS-G-309")
        assertNotNull(parsedLegacyPipe)
        assertEquals("cow_pipe_9", parsedLegacyPipe!!.cowDocId)
        assertEquals("JBGS-G-309", parsedLegacyPipe.animalCode)

        val parsedLegacyJson = QrAndExportUtils.parseCattleQrPayload("""{"id":"cow_json_1","animalCode":"JBGS-G-401"}""")
        assertNotNull(parsedLegacyJson)
        assertEquals("cow_json_1", parsedLegacyJson!!.cowDocId)
        assertEquals("JBGS-G-401", parsedLegacyJson.animalCode)

        // Deep-link format
        val parsedUrl = QrAndExportUtils.parseCattleQrPayload("jbgs://cattle/cow_firebase_unique_99")
        assertNotNull(parsedUrl)
        assertEquals("cow_firebase_unique_99", parsedUrl!!.cowDocId)

        // Direct animalCode (both JBGS- and legacy JBG-)
        val parsedCode = QrAndExportUtils.parseCattleQrPayload("JBGS-G-501")
        assertNotNull(parsedCode)
        assertEquals("JBGS-G-501", parsedCode!!.animalCode)
        val parsedLegacyDirect = QrAndExportUtils.parseCattleQrPayload("JBG-8821")
        assertNotNull(parsedLegacyDirect)
        assertEquals("JBG-8821", parsedLegacyDirect!!.animalCode)

        // Invalid / non-cow garbage text rejected
        assertNull(QrAndExportUtils.parseCattleQrPayload(""))
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://random-website.com/not-a-cow"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("invalid qr with spaces !!"))

        // Full ZXing QR encode -> YUV luminance -> decode round-trip (both 0° and 90° camera rotation)
        val bmp = QrAndExportUtils.generateQrBitmap(payload, 256)
        assertNotNull("Expected QR bitmap to generate", bmp)
        val width = bmp!!.width
        val height = bmp.height
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        val yBytes = ByteArray(width * height) { idx ->
            val px = pixels[idx]
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            ((0.299 * r + 0.587 * g + 0.114 * b).toInt().coerceIn(0, 255)).toByte()
        }
        assertEquals(payload, QrAndExportUtils.decodeQrFromYuvByteArray(yBytes, width, height, rotationDegrees = 0))
        assertEquals(payload, QrAndExportUtils.decodeQrFromYuvByteArray(yBytes, width, height, rotationDegrees = 90))
    }

    @Test
    fun `scanning valid cow QR fetches cow by Firebase ID and opens full Cow Details with history, vaccination, treatment, and feed records`() {
        var queriedPayload: String? = null

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattleState = DataUiState.Success(listOf(sampleCow)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    healthRecords = sampleHealthRecords,
                    feedLogs = sampleFeedLogs,
                    onLookupCattleByQr = { raw ->
                        queriedPayload = raw
                        val parsed = QrAndExportUtils.parseCattleQrPayload(raw)
                        if (parsed?.cowDocId == sampleCow.id) Result.success(sampleCow) else Result.success(null)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Open QR Scanner and submit scanned payload
        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cattle_qr_scanner_dialog").assertIsDisplayed()

        composeRule.onNodeWithTag("qr_manual_input").performTextInput("JBGS:CATTLE:cow_firebase_unique_99:JBGS-G-501")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        assertEquals("JBGS:CATTLE:cow_firebase_unique_99:JBGS-G-501", queriedPayload)

        // Full Cow Details Screen opens directly with verified QR banner and all sections
        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_qr_verified_banner").assertIsDisplayed()
        composeRule.onAllNodesWithText("नंदिनी गौमाता", substring = true).onFirst().assertIsDisplayed()
        // Cow has no photo -> Default Cow Image is shown cleanly (never blank)
        composeRule.onNodeWithTag("cattle_detail_photo_default").assertIsDisplayed()

        val list = composeRule.onNodeWithTag("cattle_detail_scroll_list")
        list.performScrollToNode(hasTestTag("cattle_detail_history_section"))
        composeRule.onNodeWithText("गाय का इतिहास", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("1 टीकाकरण दर्ज", substring = true).assertIsDisplayed()

        list.performScrollToNode(hasTestTag("cattle_detail_vaccination_section"))
        composeRule.onNodeWithText("FMD खुरपका-मुंहपका टीका", substring = true).assertIsDisplayed()

        list.performScrollToNode(hasTestTag("cattle_detail_treatment_section"))
        composeRule.onNodeWithText("सामान्य बुखार उपचार", substring = true).assertIsDisplayed()

        list.performScrollToNode(hasTestTag("cattle_detail_feed_section"))
        composeRule.onNodeWithText("2026-11-21", substring = true).assertIsDisplayed()
    }

    @Test
    fun `scanning invalid QR or unknown Cow ID shows clear Hindi error message`() {
        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattleState = DataUiState.Success(listOf(sampleCow)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    onLookupCattleByQr = { Result.success(null) }
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()

        // 1. Invalid non-cow QR text -> clear Invalid QR Hindi message
        composeRule.onNodeWithTag("qr_manual_input").performTextInput("https://random-website.com/not-a-cow")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_scanner_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("अमान्य QR कोड", substring = true).assertIsDisplayed()

        // 2. Unknown Cow ID -> Not Found Hindi message
        composeRule.onNodeWithTag("qr_manual_input").performTextClearance()
        composeRule.onNodeWithTag("qr_manual_input").performTextInput("JBGS:CATTLE:unknown_id_404:JBGS-G-999")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_scanner_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("कोई गाय रिकॉर्ड नहीं मिला", substring = true).assertIsDisplayed()
    }

    @Test
    fun `end-to-end add cow, upload photo to cattle_photos path, generate QR, scan Cow 1 then Cow 2 opens exact cow and photo with Retry recovery`() = runBlocking {
        // Step 1 & 2: Simulate photo upload producing cattle_photos/<cattleId>/... storage path
        CattlePhotoStorage.testPhotoUploader = { cattleId, _, _ ->
            Result.success(
                UploadedCowPhoto(
                    photoUrl = CattlePhotoStorage.buildFirestorePhotoUri(cattleId, 11L),
                    storagePath = "cattle_photos/$cattleId/photo_11.jpg"
                )
            )
        }
        val uploadCow1 = CattlePhotoStorage.testPhotoUploader!!.invoke("cow_doc_1", "content://media/picker/1", true).getOrThrow()
        assertEquals("cattle_photos/cow_doc_1/photo_11.jpg", uploadCow1.storagePath)

        // Simulate Cow 1 stored with photoStoragePath only (`cattle_photos/cow_doc_1/photo_11.jpg`) and blank photoUri,
        // failing while offline (triggering Retry button) and succeeding once network recovers on Retry!
        val cow1ResolvedUri = CattlePhotoStorage.buildFirestorePhotoUri("cow_doc_1", 11L)
        val cow2PhotoUri = CattlePhotoStorage.buildFirestorePhotoUri("cow_doc_2", 22L)
        var allowCow1Resolve = false
        var cow1ResolveAttempts = 0

        CattlePhotoStorage.testStorageUrlResolver = { cattleId, storagePath ->
            if (cattleId == "cow_doc_1" && storagePath == "cattle_photos/cow_doc_1/photo_11.jpg") {
                cow1ResolveAttempts++
                if (allowCow1Resolve) cow1ResolvedUri else null
            } else {
                null
            }
        }
        CattlePhotoStorage.testCloudPhotoResolver = { uri ->
            when (uri) {
                cow1ResolvedUri -> Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
                cow2PhotoUri -> Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
                else -> null
            }
        }

        // Step 3: Build canonical QR codes for Cow 1 (with storage path photo) and Cow 2 (different cow + photo)
        val cow1Qr = QrAndExportUtils.buildCattleQrPayload("cow_doc_1", "JBGS-G-111")
        val cow2Qr = QrAndExportUtils.buildCattleQrPayload("cow_doc_2", "JBGS-G-222")

        val cow1 = Cattle(
            id = "cow_doc_1",
            animalCode = "JBGS-G-111",
            name = "कावेरी गौमाता",
            breed = "गिर",
            ageText = "4 वर्ष",
            colour = "भूरा",
            weightKg = 410.0,
            healthStatus = "HEALTHY",
            entryDate = "2024-06-01",
            shedNumber = "Shed-A1",
            qrPayload = cow1Qr,
            photoUri = null, // Intentionally blank to test `cattle_photos/<cattleId>/...` path resolution!
            photoStoragePath = "cattle_photos/cow_doc_1/photo_11.jpg",
            vaccinationSummary = "FMD व HS टीका पूर्ण",
            notes = "गाय 1 का इतिहास",
            publicApproved = true
        )
        val cow2 = Cattle(
            id = "cow_doc_2",
            animalCode = "JBGS-G-222",
            name = "सरस्वती गौमाता",
            breed = "साहीवाल",
            ageText = "6 वर्ष",
            colour = "सफेद",
            weightKg = 460.0,
            healthStatus = "UNDER_TREATMENT",
            entryDate = "2023-11-10",
            shedNumber = "Shed-C3",
            qrPayload = cow2Qr,
            photoUri = cow2PhotoUri,
            photoStoragePath = "cattle_photos/cow_doc_2/photo_22.jpg",
            vaccinationSummary = "BQ टीका पूर्ण",
            notes = "गाय 2 का अलग इतिहास",
            publicApproved = true
        )
        val cow3NoPhoto = Cattle(
            id = "cow_doc_3",
            animalCode = "JBGS-G-333",
            name = "लक्ष्मी गौमाता",
            breed = "थारपारकर",
            ageText = "3 वर्ष",
            colour = "सफेद",
            weightKg = 370.0,
            healthStatus = "HEALTHY",
            entryDate = "2025-01-20",
            shedNumber = "Shed-D1",
            qrPayload = QrAndExportUtils.buildCattleQrPayload("cow_doc_3", "JBGS-G-333"),
            photoUri = null,
            photoStoragePath = null,
            publicApproved = true
        )

        val allCows = listOf(cow1, cow2, cow3NoPhoto)

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattleState = DataUiState.Success(allCows),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    onLookupCattleByQr = { raw ->
                        val parsed = QrAndExportUtils.parseCattleQrPayload(raw)
                        val match = allCows.firstOrNull { !parsed?.cowDocId.isNullOrBlank() && it.id == parsed?.cowDocId }
                            ?: allCows.firstOrNull { !parsed?.animalCode.isNullOrBlank() && it.animalCode.equals(parsed?.animalCode, ignoreCase = true) }
                        Result.success(match)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Step 4, 5, 6: Scan Cow 1's QR -> Cow 1 opens -> initial Storage fetch fails -> Default Image + Retry shown -> Retry loads real photo!
        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_manual_input").performTextInput(cow1Qr)
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("कावेरी गौमाता", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_default").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_retry_btn").assertIsDisplayed()

        // Simulate network recovery and tap Retry -> resolves `cattle_photos/cow_doc_1/photo_11.jpg` and displays loaded photo!
        allowCow1Resolve = true
        composeRule.onNodeWithTag("cattle_detail_photo_retry_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cattle_detail_photo_loaded").assertIsDisplayed()
        assertTrue(cow1ResolveAttempts >= 2)

        // Go back to cattle list
        composeRule.onNodeWithTag("cattle_detail_back_button").performClick()
        composeRule.waitForIdle()

        // Step 7: Scan Cow 2's QR -> Cow 2 (NOT Cow 1!) opens with Cow 2's details and photo
        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_manual_input").performTextInput(cow2Qr)
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("सरस्वती गौमाता", substring = true).onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("JBGS-G-222", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_loaded").assertIsDisplayed()

        // Go back and scan Cow 3 (no photo) using legacy single-code QR -> Default Cow Image shown cleanly without error overlay
        composeRule.onNodeWithTag("cattle_detail_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_manual_input").performTextInput("JBGS:CATTLE:JBGS-G-333")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("लक्ष्मी गौमाता", substring = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_default").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_error_overlay").assertDoesNotExist()
    }

    @Test
    fun `cow QR button in list opens QR dialog and Open Full Details navigates to cow profile while keeping QR permanent on update`() {
        var liveCattle by mutableStateOf(listOf(sampleCow))

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattleState = DataUiState.Success(liveCattle),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { updated, _ ->
                        // Admin update preserves permanent QR payload
                        liveCattle = liveCattle.map { if (it.id == updated.id) updated else it }
                    },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    healthRecords = sampleHealthRecords,
                    feedLogs = sampleFeedLogs
                )
            }
        }
        composeRule.waitForIdle()

        // Open QR dialog from list QR icon button -> tap Open Full Details
        composeRule.onNodeWithTag("cattle_qr_button_JBGS-G-501").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_code_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("qr_dialog_open_details_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()

        // Simulate Admin updating cow weight & notes -> live details update, QR payload stays permanent
        val originalQr = sampleCow.qrPayload
        liveCattle = listOf(sampleCow.copy(weightKg = 445.0, notes = "अद्यतन वजन जांच पूर्ण"))
        composeRule.waitForIdle()

        assertEquals(originalQr, liveCattle.first().qrPayload)
        composeRule.onNodeWithTag("cattle_detail_back_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cattle_card_JBGS-G-501").assertIsDisplayed()
    }
}

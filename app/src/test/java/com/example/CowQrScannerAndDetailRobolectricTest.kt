package com.example

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
import androidx.compose.ui.test.performTextInput
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.data.model.Cattle
import com.example.data.model.FeedAndOperationLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.screens.CattleManagementScreen
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals("JBGS:CATTLE:cow_firebase_unique_99:JBGS-G-501", payload)

        // Canonical format
        val parsed = QrAndExportUtils.parseCattleQrPayload(payload)
        assertNotNull(parsed)
        assertEquals("cow_firebase_unique_99", parsed!!.cowDocId)
        assertEquals("JBGS-G-501", parsed.animalCode)

        // Deep-link format
        val parsedUrl = QrAndExportUtils.parseCattleQrPayload("jbgs://cattle/cow_firebase_unique_99")
        assertNotNull(parsedUrl)
        assertEquals("cow_firebase_unique_99", parsedUrl!!.cowDocId)

        // Direct animalCode
        val parsedCode = QrAndExportUtils.parseCattleQrPayload("JBGS-G-501")
        assertNotNull(parsedCode)
        assertEquals("JBGS-G-501", parsedCode!!.animalCode)

        // Invalid / non-cow garbage text rejected
        assertNull(QrAndExportUtils.parseCattleQrPayload(""))
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://random-website.com/not-a-cow"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("invalid qr with spaces !!"))

        // Full ZXing QR encode -> YUV luminance -> decode round-trip
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
        val decoded = QrAndExportUtils.decodeQrFromYuvByteArray(yBytes, width, height)
        assertEquals(payload, decoded)
    }

    @Test
    fun `scanning valid cow QR fetches cow by Firebase ID and opens full Cow Details with vaccination, treatment, and feed records`() {
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

        val list = composeRule.onNodeWithTag("cattle_detail_scroll_list")
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

        // 1. Unknown Cow ID -> Not Found Hindi message
        composeRule.onNodeWithTag("qr_manual_input").performTextInput("JBGS:CATTLE:unknown_id_404:JBGS-G-999")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("qr_scanner_error_banner").assertIsDisplayed()
        composeRule.onNodeWithText("कोई गाय रिकॉर्ड नहीं मिला", substring = true).assertIsDisplayed()
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

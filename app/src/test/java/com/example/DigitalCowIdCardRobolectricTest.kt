package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.data.model.Cattle
import com.example.data.model.HealthRecord
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.screens.CattleManagementScreen
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DigitalCowIdCardRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin_phase2",
        email = "bhagwankibaat4@gmail.com",
        displayName = "गौशाला एडमिन",
        role = UserRole.ADMIN
    )

    private val approvedCow = Cattle(
        id = "secret_firestore_doc_id_8877",
        animalCode = "JBGS-G-357",
        name = "कामधेनु गौमाता",
        category = "COW",
        breed = "गिर (Gir)",
        sex = "FEMALE",
        ageText = "4 वर्ष",
        birthDate = "2022-03-18",
        colour = "सफेद-लाल",
        weightKg = 410.0,
        healthStatus = "HEALTHY",
        entryDate = "2024-01-10",
        shedNumber = "Shed-A1",
        identificationMarks = "माथे पर अर्धचंद्र तिलक",
        qrPayload = "JBGS:CATTLE:secret_firestore_doc_id_8877:JBGS-G-357",
        photoUri = "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0821187064.firebasestorage.app/o/cattle_photos%2Fcow.jpg?alt=media",
        vaccinationSummary = "FMD व HS टीका पूर्ण (आगामी: 2027-04-01)",
        notes = "PRIVATE_INTERNAL_DONOR_PHONE_9876543210",
        publicApproved = true,
        sponsorshipAvailable = true,
        monthlySupportInr = 2100L,
        createdBy = "admin_uid_private"
    )

    private val unapprovedCow = approvedCow.copy(
        id = "private_cow_doc_999",
        animalCode = "JBGS-G-999",
        name = "निजी गाय",
        publicApproved = false
    )

    @Test
    fun `Scenario 1 & 2 - QR URL uses real Firebase Hosting domain and never exposes Firebase Document ID`() {
        val generated = QrAndExportUtils.buildCattleQrPayload(approvedCow.id, approvedCow.animalCode)
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357", generated)
        assertFalse("QR URL must never expose Firebase document ID", generated.contains(approvedCow.id))

        // Legacy cattle object automatically resolves to new HTTPS URL for Google Lens display
        val resolved = QrAndExportUtils.resolvePublicCattleQrUrl(approvedCow)
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357", resolved)

        // Google Lens / third-party scanner URL variations parse accurately
        val parsedExact = QrAndExportUtils.parseCattleQrPayload(generated)
        assertNotNull(parsedExact)
        assertEquals("JBGS-G-357", parsedExact!!.animalCode)
        assertNull(parsedExact.cowDocId)

        val parsedTrailingSlash = QrAndExportUtils.parseCattleQrPayload("https://gen-lang-client-0821187064.web.app/cattle/jbgs-g-357/")
        assertNotNull(parsedTrailingSlash)
        assertEquals("JBGS-G-357", parsedTrailingSlash!!.animalCode)
    }

    @Test
    fun `Scenario 5 & 6 - Public card projection allows only approved cows, includes full profile fields, and strips all private fields`() {
        val sampleHealthRecords = listOf(
            HealthRecord(
                id = "hr_secret_1",
                cattleId = approvedCow.id,
                cattleName = approvedCow.name,
                animalCode = approvedCow.animalCode,
                recordType = "TREATMENT",
                title = "पैर की चोट का उपचार",
                medicine = "एंटीसेप्टिक ड्रेसिंग",
                vetName = "डॉ. शर्मा",
                costInr = 750.0,
                weightKg = 412.5,
                nextFollowUpDate = "2027-01-15",
                notes = "SECRET_VET_COST_NOTE_XYZ",
                recordedBy = "private_staff_uid_77"
            )
        )
        val sampleFeedLog = com.example.data.model.FeedAndOperationLog(
            id = "feed_1",
            logDate = "2026-11-27",
            greenFodderKg = 165.0,
            dryFodderBhusaKg = 210.0,
            concentrateKg = 42.0,
            waterLiters = 850.0,
            shedCleaned = true,
            notes = "INTERNAL_FEED_VENDOR_NOTE",
            recordedBy = "private_staff_uid_88"
        )

        val publicMap = QrAndExportUtils.buildPublicCattleCardMap(
            cattle = approvedCow,
            resolvedPhotoUrl = approvedCow.photoUri,
            lastHealthCheckup = "वार्षिक स्वास्थ्य जांच • 2026-11-20",
            healthRecords = sampleHealthRecords,
            latestFeedLog = sampleFeedLog,
            useServerTimestamp = false
        )
        assertNotNull("Approved cow must build public card map", publicMap)
        val map = publicMap!!

        // Verify all required public profile fields are present from real cattle data
        assertEquals("JBGS-G-357", map["animalCode"])
        assertEquals("कामधेनु गौमाता", map["name"])
        assertEquals("COW", map["category"])
        assertEquals("गिर (Gir)", map["breed"])
        assertEquals("FEMALE", map["sex"])
        assertEquals("4 वर्ष", map["ageText"])
        assertEquals("2022-03-18", map["birthDate"])
        assertEquals("सफेद-लाल", map["colour"])
        assertEquals(410.0, map["weightKg"])
        assertEquals("2024-01-10", map["entryDate"])
        assertEquals("Shed-A1", map["shedNumber"])
        assertEquals("HEALTHY", map["healthStatus"])
        assertEquals("FMD व HS टीका पूर्ण (आगामी: 2027-04-01)", map["vaccinationSummary"])
        assertEquals("वार्षिक स्वास्थ्य जांच • 2026-11-20", map["lastHealthCheckup"])
        assertEquals("माथे पर अर्धचंद्र तिलक", map["identificationMarks"])
        assertEquals(true, map["sponsorshipAvailable"])
        assertEquals(2100L, map["monthlySupportInr"])
        assertEquals(GosalaBrand.NAME_HI, map["goshalaNameHi"])
        assertEquals(GosalaBrand.ADDRESS_HI, map["goshalaAddressHi"])
        assertEquals(true, map["publicApproved"])
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357", map["qrUrl"])

        @Suppress("UNCHECKED_CAST")
        val healthHistory = map["healthHistory"] as? List<Map<String, Any>>
        assertNotNull("Expected sanitized healthHistory list", healthHistory)
        assertEquals(1, healthHistory!!.size)
        assertEquals("TREATMENT", healthHistory[0]["recordType"])
        assertEquals("पैर की चोट का उपचार", healthHistory[0]["title"])
        assertEquals("एंटीसेप्टिक ड्रेसिंग", healthHistory[0]["medicine"])
        assertEquals("डॉ. शर्मा", healthHistory[0]["vetName"])
        assertFalse("healthHistory must never include costInr", healthHistory[0].containsKey("costInr"))
        assertFalse("healthHistory must never include notes", healthHistory[0].containsKey("notes"))
        assertFalse("healthHistory must never include recordedBy", healthHistory[0].containsKey("recordedBy"))

        @Suppress("UNCHECKED_CAST")
        val feedSummary = map["feedSummary"] as? Map<String, Any>
        assertNotNull("Expected sanitized feedSummary map", feedSummary)
        assertEquals("2026-11-27", feedSummary!!["logDate"])
        assertEquals(165.0, feedSummary["greenFodderKg"])
        assertEquals(210.0, feedSummary["dryFodderBhusaKg"])
        assertEquals(true, feedSummary["shedCleaned"])
        assertFalse("feedSummary must never include internal notes", feedSummary.containsKey("notes"))

        // Verify strictly NO private/internal fields are ever leaked anywhere
        assertFalse(map.containsKey("notes"))
        assertFalse(map.containsKey("id"))
        assertFalse(map.containsKey("cattleId"))
        assertFalse(map.containsKey("createdBy"))
        val serialized = map.toString()
        assertFalse(serialized.contains("9876543210"))
        assertFalse(serialized.contains(approvedCow.id))
        assertFalse(serialized.contains("SECRET_VET_COST_NOTE_XYZ"))
        assertFalse(serialized.contains("INTERNAL_FEED_VENDOR_NOTE"))
        assertFalse(serialized.contains("private_staff_uid_77"))

        // Unapproved cow MUST NEVER be published to publicCattleCards
        val blockedMap = QrAndExportUtils.buildPublicCattleCardMap(
            cattle = unapprovedCow,
            useServerTimestamp = false
        )
        assertNull("Unapproved cow must NOT produce a public card map", blockedMap)
    }

    @Test
    fun `Scenario 7 - Invalid QR URLs are strictly rejected`() {
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://gen-lang-client-0821187064.web.app/cattle/"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://gen-lang-client-0821187064.web.app/cattle/a b c"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://gen-lang-client-0821187064.web.app/admin/panel"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("https://evil.example.com/phishing/login"))
        assertNull(QrAndExportUtils.parseCattleQrPayload("javascript:alert(1)"))
    }

    @Test
    fun `Scenario 10 - Digital Cow ID Card PDF renderer draws complete PVC card and WhatsApp share link`() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val samplePhoto = Bitmap.createBitmap(120, 90, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#8B5A2B"))
        }
        // Render the exact A4 PVC Digital Cow ID Card layout used by exportDigitalCowIdCardPdf onto a recording Canvas
        val pageBitmap = Bitmap.createBitmap(595, 842, Bitmap.Config.ARGB_8888)
        val drawnTexts = mutableListOf<String>()
        val canvas = object : android.graphics.Canvas(pageBitmap) {
            override fun drawText(text: String, x: Float, y: Float, paint: android.graphics.Paint) {
                drawnTexts.add(text)
                super.drawText(text, x, y, paint)
            }
        }
        QrAndExportUtils.drawDigitalCowIdCardOnCanvas(
            canvas = canvas,
            pageWidth = 595,
            pageHeight = 842,
            context = context,
            cattle = approvedCow,
            language = AppLanguage.HINDI,
            lastHealthCheckup = "FMD जांच • 2026-11-20",
            cowPhotoBitmap = samplePhoto
        )
        // Verify rendered PVC Digital Cow ID Card contains real cow info, Goshala header, and QR URL (and zero private notes)
        val drawnText = drawnTexts.joinToString(" | ")
        assertTrue("Expected Goshala title on PVC card", drawnText.contains(GosalaBrand.NAME_HI))
        assertTrue("Expected cow name on PVC card", drawnText.contains("कामधेनु गौमाता"))
        assertTrue("Expected Animal ID on PVC card", drawnText.contains("JBGS-G-357"))
        assertTrue("Expected Hosting QR URL on PVC card", drawnText.contains("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357"))
        assertFalse("Private internal notes must NEVER appear on PVC card", drawnText.contains("9876543210"))

        val waTextHi = QrAndExportUtils.buildDigitalCowWhatsAppText(approvedCow, AppLanguage.HINDI)
        assertTrue(waTextHi.contains("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357"))
        assertTrue(waTextHi.contains("कामधेनु गौमाता"))
        assertFalse(waTextHi.contains("9876543210"))
    }

    @Test
    fun `Scenario 3 & 4 - Scanning new HTTPS URL in Android QR scanner opens exact cow detail with PVC Digital ID Card and syncs public card`() {
        var syncedCowCode: String? = null
        val healthList = listOf(
            HealthRecord(
                id = "hr_1",
                cattleId = approvedCow.id,
                cattleName = approvedCow.name,
                animalCode = approvedCow.animalCode,
                recordType = "VET_VISIT",
                title = "नियमित स्वास्थ्य परीक्षण",
                nextFollowUpDate = "2027-01-15"
            )
        )

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattleState = DataUiState.Success(listOf(approvedCow, unapprovedCow)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    healthRecords = healthList,
                    onLookupCattleByQr = { raw ->
                        val parsed = QrAndExportUtils.parseCattleQrPayload(raw)
                        val match = listOf(approvedCow, unapprovedCow).firstOrNull {
                            it.animalCode.equals(parsed?.animalCode, ignoreCase = true)
                        }
                        Result.success(match)
                    },
                    onSyncPublicCattleCard = { cow, _ -> syncedCowCode = cow.animalCode }
                )
            }
        }

        // Scan the new Firebase Hosting HTTPS QR URL in the Android scanner
        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.onNodeWithTag("qr_manual_input")
            .performTextInput("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()

        // Exact cow detail screen opens & public card sync is invoked
        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("कामधेनु गौमाता", substring = true).onFirst().assertIsDisplayed()
        assertEquals("JBGS-G-357", syncedCowCode)

        // Scroll to PVC Digital Cow ID Card section and verify PDF, WhatsApp, and Web actions
        composeRule.onNodeWithTag("cattle_detail_scroll_list")
            .performScrollToNode(hasTestTag("cattle_detail_digital_id_card"))
        composeRule.onNodeWithTag("cattle_detail_digital_id_card").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_export_pdf_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_whatsapp_share_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_open_web_card_btn").assertExists()
    }

    @Test
    fun `Audit - Cattle QR encodes direct HTTPS Public Profile URL cleanly, decodes identically via ZXing, and preserves existing valid QR without regeneration`() {
        val validCow625 = approvedCow.copy(
            id = "doc_jbgs_625",
            animalCode = "JBGS-G-625",
            name = "नंदिनी",
            qrPayload = "https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625"
        )
        // 1. Existing valid QR URL is recognized as valid and preserved unchanged (no regeneration)
        assertTrue(QrAndExportUtils.isValidPublicCattleQrUrl(validCow625.qrPayload, validCow625.animalCode))
        val resolvedExisting = QrAndExportUtils.resolvePublicCattleQrUrl(validCow625)
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625", resolvedExisting)

        // 2. Malformed/empty/trailing-slash/legacy QR URLs are rejected by validator and cleanly normalized
        assertFalse(QrAndExportUtils.isValidPublicCattleQrUrl("https://gen-lang-client-0821187064.web.app/cattle/", "JBGS-G-625"))
        assertFalse(QrAndExportUtils.isValidPublicCattleQrUrl("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625/", "JBGS-G-625"))
        assertFalse(QrAndExportUtils.isValidPublicCattleQrUrl("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625?ref=redirect", "JBGS-G-625"))
        assertFalse(QrAndExportUtils.isValidPublicCattleQrUrl("JBGS:CATTLE:doc_jbgs_625:JBGS-G-625", "JBGS-G-625"))
        assertFalse(QrAndExportUtils.isValidPublicCattleQrUrl("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357", "JBGS-G-625"))

        // 3. Distinct cattle IDs always encode distinct direct HTTPS URLs with no redirects
        val url625 = QrAndExportUtils.resolvePublicCattleQrUrl(validCow625)
        val url357 = QrAndExportUtils.resolvePublicCattleQrUrl(approvedCow)
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625", url625)
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357", url357)
        assertFalse(url625 == url357)

        // 4. Generate actual QR bitmap and decode it back with ZXing to prove exact URL encoding
        val qrBitmap = QrAndExportUtils.generateQrBitmap(url625, 512)
        assertNotNull("Expected valid QR bitmap", qrBitmap)
        val width = qrBitmap!!.width
        val height = qrBitmap.height
        val pixels = IntArray(width * height)
        qrBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val decoded = MultiFormatReader().decode(
            BinaryBitmap(HybridBinarizer(RGBLuminanceSource(width, height, pixels)))
        ).text
        assertEquals("https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-625", decoded)
    }
}

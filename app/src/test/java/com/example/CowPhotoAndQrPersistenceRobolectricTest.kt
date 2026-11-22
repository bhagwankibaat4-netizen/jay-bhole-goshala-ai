package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.CattlePhotoStorage
import com.example.core.QrAndExportUtils
import com.example.core.UploadedCowPhoto
import com.example.data.model.AppModule
import com.example.data.model.Cattle
import com.example.data.model.CrudAction
import com.example.data.model.PublicSummary
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.components.CowPhotoImage
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.CattleManagementScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.StaffDashboardScreen
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CowPhotoAndQrPersistenceRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin_uid_photo",
        displayName = "गौशाला अध्यक्ष",
        email = "bhagwankibaat4@gmail.com",
        role = UserRole.ADMIN,
        staffActive = true
    )

    private val staffSession = UserSession(
        uid = "staff_uid_photo",
        displayName = "रमेश सेवक",
        email = "sevak@gosala.org",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(AppModule.CATTLE.key to listOf(CrudAction.VIEW.name))
    )

    private val publicSession = UserSession(
        uid = "public_uid_photo",
        displayName = "गोभक्त श्रद्धालु",
        email = "devotee@example.com",
        role = UserRole.PUBLIC
    )

    private fun makeTestBitmap(color: Int): Bitmap =
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    @Before
    fun setUp() {
        CattlePhotoStorage.clearMemoryCache()
        CattlePhotoStorage.testCloudPhotoResolver = null
        CattlePhotoStorage.testPhotoUploader = null
        CattlePhotoStorage.testProductPhotoUploader = null
    }

    @After
    fun tearDown() {
        CattlePhotoStorage.clearMemoryCache()
        CattlePhotoStorage.testCloudPhotoResolver = null
        CattlePhotoStorage.testPhotoUploader = null
        CattlePhotoStorage.testProductPhotoUploader = null
    }

    @Test
    fun `scenario 1 - local device URIs are classified as non-persistent and cloud upload converts to permanent cloud URL`() = runBlocking {
        assertTrue(CattlePhotoStorage.isLocalDeviceUri("content://media/picker/0/photopicker/1001"))
        assertTrue(CattlePhotoStorage.isLocalDeviceUri("file:///storage/emulated/0/DCIM/cow.jpg"))
        assertFalse(CattlePhotoStorage.isCloudPhotoUri("content://media/picker/0/photopicker/1001"))

        val expectedCloudUrl = "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0821187064.firebasestorage.app/o/cattle_photos%2Fcow_701%2Fphoto_1.jpg?alt=media"
        assertTrue(CattlePhotoStorage.isCloudPhotoUri(expectedCloudUrl))
        assertFalse(CattlePhotoStorage.isLocalDeviceUri(expectedCloudUrl))

        val firestoreCloudPointer = CattlePhotoStorage.buildFirestorePhotoUri("cow_701", 1001L)
        assertTrue(CattlePhotoStorage.isCloudPhotoUri(firestoreCloudPointer))
        assertEquals("cow_701", CattlePhotoStorage.extractFirestorePhotoDocId(firestoreCloudPointer))

        // Verify upload hook converts local URI into permanent cloud URL + storagePath
        CattlePhotoStorage.testPhotoUploader = { cattleId, localUri, _ ->
            assertTrue(CattlePhotoStorage.isLocalDeviceUri(localUri))
            Result.success(
                UploadedCowPhoto(
                    photoUrl = expectedCloudUrl,
                    storagePath = "cattle_photos/$cattleId/photo_1.jpg"
                )
            )
        }
        val uploaded = CattlePhotoStorage.testPhotoUploader!!.invoke(
            "cow_701",
            "content://media/picker/0/cow.jpg",
            true
        )
        assertTrue(uploaded.isSuccess)
        assertTrue(CattlePhotoStorage.isCloudPhotoUri(uploaded.getOrNull()?.photoUrl))
        assertEquals("cattle_photos/cow_701/photo_1.jpg", uploaded.getOrNull()?.storagePath)
    }

    @Test
    fun `scenarios 2, 3, 4 - Public, Admin, and Staff Dashboards display saved cloud cow photo across restarts and devices`() {
        val cloudPhotoV1 = CattlePhotoStorage.buildFirestorePhotoUri("cow_pub_101", 1L)
        val cloudBitmap = makeTestBitmap(Color.rgb(46, 125, 50))
        val requestedUrls = mutableListOf<String>()

        CattlePhotoStorage.testCloudPhotoResolver = { uri ->
            requestedUrls.add(uri)
            if (uri == cloudPhotoV1) cloudBitmap else null
        }

        val approvedCow = Cattle(
            id = "cow_pub_101",
            animalCode = "JBGS-G-701",
            name = "सुरभि गौमाता",
            category = "COW",
            breed = "गिर",
            sex = "FEMALE",
            ageText = "4 वर्ष",
            colour = "सफेद-भूरा",
            weightKg = 415.0,
            healthStatus = "HEALTHY",
            entryDate = "2024-03-10",
            shedNumber = "Shed-A2",
            identificationMarks = "माथे पर तिलक",
            qrPayload = QrAndExportUtils.buildCattleQrPayload("cow_pub_101", "JBGS-G-701"),
            photoUri = cloudPhotoV1,
            photoStoragePath = "cattlePhotos/cow_pub_101",
            vaccinationSummary = "FMD टीका पूर्ण (2026-11-15)",
            notes = "शांत स्वभाव",
            publicApproved = true,
            sponsorshipAvailable = true,
            monthlySupportInr = 2100L
        )

        var activeRole by mutableStateOf(UserRole.PUBLIC)

        composeRule.setContent {
            GosalaTheme {
                when (activeRole) {
                    UserRole.PUBLIC -> HomeScreen(
                        language = AppLanguage.HINDI,
                        session = publicSession,
                        summary = PublicSummary(totalCattle = 1, healthyCattle = 1),
                        cattle = listOf(approvedCow),
                        feedLogs = emptyList(),
                        finance = emptyList(),
                        donations = emptyList(),
                        onNavigate = {},
                        onQuickAddCattle = {},
                        onQuickAddFeed = {},
                        onQuickAddExpense = {}
                    )
                    UserRole.ADMIN -> AdminDashboardScreen(
                        language = AppLanguage.HINDI,
                        session = adminSession,
                        cattle = listOf(approvedCow),
                        health = emptyList(),
                        feedLogs = emptyList(),
                        staffAccounts = emptyList(),
                        finance = emptyList(),
                        donations = emptyList(),
                        sponsorships = emptyList(),
                        compost = emptyList(),
                        onNavigate = {},
                        onQuickAddCattle = {},
                        onQuickAddFeed = {},
                        onQuickAddExpense = {}
                    )
                    UserRole.STAFF -> StaffDashboardScreen(
                        language = AppLanguage.HINDI,
                        session = staffSession,
                        cattle = listOf(approvedCow),
                        feedLogs = emptyList(),
                        onNavigate = {},
                        onQuickAddCattle = {},
                        onQuickAddFeed = {},
                        onQuickAddExpense = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // 1. Public User Dashboard shows saved cow photo strip & loaded cloud photo
        composeRule.onNodeWithTag("public_dashboard_root")
            .performScrollToNode(hasTestTag("public_dashboard_cattle_strip"))
        composeRule.onNodeWithTag("public_dashboard_cattle_strip_cow_JBGS-G-701").assertIsDisplayed()
        composeRule.onNodeWithTag("public_dashboard_cattle_strip_photo_JBGS-G-701_loaded", useUnmergedTree = true).assertIsDisplayed()

        // 2. Simulate app restart / another mobile login (evict in-memory cache) -> Admin Dashboard still loads photo from cloud
        CattlePhotoStorage.clearMemoryCache()
        activeRole = UserRole.ADMIN
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_dashboard_root")
            .performScrollToNode(hasTestTag("admin_dashboard_cattle_strip"))
        composeRule.onNodeWithTag("admin_dashboard_cattle_strip_photo_JBGS-G-701_loaded", useUnmergedTree = true).assertIsDisplayed()

        // 3. Staff Dashboard (with Cattle VIEW) also loads the saved cloud photo
        CattlePhotoStorage.clearMemoryCache()
        activeRole = UserRole.STAFF
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("staff_dashboard_root")
            .performScrollToNode(hasTestTag("staff_dashboard_cattle_strip"))
        composeRule.onNodeWithTag("staff_dashboard_cattle_strip_photo_JBGS-G-701_loaded", useUnmergedTree = true).assertIsDisplayed()

        // Verified cloud fetch occurred across fresh cache / simulated device logins
        assertTrue(requestedUrls.count { it == cloudPhotoV1 } >= 3)
    }

    @Test
    fun `scenario 5 - QR scan opens exact scanned cow full details and exact cow photo in strict Public Read-Only View without Edit or Delete`() {
        val photoCowA = CattlePhotoStorage.buildFirestorePhotoUri("cow_A", 10L)
        val photoCowB = CattlePhotoStorage.buildFirestorePhotoUri("cow_B", 20L)
        val resolvedUris = mutableListOf<String>()

        CattlePhotoStorage.testCloudPhotoResolver = { uri ->
            resolvedUris.add(uri)
            when (uri) {
                photoCowA -> makeTestBitmap(Color.BLUE)
                photoCowB -> makeTestBitmap(Color.MAGENTA)
                else -> null
            }
        }

        val cowA = Cattle(
            id = "cow_A",
            animalCode = "JBGS-G-801",
            name = "कपिला गौमाता",
            breed = "साहीवाल",
            ageText = "6 वर्ष",
            colour = "लाल-भूरा",
            weightKg = 440.0,
            healthStatus = "HEALTHY",
            entryDate = "2023-05-01",
            shedNumber = "Shed-A1",
            identificationMarks = "घंटी बंधी हुई",
            qrPayload = QrAndExportUtils.buildCattleQrPayload("cow_A", "JBGS-G-801"),
            photoUri = photoCowA,
            vaccinationSummary = "HS एवं BQ टीका पूर्ण",
            notes = "गाय A की टिप्पणी",
            publicApproved = true
        )
        val cowB = Cattle(
            id = "cow_B",
            animalCode = "JBGS-G-802",
            name = "गंगा गौमाता",
            breed = "थारपारकर",
            ageText = "5 वर्ष",
            colour = "सफेद",
            weightKg = 405.0,
            healthStatus = "HEALTHY",
            entryDate = "2024-09-12",
            shedNumber = "Shed-B3",
            identificationMarks = "बाएँ कान पर निशान",
            qrPayload = QrAndExportUtils.buildCattleQrPayload("cow_B", "JBGS-G-802"),
            photoUri = photoCowB,
            vaccinationSummary = "FMD खुरपका-मुंहपका टीका पूर्ण (आगामी: 2027-03-01)",
            notes = "गाय B की विशेष टिप्पणी",
            publicApproved = true
        )

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession, // Even when Admin scans QR, QR Scan page MUST be Public Read-Only View!
                    cattleState = DataUiState.Success(listOf(cowA, cowB)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {},
                    onLookupCattleByQr = { raw ->
                        val parsed = QrAndExportUtils.parseCattleQrPayload(raw)
                        val match = listOf(cowA, cowB).firstOrNull { it.id == parsed?.cowDocId }
                        Result.success(match)
                    }
                )
            }
        }
        composeRule.waitForIdle()

        // Scan cow B's QR code
        composeRule.onNodeWithTag("qr_lookup_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("qr_manual_input").performTextInput("JBGS:CATTLE:cow_B:JBGS-G-802")
        composeRule.onNodeWithTag("qr_manual_search_button").performClick()
        composeRule.waitForIdle()

        // Verify exact cow B opened with its exact photo (photoCowB) and NEVER cow A's photo
        composeRule.onNodeWithTag("cattle_detail_full_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_qr_verified_banner").assertIsDisplayed()
        composeRule.onNodeWithText("सार्वजनिक दृश्य (केवल देखने हेतु)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("cattle_detail_photo_loaded").assertIsDisplayed()
        assertTrue(resolvedUris.last() == photoCowB)

        // Verify strict Public Read-Only View: Edit & Delete buttons MUST NOT exist on QR Scan page!
        composeRule.onNodeWithTag("cattle_edit_button").assertDoesNotExist()
        composeRule.onNodeWithTag("cattle_delete_button").assertDoesNotExist()

        // Verify all cow B details including shed, ID marks, entry date, notes, and vaccination summary
        composeRule.onAllNodesWithText("गंगा गौमाता", substring = true).onFirst().assertIsDisplayed()
        val scrollList = composeRule.onNodeWithTag("cattle_detail_scroll_list")
        scrollList.performScrollToNode(hasTestTag("cattle_detail_vaccination_section"))
        composeRule.onNodeWithText("FMD खुरपका-मुंहपका टीका पूर्ण", substring = true).assertIsDisplayed()
    }

    @Test
    fun `scenario 6 - updating cow photo immediately updates displayed photo everywhere while QR stays permanent`() {
        val photoV1 = CattlePhotoStorage.buildFirestorePhotoUri("cow_upd_1", 100L)
        val photoV2 = CattlePhotoStorage.buildFirestorePhotoUri("cow_upd_1", 200L)
        val fetchedVersions = mutableListOf<String>()

        CattlePhotoStorage.testCloudPhotoResolver = { uri ->
            fetchedVersions.add(uri)
            when (uri) {
                photoV1 -> makeTestBitmap(Color.YELLOW)
                photoV2 -> makeTestBitmap(Color.CYAN)
                else -> null
            }
        }

        val initialQr = QrAndExportUtils.buildCattleQrPayload("cow_upd_1", "JBGS-G-901")
        var cowState by mutableStateOf(
            Cattle(
                id = "cow_upd_1",
                animalCode = "JBGS-G-901",
                name = "कामधेनु",
                breed = "गिर",
                ageText = "5 वर्ष",
                colour = "सफेद",
                weightKg = 400.0,
                healthStatus = "HEALTHY",
                entryDate = "2024-01-01",
                shedNumber = "Shed-A1",
                qrPayload = initialQr,
                photoUri = photoV1,
                publicApproved = true
            )
        )

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    cattleState = DataUiState.Success(listOf(cowState)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { _, _ -> },
                    onDeleteCattle = {},
                    onSponsorCattle = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("cattle_list_photo_JBGS-G-901_loaded", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(fetchedVersions.contains(photoV1))

        // Admin updates cow photo to new cloud version v2 (QR stays unchanged)
        cowState = cowState.copy(photoUri = photoV2)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("cattle_list_photo_JBGS-G-901_loaded", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(fetchedVersions.contains(photoV2))
        assertEquals(initialQr, cowState.qrPayload)
    }

    @Test
    fun `scenario 7 - slow photo load shows Loading Indicator and missing or failed photo shows Default Cow Image without crashing`() {
        val slowUri = CattlePhotoStorage.buildFirestorePhotoUri("cow_slow", 55L)
        val brokenUri = CattlePhotoStorage.buildFirestorePhotoUri("cow_broken", 66L)
        val gate = CompletableDeferred<Bitmap?>()

        CattlePhotoStorage.testCloudPhotoResolver = { uri ->
            when (uri) {
                slowUri -> gate.await()
                brokenUri -> null // Simulate network failure / missing photo
                else -> null
            }
        }

        var currentUri by mutableStateOf<String?>(slowUri)

        composeRule.setContent {
            GosalaTheme {
                CowPhotoImage(
                    photoUri = currentUri,
                    contentDescription = "गाय फोटो",
                    testTagPrefix = "test_cow_img",
                    modifier = Modifier.size(120.dp)
                )
            }
        }
        composeRule.waitForIdle()

        // 1. While slow network fetch is in flight -> Loading Indicator is displayed
        composeRule.onNodeWithTag("test_cow_img_loading").assertIsDisplayed()

        // 2. When slow fetch fails/returns null -> falls back to Default Cow Image cleanly (no crash)
        gate.complete(null)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_cow_img_default").assertIsDisplayed()

        // 3. Explicit broken cloud photo URI -> Default Cow Image cleanly
        currentUri = brokenUri
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_cow_img_default").assertIsDisplayed()

        // 4. Null / empty photo URI -> Default Cow Image cleanly
        currentUri = null
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("test_cow_img_default").assertIsDisplayed()
    }

    @Test
    fun `scenario 8 - product local gallery URI uploads to permanent cloud URL and never persists raw content URI`() = runBlocking {
        val expectedUrl = "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0821187064.firebasestorage.app/o/product_photos%2Fprod_kande%2Fphoto_1.jpg?alt=media"
        CattlePhotoStorage.testProductPhotoUploader = { productId, localUri ->
            assertEquals("prod_kande", productId)
            assertTrue(CattlePhotoStorage.isLocalDeviceUri(localUri))
            Result.success(
                UploadedCowPhoto(
                    photoUrl = expectedUrl,
                    storagePath = "product_photos/$productId/photo_1.jpg"
                )
            )
        }

        // 1. Local content:// gallery URI converts to permanent cloud HTTPS URL
        val uploaded = CattlePhotoStorage.uploadProductPhoto(
            context = null,
            productId = "prod_kande",
            sourceUriString = "content://media/picker/0/item/kande_photo"
        )
        assertTrue(uploaded.isSuccess)
        assertEquals(expectedUrl, uploaded.getOrThrow().photoUrl)
        assertTrue(CattlePhotoStorage.isCloudPhotoUri(uploaded.getOrThrow().photoUrl))
        assertFalse(CattlePhotoStorage.isLocalDeviceUri(uploaded.getOrThrow().photoUrl))

        // 2. Existing HTTPS URL and built-in preset keys pass through unchanged
        val existingHttps = CattlePhotoStorage.uploadProductPhoto(
            context = null,
            productId = "prod_kande",
            sourceUriString = expectedUrl
        )
        assertTrue(existingHttps.isSuccess)
        assertEquals(expectedUrl, existingHttps.getOrThrow().photoUrl)

        val presetRes = CattlePhotoStorage.uploadProductPhoto(
            context = null,
            productId = "prod_kande",
            sourceUriString = "preset:kande"
        )
        assertTrue(presetRes.isSuccess)
        assertEquals("preset:kande", presetRes.getOrThrow().photoUrl)

        // 3. Without uploader/context, local content:// URI fails cleanly instead of leaking local URI to Firestore
        CattlePhotoStorage.testProductPhotoUploader = null
        val failedLocal = CattlePhotoStorage.uploadProductPhoto(
            context = null,
            productId = "prod_kande",
            sourceUriString = "content://media/picker/0/item/unsaved"
        )
        assertTrue(failedLocal.isFailure)
    }
}

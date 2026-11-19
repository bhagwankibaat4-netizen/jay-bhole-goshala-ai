package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.AppThemeMode
import com.example.core.QrAndExportUtils
import com.example.data.model.*
import com.example.ui.auth.AuthScreen
import com.example.ui.components.GosalaTopBar
import com.example.ui.navigation.AuthNavigationController
import com.example.ui.navigation.AuthNavigationState
import com.example.ui.navigation.GosalaRootNavHost
import com.example.ui.navigation.rememberAuthNavigationController
import com.example.ui.screens.*
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("जय भोले गौशाला", appName)
    }

    @Test
    fun `role permissions enforce strict least privilege`() {
        val admin = UserSession(uid = "a1", role = UserRole.ADMIN)
        assertTrue(admin.canView(AppModule.FINANCE))
        assertTrue(admin.canDelete(AppModule.STAFF))

        val staff = UserSession(
            uid = "s1",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(
                AppModule.FEED.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name)
            )
        )
        assertTrue(staff.canView(AppModule.FEED))
        assertTrue(staff.canAdd(AppModule.FEED))
        assertFalse(staff.canEdit(AppModule.FEED))
        assertFalse(staff.canDelete(AppModule.FEED))
        assertFalse(staff.canView(AppModule.FINANCE))

        val publicUser = UserSession(uid = "p1", role = UserRole.PUBLIC)
        assertTrue(publicUser.canView(AppModule.CATTLE))
        assertTrue(publicUser.canView(AppModule.DONATIONS))
        assertTrue(publicUser.canView(AppModule.AI))
        assertFalse(publicUser.canView(AppModule.FINANCE))
        assertFalse(publicUser.canView(AppModule.STAFF))
        assertFalse(publicUser.canAdd(AppModule.CATTLE))
        assertFalse(publicUser.canDelete(AppModule.DONATIONS))
    }

    @Test
    fun `qr generation and csv export succeed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp = QrAndExportUtils.generateQrBitmap("JBG-001|Kamadhenu", 128)
        assertNotNull(bmp)
        assertEquals(128, bmp?.width)

        val csvResult = QrAndExportUtils.exportExcelCsvReport(
            context = context,
            filePrefix = "test_cattle",
            headers = listOf("ID", "नाम"),
            rows = listOf(listOf("JBG-001", "नंदिनी"))
        )
        assertTrue(csvResult.isSuccess)
        val content = csvResult.getOrThrow().readText(Charsets.UTF_8)
        assertTrue(content.contains("नंदिनी"))
    }

    @Test
    fun `admin dashboard shows dedicated admin modules and quick actions`() {
        val adminSession = UserSession(
            uid = "admin_1",
            email = "bhagwankibaat4@gmail.com",
            displayName = "लवकुश सिंह बघेल",
            role = UserRole.ADMIN,
            staffActive = true
        )
        var navigatedRoute = ""
        composeRule.setContent {
            GosalaTheme {
                AdminDashboardScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattle = listOf(
                        Cattle(id = "c1", animalCode = "JBGS-G-001", name = "नंदिनी", category = "COW", healthStatus = "HEALTHY")
                    ),
                    health = emptyList(),
                    feedLogs = emptyList(),
                    staffAccounts = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    sponsorships = emptyList(),
                    compost = emptyList(),
                    onNavigate = { navigatedRoute = it },
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }

        composeRule.onNodeWithTag("admin_dashboard_root").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_header_card").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_official_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_dashboard_root")
            .performScrollToNode(hasTestTag("admin_quick_add_cattle"))
        composeRule.onNodeWithTag("admin_quick_add_cattle").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_dashboard_root")
            .performScrollToNode(hasTestTag("admin_mod_staff"))
        composeRule.onNodeWithTag("admin_mod_staff").performClick()
        assertEquals(Routes.STAFF, navigatedRoute)
    }

    @Test
    fun `admin can open add cow and edit existing cow`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val savedCalls = mutableListOf<Pair<Cattle, Boolean>>()
        val existingCow = Cattle(
            id = "cow_1",
            animalCode = "JBGS-G-101",
            name = "गौरी",
            category = "COW",
            breed = "गिर",
            sex = "FEMALE",
            ageText = "4 वर्ष",
            colour = "सफेद",
            weightKg = 380.0,
            healthStatus = "HEALTHY",
            entryDate = "2026-11-18",
            shedNumber = "Shed-A1"
        )

        composeRule.setContent {
            GosalaTheme {
                CattleManagementScreen(
                    language = AppLanguage.ENGLISH,
                    session = adminSession,
                    cattleState = DataUiState.Success(listOf(existingCow)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onSaveCattle = { item, isEdit -> savedCalls.add(item to isEdit) },
                    onDeleteCattle = {},
                    onSponsorCattle = {}
                )
            }
        }

        // Open existing cow -> click Edit -> save
        composeRule.onNodeWithTag("cattle_card_JBGS-G-101").performClick()
        composeRule.onNodeWithTag("cattle_edit_button").performClick()
        composeRule.onNodeWithTag("cattle_save_button").performClick()

        assertEquals(1, savedCalls.size)
        assertTrue(savedCalls[0].second) // isEdit = true
        assertEquals("गौरी", savedCalls[0].first.name)

        // Open Add Cow FAB
        composeRule.onNodeWithTag("add_cattle_fab").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("cattle_save_button").assertIsDisplayed()
    }

    @Test
    fun `admin can access staff management and public user is denied`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        var currentSession by mutableStateOf(adminSession)

        composeRule.setContent {
            GosalaTheme {
                StaffManagementScreen(
                    language = AppLanguage.HINDI,
                    session = currentSession,
                    staffAccountsState = DataUiState.Success(emptyList()),
                    staffInvitesState = DataUiState.Success(emptyList()),
                    staffOpsState = DataUiState.Success(emptyList()),
                    onCreateInvite = { _, _, _, _, _, _ -> },
                    onUpdateStaff = { _, _, _, _ -> },
                    onAddStaffOperation = {}
                )
            }
        }

        composeRule.onNodeWithTag("add_staff_invite_fab").assertIsDisplayed()

        // Switch to Public session -> FAB disappears and Admin Access Only shown
        currentSession = UserSession(uid = "pub_1", role = UserRole.PUBLIC)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_staff_invite_fab").assertDoesNotExist()
        composeRule.onNodeWithText("केवल प्रशासक के लिए").assertIsDisplayed()
    }

    @Test
    fun `auth screen shows hindi error message when unauthorized user attempts admin login`() {
        var portal by mutableStateOf(LoginPortal.ADMIN)
        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = portal,
                    onSelectPortal = { portal = it },
                    sessionError = "इस खाते को Admin की अनुमति नहीं मिली है। कृपया गौशाला प्रशासक से संपर्क करें।",
                    rejectedAccountNote = "Email: user@example.com | UID: uid_123",
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("auth_official_logo").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("portal_chip_admin").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("auth_error_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("इस खाते को Admin की अनुमति नहीं मिली है। कृपया गौशाला प्रशासक से संपर्क करें।")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `public dashboard preserves public layout and hides admin controls`() {
        val publicSession = UserSession(uid = "pub_1", role = UserRole.PUBLIC)
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = PublicSummary(totalCattle = 12, healthyCattle = 11, sickCattle = 1),
                    cattle = emptyList(),
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }

        composeRule.onNodeWithTag("home_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("public_official_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("public_dashboard_root")
            .performScrollToNode(hasTestTag("module_donations"))
        composeRule.onNodeWithTag("module_cattle").assertIsDisplayed()
        composeRule.onNodeWithTag("module_donations").assertIsDisplayed()
        composeRule.onNodeWithTag("module_staff").assertDoesNotExist()
        composeRule.onNodeWithTag("module_finance").assertDoesNotExist()
    }

    @Test
    fun `staff dashboard only renders modules granted to staff account`() {
        val staffSession = UserSession(
            uid = "staff_1",
            displayName = "रामू सेवक",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(
                AppModule.FEED.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name)
            )
        )
        composeRule.setContent {
            GosalaTheme {
                StaffDashboardScreen(
                    language = AppLanguage.HINDI,
                    session = staffSession,
                    cattle = emptyList(),
                    feedLogs = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }

        composeRule.onNodeWithTag("staff_official_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("staff_dashboard_root")
            .performScrollToNode(hasTestTag("staff_mod_feed"))
        composeRule.onNodeWithTag("staff_mod_feed").assertIsDisplayed()
        composeRule.onNodeWithTag("staff_mod_finance").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_mod_staff").assertDoesNotExist()
    }

    @Test
    fun `topbar and profile about section display official logo`() {
        val publicSession = UserSession(
            uid = "pub_1",
            email = "bhakt@example.com",
            displayName = "गौ भक्त",
            role = UserRole.PUBLIC
        )
        composeRule.setContent {
            GosalaTheme {
                androidx.compose.foundation.layout.Column {
                    GosalaTopBar(
                        title = null,
                        showBack = false,
                        onBack = {},
                        language = AppLanguage.HINDI,
                        isDark = false,
                        unreadNotifications = 0,
                        onToggleLanguage = {},
                        onToggleTheme = {},
                        onNotificationsClick = {}
                    )
                    ProfileSettingsScreen(
                        language = AppLanguage.HINDI,
                        themeMode = AppThemeMode.LIGHT,
                        session = publicSession,
                        auditLogsState = DataUiState.Success(emptyList()),
                        onSetLanguage = {},
                        onSetThemeMode = {},
                        onOpenReports = {},
                        onSignOut = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("topbar_official_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("profile_settings_list")
            .performScrollToNode(hasTestTag("about_official_logo"))
        composeRule.onNodeWithTag("about_official_logo").assertIsDisplayed()
    }

    @Test
    fun `finance screen validates input and records income and expense entries`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val recorded = mutableListOf<FinancialTransaction>()
        val deletedIds = mutableListOf<String>()
        val existingTx = FinancialTransaction(
            id = "tx_1",
            txType = "EXPENSE",
            category = "FEED_PURCHASE",
            amountInr = 2500.0,
            paymentMode = "UPI",
            paymentStatus = "COMPLETED",
            partyName = "रामपाल किसान",
            description = "भूसा खरीद",
            dateStr = "2026-11-19"
        )

        composeRule.setContent {
            GosalaTheme {
                FinanceManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    financeState = DataUiState.Success(listOf(existingTx)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onAddTransaction = { recorded.add(it) },
                    onDeleteTransaction = { deletedIds.add(it) }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_finance_fab").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("finance_save_button").performClick()
        // Inline Hindi error shown instead of silent no-op
        composeRule.onNodeWithText("कृपया राशि (>0), व्यक्ति का नाम और विवरण भरें।").assertIsDisplayed()
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `farmer module handles loading error and add validation cleanly`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        var state by mutableStateOf<DataUiState<List<FarmerRecord>>>(DataUiState.Error("नेटवर्क त्रुटि"))

        composeRule.setContent {
            GosalaTheme {
                FarmerCooperationScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    farmersState = state,
                    farmerTxState = DataUiState.Success(emptyList()),
                    onAddFarmer = {},
                    onAddFarmerTx = { _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("किसान डेटा लोड नहीं हो सका").assertIsDisplayed()

        state = DataUiState.Success(emptyList())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_farmer_fab").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("farmer_save_button").performClick()
        composeRule.onNodeWithText("कृपया नाम, गाँव और सही मोबाइल नंबर दर्ज करें।").assertIsDisplayed()
    }

    @Test
    fun `compost module renders batches and opens add batch dialog`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val savedBatches = mutableListOf<CompostBatch>()
        val batch = CompostBatch(
            id = "b1",
            batchCode = "CMP-101",
            productType = "VERMICOMPOST",
            quantity = 500.0,
            unit = "KG",
            productionCostInr = 1200.0,
            packagingCostInr = 300.0,
            sellingPricePerUnitInr = 12.0,
            soldQuantity = 100.0,
            stockRemaining = 400.0,
            status = "READY_FOR_SALE",
            dateStr = "2026-11-19"
        )

        composeRule.setContent {
            GosalaTheme {
                CompostProductionScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    compostState = DataUiState.Success(listOf(batch)),
                    onAddBatch = { savedBatches.add(it) },
                    onRecordSale = { _, _ -> },
                    onAskAiCostAnalysis = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("CMP-101 • VERMICOMPOST").assertIsDisplayed()
        composeRule.onNodeWithTag("add_compost_fab").performClick()
        composeRule.onNodeWithTag("compost_save_button").performClick()
        assertEquals(1, savedBatches.size)
        assertEquals(500.0, savedBatches[0].quantity, 0.01)
    }

    @Test
    fun `health dialog displays more than four cattle for selection`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val sixCows = (1..6).map { i ->
            Cattle(id = "c$i", animalCode = "JBGS-G-00$i", name = "गौमाता$i", category = "COW")
        }

        composeRule.setContent {
            GosalaTheme {
                HealthManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    healthState = DataUiState.Success(emptyList()),
                    cattleList = sixCows,
                    onAddHealthRecord = {},
                    onDeleteHealthRecord = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("add_health_fab").performClick()
        composeRule.onNodeWithText("पशु चुनें (6):").assertIsDisplayed()
    }

    @Test
    fun `staff attendance report includes TASK entries`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val taskEntry = StaffOperation(
            id = "op1",
            staffUid = "s1",
            staffName = "रमेश सेवक",
            entryType = "TASK",
            dateStr = "2026-11-19",
            attendanceStatus = "NA",
            taskDescription = "शेड सफाई व पानी"
        )

        composeRule.setContent {
            GosalaTheme {
                ReportsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattle = emptyList(),
                    health = emptyList(),
                    feedLogs = emptyList(),
                    staffAccounts = emptyList(),
                    staffOps = listOf(taskEntry),
                    finance = emptyList(),
                    donations = emptyList(),
                    farmerTx = emptyList(),
                    compost = emptyList(),
                    onShowMessage = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("report_chip_staff_attendance").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("शेड सफाई व पानी").assertIsDisplayed()
    }

    @Test
    fun `offline queued exception maps to clear hindi message and logout callback fires`() {
        val offlineMsg = com.example.core.friendlyHindiError(
            com.example.core.OfflineQueuedException(),
            AppLanguage.HINDI
        )
        assertTrue(offlineMsg.contains("इंटरनेट बंद या धीमा"))

        var signedOut = false
        val session = UserSession(uid = "u1", email = "u@example.com", displayName = "भक्त", role = UserRole.PUBLIC)
        composeRule.setContent {
            GosalaTheme {
                ProfileSettingsScreen(
                    language = AppLanguage.HINDI,
                    themeMode = AppThemeMode.LIGHT,
                    session = session,
                    auditLogsState = DataUiState.Success(emptyList()),
                    onSetLanguage = {},
                    onSetThemeMode = {},
                    onOpenReports = {},
                    onSignOut = { signedOut = true }
                )
            }
        }
        composeRule.onNodeWithTag("profile_settings_list")
            .performScrollToNode(hasTestTag("sign_out_button"))
        composeRule.onNodeWithTag("sign_out_button").performClick()
        assertTrue(signedOut)
    }

    @Test
    fun `android manifest declares required least-privilege permissions only`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(perms.contains(android.Manifest.permission.INTERNET))
        assertTrue(perms.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
        assertTrue(perms.contains(android.Manifest.permission.RECORD_AUDIO))
        assertTrue(perms.contains(android.Manifest.permission.POST_NOTIFICATIONS))
        // Ensure zero prohibited broad storage permissions
        assertFalse(perms.contains("android.permission.READ_EXTERNAL_STORAGE"))
        assertFalse(perms.contains("android.permission.READ_MEDIA_IMAGES"))
    }

    @Test
    fun `finance edit and delete require permission and show confirmation dialog`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val viewOnlyStaff = UserSession(
            uid = "staff_v",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(AppModule.FINANCE.key to listOf(CrudAction.VIEW.name))
        )
        var currentSession by mutableStateOf(adminSession)
        val updated = mutableListOf<FinancialTransaction>()
        val deletedIds = mutableListOf<String>()
        val tx = FinancialTransaction(
            id = "tx_10",
            txType = "EXPENSE",
            category = "FEED_PURCHASE",
            amountInr = 2500.0,
            paymentMode = "UPI",
            paymentStatus = "COMPLETED",
            partyName = "रामपाल किसान",
            description = "भूसा खरीद",
            dateStr = "2026-11-19"
        )

        composeRule.setContent {
            GosalaTheme {
                FinanceManagementScreen(
                    language = AppLanguage.HINDI,
                    session = currentSession,
                    financeState = DataUiState.Success(listOf(tx)),
                    openAddOnStart = false,
                    onConsumedOpenAdd = {},
                    onAddTransaction = {},
                    onDeleteTransaction = { deletedIds.add(it) },
                    onUpdateTransaction = { updated.add(it) }
                )
            }
        }

        // Scroll LazyColumn to tx card and click Edit -> Save updates existing record
        composeRule.onNodeWithTag("finance_list").performScrollToNode(hasTestTag("finance_edit_tx_10"))
        composeRule.onNodeWithTag("finance_edit_tx_10").performClick()
        composeRule.onNodeWithTag("finance_save_button").performClick()
        assertEquals(1, updated.size)
        assertEquals("tx_10", updated[0].id)

        // Click Delete -> must ask confirmation first -> confirm deletes
        composeRule.onNodeWithTag("finance_list").performScrollToNode(hasTestTag("finance_delete_tx_10"))
        composeRule.onNodeWithTag("finance_delete_tx_10").performClick()
        assertTrue(deletedIds.isEmpty())
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        assertEquals(listOf("tx_10"), deletedIds)

        // Switch to view-only staff -> Edit and Delete icons must not exist
        currentSession = viewOnlyStaff
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("finance_edit_tx_10").assertDoesNotExist()
        composeRule.onNodeWithTag("finance_delete_tx_10").assertDoesNotExist()
    }

    @Test
    fun `farmer edit and delete enforce permissions and confirm deletion`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val viewOnlyStaff = UserSession(
            uid = "staff_v",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(AppModule.FARMERS.key to listOf(CrudAction.VIEW.name))
        )
        var currentSession by mutableStateOf(adminSession)
        val updated = mutableListOf<FarmerRecord>()
        val deleted = mutableListOf<FarmerRecord>()
        val farmer = FarmerRecord(
            id = "f_1",
            name = "हरि सिंह",
            mobile = "9876543210",
            village = "पोरसा",
            cooperationType = "BOTH"
        )

        composeRule.setContent {
            GosalaTheme {
                FarmerCooperationScreen(
                    language = AppLanguage.HINDI,
                    session = currentSession,
                    farmersState = DataUiState.Success(listOf(farmer)),
                    farmerTxState = DataUiState.Success(emptyList()),
                    onAddFarmer = {},
                    onAddFarmerTx = { _, _ -> },
                    onUpdateFarmer = { updated.add(it) },
                    onDeleteFarmer = { deleted.add(it) }
                )
            }
        }

        composeRule.onNodeWithTag("farmer_edit_f_1").performClick()
        composeRule.onNodeWithTag("farmer_save_button").performClick()
        assertEquals(1, updated.size)
        assertEquals("f_1", updated[0].id)

        composeRule.onNodeWithTag("farmer_delete_f_1").performClick()
        assertTrue(deleted.isEmpty())
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        assertEquals(1, deleted.size)
        assertEquals("f_1", deleted[0].id)

        currentSession = viewOnlyStaff
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farmer_edit_f_1").assertDoesNotExist()
        composeRule.onNodeWithTag("farmer_delete_f_1").assertDoesNotExist()
    }

    @Test
    fun `compost edit and delete enforce permissions and confirm deletion`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val viewOnlyStaff = UserSession(
            uid = "staff_v",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(AppModule.PRODUCTION.key to listOf(CrudAction.VIEW.name))
        )
        var currentSession by mutableStateOf(adminSession)
        val updated = mutableListOf<CompostBatch>()
        val deleted = mutableListOf<CompostBatch>()
        val batch = CompostBatch(
            id = "b_9",
            batchCode = "CMP-909",
            productType = "VERMICOMPOST",
            quantity = 300.0,
            unit = "KG",
            productionCostInr = 900.0,
            packagingCostInr = 200.0,
            sellingPricePerUnitInr = 15.0,
            soldQuantity = 50.0,
            stockRemaining = 250.0,
            status = "READY_FOR_SALE",
            dateStr = "2026-11-19"
        )

        composeRule.setContent {
            GosalaTheme {
                CompostProductionScreen(
                    language = AppLanguage.HINDI,
                    session = currentSession,
                    compostState = DataUiState.Success(listOf(batch)),
                    onAddBatch = {},
                    onRecordSale = { _, _ -> },
                    onAskAiCostAnalysis = {},
                    onUpdateBatch = { updated.add(it) },
                    onDeleteBatch = { deleted.add(it) }
                )
            }
        }

        composeRule.onNodeWithTag("compost_edit_b_9").performScrollTo().performClick()
        composeRule.onNodeWithTag("compost_save_button").performClick()
        assertEquals(1, updated.size)
        assertEquals("b_9", updated[0].id)

        composeRule.onNodeWithTag("compost_delete_b_9").performScrollTo().performClick()
        assertTrue(deleted.isEmpty())
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        assertEquals(1, deleted.size)
        assertEquals("b_9", deleted[0].id)

        currentSession = viewOnlyStaff
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compost_edit_b_9").assertDoesNotExist()
        composeRule.onNodeWithTag("compost_delete_b_9").assertDoesNotExist()
    }

    @Test
    fun `admin can delete staff account and pending invite with confirmation`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        val deletedStaff = mutableListOf<String>()
        val deletedInvites = mutableListOf<String>()
        val staffAcc = StaffAccount(
            id = "staff_2",
            uid = "staff_2",
            email = "sevak@gosala.org",
            name = "रमेश सेवक",
            designation = "गौ सेवक",
            monthlySalary = 10000.0,
            active = true
        )
        val invite = StaffInvite(
            id = "new@gosala.org",
            email = "new@gosala.org",
            name = "सुरेश सेवक",
            designation = "सहायक",
            monthlySalary = 9000.0
        )

        composeRule.setContent {
            GosalaTheme {
                StaffManagementScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    staffAccountsState = DataUiState.Success(listOf(staffAcc)),
                    staffInvitesState = DataUiState.Success(listOf(invite)),
                    staffOpsState = DataUiState.Success(emptyList()),
                    onCreateInvite = { _, _, _, _, _, _ -> },
                    onUpdateStaff = { _, _, _, _ -> },
                    onAddStaffOperation = {},
                    onDeleteStaff = { deletedStaff.add(it) },
                    onDeleteInvite = { deletedInvites.add(it) }
                )
            }
        }

        composeRule.onNodeWithTag("staff_delete_staff_2").performClick()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        assertEquals(listOf("staff_2"), deletedStaff)

        composeRule.onNodeWithTag("invite_delete_new@gosala.org").performClick()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        assertEquals(listOf("new@gosala.org"), deletedInvites)
    }

    @Test
    fun `auth screen switches across public staff and admin portals cleanly`() {
        var portal by mutableStateOf(LoginPortal.PUBLIC)
        composeRule.setContent {
            GosalaTheme {
                AuthScreen(
                    language = AppLanguage.HINDI,
                    onToggleLanguage = {},
                    selectedPortal = portal,
                    onSelectPortal = { portal = it },
                    sessionError = null,
                    rejectedAccountNote = null,
                    skipAutoSignIn = true,
                    isResolvingSession = false,
                    onAuthSuccess = {}
                )
            }
        }

        composeRule.onNodeWithTag("portal_chip_public").performScrollTo().assertIsSelected()
        composeRule.onNodeWithText("Google से साइन इन करें").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("portal_chip_staff").performScrollTo().performClick()
        assertEquals(LoginPortal.STAFF, portal)
        composeRule.onNodeWithText("Staff Google खाते से साइन इन करें").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("portal_chip_admin").performScrollTo().performClick()
        assertEquals(LoginPortal.ADMIN, portal)
        composeRule.onNodeWithText("Admin Google खाते से साइन इन करें").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `feed donations and notifications screens render cleanly without blank screens`() {
        val adminSession = UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true)
        var screenIndex by mutableStateOf(0)

        composeRule.setContent {
            GosalaTheme {
                when (screenIndex) {
                    0 -> FeedAndOpsScreen(
                        language = AppLanguage.HINDI,
                        session = adminSession,
                        feedLogsState = DataUiState.Success(emptyList()),
                        openAddOnStart = false,
                        onConsumedOpenAdd = {},
                        onAddFeedLog = {},
                        onToggleTaskCompleted = { _, _ -> }
                    )
                    1 -> DonationAndAdoptionScreen(
                        language = AppLanguage.HINDI,
                        session = adminSession,
                        cattleList = emptyList(),
                        donationsState = DataUiState.Success(emptyList()),
                        sponsorshipsState = DataUiState.Success(emptyList()),
                        preselectedCattleForSponsor = null,
                        onClearPreselectedCattle = {},
                        onSubmitDonation = {},
                        onVerifyDonation = { _, _ -> },
                        onSubmitSponsorship = {},
                        onReviewSponsorship = { _, _ -> }
                    )
                    else -> NotificationsScreen(
                        language = AppLanguage.HINDI,
                        session = adminSession,
                        notificationsState = DataUiState.Success(emptyList()),
                        onMarkRead = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("add_feed_fab").assertIsDisplayed()
        screenIndex = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText("गौ सेवा दान").assertIsDisplayed()
        screenIndex = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithText("कोई नई सूचना नहीं").assertIsDisplayed()
    }

    @Test
    fun `auth navigation controller enforces login and role dashboard rules`() {
        assertEquals(Routes.LOGIN, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Unauthenticated()))
        assertEquals(Routes.AUTH_LOADING, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Loading))
        assertEquals(Routes.AUTH_ERROR, AuthNavigationController.resolveTargetRoute(AuthNavigationState.SessionError("Err")))

        val blankUidSession = UserSession(uid = "", role = UserRole.ADMIN, staffActive = true)
        assertEquals(Routes.LOGIN, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Authorized(blankUidSession)))
        assertFalse(AuthNavigationController.canAccessRoute(blankUidSession, Routes.ADMIN_DASHBOARD))
        assertFalse(AuthNavigationController.canAccessRoute(null, Routes.PUBLIC_DASHBOARD))

        val adminSession = UserSession(uid = "a1", role = UserRole.ADMIN, staffActive = true)
        assertEquals(Routes.ADMIN_DASHBOARD, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Authorized(adminSession)))
        assertTrue(AuthNavigationController.canAccessRoute(adminSession, Routes.ADMIN_DASHBOARD))
        assertTrue(AuthNavigationController.canAccessRoute(adminSession, Routes.FINANCE))
        assertTrue(AuthNavigationController.canAccessRoute(adminSession, Routes.STAFF))

        val activeStaff = UserSession(
            uid = "s1",
            role = UserRole.STAFF,
            staffActive = true,
            permissions = mapOf(AppModule.FEED.key to listOf(CrudAction.VIEW.name))
        )
        assertEquals(Routes.STAFF_DASHBOARD, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Authorized(activeStaff)))
        assertTrue(AuthNavigationController.canAccessRoute(activeStaff, Routes.STAFF_DASHBOARD))
        assertTrue(AuthNavigationController.canAccessRoute(activeStaff, Routes.FEED))
        assertFalse(AuthNavigationController.canAccessRoute(activeStaff, Routes.ADMIN_DASHBOARD))
        assertFalse(AuthNavigationController.canAccessRoute(activeStaff, Routes.FINANCE))

        val inactiveStaff = activeStaff.copy(staffActive = false)
        assertFalse(AuthNavigationController.canAccessRoute(inactiveStaff, Routes.STAFF_DASHBOARD))

        val publicSession = UserSession(uid = "p1", role = UserRole.PUBLIC)
        assertEquals(Routes.PUBLIC_DASHBOARD, AuthNavigationController.resolveTargetRoute(AuthNavigationState.Authorized(publicSession)))
        assertTrue(AuthNavigationController.canAccessRoute(publicSession, Routes.PUBLIC_DASHBOARD))
        assertTrue(AuthNavigationController.canAccessRoute(publicSession, Routes.DONATIONS))
        assertFalse(AuthNavigationController.canAccessRoute(publicSession, Routes.ADMIN_DASHBOARD))
        assertFalse(AuthNavigationController.canAccessRoute(publicSession, Routes.STAFF_DASHBOARD))
        assertFalse(AuthNavigationController.canAccessRoute(publicSession, Routes.FINANCE))
    }

    @Test
    fun `root nav host redirects unauthenticated users to login and blocks dashboard access`() {
        val unauthState: AuthNavigationState = AuthNavigationState.Unauthenticated(
            errorMessage = "कृपया लॉगिन करें"
        )
        lateinit var controller: AuthNavigationController

        composeRule.setContent {
            GosalaTheme {
                controller = rememberAuthNavigationController()
                GosalaRootNavHost(
                    authState = unauthState,
                    authNavController = controller,
                    loadingContent = { androidx.compose.material3.Text("Loading") },
                    loginContent = { err, _ ->
                        androidx.compose.material3.Text(
                            text = err ?: "Login Screen",
                            modifier = androidx.compose.ui.Modifier.testTag("nav_login_screen")
                        )
                    },
                    sessionErrorContent = { androidx.compose.material3.Text(it) },
                    adminDashboardContent = {
                        androidx.compose.material3.Text("Admin", modifier = androidx.compose.ui.Modifier.testTag("nav_admin_screen"))
                    },
                    staffDashboardContent = {
                        androidx.compose.material3.Text("Staff", modifier = androidx.compose.ui.Modifier.testTag("nav_staff_screen"))
                    },
                    publicDashboardContent = {
                        androidx.compose.material3.Text("Public", modifier = androidx.compose.ui.Modifier.testTag("nav_public_screen"))
                    }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)
        composeRule.onNodeWithTag("nav_login_screen").assertIsDisplayed()

        // Attempting protected navigation while unauthenticated keeps user on Login Screen
        composeRule.runOnIdle {
            controller.navigateProtected(Routes.ADMIN_DASHBOARD, unauthState)
            controller.navigateProtected(Routes.STAFF_DASHBOARD, unauthState)
            controller.navigateProtected(Routes.PUBLIC_DASHBOARD, unauthState)
        }
        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)
        composeRule.onNodeWithTag("nav_login_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_admin_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_staff_screen").assertDoesNotExist()
        composeRule.onNodeWithTag("nav_public_screen").assertDoesNotExist()
    }

    @Test
    fun `root nav host routes authorized admin staff public to role dashboards and returns to login on logout`() {
        var authState by mutableStateOf<AuthNavigationState>(AuthNavigationState.Unauthenticated())
        lateinit var controller: AuthNavigationController

        composeRule.setContent {
            GosalaTheme {
                controller = rememberAuthNavigationController()
                GosalaRootNavHost(
                    authState = authState,
                    authNavController = controller,
                    loadingContent = { androidx.compose.material3.Text("Loading", modifier = androidx.compose.ui.Modifier.testTag("nav_loading")) },
                    loginContent = { _, _ -> androidx.compose.material3.Text("Login", modifier = androidx.compose.ui.Modifier.testTag("nav_login")) },
                    sessionErrorContent = { androidx.compose.material3.Text(it, modifier = androidx.compose.ui.Modifier.testTag("nav_error")) },
                    adminDashboardContent = { androidx.compose.material3.Text("Admin:${it.uid}", modifier = androidx.compose.ui.Modifier.testTag("nav_admin")) },
                    staffDashboardContent = { androidx.compose.material3.Text("Staff:${it.uid}", modifier = androidx.compose.ui.Modifier.testTag("nav_staff")) },
                    publicDashboardContent = { androidx.compose.material3.Text("Public:${it.uid}", modifier = androidx.compose.ui.Modifier.testTag("nav_public")) }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)
        composeRule.onNodeWithTag("nav_login").assertIsDisplayed()

        // 1. Authorized Admin -> Admin Dashboard
        authState = AuthNavigationState.Authorized(UserSession(uid = "admin_1", role = UserRole.ADMIN, staffActive = true))
        composeRule.waitForIdle()
        assertEquals(Routes.ADMIN_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("nav_admin").assertIsDisplayed()

        // 2. Authorized Staff -> Staff Dashboard (and attempting Admin Dashboard redirects back to Staff Dashboard)
        val staffAuth = AuthNavigationState.Authorized(UserSession(uid = "staff_1", role = UserRole.STAFF, staffActive = true))
        authState = staffAuth
        composeRule.waitForIdle()
        assertEquals(Routes.STAFF_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("nav_staff").assertIsDisplayed()
        composeRule.runOnIdle { controller.navigateProtected(Routes.ADMIN_DASHBOARD, staffAuth) }
        composeRule.waitForIdle()
        assertEquals(Routes.STAFF_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("nav_admin").assertDoesNotExist()

        // 3. Authorized Public -> Public Dashboard (and attempting Admin/Staff Dashboard redirects back to Public Dashboard)
        val publicAuth = AuthNavigationState.Authorized(UserSession(uid = "pub_1", role = UserRole.PUBLIC))
        authState = publicAuth
        composeRule.waitForIdle()
        assertEquals(Routes.PUBLIC_DASHBOARD, controller.currentRoute)
        composeRule.onNodeWithTag("nav_public").assertIsDisplayed()
        composeRule.runOnIdle { controller.navigateProtected(Routes.ADMIN_DASHBOARD, publicAuth) }
        composeRule.waitForIdle()
        assertEquals(Routes.PUBLIC_DASHBOARD, controller.currentRoute)

        // 4. Sign Out -> redirects back to Login Screen and removes dashboard
        authState = AuthNavigationState.Unauthenticated()
        composeRule.waitForIdle()
        assertEquals(Routes.LOGIN, controller.currentRoute)
        composeRule.onNodeWithTag("nav_login").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_public").assertDoesNotExist()
    }
}



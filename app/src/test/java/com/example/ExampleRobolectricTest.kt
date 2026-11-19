package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.AppThemeMode
import com.example.core.QrAndExportUtils
import com.example.data.model.*
import com.example.ui.auth.AuthScreen
import com.example.ui.components.GosalaTopBar
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
}

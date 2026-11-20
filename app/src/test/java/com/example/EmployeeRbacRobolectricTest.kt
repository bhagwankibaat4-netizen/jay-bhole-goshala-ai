package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.core.AppLanguage
import com.example.data.model.*
import com.example.ui.navigation.AuthNavigationController
import com.example.ui.navigation.AuthNavigationState
import com.example.ui.navigation.GosalaRootNavHost
import com.example.ui.navigation.rememberAuthNavigationController
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.DonationAndAdoptionScreen
import com.example.ui.screens.Routes
import com.example.ui.screens.StaffDashboardScreen
import com.example.ui.screens.StaffManagementScreen
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
class EmployeeRbacRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `inactive employee is redirected to login and blocked from all modules`() {
        val inactiveStaff = UserSession(
            uid = "staff_inactive_1",
            email = "inactive@goshala.in",
            displayName = "निष्क्रिय कर्मचारी",
            role = UserRole.STAFF,
            staffActive = false,
            employeeId = "EMP-099",
            designation = "सहायक",
            permissions = mapOf(
                AppModule.CATTLE.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
                AppModule.DONATIONS.key to listOf(CrudAction.VIEW.name, CrudAction.EDIT.name)
            )
        )

        assertFalse(inactiveStaff.canView(AppModule.CATTLE))
        assertFalse(inactiveStaff.canAdd(AppModule.CATTLE))
        assertFalse(inactiveStaff.canView(AppModule.DONATIONS))
        assertFalse(inactiveStaff.canEdit(AppModule.DONATIONS))

        val targetRoute = AuthNavigationController.resolveTargetRoute(
            AuthNavigationState.Authorized(inactiveStaff)
        )
        assertEquals(Routes.LOGIN, targetRoute)

        var loginShown = false
        var staffDashboardShown = false

        composeRule.setContent {
            GosalaTheme {
                val navController = rememberAuthNavigationController()
                GosalaRootNavHost(
                    authState = AuthNavigationState.Authorized(inactiveStaff),
                    authNavController = navController,
                    loadingContent = {},
                    loginContent = { _, _ -> loginShown = true },
                    sessionErrorContent = {},
                    adminDashboardContent = {},
                    staffDashboardContent = { staffDashboardShown = true },
                    publicDashboardContent = {}
                )
            }
        }
        composeRule.waitForIdle()
        assertTrue("Inactive staff must be routed to Login", loginShown)
        assertFalse("Inactive staff must never open Staff Dashboard", staffDashboardShown)
    }

    @Test
    fun `admin employee registration dialog captures employee id active status and all 10 modules`() {
        val adminSession = UserSession(
            uid = "admin_1",
            email = "bhagwankibaat4@gmail.com",
            displayName = "प्रशासक",
            role = UserRole.ADMIN,
            staffActive = true
        )
        var capturedEmpId = ""
        var capturedEmail = ""
        var capturedName = ""
        var capturedPhone = ""
        var capturedDesig = ""
        var capturedActive = false
        var capturedPerms: Map<String, List<String>> = emptyMap()

        composeRule.setContent {
            GosalaTheme {
                StaffManagementScreen(
                    language = AppLanguage.ENGLISH,
                    session = adminSession,
                    staffAccountsState = DataUiState.Success(emptyList()),
                    staffInvitesState = DataUiState.Success(emptyList()),
                    staffOpsState = DataUiState.Success(emptyList()),
                    onCreateInvite = { _, _, _, _, _, _ -> },
                    onUpdateStaff = { _, _, _, _ -> },
                    onAddStaffOperation = {},
                    onCreateEmployeeWithDetails = { empId, email, name, phone, desig, _, active, perms ->
                        capturedEmpId = empId
                        capturedEmail = email
                        capturedName = name
                        capturedPhone = phone
                        capturedDesig = desig
                        capturedActive = active
                        capturedPerms = perms
                    }
                )
            }
        }

        composeRule.onNodeWithTag("add_staff_invite_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("staff_employee_id_input").performTextReplacement("EMP-105")
        composeRule.onNodeWithTag("staff_email_input").performTextInput("ramesh@goshala.org")
        composeRule.onNodeWithTag("staff_name_input").performTextInput("Ramesh Yadav")
        composeRule.onNodeWithTag("staff_phone_input").performTextInput("9876543210")
        composeRule.onNodeWithTag("staff_designation_input").performTextReplacement("Cow Caretaker")
        composeRule.onNodeWithTag("staff_salary_input").performTextReplacement("12000")

        // Verify STAFF and DONATIONS module permissions exist in matrix and toggle DONATIONS ADD (auto-enables VIEW)
        composeRule.onNodeWithTag("perm_row_staff").performScrollTo()
        composeRule.onNodeWithTag("perm_staff_VIEW").assertExists()
        composeRule.onNodeWithTag("perm_row_donations").performScrollTo()
        composeRule.onNodeWithTag("perm_donations_ADD").performClick()
        composeRule.onNodeWithTag("perm_row_finance").performScrollTo()
        composeRule.onNodeWithTag("perm_finance_VIEW").performClick()

        composeRule.onNodeWithTag("staff_invite_save_button").performClick()
        composeRule.waitForIdle()

        assertEquals("EMP-105", capturedEmpId)
        assertEquals("ramesh@goshala.org", capturedEmail)
        assertEquals("Ramesh Yadav", capturedName)
        assertEquals("9876543210", capturedPhone)
        assertEquals("Cow Caretaker", capturedDesig)
        assertTrue(capturedActive)
        assertTrue(capturedPerms[AppModule.DONATIONS.key].orEmpty().contains(CrudAction.ADD.name))
        assertTrue(capturedPerms[AppModule.DONATIONS.key].orEmpty().contains(CrudAction.VIEW.name))
        assertTrue(capturedPerms[AppModule.FINANCE.key].orEmpty().contains(CrudAction.VIEW.name))
    }

    @Test
    fun `employee dashboard shows employee id active badge permitted modules and updates live`() {
        var sessionState by mutableStateOf(
            UserSession(
                uid = "staff_live_1",
                email = "sewak@goshala.in",
                displayName = "मोहन सेवक",
                role = UserRole.STAFF,
                staffActive = true,
                employeeId = "EMP-202",
                designation = "गौशाला पर्यवेक्षक",
                permissions = mapOf(
                    AppModule.CATTLE.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
                    AppModule.FEED.key to listOf(CrudAction.VIEW.name)
                )
            )
        )

        composeRule.setContent {
            GosalaTheme {
                StaffDashboardScreen(
                    language = AppLanguage.ENGLISH,
                    session = sessionState,
                    cattle = listOf(Cattle(id = "c1", animalCode = "JBGS-001", name = "Ganga", healthStatus = "HEALTHY")),
                    feedLogs = listOf(FeedAndOperationLog(id = "f1", taskTitle = "Morning Fodder", taskCompleted = false)),
                    donations = listOf(Donation(id = "d1", donorName = "Devotee", amountInr = 1100.0, paymentStatus = "VERIFIED_SUCCESS")),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }

        // Header shows Employee ID and Active badge
        composeRule.onNodeWithTag("staff_employee_id_badge").assertExists()
        composeRule.onNodeWithTag("staff_active_status_badge").assertExists()

        // Permitted quick action (CATTLE ADD) visible; unpermitted quick actions hidden
        composeRule.onNodeWithTag("staff_quick_add_cattle").assertExists()
        composeRule.onNodeWithTag("staff_quick_add_feed").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_quick_add_finance").assertDoesNotExist()

        // Only CATTLE & FEED module tiles visible; DONATIONS, FINANCE, and AI hidden
        composeRule.onNodeWithTag("staff_dashboard_root").performScrollToNode(hasTestTag("staff_mod_cattle"))
        composeRule.onNodeWithTag("staff_mod_cattle").assertExists()
        composeRule.onNodeWithTag("staff_mod_feed").assertExists()
        composeRule.onNodeWithTag("staff_mod_donations").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_mod_finance").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_mod_ai").assertDoesNotExist()

        // Simulate live permission update from Admin granting FINANCE + DONATIONS + AI
        composeRule.runOnIdle {
            sessionState = sessionState.copy(
                permissions = sessionState.permissions + mapOf(
                    AppModule.FINANCE.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
                    AppModule.DONATIONS.key to listOf(CrudAction.VIEW.name),
                    AppModule.AI.key to listOf(CrudAction.VIEW.name)
                )
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("staff_dashboard_root").performScrollToNode(hasTestTag("staff_mod_finance"))
        composeRule.onNodeWithTag("staff_mod_finance").assertExists()
        composeRule.onNodeWithTag("staff_dashboard_root").performScrollToNode(hasTestTag("staff_mod_donations"))
        composeRule.onNodeWithTag("staff_mod_donations").assertExists()
        composeRule.onNodeWithTag("staff_dashboard_root").performScrollToNode(hasTestTag("staff_mod_ai"))
        composeRule.onNodeWithTag("staff_mod_ai").assertExists()
    }

    @Test
    fun `employee with zero assigned modules sees empty state card instead of public dashboard`() {
        val noModuleStaff = UserSession(
            uid = "staff_empty",
            email = "newstaff@goshala.in",
            displayName = "नया कर्मचारी",
            role = UserRole.STAFF,
            staffActive = true,
            employeeId = "EMP-301",
            designation = "प्रशिक्षु",
            permissions = emptyMap()
        )

        composeRule.setContent {
            GosalaTheme {
                StaffDashboardScreen(
                    language = AppLanguage.HINDI,
                    session = noModuleStaff,
                    cattle = emptyList(),
                    feedLogs = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }

        composeRule.onNodeWithTag("staff_dashboard_root").assertExists()
        composeRule.onNodeWithTag("staff_no_modules_card").assertExists()
        composeRule.onNodeWithTag("staff_quick_actions_card").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_mod_donations").assertDoesNotExist()
        composeRule.onNodeWithTag("staff_mod_ai").assertDoesNotExist()
    }

    @Test
    fun `donations screen enforces view-only vs full-crud staff permissions and preserves public flow`() {
        val sampleDonation = Donation(
            id = "don_1",
            receiptNumber = "JBGS-R-001",
            donorName = "संतोष शर्मा",
            amountInr = 2100.0,
            purpose = "FODDER",
            paymentStatus = "PENDING_VERIFICATION",
            dateStr = "2026-11-20"
        )
        val sampleSponsorship = Sponsorship(
            id = "sp_1",
            cattleId = "c1",
            cattleName = "नंदिनी",
            animalCode = "JBGS-G-001",
            applicantName = "राजेश गुप्ता",
            monthlySupportInr = 2100.0,
            durationMonths = 3L,
            totalSupportInr = 6300.0,
            status = "PENDING",
            disclaimerAccepted = true
        )

        var currentSession by mutableStateOf(
            UserSession(
                uid = "staff_view_only",
                email = "viewer@goshala.in",
                displayName = "रीड ओनली स्टाफ",
                role = UserRole.STAFF,
                staffActive = true,
                employeeId = "EMP-401",
                permissions = mapOf(AppModule.DONATIONS.key to listOf(CrudAction.VIEW.name))
            )
        )
        var deletedDonationId = ""

        composeRule.setContent {
            GosalaTheme {
                DonationAndAdoptionScreen(
                    language = AppLanguage.ENGLISH,
                    session = currentSession,
                    cattleList = listOf(
                        Cattle(
                            id = "c1",
                            animalCode = "JBGS-G-001",
                            name = "नंदिनी",
                            publicApproved = true,
                            sponsorshipAvailable = true
                        )
                    ),
                    donationsState = DataUiState.Success(listOf(sampleDonation)),
                    sponsorshipsState = DataUiState.Success(listOf(sampleSponsorship)),
                    preselectedCattleForSponsor = null,
                    onClearPreselectedCattle = {},
                    onSubmitDonation = {},
                    onVerifyDonation = { _, _ -> },
                    onSubmitSponsorship = {},
                    onReviewSponsorship = { _, _ -> },
                    onDeleteDonation = { deletedDonationId = it }
                )
            }
        }

        // 1. View-Only Staff on Donations tab: sees banner, NO add/verify/edit/delete controls
        composeRule.onNodeWithTag("donations_view_only_banner").assertExists()
        composeRule.onNodeWithTag("submit_donation_button").assertDoesNotExist()
        composeRule.onNodeWithTag("donation_verify_don_1").assertDoesNotExist()
        composeRule.onNodeWithTag("donation_edit_don_1").assertDoesNotExist()
        composeRule.onNodeWithTag("donation_delete_don_1").assertDoesNotExist()

        // Switch to Go-Seva Sponsorship tab for View-Only Staff
        composeRule.onNodeWithTag("donations_tab_sponsor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sponsorships_view_only_banner").assertExists()
        composeRule.onNodeWithTag("submit_sponsorship_button").assertDoesNotExist()
        composeRule.onNodeWithTag("sponsorship_approve_sp_1").assertDoesNotExist()
        composeRule.onNodeWithTag("sponsorship_edit_sp_1").assertDoesNotExist()
        composeRule.onNodeWithTag("sponsorship_delete_sp_1").assertDoesNotExist()

        // 2. Grant Full CRUD to Staff: Add, Verify, Edit, Delete become available
        composeRule.runOnIdle {
            currentSession = currentSession.copy(
                permissions = mapOf(
                    AppModule.DONATIONS.key to listOf(
                        CrudAction.VIEW.name,
                        CrudAction.ADD.name,
                        CrudAction.EDIT.name,
                        CrudAction.DELETE.name
                    )
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sponsorships_view_only_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("sponsorships_lazy_list").performScrollToNode(hasTestTag("submit_sponsorship_button"))
        composeRule.onNodeWithTag("submit_sponsorship_button").assertExists()
        composeRule.onNodeWithTag("sponsorships_lazy_list").performScrollToNode(hasTestTag("sponsorship_approve_sp_1"))
        composeRule.onNodeWithTag("sponsorship_approve_sp_1").assertExists()
        composeRule.onNodeWithTag("sponsorship_edit_sp_1").assertExists()
        composeRule.onNodeWithTag("sponsorship_delete_sp_1").assertExists()

        // Switch back to Donations tab & verify Full CRUD controls + delete confirmation
        composeRule.onNodeWithTag("donations_tab_donate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("donations_view_only_banner").assertDoesNotExist()
        composeRule.onNodeWithTag("donations_lazy_list").performScrollToNode(hasTestTag("submit_donation_button"))
        composeRule.onNodeWithTag("submit_donation_button").assertExists()
        composeRule.onNodeWithTag("donations_lazy_list").performScrollToNode(hasTestTag("donation_verify_don_1"))
        composeRule.onNodeWithTag("donation_verify_don_1").assertExists()
        composeRule.onNodeWithTag("donation_edit_don_1").assertExists()
        composeRule.onNodeWithTag("donation_delete_don_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("don_1", deletedDonationId)

        // 3. Public User preserves self-donation form while Admin/Staff Edit/Delete/Verify remain hidden
        composeRule.runOnIdle {
            currentSession = UserSession(
                uid = "public_1",
                email = "bhakt@gmail.com",
                displayName = "गौ भक्त",
                role = UserRole.PUBLIC
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("donations_lazy_list").performScrollToNode(hasTestTag("submit_donation_button"))
        composeRule.onNodeWithTag("submit_donation_button").assertExists()
        composeRule.onNodeWithTag("donation_verify_don_1").assertDoesNotExist()
        composeRule.onNodeWithTag("donation_edit_don_1").assertDoesNotExist()
        composeRule.onNodeWithTag("donation_delete_don_1").assertDoesNotExist()
    }

    @Test
    fun `admin staff and public dashboards stay strictly distinct`() {
        val adminSession = UserSession(uid = "a1", role = UserRole.ADMIN, staffActive = true)
        val staffSession = UserSession(uid = "s1", role = UserRole.STAFF, staffActive = true)
        val publicSession = UserSession(uid = "p1", role = UserRole.PUBLIC)

        assertEquals(Routes.ADMIN_DASHBOARD, AuthNavigationController.dashboardRouteForRole(adminSession.role))
        assertEquals(Routes.STAFF_DASHBOARD, AuthNavigationController.dashboardRouteForRole(staffSession.role))
        assertEquals(Routes.PUBLIC_DASHBOARD, AuthNavigationController.dashboardRouteForRole(publicSession.role))

        composeRule.setContent {
            GosalaTheme {
                AdminDashboardScreen(
                    language = AppLanguage.ENGLISH,
                    session = adminSession,
                    cattle = emptyList(),
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
            }
        }
        composeRule.onNodeWithTag("admin_dashboard_root").assertExists()
        composeRule.onNodeWithTag("staff_dashboard_root").assertDoesNotExist()
    }
}

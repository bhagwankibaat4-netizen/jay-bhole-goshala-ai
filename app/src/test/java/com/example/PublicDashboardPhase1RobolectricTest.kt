package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.core.AppLanguage
import com.example.data.model.Cattle
import com.example.data.model.PublicSummary
import com.example.data.model.UserRole
import com.example.data.model.UserSession
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.Routes
import com.example.ui.theme.GosalaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PublicDashboardPhase1RobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val publicSession = UserSession(
        uid = "pub_user_1",
        email = "bhakt@example.com",
        displayName = "रमेश भक्त",
        role = UserRole.PUBLIC
    )

    private val sampleSummary = PublicSummary(
        totalCattle = 48,
        healthyCattle = 45,
        sickCattle = 3,
        todayFeedKg = 620.0,
        todayExpenseInr = 4500.0
    )

    private val approvedCows = listOf(
        Cattle(id = "c1", animalCode = "JBGS-G-001", name = "नंदिनी", category = "COW", healthStatus = "HEALTHY")
    )

    @Test
    fun `public dashboard displays goshala info, production process, schemes, all 6 products, and farmer relation`() {
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = sampleSummary,
                    cattle = approvedCows,
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
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")

        // 1. Goshala Overview, Production Process & Schemes
        root.performScrollToNode(hasTestTag("public_goshala_info_section"))
        composeRule.onNodeWithTag("public_goshala_info_section").assertIsDisplayed()

        root.performScrollToNode(hasTestTag("public_production_process_card"))
        composeRule.onNodeWithTag("public_production_process_card").assertIsDisplayed()

        root.performScrollToNode(hasTestTag("public_schemes_section"))
        composeRule.onNodeWithTag("public_schemes_section").assertIsDisplayed()

        // 2. All 6 Goshala Products
        root.performScrollToNode(hasTestTag("public_products_section"))
        composeRule.onNodeWithTag("public_products_section").assertIsDisplayed()

        val productTags = listOf(
            "product_card_compost",
            "product_card_vermicompost",
            "product_card_kande",
            "product_card_gaukashtha",
            "product_card_dhoop",
            "product_card_other_products"
        )
        productTags.forEach { tag ->
            root.performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        // 3. Farmer–Goshala Relation section
        root.performScrollToNode(hasTestTag("public_farmer_relation_section"))
        composeRule.onNodeWithTag("public_farmer_relation_section").assertIsDisplayed()
        root.performScrollToNode(hasTestTag("public_farmer_apply_btn"))
        composeRule.onNodeWithTag("public_farmer_apply_btn").assertIsDisplayed()
    }

    @Test
    fun `public products category chips filter products accurately`() {
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = sampleSummary,
                    cattle = approvedCows,
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
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("public_products_section"))

        // Filter to MANURE (compost + vermicompost only)
        composeRule.onNodeWithTag("product_filter_manure").performClick()
        composeRule.waitForIdle()
        root.performScrollToNode(hasTestTag("product_card_compost"))
        composeRule.onNodeWithTag("product_card_compost").assertIsDisplayed()
        composeRule.onNodeWithTag("product_card_kande").assertDoesNotExist()
        composeRule.onNodeWithTag("product_card_dhoop").assertDoesNotExist()

        // Filter to PUJA (dhoop + other_products)
        root.performScrollToNode(hasTestTag("public_products_section"))
        composeRule.onNodeWithTag("product_filter_puja").performScrollTo().performClick()
        composeRule.waitForIdle()
        root.performScrollToNode(hasTestTag("product_card_dhoop"))
        composeRule.onNodeWithTag("product_card_dhoop").assertIsDisplayed()
        composeRule.onNodeWithTag("product_card_compost").assertDoesNotExist()

        // Reset to ALL
        root.performScrollToNode(hasTestTag("public_products_section"))
        composeRule.onNodeWithTag("product_filter_all").performScrollTo().performClick()
        composeRule.waitForIdle()
        root.performScrollToNode(hasTestTag("product_card_gaukashtha"))
        composeRule.onNodeWithTag("product_card_gaukashtha").assertIsDisplayed()
    }

    @Test
    fun `product buy request validates inputs and shows confirmation banner`() {
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = sampleSummary,
                    cattle = approvedCows,
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
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("product_card_compost"))
        composeRule.onNodeWithTag("product_buy_compost").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_purchase_dialog").assertIsDisplayed()

        // Submit without phone/address -> validation error shown
        composeRule.onNodeWithTag("product_purchase_submit_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("कृपया सही नाम, 10 अंकों का मोबाइल नंबर और पता दर्ज करें।")
            .assertIsDisplayed()

        // Fill valid phone, quantity, and address -> submits cleanly
        composeRule.onNodeWithTag("purchase_phone_input").performTextInput("9876543210")
        composeRule.onNodeWithTag("purchase_qty_input").performTextClearance()
        composeRule.onNodeWithTag("purchase_qty_input").performTextInput("2")
        composeRule.onNodeWithTag("purchase_address_input").performTextInput("ग्राम गोपी, पोरसा")
        composeRule.onNodeWithTag("product_purchase_submit_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_purchase_dialog").assertDoesNotExist()
        root.performScrollToNode(hasTestTag("public_request_confirmation_card"))
        composeRule.onNodeWithTag("public_request_confirmation_card").assertIsDisplayed()

        // Dismiss banner
        composeRule.onNodeWithTag("dismiss_public_request_banner").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("public_request_confirmation_card").assertDoesNotExist()
    }

    @Test
    fun `farmer contact application validates inputs and submits confirmation`() {
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = sampleSummary,
                    cattle = approvedCows,
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
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("public_farmer_apply_btn"))
        composeRule.onNodeWithTag("public_farmer_apply_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("farmer_inquiry_dialog").assertIsDisplayed()

        // Switch purpose to BUY_MANURE and test empty validation
        composeRule.onNodeWithTag("farmer_type_manure").performClick()
        composeRule.onNodeWithTag("farmer_inquiry_submit_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("कृपया सही नाम, 10 अंकों का मोबाइल नंबर और गाँव दर्ज करें।")
            .assertIsDisplayed()

        // Fill valid details & submit
        composeRule.onNodeWithTag("farmer_phone_input").performTextInput("9123456789")
        composeRule.onNodeWithTag("farmer_village_input").performTextInput("पोरसा, मुरैना")
        composeRule.onNodeWithTag("farmer_material_input").performTextInput("5 बैग वर्मी कम्पोस्ट")
        composeRule.onNodeWithTag("farmer_inquiry_submit_btn").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("farmer_inquiry_dialog").assertDoesNotExist()
        root.performScrollToNode(hasTestTag("public_request_confirmation_card"))
        composeRule.onNodeWithTag("public_request_confirmation_card").assertIsDisplayed()
    }

    @Test
    fun `public dashboard enforces read-only permissions and preserves cattle adoption and donation navigation`() {
        val navigatedRoutes = mutableListOf<String>()
        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = sampleSummary,
                    cattle = approvedCows,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    onNavigate = { navigatedRoutes.add(it) },
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Strictly NO Admin/Staff add/edit/delete or management tiles on Public Dashboard
        val forbiddenAdminTags = listOf(
            "quick_add_cattle",
            "quick_add_feed",
            "quick_add_expense",
            "module_staff",
            "module_finance",
            "module_production",
            "module_farmers",
            "module_health",
            "module_feed",
            "module_reports"
        )
        forbiddenAdminTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }

        // Verify navigation from Goshala Info & Seva Schemes to existing real flows
        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("public_goshala_info_section"))
        composeRule.onNodeWithTag("public_view_cattle_btn").performClick()

        root.performScrollToNode(hasTestTag("public_schemes_section"))
        composeRule.onNodeWithTag("public_scheme_sponsor_btn").performClick()
        composeRule.onNodeWithTag("public_scheme_donate_btn").performClick()

        assertEquals(
            listOf(Routes.CATTLE, Routes.DONATIONS, Routes.DONATIONS),
            navigatedRoutes
        )
    }
}

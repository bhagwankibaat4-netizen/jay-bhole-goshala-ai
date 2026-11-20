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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.core.AppLanguage
import com.example.data.model.*
import com.example.ui.navigation.AuthNavigationController
import com.example.ui.screens.AdminApplicationsScreen
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.DonationAndAdoptionScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.Routes
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2OrdersAndAdoptionRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val publicSession = UserSession(
        uid = "pub_user_phase2",
        email = "bhakt2@example.com",
        displayName = "मोहन शर्मा",
        role = UserRole.PUBLIC
    )

    private val adminSession = UserSession(
        uid = "admin_phase2",
        email = "admin@gosala.org",
        displayName = "गौशाला प्रशासक",
        role = UserRole.ADMIN
    )

    private val staffSession = UserSession(
        uid = "staff_phase2",
        email = "staff@gosala.org",
        displayName = "रवि कर्मचारी",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(AppModule.DONATIONS.key to listOf(CrudAction.VIEW.name, CrudAction.EDIT.name))
    )

    private val adoptableCows = listOf(
        Cattle(
            id = "cow_1",
            animalCode = "JBGS-G-101",
            name = "गंगा गौमाता",
            category = "COW",
            breed = "गिर (Gir)",
            ageText = "5 वर्ष",
            healthStatus = "HEALTHY",
            notes = "शांत स्वभाव व स्वस्थ देशी गाय",
            publicApproved = true,
            sponsorshipAvailable = true,
            monthlySupportInr = 2100L
        ),
        Cattle(
            id = "cow_2",
            animalCode = "JBGS-G-102",
            name = "कपिला",
            category = "COW",
            breed = "साहीवाल",
            ageText = "4 वर्ष",
            healthStatus = "HEALTHY",
            notes = "दैनिक सेवा हेतु उपलब्ध",
            publicApproved = true,
            sponsorshipAvailable = true,
            monthlySupportInr = 2500L
        )
    )

    @Test
    fun `public product purchase submits valid ProductOrder with qty stepper and UPI payment status PAID`() {
        var submittedOrder: ProductOrder? = null

        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = PublicSummary(totalCattle = 40, healthyCattle = 38, sickCattle = 2),
                    cattle = adoptableCows,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {},
                    productOrdersState = DataUiState.Success(emptyList()),
                    onSubmitProductOrder = { submittedOrder = it }
                )
            }
        }
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("product_card_vermicompost"))
        composeRule.onNodeWithTag("product_card_vermicompost").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("product_purchase_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("purchase_total_amount_box").assertIsDisplayed()

        // Increase quantity from 1 -> 2 using stepper
        composeRule.onNodeWithTag("purchase_qty_plus").performScrollTo().performClick()
        composeRule.onNodeWithTag("purchase_phone_input").performScrollTo().performTextInput("9876543210")
        composeRule.onNodeWithTag("purchase_address_input").performScrollTo().performTextInput("ग्राम बिन्जारी, भिवाड़ी")
        composeRule.onNodeWithTag("purchase_pay_mode_upi").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("purchase_payment_ref_input").performScrollTo().performTextInput("UPI-99887766")

        composeRule.onNodeWithTag("product_purchase_submit_btn").performClick()
        composeRule.waitForIdle()

        assertNotNull("Expected ProductOrder to be submitted", submittedOrder)
        val order = submittedOrder!!
        assertEquals("vermicompost", order.productId)
        assertEquals(2L, order.quantity)
        assertEquals(900.0, order.totalAmountInr, 0.01)
        assertEquals("NEW", order.orderStatus)
        assertEquals("UPI", order.paymentMode)
        assertEquals("PAID", order.paymentStatus)
        assertEquals("UPI-99887766", order.paymentRef)
        assertEquals(publicSession.uid, order.buyerUid)
        assertEquals("9876543210", order.buyerPhone)
        assertTrue(order.orderNumber.startsWith("JBGS-ORD-"))
    }

    @Test
    fun `public dashboard displays live my product orders with status, payment badge, and admin note`() {
        val myOrders = listOf(
            ProductOrder(
                id = "ord_live_1",
                orderNumber = "JBGS-ORD-2026",
                buyerUid = publicSession.uid,
                buyerName = publicSession.displayName,
                buyerPhone = "9876543210",
                buyerAddress = "तिजारा रोड, अलवर",
                productId = "compost",
                productName = "जैविक गोबर खाद",
                unitLabel = "50 किग्रा बैग",
                unitPriceInr = 350.0,
                quantity = 3L,
                totalAmountInr = 1050.0,
                orderStatus = "DELIVERY",
                paymentMode = "UPI",
                paymentStatus = "VERIFIED",
                adminNote = "आज शाम 4 बजे डिलीवरी पहुँचेगी।",
                dateStr = "2026-11-20"
            )
        )

        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = PublicSummary(totalCattle = 40, healthyCattle = 38),
                    cattle = adoptableCows,
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {},
                    productOrdersState = DataUiState.Success(myOrders)
                )
            }
        }
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("public_my_orders_section"))
        composeRule.onNodeWithTag("public_order_card_ord_live_1").assertIsDisplayed()
        composeRule.onNodeWithText("डिलीवरी", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Verified (सत्यापित)", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("आज शाम 4 बजे डिलीवरी पहुँचेगी।", substring = true).assertIsDisplayed()
    }

    @Test
    fun `cow adoption form shows cow cards with ID and details and submits all 7 required fields`() {
        var submittedSponsorship: Sponsorship? = null

        composeRule.setContent {
            GosalaTheme {
                DonationAndAdoptionScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    cattleList = adoptableCows,
                    donationsState = DataUiState.Success(emptyList()),
                    sponsorshipsState = DataUiState.Success(emptyList()),
                    preselectedCattleForSponsor = null,
                    onClearPreselectedCattle = {},
                    onSubmitDonation = {},
                    onVerifyDonation = { _, _ -> },
                    onSubmitSponsorship = { submittedSponsorship = it },
                    onReviewSponsorship = { _, _ -> },
                    initialTabIndex = 1
                )
            }
        }
        composeRule.waitForIdle()

        // Verify both available cows appear with photo/ID cards and select second cow (JBGS-G-102)
        composeRule.onNodeWithTag("sponsor_cow_card_JBGS-G-101").assertIsDisplayed()
        composeRule.onNodeWithTag("sponsor_cow_card_JBGS-G-102").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("sponsor_name_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("sponsor_name_input").performTextInput("सतीश यादव")
        composeRule.onNodeWithTag("sponsor_phone_input").performScrollTo().performTextInput("9829011223")
        composeRule.onNodeWithTag("sponsor_address_input").performScrollTo().performTextInput("भिवाड़ी, राजस्थान")
        composeRule.onNodeWithTag("sponsor_duration_6").performScrollTo().performClick()
        composeRule.onNodeWithTag("sponsor_message_input").performScrollTo().performTextInput("गौमाता के चारे हेतु सादर संकल्प")
        composeRule.onNodeWithTag("sponsor_pay_mode_upi").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sponsor_payment_ref_input").performScrollTo().performTextInput("UTR-55443322")
        composeRule.onNodeWithTag("sponsor_disclaimer_checkbox").performScrollTo().performClick()
        composeRule.onNodeWithTag("submit_sponsorship_button").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertNotNull("Expected Sponsorship application to be submitted", submittedSponsorship)
        val sp = submittedSponsorship!!
        assertEquals("cow_2", sp.cattleId)
        assertEquals("JBGS-G-102", sp.animalCode)
        assertEquals("कपिला", sp.cattleName)
        assertEquals("सतीश यादव", sp.applicantName)
        assertEquals("9829011223", sp.applicantPhone)
        assertEquals("भिवाड़ी, राजस्थान", sp.applicantAddress)
        assertEquals(6L, sp.durationMonths)
        assertEquals(2500.0, sp.monthlySupportInr, 0.01)
        assertEquals(15000.0, sp.totalSupportInr, 0.01)
        assertEquals("गौमाता के चारे हेतु सादर संकल्प", sp.message)
        assertEquals("UPI", sp.paymentMode)
        assertEquals("PAID", sp.paymentStatus)
        assertEquals("UTR-55443322", sp.paymentRef)
        assertTrue(sp.disclaimerAccepted)
    }

    @Test
    fun `admin applications screen manages product orders across statuses and verifies adoption payments`() {
        val sampleOrder = ProductOrder(
            id = "ord_adm_1",
            orderNumber = "JBGS-ORD-8801",
            buyerUid = publicSession.uid,
            buyerName = "मोहन शर्मा",
            buyerPhone = "9876543210",
            buyerAddress = "भिवाड़ी",
            productId = "compost",
            productName = "जैविक गोबर खाद",
            unitLabel = "50 किग्रा बैग",
            unitPriceInr = 350.0,
            quantity = 2L,
            totalAmountInr = 700.0,
            orderStatus = "NEW",
            paymentMode = "UPI",
            paymentStatus = "PAID",
            paymentRef = "UPI-1234",
            dateStr = "2026-11-20"
        )
        val sampleAdoption = Sponsorship(
            id = "sp_adm_1",
            applicantUid = publicSession.uid,
            applicantName = "सतीश यादव",
            applicantPhone = "9829011223",
            applicantAddress = "भिवाड़ी",
            cattleId = "cow_1",
            cattleName = "गंगा गौमाता",
            animalCode = "JBGS-G-101",
            monthlySupportInr = 2100.0,
            durationMonths = 3L,
            totalSupportInr = 6300.0,
            status = "PENDING",
            message = "सेवा संकल्प",
            paymentMode = "CASH",
            paymentStatus = "PENDING",
            disclaimerAccepted = true,
            dateStr = "2026-11-20"
        )

        var updatedOrderStatus: String? = null
        var updatedOrderPayment: String? = null
        var adoptionApproved: Boolean? = null
        var verifiedAdoptionPayment: String? = null

        composeRule.setContent {
            GosalaTheme {
                AdminApplicationsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    productOrdersState = DataUiState.Success(listOf(sampleOrder)),
                    sponsorshipsState = DataUiState.Success(listOf(sampleAdoption)),
                    onUpdateOrderStatus = { _, st, pay, _ ->
                        updatedOrderStatus = st
                        updatedOrderPayment = pay
                    },
                    onDeleteOrder = {},
                    onReviewSponsorship = { _, approve -> adoptionApproved = approve },
                    onVerifySponsorshipPayment = { _, pay -> verifiedAdoptionPayment = pay },
                    onDeleteSponsorship = {}
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Product Orders tab: update order status to READY and verify payment
        composeRule.onNodeWithTag("admin_orders_list").performScrollToNode(hasTestTag("admin_order_card_ord_adm_1"))
        composeRule.onNodeWithTag("admin_order_card_ord_adm_1").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_orders_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_order_status_ord_adm_1_READY").performClick()
        composeRule.waitForIdle()
        assertEquals("READY", updatedOrderStatus)

        composeRule.onNodeWithTag("admin_order_verify_pay_ord_adm_1").performClick()
        composeRule.waitForIdle()
        assertEquals("VERIFIED", updatedOrderPayment)

        // 2. Cow Adoptions tab: approve adoption and verify payment
        composeRule.onNodeWithTag("admin_apps_tab_adoptions").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_adoptions_list").performScrollToNode(hasTestTag("admin_adoption_card_sp_adm_1"))
        composeRule.onNodeWithTag("admin_adoption_card_sp_adm_1").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_adoptions_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_adoption_approve_sp_adm_1").performClick()
        composeRule.waitForIdle()
        assertEquals(true, adoptionApproved)

        composeRule.onNodeWithTag("admin_adoption_verify_pay_sp_adm_1").performClick()
        composeRule.waitForIdle()
        assertEquals("VERIFIED", verifiedAdoptionPayment)
    }

    @Test
    fun `staff and public roles are strictly blocked from AdminApplicationsScreen and route guard`() {
        assertTrue(AuthNavigationController.canAccessRoute(adminSession, Routes.APPLICATIONS))
        assertFalse(AuthNavigationController.canAccessRoute(staffSession, Routes.APPLICATIONS))
        assertFalse(AuthNavigationController.canAccessRoute(publicSession, Routes.APPLICATIONS))

        composeRule.setContent {
            GosalaTheme {
                AdminApplicationsScreen(
                    language = AppLanguage.HINDI,
                    session = staffSession,
                    productOrdersState = DataUiState.Success(emptyList()),
                    sponsorshipsState = DataUiState.Success(emptyList()),
                    onUpdateOrderStatus = { _, _, _, _ -> },
                    onDeleteOrder = {},
                    onReviewSponsorship = { _, _ -> },
                    onVerifySponsorshipPayment = { _, _ -> },
                    onDeleteSponsorship = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_applications_access_denied").assertIsDisplayed()
    }

    @Test
    fun `admin dashboard shows applications tile and pending product order count`() {
        var navigatedRoute: String? = null
        val newOrder = ProductOrder(
            id = "ord_1",
            orderNumber = "JBGS-ORD-1001",
            orderStatus = "NEW"
        )

        composeRule.setContent {
            GosalaTheme {
                AdminDashboardScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattle = adoptableCows,
                    health = emptyList(),
                    feedLogs = emptyList(),
                    staffAccounts = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    sponsorships = emptyList(),
                    compost = emptyList(),
                    productOrders = listOf(newOrder),
                    onNavigate = { navigatedRoute = it },
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_pending_approvals_card").assertIsDisplayed().performClick()
        assertEquals(Routes.APPLICATIONS, navigatedRoute)

        composeRule.onNodeWithTag("admin_dashboard_root")
            .performScrollToNode(hasTestTag("admin_mod_applications"))
        composeRule.onNodeWithTag("admin_mod_applications").assertIsDisplayed()
    }

    @Test
    fun `ProductOrder and Sponsorship schemas and Hindi status labels match Phase 2 specification`() {
        assertEquals("नया आवेदन", productOrderStatusLabel("NEW", true))
        assertEquals("स्वीकृत", productOrderStatusLabel("APPROVED", true))
        assertEquals("तैयार", productOrderStatusLabel("READY", true))
        assertEquals("डिलीवरी", productOrderStatusLabel("DELIVERY", true))
        assertEquals("पूर्ण", productOrderStatusLabel("COMPLETED", true))
        assertEquals("रद्द", productOrderStatusLabel("CANCELLED", true))

        assertEquals("Pending (लंबित)", paymentStatusLabel("PENDING", true))
        assertEquals("Paid (भुगतान किया)", paymentStatusLabel("PAID", true))
        assertEquals("Verified (सत्यापित)", paymentStatusLabel("VERIFIED", true))

        val orderMap = ProductOrder(
            orderNumber = "JBGS-ORD-9999",
            buyerUid = "u1",
            buyerName = "रमेश",
            buyerPhone = "9876543210",
            buyerAddress = "भिवाड़ी",
            productId = "compost",
            productName = "जैविक गोबर खाद",
            unitLabel = "50 किग्रा बैग",
            unitPriceInr = 350.0,
            quantity = 2L,
            totalAmountInr = 700.0
        ).toCreateMap()
        val expectedOrderKeys = setOf(
            "orderNumber", "buyerUid", "buyerName", "buyerPhone", "buyerAddress",
            "productId", "productName", "unitLabel", "unitPriceInr", "quantity",
            "totalAmountInr", "orderStatus", "paymentMode", "paymentStatus",
            "paymentRef", "notes", "adminNote", "dateStr", "reviewedBy",
            "createdAt", "updatedAt"
        )
        assertEquals(expectedOrderKeys, orderMap.keys)

        val sponsorshipMap = Sponsorship(
            applicantUid = "u1",
            applicantName = "रमेश",
            applicantPhone = "9876543210",
            applicantAddress = "भिवाड़ी",
            cattleId = "c1",
            cattleName = "गंगा",
            animalCode = "JBGS-G-001",
            monthlySupportInr = 2100.0,
            durationMonths = 3L,
            totalSupportInr = 6300.0,
            message = "गौसेवा",
            paymentMode = "UPI",
            paymentStatus = "PAID",
            paymentRef = "UPI123",
            disclaimerAccepted = true
        ).toCreateMap()
        assertTrue(sponsorshipMap.containsKey("message"))
        assertTrue(sponsorshipMap.containsKey("paymentMode"))
        assertTrue(sponsorshipMap.containsKey("paymentStatus"))
        assertTrue(sponsorshipMap.containsKey("paymentRef"))
    }
}

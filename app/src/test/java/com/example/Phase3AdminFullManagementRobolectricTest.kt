package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.example.ui.screens.AdminProductsScreen
import com.example.ui.screens.CompostProductionScreen
import com.example.ui.screens.FarmerCooperationScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.Routes
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
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
class Phase3AdminFullManagementRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin_phase3",
        email = "admin@gosala.org",
        displayName = "गौशाला प्रशासक",
        role = UserRole.ADMIN
    )

    private val staffSession = UserSession(
        uid = "staff_phase3",
        email = "staff@gosala.org",
        displayName = "रवि कर्मचारी",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(
            AppModule.PRODUCTION.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
            AppModule.FARMERS.key to listOf(CrudAction.VIEW.name)
        )
    )

    private val publicSession = UserSession(
        uid = "pub_phase3",
        email = "bhakt@gosala.org",
        displayName = "राकेश भक्त",
        role = UserRole.PUBLIC
    )

    @Test
    fun `admin dashboard shows Admin Products tile and navigates to ADMIN_PRODUCTS`() {
        var navigatedRoute: String? = null
        val liveProducts = GoshalaProduct.defaultSeedProducts()

        composeRule.setContent {
            GosalaTheme {
                AdminDashboardScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    cattle = emptyList(),
                    health = emptyList(),
                    feedLogs = emptyList(),
                    staffAccounts = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    sponsorships = emptyList(),
                    compost = emptyList(),
                    productOrders = emptyList(),
                    products = liveProducts,
                    onNavigate = { navigatedRoute = it },
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_dashboard_root")
            .performScrollToNode(hasTestTag("admin_mod_products"))
        composeRule.onNodeWithTag("admin_mod_products").assertIsDisplayed().performClick()
        assertEquals(Routes.ADMIN_PRODUCTS, navigatedRoute)
    }

    @Test
    fun `admin adds new product with photo URI, category, price, stock, and unit`() {
        var savedProduct: GoshalaProduct? = null
        var savedIsEdit: Boolean? = null

        composeRule.setContent {
            GosalaTheme {
                AdminProductsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    productsState = DataUiState.Success(emptyList()),
                    onSaveProduct = { product, isEdit ->
                        savedProduct = product
                        savedIsEdit = isEdit
                    },
                    onDeleteProduct = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_products_empty_card").assertIsDisplayed()
        composeRule.onNodeWithTag("add_product_fab").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_product_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("product_name_input").performScrollTo().performTextInput("हवन गौ-काष्ठ पैक")
        composeRule.onNodeWithTag("product_desc_input").performScrollTo().performTextInput("शुद्ध देशी गाय के गोबर से निर्मित इको-फ्रेंडली लकड़ी")
        composeRule.onNodeWithTag("product_cat_fuel").performScrollTo().performClick()
        composeRule.onNodeWithTag("product_price_input").performScrollTo().performTextInput("240")
        composeRule.onNodeWithTag("product_stock_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("product_stock_input").performTextInput("120")
        composeRule.onNodeWithTag("product_unit_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("product_unit_input").performTextInput("10 किग्रा पैक")
        composeRule.onNodeWithTag("product_photo_uri_input").performScrollTo().performTextInput("content://media/picker/0/photo_gaukashtha")

        composeRule.onNodeWithTag("product_save_button").performClick()
        composeRule.waitForIdle()

        val p = savedProduct
        assertNotNull("Expected product to be saved", p)
        assertEquals(false, savedIsEdit)
        assertEquals("हवन गौ-काष्ठ पैक", p!!.nameHi)
        assertEquals("FUEL", p.categoryKey)
        assertEquals(240.0, p.priceInr, 0.01)
        assertEquals(120L, p.stockQuantity)
        assertEquals("10 किग्रा पैक", p.unitLabel)
        assertEquals("content://media/picker/0/photo_gaukashtha", p.photoUri)
        assertTrue(p.available)
    }

    @Test
    fun `admin edits product price and photo, and delete requires confirmation dialog`() {
        val existing = GoshalaProduct(
            id = "prod_compost_1",
            nameHi = "जैविक गोबर खाद",
            nameEn = "Organic Manure",
            categoryKey = "MANURE",
            descriptionHi = "शुद्ध जैविक खाद",
            priceInr = 350.0,
            stockQuantity = 80L,
            unitLabel = "50 किग्रा बैग",
            available = true,
            photoKey = "compost"
        )
        var updatedProduct: GoshalaProduct? = null
        var updatedIsEdit: Boolean? = null
        var deletedProduct: GoshalaProduct? = null

        composeRule.setContent {
            GosalaTheme {
                AdminProductsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    productsState = DataUiState.Success(listOf(existing)),
                    onSaveProduct = { p, isEdit ->
                        updatedProduct = p
                        updatedIsEdit = isEdit
                    },
                    onDeleteProduct = { deletedProduct = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Scroll card fully clear of the bottom FAB before tapping Edit
        composeRule.onNodeWithTag("admin_products_screen")
            .performScrollToNode(hasTestTag("admin_product_card_prod_compost_1"))
        composeRule.onNodeWithTag("admin_products_screen").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        // 1. Edit existing product price and photo URI
        composeRule.onNodeWithTag("admin_product_edit_prod_compost_1").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_product_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("product_price_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("product_price_input").performTextInput("425")
        composeRule.onNodeWithTag("product_photo_uri_input").performScrollTo().performTextInput("https://gosala.example/compost_new.jpg")
        composeRule.onNodeWithTag("product_save_button").performClick()
        composeRule.waitForIdle()

        val updated = updatedProduct
        assertNotNull("Expected product update callback to fire", updated)
        assertEquals(true, updatedIsEdit)
        assertEquals(425.0, updated!!.priceInr, 0.01)
        assertEquals("https://gosala.example/compost_new.jpg", updated.photoUri)

        // 2. Delete flow: Cancel keeps record intact, Confirm triggers onDeleteProduct
        composeRule.onNodeWithTag("admin_product_delete_prod_compost_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("रद्द करें").performClick()
        composeRule.waitForIdle()
        assertNull("Cancelling confirmation dialog must NOT delete product", deletedProduct)

        composeRule.onNodeWithTag("admin_product_delete_prod_compost_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("prod_compost_1", deletedProduct?.id)
    }

    @Test
    fun `public user dashboard live-syncs Admin product add, price update, and deletion`() {
        var liveCatalog by mutableStateOf(
            listOf(
                GoshalaProduct(
                    id = "prod_live_1",
                    nameHi = "नवीन गौ-धूप स्टिक",
                    categoryKey = "PUJA",
                    descriptionHi = "हर्बल धूपबत्ती",
                    priceInr = 95.0,
                    stockQuantity = 40L,
                    unitLabel = "1 पैकेट",
                    available = true,
                    photoKey = "dhoop"
                )
            )
        )

        composeRule.setContent {
            GosalaTheme {
                HomeScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    summary = PublicSummary(totalCattle = 45, healthyCattle = 44),
                    cattle = emptyList(),
                    feedLogs = emptyList(),
                    finance = emptyList(),
                    donations = emptyList(),
                    onNavigate = {},
                    onQuickAddCattle = {},
                    onQuickAddFeed = {},
                    onQuickAddExpense = {},
                    products = liveCatalog
                )
            }
        }
        composeRule.waitForIdle()

        val root = composeRule.onNodeWithTag("public_dashboard_root")
        root.performScrollToNode(hasTestTag("product_card_prod_live_1"))
        composeRule.onNodeWithTag("product_card_prod_live_1").assertIsDisplayed()
        composeRule.onNodeWithText("₹95 / 1 पैकेट", substring = true).assertIsDisplayed()

        // Admin updates price & adds a second product
        liveCatalog = listOf(
            liveCatalog.first().copy(priceInr = 125.0),
            GoshalaProduct(
                id = "prod_live_2",
                nameHi = "शुद्ध गोबर के कंडे",
                categoryKey = "FUEL",
                descriptionHi = "पूजा व हवन हेतु",
                priceInr = 180.0,
                stockQuantity = 100L,
                unitLabel = "25 नग",
                available = true,
                photoKey = "kande"
            )
        )
        composeRule.waitForIdle()

        composeRule.onNodeWithText("₹125 / 1 पैकेट", substring = true).assertIsDisplayed()
        root.performScrollToNode(hasTestTag("product_card_prod_live_2"))
        composeRule.onNodeWithTag("product_card_prod_live_2").assertIsDisplayed()

        // Admin deletes first product -> disappears from Public Dashboard
        liveCatalog = listOf(liveCatalog.last())
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("product_card_prod_live_1").assertDoesNotExist()
    }

    @Test
    fun `production management supports cow-dung product types, raw dung, staff, edit, and confirmed delete`() {
        var addedBatch: CompostBatch? = null
        var updatedBatch: CompostBatch? = null
        var deletedBatch: CompostBatch? = null

        val initialBatch = CompostBatch(
            id = "batch_1",
            batchCode = "CMP-2026-01",
            productType = "KANDE",
            quantity = 200.0,
            unit = "नग",
            rawDungKg = 350.0,
            stockRemaining = 180.0,
            sellingPricePerUnitInr = 5.0,
            dateStr = "2026-11-20",
            staffNames = "रामलाल, श्याम",
            status = "READY_FOR_SALE"
        )

        composeRule.setContent {
            GosalaTheme {
                CompostProductionScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    compostState = DataUiState.Success(listOf(initialBatch)),
                    onAddBatch = { addedBatch = it },
                    onRecordSale = { _, _ -> },
                    onAskAiCostAnalysis = {},
                    onUpdateBatch = { updatedBatch = it },
                    onDeleteBatch = { deletedBatch = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Add new GAU_KASHTHA batch with raw dung and staff names
        composeRule.onNodeWithTag("add_compost_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compost_type_gau_kashtha").performScrollTo().performClick()
        composeRule.onNodeWithTag("compost_raw_dung_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("compost_raw_dung_input").performTextInput("950")
        composeRule.onNodeWithTag("compost_qty_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("compost_qty_input").performTextInput("300")
        composeRule.onNodeWithTag("compost_staff_input").performScrollTo().performTextInput("महेश, दिनेश")
        composeRule.onNodeWithTag("compost_save_button").performClick()
        composeRule.waitForIdle()

        val added = addedBatch
        assertNotNull("Expected batch to be added", added)
        assertEquals("GAU_KASHTHA", added!!.productType)
        assertEquals(950.0, added.rawDungKg, 0.01)
        assertEquals(300.0, added.quantity, 0.01)
        assertEquals("महेश, दिनेश", added.staffNames)

        // Scroll card clear of bottom FAB before tapping Edit / Delete
        composeRule.onNodeWithTag("compost_production_list")
            .performScrollToNode(hasTestTag("compost_card_batch_1"))
        composeRule.onNodeWithTag("compost_production_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("compost_edit_batch_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("compost_staff_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("compost_staff_input").performTextInput("रामलाल, सुरेश")
        composeRule.onNodeWithTag("compost_save_button").performClick()
        composeRule.waitForIdle()
        assertEquals("रामलाल, सुरेश", updatedBatch?.staffNames)

        // Delete with confirmation dialog
        composeRule.onNodeWithTag("compost_delete_batch_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("batch_1", deletedBatch?.id)
    }

    @Test
    fun `farmer management supports suppliesOffered, transaction paymentStatus, edit, and confirmed delete`() {
        var addedFarmer: FarmerRecord? = null
        var updatedTx: FarmerTransaction? = null
        var deletedTxId: String? = null

        val sampleFarmer = FarmerRecord(
            id = "farmer_1",
            name = "हरिराम किसान",
            mobile = "9829001122",
            village = "बिन्जारी",
            suppliesOffered = "भूसा, हरा चारा"
        )
        val sampleTx = FarmerTransaction(
            id = "ftx_1",
            invoiceNumber = "FRM-2026-01",
            farmerId = "farmer_1",
            farmerName = "हरिराम किसान",
            village = "बिन्जारी",
            direction = "PURCHASE_FROM_FARMER",
            productName = "भूसा (Bhusa)",
            quantity = 10.0,
            unit = "QUINTAL",
            ratePerUnitInr = 600.0,
            totalAmountInr = 6000.0,
            paidAmountInr = 3000.0,
            outstandingInr = 3000.0,
            paymentStatus = "PARTIAL",
            dateStr = "2026-11-20"
        )

        composeRule.setContent {
            GosalaTheme {
                FarmerCooperationScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    farmersState = DataUiState.Success(listOf(sampleFarmer)),
                    farmerTxState = DataUiState.Success(listOf(sampleTx)),
                    onAddFarmer = { addedFarmer = it },
                    onAddFarmerTx = { _, _ -> },
                    onUpdateFarmer = {},
                    onDeleteFarmer = {},
                    onUpdateFarmerTx = { updatedTx = it },
                    onDeleteFarmerTx = { deletedTxId = it }
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Add farmer with suppliesOffered
        composeRule.onNodeWithTag("add_farmer_fab").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farmer_name_input").performScrollTo().performTextInput("रमेश गुर्जर")
        composeRule.onNodeWithTag("farmer_mobile_input").performScrollTo().performTextInput("9811223344")
        composeRule.onNodeWithTag("farmer_village_input").performScrollTo().performTextInput("टपूकड़ा")
        composeRule.onNodeWithTag("farmer_supplies_input").performScrollTo().performTextInput("बरसीम, सूखा चारा")
        composeRule.onNodeWithTag("farmer_save_button").performClick()
        composeRule.waitForIdle()

        val farmer = addedFarmer
        assertNotNull("Expected farmer to be added", farmer)
        assertEquals("रमेश गुर्जर", farmer!!.name)
        assertEquals("बरसीम, सूखा चारा", farmer.suppliesOffered)

        // 2. Switch to Invoices tab -> edit transaction paymentStatus to PAID and delete with confirmation
        composeRule.onNodeWithText("बिल व लेनदेन (1)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farmer_tx_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farmer_tx_edit_ftx_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("farmer_tx_pay_paid").performScrollTo().performClick()
        composeRule.onNodeWithTag("farmer_tx_save_button").performClick()
        composeRule.waitForIdle()
        assertEquals("PAID", updatedTx?.paymentStatus)

        composeRule.onNodeWithTag("farmer_tx_delete_ftx_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("ftx_1", deletedTxId)
    }

    @Test
    fun `admin applications screen shows full order detail, edits adoption plan, and opens cattle management`() {
        val sampleOrder = ProductOrder(
            id = "ord_p3_1",
            orderNumber = "JBGS-ORD-3001",
            buyerUid = publicSession.uid,
            buyerName = "राकेश भक्त",
            buyerPhone = "9876543210",
            buyerAddress = "भिवाड़ी",
            productId = "prod_1",
            productName = "जैविक गोबर खाद",
            unitLabel = "50 किग्रा बैग",
            unitPriceInr = 350.0,
            quantity = 2L,
            totalAmountInr = 700.0,
            orderStatus = "NEW",
            paymentMode = "UPI",
            paymentStatus = "PAID",
            paymentRef = "UPI-3001",
            dateStr = "2026-11-20"
        )
        val sampleAdoption = Sponsorship(
            id = "sp_p3_1",
            applicantUid = publicSession.uid,
            applicantName = "सुमन देवी",
            applicantPhone = "9829112233",
            applicantAddress = "अलवर",
            cattleId = "cow_1",
            cattleName = "गंगा गौमाता",
            animalCode = "JBGS-G-101",
            monthlySupportInr = 2100.0,
            durationMonths = 3L,
            totalSupportInr = 6300.0,
            status = "PENDING",
            paymentMode = "UPI",
            paymentStatus = "PAID",
            disclaimerAccepted = true,
            dateStr = "2026-11-20"
        )

        var deletedOrderId: String? = null
        var updatedAdoption: Sponsorship? = null
        var openedCattle = false

        composeRule.setContent {
            GosalaTheme {
                AdminApplicationsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    productOrdersState = DataUiState.Success(listOf(sampleOrder)),
                    sponsorshipsState = DataUiState.Success(listOf(sampleAdoption)),
                    onUpdateOrderStatus = { _, _, _, _ -> },
                    onDeleteOrder = { deletedOrderId = it },
                    onReviewSponsorship = { _, _ -> },
                    onVerifySponsorshipPayment = { _, _ -> },
                    onDeleteSponsorship = {},
                    onUpdateSponsorship = { updatedAdoption = it },
                    onOpenCattleManagement = { openedCattle = true }
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Open full order detail dialog and verify delete confirmation
        composeRule.onNodeWithTag("admin_orders_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_order_detail_btn_ord_p3_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_order_detail_dialog").assertIsDisplayed()
        composeRule.onNodeWithText("बंद करें").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_order_delete_ord_p3_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("confirm_dialog_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals("ord_p3_1", deletedOrderId)

        // 2. Switch to Cow Adoptions -> edit amount/duration/status and tap Cow Info
        composeRule.onNodeWithTag("admin_apps_tab_adoptions").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_adoptions_list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("admin_adoption_cattle_btn_sp_p3_1").performClick()
        assertTrue(openedCattle)

        composeRule.onNodeWithTag("admin_adoption_edit_sp_p3_1").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_adoption_edit_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_adoption_edit_amount").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("admin_adoption_edit_amount").performTextInput("3100")
        composeRule.onNodeWithTag("admin_adoption_edit_duration").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("admin_adoption_edit_duration").performTextInput("6")
        composeRule.onNodeWithTag("admin_adoption_edit_status_approved").performScrollTo().performClick()
        composeRule.onNodeWithTag("admin_adoption_edit_save").performClick()
        composeRule.waitForIdle()

        val updated = updatedAdoption
        assertNotNull("Expected adoption update callback", updated)
        assertEquals(3100.0, updated!!.monthlySupportInr, 0.01)
        assertEquals(6L, updated.durationMonths)
        assertEquals(18600.0, updated.totalSupportInr, 0.01)
        assertEquals("APPROVED", updated.status)
    }

    @Test
    fun `role separation strictly blocks Staff and Public User from AdminProductsScreen`() {
        assertTrue(AuthNavigationController.canAccessRoute(adminSession, Routes.ADMIN_PRODUCTS))
        assertFalse(AuthNavigationController.canAccessRoute(staffSession, Routes.ADMIN_PRODUCTS))
        assertFalse(AuthNavigationController.canAccessRoute(publicSession, Routes.ADMIN_PRODUCTS))

        composeRule.setContent {
            GosalaTheme {
                AdminProductsScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    productsState = DataUiState.Success(GoshalaProduct.defaultSeedProducts()),
                    onSaveProduct = { _, _ -> },
                    onDeleteProduct = {}
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_products_access_denied").assertIsDisplayed()
    }

    @Test
    fun `Phase 3 model schemas and Hindi labels match specification`() {
        assertEquals(6, GoshalaProduct.defaultSeedProducts().size)
        assertEquals("जैविक खाद", productCategoryLabel("MANURE", true))
        assertEquals("कंडे व गौ-काष्ठ", productCategoryLabel("FUEL", true))
        assertEquals("धूपबत्ती व पूजा", productCategoryLabel("PUJA", true))
        assertEquals("अन्य उत्पाद", productCategoryLabel("OTHER", true))

        assertEquals("गोबर के कंडे", productionProductTypeLabel("KANDE", true))
        assertEquals("गोबर की लकड़ी (गौ-काष्ठ)", productionProductTypeLabel("GAU_KASHTHA", true))
        assertEquals("प्राकृतिक धूपबत्ती", productionProductTypeLabel("DHOOP", true))

        assertEquals("पूर्ण भुगतान (Paid)", farmerPaymentStatusLabel("PAID", true))
        assertEquals("आंशिक (Partial)", farmerPaymentStatusLabel("PARTIAL", true))
        assertEquals("बकाया (Pending)", farmerPaymentStatusLabel("PENDING", true))

        val prodMap = GoshalaProduct(
            nameHi = "जैविक खाद",
            categoryKey = "MANURE",
            descriptionHi = "शुद्ध गोबर खाद",
            priceInr = 350.0,
            stockQuantity = 50L,
            unitLabel = "50 किग्रा बैग"
        ).toCreateMap()
        assertTrue(prodMap.containsKey("nameHi"))
        assertTrue(prodMap.containsKey("priceInr"))
        assertTrue(prodMap.containsKey("stockQuantity"))
        assertTrue(prodMap.containsKey("unitLabel"))
        assertTrue(prodMap.containsKey("available"))
        assertTrue(prodMap.containsKey("photoKey"))
        assertTrue(prodMap.containsKey("photoUri"))
    }
}

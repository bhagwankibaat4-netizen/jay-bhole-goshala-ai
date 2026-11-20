package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.str
import com.example.data.model.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

@Composable
fun AdminApplicationsScreen(
    language: AppLanguage,
    session: UserSession,
    productOrdersState: DataUiState<List<ProductOrder>>,
    sponsorshipsState: DataUiState<List<Sponsorship>>,
    onUpdateOrderStatus: (ProductOrder, String, String, String) -> Unit,
    onDeleteOrder: (String) -> Unit,
    onReviewSponsorship: (Sponsorship, Boolean) -> Unit,
    onVerifySponsorshipPayment: (Sponsorship, String) -> Unit,
    onDeleteSponsorship: (String) -> Unit
) {
    if (session.role != UserRole.ADMIN) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("admin_applications_access_denied"),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Default.Lock,
                title = str(language, "केवल प्रशासक हेतु", "Admin Access Only"),
                subtitle = str(
                    language,
                    "उत्पाद खरीद एवं गाय गोद आवेदन प्रबंधन केवल गौशाला प्रशासक (Admin) के लिए उपलब्ध है।",
                    "Product purchase and cow adoption application management is restricted to Goshala Admins."
                )
            )
        }
        return
    }

    var tabIndex by remember { mutableIntStateOf(0) }
    var noteEditingOrder by remember { mutableStateOf<ProductOrder?>(null) }
    var deletingOrder by remember { mutableStateOf<ProductOrder?>(null) }
    var certSponsorship by remember { mutableStateOf<Sponsorship?>(null) }
    var deletingSponsorship by remember { mutableStateOf<Sponsorship?>(null) }

    val ordersCount = (productOrdersState as? DataUiState.Success)?.data?.count { it.orderStatus == "NEW" } ?: 0
    val pendingAdoptionsCount = (sponsorshipsState as? DataUiState.Success)?.data?.count { it.status == "PENDING" } ?: 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("admin_applications_screen")
    ) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = {
                    Text(
                        text = if (ordersCount > 0) {
                            str(language, "उत्पाद खरीद ($ordersCount)", "Product Orders ($ordersCount)")
                        } else {
                            str(language, "उत्पाद खरीद आवेदन", "Product Orders")
                        }
                    )
                },
                modifier = Modifier.testTag("admin_apps_tab_orders")
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = {
                    Text(
                        text = if (pendingAdoptionsCount > 0) {
                            str(language, "गाय गोद ($pendingAdoptionsCount)", "Cow Adoptions ($pendingAdoptionsCount)")
                        } else {
                            str(language, "गाय गोद आवेदन", "Cow Adoptions")
                        }
                    )
                },
                modifier = Modifier.testTag("admin_apps_tab_adoptions")
            )
        }
        Spacer(Modifier.height(10.dp))

        if (tabIndex == 0) {
            AdminProductOrdersTab(
                language = language,
                ordersState = productOrdersState,
                onUpdateOrderStatus = onUpdateOrderStatus,
                onEditAdminNote = { noteEditingOrder = it },
                onRequestDelete = { deletingOrder = it }
            )
        } else {
            AdminCowAdoptionsTab(
                language = language,
                sponsorshipsState = sponsorshipsState,
                onReviewSponsorship = onReviewSponsorship,
                onVerifySponsorshipPayment = onVerifySponsorshipPayment,
                onOpenCertificate = { certSponsorship = it },
                onRequestDelete = { deletingSponsorship = it }
            )
        }
    }

    noteEditingOrder?.let { order ->
        var noteText by remember(order.id) { mutableStateOf(order.adminNote) }
        AlertDialog(
            onDismissRequest = { noteEditingOrder = null },
            title = { Text(str(language, "गौशाला संदेश जोड़ें", "Add Goshala Note"), fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(str(language, "आवेदक हेतु संदेश (जैसे डिलीवरी समय/संपर्क)", "Note for buyer (e.g. pickup/delivery time)")) },
                    modifier = Modifier.fillMaxWidth().testTag("admin_order_note_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateOrderStatus(order, order.orderStatus, order.paymentStatus, noteText.trim())
                        noteEditingOrder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("admin_order_note_save")
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = {
                TextButton(onClick = { noteEditingOrder = null }) { Text(str(language, "रद्द करें", "Cancel")) }
            }
        )
    }

    deletingOrder?.let { order ->
        ConfirmActionDialog(
            title = str(language, "उत्पाद ऑर्डर हटाएं", "Delete Product Order"),
            message = str(
                language,
                "क्या आप वाकई ${order.orderNumber} (${order.buyerName} — ${order.productName}) को हटाना चाहते हैं?",
                "Are you sure you want to delete ${order.orderNumber} (${order.buyerName} — ${order.productName})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteOrder(order.id)
                deletingOrder = null
            },
            onDismiss = { deletingOrder = null }
        )
    }

    certSponsorship?.let { sp ->
        AlertDialog(
            onDismissRequest = { certSponsorship = null },
            title = { Text(str(language, "गौ-गोद प्रायोजन प्रमाणपत्र", "Cow Sponsorship Certificate"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(GosalaBrand.NAME_HI, fontWeight = FontWeight.Bold, color = ForestGreen)
                    HorizontalDivider()
                    Text("${str(language, "प्रमाणपत्र क्र.", "Certificate No.")}: ${sp.certificateNumber.ifBlank { "-" }}")
                    Text("${str(language, "प्रायोजक", "Sponsor")}: ${sp.applicantName}")
                    Text("${str(language, "गौवंश", "Cow")}: ${sp.cattleName} (${sp.animalCode})", fontWeight = FontWeight.Bold)
                    Text("${str(language, "सहयोग", "Support")}: ₹${sp.monthlySupportInr.toInt()}/माह × ${sp.durationMonths} माह = ₹${sp.totalSupportInr.toInt()}")
                }
            },
            confirmButton = {
                TextButton(onClick = { certSponsorship = null }) { Text(str(language, "बंद करें", "Close")) }
            }
        )
    }

    deletingSponsorship?.let { sp ->
        ConfirmActionDialog(
            title = str(language, "गौ-गोद आवेदन हटाएं", "Delete Adoption Application"),
            message = str(
                language,
                "क्या आप वाकई ${sp.applicantName} का ${sp.cattleName} हेतु गौ-गोद आवेदन हटाना चाहते हैं?",
                "Are you sure you want to delete ${sp.applicantName}'s adoption application for ${sp.cattleName}?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteSponsorship(sp.id)
                deletingSponsorship = null
            },
            onDismiss = { deletingSponsorship = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminProductOrdersTab(
    language: AppLanguage,
    ordersState: DataUiState<List<ProductOrder>>,
    onUpdateOrderStatus: (ProductOrder, String, String, String) -> Unit,
    onEditAdminNote: (ProductOrder) -> Unit,
    onRequestDelete: (ProductOrder) -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    var selectedFilter by remember { mutableStateOf("ALL") }

    val allOrders = (ordersState as? DataUiState.Success)?.data.orEmpty()
    val filteredOrders = remember(allOrders, selectedFilter) {
        if (selectedFilter == "ALL") allOrders else allOrders.filter { it.orderStatus == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier.testTag("admin_orders_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("${str(language, "सभी", "All")} (${allOrders.size})") },
                    modifier = Modifier.testTag("admin_order_filter_all")
                )
                ProductOrder.ALL_ORDER_STATUSES.forEach { st ->
                    val count = allOrders.count { it.orderStatus == st }
                    FilterChip(
                        selected = selectedFilter == st,
                        onClick = { selectedFilter = st },
                        label = { Text("${productOrderStatusLabel(st, isHindi)} ($count)") },
                        modifier = Modifier.testTag("admin_order_filter_${st.lowercase()}")
                    )
                }
            }
        }

        when (ordersState) {
            is DataUiState.Loading -> item { CircularProgressIndicator(color = ForestGreen) }
            is DataUiState.Error -> item { EmptyStateCard(Icons.Default.ErrorOutline, "Error", ordersState.message) }
            is DataUiState.Success -> {
                if (filteredOrders.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.ShoppingBag,
                            title = str(language, "कोई उत्पाद खरीद आवेदन नहीं", "No Product Orders"),
                            subtitle = str(language, "पब्लिक यूज़र द्वारा भेजे गए उत्पाद आवेदन यहाँ दिखेंगे।", "Product orders submitted by public users appear here.")
                        )
                    }
                } else {
                    items(filteredOrders, key = { it.id.ifBlank { it.orderNumber } }) { order ->
                        val statusColor = when (order.orderStatus) {
                            "COMPLETED" -> GoshalaGreen
                            "APPROVED", "READY", "DELIVERY" -> ForestGreen
                            "CANCELLED" -> AlertRed
                            else -> Saffron
                        }
                        val payColor = when (order.paymentStatus) {
                            "VERIFIED" -> GoshalaGreen
                            "PAID" -> Color(0xFF1565C0)
                            else -> Saffron
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_order_card_${order.id.ifBlank { order.orderNumber }}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${order.orderNumber} • ${order.dateStr}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = productOrderStatusLabel(order.orderStatus, isHindi),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${order.productName} × ${order.quantity} (${order.unitLabel}) — ₹${order.totalAmountInr.toInt()}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                                Text(
                                    text = "${str(language, "ग्राहक", "Buyer")}: ${order.buyerName} (${order.buyerPhone})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${str(language, "पता", "Address")}: ${order.buyerAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (order.notes.isNotBlank()) {
                                    Text(
                                        text = "${str(language, "टिप्पणी", "Buyer Note")}: ${order.notes}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Payment info row
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = payColor.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${str(language, "भुगतान", "Payment")}: ${paymentModeLabel(order.paymentMode, isHindi)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            Text(
                                                text = paymentStatusLabel(order.paymentStatus, isHindi),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = payColor
                                            )
                                        }
                                        if (order.paymentRef.isNotBlank()) {
                                            Text(
                                                text = "${str(language, "संदर्भ", "Ref")}: ${order.paymentRef}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DarkText
                                            )
                                        }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ProductOrder.ALL_PAYMENT_STATUSES.forEach { pStatus ->
                                                FilterChip(
                                                    selected = order.paymentStatus == pStatus,
                                                    onClick = {
                                                        if (order.paymentStatus != pStatus) {
                                                            onUpdateOrderStatus(order, order.orderStatus, pStatus, order.adminNote)
                                                        }
                                                    },
                                                    label = { Text(paymentStatusLabel(pStatus, isHindi), style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag(
                                                        if (pStatus == "VERIFIED") "admin_order_verify_pay_${order.id}" else "admin_order_pay_${order.id}_${pStatus.lowercase()}"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                // Order Status Workflow Chips (All 6 Stages)
                                Text(
                                    text = str(language, "ऑर्डर स्थिति बदलें:", "Update Order Status:"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    ProductOrder.ALL_ORDER_STATUSES.forEach { st ->
                                        FilterChip(
                                            selected = order.orderStatus == st,
                                            onClick = {
                                                if (order.orderStatus != st) {
                                                    onUpdateOrderStatus(order, st, order.paymentStatus, order.adminNote)
                                                }
                                            },
                                            label = { Text(productOrderStatusLabel(st, isHindi), style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.testTag("admin_order_status_${order.id}_$st")
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (order.orderStatus == "NEW") {
                                        Button(
                                            onClick = { onUpdateOrderStatus(order, "APPROVED", order.paymentStatus, order.adminNote) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen),
                                            modifier = Modifier.testTag("admin_order_quick_approve_${order.id}")
                                        ) { Text(str(language, "स्वीकार करें", "Accept")) }
                                        TextButton(
                                            onClick = { onUpdateOrderStatus(order, "CANCELLED", order.paymentStatus, order.adminNote) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                                            modifier = Modifier.testTag("admin_order_quick_reject_${order.id}")
                                        ) { Text(str(language, "अस्वीकार / रद्द", "Reject")) }
                                    }
                                    Spacer(Modifier.weight(1f))
                                    IconButton(
                                        onClick = { onEditAdminNote(order) },
                                        modifier = Modifier.testTag("admin_order_note_btn_${order.id}")
                                    ) {
                                        Icon(Icons.Default.EditNote, contentDescription = str(language, "संदेश जोड़ें", "Add Note"), tint = ForestGreen)
                                    }
                                    IconButton(
                                        onClick = { onRequestDelete(order) },
                                        modifier = Modifier.testTag("admin_order_delete_${order.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = str(language, "हटाएं", "Delete"), tint = AlertRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminCowAdoptionsTab(
    language: AppLanguage,
    sponsorshipsState: DataUiState<List<Sponsorship>>,
    onReviewSponsorship: (Sponsorship, Boolean) -> Unit,
    onVerifySponsorshipPayment: (Sponsorship, String) -> Unit,
    onOpenCertificate: (Sponsorship) -> Unit,
    onRequestDelete: (Sponsorship) -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    var selectedFilter by remember { mutableStateOf("ALL") }

    val allApps = (sponsorshipsState as? DataUiState.Success)?.data.orEmpty()
    val filteredApps = remember(allApps, selectedFilter) {
        if (selectedFilter == "ALL") allApps else allApps.filter { it.status == selectedFilter }
    }

    LazyColumn(
        modifier = Modifier.testTag("admin_adoptions_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to str(language, "सभी", "All"),
                    "PENDING" to str(language, "लंबित", "Pending"),
                    "APPROVED" to str(language, "स्वीकृत", "Approved"),
                    "REJECTED" to str(language, "अस्वीकृत", "Rejected")
                ).forEach { (key, label) ->
                    val count = if (key == "ALL") allApps.size else allApps.count { it.status == key }
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text("$label ($count)") },
                        modifier = Modifier.testTag("admin_adoption_filter_${key.lowercase()}")
                    )
                }
            }
        }

        when (sponsorshipsState) {
            is DataUiState.Loading -> item { CircularProgressIndicator(color = ForestGreen) }
            is DataUiState.Error -> item { EmptyStateCard(Icons.Default.ErrorOutline, "Error", sponsorshipsState.message) }
            is DataUiState.Success -> {
                if (filteredApps.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Favorite,
                            title = str(language, "कोई गाय गोद आवेदन नहीं", "No Cow Adoption Applications"),
                            subtitle = str(language, "पब्लिक यूज़र द्वारा भेजे गए गौ-गोद आवेदन यहाँ दिखेंगे।", "Cow adoption applications will appear here.")
                        )
                    }
                } else {
                    items(filteredApps, key = { it.id }) { sp ->
                        val statusColor = when (sp.status) {
                            "APPROVED" -> GoshalaGreen
                            "REJECTED" -> AlertRed
                            else -> Saffron
                        }
                        val payColor = when (sp.paymentStatus) {
                            "VERIFIED" -> GoshalaGreen
                            "PAID" -> Color(0xFF1565C0)
                            else -> Saffron
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("admin_adoption_card_${sp.id}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${sp.cattleName} (${sp.animalCode})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ForestGreen
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = sp.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${str(language, "आवेदक", "Applicant")}: ${sp.applicantName} (${sp.applicantPhone})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${str(language, "पता", "Address")}: ${sp.applicantAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${str(language, "अवधि व राशि", "Duration & Amount")}: ₹${sp.monthlySupportInr.toInt()}/माह × ${sp.durationMonths} माह = ₹${sp.totalSupportInr.toInt()}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                if (sp.message.isNotBlank()) {
                                    Text(
                                        text = "${str(language, "संदेश", "Message")}: ${sp.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = payColor.copy(alpha = 0.10f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${str(language, "भुगतान", "Payment")}: ${paymentModeLabel(sp.paymentMode, isHindi)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = paymentStatusLabel(sp.paymentStatus, isHindi),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = payColor
                                            )
                                        }
                                        if (sp.paymentRef.isNotBlank()) {
                                            Text(
                                                text = "${str(language, "संदर्भ", "Ref")}: ${sp.paymentRef}",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ProductOrder.ALL_PAYMENT_STATUSES.forEach { pStatus ->
                                                FilterChip(
                                                    selected = sp.paymentStatus == pStatus,
                                                    onClick = {
                                                        if (sp.paymentStatus != pStatus) {
                                                            onVerifySponsorshipPayment(sp, pStatus)
                                                        }
                                                    },
                                                    label = { Text(paymentStatusLabel(pStatus, isHindi), style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.testTag(
                                                        if (pStatus == "VERIFIED") "admin_adoption_verify_pay_${sp.id}" else "admin_adoption_pay_${sp.id}_${pStatus.lowercase()}"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (sp.status == "PENDING") {
                                        Button(
                                            onClick = { onReviewSponsorship(sp, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen),
                                            modifier = Modifier.testTag("admin_adoption_approve_${sp.id}")
                                        ) { Text(str(language, "स्वीकार करें", "Accept")) }
                                        TextButton(
                                            onClick = { onReviewSponsorship(sp, false) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                                            modifier = Modifier.testTag("admin_adoption_reject_${sp.id}")
                                        ) { Text(str(language, "अस्वीकार करें", "Reject")) }
                                    }
                                    if (sp.status == "APPROVED") {
                                        OutlinedButton(onClick = { onOpenCertificate(sp) }) {
                                            Text(str(language, "प्रमाणपत्र", "Certificate"))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRequestDelete(sp) },
                                        modifier = Modifier.testTag("admin_adoption_delete_${sp.id}")
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = str(language, "हटाएं", "Delete"), tint = AlertRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

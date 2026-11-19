package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

@Composable
fun FarmerCooperationScreen(
    language: AppLanguage,
    session: UserSession,
    farmersState: DataUiState<List<FarmerRecord>>,
    farmerTxState: DataUiState<List<FarmerTransaction>>,
    onAddFarmer: (FarmerRecord) -> Unit,
    onAddFarmerTx: (FarmerTransaction, FarmerRecord) -> Unit,
    onUpdateFarmer: (FarmerRecord) -> Unit = {},
    onDeleteFarmer: (FarmerRecord) -> Unit = {}
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var showAddFarmerDialog by remember { mutableStateOf(false) }
    var editingFarmer by remember { mutableStateOf<FarmerRecord?>(null) }
    var deletingFarmer by remember { mutableStateOf<FarmerRecord?>(null) }
    var selectedFarmerForTx by remember { mutableStateOf<FarmerRecord?>(null) }

    val farmers = (farmersState as? DataUiState.Success)?.data.orEmpty()
    val transactions = (farmerTxState as? DataUiState.Success)?.data.orEmpty()

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.FARMERS)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingFarmer = null
                        showAddFarmerDialog = true
                    },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_farmer_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "किसान जोड़ें", "Add Farmer"))
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // Notice: No Farmer Login
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SoftGoldTint),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Agriculture, contentDescription = null, tint = ForestGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = str(
                            language,
                            "किसान–गौशाला सहयोग (केवल गौशाला स्टाफ/प्रशासक द्वारा प्रबंधित, किसान लॉगिन नहीं)",
                            "Farmer–Goshala Trade (Goshala-managed directory & ledger, no farmer login)"
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val totalPurchase = transactions.filter { it.direction == "PURCHASE_FROM_FARMER" }.sumOf { it.totalAmountInr }
                val totalSale = transactions.filter { it.direction == "SALE_TO_FARMER" }.sumOf { it.totalAmountInr }
                MetricStatCard(
                    title = str(language, "किसान से खरीद", "Purchased"),
                    value = "₹${totalPurchase.toInt()}",
                    subtitle = str(language, "चारा / भूसा / बरसीम", "Fodder & Bhusa"),
                    icon = Icons.Default.ShoppingBasket,
                    accentColor = Saffron,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = str(language, "किसान को बिक्री", "Sold"),
                    value = "₹${totalSale.toInt()}",
                    subtitle = str(language, "खाद व पौधे", "Compost & Plants"),
                    icon = Icons.Default.Sell,
                    accentColor = GoshalaGreen,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text(str(language, "किसान खाता (${farmers.size})", "Farmers (${farmers.size})")) })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text(str(language, "बिल व लेनदेन (${transactions.size})", "Invoices (${transactions.size})")) })
            }

            Spacer(Modifier.height(10.dp))

            if (tabIndex == 0) {
                when (farmersState) {
                    is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                    is DataUiState.Error -> EmptyStateCard(
                        icon = Icons.Default.ErrorOutline,
                        title = str(language, "किसान डेटा लोड नहीं हो सका", "Could not load farmers"),
                        subtitle = farmersState.message
                    )
                    is DataUiState.Success -> if (farmers.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.Agriculture,
                            title = str(language, "कोई किसान पंजीकृत नहीं", "No farmers registered"),
                            subtitle = str(language, "चारा आपूर्तिकर्ता या खाद खरीदार किसान का नाम, गाँव व मोबाइल जोड़ें।", "Register local farmers as fodder suppliers or compost buyers.")
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.testTag("farmers_list"),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                        items(farmers, key = { it.id }) { farmer ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(farmer.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text("${farmer.village} • ${farmer.mobile}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (session.canAdd(AppModule.FARMERS)) {
                                            FilledTonalButton(onClick = { selectedFarmerForTx = farmer }) {
                                                Text(str(language, "+ बिल/लेनदेन", "+ Invoice"))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "${str(language, "खरीद", "Bought")}: ₹${farmer.totalPurchasedInr.toInt()} | ${str(language, "बिक्री", "Sold")}: ₹${farmer.totalSoldInr.toInt()} | ${str(language, "बकाया", "Due")}: ₹${farmer.outstandingInr.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (farmer.outstandingInr > 0) AlertRed else GoshalaGreen,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (session.canEdit(AppModule.FARMERS) || session.canDelete(AppModule.FARMERS)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Start,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (session.canEdit(AppModule.FARMERS)) {
                                                IconButton(
                                                    onClick = {
                                                        editingFarmer = farmer
                                                        showAddFarmerDialog = false
                                                    },
                                                    modifier = Modifier.testTag("farmer_edit_${farmer.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = str(language, "संपादित करें", "Edit"),
                                                        tint = ForestGreen
                                                    )
                                                }
                                            }
                                            if (session.canDelete(AppModule.FARMERS)) {
                                                IconButton(
                                                    onClick = { deletingFarmer = farmer },
                                                    modifier = Modifier.testTag("farmer_delete_${farmer.id}")
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = str(language, "हटाएं", "Delete"),
                                                        tint = AlertRed
                                                    )
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
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Invoice", "Date", "Farmer", "Direction", "Product", "Qty", "Total", "Due")
                            val rows = transactions.map {
                                listOf(it.invoiceNumber, it.dateStr, it.farmerName, it.direction, it.productName, "${it.quantity} ${it.unit}", "₹${it.totalAmountInr}", "₹${it.outstandingInr}")
                            }
                            QrAndExportUtils.exportPdfReport(
                                context, "farmer_trade", str(language, "किसान-गौशाला व्यापार रिपोर्ट", "Farmer Trade Report"),
                                "Jay Bhole Gosala Samiti", headers, rows
                            ).onSuccess { f -> QrAndExportUtils.shareFile(context, f, "application/pdf", "Share Farmer PDF") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("PDF")
                    }
                    OutlinedButton(
                        onClick = {
                            val headers = listOf("Invoice", "Date", "Farmer", "Village", "Direction", "Product", "Qty", "Unit", "Rate", "Total", "Paid", "Due")
                            val rows = transactions.map {
                                listOf(it.invoiceNumber, it.dateStr, it.farmerName, it.village, it.direction, it.productName, it.quantity.toString(), it.unit, it.ratePerUnitInr.toString(), it.totalAmountInr.toString(), it.paidAmountInr.toString(), it.outstandingInr.toString())
                            }
                            QrAndExportUtils.exportExcelCsvReport(context, "farmer_trade", headers, rows)
                                .onSuccess { f -> QrAndExportUtils.shareFile(context, f, "text/csv", "Share Farmer Excel CSV") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Excel")
                    }
                }
                Spacer(Modifier.height(8.dp))

                when (farmerTxState) {
                    is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                    is DataUiState.Error -> EmptyStateCard(
                        icon = Icons.Default.ErrorOutline,
                        title = str(language, "लेनदेन लोड नहीं हो सके", "Could not load invoices"),
                        subtitle = farmerTxState.message
                    )
                    is DataUiState.Success -> if (transactions.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.Receipt,
                            title = str(language, "कोई किसान लेनदेन नहीं", "No farmer invoices yet"),
                            subtitle = str(language, "किसान सूची से '+ बिल/लेनदेन' चुनकर खरीद या बिक्री बिल बनाएं।", "Select '+ Invoice' on any farmer record to create a purchase or sale bill.")
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                        items(transactions, key = { it.id }) { tx ->
                            val isPurchase = tx.direction == "PURCHASE_FROM_FARMER"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${tx.invoiceNumber} • ${tx.farmerName}", fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (isPurchase) str(language, "खरीद", "Purchase") else str(language, "बिक्री", "Sale"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isPurchase) Saffron else GoshalaGreen,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text("${tx.productName} (${tx.quantity} ${tx.unit} @ ₹${tx.ratePerUnitInr}) • Grade ${tx.qualityGrade}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${tx.dateStr} | कुल: ₹${tx.totalAmountInr.toInt()} | भुगतान: ₹${tx.paidAmountInr.toInt()} | बकाया: ₹${tx.outstandingInr.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (tx.outstandingInr > 0) AlertRed else ForestGreen
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    if (showAddFarmerDialog || editingFarmer != null) {
        val currentEdit = editingFarmer
        val isEdit = currentEdit != null
        var name by remember(currentEdit?.id) { mutableStateOf(currentEdit?.name ?: "") }
        var mobile by remember(currentEdit?.id) { mutableStateOf(currentEdit?.mobile ?: "") }
        var village by remember(currentEdit?.id) { mutableStateOf(currentEdit?.village ?: "") }
        var address by remember(currentEdit?.id) { mutableStateOf(currentEdit?.address ?: "") }
        var coopType by remember(currentEdit?.id) { mutableStateOf(currentEdit?.cooperationType ?: "BOTH") }
        var farmerError by remember(currentEdit?.id) { mutableStateOf<String?>(null) }

        val closeDialog = {
            showAddFarmerDialog = false
            editingFarmer = null
        }

        AlertDialog(
            onDismissRequest = closeDialog,
            title = {
                Text(
                    if (isEdit) str(language, "किसान विवरण संपादित करें", "Edit Farmer Details")
                    else str(language, "नया किसान पंजीकरण", "Register Farmer"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    farmerError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    OutlinedTextField(value = name, onValueChange = { name = it; farmerError = null }, label = { Text(str(language, "किसान का नाम *", "Farmer Name *")) }, singleLine = true)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it; farmerError = null }, label = { Text(str(language, "मोबाइल नंबर *", "Mobile *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    OutlinedTextField(value = village, onValueChange = { village = it; farmerError = null }, label = { Text(str(language, "गाँव *", "Village *")) }, singleLine = true)
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(str(language, "पूरा पता", "Address")) })
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("SUPPLIER" to "आपूर्तिकर्ता", "BUYER" to "खरीदार", "BOTH" to "दोनों").forEach { (k, l) ->
                            FilterChip(selected = coopType == k, onClick = { coopType = k }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || mobile.trim().length < 6 || village.isBlank()) {
                            farmerError = str(language, "कृपया नाम, गाँव और सही मोबाइल नंबर दर्ज करें।", "Please enter name, village, and valid mobile number.")
                            return@Button
                        }
                        if (currentEdit != null) {
                            onUpdateFarmer(
                                currentEdit.copy(
                                    name = name.trim(),
                                    mobile = mobile.trim(),
                                    village = village.trim(),
                                    address = address.trim(),
                                    cooperationType = coopType
                                )
                            )
                        } else {
                            onAddFarmer(FarmerRecord(name = name.trim(), mobile = mobile.trim(), village = village.trim(), address = address.trim(), cooperationType = coopType))
                        }
                        closeDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("farmer_save_button")
                ) { Text(if (isEdit) str(language, "अपडेट करें", "Update") else str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = closeDialog) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    deletingFarmer?.let { target ->
        ConfirmActionDialog(
            title = str(language, "किसान रिकॉर्ड हटाएं", "Delete Farmer Record"),
            message = str(
                language,
                "क्या आप वाकई ${target.name} (${target.village}) का रिकॉर्ड हटाना चाहते हैं?",
                "Are you sure you want to delete farmer ${target.name} (${target.village})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteFarmer(target)
                deletingFarmer = null
            },
            onDismiss = { deletingFarmer = null }
        )
    }

    selectedFarmerForTx?.let { farmer ->
        var direction by remember { mutableStateOf("PURCHASE_FROM_FARMER") }
        var product by remember { mutableStateOf("भूसा (Bhusa)") }
        var qty by remember { mutableStateOf("10") }
        var unit by remember { mutableStateOf("QUINTAL") }
        var quality by remember { mutableStateOf("A-Grade") }
        var rate by remember { mutableStateOf("550") }
        var paid by remember { mutableStateOf("5500") }
        var notes by remember { mutableStateOf("") }
        var txError by remember { mutableStateOf<String?>(null) }

        val purchaseProducts = listOf("भूसा (Bhusa)", "हरा चारा", "बरसीम", "मक्का", "सूखी घास")
        val saleProducts = listOf("गोबर खाद", "वर्मी कम्पोस्ट", "जैविक खाद", "नर्सरी पौधे")

        AlertDialog(
            onDismissRequest = { selectedFarmerForTx = null },
            title = { Text("${farmer.name} — ${str(language, "नया बिल", "New Invoice")}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    txError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(selected = direction == "PURCHASE_FROM_FARMER", onClick = { direction = "PURCHASE_FROM_FARMER"; product = purchaseProducts.first() }, label = { Text(str(language, "किसान से खरीद", "Buy Fodder")) })
                        FilterChip(selected = direction == "SALE_TO_FARMER", onClick = { direction = "SALE_TO_FARMER"; product = saleProducts.first() }, label = { Text(str(language, "किसान को बिक्री", "Sell Product")) })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (if (direction == "PURCHASE_FROM_FARMER") purchaseProducts else saleProducts).forEach { p ->
                            FilterChip(selected = product == p, onClick = { product = p }, label = { Text(p, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = product, onValueChange = { product = it; txError = null }, label = { Text(str(language, "उत्पाद विवरण *", "Product *")) }, singleLine = true)
                    OutlinedTextField(value = qty, onValueChange = { qty = it; txError = null }, label = { Text(str(language, "मात्रा *", "Quantity *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(str(language, "इकाई (QUINTAL/KG/BAG)", "Unit")) }, singleLine = true)
                    OutlinedTextField(value = quality, onValueChange = { quality = it }, label = { Text(str(language, "गुणवत्ता", "Quality")) }, singleLine = true)
                    OutlinedTextField(value = rate, onValueChange = { rate = it; txError = null }, label = { Text(str(language, "तय दर (₹) *", "Rate (₹) *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text(str(language, "भुगतान राशि (₹)", "Paid Amount (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(str(language, "टिप्पणी", "Notes")) })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val r = rate.toDoubleOrNull() ?: 0.0
                        if (q <= 0 || product.isBlank()) {
                            txError = str(language, "कृपया उत्पाद विवरण और सही मात्रा (>0) दर्ज करें।", "Please enter product and valid quantity (>0).")
                            return@Button
                        }
                        val total = q * r
                        onAddFarmerTx(
                            FarmerTransaction(
                                farmerId = farmer.id,
                                farmerName = farmer.name,
                                village = farmer.village,
                                direction = direction,
                                productName = product.trim(),
                                quantity = q,
                                unit = unit.trim().ifBlank { "KG" },
                                qualityGrade = quality.trim().ifBlank { "A" },
                                ratePerUnitInr = r,
                                totalAmountInr = total,
                                paidAmountInr = paid.toDoubleOrNull() ?: 0.0,
                                dateStr = QrAndExportUtils.todayIso(),
                                notes = notes.trim()
                            ),
                            farmer
                        )
                        selectedFarmerForTx = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "बिल सहेजें", "Save Invoice")) }
            },
            dismissButton = { TextButton(onClick = { selectedFarmerForTx = null }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }
}

@Composable
fun CompostProductionScreen(
    language: AppLanguage,
    session: UserSession,
    compostState: DataUiState<List<CompostBatch>>,
    onAddBatch: (CompostBatch) -> Unit,
    onRecordSale: (CompostBatch, Double) -> Unit,
    onAskAiCostAnalysis: () -> Unit,
    onUpdateBatch: (CompostBatch) -> Unit = {},
    onDeleteBatch: (CompostBatch) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<CompostBatch?>(null) }
    var deletingBatch by remember { mutableStateOf<CompostBatch?>(null) }
    var sellBatch by remember { mutableStateOf<CompostBatch?>(null) }

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.PRODUCTION)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingBatch = null
                        showAddDialog = true
                    },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_compost_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "उत्पादन बैच जोड़ें", "New Batch"))
                }
            }
        }
    ) { innerPadding ->
        when (compostState) {
            is DataUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
            is DataUiState.Error -> Box(Modifier.padding(innerPadding).padding(16.dp)) {
                EmptyStateCard(Icons.Default.ErrorOutline, str(language, "त्रुटि", "Error"), compostState.message)
            }
            is DataUiState.Success -> {
                val batches = compostState.data
                val totalCost = batches.sumOf { it.productionCostInr + it.packagingCostInr }
                val totalRevenue = batches.sumOf { it.soldQuantity * it.sellingPricePerUnitInr }
                val profit = totalRevenue - totalCost

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 84.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricStatCard(
                                title = str(language, "उत्पादन लागत", "Prod. Cost"),
                                value = "₹${totalCost.toInt()}",
                                subtitle = "${batches.size} ${str(language, "बैच", "batches")}",
                                icon = Icons.Default.Spa,
                                accentColor = Saffron,
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatCard(
                                title = str(language, "बिक्री व लाभ/हानि", "Sales & P/L"),
                                value = "₹${totalRevenue.toInt()}",
                                subtitle = "${if (profit >= 0) "+" else ""}₹${profit.toInt()} ${str(language, "लाभ", "P/L")}",
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                accentColor = if (profit >= 0) GoshalaGreen else AlertRed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        FilledTonalButton(
                            onClick = onAskAiCostAnalysis,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Saffron)
                            Spacer(Modifier.width(8.dp))
                            Text(str(language, "AI से खाद उत्पादन लागत व मुनाफा विश्लेषण पूछें", "Ask AI to Analyze Production Cost & Margins"))
                        }
                    }

                    if (batches.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Spa,
                                title = str(language, "कोई उत्पादन बैच नहीं", "No production batches yet"),
                                subtitle = str(language, "गोबर संग्रहण, कम्पोस्ट, वर्मी कम्पोस्ट, जैविक खाद व नर्सरी पौधे दर्ज करें।", "Track dung collection, compost, vermicompost, dry manure, and nursery plants.")
                            )
                        }
                    } else {
                        items(batches, key = { it.id }) { batch ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${batch.batchCode} • ${batch.productType}", fontWeight = FontWeight.Bold)
                                        Text(batch.status, style = MaterialTheme.typography.labelSmall, color = ForestGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Text("${str(language, "मात्रा", "Qty")}: ${batch.quantity} ${batch.unit} | ${str(language, "शेष स्टॉक", "Stock")}: ${batch.stockRemaining} ${batch.unit}", style = MaterialTheme.typography.bodySmall)
                                    Text("${str(language, "लागत", "Cost")}: ₹${(batch.productionCostInr + batch.packagingCostInr).toInt()} | ${str(language, "विक्रय दर", "Rate")}: ₹${batch.sellingPricePerUnitInr.toInt()}/${batch.unit}", style = MaterialTheme.typography.labelSmall)
                                    if (session.canEdit(AppModule.PRODUCTION) || session.canDelete(AppModule.PRODUCTION)) {
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (session.canEdit(AppModule.PRODUCTION) && batch.stockRemaining > 0) {
                                                OutlinedButton(onClick = { sellBatch = batch }) {
                                                    Text(str(language, "बिक्री दर्ज करें", "Record Sale"))
                                                }
                                            } else {
                                                Spacer(Modifier.width(1.dp))
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (session.canEdit(AppModule.PRODUCTION)) {
                                                    IconButton(
                                                        onClick = {
                                                            editingBatch = batch
                                                            showAddDialog = false
                                                        },
                                                        modifier = Modifier.testTag("compost_edit_${batch.id}")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Edit,
                                                            contentDescription = str(language, "संपादित करें", "Edit"),
                                                            tint = ForestGreen
                                                        )
                                                    }
                                                }
                                                if (session.canDelete(AppModule.PRODUCTION)) {
                                                    IconButton(
                                                        onClick = { deletingBatch = batch },
                                                        modifier = Modifier.testTag("compost_delete_${batch.id}")
                                                    ) {
                                                        Icon(
                                                            Icons.Default.DeleteOutline,
                                                            contentDescription = str(language, "हटाएं", "Delete"),
                                                            tint = AlertRed
                                                        )
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
            }
        }
    }

    if (showAddDialog || editingBatch != null) {
        val currentEdit = editingBatch
        val isEdit = currentEdit != null
        var productType by remember(currentEdit?.id) { mutableStateOf(currentEdit?.productType ?: "VERMICOMPOST") }
        var qty by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.quantity.toString().removeSuffix(".0") else "500")
        }
        var unit by remember(currentEdit?.id) { mutableStateOf(currentEdit?.unit ?: "KG") }
        var rawDung by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.rawDungKg.toString().removeSuffix(".0") else "800")
        }
        var prodCost by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.productionCostInr.toString().removeSuffix(".0") else "1200")
        }
        var packCost by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.packagingCostInr.toString().removeSuffix(".0") else "300")
        }
        var sellPrice by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.sellingPricePerUnitInr.toString().removeSuffix(".0") else "12")
        }
        var status by remember(currentEdit?.id) { mutableStateOf(currentEdit?.status ?: "READY_FOR_SALE") }
        var batchError by remember(currentEdit?.id) { mutableStateOf<String?>(null) }

        val closeDialog = {
            showAddDialog = false
            editingBatch = null
        }

        AlertDialog(
            onDismissRequest = closeDialog,
            title = {
                Text(
                    if (isEdit) str(language, "उत्पादन बैच संपादित करें", "Edit Production Batch")
                    else str(language, "गोबर व खाद उत्पादन बैच", "New Compost / Production Batch"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    batchError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("DUNG_COLLECTION" to "गोबर", "COMPOST" to "कम्पोस्ट", "VERMICOMPOST" to "वर्मी", "DRY_MANURE" to "सूखी खाद", "NURSERY_PLANTS" to "नर्सरी", "PACKAGED_PRODUCT" to "पैकेज्ड").forEach { (k, l) ->
                            FilterChip(selected = productType == k, onClick = { productType = k }, label = { Text(l, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = qty, onValueChange = { qty = it; batchError = null }, label = { Text(str(language, "उत्पाद मात्रा *", "Output Quantity *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(str(language, "इकाई (KG/BAG/PLANT)", "Unit")) }, singleLine = true)
                    OutlinedTextField(value = rawDung, onValueChange = { rawDung = it }, label = { Text(str(language, "कच्चा गोबर (किग्रा)", "Raw Dung (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = prodCost, onValueChange = { prodCost = it }, label = { Text(str(language, "उत्पादन लागत (₹)", "Production Cost (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = packCost, onValueChange = { packCost = it }, label = { Text(str(language, "पैकेजिंग लागत (₹)", "Packaging Cost (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = sellPrice, onValueChange = { sellPrice = it }, label = { Text(str(language, "विक्रय मूल्य प्रति इकाई (₹)", "Selling Price / Unit (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    if (isEdit) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("IN_PROGRESS" to "प्रगति पर", "PACKAGED" to "पैकेज्ड", "READY_FOR_SALE" to "बिक्री हेतु", "SOLD_OUT" to "समाप्त").forEach { (k, l) ->
                                FilterChip(selected = status == k, onClick = { status = k }, label = { Text(l, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
                        if (q <= 0.0) {
                            batchError = str(language, "कृपया सही उत्पाद मात्रा (>0) दर्ज करें।", "Please enter a valid output quantity (>0).")
                            return@Button
                        }
                        if (currentEdit != null) {
                            onUpdateBatch(
                                currentEdit.copy(
                                    productType = productType,
                                    quantity = q,
                                    unit = unit.trim().ifBlank { "KG" },
                                    rawDungKg = rawDung.toDoubleOrNull() ?: 0.0,
                                    productionCostInr = prodCost.toDoubleOrNull() ?: 0.0,
                                    packagingCostInr = packCost.toDoubleOrNull() ?: 0.0,
                                    sellingPricePerUnitInr = sellPrice.toDoubleOrNull() ?: 0.0,
                                    stockRemaining = (q - currentEdit.soldQuantity).coerceAtLeast(0.0),
                                    status = status
                                )
                            )
                        } else {
                            onAddBatch(
                                CompostBatch(
                                    productType = productType,
                                    quantity = q,
                                    unit = unit.trim().ifBlank { "KG" },
                                    rawDungKg = rawDung.toDoubleOrNull() ?: 0.0,
                                    productionCostInr = prodCost.toDoubleOrNull() ?: 0.0,
                                    packagingCostInr = packCost.toDoubleOrNull() ?: 0.0,
                                    sellingPricePerUnitInr = sellPrice.toDoubleOrNull() ?: 0.0,
                                    soldQuantity = 0.0,
                                    stockRemaining = q,
                                    status = status,
                                    dateStr = QrAndExportUtils.todayIso()
                                )
                            )
                        }
                        closeDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("compost_save_button")
                ) { Text(if (isEdit) str(language, "अपडेट करें", "Update") else str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = closeDialog) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    deletingBatch?.let { target ->
        ConfirmActionDialog(
            title = str(language, "उत्पादन बैच हटाएं", "Delete Production Batch"),
            message = str(
                language,
                "क्या आप वाकई बैच ${target.batchCode} (${target.productType}) को हटाना चाहते हैं?",
                "Are you sure you want to delete batch ${target.batchCode} (${target.productType})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteBatch(target)
                deletingBatch = null
            },
            onDismiss = { deletingBatch = null }
        )
    }

    sellBatch?.let { batch ->
        var sellQty by remember { mutableStateOf("") }
        var saleError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { sellBatch = null },
            title = { Text(str(language, "बिक्री दर्ज करें (${batch.batchCode})", "Record Sale (${batch.batchCode})")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = sellQty,
                        onValueChange = { sellQty = it; saleError = null },
                        label = { Text("${str(language, "बिक्री मात्रा", "Sell Quantity")} (Max ${batch.stockRemaining} ${batch.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                    saleError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sq = sellQty.toDoubleOrNull() ?: 0.0
                        if (sq <= 0.0 || sq > batch.stockRemaining) {
                            saleError = str(language, "मात्रा 0 से अधिक और शेष स्टॉक (${batch.stockRemaining}) के अंदर होनी चाहिए।", "Enter quantity between 0 and ${batch.stockRemaining}.")
                            return@Button
                        }
                        onRecordSale(batch, sq)
                        sellBatch = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = { sellBatch = null }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }
}

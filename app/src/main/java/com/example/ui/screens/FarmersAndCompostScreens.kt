package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
    onAddFarmerTx: (FarmerTransaction, FarmerRecord) -> Unit
) {
    val context = LocalContext.current
    var tabIndex by remember { mutableIntStateOf(0) }
    var showAddFarmerDialog by remember { mutableStateOf(false) }
    var selectedFarmerForTx by remember { mutableStateOf<FarmerRecord?>(null) }

    val farmers = (farmersState as? DataUiState.Success)?.data.orEmpty()
    val transactions = (farmerTxState as? DataUiState.Success)?.data.orEmpty()

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.FARMERS)) {
                ExtendedFloatingActionButton(
                    onClick = { showAddFarmerDialog = true },
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
                if (farmers.isEmpty()) {
                    EmptyStateCard(
                        icon = Icons.Default.Agriculture,
                        title = str(language, "कोई किसान पंजीकृत नहीं", "No farmers registered"),
                        subtitle = str(language, "चारा आपूर्तिकर्ता या खाद खरीदार किसान का नाम, गाँव व मोबाइल जोड़ें।", "Register local farmers as fodder suppliers or compost buyers.")
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
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

                if (transactions.isEmpty()) {
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

    if (showAddFarmerDialog) {
        var name by remember { mutableStateOf("") }
        var mobile by remember { mutableStateOf("") }
        var village by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var coopType by remember { mutableStateOf("BOTH") }

        AlertDialog(
            onDismissRequest = { showAddFarmerDialog = false },
            title = { Text(str(language, "नया किसान पंजीकरण", "Register Farmer"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str(language, "किसान का नाम *", "Farmer Name *")) }, singleLine = true)
                    OutlinedTextField(value = mobile, onValueChange = { mobile = it }, label = { Text(str(language, "मोबाइल नंबर *", "Mobile *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                    OutlinedTextField(value = village, onValueChange = { village = it }, label = { Text(str(language, "गाँव *", "Village *")) }, singleLine = true)
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(str(language, "पूरा पता", "Address")) })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("SUPPLIER" to "आपूर्तिकर्ता", "BUYER" to "खरीदार", "BOTH" to "दोनों").forEach { (k, l) ->
                            FilterChip(selected = coopType == k, onClick = { coopType = k }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank() || mobile.length < 6 || village.isBlank()) return@Button
                        onAddFarmer(FarmerRecord(name = name.trim(), mobile = mobile.trim(), village = village.trim(), address = address.trim(), cooperationType = coopType))
                        showAddFarmerDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showAddFarmerDialog = false }) { Text(str(language, "रद्द करें", "Cancel")) } }
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

        val purchaseProducts = listOf("भूसा (Bhusa)", "हरा चारा", "बरसीम", "मक्का", "सूखी घास")
        val saleProducts = listOf("गोबर खाद", "वर्मी कम्पोस्ट", "जैविक खाद", "नर्सरी पौधे")

        AlertDialog(
            onDismissRequest = { selectedFarmerForTx = null },
            title = { Text("${farmer.name} — ${str(language, "नया बिल", "New Invoice")}", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = direction == "PURCHASE_FROM_FARMER", onClick = { direction = "PURCHASE_FROM_FARMER"; product = purchaseProducts.first() }, label = { Text(str(language, "किसान से खरीद", "Buy Fodder")) })
                        FilterChip(selected = direction == "SALE_TO_FARMER", onClick = { direction = "SALE_TO_FARMER"; product = saleProducts.first() }, label = { Text(str(language, "किसान को बिक्री", "Sell Product")) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (if (direction == "PURCHASE_FROM_FARMER") purchaseProducts else saleProducts).take(3).forEach { p ->
                            FilterChip(selected = product == p, onClick = { product = p }, label = { Text(p, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = product, onValueChange = { product = it }, label = { Text(str(language, "उत्पाद विवरण *", "Product *")) }, singleLine = true)
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text(str(language, "मात्रा *", "Quantity *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(str(language, "इकाई (QUINTAL/KG/BAG)", "Unit")) }, singleLine = true)
                    OutlinedTextField(value = quality, onValueChange = { quality = it }, label = { Text(str(language, "गुणवत्ता", "Quality")) }, singleLine = true)
                    OutlinedTextField(value = rate, onValueChange = { rate = it }, label = { Text(str(language, "तय दर (₹) *", "Rate (₹) *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = paid, onValueChange = { paid = it }, label = { Text(str(language, "भुगतान राशि (₹)", "Paid Amount (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(str(language, "टिप्पणी", "Notes")) })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
                        val r = rate.toDoubleOrNull() ?: 0.0
                        if (q <= 0 || product.isBlank()) return@Button
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
    onAskAiCostAnalysis: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var sellBatch by remember { mutableStateOf<CompostBatch?>(null) }

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.PRODUCTION)) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
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
                                icon = Icons.Default.TrendingUp,
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
                                    if (session.canEdit(AppModule.PRODUCTION) && batch.stockRemaining > 0) {
                                        Spacer(Modifier.height(8.dp))
                                        OutlinedButton(onClick = { sellBatch = batch }) {
                                            Text(str(language, "बिक्री दर्ज करें", "Record Sale"))
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

    if (showAddDialog) {
        var productType by remember { mutableStateOf("VERMICOMPOST") }
        var qty by remember { mutableStateOf("500") }
        var unit by remember { mutableStateOf("KG") }
        var rawDung by remember { mutableStateOf("800") }
        var prodCost by remember { mutableStateOf("1200") }
        var packCost by remember { mutableStateOf("300") }
        var sellPrice by remember { mutableStateOf("12") }
        var status by remember { mutableStateOf("READY_FOR_SALE") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(str(language, "गोबर व खाद उत्पादन बैच", "New Compost / Production Batch"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("DUNG_COLLECTION" to "गोबर", "COMPOST" to "कम्पोस्ट", "VERMICOMPOST" to "वर्मी", "NURSERY_PLANTS" to "नर्सरी").forEach { (k, l) ->
                            FilterChip(selected = productType == k, onClick = { productType = k }, label = { Text(l, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    OutlinedTextField(value = qty, onValueChange = { qty = it }, label = { Text(str(language, "उत्पाद मात्रा *", "Output Quantity *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text(str(language, "इकाई (KG/BAG/PLANT)", "Unit")) }, singleLine = true)
                    OutlinedTextField(value = rawDung, onValueChange = { rawDung = it }, label = { Text(str(language, "कच्चा गोबर (किग्रा)", "Raw Dung (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = prodCost, onValueChange = { prodCost = it }, label = { Text(str(language, "उत्पादन लागत (₹)", "Production Cost (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = packCost, onValueChange = { packCost = it }, label = { Text(str(language, "पैकेजिंग लागत (₹)", "Packaging Cost (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = sellPrice, onValueChange = { sellPrice = it }, label = { Text(str(language, "विक्रय मूल्य प्रति इकाई (₹)", "Selling Price / Unit (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q = qty.toDoubleOrNull() ?: 0.0
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
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    sellBatch?.let { batch ->
        var sellQty by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sellBatch = null },
            title = { Text(str(language, "बिक्री दर्ज करें (${batch.batchCode})", "Record Sale (${batch.batchCode})")) },
            text = {
                OutlinedTextField(
                    value = sellQty,
                    onValueChange = { sellQty = it },
                    label = { Text("${str(language, "बिक्री मात्रा", "Sell Quantity")} (Max ${batch.stockRemaining} ${batch.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sq = sellQty.toDoubleOrNull() ?: 0.0
                        if (sq > 0 && sq <= batch.stockRemaining) {
                            onRecordSale(batch, sq)
                            sellBatch = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = { sellBatch = null }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }
}

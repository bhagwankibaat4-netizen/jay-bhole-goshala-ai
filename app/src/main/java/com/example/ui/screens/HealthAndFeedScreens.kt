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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
fun HealthManagementScreen(
    language: AppLanguage,
    session: UserSession,
    healthState: DataUiState<List<HealthRecord>>,
    cattleList: List<Cattle>,
    onAddHealthRecord: (HealthRecord) -> Unit,
    onDeleteHealthRecord: (String) -> Unit
) {
    var filterType by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.HEALTH)) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_health_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "स्वास्थ्य प्रविष्टि", "Add Health Log"))
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
            // Sick Cattle Alerts Banner
            val sickCattle = cattleList.filter { it.healthStatus != "HEALTHY" }
            if (sickCattle.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SoftRedTint),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = str(
                                language,
                                "${sickCattle.size} पशु निगरानी/उपचार में: ${sickCattle.joinToString { it.name }}",
                                "${sickCattle.size} cattle need care: ${sickCattle.joinToString { it.name }}"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = AlertRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to str(language, "सभी", "All"),
                    "VACCINATION" to str(language, "टीकाकरण", "Vaccine"),
                    "TREATMENT" to str(language, "उपचार", "Treatment"),
                    "VET_VISIT" to str(language, "डॉक्टर", "Vet"),
                    "WEIGHT_CHECK" to str(language, "वजन", "Weight")
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = filterType == key,
                        onClick = { filterType = key },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            when (healthState) {
                is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestGreen)
                }
                is DataUiState.Error -> EmptyStateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = str(language, "त्रुटि", "Error"),
                    subtitle = healthState.message
                )
                is DataUiState.Success -> {
                    val filtered = healthState.data.filter { filterType == "ALL" || it.recordType == filterType }
                    val totalMedCost = filtered.sumOf { it.costInr }
                    val activeReminders = filtered.count { it.reminderActive }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricStatCard(
                            title = str(language, "चिकित्सा खर्च", "Medical Cost"),
                            value = "₹${totalMedCost.toInt()}",
                            subtitle = "${filtered.size} ${str(language, "रिकॉर्ड", "records")}",
                            icon = Icons.Default.MedicalServices,
                            accentColor = AlertRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = str(language, "फॉलो-अप रिमाइंडर", "Reminders"),
                            value = activeReminders.toString(),
                            subtitle = str(language, "सक्रिय अलर्ट", "Active alerts"),
                            icon = Icons.Default.NotificationsActive,
                            accentColor = Saffron,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    if (filtered.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.MedicalServices,
                            title = str(language, "कोई स्वास्थ्य रिकॉर्ड नहीं", "No health records yet"),
                            subtitle = str(language, "टीकाकरण, उपचार, वजन और डॉक्टर विजिट दर्ज करें।", "Log vaccinations, vet visits, treatments, and weight checks.")
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(filtered, key = { it.id }) { rec ->
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
                                            Text(
                                                text = "${rec.cattleName} (${rec.animalCode})",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Surface(
                                                color = SoftGoldTint,
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = rec.recordType,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = ForestGreen,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(rec.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        if (rec.medicine.isNotBlank()) {
                                            Text("${str(language, "दवा", "Medicine")}: ${rec.medicine}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (rec.vetName.isNotBlank()) {
                                            Text("${str(language, "चिकित्सक", "Vet")}: ${rec.vetName}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "₹${rec.costInr.toInt()} | ${rec.weightKg} kg | ${str(language, "फॉलो-अप", "Next")}: ${rec.nextFollowUpDate.ifBlank { "-" }}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (session.canDelete(AppModule.HEALTH)) {
                                                IconButton(onClick = { onDeleteHealthRecord(rec.id) }) {
                                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = AlertRed)
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

    if (showAddDialog) {
        AddHealthDialog(
            language = language,
            cattleList = cattleList,
            onDismiss = { showAddDialog = false },
            onSave = {
                onAddHealthRecord(it)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddHealthDialog(
    language: AppLanguage,
    cattleList: List<Cattle>,
    onDismiss: () -> Unit,
    onSave: (HealthRecord) -> Unit
) {
    var selectedCattle by remember { mutableStateOf(cattleList.firstOrNull()) }
    var manualCattleName by remember { mutableStateOf("") }
    var recordType by remember { mutableStateOf("VACCINATION") }
    var title by remember { mutableStateOf("") }
    var medicine by remember { mutableStateOf("") }
    var vetName by remember { mutableStateOf("") }
    var costText by remember { mutableStateOf("0") }
    var weightText by remember { mutableStateOf(selectedCattle?.weightKg?.toString() ?: "0") }
    var followUp by remember { mutableStateOf("") }
    var reminderActive by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(language, "स्वास्थ्य / टीकाकरण दर्ज करें", "Record Health / Vaccination"), fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (cattleList.isNotEmpty()) {
                    Text(str(language, "पशु चुनें (${cattleList.size}):", "Select Animal (${cattleList.size}):"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cattleList.forEach { c ->
                            FilterChip(
                                selected = selectedCattle?.id == c.id,
                                onClick = { selectedCattle = c; weightText = c.weightKg.toString() },
                                label = { Text("${c.name} (${c.animalCode})") }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = manualCattleName,
                        onValueChange = { manualCattleName = it },
                        label = { Text(str(language, "पशु का नाम *", "Animal Name *")) },
                        singleLine = true
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("VACCINATION" to "टीका", "TREATMENT" to "उपचार", "VET_VISIT" to "डॉक्टर", "WEIGHT_CHECK" to "वजन").forEach { (k, l) ->
                        FilterChip(selected = recordType == k, onClick = { recordType = k }, label = { Text(l) })
                    }
                }
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(str(language, "शीर्षक (जैसे: FMD टीका) *", "Title *")) }, singleLine = true)
                OutlinedTextField(value = medicine, onValueChange = { medicine = it }, label = { Text(str(language, "दवा / वैक्सीन", "Medicine / Vaccine")) }, singleLine = true)
                OutlinedTextField(value = vetName, onValueChange = { vetName = it }, label = { Text(str(language, "पशु चिकित्सक नाम", "Veterinarian Name")) }, singleLine = true)
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text(str(language, "खर्च (₹)", "Cost (₹)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(str(language, "वर्तमान वजन (किग्रा)", "Weight (kg)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(value = followUp, onValueChange = { followUp = it }, label = { Text(str(language, "अगला फॉलो-अप (YYYY-MM-DD)", "Follow-up Date")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reminderActive, onCheckedChange = { reminderActive = it })
                    Text(str(language, "फॉलो-अप रिमाइंडर सक्रिय करें", "Enable follow-up reminder"), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(str(language, "टिप्पणी", "Notes")) })
                errorMsg?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cName = selectedCattle?.name ?: manualCattleName.trim()
                    if (cName.isBlank() || title.isBlank()) {
                        errorMsg = str(language, "पशु नाम व शीर्षक आवश्यक हैं।", "Animal and title are required.")
                        return@Button
                    }
                    onSave(
                        HealthRecord(
                            cattleId = selectedCattle?.id ?: "manual_${System.currentTimeMillis()}",
                            cattleName = cName,
                            animalCode = selectedCattle?.animalCode ?: "MANUAL",
                            recordType = recordType,
                            title = title.trim(),
                            medicine = medicine.trim(),
                            vetName = vetName.trim(),
                            costInr = costText.toDoubleOrNull() ?: 0.0,
                            weightKg = weightText.toDoubleOrNull() ?: 0.0,
                            nextFollowUpDate = followUp.trim(),
                            reminderActive = reminderActive,
                            notes = notes.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) {
                Text(str(language, "सहेजें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(str(language, "रद्द करें", "Cancel")) }
        }
    )
}

@Composable
fun FeedAndOpsScreen(
    language: AppLanguage,
    session: UserSession,
    feedLogsState: DataUiState<List<FeedAndOperationLog>>,
    openAddOnStart: Boolean,
    onConsumedOpenAdd: () -> Unit,
    onAddFeedLog: (FeedAndOperationLog) -> Unit,
    onToggleTaskCompleted: (String, Boolean) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(openAddOnStart) {
        if (openAddOnStart && session.canAdd(AppModule.FEED)) {
            showAddDialog = true
            onConsumedOpenAdd()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.FEED)) {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_feed_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "चारा / कार्य जोड़ें", "Log Feed / Task"))
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
            when (feedLogsState) {
                is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ForestGreen)
                }
                is DataUiState.Error -> EmptyStateCard(
                    icon = Icons.Default.ErrorOutline,
                    title = str(language, "त्रुटि", "Error"),
                    subtitle = feedLogsState.message
                )
                is DataUiState.Success -> {
                    val logs = feedLogsState.data
                    val latestStock = logs.firstOrNull()?.feedStockRemainingKg ?: 0.0
                    val totalTodayFeed = logs.filter { it.logDate == QrAndExportUtils.todayIso() }
                        .sumOf { it.greenFodderKg + it.dryFodderBhusaKg + it.concentrateKg }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricStatCard(
                            title = str(language, "आज का कुल चारा", "Today's Feed"),
                            value = "${totalTodayFeed.toInt()} kg",
                            subtitle = str(language, "हरा + भूसा + दाना", "Green + Dry + Mix"),
                            icon = Icons.Default.Grass,
                            accentColor = GoshalaGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            title = str(language, "शेष चारा स्टॉक", "Feed Stock"),
                            value = "${latestStock.toInt()} kg",
                            subtitle = if (latestStock < 500) str(language, "कम स्टॉक अलर्ट!", "Low Stock!") else str(language, "पर्याप्त भंडार", "Healthy Stock"),
                            icon = Icons.Default.Inventory,
                            accentColor = if (latestStock < 500) AlertRed else ForestGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))

                    if (logs.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.Grass,
                            title = str(language, "कोई चारा लॉग नहीं", "No feed & daily operation logs"),
                            subtitle = str(language, "हरा चारा, भूसा, पानी, सफाई और स्टाफ कार्य दर्ज करें।", "Record green fodder, bhusa, water, cleaning, and tasks.")
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(logs, key = { it.id }) { log ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(log.logDate, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (log.shedCleaned) str(language, "शेड सफाई: पूर्ण ✓", "Shed Clean ✓")
                                                else str(language, "सफाई लंबित", "Clean Pending"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (log.shedCleaned) GoshalaGreen else Saffron
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${str(language, "हरा चारा", "Green")}: ${log.greenFodderKg} kg • ${str(language, "भूसा", "Bhusa")}: ${log.dryFodderBhusaKg} kg • ${str(language, "दाना", "Conc.")}: ${log.concentrateKg} kg",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${str(language, "पानी", "Water")}: ${log.waterLiters} L | ${str(language, "शेष स्टॉक", "Stock")}: ${log.feedStockRemainingKg} kg",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (log.taskTitle.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = log.taskCompleted,
                                                    onCheckedChange = { checked ->
                                                        if (session.canEdit(AppModule.FEED)) {
                                                            onToggleTaskCompleted(log.id, checked)
                                                        }
                                                    },
                                                    enabled = session.canEdit(AppModule.FEED)
                                                )
                                                Text(
                                                    text = "${log.taskTitle} (${log.assignedStaffName.ifBlank { "स्टाफ" }})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium
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

    if (showAddDialog) {
        var logDate by remember { mutableStateOf(QrAndExportUtils.todayIso()) }
        var greenKg by remember { mutableStateOf("120") }
        var dryKg by remember { mutableStateOf("180") }
        var concKg by remember { mutableStateOf("25") }
        var waterL by remember { mutableStateOf("900") }
        var stockKg by remember { mutableStateOf("1200") }
        var shedCleaned by remember { mutableStateOf(true) }
        var staffName by remember { mutableStateOf("") }
        var taskTitle by remember { mutableStateOf("") }
        var notes by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(str(language, "दैनिक चारा व कार्य प्रविष्टि", "Log Feed & Daily Operations"), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(value = logDate, onValueChange = { logDate = it }, label = { Text(str(language, "दिनांक (YYYY-MM-DD)", "Date")) }, singleLine = true)
                    OutlinedTextField(value = greenKg, onValueChange = { greenKg = it }, label = { Text(str(language, "हरा चारा (किग्रा)", "Green Fodder (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = dryKg, onValueChange = { dryKg = it }, label = { Text(str(language, "सूखा चारा / भूसा (किग्रा)", "Dry Fodder / Bhusa (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = concKg, onValueChange = { concKg = it }, label = { Text(str(language, "पशु आहार / खली (किग्रा)", "Concentrate (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = waterL, onValueChange = { waterL = it }, label = { Text(str(language, "पानी (लीटर)", "Water (Liters)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = stockKg, onValueChange = { stockKg = it }, label = { Text(str(language, "शेष चारा स्टॉक (किग्रा)", "Remaining Feed Stock (kg)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = shedCleaned, onCheckedChange = { shedCleaned = it })
                        Text(str(language, "शेड की सफाई पूरी हुई", "Shed cleaning completed"), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(value = staffName, onValueChange = { staffName = it }, label = { Text(str(language, "स्टाफ नाम", "Assigned Staff")) }, singleLine = true)
                    OutlinedTextField(value = taskTitle, onValueChange = { taskTitle = it }, label = { Text(str(language, "दैनिक कार्य विवरण", "Task Description")) }, singleLine = true)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(str(language, "टिप्पणी", "Notes")) })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddFeedLog(
                            FeedAndOperationLog(
                                logDate = logDate.trim().ifBlank { QrAndExportUtils.todayIso() },
                                greenFodderKg = greenKg.toDoubleOrNull() ?: 0.0,
                                dryFodderBhusaKg = dryKg.toDoubleOrNull() ?: 0.0,
                                concentrateKg = concKg.toDoubleOrNull() ?: 0.0,
                                waterLiters = waterL.toDoubleOrNull() ?: 0.0,
                                shedCleaned = shedCleaned,
                                assignedStaffName = staffName.trim(),
                                taskTitle = taskTitle.trim(),
                                taskCompleted = false,
                                feedStockRemainingKg = stockKg.toDoubleOrNull() ?: 0.0,
                                notes = notes.trim()
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
}

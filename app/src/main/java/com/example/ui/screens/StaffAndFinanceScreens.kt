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
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import com.example.ui.components.CategoryDonutCard
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

@Composable
fun StaffManagementScreen(
    language: AppLanguage,
    session: UserSession,
    staffAccountsState: DataUiState<List<StaffAccount>>,
    staffInvitesState: DataUiState<List<StaffInvite>>,
    staffOpsState: DataUiState<List<StaffOperation>>,
    onCreateInvite: (String, String, String, String, Double, Map<String, List<String>>) -> Unit,
    onUpdateStaff: (String, Boolean, Double, Map<String, List<String>>) -> Unit,
    onAddStaffOperation: (StaffOperation) -> Unit,
    onDeleteStaff: (String) -> Unit = {},
    onDeleteInvite: (String) -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showOpsDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffAccount?>(null) }
    var deletingStaff by remember { mutableStateOf<StaffAccount?>(null) }
    var deletingInvite by remember { mutableStateOf<StaffInvite?>(null) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 0 && session.role == UserRole.ADMIN) {
                ExtendedFloatingActionButton(
                    onClick = { showInviteDialog = true },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_staff_invite_fab")
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "स्टाफ जोड़ें", "Authorize Staff"))
                }
            } else if (selectedTab == 1 && (session.role == UserRole.ADMIN || session.canAdd(AppModule.STAFF))) {
                ExtendedFloatingActionButton(
                    onClick = { showOpsDialog = true },
                    containerColor = Saffron,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_staff_op_fab")
                ) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "उपस्थिति / वेतन", "Attendance / Salary"))
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
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(str(language, "स्टाफ व अनुमतियाँ", "Staff & Permissions")) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(str(language, "उपस्थिति व वेतन", "Attendance & Salary")) }
                )
            }
            Spacer(Modifier.height(10.dp))

            if (selectedTab == 0) {
                if (session.role != UserRole.ADMIN) {
                    EmptyStateCard(
                        icon = Icons.Default.Lock,
                        title = str(language, "केवल प्रशासक के लिए", "Admin Access Only"),
                        subtitle = str(language, "स्टाफ खाते व अनुमतियाँ केवल प्रशासक प्रबंधित कर सकते हैं।", "Only Admin can create or edit staff accounts and module permissions.")
                    )
                } else {
                    val accounts = (staffAccountsState as? DataUiState.Success)?.data.orEmpty()
                    val invites = (staffInvitesState as? DataUiState.Success)?.data.orEmpty()
                    if (accounts.isEmpty() && invites.isEmpty()) {
                        EmptyStateCard(
                            icon = Icons.Default.Groups,
                            title = str(language, "कोई स्टाफ पंजीकृत नहीं", "No staff accounts yet"),
                            subtitle = str(language, "Google ईमेल द्वारा नया स्टाफ जोड़ें और मॉड्यूल-वार VIEW/ADD/EDIT/DELETE अनुमतियाँ तय करें।", "Authorize staff via Google email and assign granular CRUD permissions.")
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 84.dp)
                        ) {
                            items(accounts, key = { it.id }) { staff ->
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
                                                Text(staff.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                                Text("${staff.designation} • ${staff.email}", style = MaterialTheme.typography.bodySmall)
                                                Text("${str(language, "वेतन", "Salary")}: ₹${staff.monthlySalary.toInt()}/माह", style = MaterialTheme.typography.labelSmall, color = ForestGreen)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                FilledTonalButton(
                                                    onClick = { editingStaff = staff },
                                                    modifier = Modifier.testTag("staff_edit_${staff.uid}")
                                                ) {
                                                    Text(str(language, "अनुमतियाँ", "Edit"))
                                                }
                                                if (staff.uid != session.uid) {
                                                    IconButton(
                                                        onClick = { deletingStaff = staff },
                                                        modifier = Modifier.testTag("staff_delete_${staff.uid}")
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
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = staff.permissions.entries.joinToString(" | ") { "${it.key}:${it.value.joinToString(",")}" }.ifBlank { "No permissions" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (invites.isNotEmpty()) {
                                item {
                                    Text(
                                        text = str(language, "पूर्व-स्वीकृत स्टाफ आमंत्रण (लॉगिन प्रतीक्षित)", "Pre-Authorized Invites (Pending Sign-In)"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(invites, key = { it.email }) { inv ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${inv.name} (${inv.designation})", fontWeight = FontWeight.Bold)
                                                Text(inv.email, style = MaterialTheme.typography.bodySmall)
                                                Text(
                                                    text = inv.permissions.entries.joinToString(" | ") { "${it.key}: ${it.value.joinToString("/")}" },
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                            IconButton(
                                                onClick = { deletingInvite = inv },
                                                modifier = Modifier.testTag("invite_delete_${inv.email}")
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteOutline,
                                                    contentDescription = str(language, "आमंत्रण हटाएं", "Delete invite"),
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
            } else {
                when (staffOpsState) {
                    is DataUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                    is DataUiState.Error -> EmptyStateCard(
                        icon = Icons.Default.ErrorOutline,
                        title = str(language, "त्रुटि", "Error"),
                        subtitle = staffOpsState.message
                    )
                    is DataUiState.Success -> {
                        val ops = staffOpsState.data
                        if (ops.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Default.EventAvailable,
                                title = str(language, "कोई उपस्थिति / वेतन रिकॉर्ड नहीं", "No attendance or salary entries"),
                                subtitle = str(language, "दैनिक उपस्थिति, कार्य और वेतन भुगतान दर्ज करें।", "Track staff attendance, tasks, and salary disbursements.")
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 84.dp)
                            ) {
                                items(ops, key = { it.id }) { op ->
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
                                                Text(op.staffName, fontWeight = FontWeight.Bold)
                                                Text("${op.entryType} • ${op.dateStr}", style = MaterialTheme.typography.labelSmall, color = ForestGreen)
                                            }
                                            if (op.entryType == "ATTENDANCE") {
                                                Text("${str(language, "स्थिति", "Status")}: ${op.attendanceStatus}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (op.amountInr > 0) {
                                                Text("${str(language, "राशि", "Amount")}: ₹${op.amountInr.toInt()} (${op.paidStatus})", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (op.taskDescription.isNotBlank()) {
                                                Text(op.taskDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

    if (showInviteDialog) {
        StaffInvitePermissionDialog(
            language = language,
            onDismiss = { showInviteDialog = false },
            onSave = { email, name, phone, desig, salary, perms ->
                onCreateInvite(email, name, phone, desig, salary, perms)
                showInviteDialog = false
            }
        )
    }

    editingStaff?.let { staff ->
        EditStaffPermissionDialog(
            language = language,
            staff = staff,
            onDismiss = { editingStaff = null },
            onSave = { active, salary, perms ->
                onUpdateStaff(staff.uid, active, salary, perms)
                editingStaff = null
            }
        )
    }

    deletingStaff?.let { staff ->
        ConfirmActionDialog(
            title = str(language, "स्टाफ खाता हटाएं", "Delete Staff Account"),
            message = str(
                language,
                "क्या आप वाकई ${staff.name} (${staff.email}) का स्टाफ खाता हटाना चाहते हैं?",
                "Are you sure you want to delete staff account ${staff.name} (${staff.email})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteStaff(staff.uid)
                deletingStaff = null
            },
            onDismiss = { deletingStaff = null }
        )
    }

    deletingInvite?.let { inv ->
        ConfirmActionDialog(
            title = str(language, "स्टाफ आमंत्रण हटाएं", "Delete Staff Invite"),
            message = str(
                language,
                "क्या आप वाकई ${inv.name} (${inv.email}) का आमंत्रण रद्द करना चाहते हैं?",
                "Are you sure you want to remove staff invite for ${inv.name} (${inv.email})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteInvite(inv.email)
                deletingInvite = null
            },
            onDismiss = { deletingInvite = null }
        )
    }

    if (showOpsDialog) {
        val activeStaffList = (staffAccountsState as? DataUiState.Success)?.data.orEmpty()
        var staffName by remember { mutableStateOf(activeStaffList.firstOrNull()?.name ?: "") }
        var staffUid by remember { mutableStateOf(activeStaffList.firstOrNull()?.uid ?: session.uid) }
        var entryType by remember { mutableStateOf("ATTENDANCE") }
        var attStatus by remember { mutableStateOf("PRESENT") }
        var amount by remember { mutableStateOf("0") }
        var taskDesc by remember { mutableStateOf("") }
        var paidStatus by remember { mutableStateOf("PAID") }
        var opError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showOpsDialog = false },
            title = { Text(str(language, "उपस्थिति / वेतन प्रविष्टि", "Staff Operation Entry"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    opError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    if (activeStaffList.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeStaffList.forEach { st ->
                                FilterChip(
                                    selected = staffUid == st.uid,
                                    onClick = { staffUid = st.uid; staffName = st.name },
                                    label = { Text(st.name) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = staffName, onValueChange = { staffName = it; opError = null }, label = { Text(str(language, "कर्मचारी नाम *", "Staff Name *")) }, singleLine = true)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ATTENDANCE" to "उपस्थिति", "SALARY" to "वेतन", "TASK" to "कार्य").forEach { (k, l) ->
                            FilterChip(selected = entryType == k, onClick = { entryType = k }, label = { Text(l) })
                        }
                    }
                    if (entryType == "ATTENDANCE") {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("PRESENT" to "उपस्थित", "HALF_DAY" to "आधा दिन", "ABSENT" to "अनुपस्थित").forEach { (k, l) ->
                                FilterChip(selected = attStatus == k, onClick = { attStatus = k }, label = { Text(l) })
                            }
                        }
                    }
                    if (entryType == "SALARY") {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text(str(language, "वेतन राशि (₹)", "Salary Amount (₹)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = paidStatus == "PAID", onClick = { paidStatus = "PAID" }, label = { Text("PAID") })
                            FilterChip(selected = paidStatus == "PENDING", onClick = { paidStatus = "PENDING" }, label = { Text("PENDING") })
                        }
                    }
                    OutlinedTextField(value = taskDesc, onValueChange = { taskDesc = it }, label = { Text(str(language, "कार्य / टिप्पणी", "Task / Note")) })
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (staffName.isBlank()) {
                            opError = str(language, "कृपया कर्मचारी का नाम दर्ज करें।", "Please enter staff name.")
                            return@Button
                        }
                        onAddStaffOperation(
                            StaffOperation(
                                staffUid = staffUid,
                                staffName = staffName.trim(),
                                entryType = entryType,
                                dateStr = QrAndExportUtils.todayIso(),
                                attendanceStatus = if (entryType == "ATTENDANCE") attStatus else "NA",
                                amountInr = amount.toDoubleOrNull() ?: 0.0,
                                taskDescription = taskDesc.trim(),
                                paidStatus = if (entryType == "SALARY") paidStatus else "NA"
                            )
                        )
                        showOpsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) { Text(str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = { showOpsDialog = false }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }
}

@Composable
private fun StaffInvitePermissionDialog(
    language: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Double, Map<String, List<String>>) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("गौ सेवक") }
    var salary by remember { mutableStateOf("10000") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val permState = remember {
        mutableStateMapOf<String, Set<String>>().apply {
            put(AppModule.CATTLE.key, setOf("VIEW"))
            put(AppModule.FEED.key, setOf("VIEW", "ADD"))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(language, "नया स्टाफ व अनुमतियाँ", "Authorize Staff & Permissions"), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                errorMsg?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                OutlinedTextField(value = email, onValueChange = { email = it; errorMsg = null }, label = { Text(str(language, "Google ईमेल *", "Staff Google Email *")) }, singleLine = true)
                OutlinedTextField(value = name, onValueChange = { name = it; errorMsg = null }, label = { Text(str(language, "पूरा नाम *", "Full Name *")) }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(str(language, "मोबाइल नंबर", "Mobile")) }, singleLine = true)
                OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text(str(language, "पद / जिम्मेदारी", "Designation")) }, singleLine = true)
                OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text(str(language, "मासिक वेतन (₹)", "Monthly Salary (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Spacer(Modifier.height(4.dp))
                Text(str(language, "मॉड्यूल अनुमतियाँ (अनअसाइन डिफ़ॉल्ट रूप से बंद हैं):", "Module Permissions (Unassigned denied by default):"), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                PermissionMatrixEditor(language = language, permState = permState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!email.contains("@") || name.isBlank()) {
                        errorMsg = str(language, "कृपया सही Google ईमेल और पूरा नाम दर्ज करें।", "Please enter a valid Google email and full name.")
                        return@Button
                    }
                    val cleanMap = permState.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
                    val cleanDesignation = designation.trim().ifBlank { "गौ सेवक" }
                    onSave(email.trim().lowercase(), name.trim(), phone.trim(), cleanDesignation, salary.toDoubleOrNull() ?: 0.0, cleanMap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("staff_invite_save_button")
            ) { Text(str(language, "स्वीकृत करें", "Authorize")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(language, "रद्द करें", "Cancel")) } }
    )
}

@Composable
private fun EditStaffPermissionDialog(
    language: AppLanguage,
    staff: StaffAccount,
    onDismiss: () -> Unit,
    onSave: (Boolean, Double, Map<String, List<String>>) -> Unit
) {
    var active by remember { mutableStateOf(staff.active) }
    var salary by remember { mutableStateOf(staff.monthlySalary.toInt().toString()) }
    val permState = remember {
        mutableStateMapOf<String, Set<String>>().apply {
            staff.permissions.forEach { (k, v) -> put(k, v.toSet()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${staff.name} — ${str(language, "अनुमतियाँ", "Permissions")}", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (active) str(language, "खाता सक्रिय", "Account Active") else str(language, "खाता निष्क्रिय", "Account Suspended"))
                }
                OutlinedTextField(value = salary, onValueChange = { salary = it }, label = { Text(str(language, "मासिक वेतन (₹)", "Monthly Salary")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                PermissionMatrixEditor(language = language, permState = permState)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanMap = permState.filterValues { it.isNotEmpty() }.mapValues { it.value.toList() }
                    onSave(active, salary.toDoubleOrNull() ?: staff.monthlySalary, cleanMap)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
            ) { Text(str(language, "अपडेट करें", "Update")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(language, "रद्द करें", "Cancel")) } }
    )
}

@Composable
private fun PermissionMatrixEditor(
    language: AppLanguage,
    permState: MutableMap<String, Set<String>>
) {
    val modules = AppModule.entries.filter { it != AppModule.STAFF }
    modules.forEach { mod ->
        val current = permState[mod.key].orEmpty()
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(mod.label(language), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CrudAction.entries.forEach { action ->
                    val selected = current.contains(action.name)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            permState[mod.key] = if (selected) current - action.name else current + action.name
                        },
                        label = { Text(action.label(language), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
fun FinanceManagementScreen(
    language: AppLanguage,
    session: UserSession,
    financeState: DataUiState<List<FinancialTransaction>>,
    openAddOnStart: Boolean,
    onConsumedOpenAdd: () -> Unit,
    onAddTransaction: (FinancialTransaction) -> Unit,
    onDeleteTransaction: (String) -> Unit,
    onUpdateTransaction: (FinancialTransaction) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTx by remember { mutableStateOf<FinancialTransaction?>(null) }
    var deletingTx by remember { mutableStateOf<FinancialTransaction?>(null) }
    var filterType by remember { mutableStateOf("ALL") }

    LaunchedEffect(openAddOnStart) {
        if (openAddOnStart && session.canAdd(AppModule.FINANCE)) {
            editingTx = null
            showAddDialog = true
            onConsumedOpenAdd()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (session.canAdd(AppModule.FINANCE)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingTx = null
                        showAddDialog = true
                    },
                    containerColor = ForestGreen,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_finance_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "आय / खर्च जोड़ें", "Add Entry"))
                }
            }
        }
    ) { innerPadding ->
        when (financeState) {
            is DataUiState.Loading -> Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ForestGreen)
            }
            is DataUiState.Error -> Box(Modifier.padding(innerPadding).padding(16.dp)) {
                EmptyStateCard(Icons.Default.ErrorOutline, str(language, "त्रुटि", "Error"), financeState.message)
            }
            is DataUiState.Success -> {
                val allTx = financeState.data
                val totalIncome = allTx.filter { it.txType == "INCOME" }.sumOf { it.amountInr }
                val totalExpense = allTx.filter { it.txType == "EXPENSE" }.sumOf { it.amountInr }
                val pendingAmount = allTx.filter { it.paymentStatus == "PENDING" }.sumOf { it.amountInr }
                val filtered = allTx.filter { filterType == "ALL" || it.txType == filterType }

                val monthBars = remember(allTx) {
                    allTx.groupBy { it.dateStr.take(7).ifBlank { "2026-11" } }
                        .entries
                        .sortedBy { it.key }
                        .takeLast(5)
                        .map { (ym, list) ->
                            Triple(
                                ym.takeLast(5),
                                list.filter { it.txType == "INCOME" }.sumOf { it.amountInr },
                                list.filter { it.txType == "EXPENSE" }.sumOf { it.amountInr }
                            )
                        }
                }

                val expenseSlices = remember(allTx) {
                    val exp = allTx.filter { it.txType == "EXPENSE" }
                    listOf(
                        Triple(str(language, "चारा खरीद", "Feed"), exp.filter { it.category == "FEED_PURCHASE" }.sumOf { it.amountInr }, GoshalaGreen),
                        Triple(str(language, "वेतन", "Salary"), exp.filter { it.category == "STAFF_SALARY" }.sumOf { it.amountInr }, Saffron),
                        Triple(str(language, "चिकित्सा", "Medical"), exp.filter { it.category == "MEDICAL" }.sumOf { it.amountInr }, AlertRed),
                        Triple(str(language, "अन्य", "Other"), exp.filter { it.category !in listOf("FEED_PURCHASE", "STAFF_SALARY", "MEDICAL") }.sumOf { it.amountInr }, Golden)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp)
                        .testTag("finance_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 10.dp, bottom = 96.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MetricStatCard(
                                title = str(language, "कुल आय", "Total Income"),
                                value = "₹${totalIncome.toInt()}",
                                subtitle = str(language, "दान व बिक्री", "Donations + Sales"),
                                icon = Icons.AutoMirrored.Filled.TrendingUp,
                                accentColor = GoshalaGreen,
                                modifier = Modifier.weight(1f)
                            )
                            MetricStatCard(
                                title = str(language, "कुल खर्च", "Total Expense"),
                                value = "₹${totalExpense.toInt()}",
                                subtitle = str(language, "बकाया: ₹${pendingAmount.toInt()}", "Pending: ₹${pendingAmount.toInt()}"),
                                icon = Icons.AutoMirrored.Filled.TrendingDown,
                                accentColor = Saffron,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item { IncomeExpenseBarChart(bars = monthBars, language = language) }
                    item { CategoryDonutCard(title = str(language, "खर्च का विवरण", "Expense Breakdown"), slices = expenseSlices, language = language) }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filterType == "ALL", onClick = { filterType = "ALL" }, label = { Text(str(language, "सभी", "All")) })
                            FilterChip(selected = filterType == "INCOME", onClick = { filterType = "INCOME" }, label = { Text(str(language, "आय", "Income")) })
                            FilterChip(selected = filterType == "EXPENSE", onClick = { filterType = "EXPENSE" }, label = { Text(str(language, "खर्च", "Expense")) })
                        }
                    }

                    if (filtered.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.AccountBalanceWallet,
                                title = str(language, "कोई लेनदेन दर्ज नहीं", "No financial entries yet"),
                                subtitle = str(language, "नकद, बैंक और UPI द्वारा आय-व्यय प्रविष्टियाँ जोड़ें।", "Record cash, bank, and UPI income or expenses.")
                            )
                        }
                    } else {
                        items(filtered, key = { it.id }) { tx ->
                            val isIncome = tx.txType == "INCOME"
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${tx.partyName} • ${tx.category}", fontWeight = FontWeight.Bold)
                                        Text(tx.description, style = MaterialTheme.typography.bodySmall)
                                        Text("${tx.dateStr} | ${tx.paymentMode} | ${tx.paymentStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = "${if (isIncome) "+" else "-"}₹${tx.amountInr.toInt()}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isIncome) GoshalaGreen else AlertRed
                                    )
                                    if (session.canEdit(AppModule.FINANCE)) {
                                        IconButton(
                                            onClick = {
                                                editingTx = tx
                                                showAddDialog = false
                                            },
                                            modifier = Modifier.testTag("finance_edit_${tx.id}")
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = str(language, "संपादित करें", "Edit"),
                                                tint = ForestGreen
                                            )
                                        }
                                    }
                                    if (session.canDelete(AppModule.FINANCE)) {
                                        IconButton(
                                            onClick = { deletingTx = tx },
                                            modifier = Modifier.testTag("finance_delete_${tx.id}")
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

    if (showAddDialog || editingTx != null) {
        val currentEdit = editingTx
        val isEdit = currentEdit != null
        var txType by remember(currentEdit?.id) { mutableStateOf(currentEdit?.txType ?: "EXPENSE") }
        var category by remember(currentEdit?.id) { mutableStateOf(currentEdit?.category ?: "FEED_PURCHASE") }
        var amount by remember(currentEdit?.id) {
            mutableStateOf(if (currentEdit != null) currentEdit.amountInr.toString().removeSuffix(".0") else "")
        }
        var mode by remember(currentEdit?.id) { mutableStateOf(currentEdit?.paymentMode ?: "UPI") }
        var status by remember(currentEdit?.id) { mutableStateOf(currentEdit?.paymentStatus ?: "COMPLETED") }
        var party by remember(currentEdit?.id) { mutableStateOf(currentEdit?.partyName ?: "") }
        var desc by remember(currentEdit?.id) { mutableStateOf(currentEdit?.description ?: "") }
        var dateStr by remember(currentEdit?.id) { mutableStateOf(currentEdit?.dateStr ?: QrAndExportUtils.todayIso()) }
        var finError by remember(currentEdit?.id) { mutableStateOf<String?>(null) }

        val closeDialog = {
            showAddDialog = false
            editingTx = null
        }

        AlertDialog(
            onDismissRequest = closeDialog,
            title = {
                Text(
                    if (isEdit) str(language, "वित्तीय प्रविष्टि संपादित करें", "Edit Financial Entry")
                    else str(language, "नई वित्तीय प्रविष्टि", "New Financial Entry"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    finError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(selected = txType == "INCOME", onClick = { txType = "INCOME"; category = "PRODUCT_SALE" }, label = { Text(str(language, "आय (Income)", "Income")) })
                        FilterChip(selected = txType == "EXPENSE", onClick = { txType = "EXPENSE"; category = "FEED_PURCHASE" }, label = { Text(str(language, "खर्च (Expense)", "Expense")) })
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("FEED_PURCHASE" to "चारा", "MEDICAL" to "दवा", "STAFF_SALARY" to "वेतन", "PRODUCT_SALE" to "उत्पाद", "DONATION" to "दान", "MAINTENANCE" to "रखरखाव", "OTHER" to "अन्य").forEach { (k, l) ->
                            FilterChip(selected = category == k, onClick = { category = k }, label = { Text(l) })
                        }
                    }
                    OutlinedTextField(value = amount, onValueChange = { amount = it; finError = null }, label = { Text(str(language, "राशि (₹) *", "Amount (₹) *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value = party, onValueChange = { party = it; finError = null }, label = { Text(str(language, "पक्ष / व्यक्ति का नाम *", "Party Name *")) }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it; finError = null }, label = { Text(str(language, "विवरण *", "Description *")) }, singleLine = true)
                    OutlinedTextField(value = dateStr, onValueChange = { dateStr = it }, label = { Text(str(language, "दिनांक", "Date")) }, singleLine = true)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("CASH", "BANK", "UPI").forEach { m ->
                            FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = status == "COMPLETED", onClick = { status = "COMPLETED" }, label = { Text(str(language, "पूर्ण", "Completed")) })
                        FilterChip(selected = status == "PENDING", onClick = { status = "PENDING" }, label = { Text(str(language, "बकाया", "Pending")) })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull() ?: 0.0
                        if (amt <= 0 || party.isBlank() || desc.isBlank()) {
                            finError = str(language, "कृपया राशि (>0), व्यक्ति का नाम और विवरण भरें।", "Please enter amount (>0), party name, and description.")
                            return@Button
                        }
                        if (currentEdit != null) {
                            onUpdateTransaction(
                                currentEdit.copy(
                                    txType = txType,
                                    category = category,
                                    amountInr = amt,
                                    paymentMode = mode,
                                    paymentStatus = status,
                                    partyName = party.trim(),
                                    description = desc.trim(),
                                    dateStr = dateStr.trim().ifBlank { QrAndExportUtils.todayIso() }
                                )
                            )
                        } else {
                            onAddTransaction(
                                FinancialTransaction(
                                    txType = txType,
                                    category = category,
                                    amountInr = amt,
                                    paymentMode = mode,
                                    paymentStatus = status,
                                    partyName = party.trim(),
                                    description = desc.trim(),
                                    dateStr = dateStr.trim().ifBlank { QrAndExportUtils.todayIso() },
                                    receiptNumber = "TX-${System.currentTimeMillis().toString().takeLast(5)}"
                                )
                            )
                        }
                        closeDialog()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("finance_save_button")
                ) { Text(if (isEdit) str(language, "अपडेट करें", "Update") else str(language, "सहेजें", "Save")) }
            },
            dismissButton = { TextButton(onClick = closeDialog) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    deletingTx?.let { target ->
        ConfirmActionDialog(
            title = str(language, "वित्तीय प्रविष्टि हटाएं", "Delete Financial Entry"),
            message = str(
                language,
                "क्या आप वाकई ${target.partyName} की ₹${target.amountInr.toInt()} की प्रविष्टि हटाना चाहते हैं?",
                "Are you sure you want to delete the ₹${target.amountInr.toInt()} entry for ${target.partyName}?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteTransaction(target.id)
                deletingTx = null
            },
            onDismiss = { deletingTx = null }
        )
    }
}

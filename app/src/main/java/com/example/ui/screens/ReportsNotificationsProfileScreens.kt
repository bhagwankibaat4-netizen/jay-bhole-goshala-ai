package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.AppThemeMode
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GosalaOfficialLogo
import com.example.ui.components.RoleBadgeChip
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

enum class GoshalaReportType(val titleHi: String, val titleEn: String) {
    DAILY_GOSHALA("1. दैनिक गौशाला रिपोर्ट", "1. Daily Goshala Report"),
    MONTHLY_GOSHALA("2. मासिक गौशाला रिपोर्ट", "2. Monthly Goshala Report"),
    CATTLE("3. गौवंश रिपोर्ट", "3. Cattle Report"),
    HEALTH("4. स्वास्थ्य रिपोर्ट", "4. Health Report"),
    VACCINATION("5. टीकाकरण रिपोर्ट", "5. Vaccination Report"),
    FEED("6. चारा रिपोर्ट", "6. Feed Report"),
    STAFF_ATTENDANCE("7. कर्मचारी उपस्थिति रिपोर्ट", "7. Staff Attendance Report"),
    SALARY("8. वेतन रिपोर्ट", "8. Salary Report"),
    INCOME_EXPENSE("9. आय-व्यय रिपोर्ट", "9. Income & Expense Report"),
    DONATION("10. दान रिपोर्ट", "10. Donation Report"),
    FARMER_TRADE("11. किसान व्यापार रिपोर्ट", "11. Farmer Trade Report"),
    COMPOST_PRODUCTION("12. खाद उत्पादन रिपोर्ट", "12. Compost Production Report"),
    PRODUCT_SALES("13. उत्पाद बिक्री रिपोर्ट", "13. Product Sales Report")
}

@Composable
fun ReportsScreen(
    language: AppLanguage,
    session: UserSession,
    cattle: List<Cattle>,
    health: List<HealthRecord>,
    feedLogs: List<FeedAndOperationLog>,
    staffAccounts: List<StaffAccount>,
    staffOps: List<StaffOperation>,
    finance: List<FinancialTransaction>,
    donations: List<Donation>,
    farmerTx: List<FarmerTransaction>,
    compost: List<CompostBatch>,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf(GoshalaReportType.DAILY_GOSHALA) }

    if (session.role == UserRole.PUBLIC || (session.role == UserRole.STAFF && !session.canView(AppModule.REPORTS))) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            EmptyStateCard(
                icon = Icons.Default.Lock,
                title = str(language, "रिपोर्ट केवल अधिकृत कर्मचारियों हेतु", "Reports Restricted"),
                subtitle = str(language, "पूर्ण रिपोर्ट व PDF/Excel निर्यात के लिए प्रशासक अनुमति आवश्यक है।", "Admin or assigned report permission is required.")
            )
        }
        return
    }

    val today = QrAndExportUtils.todayIso()
    val currentMonth = today.take(7)

    val (headers, rows) = remember(selectedReport, cattle, health, feedLogs, staffAccounts, staffOps, finance, donations, farmerTx, compost, language) {
        when (selectedReport) {
            GoshalaReportType.DAILY_GOSHALA -> {
                val h = listOf(str(language, "विवरण", "Metric"), str(language, "आज (${today})", "Today"))
                val r = listOf(
                    listOf(str(language, "कुल गौवंश", "Total Cattle"), cattle.size.toString()),
                    listOf(str(language, "बीमार/निगरानी", "Sick / Care"), cattle.count { it.healthStatus != "HEALTHY" }.toString()),
                    listOf(str(language, "आज का चारा (Kg)", "Today Feed (Kg)"), feedLogs.filter { it.logDate == today }.sumOf { it.greenFodderKg + it.dryFodderBhusaKg }.toString()),
                    listOf(str(language, "आज की आय (₹)", "Today Income"), finance.filter { it.txType == "INCOME" && it.dateStr == today }.sumOf { it.amountInr }.toString()),
                    listOf(str(language, "आज का खर्च (₹)", "Today Expense"), finance.filter { it.txType == "EXPENSE" && it.dateStr == today }.sumOf { it.amountInr }.toString())
                )
                h to r
            }
            GoshalaReportType.MONTHLY_GOSHALA -> {
                val h = listOf(str(language, "माह ($currentMonth)", "Month"), str(language, "मान", "Value"))
                val r = listOf(
                    listOf(str(language, "मासिक आय (₹)", "Monthly Income"), finance.filter { it.txType == "INCOME" && it.dateStr.startsWith(currentMonth) }.sumOf { it.amountInr }.toString()),
                    listOf(str(language, "मासिक व्यय (₹)", "Monthly Expense"), finance.filter { it.txType == "EXPENSE" && it.dateStr.startsWith(currentMonth) }.sumOf { it.amountInr }.toString()),
                    listOf(str(language, "सत्यापित दान (₹)", "Verified Donations"), donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" && it.dateStr.startsWith(currentMonth) }.sumOf { it.amountInr }.toString()),
                    listOf(str(language, "खाद बैच", "Compost Batches"), compost.count { it.dateStr.startsWith(currentMonth) }.toString())
                )
                h to r
            }
            GoshalaReportType.CATTLE -> {
                listOf("ID", str(language, "नाम", "Name"), str(language, "नस्ल", "Breed"), str(language, "शेड", "Shed"), str(language, "स्थिति", "Health")) to
                    cattle.map { listOf(it.animalCode, it.name, it.breed, it.shedNumber, it.healthStatus) }
            }
            GoshalaReportType.HEALTH -> {
                listOf(str(language, "पशु", "Cattle"), str(language, "प्रकार", "Type"), str(language, "विवरण", "Title"), str(language, "खर्च ₹", "Cost")) to
                    health.map { listOf(it.cattleName, it.recordType, it.title, it.costInr.toInt().toString()) }
            }
            GoshalaReportType.VACCINATION -> {
                listOf(str(language, "पशु", "Cattle"), str(language, "टीका", "Vaccine"), str(language, "दवा", "Med"), str(language, "अगली तिथि", "Next Due")) to
                    health.filter { it.recordType == "VACCINATION" }.map { listOf(it.cattleName, it.title, it.medicine, it.nextFollowUpDate) }
            }
            GoshalaReportType.FEED -> {
                listOf(str(language, "तिथि", "Date"), str(language, "हरा (Kg)", "Green"), str(language, "सूखा/भूसा", "Dry"), str(language, "बचा स्टॉक", "Stock")) to
                    feedLogs.map { listOf(it.logDate, it.greenFodderKg.toString(), it.dryFodderBhusaKg.toString(), it.feedStockRemainingKg.toString()) }
            }
            GoshalaReportType.STAFF_ATTENDANCE -> {
                listOf(str(language, "कर्मचारी", "Staff"), str(language, "तिथि", "Date"), str(language, "उपस्थिति", "Status"), str(language, "कार्य", "Task")) to
                    staffOps.filter { it.entryType == "ATTENDANCE" || it.entryType == "TASK" || it.entryType == "WORK" }.map {
                        listOf(it.staffName, it.dateStr, it.attendanceStatus, it.taskDescription)
                    }
            }
            GoshalaReportType.SALARY -> {
                listOf(str(language, "कर्मचारी", "Staff"), str(language, "पद", "Role"), str(language, "मासिक वेतन ₹", "Salary ₹"), str(language, "स्थिति", "Status")) to
                    staffAccounts.map { listOf(it.name, it.designation, it.monthlySalary.toInt().toString(), if (it.active) "ACTIVE" else "INACTIVE") }
            }
            GoshalaReportType.INCOME_EXPENSE -> {
                listOf(str(language, "तिथि", "Date"), str(language, "प्रकार", "Type"), str(language, "श्रेणी", "Category"), str(language, "राशि ₹", "Amount")) to
                    finance.map { listOf(it.dateStr, it.txType, it.category, it.amountInr.toInt().toString()) }
            }
            GoshalaReportType.DONATION -> {
                listOf(str(language, "रसीद", "Receipt"), str(language, "दानदाता", "Donor"), str(language, "राशि ₹", "Amount"), str(language, "स्थिति", "Status")) to
                    donations.map { listOf(it.receiptNumber, it.donorName, it.amountInr.toInt().toString(), it.paymentStatus) }
            }
            GoshalaReportType.FARMER_TRADE -> {
                listOf(str(language, "बिल", "Invoice"), str(language, "किसान", "Farmer"), str(language, "दिशा", "Type"), str(language, "कुल ₹", "Total ₹")) to
                    farmerTx.map { listOf(it.invoiceNumber, it.farmerName, it.direction, it.totalAmountInr.toInt().toString()) }
            }
            GoshalaReportType.COMPOST_PRODUCTION -> {
                listOf(str(language, "बैच", "Batch"), str(language, "उत्पाद", "Product"), str(language, "मात्रा", "Qty"), str(language, "लागत ₹", "Cost ₹")) to
                    compost.map { listOf(it.batchCode, it.productType, "${it.quantity} ${it.unit}", (it.productionCostInr + it.packagingCostInr).toInt().toString()) }
            }
            GoshalaReportType.PRODUCT_SALES -> {
                listOf(str(language, "बैच", "Batch"), str(language, "उत्पाद", "Product"), str(language, "बिक्री", "Sold"), str(language, "बिक्री आय ₹", "Revenue ₹")) to
                    compost.map { listOf(it.batchCode, it.productType, "${it.soldQuantity} ${it.unit}", (it.soldQuantity * it.sellingPricePerUnitInr).toInt().toString()) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = str(language, "रिपोर्ट एवं निर्यात केंद्र (PDF / Excel)", "Reports & Export Center (PDF / Excel)"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GoshalaReportType.entries.forEach { report ->
                    FilterChip(
                        selected = selectedReport == report,
                        onClick = { selectedReport = report },
                        label = { Text(str(language, report.titleHi, report.titleEn)) },
                        modifier = Modifier.testTag("report_chip_${report.name.lowercase()}")
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        val title = str(language, selectedReport.titleHi, selectedReport.titleEn)
                        QrAndExportUtils.exportPdfReport(
                            context = context,
                            filePrefix = selectedReport.name.lowercase(),
                            title = title,
                            subtitle = GosalaBrand.SHORT_ADDRESS_HI,
                            headers = headers,
                            rows = rows
                        ).fold(
                            onSuccess = { file -> QrAndExportUtils.shareFile(context, file, "application/pdf", title) },
                            onFailure = { err -> onShowMessage(err.localizedMessage ?: "PDF export failed") }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.weight(1f).testTag("export_pdf_button")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "PDF निर्यात", "Export PDF"))
                }

                OutlinedButton(
                    onClick = {
                        val title = str(language, selectedReport.titleHi, selectedReport.titleEn)
                        QrAndExportUtils.exportExcelCsvReport(
                            context = context,
                            filePrefix = selectedReport.name.lowercase(),
                            headers = headers,
                            rows = rows
                        ).fold(
                            onSuccess = { file -> QrAndExportUtils.shareFile(context, file, "text/csv", title) },
                            onFailure = { err -> onShowMessage(err.localizedMessage ?: "CSV export failed") }
                        )
                    },
                    modifier = Modifier.weight(1f).testTag("export_excel_button")
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(str(language, "Excel (CSV)", "Excel (CSV)"))
                }
            }
        }

        // Table Header Preview
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = str(language, selectedReport.titleHi, selectedReport.titleEn),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(color = ForestGreen, shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            headers.forEach { h ->
                                Text(
                                    text = h,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            text = str(language, "इस रिपोर्ट में अभी कोई प्रविष्टि नहीं है।", "No records found for this report."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    } else {
                        rows.take(50).forEachIndexed { idx, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (idx % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        text = cell,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

@Composable
fun NotificationsScreen(
    language: AppLanguage,
    session: UserSession,
    notificationsState: DataUiState<List<NotificationItem>>,
    onMarkRead: (String) -> Unit
) {
    val context = LocalContext.current
    var notifPermissionGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifPermissionGranted = granted
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Saffron)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = str(language, "टीकाकरण व फॉलो-अप अलर्ट पाने के लिए सूचना अनुमति दें।", "Enable notifications for vaccine & follow-up alerts."),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(str(language, "अनुमति दें", "Allow"))
                        }
                    }
                }
            }
        }

        when (notificationsState) {
            is DataUiState.Loading -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            is DataUiState.Error -> item { Text(notificationsState.message, color = AlertRed) }
            is DataUiState.Success -> {
                if (notificationsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.NotificationsNone,
                            title = str(language, "कोई नई सूचना नहीं", "No Notifications"),
                            subtitle = str(language, "स्वास्थ्य अनुस्मारक, चारा स्टॉक अलर्ट और दान अपडेट यहाँ दिखेंगे।", "Health reminders, fodder stock alerts, and donation updates appear here.")
                        )
                    }
                } else {
                    items(notificationsState.data, key = { it.id }) { item ->
                        val isRead = item.readByUids.contains(session.uid)
                        Card(
                            onClick = { if (!isRead) onMarkRead(item.id) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isRead) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isRead) MaterialTheme.colorScheme.surfaceVariant else Saffron,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = null,
                                            tint = if (isRead) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isRead) FontWeight.Normal else FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (!isRead) {
                                            Box(Modifier.size(8.dp).background(Saffron, CircleShape))
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsScreen(
    language: AppLanguage,
    themeMode: AppThemeMode,
    session: UserSession,
    auditLogsState: DataUiState<List<AuditLog>>,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetThemeMode: (AppThemeMode) -> Unit,
    onOpenReports: () -> Unit,
    onSignOut: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("profile_settings_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // User Profile Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ForestGreen,
                        modifier = Modifier.size(56.dp).border(2.dp, Golden, CircleShape)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = session.displayName.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(session.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        RoleBadgeChip(role = session.role, language = language)
                    }
                }
            }
        }

        // Language & Theme Settings
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(str(language, "भाषा व थीम सेटिंग्स", "Language & Theme Settings"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                    Text(str(language, "ऐप भाषा (Hindi First)", "App Language"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { onSetLanguage(lang) },
                                label = { Text(lang.label) },
                                modifier = Modifier.testTag("settings_lang_${lang.code}")
                            )
                        }
                    }

                    Text(str(language, "थीम (Theme)", "Appearance"), style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            AppThemeMode.LIGHT to str(language, "लाइट थीम", "Light"),
                            AppThemeMode.DARK to str(language, "डार्क थीम", "Dark")
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = { Text(label) },
                                modifier = Modifier.testTag("settings_theme_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Staff Assigned Permissions Card
        if (session.role == UserRole.STAFF) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(str(language, "आपकी स्वीकृत मॉड्यूल अनुमतियाँ", "Your Assigned Staff Permissions"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (session.permissions.isEmpty()) {
                            Text(str(language, "अभी कोई अनुमति असाइन नहीं है।", "No module permissions assigned yet."), style = MaterialTheme.typography.bodySmall)
                        } else {
                            session.permissions.forEach { (mod, actions) ->
                                Text("• $mod : ${actions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Quick Reports shortcut for authorized roles
        if (session.role == UserRole.ADMIN || (session.role == UserRole.STAFF && session.canView(AppModule.REPORTS))) {
            item {
                OutlinedButton(
                    onClick = onOpenReports,
                    modifier = Modifier.fillMaxWidth().testTag("profile_open_reports_btn")
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(str(language, "सभी 13 गौशाला रिपोर्ट देखें व PDF/Excel निर्यात करें", "Open All 13 Goshala Reports & Exports"))
                }
            }
        }

        // Admin Audit Trail
        if (session.role == UserRole.ADMIN) {
            item {
                Text(
                    text = str(language, "सुरक्षा ऑडिट लॉग (हाल की गतिविधियाँ)", "Audit Trail (Recent Activity)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            when (auditLogsState) {
                is DataUiState.Success -> {
                    if (auditLogsState.data.isEmpty()) {
                        item {
                            Text(str(language, "अभी कोई ऑडिट प्रविष्टि नहीं है।", "No audit logs recorded yet."), style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        items(auditLogsState.data.take(25), key = { it.id }) { log ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("${log.module} • ${log.action}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ForestGreen)
                                    Text(log.summary, style = MaterialTheme.typography.bodySmall)
                                    Text("${log.actorName} (${log.actorRole})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        // Goshala Identity & About Card
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GosalaOfficialLogo(
                        size = 64.dp,
                        innerPadding = 3.dp,
                        borderWidth = 2.dp,
                        testTag = "about_official_logo"
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = str(language, GosalaBrand.SUPPORTING_SLOGAN_HI, GosalaBrand.SUPPORTING_SLOGAN_EN),
                            style = MaterialTheme.typography.labelMedium,
                            color = Saffron,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = str(language, GosalaBrand.ADDRESS_HI, GosalaBrand.ADDRESS_EN),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Sign Out Button
        item {
            Button(
                onClick = onSignOut,
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("sign_out_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(str(language, "सुरक्षित साइन आउट करें", "Sign Out"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

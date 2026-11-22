package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.AppModule
import com.example.data.model.Cattle
import com.example.data.model.FeedAndOperationLog
import com.example.data.model.HealthRecord
import com.example.data.model.UserSession
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.QrCodeDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState
import kotlinx.coroutines.launch

@Composable
fun CattleManagementScreen(
    language: AppLanguage,
    session: UserSession,
    cattleState: DataUiState<List<Cattle>>,
    openAddOnStart: Boolean,
    onConsumedOpenAdd: () -> Unit,
    onSaveCattle: (Cattle, Boolean) -> Unit,
    onDeleteCattle: (Cattle) -> Unit,
    onSponsorCattle: (Cattle) -> Unit,
    healthRecords: List<HealthRecord> = emptyList(),
    feedLogs: List<FeedAndOperationLog> = emptyList(),
    onLookupCattleByQr: (suspend (String) -> Result<Cattle?>)? = null
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf("ALL") }
    var pageLimit by remember { mutableIntStateOf(20) }

    var editingCattle by remember { mutableStateOf<Cattle?>(null) }
    var showFormDialog by remember { mutableStateOf(false) }
    var selectedCattle by remember { mutableStateOf<Cattle?>(null) }
    var openedFromQrScan by remember { mutableStateOf(false) }
    var qrCattle by remember { mutableStateOf<Cattle?>(null) }
    var deleteTarget by remember { mutableStateOf<Cattle?>(null) }
    var showQrLookupDialog by remember { mutableStateOf(false) }
    var isQrSearching by remember { mutableStateOf(false) }
    var qrLookupError by remember { mutableStateOf<String?>(null) }

    // Keep open Cow Details live-synced whenever Admin updates cow info in Firebase
    val latestCattleList = (cattleState as? DataUiState.Success)?.data.orEmpty()
    val activeSelectedCattle = remember(selectedCattle, latestCattleList) {
        val current = selectedCattle ?: return@remember null
        if (current.id.isNotBlank()) {
            latestCattleList.firstOrNull { it.id == current.id } ?: current
        } else {
            latestCattleList.firstOrNull { it.animalCode.equals(current.animalCode, ignoreCase = true) } ?: current
        }
    }

    LaunchedEffect(openAddOnStart) {
        if (openAddOnStart && session.canAdd(AppModule.CATTLE)) {
            editingCattle = null
            showFormDialog = true
            onConsumedOpenAdd()
        }
    }

    if (activeSelectedCattle != null) {
        CattleDetailFullScreen(
            cattle = activeSelectedCattle,
            language = language,
            healthRecords = healthRecords,
            feedLogs = feedLogs,
            canEdit = session.canEdit(AppModule.CATTLE),
            canDelete = session.canDelete(AppModule.CATTLE),
            openedFromQrScan = openedFromQrScan,
            onBack = {
                selectedCattle = null
                openedFromQrScan = false
            },
            onShowQr = { qrCattle = activeSelectedCattle },
            onEdit = {
                editingCattle = activeSelectedCattle
                selectedCattle = null
                openedFromQrScan = false
                showFormDialog = true
            },
            onDelete = { deleteTarget = activeSelectedCattle },
            onSponsor = {
                val target = activeSelectedCattle
                selectedCattle = null
                openedFromQrScan = false
                onSponsorCattle(target)
            }
        )
    } else {
        Scaffold(
            floatingActionButton = {
                if (session.canAdd(AppModule.CATTLE)) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingCattle = null
                            showFormDialog = true
                        },
                        containerColor = ForestGreen,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("add_cattle_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(str(language, "गौवंश जोड़ें", "Add Cattle"))
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
                // Search & Live Camera QR Scanner Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cattle_search_input"),
                        placeholder = { Text(str(language, "नाम, ID, नस्ल या शेड खोजें...", "Search name, ID, breed, shed...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    FilledTonalIconButton(
                        onClick = {
                            qrLookupError = null
                            isQrSearching = false
                            showQrLookupDialog = true
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("qr_lookup_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = str(language, "QR स्कैन करें", "Scan QR"))
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Category Filter Chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to str(language, "सभी", "All"),
                        "COW" to str(language, "गाय", "Cow"),
                        "BULL" to str(language, "बैल", "Bull"),
                        "CALF" to str(language, "बछड़ा", "Calf")
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = categoryFilter == key,
                            onClick = { categoryFilter = key },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                when (cattleState) {
                    is DataUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = ForestGreen)
                        }
                    }
                    is DataUiState.Error -> {
                        EmptyStateCard(
                            icon = Icons.Default.ErrorOutline,
                            title = str(language, "डेटा लोड नहीं हो सका", "Could not load cattle"),
                            subtitle = cattleState.message
                        )
                    }
                    is DataUiState.Success -> {
                        val filtered = remember(cattleState.data, searchQuery, categoryFilter) {
                            cattleState.data.filter { item ->
                                val matchesCat = categoryFilter == "ALL" || item.category == categoryFilter
                                val q = searchQuery.trim().lowercase()
                                val matchesQuery = q.isBlank() ||
                                    item.name.lowercase().contains(q) ||
                                    item.animalCode.lowercase().contains(q) ||
                                    item.breed.lowercase().contains(q) ||
                                    item.shedNumber.lowercase().contains(q)
                                matchesCat && matchesQuery
                            }
                        }

                        if (filtered.isEmpty()) {
                            EmptyStateCard(
                                icon = Icons.Default.Pets,
                                title = str(language, "कोई गौवंश रिकॉर्ड नहीं मिला", "No cattle records found"),
                                subtitle = str(
                                    language,
                                    if (session.canAdd(AppModule.CATTLE)) "नया गौवंश पंजीकृत करने के लिए नीचे '+ गौवंश जोड़ें' बटन दबाएं।" else "सार्वजनिक दर्शन के लिए स्वीकृत गौवंश यहाँ दिखेंगे।",
                                    if (session.canAdd(AppModule.CATTLE)) "Tap '+ Add Cattle' to register a cow, bull, or calf." else "Approved public cattle profiles will appear here."
                                ),
                                actionLabel = if (session.canAdd(AppModule.CATTLE)) str(language, "नया गौवंश जोड़ें", "Add Cattle") else null,
                                onAction = if (session.canAdd(AppModule.CATTLE)) {
                                    { editingCattle = null; showFormDialog = true }
                                } else null
                            )
                        } else {
                            val paged = filtered.take(pageLimit)
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 84.dp)
                            ) {
                                items(paged, key = { it.id }) { animal ->
                                    CattleListCard(
                                        cattle = animal,
                                        language = language,
                                        onClick = {
                                            openedFromQrScan = false
                                            selectedCattle = animal
                                        },
                                        onShowQr = { qrCattle = animal }
                                    )
                                }
                                if (filtered.size > paged.size) {
                                    item {
                                        OutlinedButton(
                                            onClick = { pageLimit += 20 },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(str(language, "और दिखाएं (${filtered.size - paged.size} शेष)", "Load More (${filtered.size - paged.size} remaining)"))
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

    qrCattle?.let { animal ->
        QrCodeDialog(
            title = animal.name,
            codeLabel = animal.animalCode,
            payload = animal.qrPayload.ifBlank { QrAndExportUtils.buildCattleQrPayload(animal.id, animal.animalCode) },
            language = language,
            onDismiss = { qrCattle = null },
            primaryActionLabel = str(language, "पूरा विवरण खोलें", "Open Full Details"),
            onPrimaryAction = {
                qrCattle = null
                openedFromQrScan = false
                selectedCattle = animal
            }
        )
    }

    if (showQrLookupDialog) {
        CattleQrScannerDialog(
            language = language,
            isSearching = isQrSearching,
            errorMessage = qrLookupError,
            onClearError = { qrLookupError = null },
            onQrCodeDetected = { rawPayload ->
                scope.launch {
                    isQrSearching = true
                    qrLookupError = null
                    val parsed = QrAndExportUtils.parseCattleQrPayload(rawPayload)
                    if (parsed == null) {
                        isQrSearching = false
                        qrLookupError = str(
                            language,
                            "अमान्य QR कोड। यह जय भोले गौशाला का मान्य गाय QR कोड नहीं है। कृपया सही QR स्कैन करें या Cow ID दर्ज करें।",
                            "Invalid QR code. Please scan a valid Jay Bhole Goshala Cow QR code or enter a valid Cow ID."
                        )
                        return@launch
                    }

                    val lookupResult: Result<Cattle?> = if (onLookupCattleByQr != null) {
                        onLookupCattleByQr(rawPayload)
                    } else {
                        val match = latestCattleList.firstOrNull { cow ->
                            (!parsed.cowDocId.isNullOrBlank() && cow.id == parsed.cowDocId) ||
                                (!parsed.animalCode.isNullOrBlank() && cow.animalCode.equals(parsed.animalCode, ignoreCase = true)) ||
                                cow.qrPayload.equals(parsed.rawValue, ignoreCase = true)
                        }
                        Result.success(match)
                    }

                    isQrSearching = false
                    lookupResult.fold(
                        onSuccess = { found ->
                            if (found != null) {
                                showQrLookupDialog = false
                                qrLookupError = null
                                openedFromQrScan = true
                                selectedCattle = found
                            } else {
                                qrLookupError = str(
                                    language,
                                    "इस QR कोड / Cow ID से कोई गाय रिकॉर्ड नहीं मिला। कृपया सही गाय का QR कोड स्कैन करें।",
                                    "No cow record found for this QR code / Cow ID. Please scan a valid cow QR code."
                                )
                            }
                        },
                        onFailure = { err ->
                            qrLookupError = if (err.message == "INVALID_QR") {
                                str(
                                    language,
                                    "अमान्य QR कोड। कृपया सही गाय का QR कोड स्कैन करें।",
                                    "Invalid QR code. Please scan a valid cow QR code."
                                )
                            } else {
                                str(
                                    language,
                                    "गाय का रिकॉर्ड खोजने में समस्या आई। कृपया इंटरनेट कनेक्शन जांचकर पुनः प्रयास करें।",
                                    "Could not fetch cow record from Firebase. Please check internet connection and try again."
                                )
                            }
                        }
                    )
                }
            },
            onDismiss = {
                showQrLookupDialog = false
                isQrSearching = false
                qrLookupError = null
            }
        )
    }

    deleteTarget?.let { target ->
        ConfirmActionDialog(
            title = str(language, "रिकॉर्ड हटाने की पुष्टि", "Confirm Delete"),
            message = str(
                language,
                "क्या आप वाकई ${target.name} (${target.animalCode}) का रिकॉर्ड हटाना चाहते हैं?",
                "Are you sure you want to delete ${target.name} (${target.animalCode})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteCattle(target)
                if (selectedCattle?.id == target.id) {
                    selectedCattle = null
                    openedFromQrScan = false
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showFormDialog) {
        CattleFormDialog(
            language = language,
            initial = editingCattle,
            onDismiss = { showFormDialog = false },
            onSave = { cattle, isEdit ->
                onSaveCattle(cattle, isEdit)
                showFormDialog = false
            }
        )
    }
}

@Composable
private fun CattleListCard(
    cattle: Cattle,
    language: AppLanguage,
    onClick: () -> Unit,
    onShowQr: () -> Unit
) {
    val statusColor = when (cattle.healthStatus) {
        "HEALTHY" -> GoshalaGreen
        "SICK" -> AlertRed
        else -> Saffron
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("cattle_card_${cattle.animalCode}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!cattle.photoUri.isNullOrBlank()) {
                AsyncImage(
                    model = Uri.parse(cattle.photoUri),
                    contentDescription = cattle.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SoftGreenTint,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Pets, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(30.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = cattle.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = statusColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = cattle.healthStatus,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${cattle.animalCode} • ${cattle.breed} • ${cattle.ageText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${str(language, "शेड", "Shed")}: ${cattle.shedNumber} | ${cattle.weightKg} kg",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(
                onClick = onShowQr,
                modifier = Modifier.testTag("cattle_qr_button_${cattle.animalCode}")
            ) {
                Icon(Icons.Default.QrCode2, contentDescription = "QR", tint = ForestGreen)
            }
        }
    }
}

@Composable
private fun CattleFormDialog(
    language: AppLanguage,
    initial: Cattle?,
    onDismiss: () -> Unit,
    onSave: (Cattle, Boolean) -> Unit
) {
    val isEdit = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "COW") }
    var breed by remember { mutableStateOf(initial?.breed ?: "") }
    var sex by remember { mutableStateOf(initial?.sex ?: "FEMALE") }
    var ageText by remember { mutableStateOf(initial?.ageText ?: "") }
    var birthDate by remember { mutableStateOf(initial?.birthDate ?: "") }
    var colour by remember { mutableStateOf(initial?.colour ?: "") }
    var weightText by remember { mutableStateOf(initial?.weightKg?.takeIf { it > 0 }?.toString() ?: "") }
    var healthStatus by remember { mutableStateOf(initial?.healthStatus ?: "HEALTHY") }
    var entryDate by remember { mutableStateOf(initial?.entryDate ?: QrAndExportUtils.todayIso()) }
    var shedNumber by remember { mutableStateOf(initial?.shedNumber ?: "Shed-A1") }
    var marks by remember { mutableStateOf(initial?.identificationMarks ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var photoUri by remember { mutableStateOf(initial?.photoUri) }
    var publicApproved by remember { mutableStateOf(initial?.publicApproved ?: true) }
    var sponsorshipAvailable by remember { mutableStateOf(initial?.sponsorshipAvailable ?: true) }
    var monthlySupport by remember { mutableStateOf((initial?.monthlySupportInr ?: 2100L).toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) photoUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEdit) str(language, "गौवंश संपादित करें", "Edit Cattle") else str(language, "नया गौवंश पंजीकरण", "Register New Cattle"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (photoUri != null) str(language, "फोटो चयनित (बदलें)", "Photo Selected (Change)")
                        else str(language, "गैलरी से फोटो चुनें", "Select Photo (Photo Picker)")
                    )
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(str(language, "पशु का नाम *", "Animal Name *")) }, singleLine = true)

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("COW" to "गाय", "BULL" to "बैल", "CALF" to "बछड़ा").forEach { (k, l) ->
                        FilterChip(selected = category == k, onClick = {
                            category = k
                            if (k == "COW") sex = "FEMALE"
                        }, label = { Text(l) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sex == "FEMALE", onClick = { sex = "FEMALE" }, label = { Text(str(language, "मादा", "Female")) })
                    FilterChip(selected = sex == "MALE", onClick = { sex = "MALE" }, label = { Text(str(language, "नर", "Male")) })
                }

                OutlinedTextField(value = breed, onValueChange = { breed = it }, label = { Text(str(language, "नस्ल (जैसे: गिर, साहीवाल) *", "Breed *")) }, singleLine = true)
                OutlinedTextField(value = ageText, onValueChange = { ageText = it }, label = { Text(str(language, "आयु (जैसे: 4 वर्ष) *", "Age *")) }, singleLine = true)
                OutlinedTextField(value = birthDate, onValueChange = { birthDate = it }, label = { Text(str(language, "जन्मतिथि (YYYY-MM-DD)", "Birth Date")) }, singleLine = true)
                OutlinedTextField(value = colour, onValueChange = { colour = it }, label = { Text(str(language, "रंग *", "Colour *")) }, singleLine = true)
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text(str(language, "वजन (किग्रा)", "Weight (kg)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                Text(str(language, "स्वास्थ्य स्थिति", "Health Status"), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("HEALTHY" to "स्वस्थ", "SICK" to "बीमार", "UNDER_TREATMENT" to "उपचार").forEach { (k, l) ->
                        FilterChip(selected = healthStatus == k, onClick = { healthStatus = k }, label = { Text(l) })
                    }
                }

                OutlinedTextField(value = entryDate, onValueChange = { entryDate = it }, label = { Text(str(language, "प्रवेश तिथि *", "Entry Date *")) }, singleLine = true)
                OutlinedTextField(value = shedNumber, onValueChange = { shedNumber = it }, label = { Text(str(language, "शेड नंबर *", "Shed Number *")) }, singleLine = true)
                OutlinedTextField(value = marks, onValueChange = { marks = it }, label = { Text(str(language, "पहचान चिह्न", "Identification Marks")) }, singleLine = true)
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text(str(language, "विशेष टिप्पणी", "Notes")) })

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = publicApproved, onCheckedChange = { publicApproved = it })
                    Text(str(language, "सार्वजनिक दर्शन के लिए स्वीकृत", "Approved for Public View"), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sponsorshipAvailable, onCheckedChange = { sponsorshipAvailable = it })
                    Text(str(language, "गौ-गोद (प्रायोजन) हेतु उपलब्ध", "Available for Sponsorship"), style = MaterialTheme.typography.bodySmall)
                }
                if (sponsorshipAvailable) {
                    OutlinedTextField(
                        value = monthlySupport,
                        onValueChange = { monthlySupport = it },
                        label = { Text(str(language, "मासिक सेवा सहयोग (₹)", "Monthly Support (₹)")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }

                errorMsg?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || breed.isBlank() || ageText.isBlank() || colour.isBlank() || entryDate.isBlank() || shedNumber.isBlank()) {
                        errorMsg = str(language, "कृपया सभी आवश्यक (*) विवरण भरें।", "Please fill all required (*) fields.")
                        return@Button
                    }
                    val updated = (initial ?: Cattle()).copy(
                        name = name.trim(),
                        category = category,
                        breed = breed.trim(),
                        sex = sex,
                        ageText = ageText.trim(),
                        birthDate = birthDate.trim(),
                        colour = colour.trim(),
                        weightKg = weightText.toDoubleOrNull() ?: 0.0,
                        healthStatus = healthStatus,
                        entryDate = entryDate.trim(),
                        shedNumber = shedNumber.trim(),
                        identificationMarks = marks.trim(),
                        photoUri = photoUri,
                        notes = notes.trim().ifBlank { null },
                        publicApproved = publicApproved,
                        sponsorshipAvailable = sponsorshipAvailable,
                        monthlySupportInr = monthlySupport.toLongOrNull() ?: 2100L
                    )
                    onSave(updated, isEdit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("cattle_save_button")
            ) {
                Text(str(language, "सहेजें", "Save"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(language, "रद्द करें", "Cancel"))
            }
        }
    )
}

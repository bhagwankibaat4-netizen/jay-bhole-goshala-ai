package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GosalaAsyncResourceImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

@Composable
fun DonationAndAdoptionScreen(
    language: AppLanguage,
    session: UserSession,
    cattleList: List<Cattle>,
    donationsState: DataUiState<List<Donation>>,
    sponsorshipsState: DataUiState<List<Sponsorship>>,
    preselectedCattleForSponsor: Cattle?,
    onClearPreselectedCattle: () -> Unit,
    onSubmitDonation: (Donation) -> Unit,
    onVerifyDonation: (String, String) -> Unit,
    onSubmitSponsorship: (Sponsorship) -> Unit,
    onReviewSponsorship: (Sponsorship, Boolean) -> Unit,
    onUpdateDonation: (Donation) -> Unit = {},
    onDeleteDonation: (String) -> Unit = {},
    onUpdateSponsorship: (Sponsorship) -> Unit = {},
    onDeleteSponsorship: (String) -> Unit = {},
    initialTabIndex: Int = 0,
    onVerifySponsorshipPayment: (Sponsorship, String) -> Unit = { _, _ -> }
) {
    var tabIndex by remember {
        mutableIntStateOf(if (preselectedCattleForSponsor != null || initialTabIndex == 1) 1 else 0)
    }
    var receiptDonation by remember { mutableStateOf<Donation?>(null) }
    var certSponsorship by remember { mutableStateOf<Sponsorship?>(null) }
    var editingDonation by remember { mutableStateOf<Donation?>(null) }
    var deletingDonation by remember { mutableStateOf<Donation?>(null) }
    var editingSponsorship by remember { mutableStateOf<Sponsorship?>(null) }
    var deletingSponsorship by remember { mutableStateOf<Sponsorship?>(null) }

    LaunchedEffect(preselectedCattleForSponsor, initialTabIndex) {
        if (preselectedCattleForSponsor != null || initialTabIndex == 1) tabIndex = 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text(str(language, "गौ सेवा दान", "Donate")) },
                modifier = Modifier.testTag("donations_tab_donate")
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text(str(language, "गौ-गोद (प्रायोजन)", "Sponsor a Cow")) },
                modifier = Modifier.testTag("donations_tab_sponsor")
            )
        }
        Spacer(Modifier.height(10.dp))

        if (tabIndex == 0) {
            DonationTabContent(
                language = language,
                session = session,
                donationsState = donationsState,
                onSubmitDonation = onSubmitDonation,
                onVerifyDonation = onVerifyDonation,
                onOpenReceipt = { receiptDonation = it },
                onEditDonation = { editingDonation = it },
                onDeleteDonation = { deletingDonation = it }
            )
        } else {
            SponsorshipTabContent(
                language = language,
                session = session,
                cattleList = cattleList,
                sponsorshipsState = sponsorshipsState,
                preselectedCattle = preselectedCattleForSponsor,
                onClearPreselected = onClearPreselectedCattle,
                onSubmitSponsorship = onSubmitSponsorship,
                onReviewSponsorship = onReviewSponsorship,
                onVerifySponsorshipPayment = onVerifySponsorshipPayment,
                onOpenCertificate = { certSponsorship = it },
                onEditSponsorship = { editingSponsorship = it },
                onDeleteSponsorship = { deletingSponsorship = it }
            )
        }
    }

    receiptDonation?.let { don ->
        AlertDialog(
            onDismissRequest = { receiptDonation = null },
            title = { Text(str(language, "दान रसीद / Donation Receipt", "Donation Receipt"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(GosalaBrand.NAME_HI, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Text(GosalaBrand.ADDRESS_HI, style = MaterialTheme.typography.labelSmall)
                    HorizontalDivider()
                    Text("${str(language, "रसीद संख्या", "Receipt No.")}: ${don.receiptNumber}")
                    Text("${str(language, "दाता", "Donor")}: ${don.donorName}")
                    Text("${str(language, "राशि", "Amount")}: ₹${don.amountInr.toInt()}", fontWeight = FontWeight.Bold)
                    Text("${str(language, "उद्देश्य", "Purpose")}: ${don.purpose}")
                    Text("${str(language, "संदर्भ", "Ref")}: ${don.paymentProviderRef}")
                    Text("${str(language, "तिथि", "Date")}: ${don.dateStr}")
                    Text(
                        text = "${str(language, "भुगतान स्थिति", "Status")}: ${don.paymentStatus}",
                        color = if (don.paymentStatus == "VERIFIED_SUCCESS") GoshalaGreen else Saffron,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = { TextButton(onClick = { receiptDonation = null }) { Text(str(language, "बंद करें", "Close")) } }
        )
    }

    certSponsorship?.let { sp ->
        AlertDialog(
            onDismissRequest = { certSponsorship = null },
            title = { Text(str(language, "गौ-गोद प्रायोजन प्रमाणपत्र", "Cow Sponsorship Certificate"), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(GosalaBrand.NAME_HI, fontWeight = FontWeight.Bold, color = ForestGreen)
                    Text(GosalaBrand.SUPPORTING_SLOGAN_HI, style = MaterialTheme.typography.labelSmall, color = Saffron)
                    HorizontalDivider()
                    Text("${str(language, "प्रमाणपत्र क्र.", "Certificate No.")}: ${sp.certificateNumber.ifBlank { "-" }}")
                    Text("${str(language, "प्रायोजक", "Sponsor")}: ${sp.applicantName}")
                    Text("${str(language, "गौवंश", "Cow")}: ${sp.cattleName} (${sp.animalCode})", fontWeight = FontWeight.Bold)
                    Text("${str(language, "सहयोग", "Support")}: ₹${sp.monthlySupportInr.toInt()}/माह × ${sp.durationMonths} माह = ₹${sp.totalSupportInr.toInt()}")
                    Text(
                        text = str(
                            language,
                            "नोट: यह एक सेवा प्रायोजन प्रमाणपत्र है। इससे गाय का कानूनी स्वामित्व हस्तांतरित नहीं होता है।",
                            "Note: This is a care sponsorship certificate and does not transfer legal ownership."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { certSponsorship = null }) { Text(str(language, "बंद करें", "Close")) } }
        )
    }

    editingDonation?.let { don ->
        var editName by remember(don.id) { mutableStateOf(don.donorName) }
        var editPhone by remember(don.id) { mutableStateOf(don.donorPhone) }
        var editCity by remember(don.id) { mutableStateOf(don.donorCity) }
        var editAmount by remember(don.id) { mutableStateOf(don.amountInr.toInt().toString()) }
        var editPurpose by remember(don.id) { mutableStateOf(don.purpose) }
        var editRef by remember(don.id) { mutableStateOf(don.paymentProviderRef) }
        var editStatus by remember(don.id) { mutableStateOf(don.paymentStatus) }
        var editErr by remember(don.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingDonation = null },
            title = { Text(str(language, "दान विवरण संपादित करें", "Edit Donation"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editErr?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
                    OutlinedTextField(value = editName, onValueChange = { editName = it; editErr = null }, label = { Text(str(language, "दाता का नाम *", "Donor Name *")) }, singleLine = true)
                    OutlinedTextField(value = editAmount, onValueChange = { editAmount = it; editErr = null }, label = { Text(str(language, "राशि (₹) *", "Amount (₹) *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it }, label = { Text(str(language, "मोबाइल", "Mobile")) }, singleLine = true)
                    OutlinedTextField(value = editCity, onValueChange = { editCity = it }, label = { Text(str(language, "शहर / गाँव", "City / Village")) }, singleLine = true)
                    OutlinedTextField(value = editRef, onValueChange = { editRef = it }, label = { Text(str(language, "UPI / संदर्भ", "Ref")) }, singleLine = true)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GAU_SEVA" to "गौ सेवा", "GREEN_FODDER" to "हरा चारा", "MEDICAL_CARE" to "चिकित्सा").forEach { (k, l) ->
                            FilterChip(selected = editPurpose == k, onClick = { editPurpose = k }, label = { Text(l) })
                        }
                    }
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PENDING_VERIFICATION", "VERIFIED_SUCCESS", "FAILED").forEach { st ->
                            FilterChip(selected = editStatus == st, onClick = { editStatus = st }, label = { Text(st, style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = editAmount.toDoubleOrNull() ?: 0.0
                        if (editName.isBlank() || amt <= 0.0) {
                            editErr = str(language, "सही नाम व राशि दर्ज करें।", "Enter valid donor name and amount.")
                            return@Button
                        }
                        onUpdateDonation(
                            don.copy(
                                donorName = editName.trim(),
                                donorPhone = editPhone.trim(),
                                donorCity = editCity.trim(),
                                amountInr = amt,
                                purpose = editPurpose,
                                paymentProviderRef = editRef.trim().ifBlank { "MANUAL_PENDING" },
                                paymentStatus = editStatus
                            )
                        )
                        editingDonation = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("donation_edit_save_button")
                ) { Text(str(language, "अपडेट करें", "Update")) }
            },
            dismissButton = { TextButton(onClick = { editingDonation = null }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    deletingDonation?.let { don ->
        ConfirmActionDialog(
            title = str(language, "दान रिकॉर्ड हटाएं", "Delete Donation Record"),
            message = str(
                language,
                "क्या आप वाकई ${don.donorName} (₹${don.amountInr.toInt()}) की दान प्रविष्टि हटाना चाहते हैं?",
                "Are you sure you want to delete donation by ${don.donorName} (₹${don.amountInr.toInt()})?"
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteDonation(don.id)
                deletingDonation = null
            },
            onDismiss = { deletingDonation = null }
        )
    }

    editingSponsorship?.let { sp ->
        var editApplicant by remember(sp.id) { mutableStateOf(sp.applicantName) }
        var editPhone by remember(sp.id) { mutableStateOf(sp.applicantPhone) }
        var editAddress by remember(sp.id) { mutableStateOf(sp.applicantAddress) }
        var editMonthly by remember(sp.id) { mutableStateOf(sp.monthlySupportInr.toInt().toString()) }
        var editMonths by remember(sp.id) { mutableStateOf(sp.durationMonths.toString()) }
        var editErr by remember(sp.id) { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { editingSponsorship = null },
            title = { Text(str(language, "गौ-गोद विवरण संपादित करें", "Edit Cow Sponsorship"), fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editErr?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
                    OutlinedTextField(value = editApplicant, onValueChange = { editApplicant = it; editErr = null }, label = { Text(str(language, "आवेदक का नाम *", "Applicant Name *")) }, singleLine = true)
                    OutlinedTextField(value = editPhone, onValueChange = { editPhone = it; editErr = null }, label = { Text(str(language, "मोबाइल नंबर *", "Phone *")) }, singleLine = true)
                    OutlinedTextField(value = editAddress, onValueChange = { editAddress = it }, label = { Text(str(language, "पता *", "Address *")) })
                    OutlinedTextField(value = editMonthly, onValueChange = { editMonthly = it }, label = { Text(str(language, "मासिक सहयोग (₹)", "Monthly Support (₹)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = editMonths, onValueChange = { editMonths = it }, label = { Text(str(language, "अवधि (माह)", "Duration (Months)")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mAmt = editMonthly.toDoubleOrNull() ?: 0.0
                        val mDur = editMonths.toLongOrNull() ?: 0L
                        if (editApplicant.isBlank() || editPhone.trim().length < 6 || mAmt <= 0.0 || mDur < 1L) {
                            editErr = str(language, "कृपया सभी सही विवरण दर्ज करें।", "Enter valid sponsorship details.")
                            return@Button
                        }
                        onUpdateSponsorship(
                            sp.copy(
                                applicantName = editApplicant.trim(),
                                applicantPhone = editPhone.trim(),
                                applicantAddress = editAddress.trim().ifBlank { sp.applicantAddress },
                                monthlySupportInr = mAmt,
                                durationMonths = mDur,
                                totalSupportInr = mAmt * mDur
                            )
                        )
                        editingSponsorship = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                    modifier = Modifier.testTag("sponsorship_edit_save_button")
                ) { Text(str(language, "अपडेट करें", "Update")) }
            },
            dismissButton = { TextButton(onClick = { editingSponsorship = null }) { Text(str(language, "रद्द करें", "Cancel")) } }
        )
    }

    deletingSponsorship?.let { sp ->
        ConfirmActionDialog(
            title = str(language, "गौ-गोद आवेदन हटाएं", "Delete Sponsorship Application"),
            message = str(
                language,
                "क्या आप वाकई ${sp.applicantName} का ${sp.cattleName} हेतु गौ-गोद आवेदन हटाना चाहते हैं?",
                "Are you sure you want to delete sponsorship by ${sp.applicantName} for ${sp.cattleName}?"
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

@Composable
private fun DonationTabContent(
    language: AppLanguage,
    session: UserSession,
    donationsState: DataUiState<List<Donation>>,
    onSubmitDonation: (Donation) -> Unit,
    onVerifyDonation: (String, String) -> Unit,
    onOpenReceipt: (Donation) -> Unit,
    onEditDonation: (Donation) -> Unit,
    onDeleteDonation: (Donation) -> Unit
) {
    var donorName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var donorPhone by remember { mutableStateOf("") }
    var donorCity by remember { mutableStateOf("") }
    var selectedAmount by remember { mutableStateOf("1100") }
    var purpose by remember { mutableStateOf("GAU_SEVA") }
    var refNote by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val canAdd = session.role == UserRole.PUBLIC || session.canAdd(AppModule.DONATIONS)
    val canEdit = session.canEdit(AppModule.DONATIONS)
    val canDelete = session.canDelete(AppModule.DONATIONS)

    LazyColumn(
        modifier = Modifier.testTag("donations_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (canAdd) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(str(language, "गौमाता सेवा हेतु दान करें", "Contribute to Gau Seva"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("501", "1100", "2100", "5100").forEach { amt ->
                                FilterChip(
                                    selected = selectedAmount == amt,
                                    onClick = { selectedAmount = amt },
                                    label = { Text("₹$amt") }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = selectedAmount,
                            onValueChange = { selectedAmount = it },
                            label = { Text(str(language, "दान राशि (₹) *", "Custom Amount (₹) *")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(value = donorName, onValueChange = { donorName = it }, label = { Text(str(language, "दाता का नाम *", "Donor Name *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = donorPhone, onValueChange = { donorPhone = it }, label = { Text(str(language, "मोबाइल नंबर", "Mobile")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = donorCity, onValueChange = { donorCity = it }, label = { Text(str(language, "शहर / गाँव", "City / Village")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("GAU_SEVA" to "गौ सेवा", "GREEN_FODDER" to "हरा चारा", "MEDICAL_CARE" to "चिकित्सा").forEach { (k, l) ->
                                FilterChip(selected = purpose == k, onClick = { purpose = k }, label = { Text(l, style = MaterialTheme.typography.labelSmall) })
                            }
                        }
                        OutlinedTextField(
                            value = refNote,
                            onValueChange = { refNote = it },
                            label = { Text(str(language, "UPI / बैंक संदर्भ संख्या (यदि भुगतान किया हो)", "UPI / Bank Reference (if paid)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = str(
                                language,
                                "सत्यापन नियम: जब तक प्रशासक द्वारा भुगतान की पुष्टि नहीं होती, दान स्थिति 'सत्यापन लंबित' रहेगी।",
                                "Verification Policy: Unverified payments remain 'PENDING_VERIFICATION' until verified."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        errorMsg?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
                        Button(
                            onClick = {
                                val amt = selectedAmount.toDoubleOrNull() ?: 0.0
                                if (amt <= 0 || donorName.isBlank()) {
                                    errorMsg = str(language, "कृपया नाम व सही राशि दर्ज करें।", "Enter donor name and valid amount.")
                                    return@Button
                                }
                                errorMsg = null
                                onSubmitDonation(
                                    Donation(
                                        donorName = donorName.trim(),
                                        donorPhone = donorPhone.trim(),
                                        donorCity = donorCity.trim(),
                                        purpose = purpose,
                                        amountInr = amt,
                                        paymentProviderRef = refNote.trim().ifBlank { "MANUAL_PENDING" }
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("submit_donation_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Saffron)
                        ) {
                            Icon(Icons.Default.VolunteerActivism, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(str(language, "दान दर्ज करें", "Submit Donation"))
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("donations_view_only_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                ) {
                    Text(
                        text = str(
                            language,
                            "आपके खाते को दान रिकॉर्ड केवल देखने (View Only) की अनुमति है। जोड़ने/संपादन/हटाने हेतु प्रशासक से अनुमति लें।",
                            "Your account has View-Only permission for Donations. Contact Admin for Add/Edit/Delete access."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Text(str(language, "दान इतिहास व रसीदें", "Donation History & Receipts"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        when (donationsState) {
            is DataUiState.Loading -> item { CircularProgressIndicator(color = ForestGreen) }
            is DataUiState.Error -> item { EmptyStateCard(Icons.Default.ErrorOutline, "Error", donationsState.message) }
            is DataUiState.Success -> {
                if (donationsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.ReceiptLong,
                            title = str(language, "अभी कोई दान रसीद नहीं", "No donation records yet"),
                            subtitle = str(language, "दान विवरण यहाँ प्रदर्शित होंगे।", "Donation records will appear here.")
                        )
                    }
                } else {
                    items(donationsState.data, key = { it.id }) { don ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${don.donorName} — ₹${don.amountInr.toInt()}", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = don.paymentStatus,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (don.paymentStatus == "VERIFIED_SUCCESS") GoshalaGreen else Saffron,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("${don.receiptNumber} • ${don.purpose} • ${don.dateStr}", style = MaterialTheme.typography.bodySmall)
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(onClick = { onOpenReceipt(don) }) {
                                        Text(str(language, "रसीद देखें", "Receipt"))
                                    }
                                    if (canEdit && don.paymentStatus == "PENDING_VERIFICATION") {
                                        Button(
                                            onClick = { onVerifyDonation(don.id, "VERIFIED_SUCCESS") },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen),
                                            modifier = Modifier.testTag("donation_verify_${don.id}")
                                        ) { Text(str(language, "सत्यापित करें", "Verify")) }
                                        TextButton(
                                            onClick = { onVerifyDonation(don.id, "FAILED") },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                                        ) { Text(str(language, "असफल", "Failed")) }
                                    }
                                    if (canEdit) {
                                        IconButton(
                                            onClick = { onEditDonation(don) },
                                            modifier = Modifier.testTag("donation_edit_${don.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = str(language, "संपादित करें", "Edit"), tint = ForestGreen)
                                        }
                                    }
                                    if (canDelete) {
                                        IconButton(
                                            onClick = { onDeleteDonation(don) },
                                            modifier = Modifier.testTag("donation_delete_${don.id}")
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
}

@Composable
private fun SponsorshipTabContent(
    language: AppLanguage,
    session: UserSession,
    cattleList: List<Cattle>,
    sponsorshipsState: DataUiState<List<Sponsorship>>,
    preselectedCattle: Cattle?,
    onClearPreselected: () -> Unit,
    onSubmitSponsorship: (Sponsorship) -> Unit,
    onReviewSponsorship: (Sponsorship, Boolean) -> Unit,
    onVerifySponsorshipPayment: (Sponsorship, String) -> Unit,
    onOpenCertificate: (Sponsorship) -> Unit,
    onEditSponsorship: (Sponsorship) -> Unit,
    onDeleteSponsorship: (Sponsorship) -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    val sponsorable = cattleList.filter { it.publicApproved && it.sponsorshipAvailable }
    var selectedCow by remember(preselectedCattle, sponsorable) {
        mutableStateOf(preselectedCattle ?: sponsorable.firstOrNull())
    }
    var applicantName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var durationMonths by remember { mutableStateOf("3") }
    var monthlySupport by remember(selectedCow) { mutableStateOf((selectedCow?.monthlySupportInr ?: 2100L).toString()) }
    var message by remember { mutableStateOf("") }
    var paymentMode by remember { mutableStateOf("CASH") }
    var paymentRef by remember { mutableStateOf("") }
    var disclaimerChecked by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val parsedMonths = (durationMonths.toLongOrNull() ?: 3L).coerceIn(1L, 120L)
    val parsedMonthly = (monthlySupport.toDoubleOrNull() ?: (selectedCow?.monthlySupportInr?.toDouble() ?: 2100.0)).coerceAtLeast(100.0)
    val totalAdoptionAmount = (parsedMonthly * parsedMonths).toInt()

    val canAdd = session.role == UserRole.PUBLIC || session.canAdd(AppModule.DONATIONS)
    val canEdit = session.canEdit(AppModule.DONATIONS)
    val canDelete = session.canDelete(AppModule.DONATIONS)

    LazyColumn(
        modifier = Modifier.testTag("sponsorships_lazy_list"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (canAdd) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = str(language, "गौ-गोद योजना (सेवा प्रायोजन) आवेदन", "Cow Adoption (Seva Sponsorship) Application"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        if (sponsorable.isEmpty()) {
                            Text(
                                text = str(language, "अभी कोई स्वीकृत गाय प्रायोजन हेतु सूचीबद्ध नहीं है।", "No public cows are currently listed for sponsorship."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = str(language, "उपलब्ध गाय चुनें (फोटो, ID व विवरण):", "Select an Available Cow (Photo, ID & Details):"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                sponsorable.forEach { cow ->
                                    val isSelected = selectedCow?.id == cow.id
                                    val healthColor = if (cow.healthStatus == "HEALTHY") GoshalaGreen else Saffron
                                    val healthLabel = if (cow.healthStatus == "HEALTHY") {
                                        str(language, "स्वस्थ", "Healthy")
                                    } else {
                                        str(language, "निगरानी में", "Under Care")
                                    }
                                    Card(
                                        onClick = {
                                            selectedCow = cow
                                            monthlySupport = cow.monthlySupportInr.toString()
                                            onClearPreselected()
                                        },
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) ForestGreen else MaterialTheme.colorScheme.outlineVariant
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) SoftGreenTint else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        ),
                                        modifier = Modifier
                                            .width(210.dp)
                                            .testTag("sponsor_cow_card_${cow.animalCode}")
                                    ) {
                                        Column {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(104.dp)
                                            ) {
                                                if (!cow.photoUri.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = cow.photoUri,
                                                        contentDescription = cow.name,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    GosalaAsyncResourceImage(
                                                        resId = R.drawable.img_hero_goshala,
                                                        contentDescription = cow.name,
                                                        contentScale = ContentScale.Crop,
                                                        placeholderColor = SoftGreenTint,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = healthColor,
                                                    modifier = Modifier
                                                        .align(Alignment.TopStart)
                                                        .padding(6.dp)
                                                ) {
                                                    Text(
                                                        text = healthLabel,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (isSelected) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = ForestGreen,
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(6.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = str(language, "चयनित", "Selected"),
                                                            tint = Color.White,
                                                            modifier = Modifier
                                                                .padding(4.dp)
                                                                .size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Column(
                                                modifier = Modifier.padding(10.dp),
                                                verticalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Text(
                                                    text = cow.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ForestGreen,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "ID: ${cow.animalCode}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = DarkText
                                                )
                                                Text(
                                                    text = listOf(cow.breed, cow.ageText).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { str(language, "देशी गौवंश", "Indigenous Cow") },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!cow.notes.isNullOrBlank()) {
                                                    Text(
                                                        text = cow.notes,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "₹${cow.monthlySupportInr} / ${str(language, "माह", "month")}",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Saffron
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            selectedCow?.let { cow ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SoftGoldTint,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sponsor_total_amount_box")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${str(language, "चयनित गाय", "Selected Cow")}: ${cow.name} (${cow.animalCode})",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = DarkText
                                            )
                                            Text(
                                                text = "₹${parsedMonthly.toInt()}/${str(language, "माह", "mo")} × $parsedMonths ${str(language, "माह", "mo")}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = DarkText.copy(alpha = 0.8f)
                                            )
                                        }
                                        Text(
                                            text = "₹$totalAdoptionAmount",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ForestGreen
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = applicantName,
                                onValueChange = { applicantName = it; errorMsg = null },
                                label = { Text(str(language, "आवेदक का नाम *", "Applicant Name *")) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("sponsor_name_input")
                            )
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it; errorMsg = null },
                                label = { Text(str(language, "मोबाइल नंबर *", "Phone *")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("sponsor_phone_input")
                            )
                            OutlinedTextField(
                                value = address,
                                onValueChange = { address = it; errorMsg = null },
                                label = { Text(str(language, "पता *", "Address *")) },
                                modifier = Modifier.fillMaxWidth().testTag("sponsor_address_input")
                            )
                            Text(
                                text = str(language, "गोद लेने की अवधि चुनें:", "Select Adoption Duration:"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("1", "3", "6", "12").forEach { m ->
                                    FilterChip(
                                        selected = durationMonths == m,
                                        onClick = { durationMonths = m },
                                        label = { Text("$m ${str(language, "माह", "Mo")}", maxLines = 1) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("sponsor_duration_$m")
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = monthlySupport,
                                onValueChange = { monthlySupport = it },
                                label = { Text(str(language, "मासिक सेवा राशि (₹)", "Monthly Support (₹)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("sponsor_monthly_input")
                            )
                            OutlinedTextField(
                                value = message,
                                onValueChange = { message = it },
                                label = { Text(str(language, "संदेश / संकल्प (वैकल्पिक)", "Message / Dedication (Optional)")) },
                                modifier = Modifier.fillMaxWidth().testTag("sponsor_message_input")
                            )

                            Text(
                                text = str(language, "भुगतान माध्यम चुनें:", "Select Payment Mode:"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ProductOrder.ALL_PAYMENT_MODES.forEach { mode ->
                                    FilterChip(
                                        selected = paymentMode == mode,
                                        onClick = { paymentMode = mode },
                                        label = { Text(paymentModeLabel(mode, isHindi), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("sponsor_pay_mode_${mode.lowercase()}")
                                    )
                                }
                            }
                            if (paymentMode != "CASH") {
                                OutlinedTextField(
                                    value = paymentRef,
                                    onValueChange = { paymentRef = it },
                                    label = { Text(str(language, "UPI / ऑनलाइन ट्रांज़ैक्शन संदर्भ (यदि भुगतान किया हो)", "UPI / Online Ref (if paid)")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("sponsor_payment_ref_input")
                                )
                            }
                            Text(
                                text = str(
                                    language,
                                    "भुगतान सत्यापन: नकद या ऑनलाइन भुगतान संदर्भ की पुष्टि प्रशासक द्वारा की जाएगी (Pending → Paid → Verified)।",
                                    "Payment Verification: Cash/Online payments are verified by Admin (Pending → Paid → Verified)."
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = disclaimerChecked,
                                    onCheckedChange = { disclaimerChecked = it },
                                    modifier = Modifier.testTag("sponsor_disclaimer_checkbox")
                                )
                                Text(
                                    text = str(
                                        language,
                                        "मैं समझता/समझती हूँ कि गौ-गोद (प्रायोजन) केवल चारा व सेवा सहयोग है, इससे गाय का कानूनी स्वामित्व हस्तांतरित नहीं होता।",
                                        "I understand cow sponsorship supports daily care only and does NOT transfer legal ownership of the cow."
                                    ),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            errorMsg?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall) }
                            Button(
                                onClick = {
                                    val cow = selectedCow
                                    if (cow == null || applicantName.isBlank() || phone.filter { it.isDigit() }.length < 6 || address.isBlank() || !disclaimerChecked) {
                                        errorMsg = str(language, "कृपया सभी विवरण भरें और कानूनी अस्वीकरण स्वीकार करें।", "Fill all fields and accept the ownership disclaimer.")
                                        return@Button
                                    }
                                    errorMsg = null
                                    val initialPayStatus = if (paymentMode != "CASH" && paymentRef.trim().isNotBlank()) "PAID" else "PENDING"
                                    val monthlyVal = monthlySupport.toDoubleOrNull()?.coerceAtLeast(100.0) ?: 2100.0
                                    val monthsVal = durationMonths.toLongOrNull()?.coerceIn(1L, 120L) ?: 3L
                                    onSubmitSponsorship(
                                        Sponsorship(
                                            applicantName = applicantName.trim(),
                                            applicantPhone = phone.trim(),
                                            applicantAddress = address.trim(),
                                            cattleId = cow.id,
                                            cattleName = cow.name,
                                            animalCode = cow.animalCode,
                                            monthlySupportInr = monthlyVal,
                                            durationMonths = monthsVal,
                                            totalSupportInr = monthlyVal * monthsVal,
                                            message = message.trim(),
                                            paymentMode = paymentMode,
                                            paymentStatus = initialPayStatus,
                                            paymentRef = paymentRef.trim(),
                                            disclaimerAccepted = true
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().testTag("submit_sponsorship_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                            ) {
                                Text(str(language, "आवेदन जमा करें (₹$totalAdoptionAmount)", "Submit Application (₹$totalAdoptionAmount)"))
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sponsorships_view_only_banner"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                ) {
                    Text(
                        text = str(
                            language,
                            "आपके खाते को गौ-गोद (गोसेवा) आवेदन केवल देखने (View Only) की अनुमति है।",
                            "Your account has View-Only permission for Cow Sponsorships."
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = DarkText,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Text(str(language, "गौ-गोद आवेदन स्थिति", "Sponsorship Applications"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        when (sponsorshipsState) {
            is DataUiState.Loading -> item { CircularProgressIndicator(color = ForestGreen) }
            is DataUiState.Error -> item { EmptyStateCard(Icons.Default.ErrorOutline, "Error", sponsorshipsState.message) }
            is DataUiState.Success -> {
                if (sponsorshipsState.data.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.Favorite,
                            title = str(language, "कोई गौ-गोद आवेदन नहीं", "No sponsorship applications"),
                            subtitle = str(language, "आवेदन स्वीकृत होने पर यहाँ सेवा प्रमाणपत्र दिखेगा।", "Approved sponsorships generate a service certificate here.")
                        )
                    }
                } else {
                    items(sponsorshipsState.data, key = { it.id }) { sp ->
                        val payColor = when (sp.paymentStatus) {
                            "VERIFIED" -> GoshalaGreen
                            "PAID" -> Color(0xFF1565C0)
                            else -> Saffron
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sponsorship_card_${sp.id}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${sp.cattleName} (${sp.animalCode})", fontWeight = FontWeight.Bold)
                                    Text(
                                        text = sp.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when (sp.status) {
                                            "APPROVED" -> GoshalaGreen
                                            "REJECTED" -> AlertRed
                                            else -> Saffron
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${sp.applicantName} • ₹${sp.monthlySupportInr.toInt()}/माह × ${sp.durationMonths} माह = ₹${sp.totalSupportInr.toInt()}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = payColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${paymentModeLabel(sp.paymentMode, isHindi)} • ${paymentStatusLabel(sp.paymentStatus, isHindi)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = payColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    if (sp.paymentRef.isNotBlank()) {
                                        Text(
                                            text = "Ref: ${sp.paymentRef}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (sp.message.isNotBlank()) {
                                    Text(
                                        text = "${str(language, "संदेश", "Message")}: ${sp.message}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (sp.status == "APPROVED") {
                                        OutlinedButton(onClick = { onOpenCertificate(sp) }) {
                                            Text(str(language, "प्रमाणपत्र", "Certificate"))
                                        }
                                    }
                                    if (canEdit && sp.status == "PENDING") {
                                        Button(
                                            onClick = { onReviewSponsorship(sp, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen),
                                            modifier = Modifier.testTag("sponsorship_approve_${sp.id}")
                                        ) { Text(str(language, "स्वीकृत", "Approve")) }
                                        TextButton(
                                            onClick = { onReviewSponsorship(sp, false) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed),
                                            modifier = Modifier.testTag("sponsorship_reject_${sp.id}")
                                        ) { Text(str(language, "अस्वीकार", "Reject")) }
                                    }
                                    if (canEdit && sp.paymentStatus != "VERIFIED") {
                                        FilledTonalButton(
                                            onClick = { onVerifySponsorshipPayment(sp, "VERIFIED") },
                                            modifier = Modifier.testTag("sponsorship_verify_payment_${sp.id}")
                                        ) {
                                            Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(str(language, "भुगतान सत्यापित", "Verify Pay"))
                                        }
                                    }
                                    if (canEdit) {
                                        IconButton(
                                            onClick = { onEditSponsorship(sp) },
                                            modifier = Modifier.testTag("sponsorship_edit_${sp.id}")
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = str(language, "संपादित करें", "Edit"), tint = ForestGreen)
                                        }
                                    }
                                    if (canDelete) {
                                        IconButton(
                                            onClick = { onDeleteSponsorship(sp) },
                                            modifier = Modifier.testTag("sponsorship_delete_${sp.id}")
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
}

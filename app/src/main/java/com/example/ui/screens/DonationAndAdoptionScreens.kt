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
import com.example.core.GosalaBrand
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.EmptyStateCard
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
    onReviewSponsorship: (Sponsorship, Boolean) -> Unit
) {
    var tabIndex by remember { mutableIntStateOf(if (preselectedCattleForSponsor != null) 1 else 0) }
    var receiptDonation by remember { mutableStateOf<Donation?>(null) }
    var certSponsorship by remember { mutableStateOf<Sponsorship?>(null) }

    LaunchedEffect(preselectedCattleForSponsor) {
        if (preselectedCattleForSponsor != null) tabIndex = 1
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
                text = { Text(str(language, "गौ सेवा दान", "Donate")) }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text(str(language, "गौ-गोद (प्रायोजन)", "Sponsor a Cow")) }
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
                onOpenReceipt = { receiptDonation = it }
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
                onOpenCertificate = { certSponsorship = it }
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
}

@Composable
private fun DonationTabContent(
    language: AppLanguage,
    session: UserSession,
    donationsState: DataUiState<List<Donation>>,
    onSubmitDonation: (Donation) -> Unit,
    onVerifyDonation: (String, String) -> Unit,
    onOpenReceipt: (Donation) -> Unit
) {
    var donorName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var donorPhone by remember { mutableStateOf("") }
    var donorCity by remember { mutableStateOf("") }
    var selectedAmount by remember { mutableStateOf("1100") }
    var purpose by remember { mutableStateOf("GAU_SEVA") }
    var refNote by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val canVerify = session.role == UserRole.ADMIN || session.canEdit(AppModule.DONATIONS)

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
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
                            subtitle = str(language, "ऊपर दिए गए फॉर्म से गौ सेवा सहयोग दर्ज करें।", "Use the form above to record a Gau Seva donation.")
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
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedButton(onClick = { onOpenReceipt(don) }) {
                                        Text(str(language, "रसीद देखें", "Receipt"))
                                    }
                                    if (canVerify && don.paymentStatus == "PENDING_VERIFICATION") {
                                        Button(
                                            onClick = { onVerifyDonation(don.id, "VERIFIED_SUCCESS") },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen)
                                        ) { Text(str(language, "सत्यापित करें", "Verify")) }
                                        TextButton(
                                            onClick = { onVerifyDonation(don.id, "FAILED") },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                                        ) { Text(str(language, "असफल", "Failed")) }
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
    onOpenCertificate: (Sponsorship) -> Unit
) {
    val sponsorable = cattleList.filter { it.publicApproved && it.sponsorshipAvailable }
    var selectedCow by remember(preselectedCattle, sponsorable) {
        mutableStateOf(preselectedCattle ?: sponsorable.firstOrNull())
    }
    var applicantName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var durationMonths by remember { mutableStateOf("3") }
    var monthlySupport by remember(selectedCow) { mutableStateOf((selectedCow?.monthlySupportInr ?: 2100L).toString()) }
    var disclaimerChecked by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(str(language, "गौ-गोद (सेवा प्रायोजन) आवेदन", "Cow Sponsorship Application"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ForestGreen)
                    if (sponsorable.isEmpty()) {
                        Text(
                            text = str(language, "अभी कोई स्वीकृत गाय प्रायोजन हेतु सूचीबद्ध नहीं है।", "No public cows are currently listed for sponsorship."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(str(language, "गौवंश चुनें:", "Select Cow:"), style = MaterialTheme.typography.labelMedium)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            sponsorable.take(4).forEach { cow ->
                                FilterChip(
                                    selected = selectedCow?.id == cow.id,
                                    onClick = {
                                        selectedCow = cow
                                        monthlySupport = cow.monthlySupportInr.toString()
                                        onClearPreselected()
                                    },
                                    label = { Text(cow.name) }
                                )
                            }
                        }
                        OutlinedTextField(value = applicantName, onValueChange = { applicantName = it }, label = { Text(str(language, "आवेदक का नाम *", "Applicant Name *")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(str(language, "मोबाइल नंबर *", "Phone *")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text(str(language, "पता *", "Address *")) }, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("1", "3", "6", "12").forEach { m ->
                                FilterChip(selected = durationMonths == m, onClick = { durationMonths = m }, label = { Text("$m ${str(language, "माह", "Mo")}") })
                            }
                        }
                        OutlinedTextField(
                            value = monthlySupport,
                            onValueChange = { monthlySupport = it },
                            label = { Text(str(language, "मासिक सेवा राशि (₹)", "Monthly Support (₹)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = disclaimerChecked, onCheckedChange = { disclaimerChecked = it })
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
                                if (cow == null || applicantName.isBlank() || phone.length < 6 || address.isBlank() || !disclaimerChecked) {
                                    errorMsg = str(language, "कृपया सभी विवरण भरें और कानूनी अस्वीकरण स्वीकार करें।", "Fill all fields and accept the ownership disclaimer.")
                                    return@Button
                                }
                                errorMsg = null
                                onSubmitSponsorship(
                                    Sponsorship(
                                        applicantName = applicantName.trim(),
                                        applicantPhone = phone.trim(),
                                        applicantAddress = address.trim(),
                                        cattleId = cow.id,
                                        cattleName = cow.name,
                                        animalCode = cow.animalCode,
                                        monthlySupportInr = monthlySupport.toDoubleOrNull() ?: 2100.0,
                                        durationMonths = durationMonths.toLongOrNull() ?: 3L,
                                        disclaimerAccepted = true
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth().testTag("submit_sponsorship_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Text(str(language, "आवेदन जमा करें", "Submit Application"))
                        }
                    }
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
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
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
                                Text("${sp.applicantName} • ₹${sp.monthlySupportInr.toInt()}/माह × ${sp.durationMonths} माह", style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    if (sp.status == "APPROVED") {
                                        OutlinedButton(onClick = { onOpenCertificate(sp) }) {
                                            Text(str(language, "प्रमाणपत्र", "Certificate"))
                                        }
                                    }
                                    if (session.role == UserRole.ADMIN && sp.status == "PENDING") {
                                        Button(
                                            onClick = { onReviewSponsorship(sp, true) },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoshalaGreen)
                                        ) { Text(str(language, "स्वीकृत", "Approve")) }
                                        TextButton(
                                            onClick = { onReviewSponsorship(sp, false) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = AlertRed)
                                        ) { Text(str(language, "अस्वीकार", "Reject")) }
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

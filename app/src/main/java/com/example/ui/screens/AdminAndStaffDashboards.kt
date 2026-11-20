package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.GosalaOfficialLogo
import com.example.ui.components.MetricStatCard
import com.example.ui.components.ModuleGridCard
import com.example.ui.components.RoleBadgeChip
import com.example.ui.theme.*

@Composable
fun AdminDashboardScreen(
    language: AppLanguage,
    session: UserSession,
    cattle: List<Cattle>,
    health: List<HealthRecord>,
    feedLogs: List<FeedAndOperationLog>,
    staffAccounts: List<StaffAccount>,
    finance: List<FinancialTransaction>,
    donations: List<Donation>,
    sponsorships: List<Sponsorship>,
    compost: List<CompostBatch>,
    onNavigate: (String) -> Unit,
    onQuickAddCattle: () -> Unit,
    onQuickAddFeed: () -> Unit,
    onQuickAddExpense: () -> Unit,
    productOrders: List<ProductOrder> = emptyList()
) {
    val today = QrAndExportUtils.todayIso()
    val cowCount = cattle.count { it.category == "COW" }
    val bullCount = cattle.count { it.category == "BULL" }
    val calfCount = cattle.count { it.category == "CALF" }
    val sickCount = cattle.count { it.healthStatus != "HEALTHY" }
    val todayFeedKg = feedLogs.filter { it.logDate == today }
        .sumOf { it.greenFodderKg + it.dryFodderBhusaKg + it.concentrateKg }
    val todayIncome = finance.filter { it.txType == "INCOME" && it.dateStr == today }.sumOf { it.amountInr }
    val todayExpense = finance.filter { it.txType == "EXPENSE" && it.dateStr == today }.sumOf { it.amountInr }
    val verifiedDonations = donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
    val pendingDonations = donations.count { it.paymentStatus == "PENDING_VERIFICATION" }
    val pendingSponsorships = sponsorships.count { it.status == "PENDING" }
    val pendingOrders = productOrders.count { it.orderStatus == "NEW" }
    val activeStaff = staffAccounts.count { it.active }
    val readyStock = compost.sumOf { it.stockRemaining }.toInt()

    val adminModules = listOf(
        HomeModuleTile(Routes.APPLICATIONS, str(language, "उत्पाद व गोद\nआवेदन", "Orders &\nAdoptions"), Icons.Default.ShoppingBag, Saffron, SoftSaffronTint, "admin_mod_applications"),
        HomeModuleTile(Routes.CATTLE, str(language, "गाय-बैल\nप्रबंधन व QR", "Cattle, Edit\n& QR Cards"), Icons.Default.Pets, ForestGreen, SoftGreenTint, "admin_mod_cattle"),
        HomeModuleTile(Routes.HEALTH, str(language, "स्वास्थ्य व\nटीकाकरण", "Health &\nVaccines"), Icons.Default.MedicalServices, AlertRed, SoftRedTint, "admin_mod_health"),
        HomeModuleTile(Routes.FEED, str(language, "चारा, पानी व\nदैनिक कार्य", "Feed, Water\n& Daily Ops"), Icons.Default.Grass, GoshalaGreen, SoftGreenTint, "admin_mod_feed"),
        HomeModuleTile(Routes.STAFF, str(language, "कर्मचारी व\nअनुमतियाँ", "Staff, Tasks\n& Salary"), Icons.Default.Groups, Color(0xFF3949AB), Color(0xFFE8EAF6), "admin_mod_staff"),
        HomeModuleTile(Routes.FINANCE, str(language, "वित्त आय-\nव्यय बही", "Income &\nExpenses"), Icons.Default.AccountBalanceWallet, Color(0xFFB7791F), SoftGoldTint, "admin_mod_finance"),
        HomeModuleTile(Routes.DONATIONS, str(language, "दान व गौ-गोद\nस्वीकृति", "Donations &\nApprovals"), Icons.Default.VolunteerActivism, Saffron, SoftSaffronTint, "admin_mod_donations"),
        HomeModuleTile(Routes.FARMERS, str(language, "किसान व्यापार\nव बहीखाता", "Farmer\nLedger"), Icons.Default.Agriculture, GoshalaGreen, SoftGreenTint, "admin_mod_farmers"),
        HomeModuleTile(Routes.PRODUCTION, str(language, "गोबर खाद व\nउत्पाद बिक्री", "Compost &\nProduct Sales"), Icons.Default.Spa, Color(0xFF6D4C41), Color(0xFFEFEBE9), "admin_mod_production"),
        HomeModuleTile(Routes.REPORTS, str(language, "सभी 13 रिपोर्ट\nPDF / Excel", "13 Reports\nPDF / Excel"), Icons.Default.Assessment, ForestGreen, SoftGoldTint, "admin_mod_reports"),
        HomeModuleTile(Routes.NOTIFICATIONS, str(language, "सूचनाएं एवं\nरिमाइंडर", "Alerts &\nReminders"), Icons.Default.NotificationsActive, Saffron, SoftSaffronTint, "admin_mod_notifications"),
        HomeModuleTile(Routes.AI_ASSISTANT, str(language, "AI प्रशासक\nसारांश", "AI Admin\nAssistant"), Icons.Default.AutoAwesome, ForestGreen, SoftGreenTint, "admin_mod_ai"),
        HomeModuleTile(Routes.PROFILE, str(language, "सेटिंग्स व\nऑडिट लॉग", "Settings &\nAudit Trail"), Icons.Default.AdminPanelSettings, Color(0xFF3949AB), Color(0xFFE8EAF6), "admin_mod_settings")
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("admin_dashboard_root"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Dedicated Admin Control Header + Devotional Identity
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Golden, RoundedCornerShape(22.dp))
                    .testTag("admin_header_card"),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ForestGreen, Color(0xFF196141))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GosalaOfficialLogo(
                                size = 58.dp,
                                innerPadding = 2.dp,
                                borderWidth = 2.dp,
                                testTag = "admin_official_logo"
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = str(language, "प्रशासक नियंत्रण कक्ष (Admin)", "Admin Control Dashboard"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Golden
                                )
                                Text(
                                    text = "${session.displayName} • ${GosalaBrand.NAME_HI}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            RoleBadgeChip(role = session.role, language = language)
                        }
                        Text(
                            text = str(language, GosalaBrand.MAIN_SLOGAN_HI_1, "Grace of Mahadev, Service to Gaumata") +
                                " — " + str(language, GosalaBrand.SHORT_ADDRESS_HI, GosalaBrand.SHORT_ADDRESS_EN),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // 2. Pending Approvals Banner (Product Orders, Donations & Cow Sponsorships)
        if (pendingOrders > 0 || pendingDonations > 0 || pendingSponsorships > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onNavigate(if (pendingOrders > 0 || pendingSponsorships > 0) Routes.APPLICATIONS else Routes.DONATIONS)
                        }
                        .testTag("admin_pending_approvals_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftSaffronTint)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PendingActions, contentDescription = null, tint = Saffron, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str(language, "प्रशासक अनुमोदन प्रतीक्षित", "Pending Admin Approvals"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = str(
                                    language,
                                    "नए उत्पाद ऑर्डर: $pendingOrders | गौ-गोद आवेदन: $pendingSponsorships | दान: $pendingDonations",
                                    "New Product Orders: $pendingOrders | Adoptions: $pendingSponsorships | Donations: $pendingDonations"
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkText
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Saffron)
                    }
                }
            }
        }

        // 3. Admin KPI Statistics Grid (Live Firestore Data)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "कुल गौवंश", "Total Cattle"),
                        value = cattle.size.toString(),
                        subtitle = str(language, "गाय $cowCount • बैल $bullCount • बछड़ा $calfCount", "Cow $cowCount • Bull $bullCount • Calf $calfCount"),
                        icon = Icons.Default.Pets,
                        accentColor = ForestGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.CATTLE) }
                    )
                    MetricStatCard(
                        title = str(language, "स्वास्थ्य निगरानी", "Health Alerts"),
                        value = sickCount.toString(),
                        subtitle = str(language, "${health.size} उपचार रिकॉर्ड", "${health.size} health logs"),
                        icon = Icons.Default.LocalHospital,
                        accentColor = AlertRed,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.HEALTH) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "आज की आय / खर्च", "Today In / Out"),
                        value = "₹${todayIncome.toInt()} / ₹${todayExpense.toInt()}",
                        subtitle = str(language, "${finance.size} कुल लेन-देन", "${finance.size} total entries"),
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = Saffron,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.FINANCE) }
                    )
                    MetricStatCard(
                        title = str(language, "सत्यापित दान", "Verified Donations"),
                        value = "₹${verifiedDonations.toInt()}",
                        subtitle = str(language, "लंबित सत्यापन: $pendingDonations", "Pending verify: $pendingDonations"),
                        icon = Icons.Default.VolunteerActivism,
                        accentColor = Golden,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.DONATIONS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "आज का चारा", "Today's Feed"),
                        value = "${todayFeedKg.toInt()} kg",
                        subtitle = str(language, "${feedLogs.size} दैनिक लॉग", "${feedLogs.size} daily logs"),
                        icon = Icons.Default.Grass,
                        accentColor = GoshalaGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.FEED) }
                    )
                    MetricStatCard(
                        title = str(language, "स्टाफ व खाद स्टॉक", "Staff & Compost"),
                        value = "$activeStaff / ${readyStock}u",
                        subtitle = str(language, "सक्रिय स्टाफ | खाद स्टॉक", "Active Staff | Compost Stock"),
                        icon = Icons.Default.Groups,
                        accentColor = Color(0xFF3949AB),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.STAFF) }
                    )
                }
            }
        }

        // 4. Quick Admin Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = str(language, "+ त्वरित प्रशासक कार्य", "+ Quick Admin Actions"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Golden
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AdminQuickBtn(
                            label = str(language, "गाय जोड़ें", "Add Cow"),
                            icon = Icons.Default.AddCircleOutline,
                            onClick = onQuickAddCattle,
                            modifier = Modifier.weight(1f),
                            testTag = "admin_quick_add_cattle"
                        )
                        AdminQuickBtn(
                            label = str(language, "चारा लॉग", "Log Feed"),
                            icon = Icons.Default.Grass,
                            onClick = onQuickAddFeed,
                            modifier = Modifier.weight(1f),
                            testTag = "admin_quick_add_feed"
                        )
                        AdminQuickBtn(
                            label = str(language, "आय/खर्च", "Ledger"),
                            icon = Icons.Default.ReceiptLong,
                            onClick = onQuickAddExpense,
                            modifier = Modifier.weight(1f),
                            testTag = "admin_quick_add_finance"
                        )
                        AdminQuickBtn(
                            label = str(language, "स्टाफ", "Staff"),
                            icon = Icons.Default.PersonAdd,
                            onClick = { onNavigate(Routes.STAFF) },
                            modifier = Modifier.weight(1f),
                            testTag = "admin_quick_staff"
                        )
                    }
                }
            }
        }

        // 5. All 16 Admin Features Grid
        item {
            Text(
                text = str(language, "संपूर्ण गौशाला प्रशासन मॉड्यूल (Admin)", "All Admin Goshala Modules"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(adminModules.chunked(3)) { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { tile ->
                    ModuleGridCard(
                        title = tile.title,
                        icon = tile.icon,
                        tintColor = tile.tint,
                        bgTint = tile.bgTint,
                        testTag = tile.testTag,
                        onClick = { onNavigate(tile.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun StaffDashboardScreen(
    language: AppLanguage,
    session: UserSession,
    cattle: List<Cattle>,
    feedLogs: List<FeedAndOperationLog>,
    onNavigate: (String) -> Unit,
    onQuickAddCattle: () -> Unit,
    onQuickAddFeed: () -> Unit,
    onQuickAddExpense: () -> Unit,
    health: List<HealthRecord> = emptyList(),
    finance: List<FinancialTransaction> = emptyList(),
    donations: List<Donation> = emptyList(),
    sponsorships: List<Sponsorship> = emptyList(),
    farmers: List<FarmerRecord> = emptyList(),
    compost: List<CompostBatch> = emptyList()
) {
    val modules = buildList {
        if (session.canView(AppModule.CATTLE)) {
            add(HomeModuleTile(Routes.CATTLE, str(language, "गाय/पशु\nप्रबंधन", "Cattle\nRegistry"), Icons.Default.Pets, ForestGreen, SoftGreenTint, "staff_mod_cattle"))
        }
        if (session.canView(AppModule.HEALTH)) {
            add(HomeModuleTile(Routes.HEALTH, str(language, "स्वास्थ्य एवं\nउपचार", "Health &\nVet Care"), Icons.Default.MedicalServices, AlertRed, SoftRedTint, "staff_mod_health"))
        }
        if (session.canView(AppModule.FEED)) {
            add(HomeModuleTile(Routes.FEED, str(language, "चारा व दैनिक\nरजिस्टर", "Feed &\nDaily Ops"), Icons.Default.Grass, GoshalaGreen, SoftGreenTint, "staff_mod_feed"))
        }
        if (session.canView(AppModule.DONATIONS)) {
            add(HomeModuleTile(Routes.DONATIONS, str(language, "दान एवं\nगोसेवा योजना", "Donations &\nGo-Seva"), Icons.Default.VolunteerActivism, Saffron, SoftSaffronTint, "staff_mod_donations"))
        }
        if (session.canView(AppModule.FINANCE)) {
            add(HomeModuleTile(Routes.FINANCE, str(language, "खर्च रजिस्टर\nव वित्त", "Finance\nLedger"), Icons.Default.AccountBalanceWallet, Color(0xFFB7791F), SoftGoldTint, "staff_mod_finance"))
        }
        if (session.canView(AppModule.FARMERS)) {
            add(HomeModuleTile(Routes.FARMERS, str(language, "किसान\nसहयोग", "Farmer\nTrade"), Icons.Default.Agriculture, GoshalaGreen, SoftGreenTint, "staff_mod_farmers"))
        }
        if (session.canView(AppModule.PRODUCTION)) {
            add(HomeModuleTile(Routes.PRODUCTION, str(language, "गोबर खाद\nउत्पाद", "Compost &\nProducts"), Icons.Default.Spa, Color(0xFF6D4C41), Color(0xFFEFEBE9), "staff_mod_production"))
        }
        if (session.canView(AppModule.STAFF)) {
            add(HomeModuleTile(Routes.STAFF, str(language, "स्टाफ उपस्थिति\nव कार्य", "Staff\nOperations"), Icons.Default.EventAvailable, Color(0xFF3949AB), Color(0xFFE8EAF6), "staff_mod_staff"))
        }
        if (session.canView(AppModule.REPORTS)) {
            add(HomeModuleTile(Routes.REPORTS, str(language, "रिपोर्ट व\nनिर्यात", "Reports &\nExports"), Icons.Default.Assessment, ForestGreen, SoftGoldTint, "staff_mod_reports"))
        }
        if (session.canView(AppModule.AI)) {
            add(HomeModuleTile(Routes.AI_ASSISTANT, str(language, "AI गौशाला\nसहायक", "AI\nAssistant"), Icons.Default.AutoAwesome, Saffron, SoftSaffronTint, "staff_mod_ai"))
        }
    }

    val today = QrAndExportUtils.todayIso()
    val pendingTasks = feedLogs.count { it.taskTitle.isNotBlank() && !it.taskCompleted }
    val verifiedDonations = donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
    val pendingApprovals = donations.count { it.paymentStatus == "PENDING_VERIFICATION" } + sponsorships.count { it.status == "PENDING" }
    val todayExpense = finance.filter { it.txType == "EXPENSE" && it.dateStr == today }.sumOf { it.amountInr }
    val sickCattle = cattle.count { it.healthStatus != "HEALTHY" }
    val compostStock = compost.sumOf { it.stockRemaining }.toInt()

    data class StaffKpi(
        val title: String,
        val value: String,
        val subtitle: String,
        val icon: ImageVector,
        val accent: Color,
        val route: String
    )

    val kpis = buildList {
        if (session.canView(AppModule.CATTLE)) {
            add(
                StaffKpi(
                    title = str(language, "कुल गौवंश", "Total Cattle"),
                    value = cattle.size.toString(),
                    subtitle = str(language, "पंजीकृत पशु", "Registered"),
                    icon = Icons.Default.Pets,
                    accent = ForestGreen,
                    route = Routes.CATTLE
                )
            )
        }
        if (session.canView(AppModule.FEED)) {
            add(
                StaffKpi(
                    title = str(language, "लंबित कार्य", "Pending Tasks"),
                    value = pendingTasks.toString(),
                    subtitle = str(language, "दैनिक चारा संचालन", "Daily Feed Ops"),
                    icon = Icons.Default.TaskAlt,
                    accent = Saffron,
                    route = Routes.FEED
                )
            )
        }
        if (session.canView(AppModule.DONATIONS)) {
            add(
                StaffKpi(
                    title = str(language, "दान व गोसेवा", "Donations & Seva"),
                    value = "₹${verifiedDonations.toInt()}",
                    subtitle = str(language, "लंबित जांच: $pendingApprovals", "Pending review: $pendingApprovals"),
                    icon = Icons.Default.VolunteerActivism,
                    accent = Golden,
                    route = Routes.DONATIONS
                )
            )
        }
        if (session.canView(AppModule.HEALTH)) {
            add(
                StaffKpi(
                    title = str(language, "स्वास्थ्य रिकॉर्ड", "Health Records"),
                    value = health.size.toString(),
                    subtitle = str(language, "निगरानी पशु: $sickCattle", "Under care: $sickCattle"),
                    icon = Icons.Default.LocalHospital,
                    accent = AlertRed,
                    route = Routes.HEALTH
                )
            )
        }
        if (session.canView(AppModule.FINANCE)) {
            add(
                StaffKpi(
                    title = str(language, "आज का खर्च", "Today Expense"),
                    value = "₹${todayExpense.toInt()}",
                    subtitle = str(language, "${finance.size} कुल प्रविष्टि", "${finance.size} total entries"),
                    icon = Icons.Default.AccountBalanceWallet,
                    accent = Color(0xFFB7791F),
                    route = Routes.FINANCE
                )
            )
        }
        if (session.canView(AppModule.FARMERS)) {
            add(
                StaffKpi(
                    title = str(language, "किसान सहयोग", "Farmers"),
                    value = farmers.size.toString(),
                    subtitle = str(language, "पंजीकृत किसान", "Registered"),
                    icon = Icons.Default.Agriculture,
                    accent = GoshalaGreen,
                    route = Routes.FARMERS
                )
            )
        }
        if (session.canView(AppModule.PRODUCTION)) {
            add(
                StaffKpi(
                    title = str(language, "खाद स्टॉक", "Compost Stock"),
                    value = "${compostStock}u",
                    subtitle = str(language, "${compost.size} बैच", "${compost.size} batches"),
                    icon = Icons.Default.Spa,
                    accent = Color(0xFF6D4C41),
                    route = Routes.PRODUCTION
                )
            )
        }
    }

    data class StaffQuickAction(
        val label: String,
        val icon: ImageVector,
        val testTag: String,
        val onClick: () -> Unit
    )

    val quickActions = buildList {
        if (session.canAdd(AppModule.CATTLE)) {
            add(StaffQuickAction(str(language, "गाय जोड़ें", "Add Cattle"), Icons.Default.AddCircleOutline, "staff_quick_add_cattle", onQuickAddCattle))
        }
        if (session.canAdd(AppModule.FEED)) {
            add(StaffQuickAction(str(language, "चारा दर्ज", "Log Feed"), Icons.Default.Grass, "staff_quick_add_feed", onQuickAddFeed))
        }
        if (session.canAdd(AppModule.FINANCE)) {
            add(StaffQuickAction(str(language, "खर्च/आय", "Expense"), Icons.Default.ReceiptLong, "staff_quick_add_finance", onQuickAddExpense))
        }
        if (session.canAdd(AppModule.DONATIONS)) {
            add(StaffQuickAction(str(language, "दान दर्ज", "Donation"), Icons.Default.VolunteerActivism, "staff_quick_add_donation") { onNavigate(Routes.DONATIONS) })
        }
        if (session.canAdd(AppModule.HEALTH)) {
            add(StaffQuickAction(str(language, "स्वास्थ्य", "Health"), Icons.Default.MedicalServices, "staff_quick_add_health") { onNavigate(Routes.HEALTH) })
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("staff_dashboard_root"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Golden, RoundedCornerShape(20.dp))
                    .testTag("staff_header_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GosalaOfficialLogo(
                            size = 54.dp,
                            innerPadding = 2.dp,
                            borderWidth = 2.dp,
                            testTag = "staff_official_logo"
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = str(language, "कर्मचारी कार्य डैशबोर्ड", "Employee Operations Dashboard"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Golden
                            )
                            Text(
                                text = "${session.displayName} • ${session.designation.ifBlank { str(language, "गौ सेवक", "Goshala Staff") }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        RoleBadgeChip(role = session.role, language = language)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (session.employeeId.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.16f),
                                modifier = Modifier.testTag("staff_employee_id_badge")
                            ) {
                                Text(
                                    text = "ID: ${session.employeeId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Golden,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoshalaGreen,
                            modifier = Modifier.testTag("staff_active_status_badge")
                        ) {
                            Text(
                                text = str(language, "सक्रिय (Active)", "Active"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = str(language, "स्वीकृत मॉड्यूल: ${modules.size}", "Modules: ${modules.size}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        if (kpis.isNotEmpty()) {
            items(kpis.chunked(2)) { rowKpis ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowKpis.forEach { kpi ->
                        MetricStatCard(
                            title = kpi.title,
                            value = kpi.value,
                            subtitle = kpi.subtitle,
                            icon = kpi.icon,
                            accentColor = kpi.accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(kpi.route) }
                        )
                    }
                    if (rowKpis.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (quickActions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_quick_actions_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ForestGreen)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = str(language, "+ त्वरित कर्मचारी कार्य (स्वीकृत)", "+ Quick Employee Actions"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Golden
                        )
                        quickActions.chunked(3).forEach { rowActions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowActions.forEach { action ->
                                    AdminQuickBtn(
                                        label = action.label,
                                        icon = action.icon,
                                        onClick = action.onClick,
                                        modifier = Modifier.weight(1f),
                                        testTag = action.testTag
                                    )
                                }
                                repeat(3 - rowActions.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = str(language, "आपके स्वीकृत मॉड्यूल (Role-Based)", "Your Assigned Modules"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (modules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_no_modules_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = str(language, "अभी कोई मॉड्यूल आवंटित नहीं है", "No Modules Assigned Yet"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = str(
                                language,
                                "कृपया गौशाला प्रशासक (Admin) से संपर्क कर अपने कार्य मॉड्यूल व अनुमतियाँ सक्रिय कराएँ।",
                                "Please ask the Goshala Admin to assign your work modules and permissions."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkText
                        )
                    }
                }
            }
        } else {
            items(modules.chunked(3)) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { tile ->
                        ModuleGridCard(
                            title = tile.title,
                            icon = tile.icon,
                            tintColor = tile.tint,
                            bgTint = tile.bgTint,
                            testTag = tile.testTag,
                            onClick = { onNavigate(tile.route) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminQuickBtn(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Golden, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

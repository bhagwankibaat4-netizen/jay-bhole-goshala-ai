package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.ui.components.GosalaAsyncResourceImage
import com.example.ui.components.GosalaOfficialLogo
import com.example.ui.components.MetricStatCard
import com.example.ui.components.ModuleGridCard
import com.example.ui.components.RoleBadgeChip
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

data class HomeModuleTile(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val tint: Color,
    val bgTint: Color,
    val testTag: String
)

@Composable
fun HomeScreen(
    language: AppLanguage,
    session: UserSession,
    summary: PublicSummary,
    cattle: List<Cattle>,
    feedLogs: List<FeedAndOperationLog>,
    finance: List<FinancialTransaction>,
    donations: List<Donation>,
    onNavigate: (String) -> Unit,
    onQuickAddCattle: () -> Unit,
    onQuickAddFeed: () -> Unit,
    onQuickAddExpense: () -> Unit,
    productOrdersState: DataUiState<List<ProductOrder>> = DataUiState.Success(emptyList()),
    onSubmitProductOrder: (ProductOrder) -> Unit = {},
    onOpenSponsorTab: (() -> Unit)? = null
) {
    val isPublic = session.role == UserRole.PUBLIC
    val today = QrAndExportUtils.todayIso()

    val publicProducts = remember { defaultPublicGoshalaProducts() }
    var selectedProductCategory by remember { mutableStateOf("ALL") }
    val filteredPublicProducts = remember(publicProducts, selectedProductCategory) {
        if (selectedProductCategory == "ALL") publicProducts
        else publicProducts.filter { it.categoryKey == selectedProductCategory }
    }
    var purchasingProduct by remember { mutableStateOf<PublicGoshalaProduct?>(null) }
    var showFarmerDialog by remember { mutableStateOf(false) }
    var latestRequestBanner by remember { mutableStateOf<PublicRequestSummary?>(null) }

    val totalCattle = if (isPublic) summary.totalCattle.toInt().coerceAtLeast(cattle.size) else cattle.size
    val healthyCattle = if (isPublic) summary.healthyCattle.toInt().coerceAtLeast(cattle.count { it.healthStatus == "HEALTHY" })
    else cattle.count { it.healthStatus == "HEALTHY" }
    val sickCattle = if (isPublic) summary.sickCattle.toInt() else cattle.count { it.healthStatus != "HEALTHY" }

    val todayFeedKg = if (isPublic) summary.todayFeedKg else {
        feedLogs.filter { it.logDate == today }
            .sumOf { it.greenFodderKg + it.dryFodderBhusaKg + it.concentrateKg }
    }
    val todayExpense = if (isPublic) summary.todayExpenseInr else {
        finance.filter { it.txType == "EXPENSE" && it.dateStr == today }
            .sumOf { it.amountInr }
    }
    val verifiedDonations = if (isPublic) {
        donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
    } else {
        donations.filter { it.paymentStatus == "VERIFIED_SUCCESS" }.sumOf { it.amountInr }
    }

    val modules = buildList {
        if (session.canView(AppModule.CATTLE)) {
            add(
                HomeModuleTile(
                    route = Routes.CATTLE,
                    title = str(language, "गाय-बैल\nप्रबंधन", "Cattle\nRegistry"),
                    icon = Icons.Default.Pets,
                    tint = ForestGreen,
                    bgTint = SoftGreenTint,
                    testTag = "module_cattle"
                )
            )
        }
        if (session.canView(AppModule.FEED) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.FEED,
                    title = str(language, "चारा व\nदैनिक कार्य", "Feed &\nDaily Ops"),
                    icon = Icons.Default.Grass,
                    tint = GoshalaGreen,
                    bgTint = SoftGreenTint,
                    testTag = "module_feed"
                )
            )
        }
        if (session.canView(AppModule.HEALTH) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.HEALTH,
                    title = str(language, "स्वास्थ्य व\nउपचार", "Health &\nVet Care"),
                    icon = Icons.Default.MedicalServices,
                    tint = AlertRed,
                    bgTint = SoftRedTint,
                    testTag = "module_health"
                )
            )
        }
        if ((session.role == UserRole.ADMIN || session.canView(AppModule.STAFF)) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.STAFF,
                    title = str(language, "कर्मचारी\nप्रबंधन", "Staff &\nPermissions"),
                    icon = Icons.Default.Groups,
                    tint = Color(0xFF3949AB),
                    bgTint = Color(0xFFE8EAF6),
                    testTag = "module_staff"
                )
            )
        }
        if (session.canView(AppModule.FINANCE) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.FINANCE,
                    title = str(language, "वित्त\nआय-व्यय", "Finance\nLedger"),
                    icon = Icons.Default.AccountBalanceWallet,
                    tint = Color(0xFFB7791F),
                    bgTint = SoftGoldTint,
                    testTag = "module_finance"
                )
            )
        }
        if (session.canView(AppModule.FARMERS) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.FARMERS,
                    title = str(language, "किसान\nसहयोग", "Farmer\nTrade"),
                    icon = Icons.Default.Agriculture,
                    tint = GoshalaGreen,
                    bgTint = SoftGreenTint,
                    testTag = "module_farmers"
                )
            )
        }
        if (session.canView(AppModule.PRODUCTION) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.PRODUCTION,
                    title = str(language, "गोबर उत्पाद\nव खाद", "Compost &\nProducts"),
                    icon = Icons.Default.Spa,
                    tint = Color(0xFF6D4C41),
                    bgTint = Color(0xFFEFEBE9),
                    testTag = "module_production"
                )
            )
        }
        add(
            HomeModuleTile(
                route = Routes.DONATIONS,
                title = str(language, "दान व\nगौ-गोद", "Donate &\nSponsor"),
                icon = Icons.Default.VolunteerActivism,
                tint = Saffron,
                bgTint = SoftSaffronTint,
                testTag = "module_donations"
            )
        )
        if ((session.role == UserRole.ADMIN || session.canView(AppModule.REPORTS)) && !isPublic) {
            add(
                HomeModuleTile(
                    route = Routes.REPORTS,
                    title = str(language, "रिपोर्ट और\nनिर्यात", "Reports &\nExports"),
                    icon = Icons.Default.Assessment,
                    tint = ForestGreen,
                    bgTint = SoftGoldTint,
                    testTag = "module_reports"
                )
            )
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("public_dashboard_root"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. DEVOTIONAL HERO SECTION (Lord Shiva, Cow, Trees, Golden Sunlight)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_hero_card"),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(258.dp)
                ) {
                    GosalaAsyncResourceImage(
                        resId = R.drawable.img_hero_goshala,
                        contentDescription = str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        placeholderColor = ForestGreen,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.08f),
                                        Color.Transparent,
                                        ForestGreen.copy(alpha = 0.72f),
                                        ForestGreen.copy(alpha = 0.96f)
                                    )
                                )
                            )
                    )

                    GosalaOfficialLogo(
                        size = 56.dp,
                        innerPadding = 2.dp,
                        borderWidth = 2.dp,
                        testTag = "public_official_logo",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    ) {
                        RoleBadgeChip(role = session.role, language = language)
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = str(language, GosalaBrand.MAIN_SLOGAN_HI_1, "Grace of Mahadev, Service to Gaumata."),
                            style = MaterialTheme.typography.titleSmall,
                            color = Golden,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = str(language, GosalaBrand.MAIN_SLOGAN_HI_2, GosalaBrand.MAIN_SLOGAN_EN),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.94f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Address & Supporting Slogan Strip
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SoftGoldTint,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Saffron, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = str(language, GosalaBrand.SUPPORTING_SLOGAN_HI, GosalaBrand.SUPPORTING_SLOGAN_EN),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = str(language, GosalaBrand.ADDRESS_HI, GosalaBrand.ADDRESS_EN),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Optional Confirmation Banner after Public Product Buy or Farmer Application
        if (isPublic && latestRequestBanner != null) {
            item(key = "public_request_confirmation") {
                val banner = latestRequestBanner!!
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ForestGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .testTag("public_request_confirmation_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGreenTint)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = ForestGreen,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = banner.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = banner.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkText
                            )
                        }
                        IconButton(
                            onClick = { latestRequestBanner = null },
                            modifier = Modifier.testTag("dismiss_public_request_banner")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = str(language, "बंद करें", "Dismiss"),
                                tint = ForestGreen
                            )
                        }
                    }
                }
            }
        }

        // 2. LIVE KPI SUMMARY CARDS (6 Metrics from Prompt)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "कुल गौवंश", "Total Cows"),
                        value = totalCattle.toString(),
                        subtitle = str(language, "पंजीकृत पशु", "Registered"),
                        icon = Icons.Default.Pets,
                        accentColor = ForestGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.CATTLE) }
                    )
                    MetricStatCard(
                        title = str(language, "स्वस्थ गौवंश", "Healthy"),
                        value = healthyCattle.toString(),
                        subtitle = str(language, "सामान्य स्थिति", "Good Health"),
                        icon = Icons.Default.Verified,
                        accentColor = GoshalaGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = str(language, "बीमार / उपचार", "Sick / Care"),
                        value = sickCattle.toString(),
                        subtitle = str(language, "निगरानी में", "Under Care"),
                        icon = Icons.Default.LocalHospital,
                        accentColor = AlertRed,
                        modifier = Modifier.weight(1f),
                        onClick = { if (!isPublic && session.canView(AppModule.HEALTH)) onNavigate(Routes.HEALTH) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "आज का चारा", "Today's Feed"),
                        value = "${todayFeedKg.toInt()} kg",
                        subtitle = str(language, "हरा व सूखा", "Green + Dry"),
                        icon = Icons.Default.Grass,
                        accentColor = GoshalaGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { if (!isPublic && session.canView(AppModule.FEED)) onNavigate(Routes.FEED) }
                    )
                    MetricStatCard(
                        title = str(language, "आज का खर्च", "Today's Expense"),
                        value = "₹${todayExpense.toInt()}",
                        subtitle = str(language, "दैनिक व्यय", "Daily Outflow"),
                        icon = Icons.Default.CurrencyRupee,
                        accentColor = Saffron,
                        modifier = Modifier.weight(1f),
                        onClick = { if (!isPublic && session.canView(AppModule.FINANCE)) onNavigate(Routes.FINANCE) }
                    )
                    MetricStatCard(
                        title = str(language, if (isPublic) "मेरा दान" else "सत्यापित दान", if (isPublic) "My Donations" else "Donations"),
                        value = "₹${verifiedDonations.toInt()}",
                        subtitle = str(language, "सत्यापित सहयोग", "Verified"),
                        icon = Icons.Default.VolunteerActivism,
                        accentColor = Golden,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Routes.DONATIONS) }
                    )
                }
            }
        }

        // 3. QUICK ACTIONS BANNER (+ त्वरित कार्य)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ForestGreen)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = Golden)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = str(language, "+ त्वरित कार्य", "+ Quick Actions"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isPublic && session.canAdd(AppModule.CATTLE)) {
                            QuickActionPill(
                                label = str(language, "नई गाय जोड़ें", "Add Cattle"),
                                icon = Icons.Default.AddCircleOutline,
                                onClick = onQuickAddCattle,
                                modifier = Modifier.weight(1f),
                                testTag = "quick_add_cattle"
                            )
                        }
                        if (!isPublic && session.canAdd(AppModule.FEED)) {
                            QuickActionPill(
                                label = str(language, "चारा दर्ज करें", "Log Feed"),
                                icon = Icons.Default.Grass,
                                onClick = onQuickAddFeed,
                                modifier = Modifier.weight(1f),
                                testTag = "quick_add_feed"
                            )
                        }
                        if (!isPublic && session.canAdd(AppModule.FINANCE)) {
                            QuickActionPill(
                                label = str(language, "खर्च जोड़ें", "Add Expense"),
                                icon = Icons.Default.ReceiptLong,
                                onClick = onQuickAddExpense,
                                modifier = Modifier.weight(1f),
                                testTag = "quick_add_expense"
                            )
                        }
                        if (isPublic || (!session.canAdd(AppModule.CATTLE) && !session.canAdd(AppModule.FEED))) {
                            QuickActionPill(
                                label = str(language, "दान करें", "Donate"),
                                icon = Icons.Default.VolunteerActivism,
                                onClick = { onNavigate(Routes.DONATIONS) },
                                modifier = Modifier.weight(1f),
                                testTag = "quick_donate"
                            )
                            QuickActionPill(
                                label = str(language, "गौ-गोद लें", "Sponsor Cow"),
                                icon = Icons.Default.Favorite,
                                onClick = { onOpenSponsorTab?.invoke() ?: onNavigate(Routes.DONATIONS) },
                                modifier = Modifier.weight(1f),
                                testTag = "quick_sponsor"
                            )
                        }
                        QuickActionPill(
                            label = str(language, "AI सहायक", "AI Assist"),
                            icon = Icons.Default.AutoAwesome,
                            onClick = { onNavigate(Routes.AI_ASSISTANT) },
                            modifier = Modifier.weight(1f),
                            testTag = "quick_ai"
                        )
                    }
                }
            }
        }

        // 4. GEMINI AI ASSISTANT FEATURED CARD
        item {
            Card(
                onClick = { onNavigate(Routes.AI_ASSISTANT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Golden.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .testTag("home_ai_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Saffron,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str(language, "AI गौशाला सहायक (वॉइस व फोटो विश्लेषण)", "AI Goshala Assistant (Voice & Vision)"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = str(
                                language,
                                "हिंदी में बोलकर एंट्री ड्राफ्ट बनाएं, पशु स्वास्थ्य फोटो जांचें व रिपोर्ट पूछें।",
                                "Hindi voice entry drafts, cow photo check, and smart management Q&A."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = ForestGreen)
                }
            }
        }

        // 5. 3-COLUMN GOSHALA MODULES GRID (Matching Reference Design)
        item {
            Text(
                text = str(language, "गौशाला सेवा व प्रबंधन मॉड्यूल", "Goshala Modules"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
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
        // 6. PHASE 1 PUBLIC USER SECTIONS (Read-Only Info, Products Catalog & Farmer-Goshala Cooperation)
        if (isPublic) {
            item(key = "public_goshala_overview") {
                PublicGoshalaOverviewCard(
                    language = language,
                    totalCattle = totalCattle,
                    healthyCattle = healthyCattle,
                    approvedCowsCount = cattle.size,
                    onOpenCattle = { onNavigate(Routes.CATTLE) }
                )
            }

            item(key = "public_production_process") {
                PublicProductionProcessCard(language = language)
            }

            item(key = "public_schemes") {
                PublicSchemesCard(
                    language = language,
                    onOpenDonations = { onNavigate(Routes.DONATIONS) },
                    onOpenFarmerForm = { showFarmerDialog = true },
                    onOpenSponsor = { onOpenSponsorTab?.invoke() ?: onNavigate(Routes.DONATIONS) }
                )
            }

            item(key = "public_products_header") {
                PublicProductsHeaderAndFilters(
                    language = language,
                    selectedCategory = selectedProductCategory,
                    onSelectCategory = { selectedProductCategory = it }
                )
            }

            items(
                items = filteredPublicProducts,
                key = { product -> "public_product_${product.id}" }
            ) { product ->
                PublicProductCard(
                    language = language,
                    product = product,
                    onBuyClick = { purchasingProduct = product }
                )
            }

            item(key = "public_my_orders") {
                PublicMyProductOrdersSection(
                    language = language,
                    ordersState = productOrdersState
                )
            }

            item(key = "public_farmer_relation_details") {
                PublicFarmerRelationDetailsCard(language = language)
            }

            item(key = "public_farmer_relation_cta") {
                PublicFarmerApplicationCtaCard(
                    language = language,
                    onOpenFarmerApplication = { showFarmerDialog = true }
                )
            }
        }
    }

    if (isPublic) {
        purchasingProduct?.let { selectedProd ->
            ProductPurchaseRequestDialog(
                language = language,
                session = session,
                product = selectedProd,
                onDismiss = { purchasingProduct = null },
                onSubmitted = { summaryResult ->
                    latestRequestBanner = summaryResult
                    purchasingProduct = null
                },
                onSubmitOrder = onSubmitProductOrder
            )
        }

        if (showFarmerDialog) {
            FarmerContactApplicationDialog(
                language = language,
                session = session,
                onDismiss = { showFarmerDialog = false },
                onSubmitted = { summaryResult ->
                    latestRequestBanner = summaryResult
                    showFarmerDialog = false
                }
            )
        }
    }
}

@Composable
private fun QuickActionPill(
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
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

object Routes {
    const val LOGIN = "login"
    const val AUTH_LOADING = "auth_loading"
    const val AUTH_ERROR = "auth_error"
    const val PUBLIC_DASHBOARD = "public_dashboard"
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val STAFF_DASHBOARD = "staff_dashboard"
    const val HOME = PUBLIC_DASHBOARD
    const val CATTLE = "cattle"
    const val HEALTH = "health"
    const val FEED = "feed"
    const val STAFF = "staff"
    const val FINANCE = "finance"
    const val FARMERS = "farmers"
    const val PRODUCTION = "production"
    const val DONATIONS = "donations"
    const val APPLICATIONS = "applications"
    const val AI_ASSISTANT = "ai_assistant"
    const val REPORTS = "reports"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
}

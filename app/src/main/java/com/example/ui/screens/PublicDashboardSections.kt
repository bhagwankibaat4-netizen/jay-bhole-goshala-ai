package com.example.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.AppLanguage
import com.example.core.GosalaBrand
import com.example.core.str
import com.example.data.model.ProductOrder
import com.example.data.model.UserSession
import com.example.data.model.paymentModeLabel
import com.example.data.model.paymentStatusLabel
import com.example.data.model.productOrderStatusLabel
import com.example.ui.components.GosalaAsyncResourceImage
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

data class PublicGoshalaProduct(
    val id: String,
    val categoryKey: String,
    val nameHi: String,
    val nameEn: String,
    val descriptionHi: String,
    val descriptionEn: String,
    val priceInr: Int,
    val unitHi: String,
    val unitEn: String,
    val stockLabelHi: String,
    val stockLabelEn: String,
    val inStock: Boolean,
    @DrawableRes val imageRes: Int
)

data class PublicRequestSummary(
    val refCode: String,
    val title: String,
    val detail: String
)

fun defaultPublicGoshalaProducts(): List<PublicGoshalaProduct> = listOf(
    PublicGoshalaProduct(
        id = "compost",
        categoryKey = "MANURE",
        nameHi = "जैविक गोबर खाद",
        nameEn = "Organic Cow Dung Manure",
        descriptionHi = "देशी गाय के गोबर से प्राकृतिक विधि द्वारा तैयार पोषक तत्वों से भरपूर जैविक खाद — खेत की उर्वरता बढ़ाए।",
        descriptionEn = "Nutrient-rich natural fertilizer crafted from indigenous cow dung to restore soil fertility.",
        priceInr = 350,
        unitHi = "50 किग्रा बैग",
        unitEn = "50 kg bag",
        stockLabelHi = "उपलब्ध (सीधा गौशाला से)",
        stockLabelEn = "Available at Goshala",
        inStock = true,
        imageRes = R.drawable.img_prod_compost_1795203989906
    ),
    PublicGoshalaProduct(
        id = "vermicompost",
        categoryKey = "MANURE",
        nameHi = "वर्मी कम्पोस्ट (केंचुआ खाद)",
        nameEn = "Vermicompost",
        descriptionHi = "जैविक खेती, सब्ज़ी, बागवानी व नर्सरी पौधों के लिए उच्च गुणवत्ता वाली छनी हुई केंचुआ खाद।",
        descriptionEn = "Fine-sieved earthworm vermicompost ideal for organic farming, vegetables, and home gardens.",
        priceInr = 450,
        unitHi = "40 किग्रा बैग",
        unitEn = "40 kg bag",
        stockLabelHi = "उपलब्ध (ताज़ा बैच)",
        stockLabelEn = "In Stock (Fresh Batch)",
        inStock = true,
        imageRes = R.drawable.img_prod_vermicompost_1795203989044
    ),
    PublicGoshalaProduct(
        id = "kande",
        categoryKey = "FUEL",
        nameHi = "गोबर के कंडे",
        nameEn = "Cow Dung Cakes (Kande)",
        descriptionHi = "हवन, यज्ञ, पूजा-पाठ एवं पारंपरिक उपयोग हेतु धूप में सुखाए गए शुद्ध देशी गाय के कंडे।",
        descriptionEn = "Sun-dried pure indigenous cow dung cakes for havan, rituals, and traditional eco-friendly use.",
        priceInr = 120,
        unitHi = "25 नग पैक",
        unitEn = "Pack of 25",
        stockLabelHi = "उपलब्ध",
        stockLabelEn = "In Stock",
        inStock = true,
        imageRes = R.drawable.img_prod_kande_1795203990766
    ),
    PublicGoshalaProduct(
        id = "gaukashtha",
        categoryKey = "FUEL",
        nameHi = "गोबर की लकड़ी / उपले (गौ-काष्ठ)",
        nameEn = "Cow Dung Logs (Gau-Kashtha)",
        descriptionHi = "वृक्ष संरक्षण हेतु गोबर से निर्मित पर्यावरण-अनुकूल लकड़ी — होलिका दहन, यज्ञ व ईंधन के लिए सर्वोत्तम।",
        descriptionEn = "Eco-friendly compressed cow dung logs to save trees — ideal for rituals, bonfires, and clean fuel.",
        priceInr = 180,
        unitHi = "10 किग्रा बंडल",
        unitEn = "10 kg bundle",
        stockLabelHi = "सीमित स्टॉक उपलब्ध",
        stockLabelEn = "Limited Stock Available",
        inStock = true,
        imageRes = R.drawable.img_prod_gaukashtha_1795203989824
    ),
    PublicGoshalaProduct(
        id = "dhoop",
        categoryKey = "PUJA",
        nameHi = "प्राकृतिक धूपबत्ती",
        nameEn = "Natural Cow Dung Dhoop",
        descriptionHi = "गाय के गोबर, हवन सामग्री, गूगल व प्राकृतिक जड़ी-बूटियों से बनी सुगंधित रसायन-मुक्त धूपबत्ती।",
        descriptionEn = "Chemical-free aromatic incense cones made with cow dung, guggal, and sacred herbal blends.",
        priceInr = 80,
        unitHi = "30 कोन डिब्बा",
        unitEn = "Box of 30 cones",
        stockLabelHi = "उपलब्ध",
        stockLabelEn = "In Stock",
        inStock = true,
        imageRes = R.drawable.img_prod_dhoop_1795203989414
    ),
    PublicGoshalaProduct(
        id = "other_products",
        categoryKey = "PUJA",
        nameHi = "गोबर से बने अन्य उत्पाद (दीये व गमले)",
        nameEn = "Other Cow Dung Crafts (Diyas & Pots)",
        descriptionHi = "पर्यावरण-अनुकूल गोमय दीये, पौध-गमले व हवन कप जो उपयोग के बाद मिट्टी में खाद बन जाते हैं।",
        descriptionEn = "Biodegradable cow dung diyas, nursery planters, and havan cups that enrich soil after use.",
        priceInr = 150,
        unitHi = "12 नग सेट",
        unitEn = "Set of 12",
        stockLabelHi = "ऑर्डर पर उपलब्ध",
        stockLabelEn = "Available on Order",
        inStock = true,
        imageRes = R.drawable.img_prod_dhoop_1795203989414
    )
)

@Composable
fun PublicGoshalaInfoAndSchemesCard(
    language: AppLanguage,
    totalCattle: Int,
    healthyCattle: Int,
    approvedCowsCount: Int,
    onOpenCattle: () -> Unit,
    onOpenDonations: () -> Unit,
    onOpenFarmerForm: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PublicGoshalaOverviewCard(
            language = language,
            totalCattle = totalCattle,
            healthyCattle = healthyCattle,
            approvedCowsCount = approvedCowsCount,
            onOpenCattle = onOpenCattle
        )
        PublicProductionProcessCard(language = language)
        PublicSchemesCard(
            language = language,
            onOpenDonations = onOpenDonations,
            onOpenFarmerForm = onOpenFarmerForm
        )
    }
}

@Composable
fun PublicGoshalaOverviewCard(
    language: AppLanguage,
    totalCattle: Int,
    healthyCattle: Int,
    approvedCowsCount: Int,
    onOpenCattle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_goshala_info_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeaderRow(
            icon = Icons.Default.Info,
            title = str(language, "गौशाला परिचय एवं गतिविधियाँ", "Goshala Overview & Activities"),
            subtitle = str(language, "पारदर्शी जन-जानकारी (केवल देखने हेतु)", "Public Information (Read-Only)")
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = str(language, "गौवंश सेवा स्थिति", "Cattle Care Snapshot"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Text(
                            text = str(
                                language,
                                "कुल $totalCattle गौवंश (स्वस्थ: $healthyCattle) • दर्शन हेतु स्वीकृत: $approvedCowsCount",
                                "Total $totalCattle cattle (Healthy: $healthyCattle) • Publicly listed: $approvedCowsCount"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenCattle,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("public_view_cattle_btn")
                    ) {
                        Text(str(language, "गोवंश देखें", "View Cows"))
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                Text(
                    text = str(language, "गौशाला की दैनिक गतिविधियाँ", "Daily Goshala Activities"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val activities = listOf(
                    Triple(Icons.Default.Grass, str(language, "दैनिक चारा व स्वच्छ जल सेवा", "Daily Green & Dry Fodder Care"), ForestGreen),
                    Triple(Icons.Default.MedicalServices, str(language, "नियमित पशु स्वास्थ्य व टीकाकरण", "Regular Veterinary Care & Vaccination"), AlertRed),
                    Triple(Icons.Default.Spa, str(language, "गोबर खाद, वर्मी कम्पोस्ट व गोमय उत्पाद निर्माण", "Organic Compost & Cow Dung Product Making"), Color(0xFF6D4C41)),
                    Triple(Icons.Default.Agriculture, str(language, "स्थानीय किसानों के साथ चारा-खाद सहयोग", "Local Farmer Fodder & Manure Cooperation"), GoshalaGreen)
                )
                activities.forEach { (icon, label, tint) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = tint.copy(alpha = 0.12f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PublicProductionProcessCard(language: AppLanguage) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_production_process_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = SoftGoldTint,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Recycling, contentDescription = null, tint = Color(0xFF6D4C41), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = str(language, "गोबर उत्पाद एवं उत्पादन प्रक्रिया", "Cow Dung Products & Production Process"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen
                    )
                    Text(
                        text = str(language, "शून्य अपशिष्ट — प्रकृति व गौसेवा का स्वावलंबी चक्र", "Zero-waste sustainable rural economy"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val steps = listOf(
                "1" to str(language, "ताज़ा गोबर संग्रहण एवं स्वच्छ छँटाई", "Daily fresh cow dung collection & sorting"),
                "2" to str(language, "प्राकृतिक कम्पोस्टिंग व केंचुआ खाद बेड में परिपक्वन (45–60 दिन)", "Natural composting & vermicompost bed curing (45–60 days)"),
                "3" to str(language, "कंडे, गौ-काष्ठ, धूपबत्ती व गोमय दीयों का निर्माण व सूर्य-सुखाई", "Shaping kande, gau-kashtha logs, dhoop cones & diyas with solar drying"),
                "4" to str(language, "गुणवत्ता जाँच, जैविक पैकिंग एवं किसानों व भक्तों को वितरण", "Quality inspection, eco-packing & distribution to farmers and devotees")
            )
            steps.forEach { (num, text) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ForestGreen,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(num, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun PublicSchemesCard(
    language: AppLanguage,
    onOpenDonations: () -> Unit,
    onOpenFarmerForm: () -> Unit,
    onOpenSponsor: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_schemes_section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = str(language, "गौशाला से जुड़ी योजनाएँ", "Goshala Seva Schemes & Plans"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ForestGreen
            )

            SchemeActionRow(
                icon = Icons.Default.Favorite,
                tint = Saffron,
                title = str(language, "गौ-गोद (सेवा प्रायोजन) योजना", "Cow Adoption / Sponsorship Plan"),
                desc = str(language, "किसी एक गौमाता के मासिक चारे व देखभाल का संकल्प लें और सेवा प्रमाणपत्र प्राप्त करें।", "Sponsor monthly fodder & care of a cow and receive a service certificate."),
                actionLabel = str(language, "आवेदन", "Apply"),
                testTag = "public_scheme_sponsor_btn",
                onClick = { onOpenSponsor?.invoke() ?: onOpenDonations() }
            )
            SchemeActionRow(
                icon = Icons.Default.VolunteerActivism,
                tint = ForestGreen,
                title = str(language, "चारा, औषधि एवं शेड दान योजना", "Fodder, Medicine & Shed Donation"),
                desc = str(language, "गौमाता के दैनिक पोषण व उपचार हेतु स्वेच्छा से दान करें और रसीद प्राप्त करें।", "Contribute voluntarily toward fodder & veterinary care with official receipt."),
                actionLabel = str(language, "दान करें", "Donate"),
                testTag = "public_scheme_donate_btn",
                onClick = onOpenDonations
            )
            SchemeActionRow(
                icon = Icons.Default.Agriculture,
                tint = GoshalaGreen,
                title = str(language, "किसान–गौशाला जैविक साझेदारी", "Farmer–Goshala Organic Partnership"),
                desc = str(language, "भूसा/चारा आपूर्ति करें और खेतों के लिए शुद्ध जैविक खाद प्राप्त करें।", "Supply fodder/straw and receive pure organic cow dung manure for farming."),
                actionLabel = str(language, "जुड़ें", "Connect"),
                testTag = "public_scheme_farmer_btn",
                onClick = onOpenFarmerForm
            )
        }
    }
}

@Composable
private fun SchemeActionRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    desc: String,
    actionLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(
                onClick = onClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(testTag)
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PublicProductsHeaderAndFilters(
    language: AppLanguage,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_products_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeaderRow(
            icon = Icons.Default.Storefront,
            title = str(language, "गौशाला के शुद्ध उत्पाद (Products)", "Pure Goshala Products"),
            subtitle = str(language, "उत्पाद की पूरी आय गौमाता के चारे व सेवा में लगती है", "100% proceeds support daily cow care & fodder")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val cats = listOf(
                "ALL" to str(language, "सभी उत्पाद", "All Products"),
                "MANURE" to str(language, "जैविक खाद", "Organic Manure"),
                "FUEL" to str(language, "कंडे व गौ-काष्ठ", "Kande & Logs"),
                "PUJA" to str(language, "धूपबत्ती व अन्य", "Dhoop & Crafts")
            )
            cats.forEach { (key, label) ->
                FilterChip(
                    selected = selectedCategory == key,
                    onClick = { onSelectCategory(key) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("product_filter_${key.lowercase()}")
                )
            }
        }
    }
}

@Composable
fun PublicProductsShowcaseSection(
    language: AppLanguage,
    products: List<PublicGoshalaProduct>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    onBuyClick: (PublicGoshalaProduct) -> Unit
) {
    val filtered = remember(products, selectedCategory) {
        if (selectedCategory == "ALL") products else products.filter { it.categoryKey == selectedCategory }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PublicProductsHeaderAndFilters(
            language = language,
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory
        )
        filtered.forEach { product ->
            PublicProductCard(
                language = language,
                product = product,
                onBuyClick = { onBuyClick(product) }
            )
        }
    }
}

@Composable
fun PublicProductCard(
    language: AppLanguage,
    product: PublicGoshalaProduct,
    onBuyClick: () -> Unit
) {
    Card(
        onClick = onBuyClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
            ) {
                GosalaAsyncResourceImage(
                    resId = product.imageRes,
                    contentDescription = str(language, product.nameHi, product.nameEn),
                    contentScale = ContentScale.Crop,
                    placeholderColor = SoftGreenTint,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (product.inStock) ForestGreen else AlertRed,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = str(language, product.stockLabelHi, product.stockLabelEn),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Golden,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = "₹${product.priceInr} / ${str(language, product.unitHi, product.unitEn)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = DarkText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = str(language, product.nameHi, product.nameEn),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = str(language, product.descriptionHi, product.descriptionEn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "₹${product.priceInr}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = ForestGreen
                        )
                        Text(
                            text = str(language, "प्रति ${product.unitHi}", "Per ${product.unitEn}"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onBuyClick,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("product_buy_${product.id}")
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = str(language, "खरीदें", "Buy Request"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PublicFarmerRelationSection(
    language: AppLanguage,
    onOpenFarmerApplication: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PublicFarmerRelationDetailsCard(language = language)
        PublicFarmerApplicationCtaCard(
            language = language,
            onOpenFarmerApplication = onOpenFarmerApplication
        )
    }
}

@Composable
fun PublicFarmerRelationDetailsCard(language: AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_farmer_relation_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeaderRow(
            icon = Icons.Default.Agriculture,
            title = str(language, "किसान–गौशाला संबंध (सहयोग)", "Farmer–Goshala Cooperation"),
            subtitle = str(language, "ग्रामीण स्वावलंबन: चारा आपूर्ति और जैविक खाद का सीधा लाभ", "Mutual rural prosperity: fodder supply & organic manure")
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoshalaGreen.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FarmerPillarBlock(
                    icon = Icons.Default.ShoppingCartCheckout,
                    tint = ForestGreen,
                    bgTint = SoftGreenTint,
                    title = str(language, "1. गौशाला किसान से क्या खरीदती है", "1. What Goshala Buys from Farmers"),
                    items = listOf(
                        str(language, "• गेहूँ व सरसों का भूसा", "• Wheat & mustard straw (Bhusa)"),
                        str(language, "• ताज़ा हरा चारा (बरसीम, ज्वार, मक्का)", "• Fresh green fodder (Berseem, Jowar, Maize)"),
                        str(language, "• सूखा चारा, कड़बी व दाना-खली", "• Dry fodder, kadbi & cattle feed grains")
                    )
                )

                FarmerPillarBlock(
                    icon = Icons.Default.LocalShipping,
                    tint = Saffron,
                    bgTint = SoftSaffronTint,
                    title = str(language, "2. किसान गौशाला को क्या उपलब्ध करा सकता है", "2. What Farmers Can Supply to Goshala"),
                    items = listOf(
                        str(language, "• मौसमी हरा व सूखा चारा (सीधे गौशाला केंद्र पर)", "• Seasonal green & dry fodder directly at Goshala"),
                        str(language, "• फसल कटाई के बाद बचा हुआ स्वच्छ भूसा व अवशेष", "• Clean post-harvest straw & crop residue"),
                        str(language, "• उचित दर पर नियमित या एकमुश्त आपूर्ति", "• Regular or one-time supply at fair rates")
                    )
                )

                FarmerPillarBlock(
                    icon = Icons.Default.Spa,
                    tint = Color(0xFF6D4C41),
                    bgTint = SoftGoldTint,
                    title = str(language, "3. गौशाला किसान को क्या देती है", "3. What Goshala Provides to Farmers"),
                    items = listOf(
                        str(language, "• शुद्ध जैविक गोबर खाद एवं वर्मी कम्पोस्ट", "• Pure organic cow dung manure & vermicompost"),
                        str(language, "• गोबर के कंडे, गौ-काष्ठ व अन्य गोमय उत्पाद", "• Cow dung cakes, gau-kashtha & eco-products"),
                        str(language, "• जैविक खेती व मिट्टी की उर्वरता बढ़ाने में सहयोग", "• Support for natural farming & soil enrichment")
                    )
                )
            }
        }
    }
}

@Composable
fun PublicFarmerApplicationCtaCard(
    language: AppLanguage,
    onOpenFarmerApplication: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SoftGreenTint)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = str(
                    language,
                    "चारा/भूसा आपूर्ति या जैविक खाद हेतु सीधे गौशाला से जुड़ें",
                    "Connect directly with Goshala for fodder supply or organic manure"
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = ForestGreen
            )
            Button(
                onClick = onOpenFarmerApplication,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("public_farmer_apply_btn")
            ) {
                Icon(Icons.Default.Handshake, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = str(language, "किसान संपर्क / आवेदन करें", "Farmer Contact / Apply"),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FarmerPillarBlock(
    icon: ImageVector,
    tint: Color,
    bgTint: Color,
    title: String,
    items: List<String>
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgTint.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
            }
            items.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = DarkText.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ForestGreen.copy(alpha = 0.12f),
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PublicMyProductOrdersSection(
    language: AppLanguage,
    ordersState: DataUiState<List<ProductOrder>>
) {
    val isHindi = language == AppLanguage.HINDI
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("public_my_orders_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeaderRow(
            icon = Icons.Default.LocalShipping,
            title = str(language, "मेरे उत्पाद खरीद आवेदन", "My Product Purchase Orders"),
            subtitle = str(language, "आवेदन व भुगतान की लाइव स्थिति यहाँ देखें", "Track live order & payment verification status")
        )

        when (ordersState) {
            is DataUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ForestGreen, modifier = Modifier.size(28.dp))
                }
            }
            is DataUiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftRedTint)
                ) {
                    Text(
                        text = ordersState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is DataUiState.Success -> {
                if (ordersState.data.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("public_my_orders_empty"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = ForestGreen)
                            Text(
                                text = str(
                                    language,
                                    "अभी तक कोई उत्पाद खरीद आवेदन जमा नहीं है। ऊपर किसी उत्पाद पर क्लिक कर आवेदन करें।",
                                    "No product purchase applications yet. Tap any product above to place a request."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    ordersState.data.forEach { order ->
                        val statusColor = when (order.orderStatus) {
                            "COMPLETED" -> GoshalaGreen
                            "APPROVED", "READY", "DELIVERY" -> ForestGreen
                            "CANCELLED" -> AlertRed
                            else -> Saffron
                        }
                        val payColor = when (order.paymentStatus) {
                            "VERIFIED" -> GoshalaGreen
                            "PAID" -> Color(0xFF1565C0)
                            else -> Saffron
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("public_order_card_${order.id.ifBlank { order.orderNumber }}"),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${order.orderNumber} • ${order.dateStr}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = statusColor.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = productOrderStatusLabel(order.orderStatus, isHindi),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${order.productName} × ${order.quantity} (${order.unitLabel})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${str(language, "कुल राशि", "Total")}: ₹${order.totalAmountInr.toInt()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = ForestGreen
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = payColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "${paymentModeLabel(order.paymentMode, isHindi)} • ${paymentStatusLabel(order.paymentStatus, isHindi)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = payColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${str(language, "पता", "Address")}: ${order.buyerAddress}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (order.adminNote.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = SoftGoldTint,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${str(language, "गौशाला संदेश", "Goshala Note")}: ${order.adminNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DarkText,
                                            modifier = Modifier.padding(8.dp)
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

@Composable
fun ProductPurchaseRequestDialog(
    language: AppLanguage,
    session: UserSession,
    product: PublicGoshalaProduct,
    onDismiss: () -> Unit,
    onSubmitted: (PublicRequestSummary) -> Unit,
    onSubmitOrder: (ProductOrder) -> Unit = {}
) {
    val isHindi = language == AppLanguage.HINDI
    var buyerName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var paymentMode by remember { mutableStateOf("CASH") }
    var paymentRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val qty = (qtyText.toIntOrNull() ?: 1).coerceIn(1, 500)
    val estTotal = qty * product.priceInr

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("product_purchase_dialog"),
        title = {
            Text(
                text = str(language, "उत्पाद विवरण व खरीद आवेदन", "Product Details & Purchase Application"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                errorMsg?.let {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SoftRedTint,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            color = AlertRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                // Compact Full Product Photo + Details Card (fits cleanly on mobile dialogs)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SoftGreenTint,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        GosalaAsyncResourceImage(
                            resId = product.imageRes,
                            contentDescription = str(language, product.nameHi, product.nameEn),
                            contentScale = ContentScale.Crop,
                            placeholderColor = SoftGreenTint,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = str(language, product.nameHi, product.nameEn),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                            Text(
                                text = str(language, product.descriptionHi, product.descriptionEn),
                                style = MaterialTheme.typography.bodySmall,
                                color = DarkText
                            )
                            Text(
                                text = "${str(language, product.stockLabelHi, product.stockLabelEn)} • ₹${product.priceInr} / ${str(language, product.unitHi, product.unitEn)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                        }
                    }
                }

                // Total Payable Highlight
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SoftGoldTint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("purchase_total_amount_box")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = str(language, "कुल देय राशि ($qty × ₹${product.priceInr})", "Total Payable ($qty × ₹${product.priceInr})"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                        Text(
                            text = "₹$estTotal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ForestGreen
                        )
                    }
                }

                // Quantity Stepper + Direct Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { qtyText = (qty - 1).coerceAtLeast(1).toString() },
                        modifier = Modifier.testTag("purchase_qty_minus")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = str(language, "मात्रा घटाएं", "Decrease Quantity"))
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text(str(language, "मात्रा (संख्या)", "Quantity")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("purchase_qty_input")
                    )
                    FilledTonalIconButton(
                        onClick = { qtyText = (qty + 1).coerceAtMost(500).toString() },
                        modifier = Modifier.testTag("purchase_qty_plus")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = str(language, "मात्रा बढ़ाएं", "Increase Quantity"))
                    }
                }

                OutlinedTextField(
                    value = buyerName,
                    onValueChange = { buyerName = it; errorMsg = null },
                    label = { Text(str(language, "आपका नाम *", "Your Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("purchase_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text(str(language, "मोबाइल नंबर *", "Mobile Number *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("purchase_phone_input")
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it; errorMsg = null },
                    label = { Text(str(language, "गाँव / पूरा पता *", "Village / Full Address *")) },
                    modifier = Modifier.fillMaxWidth().testTag("purchase_address_input")
                )

                // Payment Mode Selector (No third-party payment gateway; Admin verifies)
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
                                .testTag("purchase_pay_mode_${mode.lowercase()}")
                        )
                    }
                }

                if (paymentMode != "CASH") {
                    OutlinedTextField(
                        value = paymentRef,
                        onValueChange = { paymentRef = it },
                        label = { Text(str(language, "UPI / ऑनलाइन ट्रांज़ैक्शन संदर्भ (यदि भुगतान किया हो)", "UPI / Online Reference (if already paid)")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("purchase_payment_ref_input")
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(str(language, "अतिरिक्त टिप्पणी (वैकल्पिक)", "Notes (Optional)")) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = str(
                        language,
                        "भुगतान सत्यापन नियम: नकद या ऑनलाइन भुगतान संदर्भ की पुष्टि गौशाला प्रशासक द्वारा की जाएगी (Pending → Paid → Verified)। केंद्र: ${GosalaBrand.ADDRESS_HI}",
                        "Payment Policy: Cash/Online payment references are verified by Goshala Admin (Pending → Paid → Verified)."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanDigits = phone.filter { it.isDigit() }
                    if (buyerName.trim().length < 2 || cleanDigits.length < 10 || address.trim().length < 3) {
                        errorMsg = str(
                            language,
                            "कृपया सही नाम, 10 अंकों का मोबाइल नंबर और पता दर्ज करें।",
                            "Please enter valid name, 10-digit mobile number, and address."
                        )
                        return@Button
                    }
                    val ref = "JBGS-ORD-${(1000..9999).random()}"
                    val initialPaymentStatus = if (paymentMode != "CASH" && paymentRef.trim().isNotBlank()) {
                        "PAID"
                    } else {
                        "PENDING"
                    }
                    val order = ProductOrder(
                        orderNumber = ref,
                        buyerUid = session.uid,
                        buyerName = buyerName.trim(),
                        buyerPhone = cleanDigits,
                        buyerAddress = address.trim(),
                        productId = product.id,
                        productName = str(language, product.nameHi, product.nameEn),
                        unitLabel = str(language, product.unitHi, product.unitEn),
                        unitPriceInr = product.priceInr.toDouble(),
                        quantity = qty.toLong(),
                        totalAmountInr = estTotal.toDouble(),
                        orderStatus = "NEW",
                        paymentMode = paymentMode,
                        paymentStatus = initialPaymentStatus,
                        paymentRef = paymentRef.trim(),
                        notes = notes.trim()
                    )
                    onSubmitOrder(order)
                    onSubmitted(
                        PublicRequestSummary(
                            refCode = ref,
                            title = str(language, "उत्पाद खरीद आवेदन जमा ($ref)", "Purchase Application Submitted ($ref)"),
                            detail = str(
                                language,
                                "${product.nameHi} × $qty (₹$estTotal) • स्थिति: नया आवेदन • भुगतान: ${paymentStatusLabel(initialPaymentStatus, true)} — आप नीचे 'मेरे उत्पाद खरीद आवेदन' में लाइव स्थिति देख सकते हैं।",
                                "${product.nameEn} × $qty (₹$estTotal) • Status: New • Payment: ${paymentStatusLabel(initialPaymentStatus, false)}"
                            )
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("product_purchase_submit_btn")
            ) {
                Text(str(language, "आवेदन जमा करें", "Submit Application"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(language, "रद्द करें", "Cancel"))
            }
        }
    )
}

@Composable
fun FarmerContactApplicationDialog(
    language: AppLanguage,
    session: UserSession,
    onDismiss: () -> Unit,
    onSubmitted: (PublicRequestSummary) -> Unit
) {
    var farmerName by remember(session.displayName) { mutableStateOf(session.displayName) }
    var phone by remember { mutableStateOf("") }
    var village by remember { mutableStateOf("") }
    var requestType by remember { mutableStateOf("SUPPLY_FODDER") }
    var materialDetails by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("farmer_inquiry_dialog"),
        title = {
            Text(
                text = str(language, "किसान संपर्क एवं आवेदन फॉर्म", "Farmer Contact & Application"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                errorMsg?.let {
                    Text(it, color = AlertRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = str(language, "आवेदन का प्रकार चुनें:", "Select Purpose:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = requestType == "SUPPLY_FODDER",
                        onClick = { requestType = "SUPPLY_FODDER" },
                        label = { Text(str(language, "भूसा / चारा देना", "Supply Fodder")) },
                        modifier = Modifier.testTag("farmer_type_supply")
                    )
                    FilterChip(
                        selected = requestType == "BUY_MANURE",
                        onClick = { requestType = "BUY_MANURE" },
                        label = { Text(str(language, "जैविक खाद लेना", "Buy Manure")) },
                        modifier = Modifier.testTag("farmer_type_manure")
                    )
                }
                OutlinedTextField(
                    value = farmerName,
                    onValueChange = { farmerName = it; errorMsg = null },
                    label = { Text(str(language, "किसान का नाम *", "Farmer Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("farmer_name_input")
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; errorMsg = null },
                    label = { Text(str(language, "मोबाइल नंबर *", "Mobile Number *")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("farmer_phone_input")
                )
                OutlinedTextField(
                    value = village,
                    onValueChange = { village = it; errorMsg = null },
                    label = { Text(str(language, "गाँव / तहसील *", "Village / Tehsil *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("farmer_village_input")
                )
                OutlinedTextField(
                    value = materialDetails,
                    onValueChange = { materialDetails = it },
                    label = {
                        Text(
                            if (requestType == "SUPPLY_FODDER") {
                                str(language, "चारा / भूसा विवरण व मात्रा (उदा. 10 क्विंटल भूसा)", "Fodder type & quantity (e.g. 10 quintal straw)")
                            } else {
                                str(language, "आवश्यक जैविक खाद मात्रा (उदा. 5 बैग वर्मी कम्पोस्ट)", "Required organic manure quantity")
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("farmer_material_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanDigits = phone.filter { it.isDigit() }
                    if (farmerName.trim().length < 2 || cleanDigits.length < 10 || village.trim().length < 2) {
                        errorMsg = str(
                            language,
                            "कृपया सही नाम, 10 अंकों का मोबाइल नंबर और गाँव दर्ज करें।",
                            "Please enter valid name, 10-digit mobile number, and village."
                        )
                        return@Button
                    }
                    val ref = "FRM-${(1000..9999).random()}"
                    val typeLabel = if (requestType == "SUPPLY_FODDER") {
                        str(language, "चारा/भूसा आपूर्ति", "Fodder Supply")
                    } else {
                        str(language, "जैविक खाद मांग", "Organic Manure Request")
                    }
                    onSubmitted(
                        PublicRequestSummary(
                            refCode = ref,
                            title = str(language, "किसान आवेदन तैयार ($ref)", "Farmer Application Prepared ($ref)"),
                            detail = str(
                                language,
                                "$typeLabel • ${farmerName.trim()} (${village.trim()}, $cleanDigits)${if (materialDetails.isNotBlank()) " — ${materialDetails.trim()}" else ""}",
                                "$typeLabel • ${farmerName.trim()} (${village.trim()}, $cleanDigits)"
                            )
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("farmer_inquiry_submit_btn")
            ) {
                Text(str(language, "आवेदन भेजें", "Submit Application"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(language, "रद्द करें", "Cancel"))
            }
        }
    )
}

package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.AppLanguage
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GosalaAsyncResourceImage
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState

@DrawableRes
fun presetProductDrawableRes(photoKey: String): Int = when (photoKey.lowercase()) {
    "vermicompost" -> R.drawable.img_prod_vermicompost_1795203989044
    "kande" -> R.drawable.img_prod_kande_1795203990766
    "gaukashtha" -> R.drawable.img_prod_gaukashtha_1795203989824
    "dhoop", "other", "other_products" -> R.drawable.img_prod_dhoop_1795203989414
    else -> R.drawable.img_prod_compost_1795203989906
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminProductsScreen(
    language: AppLanguage,
    session: UserSession,
    productsState: DataUiState<List<GoshalaProduct>>,
    onSaveProduct: (GoshalaProduct, Boolean) -> Unit,
    onDeleteProduct: (GoshalaProduct) -> Unit,
    onSeedDefaultProducts: () -> Unit = {}
) {
    if (session.role != UserRole.ADMIN) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .testTag("admin_products_access_denied"),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateCard(
                icon = Icons.Default.Lock,
                title = str(language, "केवल प्रशासक हेतु", "Admin Access Only"),
                subtitle = str(
                    language,
                    "उत्पाद प्रबंधन (जोड़ना, मूल्य/फोटो बदलना, हटाना) केवल गौशाला प्रशासक (Admin) के लिए उपलब्ध है।",
                    "Product catalog management (add, edit price/photo, delete) is restricted to Goshala Admins."
                )
            )
        }
        return
    }

    val isHindi = language == AppLanguage.HINDI
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<GoshalaProduct?>(null) }
    var deletingProduct by remember { mutableStateOf<GoshalaProduct?>(null) }

    val allProducts = (productsState as? DataUiState.Success)?.data.orEmpty()
    val filteredProducts = remember(allProducts, searchQuery, selectedCategory) {
        allProducts.filter { p ->
            val catOk = selectedCategory == "ALL" || p.categoryKey == selectedCategory
            val q = searchQuery.trim()
            val queryOk = q.isBlank() ||
                p.nameHi.contains(q, ignoreCase = true) ||
                p.nameEn.contains(q, ignoreCase = true) ||
                p.descriptionHi.contains(q, ignoreCase = true)
            catOk && queryOk
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingProduct = null
                    showAddDialog = true
                },
                containerColor = ForestGreen,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(str(language, "नया उत्पाद जोड़ें", "Add Product"))
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .testTag("admin_products_screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGreenTint)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = ForestGreen)
                        Text(
                            text = str(
                                language,
                                "यहाँ जोड़ा, बदला या हटाया गया हर उत्पाद Public User Dashboard पर स्वतः अपडेट होगा।",
                                "Every product added, updated, or deleted here automatically syncs to the Public User Dashboard."
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreen
                        )
                    }
                }
            }

            item {
                val activeCount = allProducts.count { it.available && it.stockQuantity > 0 }
                val totalStock = allProducts.sumOf { it.stockQuantity }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricStatCard(
                        title = str(language, "कुल उत्पाद", "Total Products"),
                        value = allProducts.size.toString(),
                        subtitle = str(language, "सक्रिय उपलब्ध: $activeCount", "Available: $activeCount"),
                        icon = Icons.Default.Storefront,
                        accentColor = ForestGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricStatCard(
                        title = str(language, "कुल स्टॉक", "Total Stock"),
                        value = totalStock.toString(),
                        subtitle = str(language, "सभी श्रेणियाँ", "All categories"),
                        icon = Icons.Default.Inventory2,
                        accentColor = Saffron,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(str(language, "उत्पाद खोजें (खाद, कंडे, धूपबत्ती...)", "Search products...")) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_products_search")
                )
            }

            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == "ALL",
                        onClick = { selectedCategory = "ALL" },
                        label = { Text("${str(language, "सभी", "All")} (${allProducts.size})") },
                        modifier = Modifier.testTag("admin_prod_filter_all")
                    )
                    GoshalaProduct.ALL_CATEGORIES.forEach { cat ->
                        val count = allProducts.count { it.categoryKey == cat }
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text("${productCategoryLabel(cat, isHindi)} ($count)") },
                            modifier = Modifier.testTag("admin_prod_filter_${cat.lowercase()}")
                        )
                    }
                }
            }

            when (productsState) {
                is DataUiState.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                }
                is DataUiState.Error -> item {
                    EmptyStateCard(
                        icon = Icons.Default.ErrorOutline,
                        title = str(language, "उत्पाद लोड नहीं हो सके", "Could not load products"),
                        subtitle = productsState.message
                    )
                }
                is DataUiState.Success -> {
                    if (allProducts.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_products_empty_card"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Storefront, contentDescription = null, tint = ForestGreen, modifier = Modifier.size(40.dp))
                                    Text(
                                        text = str(language, "अभी कोई लाइव उत्पाद दर्ज नहीं है", "No Live Products Yet"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = str(
                                            language,
                                            "आप '+ नया उत्पाद जोड़ें' से नया उत्पाद बना सकते हैं या एक क्लिक में गौशाला के 6 प्रमुख उत्पाद लोड कर सकते हैं।",
                                            "Add a new product or load the 6 default Goshala products with one tap."
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = onSeedDefaultProducts,
                                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                                        modifier = Modifier.testTag("seed_default_products_btn")
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(str(language, "डिफ़ॉल्ट 6 उत्पाद लोड करें", "Load 6 Default Products"))
                                    }
                                }
                            }
                        }
                    } else if (filteredProducts.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.SearchOff,
                                title = str(language, "कोई मिलान उत्पाद नहीं", "No Matching Products"),
                                subtitle = str(language, "अन्य श्रेणी या खोज शब्द आज़माएं।", "Try another category or search term.")
                            )
                        }
                    } else {
                        items(filteredProducts, key = { it.id.ifBlank { it.nameHi } }) { product ->
                            AdminProductCard(
                                language = language,
                                product = product,
                                onToggleAvailability = {
                                    onSaveProduct(product.copy(available = !product.available), true)
                                },
                                onEdit = {
                                    editingProduct = product
                                    showAddDialog = false
                                },
                                onDelete = { deletingProduct = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingProduct != null) {
        AdminProductEditorDialog(
            language = language,
            initialProduct = editingProduct,
            onDismiss = {
                showAddDialog = false
                editingProduct = null
            },
            onSave = { updated, isEdit ->
                onSaveProduct(updated, isEdit)
                showAddDialog = false
                editingProduct = null
            }
        )
    }

    deletingProduct?.let { target ->
        ConfirmActionDialog(
            title = str(language, "उत्पाद हटाएं", "Delete Product"),
            message = str(
                language,
                "क्या आप वाकई \"${target.nameHi}\" को हटाना चाहते हैं? यह Public User Dashboard से भी हट जाएगा।",
                "Are you sure you want to delete \"${target.nameHi}\"? It will also be removed from the Public User Dashboard."
            ),
            confirmLabel = str(language, "हटाएं", "Delete"),
            cancelLabel = str(language, "रद्द करें", "Cancel"),
            onConfirm = {
                onDeleteProduct(target)
                deletingProduct = null
            },
            onDismiss = { deletingProduct = null }
        )
    }
}

@Composable
private fun AdminProductCard(
    language: AppLanguage,
    product: GoshalaProduct,
    onToggleAvailability: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    val context = LocalContext.current
    val inStock = product.available && product.stockQuantity > 0L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_product_card_${product.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftGreenTint)
                ) {
                    if (product.photoUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(product.photoUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = product.nameHi,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        GosalaAsyncResourceImage(
                            resId = presetProductDrawableRes(product.photoKey),
                            contentDescription = product.nameHi,
                            contentScale = ContentScale.Crop,
                            placeholderColor = SoftGreenTint,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftGoldTint
                        ) {
                            Text(
                                text = productCategoryLabel(product.categoryKey, isHindi),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = DarkText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (inStock) SoftGreenTint else SoftRedTint
                        ) {
                            Text(
                                text = if (inStock) str(language, "उपलब्ध", "Available") else str(language, "अनुपलब्ध", "Unavailable"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (inStock) ForestGreen else AlertRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isHindi) product.nameHi else product.nameEn.ifBlank { product.nameHi },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isHindi) product.descriptionHi else product.descriptionEn.ifBlank { product.descriptionHi },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "₹${product.priceInr.toInt()} / ${product.unitLabel} • ${str(language, "स्टॉक", "Stock")}: ${product.stockQuantity}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ForestGreen
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Switch(
                        checked = product.available,
                        onCheckedChange = { onToggleAvailability() },
                        modifier = Modifier.testTag("admin_product_toggle_${product.id}")
                    )
                    Text(
                        text = if (product.available) str(language, "बिक्री चालू", "Active") else str(language, "बिक्री बंद", "Hidden"),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("admin_product_edit_${product.id}")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = str(language, "संपादित करें", "Edit Product"),
                            tint = ForestGreen
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("admin_product_delete_${product.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = str(language, "हटाएं", "Delete Product"),
                            tint = AlertRed
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminProductEditorDialog(
    language: AppLanguage,
    initialProduct: GoshalaProduct?,
    onDismiss: () -> Unit,
    onSave: (GoshalaProduct, Boolean) -> Unit
) {
    val isEdit = initialProduct != null
    val isHindi = language == AppLanguage.HINDI
    val context = LocalContext.current

    var nameHi by remember(initialProduct?.id) { mutableStateOf(initialProduct?.nameHi ?: "") }
    var nameEn by remember(initialProduct?.id) { mutableStateOf(initialProduct?.nameEn ?: "") }
    var categoryKey by remember(initialProduct?.id) { mutableStateOf(initialProduct?.categoryKey ?: "MANURE") }
    var descriptionHi by remember(initialProduct?.id) { mutableStateOf(initialProduct?.descriptionHi ?: "") }
    var priceText by remember(initialProduct?.id) {
        mutableStateOf(
            if (initialProduct != null) initialProduct.priceInr.toString().removeSuffix(".0") else ""
        )
    }
    var stockText by remember(initialProduct?.id) {
        mutableStateOf(initialProduct?.stockQuantity?.toString() ?: "50")
    }
    var unitLabel by remember(initialProduct?.id) { mutableStateOf(initialProduct?.unitLabel ?: "किलो") }
    var available by remember(initialProduct?.id) { mutableStateOf(initialProduct?.available ?: true) }
    var photoKey by remember(initialProduct?.id) { mutableStateOf(initialProduct?.photoKey ?: "compost") }
    var photoUri by remember(initialProduct?.id) { mutableStateOf(initialProduct?.photoUri ?: "") }
    var errorMsg by remember(initialProduct?.id) { mutableStateOf<String?>(null) }

    // Zero-permission Android Photo Picker (Google Play Policy Compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("admin_product_dialog"),
        title = {
            Text(
                text = if (isEdit) str(language, "उत्पाद विवरण संपादित करें", "Edit Product")
                else str(language, "नया गौशाला उत्पाद जोड़ें", "Add New Goshala Product"),
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

                // Product Photo Preview + Zero-Permission Photo Picker + Preset Drawable Selector
                Text(
                    text = str(language, "उत्पाद की फोटो:", "Product Photo:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.5.dp, ForestGreen, RoundedCornerShape(12.dp))
                    ) {
                        if (photoUri.isNotBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(photoUri).crossfade(true).build(),
                                contentDescription = str(language, "उत्पाद फोटो", "Product photo"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            GosalaAsyncResourceImage(
                                resId = presetProductDrawableRes(photoKey),
                                contentDescription = str(language, "उत्पाद फोटो", "Product photo"),
                                contentScale = ContentScale.Crop,
                                placeholderColor = SoftGreenTint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_pick_photo_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str(language, "गैलरी से फोटो चुनें", "Pick Photo"))
                        }
                        if (photoUri.isNotBlank()) {
                            TextButton(
                                onClick = { photoUri = "" },
                                modifier = Modifier.testTag("product_clear_photo_btn")
                            ) {
                                Text(str(language, "गैलरी फोटो हटाएं (डिफ़ॉल्ट उपयोग)", "Use Preset Photo"))
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GoshalaProduct.PRESET_PHOTO_KEYS.forEach { key ->
                        val selected = photoUri.isBlank() && photoKey == key
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) ForestGreen else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    photoKey = key
                                    photoUri = ""
                                }
                                .testTag("product_preset_photo_$key")
                        ) {
                            GosalaAsyncResourceImage(
                                resId = presetProductDrawableRes(key),
                                contentDescription = key,
                                contentScale = ContentScale.Crop,
                                placeholderColor = SoftGreenTint,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = nameHi,
                    onValueChange = { nameHi = it; errorMsg = null },
                    label = { Text(str(language, "उत्पाद का नाम *", "Product Name *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                )
                OutlinedTextField(
                    value = nameEn,
                    onValueChange = { nameEn = it },
                    label = { Text(str(language, "अंग्रेजी नाम (वैकल्पिक)", "English Name (Optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = descriptionHi,
                    onValueChange = { descriptionHi = it; errorMsg = null },
                    label = { Text(str(language, "उत्पाद का विवरण *", "Product Description *")) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("product_desc_input")
                )

                Text(
                    text = str(language, "उत्पाद की श्रेणी:", "Category:"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GoshalaProduct.ALL_CATEGORIES.forEach { cat ->
                        FilterChip(
                            selected = categoryKey == cat,
                            onClick = { categoryKey = cat },
                            label = { Text(productCategoryLabel(cat, isHindi), style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("product_cat_${cat.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it; errorMsg = null },
                        label = { Text(str(language, "बिक्री मूल्य (₹) *", "Price (₹) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("product_price_input")
                    )
                    OutlinedTextField(
                        value = stockText,
                        onValueChange = { stockText = it.filter { ch -> ch.isDigit() } },
                        label = { Text(str(language, "उपलब्ध स्टॉक", "Stock Qty")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("product_stock_input")
                    )
                }

                OutlinedTextField(
                    value = unitLabel,
                    onValueChange = { unitLabel = it; errorMsg = null },
                    label = { Text(str(language, "इकाई (किलो, पैकेट, पीस, बैग) *", "Unit (kg, packet, piece) *")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_unit_input")
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GoshalaProduct.SUGGESTED_UNITS.forEach { u ->
                        FilterChip(
                            selected = unitLabel == u,
                            onClick = { unitLabel = u },
                            label = { Text(u, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                OutlinedTextField(
                    value = photoUri,
                    onValueChange = { photoUri = it },
                    label = { Text(str(language, "कस्टम फोटो लिंक/URI (वैकल्पिक)", "Custom Photo URI / URL (Optional)")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("product_photo_uri_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = str(language, "उत्पाद उपलब्ध है (Public को दिखाएँ)", "Available on Public Dashboard"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = available,
                        onCheckedChange = { available = it },
                        modifier = Modifier.testTag("product_available_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPrice = priceText.toDoubleOrNull() ?: 0.0
                    val parsedStock = stockText.toLongOrNull() ?: 0L
                    if (nameHi.isBlank()) {
                        errorMsg = str(language, "कृपया उत्पाद का नाम दर्ज करें।", "Please enter product name.")
                        return@Button
                    }
                    if (descriptionHi.isBlank()) {
                        errorMsg = str(language, "कृपया उत्पाद का विवरण दर्ज करें।", "Please enter product description.")
                        return@Button
                    }
                    if (parsedPrice <= 0.0) {
                        errorMsg = str(language, "कृपया 0 से अधिक सही बिक्री मूल्य दर्ज करें।", "Please enter a valid price (> 0).")
                        return@Button
                    }
                    val product = (initialProduct ?: GoshalaProduct()).copy(
                        nameHi = nameHi.trim(),
                        nameEn = nameEn.trim().ifBlank { nameHi.trim() },
                        categoryKey = categoryKey,
                        descriptionHi = descriptionHi.trim(),
                        descriptionEn = initialProduct?.descriptionEn?.ifBlank { descriptionHi.trim() } ?: descriptionHi.trim(),
                        priceInr = parsedPrice,
                        stockQuantity = parsedStock.coerceAtLeast(0L),
                        unitLabel = unitLabel.trim().ifBlank { "इकाई" },
                        available = available,
                        photoKey = photoKey,
                        photoUri = photoUri.trim()
                    )
                    onSave(product, isEdit)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                modifier = Modifier.testTag("product_save_button")
            ) {
                Text(if (isEdit) str(language, "अपडेट करें", "Update") else str(language, "उत्पाद सहेजें", "Save Product"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(str(language, "रद्द करें", "Cancel"))
            }
        }
    )
}

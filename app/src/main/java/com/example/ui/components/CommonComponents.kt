package com.example.ui.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.AppLanguage
import com.example.core.CattlePhotoStorage
import com.example.core.GosalaBrand
import com.example.core.QrAndExportUtils
import com.example.core.str
import com.example.data.model.Cattle
import com.example.data.model.UserRole
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GosalaAsyncResourceImage(
    @DrawableRes resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholderColor: Color = SoftGreenTint
) {
    val context = LocalContext.current
    val request = remember(context, resId) {
        ImageRequest.Builder(context)
            .data(resId)
            .allowHardware(false)
            .crossfade(false)
            .build()
    }
    val placeholder = remember(placeholderColor) { ColorPainter(placeholderColor) }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        alignment = alignment,
        placeholder = placeholder,
        error = placeholder,
        modifier = modifier
    )
}

private sealed interface CloudCowPhotoState {
    data object Loading : CloudCowPhotoState
    data class Loaded(val bitmap: Bitmap) : CloudCowPhotoState
    data object Fallback : CloudCowPhotoState
}

private sealed interface ResolvedPhotoSourceState {
    data object Resolving : ResolvedPhotoSourceState
    data class Ready(val uri: String) : ResolvedPhotoSourceState
    data object Empty : ResolvedPhotoSourceState
    data object Failed : ResolvedPhotoSourceState
}

/**
 * Crash-safe cow photo loader used across Public Dashboard, Admin/Staff Dashboards, Cattle List,
 * Cow Details / QR Scan Screen, Sponsorship Cards, and Add/Edit Form preview.
 * - Supports Firebase Storage HTTPS URLs, `cattle_photos/<cattleId>/...` / `gs://` storage paths,
 *   Firestore `cattlePhotos/{id}` cloud pointers, and local form previews.
 * - Shows a loading indicator while loading over slow networks.
 * - Always falls back to the warm Default Cow Image (`img_default_cow_1795363316557`) on missing/broken photos.
 * - Optionally displays an error badge + Retry button (`showRetryOnError = true`) on Cow Details.
 */
@Composable
fun CowPhotoImage(
    photoUri: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    testTagPrefix: String = "cow_photo",
    photoStoragePath: String? = null,
    cattleId: String? = null,
    showRetryOnError: Boolean = false,
    language: AppLanguage = AppLanguage.HINDI
) {
    val trimmedUri = photoUri?.trim().orEmpty()
    val trimmedPath = photoStoragePath?.trim().orEmpty()
    val cleanId = cattleId?.trim().orEmpty()
    val context = LocalContext.current
    var retryAttempt by remember(trimmedUri, trimmedPath, cleanId) { mutableIntStateOf(0) }

    val needsStorageResolve = remember(trimmedUri, trimmedPath) {
        CattlePhotoStorage.isStoragePathUri(trimmedUri) ||
            (trimmedUri.isBlank() && CattlePhotoStorage.isStoragePathUri(trimmedPath))
    }

    val resolvedState by produceState<ResolvedPhotoSourceState>(
        initialValue = when {
            needsStorageResolve -> ResolvedPhotoSourceState.Resolving
            trimmedUri.isBlank() -> ResolvedPhotoSourceState.Empty
            else -> ResolvedPhotoSourceState.Ready(trimmedUri)
        },
        key1 = trimmedUri,
        key2 = trimmedPath,
        key3 = retryAttempt
    ) {
        if (!needsStorageResolve) {
            value = if (trimmedUri.isBlank()) {
                ResolvedPhotoSourceState.Empty
            } else {
                ResolvedPhotoSourceState.Ready(trimmedUri)
            }
            return@produceState
        }
        value = ResolvedPhotoSourceState.Resolving
        val url = CattlePhotoStorage.resolveCowPhotoUrl(
            cattleId = cleanId,
            photoUri = trimmedUri,
            photoStoragePath = trimmedPath
        )
        value = if (!url.isNullOrBlank()) {
            ResolvedPhotoSourceState.Ready(url)
        } else {
            ResolvedPhotoSourceState.Failed
        }
    }

    Box(
        modifier = modifier
            .background(SoftGreenTint)
            .testTag(testTagPrefix),
        contentAlignment = Alignment.Center
    ) {
        when (val source = resolvedState) {
            is ResolvedPhotoSourceState.Resolving -> {
                CowPhotoLoadingIndicator(testTag = "${testTagPrefix}_loading")
            }
            is ResolvedPhotoSourceState.Empty -> {
                DefaultCowFallbackImage(
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    testTag = "${testTagPrefix}_default"
                )
            }
            is ResolvedPhotoSourceState.Failed -> {
                DefaultCowFallbackImage(
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    testTag = "${testTagPrefix}_default"
                )
                if (showRetryOnError) {
                    CowPhotoRetryOverlay(
                        language = language,
                        testTagPrefix = testTagPrefix,
                        onRetry = {
                            CattlePhotoStorage.evictCachedPhoto(trimmedUri, cleanId, trimmedPath)
                            retryAttempt++
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
            is ResolvedPhotoSourceState.Ready -> {
                val effectiveUri = source.uri
                if (effectiveUri.startsWith(CattlePhotoStorage.FIRESTORE_PHOTO_SCHEME, ignoreCase = true)) {
                    val loadState by produceState<CloudCowPhotoState>(
                        initialValue = CloudCowPhotoState.Loading,
                        key1 = effectiveUri,
                        key2 = retryAttempt
                    ) {
                        value = CloudCowPhotoState.Loading
                        val bmp = CattlePhotoStorage.loadFirestorePhotoBitmap(effectiveUri)
                        value = if (bmp != null) CloudCowPhotoState.Loaded(bmp) else CloudCowPhotoState.Fallback
                    }
                    when (val state = loadState) {
                        is CloudCowPhotoState.Loading -> {
                            CowPhotoLoadingIndicator(testTag = "${testTagPrefix}_loading")
                        }
                        is CloudCowPhotoState.Loaded -> {
                            Image(
                                bitmap = state.bitmap.asImageBitmap(),
                                contentDescription = contentDescription,
                                contentScale = contentScale,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("${testTagPrefix}_loaded")
                            )
                        }
                        is CloudCowPhotoState.Fallback -> {
                            DefaultCowFallbackImage(
                                contentDescription = contentDescription,
                                contentScale = contentScale,
                                testTag = "${testTagPrefix}_default"
                            )
                            if (showRetryOnError) {
                                CowPhotoRetryOverlay(
                                    language = language,
                                    testTagPrefix = testTagPrefix,
                                    onRetry = {
                                        CattlePhotoStorage.evictCachedPhoto(effectiveUri, cleanId, trimmedPath)
                                        retryAttempt++
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }
                } else {
                    val request = remember(context, effectiveUri, retryAttempt) {
                        ImageRequest.Builder(context)
                            .data(effectiveUri)
                            .setParameter("retry", retryAttempt, memoryCacheKey = null)
                            .allowHardware(false)
                            .crossfade(true)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = contentDescription,
                        contentScale = contentScale,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("${testTagPrefix}_remote"),
                        loading = {
                            CowPhotoLoadingIndicator(testTag = "${testTagPrefix}_loading")
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxSize()) {
                                DefaultCowFallbackImage(
                                    contentDescription = contentDescription,
                                    contentScale = contentScale,
                                    testTag = "${testTagPrefix}_default"
                                )
                                if (showRetryOnError) {
                                    CowPhotoRetryOverlay(
                                        language = language,
                                        testTagPrefix = testTagPrefix,
                                        onRetry = {
                                            CattlePhotoStorage.evictCachedPhoto(effectiveUri, cleanId, trimmedPath)
                                            retryAttempt++
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CowPhotoLoadingIndicator(testTag: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SoftGreenTint)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = ForestGreen
        )
    }
}

@Composable
private fun CowPhotoRetryOverlay(
    language: AppLanguage,
    testTagPrefix: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .padding(8.dp)
            .testTag("${testTagPrefix}_error_overlay")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = str(language, "फोटो लोड नहीं हुई", "Photo failed to load"),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
            Surface(
                color = Golden,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .clickable(onClick = onRetry)
                    .testTag("${testTagPrefix}_retry_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = str(language, "पुनः प्रयास", "Retry"),
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = str(language, "पुनः प्रयास", "Retry"),
                        color = Color.Black,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultCowFallbackImage(
    contentDescription: String,
    contentScale: ContentScale,
    testTag: String
) {
    GosalaAsyncResourceImage(
        resId = R.drawable.img_default_cow_1795363316557,
        contentDescription = contentDescription,
        contentScale = contentScale,
        placeholderColor = SoftGreenTint,
        modifier = Modifier
            .fillMaxSize()
            .testTag(testTag)
    )
}

/**
 * Horizontal cow photo gallery card shown on Public, Admin, and Staff Dashboards so real saved cow
 * photos appear immediately on every dashboard with Loading Indicator + Default Cow Image fallback.
 */
@Composable
fun DashboardCowPhotoStrip(
    language: AppLanguage,
    cattle: List<Cattle>,
    sectionTitle: String,
    sectionTag: String,
    onOpenCattleList: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cattle.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(sectionTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ForestGreen,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onOpenCattleList) {
                Text(str(language, "सभी देखें (${cattle.size})", "View All (${cattle.size})"))
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(cattle.take(10), key = { it.id.ifBlank { it.animalCode } }) { cow ->
                val statusColor = when (cow.healthStatus) {
                    "HEALTHY" -> GoshalaGreen
                    "SICK" -> AlertRed
                    else -> Saffron
                }
                val statusLabel = when (cow.healthStatus) {
                    "HEALTHY" -> str(language, "स्वस्थ", "Healthy")
                    "SICK" -> str(language, "बीमार", "Sick")
                    else -> str(language, "उपचार", "Care")
                }
                Card(
                    onClick = onOpenCattleList,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .width(168.dp)
                        .testTag("${sectionTag}_cow_${cow.animalCode}")
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(106.dp)
                        ) {
                            CowPhotoImage(
                                photoUri = cow.photoUri,
                                photoStoragePath = cow.photoStoragePath,
                                cattleId = cow.id,
                                contentDescription = cow.name,
                                contentScale = ContentScale.Crop,
                                testTagPrefix = "${sectionTag}_photo_${cow.animalCode}",
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = cow.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cow.animalCode} • ${cow.breed}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${cow.ageText} • ${cow.shedNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ForestGreen,
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

@Composable
fun GosalaOfficialLogo(
    size: Dp,
    modifier: Modifier = Modifier,
    innerPadding: Dp = 2.dp,
    borderWidth: Dp = 1.5.dp,
    testTag: String? = null
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
            .size(size)
            .border(borderWidth, Golden, CircleShape)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        GosalaAsyncResourceImage(
            resId = R.drawable.img_official_goshala_logo,
            contentDescription = GosalaBrand.NAME_HI,
            contentScale = ContentScale.Fit,
            placeholderColor = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .clip(CircleShape)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GosalaTopBar(
    title: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    language: AppLanguage,
    isDark: Boolean,
    unreadNotifications: Int,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onNotificationsClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("top_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = str(language, "वापस", "Back"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    GosalaOfficialLogo(
                        size = 44.dp,
                        innerPadding = 2.dp,
                        borderWidth = 1.5.dp,
                        testTag = "topbar_official_logo"
                    )
                    Spacer(Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title ?: str(language, GosalaBrand.NAME_HI, GosalaBrand.NAME_EN),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = str(language, GosalaBrand.SHORT_ADDRESS_HI, GosalaBrand.SHORT_ADDRESS_EN),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.testTag("top_lang_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = str(language, "भाषा बदलें", "Switch language"),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.testTag("top_theme_toggle")
                ) {
                    Icon(
                        imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = str(language, "थीम बदलें", "Switch theme"),
                        tint = Saffron
                    )
                }

                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier.testTag("top_notifications_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadNotifications > 0) {
                                Badge(containerColor = AlertRed) {
                                    Text(unreadNotifications.coerceAtMost(99).toString(), color = Color.White)
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = str(language, "सूचनाएं", "Notifications"),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RoleBadgeChip(role: UserRole, language: AppLanguage) {
    val bg = when (role) {
        UserRole.ADMIN -> ForestGreen
        UserRole.STAFF -> Saffron
        UserRole.PUBLIC -> GoshalaGreen
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.testTag("role_badge_${role.name.lowercase()}")
    ) {
        Text(
            text = role.label(language),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun MetricStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ModuleGridCard(
    title: String,
    icon: ImageVector,
    tintColor: Color,
    bgTint: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bgTint,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = tintColor, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2
            )
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun QrCodeDialog(
    title: String,
    codeLabel: String,
    payload: String,
    language: AppLanguage,
    onDismiss: () -> Unit,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    val qrBitmap by produceState<Bitmap?>(initialValue = null, payload) {
        value = withContext(Dispatchers.Default) {
            QrAndExportUtils.generateQrBitmap(payload, 512)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("qr_code_dialog"),
        title = {
            Text(
                text = "$title ($codeLabel)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val bmp = qrBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR Code $codeLabel",
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, Golden, RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = payload,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primaryActionLabel != null && onPrimaryAction != null) {
                    Button(
                        onClick = onPrimaryAction,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen),
                        modifier = Modifier.testTag("qr_dialog_open_details_button")
                    ) {
                        Text(primaryActionLabel)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(str(language, "बंद करें", "Close"))
                }
            }
        }
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    isDestructive: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) AlertRed else ForestGreen
                ),
                modifier = Modifier.testTag("confirm_dialog_confirm_button")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelLabel)
            }
        }
    )
}

@Composable
fun IncomeExpenseBarChart(
    bars: List<Triple<String, Double, Double>>,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val maxVal = bars.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(100.0) ?: 100.0
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = str(language, "मासिक आय-व्यय सारांश", "Income vs Expense Summary"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(GoshalaGreen, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(str(language, "आय", "Income"), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(10.dp).background(Saffron, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(str(language, "खर्च", "Expense"), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (bars.isEmpty()) {
                Text(
                    text = str(language, "चार्ट देखने के लिए वित्तीय प्रविष्टियाँ जोड़ें।", "Add financial records to view chart."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val groupWidth = size.width / bars.size.coerceAtLeast(1)
                    val barWidth = (groupWidth * 0.28f).coerceAtMost(28.dp.toPx())
                    bars.forEachIndexed { index, (_, income, expense) ->
                        val cx = index * groupWidth + groupWidth / 2f
                        val incH = ((income / maxVal) * (size.height - 10f)).toFloat().coerceAtLeast(4f)
                        val expH = ((expense / maxVal) * (size.height - 10f)).toFloat().coerceAtLeast(4f)
                        drawRoundRect(
                            color = GoshalaGreen,
                            topLeft = Offset(cx - barWidth - 3f, size.height - incH),
                            size = Size(barWidth, incH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = Saffron,
                            topLeft = Offset(cx + 3f, size.height - expH),
                            size = Size(barWidth, expH),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    bars.forEach { (label, _, _) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryDonutCard(
    title: String,
    slices: List<Triple<String, Double, Color>>,
    language: AppLanguage
) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1.0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if (slices.all { it.second <= 0.0 }) {
                Text(
                    text = str(language, "अभी कोई खर्च श्रेणी उपलब्ध नहीं है।", "No expense breakdown recorded yet."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(modifier = Modifier.size(110.dp).padding(6.dp)) {
                        var startAngle = -90f
                        slices.forEach { (_, value, color) ->
                            val sweep = ((value / total) * 360f).toFloat()
                            if (sweep > 0f) {
                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    style = Stroke(width = 22.dp.toPx())
                                )
                                startAngle += sweep
                            }
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        slices.filter { it.second > 0.0 }.forEach { (label, value, color) ->
                            val pct = ((value / total) * 100).toInt()
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(color, CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "$label — $pct% (₹${value.toInt()})",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

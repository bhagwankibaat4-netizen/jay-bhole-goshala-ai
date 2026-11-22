package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.core.AppLanguage
import com.example.core.str
import com.example.data.model.*
import com.example.ui.theme.*

data class NotificationPhotoPreset(
    val key: String,
    val labelHi: String,
    val labelEn: String,
    val drawableRes: Int
)

val notificationPhotoPresets = listOf(
    NotificationPhotoPreset("preset:goshala", "गौशाला", "Goshala", R.drawable.img_hero_goshala),
    NotificationPhotoPreset("preset:cow", "गोवंश", "Cattle", R.drawable.img_default_cow_1795363316557),
    NotificationPhotoPreset("preset:compost", "जैविक खाद", "Compost", R.drawable.img_prod_compost_1795203989906),
    NotificationPhotoPreset("preset:kande", "गोबर कंडे", "Kande", R.drawable.img_prod_kande_1795203990766),
    NotificationPhotoPreset("preset:gaukashtha", "गोकाष्ठ", "Gau-Kashtha", R.drawable.img_prod_gaukashtha_1795203989824),
    NotificationPhotoPreset("preset:dhoop", "धूपबत्ती", "Dhoopbatti", R.drawable.img_prod_dhoop_1795203989414),
    NotificationPhotoPreset("preset:vermicompost", "वर्मीकम्पोस्ट", "Vermicompost", R.drawable.img_prod_vermicompost_1795203989044),
    NotificationPhotoPreset("preset:logo", "समिति लोगो", "Logo", R.drawable.img_official_goshala_logo)
)

fun notificationPresetDrawableRes(photoUrl: String): Int? {
    val trimmed = photoUrl.trim()
    return notificationPhotoPresets.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.drawableRes
}

fun notificationCategoryIcon(category: NotificationCategory): ImageVector = when (category) {
    NotificationCategory.ACTIVITIES -> Icons.Default.Pets
    NotificationCategory.PRODUCTS -> Icons.Default.Spa
    NotificationCategory.PRODUCT_AVAILABILITY -> Icons.Default.ShoppingCart
    NotificationCategory.DONATIONS -> Icons.Default.VolunteerActivism
    NotificationCategory.ADOPTION -> Icons.Default.Favorite
    NotificationCategory.ANNOUNCEMENTS -> Icons.Default.Campaign
    NotificationCategory.REMINDERS -> Icons.Default.NotificationsActive
}

@Composable
fun NotificationPhotoBanner(
    photoUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    heightDp: Int = 156
) {
    val trimmed = photoUrl.trim()
    if (trimmed.isBlank()) return
    val presetRes = notificationPresetDrawableRes(trimmed)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SoftGreenTint)
    ) {
        if (presetRes != null) {
            Image(
                painter = painterResource(id = presetRes),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = trimmed,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(id = R.drawable.img_hero_goshala),
                error = painterResource(id = R.drawable.img_hero_goshala),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private val isoDateRegex = Regex("^\\d{4}-\\d{2}-\\d{2}$")
private val time24Regex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

@Composable
fun NotificationEditorDialog(
    language: AppLanguage,
    existing: NotificationItem?,
    isSubmitting: Boolean,
    staffAccounts: List<StaffAccount> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (item: NotificationItem, isEdit: Boolean) -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    val isEdit = existing != null && existing.id.isNotBlank()
    val activeStaffList = remember(staffAccounts) {
        staffAccounts.filter { it.active && it.uid.isNotBlank() }
    }

    var itemKind by remember(existing) {
        mutableStateOf(
            if (existing?.isReminderItem() == true) "REMINDER" else "NOTICE"
        )
    }
    var selectedCategory by remember(existing) {
        mutableStateOf(
            existing?.resolvedCategory() ?: NotificationCategory.ANNOUNCEMENTS
        )
    }
    var audienceMode by remember(existing) {
        mutableStateOf(
            when {
                existing?.targetUid?.isNotBlank() == true -> "SPECIFIC_STAFF"
                else -> existing?.targetRole?.ifBlank { "PUBLIC" } ?: "PUBLIC"
            }
        )
    }
    var selectedStaffUid by remember(existing) {
        mutableStateOf(existing?.targetUid.orEmpty())
    }
    var assigneeName by remember(existing) {
        mutableStateOf(existing?.assigneeName.orEmpty())
    }
    var targetModule by remember(existing) {
        mutableStateOf(normalizeNotifModuleKey(existing?.targetModule))
    }
    var priority by remember(existing) {
        mutableStateOf(existing?.resolvedPriority() ?: NotificationPriority.NORMAL)
    }
    var title by remember(existing) { mutableStateOf(existing?.title.orEmpty()) }
    var message by remember(existing) { mutableStateOf(existing?.message.orEmpty()) }
    var photoUrl by remember(existing) { mutableStateOf(existing?.photoUrl.orEmpty()) }
    var publishDate by remember(existing) {
        mutableStateOf(existing?.publishDate?.ifBlank { todayIsoDateString() } ?: todayIsoDateString())
    }
    var expiryDate by remember(existing) { mutableStateOf(existing?.expiryDate.orEmpty()) }
    var reminderTime by remember(existing) {
        mutableStateOf(existing?.reminderTime?.ifBlank { "08:00" } ?: "08:00")
    }
    var recurrence by remember(existing) {
        mutableStateOf(existing?.resolvedRecurrence() ?: ReminderRecurrence.ONCE)
    }
    var publishNow by remember(existing) { mutableStateOf(existing?.published ?: true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            photoUrl = uri.toString()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 580.dp)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = ForestGreen,
                    contentColor = PureWhite
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (itemKind == "REMINDER") Icons.Default.AlarmAdd else Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Golden
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    isEdit -> str(language, "सूचना / रिमाइंडर संपादित करें", "Edit Notice / Reminder")
                                    itemKind == "REMINDER" -> str(language, "नया रिमाइंडर बनाएँ", "Create New Reminder")
                                    else -> str(language, "नई सूचना बनाएँ", "Create New Notice")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = str(language, "जय भोले गौशाला समिति • प्रशासक नियंत्रण", "Jay Bhole Gosala Samiti • Admin Control"),
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.85f)
                            )
                        }
                        IconButton(
                            onClick = { if (!isSubmitting) onDismiss() },
                            modifier = Modifier.testTag("notif_editor_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = str(language, "बंद करें", "Close"), tint = PureWhite)
                        }
                    }
                }

                // Scrollable Form Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Notice vs Reminder Mode
                    Text(
                        text = str(language, "प्रकार चुनें", "Select Type"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = itemKind == "NOTICE",
                            onClick = {
                                itemKind = "NOTICE"
                                if (selectedCategory == NotificationCategory.REMINDERS) {
                                    selectedCategory = NotificationCategory.ANNOUNCEMENTS
                                }
                            },
                            label = { Text(str(language, "📢 सूचना (Notice)", "📢 Notice")) },
                            modifier = Modifier.weight(1f).testTag("notif_kind_notice")
                        )
                        FilterChip(
                            selected = itemKind == "REMINDER",
                            onClick = {
                                itemKind = "REMINDER"
                                selectedCategory = NotificationCategory.REMINDERS
                            },
                            label = { Text(str(language, "🔔 रिमाइंडर (Reminder)", "🔔 Reminder")) },
                            modifier = Modifier.weight(1f).testTag("notif_kind_reminder")
                        )
                    }

                    // 2. Category Selector (7 categories)
                    Text(
                        text = str(language, "श्रेणी (Category)", "Category"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    if (cat == NotificationCategory.REMINDERS) {
                                        itemKind = "REMINDER"
                                    }
                                },
                                label = { Text("${cat.emoji} ${cat.label(isHindi)}") },
                                modifier = Modifier.testTag("notif_cat_${cat.name}")
                            )
                        }
                    }

                    // 3. Target Audience (wrapped rows so all 5 options including Specific Staff are clearly visible)
                    Text(
                        text = str(language, "किसके लिए (Audience)", "Target Audience"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    val audiences = listOf(
                        "PUBLIC" to str(language, "सार्वजनिक यूजर", "Public Users"),
                        "ALL" to str(language, "सभी (Public + Staff)", "All (Public + Staff)"),
                        "STAFF" to str(language, "स्टाफ / विभाग", "Staff / Dept"),
                        "SPECIFIC_STAFF" to str(language, "विशिष्ट स्टाफ", "Specific Staff"),
                        "ADMIN" to str(language, "केवल प्रशासक", "Admin Only")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        audiences.chunked(2).forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pair.forEach { (roleKey, label) ->
                                    FilterChip(
                                        selected = audienceMode == roleKey,
                                        onClick = {
                                            audienceMode = roleKey
                                            validationError = null
                                            if (roleKey != "STAFF") targetModule = ""
                                            if (roleKey != "SPECIFIC_STAFF") {
                                                selectedStaffUid = ""
                                            } else if (selectedStaffUid.isBlank() && activeStaffList.size == 1) {
                                                selectedStaffUid = activeStaffList.first().uid
                                                assigneeName = activeStaffList.first().name
                                            }
                                        },
                                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("notif_audience_$roleKey")
                                    )
                                }
                                if (pair.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Specific Staff Picker when targeting SPECIFIC_STAFF
                    if (audienceMode == "SPECIFIC_STAFF") {
                        Text(
                            text = str(language, "विशिष्ट स्टाफ चुनें (निजी कार्य सूचना)", "Select Specific Staff (Private Work Notice)"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                        if (activeStaffList.isEmpty()) {
                            Surface(
                                color = SoftGoldTint,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = str(
                                        language,
                                        "अभी कोई सक्रिय स्टाफ खाता उपलब्ध नहीं है। पहले 'स्टाफ प्रबंधन' से स्टाफ जोड़ें।",
                                        "No active staff accounts available. Add staff in Staff Management first."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DarkText,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                activeStaffList.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pair.forEach { staff ->
                                            val picked = selectedStaffUid == staff.uid
                                            FilterChip(
                                                selected = picked,
                                                onClick = {
                                                    selectedStaffUid = staff.uid
                                                    assigneeName = staff.name
                                                    validationError = null
                                                },
                                                label = {
                                                    Text(
                                                        text = if (staff.designation.isNotBlank()) "${staff.name} (${staff.designation})" else staff.name,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("notif_staff_pick_${staff.uid}")
                                            )
                                        }
                                        if (pair.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Staff permission module selector when targeting STAFF
                    if (audienceMode == "STAFF") {
                        Text(
                            text = str(language, "स्टाफ कार्य विभाग (अनुमति अनुसार)", "Staff Work Module (Permission-scoped)"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MutedText
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modules = listOf(
                                "" to str(language, "सभी स्टाफ", "All Staff"),
                                AppModule.CATTLE.key to str(language, "गौवंश", "Cattle"),
                                AppModule.HEALTH.key to str(language, "स्वास्थ्य", "Health"),
                                AppModule.FEED.key to str(language, "चारा", "Feed"),
                                AppModule.PRODUCTION.key to str(language, "खाद/उत्पाद", "Compost & Products"),
                                AppModule.DONATIONS.key to str(language, "दान", "Donations"),
                                AppModule.FINANCE.key to str(language, "वित्त", "Finance"),
                                AppModule.FARMERS.key to str(language, "किसान", "Farmers")
                            )
                            modules.forEach { (modKey, label) ->
                                FilterChip(
                                    selected = targetModule == modKey,
                                    onClick = { targetModule = modKey },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("notif_module_${modKey.ifBlank { "ALL" }}")
                                )
                            }
                        }
                    }

                    // Priority Selector (NORMAL / HIGH / URGENT)
                    Text(
                        text = str(language, "प्राथमिकता (Priority)", "Priority"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NotificationPriority.entries.forEach { p ->
                            FilterChip(
                                selected = priority == p,
                                onClick = { priority = p },
                                label = { Text(p.label(isHindi)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("notif_priority_${p.name}")
                            )
                        }
                    }

                    // Responsible Person / Department (for Staff or Reminder items)
                    if (audienceMode == "STAFF" || audienceMode == "SPECIFIC_STAFF" || itemKind == "REMINDER") {
                        OutlinedTextField(
                            value = assigneeName,
                            onValueChange = { assigneeName = it.take(120) },
                            label = { Text(str(language, "जिम्मेदार व्यक्ति / विभाग (वैकल्पिक)", "Responsible Person / Dept (Optional)")) },
                            placeholder = { Text(str(language, "जैसे: चारा टीम / डॉ. वर्मा", "e.g. Feed Team / Dr. Verma")) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notif_assignee_input")
                        )
                    }

                    // 4. Title & Description
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it.take(140)
                            validationError = null
                        },
                        label = { Text(str(language, "सूचना का शीर्षक *", "Notice Title *")) },
                        placeholder = {
                            Text(
                                str(
                                    language,
                                    "जैसे: नया जैविक खाद व गोबर कंडे उपलब्ध",
                                    "e.g., Fresh Organic Manure & Kande Available"
                                )
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_title_input")
                    )

                    OutlinedTextField(
                        value = message,
                        onValueChange = {
                            message = it.take(1500)
                            validationError = null
                        },
                        label = { Text(str(language, "पूरा विवरण *", "Full Description *")) },
                        placeholder = {
                            Text(
                                str(
                                    language,
                                    "उत्पाद की कीमत, उपलब्धता, गतिविधि या रिमाइंडर की पूरी जानकारी लिखें...",
                                    "Write complete details about product price, availability, activity, or reminder..."
                                )
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("notif_message_input")
                    )

                    // 5. Photo Selector (Optional)
                    Text(
                        text = str(language, "फोटो जोड़ें (वैकल्पिक)", "Attach Photo (Optional)"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("notif_pick_photo_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(str(language, "गैलरी से चुनें", "Pick from Gallery"))
                        }
                        if (photoUrl.isNotBlank()) {
                            TextButton(
                                onClick = { photoUrl = "" },
                                modifier = Modifier.testTag("notif_clear_photo_btn")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AlertRed, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(str(language, "फोटो हटाएँ", "Remove"), color = AlertRed)
                            }
                        }
                    }

                    // Built-in Goshala Photo Presets (2x4 grid — all visible without nested horizontal scroll)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        notificationPhotoPresets.chunked(4).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowPresets.forEach { preset ->
                                    val selected = photoUrl.equals(preset.key, ignoreCase = true)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(
                                                width = if (selected) 2.dp else 1.dp,
                                                color = if (selected) ForestGreen else OutlineWarm,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .background(if (selected) SoftGreenTint else MaterialTheme.colorScheme.surface)
                                            .clickable { photoUrl = preset.key }
                                            .padding(4.dp)
                                            .testTag("notif_preset_${preset.key.removePrefix("preset:")}")
                                    ) {
                                        Image(
                                            painter = painterResource(id = preset.drawableRes),
                                            contentDescription = if (isHindi) preset.labelHi else preset.labelEn,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(44.dp)
                                                .clip(RoundedCornerShape(7.dp))
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = if (isHindi) preset.labelHi else preset.labelEn,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (photoUrl.isNotBlank()) {
                        NotificationPhotoBanner(
                            photoUrl = photoUrl,
                            contentDescription = str(language, "चयनित सूचना फोटो", "Selected notification photo"),
                            heightDp = 132
                        )
                    }

                    // 6. Publish Date & Optional Expiry Date
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = publishDate,
                            onValueChange = {
                                publishDate = it.take(10)
                                validationError = null
                            },
                            label = { Text(str(language, "प्रकाशन तिथि", "Publish Date")) },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("notif_publish_date_input")
                        )
                        OutlinedTextField(
                            value = expiryDate,
                            onValueChange = {
                                expiryDate = it.take(10)
                                validationError = null
                            },
                            label = { Text(str(language, "समाप्ति तिथि (वैकल्पिक)", "Expiry (Optional)")) },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("notif_expiry_date_input")
                        )
                    }

                    // 7. Reminder Time & Recurrence (shown when Reminder mode is active)
                    if (itemKind == "REMINDER") {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = str(language, "🔔 रिमाइंडर समय और आवृत्ति", "🔔 Reminder Time & Recurrence"),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                                OutlinedTextField(
                                    value = reminderTime,
                                    onValueChange = {
                                        reminderTime = it.take(5)
                                        validationError = null
                                    },
                                    label = { Text(str(language, "समय (HH:mm)", "Time (HH:mm)")) },
                                    placeholder = { Text("08:00") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("notif_reminder_time_input")
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("06:30", "08:00", "10:00", "12:00", "17:00", "19:00").forEach { slot ->
                                        AssistChip(
                                            onClick = { reminderTime = slot },
                                            label = { Text(slot) }
                                        )
                                    }
                                }
                                Text(
                                    text = str(language, "दोहराव (Recurrence)", "Recurrence"),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = DarkText
                                )
                                ReminderRecurrence.entries.chunked(2).forEach { pair ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        pair.forEach { rec ->
                                            FilterChip(
                                                selected = recurrence == rec,
                                                onClick = { recurrence = rec },
                                                label = { Text(rec.label(isHindi)) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("notif_recurrence_${rec.name}")
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 8. Publish Now vs Save as Draft
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftGreenTint)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (publishNow) {
                                        str(language, "तुरंत प्रकाशित करें", "Publish Immediately")
                                    } else {
                                        str(language, "ड्राफ्ट के रूप में सहेजें (बाद में प्रकाशित)", "Save as Draft (Publish Later)")
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreen
                                )
                                Text(
                                    text = if (publishNow) {
                                        str(language, "लक्षित उपयोगकर्ताओं को तुरंत दिखाई देगी।", "Visible immediately to target users.")
                                    } else {
                                        str(language, "केवल प्रशासक को दिखेगी जब तक प्रकाशित न करें।", "Only visible to Admin until published.")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                            }
                            Switch(
                                checked = publishNow,
                                onCheckedChange = { publishNow = it },
                                modifier = Modifier.testTag("notif_publish_now_switch")
                            )
                        }
                    }

                    validationError?.let { err ->
                        Surface(
                            color = SoftRedTint,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = err,
                                color = AlertRed,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .testTag("notif_editor_error")
                            )
                        }
                    }
                }

                // Footer Actions
                HorizontalDivider(color = OutlineWarm.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { if (!isSubmitting) onDismiss() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(str(language, "रद्द करें", "Cancel"))
                    }
                    Button(
                        enabled = !isSubmitting,
                        onClick = {
                            val cleanTitle = title.trim()
                            val cleanMsg = message.trim()
                            val cleanPubDate = publishDate.trim()
                            val cleanExpDate = expiryDate.trim()
                            val cleanTime = reminderTime.trim()

                            when {
                                cleanTitle.isBlank() -> {
                                    validationError = str(language, "कृपया सूचना का शीर्षक लिखें।", "Please enter a title.")
                                    return@Button
                                }
                                cleanMsg.isBlank() -> {
                                    validationError = str(language, "कृपया सूचना का पूरा विवरण लिखें।", "Please enter the full description.")
                                    return@Button
                                }
                                !isoDateRegex.matches(cleanPubDate) -> {
                                    validationError = str(language, "प्रकाशन तिथि YYYY-MM-DD प्रारूप में लिखें।", "Use YYYY-MM-DD for publish date.")
                                    return@Button
                                }
                                cleanExpDate.isNotBlank() && !isoDateRegex.matches(cleanExpDate) -> {
                                    validationError = str(language, "समाप्ति तिथि YYYY-MM-DD प्रारूप में लिखें।", "Use YYYY-MM-DD for expiry date.")
                                    return@Button
                                }
                                cleanExpDate.isNotBlank() && cleanExpDate < cleanPubDate -> {
                                    validationError = str(language, "समाप्ति तिथि प्रकाशन तिथि से पहले नहीं हो सकती।", "Expiry date cannot be before publish date.")
                                    return@Button
                                }
                                itemKind == "REMINDER" && cleanTime.isNotBlank() && !time24Regex.matches(cleanTime) -> {
                                    validationError = str(language, "रिमाइंडर समय HH:mm (जैसे 08:00) प्रारूप में लिखें।", "Use HH:mm format (e.g. 08:00) for reminder time.")
                                    return@Button
                                }
                                audienceMode == "SPECIFIC_STAFF" && selectedStaffUid.isBlank() -> {
                                    validationError = str(language, "कृपया किसी विशिष्ट स्टाफ सदस्य को चुनें।", "Please select a specific staff member.")
                                    return@Button
                                }
                            }

                            val resolvedTargetRole = if (audienceMode == "SPECIFIC_STAFF") "STAFF" else audienceMode
                            val resolvedTargetUid = if (audienceMode == "SPECIFIC_STAFF") selectedStaffUid.trim() else ""
                            val resolvedTargetModule = if (audienceMode == "STAFF") normalizeNotifModuleKey(targetModule) else ""

                            val itemToSave = (existing ?: NotificationItem()).copy(
                                targetRole = resolvedTargetRole,
                                targetUid = resolvedTargetUid,
                                targetModule = resolvedTargetModule,
                                category = selectedCategory.name,
                                title = cleanTitle,
                                message = cleanMsg,
                                photoUrl = photoUrl.trim(),
                                published = publishNow,
                                publishDate = cleanPubDate,
                                expiryDate = cleanExpDate,
                                itemKind = itemKind,
                                reminderTime = if (itemKind == "REMINDER") cleanTime else "",
                                recurrence = if (itemKind == "REMINDER") recurrence.name else ReminderRecurrence.ONCE.name,
                                priority = priority.name,
                                assigneeName = assigneeName.trim()
                            )
                            onSave(itemToSave, isEdit)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("notif_save_button")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = PureWhite
                            )
                        } else {
                            Icon(
                                imageVector = if (publishNow) Icons.Default.Publish else Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isEdit -> str(language, "अपडेट करें", "Update")
                                    publishNow -> str(language, "प्रकाशित करें", "Publish")
                                    else -> str(language, "ड्राफ्ट सहेजें", "Save Draft")
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

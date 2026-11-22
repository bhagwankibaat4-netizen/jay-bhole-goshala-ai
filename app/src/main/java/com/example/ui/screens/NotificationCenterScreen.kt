package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.core.AppLanguage
import com.example.core.str
import com.example.data.model.*
import com.example.ui.components.EmptyStateCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.DataUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class NotifTab(val labelHi: String, val labelEn: String, val testTagKey: String) {
    ALL("सभी", "All", "all"),
    ACTIVE("सक्रिय", "Active", "active"),
    PUBLISHED("प्रकाशित", "Published", "published"),
    DRAFTS("ड्राफ्ट", "Drafts", "drafts"),
    REMINDERS("रिमाइंडर", "Reminders", "reminders"),
    HISTORY("इतिहास", "History", "history")
}

@Composable
fun NotificationCenterScreen(
    language: AppLanguage,
    session: UserSession,
    notificationsState: DataUiState<List<NotificationItem>>,
    alertsEnabled: Boolean = true,
    remindersEnabled: Boolean = true,
    isSubmitting: Boolean = false,
    staffAccounts: List<StaffAccount> = emptyList(),
    initialNotificationId: String? = null,
    onConsumeInitialNotificationId: () -> Unit = {},
    onToggleAlerts: (Boolean) -> Unit = {},
    onToggleReminders: (Boolean) -> Unit = {},
    onMarkRead: (String) -> Unit,
    onMarkUnread: (String) -> Unit = {},
    onMarkAllRead: (List<String>) -> Unit = {},
    onToggleActionDone: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateToRelatedRoute: ((String) -> Unit)? = null,
    onSaveNotification: (NotificationItem, Boolean) -> Unit = { _, _ -> },
    onSetPublished: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteNotification: (NotificationItem) -> Unit = {}
) {
    val context = LocalContext.current
    val isHindi = language == AppLanguage.HINDI
    val isAdmin = session.role == UserRole.ADMIN
    val todayIso = remember { todayIsoDateString() }

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

    val availableTabs = remember(isAdmin) {
        if (isAdmin) {
            listOf(NotifTab.ALL, NotifTab.PUBLISHED, NotifTab.DRAFTS, NotifTab.REMINDERS, NotifTab.HISTORY)
        } else {
            listOf(NotifTab.ACTIVE, NotifTab.REMINDERS, NotifTab.HISTORY)
        }
    }
    var selectedTab by remember(isAdmin) {
        mutableStateOf(if (isAdmin) NotifTab.ALL else NotifTab.ACTIVE)
    }
    var selectedCategory by remember { mutableStateOf<NotificationCategory?>(null) }

    var detailItem by remember { mutableStateOf<NotificationItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<NotificationItem?>(null) }
    var deletingItem by remember { mutableStateOf<NotificationItem?>(null) }

    val allItems = (notificationsState as? DataUiState.Success)?.data.orEmpty()
    val visibleForRole = remember(allItems, session, todayIso) {
        allItems.filter { it.isVisibleToSession(session, todayIso) }
    }
    val unreadActiveIds = remember(visibleForRole, session.uid, todayIso) {
        visibleForRole
            .filter { it.published && !it.isExpired(todayIso) && !it.isReadBy(session.uid) }
            .map { it.id }
            .filter { it.isNotBlank() }
    }

    val filteredItems = remember(
        visibleForRole,
        selectedTab,
        selectedCategory,
        isAdmin,
        session.uid,
        todayIso,
        alertsEnabled,
        remindersEnabled
    ) {
        val byTab = when (selectedTab) {
            NotifTab.ALL -> visibleForRole
            NotifTab.PUBLISHED -> visibleForRole.filter { it.published && !it.isExpired(todayIso) }
            NotifTab.DRAFTS -> visibleForRole.filter { !it.published || it.isScheduledFuture(todayIso) }
            NotifTab.ACTIVE -> visibleForRole.filter {
                !it.isExpired(todayIso) && (
                    if (it.isReminderItem()) remindersEnabled else alertsEnabled
                )
            }
            NotifTab.REMINDERS -> visibleForRole.filter {
                it.isReminderItem() && (isAdmin || (!it.isExpired(todayIso) && remindersEnabled))
            }
            NotifTab.HISTORY -> visibleForRole.filter {
                if (isAdmin) it.isExpired(todayIso) || it.isReadBy(session.uid)
                else it.isHistoryForUser(session.uid, todayIso)
            }
        }
        val byCat = if (selectedCategory == null) {
            byTab
        } else {
            byTab.filter { it.resolvedCategory() == selectedCategory }
        }
        // Unread items surface first within active lists
        byCat.sortedWith(
            compareBy<NotificationItem> { if (!isAdmin && selectedTab == NotifTab.ACTIVE) it.isReadBy(session.uid) else false }
                .thenByDescending { it.createdAt?.seconds ?: 0L }
                .thenByDescending { it.publishDate }
        )
    }

    // Keep detail dialog item synced with live updates
    val liveDetailItem = remember(detailItem, visibleForRole) {
        val id = detailItem?.id
        if (id.isNullOrBlank()) detailItem else visibleForRole.firstOrNull { it.id == id } ?: detailItem
    }

    // Auto-open specific notification when launched from a system reminder tap
    LaunchedEffect(initialNotificationId, visibleForRole) {
        val targetId = initialNotificationId?.trim().orEmpty()
        if (targetId.isNotBlank()) {
            val matched = visibleForRole.firstOrNull { it.id == targetId }
            if (matched != null) {
                detailItem = matched
                if (!matched.isReadBy(session.uid)) {
                    onMarkRead(matched.id)
                }
                onConsumeInitialNotificationId()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("notification_center_list"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // 1. Compact Role-Specific Header + Alert/Reminder Toggles + Permission Bar
        item(key = "notif_hero_header") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ForestGreen, GoshalaGreen)
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Golden,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (session.role) {
                                    UserRole.ADMIN -> str(language, "सूचना एवं रिमाइंडर प्रबंधन", "Notifications & Reminders Admin")
                                    UserRole.STAFF -> str(language, "स्टाफ सूचना एवं कार्य रिमाइंडर", "Staff Notices & Work Reminders")
                                    UserRole.PUBLIC -> str(language, "गौशाला सूचना केंद्र एवं अलर्ट", "Goshala Notice Center & Alerts")
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = PureWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = when (session.role) {
                                    UserRole.ADMIN -> str(
                                        language,
                                        "कुल ${visibleForRole.size} • प्रकाशित ${visibleForRole.count { it.published }} • ड्राफ्ट ${visibleForRole.count { !it.published }}",
                                        "Total ${visibleForRole.size} • Published ${visibleForRole.count { it.published }} • Drafts ${visibleForRole.count { !it.published }}"
                                    )
                                    UserRole.STAFF -> str(
                                        language,
                                        "अपठित: ${unreadActiveIds.size} • कार्य रिमाइंडर",
                                        "Unread: ${unreadActiveIds.size} • Work reminders"
                                    )
                                    UserRole.PUBLIC -> str(
                                        language,
                                        "नई अपठित: ${unreadActiveIds.size} • उत्पाद, गतिविधियाँ व घोषणाएँ",
                                        "New unread: ${unreadActiveIds.size} • Products & updates"
                                    )
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isAdmin) {
                            FilledTonalButton(
                                onClick = {
                                    editingItem = null
                                    showEditor = true
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Saffron,
                                    contentColor = PureWhite
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(34.dp).testTag("admin_create_notif_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = str(language, "नया जोड़ें", "Add New"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else if (unreadActiveIds.isNotEmpty()) {
                            TextButton(
                                onClick = { onMarkAllRead(unreadActiveIds) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp).testTag("notif_mark_all_read_btn")
                            ) {
                                Text(
                                    text = str(language, "सभी पढ़ें", "Read All"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Golden,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Compact Alert & Reminder ON/OFF Controls + Permission trigger in one sleek bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { onToggleAlerts(!alertsEnabled) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (alertsEnabled) PureWhite.copy(alpha = 0.22f) else PureWhite.copy(alpha = 0.08f),
                            modifier = Modifier.testTag("toggle_alerts_enabled")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (alertsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (alertsEnabled) Golden else PureWhite.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = str(
                                        language,
                                        if (alertsEnabled) "अलर्ट: चालू" else "अलर्ट: बंद",
                                        if (alertsEnabled) "Alerts: ON" else "Alerts: OFF"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Surface(
                            onClick = { onToggleReminders(!remindersEnabled) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (remindersEnabled) PureWhite.copy(alpha = 0.22f) else PureWhite.copy(alpha = 0.08f),
                            modifier = Modifier.testTag("toggle_reminders_enabled")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (remindersEnabled) Icons.Default.AlarmOn else Icons.Default.AlarmOff,
                                    contentDescription = null,
                                    tint = if (remindersEnabled) Golden else PureWhite.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = str(
                                        language,
                                        if (remindersEnabled) "रिमाइंडर: चालू" else "रिमाइंडर: बंद",
                                        if (remindersEnabled) "Reminders: ON" else "Reminders: OFF"
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PureWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!notifPermissionGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            Spacer(Modifier.weight(1f))
                            Surface(
                                onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                                shape = RoundedCornerShape(10.dp),
                                color = Saffron,
                                modifier = Modifier.testTag("notif_permission_allow_btn")
                            ) {
                                Text(
                                    text = str(language, "अनुमति दें", "Allow"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Compact Tabs + 7 Category Filter Chips
        item(key = "notif_tabs_and_categories") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    availableTabs.forEach { tab ->
                        val selected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) ForestGreen else CreamSurfaceVariant,
                            modifier = Modifier.testTag("notif_tab_${tab.testTagKey}")
                        ) {
                            Text(
                                text = if (isHindi) tab.labelHi else tab.labelEn,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) PureWhite else DarkText,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = { selectedCategory = null },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedCategory == null) SoftGreenTint else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, if (selectedCategory == null) ForestGreen else OutlineWarm),
                        modifier = Modifier.testTag("notif_filter_ALL")
                    ) {
                        Text(
                            text = str(language, "सभी श्रेणियाँ", "All Categories"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selectedCategory == null) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedCategory == null) ForestGreen else DarkText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    NotificationCategory.entries.forEach { cat ->
                        val selected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = if (selected) null else cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) SoftGreenTint else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (selected) ForestGreen else OutlineWarm),
                            modifier = Modifier.testTag("notif_filter_${cat.name}")
                        ) {
                            Text(
                                text = "${cat.emoji} ${cat.label(isHindi)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) ForestGreen else DarkText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Notice if user paused alerts or reminders
        if (!isAdmin && ((!alertsEnabled && selectedTab == NotifTab.ACTIVE) || (!remindersEnabled && selectedTab == NotifTab.REMINDERS))) {
            item(key = "notif_muted_banner") {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SoftGoldTint)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsOff, contentDescription = null, tint = Saffron)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (!remindersEnabled && selectedTab == NotifTab.REMINDERS) {
                                str(language, "रिमाइंडर अलर्ट बंद हैं। देखने के लिए ऊपर स्विच चालू करें।", "Reminder alerts are OFF. Turn ON the switch above to view them.")
                            } else {
                                str(language, "मुख्य सूचना अलर्ट बंद हैं। ऊपर से कभी भी चालू करें।", "Important alerts are OFF. Turn ON the switch above anytime.")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = DarkText
                        )
                    }
                }
            }
        }

        // 6. Notification & Reminder Cards List
        when (notificationsState) {
            is DataUiState.Loading -> {
                item(key = "notif_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ForestGreen)
                    }
                }
            }
            is DataUiState.Error -> {
                item(key = "notif_error") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SoftRedTint)
                    ) {
                        Text(
                            text = notificationsState.message,
                            color = AlertRed,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is DataUiState.Success -> {
                if (filteredItems.isEmpty()) {
                    item(key = "notif_empty") {
                        EmptyStateCard(
                            icon = if (selectedTab == NotifTab.REMINDERS) Icons.Default.AlarmOff else Icons.Default.NotificationsNone,
                            title = when (selectedTab) {
                                NotifTab.DRAFTS -> str(language, "कोई ड्राफ्ट सूचना नहीं", "No Draft Notices")
                                NotifTab.REMINDERS -> str(language, "कोई सक्रिय रिमाइंडर नहीं", "No Active Reminders")
                                NotifTab.HISTORY -> str(language, "इतिहास अभी खाली है", "History Is Empty")
                                else -> str(language, "कोई नई सूचना नहीं", "No Notifications Yet")
                            },
                            subtitle = if (isAdmin) {
                                str(
                                    language,
                                    "ऊपर '+ नई सूचना / रिमाइंडर' बटन से गौशाला के उत्पाद, गतिविधि या रिमाइंडर प्रकाशित करें।",
                                    "Tap '+ New Notice / Reminder' above to publish Goshala products, activities, or reminders."
                                )
                            } else {
                                str(
                                    language,
                                    "गौशाला उत्पाद, गतिविधियाँ, दान अभियान और रिमाइंडर यहाँ दिखेंगे।",
                                    "Goshala products, activities, donation campaigns, and reminders appear here."
                                )
                            }
                        )
                    }
                } else {
                    items(filteredItems, key = { it.id.ifBlank { "${it.title}_${it.publishDate}" } }) { item ->
                        NotificationItemCard(
                            item = item,
                            language = language,
                            session = session,
                            todayIso = todayIso,
                            onOpen = {
                                detailItem = item
                                if (!item.isReadBy(session.uid) && item.id.isNotBlank()) {
                                    onMarkRead(item.id)
                                }
                            },
                            onToggleActionDone = {
                                if (item.id.isNotBlank()) {
                                    onToggleActionDone(item.id, !item.isCompletedBy(session.uid))
                                }
                            },
                            onEdit = {
                                editingItem = item
                                showEditor = true
                            },
                            onTogglePublish = {
                                if (item.id.isNotBlank()) {
                                    onSetPublished(item.id, !item.published)
                                }
                            },
                            onDelete = { deletingItem = item }
                        )
                    }
                }
            }
        }
    }

    // Full Notice / Reminder Detail Dialog
    liveDetailItem?.let { item ->
        val relatedRoute = remember(item, session) {
            resolveRelatedRouteForSession(item, session)
        }
        NotificationDetailDialog(
            item = item,
            language = language,
            session = session,
            todayIso = todayIso,
            relatedRoute = relatedRoute,
            onDismiss = { detailItem = null },
            onToggleRead = {
                if (item.id.isNotBlank()) {
                    if (item.isReadBy(session.uid)) {
                        onMarkUnread(item.id)
                    } else {
                        onMarkRead(item.id)
                    }
                }
            },
            onToggleActionDone = {
                if (item.id.isNotBlank()) {
                    onToggleActionDone(item.id, !item.isCompletedBy(session.uid))
                }
            },
            onOpenRelatedRoute = if (relatedRoute != null && onNavigateToRelatedRoute != null) {
                {
                    detailItem = null
                    onNavigateToRelatedRoute(relatedRoute)
                }
            } else null,
            onEdit = if (isAdmin) {
                {
                    detailItem = null
                    editingItem = item
                    showEditor = true
                }
            } else null
        )
    }

    // Admin Create / Edit Dialog
    if (showEditor && isAdmin) {
        NotificationEditorDialog(
            language = language,
            existing = editingItem,
            isSubmitting = isSubmitting,
            staffAccounts = staffAccounts,
            onDismiss = {
                showEditor = false
                editingItem = null
            },
            onSave = { item, isEdit ->
                onSaveNotification(item, isEdit)
                showEditor = false
                editingItem = null
            }
        )
    }

    // Admin Delete Confirmation Dialog
    deletingItem?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = AlertRed) },
            title = { Text(str(language, "सूचना हटाएँ?", "Delete Notification?")) },
            text = {
                Text(
                    str(
                        language,
                        "क्या आप \"${item.title}\" को स्थायी रूप से हटाना चाहते हैं?",
                        "Do you want to permanently delete \"${item.title}\"?"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteNotification(item)
                        deletingItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = PureWhite),
                    modifier = Modifier.testTag("notif_confirm_delete_btn")
                ) {
                    Text(str(language, "हटाएँ", "Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text(str(language, "रद्द करें", "Cancel"))
                }
            }
        )
    }
}

@Composable
private fun NotificationItemCard(
    item: NotificationItem,
    language: AppLanguage,
    session: UserSession,
    todayIso: String,
    onOpen: () -> Unit,
    onToggleActionDone: () -> Unit,
    onEdit: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit
) {
    val isHindi = language == AppLanguage.HINDI
    val isAdmin = session.role == UserRole.ADMIN
    val isActiveStaffWorkItem = session.role == UserRole.STAFF && session.staffActive &&
        (item.targetRole.equals("STAFF", ignoreCase = true) || item.targetUid == session.uid)
    val isRead = item.isReadBy(session.uid)
    val isActionDone = item.isCompletedBy(session.uid)
    val isExpired = item.isExpired(todayIso)
    val isScheduled = item.isScheduledFuture(todayIso)
    val category = item.resolvedCategory()
    val priority = item.resolvedPriority()

    val cardContainer = when {
        !item.published -> SoftGoldTint.copy(alpha = 0.7f)
        !isRead -> SoftGreenTint
        else -> MaterialTheme.colorScheme.surface
    }
    val borderStroke = when {
        !item.published -> BorderStroke(1.2.dp, Saffron.copy(alpha = 0.7f))
        !isRead -> BorderStroke(1.5.dp, ForestGreen.copy(alpha = 0.65f))
        else -> BorderStroke(1.dp, OutlineWarm.copy(alpha = 0.7f))
    }

    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardContainer),
        border = borderStroke,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notif_card_${item.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top badges row: Category (flex-shrink) + guaranteed-visible NEW/Read/Draft pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ForestGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = notificationCategoryIcon(category),
                        contentDescription = null,
                        tint = ForestGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${category.emoji} ${category.label(isHindi)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ForestGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (!item.published) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Saffron)
                            .testTag("notif_draft_badge_${item.id}")
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = str(language, "ड्राफ्ट", "DRAFT"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                } else if (isScheduled) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Golden)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = str(language, "निर्धारित", "SCHEDULED"),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = DarkText
                        )
                    }
                } else if (isExpired) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = str(language, "समाप्त", "EXPIRED"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                } else if (!isRead) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Saffron)
                            .testTag("notif_new_badge_${item.id}")
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "NEW",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = PureWhite
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("notif_read_badge_${item.id}")
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = str(language, "पढ़ा गया", "Read"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
            }

            // Optional Photo Banner
            if (item.photoUrl.isNotBlank()) {
                NotificationPhotoBanner(
                    photoUrl = item.photoUrl,
                    contentDescription = item.title,
                    heightDp = 148
                )
            }

            // Title & Description Preview
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isRead) FontWeight.Medium else FontWeight.Bold,
                color = if (isRead) DarkText.copy(alpha = 0.85f) else ForestGreen
            )
            Text(
                text = item.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // Priority & Assignee Meta Pills (when set)
            if (priority != NotificationPriority.NORMAL || item.assigneeName.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (priority != NotificationPriority.NORMAL) {
                        val badgeColor = if (priority == NotificationPriority.URGENT) AlertRed else Saffron
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeColor.copy(alpha = 0.14f))
                                .testTag("notif_priority_badge_${item.id}")
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⚡ ${priority.label(isHindi)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }
                    }
                    if (item.assigneeName.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Icon(
                                Icons.Default.PersonOutline,
                                contentDescription = null,
                                tint = ForestGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = str(language, "जिम्मेदार: ${item.assigneeName}", "Assignee: ${item.assigneeName}"),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = ForestGreen,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Date, Reminder Schedule, & Audience Meta Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MutedText,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatNotifDisplayDate(item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }

                if (item.isReminderItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Saffron,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        val timeText = item.reminderTime.ifBlank { "08:00" }
                        Text(
                            text = "$timeText • ${item.resolvedRecurrence().label(isHindi)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Saffron
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                if (isAdmin || session.role == UserRole.STAFF) {
                    Text(
                        text = audienceLabel(item, isHindi),
                        style = MaterialTheme.typography.labelSmall,
                        color = GoshalaGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Staff Required-Action Status Toggle (लंबित / पूर्ण)
            if (isActiveStaffWorkItem) {
                HorizontalDivider(color = OutlineWarm.copy(alpha = 0.45f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isActionDone) {
                            str(language, "कार्रवाई स्थिति: पूर्ण ✓", "Action Status: Completed ✓")
                        } else {
                            str(language, "कार्रवाई स्थिति: लंबित", "Action Status: Pending")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActionDone) ForestGreen else Saffron
                    )
                    OutlinedButton(
                        onClick = onToggleActionDone,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("notif_action_toggle_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (isActionDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isActionDone) ForestGreen else Saffron,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isActionDone) {
                                str(language, "पूर्ण", "Done")
                            } else {
                                str(language, "पूर्ण करें", "Mark Done")
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // Admin Completion Summary when staff have marked action complete
            if (isAdmin && item.completedByUids.isNotEmpty()) {
                Text(
                    text = str(
                        language,
                        "स्टाफ कार्रवाई पूर्ण: ${item.completedByUids.size}",
                        "Staff Completed: ${item.completedByUids.size}"
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestGreen
                )
            }

            // Admin Quick Management Actions (Edit, Publish/Unpublish, Delete)
            if (isAdmin) {
                HorizontalDivider(color = OutlineWarm.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("notif_edit_${item.id}")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(str(language, "संपादित", "Edit"), style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(
                        onClick = onTogglePublish,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("notif_toggle_pub_${item.id}")
                    ) {
                        Icon(
                            imageVector = if (item.published) Icons.Default.VisibilityOff else Icons.Default.Publish,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (item.published) str(language, "अप्रकाशित", "Unpublish") else str(language, "प्रकाशित", "Publish"),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag("notif_delete_${item.id}")
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = str(language, "हटाएँ", "Delete"),
                            tint = AlertRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationDetailDialog(
    item: NotificationItem,
    language: AppLanguage,
    session: UserSession,
    todayIso: String,
    relatedRoute: String?,
    onDismiss: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleActionDone: () -> Unit,
    onOpenRelatedRoute: (() -> Unit)?,
    onEdit: (() -> Unit)?
) {
    val isHindi = language == AppLanguage.HINDI
    val isRead = item.isReadBy(session.uid)
    val isActionDone = item.isCompletedBy(session.uid)
    val isActiveStaffWorkItem = session.role == UserRole.STAFF && session.staffActive &&
        (item.targetRole.equals("STAFF", ignoreCase = true) || item.targetUid == session.uid)
    val category = item.resolvedCategory()
    val priority = item.resolvedPriority()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Header
                Surface(color = ForestGreen, contentColor = PureWhite) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = notificationCategoryIcon(category),
                            contentDescription = null,
                            tint = Golden
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${category.emoji} ${category.label(isHindi)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("notif_detail_close_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = str(language, "बंद करें", "Close"), tint = PureWhite)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (item.photoUrl.isNotBlank()) {
                        NotificationPhotoBanner(
                            photoUrl = item.photoUrl,
                            contentDescription = item.title,
                            heightDp = 190
                        )
                    }

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreen,
                        modifier = Modifier.testTag("notif_detail_title")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistChip(
                            onClick = {},
                            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(15.dp)) },
                            label = { Text(formatNotifDisplayDate(item)) }
                        )
                        if (item.isReminderItem()) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = { Icon(Icons.Default.Alarm, contentDescription = null, tint = Saffron, modifier = Modifier.size(15.dp)) },
                                label = {
                                    Text("${item.reminderTime.ifBlank { "08:00" }} • ${item.resolvedRecurrence().label(isHindi)}")
                                }
                            )
                        }
                        if (priority != NotificationPriority.NORMAL) {
                            AssistChip(
                                onClick = {},
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.PriorityHigh,
                                        contentDescription = null,
                                        tint = if (priority == NotificationPriority.URGENT) AlertRed else Saffron,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text(priority.label(isHindi)) }
                            )
                        }
                    }

                    if (item.assigneeName.isNotBlank()) {
                        Text(
                            text = str(language, "जिम्मेदार व्यक्ति / विभाग: ${item.assigneeName}", "Responsible: ${item.assigneeName}"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = ForestGreen
                        )
                    }

                    if (item.expiryDate.isNotBlank()) {
                        val expired = item.isExpired(todayIso)
                        Text(
                            text = if (expired) {
                                str(language, "समाप्ति तिथि (बीत चुकी): ${item.expiryDate}", "Expired on: ${item.expiryDate}")
                            } else {
                                str(language, "वैध तिथि तक: ${item.expiryDate}", "Valid until: ${item.expiryDate}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (expired) AlertRed else MutedText
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CreamSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DarkText,
                            modifier = Modifier
                                .padding(14.dp)
                                .testTag("notif_detail_message")
                        )
                    }

                    if (isActiveStaffWorkItem) {
                        OutlinedButton(
                            onClick = onToggleActionDone,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notif_detail_action_toggle")
                        ) {
                            Icon(
                                imageVector = if (isActionDone) Icons.Default.CheckCircle else Icons.Default.TaskAlt,
                                contentDescription = null,
                                tint = if (isActionDone) ForestGreen else Saffron,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isActionDone) {
                                    str(language, "कार्य पूर्ण चिह्नित है (लंबित करें)", "Action Marked Done (Undo)")
                                } else {
                                    str(language, "आवश्यक कार्य पूर्ण चिह्नित करें", "Mark Required Action Completed")
                                },
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (onOpenRelatedRoute != null && relatedRoute != null) {
                        Button(
                            onClick = onOpenRelatedRoute,
                            colors = ButtonDefaults.buttonColors(containerColor = Saffron, contentColor = PureWhite),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notif_detail_open_related")
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = relatedRouteLabel(relatedRoute, isHindi),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                HorizontalDivider(color = OutlineWarm.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onToggleRead,
                        modifier = Modifier.testTag("notif_detail_toggle_read")
                    ) {
                        Icon(
                            imageVector = if (isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isRead) {
                                str(language, "अपठित करें", "Mark Unread")
                            } else {
                                str(language, "पढ़ा चिह्नित करें", "Mark Read")
                            }
                        )
                    }
                    if (onEdit != null) {
                        OutlinedButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(str(language, "संपादित", "Edit"))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ForestGreen, contentColor = PureWhite)
                    ) {
                        Text(str(language, "बंद करें", "Close"))
                    }
                }
            }
        }
    }
}

private fun formatNotifDisplayDate(item: NotificationItem): String {
    if (item.publishDate.isNotBlank()) return item.publishDate
    val seconds = item.createdAt?.seconds ?: return todayIsoDateString()
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(seconds * 1000L))
}

private fun audienceLabel(item: NotificationItem, isHindi: Boolean): String {
    if (item.targetUid.isNotBlank()) {
        val who = item.assigneeName.ifBlank { if (isHindi) "विशिष्ट स्टाफ" else "Specific Staff" }
        return if (isHindi) "निजी • $who" else "Private • $who"
    }
    val base = when (item.targetRole.uppercase()) {
        "PUBLIC" -> if (isHindi) "सार्वजनिक" else "Public"
        "STAFF" -> if (isHindi) "स्टाफ" else "Staff"
        "ADMIN" -> if (isHindi) "प्रशासक" else "Admin"
        else -> if (isHindi) "सभी" else "All"
    }
    val normMod = normalizeNotifModuleKey(item.targetModule)
    return if (item.targetRole.equals("STAFF", ignoreCase = true) && normMod.isNotBlank()) {
        "$base • ${normMod.uppercase()}"
    } else {
        base
    }
}

private fun resolveRelatedRouteForSession(item: NotificationItem, session: UserSession): String? {
    val normMod = normalizeNotifModuleKey(item.targetModule)
    if (normMod.isNotBlank()) {
        return when (normMod) {
            AppModule.CATTLE.key -> Routes.CATTLE.takeIf { session.canView(AppModule.CATTLE) }
            AppModule.HEALTH.key -> Routes.HEALTH.takeIf { session.canView(AppModule.HEALTH) }
            AppModule.FEED.key -> Routes.FEED.takeIf { session.canView(AppModule.FEED) }
            AppModule.PRODUCTION.key -> Routes.PRODUCTION.takeIf { session.canView(AppModule.PRODUCTION) }
            AppModule.DONATIONS.key -> Routes.DONATIONS.takeIf { session.canView(AppModule.DONATIONS) }
            AppModule.FINANCE.key -> Routes.FINANCE.takeIf { session.canView(AppModule.FINANCE) }
            AppModule.FARMERS.key -> Routes.FARMERS.takeIf { session.canView(AppModule.FARMERS) }
            else -> null
        }
    }
    return when (item.resolvedCategory()) {
        NotificationCategory.PRODUCTS, NotificationCategory.PRODUCT_AVAILABILITY -> when (session.role) {
            UserRole.ADMIN -> Routes.ADMIN_PRODUCTS
            UserRole.PUBLIC -> Routes.PUBLIC_DASHBOARD
            UserRole.STAFF -> Routes.PRODUCTION.takeIf { session.canView(AppModule.PRODUCTION) }
        }
        NotificationCategory.DONATIONS, NotificationCategory.ADOPTION ->
            Routes.DONATIONS.takeIf { session.canView(AppModule.DONATIONS) }
        NotificationCategory.ACTIVITIES ->
            Routes.CATTLE.takeIf { session.canView(AppModule.CATTLE) }
        else -> null
    }
}

private fun relatedRouteLabel(route: String, isHindi: Boolean): String = when (route) {
    Routes.CATTLE -> if (isHindi) "गौवंश अनुभाग खोलें" else "Open Cattle Section"
    Routes.HEALTH -> if (isHindi) "स्वास्थ्य व उपचार खोलें" else "Open Health & Vet Care"
    Routes.FEED -> if (isHindi) "चारा व कार्य खोलें" else "Open Feed & Operations"
    Routes.PRODUCTION -> if (isHindi) "खाद व उत्पाद अनुभाग खोलें" else "Open Compost & Products"
    Routes.ADMIN_PRODUCTS -> if (isHindi) "उत्पाद प्रबंधन खोलें" else "Open Product Management"
    Routes.PUBLIC_DASHBOARD -> if (isHindi) "गौशाला उत्पाद देखें" else "View Goshala Products"
    Routes.DONATIONS -> if (isHindi) "दान व गौ-गोद अनुभाग खोलें" else "Open Donations & Adoption"
    Routes.FINANCE -> if (isHindi) "वित्त अनुभाग खोलें" else "Open Finance Section"
    Routes.FARMERS -> if (isHindi) "किसान सहयोग अनुभाग खोलें" else "Open Farmers Section"
    else -> if (isHindi) "संबंधित अनुभाग खोलें" else "Open Related Section"
}

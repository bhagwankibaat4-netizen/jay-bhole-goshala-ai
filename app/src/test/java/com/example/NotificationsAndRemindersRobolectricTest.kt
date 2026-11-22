package com.example

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.core.AppLanguage
import com.example.core.NotificationReminderScheduler
import com.example.core.ReminderAlarmReceiver
import com.example.core.ReminderBootReceiver
import com.example.data.model.*
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.Routes
import com.example.ui.theme.GosalaTheme
import com.example.ui.viewmodel.DataUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotificationsAndRemindersRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val adminSession = UserSession(
        uid = "admin-uid-1",
        displayName = "गौशाला प्रशासक",
        email = "admin@gosala.org",
        role = UserRole.ADMIN
    )

    private val publicSession = UserSession(
        uid = "public-uid-1",
        displayName = "गौ-भक्त रमेश",
        email = "ramesh@example.com",
        role = UserRole.PUBLIC
    )

    private val staffHealthSession = UserSession(
        uid = "staff-uid-1",
        displayName = "स्टाफ मोहन",
        email = "mohan@gosala.org",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(
            AppModule.HEALTH.key to listOf(CrudAction.VIEW.name, CrudAction.ADD.name),
            AppModule.CATTLE.key to listOf(CrudAction.VIEW.name)
        )
    )

    private val staffFinanceSession = UserSession(
        uid = "staff-uid-2",
        displayName = "स्टाफ सुरेश",
        email = "suresh@gosala.org",
        role = UserRole.STAFF,
        staffActive = true,
        permissions = mapOf(
            AppModule.FINANCE.key to listOf(CrudAction.VIEW.name)
        )
    )

    private val sampleStaffAccounts = listOf(
        StaffAccount(
            uid = "staff-uid-1",
            name = "स्टाफ मोहन",
            email = "mohan@gosala.org",
            designation = "स्वास्थ्य सहायक",
            active = true
        ),
        StaffAccount(
            uid = "staff-uid-2",
            name = "स्टाफ सुरेश",
            email = "suresh@gosala.org",
            designation = "लेखा सहायक",
            active = true
        )
    )

    @Test
    fun model_sevenCategories_visibilityScoping_and_dedupKey_workCorrectly() {
        assertEquals(7, NotificationCategory.entries.size)
        assertEquals(NotificationCategory.REMINDERS, NotificationCategory.fromRaw("HEALTH"))
        assertEquals(NotificationCategory.DONATIONS, NotificationCategory.fromRaw("DONATION"))
        assertEquals(NotificationCategory.ADOPTION, NotificationCategory.fromRaw("SPONSORSHIP"))
        assertEquals(NotificationCategory.PRODUCT_AVAILABILITY, NotificationCategory.fromRaw("ORDER"))
        assertEquals(NotificationCategory.PRODUCTS, NotificationCategory.fromRaw("COMPOST"))
        assertEquals(NotificationCategory.ACTIVITIES, NotificationCategory.fromRaw("CATTLE"))

        val today = "2026-11-22"
        val publicNotice = NotificationItem(
            id = "n1",
            targetRole = "PUBLIC",
            category = NotificationCategory.PRODUCTS.name,
            title = "नया जैविक खाद उपलब्ध",
            message = "ताजा जैविक खाद ₹150 प्रति बैग में उपलब्ध है।",
            published = true,
            publishDate = "2026-11-20"
        )
        val draftNotice = publicNotice.copy(id = "n2", published = false)
        val futureNotice = publicNotice.copy(id = "n3", publishDate = "2026-12-01")
        val staffHealthReminder = NotificationItem(
            id = "n4",
            targetRole = "STAFF",
            targetModule = AppModule.HEALTH.key,
            category = NotificationCategory.REMINDERS.name,
            itemKind = "REMINDER",
            reminderTime = "08:00",
            recurrence = ReminderRecurrence.DAILY.name,
            priority = NotificationPriority.HIGH.name,
            assigneeName = "स्वास्थ्य टीम",
            title = "सुबह टीकाकरण जाँच",
            message = "बीमार गायों का तापमान जाँचें।",
            published = true,
            publishDate = "2026-11-22"
        )
        val staffFinanceReminder = staffHealthReminder.copy(
            id = "n5",
            targetModule = AppModule.FINANCE.key,
            title = "मासिक वित्त मिलान"
        )
        val privateStaffNotice = NotificationItem(
            id = "n6",
            targetRole = "STAFF",
            targetUid = "staff-uid-1",
            assigneeName = "स्टाफ मोहन",
            priority = NotificationPriority.URGENT.name,
            category = NotificationCategory.REMINDERS.name,
            itemKind = "REMINDER",
            title = "केवल मोहन के लिए निजी कार्य",
            message = "शेड 2 की गाय का तत्काल परीक्षण करें।",
            published = true,
            publishDate = "2026-11-22"
        )
        val privateStaffDraft = privateStaffNotice.copy(id = "n7", published = false)

        // Admin sees all (including drafts, scheduled, staff reminders, private staff)
        assertTrue(publicNotice.isVisibleToSession(adminSession, today))
        assertTrue(draftNotice.isVisibleToSession(adminSession, today))
        assertTrue(futureNotice.isVisibleToSession(adminSession, today))
        assertTrue(staffHealthReminder.isVisibleToSession(adminSession, today))
        assertTrue(staffFinanceReminder.isVisibleToSession(adminSession, today))
        assertTrue(privateStaffNotice.isVisibleToSession(adminSession, today))
        assertTrue(privateStaffDraft.isVisibleToSession(adminSession, today))

        // Public sees ONLY published active broadcast PUBLIC/ALL notices
        assertTrue(publicNotice.isVisibleToSession(publicSession, today))
        assertFalse(draftNotice.isVisibleToSession(publicSession, today))
        assertFalse(futureNotice.isVisibleToSession(publicSession, today))
        assertFalse(staffHealthReminder.isVisibleToSession(publicSession, today))
        assertFalse(privateStaffNotice.isVisibleToSession(publicSession, today))

        // Correct Staff sees PUBLIC + permitted module + own published private notice (NOT drafts or other modules)
        assertTrue(publicNotice.isVisibleToSession(staffHealthSession, today))
        assertTrue(staffHealthReminder.isVisibleToSession(staffHealthSession, today))
        assertTrue(privateStaffNotice.isVisibleToSession(staffHealthSession, today))
        assertFalse(privateStaffDraft.isVisibleToSession(staffHealthSession, today))
        assertFalse(staffFinanceReminder.isVisibleToSession(staffHealthSession, today))
        assertFalse(draftNotice.isVisibleToSession(staffHealthSession, today))

        // Wrong Staff CANNOT see another staff's private notice OR unauthorized module notice
        assertFalse(privateStaffNotice.isVisibleToSession(staffFinanceSession, today))
        assertFalse(staffHealthReminder.isVisibleToSession(staffFinanceSession, today))
        assertTrue(staffFinanceReminder.isVisibleToSession(staffFinanceSession, today))

        // Deduplication key normalization prevents duplicate entries while differentiating targetUid
        val key1 = computeNotificationDedupKey("  नया जैविक खाद  ", "PRODUCTS", "public", "2026-11-22", "NOTICE", "")
        val key2 = computeNotificationDedupKey("नया जैविक खाद", "PRODUCTS", "PUBLIC", "2026-11-22", "NOTICE", "")
        val keyPrivateA = computeNotificationDedupKey("निजी कार्य", "REMINDERS", "STAFF", "2026-11-22", "REMINDER", "08:00", "staff-uid-1")
        val keyPrivateB = computeNotificationDedupKey("निजी कार्य", "REMINDERS", "STAFF", "2026-11-22", "REMINDER", "08:00", "staff-uid-2")
        assertEquals(key1, key2)
        assertFalse(keyPrivateA == keyPrivateB)

        // Serialization maps preserve createdAt on update & normalize targetModule to lowercase
        val createMap = staffHealthReminder.toCreateMap()
        val updateMap = staffHealthReminder.toUpdateMap()
        assertTrue(createMap.containsKey("createdAt"))
        assertTrue(createMap.containsKey("updatedAt"))
        assertFalse(updateMap.containsKey("createdAt"))
        assertTrue(updateMap.containsKey("updatedAt"))
        assertEquals(true, createMap["published"])
        assertEquals("REMINDER", createMap["itemKind"])
        assertEquals("HIGH", createMap["priority"])
        assertEquals("स्वास्थ्य टीम", createMap["assigneeName"])
        assertEquals("health", createMap["targetModule"])
    }

    @Test
    fun admin_canCreateReminder_togglePublish_and_deleteNotice() {
        var savedItem: NotificationItem? = null
        var savedIsEdit: Boolean? = null
        var toggledPublish: Pair<String, Boolean>? = null
        var deletedItem: NotificationItem? = null

        val existingNotice = NotificationItem(
            id = "admin-n1",
            targetRole = "PUBLIC",
            category = NotificationCategory.PRODUCTS.name,
            title = "गोबर के कंडे उपलब्ध",
            message = "हवन और पूजा हेतु शुद्ध गोबर के कंडे उपलब्ध हैं।",
            photoUrl = "preset:kande",
            published = true,
            publishDate = todayIsoDateString()
        )

        composeRule.setContent {
            GosalaTheme {
                NotificationsScreen(
                    language = AppLanguage.HINDI,
                    session = adminSession,
                    notificationsState = DataUiState.Success(listOf(existingNotice)),
                    staffAccounts = sampleStaffAccounts,
                    onMarkRead = {},
                    onSaveNotification = { item, isEdit ->
                        savedItem = item
                        savedIsEdit = isEdit
                    },
                    onSetPublished = { id, pub -> toggledPublish = id to pub },
                    onDeleteNotification = { item -> deletedItem = item }
                )
            }
        }

        // 1. Toggle Publish -> Unpublish
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_toggle_pub_admin-n1"))
        composeRule.onNodeWithTag("notif_toggle_pub_admin-n1").performClick()
        assertEquals("admin-n1" to false, toggledPublish)

        // 2. Delete notice via confirmation dialog
        composeRule.onNodeWithTag("notif_delete_admin-n1").performClick()
        composeRule.onNodeWithTag("notif_confirm_delete_btn").assertIsDisplayed().performClick()
        assertEquals("admin-n1", deletedItem?.id)

        // 3. Create new Reminder via Admin Editor Dialog
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("admin_create_notif_btn"))
        composeRule.onNodeWithTag("admin_create_notif_btn").performClick()

        composeRule.onNodeWithTag("notif_kind_reminder").performClick()
        composeRule.onNodeWithTag("notif_priority_HIGH").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_title_input").performScrollTo().performTextInput("सुबह गौ-सेवा एवं टीकाकरण रिमाइंडर")
        composeRule.onNodeWithTag("notif_message_input").performScrollTo().performTextInput("सभी गौ-भक्तों के लिए सुबह 8 बजे स्वास्थ्य शिविर आयोजित है।")
        composeRule.onNodeWithTag("notif_preset_compost").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_recurrence_DAILY").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_save_button").performClick()

        assertNotNull(savedItem)
        assertEquals(false, savedIsEdit)
        assertEquals("REMINDER", savedItem?.itemKind)
        assertEquals(ReminderRecurrence.DAILY.name, savedItem?.recurrence)
        assertEquals(NotificationPriority.HIGH.name, savedItem?.priority)
        assertEquals("preset:compost", savedItem?.photoUrl)
        assertEquals(true, savedItem?.published)
        assertEquals("सुबह गौ-सेवा एवं टीकाकरण रिमाइंडर", savedItem?.title)
    }

    @Test
    fun publicUser_seesPublishedNoticesWithNewTag_marksReadOnOpen_and_viewsHistory() {
        val today = todayIsoDateString()
        val unreadPub = NotificationItem(
            id = "pub-1",
            targetRole = "PUBLIC",
            category = NotificationCategory.PRODUCT_AVAILABILITY.name,
            title = "धूपबत्ती व गोकाष्ठ नया स्टॉक आया",
            message = "गौशाला में शुद्ध देसी गोबर धूपबत्ती और गोकाष्ठ का नया स्टॉक बिक्री के लिए उपलब्ध हो गया है।",
            photoUrl = "preset:dhoop",
            published = true,
            publishDate = today,
            readByUids = emptyList()
        )
        val draftHidden = NotificationItem(
            id = "draft-1",
            targetRole = "PUBLIC",
            category = NotificationCategory.ANNOUNCEMENTS.name,
            title = "अप्रकाशित ड्राफ्ट सूचना",
            message = "यह ड्राफ्ट सार्वजनिक यूजर को नहीं दिखना चाहिए।",
            published = false,
            publishDate = today
        )
        val expiredOld = NotificationItem(
            id = "old-1",
            targetRole = "ALL",
            category = NotificationCategory.ACTIVITIES.name,
            title = "पिछला गौ-स्वास्थ्य शिविर",
            message = "सफलतापूर्वक संपन्न हुआ।",
            published = true,
            publishDate = "2024-01-01",
            expiryDate = "2024-01-10",
            readByUids = listOf(publicSession.uid)
        )

        var items by mutableStateOf(listOf(unreadPub, draftHidden, expiredOld))
        var markedReadId: String? = null
        var markedUnreadId: String? = null

        composeRule.setContent {
            GosalaTheme {
                NotificationsScreen(
                    language = AppLanguage.HINDI,
                    session = publicSession,
                    notificationsState = DataUiState.Success(items),
                    onMarkRead = { id ->
                        markedReadId = id
                        items = items.map {
                            if (it.id == id) it.copy(readByUids = it.readByUids + publicSession.uid) else it
                        }
                    },
                    onMarkUnread = { id ->
                        markedUnreadId = id
                        items = items.map {
                            if (it.id == id) it.copy(readByUids = it.readByUids - publicSession.uid) else it
                        }
                    }
                )
            }
        }

        // Public must NOT see Admin create button or Draft notice
        assertEquals(0, composeRule.onAllNodesWithTag("admin_create_notif_btn").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("notif_card_draft-1").fetchSemanticsNodes().size)

        // Published unread notice displays with NEW badge
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_card_pub-1"))
        composeRule.onNodeWithTag("notif_new_badge_pub-1", useUnmergedTree = true).assertIsDisplayed()

        // Tapping notice opens full detail dialog and marks as Read
        composeRule.onNodeWithTag("notif_card_pub-1").performClick()
        composeRule.onNodeWithTag("notif_detail_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notif_detail_message").performScrollTo().assertIsDisplayed()
        assertEquals("pub-1", markedReadId)

        // Toggle back to Unread inside detail dialog
        composeRule.onNodeWithTag("notif_detail_toggle_read").performClick()
        assertEquals("pub-1", markedUnreadId)

        // Mark Read again and close dialog -> card shows Read badge and appears in History tab
        composeRule.onNodeWithTag("notif_detail_toggle_read").performClick()
        composeRule.onNodeWithTag("notif_detail_close_btn").performClick()
        composeRule.onNodeWithTag("notif_read_badge_pub-1", useUnmergedTree = true).assertIsDisplayed()

        // Switch to History tab -> expired and read notices are visible
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_tab_history"))
        composeRule.onNodeWithTag("notif_tab_history").performClick()
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_card_old-1"))
        composeRule.onNodeWithTag("notif_card_old-1").assertIsDisplayed()
    }

    @Test
    fun staff_seesScopedWorkReminders_and_alertReminderTogglesWork() {
        val today = todayIsoDateString()
        val healthReminder = NotificationItem(
            id = "rem-health",
            targetRole = "STAFF",
            targetModule = AppModule.HEALTH.key,
            category = NotificationCategory.REMINDERS.name,
            itemKind = "REMINDER",
            reminderTime = "09:00",
            recurrence = ReminderRecurrence.DAILY.name,
            title = "दैनिक टीकाकरण जाँच रिमाइंडर",
            message = "सभी नए गोवंश का स्वास्थ्य परीक्षण करें।",
            published = true,
            publishDate = today
        )
        val financeReminder = healthReminder.copy(
            id = "rem-finance",
            targetModule = AppModule.FINANCE.key,
            title = "वित्त खाता जाँच रिमाइंडर"
        )

        var alertsOn by mutableStateOf(true)
        var remindersOn by mutableStateOf(true)

        composeRule.setContent {
            GosalaTheme {
                NotificationsScreen(
                    language = AppLanguage.HINDI,
                    session = staffHealthSession,
                    notificationsState = DataUiState.Success(listOf(healthReminder, financeReminder)),
                    alertsEnabled = alertsOn,
                    remindersEnabled = remindersOn,
                    onToggleAlerts = { alertsOn = it },
                    onToggleReminders = { remindersOn = it },
                    onMarkRead = {}
                )
            }
        }

        // Staff with HEALTH permission sees healthReminder, NOT financeReminder
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_card_rem-health"))
        composeRule.onNodeWithTag("notif_card_rem-health").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTag("notif_card_rem-finance").fetchSemanticsNodes().size)

        // Toggle Reminders OFF -> preference callback fires and reminder hides from active tab
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("toggle_reminders_enabled"))
        composeRule.onNodeWithTag("toggle_reminders_enabled").performClick()
        assertFalse(remindersOn)

        composeRule.onNodeWithTag("toggle_alerts_enabled").performClick()
        assertFalse(alertsOn)
    }

    @Test
    fun admin_specificStaff_and_editWithoutDuplicate_and_staffActionDone_and_deepLinkRoute() {
        val today = todayIsoDateString()
        var currentSession by mutableStateOf(adminSession)
        var items by mutableStateOf(
            listOf(
                NotificationItem(
                    id = "edit-me-1",
                    targetRole = "STAFF",
                    targetModule = AppModule.HEALTH.key,
                    category = NotificationCategory.REMINDERS.name,
                    itemKind = "REMINDER",
                    reminderTime = "08:30",
                    recurrence = ReminderRecurrence.DAILY.name,
                    priority = NotificationPriority.URGENT.name,
                    assigneeName = "स्टाफ मोहन",
                    title = "शेड 1 स्वास्थ्य निरीक्षण",
                    message = "सभी गायों का तापमान दर्ज करें।",
                    published = true,
                    publishDate = today
                )
            )
        )
        var lastSaved: Pair<NotificationItem, Boolean>? = null
        var toggledAction: Pair<String, Boolean>? = null
        var navigatedRoute: String? = null
        var initialOpenId by mutableStateOf<String?>(null)

        composeRule.setContent {
            GosalaTheme {
                NotificationsScreen(
                    language = AppLanguage.HINDI,
                    session = currentSession,
                    notificationsState = DataUiState.Success(items),
                    staffAccounts = sampleStaffAccounts,
                    initialNotificationId = initialOpenId,
                    onConsumeInitialNotificationId = { initialOpenId = null },
                    onMarkRead = { id ->
                        items = items.map {
                            if (it.id == id) it.copy(readByUids = (it.readByUids + currentSession.uid).distinct()) else it
                        }
                    },
                    onToggleActionDone = { id, done ->
                        toggledAction = id to done
                        items = items.map {
                            if (it.id == id) {
                                val next = if (done) (it.completedByUids + currentSession.uid).distinct()
                                else it.completedByUids - currentSession.uid
                                it.copy(completedByUids = next)
                            } else it
                        }
                    },
                    onNavigateToRelatedRoute = { route -> navigatedRoute = route },
                    onSaveNotification = { item, isEdit ->
                        lastSaved = item to isEdit
                        items = if (isEdit) {
                            items.map { if (it.id == item.id) item else it }
                        } else {
                            items + item.copy(id = "priv-staff-1")
                        }
                    }
                )
            }
        }

        // 1. Admin edits existing notice -> updates in-place (isEdit == true, list size stays 1, NO duplicate)
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_edit_edit-me-1"))
        composeRule.onNodeWithTag("notif_edit_edit-me-1").performClick()
        composeRule.onNodeWithTag("notif_title_input").performScrollTo().performTextClearance()
        composeRule.onNodeWithTag("notif_title_input").performTextInput("शेड 1 स्वास्थ्य निरीक्षण (संशोधित)")
        composeRule.onNodeWithTag("notif_save_button").performClick()
        assertEquals(true, lastSaved?.second)
        assertEquals("edit-me-1", lastSaved?.first?.id)
        assertEquals(1, items.size)

        // 2. Admin creates Specific Staff private URGENT notice for staff-uid-1 (स्टाफ मोहन)
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("admin_create_notif_btn"))
        composeRule.onNodeWithTag("admin_create_notif_btn").performClick()
        composeRule.onNodeWithTag("notif_audience_SPECIFIC_STAFF").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_staff_pick_staff-uid-1").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_priority_URGENT").performScrollTo().performClick()
        composeRule.onNodeWithTag("notif_title_input").performScrollTo().performTextInput("मोहन का निजी दवा कार्य")
        composeRule.onNodeWithTag("notif_message_input").performScrollTo().performTextInput("गाय नं. 12 को शाम की खुराक दें।")
        composeRule.onNodeWithTag("notif_save_button").performClick()

        assertEquals(false, lastSaved?.second)
        assertEquals("STAFF", lastSaved?.first?.targetRole)
        assertEquals("staff-uid-1", lastSaved?.first?.targetUid)
        assertEquals("स्टाफ मोहन", lastSaved?.first?.assigneeName)
        assertEquals("URGENT", lastSaved?.first?.priority)
        assertEquals(2, items.size)

        // 3. Switch to Correct Staff (staff-uid-1): sees BOTH health module reminder & private notice, marks Action Done & deep-links to HEALTH
        currentSession = staffHealthSession
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notification_center_list")
            .performScrollToNode(hasTestTag("notif_card_priv-staff-1"))
        composeRule.onNodeWithTag("notif_priority_badge_priv-staff-1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("notif_action_toggle_priv-staff-1").performClick()
        assertEquals("priv-staff-1" to true, toggledAction)

        // Simulate tapping system reminder notification (initialNotificationId deep-link auto-opens detail dialog)
        initialOpenId = "edit-me-1"
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("notif_detail_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("notif_detail_open_related").performScrollTo().performClick()
        assertEquals(Routes.HEALTH, navigatedRoute)

        // 4. Switch to Wrong Staff (staff-uid-2 / Finance): MUST NOT see staff-uid-1's private notice OR health reminder
        currentSession = staffFinanceSession
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithTag("notif_card_priv-staff-1").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag("notif_card_edit-me-1").fetchSemanticsNodes().size)
    }

    @Test
    fun reminderEngine_timezoneRecurrence_alarmTrigger_antiDuplicate_bootPersistence_and_deepLinkIntent() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        NotificationReminderScheduler.cancelAllScheduledReminders(app)

        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        fun istEpoch(year: Int, month: Int, day: Int, hour: Int, min: Int): Long =
            Calendar.getInstance(ist).apply {
                set(year, month - 1, day, hour, min, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

        val nowIst = istEpoch(2026, 11, 22, 7, 30)

        // 1. ONCE (future vs past)
        val onceFuture = NotificationReminderScheduler.computeNextTriggerMillis(
            publishDate = "2026-11-22",
            expiryDate = "",
            reminderTime = "08:00",
            recurrence = ReminderRecurrence.ONCE,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertEquals(istEpoch(2026, 11, 22, 8, 0), onceFuture)

        val oncePast = NotificationReminderScheduler.computeNextTriggerMillis(
            publishDate = "2026-11-22",
            expiryDate = "",
            reminderTime = "07:00",
            recurrence = ReminderRecurrence.ONCE,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertNull(oncePast)

        // 2. DAILY (rolls over to next day when today's slot passed)
        val dailyNext = NotificationReminderScheduler.computeNextTriggerMillis(
            publishDate = "2026-11-20",
            expiryDate = "2026-11-30",
            reminderTime = "06:30",
            recurrence = ReminderRecurrence.DAILY,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertEquals(istEpoch(2026, 11, 23, 6, 30), dailyNext)

        // 3. WEEKLY (+7 days)
        val weeklyNext = NotificationReminderScheduler.computeNextTriggerMillis(
            publishDate = "2026-11-15",
            expiryDate = "",
            reminderTime = "07:00",
            recurrence = ReminderRecurrence.WEEKLY,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertEquals(istEpoch(2026, 11, 29, 7, 0), weeklyNext)

        // 4. MONTHLY month-end clamping (Jan 31 -> Feb 28 on non-leap year 2026)
        val monthlyClamped = NotificationReminderScheduler.computeNextTriggerMillis(
            publishDate = "2026-01-31",
            expiryDate = "",
            reminderTime = "09:00",
            recurrence = ReminderRecurrence.MONTHLY,
            nowMillis = istEpoch(2026, 2, 1, 8, 0),
            timeZone = ist
        )
        assertEquals(istEpoch(2026, 2, 28, 9, 0), monthlyClamped)

        // 5. Real AlarmManager scheduling + local persistence filtering (only authorized published reminder)
        val activeReminder = NotificationItem(
            id = "rem-e2e-1",
            targetRole = "STAFF",
            targetModule = AppModule.HEALTH.key,
            category = NotificationCategory.REMINDERS.name,
            itemKind = "REMINDER",
            reminderTime = "08:00",
            recurrence = ReminderRecurrence.DAILY.name,
            priority = NotificationPriority.URGENT.name,
            assigneeName = "स्टाफ मोहन",
            title = "सुबह 8 बजे टीकाकरण रिमाइंडर",
            message = "शेड 1 के गोवंश का टीकाकरण पूरा करें।",
            published = true,
            publishDate = "2026-11-22"
        )
        val unpublishedDraft = activeReminder.copy(id = "rem-draft", published = false)
        val unauthorizedFinance = activeReminder.copy(id = "rem-finance", targetModule = AppModule.FINANCE.key)

        val scheduledCount = NotificationReminderScheduler.syncRemindersForSession(
            context = app,
            session = staffHealthSession,
            notifications = listOf(activeReminder, unpublishedDraft, unauthorizedFinance),
            remindersEnabled = true,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertEquals(1, scheduledCount)
        val persisted = NotificationReminderScheduler.loadPersistedReminders(app)
        assertEquals(1, persisted.size)
        assertEquals("rem-e2e-1", persisted.first().id)

        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        // 6. Simulate App Closed + Alarm firing via ReminderAlarmReceiver -> real Android Notification + deep-link PendingIntent
        val triggerTs = istEpoch(2026, 11, 22, 8, 0)
        val alarmIntent = Intent(app, ReminderAlarmReceiver::class.java).apply {
            action = "com.example.ACTION_GOSALA_REMINDER"
            putExtra(NotificationReminderScheduler.EXTRA_NOTIFICATION_ID, "rem-e2e-1")
            putExtra(NotificationReminderScheduler.EXTRA_TRIGGER_MILLIS, triggerTs)
        }
        ReminderAlarmReceiver().onReceive(app, alarmIntent)

        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        assertEquals(1, shadowNm.allNotifications.size)
        val posted = shadowNm.allNotifications.first()
        assertTrue(posted.extras.getString("android.title").orEmpty().contains("सुबह 8 बजे टीकाकरण रिमाइंडर"))
        assertTrue(posted.extras.getString("android.text").orEmpty().contains("जिम्मेदार: स्टाफ मोहन"))

        // Verify clicking system notification opens MainActivity -> Routes.NOTIFICATIONS + exact notifId
        assertNotNull(posted.contentIntent)
        val launchIntent = Shadows.shadowOf(posted.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, launchIntent.component?.className)
        assertEquals(
            NotificationReminderScheduler.ROUTE_NOTIFICATIONS,
            launchIntent.getStringExtra(NotificationReminderScheduler.EXTRA_OPEN_ROUTE)
        )
        assertEquals(
            "rem-e2e-1",
            launchIntent.getStringExtra(NotificationReminderScheduler.EXTRA_NOTIFICATION_ID)
        )

        // 7. Anti-duplicate check: re-firing exact same occurrence is strictly suppressed!
        val duplicateFired = NotificationReminderScheduler.handleAlarmTriggered(
            context = app,
            notifId = "rem-e2e-1",
            triggerMillis = triggerTs,
            nowMillis = triggerTs,
            timeZone = ist
        )
        assertFalse(duplicateFired)
        assertEquals(1, shadowNm.allNotifications.size)

        // 8. Simulate Device Reboot & TimeZone Change via ReminderBootReceiver -> restores alarms cleanly
        ReminderBootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        ReminderBootReceiver().onReceive(app, Intent(Intent.ACTION_TIMEZONE_CHANGED))
        assertEquals(1, NotificationReminderScheduler.loadPersistedReminders(app).size)
        assertTrue(shadowAlarmManager.scheduledAlarms.isNotEmpty())

        // 9. Turning reminders OFF clears persisted reminders & cancels scheduled alarms
        val afterDisable = NotificationReminderScheduler.syncRemindersForSession(
            context = app,
            session = staffHealthSession,
            notifications = listOf(activeReminder),
            remindersEnabled = false,
            nowMillis = nowIst,
            timeZone = ist
        )
        assertEquals(0, afterDisable)
        assertTrue(NotificationReminderScheduler.loadPersistedReminders(app).isEmpty())
    }
}


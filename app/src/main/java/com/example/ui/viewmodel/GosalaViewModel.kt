package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AppLanguage
import com.example.core.QrAndExportUtils
import com.example.core.friendlyHindiError
import com.example.data.model.*
import com.example.data.repository.GosalaRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

class GosalaViewModel(
    private val repository: GosalaRepository,
    val session: UserSession
) : ViewModel() {

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private fun <T> Flow<List<T>>.toUiStateFlow(tag: String): StateFlow<DataUiState<List<T>>> =
        map<List<T>, DataUiState<List<T>>> { DataUiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing $tag", root)
                emit(DataUiState.Error(root.message ?: "Failed to load $tag"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(30_000L),
                initialValue = DataUiState.Loading
            )

    val publicSummary: StateFlow<PublicSummary> = repository.observePublicSummary()
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            Log.w(TAG, "Public summary stream notice", fsErr ?: error)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30_000L), PublicSummary())

    val cattleState: StateFlow<DataUiState<List<Cattle>>> =
        repository.observeCattle(session).toUiStateFlow("cattle")

    val healthState: StateFlow<DataUiState<List<HealthRecord>>> =
        repository.observeHealthRecords(session).toUiStateFlow("healthRecords")

    val feedLogsState: StateFlow<DataUiState<List<FeedAndOperationLog>>> =
        repository.observeFeedLogs(session).toUiStateFlow("feedLogs")

    val staffAccountsState: StateFlow<DataUiState<List<StaffAccount>>> =
        repository.observeStaffAccounts(session).toUiStateFlow("staffAccounts")

    val staffInvitesState: StateFlow<DataUiState<List<StaffInvite>>> =
        repository.observeStaffInvites(session).toUiStateFlow("staffInvites")

    val staffOpsState: StateFlow<DataUiState<List<StaffOperation>>> =
        repository.observeStaffOperations(session).toUiStateFlow("staffOperations")

    val financeState: StateFlow<DataUiState<List<FinancialTransaction>>> =
        repository.observeFinancialTx(session).toUiStateFlow("financialTransactions")

    val farmersState: StateFlow<DataUiState<List<FarmerRecord>>> =
        repository.observeFarmers(session).toUiStateFlow("farmers")

    val farmerTxState: StateFlow<DataUiState<List<FarmerTransaction>>> =
        repository.observeFarmerTx(session).toUiStateFlow("farmerTransactions")

    val compostState: StateFlow<DataUiState<List<CompostBatch>>> =
        repository.observeCompostBatches(session).toUiStateFlow("compostBatches")

    val donationsState: StateFlow<DataUiState<List<Donation>>> =
        repository.observeDonations(session).toUiStateFlow("donations")

    val sponsorshipsState: StateFlow<DataUiState<List<Sponsorship>>> =
        repository.observeSponsorships(session).toUiStateFlow("sponsorships")

    val productOrdersState: StateFlow<DataUiState<List<ProductOrder>>> =
        repository.observeProductOrders(session).toUiStateFlow("productOrders")

    val productsState: StateFlow<DataUiState<List<GoshalaProduct>>> =
        repository.observeProducts(session).toUiStateFlow("products")

    val notificationsState: StateFlow<DataUiState<List<NotificationItem>>> =
        repository.observeNotifications(session).toUiStateFlow("notifications")

    val auditLogsState: StateFlow<DataUiState<List<AuditLog>>> =
        repository.observeAuditLogs(session).toUiStateFlow("auditLogs")

    private fun runMutation(
        language: AppLanguage,
        successMsgHi: String,
        successMsgEn: String,
        block: suspend () -> Result<Any?>
    ) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val res = block()
                res.fold(
                    onSuccess = {
                        _toastMessage.tryEmit(if (language == AppLanguage.HINDI) successMsgHi else successMsgEn)
                        syncSummarySnapshotIfPermitted()
                    },
                    onFailure = { err ->
                        _toastMessage.tryEmit(friendlyHindiError(err, language))
                    }
                )
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private suspend fun syncSummarySnapshotIfPermitted() {
        val canSync = session.role == UserRole.ADMIN ||
            session.canEdit(AppModule.CATTLE) ||
            session.canAdd(AppModule.FEED) ||
            session.canAdd(AppModule.FINANCE) ||
            session.canEdit(AppModule.DONATIONS)
        if (!canSync) return

        val cattle = (cattleState.value as? DataUiState.Success)?.data.orEmpty()
        val feed = (feedLogsState.value as? DataUiState.Success)?.data.orEmpty()
        val fin = (financeState.value as? DataUiState.Success)?.data.orEmpty()
        val dons = (donationsState.value as? DataUiState.Success)?.data.orEmpty()

        val today = QrAndExportUtils.todayIso()
        val todayFeed = feed.filter { it.logDate == today }
            .sumOf { it.greenFodderKg + it.dryFodderBhusaKg + it.concentrateKg }
        val todayExp = fin.filter { it.txType == "EXPENSE" && it.dateStr == today }
            .sumOf { it.amountInr }
        val verifiedDon = dons.filter { it.paymentStatus == "VERIFIED_SUCCESS" }
            .sumOf { it.amountInr }

        repository.syncPublicSummary(
            totalCattle = cattle.size,
            healthyCattle = cattle.count { it.healthStatus == "HEALTHY" },
            sickCattle = cattle.count { it.healthStatus != "HEALTHY" },
            todayFeedKg = todayFeed,
            todayExpenseInr = todayExp,
            totalDonationsInr = verifiedDon
        )
    }

    fun saveCattle(cattle: Cattle, isEdit: Boolean, language: AppLanguage, appContext: Context? = null) {
        runMutation(language, "गौवंश रिकॉर्ड व फोटो सहेजी गई।", "Cattle record & photo saved.") {
            repository.saveCattle(session, cattle, isEdit, appContext)
        }
    }

    fun deleteCattle(cattle: Cattle, language: AppLanguage) {
        runMutation(language, "गौवंश रिकॉर्ड हटाया गया।", "Cattle record deleted.") {
            repository.deleteCattle(session, cattle)
        }
    }

    private val syncedPublicCardSignatures = mutableSetOf<String>()

    init {
        if (session.role == UserRole.ADMIN || session.canEdit(AppModule.CATTLE)) {
            viewModelScope.launch {
                combine(cattleState, healthState, feedLogsState) { cState, hState, fState ->
                    val cattle = (cState as? DataUiState.Success)?.data ?: return@combine null
                    val health = (hState as? DataUiState.Success)?.data.orEmpty()
                    val feeds = (fState as? DataUiState.Success)?.data.orEmpty()
                    Triple(cattle, health, feeds)
                }
                    .filterNotNull()
                    .collectLatest { (cattle, health, feeds) ->
                        val latestFeed = feeds.maxByOrNull { it.logDate }
                        val approvedToSync = cattle.filter { cow ->
                            if (!cow.publicApproved) return@filter false
                            val sig = "${cow.animalCode}|${cow.photoUri.orEmpty()}|${cow.weightKg}|${cow.healthStatus}|${cow.vaccinationSummary.orEmpty()}|${health.count { it.cattleId == cow.id || it.animalCode.equals(cow.animalCode, ignoreCase = true) }}|${latestFeed?.logDate.orEmpty()}"
                            syncedPublicCardSignatures.add(sig)
                        }
                        if (approvedToSync.isNotEmpty()) {
                            runCatching {
                                repository.syncAllApprovedPublicCattleCards(
                                    session = session,
                                    cattleList = approvedToSync,
                                    healthRecords = health,
                                    feedLogs = feeds
                                )
                            }
                        }
                    }
            }
        }
    }

    fun syncPublicCattleCardIfAuthorized(cattle: Cattle, lastHealthCheckup: String? = null) {
        if (!session.canEdit(AppModule.CATTLE) && !session.canAdd(AppModule.CATTLE)) return
        val health = (healthState.value as? DataUiState.Success)?.data
        val latestFeed = (feedLogsState.value as? DataUiState.Success)?.data?.maxByOrNull { it.logDate }
        viewModelScope.launch {
            runCatching {
                repository.syncPublicCattleCard(
                    session = session,
                    cattle = cattle,
                    lastHealthCheckup = lastHealthCheckup,
                    healthRecords = health,
                    latestFeedLog = latestFeed
                )
            }
        }
    }

    suspend fun findCattleByQr(rawQrOrId: String): Result<Cattle?> {
        val parsed = QrAndExportUtils.parseCattleQrPayload(rawQrOrId)
            ?: return Result.failure(IllegalArgumentException("INVALID_QR"))

        val remote = repository.findCattleByQrOrId(session, rawQrOrId)
        if (remote.isSuccess && remote.getOrNull() != null) {
            return remote
        }

        // Local live-synced cache fallback: strictly prioritize cowDocId -> animalCode -> qrPayload
        // so a wrong cow is never returned.
        val cached = (cattleState.value as? DataUiState.Success)?.data.orEmpty()
        val localMatch = cached.firstOrNull { cow ->
            !parsed.cowDocId.isNullOrBlank() && cow.id == parsed.cowDocId
        } ?: cached.firstOrNull { cow ->
            !parsed.animalCode.isNullOrBlank() && cow.animalCode.equals(parsed.animalCode, ignoreCase = true)
        } ?: cached.firstOrNull { cow ->
            cow.qrPayload.equals(parsed.rawValue, ignoreCase = true)
        }

        if (localMatch != null) {
            val enriched = if (com.example.core.CattlePhotoStorage.isCloudPhotoUri(localMatch.photoUri)) {
                localMatch
            } else {
                val resolved = com.example.core.CattlePhotoStorage.resolveCowPhotoUrl(
                    cattleId = localMatch.id,
                    photoUri = localMatch.photoUri,
                    photoStoragePath = localMatch.photoStoragePath
                )
                if (!resolved.isNullOrBlank()) localMatch.copy(photoUri = resolved) else localMatch
            }
            return Result.success(enriched)
        }
        return remote
    }

    fun addHealthRecord(record: HealthRecord, language: AppLanguage) {
        runMutation(language, "स्वास्थ्य रिकॉर्ड सहेजा गया।", "Health record saved.") {
            repository.addHealthRecord(session, record)
        }
    }

    fun deleteHealthRecord(id: String, language: AppLanguage) {
        runMutation(language, "स्वास्थ्य रिकॉर्ड हटाया गया।", "Health record deleted.") {
            repository.deleteHealthRecord(session, id)
        }
    }

    fun addFeedLog(log: FeedAndOperationLog, language: AppLanguage) {
        runMutation(language, "चारा व दैनिक कार्य दर्ज किया गया।", "Feed & operation log saved.") {
            repository.addFeedLog(session, log)
        }
    }

    fun toggleFeedTask(logId: String, completed: Boolean, language: AppLanguage) {
        runMutation(language, "कार्य स्थिति अपडेट हुई।", "Task status updated.") {
            repository.toggleFeedTaskCompleted(session, logId, completed)
        }
    }

    fun createStaffInvite(
        email: String,
        name: String,
        phone: String,
        designation: String,
        salary: Double,
        permissions: Map<String, List<String>>,
        language: AppLanguage,
        employeeId: String = "",
        active: Boolean = true
    ) {
        runMutation(language, "कर्मचारी पंजीकरण व अनुमतियां सहेजी गईं।", "Employee registration & permissions saved.") {
            repository.createStaffInvite(session, email, name, phone, designation, salary, permissions, employeeId, active)
        }
    }

    fun updateStaffInvite(
        email: String,
        employeeId: String,
        name: String,
        phone: String,
        designation: String,
        salary: Double,
        active: Boolean,
        permissions: Map<String, List<String>>,
        language: AppLanguage
    ) {
        runMutation(language, "कर्मचारी आमंत्रण अपडेट हुआ।", "Employee invite updated.") {
            repository.updateStaffInvite(session, email, employeeId, name, phone, designation, salary, active, permissions)
        }
    }

    fun updateStaffAccount(
        staffUid: String,
        active: Boolean,
        salary: Double,
        permissions: Map<String, List<String>>,
        language: AppLanguage,
        employeeId: String? = null,
        name: String? = null,
        phone: String? = null,
        designation: String? = null
    ) {
        runMutation(language, "कर्मचारी विवरण व अनुमतियां अपडेट हुईं।", "Employee details & permissions updated.") {
            repository.updateStaffAccount(session, staffUid, active, salary, permissions, employeeId, name, phone, designation)
        }
    }

    fun deleteStaffAccount(staffUid: String, language: AppLanguage) {
        runMutation(language, "कर्मचारी खाता हटाया गया।", "Staff account deleted.") {
            repository.deleteStaffAccount(session, staffUid)
        }
    }

    fun deleteStaffInvite(email: String, language: AppLanguage) {
        runMutation(language, "कर्मचारी आमंत्रण हटाया गया।", "Staff invite deleted.") {
            repository.deleteStaffInvite(session, email)
        }
    }

    fun addStaffOperation(entry: StaffOperation, language: AppLanguage) {
        runMutation(language, "कर्मचारी प्रविष्टि सहेजी गई।", "Staff operation saved.") {
            repository.addStaffOperation(session, entry)
        }
    }

    fun addFinancialTx(tx: FinancialTransaction, language: AppLanguage) {
        runMutation(language, "वित्तीय प्रविष्टि सहेजी गई।", "Financial transaction saved.") {
            repository.addFinancialTx(session, tx)
        }
    }

    fun updateFinancialTx(tx: FinancialTransaction, language: AppLanguage) {
        runMutation(language, "वित्तीय प्रविष्टि अपडेट हुई।", "Financial transaction updated.") {
            repository.updateFinancialTx(session, tx)
        }
    }

    fun deleteFinancialTx(txId: String, language: AppLanguage) {
        runMutation(language, "वित्तीय प्रविष्टि हटाई गई।", "Transaction deleted.") {
            repository.deleteFinancialTx(session, txId)
        }
    }

    fun addFarmer(farmer: FarmerRecord, language: AppLanguage) {
        runMutation(language, "किसान रिकॉर्ड सहेजा गया।", "Farmer record saved.") {
            repository.addFarmer(session, farmer)
        }
    }

    fun updateFarmer(farmer: FarmerRecord, language: AppLanguage) {
        runMutation(language, "किसान रिकॉर्ड अपडेट हुआ।", "Farmer record updated.") {
            repository.updateFarmer(session, farmer)
        }
    }

    fun deleteFarmer(farmer: FarmerRecord, language: AppLanguage) {
        runMutation(language, "किसान रिकॉर्ड हटाया गया।", "Farmer record deleted.") {
            repository.deleteFarmer(session, farmer)
        }
    }

    fun addFarmerTx(tx: FarmerTransaction, farmer: FarmerRecord, language: AppLanguage) {
        runMutation(language, "किसान खरीद/बिक्री बिल सहेजा गया।", "Farmer invoice saved.") {
            repository.addFarmerTransaction(session, tx, farmer)
        }
    }

    fun updateFarmerTx(tx: FarmerTransaction, language: AppLanguage) {
        runMutation(language, "किसान लेनदेन अपडेट हुआ।", "Farmer invoice updated.") {
            repository.updateFarmerTransaction(session, tx)
        }
    }

    fun deleteFarmerTx(txId: String, language: AppLanguage) {
        runMutation(language, "किसान लेनदेन हटाया गया।", "Farmer invoice deleted.") {
            repository.deleteFarmerTransaction(session, txId)
        }
    }

    fun addCompostBatch(batch: CompostBatch, language: AppLanguage) {
        runMutation(language, "उत्पादन बैच सहेजा गया।", "Production batch saved.") {
            repository.addCompostBatch(session, batch)
        }
    }

    fun updateCompostBatch(batch: CompostBatch, language: AppLanguage) {
        runMutation(language, "उत्पादन बैच अपडेट हुआ।", "Production batch updated.") {
            repository.updateCompostBatch(session, batch)
        }
    }

    fun deleteCompostBatch(batch: CompostBatch, language: AppLanguage) {
        runMutation(language, "उत्पादन बैच हटाया गया।", "Production batch deleted.") {
            repository.deleteCompostBatch(session, batch)
        }
    }

    fun recordCompostSale(batch: CompostBatch, sellQty: Double, language: AppLanguage) {
        runMutation(language, "उत्पाद बिक्री दर्ज की गई।", "Product sale recorded.") {
            repository.recordCompostSale(session, batch, sellQty)
        }
    }

    fun submitDonation(donation: Donation, language: AppLanguage) {
        runMutation(
            language,
            "दान विवरण दर्ज हुआ। सत्यापन के बाद रसीद स्वीकृत होगी।",
            "Donation recorded. Receipt awaits verification."
        ) {
            repository.submitDonation(session, donation)
        }
    }

    fun verifyDonation(donationId: String, status: String, language: AppLanguage) {
        runMutation(language, "दान स्थिति अपडेट हुई।", "Donation status updated.") {
            repository.verifyDonation(session, donationId, status)
        }
    }

    fun updateDonation(donation: Donation, language: AppLanguage) {
        runMutation(language, "दान विवरण अपडेट हुआ।", "Donation updated.") {
            repository.updateDonation(session, donation)
        }
    }

    fun deleteDonation(donationId: String, language: AppLanguage) {
        runMutation(language, "दान प्रविष्टि हटाई गई।", "Donation deleted.") {
            repository.deleteDonation(session, donationId)
        }
    }

    fun submitSponsorship(sponsorship: Sponsorship, language: AppLanguage) {
        runMutation(
            language,
            "गौ-गोद आवेदन सफलतापूर्वक जमा हुआ।",
            "Cow sponsorship application submitted."
        ) {
            repository.submitSponsorship(session, sponsorship)
        }
    }

    fun updateSponsorship(sponsorship: Sponsorship, language: AppLanguage) {
        runMutation(language, "गौ-गोद विवरण अपडेट हुआ।", "Sponsorship updated.") {
            repository.updateSponsorship(session, sponsorship)
        }
    }

    fun reviewSponsorship(sponsorship: Sponsorship, approve: Boolean, language: AppLanguage) {
        runMutation(
            language,
            if (approve) "आवेदन स्वीकृत हुआ और प्रमाणपत्र जारी किया गया।" else "आवेदन अस्वीकृत किया गया।",
            if (approve) "Application approved and certificate issued." else "Application rejected."
        ) {
            repository.reviewSponsorship(session, sponsorship, approve)
        }
    }

    fun verifySponsorshipPayment(sponsorship: Sponsorship, paymentStatus: String, language: AppLanguage) {
        runMutation(
            language,
            "गौ-गोद भुगतान स्थिति अपडेट हुई।",
            "Cow adoption payment status updated."
        ) {
            repository.verifySponsorshipPayment(session, sponsorship, paymentStatus)
        }
    }

    fun deleteSponsorship(sponsorshipId: String, language: AppLanguage) {
        runMutation(language, "गौ-गोद आवेदन हटाया गया।", "Sponsorship deleted.") {
            repository.deleteSponsorship(session, sponsorshipId)
        }
    }

    fun submitProductOrder(order: ProductOrder, language: AppLanguage) {
        runMutation(
            language,
            "उत्पाद खरीद आवेदन सफलतापूर्वक जमा हुआ।",
            "Product purchase application submitted."
        ) {
            repository.submitProductOrder(session, order)
        }
    }

    fun updateProductOrderStatus(
        order: ProductOrder,
        newOrderStatus: String,
        newPaymentStatus: String,
        adminNote: String,
        language: AppLanguage
    ) {
        runMutation(
            language,
            "ऑर्डर स्थिति अपडेट हुई।",
            "Product order status updated."
        ) {
            repository.updateProductOrderStatus(session, order, newOrderStatus, newPaymentStatus, adminNote)
        }
    }

    fun deleteProductOrder(orderId: String, language: AppLanguage) {
        runMutation(language, "उत्पाद ऑर्डर हटाया गया।", "Product order deleted.") {
            repository.deleteProductOrder(session, orderId)
        }
    }

    fun saveProduct(
        product: GoshalaProduct,
        isEdit: Boolean,
        language: AppLanguage,
        appContext: Context? = null
    ) {
        runMutation(
            language,
            if (isEdit) "उत्पाद विवरण अपडेट हुआ।" else "नया उत्पाद सफलतापूर्वक जोड़ा गया।",
            if (isEdit) "Product updated." else "New product added."
        ) {
            repository.saveProduct(session, product, isEdit, appContext)
        }
    }

    fun deleteProduct(product: GoshalaProduct, language: AppLanguage) {
        runMutation(language, "उत्पाद हटाया गया।", "Product deleted.") {
            repository.deleteProduct(session, product)
        }
    }

    fun seedDefaultProducts(language: AppLanguage) {
        runMutation(language, "डिफ़ॉल्ट 6 गौशाला उत्पाद लोड किए गए।", "Default 6 Goshala products loaded.") {
            repository.seedDefaultProducts(session)
        }
    }

    fun saveNotification(
        item: NotificationItem,
        isEdit: Boolean,
        language: AppLanguage,
        context: Context? = null
    ) {
        val isReminder = item.isReminderItem()
        val msgHi = when {
            isEdit && isReminder -> "रिमाइंडर अपडेट किया गया।"
            isEdit -> "सूचना अपडेट की गई।"
            !item.published -> "ड्राफ्ट के रूप में सुरक्षित किया गया।"
            isReminder -> "नया रिमाइंडर प्रकाशित किया गया।"
            else -> "नई सूचना प्रकाशित की गई।"
        }
        val msgEn = when {
            isEdit && isReminder -> "Reminder updated."
            isEdit -> "Notification updated."
            !item.published -> "Saved as draft."
            isReminder -> "Reminder published."
            else -> "Notification published."
        }
        runMutation(language, msgHi, msgEn) {
            repository.saveNotification(session, item, isEdit, context)
        }
    }

    fun setNotificationPublished(notifId: String, published: Boolean, language: AppLanguage) {
        runMutation(
            language,
            if (published) "सूचना प्रकाशित कर दी गई।" else "सूचना अप्रकाशित (ड्राफ्ट) की गई।",
            if (published) "Notification published." else "Notification unpublished."
        ) {
            repository.setNotificationPublished(session, notifId, published)
        }
    }

    fun deleteNotification(item: NotificationItem, language: AppLanguage) {
        runMutation(language, "सूचना हटाई गई।", "Notification deleted.") {
            repository.deleteNotification(session, item.id, item.title)
        }
    }

    fun markNotificationRead(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationRead(notifId)
        }
    }

    fun markNotificationUnread(notifId: String) {
        viewModelScope.launch {
            repository.markNotificationUnread(notifId)
        }
    }

    fun toggleNotificationActionDone(notifId: String, done: Boolean, language: AppLanguage) {
        runMutation(
            language,
            if (done) "कार्य पूर्ण चिह्नित किया गया।" else "कार्य लंबित चिह्नित किया गया।",
            if (done) "Action marked as done." else "Action marked as pending."
        ) {
            repository.toggleNotificationActionDone(notifId, done)
        }
    }

    fun markAllNotificationsRead(notifIds: List<String>, language: AppLanguage) {
        if (notifIds.isEmpty()) return
        runMutation(language, "सभी सूचनाएँ पढ़ी गईं चिह्नित हुईं।", "All notifications marked as read.") {
            repository.markAllNotificationsRead(notifIds)
        }
    }

    private companion object {
        const val TAG = "GosalaVM"
    }
}

package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.core.OperationType
import com.example.core.QrAndExportUtils
import com.example.core.handleFirestoreError
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class GosalaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required before accessing Firestore.")

    // --- PUBLIC SUMMARY ---
    fun observePublicSummary(): Flow<PublicSummary> =
        db.collection("goshalaInfo").document("public_summary")
            .snapshots()
            .map { snap ->
                snap.toObject(PublicSummary::class.java, ServerTimestampBehavior.ESTIMATE) ?: PublicSummary()
            }
            .flowOn(Dispatchers.Default)

    suspend fun syncPublicSummary(
        totalCattle: Int,
        healthyCattle: Int,
        sickCattle: Int,
        todayFeedKg: Double,
        todayExpenseInr: Double,
        totalDonationsInr: Double
    ): Result<Unit> = safeWrite("goshalaInfo/public_summary", OperationType.WRITE) {
        val payload = mapOf(
            "totalCattle" to totalCattle.toLong(),
            "healthyCattle" to healthyCattle.toLong(),
            "sickCattle" to sickCattle.toLong(),
            "todayFeedKg" to todayFeedKg,
            "todayExpenseInr" to todayExpenseInr,
            "totalDonationsInr" to totalDonationsInr,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("goshalaInfo").document("public_summary").set(payload).await()
    }

    // --- CATTLE ---
    fun observeCattle(session: UserSession, limitCount: Long = 100L): Flow<List<Cattle>> {
        val query: Query = if (session.role == UserRole.ADMIN || session.hasPermission(AppModule.CATTLE, CrudAction.VIEW)) {
            db.collection("cattle").limit(limitCount)
        } else {
            db.collection("cattle").whereEqualTo("publicApproved", true).limit(limitCount)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Cattle::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.animalCode }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveCattle(session: UserSession, cattle: Cattle, isEdit: Boolean): Result<String> {
        val uid = requireUid()
        val allowed = if (isEdit) session.canEdit(AppModule.CATTLE) else session.canAdd(AppModule.CATTLE)
        if (!allowed) {
            return Result.failure(SecurityException("इस खाते को गौवंश ${if (isEdit) "संपादित करने" else "जोड़ने"} की अनुमति नहीं है।"))
        }
        val docRef = if (isEdit && cattle.id.isNotBlank()) {
            db.collection("cattle").document(cattle.id)
        } else {
            db.collection("cattle").document()
        }
        val code = cattle.animalCode.ifBlank {
            val prefix = when (cattle.category) {
                "BULL" -> "B"
                "CALF" -> "C"
                else -> "G"
            }
            "JBGS-$prefix-${(100..999).random()}"
        }.take(40)
        val qr = cattle.qrPayload.ifBlank { "JBGS:CATTLE:${docRef.id}:$code" }.take(300)

        return safeWrite(docRef.path, if (isEdit) OperationType.UPDATE else OperationType.CREATE) {
            if (isEdit) {
                val updates = mapOf(
                    "name" to cattle.name.trim().take(80),
                    "category" to cattle.category,
                    "breed" to cattle.breed.trim().take(80),
                    "sex" to cattle.sex,
                    "ageText" to cattle.ageText.trim().take(40),
                    "birthDate" to cattle.birthDate.trim().take(30),
                    "colour" to cattle.colour.trim().take(40),
                    "weightKg" to cattle.weightKg.coerceIn(0.0, 2000.0),
                    "healthStatus" to cattle.healthStatus,
                    "entryDate" to cattle.entryDate.trim().take(30),
                    "shedNumber" to cattle.shedNumber.trim().take(30),
                    "identificationMarks" to cattle.identificationMarks.trim().take(200),
                    "photoUri" to cattle.photoUri?.take(500),
                    "notes" to cattle.notes?.take(500),
                    "publicApproved" to cattle.publicApproved,
                    "sponsorshipAvailable" to cattle.sponsorshipAvailable,
                    "monthlySupportInr" to cattle.monthlySupportInr.coerceIn(0L, 500000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                docRef.update(updates).await()
            } else {
                val payload = cattle.copy(
                    animalCode = code,
                    name = cattle.name.trim().take(80),
                    breed = cattle.breed.trim().take(80),
                    ageText = cattle.ageText.trim().take(40),
                    birthDate = cattle.birthDate.trim().take(30),
                    colour = cattle.colour.trim().take(40),
                    weightKg = cattle.weightKg.coerceIn(0.0, 2000.0),
                    entryDate = cattle.entryDate.trim().take(30),
                    shedNumber = cattle.shedNumber.trim().take(30),
                    identificationMarks = cattle.identificationMarks.trim().take(200),
                    qrPayload = qr,
                    photoUri = cattle.photoUri?.take(500),
                    notes = cattle.notes?.take(500),
                    monthlySupportInr = cattle.monthlySupportInr.coerceIn(0L, 500000L),
                    createdBy = uid
                ).toCreateMap()
                docRef.set(payload).await()
            }
            if (cattle.healthStatus == "SICK" || cattle.healthStatus == "UNDER_TREATMENT") {
                createNotificationInternal(
                    targetRole = "STAFF",
                    category = "HEALTH",
                    title = "पशु स्वास्थ्य अलर्ट: ${cattle.name}",
                    message = "पशु ID $code (${cattle.shedNumber}) की स्थिति ${cattle.healthStatus} दर्ज की गई है।"
                )
            }
            logAudit(session, if (isEdit) "UPDATE" else "CREATE", "cattle", docRef.id, "${cattle.name} ($code)")
            docRef.id
        }
    }

    suspend fun deleteCattle(session: UserSession, cattle: Cattle): Result<Unit> {
        if (!session.canDelete(AppModule.CATTLE)) {
            return Result.failure(SecurityException("गौवंश रिकॉर्ड हटाने की अनुमति नहीं है।"))
        }
        return safeWrite("cattle/${cattle.id}", OperationType.DELETE) {
            require(cattle.id.isNotBlank())
            db.collection("cattle").document(cattle.id).delete().await()
            logAudit(session, "DELETE", "cattle", cattle.id, "Removed ${cattle.name} (${cattle.animalCode})")
        }
    }

    // --- HEALTH RECORDS ---
    fun observeHealthRecords(session: UserSession): Flow<List<HealthRecord>> {
        if (!session.canView(AppModule.HEALTH) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("healthRecords").limit(100).snapshots().map { snap ->
            snap.toObjects(HealthRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addHealthRecord(session: UserSession, record: HealthRecord): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("healthRecords").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(record.copy(recordedBy = uid).toCreateMap()).await()
            if (record.reminderActive && record.nextFollowUpDate.isNotBlank()) {
                createNotificationInternal(
                    targetRole = "STAFF",
                    category = if (record.recordType == "VACCINATION") "VACCINATION" else "FOLLOW_UP",
                    title = "${record.cattleName}: ${record.title}",
                    message = "आगामी फॉलो-अप / टीकाकरण तिथि: ${record.nextFollowUpDate}"
                )
            }
            logAudit(session, "CREATE", "health", docRef.id, "${record.recordType}: ${record.cattleName}")
        }
    }

    suspend fun deleteHealthRecord(session: UserSession, recordId: String): Result<Unit> =
        safeWrite("healthRecords/$recordId", OperationType.DELETE) {
            db.collection("healthRecords").document(recordId).delete().await()
            logAudit(session, "DELETE", "health", recordId, "Deleted health record")
        }

    // --- FEED & DAILY OPERATIONS ---
    fun observeFeedLogs(session: UserSession): Flow<List<FeedAndOperationLog>> {
        if (!session.canView(AppModule.FEED) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("feedAndOperations").limit(100).snapshots().map { snap ->
            snap.toObjects(FeedAndOperationLog::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.logDate }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addFeedLog(session: UserSession, log: FeedAndOperationLog): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("feedAndOperations").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(log.copy(recordedBy = uid).toCreateMap()).await()
            if (log.feedStockRemainingKg in 0.0..500.0) {
                createNotificationInternal(
                    targetRole = "ADMIN",
                    category = "FEED_STOCK",
                    title = "चारा स्टॉक चेतावनी (कम स्टॉक)",
                    message = "वर्तमान चारा स्टॉक केवल ${log.feedStockRemainingKg.toInt()} किग्रा शेष है। कृपया किसान से खरीद की व्यवस्था करें।"
                )
            }
            if (log.taskTitle.isNotBlank() && !log.taskCompleted) {
                createNotificationInternal(
                    targetRole = "STAFF",
                    category = "STAFF_TASK",
                    title = "दैनिक कार्य: ${log.taskTitle}",
                    message = "जिम्मेदार: ${log.assignedStaffName.ifBlank { "स्टाफ" }} (${log.logDate})"
                )
            }
            logAudit(session, "CREATE", "feed", docRef.id, "Feed log ${log.logDate}")
        }
    }

    suspend fun toggleFeedTaskCompleted(session: UserSession, logId: String, completed: Boolean): Result<Unit> =
        safeWrite("feedAndOperations/$logId", OperationType.UPDATE) {
            db.collection("feedAndOperations").document(logId).update(
                mapOf("taskCompleted" to completed, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAudit(session, "UPDATE", "feed", logId, "Task completed=$completed")
        }

    // --- STAFF MANAGEMENT (ADMIN & AUTHORIZED STAFF) ---
    fun observeStaffAccounts(session: UserSession): Flow<List<StaffAccount>> {
        if (session.role != UserRole.ADMIN) return flowOf(emptyList())
        return db.collection("staffAccounts").limit(100).snapshots().map { snap ->
            snap.toObjects(StaffAccount::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name }
        }.flowOn(Dispatchers.Default)
    }

    fun observeStaffInvites(session: UserSession): Flow<List<StaffInvite>> {
        if (session.role != UserRole.ADMIN) return flowOf(emptyList())
        return db.collection("staffInvites").limit(100).snapshots().map { snap ->
            snap.toObjects(StaffInvite::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name }
        }.flowOn(Dispatchers.Default)
    }

    fun observeStaffOperations(session: UserSession): Flow<List<StaffOperation>> {
        val query = when {
            session.role == UserRole.ADMIN || session.hasPermission(AppModule.STAFF, CrudAction.VIEW) ->
                db.collection("staffOperations").limit(100)
            session.role == UserRole.STAFF ->
                db.collection("staffOperations").whereEqualTo("staffUid", session.uid).limit(100)
            else -> return flowOf(emptyList())
        }
        return query.snapshots().map { snap ->
            snap.toObjects(StaffOperation::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.dateStr }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun createStaffInvite(
        session: UserSession,
        email: String,
        name: String,
        phone: String,
        designation: String,
        monthlySalary: Double,
        permissions: Map<String, List<String>>
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ आमंत्रित कर सकते हैं।"))
        }
        val uid = requireUid()
        val normalizedEmail = email.trim().lowercase()
        return safeWrite("staffInvites/$normalizedEmail", OperationType.WRITE) {
            require(normalizedEmail.contains("@") && normalizedEmail.contains(".")) { "Valid email required" }
            val payload = mapOf(
                "email" to normalizedEmail,
                "name" to name.trim().take(100),
                "phone" to phone.trim().take(20),
                "designation" to designation.trim().ifBlank { "गौ सेवक" }.take(80),
                "monthlySalary" to monthlySalary.coerceIn(0.0, 1000000.0),
                "permissions" to permissions,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("staffInvites").document(normalizedEmail).set(payload).await()
            logAudit(session, "CREATE", "staff", normalizedEmail, "Invited staff $name ($normalizedEmail)")
        }
    }

    suspend fun updateStaffAccount(
        session: UserSession,
        staffUid: String,
        active: Boolean,
        monthlySalary: Double,
        permissions: Map<String, List<String>>
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ अनुमतियाँ बदल सकते हैं।"))
        }
        return safeWrite("staffAccounts/$staffUid", OperationType.UPDATE) {
            db.collection("staffAccounts").document(staffUid).update(
                mapOf(
                    "active" to active,
                    "monthlySalary" to monthlySalary.coerceIn(0.0, 1000000.0),
                    "permissions" to permissions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "staff", staffUid, "Updated permissions/active=$active")
        }
    }

    suspend fun addStaffOperation(session: UserSession, entry: StaffOperation): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("staffOperations").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(entry.copy(recordedBy = uid).toCreateMap()).await()
            logAudit(session, "CREATE", "staff", docRef.id, "${entry.entryType} for ${entry.staffName}")
        }
    }

    // --- FINANCIAL MANAGEMENT ---
    fun observeFinancialTx(session: UserSession): Flow<List<FinancialTransaction>> {
        if (!session.canView(AppModule.FINANCE) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("financialTransactions").limit(150).snapshots().map { snap ->
            snap.toObjects(FinancialTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.dateStr }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addFinancialTx(session: UserSession, tx: FinancialTransaction): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("financialTransactions").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(tx.copy(recordedBy = uid).toCreateMap()).await()
            if (tx.paymentStatus == "PENDING") {
                createNotificationInternal(
                    targetRole = "ADMIN",
                    category = "PENDING_PAYMENT",
                    title = "बकाया भुगतान अलर्ट: ₹${tx.amountInr.toInt()}",
                    message = "${tx.partyName} — ${tx.description}"
                )
            }
            logAudit(session, "CREATE", "finance", docRef.id, "${tx.txType} ₹${tx.amountInr}")
        }
    }

    suspend fun deleteFinancialTx(session: UserSession, txId: String): Result<Unit> =
        safeWrite("financialTransactions/$txId", OperationType.DELETE) {
            db.collection("financialTransactions").document(txId).delete().await()
            logAudit(session, "DELETE", "finance", txId, "Deleted transaction")
        }

    // --- FARMER COOPERATION (NO FARMER LOGIN ACCOUNTS) ---
    fun observeFarmers(session: UserSession): Flow<List<FarmerRecord>> {
        if (!session.canView(AppModule.FARMERS) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("farmers").limit(100).snapshots().map { snap ->
            snap.toObjects(FarmerRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name }
        }.flowOn(Dispatchers.Default)
    }

    fun observeFarmerTx(session: UserSession): Flow<List<FarmerTransaction>> {
        if (!session.canView(AppModule.FARMERS) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("farmerTransactions").limit(150).snapshots().map { snap ->
            snap.toObjects(FarmerTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.dateStr }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addFarmer(session: UserSession, farmer: FarmerRecord): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("farmers").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(farmer.copy(createdBy = uid).toCreateMap()).await()
            logAudit(session, "CREATE", "farmers", docRef.id, "Added farmer ${farmer.name}")
        }
    }

    suspend fun addFarmerTransaction(session: UserSession, tx: FarmerTransaction, currentFarmer: FarmerRecord): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("farmerTransactions").document()
        val invoice = tx.invoiceNumber.ifBlank { "FG-${System.currentTimeMillis().toString().takeLast(5)}" }
        return safeWrite(docRef.path, OperationType.CREATE) {
            val outstanding = (tx.totalAmountInr - tx.paidAmountInr).coerceAtLeast(0.0)
            docRef.set(
                tx.copy(
                    invoiceNumber = invoice,
                    outstandingInr = outstanding,
                    recordedBy = uid
                ).toCreateMap()
            ).await()

            val newPurchased = if (tx.direction == "PURCHASE_FROM_FARMER") {
                currentFarmer.totalPurchasedInr + tx.totalAmountInr
            } else currentFarmer.totalPurchasedInr
            val newSold = if (tx.direction == "SALE_TO_FARMER") {
                currentFarmer.totalSoldInr + tx.totalAmountInr
            } else currentFarmer.totalSoldInr

            db.collection("farmers").document(currentFarmer.id).update(
                mapOf(
                    "totalPurchasedInr" to newPurchased,
                    "totalSoldInr" to newSold,
                    "outstandingInr" to (currentFarmer.outstandingInr + outstanding),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (outstanding > 0.0) {
                createNotificationInternal(
                    targetRole = "ADMIN",
                    category = "PENDING_PAYMENT",
                    title = "किसान बकाया: ${tx.farmerName}",
                    message = "बिल $invoice में ₹${outstanding.toInt()} बकाया है।"
                )
            }
            logAudit(session, "CREATE", "farmers", docRef.id, "${tx.direction} ${tx.productName} ₹${tx.totalAmountInr}")
        }
    }

    // --- GOBAR & COMPOST PRODUCTION ---
    fun observeCompostBatches(session: UserSession): Flow<List<CompostBatch>> {
        if (!session.canView(AppModule.PRODUCTION) || session.role == UserRole.PUBLIC) return flowOf(emptyList())
        return db.collection("compostProduction").limit(100).snapshots().map { snap ->
            snap.toObjects(CompostBatch::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.dateStr }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun addCompostBatch(session: UserSession, batch: CompostBatch): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("compostProduction").document()
        val code = batch.batchCode.ifBlank { "CMP-${System.currentTimeMillis().toString().takeLast(4)}" }
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(batch.copy(batchCode = code, recordedBy = uid).toCreateMap()).await()
            if (batch.stockRemaining < 20.0 && batch.status == "READY_FOR_SALE") {
                createNotificationInternal(
                    targetRole = "ADMIN",
                    category = "LOW_INVENTORY",
                    title = "कम उत्पाद स्टॉक: ${batch.productType}",
                    message = "बैच $code का स्टॉक केवल ${batch.stockRemaining} ${batch.unit} बचा है।"
                )
            }
            logAudit(session, "CREATE", "production", docRef.id, "$code (${batch.productType})")
        }
    }

    suspend fun recordCompostSale(session: UserSession, batch: CompostBatch, sellQty: Double): Result<Unit> =
        safeWrite("compostProduction/${batch.id}", OperationType.UPDATE) {
            require(sellQty > 0 && sellQty <= batch.stockRemaining) { "अमान्य बिक्री मात्रा / Invalid sale quantity" }
            val newSold = batch.soldQuantity + sellQty
            val newStock = (batch.stockRemaining - sellQty).coerceAtLeast(0.0)
            val newStatus = if (newStock <= 0.0) "SOLD_OUT" else batch.status
            db.collection("compostProduction").document(batch.id).update(
                mapOf(
                    "soldQuantity" to newSold,
                    "stockRemaining" to newStock,
                    "status" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "production", batch.id, "Sold $sellQty ${batch.unit} from ${batch.batchCode}")
        }

    // --- DONATIONS & COW SPONSORSHIP ---
    fun observeDonations(session: UserSession): Flow<List<Donation>> {
        val query = if (session.role == UserRole.ADMIN || session.hasPermission(AppModule.DONATIONS, CrudAction.VIEW)) {
            db.collection("donations").limit(150)
        } else {
            db.collection("donations").whereEqualTo("donorUid", session.uid).limit(100)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Donation::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun submitDonation(session: UserSession, donation: Donation): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("donations").document()
        val receipt = "JBGS-RCPT-${System.currentTimeMillis().toString().takeLast(6)}"
        // CRITICAL SECURITY & PAYMENT INTEGRITY: Never mark an unverified payment as successful!
        val safeStatus = "PENDING_VERIFICATION"
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(
                donation.copy(
                    donorUid = uid,
                    paymentProviderRef = donation.paymentProviderRef.ifBlank { "UPI_OR_CASH_PENDING" },
                    paymentStatus = safeStatus,
                    receiptNumber = receipt,
                    dateStr = QrAndExportUtils.todayIso(),
                    recordedBy = uid
                ).toCreateMap()
            ).await()
            createNotificationInternal(
                targetRole = "ADMIN",
                category = "DONATION",
                title = "नया दान सत्यापन हेतु: ₹${donation.amountInr.toInt()}",
                message = "दाता: ${donation.donorName} | रसीद: $receipt"
            )
            logAudit(session, "CREATE", "donations", docRef.id, "Donation ₹${donation.amountInr} ($safeStatus)")
        }
    }

    suspend fun verifyDonation(session: UserSession, donationId: String, newStatus: String): Result<Unit> =
        safeWrite("donations/$donationId", OperationType.UPDATE) {
            db.collection("donations").document(donationId).update(
                mapOf(
                    "paymentStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "donations", donationId, "Donation status -> $newStatus")
        }

    fun observeSponsorships(session: UserSession): Flow<List<Sponsorship>> {
        val query = if (session.role == UserRole.ADMIN || session.hasPermission(AppModule.DONATIONS, CrudAction.VIEW)) {
            db.collection("sponsorships").limit(100)
        } else {
            db.collection("sponsorships").whereEqualTo("applicantUid", session.uid).limit(100)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Sponsorship::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun submitSponsorship(session: UserSession, sponsorship: Sponsorship): Result<Unit> {
        val uid = requireUid()
        val docRef = db.collection("sponsorships").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            require(sponsorship.disclaimerAccepted) { "गौ-गोद अस्वीकरण सहमति आवश्यक है / Disclaimer required" }
            docRef.set(
                sponsorship.copy(
                    applicantUid = uid,
                    status = "PENDING",
                    totalSupportInr = sponsorship.monthlySupportInr * sponsorship.durationMonths,
                    dateStr = QrAndExportUtils.todayIso(),
                    certificateNumber = "",
                    reviewedBy = ""
                ).toCreateMap()
            ).await()
            createNotificationInternal(
                targetRole = "ADMIN",
                category = "ADOPTION",
                title = "नया गौ-गोद (प्रायोजन) आवेदन",
                message = "${sponsorship.applicantName} ने ${sponsorship.cattleName} (${sponsorship.animalCode}) के लिए आवेदन किया।"
            )
            logAudit(session, "CREATE", "sponsorships", docRef.id, "Applied for ${sponsorship.cattleName}")
        }
    }

    suspend fun reviewSponsorship(session: UserSession, sponsorship: Sponsorship, approve: Boolean): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) गौ-गोद आवेदन स्वीकृत या अस्वीकृत कर सकते हैं।"))
        }
        return safeWrite("sponsorships/${sponsorship.id}", OperationType.UPDATE) {
            val uid = requireUid()
            val newStatus = if (approve) "APPROVED" else "REJECTED"
            val cert = if (approve) "JBGS-GAU-${System.currentTimeMillis().toString().takeLast(5)}" else ""
            db.collection("sponsorships").document(sponsorship.id).update(
                mapOf(
                    "status" to newStatus,
                    "certificateNumber" to cert,
                    "reviewedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createNotificationInternal(
                targetRole = "PUBLIC",
                targetUid = sponsorship.applicantUid,
                category = "ADOPTION",
                title = if (approve) "गौ-गोद आवेदन स्वीकृत!" else "गौ-गोद आवेदन अद्यतन",
                message = "${sponsorship.cattleName} के लिए आपका प्रायोजन आवेदन ${if (approve) "स्वीकृत (प्रमाणपत्र: $cert)" else "अस्वीकृत"} हुआ।"
            )
            logAudit(session, if (approve) "APPROVE" else "REJECT", "sponsorships", sponsorship.id, "Sponsorship $newStatus")
        }
    }

    // --- NOTIFICATIONS & AUDIT LOGS ---
    fun observeNotifications(session: UserSession): Flow<List<NotificationItem>> {
        val query = if (session.role == UserRole.ADMIN || session.hasPermission(AppModule.REPORTS, CrudAction.VIEW)) {
            db.collection("notifications").limit(50)
        } else {
            db.collection("notifications").whereEqualTo("targetUid", session.uid).limit(50)
        }
        return query.snapshots().map { snap ->
            snap.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> =
        safeWrite("notifications/$notifId", OperationType.UPDATE) {
            val uid = requireUid()
            db.collection("notifications").document(notifId)
                .update("readByUids", FieldValue.arrayUnion(uid)).await()
        }

    fun observeAuditLogs(session: UserSession): Flow<List<AuditLog>> {
        if (session.role != UserRole.ADMIN) return flowOf(emptyList())
        return db.collection("auditLogs").limit(80).snapshots().map { snap ->
            snap.toObjects(AuditLog::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun createNotificationInternal(
        targetRole: String,
        targetUid: String = "",
        category: String,
        title: String,
        message: String
    ) {
        try {
            val ref = db.collection("notifications").document()
            ref.set(
                NotificationItem(
                    targetRole = targetRole,
                    targetUid = targetUid,
                    category = category,
                    title = title.take(120),
                    message = message.take(500)
                ).toCreateMap()
            ).await()
        } catch (_: Exception) {
            // Non-blocking notification helper
        }
    }

    private suspend fun logAudit(
        session: UserSession,
        action: String,
        module: String,
        entityId: String,
        summary: String
    ) {
        try {
            val ref = db.collection("auditLogs").document()
            ref.set(
                mapOf(
                    "actorUid" to session.uid,
                    "actorName" to session.displayName.take(100),
                    "actorRole" to session.role.name,
                    "action" to action,
                    "module" to module,
                    "entityId" to entityId,
                    "summary" to summary.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Best-effort audit trail
        }
    }

    private suspend inline fun <T> safeWrite(
        path: String,
        operationType: OperationType,
        crossinline block: suspend () -> T
    ): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            Result.failure(e)
        }
    }
}

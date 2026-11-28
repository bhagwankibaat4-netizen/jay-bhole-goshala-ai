package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.R
import com.example.core.CattlePhotoStorage
import com.example.core.OfflineQueuedException
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
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream

class GosalaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private companion object {
        const val TAG = "GosalaRepo"
    }
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
        val query: Query = when {
            session.role == UserRole.ADMIN || session.hasPermission(AppModule.CATTLE, CrudAction.VIEW) ->
                db.collection("cattle").limit(limitCount)
            session.role == UserRole.PUBLIC ->
                db.collection("cattle").whereEqualTo("publicApproved", true).limit(limitCount)
            else -> return flowOf(emptyList())
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Cattle::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.animalCode }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveCattle(
        session: UserSession,
        cattle: Cattle,
        isEdit: Boolean,
        appContext: Context? = null
    ): Result<String> {
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
        val requestedCode = cattle.animalCode.trim()
        val code: String = if (requestedCode.isNotBlank()) {
            if (!isEdit) {
                val duplicateExists = try {
                    withTimeout(6_000L) {
                        db.collection("cattle")
                            .whereEqualTo("animalCode", requestedCode)
                            .limit(1)
                            .get()
                            .await()
                            .documents
                            .isNotEmpty()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    false
                }
                if (duplicateExists) {
                    return Result.failure(
                        IllegalStateException("इस Cow ID ($requestedCode) से गाय का रिकॉर्ड पहले से मौजूद है। डुप्लीकेट रिकॉर्ड से बचने के लिए अलग ID उपयोग करें।")
                    )
                }
            }
            requestedCode.take(40)
        } else {
            val prefix = when (cattle.category) {
                "BULL" -> "B"
                "CALF" -> "C"
                else -> "G"
            }
            var generated = "JBGS-$prefix-${(100..999).random()}"
            if (!isEdit) {
                for (attempt in 0 until 5) {
                    val exists = try {
                        withTimeout(4_000L) {
                            db.collection("cattle")
                                .whereEqualTo("animalCode", generated)
                                .limit(1)
                                .get()
                                .await()
                                .documents
                                .isNotEmpty()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    if (!exists) break
                    generated = "JBGS-$prefix-${(1000..9999).random()}"
                }
            }
            generated.take(40)
        }
        val normalizedCode = QrAndExportUtils.normalizeAnimalCode(code) ?: code
        val existingQr = cattle.qrPayload.trim()
        val hasValidExistingQr = QrAndExportUtils.isValidPublicCattleQrUrl(existingQr, normalizedCode)
        val qr = when {
            hasValidExistingQr -> existingQr
            normalizedCode.isNotBlank() -> QrAndExportUtils.buildCattleQrPayload(docRef.id, normalizedCode)
            QrAndExportUtils.isValidPublicCattleQrUrl(existingQr) -> existingQr
            else -> QrAndExportUtils.buildCattleQrPayload(docRef.id, code)
        }.take(300)

        // Ensure cow photo is ALWAYS uploaded to permanent cloud storage — NEVER save an ephemeral local URI.
        val rawPhoto = cattle.photoUri?.trim()?.takeIf { it.isNotBlank() }
        var resolvedPhotoUrl: String? = null
        var resolvedStoragePath: String? = cattle.photoStoragePath?.trim()?.takeIf { it.isNotBlank() }

        if (rawPhoto != null) {
            if (CattlePhotoStorage.isLocalDeviceUri(rawPhoto)) {
                val uploadRes = CattlePhotoStorage.uploadCattlePhoto(
                    context = appContext,
                    db = db,
                    uid = uid,
                    cattleId = docRef.id,
                    sourceUriString = rawPhoto,
                    publicApproved = cattle.publicApproved
                )
                val uploaded = uploadRes.getOrElse { err ->
                    return Result.failure(err)
                }
                resolvedPhotoUrl = uploaded.photoUrl.take(2000)
                resolvedStoragePath = uploaded.storagePath.takeIf { it.isNotBlank() }?.take(300)
            } else if (CattlePhotoStorage.isCloudPhotoUri(rawPhoto)) {
                resolvedPhotoUrl = rawPhoto.take(2000)
                if (isEdit && rawPhoto.startsWith(CattlePhotoStorage.FIRESTORE_PHOTO_SCHEME, ignoreCase = true)) {
                    CattlePhotoStorage.syncPhotoVisibility(db, docRef.id, cattle.publicApproved)
                }
            } else if (CattlePhotoStorage.isStoragePathUri(rawPhoto)) {
                resolvedStoragePath = rawPhoto.take(300)
                resolvedPhotoUrl = CattlePhotoStorage.resolveCowPhotoUrl(docRef.id, rawPhoto, rawPhoto)?.take(2000)
            }
        }

        return safeWrite(docRef.path, if (isEdit) OperationType.UPDATE else OperationType.CREATE) {
            if (isEdit) {
                val updates = mutableMapOf<String, Any>(
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
                    "publicApproved" to cattle.publicApproved,
                    "sponsorshipAvailable" to cattle.sponsorshipAvailable,
                    "monthlySupportInr" to cattle.monthlySupportInr.coerceIn(0L, 500000L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!hasValidExistingQr) {
                    updates["qrPayload"] = qr
                }
                if (resolvedPhotoUrl != null) {
                    updates["photoUri"] = resolvedPhotoUrl
                }
                if (resolvedStoragePath != null) {
                    updates["photoStoragePath"] = resolvedStoragePath
                }
                cattle.vaccinationSummary?.trim()?.takeIf { it.isNotBlank() }?.let {
                    updates["vaccinationSummary"] = it.take(500)
                }
                cattle.notes?.trim()?.takeIf { it.isNotBlank() }?.let {
                    updates["notes"] = it.take(500)
                }
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
                    photoUri = resolvedPhotoUrl,
                    photoStoragePath = resolvedStoragePath,
                    vaccinationSummary = cattle.vaccinationSummary?.trim()?.takeIf { it.isNotBlank() }?.take(500),
                    notes = cattle.notes?.trim()?.takeIf { it.isNotBlank() }?.take(500),
                    monthlySupportInr = cattle.monthlySupportInr.coerceIn(0L, 500000L),
                    createdBy = uid
                ).toCreateMap()
                docRef.set(payload).await()
            }
            val savedCattleForSync = cattle.copy(
                id = docRef.id,
                animalCode = code,
                qrPayload = qr,
                photoUri = resolvedPhotoUrl ?: cattle.photoUri,
                photoStoragePath = resolvedStoragePath ?: cattle.photoStoragePath
            )
            syncPublicCattleCardInternal(savedCattleForSync, lastHealthCheckup = null)
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
            CattlePhotoStorage.deleteCattlePhoto(db, cattle.id, cattle.photoStoragePath)
            deletePublicCattleCardInternal(cattle.animalCode)
            logAudit(session, "DELETE", "cattle", cattle.id, "Removed ${cattle.name} (${cattle.animalCode})")
        }
    }

    /**
     * Best-effort synchronization of an Admin-approved cow's public-safe Cattle Profile
     * into `/publicCattleCards/{animalCode}` so Google Lens / browser QR scans immediately display
     * the latest photo, details, health/vaccination records, and fodder summary without login.
     * Never writes private internal `notes`, `costInr`, or unapproved cows.
     */
    suspend fun syncPublicCattleCard(
        session: UserSession,
        cattle: Cattle,
        lastHealthCheckup: String? = null,
        healthRecords: List<HealthRecord>? = null,
        latestFeedLog: FeedAndOperationLog? = null
    ) {
        if (!session.canEdit(AppModule.CATTLE) && !session.canAdd(AppModule.CATTLE)) return
        syncPublicCattleCardInternal(
            cattle = cattle,
            lastHealthCheckup = lastHealthCheckup,
            prefetchedHealthRecords = healthRecords,
            prefetchedLatestFeedLog = latestFeedLog
        )
    }

    /**
     * Background batch sync for all pre-existing Admin-approved cattle (such as JBGS-G-357)
     * so every approved cow's public QR web profile stays populated with live photo, health, and feed data.
     */
    suspend fun syncAllApprovedPublicCattleCards(
        session: UserSession,
        cattleList: List<Cattle>,
        healthRecords: List<HealthRecord> = emptyList(),
        feedLogs: List<FeedAndOperationLog> = emptyList()
    ) {
        if (session.role != UserRole.ADMIN && !session.canEdit(AppModule.CATTLE)) return
        val latestFeed = feedLogs.maxByOrNull { it.logDate }
        for (cow in cattleList) {
            val normalizedCode = QrAndExportUtils.normalizeAnimalCode(cow.animalCode)
            // Upgrade ONLY missing/legacy qrPayload records to the direct HTTPS URL;
            // never regenerate or overwrite an already-valid QR URL.
            if (cow.id.isNotBlank() && normalizedCode != null &&
                !QrAndExportUtils.isValidPublicCattleQrUrl(cow.qrPayload, normalizedCode)
            ) {
                val canonicalUrl = QrAndExportUtils.buildPublicCattleQrUrl(normalizedCode)
                runCatching {
                    db.collection("cattle").document(cow.id).update(
                        mapOf(
                            "qrPayload" to canonicalUrl,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            if (!cow.publicApproved) continue
            syncPublicCattleCardInternal(
                cattle = cow,
                lastHealthCheckup = null,
                prefetchedHealthRecords = healthRecords,
                prefetchedLatestFeedLog = latestFeed
            )
        }
    }

    private suspend fun syncPublicCattleCardInternal(
        cattle: Cattle,
        lastHealthCheckup: String?,
        prefetchedHealthRecords: List<HealthRecord>? = null,
        prefetchedLatestFeedLog: FeedAndOperationLog? = null
    ) {
        val normalizedCode = QrAndExportUtils.normalizeAnimalCode(cattle.animalCode) ?: return
        val cardDocRef = db.collection("publicCattleCards").document(normalizedCode)
        if (!cattle.publicApproved) {
            deletePublicCattleCardInternal(normalizedCode)
            return
        }
        try {
            val enriched = enrichCattlePhoto(cattle)
            val resolvedPhoto = enriched.photoUri?.trim()
            var httpsPhotoUrl: String? = resolvedPhoto?.takeIf {
                it.startsWith("https://", ignoreCase = true)
            }
            // If photoUri was not HTTPS yet (e.g., storage path or folder upload), try Firebase Storage directly
            if (httpsPhotoUrl.isNullOrBlank() && cattle.id.isNotBlank()) {
                val storageResolved = CattlePhotoStorage.resolveCowPhotoUrl(
                    cattleId = cattle.id,
                    photoUri = null,
                    photoStoragePath = cattle.photoStoragePath
                )
                if (storageResolved?.startsWith("https://", ignoreCase = true) == true) {
                    httpsPhotoUrl = storageResolved
                }
            }
            var photoDataUrl: String? = null

            // If cow photo is stored in Firestore fallback (`cattlePhotos/{cattleId}`), embed compact
            // JPEG data URL so public Google Lens viewers see the real cow photo even if Storage URL is absent.
            if (httpsPhotoUrl.isNullOrBlank() && cattle.id.isNotBlank()) {
                val fallbackSnap = runCatching {
                    withTimeout(6_000L) {
                        db.collection("cattlePhotos").document(cattle.id).get().await()
                    }
                }.getOrNull()
                if (fallbackSnap != null && fallbackSnap.exists()) {
                    val b64 = fallbackSnap.getString("imageBase64")?.trim().orEmpty()
                    val mime = fallbackSnap.getString("contentType")?.trim().orEmpty().ifBlank { "image/jpeg" }
                    if (b64.length in 16..240_000) {
                        photoDataUrl = "data:$mime;base64,$b64"
                    }
                }
            }

            // Fetch cow's real health records if not supplied
            val healthList: List<HealthRecord> = prefetchedHealthRecords ?: run {
                val byCode = runCatching {
                    withTimeout(6_000L) {
                        db.collection("healthRecords")
                            .whereEqualTo("animalCode", normalizedCode)
                            .limit(15)
                            .get()
                            .await()
                            .toObjects(HealthRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    }
                }.getOrDefault(emptyList())
                val byId = if (cattle.id.isNotBlank()) {
                    runCatching {
                        withTimeout(6_000L) {
                            db.collection("healthRecords")
                                .whereEqualTo("cattleId", cattle.id)
                                .limit(15)
                                .get()
                                .await()
                                .toObjects(HealthRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                        }
                    }.getOrDefault(emptyList())
                } else emptyList()
                (byCode + byId).distinctBy { it.id }
            }

            // Fetch latest Goshala feed log if not supplied
            val latestFeed: FeedAndOperationLog? = prefetchedLatestFeedLog ?: runCatching {
                withTimeout(5_000L) {
                    db.collection("feedAndOperations")
                        .limit(20)
                        .get()
                        .await()
                        .toObjects(FeedAndOperationLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .maxByOrNull { it.logDate }
                }
            }.getOrNull()

            val cardMap = QrAndExportUtils.buildPublicCattleCardMap(
                cattle = enriched.copy(animalCode = normalizedCode),
                resolvedPhotoUrl = httpsPhotoUrl,
                photoDataUrl = photoDataUrl,
                lastHealthCheckup = lastHealthCheckup,
                healthRecords = healthList,
                latestFeedLog = latestFeed,
                useServerTimestamp = true
            ) ?: return

            withTimeout(8_000L) {
                cardDocRef.set(cardMap).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal publicCattleCards sync warning for $normalizedCode: ${e.message}")
        }
    }

    private suspend fun deletePublicCattleCardInternal(animalCode: String) {
        val normalizedCode = QrAndExportUtils.normalizeAnimalCode(animalCode) ?: return
        try {
            withTimeout(6_000L) {
                db.collection("publicCattleCards").document(normalizedCode).delete().await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal publicCattleCards cleanup notice for $normalizedCode: ${e.message}")
        }
    }

    /**
     * Looks up a single cow record directly from Firebase Firestore using a scanned QR payload
     * or Cow Unique ID / animalCode. Respects role access (`publicApproved == true` for PUBLIC),
     * resolves cloud photo URLs from `photoStoragePath` / `cattle_photos/<cattleId>/...` if needed,
     * and guarantees exact matching so a wrong cow is never opened.
     */
    suspend fun findCattleByQrOrId(session: UserSession, rawQrOrId: String): Result<Cattle?> {
        if (!session.canView(AppModule.CATTLE)) {
            return Result.failure(SecurityException("गौवंश रिकॉर्ड देखने की अनुमति नहीं है।"))
        }
        val parsed = QrAndExportUtils.parseCattleQrPayload(rawQrOrId)
            ?: return Result.failure(IllegalArgumentException("INVALID_QR"))

        return try {
            val isPublic = session.role == UserRole.PUBLIC

            // 1. Strict direct lookup by Firebase document Unique ID
            val docId = parsed.cowDocId
            if (!docId.isNullOrBlank()) {
                val docMatch = try {
                    val snap = withTimeout(10_000L) {
                        db.collection("cattle").document(docId).get().await()
                    }
                    if (snap.exists()) {
                        snap.toObject(Cattle::class.java, ServerTimestampBehavior.ESTIMATE)
                            ?.takeIf { !isPublic || it.publicApproved }
                    } else null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Direct cattle doc lookup fallback for id=$docId", e)
                    null
                }
                if (docMatch != null) {
                    return Result.success(enrichCattlePhoto(docMatch))
                }
            }

            // 2. Exact lookup by human-readable animalCode (e.g. JBGS-G-101, JBG-1234, COW-101)
            val code = parsed.animalCode
            if (!code.isNullOrBlank()) {
                val candidates = listOf(code.trim(), code.trim().uppercase()).distinct()
                for (candidate in candidates) {
                    val codeMatch = querySingleCattleByField(
                        field = "animalCode",
                        value = candidate,
                        isPublic = isPublic
                    )
                    if (codeMatch != null) {
                        return Result.success(enrichCattlePhoto(codeMatch))
                    }
                }
            }

            // 3. Fallback lookup by stored qrPayload (supports legacy custom payloads saved in Firestore)
            val payloadMatch = querySingleCattleByField(
                field = "qrPayload",
                value = parsed.rawValue,
                isPublic = isPublic
            )
            if (payloadMatch != null) {
                return Result.success(enrichCattlePhoto(payloadMatch))
            }

            // 4. Case-insensitive fallback scan over approved/visible cattle if composite index was absent
            val fallbackList = try {
                val baseQuery = if (isPublic) {
                    db.collection("cattle").whereEqualTo("publicApproved", true).limit(200)
                } else {
                    db.collection("cattle").limit(200)
                }
                withTimeout(10_000L) { baseQuery.get().await() }
                    .toObjects(Cattle::class.java, ServerTimestampBehavior.ESTIMATE)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }

            val exactFallback = fallbackList.firstOrNull { cow ->
                !parsed.cowDocId.isNullOrBlank() && cow.id == parsed.cowDocId
            } ?: fallbackList.firstOrNull { cow ->
                !parsed.animalCode.isNullOrBlank() && cow.animalCode.equals(parsed.animalCode, ignoreCase = true)
            } ?: fallbackList.firstOrNull { cow ->
                cow.qrPayload.equals(parsed.rawValue, ignoreCase = true)
            }

            Result.success(exactFallback?.let { enrichCattlePhoto(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun querySingleCattleByField(
        field: String,
        value: String,
        isPublic: Boolean
    ): Cattle? {
        return try {
            val query = if (isPublic) {
                db.collection("cattle")
                    .whereEqualTo("publicApproved", true)
                    .whereEqualTo(field, value)
                    .limit(1)
            } else {
                db.collection("cattle")
                    .whereEqualTo(field, value)
                    .limit(1)
            }
            val snap = withTimeout(10_000L) { query.get().await() }
            snap.documents.firstOrNull()
                ?.toObject(Cattle::class.java, ServerTimestampBehavior.ESTIMATE)
                ?.takeIf { !isPublic || it.publicApproved }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firestore query failed for $field=$value (isPublic=$isPublic): ${e.message}")
            null
        }
    }

    private suspend fun enrichCattlePhoto(cow: Cattle): Cattle {
        if (CattlePhotoStorage.isCloudPhotoUri(cow.photoUri)) return cow
        val resolved = CattlePhotoStorage.resolveCowPhotoUrl(
            cattleId = cow.id,
            photoUri = cow.photoUri,
            photoStoragePath = cow.photoStoragePath
        )
        return when {
            !resolved.isNullOrBlank() -> cow.copy(photoUri = resolved)
            CattlePhotoStorage.isLocalDeviceUri(cow.photoUri) -> cow.copy(photoUri = null)
            else -> cow
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
        if (!session.canAdd(AppModule.HEALTH)) {
            return Result.failure(SecurityException("स्वास्थ्य रिकॉर्ड जोड़ने की अनुमति नहीं है।"))
        }
        val uid = requireUid()
        val docRef = db.collection("healthRecords").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(record.copy(recordedBy = uid).toCreateMap()).await()
            if (record.recordType.equals("VACCINATION", ignoreCase = true) && record.cattleId.isNotBlank()) {
                val summary = buildString {
                    append(record.title.trim())
                    if (record.nextFollowUpDate.isNotBlank()) {
                        append(" (आगामी: ${record.nextFollowUpDate.trim()})")
                    }
                }.take(500)
                runCatching {
                    db.collection("cattle").document(record.cattleId).update(
                        mapOf(
                            "vaccinationSummary" to summary,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            val normalizedCode = QrAndExportUtils.normalizeAnimalCode(record.animalCode)
            if (normalizedCode != null) {
                val checkupText = buildString {
                    append(record.title.trim().ifBlank { record.recordType })
                    append(" • ")
                    append(QrAndExportUtils.todayIso())
                }.take(120)
                val updatedHistory = runCatching {
                    val byCode = withTimeout(5_000L) {
                        db.collection("healthRecords")
                            .whereEqualTo("animalCode", normalizedCode)
                            .limit(15)
                            .get()
                            .await()
                            .toObjects(HealthRecord::class.java, ServerTimestampBehavior.ESTIMATE)
                    }
                    val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    byCode.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(15).map { rec ->
                        val entry = mutableMapOf<String, Any>(
                            "recordType" to rec.recordType.trim().uppercase(java.util.Locale.US),
                            "title" to rec.title.trim().take(120)
                        )
                        rec.medicine.trim().takeIf { it.isNotBlank() }?.let { entry["medicine"] = it.take(160) }
                        rec.vetName.trim().takeIf { it.isNotBlank() }?.let { entry["vetName"] = it.take(100) }
                        if (rec.weightKg > 0.0) entry["weightKg"] = rec.weightKg.coerceIn(0.0, 2000.0)
                        rec.nextFollowUpDate.trim().takeIf { it.isNotBlank() }?.let { entry["nextFollowUpDate"] = it.take(30) }
                        rec.createdAt?.toDate()?.let { entry["dateStr"] = dateFmt.format(it) }
                        entry
                    }
                }.getOrNull()
                val publicHealthUpdates = mutableMapOf<String, Any>(
                    "lastHealthCheckup" to checkupText,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (!updatedHistory.isNullOrEmpty()) {
                    publicHealthUpdates["healthHistory"] = updatedHistory
                }
                if (record.recordType.equals("VACCINATION", ignoreCase = true)) {
                    val summary = buildString {
                        append(record.title.trim())
                        if (record.nextFollowUpDate.isNotBlank()) {
                            append(" (आगामी: ${record.nextFollowUpDate.trim()})")
                        }
                    }.take(500)
                    publicHealthUpdates["vaccinationSummary"] = summary
                }
                runCatching {
                    db.collection("publicCattleCards").document(normalizedCode).update(publicHealthUpdates).await()
                }
            }
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

    suspend fun deleteHealthRecord(session: UserSession, recordId: String): Result<Unit> {
        if (!session.canDelete(AppModule.HEALTH)) {
            return Result.failure(SecurityException("स्वास्थ्य रिकॉर्ड हटाने की अनुमति नहीं है।"))
        }
        return safeWrite("healthRecords/$recordId", OperationType.DELETE) {
            db.collection("healthRecords").document(recordId).delete().await()
            logAudit(session, "DELETE", "health", recordId, "Deleted health record")
        }
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
        if (!session.canAdd(AppModule.FEED)) {
            return Result.failure(SecurityException("चारा रिकॉर्ड जोड़ने की अनुमति नहीं है।"))
        }
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

    suspend fun toggleFeedTaskCompleted(session: UserSession, logId: String, completed: Boolean): Result<Unit> {
        if (!session.canEdit(AppModule.FEED)) {
            return Result.failure(SecurityException("दैनिक कार्य संपादित करने की अनुमति नहीं है।"))
        }
        return safeWrite("feedAndOperations/$logId", OperationType.UPDATE) {
            db.collection("feedAndOperations").document(logId).update(
                mapOf("taskCompleted" to completed, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAudit(session, "UPDATE", "feed", logId, "Task completed=$completed")
        }
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

    /**
     * Best-effort synchronization so that when an Admin registers or updates an employee by email,
     * any already-registered user (/users/{uid}) or existing /staffAccounts doc with that email
     * immediately gets a synced /staffAccounts/{uid} record matching their Firebase Auth UID.
     */
    private suspend fun syncExistingUserToStaffAccount(
        adminUid: String,
        normalizedEmail: String,
        employeeId: String,
        name: String,
        phone: String,
        designation: String,
        monthlySalary: Double,
        active: Boolean,
        permissions: Map<String, List<String>>
    ) {
        val syncedUids = mutableSetOf<String>()
        // 1. Update any existing /staffAccounts docs matching this email
        try {
            val existingStaffDocs = db.collection("staffAccounts")
                .whereEqualTo("email", normalizedEmail)
                .limit(5)
                .get()
                .await()
            for (doc in existingStaffDocs.documents) {
                if (doc.id == adminUid) continue
                syncedUids.add(doc.id)
                db.collection("staffAccounts").document(doc.id).update(
                    mapOf(
                        "employeeId" to employeeId,
                        "name" to name,
                        "phone" to phone,
                        "designation" to designation,
                        "monthlySalary" to monthlySalary,
                        "active" to active,
                        "permissions" to permissions,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal staffAccounts sync notice for $normalizedEmail: ${e.message}")
        }

        // 2. If the employee already signed up earlier (/users/{uid} exists), create /staffAccounts/{uid} immediately
        try {
            val matchingUsers = db.collection("users")
                .whereEqualTo("email", normalizedEmail)
                .limit(5)
                .get()
                .await()
            for (userDoc in matchingUsers.documents) {
                val targetUid = userDoc.id
                if (targetUid == adminUid || userDoc.getString("role") == "ADMIN") continue
                if (!syncedUids.contains(targetUid)) {
                    val staffDocRef = db.collection("staffAccounts").document(targetUid)
                    val existingSnap = staffDocRef.get().await()
                    if (existingSnap.exists()) {
                        staffDocRef.update(
                            mapOf(
                                "employeeId" to employeeId,
                                "name" to name,
                                "phone" to phone,
                                "designation" to designation,
                                "monthlySalary" to monthlySalary,
                                "active" to active,
                                "permissions" to permissions,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    } else {
                        staffDocRef.set(
                            mapOf(
                                "uid" to targetUid,
                                "employeeId" to employeeId,
                                "email" to normalizedEmail,
                                "name" to name,
                                "phone" to phone,
                                "designation" to designation,
                                "monthlySalary" to monthlySalary,
                                "active" to active,
                                "permissions" to permissions,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                // Keep /users/{uid}.role synced (never downgrade Admin)
                try {
                    db.collection("users").document(targetUid).update(
                        mapOf(
                            "role" to if (active) "STAFF" else "PUBLIC",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal /users role sync notice for $targetUid: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Non-fatal /users lookup during staff sync for $normalizedEmail: ${e.message}")
        }
    }

    suspend fun createStaffInvite(
        session: UserSession,
        email: String,
        name: String,
        phone: String,
        designation: String,
        monthlySalary: Double,
        permissions: Map<String, List<String>>,
        employeeId: String = "",
        active: Boolean = true
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ आमंत्रित कर सकते हैं।"))
        }
        val uid = requireUid()
        val normalizedEmail = email.trim().lowercase()
        val cleanEmpId = employeeId.trim().ifBlank { "EMP-${(100..999).random()}" }.take(40)
        val cleanName = name.trim().take(100)
        val cleanPhone = phone.trim().take(20)
        val cleanDesignation = designation.trim().ifBlank { "गौ सेवक" }.take(80)
        val cleanSalary = monthlySalary.coerceIn(0.0, 1000000.0)
        return safeWrite("staffInvites/$normalizedEmail", OperationType.WRITE) {
            require(normalizedEmail.contains("@") && normalizedEmail.contains(".")) { "Valid email required" }
            val payload = mapOf(
                "employeeId" to cleanEmpId,
                "email" to normalizedEmail,
                "name" to cleanName,
                "phone" to cleanPhone,
                "designation" to cleanDesignation,
                "monthlySalary" to cleanSalary,
                "active" to active,
                "permissions" to permissions,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("staffInvites").document(normalizedEmail).set(payload).await()
            // Immediately link /staffAccounts/{uid} if this employee already has a Firebase Auth account in /users
            syncExistingUserToStaffAccount(
                adminUid = uid,
                normalizedEmail = normalizedEmail,
                employeeId = cleanEmpId,
                name = cleanName,
                phone = cleanPhone,
                designation = cleanDesignation,
                monthlySalary = cleanSalary,
                active = active,
                permissions = permissions
            )
            logAudit(session, "CREATE", "staff", normalizedEmail, "Invited staff $cleanName ($cleanEmpId / $normalizedEmail)")
        }
    }

    suspend fun updateStaffInvite(
        session: UserSession,
        email: String,
        employeeId: String,
        name: String,
        phone: String,
        designation: String,
        monthlySalary: Double,
        active: Boolean,
        permissions: Map<String, List<String>>
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ आमंत्रण संपादित कर सकते हैं।"))
        }
        val adminUid = requireUid()
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य ईमेल।"))
        }
        val cleanEmpId = employeeId.trim().take(40)
        val cleanName = name.trim().take(100)
        val cleanPhone = phone.trim().take(20)
        val cleanDesignation = designation.trim().ifBlank { "गौ सेवक" }.take(80)
        val cleanSalary = monthlySalary.coerceIn(0.0, 1000000.0)
        return safeWrite("staffInvites/$normalizedEmail", OperationType.UPDATE) {
            db.collection("staffInvites").document(normalizedEmail).update(
                mapOf(
                    "employeeId" to cleanEmpId,
                    "name" to cleanName,
                    "phone" to cleanPhone,
                    "designation" to cleanDesignation,
                    "monthlySalary" to cleanSalary,
                    "active" to active,
                    "permissions" to permissions,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            syncExistingUserToStaffAccount(
                adminUid = adminUid,
                normalizedEmail = normalizedEmail,
                employeeId = cleanEmpId,
                name = cleanName,
                phone = cleanPhone,
                designation = cleanDesignation,
                monthlySalary = cleanSalary,
                active = active,
                permissions = permissions
            )
            logAudit(session, "UPDATE", "staff", normalizedEmail, "Updated invite active=$active")
        }
    }

    suspend fun updateStaffAccount(
        session: UserSession,
        staffUid: String,
        active: Boolean,
        monthlySalary: Double,
        permissions: Map<String, List<String>>,
        employeeId: String? = null,
        name: String? = null,
        phone: String? = null,
        designation: String? = null
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ अनुमतियाँ बदल सकते हैं।"))
        }
        return safeWrite("staffAccounts/$staffUid", OperationType.UPDATE) {
            val staffRef = db.collection("staffAccounts").document(staffUid)
            val existingSnap = staffRef.get().await()
            val cleanSalary = monthlySalary.coerceIn(0.0, 1000000.0)
            val updates = mutableMapOf<String, Any>(
                "active" to active,
                "monthlySalary" to cleanSalary,
                "permissions" to permissions,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            employeeId?.let { updates["employeeId"] = it.trim().take(40) }
            name?.takeIf { it.isNotBlank() }?.let { updates["name"] = it.trim().take(100) }
            phone?.let { updates["phone"] = it.trim().take(20) }
            designation?.takeIf { it.isNotBlank() }?.let { updates["designation"] = it.trim().take(80) }
            staffRef.update(updates).await()

            // Keep matching /staffInvites/{email} & /users/{uid} synced so deactivation/permissions cannot drift
            val staffEmail = existingSnap.getString("email")?.trim()?.lowercase().orEmpty()
            if (staffEmail.isNotBlank()) {
                try {
                    val inviteRef = db.collection("staffInvites").document(staffEmail)
                    if (inviteRef.get().await().exists()) {
                        inviteRef.update(updates).await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal staffInvite sync notice for $staffEmail: ${e.message}")
                }
            }
            try {
                val userRef = db.collection("users").document(staffUid)
                val userSnap = userRef.get().await()
                if (userSnap.exists() && userSnap.getString("role") != "ADMIN") {
                    userRef.update(
                        mapOf(
                            "role" to if (active) "STAFF" else "PUBLIC",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal user role sync notice for $staffUid: ${e.message}")
            }
            logAudit(session, "UPDATE", "staff", staffUid, "Updated permissions/active=$active")
        }
    }

    suspend fun deleteStaffAccount(session: UserSession, staffUid: String): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ खाता हटा सकते हैं।"))
        }
        if (staffUid.isBlank() || staffUid == session.uid) {
            return Result.failure(IllegalArgumentException("अमान्य स्टाफ खाता।"))
        }
        return safeWrite("staffAccounts/$staffUid", OperationType.DELETE) {
            val staffRef = db.collection("staffAccounts").document(staffUid)
            val existingEmail = try {
                staffRef.get().await().getString("email")?.trim()?.lowercase().orEmpty()
            } catch (_: Exception) {
                ""
            }
            staffRef.delete().await()
            if (existingEmail.isNotBlank()) {
                try {
                    db.collection("staffInvites").document(existingEmail).delete().await()
                } catch (e: Exception) {
                    Log.w(TAG, "Non-fatal staffInvite cleanup notice for $existingEmail: ${e.message}")
                }
            }
            try {
                val userRef = db.collection("users").document(staffUid)
                val userSnap = userRef.get().await()
                if (userSnap.exists() && userSnap.getString("role") != "ADMIN") {
                    userRef.update(
                        mapOf(
                            "role" to "PUBLIC",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Non-fatal user role reset notice for $staffUid: ${e.message}")
            }
            logAudit(session, "DELETE", "staff", staffUid, "Deleted staff account")
        }
    }

    suspend fun deleteStaffInvite(session: UserSession, email: String): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) स्टाफ आमंत्रण हटा सकते हैं।"))
        }
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य ईमेल।"))
        }
        return safeWrite("staffInvites/$normalizedEmail", OperationType.DELETE) {
            db.collection("staffInvites").document(normalizedEmail).delete().await()
            logAudit(session, "DELETE", "staff", normalizedEmail, "Deleted staff invite")
        }
    }

    suspend fun addStaffOperation(session: UserSession, entry: StaffOperation): Result<Unit> {
        if (!session.canAdd(AppModule.STAFF)) {
            return Result.failure(SecurityException("कर्मचारी उपस्थिति/वेतन प्रविष्टि जोड़ने की अनुमति नहीं है।"))
        }
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
        if (!session.canAdd(AppModule.FINANCE)) {
            return Result.failure(SecurityException("वित्तीय प्रविष्टि जोड़ने की अनुमति नहीं है।"))
        }
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

    suspend fun updateFinancialTx(session: UserSession, tx: FinancialTransaction): Result<Unit> {
        if (!session.canEdit(AppModule.FINANCE)) {
            return Result.failure(SecurityException("वित्तीय प्रविष्टि संपादित करने की अनुमति नहीं है।"))
        }
        if (tx.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य वित्तीय प्रविष्टि आईडी।"))
        }
        return safeWrite("financialTransactions/${tx.id}", OperationType.UPDATE) {
            require(tx.amountInr > 0.0) { "राशि 0 से अधिक होनी चाहिए।" }
            require(tx.partyName.isNotBlank() && tx.description.isNotBlank()) { "पक्ष का नाम व विवरण आवश्यक है।" }
            db.collection("financialTransactions").document(tx.id).update(
                mapOf(
                    "txType" to tx.txType,
                    "category" to tx.category,
                    "amountInr" to tx.amountInr.coerceIn(0.01, 100000000.0),
                    "paymentMode" to tx.paymentMode,
                    "paymentStatus" to tx.paymentStatus,
                    "partyName" to tx.partyName.trim().take(120),
                    "description" to tx.description.trim().take(300),
                    "dateStr" to tx.dateStr.trim().ifBlank { QrAndExportUtils.todayIso() }.take(30),
                    "receiptNumber" to tx.receiptNumber.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "finance", tx.id, "Updated ${tx.txType} ₹${tx.amountInr}")
        }
    }

    suspend fun deleteFinancialTx(session: UserSession, txId: String): Result<Unit> {
        if (!session.canDelete(AppModule.FINANCE)) {
            return Result.failure(SecurityException("वित्तीय प्रविष्टि हटाने की अनुमति नहीं है।"))
        }
        return safeWrite("financialTransactions/$txId", OperationType.DELETE) {
            db.collection("financialTransactions").document(txId).delete().await()
            logAudit(session, "DELETE", "finance", txId, "Deleted transaction")
        }
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
        if (!session.canAdd(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान रिकॉर्ड जोड़ने की अनुमति नहीं है।"))
        }
        val uid = requireUid()
        val docRef = db.collection("farmers").document()
        return safeWrite(docRef.path, OperationType.CREATE) {
            docRef.set(farmer.copy(createdBy = uid).toCreateMap()).await()
            logAudit(session, "CREATE", "farmers", docRef.id, "Added farmer ${farmer.name}")
        }
    }

    suspend fun updateFarmer(session: UserSession, farmer: FarmerRecord): Result<Unit> {
        if (!session.canEdit(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान रिकॉर्ड संपादित करने की अनुमति नहीं है।"))
        }
        if (farmer.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य किसान आईडी।"))
        }
        return safeWrite("farmers/${farmer.id}", OperationType.UPDATE) {
            require(farmer.name.isNotBlank() && farmer.mobile.trim().length >= 6 && farmer.village.isNotBlank()) {
                "नाम, गाँव व सही मोबाइल नंबर आवश्यक है।"
            }
            db.collection("farmers").document(farmer.id).update(
                mapOf(
                    "name" to farmer.name.trim().take(100),
                    "mobile" to farmer.mobile.trim().take(20),
                    "village" to farmer.village.trim().take(100),
                    "address" to farmer.address.trim().take(250),
                    "suppliesOffered" to farmer.suppliesOffered.trim().take(250),
                    "cooperationType" to farmer.cooperationType,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "farmers", farmer.id, "Updated farmer ${farmer.name}")
        }
    }

    suspend fun deleteFarmer(session: UserSession, farmer: FarmerRecord): Result<Unit> {
        if (!session.canDelete(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान रिकॉर्ड हटाने की अनुमति नहीं है।"))
        }
        if (farmer.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य किसान आईडी।"))
        }
        return safeWrite("farmers/${farmer.id}", OperationType.DELETE) {
            db.collection("farmers").document(farmer.id).delete().await()
            logAudit(session, "DELETE", "farmers", farmer.id, "Deleted farmer ${farmer.name}")
        }
    }

    suspend fun addFarmerTransaction(session: UserSession, tx: FarmerTransaction, currentFarmer: FarmerRecord): Result<Unit> {
        if (!session.canAdd(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान बिल जोड़ने की अनुमति नहीं है।"))
        }
        val uid = requireUid()
        val docRef = db.collection("farmerTransactions").document()
        val invoice = tx.invoiceNumber.ifBlank { "FG-${System.currentTimeMillis().toString().takeLast(5)}" }
        return safeWrite(docRef.path, OperationType.CREATE) {
            val outstanding = (tx.totalAmountInr - tx.paidAmountInr).coerceAtLeast(0.0)
            val safePayStatus = when {
                tx.paymentStatus in FarmerTransaction.ALL_PAYMENT_STATUSES -> tx.paymentStatus
                outstanding <= 0.0 -> "PAID"
                tx.paidAmountInr > 0.0 -> "PARTIAL"
                else -> "PENDING"
            }
            docRef.set(
                tx.copy(
                    invoiceNumber = invoice,
                    outstandingInr = outstanding,
                    paymentStatus = safePayStatus,
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
            logAudit(session, "CREATE", "farmers", docRef.id, "${tx.direction} ${tx.productName} ₹${tx.totalAmountInr} ($safePayStatus)")
        }
    }

    suspend fun updateFarmerTransaction(session: UserSession, tx: FarmerTransaction): Result<Unit> {
        if (!session.canEdit(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान लेनदेन संपादित करने की अनुमति नहीं है।"))
        }
        if (tx.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य लेनदेन आईडी।"))
        }
        return safeWrite("farmerTransactions/${tx.id}", OperationType.UPDATE) {
            require(tx.productName.isNotBlank() && tx.quantity > 0.0) { "सामग्री नाम व सही मात्रा आवश्यक है।" }
            val total = (tx.quantity * tx.ratePerUnitInr).coerceAtLeast(0.0)
            val paid = tx.paidAmountInr.coerceIn(0.0, total)
            val outstanding = (total - paid).coerceAtLeast(0.0)
            val safePayStatus = when {
                tx.paymentStatus in FarmerTransaction.ALL_PAYMENT_STATUSES -> tx.paymentStatus
                outstanding <= 0.0 -> "PAID"
                paid > 0.0 -> "PARTIAL"
                else -> "PENDING"
            }
            db.collection("farmerTransactions").document(tx.id).update(
                mapOf(
                    "direction" to tx.direction,
                    "productName" to tx.productName.trim().take(100),
                    "quantity" to tx.quantity,
                    "unit" to tx.unit.trim().ifBlank { "KG" }.take(30),
                    "qualityGrade" to tx.qualityGrade.trim().ifBlank { "A" }.take(40),
                    "ratePerUnitInr" to tx.ratePerUnitInr.coerceAtLeast(0.0),
                    "totalAmountInr" to total,
                    "paidAmountInr" to paid,
                    "outstandingInr" to outstanding,
                    "paymentStatus" to safePayStatus,
                    "dateStr" to tx.dateStr.trim().ifBlank { QrAndExportUtils.todayIso() }.take(30),
                    "notes" to tx.notes.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "farmers", tx.id, "Updated farmer tx ${tx.invoiceNumber} ($safePayStatus)")
        }
    }

    suspend fun deleteFarmerTransaction(session: UserSession, txId: String): Result<Unit> {
        if (!session.canDelete(AppModule.FARMERS)) {
            return Result.failure(SecurityException("किसान लेनदेन हटाने की अनुमति नहीं है।"))
        }
        if (txId.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य लेनदेन आईडी।"))
        }
        return safeWrite("farmerTransactions/$txId", OperationType.DELETE) {
            db.collection("farmerTransactions").document(txId).delete().await()
            logAudit(session, "DELETE", "farmers", txId, "Deleted farmer transaction")
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
        if (!session.canAdd(AppModule.PRODUCTION)) {
            return Result.failure(SecurityException("उत्पादन बैच जोड़ने की अनुमति नहीं है।"))
        }
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

    suspend fun updateCompostBatch(session: UserSession, batch: CompostBatch): Result<Unit> {
        if (!session.canEdit(AppModule.PRODUCTION)) {
            return Result.failure(SecurityException("उत्पादन बैच संपादित करने की अनुमति नहीं है।"))
        }
        if (batch.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य बैच आईडी।"))
        }
        return safeWrite("compostProduction/${batch.id}", OperationType.UPDATE) {
            require(batch.quantity > 0.0) { "उत्पाद मात्रा 0 से अधिक होनी चाहिए।" }
            val newStock = (batch.quantity - batch.soldQuantity).coerceAtLeast(0.0)
            val newStatus = if (newStock <= 0.0 && batch.soldQuantity > 0.0) "SOLD_OUT" else batch.status
            db.collection("compostProduction").document(batch.id).update(
                mapOf(
                    "productType" to batch.productType,
                    "quantity" to batch.quantity.coerceAtLeast(0.0),
                    "unit" to batch.unit.trim().ifBlank { "KG" }.take(30),
                    "rawDungKg" to batch.rawDungKg.coerceAtLeast(0.0),
                    "productionCostInr" to batch.productionCostInr.coerceAtLeast(0.0),
                    "packagingCostInr" to batch.packagingCostInr.coerceAtLeast(0.0),
                    "sellingPricePerUnitInr" to batch.sellingPricePerUnitInr.coerceAtLeast(0.0),
                    "stockRemaining" to newStock,
                    "status" to newStatus,
                    "dateStr" to batch.dateStr.trim().ifBlank { QrAndExportUtils.todayIso() }.take(30),
                    "staffNames" to batch.staffNames.trim().take(200),
                    "notes" to batch.notes.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "production", batch.id, "Updated batch ${batch.batchCode}")
        }
    }

    suspend fun deleteCompostBatch(session: UserSession, batch: CompostBatch): Result<Unit> {
        if (!session.canDelete(AppModule.PRODUCTION)) {
            return Result.failure(SecurityException("उत्पादन बैच हटाने की अनुमति नहीं है।"))
        }
        if (batch.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य बैच आईडी।"))
        }
        return safeWrite("compostProduction/${batch.id}", OperationType.DELETE) {
            db.collection("compostProduction").document(batch.id).delete().await()
            logAudit(session, "DELETE", "production", batch.id, "Deleted batch ${batch.batchCode}")
        }
    }

    suspend fun recordCompostSale(session: UserSession, batch: CompostBatch, sellQty: Double): Result<Unit> {
        if (!session.canEdit(AppModule.PRODUCTION)) {
            return Result.failure(SecurityException("बिक्री दर्ज करने की अनुमति नहीं है।"))
        }
        return safeWrite("compostProduction/${batch.id}", OperationType.UPDATE) {
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
    }

    // --- DONATIONS & COW SPONSORSHIP ---
    fun observeDonations(session: UserSession): Flow<List<Donation>> {
        val query = when {
            session.role == UserRole.ADMIN || session.hasPermission(AppModule.DONATIONS, CrudAction.VIEW) ->
                db.collection("donations").limit(150)
            session.role == UserRole.PUBLIC ->
                db.collection("donations").whereEqualTo("donorUid", session.uid).limit(100)
            else -> return flowOf(emptyList())
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Donation::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun submitDonation(session: UserSession, donation: Donation): Result<Unit> {
        if (session.role != UserRole.PUBLIC && !session.canAdd(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("दान प्रविष्टि जोड़ने की अनुमति नहीं है।"))
        }
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

    suspend fun verifyDonation(session: UserSession, donationId: String, newStatus: String): Result<Unit> {
        if (!session.canEdit(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("दान सत्यापित/संपादित करने की अनुमति नहीं है।"))
        }
        return safeWrite("donations/$donationId", OperationType.UPDATE) {
            db.collection("donations").document(donationId).update(
                mapOf(
                    "paymentStatus" to newStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "donations", donationId, "Donation status -> $newStatus")
        }
    }

    suspend fun updateDonation(session: UserSession, donation: Donation): Result<Unit> {
        if (!session.canEdit(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("दान प्रविष्टि संपादित करने की अनुमति नहीं है।"))
        }
        if (donation.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य दान आईडी।"))
        }
        return safeWrite("donations/${donation.id}", OperationType.UPDATE) {
            require(donation.donorName.isNotBlank() && donation.amountInr > 0.0) { "दाता का नाम व सही राशि आवश्यक है।" }
            db.collection("donations").document(donation.id).update(
                mapOf(
                    "donorName" to donation.donorName.trim().take(100),
                    "donorPhone" to donation.donorPhone.trim().take(20),
                    "donorCity" to donation.donorCity.trim().take(100),
                    "purpose" to donation.purpose.trim().ifBlank { "GAU_SEVA" }.take(100),
                    "amountInr" to donation.amountInr.coerceIn(1.0, 100000000.0),
                    "paymentProviderRef" to donation.paymentProviderRef.trim().ifBlank { "MANUAL_PENDING" }.take(120),
                    "paymentStatus" to donation.paymentStatus,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "donations", donation.id, "Updated donation ₹${donation.amountInr}")
        }
    }

    suspend fun deleteDonation(session: UserSession, donationId: String): Result<Unit> {
        if (!session.canDelete(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("दान प्रविष्टि हटाने की अनुमति नहीं है।"))
        }
        if (donationId.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य दान आईडी।"))
        }
        return safeWrite("donations/$donationId", OperationType.DELETE) {
            db.collection("donations").document(donationId).delete().await()
            logAudit(session, "DELETE", "donations", donationId, "Deleted donation")
        }
    }

    fun observeSponsorships(session: UserSession): Flow<List<Sponsorship>> {
        val query = when {
            session.role == UserRole.ADMIN || session.hasPermission(AppModule.DONATIONS, CrudAction.VIEW) ->
                db.collection("sponsorships").limit(100)
            session.role == UserRole.PUBLIC ->
                db.collection("sponsorships").whereEqualTo("applicantUid", session.uid).limit(100)
            else -> return flowOf(emptyList())
        }
        return query.snapshots().map { snap ->
            snap.toObjects(Sponsorship::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun submitSponsorship(session: UserSession, sponsorship: Sponsorship): Result<Unit> {
        if (session.role != UserRole.PUBLIC && !session.canAdd(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("गौ-गोद आवेदन जोड़ने की अनुमति नहीं है।"))
        }
        val uid = requireUid()
        val docRef = db.collection("sponsorships").document()
        val safePaymentMode = if (sponsorship.paymentMode in ProductOrder.ALL_PAYMENT_MODES) {
            sponsorship.paymentMode
        } else {
            "CASH"
        }
        // SECURITY: Public users can NEVER self-verify payments; only Admin/authorized editor can set VERIFIED.
        val safePaymentStatus = when {
            session.role == UserRole.ADMIN && sponsorship.paymentStatus in ProductOrder.ALL_PAYMENT_STATUSES ->
                sponsorship.paymentStatus
            sponsorship.paymentStatus == "PAID" -> "PAID"
            else -> "PENDING"
        }
        val cleanMonths = sponsorship.durationMonths.coerceIn(1L, 120L)
        val cleanMonthly = sponsorship.monthlySupportInr.coerceAtLeast(100.0)
        return safeWrite(docRef.path, OperationType.CREATE) {
            require(sponsorship.disclaimerAccepted) { "गौ-गोद अस्वीकरण सहमति आवश्यक है / Disclaimer required" }
            docRef.set(
                sponsorship.copy(
                    applicantUid = uid,
                    applicantName = sponsorship.applicantName.trim().take(100),
                    applicantPhone = sponsorship.applicantPhone.trim().take(20),
                    applicantAddress = sponsorship.applicantAddress.trim().take(250),
                    monthlySupportInr = cleanMonthly,
                    durationMonths = cleanMonths,
                    totalSupportInr = cleanMonthly * cleanMonths,
                    status = "PENDING",
                    message = sponsorship.message.trim().take(300),
                    paymentMode = safePaymentMode,
                    paymentStatus = safePaymentStatus,
                    paymentRef = sponsorship.paymentRef.trim().take(120),
                    dateStr = QrAndExportUtils.todayIso(),
                    certificateNumber = "",
                    reviewedBy = ""
                ).toCreateMap()
            ).await()
            createNotificationInternal(
                targetRole = "ADMIN",
                category = "ADOPTION",
                title = "नया गौ-गोद (प्रायोजन) आवेदन",
                message = "${sponsorship.applicantName} ने ${sponsorship.cattleName} (${sponsorship.animalCode}) के लिए ₹${(cleanMonthly * cleanMonths).toInt()} का आवेदन किया।"
            )
            logAudit(session, "CREATE", "sponsorships", docRef.id, "Applied for ${sponsorship.cattleName} ($safePaymentMode/$safePaymentStatus)")
        }
    }

    suspend fun updateSponsorship(session: UserSession, sponsorship: Sponsorship): Result<Unit> {
        if (!session.canEdit(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("गौ-गोद विवरण संपादित करने की अनुमति नहीं है।"))
        }
        if (sponsorship.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य प्रायोजन आईडी।"))
        }
        return safeWrite("sponsorships/${sponsorship.id}", OperationType.UPDATE) {
            require(sponsorship.applicantName.isNotBlank() && sponsorship.applicantPhone.trim().length >= 6) {
                "आवेदक का नाम व मोबाइल नंबर आवश्यक है।"
            }
            val months = sponsorship.durationMonths.coerceIn(1L, 120L)
            val monthly = sponsorship.monthlySupportInr.coerceAtLeast(100.0)
            val safeStatus = if (sponsorship.status in listOf("PENDING", "APPROVED", "REJECTED")) {
                sponsorship.status
            } else {
                "PENDING"
            }
            val safePayStatus = if (sponsorship.paymentStatus in ProductOrder.ALL_PAYMENT_STATUSES) {
                sponsorship.paymentStatus
            } else {
                "PENDING"
            }
            val cert = when {
                safeStatus == "APPROVED" && sponsorship.certificateNumber.isBlank() ->
                    "JBGS-GAU-${System.currentTimeMillis().toString().takeLast(5)}"
                safeStatus == "APPROVED" -> sponsorship.certificateNumber.trim().take(60)
                else -> ""
            }
            val uid = requireUid()
            db.collection("sponsorships").document(sponsorship.id).update(
                mapOf(
                    "applicantName" to sponsorship.applicantName.trim().take(100),
                    "applicantPhone" to sponsorship.applicantPhone.trim().take(20),
                    "applicantAddress" to sponsorship.applicantAddress.trim().take(250),
                    "monthlySupportInr" to monthly,
                    "durationMonths" to months,
                    "totalSupportInr" to (monthly * months),
                    "status" to safeStatus,
                    "paymentStatus" to safePayStatus,
                    "certificateNumber" to cert,
                    "reviewedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit(session, "UPDATE", "sponsorships", sponsorship.id, "Updated sponsorship ${sponsorship.applicantName} ($safeStatus/$safePayStatus)")
        }
    }

    suspend fun reviewSponsorship(session: UserSession, sponsorship: Sponsorship, approve: Boolean): Result<Unit> {
        if (!session.canEdit(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("गौ-गोद आवेदन स्वीकृत या अस्वीकृत करने की अनुमति नहीं है।"))
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

    suspend fun verifySponsorshipPayment(
        session: UserSession,
        sponsorship: Sponsorship,
        newPaymentStatus: String
    ): Result<Unit> {
        if (!session.canEdit(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("गौ-गोद भुगतान सत्यापित करने की अनुमति नहीं है।"))
        }
        if (sponsorship.id.isBlank() || newPaymentStatus !in ProductOrder.ALL_PAYMENT_STATUSES) {
            return Result.failure(IllegalArgumentException("अमान्य भुगतान स्थिति या प्रायोजन आईडी।"))
        }
        return safeWrite("sponsorships/${sponsorship.id}", OperationType.UPDATE) {
            val uid = requireUid()
            db.collection("sponsorships").document(sponsorship.id).update(
                mapOf(
                    "paymentStatus" to newPaymentStatus,
                    "reviewedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createNotificationInternal(
                targetRole = "PUBLIC",
                targetUid = sponsorship.applicantUid,
                category = "ADOPTION_PAYMENT",
                title = "गौ-गोद भुगतान स्थिति अपडेट",
                message = "${sponsorship.cattleName} (${sponsorship.animalCode}) हेतु भुगतान स्थिति अब ${paymentStatusLabel(newPaymentStatus, true)} है।"
            )
            logAudit(session, "VERIFY_PAYMENT", "sponsorships", sponsorship.id, "Payment -> $newPaymentStatus")
        }
    }

    suspend fun deleteSponsorship(session: UserSession, sponsorshipId: String): Result<Unit> {
        if (!session.canDelete(AppModule.DONATIONS)) {
            return Result.failure(SecurityException("गौ-गोद आवेदन हटाने की अनुमति नहीं है।"))
        }
        if (sponsorshipId.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य प्रायोजन आईडी।"))
        }
        return safeWrite("sponsorships/$sponsorshipId", OperationType.DELETE) {
            db.collection("sponsorships").document(sponsorshipId).delete().await()
            logAudit(session, "DELETE", "sponsorships", sponsorshipId, "Deleted sponsorship")
        }
    }

    // --- PRODUCT PURCHASE APPLICATIONS (PUBLIC ORDERS & ADMIN MANAGEMENT) ---
    fun observeProductOrders(session: UserSession): Flow<List<ProductOrder>> {
        val query = when (session.role) {
            UserRole.ADMIN -> db.collection("productOrders").limit(150)
            UserRole.PUBLIC -> db.collection("productOrders").whereEqualTo("buyerUid", session.uid).limit(100)
            UserRole.STAFF -> return flowOf(emptyList())
        }
        return query.snapshots().map { snap ->
            snap.toObjects(ProductOrder::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.createdAt?.seconds ?: 0L }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun submitProductOrder(session: UserSession, order: ProductOrder): Result<String> {
        if (session.role != UserRole.PUBLIC && session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("उत्पाद खरीद आवेदन केवल Public User या Admin द्वारा जमा किया जा सकता है।"))
        }
        val uid = requireUid()
        val docRef = db.collection("productOrders").document()
        val orderNo = order.orderNumber.trim().ifBlank {
            "JBGS-ORD-${(1000..9999).random()}"
        }.take(40)
        val qty = order.quantity.coerceIn(1L, 500L)
        val unitPrice = order.unitPriceInr.coerceIn(1.0, 1000000.0)
        val total = (unitPrice * qty).coerceIn(1.0, 500000000.0)
        val safePaymentMode = if (order.paymentMode in ProductOrder.ALL_PAYMENT_MODES) order.paymentMode else "CASH"
        val safePaymentStatus = when {
            session.role == UserRole.ADMIN && order.paymentStatus in ProductOrder.ALL_PAYMENT_STATUSES -> order.paymentStatus
            order.paymentStatus == "PAID" -> "PAID"
            else -> "PENDING"
        }
        return safeWrite(docRef.path, OperationType.CREATE) {
            require(order.buyerName.trim().length >= 2) { "सही नाम आवश्यक है।" }
            require(order.buyerPhone.filter { it.isDigit() }.length >= 6) { "सही मोबाइल नंबर आवश्यक है।" }
            require(order.buyerAddress.trim().length >= 2) { "सही पता आवश्यक है।" }
            require(order.productId.isNotBlank() && order.productName.isNotBlank()) { "उत्पाद विवरण आवश्यक है।" }

            docRef.set(
                order.copy(
                    orderNumber = orderNo,
                    buyerUid = uid,
                    buyerName = order.buyerName.trim().take(100),
                    buyerPhone = order.buyerPhone.trim().take(20),
                    buyerAddress = order.buyerAddress.trim().take(300),
                    productId = order.productId.trim().take(60),
                    productName = order.productName.trim().take(120),
                    unitLabel = order.unitLabel.trim().ifBlank { "इकाई" }.take(60),
                    unitPriceInr = unitPrice,
                    quantity = qty,
                    totalAmountInr = total,
                    orderStatus = "NEW",
                    paymentMode = safePaymentMode,
                    paymentStatus = safePaymentStatus,
                    paymentRef = order.paymentRef.trim().take(120),
                    notes = order.notes.trim().take(400),
                    adminNote = "",
                    dateStr = QrAndExportUtils.todayIso(),
                    reviewedBy = ""
                ).toCreateMap()
            ).await()
            createNotificationInternal(
                targetRole = "ADMIN",
                category = "PRODUCT_ORDER",
                title = "नया उत्पाद खरीद आवेदन ($orderNo)",
                message = "${order.buyerName.trim()} • ${order.productName.trim()} × $qty (₹${total.toInt()})"
            )
            logAudit(session, "CREATE", "productOrders", docRef.id, "$orderNo ${order.productName} x$qty ₹${total.toInt()}")
            orderNo
        }
    }

    suspend fun updateProductOrderStatus(
        session: UserSession,
        order: ProductOrder,
        newOrderStatus: String,
        newPaymentStatus: String,
        adminNote: String = order.adminNote
    ): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) उत्पाद ऑर्डर स्थिति अपडेट कर सकते हैं।"))
        }
        if (order.id.isBlank() ||
            newOrderStatus !in ProductOrder.ALL_ORDER_STATUSES ||
            newPaymentStatus !in ProductOrder.ALL_PAYMENT_STATUSES
        ) {
            return Result.failure(IllegalArgumentException("अमान्य ऑर्डर या भुगतान स्थिति।"))
        }
        return safeWrite("productOrders/${order.id}", OperationType.UPDATE) {
            val uid = requireUid()
            db.collection("productOrders").document(order.id).update(
                mapOf(
                    "orderStatus" to newOrderStatus,
                    "paymentStatus" to newPaymentStatus,
                    "adminNote" to adminNote.trim().take(300),
                    "reviewedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createNotificationInternal(
                targetRole = "PUBLIC",
                targetUid = order.buyerUid,
                category = "PRODUCT_ORDER",
                title = "ऑर्डर अपडेट: ${order.orderNumber}",
                message = "${order.productName} × ${order.quantity} — स्थिति: ${productOrderStatusLabel(newOrderStatus, true)} | भुगतान: ${paymentStatusLabel(newPaymentStatus, true)}"
            )
            logAudit(session, "UPDATE", "productOrders", order.id, "${order.orderNumber} -> $newOrderStatus / $newPaymentStatus")
        }
    }

    suspend fun deleteProductOrder(session: UserSession, orderId: String): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) उत्पाद ऑर्डर हटा सकते हैं।"))
        }
        if (orderId.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य ऑर्डर आईडी।"))
        }
        return safeWrite("productOrders/$orderId", OperationType.DELETE) {
            db.collection("productOrders").document(orderId).delete().await()
            logAudit(session, "DELETE", "productOrders", orderId, "Deleted product order")
        }
    }

    // --- ADMIN PRODUCT CATALOG (SYNCED LIVE TO PUBLIC DASHBOARD) ---
    fun observeProducts(session: UserSession): Flow<List<GoshalaProduct>> {
        if (session.role == UserRole.STAFF && !session.staffActive) return flowOf(emptyList())
        return db.collection("products").limit(150).snapshots().map { snap ->
            snap.toObjects(GoshalaProduct::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.nameHi }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun saveProduct(
        session: UserSession,
        product: GoshalaProduct,
        isEdit: Boolean,
        appContext: Context? = null
    ): Result<String> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) उत्पाद जोड़ या संपादित कर सकते हैं।"))
        }
        val uid = requireUid()
        val cleanNameHi = product.nameHi.trim().take(120)
        val cleanNameEn = product.nameEn.trim().ifBlank { cleanNameHi }.take(120)
        val cleanDescHi = product.descriptionHi.trim().take(500)
        val cleanDescEn = product.descriptionEn.trim().ifBlank { cleanDescHi }.take(500)
        val cleanCat = if (product.categoryKey in GoshalaProduct.ALL_CATEGORIES) product.categoryKey else "OTHER"
        val cleanPrice = product.priceInr.coerceIn(1.0, 1000000.0)
        val cleanStock = product.stockQuantity.coerceIn(0L, 1000000L)
        val cleanUnit = product.unitLabel.trim().ifBlank { "इकाई" }.take(60)
        val cleanPhotoKey = product.photoKey.trim().ifBlank { "compost" }.take(60)
        val rawPhotoUri = product.photoUri.trim()

        val docRef = if (isEdit && product.id.isNotBlank()) {
            db.collection("products").document(product.id)
        } else {
            db.collection("products").document()
        }
        return safeWrite(docRef.path, if (isEdit) OperationType.UPDATE else OperationType.CREATE) {
            require(cleanNameHi.isNotBlank()) { "उत्पाद का नाम आवश्यक है।" }
            require(cleanDescHi.isNotBlank()) { "उत्पाद का विवरण आवश्यक है।" }
            require(product.priceInr > 0.0) { "बिक्री मूल्य 0 से अधिक होना चाहिए।" }

            val cleanPhotoUri = if (CattlePhotoStorage.isLocalDeviceUri(rawPhotoUri)) {
                val uploaded = CattlePhotoStorage.uploadProductPhoto(
                    context = appContext,
                    productId = docRef.id,
                    sourceUriString = rawPhotoUri
                ).getOrElse { err ->
                    throw IllegalStateException(
                        err.message ?: "उत्पाद की फोटो क्लाउड पर अपलोड नहीं हो सकी। कृपया इंटरनेट जांचकर पुनः प्रयास करें।",
                        err
                    )
                }
                uploaded.photoUrl.trim().take(2000)
            } else {
                rawPhotoUri.take(2000)
            }

            if (isEdit) {
                docRef.update(
                    mapOf(
                        "nameHi" to cleanNameHi,
                        "nameEn" to cleanNameEn,
                        "categoryKey" to cleanCat,
                        "descriptionHi" to cleanDescHi,
                        "descriptionEn" to cleanDescEn,
                        "priceInr" to cleanPrice,
                        "stockQuantity" to cleanStock,
                        "unitLabel" to cleanUnit,
                        "available" to product.available,
                        "photoKey" to cleanPhotoKey,
                        "photoUri" to cleanPhotoUri,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    product.copy(
                        nameHi = cleanNameHi,
                        nameEn = cleanNameEn,
                        categoryKey = cleanCat,
                        descriptionHi = cleanDescHi,
                        descriptionEn = cleanDescEn,
                        priceInr = cleanPrice,
                        stockQuantity = cleanStock,
                        unitLabel = cleanUnit,
                        photoKey = cleanPhotoKey,
                        photoUri = cleanPhotoUri,
                        createdBy = uid
                    ).toCreateMap()
                ).await()
            }
            logAudit(session, if (isEdit) "UPDATE" else "CREATE", "products", docRef.id, "$cleanNameHi ₹${cleanPrice.toInt()} (stock=$cleanStock)")
            docRef.id
        }
    }

    suspend fun deleteProduct(session: UserSession, product: GoshalaProduct): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) उत्पाद हटा सकते हैं।"))
        }
        if (product.id.isBlank()) {
            return Result.failure(IllegalArgumentException("अमान्य उत्पाद आईडी।"))
        }
        return safeWrite("products/${product.id}", OperationType.DELETE) {
            db.collection("products").document(product.id).delete().await()
            CattlePhotoStorage.deleteProductPhoto(product.photoUri)
            logAudit(session, "DELETE", "products", product.id, "Deleted product ${product.nameHi}")
        }
    }

    suspend fun seedDefaultProducts(session: UserSession): Result<Unit> {
        if (session.role != UserRole.ADMIN) {
            return Result.failure(SecurityException("केवल प्रशासक (Admin) डिफ़ॉल्ट उत्पाद लोड कर सकते हैं।"))
        }
        val uid = requireUid()
        return safeWrite("products/seed", OperationType.WRITE) {
            for (seed in GoshalaProduct.defaultSeedProducts()) {
                val ref = db.collection("products").document(seed.id)
                if (!ref.get().await().exists()) {
                    ref.set(seed.copy(createdBy = uid).toCreateMap()).await()
                }
            }
            logAudit(session, "CREATE", "products", "seed", "Seeded default Goshala products")
        }
    }

    // --- NOTIFICATIONS & AUDIT LOGS ---
    fun observeNotifications(session: UserSession): Flow<List<NotificationItem>> {
        if (session.role == UserRole.ADMIN) {
            return db.collection("notifications")
                .limit(120)
                .snapshots()
                .map { snap ->
                    snap.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE)
                        .distinctBy { it.id }
                        .sortedWith(
                            compareByDescending<NotificationItem> { it.createdAt?.seconds ?: 0L }
                                .thenByDescending { it.publishDate }
                        )
                }.flowOn(Dispatchers.Default)
        }

        val flows = mutableListOf<Flow<List<NotificationItem>>>()

        // 1. Broadcast PUBLIC & ALL published notices (targetUid == "")
        flows += db.collection("notifications")
            .whereEqualTo("targetRole", "PUBLIC")
            .whereEqualTo("targetUid", "")
            .whereEqualTo("published", true)
            .limit(60)
            .snapshots()
            .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }

        flows += db.collection("notifications")
            .whereEqualTo("targetRole", "ALL")
            .whereEqualTo("targetUid", "")
            .whereEqualTo("published", true)
            .limit(40)
            .snapshots()
            .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }

        // 2. Personal / Specific-Staff published notices targeted strictly to this UID
        flows += db.collection("notifications")
            .whereEqualTo("targetUid", session.uid)
            .whereEqualTo("published", true)
            .limit(30)
            .snapshots()
            .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }

        // 3. Active Staff module-scoped & general broadcast notices (1:1 aligned with Firestore Rules)
        if (session.role == UserRole.STAFF && session.staffActive) {
            // General staff notices (no module restriction)
            flows += db.collection("notifications")
                .whereEqualTo("targetRole", "STAFF")
                .whereEqualTo("targetUid", "")
                .whereEqualTo("targetModule", "")
                .whereEqualTo("published", true)
                .limit(40)
                .snapshots()
                .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }

            // Strictly one query per VIEW-permitted module (equality-only -> zero composite index!)
            AppModule.entries
                .filter { session.hasPermission(it, CrudAction.VIEW) }
                .forEach { mod ->
                    flows += db.collection("notifications")
                        .whereEqualTo("targetRole", "STAFF")
                        .whereEqualTo("targetUid", "")
                        .whereEqualTo("targetModule", mod.key)
                        .whereEqualTo("published", true)
                        .limit(25)
                        .snapshots()
                        .map { it.toObjects(NotificationItem::class.java, ServerTimestampBehavior.ESTIMATE) }
                }
        }

        val combinedFlow: Flow<List<NotificationItem>> = combine(flows) { arrays ->
            arrays.flatMap { it }
        }

        return combinedFlow.map { rawItems ->
            val todayIso = todayIsoDateString()
            val byId = rawItems
                .filter { it.isVisibleToSession(session, todayIso) }
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<NotificationItem> { it.createdAt?.seconds ?: 0L }
                        .thenByDescending { it.publishDate }
                )
            // Deduplicate identical notifications by dedupKey (keeping the newest)
            val seenDedup = HashSet<String>()
            byId.filter { item ->
                val key = item.dedupKey.trim()
                if (key.isEmpty()) true else seenDedup.add(key)
            }
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun uploadNotificationPhotoIfLocal(
        context: Context,
        notifId: String,
        rawPhotoUri: String
    ): String {
        val trimmed = rawPhotoUri.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("preset:", ignoreCase = true)
        ) {
            return trimmed.take(2000)
        }
        val jpegBytes = withContext(Dispatchers.IO) {
            val uri = Uri.parse(trimmed)
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: throw IllegalArgumentException("चयनित फोटो पढ़ी नहीं जा सकी। कृपया दूसरी फोटो चुनें।")
            try {
                val maxDim = 1080
                val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                    val ratio = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
                    Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * ratio).toInt().coerceAtLeast(1),
                        (bitmap.height * ratio).toInt().coerceAtLeast(1),
                        true
                    )
                } else {
                    bitmap
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                if (scaled !== bitmap) scaled.recycle()
                out.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        val storageRef = FirebaseStorage.getInstance()
            .reference
            .child("notification_photos/$notifId/photo_${System.currentTimeMillis()}.jpg")
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        return withTimeout(15_000L) {
            storageRef.putBytes(jpegBytes, metadata).await()
            storageRef.downloadUrl.await().toString().take(2000)
        }
    }

    suspend fun saveNotification(
        session: UserSession,
        item: NotificationItem,
        isEdit: Boolean,
        context: Context? = null
    ): Result<String> = safeWrite("notifications", if (isEdit) OperationType.UPDATE else OperationType.CREATE) {
        if (session.role != UserRole.ADMIN) {
            throw SecurityException("केवल प्रशासक (Admin) सूचना या रिमाइंडर बना व संपादित कर सकते हैं।")
        }
        val uid = requireUid()
        val cleanTitle = item.title.trim()
        val cleanMessage = item.message.trim()
        if (cleanTitle.isBlank() || cleanMessage.isBlank()) {
            throw IllegalArgumentException("शीर्षक और विवरण भरना आवश्यक है।")
        }
        val cleanPublishDate = item.publishDate.trim().ifBlank { todayIsoDateString() }
        val cleanExpiryDate = item.expiryDate.trim()
        if (cleanExpiryDate.isNotBlank() && cleanExpiryDate < cleanPublishDate) {
            throw IllegalArgumentException("समाप्ति तिथि प्रकाशन तिथि से पहले नहीं हो सकती।")
        }

        val safeRole = item.targetRole.trim().uppercase().takeIf { it in setOf("ALL", "PUBLIC", "STAFF", "ADMIN") } ?: "PUBLIC"
        val safeKind = if (item.itemKind.equals("REMINDER", ignoreCase = true)) "REMINDER" else "NOTICE"
        val safeModule = if (safeRole == "STAFF" && item.targetUid.isBlank()) normalizeNotifModuleKey(item.targetModule) else ""
        val cleanTargetUid = if (safeRole == "STAFF") item.targetUid.trim() else ""
        val computedDedup = computeNotificationDedupKey(
            title = cleanTitle,
            category = item.category,
            targetRole = safeRole,
            publishDate = cleanPublishDate,
            itemKind = safeKind,
            reminderTime = item.reminderTime,
            targetUid = cleanTargetUid
        )

        val ref = if (isEdit && item.id.isNotBlank()) {
            // Prevent accidental duplicate collision with a DIFFERENT existing document on edit
            val conflicting = db.collection("notifications")
                .whereEqualTo("dedupKey", computedDedup)
                .limit(2)
                .get()
                .await()
                .documents
                .any { it.id != item.id }
            if (conflicting) {
                throw IllegalStateException("इसी विवरण की एक अन्य सूचना पहले से मौजूद है।")
            }
            db.collection("notifications").document(item.id)
        } else {
            // Prevent duplicate notifications with the same dedupKey on create
            val existing = db.collection("notifications")
                .whereEqualTo("dedupKey", computedDedup)
                .limit(1)
                .get()
                .await()
            if (!existing.isEmpty) {
                throw IllegalStateException("यह सूचना पहले से मौजूद है (डुप्लिकेट सूचना रोकी गई)।")
            }
            db.collection("notifications").document()
        }

        val resolvedPhotoUrl = if (item.photoUrl.startsWith("content://", ignoreCase = true) ||
            item.photoUrl.startsWith("file://", ignoreCase = true)
        ) {
            val ctx = context ?: throw IllegalStateException("फोटो अपलोड के लिए संदर्भ उपलब्ध नहीं है।")
            uploadNotificationPhotoIfLocal(ctx, ref.id, item.photoUrl)
        } else {
            item.photoUrl.trim().take(2000)
        }

        val enriched = item.copy(
            id = ref.id,
            targetRole = safeRole,
            targetUid = cleanTargetUid,
            targetModule = safeModule,
            title = cleanTitle,
            message = cleanMessage,
            photoUrl = resolvedPhotoUrl,
            publishDate = cleanPublishDate,
            expiryDate = cleanExpiryDate,
            itemKind = safeKind,
            priority = item.resolvedPriority().name,
            assigneeName = item.assigneeName.trim().take(120),
            createdBy = item.createdBy.ifBlank { uid },
            dedupKey = computedDedup
        )

        if (isEdit && item.id.isNotBlank()) {
            ref.update(enriched.toUpdateMap()).await()
            logAudit(session, "UPDATE", "notifications", ref.id, "Updated notification: ${cleanTitle.take(80)}")
        } else {
            ref.set(enriched.toCreateMap()).await()
            logAudit(session, "CREATE", "notifications", ref.id, "Created notification: ${cleanTitle.take(80)}")
        }
        ref.id
    }

    suspend fun setNotificationPublished(
        session: UserSession,
        notifId: String,
        published: Boolean
    ): Result<Unit> = safeWrite("notifications/$notifId", OperationType.UPDATE) {
        if (session.role != UserRole.ADMIN) {
            throw SecurityException("केवल प्रशासक (Admin) सूचना प्रकाशित या अप्रकाशित कर सकते हैं।")
        }
        requireUid()
        db.collection("notifications").document(notifId)
            .update(
                mapOf(
                    "published" to published,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        logAudit(
            session,
            "UPDATE",
            "notifications",
            notifId,
            if (published) "Published notification $notifId" else "Unpublished notification $notifId"
        )
    }

    suspend fun deleteNotification(
        session: UserSession,
        notifId: String,
        title: String = ""
    ): Result<Unit> = safeWrite("notifications/$notifId", OperationType.DELETE) {
        if (session.role != UserRole.ADMIN) {
            throw SecurityException("केवल प्रशासक (Admin) सूचना हटा सकते हैं।")
        }
        requireUid()
        db.collection("notifications").document(notifId).delete().await()
        logAudit(session, "DELETE", "notifications", notifId, "Deleted notification: ${title.take(80)}")
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> =
        safeWrite("notifications/$notifId", OperationType.UPDATE) {
            val uid = requireUid()
            db.collection("notifications").document(notifId)
                .update("readByUids", FieldValue.arrayUnion(uid)).await()
        }

    suspend fun markNotificationUnread(notifId: String): Result<Unit> =
        safeWrite("notifications/$notifId", OperationType.UPDATE) {
            val uid = requireUid()
            db.collection("notifications").document(notifId)
                .update("readByUids", FieldValue.arrayRemove(uid)).await()
        }

    suspend fun toggleNotificationActionDone(notifId: String, done: Boolean): Result<Unit> =
        safeWrite("notifications/$notifId", OperationType.UPDATE) {
            val uid = requireUid()
            val op = if (done) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid)
            db.collection("notifications").document(notifId)
                .update("completedByUids", op).await()
        }

    suspend fun markAllNotificationsRead(notifIds: List<String>): Result<Unit> =
        safeWrite("notifications/batch_read", OperationType.UPDATE) {
            val uid = requireUid()
            notifIds.distinct().take(50).forEach { id ->
                try {
                    db.collection("notifications").document(id)
                        .update("readByUids", FieldValue.arrayUnion(uid)).await()
                } catch (_: Exception) {
                    // Continue marking remaining items even if one document was removed concurrently
                }
            }
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
            val todayIso = todayIsoDateString()
            val mappedCategory = NotificationCategory.fromRaw(category).name
            val ref = db.collection("notifications").document()
            ref.set(
                NotificationItem(
                    targetRole = targetRole,
                    targetUid = targetUid,
                    category = mappedCategory,
                    title = title.take(140),
                    message = message.take(1500),
                    published = true,
                    publishDate = todayIso,
                    itemKind = "NOTICE",
                    createdBy = auth.currentUser?.uid.orEmpty()
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
            val result = withTimeout(10_000L) { block() }
            Result.success(result)
        } catch (e: TimeoutCancellationException) {
            handleFirestoreError(e, operationType, path)
            Result.failure(OfflineQueuedException())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, operationType, path)
            Result.failure(e)
        }
    }
}

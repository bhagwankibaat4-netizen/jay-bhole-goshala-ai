package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.util.LruCache
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.File

data class UploadedCowPhoto(
    val photoUrl: String,
    val storagePath: String
)

/**
 * Manages permanent cloud persistence and crash-safe loading of cow photos:
 * 1. Primary: Firebase Storage (`cattle_photos/{cattleId}/photo_{ts}.jpg`) returning permanent HTTPS URL.
 * 2. Automatic Cloud Fallback: Firestore `cattlePhotos/{cattleId}` (compact JPEG Base64 <= 180 KB)
 *    with permanent cloud pointer `firestore://cattlePhotos/{cattleId}?v={ts}` so photos ALWAYS
 *    survive app restart, logout, Public User Dashboard viewing, and cross-device login even if
 *    the Storage bucket/rules have not yet been manually activated in Firebase Console.
 * 3. NEVER persists ephemeral local `content://` or `file://` device URIs to Firestore.
 */
object CattlePhotoStorage {
    private const val TAG = "CattlePhotoStorage"
    const val FIRESTORE_PHOTO_SCHEME = "firestore://cattlePhotos/"
    private const val MAX_STORAGE_DIM = 960
    private const val STORAGE_JPEG_QUALITY = 80
    private const val MAX_FALLBACK_DIM = 520
    private const val FALLBACK_MAX_BASE64_LEN = 220_000

    private val memoryCache = object : LruCache<String, Bitmap>(24) {}

    @Volatile
    private var registeredDb: FirebaseFirestore? = null

    /** Optional test hook to intercept Firestore cloud photo resolution in Robolectric unit tests. */
    @Volatile
    var testCloudPhotoResolver: (suspend (String) -> Bitmap?)? = null

    /** Optional test hook to intercept photo uploads in unit tests without live network. */
    @Volatile
    var testPhotoUploader: (suspend (cattleId: String, localUri: String, publicApproved: Boolean) -> Result<UploadedCowPhoto>)? = null

    /** Optional test hook to intercept product photo uploads in unit tests without live network. */
    @Volatile
    var testProductPhotoUploader: (suspend (productId: String, localUri: String) -> Result<UploadedCowPhoto>)? = null

    fun registerFirestore(db: FirebaseFirestore) {
        registeredDb = db
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    fun isCloudPhotoUri(uri: String?): Boolean {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        return trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith(FIRESTORE_PHOTO_SCHEME, ignoreCase = true)
    }

    fun isLocalDeviceUri(uri: String?): Boolean {
        val trimmed = uri?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        return trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            trimmed.startsWith("/")
    }

    fun extractFirestorePhotoDocId(photoUri: String?): String? {
        val trimmed = photoUri?.trim().orEmpty()
        if (!trimmed.startsWith(FIRESTORE_PHOTO_SCHEME, ignoreCase = true)) return null
        val remainder = trimmed.substring(FIRESTORE_PHOTO_SCHEME.length)
        return remainder.substringBefore("?").substringBefore("/").trim().takeIf { it.isNotBlank() }
    }

    fun buildFirestorePhotoUri(cattleId: String, version: Long): String =
        "$FIRESTORE_PHOTO_SCHEME$cattleId?v=$version"

    /**
     * Creates a FileProvider-backed temporary URI inside `cacheDir/camera_photos` for `TakePicture()`.
     */
    fun createCameraCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "cow_capture_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Uploads a locally picked/captured cow photo to permanent cloud storage.
     * Tries Firebase Storage first, then automatically falls back to Firestore `cattlePhotos/{cattleId}`.
     * Never returns a raw local device URI.
     */
    suspend fun uploadCattlePhoto(
        context: Context?,
        db: FirebaseFirestore,
        uid: String,
        cattleId: String,
        sourceUriString: String,
        publicApproved: Boolean
    ): Result<UploadedCowPhoto> {
        val trimmed = sourceUriString.trim()
        if (isCloudPhotoUri(trimmed)) {
            return Result.success(
                UploadedCowPhoto(
                    photoUrl = trimmed,
                    storagePath = extractFirestorePhotoDocId(trimmed)?.let { "cattlePhotos/$it" } ?: ""
                )
            )
        }

        testPhotoUploader?.let { hook ->
            return hook(cattleId, trimmed, publicApproved)
        }

        if (context == null) {
            return Result.failure(IllegalStateException("फोटो पढ़ने के लिए ऐप कॉन्टेक्स्ट उपलब्ध नहीं है।"))
        }

        val sourceUri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("अमान्य फोटो पथ।"))

        val storageJpeg = withContext(Dispatchers.IO) {
            compressUriToJpeg(context, sourceUri, MAX_STORAGE_DIM, STORAGE_JPEG_QUALITY)
        } ?: return Result.failure(IllegalStateException("चुनी गई फोटो पढ़ी नहीं जा सकी। कृपया दूसरी फोटो चुनें।"))

        val version = System.currentTimeMillis()
        val storagePath = "cattle_photos/$cattleId/photo_$version.jpg"

        // 1. Primary Cloud Upload: Firebase Storage
        val storageUploadUrl: String? = try {
            withTimeout(15_000L) {
                val ref = FirebaseStorage.getInstance().reference.child(storagePath)
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCustomMetadata("cattleId", cattleId)
                    .build()
                ref.putBytes(storageJpeg, metadata).await()
                ref.downloadUrl.await().toString()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Storage upload unavailable/denied, using Firestore cattlePhotos cloud fallback: ${e.message}")
            null
        }

        if (!storageUploadUrl.isNullOrBlank()) {
            return Result.success(
                UploadedCowPhoto(
                    photoUrl = storageUploadUrl,
                    storagePath = storagePath
                )
            )
        }

        // 2. Automatic Cloud Fallback: Compact JPEG stored in Firestore `cattlePhotos/{cattleId}`
        val compactJpeg = withContext(Dispatchers.IO) {
            compressUriToBoundedJpeg(context, sourceUri, MAX_FALLBACK_DIM, FALLBACK_MAX_BASE64_LEN)
        } ?: return Result.failure(IllegalStateException("फोटो बहुत बड़ी है या प्रोसेस नहीं हो सकी।"))

        val base64 = Base64.encodeToString(compactJpeg, Base64.NO_WRAP)
        val cloudPointer = buildFirestorePhotoUri(cattleId, version)

        return try {
            withTimeout(15_000L) {
                val docRef = db.collection("cattlePhotos").document(cattleId)
                val payload = mapOf(
                    "cattleId" to cattleId,
                    "contentType" to "image/jpeg",
                    "imageBase64" to base64,
                    "publicApproved" to publicApproved,
                    "version" to version,
                    "uploadedBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            // Pre-populate memory cache so the newly uploaded photo renders immediately
            decodeBitmapBytes(compactJpeg)?.let { bmp ->
                memoryCache.put(cloudPointer, bmp)
            }
            Result.success(
                UploadedCowPhoto(
                    photoUrl = cloudPointer,
                    storagePath = "cattlePhotos/$cattleId"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cloud fallback photo upload failed for cattleId=$cattleId", e)
            Result.failure(
                IllegalStateException("फोटो क्लाउड पर सेव नहीं हो सकी। कृपया इंटरनेट कनेक्शन जांचकर पुनः प्रयास करें।", e)
            )
        }
    }

    /**
     * Resolves a `firestore://cattlePhotos/{cattleId}?v=...` photo into a Bitmap using LRU cache.
     */
    suspend fun loadFirestorePhotoBitmap(photoUri: String): Bitmap? {
        memoryCache.get(photoUri)?.let { return it }

        testCloudPhotoResolver?.let { hook ->
            val bmp = hook(photoUri)
            if (bmp != null) memoryCache.put(photoUri, bmp)
            return bmp
        }

        val docId = extractFirestorePhotoDocId(photoUri) ?: return null
        val db = registeredDb ?: return null

        return try {
            val snap = withTimeout(12_000L) {
                db.collection("cattlePhotos").document(docId).get().await()
            }
            val b64 = snap.getString("imageBase64")?.takeIf { it.isNotBlank() } ?: return null
            val bytes = withContext(Dispatchers.Default) {
                Base64.decode(b64, Base64.DEFAULT)
            }
            val bmp = withContext(Dispatchers.Default) {
                decodeBitmapBytes(bytes)
            }
            if (bmp != null) {
                memoryCache.put(photoUri, bmp)
            }
            bmp
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load Firestore cow photo ($photoUri): ${e.message}")
            null
        }
    }

    /**
     * Best-effort sync of `publicApproved` flag onto `cattlePhotos/{cattleId}` when Admin toggles visibility.
     */
    suspend fun syncPhotoVisibility(db: FirebaseFirestore, cattleId: String, publicApproved: Boolean) {
        if (cattleId.isBlank()) return
        try {
            val docRef = db.collection("cattlePhotos").document(cattleId)
            val snap = withTimeout(6_000L) { docRef.get().await() }
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "publicApproved" to publicApproved,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Optional photo visibility sync skipped: ${e.message}")
        }
    }

    /**
     * Best-effort cleanup of cloud photo when a cow record is deleted.
     */
    suspend fun deleteCattlePhoto(db: FirebaseFirestore, cattleId: String, storagePath: String?) {
        if (cattleId.isNotBlank()) {
            runCatching {
                db.collection("cattlePhotos").document(cattleId).delete().await()
            }
        }
        if (!storagePath.isNullOrBlank() && storagePath.startsWith("cattle_photos/")) {
            runCatching {
                FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
            }
        }
    }

    /**
     * Uploads a locally picked Admin product photo to Firebase Storage (`product_photos/{productId}/photo_{ts}.jpg`)
     * and returns its permanent HTTPS download URL. Never returns an ephemeral local `content://` or `file://` URI.
     */
    suspend fun uploadProductPhoto(
        context: Context?,
        productId: String,
        sourceUriString: String
    ): Result<UploadedCowPhoto> {
        val trimmed = sourceUriString.trim()
        if (trimmed.isBlank() || isCloudPhotoUri(trimmed) || trimmed.startsWith("preset:", ignoreCase = true)) {
            return Result.success(UploadedCowPhoto(photoUrl = trimmed, storagePath = ""))
        }

        testProductPhotoUploader?.let { hook ->
            return hook(productId, trimmed)
        }

        if (context == null) {
            return Result.failure(IllegalStateException("उत्पाद की फोटो पढ़ने के लिए ऐप कॉन्टेक्स्ट उपलब्ध नहीं है।"))
        }

        val sourceUri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("अमान्य उत्पाद फोटो पथ।"))

        val storageJpeg = withContext(Dispatchers.IO) {
            compressUriToJpeg(context, sourceUri, MAX_STORAGE_DIM, STORAGE_JPEG_QUALITY)
        } ?: return Result.failure(IllegalStateException("चुनी गई उत्पाद फोटो पढ़ी नहीं जा सकी। कृपया दूसरी फोटो चुनें।"))

        val version = System.currentTimeMillis()
        val safeProductId = productId.trim().ifBlank { "prod_$version" }
        val storagePath = "product_photos/$safeProductId/photo_$version.jpg"

        return try {
            val downloadUrl = withTimeout(15_000L) {
                val ref = FirebaseStorage.getInstance().reference.child(storagePath)
                val metadata = StorageMetadata.Builder()
                    .setContentType("image/jpeg")
                    .setCustomMetadata("productId", safeProductId)
                    .build()
                ref.putBytes(storageJpeg, metadata).await()
                ref.downloadUrl.await().toString()
            }
            Result.success(
                UploadedCowPhoto(
                    photoUrl = downloadUrl,
                    storagePath = storagePath
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Product photo upload to Firebase Storage failed for productId=$safeProductId: ${e.message}")
            Result.failure(
                IllegalStateException(
                    "उत्पाद की फोटो क्लाउड पर अपलोड नहीं हो सकी। कृपया इंटरनेट और Firebase Storage नियम जांचकर पुनः प्रयास करें।",
                    e
                )
            )
        }
    }

    /**
     * Best-effort cleanup of a Firebase Storage product photo when Admin deletes a product.
     */
    suspend fun deleteProductPhoto(photoUrl: String?) {
        val trimmed = photoUrl?.trim().orEmpty()
        if (trimmed.isBlank() || !trimmed.contains("product_photos", ignoreCase = true)) return
        runCatching {
            FirebaseStorage.getInstance().getReferenceFromUrl(trimmed).delete().await()
        }
    }

    internal fun compressUriToJpeg(
        context: Context,
        uri: Uri,
        maxDimension: Int,
        quality: Int
    ): ByteArray? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            var sampleSize = 1
            while ((boundsOpts.outWidth / sampleSize) > maxDimension * 2 ||
                (boundsOpts.outHeight / sampleSize) > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val scaled = scaleBitmapDown(decoded, maxDimension)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "Image compression failed for uri=$uri", e)
            null
        }
    }

    private fun compressUriToBoundedJpeg(
        context: Context,
        uri: Uri,
        initialMaxDim: Int,
        maxBase64Chars: Int
    ): ByteArray? {
        val attempts = listOf(
            initialMaxDim to 72,
            420 to 64,
            320 to 56
        )
        for ((dim, q) in attempts) {
            val bytes = compressUriToJpeg(context, uri, dim, q) ?: continue
            // Base64 expands bytes by ~4/3
            if ((bytes.size * 4 / 3) <= maxBase64Chars) {
                return bytes
            }
        }
        return null
    }

    private fun scaleBitmapDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDim && height <= maxDim) return bitmap
        val ratio = maxDim.toFloat() / maxOf(width, height).toFloat()
        val matrix = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun decodeBitmapBytes(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()
}

package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.model.Cattle
import com.google.firebase.firestore.FieldValue
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedCattleQr(
    val cowDocId: String?,
    val animalCode: String?,
    val rawValue: String
)

object QrAndExportUtils {

    /** Verified real Firebase Hosting domain for project gen-lang-client-0821187064 (app/google-services.json). */
    const val PUBLIC_HOSTING_BASE_URL = "https://gen-lang-client-0821187064.web.app"

    private val SAFE_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{4,128}$")
    private val PUBLIC_ANIMAL_CODE_REGEX = Regex("^[A-Z0-9][A-Z0-9_\\-]{1,39}$")
    private val ANIMAL_CODE_REGEX = Regex("^(?:JBGS|JBG|COW|GAU|CATTLE|G|B|C)-[A-Z0-9\\-]{1,40}$", RegexOption.IGNORE_CASE)

    fun normalizeAnimalCode(raw: String?): String? {
        val cleaned = raw?.trim()?.uppercase(Locale.US) ?: return null
        return cleaned.takeIf { PUBLIC_ANIMAL_CODE_REGEX.matches(it) }
    }

    /**
     * Builds the permanent HTTPS Firebase Hosting Digital Cow ID Card URL for Google Lens / web QR scans.
     * Contains ONLY the clean Animal Code — NEVER exposes Firebase Document IDs or sensitive data.
     */
    fun buildPublicCattleQrUrl(animalCode: String): String {
        val normalized = normalizeAnimalCode(animalCode) ?: return ""
        return "$PUBLIC_HOSTING_BASE_URL/cattle/$normalized"
    }

    /**
     * Resolves the canonical public HTTPS QR URL for any Cattle record (including legacy cows
     * that previously stored `JBGS:CATTLE:<docId>:<code>` in Firestore).
     */
    fun resolvePublicCattleQrUrl(cattle: Cattle): String {
        val urlFromCode = buildPublicCattleQrUrl(cattle.animalCode)
        if (urlFromCode.isNotBlank()) return urlFromCode
        val existing = cattle.qrPayload.trim()
        if (existing.startsWith("$PUBLIC_HOSTING_BASE_URL/cattle/", ignoreCase = true)) {
            return existing
        }
        return buildCattleQrPayload(cattle.id, cattle.animalCode)
    }

    /**
     * Canonical permanent QR payload for newly generated cow QR codes.
     * Uses the real Firebase Hosting HTTPS URL (`https://gen-lang-client-0821187064.web.app/cattle/<ANIMAL_CODE>`)
     * so scanning via Google Lens or any third-party QR scanner opens the Digital Cow ID Card directly.
     * Never exposes Firebase document IDs when a valid Animal Code is present.
     */
    fun buildCattleQrPayload(cowDocId: String, animalCode: String): String {
        val publicUrl = buildPublicCattleQrUrl(animalCode)
        if (publicUrl.isNotBlank()) return publicUrl
        val cleanCode = animalCode.trim()
        if (cleanCode.isNotBlank()) return "JBGS:CATTLE::$cleanCode"
        val cleanId = cowDocId.trim()
        return if (cleanId.isNotBlank()) "JBGS:CATTLE:$cleanId" else ""
    }

    /**
     * Builds the strictly whitelisted public-safe Firestore map for `/publicCattleCards/{animalCode}`.
     * Returns null if the cow is not Admin-approved (`publicApproved != true`) or lacks a valid Animal Code.
     * NEVER includes internal `notes`, Firebase `cattleId`, phone numbers, private addresses, or `createdBy`.
     */
    fun buildPublicCattleCardMap(
        cattle: Cattle,
        resolvedPhotoUrl: String? = cattle.photoUri,
        photoDataUrl: String? = null,
        lastHealthCheckup: String? = null,
        useServerTimestamp: Boolean = true
    ): Map<String, Any>? {
        if (!cattle.publicApproved) return null
        val normalizedCode = normalizeAnimalCode(cattle.animalCode) ?: return null
        val qrUrl = buildPublicCattleQrUrl(normalizedCode)
        val httpsPhoto = resolvedPhotoUrl?.trim()?.takeIf {
            it.startsWith("https://", ignoreCase = true)
        }?.take(2000)
        val safeDataUrl = photoDataUrl?.trim()?.takeIf {
            it.startsWith("data:image/", ignoreCase = true) && it.length <= 260_000
        }

        val map = mutableMapOf<String, Any>(
            "animalCode" to normalizedCode,
            "name" to cattle.name.trim().ifBlank { normalizedCode }.take(80),
            "category" to cattle.category.trim().uppercase(Locale.US).takeIf {
                it in setOf("COW", "BULL", "CALF")
            }.orEmpty().ifBlank { "COW" },
            "breed" to cattle.breed.trim().ifBlank { "देसी" }.take(80),
            "sex" to cattle.sex.trim().uppercase(Locale.US).takeIf {
                it in setOf("FEMALE", "MALE")
            }.orEmpty().ifBlank { "FEMALE" },
            "ageText" to cattle.ageText.trim().ifBlank { "-" }.take(40),
            "birthDate" to cattle.birthDate.trim().take(30),
            "colour" to cattle.colour.trim().ifBlank { "-" }.take(40),
            "weightKg" to cattle.weightKg.coerceIn(0.0, 2000.0),
            "entryDate" to cattle.entryDate.trim().ifBlank { "-" }.take(30),
            "healthStatus" to cattle.healthStatus.trim().uppercase(Locale.US).takeIf {
                it in setOf("HEALTHY", "SICK", "UNDER_TREATMENT", "RECOVERING")
            }.orEmpty().ifBlank { "HEALTHY" },
            "identificationMarks" to cattle.identificationMarks.trim().take(200),
            "goshalaNameHi" to GosalaBrand.NAME_HI,
            "goshalaNameEn" to GosalaBrand.NAME_EN,
            "goshalaAddressHi" to GosalaBrand.ADDRESS_HI,
            "goshalaAddressEn" to GosalaBrand.ADDRESS_EN,
            "qrUrl" to qrUrl,
            "publicApproved" to true,
            "updatedAt" to if (useServerTimestamp) FieldValue.serverTimestamp() else System.currentTimeMillis()
        )
        cattle.vaccinationSummary?.trim()?.takeIf { it.isNotBlank() }?.let {
            map["vaccinationSummary"] = it.take(500)
        }
        lastHealthCheckup?.trim()?.takeIf { it.isNotBlank() }?.let {
            map["lastHealthCheckup"] = it.take(120)
        }
        if (!httpsPhoto.isNullOrBlank()) {
            map["photoUrl"] = httpsPhoto
        }
        if (!safeDataUrl.isNullOrBlank()) {
            map["photoDataUrl"] = safeDataUrl
        }
        return map
    }

    /**
     * Parses scanned QR text or manual Cow ID input into structured Firebase Cow ID / Animal Code.
     * Supports:
     * - New Firebase Hosting HTTPS URL: https://gen-lang-client-0821187064.web.app/cattle/JBGS-G-357
     * - Legacy Canonical: JBGS:CATTLE:<firebaseDocId>:<animalCode>
     * - Legacy 1-part canonical: JBGS:CATTLE:<firebaseDocId> or JBGS:CATTLE:<animalCode>
     * - Legacy prefixes: JBGS-CATTLE:<id>, CATTLE:<id>, COW:<id>, JBGS|CATTLE|<id>|<code>
     * - Legacy JSON: {"id":"...","animalCode":"..."} / {"cowId":"...","code":"..."}
     * - Deep-link / URL: jbgs://cattle/<id>, https://.../cattle/<id>, ?cowId=<id>&animalCode=<code>
     * - Direct Animal Code (e.g. JBGS-G-101, JBG-1234, COW-101)
     * - Direct Firebase document ID
     */
    fun parseCattleQrPayload(raw: String): ParsedCattleQr? {
        val trimmed = raw.replace("\uFEFF", "").trim()
        if (trimmed.isBlank()) return null

        // 1. Canonical legacy format: JBGS:CATTLE:<docId>:<animalCode> or JBGS:CATTLE:<docIdOrCode>
        if (trimmed.startsWith("JBGS:CATTLE:", ignoreCase = true)) {
            val rest = trimmed.substring(12).trim()
            if (rest.isBlank()) return null
            val parts = rest.split(":")
            if (parts.size >= 2) {
                val rawDoc = parts[0].trim()
                val rawCode = parts[1].trim()
                val docId = rawDoc.takeIf { SAFE_ID_REGEX.matches(it) }
                val code = rawCode.takeIf { it.isNotBlank() && it.length <= 60 && !it.contains(" ") }
                if (docId == null && code == null) return null
                return ParsedCattleQr(cowDocId = docId, animalCode = code, rawValue = trimmed)
            } else {
                val single = parts[0].trim()
                if (!SAFE_ID_REGEX.matches(single)) return null
                val normalizedCode = if (ANIMAL_CODE_REGEX.matches(single)) {
                    single.uppercase(Locale.US)
                } else {
                    single
                }
                return ParsedCattleQr(cowDocId = single, animalCode = normalizedCode, rawValue = trimmed)
            }
        }

        // 2. Legacy pipe-delimited format: JBGS|CATTLE|<docId>|<animalCode>
        if (trimmed.startsWith("JBGS|CATTLE|", ignoreCase = true)) {
            val rest = trimmed.substring(12).trim()
            if (rest.isBlank()) return null
            val parts = rest.split("|")
            val docId = parts.getOrNull(0)?.trim()?.takeIf { SAFE_ID_REGEX.matches(it) }
            val code = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 60 && !it.contains(" ") }
            if (docId == null && code == null) return null
            return ParsedCattleQr(
                cowDocId = docId,
                animalCode = code ?: docId,
                rawValue = trimmed
            )
        }

        // 3. Legacy prefixed formats: JBGS-CATTLE:<id>, CATTLE:<id>, COW:<id>, ANIMAL_ID:<id>
        val legacyPrefixRegex = Regex("^(?:JBGS[\\-_]CATTLE|CATTLE_ID|CATTLE|COW_ID|COW|ANIMAL_ID)[:=/](.+)$", RegexOption.IGNORE_CASE)
        legacyPrefixRegex.matchEntire(trimmed)?.let { match ->
            val rest = match.groupValues[1].trim()
            if (rest.isBlank()) return null
            val parts = rest.split(":", "|", ",")
            if (parts.size >= 2) {
                val docId = parts[0].trim().takeIf { SAFE_ID_REGEX.matches(it) }
                val code = parts[1].trim().takeIf { it.isNotBlank() && it.length <= 60 && !it.contains(" ") }
                if (docId != null || code != null) {
                    return ParsedCattleQr(cowDocId = docId, animalCode = code, rawValue = trimmed)
                }
            } else if (SAFE_ID_REGEX.matches(rest)) {
                val code = if (ANIMAL_CODE_REGEX.matches(rest)) rest.uppercase(Locale.US) else rest
                return ParsedCattleQr(cowDocId = rest, animalCode = code, rawValue = trimmed)
            }
            return null
        }

        // 4. Legacy JSON QR format: {"id":"...","animalCode":"..."}
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val parsedJson = runCatching { JSONObject(trimmed) }.getOrNull()
            if (parsedJson != null) {
                val docId = listOf("docId", "documentId", "cattleId", "cowId", "id")
                    .firstNotNullOfOrNull { key ->
                        parsedJson.optString(key, "").trim().takeIf { SAFE_ID_REGEX.matches(it) }
                    }
                val code = listOf("animalCode", "animalId", "cowCode", "code")
                    .firstNotNullOfOrNull { key ->
                        parsedJson.optString(key, "").trim().takeIf { it.isNotBlank() && it.length <= 60 && !it.contains(" ") }
                    }
                if (docId != null || code != null) {
                    return ParsedCattleQr(cowDocId = docId, animalCode = code, rawValue = trimmed)
                }
            }
            return null
        }

        // 5. Official Firebase Hosting HTTPS URL or deep-link (jbgs://cattle/<id>, https://gen-lang-client-0821187064.web.app/cattle/<animalCode>)
        if (trimmed.contains("://")) {
            val scheme = trimmed.substringBefore("://").lowercase(Locale.US)
            if (scheme !in setOf("https", "http", "jbgs")) return null

            val withoutScheme = trimmed.substringAfter("://")
            val hostAndPath = withoutScheme.substringBefore("?").substringBefore("#")
            val queryPart = trimmed.substringAfter("?", "").substringBefore("#")

            // Strict path check:
            // - jbgs://cattle/<token> or jbgs://cow/<token>
            // - https://<host>/cattle/<token> or https://<host>/cow/<token>
            val pathToken = if (scheme == "jbgs") {
                Regex("^(?:cattle|cow)/([^/]+)/?$", RegexOption.IGNORE_CASE)
                    .matchEntire(hostAndPath)
                    ?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { SAFE_ID_REGEX.matches(it) }
            } else {
                val pathPart = if (hostAndPath.contains("/")) "/" + hostAndPath.substringAfter("/") else ""
                Regex("^/(?:cattle|cow)/([^/]+)/?$", RegexOption.IGNORE_CASE)
                    .matchEntire(pathPart)
                    ?.groupValues?.getOrNull(1)?.trim()
                    ?.takeIf { SAFE_ID_REGEX.matches(it) }
            }

            val queryCowId = if (queryPart.isNotBlank()) {
                Regex("(?:^|&)(?:cowId|cattleId|docId|documentId)=([A-Za-z0-9_\\-]{4,128})(?:&|$)", RegexOption.IGNORE_CASE)
                    .find(queryPart)?.groupValues?.getOrNull(1)
            } else null

            val queryCode = if (queryPart.isNotBlank()) {
                Regex("(?:^|&)(?:animalCode|animalId|code|id)=([A-Za-z0-9_\\-]{2,60})(?:&|$)", RegexOption.IGNORE_CASE)
                    .find(queryPart)?.groupValues?.getOrNull(1)
            } else null

            if (pathToken == null && queryCowId == null && queryCode == null) return null

            if (queryCowId != null && queryCode != null) {
                return ParsedCattleQr(
                    cowDocId = queryCowId,
                    animalCode = queryCode.uppercase(Locale.US),
                    rawValue = trimmed
                )
            }

            val single = pathToken ?: queryCowId ?: queryCode!!
            // For our official Firebase Hosting URLs (/cattle/JBGS-G-357), prioritize animalCode cleanly
            if (ANIMAL_CODE_REGEX.matches(single)) {
                return ParsedCattleQr(
                    cowDocId = null,
                    animalCode = single.uppercase(Locale.US),
                    rawValue = trimmed
                )
            }
            return ParsedCattleQr(
                cowDocId = single.takeIf { SAFE_ID_REGEX.matches(it) },
                animalCode = normalizeAnimalCode(single) ?: single,
                rawValue = trimmed
            )
        }

        // 6. Direct Animal Code (e.g. JBGS-G-101, JBG-1234, COW-101)
        if (ANIMAL_CODE_REGEX.matches(trimmed)) {
            return ParsedCattleQr(cowDocId = null, animalCode = trimmed.uppercase(Locale.US), rawValue = trimmed)
        }

        // 7. Direct Firebase Unique Cow ID (no spaces or special URI/invalid chars)
        if (SAFE_ID_REGEX.matches(trimmed)) {
            return ParsedCattleQr(cowDocId = trimmed, animalCode = trimmed, rawValue = trimmed)
        }

        return null
    }

    /**
     * Decodes a QR code from a CameraX Y-plane grayscale byte array using ZXing.
     * Uses multiple binarizers (Hybrid, GlobalHistogram, Inverted) and handles 90°/270° rotated
     * Android camera frames for reliable decoding on physical devices.
     */
    fun decodeQrFromYuvByteArray(
        yBytes: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int = 0
    ): String? {
        if (width <= 0 || height <= 0 || yBytes.size < width * height) return null
        val reader = MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                    DecodeHintType.TRY_HARDER to true,
                    DecodeHintType.CHARACTER_SET to "UTF-8"
                )
            )
        }
        return try {
            val directSource = PlanarYUVLuminanceSource(
                yBytes, width, height, 0, 0, width, height, false
            )
            decodeFromLuminanceSource(reader, directSource)?.let { return it }

            val normalizedRot = ((rotationDegrees % 360) + 360) % 360
            if (normalizedRot == 90 || normalizedRot == 270 || width != height) {
                val rotatedBytes = rotateYPlane90Cw(yBytes, width, height)
                val rotatedSource = PlanarYUVLuminanceSource(
                    rotatedBytes, height, width, 0, 0, height, width, false
                )
                decodeFromLuminanceSource(reader, rotatedSource)?.let { return it }
            }
            null
        } finally {
            reader.reset()
        }
    }

    private fun decodeFromLuminanceSource(
        reader: MultiFormatReader,
        source: PlanarYUVLuminanceSource
    ): String? {
        val attempts = listOf(
            { BinaryBitmap(HybridBinarizer(source)) },
            { BinaryBitmap(GlobalHistogramBinarizer(source)) },
            { BinaryBitmap(HybridBinarizer(source.invert())) }
        )
        for (bitmapProvider in attempts) {
            try {
                val text = reader.decodeWithState(bitmapProvider())?.text?.trim()
                if (!text.isNullOrBlank()) return text
            } catch (_: Exception) {
                // Try next binarizer
            } finally {
                reader.reset()
            }
        }
        return null
    }

    private fun rotateYPlane90Cw(src: ByteArray, width: Int, height: Int): ByteArray {
        val dst = ByteArray(width * height)
        for (y in 0 until height) {
            val rowOffset = y * width
            val dstCol = height - 1 - y
            for (x in 0 until width) {
                dst[x * height + dstCol] = src[rowOffset + x]
            }
        }
        return dst
    }

    fun generateQrBitmap(payload: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
            val fgColor = Color.parseColor("#124B32")
            val pixels = IntArray(sizePx * sizePx)
            for (y in 0 until sizePx) {
                val offset = y * sizePx
                for (x in 0 until sizePx) {
                    pixels[offset + x] = if (matrix[x, y]) fgColor else Color.WHITE
                }
            }
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a professional PVC-style printable Digital Cow ID Card PDF saved to `cacheDir/reports/`.
     * Displays Goshala Logo, Name & Address, Cow Photo (or official default cow placeholder),
     * all 15 cow details, scannable QR Code (Firebase Hosting URL), and Animal ID.
     */
    fun exportDigitalCowIdCardPdf(
        context: Context,
        cattle: Cattle,
        language: AppLanguage,
        lastHealthCheckup: String? = null,
        cowPhotoBitmap: Bitmap? = null
    ): Result<File> {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeCode = (normalizeAnimalCode(cattle.animalCode) ?: cattle.id.ifBlank { "COW" })
                .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val outFile = File(reportsDir, "Digital_Cow_ID_${safeCode}_${System.currentTimeMillis()}.pdf")

            val document = PdfDocument()
            try {
                val pageWidth = 595
                val pageHeight = 842
                val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
                drawDigitalCowIdCardOnCanvas(
                    canvas = page.canvas,
                    pageWidth = pageWidth,
                    pageHeight = pageHeight,
                    context = context,
                    cattle = cattle,
                    language = language,
                    lastHealthCheckup = lastHealthCheckup,
                    cowPhotoBitmap = cowPhotoBitmap
                )
                document.finishPage(page)
                FileOutputStream(outFile).use { fos ->
                    document.writeTo(fos)
                }
            } finally {
                runCatching { document.close() }
            }
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun drawDigitalCowIdCardOnCanvas(
        canvas: Canvas,
        pageWidth: Int,
        pageHeight: Int,
        context: Context,
        cattle: Cattle,
        language: AppLanguage,
        lastHealthCheckup: String? = null,
        cowPhotoBitmap: Bitmap? = null
    ) {
        val isEn = language == AppLanguage.ENGLISH
        val qrUrl = resolvePublicCattleQrUrl(cattle)
        val qrBitmap = generateQrBitmap(qrUrl, 320)
        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_official_goshala_logo)
        }.getOrNull()
        val displayPhoto = cowPhotoBitmap ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.img_default_cow_1795363316557)
        }.getOrNull()

        // Outer cream background
        val bgPaint = Paint().apply { color = Color.parseColor("#F6F1E6") }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bgPaint)

        // Main PVC Card Container
        val cardRect = RectF(28f, 28f, pageWidth - 28f, pageHeight - 28f)
        val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val goldBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CFA349")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 20f, 20f, cardBgPaint)

        // Header Banner (Forest Green)
        val headerRect = RectF(28f, 28f, pageWidth - 28f, 136f)
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#124B32") }
        canvas.drawRoundRect(headerRect, 20f, 20f, headerPaint)
        canvas.drawRect(28f, 110f, pageWidth - 28f, 136f, headerPaint)
        val goldBarPaint = Paint().apply { color = Color.parseColor("#CFA349") }
        canvas.drawRect(28f, 136f, pageWidth - 28f, 141f, goldBarPaint)

        // Goshala Logo
        val logoRect = RectF(44f, 44f, 116f, 116f)
        if (logoBitmap != null) {
            canvas.drawBitmap(logoBitmap, null, logoRect, null)
            canvas.drawOval(logoRect, goldBorderPaint)
        }

        // Header Titles
        val orgTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val orgSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4E4BD")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val orgAddrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6F2EC")
            textSize = 9.5f
        }
        canvas.drawText(if (isEn) GosalaBrand.NAME_EN else GosalaBrand.NAME_HI, 130f, 68f, orgTitlePaint)
        canvas.drawText(
            if (isEn) "OFFICIAL DIGITAL COW ID CARD" else "आधिकारिक डिजिटल गौ पहचान पत्र • DIGITAL COW ID CARD",
            130f,
            88f,
            orgSubPaint
        )
        val addrText = if (isEn) GosalaBrand.ADDRESS_EN else GosalaBrand.ADDRESS_HI
        canvas.drawText(addrText.take(66), 130f, 108f, orgAddrPaint)

        // Animal ID Badge in Header Right
        val badgeRect = RectF(pageWidth - 168f, 46f, pageWidth - 42f, 94f)
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1D6B47") }
        canvas.drawRoundRect(badgeRect, 10f, 10f, badgePaint)
        canvas.drawRoundRect(badgeRect, 10f, 10f, goldBorderPaint)
        val badgeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F4E4BD")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val badgeCodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(if (isEn) "ANIMAL ID" else "पशु ID", badgeRect.left + 10f, badgeRect.top + 17f, badgeLabelPaint)
        canvas.drawText(cattle.animalCode.ifBlank { "-" }.take(14), badgeRect.left + 10f, badgeRect.top + 36f, badgeCodePaint)

        // Photo Frame (Left) + QR Box (Right)
        val photoRect = RectF(46f, 160f, 340f, 355f)
        val photoBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#EFE7D7") }
        canvas.drawRoundRect(photoRect, 14f, 14f, photoBgPaint)
        if (displayPhoto != null) {
            val srcW = displayPhoto.width
            val srcH = displayPhoto.height
            val targetRatio = photoRect.width() / photoRect.height()
            val srcRatio = srcW.toFloat() / srcH.coerceAtLeast(1).toFloat()
            val srcRect = if (srcRatio > targetRatio) {
                val cropW = (srcH * targetRatio).toInt().coerceAtMost(srcW)
                val left = (srcW - cropW) / 2
                Rect(left, 0, left + cropW, srcH)
            } else {
                val cropH = (srcW / targetRatio).toInt().coerceAtMost(srcH)
                val top = (srcH - cropH) / 2
                Rect(0, top, srcW, top + cropH)
            }
            canvas.drawBitmap(displayPhoto, srcRect, photoRect, null)
        }
        canvas.drawRoundRect(photoRect, 14f, 14f, goldBorderPaint)

        // QR Card Box (Top Right)
        val qrBoxRect = RectF(358f, 160f, pageWidth - 46f, 355f)
        val qrBoxFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAF8F3") }
        val subtleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DFD7C6")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRoundRect(qrBoxRect, 14f, 14f, qrBoxFill)
        canvas.drawRoundRect(qrBoxRect, 14f, 14f, subtleBorder)
        if (qrBitmap != null) {
            val qrDrawRect = RectF(384f, 174f, 524f, 314f)
            canvas.drawBitmap(qrBitmap, null, qrDrawRect, null)
        }
        val qrCaptionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#124B32")
            textSize = 10.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val qrHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E5D55")
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        val qrCenterX = qrBoxRect.centerX()
        canvas.drawText(cattle.animalCode.ifBlank { "-" }, qrCenterX, 332f, qrCaptionPaint)
        canvas.drawText(
            if (isEn) "Scan with Google Lens" else "Google Lens से स्कैन करें",
            qrCenterX,
            346f,
            qrHintPaint
        )

        // Cow Name Banner
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#124B32")
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val statusColorHex = when (cattle.healthStatus) {
            "HEALTHY" -> "#166534"
            "SICK" -> "#991B1B"
            else -> "#9A3412"
        }
        val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(statusColorHex)
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(cattle.name.ifBlank { "-" }.take(34), 46f, 388f, namePaint)
        val healthLabel = when (cattle.healthStatus) {
            "HEALTHY" -> if (isEn) "HEALTHY" else "स्वस्थ (Healthy)"
            "SICK" -> if (isEn) "SICK" else "अस्वस्थ (Sick)"
            "UNDER_TREATMENT" -> if (isEn) "UNDER TREATMENT" else "उपचाराधीन"
            "RECOVERING" -> if (isEn) "RECOVERING" else "सुधार पर"
            else -> cattle.healthStatus
        }
        canvas.drawText(healthLabel, pageWidth - 46f, 386f, statusPaint)
        canvas.drawLine(46f, 398f, pageWidth - 46f, 398f, subtleBorder)

        // Details Table (All 15 fields from real Firebase data)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E5D55")
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A241F")
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D96B18")
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val notRecorded = if (isEn) "Not recorded" else "रिकॉर्ड उपलब्ध नहीं"
        val categoryLabel = when (cattle.category) {
            "COW" -> if (isEn) "Cow" else "गाय (Cow)"
            "BULL" -> if (isEn) "Bull" else "नंदी / बैल (Bull)"
            "CALF" -> if (isEn) "Calf" else "बछड़ा / बछिया (Calf)"
            else -> cattle.category
        }
        val sexLabel = when (cattle.sex) {
            "FEMALE" -> if (isEn) "Female" else "मादा (Female)"
            "MALE" -> if (isEn) "Male" else "नर (Male)"
            else -> cattle.sex
        }
        val ageDobText = buildString {
            if (cattle.ageText.isNotBlank()) append(cattle.ageText)
            if (cattle.birthDate.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(cattle.birthDate)
            }
        }.ifBlank { notRecorded }

        val gridItems = listOf(
            (if (isEn) "Unique Animal ID" else "पशु पहचान क्रमांक") to cattle.animalCode.ifBlank { notRecorded },
            (if (isEn) "Animal Type" else "पशु प्रकार") to categoryLabel,
            (if (isEn) "Breed" else "नस्ल") to cattle.breed.ifBlank { notRecorded },
            (if (isEn) "Age / Birth Date" else "आयु / जन्म तिथि") to ageDobText,
            (if (isEn) "Colour" else "रंग") to cattle.colour.ifBlank { notRecorded },
            (if (isEn) "Sex" else "लिंग") to sexLabel,
            (if (isEn) "Registration Date" else "पंजीकरण तिथि") to cattle.entryDate.ifBlank { notRecorded },
            (if (isEn) "Current Health Status" else "वर्तमान स्वास्थ्य स्थिति") to healthLabel
        )

        var y = 418f
        canvas.drawText(
            if (isEn) "ANIMAL IDENTITY DETAILS" else "गौवंश पहचान विवरण",
            46f,
            y,
            sectionPaint
        )
        y += 10f
        val colW = (pageWidth - 92f - 12f) / 2f
        gridItems.chunked(2).forEach { pair ->
            pair.forEachIndexed { idx, (lbl, value) ->
                val left = 46f + idx * (colW + 12f)
                val cellRect = RectF(left, y, left + colW, y + 40f)
                canvas.drawRoundRect(cellRect, 8f, 8f, qrBoxFill)
                canvas.drawRoundRect(cellRect, 8f, 8f, subtleBorder)
                canvas.drawText(lbl, left + 10f, y + 14f, labelPaint)
                canvas.drawText(value.take(30), left + 10f, y + 31f, valuePaint)
            }
            y += 48f
        }

        // Full-width Health, Vaccination, Identification Marks & Goshala Address rows
        y += 4f
        canvas.drawText(
            if (isEn) "HEALTH, VACCINATION & IDENTIFICATION" else "स्वास्थ्य, टीकाकरण एवं विशेष पहचान",
            46f,
            y,
            sectionPaint
        )
        y += 10f

        val fullWidthRows = listOf(
            (if (isEn) "Vaccination Record" else "टीकाकरण रिकॉर्ड") to
                (cattle.vaccinationSummary?.takeIf { it.isNotBlank() } ?: notRecorded),
            (if (isEn) "Last Health Checkup" else "अंतिम स्वास्थ्य जांच") to
                (lastHealthCheckup?.takeIf { it.isNotBlank() } ?: notRecorded),
            (if (isEn) "Special Care & Identification Marks" else "विशेष पहचान चिन्ह एवं देखभाल") to
                cattle.identificationMarks.ifBlank { notRecorded },
            (if (isEn) "Goshala Name & Address" else "गौशाला नाम एवं पता") to
                "${if (isEn) GosalaBrand.NAME_EN else GosalaBrand.NAME_HI}, ${if (isEn) GosalaBrand.ADDRESS_EN else GosalaBrand.ADDRESS_HI}"
        )
        fullWidthRows.forEach { (lbl, value) ->
            val cellRect = RectF(46f, y, pageWidth - 46f, y + 38f)
            canvas.drawRoundRect(cellRect, 8f, 8f, qrBoxFill)
            canvas.drawRoundRect(cellRect, 8f, 8f, subtleBorder)
            canvas.drawText(lbl, 56f, y + 14f, labelPaint)
            canvas.drawText(value.take(78), 56f, y + 30f, valuePaint)
            y += 44f
        }

        // Footer Bar
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4E5D55")
            textSize = 8.5f
        }
        canvas.drawText("Web ID Card: $qrUrl", 46f, pageHeight - 44f, footerPaint)

        // Outer Gold Card Border
        canvas.drawRoundRect(cardRect, 20f, 20f, goldBorderPaint)
    }

    fun buildDigitalCowWhatsAppText(cattle: Cattle, language: AppLanguage): String {
        val qrUrl = resolvePublicCattleQrUrl(cattle)
        return if (language == AppLanguage.ENGLISH) {
            buildString {
                appendLine("${GosalaBrand.NAME_EN} — Digital Cow ID Card")
                appendLine("Name: ${cattle.name} (${cattle.animalCode})")
                appendLine("Breed: ${cattle.breed.ifBlank { "-" }} | Health: ${cattle.healthStatus}")
                append("View Digital ID Card: $qrUrl")
            }
        } else {
            buildString {
                appendLine("${GosalaBrand.NAME_HI} — डिजिटल गौ पहचान पत्र")
                appendLine("नाम: ${cattle.name} (${cattle.animalCode})")
                appendLine("नस्ल: ${cattle.breed.ifBlank { "-" }} | स्वास्थ्य: ${cattle.healthStatus}")
                append("डिजिटल पहचान पत्र देखें: $qrUrl")
            }
        }
    }

    fun shareDigitalCowCardOnWhatsApp(context: Context, cattle: Cattle, language: AppLanguage) {
        val message = buildDigitalCowWhatsAppText(cattle, language)
        val encoded = URLEncoder.encode(message, "UTF-8")
        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/?text=$encoded"))
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(
                Intent.createChooser(
                    fallback,
                    str(language, "डिजिटल गौ कार्ड शेयर करें", "Share Digital Cow ID Card")
                )
            )
        }
    }

    fun openDigitalCowCardInBrowser(context: Context, cattle: Cattle) {
        val url = resolvePublicCattleQrUrl(cattle)
        if (!url.startsWith("https://", ignoreCase = true)) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        runCatching { context.startActivity(intent) }
    }

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun nowReadable(): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date())

    fun exportPdfReport(
        context: Context,
        filePrefix: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<File> {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeName = filePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val outFile = File(reportsDir, "${safeName}_${System.currentTimeMillis()}.pdf")

            val document = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 36f

            val titlePaint = Paint().apply {
                color = Color.parseColor("#124B32")
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#26352B")
                textSize = 11f
            }
            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#124B32")
            }
            val headerTextPaint = Paint().apply {
                color = Color.WHITE
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowPaint = Paint().apply {
                color = Color.parseColor("#26352B")
                textSize = 10f
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#D5AD55")
                strokeWidth = 1f
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            var canvas: Canvas = page.canvas

            fun drawHeader(c: Canvas) {
                c.drawText(GosalaBrand.NAME_HI, margin, 48f, titlePaint)
                c.drawText(title, margin, 70f, subPaint)
                c.drawText("$subtitle | ${nowReadable()}", margin, 86f, subPaint)
                c.drawLine(margin, 96f, pageWidth - margin, 96f, linePaint)

                c.drawRect(margin, 108f, pageWidth - margin, 130f, headerBgPaint)
                val colWidth = (pageWidth - 2 * margin) / headers.size.coerceAtLeast(1)
                headers.forEachIndexed { idx, h ->
                    c.drawText(h.take(18), margin + 6f + idx * colWidth, 123f, headerTextPaint)
                }
            }

            drawHeader(canvas)
            var y = 148f
            val colWidth = (pageWidth - 2 * margin) / headers.size.coerceAtLeast(1)

            if (rows.isEmpty()) {
                canvas.drawText("कोई रिकॉर्ड उपलब्ध नहीं है / No records available.", margin, y, rowPaint)
            } else {
                for (row in rows) {
                    if (y > pageHeight - 50f) {
                        document.finishPage(page)
                        pageNumber++
                        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                        canvas = page.canvas
                        drawHeader(canvas)
                        y = 148f
                    }
                    row.forEachIndexed { idx, cell ->
                        canvas.drawText(cell.take(20), margin + 6f + idx * colWidth, y, rowPaint)
                    }
                    canvas.drawLine(margin, y + 6f, pageWidth - margin, y + 6f, linePaint)
                    y += 22f
                }
            }

            document.finishPage(page)
            FileOutputStream(outFile).use { fos ->
                document.writeTo(fos)
            }
            document.close()
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun exportExcelCsvReport(
        context: Context,
        filePrefix: String,
        headers: List<String>,
        rows: List<List<String>>
    ): Result<File> {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safeName = filePrefix.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val outFile = File(reportsDir, "${safeName}_${System.currentTimeMillis()}.csv")

            fun escapeCsv(value: String): String {
                val clean = value.replace("\"", "\"\"")
                return "\"$clean\""
            }

            val sb = StringBuilder()
            sb.append('\uFEFF')
            sb.append(headers.joinToString(",") { escapeCsv(it) }).append("\r\n")
            for (row in rows) {
                sb.append(row.joinToString(",") { escapeCsv(it) }).append("\r\n")
            }

            outFile.writeText(sb.toString(), Charsets.UTF_8)
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

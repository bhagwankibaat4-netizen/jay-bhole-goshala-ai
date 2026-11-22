package com.example.core

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParsedCattleQr(
    val cowDocId: String?,
    val animalCode: String?,
    val rawValue: String
)

object QrAndExportUtils {

    private val SAFE_ID_REGEX = Regex("^[A-Za-z0-9_\\-]{4,128}$")
    private val ANIMAL_CODE_REGEX = Regex("^(?:JBGS|JBG|COW|GAU|CATTLE|G|B|C)-[A-Z0-9\\-]{1,40}$", RegexOption.IGNORE_CASE)

    /**
     * Canonical permanent QR payload linked to the cow's Firebase document Unique ID
     * (and human-readable animalCode as secondary fallback).
     */
    fun buildCattleQrPayload(cowDocId: String, animalCode: String): String {
        val cleanId = cowDocId.trim()
        val cleanCode = animalCode.trim()
        return when {
            cleanId.isNotBlank() && cleanCode.isNotBlank() -> "JBGS:CATTLE:$cleanId:$cleanCode"
            cleanId.isNotBlank() -> "JBGS:CATTLE:$cleanId"
            cleanCode.isNotBlank() -> "JBGS:CATTLE::$cleanCode"
            else -> ""
        }
    }

    /**
     * Parses scanned QR text or manual Cow ID input into structured Firebase Cow ID / Animal Code.
     * Supports:
     * - Canonical: JBGS:CATTLE:<firebaseDocId>:<animalCode>
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

        // 1. Canonical format: JBGS:CATTLE:<docId>:<animalCode> or JBGS:CATTLE:<docIdOrCode>
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
                // Populate both cowDocId and animalCode so legacy single-token QR codes
                // resolve cleanly whether the token was a Firestore docId OR an animalCode.
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

        // 5. Deep-link or URL format (jbgs://cattle/<id> or ?cowId=<id>&animalCode=<code>)
        if (trimmed.contains("://")) {
            val queryCowId = Regex("[?&](?:cowId|cattleId|docId|documentId|id)=([A-Za-z0-9_\\-]{4,128})", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
            val queryCode = Regex("[?&](?:animalCode|animalId|code)=([A-Za-z0-9_\\-]{2,60})", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
            val pathCowId = Regex("/(?:cattle|cow)/([A-Za-z0-9_\\-]{4,128})", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
            val extractedId = queryCowId ?: pathCowId
            if (extractedId == null && queryCode == null) return null
            if (extractedId != null && queryCode != null) {
                return ParsedCattleQr(
                    cowDocId = extractedId,
                    animalCode = queryCode.uppercase(Locale.US),
                    rawValue = trimmed
                )
            }
            val single = extractedId ?: queryCode!!
            return if (ANIMAL_CODE_REGEX.matches(single)) {
                ParsedCattleQr(cowDocId = null, animalCode = single.uppercase(Locale.US), rawValue = trimmed)
            } else {
                ParsedCattleQr(cowDocId = single, animalCode = single, rawValue = trimmed)
            }
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

            // Retry on 90-degree clockwise rotated Y-plane for portrait Android CameraX frames
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
            // UTF-8 BOM so Microsoft Excel renders Hindi Devanagari cleanly
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

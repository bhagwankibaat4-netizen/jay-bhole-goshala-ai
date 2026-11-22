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
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
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
    private val ANIMAL_CODE_REGEX = Regex("^JBGS-[A-Z0-9\\-]{2,40}$", RegexOption.IGNORE_CASE)

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
     * - JBGS:CATTLE:<firebaseDocId>:<animalCode>
     * - JBGS:CATTLE:<firebaseDocId>
     * - jbgs://cattle/<firebaseDocId> or URLs containing cattle/<id> or cowId=<id>
     * - Direct animalCode (e.g. JBGS-G-101)
     * - Direct Firebase document ID
     */
    fun parseCattleQrPayload(raw: String): ParsedCattleQr? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // 1. Canonical format: JBGS:CATTLE:<docId>:<animalCode> or JBGS:CATTLE:<docId>
        if (trimmed.startsWith("JBGS:CATTLE:", ignoreCase = true)) {
            val rest = trimmed.substringAfter("JBGS:CATTLE:", "")
            if (rest.isBlank()) return null
            val parts = rest.split(":")
            val docId = parts.getOrNull(0)?.trim()?.takeIf { SAFE_ID_REGEX.matches(it) }
            val code = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() && it.length <= 60 }
            if (docId == null && code == null) return null
            return ParsedCattleQr(cowDocId = docId, animalCode = code, rawValue = trimmed)
        }

        // 2. Deep-link or URL format (jbgs://cattle/<id> or ?cowId=<id>)
        if (trimmed.contains("://")) {
            val queryCowId = Regex("[?&](?:cowId|cattleId|id)=([A-Za-z0-9_\\-]{4,128})", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
            val pathCowId = Regex("/cattle/([A-Za-z0-9_\\-]{4,128})", RegexOption.IGNORE_CASE)
                .find(trimmed)?.groupValues?.getOrNull(1)
            val extracted = queryCowId ?: pathCowId ?: return null
            return if (ANIMAL_CODE_REGEX.matches(extracted)) {
                ParsedCattleQr(cowDocId = null, animalCode = extracted.uppercase(Locale.US), rawValue = trimmed)
            } else {
                ParsedCattleQr(cowDocId = extracted, animalCode = null, rawValue = trimmed)
            }
        }

        // 3. Direct Animal Code (e.g. JBGS-G-101)
        if (ANIMAL_CODE_REGEX.matches(trimmed)) {
            return ParsedCattleQr(cowDocId = null, animalCode = trimmed.uppercase(Locale.US), rawValue = trimmed)
        }

        // 4. Direct Firebase Unique Cow ID (no spaces or special URI/invalid chars)
        if (SAFE_ID_REGEX.matches(trimmed)) {
            return ParsedCattleQr(cowDocId = trimmed, animalCode = trimmed, rawValue = trimmed)
        }

        return null
    }

    /**
     * Decodes a QR code from a CameraX Y-plane grayscale byte array using ZXing.
     */
    fun decodeQrFromYuvByteArray(yBytes: ByteArray, width: Int, height: Int): String? {
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
        val source = PlanarYUVLuminanceSource(
            yBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))?.text?.trim()
        } catch (_: Exception) {
            try {
                reader.decodeWithState(BinaryBitmap(HybridBinarizer(source.invert())))?.text?.trim()
            } catch (_: Exception) {
                null
            }
        } finally {
            reader.reset()
        }
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

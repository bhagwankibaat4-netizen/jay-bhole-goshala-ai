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
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object QrAndExportUtils {

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

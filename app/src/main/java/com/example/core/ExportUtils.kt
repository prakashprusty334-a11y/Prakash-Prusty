package com.example.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ExportUtils {

    fun saveBitmapToGallery(context: Context, bitmap: Bitmap, fileNamePrefix: String): Result<Uri> {
        return try {
            val filename = "${fileNamePrefix}_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CerevixAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Result.failure(IllegalStateException("Could not create MediaStore entry"))

            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return Result.failure(IllegalStateException("Could not open output stream"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "cerevix_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $title"))
    }

    fun exportTextFileAndShare(context: Context, filename: String, mimeType: String, content: String) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = File(dir, safeName)
        file.writeText(content)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, filename)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export $filename"))
    }

    fun exportDocumentToPdfAndShare(
        context: Context,
        title: String,
        subtitle: String,
        sections: List<Pair<String, List<String>>>
    ): Result<File> {
        return try {
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 44f
            val contentWidth = pageWidth - margin * 2

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0A101F")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#2563EB")
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val headingPaint = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 11f
            }
            val footerPaint = Paint().apply {
                color = Color.parseColor("#64748B")
                textSize = 9f
            }

            var pageNum = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = pdf.startPage(pageInfo)
            var canvas: Canvas = page.canvas
            var y = 56f

            fun startNewPage() {
                canvas.drawText("CEREVIX AI • One Platform. Infinite Intelligence. • Page $pageNum", margin, pageHeight - 24f, footerPaint)
                pdf.finishPage(page)
                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = pdf.startPage(pageInfo)
                canvas = page.canvas
                y = 56f
            }

            fun drawWrappedText(text: String, paint: Paint, lineHeight: Float) {
                val words = text.split(Regex("\\s+"))
                var currentLine = StringBuilder()
                for (word in words) {
                    val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(candidate) > contentWidth) {
                        if (y > pageHeight - 60f) startNewPage()
                        canvas.drawText(currentLine.toString(), margin, y, paint)
                        y += lineHeight
                        currentLine = StringBuilder(word)
                    } else {
                        currentLine = StringBuilder(candidate)
                    }
                }
                if (currentLine.isNotEmpty()) {
                    if (y > pageHeight - 60f) startNewPage()
                    canvas.drawText(currentLine.toString(), margin, y, paint)
                    y += lineHeight
                }
            }

            drawWrappedText("CEREVIX AI DOCUMENT STUDIO", subtitlePaint, 16f)
            y += 4f
            drawWrappedText(title, titlePaint, 28f)
            if (subtitle.isNotBlank()) {
                drawWrappedText(subtitle, bodyPaint, 16f)
            }
            y += 12f

            for ((heading, bullets) in sections) {
                if (y > pageHeight - 100f) startNewPage()
                y += 8f
                drawWrappedText(heading, headingPaint, 20f)
                y += 4f
                for (bullet in bullets) {
                    drawWrappedText("• $bullet", bodyPaint, 16f)
                }
            }

            canvas.drawText("CEREVIX AI • One Platform. Infinite Intelligence. • Page $pageNum", margin, pageHeight - 24f, footerPaint)
            pdf.finishPage(page)

            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safeTitle = title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(32).ifBlank { "document" }
            val outFile = File(dir, "cerevix_${safeTitle}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            pdf.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

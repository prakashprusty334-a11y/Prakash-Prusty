package com.example.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface

enum class PhotoFilterPreset(val label: String) {
    ORIGINAL("Original"),
    NOIR("Noir"),
    CYBER_CYAN("Cyber Cyan"),
    WARM_FILM("Warm Film"),
    VIVID("Vivid")
}

enum class CropRatio(val label: String, val ratio: Float?) {
    FREE("Original", null),
    SQUARE("1:1", 1f),
    LANDSCAPE("16:9", 16f / 9f),
    PORTRAIT("4:5", 4f / 5f)
}

object ImageEffects {

    fun applyEdits(
        source: Bitmap,
        brightness: Float,      // -100..100
        contrast: Float,        // 0.5..2.0
        saturation: Float,      // 0.0..2.0
        filter: PhotoFilterPreset,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        cropRatio: CropRatio,
        overlayText: String
    ): Bitmap {
        // 1. Rotate / flip
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            if (flipHorizontal) postScale(-1f, 1f)
        }
        val transformed = if (rotationDegrees != 0 || flipHorizontal) {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } else {
            source
        }

        // 2. Crop aspect ratio centered
        val cropped = cropToRatio(transformed, cropRatio.ratio)

        // 3. Color adjustments
        val result = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val cm = ColorMatrix()

        // Saturation
        val satMatrix = ColorMatrix().apply { setSaturation(if (filter == PhotoFilterPreset.NOIR) 0f else saturation) }
        cm.postConcat(satMatrix)

        // Contrast & Brightness
        val scale = contrast
        val translate = (-.5f * scale + .5f) * 255f + brightness * 2.55f
        val bcMatrix = ColorMatrix(
            floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        cm.postConcat(bcMatrix)

        // Filter preset tint
        when (filter) {
            PhotoFilterPreset.CYBER_CYAN -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            0.85f, 0f, 0f, 0f, -10f,
                            0f, 1.05f, 0.1f, 0f, 8f,
                            0f, 0.12f, 1.2f, 0f, 18f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.WARM_FILM -> {
                cm.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.15f, 0.05f, 0f, 0f, 12f,
                            0f, 1.02f, 0f, 0f, 4f,
                            0f, 0f, 0.85f, 0f, -8f,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
            }
            PhotoFilterPreset.VIVID -> {
                val vividSat = ColorMatrix().apply { setSaturation(1.35f) }
                cm.postConcat(vividSat)
            }
            else -> Unit
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(cropped, 0f, 0f, paint)

        // 4. Text overlay
        if (overlayText.isNotBlank()) {
            val textSizePx = (result.width * 0.055f).coerceIn(28f, 96f)
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(180, 5, 8, 17)
                textSize = textSizePx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#00E5FF")
                textSize = textSizePx
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val x = result.width / 2f
            val y = result.height - textSizePx * 0.9f
            canvas.drawText(overlayText.trim(), x + 3f, y + 3f, shadowPaint)
            canvas.drawText(overlayText.trim(), x, y, textPaint)
        }

        return result
    }

    private fun cropToRatio(bitmap: Bitmap, targetRatio: Float?): Bitmap {
        if (targetRatio == null) return bitmap
        val w = bitmap.width
        val h = bitmap.height
        val currentRatio = w.toFloat() / h.toFloat()
        return if (currentRatio > targetRatio) {
            val newW = (h * targetRatio).toInt().coerceAtLeast(1)
            val x = (w - newW) / 2
            Bitmap.createBitmap(bitmap, x, 0, newW, h)
        } else {
            val newH = (w / targetRatio).toInt().coerceAtLeast(1)
            val y = (h - newH) / 2
            Bitmap.createBitmap(bitmap, 0, y, w, newH)
        }
    }
}

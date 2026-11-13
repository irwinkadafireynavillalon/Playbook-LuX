package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object CoverGenerator {

    /**
     * Generates an elegant gradient cover with purple/silver/gold styling,
     * drawing the book title and author, and saves it to the book's directory.
     */
    suspend fun generateDefaultCoverFile(
        context: Context,
        bookId: String,
        title: String,
        author: String
    ): String = withContext(Dispatchers.IO) {
        val bookDir = File(context.filesDir, "books/$bookId")
        if (!bookDir.exists()) {
            bookDir.mkdirs()
        }
        val coverFile = File(bookDir, "cover.png")
        if (coverFile.exists() && coverFile.length() > 0) {
            return@withContext coverFile.absolutePath
        }

        val width = 500
        val height = 500
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Gradient background from deep purple #1A1030 to rich purple #451E77 to dark surface
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#1A1030"),
                Color.parseColor("#381864"),
                Color.parseColor("#6A3FA0")
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )

        val bgPaint = Paint().apply {
            shader = gradient
            isAntiAlias = true
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw luxury gold and silver decorative border
        val goldBorderPaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 6f
            isAntiAlias = true
        }
        val silverInnerPaint = Paint().apply {
            color = Color.parseColor("#88C9CED6")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(24f, 24f, (width - 24).toFloat(), (height - 24).toFloat()), 24f, 24f, goldBorderPaint)
        canvas.drawRoundRect(RectF(34f, 34f, (width - 34).toFloat(), (height - 34).toFloat()), 16f, 16f, silverInnerPaint)

        // Decorative emblem circle
        val circlePaint = Paint().apply {
            color = Color.parseColor("#251542")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, 160f, 64f, circlePaint)

        val emblemBorder = Paint().apply {
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawCircle(width / 2f, 160f, 64f, emblemBorder)

        // Initial in gold
        val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "L"
        val initialPaint = Paint().apply {
            color = Color.parseColor("#F3C642")
            textSize = 64f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val textBounds = android.graphics.Rect()
        initialPaint.getTextBounds(initial, 0, initial.length, textBounds)
        canvas.drawText(initial, width / 2f, 160f + (textBounds.height() / 2f), initialPaint)

        // Title text in Silver
        val titlePaint = Paint().apply {
            color = Color.parseColor("#F3F5F9")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val safeTitle = if (title.length > 24) title.take(21) + "…" else title
        canvas.drawText(safeTitle, width / 2f, 310f, titlePaint)

        // Author in Gold
        val authorPaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val safeAuthor = if (author.length > 30) author.take(27) + "…" else author
        canvas.drawText(safeAuthor, width / 2f, 360f, authorPaint)

        // Footer brand
        val brandPaint = Paint().apply {
            color = Color.parseColor("#8E95A5")
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("PLAY LUX AUDIOBOOK", width / 2f, 430f, brandPaint)

        // Save to file
        FileOutputStream(coverFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        coverFile.absolutePath
    }

    /**
     * Extracts prominent / dominant colors from cover image for notification and player theming.
     */
    suspend fun extractColorsFromCover(coverPath: String?): CoverColors = withContext(Dispatchers.IO) {
        if (coverPath == null) return@withContext CoverColors.default()
        val file = File(coverPath)
        if (!file.exists() || file.length() == 0L) return@withContext CoverColors.default()

        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext CoverColors.default()
            val palette = Palette.from(bitmap).generate()

            val primaryColor = palette.getVibrantColor(
                palette.getDominantColor(Color.parseColor("#6A3FA0"))
            )
            val darkColor = palette.getDarkVibrantColor(
                palette.getDarkMutedColor(Color.parseColor("#1A1030"))
            )
            val accentColor = palette.getLightVibrantColor(
                palette.getLightMutedColor(Color.parseColor("#D4AF37"))
            )

            CoverColors(primary = primaryColor, background = darkColor, accent = accentColor)
        } catch (e: Exception) {
            CoverColors.default()
        }
    }
}

data class CoverColors(
    val primary: Int,
    val background: Int,
    val accent: Int
) {
    companion object {
        fun default(): CoverColors = CoverColors(
            primary = Color.parseColor("#6A3FA0"),
            background = Color.parseColor("#1A1030"),
            accent = Color.parseColor("#D4AF37")
        )
    }
}

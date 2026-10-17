package com.forgebuild.forgehouse50.ui.admin

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.forgebuild.forgehouse50.R
import com.forgebuild.forgehouse50.data.LeaderInboxItem
import java.io.File
import java.io.FileOutputStream

/**
 * Branded Quote Image Generator for Admin / Leadership responses (Item 6).
 *
 * Renders 1080 x 1350 px portrait quote cards branded with ForgeHouse logo.
 * Supports automatic text-splitting across continuation cards ("1/2", "2/2"),
 * 3 rotating tasteful tonal background palettes, strict privacy enforcement,
 * and system share sheet integration.
 */
object QuoteImageGenerator {

    private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1350

    data class QuoteCardData(
        val item: LeaderInboxItem,
        val quoteText: String,
        val replyText: String,
        val pageIndex: Int = 1,
        val totalPages: Int = 1,
        val variantIndex: Int = 0,
    )

    data class Palette(
        val bgColor: Int,
        val quoteMarkColor: Int,
        val quoteTextColor: Int,
        val metaColor: Int,
        val dividerColor: Int,
        val replyBgColor: Int,
        val replyLabelColor: Int,
        val replyTextColor: Int,
        val footerColor: Int,
    )

    val PALETTES = listOf(
        // Variant 0: Soft Warm Sage / Ivory (Brand Primary)
        Palette(
            bgColor = rgb(246, 248, 246),
            quoteMarkColor = argb(45, 46, 107, 79),
            quoteTextColor = rgb(25, 28, 26),
            metaColor = rgb(46, 107, 79),
            dividerColor = argb(50, 46, 107, 79),
            replyBgColor = rgb(234, 242, 236),
            replyLabelColor = rgb(46, 107, 79),
            replyTextColor = rgb(35, 45, 38),
            footerColor = rgb(90, 105, 95),
        ),
        // Variant 1: Soft Linen / Warm Sand (Brand Secondary)
        Palette(
            bgColor = rgb(251, 249, 244),
            quoteMarkColor = argb(45, 90, 80, 65),
            quoteTextColor = rgb(28, 28, 25),
            metaColor = rgb(95, 75, 50),
            dividerColor = argb(50, 95, 75, 50),
            replyBgColor = rgb(243, 238, 230),
            replyLabelColor = rgb(110, 85, 55),
            replyTextColor = rgb(45, 40, 35),
            footerColor = rgb(115, 110, 100),
        ),
        // Variant 2: Soft Mint / Mist (Brand Tertiary)
        Palette(
            bgColor = rgb(242, 246, 245),
            quoteMarkColor = argb(45, 32, 90, 75),
            quoteTextColor = rgb(20, 28, 25),
            metaColor = rgb(32, 90, 75),
            dividerColor = argb(50, 32, 90, 75),
            replyBgColor = rgb(230, 240, 237),
            replyLabelColor = rgb(32, 90, 75),
            replyTextColor = rgb(28, 42, 38),
            footerColor = rgb(85, 105, 100),
        )
    )

    fun canShareAsImage(item: LeaderInboxItem): Boolean {
        val hasReply = !item.reply?.text.isNullOrBlank() || item.status == "answered" || item.status == "published"
        if (!hasReply) return false
        val type = item.type.lowercase().trim()
        if (type == "insight") return false
        if ((type == "prayer" || type == "application") && !item.allow_group_share) return false
        return true
    }

    fun authorDisplayName(item: LeaderInboxItem): String {
        return if (!item.show_name) "Anonymous"
        else item.author_full_name.trim().ifBlank { "Anonymous" }
    }

    /**
     * Splits an item's content across continuation cards if it exceeds the card's capacity.
     */
    fun splitTextToCards(item: LeaderInboxItem, globalIndex: Int = 0): List<QuoteCardData> {
        val quote = item.text.trim()
        val reply = item.reply?.text.orEmpty().trim()
        val variant = globalIndex % PALETTES.size

        // Character thresholds for clean, legible typography on 1080x1350
        val maxQuoteCharsFirstPage = 280
        val maxReplyCharsFirstPage = 320

        if (quote.length <= maxQuoteCharsFirstPage && reply.length <= maxReplyCharsFirstPage) {
            return listOf(
                QuoteCardData(
                    item = item,
                    quoteText = quote,
                    replyText = reply,
                    pageIndex = 1,
                    totalPages = 1,
                    variantIndex = variant,
                )
            )
        }

        // Needs continuation split
        val splitQuote = splitAtWord(quote, maxQuoteCharsFirstPage)
        val splitReply = splitAtWord(reply, maxReplyCharsFirstPage)

        return listOf(
            QuoteCardData(
                item = item,
                quoteText = splitQuote.first,
                replyText = splitReply.first,
                pageIndex = 1,
                totalPages = 2,
                variantIndex = variant,
            ),
            QuoteCardData(
                item = item,
                quoteText = splitQuote.second.ifBlank { "…" },
                replyText = splitReply.second.ifBlank { "…" },
                pageIndex = 2,
                totalPages = 2,
                variantIndex = variant,
            )
        )
    }

    private fun splitAtWord(text: String, limit: Int): Pair<String, String> {
        if (text.length <= limit) return Pair(text, "")
        val cut = text.lastIndexOf(' ', limit)
        val splitIdx = if (cut > limit / 2) cut else limit
        return Pair(text.substring(0, splitIdx).trim(), text.substring(splitIdx).trim())
    }

    /**
     * Draws a single 1080 x 1350 Bitmap card using Canvas and StaticLayout.
     */
    fun renderCardToBitmap(context: Context, card: QuoteCardData): Bitmap {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = PALETTES[card.variantIndex % PALETTES.size]

        // 1. Background
        val bgPaint = Paint().apply { color = palette.bgColor }
        canvas.drawRect(0f, 0f, CARD_WIDTH.toFloat(), CARD_HEIGHT.toFloat(), bgPaint)

        // 2. Decorative outer border / card frame
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.dividerColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val frameRect = RectF(40f, 40f, (CARD_WIDTH - 40).toFloat(), (CARD_HEIGHT - 40).toFloat())
        canvas.drawRoundRect(frameRect, 48f, 48f, framePaint)

        // 3. Large Decorative Quotation Mark at top left
        val quoteMarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.quoteMarkColor
            textSize = 210f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        canvas.drawText("“", 90f, 190f, quoteMarkPaint)

        // 4. Page indicator if multi-page ("1/2", "2/2")
        if (card.totalPages > 1) {
            val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.metaColor
                textSize = 30f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("${card.pageIndex}/${card.totalPages}", (CARD_WIDTH - 90).toFloat(), 130f, pagePaint)
        }

        val contentWidth = CARD_WIDTH - 180

        // 5. Quote Text
        val quoteTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.quoteTextColor
            textSize = if (card.quoteText.length > 200) 40f else 46f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }
        val quoteLayout = StaticLayout.Builder.obtain(card.quoteText, 0, card.quoteText.length, quoteTextPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(10f, 1.25f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(90f, 220f)
        quoteLayout.draw(canvas)
        canvas.restore()

        var currentY = 220f + quoteLayout.height + 40f

        // 6. Author Line & Reference in Small Caps
        val authorName = authorDisplayName(card.item).uppercase()
        val refText = card.item.reference.uppercase()
        val typeBadge = card.item.type.uppercase()
        val metaLine = if (refText.isNotBlank()) "$refText  ·  $authorName" else authorName

        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.metaColor
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.08f
        }
        canvas.drawText(metaLine, 90f, currentY + 10f, metaPaint)
        currentY += 45f

        // 7. Divider Line
        val divPaint = Paint().apply {
            color = palette.dividerColor
            strokeWidth = 2.5f
        }
        canvas.drawLine(90f, currentY, (CARD_WIDTH - 90).toFloat(), currentY, divPaint)
        currentY += 40f

        // 8. Response Box (Leader's reply)
        val replyBoxLeft = 90f
        val replyBoxRight = (CARD_WIDTH - 90).toFloat()
        val replyPadding = 40f
        val replyInnerWidth = (replyBoxRight - replyBoxLeft - replyPadding * 2).toInt()

        val replyTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.replyTextColor
            textSize = if (card.replyText.length > 220) 34f else 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val replyLayout = StaticLayout.Builder.obtain(card.replyText, 0, card.replyText.length, replyTextPaint, replyInnerWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(8f, 1.25f)
            .setIncludePad(false)
            .build()

        val replyBoxHeight = replyLayout.height + 110f
        val replyRect = RectF(replyBoxLeft, currentY, replyBoxRight, currentY + replyBoxHeight)

        val replyBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.replyBgColor
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(replyRect, 28f, 28f, replyBgPaint)

        // "RESPONSE" label inside box
        val responseLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.replyLabelColor
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.12f
        }
        canvas.drawText("RESPONSE", replyBoxLeft + replyPadding, currentY + replyPadding + 10f, responseLabelPaint)

        canvas.save()
        canvas.translate(replyBoxLeft + replyPadding, currentY + replyPadding + 42f)
        replyLayout.draw(canvas)
        canvas.restore()

        // 9. Footer at bottom: ForgeHouse 50 Logo + Name + URL
        val footerY = CARD_HEIGHT - 100f

        // Load logo bitmap if available
        try {
            val logoResId = R.drawable.forgehouse_logo
            val rawLogo = BitmapFactory.decodeResource(context.resources, logoResId)
            if (rawLogo != null) {
                val scaledLogo = Bitmap.createScaledBitmap(rawLogo, 60, 60, true)
                canvas.drawBitmap(scaledLogo, 90f, footerY - 45f, Paint(Paint.ANTI_ALIAS_FLAG))
            }
        } catch (_: Exception) {}

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.quoteTextColor
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("ForgeHouse 50", 165f, footerY - 14f, brandPaint)

        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.footerColor
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("forgehouse50.pages.dev", (CARD_WIDTH - 90).toFloat(), footerY - 14f, urlPaint)

        return bitmap
    }

    /**
     * Renders items and triggers system share sheet (ACTION_SEND or ACTION_SEND_MULTIPLE).
     * Clears any stale quote cache images before writing fresh ones.
     */
    fun shareQuoteImages(context: Context, items: List<LeaderInboxItem>) {
        val shareableItems = items.filter { canShareAsImage(it) }
        if (shareableItems.isEmpty()) return

        val cacheDir = File(context.cacheDir, "quote_images")
        if (cacheDir.exists()) {
            cacheDir.listFiles()?.forEach { it.delete() }
        } else {
            cacheDir.mkdirs()
        }

        val allCards = mutableListOf<QuoteCardData>()
        shareableItems.forEachIndexed { idx, item ->
            allCards.addAll(splitTextToCards(item, idx))
        }

        val generatedFiles = mutableListOf<File>()
        allCards.forEachIndexed { cardIdx, card ->
            val bitmap = renderCardToBitmap(context, card)
            // Filename has no user id or email (privacy rule)
            val file = File(cacheDir, "forgehouse50_quote_${cardIdx + 1}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            generatedFiles.add(file)
        }

        if (generatedFiles.isEmpty()) return

        val uris = generatedFiles.map { file ->
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }

        val caption = if (shareableItems.size == 1) {
            val itm = shareableItems.first()
            "ForgeHouse 50 — ${itm.reference}\nhttps://forgehouse50.pages.dev"
        } else {
            "ForgeHouse 50 — Scripture Responses\nhttps://forgehouse50.pages.dev"
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                putExtra(Intent.EXTRA_TEXT, caption)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooser = Intent.createChooser(intent, "Share quote image")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

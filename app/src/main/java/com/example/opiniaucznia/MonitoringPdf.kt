package com.example.opiniaucznia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Osobny raport PDF monitoringu; niezależny od raportu „Ocena ucznia”. */
fun writeMonitoringPdf(context: Context, output: OutputStream, studentName: String, period: MonitoringPeriod) {
    val doc = PdfDocument()
    val pageW = 595
    val pageH = 842
    val margin = 38f
    val contentW = pageW - margin * 2
    val footerY = pageH - 42f
    val navy = Color.rgb(25, 51, 83)
    val ink = Color.rgb(32, 51, 77)
    val muted = Color.rgb(95, 108, 123)
    val border = Color.rgb(213, 222, 231)
    val pale = Color.rgb(238, 244, 249)
    val teal = Color.rgb(49, 128, 130)
    val font = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProBook.otf") }
        .getOrElse { Typeface.create("sans-serif", Typeface.NORMAL) }
    val boldFont = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProMedium.otf") }
        .getOrElse { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun p(size: Float, color: Int = ink, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) boldFont else font
    }
    var pageNo = 0
    var page: PdfDocument.Page? = null
    var c: Canvas? = null
    var y = 28f

    fun startPage() {
        if (page != null) {
            c!!.drawLine(margin, footerY, pageW - margin, footerY, p(7f, border))
            c!!.drawText("Monitoring ucznia • dokument poufny", margin, pageH - 18f, p(7f, muted))
            val label = "Strona $pageNo"
            c!!.drawText(label, pageW - margin - p(7f, muted).measureText(label), pageH - 18f, p(7f, muted))
            doc.finishPage(page!!)
        }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        c = page!!.canvas
        c!!.drawColor(Color.WHITE)
        c!!.drawRect(0f, 0f, pageW.toFloat(), 5f, p(5f, navy))
        c!!.drawRect(0f, 5f, 112f, 8f, p(3f, teal))
        y = 28f
    }
    fun wrapped(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            if (lines.isEmpty()) lines.add(word)
            else {
                val candidate = lines.last() + " " + word
                if (paint.measureText(candidate) <= width) lines[lines.lastIndex] = candidate else lines.add(word)
            }
        }
        return lines.ifEmpty { listOf("") }
    }
    fun ensure(height: Float) { if (y + height > footerY - 12f) startPage() }
    fun text(value: String, size: Float = 9.2f, color: Int = ink, bold: Boolean = false, indent: Float = 0f, gap: Float = 3f) {
        val paint = p(size, color, bold)
        val lines = wrapped(value, paint, contentW - indent)
        val lineHeight = size * 1.38f
        ensure(lines.size * lineHeight + gap)
        lines.forEach { line -> c!!.drawText(line, margin + indent, y + size, paint); y += lineHeight }
        y += gap
    }
    fun section(title: String) {
        ensure(30f)
        val rect = RectF(margin, y, pageW - margin, y + 24f)
        c!!.drawRoundRect(rect, 4f, 4f, p(10f, pale))
        c!!.drawRoundRect(rect, 4f, 4f, p(10f, border).apply { style = Paint.Style.STROKE; strokeWidth = 0.6f })
        c!!.drawText(title, margin + 10f, y + 16f, p(10.5f, navy, true))
        y += 31f
    }
    fun bar(label: String, value: Double, detail: String) {
        text(label, 8.8f, ink, true, gap = 1f)
        ensure(25f)
        val top = y
        c!!.drawRoundRect(RectF(margin, top, pageW - margin, top + 7f), 4f, 4f, p(7f, pale))
        val w = contentW * (value / 5.0).toFloat().coerceIn(0f, 1f)
        if (w > 0f) c!!.drawRoundRect(RectF(margin, top, margin + w, top + 7f), 4f, 4f, p(7f, teal))
        y += 12f
        text(detail, 7.8f, muted, gap = 4f)
    }

    startPage()
    text("RAPORT MONITORINGU UCZNIA", 16f, navy, true, gap = 2f)
    text("Osobny raport obserwacji i porównania wyników", 9f, muted, gap = 9f)
    section("1. Dane monitoringu")
    text("Uczeń: " + studentName.ifBlank { "nie podano" }, 10f, ink, true)
    text("Okres: " + period.title.ifBlank { "nie podano" })
    text("Data rozpoczęcia: " + period.startDate.ifBlank { "nie podano" })
    text("Data zakończenia: " + period.endDate.ifBlank { "nie podano" })
    text("Liczba nauczycieli: " + period.teachers.size.toString())
    text("Data wygenerowania: " + SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL")).format(Date()), 8f, muted, gap = 8f)

    section("2. Średnie wyniki według obszarów")
    text("Skala 1–5. Brak oceny oraz „Nie obserwowano” są liczone jako 0 w średniej.", 8.3f, muted, gap = 5f)
    period.observationAreas.forEach { area ->
        val scores = period.teachers.map { t -> t.ratings.firstOrNull { it.areaId == area || it.areaName == area }?.rating ?: 0 }
        val avg = if (scores.isEmpty()) 0.0 else scores.average()
        bar(area, avg, "Średnia: " + String.format(Locale("pl", "PL"), "%.2f", avg) + " / 5; nauczyciele: " + period.teachers.size)
    }
    val allScores = period.teachers.flatMap { t ->
        period.observationAreas.map { area -> t.ratings.firstOrNull { it.areaId == area || it.areaName == area }?.rating ?: 0 }
    }
    if (allScores.isNotEmpty()) text("Średnia ogólna okresu: " + String.format(Locale("pl", "PL"), "%.2f", allScores.average()) + " / 5", 10f, navy, true, gap = 8f)
    else text("Nie wprowadzono jeszcze ocen nauczycieli.", 9f, muted, gap = 8f)

    section("3. Oceny i komentarze nauczycieli")
    if (period.teachers.isEmpty()) text("Nie dodano jeszcze nauczycieli.")
    period.teachers.forEach { teacher ->
        ensure(40f)
        text(teacher.name.ifBlank { "Nauczyciel bez nazwiska" }, 10f, navy, true, gap = 1f)
        text(listOf(teacher.role, teacher.observationDate).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Nie podano funkcji ani daty." }, 8f, muted, gap = 3f)
        period.observationAreas.forEach { area ->
            val r = teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
            val result = when {
                r?.rating != null -> r.rating.toString() + "/5"
                r?.notObserved == true -> "Nie obserwowano (0)"
                else -> "Brak oceny (0)"
            }
            text(area + " — " + result, 8.5f, ink, true, indent = 8f, gap = 1f)
            if (!r?.comment.isNullOrBlank()) text("Komentarz: " + r!!.comment, 8f, muted, indent = 16f, gap = 2f)
        }
        if (teacher.generalComment.isNotBlank()) text("Komentarz ogólny: " + teacher.generalComment, 8.5f, ink, indent = 8f, gap = 3f)
        y += 5f
    }
    section("4. Wnioski z monitoringu")
    text(period.summary.ifBlank { "Nie uzupełniono wniosków z monitoringu." }, 9f, ink, gap = 7f)
    section("5. Zalecenia do dalszej pracy")
    text(period.recommendations.ifBlank { "Nie uzupełniono zaleceń." }, 9f, ink, gap = 8f)
    text("Dokument pomocniczy do analizy obserwacji. Wnioski należy interpretować z uwzględnieniem jakości i kompletności danych.", 7.5f, muted, gap = 5f)

    if (page != null) {
        c!!.drawLine(margin, footerY, pageW - margin, footerY, p(7f, border))
        c!!.drawText("Monitoring ucznia • dokument poufny", margin, pageH - 18f, p(7f, muted))
        val label = "Strona $pageNo"
        c!!.drawText(label, pageW - margin - p(7f, muted).measureText(label), pageH - 18f, p(7f, muted))
        doc.finishPage(page!!)
    }
    try { doc.writeTo(output) } finally { doc.close() }
}


/** Pusta karta do wydruku i ręcznego wypełnienia przez nauczyciela. */
fun writeMonitoringObservationCardPdf(context: Context, output: OutputStream, studentName: String, period: MonitoringPeriod) {
    val doc = PdfDocument()
    val pageW = 595
    val pageH = 842
    val margin = 36f
    val contentW = pageW - margin * 2
    val bottom = pageH - 40f
    val ink = Color.rgb(32, 51, 77)
    val navy = Color.rgb(25, 51, 83)
    val muted = Color.rgb(95, 108, 123)
    val border = Color.rgb(190, 202, 214)
    val pale = Color.rgb(242, 246, 250)
    val font = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProBook.otf") }
        .getOrElse { Typeface.create("sans-serif", Typeface.NORMAL) }
    val bold = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProMedium.otf") }
        .getOrElse { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    fun p(size: Float, color: Int = ink, heavy: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (heavy) bold else font
    }
    var pageNo = 0
    var page: PdfDocument.Page? = null
    var canvas: Canvas? = null
    var y = 26f
    fun startPage() {
        if (page != null) {
            canvas!!.drawLine(margin, bottom, pageW - margin, bottom, p(7f, border))
            canvas!!.drawText("Karta obserwacji • dokument poufny", margin, pageH - 17f, p(7f, muted))
            doc.finishPage(page!!)
        }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
        canvas = page!!.canvas
        canvas!!.drawColor(Color.WHITE)
        canvas!!.drawRect(0f, 0f, pageW.toFloat(), 5f, p(5f, navy))
        y = 24f
    }
    fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
            if (lines.isEmpty()) lines.add(word)
            else {
                val candidate = lines.last() + " " + word
                if (paint.measureText(candidate) <= width) lines[lines.lastIndex] = candidate else lines.add(word)
            }
        }
        return lines.ifEmpty { listOf("") }
    }
    fun ensure(height: Float) { if (y + height > bottom - 8f) startPage() }
    fun text(value: String, size: Float = 9f, color: Int = ink, heavy: Boolean = false, gap: Float = 3f) {
        val paint = p(size, color, heavy)
        val lines = wrap(value, paint, contentW)
        val lineH = size * 1.35f
        ensure(lines.size * lineH + gap)
        lines.forEach { canvas!!.drawText(it, margin, y + size, paint); y += lineH }
        y += gap
    }
    startPage()
    text("KARTA OBSERWACJI UCZNIA", 15f, navy, true, 4f)
    text("Wypełnia nauczyciel na podstawie obserwacji ucznia w codziennych sytuacjach szkolnych.", 8.5f, muted, false, 8f)
    text("Uczeń: " + studentName.ifBlank { "........................................................" }, 10f, ink, true)
    text("Okres: " + period.title + " (" + period.startDate + " – " + period.endDate + ")")
    text("Nauczyciel: ................................................................................................................")
    text("Rola / przedmiot: .....................................................   Data: ................................", gap = 8f)
    text("Instrukcja: zaznacz jedną ocenę dla każdego obszaru. Jeśli nie było możliwości obserwacji, zaznacz „Nie obserwowano”. Dopisz konkretny przykład.", 8.3f, ink, false, 8f)
    period.observationAreas.forEachIndexed { index, area ->
        val areaPaint = p(9f, navy, true)
        val areaLines = wrap((index + 1).toString() + ". " + area, areaPaint, contentW - 16f)
        val cardH = 65f + (areaLines.size - 1) * 11f
        ensure(cardH + 5f)
        val rect = RectF(margin, y, pageW - margin, y + cardH)
        canvas!!.drawRoundRect(rect, 4f, 4f, p(9f, Color.WHITE))
        canvas!!.drawRoundRect(rect, 4f, 4f, p(9f, border).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f })
        var ty = y + 14f
        areaLines.forEach { line -> canvas!!.drawText(line, margin + 9f, ty, areaPaint); ty += 11f }
        ty += 2f
        canvas!!.drawText("Ocena:   1 [  ]   2 [  ]   3 [  ]   4 [  ]   5 [  ]   Nie obserwowano [  ]", margin + 9f, ty + 9f, p(8.3f, ink))
        ty += 18f
        canvas!!.drawText("Komentarz / przykład:", margin + 9f, ty + 8f, p(7.7f, muted))
        canvas!!.drawLine(margin + 9f, ty + 20f, pageW - margin - 9f, ty + 20f, p(7f, border))
        canvas!!.drawLine(margin + 9f, ty + 31f, pageW - margin - 9f, ty + 31f, p(7f, border))
        y += cardH + 5f
    }
    ensure(55f)
    text("Komentarz ogólny:", 9f, navy, true, 2f)
    repeat(3) { canvas!!.drawLine(margin, y + it * 13f, pageW - margin, y + it * 13f, p(7f, border)) }
    y += 43f
    text("Podpis nauczyciela: ........................................................................", 8.5f, ink, false, 3f)
    if (page != null) {
        canvas!!.drawLine(margin, bottom, pageW - margin, bottom, p(7f, border))
        canvas!!.drawText("Karta obserwacji • dokument poufny", margin, pageH - 17f, p(7f, muted))
        doc.finishPage(page!!)
    }
    try { doc.writeTo(output) } finally { doc.close() }
}

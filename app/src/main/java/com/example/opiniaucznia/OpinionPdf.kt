package com.example.opiniaucznia

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Generuje czytelny raport A4 z kartami, spójną kolorystyką i automatycznym podziałem stron. */
fun writeOpinionPdf(
    context: Context,
    uri: Uri,
    draft: StudentDraft,
    indicators: List<AssessmentIndicator>,
    answers: Map<String, IndicatorAnswer>
) {
    val pdf = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 38f
    val contentWidth = pageWidth - margin * 2
    val footerTop = pageHeight - 48f
    val ink = Color.rgb(32, 51, 77)          // granatowy tekst
    val navy = Color.rgb(25, 51, 83)
    val blue = Color.rgb(57, 105, 145)
    val teal = Color.rgb(49, 128, 130)
    val gold = Color.rgb(190, 148, 55)
    val paper = Color.rgb(255, 255, 255)
    val paleBlue = Color.rgb(238, 244, 249)
    val paleTeal = Color.rgb(237, 247, 246)
    val paleGold = Color.rgb(255, 249, 235)
    val border = Color.rgb(213, 222, 231)
    val muted = Color.rgb(95, 108, 123)

    // Avenir Next LT Pro z zasobów aplikacji. Fallback zabezpiecza generowanie PDF,
    // jeśli pliki fontów nie zostały skopiowane do assets/fonts.
    val avenirBook = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProBook.otf") }
        .getOrElse { Typeface.create("sans-serif", Typeface.NORMAL) }
    val avenirMedium = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProMedium.otf") }
        .getOrElse { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    val avenirHeavy = runCatching { Typeface.createFromAsset(context.assets, "fonts/AvenirLTProHeavy.otf") }
        .getOrElse { Typeface.create("sans-serif", Typeface.BOLD) }

    fun paint(size: Float, color: Int = ink, bold: Boolean = false): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL)
    }
    val bodyPaint = paint(9.5f, ink)
    val labelPaint = paint(7.3f, muted, true)
    val footerPaint = paint(6.7f, muted).apply { typeface = avenirBook }
    val footerBoldPaint = paint(6.7f, muted, true).apply { typeface = avenirMedium }
    val generatedDate = SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL")).format(Date())

    // Rysowanie z delikatnie zwiększonym odstępem między znakami, aby stopka była bardziej elegancka.
    fun drawTrackedText(c: Canvas, text: String, x: Float, baseline: Float, p: Paint, tracking: Float = 0.18f) {
        var cursor = x
        text.forEach { ch ->
            val piece = ch.toString()
            c.drawText(piece, cursor, baseline, p)
            cursor += p.measureText(piece) + tracking
        }
    }

    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var canvas: Canvas? = null
    var y = 0f

    fun roundedRect(c: Canvas, rect: RectF, radius: Float, color: Int, stroke: Int? = null, strokeWidth: Float = 0.7f) {
        val p = paint(10f, color).apply { style = Paint.Style.FILL }
        c.drawRoundRect(rect, radius, radius, p)
        if (stroke != null) {
            val sp = paint(10f, stroke).apply { style = Paint.Style.STROKE; this.strokeWidth = strokeWidth }
            c.drawRoundRect(rect, radius, radius, sp)
        }
    }

    fun drawFooter() {
        val c = canvas ?: return
        val linePaint = paint(7f, border).apply { strokeWidth = 0.7f }
        c.drawLine(margin, footerTop, pageWidth - margin, footerTop, linePaint)
        val appVersion = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "nieznana"
        } catch (_: Exception) {
            "nieznana"
        }
        val brandLine1 = "Program Ocena Ucznia | wersja $appVersion | licencja freeware"
        val brandLine2 = "Gabinet Psychologiczny Paweł Gębicki | www.pggp.pl | 2026"
        val brandPaint = paint(6.4f, muted).apply { typeface = avenirBook }
        val brandStrongPaint = paint(7.1f, navy).apply { typeface = avenirMedium }
        val pageLabel = "Strona $pageNumber"
        val pageLabelWidth = footerBoldPaint.measureText(pageLabel)
        c.drawText(brandLine1, margin, footerTop + 10f, brandPaint)
        c.drawText(brandLine2, margin, footerTop + 21f, brandStrongPaint)
        c.drawText(pageLabel, pageWidth - margin - pageLabelWidth, footerTop + 21f, footerBoldPaint)
        val dateLabel = "Wygenerowano: $generatedDate r."
        c.drawText(dateLabel, margin, pageHeight - 17f, footerPaint)
        val confidential = "DOKUMENT POUFNY"
        c.drawText(confidential, pageWidth - margin - footerBoldPaint.measureText(confidential), pageHeight - 17f, footerBoldPaint)
    }

    fun finishPage() {
        if (page != null) {
            drawFooter()
            pdf.finishPage(page!!)
            page = null
            canvas = null
        }
    }

    fun startPage() {
        finishPage()
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page!!.canvas
        canvas!!.drawColor(paper)
        // Dyskretny górny pasek: bez osobnej strony tytułowej i bez ozdobnego nagłówka.
        canvas!!.drawRect(0f, 0f, pageWidth.toFloat(), 5f, paint(5f, navy))
        canvas!!.drawRect(0f, 5f, 112f, 8f, paint(3f, gold))
        y = 28f
    }

    fun ensureSpace(height: Float) {
        if (y + height > footerTop - 12f) startPage()
    }

    fun wrap(text: String, p: Paint, maxWidth: Float): List<String> {
        val result = mutableListOf<String>()
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
            if (result.isEmpty()) result += word
            else {
                val candidate = result.last() + " " + word
                if (p.measureText(candidate) <= maxWidth) result[result.lastIndex] = candidate
                else result += word
            }
        }
        return if (result.isEmpty()) listOf("") else result
    }

    fun drawTextBlock(text: String, x: Float, top: Float, maxWidth: Float, p: Paint, lineGap: Float = 2f): Float {
        val lines = wrap(text, p, maxWidth)
        val lineHeight = p.textSize * 1.38f
        lines.forEachIndexed { index, part ->
            canvas!!.drawText(part, x, top + p.textSize + index * lineHeight, p)
        }
        return lines.size * lineHeight + lineGap
    }

    fun sectionTitle(title: String, accent: Int = navy) {
        val p = paint(11.2f, navy, true)
        val height = 27f
        ensureSpace(height + 7f)
        val c = canvas!!
        roundedRect(c, RectF(margin, y, pageWidth - margin, y + height - 2f), 4f, when (accent) {
            gold -> paleGold
            teal -> paleTeal
            else -> paleBlue
        }, border, 0.45f)
        c.drawRoundRect(RectF(margin, y, margin + 3.2f, y + height - 2f), 2f, 2f, paint(3f, accent))
        c.drawText(title, margin + 11f, y + 17f, p)
        y += height + 2f
    }

    fun card(title: String, text: String, accent: Int = blue, fill: Int = paleBlue, titleSize: Float = 9.2f) {
        val innerX = margin + 13f
        val innerWidth = contentWidth - 26f
        val titleP = paint(titleSize, ink, true)
        val bodyP = paint(9.1f, ink)
        val titleLines = wrap(title, titleP, innerWidth)
        val bodyLines = wrap(text, bodyP, innerWidth)
        val titleHeight = titleLines.size * titleP.textSize * 1.28f
        val bodyHeight = bodyLines.size * bodyP.textSize * 1.36f
        val cardHeight = 12f + titleHeight + 5f + bodyHeight + 10f
        ensureSpace(cardHeight + 7f)
        val c = canvas!!
        val rect = RectF(margin, y, pageWidth - margin, y + cardHeight)
        roundedRect(c, rect, 5f, paper, border)
        c.drawRoundRect(RectF(margin, y, margin + 3.2f, y + cardHeight), 2f, 2f, paint(3f, accent))
        val titleRect = RectF(margin + 3f, y + 1f, pageWidth - margin - 1f, y + 12f + titleHeight + 4f)
        c.drawRoundRect(titleRect, 4f, 4f, paint(1f, fill))
        var ty = y + 8f
        titleLines.forEach { line ->
            c.drawText(line, innerX, ty + titleP.textSize, titleP)
            ty += titleP.textSize * 1.28f
        }
        // Wyśrodkuj treść pionowo w białej części karty, zamiast przyklejać ją do nagłówka.
        val bodyLineHeight = bodyP.textSize * 1.36f
        val bodyBlockHeight = bodyLines.size * bodyLineHeight
        val whiteAreaTop = y + 12f + titleHeight + 4f
        val bodyTop = whiteAreaTop + ((cardHeight - (whiteAreaTop - y) - 10f - bodyBlockHeight) / 2f).coerceAtLeast(2f)
        ty = bodyTop
        bodyLines.forEach { line ->
            c.drawText(line, innerX, ty + bodyP.textSize, bodyP)
            ty += bodyLineHeight
        }
        y += cardHeight + 7f
    }

    fun infoGrid(items: List<Pair<String, String>>) {
        val gap = 7f
        val cols = 2
        val cellW = (contentWidth - gap) / cols
        val rows = (items.size + cols - 1) / cols
        val heights = items.chunked(cols).map { rowItems ->
            rowItems.maxOf { (label, value) ->
                val valueP = paint(9.2f, ink, true)
                13f + wrap(value.ifBlank { "—" }, valueP, cellW - 20f).size * 12.5f + 10f
            }
        }
        heights.forEachIndexed { rowIndex, h ->
            ensureSpace(h + 6f)
            val rowItems = items.drop(rowIndex * cols).take(cols)
            rowItems.forEachIndexed { colIndex, pair ->
                val (label, value) = pair
                val x = margin + colIndex * (cellW + gap)
                val rect = RectF(x, y, x + cellW, y + h)
                roundedRect(canvas!!, rect, 5f, paleBlue, border)
                canvas!!.drawText(label.uppercase(Locale("pl", "PL")), x + 10f, y + 13f, labelPaint)
                val valueP = paint(9.2f, ink, true)
                var vy = y + 18f
                wrap(value.ifBlank { "—" }, valueP, cellW - 20f).forEach { line ->
                    canvas!!.drawText(line, x + 10f, vy + valueP.textSize, valueP)
                    vy += 12.5f
                }
            }
            y += h + 6f
        }
    }

    startPage()
    // Górna część pierwszej strony: miejsce na pieczęć szkoły i formalny tytuł opinii.
    val stampHeight = 78f
    val stampRect = RectF(margin, y, pageWidth - margin, y + stampHeight)
    roundedRect(canvas!!, stampRect, 2f, paleBlue, border)
    canvas!!.drawRect(margin, y, margin + 3f, y + stampHeight, paint(3f, gold))
    val stampLabel = paint(7.0f, muted, false)
    canvas!!.drawText("Miejsce na pieczęć szkoły", margin + 12f, y + stampHeight - 8f, stampLabel)
    y += stampHeight + 11f
    val mainTitle = paint(16f, navy, true)
    val titleLines = wrap("OPINIA O FUNKCJONOWANIU UCZNIA W SZKOLE", mainTitle, contentWidth)
    titleLines.forEach { line ->
        canvas!!.drawText(line, (pageWidth - mainTitle.measureText(line)) / 2f, y + mainTitle.textSize, mainTitle)
        y += 20f
    }
    val subtitle = "sporządzona na potrzeby postępowania w sprawie wydania orzeczenia / opinii przez zespół orzekający działający w publicznej poradni psychologiczno-pedagogicznej"
    val subtitlePaint = paint(8.2f, muted)
    val subtitleLines = wrap(subtitle, subtitlePaint, contentWidth - 12f)
    subtitleLines.forEach { line ->
        canvas!!.drawText(line, (pageWidth - subtitlePaint.measureText(line)) / 2f, y + subtitlePaint.textSize, subtitlePaint)
        y += 11f
    }
    y += 8f
    // Dane ucznia pojawiają się bezpośrednio po tytule.
    sectionTitle("1. Dane ucznia i szkoły", navy)
    val observationPeriod = listOf(draft.observationStart, draft.observationEnd)
        .filter { it.isNotBlank() }.joinToString(" – ").ifBlank { "—" }
    infoGrid(listOf(
        "Uczeń / uczennica" to draft.studentName,
        "Klasa" to draft.className,
        "Szkoła" to draft.school,
        "Rok szkolny" to draft.schoolYear,
        "Miejscowość" to draft.schoolTown,
        "Data urodzenia" to draft.birthDate,
        "Okres obserwacji" to observationPeriod
    ))

    sectionTitle("2. Podstawa opracowania", blue)
    val basisText = "Informacje z obserwacji funkcjonowania ucznia, analizy pracy edukacyjnej oraz konsultacji z osobami pracującymi z uczniem. W wersji końcowej należy pozostawić wyłącznie źródła faktycznie wykorzystane w danej sprawie."
    card("Podstawa opracowania", basisText, blue, paleBlue)
    val sourcesText = if (draft.sources.isEmpty()) "Nie wskazano źródeł informacji." else draft.sources.joinToString(" • ")
    card("Źródła faktycznie wykorzystane", sourcesText, blue, paleBlue)

    sectionTitle("3. Obszary funkcjonowania ucznia", teal)
    card("OBSZAR A", "Aktywność i uczestniczenie ucznia zgodnie z obszarami ICF (Międzynarodowej Klasyfikacji Funkcjonowania, Niepełnosprawności i Zdrowia).", teal, paleTeal)

    indicators.groupBy { it.areaTitle }.forEach { (areaTitle, areaIndicators) ->
        sectionTitle(normalizeAreaTitle(areaTitle), blue)
        areaIndicators.forEach { item ->
            val answer = answers[item.id]
            val interpretation = when {
                answer == null -> "Nie oceniono"
                answer.noData -> "Brak danych"
                answer.rating != null -> item.levels.firstOrNull { it.value == answer.rating }?.description
                    ?: "Brak opisu interpretacji w katalogu źródłowym"
                else -> "Nie oceniono"
            }
            card(item.title, interpretation, blue, paleBlue)
            if (answer?.notes?.isNotBlank() == true) {
                card("Uwagi", answer.notes, teal, paleTeal, 8.8f)
            }
        }
    }

    // Rezerwujemy miejsce na tytuł i początek wniosków, żeby nagłówek nie został
    // samotnie na końcu strony, a treść nie była przenoszona w całości na następną.
    ensureSpace(58f)
    sectionTitle("4. Wnioski do dalszej pracy z uczniem", gold)
    val conclusionText = draft.conclusions.ifBlank { "Nie wpisano wniosków." }
    val normalizedConclusion = conclusionText.replace("\r\n", "\n").replace('\r', '\n')
    // Rozdzielamy punkty listy zapisane w jednym wierszu przez AI.
    val listFormattedConclusion = normalizedConclusion.replace(Regex("(?<!\\n)\\s+-\\s+(?=\\S)"), "\n- ")
    val normalizedConclusionForLists = normalizedConclusion.replace(Regex("(?<!\\n)\\s+-\\s+(?=\\S)"), "\n- ")
val paragraphInnerX = margin + 14f
    val paragraphWidth = contentWidth - 28f

    data class StyledWord(val text: String, val bold: Boolean = false)

    fun styledWords(text: String): List<StyledWord> {
        val result = mutableListOf<StyledWord>()
        val boldPattern = Regex("""(\*\*(.+?)\*\*|__(.+?)__)""")
        var cursor = 0
        boldPattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                text.substring(cursor, match.range.first).trim().split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }.forEach { result += StyledWord(it) }
            }
            val boldText = match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
            boldText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                .forEach { result += StyledWord(it, true) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            text.substring(cursor).trim().split(Regex("\\s+"))
                .filter { it.isNotEmpty() }.forEach { result += StyledWord(it) }
        }
        return result
    }

    fun wrapStyled(words: List<StyledWord>, maxWidth: Float, fontSize: Float): List<List<StyledWord>> {
        if (words.isEmpty()) return listOf(emptyList())
        val lines = mutableListOf<List<StyledWord>>()
        var current = mutableListOf<StyledWord>()
        var currentWidth = 0f
        words.forEach { word ->
            val wordPaint = paint(fontSize, ink, word.bold)
            val wordWidth = wordPaint.measureText(word.text)
            val spaceWidth = paint(fontSize, ink).measureText(" ")
            if (current.isNotEmpty() && currentWidth + spaceWidth + wordWidth > maxWidth) {
                lines += current
                current = mutableListOf()
                currentWidth = 0f
            }
            if (current.isNotEmpty()) currentWidth += spaceWidth
            current += word
            currentWidth += wordWidth
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    fun drawStyledLine(words: List<StyledWord>, x: Float, baseline: Float, fontSize: Float) {
        var drawX = x
        words.forEachIndexed { index, word ->
            val wordPaint = paint(fontSize, ink, word.bold)
            canvas!!.drawText(word.text, drawX, baseline, wordPaint)
            drawX += wordPaint.measureText(word.text)
            if (index < words.lastIndex) drawX += paint(fontSize, ink).measureText(" ")
        }
    }

    // Grupujemy wiersze w logiczne bloki: nagłówki, podpunkty, pozycje listy i opisy.
    // Zwykłe zawinięte / kolejne zdania nie dostają osobnych kart ani sekcji.
    data class ConclusionBlock(
        val kind: String,
        val prefix: String,
        val text: String,
        val level: Int = 0
    )

    val mainHeadingPattern = Regex("^(?:([IVXLCDM]+)[.)]|(\\d+)[.)])\\s+(.+)$", RegexOption.IGNORE_CASE)
    val subpointPattern = Regex("^(?:((?:\\d+\\.)+\\d+[.)]?)|([a-zA-Z][.)]))\\s+(.+)$")
    val bulletPattern = Regex("^[-*•]\\s+(.+)$")
    val markdownHeadingPattern = Regex("^#{1,3}\\s+(.+)$")
    val blocks = mutableListOf<ConclusionBlock>()
    var currentKind: String? = null
    var currentPrefix = ""
    var currentLevel = 0
    val currentLines = mutableListOf<String>()

    fun flushBlock() {
        if (currentLines.isNotEmpty()) {
            blocks += ConclusionBlock(currentKind ?: "body", currentPrefix, currentLines.joinToString(" ").trim(), currentLevel)
            currentLines.clear()
        }
        currentKind = null
        currentPrefix = ""
        currentLevel = 0
    }

    normalizedConclusionForLists.lines().forEach { sourceLine ->
        val line = sourceLine.trim().replace(Regex("^\\*{1,2}(.*?)\\*{1,2}$")) { it.groupValues[1] }.replace(Regex("^_{1,2}(.*?)_{1,2}$")) { it.groupValues[1] }.trim()
        if (line.isEmpty()) {
            flushBlock()
            blocks += ConclusionBlock("space", "", "")
            return@forEach
        }
        val markdown = markdownHeadingPattern.find(line)
        val main = mainHeadingPattern.find(line)
        val sub = subpointPattern.find(line)
        val bullet = bulletPattern.find(line)
        val kind: String
        val prefix: String
        val content: String
        val level: Int
        when {
            markdown != null -> {
                kind = "heading"; prefix = ""; content = markdown.groupValues[1]; level = 1
            }
            main != null -> {
                kind = "heading"; prefix = if (main.groupValues[1].isNotEmpty()) "${main.groupValues[1]}." else "${main.groupValues[2]}."
                content = main.groupValues[3]; level = 1
            }
            sub != null -> {
                kind = "subpoint"; prefix = sub.groupValues[1].ifEmpty { sub.groupValues[2] }
                content = sub.groupValues[3]; level = 1
            }
            bullet != null -> {
                kind = "bullet"; prefix = "•"; content = bullet.groupValues[1]; level = 1
            }
            else -> {
                // Kolejne zdania i linie opisu pozostają w bieżącym akapicie,
                // także wtedy, gdy opis rozpoczął się myślnikiem lub podpunktem.
                if (currentKind == "body" || currentKind == "bullet" || currentKind == "subpoint") {
                    currentLines += line
                    return@forEach
                }
                kind = "body"; prefix = ""; content = line; level = 0
            }
        }
        if (currentKind != kind || kind != "body") flushBlock()
        if (currentKind == null) {
            currentKind = kind
            currentPrefix = prefix
            currentLevel = level
        }
        currentLines += content
    }
    flushBlock()

    blocks.forEach { block ->
        if (block.kind == "space") {
            y += 5f
            if (y > footerTop - 30f) startPage()
            return@forEach
        }
        val isHeading = block.kind == "heading"
        val isSubpoint = block.kind == "subpoint"
        val isBullet = block.kind == "bullet"
        val fontSize = when {
            isHeading -> 11.4f // o 2 punkty większa od tekstu podstawowego
            else -> 9.4f
        }
        val bold = isHeading
        val lineHeight = fontSize * if (isHeading) 1.5f else 1.42f
        val prefix = when {
            isHeading && block.prefix.isNotEmpty() -> "${block.prefix}  "
            isBullet -> "•  "
            isSubpoint -> "${block.prefix}  "
            else -> ""
        }
        val leftInset = if (isBullet || isSubpoint) 10f else 0f
        val prefixPaint = paint(fontSize, ink, bold)
        val prefixWidth = prefixPaint.measureText(prefix)
        val words = styledWords(block.text).map { if (bold) it.copy(bold = true) else it }
        val lines = wrapStyled(words, paragraphWidth - leftInset - prefixWidth, fontSize)
        var offset = 0
        while (offset < lines.size) {
            if (y + lineHeight + 10f > footerTop - 12f) startPage()
            val availableLines = maxOf(1, ((footerTop - 12f - y - 8f) / lineHeight).toInt())
            val linesOnPage = minOf(lines.size - offset, availableLines)
            repeat(linesOnPage) { lineIndex ->
                val lineWords = lines[offset + lineIndex]
                val lineX = margin + 14f + leftInset + if (lineIndex == 0) prefixWidth else 0f
                val baseline = y + fontSize + lineIndex * lineHeight
                if (lineIndex == 0 && prefix.isNotEmpty()) {
                    canvas!!.drawText(prefix, margin + 14f + leftInset, baseline, prefixPaint)
                }
                drawStyledLine(lineWords, lineX, baseline, fontSize)
            }
            y += linesOnPage * lineHeight
            offset += linesOnPage
            if (offset < lines.size) {
                y += 4f
                startPage()
            }
        }
        y += when {
            isHeading -> 5f
            isSubpoint -> 2f
            isBullet -> 3f
            else -> 2f
        }
    }

    sectionTitle("5. Osoby sporządzające opinię", navy)
    val staff = listOf(
        "Wychowawca klasy" to draft.homeroomTeacher,
        "Pedagog / pedagog specjalny" to draft.pedagogue,
        "Psycholog" to draft.psychologist,
        "Inni nauczyciele / specjaliści" to draft.otherStaff
    ).filter { it.second.isNotBlank() }
    if (staff.isEmpty()) {
        card("Osoby sporządzające opinię", "Nie wskazano osób sporządzających opinię.", navy, paleBlue)
    } else {
        staff.forEach { (role, name) -> card(role, name, navy, paleBlue) }
    }

    sectionTitle("6. Podpis dyrektora szkoły", navy)
    val signatureHeight = 265f
    ensureSpace(signatureHeight + 8f)
    val sigRect = RectF(margin, y, pageWidth - margin, y + signatureHeight)
    roundedRect(canvas!!, sigRect, 4f, paper, border)
    canvas!!.drawRect(margin, y, margin + 3.2f, y + signatureHeight, paint(3f, navy))
    canvas!!.drawRoundRect(RectF(margin + 3f, y + 1f, pageWidth - margin - 1f, y + 27f), 3f, 3f, paint(1f, paleBlue))
    canvas!!.drawText("Dyrektor szkoły:", margin + 14f, y + 18f, paint(9f, navy, true))
    if (draft.schoolDirector.isNotBlank()) {
        val directorPaint = paint(10f, ink, true).apply { typeface = avenirMedium }
        canvas!!.drawText(draft.schoolDirector, margin + 14f, y + 42f, directorPaint)
    }
    // Odręczny podpis jest umieszczony niżej, z większą przestrzenią nad linią.
    canvas!!.drawLine(margin + 14f, y + 155f, pageWidth - margin - 14f, y + 155f, paint(1f, border).apply { strokeWidth = 0.8f })
    canvas!!.drawText("Miejsce na odręczny podpis i pieczęć dyrektora szkoły", margin + 14f, y + 174f, paint(8f, muted))
    // Duża, pusta przestrzeń do ręcznego podpisu i przybicia pieczęci.
    y += signatureHeight + 10f

    finishPage()
    try {
        if (uri.scheme == "file") {
            val path = uri.path ?: throw IOException("Brak ścieżki pliku PDF")
            java.io.FileOutputStream(java.io.File(path)).use { output ->
                pdf.writeTo(output)
                output.flush()
            }
        } else {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                pdf.writeTo(output)
                output.flush()
            } ?: throw IOException("Nie można otworzyć pliku docelowego")
        }
    } finally {
        pdf.close()
    }
}



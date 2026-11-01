package com.example.opiniaucznia

import android.net.Uri
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MonitoringManagementPanel(
    studentId: String,
    studentName: String,
    store: MonitoringStore,
    initialMonitoring: StudentMonitoring,
    onMonitoringChanged: (StudentMonitoring) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var monitoring by remember(studentId) { mutableStateOf(initialMonitoring) }
    LaunchedEffect(initialMonitoring) { monitoring = initialMonitoring }
    var selectedPeriodId by remember(studentId) { mutableStateOf(initialMonitoring.periods.firstOrNull()?.id.orEmpty()) }
    var status by remember { mutableStateOf("") }
    var showAddTeacher by remember { mutableStateOf(false) }
    var showEditTeacher by remember { mutableStateOf(false) }
    var teacherName by remember { mutableStateOf("") }
    var teacherRole by remember { mutableStateOf("") }
    var teacherDate by remember { mutableStateOf("") }
    var showPreviousTeacherMenu by remember { mutableStateOf(false) }
    var teacherToDelete by remember { mutableStateOf<MonitoringTeacher?>(null) }
    var editingTeacherId by remember { mutableStateOf("") }
    var exportPeriod by remember { mutableStateOf<MonitoringPeriod?>(null) }
    var exportBlankCard by remember { mutableStateOf(false) }

    val selectedPeriod = monitoring.periods.firstOrNull { it.id == selectedPeriodId }
        ?: monitoring.periods.firstOrNull()

    fun persist(updated: StudentMonitoring, message: String) {
        if (store.save(studentId, updated)) {
            monitoring = updated
            onMonitoringChanged(updated)
            status = message
        } else {
            status = "Nie udało się zapisać zmian. Sprawdź, czy uczeń jest w archiwum."
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        val period = exportPeriod
        if (uri != null && period != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output -> if (exportBlankCard) writeMonitoringObservationCardPdf(context, output, studentName, period) else writeMonitoringPdf(context, output, studentName, period, monitoring.periods) }
                    ?: error("Nie udało się otworzyć pliku docelowego.")
            }
            status = if (result.isSuccess) "Zapisano osobny raport monitoringu w PDF."
            else "Nie udało się utworzyć PDF: ${result.exceptionOrNull()?.message ?: "błąd zapisu"}"
        }
        exportPeriod = null
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Monitoring — ${studentName.ifBlank { "uczeń" }}") },
            navigationIcon = { TextButton(onClick = onClose) { Text("Zamknij") } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Okres obserwacji", style = MaterialTheme.typography.titleLarge)
            if (monitoring.periods.isEmpty()) {
                Text("Nie ma jeszcze okresu obserwacji. Wróć do ekranu monitoringu i wybierz „Dodaj kolejną obserwację”.")
            } else {
                monitoring.periods.forEach { period ->
                    OutlinedButton(
                        onClick = { selectedPeriodId = period.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedPeriod?.id == period.id) "✓ ${period.title}" else period.title)
                    }
                }
            }

            selectedPeriod?.let { period ->
                HorizontalDivider()
                Text(period.title, style = MaterialTheme.typography.titleLarge)
                Text("Okres: ${period.startDate.ifBlank { "—" }} – ${period.endDate.ifBlank { "—" }}")
                if (period.observationAreas.isEmpty()) {
                    Text("Brak zapisanej karty obserwacji. Wróć do poprzedniego ekranu, przygotuj kartę AI i zapisz ją w tym okresie.")
                } else {
                    Button(onClick = {
                        teacherName = ""
                        teacherRole = ""
                        teacherDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        showAddTeacher = true
                    }, modifier = Modifier.fillMaxWidth()) { Text("Dodaj nauczyciela") }

                    Text("Skala: 1 — duże trudności; 2 — wyraźne trudności; 3 — częściowo prawidłowo; 4 — przeważnie prawidłowo; 5 — stabilnie i samodzielnie.")
                    Text("Średnia uwzględnia wyłącznie oceny od 1 do 5. Brak oceny i „Nie obserwowano” są pomijane.", style = MaterialTheme.typography.bodySmall)

                    val sortedPeriodsForTeachers = monitoring.periods.sortedWith(compareBy<MonitoringPeriod> { it.startDate.ifBlank { it.createdAt } }.thenBy { it.createdAt })
                    val periodsThroughSelected = sortedPeriodsForTeachers.takeWhile { it.id != period.id } + listOf(period)
                    val visibleTeacherEntries = periodsThroughSelected.flatMap { teacherPeriod ->
                        teacherPeriod.teachers.map { teacherPeriod to it }
                    }.distinctBy { it.first.id + "|" + it.second.id }
                    visibleTeacherEntries.forEach { (teacherPeriod, teacher) ->
                        val isCurrentPeriodTeacher = teacherPeriod.id == period.id
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) {
                                        Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                                        if (!isCurrentPeriodTeacher) Text("Poprzedni pomiar: ${teacherPeriod.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isCurrentPeriodTeacher) TextButton(onClick = { teacherToDelete = teacher }) { Text("✕ Usuń") }
                                }
                                Text(listOf(teacher.role, teacher.observationDate).filter { it.isNotBlank() }.joinToString(" • "))
                                Text("Wprowadzone oceny: ${teacher.ratings.count { it.rating != null }} / ${teacherPeriod.observationAreas.size}")
                                if (teacher.generalComment.isNotBlank()) Text("Komentarz: ${teacher.generalComment}")
                                if (isCurrentPeriodTeacher) OutlinedButton(onClick = { editingTeacherId = teacher.id; showEditTeacher = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Edytuj oceny i komentarze")
                                }
                            }
                        }
                    }

                    Text("Wykresy porównawcze i średnie", style = MaterialTheme.typography.titleLarge)
                    period.observationAreas.forEach { area ->
                        val scores = period.teachers.mapNotNull { teacher ->
                            teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
                                ?.takeIf { !it.notObserved }?.rating?.takeIf { it in 1..5 }
                        }
                        val average = scores.takeIf { it.isNotEmpty() }?.average()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(area, style = MaterialTheme.typography.titleSmall)
                                Text(if (average == null) "Średnia: brak ocen" else "Średnia: ${String.format(Locale("pl", "PL"), "%.2f", average)} / 5")
                                if (average != null) MonitoringBar((average / 5.0).toFloat(), Color(0xFFD32F2F))
                                period.teachers.forEach { teacher ->
                                    val rating = teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
                                    val score = rating?.rating?.takeIf { !rating.notObserved && it in 1..5 }
                                    val teacherColor = monitoringTeacherColor(period.teachers.indexOf(teacher))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(teacher.name, modifier = Modifier.weight(1f), color = teacherColor)
                                        Text(score?.let { "$it / 5" } ?: if (rating?.notObserved == true) "Nie obserwowano" else "Brak oceny")
                                    }
                                    if (score != null) MonitoringBar(score / 5f, teacherColor)
                                }
                            }
                        }
                    }

                    var showTrendChart by remember(period.id) { mutableStateOf(false) }
                    val orderedPeriods = monitoring.periods.sortedWith(compareBy<MonitoringPeriod> { it.startDate.ifBlank { it.createdAt } }.thenBy { it.createdAt })
                    if (orderedPeriods.size >= 2) {
                        OutlinedButton(onClick = { showTrendChart = !showTrendChart }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (showTrendChart) "Ukryj wykres zmian w czasie" else "Pokaż wykres liniowy zmian w czasie")
                        }
                        if (showTrendChart) {
                            val comparisonAreas = orderedPeriods.flatMap { it.observationAreas }.distinct()
                            val series = comparisonAreas.map { area ->
                                orderedPeriods.map { measurement ->
                                    val ratings = measurement.teachers.mapNotNull { teacher ->
                                        teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
                                            ?.takeIf { !it.notObserved }?.rating?.takeIf { it in 1..5 }
                                    }
                                    ratings.takeIf { it.isNotEmpty() }?.average()?.toFloat()
                                }
                            }
                            val colors = comparisonAreas.indices.map { monitoringTeacherColor(it + 1) }
                            val overallMeans = orderedPeriods.map { measurement ->
                                val ratings = measurement.teachers.flatMap { teacher ->
                                    measurement.observationAreas.mapNotNull { area ->
                                        teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
                                            ?.takeIf { !it.notObserved }?.rating?.takeIf { it in 1..5 }
                                    }
                                }
                                ratings.takeIf { it.isNotEmpty() }?.average()
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Zmiany średnich ocen w kolejnych pomiarach", style = MaterialTheme.typography.titleMedium)
                                    Text("Oś Y: średnia ocena (1–5). Oś X: numer pomiaru.", style = MaterialTheme.typography.bodySmall)
                                    Canvas(Modifier.fillMaxWidth().height(245.dp)) {
                                        val left = 34f; val right = size.width - 16f; val top = 25f; val bottom = size.height - 32f
                                        val native = drawContext.canvas.nativeCanvas
                                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 26f; color = android.graphics.Color.DKGRAY }
                                        for (value in 1..5) {
                                            val y = bottom - (value - 1) / 4f * (bottom - top)
                                            drawLine(Color.LightGray, Offset(left, y), Offset(right, y), strokeWidth = 1f)
                                            native.drawText(value.toString(), 7f, y + 8f, paint)
                                        }
                                        orderedPeriods.indices.forEach { index ->
                                            val x = if (orderedPeriods.size <= 1) (left + right) / 2f else left + index.toFloat() / (orderedPeriods.size - 1) * (right - left)
                                            native.drawText((index + 1).toString(), x - 7f, size.height - 5f, paint)
                                        }
                                        series.forEachIndexed { seriesIndex, values ->
                                            val points = values.mapIndexedNotNull { index, value ->
                                                value?.let {
                                                    val x = if (orderedPeriods.size <= 1) (left + right) / 2f else left + index.toFloat() / (orderedPeriods.size - 1) * (right - left)
                                                    val y = bottom - ((it - 1f) / 4f) * (bottom - top)
                                                    index to Offset(x, y)
                                                }
                                            }
                                            points.zipWithNext().forEach { (a, b) -> if (b.first == a.first + 1) drawLine(colors[seriesIndex], a.second, b.second, strokeWidth = 3f, cap = StrokeCap.Round) }
                                            points.forEach { point ->
                                                drawCircle(colors[seriesIndex], radius = 5f, center = point.second)
                                                val label = String.format(Locale("pl", "PL"), "%.2f", values[point.first])
                                                val labelWidth = paint.measureText(label)
                                                val labelX = (point.second.x - labelWidth / 2f).coerceIn(left, right - labelWidth)
                                                val labelY = if (seriesIndex % 2 == 0 || point.second.y < top + 20f) point.second.y + 22f else point.second.y - 10f
                                                paint.color = colors[seriesIndex].toArgb()
                                                native.drawText(label, labelX, labelY, paint)
                                                paint.color = android.graphics.Color.DKGRAY
                                            }
                                        }
                                    }
                                    Text("Numer pomiaru:", style = MaterialTheme.typography.titleSmall)
                                    orderedPeriods.forEachIndexed { index, measurement ->
                                        Text("${index + 1} — ${measurement.title} (${measurement.startDate.ifBlank { measurement.createdAt }})", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text("Legenda obszarów:", style = MaterialTheme.typography.titleSmall)
                                    comparisonAreas.forEachIndexed { index, area ->
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(area, color = colors[index], style = MaterialTheme.typography.bodyMedium)
                                            series[index].drop(1).forEachIndexed { offset, value ->
                                                val previous = series[index][offset]
                                                if (previous != null && value != null) {
                                                    val delta = value - previous
                                                    Text("Zmiana pomiaru ${offset + 1} → ${offset + 2}: " + (if (delta > 0) "+" else "") + String.format(Locale("pl", "PL"), "%.2f", delta) + " pkt", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider()
                                    Text("Średnia ogólna i zmiana między pomiarami", style = MaterialTheme.typography.titleSmall)
                                    overallMeans.forEachIndexed { index, mean ->
                                        Text("Pomiar ${index + 1}: " + (mean?.let { String.format(Locale("pl", "PL"), "%.2f", it) + "/5" } ?: "brak danych"), style = MaterialTheme.typography.bodySmall)
                                        if (index > 0 && mean != null && overallMeans[index - 1] != null) {
                                            val delta = mean - overallMeans[index - 1]!!
                                            Text("Zmiana względem poprzedniego: " + (if (delta > 0) "+" else "") + String.format(Locale("pl", "PL"), "%.2f", delta) + " pkt", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val allScores = period.teachers.flatMap { teacher ->
                        period.observationAreas.mapNotNull { area ->
                            teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }
                                ?.takeIf { !it.notObserved }?.rating?.takeIf { it in 1..5 }
                        }
                    }
                    val averageOverall = allScores.takeIf { it.isNotEmpty() }?.average()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Średnia ogólna okresu", style = MaterialTheme.typography.titleMedium)
                            Text(if (averageOverall == null) "Brak ocen." else "${String.format(Locale("pl", "PL"), "%.2f", averageOverall)} / 5")
                            if (averageOverall != null) MonitoringBar((averageOverall / 5.0).toFloat(), Color(0xFFD32F2F))
                        }
                    }
                }

                OutlinedTextField(
                    value = period.summary,
                    onValueChange = { text ->
                        val changed = period.copy(summary = text)
                        persist(monitoring.copy(periods = monitoring.periods.map { if (it.id == period.id) changed else it }), "Zapisano wnioski.")
                    },
                    label = { Text("Wnioski z monitoringu") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                OutlinedTextField(
                    value = period.recommendations,
                    onValueChange = { text ->
                        val changed = period.copy(recommendations = text)
                        persist(monitoring.copy(periods = monitoring.periods.map { if (it.id == period.id) changed else it }), "Zapisano zalecenia.")
                    },
                    label = { Text("Zalecenia do dalszej pracy") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(onClick = {
                    exportPeriod = period
                    exportBlankCard = false
                    val safe = studentName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ucznia" }
                    exportLauncher.launch("Monitoring_${safe}_${period.startDate.ifBlank { "okres" }}.pdf")
                }, modifier = Modifier.fillMaxWidth()) { Text("Eksportuj osobny raport monitoringu do PDF") }
                OutlinedButton(onClick = {
                    exportPeriod = period
                    exportBlankCard = true
                    exportLauncher.launch("Karta_obserwacji.pdf")
                }, modifier = Modifier.fillMaxWidth()) { Text("Eksportuj pustą kartę dla nauczyciela do PDF") }
                OutlinedButton(onClick = {
                    val safe = studentName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ucznia" }
                    val file = File(context.cacheDir, "Monitoring_${safe}_druk.pdf")
                    runCatching {
                        FileOutputStream(file).use { writeMonitoringPdf(context, it, studentName, period, monitoring.periods) }
                        check(file.exists() && file.length() > 0L) { "Plik PDF jest pusty." }
                        printPdfFile(context, file, "Monitoring ucznia - $studentName")
                    }.onSuccess { status = "Przygotowano osobny raport do wydruku." }
                     .onFailure { status = "Nie udało się przygotować raportu: ${it.message ?: "błąd"}" }
                }, modifier = Modifier.fillMaxWidth()) { Text("Drukuj raport monitoringu") }
            }
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
    }

    if (teacherToDelete != null && selectedPeriod != null) {
        val target = teacherToDelete!!
        AlertDialog(
            onDismissRequest = { teacherToDelete = null },
            title = { Text("Usunąć nauczyciela z okresu?") },
            text = { Text("Nauczyciel „${target.name}” wraz z ocenami i komentarzami zostanie usunięty wyłącznie z okresu „${selectedPeriod.title}”.") },
            confirmButton = {
                Button(onClick = {
                    val changedPeriod = selectedPeriod.copy(teachers = selectedPeriod.teachers.filterNot { it.id == target.id })
                    persist(monitoring.copy(periods = monitoring.periods.map { if (it.id == changedPeriod.id) changedPeriod else it }), "Usunięto nauczyciela z bieżącego okresu.")
                    teacherToDelete = null
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { teacherToDelete = null }) { Text("Anuluj") } }
        )
    }

    if (showAddTeacher && selectedPeriod != null) {
        AlertDialog(
            onDismissRequest = { showAddTeacher = false },
            title = { Text("Dodaj nauczyciela") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val previousTeachers = monitoring.periods
                        .filter { it.id != selectedPeriod.id }
                        .flatMap { it.teachers }
                        .distinctBy { it.name.trim().lowercase(Locale.getDefault()) + "|" + it.role.trim().lowercase(Locale.getDefault()) }
                        .filterNot { candidate ->
                            selectedPeriod.teachers.any {
                                it.name.trim().equals(candidate.name.trim(), ignoreCase = true) &&
                                    it.role.trim().equals(candidate.role.trim(), ignoreCase = true)
                            }
                        }
                    if (previousTeachers.isNotEmpty()) {
                        Column {
                            OutlinedButton(onClick = { showPreviousTeacherMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (teacherName.isBlank()) "Wybierz nauczyciela z poprzedniego okresu" else "Wybrano: $teacherName")
                            }
                            DropdownMenu(expanded = showPreviousTeacherMenu, onDismissRequest = { showPreviousTeacherMenu = false }) {
                                previousTeachers.forEach { previousTeacher ->
                                    DropdownMenuItem(
                                        text = { Text(listOf(previousTeacher.name, previousTeacher.role).filter { it.isNotBlank() }.joinToString(" • ")) },
                                        onClick = {
                                            teacherName = previousTeacher.name
                                            teacherRole = previousTeacher.role
                                            showPreviousTeacherMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Imię i nazwisko") }, singleLine = true)
                    OutlinedTextField(value = teacherRole, onValueChange = { teacherRole = it }, label = { Text("Rola / przedmiot / funkcja") }, singleLine = true)
                    OutlinedButton(onClick = { val calendar = Calendar.getInstance(); teacherDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }; DatePickerDialog(context, { _, y, m, d -> teacherDate = "%04d-%02d-%02d".format(y, m + 1, d) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.fillMaxWidth()) { Text("Data obserwacji: " + teacherDate.ifBlank { "wybierz z kalendarza" }) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val period = selectedPeriod
                    if (teacherName.isBlank()) status = "Podaj imię i nazwisko nauczyciela."
                    else if (period == null) status = "Wybierz okres obserwacji."
                    else {
                        val teacher = MonitoringTeacher(
                            id = UUID.randomUUID().toString(), name = teacherName.trim(), role = teacherRole.trim(),
                            observationDate = teacherDate.trim(),
                            ratings = period.observationAreas.map { MonitoringRating(areaId = it, areaName = it) }
                        )
                        val changed = period.copy(teachers = period.teachers + teacher)
                        persist(monitoring.copy(periods = monitoring.periods.map { if (it.id == period.id) changed else it }), "Dodano nauczyciela.")
                        selectedPeriodId = period.id
                        editingTeacherId = teacher.id
                        showAddTeacher = false
                        showEditTeacher = true
                    }
                }) { Text("Dodaj i wprowadź oceny") }
            },
            dismissButton = { OutlinedButton(onClick = { showAddTeacher = false }) { Text("Anuluj") } }
        )
    }

    if (showEditTeacher && selectedPeriod != null) {
        var draft by remember(editingTeacherId, selectedPeriod.id) {
            mutableStateOf(selectedPeriod.teachers.firstOrNull { it.id == editingTeacherId })
        }
        AlertDialog(
            onDismissRequest = { showEditTeacher = false },
            title = { Text("Oceny nauczyciela") },
            text = {
                Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (draft == null) Text("Nie znaleziono nauczyciela.")
                    else {
                        OutlinedTextField(value = draft!!.name, onValueChange = { draft = draft!!.copy(name = it) }, label = { Text("Imię i nazwisko") }, singleLine = true)
                        OutlinedTextField(value = draft!!.role, onValueChange = { draft = draft!!.copy(role = it) }, label = { Text("Rola / przedmiot / funkcja") }, singleLine = true)
                        OutlinedButton(onClick = { val calendar = Calendar.getInstance(); draft!!.observationDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }; DatePickerDialog(context, { _, y, m, d -> draft = draft!!.copy(observationDate = "%04d-%02d-%02d".format(y, m + 1, d)) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show() }, modifier = Modifier.fillMaxWidth()) { Text("Data obserwacji: " + draft!!.observationDate.ifBlank { "wybierz z kalendarza" }) }
                        selectedPeriod.observationAreas.forEach { area ->
                            val item = draft!!.ratings.firstOrNull { it.areaId == area || it.areaName == area } ?: MonitoringRating(areaId = area, areaName = area)
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(area, style = MaterialTheme.typography.titleSmall)
                                    Text("Ocena (1–5):", style = MaterialTheme.typography.bodySmall)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        (1..5).forEach { score ->
                                            OutlinedButton(onClick = {
                                                val rating = item.copy(rating = score, notObserved = false)
                                                draft = draft!!.copy(ratings = draft!!.ratings.filterNot { it.areaId == area || it.areaName == area } + rating)
                                            }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                                                Text(if (item.rating == score) "✓$score" else score.toString())
                                            }
                                        }
                                    }
                                    OutlinedButton(onClick = {
                                        val rating = item.copy(rating = null, notObserved = true)
                                        draft = draft!!.copy(ratings = draft!!.ratings.filterNot { it.areaId == area || it.areaName == area } + rating)
                                    }, modifier = Modifier.fillMaxWidth()) { Text(if (item.notObserved) "✓ Nie obserwowano" else "Nie obserwowano") }
                                    OutlinedTextField(value = item.comment, onValueChange = { comment ->
                                        val rating = item.copy(comment = comment)
                                        draft = draft!!.copy(ratings = draft!!.ratings.filterNot { it.areaId == area || it.areaName == area } + rating)
                                    }, label = { Text("Komentarz do obszaru") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                                }
                            }
                        }
                        OutlinedTextField(value = draft!!.generalComment, onValueChange = { draft = draft!!.copy(generalComment = it) }, label = { Text("Komentarz ogólny") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val teacher = draft
                    val period = selectedPeriod
                    if (teacher == null || period == null) showEditTeacher = false
                    else if (teacher.name.isBlank()) status = "Imię i nazwisko nauczyciela nie może być puste."
                    else {
                        val changed = period.copy(teachers = period.teachers.map { if (it.id == teacher.id) teacher else it })
                        persist(monitoring.copy(periods = monitoring.periods.map { if (it.id == period.id) changed else it }), "Zapisano oceny i komentarze nauczyciela.")
                        showEditTeacher = false
                    }
                }) { Text("Zapisz oceny") }
            },
            dismissButton = { OutlinedButton(onClick = { showEditTeacher = false }) { Text("Anuluj") } }
        )
    }
}

private fun monitoringTeacherColor(index: Int): Color = listOf(
    Color(0xFF247B55), Color(0xFF1976D2), Color(0xFF7E57C2),
    Color(0xFFE67E22), Color(0xFF00897B), Color(0xFF3949AB),
    Color(0xFF5D4037), Color(0xFF546E7A)
)[index.mod(8)]

@Composable
private fun MonitoringBar(value: Float, barColor: Color = Color(0xFF247B55)) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(barColor, RoundedCornerShape(8.dp)))
    }
}

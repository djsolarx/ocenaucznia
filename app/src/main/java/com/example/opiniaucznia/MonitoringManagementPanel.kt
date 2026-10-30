package com.example.opiniaucznia

import android.net.Uri
import android.app.DatePickerDialog
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
                context.contentResolver.openOutputStream(uri)?.use { output -> if (exportBlankCard) writeMonitoringObservationCardPdf(context, output, studentName, period) else writeMonitoringPdf(context, output, studentName, period) }
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
                    Text("Średnia uwzględnia wszystkich nauczycieli. Brak oceny i „Nie obserwowano” są liczone jako 0.", style = MaterialTheme.typography.bodySmall)

                    period.teachers.forEach { teacher ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(teacher.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(teacher.role, teacher.observationDate).filter { it.isNotBlank() }.joinToString(" • "))
                                Text("Wprowadzone oceny: ${teacher.ratings.count { it.rating != null }} / ${period.observationAreas.size}")
                                if (teacher.generalComment.isNotBlank()) Text("Komentarz: ${teacher.generalComment}")
                                OutlinedButton(onClick = { editingTeacherId = teacher.id; showEditTeacher = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Edytuj oceny i komentarze")
                                }
                            }
                        }
                    }

                    Text("Wykresy porównawcze i średnie", style = MaterialTheme.typography.titleLarge)
                    period.observationAreas.forEach { area ->
                        val scores = period.teachers.map { teacher ->
                            teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }?.rating ?: 0
                        }
                        val average = if (scores.isEmpty()) 0.0 else scores.average()
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(area, style = MaterialTheme.typography.titleSmall)
                                Text("Średnia: ${String.format(Locale("pl", "PL"), "%.2f", average)} / 5")
                                MonitoringBar((average / 5.0).toFloat())
                                period.teachers.forEach { teacher ->
                                    val score = teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }?.rating ?: 0
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(teacher.name, modifier = Modifier.weight(1f))
                                        Text(if (score == 0) "0 — brak oceny" else "$score / 5")
                                    }
                                    MonitoringBar(score / 5f)
                                }
                            }
                        }
                    }

                    val allScores = period.teachers.flatMap { teacher ->
                        period.observationAreas.map { area ->
                            teacher.ratings.firstOrNull { it.areaId == area || it.areaName == area }?.rating ?: 0
                        }
                    }
                    val averageOverall = if (allScores.isEmpty()) null else allScores.average()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Średnia ogólna okresu", style = MaterialTheme.typography.titleMedium)
                            Text(if (averageOverall == null) "Brak ocen." else "${String.format(Locale("pl", "PL"), "%.2f", averageOverall)} / 5")
                            if (averageOverall != null) MonitoringBar((averageOverall / 5.0).toFloat())
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
                        FileOutputStream(file).use { writeMonitoringPdf(context, it, studentName, period) }
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

    if (showAddTeacher && selectedPeriod != null) {
        AlertDialog(
            onDismissRequest = { showAddTeacher = false },
            title = { Text("Dodaj nauczyciela") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
private fun MonitoringBar(value: Float) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
    }
}

package com.example.opiniaucznia

import android.content.Intent
import android.app.DatePickerDialog
import java.util.Calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    studentId: String,
    studentName: String,
    observationSource: String,
    store: MonitoringStore,
    onBack: () -> Unit
) {
    var monitoring by remember(studentId) {
        mutableStateOf(store.load(studentId))
    }
    LaunchedEffect(studentId) {
        val latest = store.load(studentId)
        val template = latest.periods.firstOrNull { it.observationAreas.isNotEmpty() }
        if (template != null) {
            val normalized = latest.copy(periods = latest.periods.map { period ->
                if (period.observationAreas.isEmpty()) {
                    period.copy(
                        observationAreas = template.observationAreas,
                        cardContent = template.cardContent
                    )
                } else period
            })
            if (normalized != latest && store.save(studentId, normalized)) {
                monitoring = normalized
            }
        }
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var periodToDelete by remember { mutableStateOf<MonitoringPeriod?>(null) }
    var periodToEdit by remember { mutableStateOf<MonitoringPeriod?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editStartDate by remember { mutableStateOf("") }
    var editEndDate by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showPromptDialog by remember { mutableStateOf(false) }
    var importedAreas by remember { mutableStateOf(monitoring.periods.firstOrNull { it.observationAreas.isNotEmpty() }?.observationAreas.orEmpty()) }
    var showManagementPanel by remember { mutableStateOf(false) }
    val existingCard = monitoring.periods.firstOrNull { it.observationAreas.isNotEmpty() }
    val hasMonitoringCard = existingCard != null
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val monitoringPrompt = """
Przygotuj kartę monitorowania ucznia na podstawie poniższych wniosków do dalszej obserwacji.

ZASADY:
1. Wykorzystuj wyłącznie informacje z przekazanego punktu 3.
2. Nie stawiaj diagnoz i nie dopisuj informacji o uczniu, których nie ma w materiale.
3. Utwórz od 3 do 10 konkretnych, możliwych do zaobserwowania obszarów.
4. Sformułuj obszary neutralnie, tak aby nauczyciel mógł ocenić zachowanie w codziennej pracy.
5. Zwróć wyłącznie poprawny JSON, bez markdown, bez ``` i bez komentarzy.
6. Zastosuj dokładnie następującą strukturę:
{
  "title": "Karta monitorowania ucznia",
  "observationAreas": [
    "Konkretny obszar obserwacji 1",
    "Konkretny obszar obserwacji 2"
  ],
  "instructions": "Instrukcja dla nauczyciela",
  "ratingScale": [
    {"value": 1, "label": "Sporadycznie, duże trudności"},
    {"value": 2, "label": "Wyraźne trudności"},
    {"value": 3, "label": "Częściowo prawidłowo, zależnie od wsparcia"},
    {"value": 4, "label": "Przeważnie prawidłowo"},
    {"value": 5, "label": "Stabilnie i samodzielnie"}
  ]
}

PUNKT 3 — WNIOSKI DO DALSZEJ OBSERWACJI:
$observationSource
""".trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitoring ucznia") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Powrót")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(studentName, style = MaterialTheme.typography.headlineSmall)

            Text(
                "Źródło monitoringu: wnioski do dalszej obserwacji",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                if (observationSource.isBlank()) {
                    "Brak przekazanego punktu 3. Wróć do opinii i wklej pełną odpowiedź AI."
                } else {
                    "Punkt 3 został przekazany. Prompt dla AI będzie oparty na tych wnioskach."
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (hasMonitoringCard) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Obszary monitorowania są już zapisane", style = MaterialTheme.typography.titleMedium)
                        Text("Ta sama karta obowiązuje we wszystkich okresach obserwacji. Nie trzeba ponownie generować promptu ani obszarów.")
                        Text("Liczba obszarów: " + (existingCard?.observationAreas?.size ?: 0))
                    }
                }
            } else {
                if (monitoring.periods.isEmpty()) {
                    Text("Najpierw dodaj pierwszy okres obserwacji. Kartę obszarów przygotowuje się tylko raz.")
                }
                Button(
                    onClick = { showPromptDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = observationSource.isNotBlank() && monitoring.periods.isNotEmpty(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF187A72), contentColor = Color.White)
                ) { Text("Przygotuj prompt dla AI") }
                Text("Po otrzymaniu odpowiedzi AI skopiuj cały JSON. Wróć do aplikacji i naciśnij „Zapisz obszary” — aplikacja pobierze odpowiedź bezpośrednio ze schowka.", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        try {
                            val clipboardText = clipboardManager.getText()?.text.orEmpty()
                            if (clipboardText.isBlank()) {
                                status = "Schowek jest pusty. Skopiuj najpierw odpowiedź AI w formacie JSON."
                            } else {
                                val decoded = Regex("&#(x[0-9a-fA-F]+|[0-9]+);").replace(clipboardText) { match ->
                                    runCatching {
                                        val code = match.groupValues[1]
                                        val number = if (code.startsWith("x", true)) code.substring(1).toInt(16) else code.toInt()
                                        String(Character.toChars(number))
                                    }.getOrDefault(match.value)
                                }.replace("&quot;", "\"").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                                val normalizedText = decoded.replace(Regex("\\\\\\s*\\r?\\n"), "")
                                val firstBrace = normalizedText.indexOf('{')
                                val lastBrace = normalizedText.lastIndexOf('}')
                                if (firstBrace < 0 || lastBrace <= firstBrace) throw IllegalArgumentException("Brak obiektu JSON")
                                val json = org.json.JSONObject(normalizedText.substring(firstBrace, lastBrace + 1))
                                val areasJson = json.optJSONArray("observationAreas") ?: throw IllegalArgumentException("Brak listy observationAreas")
                                val areas = (0 until areasJson.length()).mapNotNull { areasJson.opt(it) as? String }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
                                if (areas.size < 3) {
                                    status = "Odpowiedź JSON zawiera mniej niż 3 prawidłowe obszary obserwacji."
                                } else if (areas.size > 10) {
                                    status = "Odpowiedź JSON zawiera więcej niż 10 obszarów. Poproś AI o przygotowanie krótszej karty."
                                } else {
                                    val normalizedJson = json.toString(2)
                                    val updated = monitoring.copy(periods = monitoring.periods.map { it.copy(observationAreas = areas, cardContent = normalizedJson) })
                                    if (store.save(studentId, updated)) {
                                        monitoring = updated
                                        importedAreas = areas
                                        status = "Zapisano " + areas.size + " obszarów. Ta sama karta będzie używana we wszystkich okresach obserwacji."
                                    } else status = "Nie udało się zapisać obszarów. Sprawdź, czy uczeń jest zapisany w archiwum."
                                }
                            }
                        } catch (e: Exception) {
                            status = "Nie znaleziono poprawnego JSON-a w schowku. Skopiuj z czatu AI samą odpowiedź JSON i spróbuj ponownie."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !hasMonitoringCard && monitoring.periods.isNotEmpty()
                ) { Text("Zapisz obszary") }
            }
            if (importedAreas.isNotEmpty() && hasMonitoringCard) {
                Text("Obszary monitorowania:", style = MaterialTheme.typography.titleSmall)
                importedAreas.forEachIndexed { index, area -> Text((index + 1).toString() + ". " + area) }
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }

            Text(
                "Okresy obserwacji",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {
                    title = ""
                    startDate = SimpleDateFormat(
                        "yyyy-MM-dd", Locale.getDefault()
                    ).format(Date())
                    endDate = ""
                    showAddDialog = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dodaj okres obserwacji")
            }

            if (monitoring.periods.isEmpty()) {
                Text("Nie dodano jeszcze żadnych okresów obserwacji.")
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    monitoring.periods.forEach { period ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    period.title,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Od: ${period.startDate.ifBlank { "nie podano" }}")
                                Text("Do: ${period.endDate.ifBlank { "nie podano" }}")
                                Text("Nauczyciele: ${period.teachers.size}")
                                OutlinedButton(onClick = {
                                    periodToEdit = period
                                    editTitle = period.title
                                    editStartDate = period.startDate
                                    editEndDate = period.endDate
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Edytuj okres obserwacji")
                                }
                                OutlinedButton(onClick = { periodToDelete = period }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Usuń okres obserwacji")
                                }
                            }
                        }
                    }
                }
            }

            Text("Wygenerowano: ${SimpleDateFormat("dd.MM.yyyy", Locale("pl", "PL")).format(Date())} r. • Dokument poufny", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = { showManagementPanel = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Otwórz monitoring: oceny, średnie i raport")
            }

        }
    }

    if (periodToEdit != null) {
        val target = periodToEdit!!
        AlertDialog(
            onDismissRequest = { periodToEdit = null },
            title = { Text("Edytuj okres obserwacji") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editTitle, onValueChange = { editTitle = it }, label = { Text("Nazwa okresu") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        val calendar = Calendar.getInstance()
                        editStartDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }
                        DatePickerDialog(context, { _, year, month, day -> editStartDate = "%04d-%02d-%02d".format(year, month + 1, day) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Data rozpoczęcia: ${editStartDate.ifBlank { "wybierz z kalendarza" }}") }
                    OutlinedButton(onClick = {
                        val calendar = Calendar.getInstance()
                        editEndDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }
                        DatePickerDialog(context, { _, year, month, day -> editEndDate = "%04d-%02d-%02d".format(year, month + 1, day) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Data zakończenia: ${editEndDate.ifBlank { "wybierz z kalendarza" }}") }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (editTitle.isBlank() || editStartDate.isBlank()) {
                        status = "Podaj nazwę i datę rozpoczęcia okresu."
                    } else {
                        fun dateKey(value: String): Int = value.split("-").mapNotNull { it.toIntOrNull() }
                            .let { if (it.size == 3) it[0] * 10000 + it[1] * 100 + it[2] else 0 }
                        if (editEndDate.isNotBlank() && dateKey(editEndDate) < dateKey(editStartDate)) {
                            status = "Data zakończenia nie może być wcześniejsza niż data rozpoczęcia."
                        } else {
                            val updated = monitoring.copy(periods = monitoring.periods.map {
                                if (it.id == target.id) it.copy(title = editTitle.trim(), startDate = editStartDate.trim(), endDate = editEndDate.trim()) else it
                            })
                            if (store.save(studentId, updated)) {
                                monitoring = updated
                                status = "Zaktualizowano okres obserwacji."
                                periodToEdit = null
                            } else status = "Nie udało się zapisać zmian okresu."
                        }
                    }
                }) { Text("Zapisz zmiany") }
            },
            dismissButton = { TextButton(onClick = { periodToEdit = null }) { Text("Anuluj") } }
        )
    }

    if (periodToDelete != null) {
        val target = periodToDelete!!
        AlertDialog(onDismissRequest = { periodToDelete = null }, title = { Text("Usunąć okres obserwacji?") }, text = { Text("Okres „${target.title}” oraz zapisane w nim oceny i komentarze zostaną usunięte. Tej operacji nie można cofnąć.") }, confirmButton = { Button(onClick = {
            val updated = monitoring.copy(periods = monitoring.periods.filterNot { it.id == target.id })
            if (store.save(studentId, updated)) { monitoring = updated; status = "Usunięto okres obserwacji: ${target.title}." } else status = "Nie udało się usunąć okresu obserwacji."
            periodToDelete = null
        }) { Text("Usuń") } }, dismissButton = { TextButton(onClick = { periodToDelete = null }) { Text("Anuluj") } })
    }

    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Prompt do czatu AI") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Udostępnij prompt do wybranego czatu AI. Gdy AI przygotuje odpowiedź, skopiuj cały JSON, wróć do aplikacji i użyj przycisku „Zapisz obszary”. Nie trzeba niczego wklejać do pola tekstowego.")
                    OutlinedTextField(
                        value = monitoringPrompt,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        minLines = 8
                    )
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(monitoringPrompt))
                            status = "Prompt skopiowano. Otwórz wybrany czat AI i wklej prompt."
                            showPromptDialog = false
                        }
                    ) {
                        Text("Kopiuj prompt")
                    }
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, monitoringPrompt)
                                putExtra(Intent.EXTRA_TITLE, "Prompt do karty monitorowania ucznia")
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(intent, "Udostępnij prompt do czatu AI")
                                )
                            }.onFailure {
                                status = "Nie udało się otworzyć menu udostępniania. Użyj opcji „Kopiuj prompt”."
                            }
                            showPromptDialog = false
                        }
                    ) {
                        Text("Udostępnij do czatu AI")
                    }
                    TextButton(onClick = { showPromptDialog = false }) {
                        Text("Zamknij")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nowy okres obserwacji") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Nazwa okresu") },
                        singleLine = true
                    )
                    OutlinedButton(onClick = {
                        val calendar = Calendar.getInstance()
                        startDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }
                        DatePickerDialog(context, { _, year, month, day -> startDate = "%04d-%02d-%02d".format(year, month + 1, day) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Data rozpoczęcia: ${startDate.ifBlank { "wybierz z kalendarza" }}") }
                    OutlinedButton(onClick = {
                        val calendar = Calendar.getInstance()
                        endDate.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { calendar.set(it[0], it[1] - 1, it[2]) }
                        DatePickerDialog(context, { _, year, month, day -> endDate = "%04d-%02d-%02d".format(year, month + 1, day) }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Data zakończenia: ${endDate.ifBlank { "wybierz z kalendarza" }}") }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank() || startDate.isBlank()) {
                            status = "Podaj nazwę i datę rozpoczęcia okresu."
                        } else {
                            val template = monitoring.periods.lastOrNull { it.observationAreas.isNotEmpty() }
                            val period = MonitoringPeriod(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                startDate = startDate.trim(),
                                endDate = endDate.trim(),
                                createdAt = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                ).format(Date()),
                                observationAreas = template?.observationAreas.orEmpty(),
                                cardContent = template?.cardContent.orEmpty(),
                                teachers = emptyList()
                            )
                            val updated = monitoring.copy(
                                periods = monitoring.periods + period
                            )

                            if (store.save(studentId, updated)) {
                                monitoring = updated
                                status = "Zapisano nowy okres obserwacji."
                                showAddDialog = false
                            } else {
                                status = "Nie udało się zapisać monitoringu. Sprawdź, czy uczeń jest zapisany w bazie."
                            }
                        }
                    }
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
    if (showManagementPanel) {
        Dialog(onDismissRequest = { showManagementPanel = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(modifier = Modifier.fillMaxSize()) {
                MonitoringManagementPanel(
                    studentId = studentId,
                    studentName = studentName,
                    store = store,
                    initialMonitoring = monitoring,
                    onMonitoringChanged = { monitoring = it },
                    onClose = { showManagementPanel = false }
                )
            }
        }
    }

}

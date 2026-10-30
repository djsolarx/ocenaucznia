package com.example.opiniaucznia

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
    var showAddDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var showPromptDialog by remember { mutableStateOf(false) }
    var aiMonitoringJson by remember { mutableStateOf("") }
    var importedAreas by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedPeriodId by remember { mutableStateOf("") }
    var showManagementPanel by remember { mutableStateOf(false) }
    var validatedCardJson by remember { mutableStateOf("") }

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

            Button(
                onClick = { showPromptDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = observationSource.isNotBlank()
            ) {
                Text("Przygotuj prompt dla AI")
            }

            OutlinedTextField(
                value = aiMonitoringJson,
                onValueChange = { aiMonitoringJson = it },
                label = { Text("Odpowiedź AI — wklej JSON karty monitorowania") },
                placeholder = { Text("Wklej tutaj sam obiekt JSON zwrócony przez AI") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5
            )

            Button(
                onClick = {
                    try {
                        val json = org.json.JSONObject(aiMonitoringJson)
                        val areasJson = json.optJSONArray("observationAreas")
                        val areas = if (areasJson == null) {
                            emptyList()
                        } else {
                            (0 until areasJson.length())
                                .map { areasJson.optString(it).trim() }
                                .filter { it.isNotBlank() }
                        }

                        if (areas.size < 3) {
                            status = "JSON jest poprawny, ale musi zawierać co najmniej 3 obszary obserwacji."
                        } else if (areas.size > 10) {
                            status = "JSON zawiera więcej niż 10 obszarów. Poproś AI o przygotowanie krótszej karty."
                        } else {
                            importedAreas = areas.distinct()
                            validatedCardJson = json.toString(2)
                            status = "Sprawdzono JSON. Wykryto ${importedAreas.size} obszarów. Wybierz okres obserwacji, aby zapisać kartę."
                        }
                    } catch (e: Exception) {
                        status = "Nieprawidłowy JSON. Wklej sam obiekt JSON bez markdown i dodatkowego komentarza."
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = aiMonitoringJson.isNotBlank()
            ) {
                Text("Sprawdź JSON karty")
            }

            if (importedAreas.isNotEmpty()) {
                Text(
                    "Obszary rozpoznane w JSON:",
                    style = MaterialTheme.typography.titleSmall
                )
                importedAreas.forEachIndexed { index, area ->
                    Text("${index + 1}. $area")
                }
            }
            if (validatedCardJson.isNotBlank() && importedAreas.isNotEmpty()) {
                Text(
                    "Zapis karty w okresie obserwacji",
                    style = MaterialTheme.typography.titleMedium
                )

                if (monitoring.periods.isEmpty()) {
                    Text("Najpierw dodaj okres obserwacji.")
                } else {
                    monitoring.periods.forEach { period ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { selectedPeriodId = period.id }
                            ) {
                                Text(
                                    if (selectedPeriodId == period.id) {
                                        "✓ ${period.title}"
                                    } else {
                                        period.title
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val selected = monitoring.periods.firstOrNull {
                                it.id == selectedPeriodId
                            }

                            if (selected == null) {
                                status = "Wybierz okres obserwacji."
                            } else {
                                val updatedPeriod = selected.copy(
                                    observationAreas = importedAreas,
                                    cardContent = validatedCardJson
                                )
                                val updated = monitoring.copy(
                                    periods = monitoring.periods.map {
                                        if (it.id == selected.id) updatedPeriod else it
                                    }
                                )

                                if (store.save(studentId, updated)) {
                                    monitoring = updated
                                    status = "Zapisano kartę w okresie: ${selected.title}. Istniejące oceny nauczycieli zostały zachowane."
                                    validatedCardJson = ""
                                    aiMonitoringJson = ""
                                    importedAreas = emptyList()
                                    selectedPeriodId = ""
                                } else {
                                    status = "Nie udało się zapisać karty. Sprawdź, czy rekord ucznia istnieje w archiwum."
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Zapisz kartę w wybranym okresie")
                    }
                }
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
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = monitoring.periods,
                        key = { it.id }
                    ) { period ->
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
                            }
                        }
                    }
                }
            }

            Button(onClick = { showManagementPanel = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Otwórz monitoring: oceny, średnie i raport")
            }

            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = { Text("Prompt do osobnego czatu AI") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Skopiuj prompt poniżej i wklej go do osobnego czatu AI. Następnie skopiuj odpowiedź JSON z powrotem do pola na ekranie monitoringu.")
                    OutlinedTextField(
                        value = monitoringPrompt,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 10
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromptDialog = false }) {
                    Text("Zamknij")
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
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Data rozpoczęcia (RRRR-MM-DD)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Data zakończenia (RRRR-MM-DD)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isBlank() || startDate.isBlank()) {
                            status = "Podaj nazwę i datę rozpoczęcia okresu."
                        } else {
                            val period = MonitoringPeriod(
                                id = UUID.randomUUID().toString(),
                                title = title.trim(),
                                startDate = startDate.trim(),
                                endDate = endDate.trim(),
                                createdAt = SimpleDateFormat(
                                    "yyyy-MM-dd HH:mm",
                                    Locale.getDefault()
                                ).format(Date())
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

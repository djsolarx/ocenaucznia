package com.example.opiniaucznia

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun IndicatorAssessmentScreen(
    indicators: List<AssessmentIndicator>,
    currentIndex: Int,
    store: IndicatorAssessmentStore,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (indicators.isEmpty()) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("Nie znaleziono wskaźników w katalogu.", style = MaterialTheme.typography.titleMedium)
            Text("Sprawdź, czy plik struktura_wskaznikow.json znajduje się w app/src/main/assets/.")
        }
        return
    }
    val safeIndex = currentIndex.coerceIn(0, indicators.lastIndex)
    val indicator = indicators[safeIndex]
    var selectedRating by rememberSaveable(indicator.id) { mutableStateOf(store.getAnswer(indicator.id).rating) }
    var noData by rememberSaveable(indicator.id) { mutableStateOf(store.getAnswer(indicator.id).noData) }
    var notes by rememberSaveable(indicator.id) { mutableStateOf(store.getAnswer(indicator.id).notes) }
    LaunchedEffect(indicator.id) {
        val saved = store.getAnswer(indicator.id)
        selectedRating = saved.rating
        noData = saved.noData
        notes = saved.notes
    }
    fun saveCurrent() = store.saveAnswer(indicator.id, IndicatorAnswer(selectedRating, noData, notes.trim()))

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "${indicator.id}_header") {
                Column {
                    Text("OCENA FUNKCJONOWANIA UCZNIA", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text("Wskaźnik ${safeIndex + 1} z ${indicators.size}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(indicator.areaTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(indicator.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (indicator.description.isNotBlank()) {
                                Spacer(Modifier.height(10.dp)); Text(indicator.description, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Spacer(Modifier.height(8.dp)); Text("Opis wskaźnika nie został wyodrębniony z dokumentu źródłowego.", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Wybierz jeden poziom oceny", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            items((1..10).toList(), key = { "${indicator.id}_level_$it" }) { levelNumber ->
                val description = indicator.levels.firstOrNull { it.value == levelNumber }?.description.orEmpty()
                val selected = selectedRating == levelNumber && !noData
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedRating = levelNumber; noData = false; saveCurrent() },
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.Top) {
                        RadioButton(selected = selected, onClick = { selectedRating = levelNumber; noData = false; saveCurrent() })
                        Column(Modifier.weight(1f).padding(top = 10.dp)) {
                            Text("Poziom $levelNumber", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(description.ifBlank { "Brak opisu tego poziomu w katalogu źródłowym." }, style = MaterialTheme.typography.bodyMedium,
                                color = if (description.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            item(key = "${indicator.id}_no_data") {
                val selected = noData
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { noData = true; selectedRating = null; saveCurrent() },
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                ) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { noData = true; selectedRating = null; saveCurrent() })
                        Column(Modifier.padding(top = 4.dp)) {
                            Text("Brak danych", fontWeight = FontWeight.Bold)
                            Text("Wybierz, gdy dostępne informacje nie pozwalają rzetelnie ocenić wskaźnika.")
                        }
                    }
                }
            }
            item(key = "${indicator.id}_notes") {
                OutlinedTextField(value = notes, onValueChange = { notes = it; saveCurrent() }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Uwagi do oceny (opcjonalnie)") }, placeholder = { Text("Wpisz obserwacje istotne dla tego wskaźnika") }, minLines = 3)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { saveCurrent(); onPrevious() }, enabled = safeIndex > 0, modifier = Modifier.weight(1f)) { Text("Wstecz") }
            Button(onClick = { saveCurrent(); if (safeIndex < indicators.lastIndex) onNext() else onFinish() }, modifier = Modifier.weight(1f)) {
                Text(if (safeIndex < indicators.lastIndex) "Zapisz i dalej" else "Zakończ ocenę")
            }
        }
    }
}

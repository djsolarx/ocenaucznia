package com.example.opiniaucznia

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PageRange
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.HorizontalDivider
import org.json.JSONArray
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Calendar


// Informacje o wersji dostÄ™pnej w repozytorium.
private data class RemoteAppVersion(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val message: String
)

@Composable
private fun CheckForUpdates() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var availableVersion by remember {
        mutableStateOf<RemoteAppVersion?>(null)
    }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }

    fun installApk(file: File) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                downloadMessage =
                    "Plik zostaďż˝ pobrany. Zezwďż˝l aplikacji Opinia Ucznia na instalowanie nieznanych aplikacji, a nastďż˝pnie wybierz ďż˝Uruchom instalatorďż˝."
                context.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                return
            }

            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            downloadMessage = "Nie udaďż˝o siďż˝ uruchomiďż˝ instalatora: ${e.message ?: "nieznany bďż˝ďż˝d"}"
        }
    }

    LaunchedEffect(Unit) {
        val installedVersionCode = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionCode
            }
        }.getOrDefault(1)

        val remoteVersion = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(
                    "https://raw.githubusercontent.com/djsolarx/ocenaucznia/main/version.json"
                ).openConnection() as HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("Cache-Control", "no-cache")

                if (connection.responseCode !in 200..299) {
                    null
                } else {
                    val json = connection.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(json)
                    val code = obj.optInt("versionCode", 0)
                    val name = obj.optString("versionName", "")
                    val apk = obj.optString("apkUrl", "")
                    val message = obj.optString(
                        "message",
                        "Dostďż˝pna jest nowa wersja aplikacji Opinia Ucznia."
                    )

                    if (code > 0 && name.isNotBlank() && apk.startsWith("https://")) {
                        RemoteAppVersion(code, name, apk, message)
                    } else {
                        null
                    }
                }
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

        if (remoteVersion != null && remoteVersion.versionCode > installedVersionCode) {
            availableVersion = remoteVersion
        }
    }

    val update = availableVersion

    if (update != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) availableVersion = null
            },
            title = { Text("Dostďż˝pna aktualizacja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        update.message +
                            "\n\nNowa wersja: ${update.versionName}" +
                            "\nZainstalowana wersja jest starsza."
                    )

                    if (isDownloading) {
                        Text("Pobieranie aktualizacji: $downloadProgress%")
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Nie zamykaj aplikacji podczas pobierania.")
                    }

                    downloadMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloading,
                    onClick = {
                        val cachedApk = downloadedApk
                        if (cachedApk != null && cachedApk.exists() && cachedApk.length() > 0L) {
                            installApk(cachedApk)
                        } else {
                            scope.launch {
                                isDownloading = true
                                downloadProgress = 0
                                downloadMessage = null

                                val result = runCatching { withContext(Dispatchers.IO) {
                                    var connection: HttpURLConnection? = null
                                    val target = File(context.cacheDir, "opinia-ucznia-update.apk")
                                    try {
                                        connection = URL(update.apkUrl).openConnection() as HttpURLConnection
                                        connection.requestMethod = "GET"
                                        connection.connectTimeout = 15000
                                        connection.readTimeout = 30000
                                        connection.instanceFollowRedirects = true

                                        if (connection.responseCode !in 200..299) {
                                            throw java.io.IOException(
                                                "Serwer zwrďż˝ciďż˝ kod ${connection.responseCode}."
                                            )
                                        }

                                        val totalBytes = connection.contentLengthLong
                                        var downloadedBytes = 0L

                                        connection.inputStream.use { input ->
                                            FileOutputStream(target).use { output ->
                                                val buffer = ByteArray(64 * 1024)
                                                while (true) {
                                                    val count = input.read(buffer)
                                                    if (count < 0) break
                                                    output.write(buffer, 0, count)
                                                    downloadedBytes += count

                                                    if (totalBytes > 0L) {
                                                        val percent =
                                                            ((downloadedBytes * 100L) / totalBytes)
                                                                .toInt().coerceIn(0, 100)
                                                        withContext(Dispatchers.Main) {
                                                            downloadProgress = percent
                                                        }
                                                    }
                                                }
                                                output.fd.sync()
                                            }
                                        }

                                        if (!target.exists() || target.length() == 0L) {
                                            throw java.io.IOException("Pobrany plik jest pusty.")
                                        }
                                        target
                                    } catch (e: Exception) {
                                        target.delete()
                                        throw e
                                    } finally {
                                        connection?.disconnect()
                                    }
                                } }

                                result.fold(
                                    onSuccess = { file ->
                                        downloadedApk = file
                                        downloadProgress = 100
                                        isDownloading = false
                                        installApk(file)
                                    },
                                    onFailure = { error ->
                                        isDownloading = false
                                        downloadMessage =
                                            "Pobieranie nie powiodďż˝o siďż˝: ${error.message ?: "sprawdďż˝ poďż˝ďż˝czenie z internetem"}"
                                    }
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        when {
                            isDownloading -> "Pobieranieďż˝"
                            downloadedApk != null -> "Uruchom instalator"
                            else -> "Pobierz aktualizacjďż˝"
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDownloading,
                    onClick = { availableVersion = null }
                ) {
                    Text("Pďż˝niej")
                }
            }
        )
    }
}

private const val PREFS_NAME = "opinia_ucznia_draft"
private const val DRAFT_KEY = "draft_json"
private const val ARCHIVE_KEY = "completed_students_v1"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OpinionApp()
                    CheckForUpdates()
                }
            }
        }
    }
}

private data class School(
    val number: String,
    val type: String,
    val name: String,
    val region: String,
    val county: String,
    val municipality: String,
    val town: String,
    val street: String,
    val building: String,
    val postalCode: String
) {
    val address: String
        get() = listOf(street, building, postalCode, town)
            .filter { it.isNotBlank() }.joinToString(" ")
}

data class StudentDraft(
    val school: String = "",
    val schoolTown: String = "",
    val studentName: String = "",
    val birthDate: String = "",
    val className: String = "",
    val schoolYear: String = "",
    val observationStart: String = "",
    val observationEnd: String = "",
    val sources: List<String> = emptyList(),
    val ratings: Map<String, String> = emptyMap(),
    val notes: Map<String, String> = emptyMap(),
    val schoolActions: String = "",
    val conclusions: String = "",
    val homeroomTeacher: String = "",
    val pedagogue: String = "",
    val psychologist: String = "",
    val otherStaff: String = "",
    val schoolDirector: String = ""
) {
    fun toJson(): String = JSONObject().apply {
        put("school", school); put("schoolTown", schoolTown)
        put("studentName", studentName); put("birthDate", birthDate)
        put("className", className); put("schoolYear", schoolYear)
        put("observationStart", observationStart); put("observationEnd", observationEnd)
        put("sources", org.json.JSONArray(sources))
        put("ratings", JSONObject().apply { ratings.forEach { (k,v) -> put(k,v) } })
        put("notes", JSONObject().apply { notes.forEach { (k,v) -> put(k,v) } })
        put("schoolActions", schoolActions); put("conclusions", conclusions)
        put("homeroomTeacher", homeroomTeacher); put("pedagogue", pedagogue)
        put("psychologist", psychologist); put("otherStaff", otherStaff); put("schoolDirector", schoolDirector)
    }.toString()

    companion object {
        fun fromJson(raw: String?): StudentDraft {
            if (raw.isNullOrBlank()) return StudentDraft()
            return try {
                val j = JSONObject(raw)
                StudentDraft(
                    school = j.optString("school"), schoolTown = j.optString("schoolTown"),
                    studentName = j.optString("studentName"), birthDate = j.optString("birthDate"),
                    className = j.optString("className"), schoolYear = j.optString("schoolYear"),
                    observationStart = j.optString("observationStart"), observationEnd = j.optString("observationEnd"),
                    sources = j.optJSONArray("sources")?.let { arr ->
                        (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
                    } ?: j.optString("sources").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(),
                    ratings = j.optJSONObject("ratings")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty(),
                    notes = j.optJSONObject("notes")?.let { o -> o.keys().asSequence().associateWith { o.optString(it) } }.orEmpty(),
                    schoolActions = j.optString("schoolActions"), conclusions = j.optString("conclusions"),
                    homeroomTeacher = j.optString("homeroomTeacher"), pedagogue = j.optString("pedagogue"),
                    psychologist = j.optString("psychologist"), otherStaff = j.optString("otherStaff"),
                    schoolDirector = j.optString("schoolDirector")
                )
            } catch (_: Exception) { StudentDraft() }
        }
    }
}

private fun loadSchools(context: Context): List<School> {
    return try {
        context.assets.open("rspo.csv").bufferedReader(Charsets.UTF_8).use { reader ->
            val header = reader.readLine()?.removePrefix("\uFEFF") ?: return emptyList()
            val columns = parseCsvLine(header, ';').map { it.trim() }
            val idx = columns.withIndex().associate { it.value to it.index }
            fun value(row: List<String>, name: String): String =
                row.getOrNull(idx[name] ?: -1)?.trim().orEmpty()
            reader.lineSequence().mapNotNull { line ->
                if (line.isBlank()) null else {
                    val cells = parseCsvLine(line, ';')
                    val name = value(cells, "Nazwa")
                    if (name.isBlank()) null else School(
                        number = value(cells, "Numer RSPO"),
                        type = value(cells, "Typ"),
                        name = name,
                        region = value(cells, "WojewĂłdztwo"),
                        county = value(cells, "Powiat"),
                        municipality = value(cells, "Gmina"),
                        town = value(cells, "MiejscowoĹ›Ä‡"),
                        street = value(cells, "Ulica"),
                        building = value(cells, "Numer budynku"),
                        postalCode = value(cells, "Kod pocztowy")
                    )
                }
            }.toList()
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseCsvLine(line: String, delimiter: Char): List<String> {
    val result = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val ch = line[i]
        when {
            ch == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                cell.append('"'); i++
            }
            ch == '"' -> quoted = !quoted
            ch == delimiter && !quoted -> {
                result.add(cell.toString()); cell.setLength(0)
            }
            else -> cell.append(ch)
        }
        i++
    }
    result.add(cell.toString())
    return result
}

private fun printPdfFile(context: Context, file: File, jobName: String) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    printManager.print(jobName, object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(8192)
                        while (!cancellationSignal.isCanceled) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.flush()
                    }
                }
                if (cancellationSignal.isCanceled) callback.onWriteCancelled()
                else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message ?: "Nie udaĹ‚o siÄ™ przygotowaÄ‡ raportu do drukowania.")
            }
        }
    }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OpinionApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val archivePrefs = remember { context.getSharedPreferences("opinia_ucznia_archive", Context.MODE_PRIVATE) }
    val assessmentStore = remember { IndicatorAssessmentStore(context) }
    val monitoringStore = remember { MonitoringStore(context) }
    val indicators = remember { runCatching { assessmentStore.loadCatalog(context) }.getOrDefault(emptyList()) }
    var draft by remember { mutableStateOf(StudentDraft.fromJson(prefs.getString(DRAFT_KEY, null))) }
    var page by remember { mutableStateOf(-1) }
    var status by remember { mutableStateOf("Dane sÄ… zapisywane lokalnie na tym urzÄ…dzeniu.") }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var showAiPromptDialog by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    fun calculateAge(dateText: String): Int? {
        val parts = dateText.split(".")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        val today = Calendar.getInstance()
        var age = today.get(Calendar.YEAR) - year
        if (today.get(Calendar.MONTH) + 1 < month || (today.get(Calendar.MONTH) + 1 == month && today.get(Calendar.DAY_OF_MONTH) < day)) age--
        return age.takeIf { it in 3..25 }
    }

    fun buildAiPrompt(): String {
        val age = calculateAge(draft.birthDate)?.toString() ?: "nie podano"
        val classMatch = Regex("^\\s*(\\d+|[IVXivx]+)").find(draft.className.trim())?.groupValues?.get(1).orEmpty()
        val answerMap = assessmentStore.getAllSavedAnswers()
        val assessmentText = indicators.mapNotNull { item ->
            val answer = answerMap[item.id] ?: return@mapNotNull null
            if (answer.rating == null && !answer.noData && answer.notes.isBlank()) return@mapNotNull null
            buildString {
                append("Obszar: ").append(item.areaTitle).append("\nWskaĹşnik: ").append(item.title).append("\n")
                when {
                    answer.noData -> append("Ocena: brak danych\n")
                    answer.rating != null -> {
                        append("Poziom: ").append(answer.rating).append("/10\n")
                        val levelText = item.levels.firstOrNull { it.value == answer.rating }?.description
                        if (!levelText.isNullOrBlank()) append("Opis poziomu: ").append(levelText).append("\n")
                    }
                    else -> append("Ocena: nieustalona\n")
                }
                if (answer.notes.isNotBlank()) append("Komentarz: ").append(answer.notes.trim()).append("\n")
            }
        }.joinToString("\n")
        return buildString {
            append("Przygotuj propozycję sekcji „Wnioski do dalszej pracy z uczniem” na podstawie wyłącznie poniższych informacji.\n")
            append("Wiek: ").append(age).append(" lat\n")
            append("Klasa: ").append(classMatch.ifBlank { "nie podano" }).append("\n")
            append("Źródła informacji wskazane w formularzu: ").append(draft.sources.joinToString("; ").ifBlank { "nie podano" }).append("\n\n")
            append("Wyniki oceny funkcjonowania:\n").append(assessmentText.ifBlank { "Brak wprowadzonych ocen i komentarzy." }).append("\n\n")
            append("Wymagania dotyczące treści i formatu:\n")
            append("1. Pisz po polsku, profesjonalnie, rzeczowo, neutralnie i zwięźle.\n")
            append("2. Korzystaj wyłącznie z danych z formularza. Nie dopisuj faktów ani nie stawiaj nieuzasadnionych diagnoz.\n")
            append("3. Zastosuj tytuł: Wnioski do dalszej pracy z uczniem.\n")
            append("4. Podziel tekst na ponumerowane sekcje, np. 1. Obszary wymagające wsparcia, 2. Zalecane działania w szkole, 3. Wskazówki do dalszej obserwacji. Uwzględniaj tylko sekcje uzasadnione danymi.\n")
            append("5. Każdy nagłówek sekcji umieść w osobnym wierszu. Pod nagłówkiem dodaj krótki akapit wyjaśniający, jeśli pozwalają na to dane.\n")
            append("6. Zalecenia przedstawiaj w postaci listy. Każde zalecenie zapisuj w osobnym wierszu, zaczynając od '- '.\n")
            append("7. Akapity i punkty listy powinny się uzupełniać, a nie powtarzać. Unikaj długich bloków tekstu i stosuj puste wiersze między sekcjami.\n")
            append("8. Podpunkty, np. 1.1. lub 1.2., stosuj tylko wtedy, gdy poprawiają czytelność.\n")
            append("9. Zalecenia muszą być konkretne, możliwe do zastosowania w szkole i powiązane z opisanymi potrzebami.\n")
            append("10. Oddzielaj informacje wynikające z danych od przypuszczeń. Sama niska ocena nie dowodzi przyczyny trudności.\n")
            append("11. Braki danych zaznaczaj wtedy, gdy mają znaczenie dla wniosków. Nie twórz zaleceń bez podstaw.\n")
            append("12. Nie używaj znaczników Markdown, takich jak **, __ lub #. Nagłówki zapisuj zwykłym tekstem z numeracją. Formatowanie zostanie nadane w PDF.\n")
            append("13. Zwróć wyłącznie gotowy tekst wniosków, bez komentarza od asystenta i bez danych identyfikujących ucznia. Tekst wymaga sprawdzenia i zatwierdzenia przez specjalistę.")
        }
    }
    var activeRecordId by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var monitoringStudentId by remember { mutableStateOf("") }
    var monitoringStudentName by remember { mutableStateOf("") }
    var monitoringObservationSource by remember { mutableStateOf("") }
    var monitoringSourceText by remember {
        mutableStateOf(prefs.getString("MONITORING_SOURCE_TEXT", "") ?: "")
    }

    fun update(next: StudentDraft) {
        draft = next
        prefs.edit().putString(DRAFT_KEY, next.toJson()).apply()
        status = "Zapisano szkic lokalnie."
    }

    var showArchivedStudents by remember { mutableStateOf(false) }
    var archiveRefresh by remember { mutableIntStateOf(0) }
    var pendingArchiveId by remember { mutableStateOf<String?>(null) }
    var pendingArchiveName by remember { mutableStateOf("") }

    fun archiveArray(): JSONArray = runCatching { JSONArray(archivePrefs.getString(ARCHIVE_KEY, "[]")) }.getOrDefault(JSONArray())

    // Bluetooth transfer launcher. The transfer Activity handles QR pairing and file preview.
    val transferLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val raw = result.data?.getStringExtra(BluetoothOpinionTransferActivity.EXTRA_IMPORTED_RECORD)
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val imported = JSONObject(raw)
                    JSONObject(imported.optString("draft"))
                    if (imported.optJSONObject("answers") == null) {
                        throw IllegalArgumentException("Opinia nie zawiera ocen i notatek.")
                    }
                    val archive = archiveArray()
                    imported.put("id", java.util.UUID.randomUUID().toString())
                    imported.put("savedAt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale("pl", "PL")).format(java.util.Date()))
                    imported.put("archived", false)
                    val merged = JSONArray()
                    for (i in 0 until archive.length()) archive.optJSONObject(i)?.let { merged.put(it) }
                    merged.put(imported)
                    archivePrefs.edit().putString(ARCHIVE_KEY, merged.toString()).apply()
                    archiveRefresh++
                    status = "Zaimportowano opiniÄ™: ${imported.optString("name", "UczeĹ„")}. Zapisano jako nowy rekord."
                }.onFailure {
                    status = "Odebrany plik opinii jest nieprawidĹ‚owy. Nie zapisano danych."
                }
            }
        }
    }

    fun startTransfer(mode: String, recordJson: String? = null) {
        val intent = Intent(context, BluetoothOpinionTransferActivity::class.java)
            .putExtra(BluetoothOpinionTransferActivity.EXTRA_MODE, mode)
        if (recordJson != null) intent.putExtra(BluetoothOpinionTransferActivity.EXTRA_RECORD_JSON, recordJson)
        transferLauncher.launch(intent)
    }

    fun setStudentArchived(recordId: String, archived: Boolean) {
        val records = archiveArray()
        var found = false
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i) ?: continue
            if (record.optString("id") == recordId) {
                record.put("archived", archived)
                found = true
                break
            }
        }
        if (found) {
            archivePrefs.edit().putString(ARCHIVE_KEY, records.toString()).apply()
            archiveRefresh++
            status = if (archived) "UczeĹ„ zostaĹ‚ przeniesiony do archiwum." else "UczeĹ„ zostaĹ‚ przywrĂłcony do aktywnych."
        }
    }
    fun saveToArchive() {
        val array = archiveArray()
        val answers = assessmentStore.getAllSavedAnswers()
        val answersJson = JSONObject().apply {
            answers.forEach { (id, answer) -> put(id, JSONObject().apply {
                if (answer.rating == null) put("rating", JSONObject.NULL) else put("rating", answer.rating)
                put("noData", answer.noData); put("notes", answer.notes)
            }) }
        }
        val previousRecord = (0 until array.length())
            .mapNotNull { array.optJSONObject(it) }
            .firstOrNull { it.optString("id") == activeRecordId }
        val record = JSONObject().apply {
            put("id", activeRecordId)
            put("name", draft.studentName.ifBlank { "UczeĹ„ bez podanego imienia i nazwiska" })
            put("school", draft.school); put("className", draft.className); put("birthDate", draft.birthDate)
            put("savedAt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale("pl", "PL")).format(java.util.Date()))
            put("draft", draft.toJson()); put("answers", answersJson)
            previousRecord?.optJSONObject("monitoring")?.let { put("monitoring", it) }
        }
        val updated = JSONArray()
        var replaced = false
        for (i in 0 until array.length()) {
            val old = array.optJSONObject(i) ?: continue
            if (old.optString("id") == activeRecordId) { updated.put(record); replaced = true } else updated.put(old)
        }
        if (!replaced) updated.put(record)
        archivePrefs.edit().putString(ARCHIVE_KEY, updated.toString()).apply()
        status = "Zapisano ucznia w lokalnej bazie."
    }

    fun currentRecordJsonForTransfer(): String {
        val answersJson = JSONObject().apply {
            assessmentStore.getAllSavedAnswers().forEach { (id, answer) ->
                put(id, JSONObject().apply {
                    if (answer.rating == null) put("rating", JSONObject.NULL) else put("rating", answer.rating)
                    put("noData", answer.noData)
                    put("notes", answer.notes)
                })
            }
        }
        return JSONObject().apply {
            put("id", activeRecordId)
            put("name", draft.studentName.ifBlank { "UczeĹ„ bez podanego imienia i nazwiska" })
            put("school", draft.school)
            put("className", draft.className)
            put("birthDate", draft.birthDate)
            put("savedAt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale("pl", "PL")).format(java.util.Date()))
            put("archived", false)
            put("draft", draft.toJson())
            put("answers", answersJson)
        }.toString()
    }

    fun loadArchivedRecord(record: JSONObject) {
        activeRecordId = record.optString("id", java.util.UUID.randomUUID().toString())
        draft = StudentDraft.fromJson(record.optString("draft"))
        prefs.edit().putString(DRAFT_KEY, draft.toJson()).apply()
        val answersObj = record.optJSONObject("answers") ?: JSONObject()
        val answers = mutableMapOf<String, IndicatorAnswer>()
        val keys = answersObj.keys()
        while (keys.hasNext()) {
            val key = keys.next(); val item = answersObj.optJSONObject(key) ?: continue
            val rating = if (item.isNull("rating")) null else item.optInt("rating", -1).takeIf { it in 1..10 }
            answers[key] = IndicatorAnswer(rating, item.optBoolean("noData", false) && rating == null, item.optString("notes"))
        }
        assessmentStore.replaceAllSavedAnswers(answers)
        page = 0
        status = "Wczytano ucznia do edycji."
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) {
            val ok = runCatching { writeOpinionPdf(context, uri, draft, indicators, assessmentStore.getAllSavedAnswers()) }.isSuccess
            status = if (ok) "Utworzono plik PDF." else "Nie udaĹ‚o siÄ™ utworzyÄ‡ PDF. SprĂłbuj ponownie."
        }
    }

    val firstIndicatorPage = 2
    val conclusionsPage = firstIndicatorPage + indicators.size
    val summaryPage = conclusionsPage + 1
    val isIndicatorPage = page in firstIndicatorPage until conclusionsPage
    val indicatorIndex = page - firstIndicatorPage
    val pageTitle = when {
        page == 0 -> "Dane szkoĹ‚y i ucznia"
        page == 1 -> "ĹąrĂłdĹ‚a informacji"
        isIndicatorPage -> "Ocena wskaĹşnika ${indicatorIndex + 1} z ${indicators.size}"
        page == conclusionsPage -> "Wnioski do dalszej pracy z uczniem"
        page == summaryPage -> "Podsumowanie"
        else -> "Opinia ucznia"
    }

    if (previewFile != null) {
        val fileToPreview = previewFile!!
        PdfPreviewScreen(
            file = fileToPreview,
            onBack = { previewFile = null },
            onPrint = {
                runCatching {
                    printPdfFile(context, fileToPreview, "Raport ucznia - ${draft.studentName}")
                }.onFailure {
                    status = "Nie udaĹ‚o siÄ™ uruchomiÄ‡ drukowania: ${it.message ?: "bĹ‚Ä…d"}"
                }
            }
        )
    } else if (page == -2) {
        AboutAppScreen(
            onBack = { page = -1 },
            onOpenWebsite = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://pggp.pl"))) }
            },
            onSupport = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://buycoffee.to/trustmatrix"))) }
            }
        )
    } else if (page == -3) {
        MonitoringScreen(
            studentId = monitoringStudentId,
            studentName = monitoringStudentName,
            observationSource = monitoringObservationSource,
            store = monitoringStore,
            onBack = { page = -1 }
        )
    } else if (page == -1) {
        Scaffold { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("OPINIA UCZNIA", style = MaterialTheme.typography.labelLarge)
                Text("Opinia o funkcjonowaniu ucznia w szkole", style = MaterialTheme.typography.headlineMedium)
                Text("WypeĹ‚niaj kolejne sekcje. Dane i oceny sÄ… zapisywane lokalnie na urzÄ…dzeniu.")
                Button(onClick = { page = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Rozpocznij / kontynuuj bieĹĽÄ…cÄ… opiniÄ™") }
                OutlinedButton(onClick = {
                    activeRecordId = java.util.UUID.randomUUID().toString()
                    draft = StudentDraft()
                    prefs.edit().putString(DRAFT_KEY, draft.toJson()).apply()
                    assessmentStore.clearAnswers()
                    page = 0
                    status = "RozpoczÄ™to nowÄ… opiniÄ™."
                }, modifier = Modifier.fillMaxWidth()) { Text("Nowa opinia") }
                OutlinedButton(
                    onClick = { page = -2 },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("O aplikacji") }
                HorizontalDivider()
                Text("Baza uczniĂłw", style = MaterialTheme.typography.titleLarge)
                Text("UczniĂłw moĹĽna archiwizowaÄ‡ bez usuwania ich opinii.", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(
                    onClick = { startTransfer("receive") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Importuj opiniÄ™ przez QR") }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!showArchivedStudents) {
                        Button(onClick = { showArchivedStudents = false }, modifier = Modifier.weight(1f)) {
                            Text("Aktywni")
                        }
                    } else {
                        OutlinedButton(onClick = { showArchivedStudents = false }, modifier = Modifier.weight(1f)) {
                            Text("Aktywni")
                        }
                    }

                    if (showArchivedStudents) {
                        Button(onClick = { showArchivedStudents = true }, modifier = Modifier.weight(1f)) {
                            Text("Archiwum")
                        }
                    } else {
                        OutlinedButton(onClick = { showArchivedStudents = true }, modifier = Modifier.weight(1f)) {
                            Text("Archiwum")
                        }
                    }
                }

                val refreshToken = archiveRefresh
                val allRecords = archiveArray()
                val visibleRecords = (0 until allRecords.length())
                    .mapNotNull { allRecords.optJSONObject(it) }
                    .filter { it.optBoolean("archived", false) == showArchivedStudents }

                Text(
                    "${if (showArchivedStudents) "W archiwum" else "Aktywni uczniowie"}: ${visibleRecords.size}",
                    style = MaterialTheme.typography.labelMedium
                )

                if (visibleRecords.isEmpty()) {
                    Text(
                        if (showArchivedStudents)
                            "Archiwum jest puste."
                        else
                            "Brak aktywnych uczniĂłw. MoĹĽesz rozpoczÄ…Ä‡ nowÄ… opiniÄ™ lub sprawdziÄ‡ archiwum."
                    )
                }

                for (record in visibleRecords.reversed()) {
                    val recordId = record.optString("id")
                    val recordName = record.optString("name", "UczeĹ„")

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(recordName, style = MaterialTheme.typography.titleMedium)

                            Text(
                                listOf(
                                    record.optString("school"),
                                    record.optString("className").let {
                                        if (it.isNotBlank()) "kl. $it" else ""
                                    }
                                ).filter { it.isNotBlank() }.joinToString(" â€˘ ")
                            )

                            Text(
                                "Zapisano: ${record.optString("savedAt")}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TextButton(
                                    onClick = { loadArchivedRecord(record) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("OtwĂłrz opiniÄ™")
                                }

                                TextButton(
                                    onClick = {
                                        if (showArchivedStudents) {
                                            setStudentArchived(recordId, false)
                                        } else {
                                            pendingArchiveId = recordId
                                            pendingArchiveName = recordName
                                        }
                                    }
                                ) {
                                    Text(if (showArchivedStudents) "â†© PrzywrĂłÄ‡" else "đź“¦ Archiwizuj")
                                }
                            }

                            TextButton(
                                onClick = { startTransfer("send", record.toString()) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("UdostÄ™pnij przez QR + Bluetooth") }
                        }
                    }
                }

                if (pendingArchiveId != null) {
                    AlertDialog(
                        onDismissRequest = { pendingArchiveId = null },
                        title = { Text("ArchiwizowaÄ‡ ucznia?") },
                        text = {
                            Text(
                                "Czy na pewno przenieĹ›Ä‡ ucznia â€ž${pendingArchiveName}â€ť do archiwum? " +
                                        "Opinia nie zostanie usuniÄ™ta i bÄ™dzie moĹĽna jÄ… pĂłĹşniej przywrĂłciÄ‡."
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val id = pendingArchiveId
                                    pendingArchiveId = null
                                    if (id != null) setStudentArchived(id, true)
                                }
                            ) {
                                Text("Tak, archiwizuj")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingArchiveId = null }) {
                                Text("Anuluj")
                            }
                        }
                    )
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Wersja ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else if (isIndicatorPage) {
        Scaffold(topBar = { TopAppBar(title = { Text(pageTitle) }, navigationIcon = {
            OutlinedButton(onClick = { page = if (indicatorIndex == 0) 1 else page - 1 }) { Text("WrĂłÄ‡") }
        }) }) { padding ->
            IndicatorAssessmentScreen(
                indicators = indicators, currentIndex = indicatorIndex, store = assessmentStore,
                onPrevious = { page = if (indicatorIndex == 0) 1 else page - 1 },
                onNext = { page++ }, onFinish = { page = conclusionsPage },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    } else {
        Scaffold(topBar = { TopAppBar(title = { Text(pageTitle) }, navigationIcon = {
            OutlinedButton(onClick = { page = when { page == 0 -> -1; page == conclusionsPage -> if (indicators.isNotEmpty()) conclusionsPage - 1 else 1; page == summaryPage -> conclusionsPage; else -> page - 1 } }) { Text("WrĂłÄ‡") }
        }) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (page) {
                    0 -> SchoolAndStudentForm(draft = draft, update = ::update)
                    1 -> {
                        Text("ĹąrĂłdĹ‚a informacji", style = MaterialTheme.typography.titleLarge)
                        Text("Zaznacz wszystkie ĹşrĂłdĹ‚a wykorzystane przy przygotowaniu opinii.")
                        SourceCheckboxes(draft.sources) { update(draft.copy(sources = it)) }
                    }
                    conclusionsPage -> {
                        Text("NaleĹĽy wskazaÄ‡ wnioski dotyczÄ…ce dalszej pracy majÄ…ce na celu poprawÄ™ funkcjonowania ucznia.")
                        OutlinedTextField(value = draft.conclusions, onValueChange = { update(draft.copy(conclusions = it)) }, label = { Text("Wnioski do dalszej pracy z uczniem") }, placeholder = { Text("Wpisz wnioski wynikajÄ…ce z przeprowadzonej oceny") }, modifier = Modifier.fillMaxWidth(), minLines = 5)
                        Text("MoĹĽesz przygotowaÄ‡ prompt dla dowolnego czatu AI. Przed udostÄ™pnieniem sprawdĹş treĹ›Ä‡ i usuĹ„ informacje, ktĂłre mogĹ‚yby identyfikowaÄ‡ ucznia.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { aiPrompt = buildAiPrompt(); showAiPromptDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Przygotuj prompt dla AI") }
                        OutlinedTextField(
                            value = monitoringSourceText,
                            onValueChange = { text ->
                                monitoringSourceText = text
                                prefs.edit().putString("MONITORING_SOURCE_TEXT", text).apply()
                            },
                            label = { Text("PeĹ‚na odpowiedĹş AI â€” wklej tutaj wygenerowany tekst") },
                            placeholder = { Text("Wklej peĹ‚nÄ… odpowiedĹş AI zawierajÄ…cÄ… punkt 3: Wnioski do dalszej obserwacji") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 6
                        )
                        Button(
                            onClick = {
                                val sekcja = Regex(
                                    "(?im)^\\s*3\\s*[.)]\\s*Wnioski do dalszej obserwacji\\b[\\s\\S]*?(?=^\\s*\\d+\\s*[.)]\\s+|\\z)"
                                ).find(monitoringSourceText)?.value?.trim()

                                if (sekcja.isNullOrBlank()) {
                                    status = "Nie znaleziono punktu 3: Wnioski do dalszej obserwacji. Wklej peĹ‚nÄ… odpowiedĹş AI."
                                } else if (activeRecordId.isNullOrBlank()) {
                                    status = "Najpierw zapisz lub otwĂłrz opiniÄ™ ucznia."
                                } else {
                                    monitoringStudentId = activeRecordId
                                    monitoringStudentName = draft.studentName
                                    monitoringObservationSource = sekcja
                                    page = -3
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Monitoring ucznia")
                        }
                        Text("Osoby opracowujÄ…ce opiniÄ™", style = MaterialTheme.typography.titleLarge)
                        OutlinedTextField(draft.homeroomTeacher, { update(draft.copy(homeroomTeacher = it)) }, label = { Text("Wychowawca klasy") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(draft.pedagogue, { update(draft.copy(pedagogue = it)) }, label = { Text("Pedagog / pedagog specjalny") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(draft.psychologist, { update(draft.copy(psychologist = it)) }, label = { Text("Psycholog") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(draft.otherStaff, { update(draft.copy(otherStaff = it)) }, label = { Text("Inni nauczyciele / specjaliĹ›ci") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        OutlinedTextField(draft.schoolDirector, { update(draft.copy(schoolDirector = it)) }, label = { Text("Dyrektor szkoĹ‚y") }, modifier = Modifier.fillMaxWidth())
                    }
                    summaryPage -> {
                        Text("Podsumowanie szkicu", style = MaterialTheme.typography.titleLarge)
                        SummaryLine("SzkoĹ‚a", draft.school); SummaryLine("MiejscowoĹ›Ä‡", draft.schoolTown)
                        SummaryLine("UczeĹ„", draft.studentName); SummaryLine("Data urodzenia", draft.birthDate)
                        SummaryLine("Klasa", draft.className); SummaryLine("Rok szkolny", draft.schoolYear)
                        SummaryLine("Okres obserwacji", listOf(draft.observationStart, draft.observationEnd).filter { it.isNotBlank() }.joinToString(" â€“ "))
                        SummaryLine("ĹąrĂłdĹ‚a informacji", draft.sources.joinToString("\n") { "â€˘ $it" })
                        val answers = assessmentStore.getAllSavedAnswers()
                        Text("Obszar A", style = MaterialTheme.typography.titleLarge)
                        Text("AktywnoĹ›Ä‡ i uczestniczenie ucznia zgodnie z obszarami ICF (MiÄ™dzynarodowej Klasyfikacji Funkcjonowania, NiepeĹ‚nosprawnoĹ›ci i Zdrowia)")
                        indicators.groupBy { it.areaTitle }.forEach { (areaTitle, areaIndicators) ->
                            Text(normalizeAreaTitle(areaTitle), style = MaterialTheme.typography.titleMedium)
                            areaIndicators.forEach { item ->
                                val answer = answers[item.id]
                                val interpretation = when {
                                    answer == null -> "Nie oceniono"
                                    answer.noData -> "Brak danych"
                                    answer.rating != null -> item.levels.firstOrNull { it.value == answer.rating }?.description ?: "Brak opisu interpretacji w katalogu ĹşrĂłdĹ‚owym"
                                    else -> "Nie oceniono"
                                }
                                Column(Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp)) {
                                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                                    Text(interpretation, style = MaterialTheme.typography.bodyMedium)
                                    if (answer?.notes?.isNotBlank() == true) Text("Uwagi: ${answer.notes}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        SummaryLine("Wnioski do dalszej pracy z uczniem", draft.conclusions)
                        SummaryLine("Wychowawca klasy", draft.homeroomTeacher); SummaryLine("Pedagog / pedagog specjalny", draft.pedagogue)
                        SummaryLine("Psycholog", draft.psychologist); SummaryLine("Inni nauczyciele / specjaliĹ›ci", draft.otherStaff)
                        SummaryLine("Dyrektor szkoĹ‚y", draft.schoolDirector)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { saveToArchive() }, modifier = Modifier.weight(1f)) { Text("Zapisz w bazie uczniĂłw") }
                            Button(onClick = {
                                val filename = "Opinia_${draft.studentName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ucznia" }}.pdf"
                                pdfLauncher.launch(filename)
                            }, modifier = Modifier.weight(1f)) { Text("Eksportuj PDF") }
                        }
                        Button(
                            onClick = {
                                val safeName = draft.studentName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ucznia" }
                                val tempPdf = File(context.cacheDir, "Opinia_${safeName}_podglad.pdf")
                                runCatching {
                                    writeOpinionPdf(context, Uri.fromFile(tempPdf), draft, indicators, assessmentStore.getAllSavedAnswers())
                                    check(tempPdf.exists() && tempPdf.length() > 0L) { "Plik PDF jest pusty lub nie powstaĹ‚." }
                                }.onSuccess {
                                    previewFile = tempPdf
                                    status = "PodglÄ…d gotowy. SprawdĹş strony przed drukowaniem."
                                }.onFailure {
                                    status = "Nie udaĹ‚o siÄ™ przygotowaÄ‡ podglÄ…du PDF: ${it.message ?: "bĹ‚Ä…d"}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("PodglÄ…d wydruku") }
                        Button(onClick = {
                            val safeName = draft.studentName.trim().replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "ucznia" }
                            val tempPdf = File(context.cacheDir, "Opinia_${safeName}_do_druku.pdf")
                            val ok = runCatching {
                                writeOpinionPdf(context, Uri.fromFile(tempPdf), draft, indicators, assessmentStore.getAllSavedAnswers())
                            }.isSuccess
                            if (ok && tempPdf.exists() && tempPdf.length() > 0L) {
                                runCatching { printPdfFile(context, tempPdf, "Raport ucznia - ${draft.studentName}") }
                                    .onFailure { status = "Nie udaĹ‚o siÄ™ uruchomiÄ‡ drukowania: ${it.message ?: "bĹ‚Ä…d systemu"}" }
                                if (status.isBlank() || status == "Dane sÄ… zapisywane lokalnie na tym urzÄ…dzeniu.") {
                                    status = "Otworzono drukowanie systemowe. Wybierz dostÄ™pnÄ… drukarkÄ™ lub usĹ‚ugÄ™ drukowania."
                                }
                            } else status = "Nie udaĹ‚o siÄ™ przygotowaÄ‡ PDF do drukowania. Najpierw sprĂłbuj wyeksportowaÄ‡ raport do PDF."
                        }, modifier = Modifier.fillMaxWidth()) { Text("Drukuj raport") }
                        OutlinedButton(
                            onClick = { startTransfer("send", currentRecordJsonForTransfer()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("UdostÄ™pnij opiniÄ™ przez QR + Bluetooth") }
                    }
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = "Wersja ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { page = when { page == 0 -> -1; page == conclusionsPage -> if (indicators.isNotEmpty()) conclusionsPage - 1 else 1; page == summaryPage -> conclusionsPage; else -> page - 1 } }, modifier = Modifier.weight(1f)) { Text(if (page == 0) "Strona gĹ‚Ăłwna" else "Poprzednia") }
                    Button(onClick = {
                        prefs.edit().putString(DRAFT_KEY, draft.toJson()).apply()
                        status = "Zapisano szkic lokalnie."
                        page = when { page == 0 -> 1; page == 1 -> if (indicators.isNotEmpty()) firstIndicatorPage else conclusionsPage; page == conclusionsPage -> summaryPage; page == summaryPage -> -1; else -> page + 1 }
                    }, modifier = Modifier.weight(1f)) { Text(when (page) { 0, 1 -> "Dalej"; conclusionsPage -> "PrzejdĹş do podsumowania"; summaryPage -> "ZakoĹ„cz"; else -> "Dalej" }) }
                }
            }
        }
    }

    if (showAiPromptDialog) {
        AlertDialog(
            onDismissRequest = { showAiPromptDialog = false },
            title = { Text("Prompt dla czatu AI") },
            text = {
                Column(Modifier.fillMaxWidth().height(440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SprawdĹş caĹ‚Ä… treĹ›Ä‡ przed wysĹ‚aniem. Prompt nie zawiera imienia, nazwiska, daty urodzenia, szkoĹ‚y ani danych personelu. Komentarze mogÄ… jednak zawieraÄ‡ dane identyfikujÄ…ce, dlatego przejrzyj je rÄ™cznie.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = aiPrompt, onValueChange = { aiPrompt = it }, modifier = Modifier.fillMaxWidth(), minLines = 12, label = { Text("TreĹ›Ä‡ do sprawdzenia i udostÄ™pnienia") })
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(aiPrompt))
                        status = "Prompt skopiowano. Wklej go do wybranego czatu AI."
                        showAiPromptDialog = false
                    }) { Text("Kopiuj prompt") }
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, aiPrompt)
                            putExtra(Intent.EXTRA_TITLE, "Prompt do opracowania wnioskĂłw")
                        }
                        runCatching { context.startActivity(Intent.createChooser(intent, "UdostÄ™pnij prompt do czatu AI")) }
                            .onFailure { status = "Nie udaĹ‚o siÄ™ otworzyÄ‡ menu udostÄ™pniania. Skopiuj prompt rÄ™cznie." }
                        showAiPromptDialog = false
                    }) { Text("UdostÄ™pnij do czatu AI") }
                }
            },
            dismissButton = { TextButton(onClick = { showAiPromptDialog = false }) { Text("Anuluj") } }
        )
    }


}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AboutAppScreen(
    onBack: () -> Unit,
    onOpenWebsite: () -> Unit,
    onSupport: () -> Unit
) {
    val navy = Color(0xFF1D3D6B)
    val lightCard = Color(0xFFF1F3F8)
    val green = Color(0xFF247B55)
    val paleGreen = Color(0xFFD7F0E1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("O aplikacji", color = Color.White, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("â€ą", color = Color.White, style = MaterialTheme.typography.headlineLarge)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = navy,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = lightCard)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Autor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = navy)
                    Text("PaweĹ‚ GÄ™bicki", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("NiezaleĹĽny developer i edukator", style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onOpenWebsite, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = navy)) {
                        Text("đźŚ  pggp.pl")
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().border(1.5.dp, green, RoundedCornerShape(22.dp)),
                shape = RoundedCornerShape(22.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = paleGreen)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("â•  Postaw mi kawÄ™!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = green)
                    Text(
    """
    Opinia Ucznia pomaga porzÄ…dkowaÄ‡ informacje o funkcjonowaniu ucznia w szkole, dokumentowaÄ‡ obserwacje i przygotowywaÄ‡ wnioski do dalszej pracy. Celem aplikacji jest wspieranie nauczycieli i specjalistĂłw w tworzeniu rzetelnych, opartych na danych opinii.

    Aplikacja rozwijana jest z myĹ›lÄ… o praktycznym wsparciu pracy szkoĹ‚y. JeĹ›li uznajesz jÄ… za przydatnÄ…, moĹĽesz wesprzeÄ‡ dalszy rozwĂłj projektu. DziÄ™kujÄ™! đź™‚
    """.trimIndent(),
    style = MaterialTheme.typography.bodyLarge,
    color = Color(0xFF303632)
)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MaĹ‚a\n5 zĹ‚", "Ĺšrednia\n10 zĹ‚", "DuĹĽa\n25 zĹ‚").forEach { amount ->
                            OutlinedButton(
                                onClick = onSupport,
                                modifier = Modifier.weight(1f).height(76.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = green)
                            ) {
                                Text(amount, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Text(
                        "Przycisk otwiera BuyCoffee.to. PĹ‚atnoĹ›Ä‡ odbywa siÄ™ na zewnÄ™trznej stronie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF5B665F),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Â© 2026 PaweĹ‚ GÄ™bicki Â· pggp.pl", textAlign = TextAlign.Center, color = Color.Gray)
                Text("Wszelkie prawa zastrzeĹĽone", textAlign = TextAlign.Center, color = Color.Gray)
                Text("Wersja ${BuildConfig.VERSION_NAME}", textAlign = TextAlign.Center, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun normalizeAreaTitle(title: String): String = when {
    title.startsWith("Obszar 1.") -> "Obszar 1 - Uczenie siÄ™ i stosowanie wiedzy"
    title.startsWith("Obszar 2.") -> "Obszar 2 - OgĂłlne zadania i obowiÄ…zki"
    title.startsWith("Obszar 3.") -> "Obszar 3 - Porozumiewanie siÄ™"
    title.startsWith("Obszar 4.") -> "Obszar 4 - Motoryka, poruszanie siÄ™, w tym mobilnoĹ›Ä‡ i aktywnoĹ›Ä‡ manualna"
    title.startsWith("Obszar 5.") -> "Obszar 5 - Dbanie o siebie, samoobsĹ‚uga i samodzielnoĹ›Ä‡"
    title.startsWith("Obszar 6.") -> "Obszar 6 - Ĺ»ycie domowe"
    title.startsWith("Obszar 7.") -> "Obszar 7 - Wzajemne kontakty i zwiÄ…zki miÄ™dzyludzkie, ĹĽycie w spoĹ‚ecznoĹ›ci szkolnej i lokalnej"
    title.startsWith("CzÄ™Ĺ›Ä‡ B.") -> "CzÄ™Ĺ›Ä‡ B - Zakres i rodzaj trudnoĹ›ci w realizacji programĂłw nauczania"
    title.startsWith("Obszar C.") -> "Obszar C - Mocne strony i uzdolnienia ucznia"
    else -> title
}

@Composable
private fun SourceCheckboxes(
    selected: List<String>,
    onSelectionChange: (List<String>) -> Unit
) {
    val sourceOptions = listOf(
        "Obserwacja ucznia podczas zajÄ™Ä‡ edukacyjnych",
        "Obserwacja podczas przerw i sytuacji spoĹ‚ecznych",
        "Rozmowa z uczniem",
        "Rozmowa z rodzicami / opiekunami",
        "Informacje od nauczycieli przedmiotĂłw",
        "Informacje od specjalistĂłw szkolnych",
        "Analiza dokumentacji szkolnej"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sourceOptions.forEach { source ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val updated = if (source in selected) selected - source else selected + source
                        onSelectionChange(updated)
                    }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = source in selected,
                    onCheckedChange = { checked ->
                        val updated = if (checked) selected + source else selected - source
                        onSelectionChange(updated)
                    }
                )
                Text(
                    text = source,
                    modifier = Modifier.weight(1f).padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SchoolAndStudentForm(draft: StudentDraft, update: (StudentDraft) -> Unit) {
    val context = LocalContext.current
    val schools = remember { mutableStateOf<List<School>?>(null) }
    var schoolQuery by remember { mutableStateOf(draft.school) }
    var showSchoolResults by remember { mutableStateOf(false) }

    Text("1. Informacje o szkole", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(
        value = schoolQuery,
        onValueChange = {
            schoolQuery = it
            update(draft.copy(school = it))
            showSchoolResults = it.trim().length >= 3
            if (schools.value == null) schools.value = loadSchools(context)
        },
        label = { Text("Wyszukaj szkoĹ‚Ä™ w RSPO") },
        placeholder = { Text("Wpisz nazwÄ™ lub miejscowoĹ›Ä‡ (min. 3 znaki)") },
        modifier = Modifier.fillMaxWidth()
    )
    if (showSchoolResults && !schools.value.isNullOrEmpty()) {
        val query = schoolQuery.trim()
        val results = schools.value!!.asSequence().filter {
            it.name.contains(query, ignoreCase = true) || it.town.contains(query, ignoreCase = true) ||
                    it.municipality.contains(query, ignoreCase = true) || it.number.contains(query, ignoreCase = true)
        }.take(20).toList()
        results.forEach { school ->
            Card(modifier = Modifier.fillMaxWidth().clickable {
                schoolQuery = school.name
                update(draft.copy(school = school.name, schoolTown = school.town))
                showSchoolResults = false
            }) {
                Column(Modifier.padding(12.dp)) {
                    Text(school.name, style = MaterialTheme.typography.titleSmall)
                    Text("${school.town}${if (school.address.isNotBlank()) " â€˘ ${school.address}" else ""}")
                    Text("RSPO: ${school.number} â€˘ ${school.type}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (results.isEmpty()) Text("Brak wynikĂłw dla podanej frazy.")
    } else if (showSchoolResults && schools.value == null) {
        Text("Wczytywanie bazy RSPOâ€¦")
    } else if (showSchoolResults && schools.value.isNullOrEmpty()) {
        Text("Nie udaĹ‚o siÄ™ wczytaÄ‡ bazy RSPO. SprawdĹş, czy plik app/src/main/assets/rspo.csv istnieje.")
    }

    OutlinedTextField(draft.schoolTown, { update(draft.copy(schoolTown = it)) },
        label = { Text("MiejscowoĹ›Ä‡ szkoĹ‚y") }, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    Text("2. Informacje o uczniu", style = MaterialTheme.typography.titleLarge)
    OutlinedTextField(draft.studentName, { update(draft.copy(studentName = it)) },
        label = { Text("ImiÄ™ i nazwisko ucznia") }, modifier = Modifier.fillMaxWidth())
    OutlinedButton(onClick = {
        val calendar = Calendar.getInstance()
        val parts = draft.birthDate.split(".")
        if (parts.size == 3) {
            parts.getOrNull(2)?.toIntOrNull()?.let { calendar.set(Calendar.YEAR, it) }
            parts.getOrNull(1)?.toIntOrNull()?.let { calendar.set(Calendar.MONTH, it - 1) }
            parts.getOrNull(0)?.toIntOrNull()?.let { calendar.set(Calendar.DAY_OF_MONTH, it) }
        }
        DatePickerDialog(
            context,
            { _, year, month, day -> update(draft.copy(birthDate = "%02d.%02d.%04d".format(day, month + 1, year))) },
            calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }, modifier = Modifier.fillMaxWidth()) {
        Text(if (draft.birthDate.isBlank()) "Wybierz datÄ™ urodzenia" else "Data urodzenia: ${draft.birthDate}")
    }
    OutlinedTextField(draft.className, { update(draft.copy(className = it)) },
        label = { Text("Klasa") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(draft.schoolYear, { update(draft.copy(schoolYear = it)) },
        label = { Text("Rok szkolny") }, placeholder = { Text("np. 2026/2027") }, modifier = Modifier.fillMaxWidth())
    Text("Okres objÄ™ty obserwacjÄ…", style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            val chosen = "%02d.%02d.%04d".format(d, m + 1, y)
            val startParts = chosen.split(".").mapNotNull { it.toIntOrNull() }
            val endParts = draft.observationEnd.split(".").mapNotNull { it.toIntOrNull() }
            val endBefore = endParts.size == 3 && (endParts[2] < startParts[2] || (endParts[2] == startParts[2] && (endParts[1] < startParts[1] || (endParts[1] == startParts[1] && endParts[0] < startParts[0]))))
            update(draft.copy(observationStart = chosen, observationEnd = if (endBefore) "" else draft.observationEnd))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text(if (draft.observationStart.isBlank()) "Wybierz datÄ™ rozpoczÄ™cia" else "Od: ${draft.observationStart}") }
    OutlinedButton(onClick = {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            val chosen = "%02d.%02d.%04d".format(d, m + 1, y)
            fun key(s: String): Int = s.split(".").mapNotNull { it.toIntOrNull() }.let { if (it.size == 3) it[2]*10000+it[1]*100+it[0] else 0 }
            if (draft.observationStart.isBlank() || key(chosen) >= key(draft.observationStart)) update(draft.copy(observationEnd = chosen))
            else android.widget.Toast.makeText(context, "Data zakoĹ„czenia nie moĹĽe byÄ‡ wczeĹ›niejsza niĹĽ data rozpoczÄ™cia.", android.widget.Toast.LENGTH_LONG).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }, modifier = Modifier.fillMaxWidth()) { Text(if (draft.observationEnd.isBlank()) "Wybierz datÄ™ zakoĹ„czenia" else "Do: ${draft.observationEnd}") }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    if (value.isNotBlank()) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value)
        }
    }
}










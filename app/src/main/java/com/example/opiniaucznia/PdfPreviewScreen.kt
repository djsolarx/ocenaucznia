package com.example.opiniaucznia

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun pdfPageCount(file: File): Int {
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
    }.getOrDefault(0)
}

@Composable
private fun PdfPageImage(file: File, pageIndex: Int) {
    val bitmap by produceState<Bitmap?>(
        initialValue = null,
        key1 = file.absolutePath,
        key2 = pageIndex
    ) {
        value = withContext(Dispatchers.IO) {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    renderer.openPage(pageIndex).use { page ->
                        val width = 1000
                        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { image ->
                            image.eraseColor(Color.WHITE)
                            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }


    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Podgląd strony ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Text(
            "Ładowanie strony ${pageIndex + 1}…",
            modifier = Modifier.padding(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfPreviewScreen(
    file: File,
    onBack: () -> Unit,
    onPrint: () -> Unit
) {
    val count = remember(file.absolutePath) { pdfPageCount(file) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Podgląd wydruku") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Wróć") }
                },
                actions = {
                    Button(onClick = onPrint) { Text("Drukuj") }
                }
            )
        }
    ) { padding ->
        if (count <= 0) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Nie można odczytać stron tego pliku PDF.")
                Button(onClick = onBack) { Text("Wróć do formularza") }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items((0 until count).toList(), key = { it }) { index ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Strona ${index + 1} z $count",
                            style = MaterialTheme.typography.labelMedium
                        )
                        PdfPageImage(file, index)
                    }
                }
            }
        }
    }
}


package com.example.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.Book
import com.example.data.local.entity.Chapter
import com.example.ui.components.BookCoverImage
import com.example.ui.theme.LuxGoldAccent
import java.io.File
import java.io.FileOutputStream

@Composable
fun EditBookDialog(
    book: Book,
    chapters: List<Chapter>,
    onDismiss: () -> Unit,
    onSave: (newTitle: String, newAuthor: String, newCoverPath: String?, updatedChapters: List<Pair<Long, String>>) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var coverPath by remember { mutableStateOf(book.coverPath) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val chapterTitles = remember {
        mutableStateListOf<String>().apply {
            addAll(chapters.map { it.title })
        }
    }

    // Android Photo Picker (zero runtime permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bookDir = File(context.filesDir, "books/${book.id}")
                if (!bookDir.exists()) bookDir.mkdirs()
                val targetFile = File(bookDir, "user_cover_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                coverPath = targetFile.absolutePath
            } catch (e: Exception) {
                // Ignore error copying cover
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.edit_book),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BookCoverImage(
                            coverPath = coverPath,
                            title = title,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("pick_cover_btn")
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.change_cover))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            errorMessage = null
                        },
                        label = { Text(stringResource(R.string.book_title_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_book_title_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text(stringResource(R.string.book_author_label)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_book_author_input")
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.edit_chapters),
                        style = MaterialTheme.typography.titleSmall,
                        color = LuxGoldAccent
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                itemsIndexed(chapters) { idx, chapter ->
                    OutlinedTextField(
                        value = chapterTitles.getOrElse(idx) { chapter.title },
                        onValueChange = { newVal ->
                            if (idx in chapterTitles.indices) {
                                chapterTitles[idx] = newVal
                                errorMessage = null
                            }
                        },
                        label = { Text("Capítulo ${idx + 1}") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("edit_chapter_input_$idx")
                    )
                }

                if (errorMessage != null) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.trim().isEmpty()) {
                        errorMessage = "El título del libro no puede estar vacío"
                        return@TextButton
                    }
                    if (chapterTitles.any { it.trim().isEmpty() }) {
                        errorMessage = "El título del capítulo no puede estar vacío"
                        return@TextButton
                    }

                    val updatedPairs = chapters.mapIndexed { idx, ch ->
                        ch.id to chapterTitles.getOrElse(idx) { ch.title }.trim()
                    }
                    onSave(title.trim(), author.trim(), coverPath, updatedPairs)
                },
                modifier = Modifier.testTag("edit_book_save_btn")
            ) {
                Text(stringResource(R.string.save), color = LuxGoldAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

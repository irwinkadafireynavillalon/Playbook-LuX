package com.example.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.Bookmark
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText
import com.example.util.TimeUtils

@Composable
fun BookmarksDialog(
    currentPosMs: Long,
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onAddBookmark: (note: String) -> Unit,
    onSelectBookmark: (Bookmark) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    var isAdding by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.player_bookmarks),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!isAdding) {
                    Button(
                        onClick = { isAdding = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = LuxGoldBright
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_bookmark_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Añadir en ${TimeUtils.formatMs(currentPosMs)}")
                    }
                } else {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text(stringResource(R.string.player_bookmark_note_hint)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bookmark_note_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { isAdding = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = {
                                onAddBookmark(noteText.trim())
                                noteText = ""
                                isAdding = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                            modifier = Modifier.testTag("save_bookmark_btn")
                        ) {
                            Text(stringResource(R.string.save), color = MaterialTheme.colorScheme.surface)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (bookmarks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.player_bookmarks_empty),
                        color = LuxSilverText,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectBookmark(bookmark) }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = LuxGoldAccent)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = TimeUtils.formatMs(bookmark.posMs),
                                        fontWeight = FontWeight.Bold,
                                        color = LuxGoldBright
                                    )
                                    if (bookmark.note.isNotBlank()) {
                                        Text(
                                            text = bookmark.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = LuxSilverText
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = LuxSilverText)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("bookmarks_dialog_close_btn")) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

package com.example.ui.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.example.data.local.entity.Folder
import com.example.ui.theme.LuxGoldAccent

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_name_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isEmpty()) {
                        isError = true
                    } else {
                        onConfirm(name.trim())
                    }
                },
                modifier = Modifier.testTag("folder_create_confirm_btn")
            ) {
                Text(stringResource(R.string.save), color = LuxGoldAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun RenameFolderDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var isError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_rename)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        isError = false
                    },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("folder_rename_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.trim().isEmpty()) {
                        isError = true
                    } else {
                        onConfirm(name.trim())
                    }
                },
                modifier = Modifier.testTag("folder_rename_confirm_btn")
            ) {
                Text(stringResource(R.string.save), color = LuxGoldAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun MoveToFolderDialog(
    folders: List<Folder>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onSelectFolder: (Long?) -> Unit
) {
    var selectedId by remember { mutableStateOf(currentFolderId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_to_folder)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = null }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == null,
                            onClick = { selectedId = null }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.FolderOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.no_folder))
                    }
                }
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedId = folder.id }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedId == folder.id,
                            onClick = { selectedId = folder.id }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Icon(Icons.Default.Folder, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(folder.name, fontWeight = FontWeight.Medium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelectFolder(selectedId) },
                modifier = Modifier.testTag("folder_move_confirm_btn")
            ) {
                Text(stringResource(R.string.ok), color = LuxGoldAccent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

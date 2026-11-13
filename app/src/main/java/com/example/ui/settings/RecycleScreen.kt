package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.entity.Book
import com.example.ui.components.BookCoverImage
import com.example.ui.components.EmptyStateView
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxSilverText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val recycledBooks by viewModel.recycledBooks.collectAsStateWithLifecycle()

    var showEmptyConfirmDialog by remember { mutableStateOf(false) }
    var bookToDeletePermanently by remember { mutableStateOf<Book?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recycle_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("recycle_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (recycledBooks.isNotEmpty()) {
                        IconButton(
                            onClick = { showEmptyConfirmDialog = true },
                            modifier = Modifier.testTag("empty_recycle_btn")
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.recycle_empty_btn),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Notice banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.recycle_auto_delete_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = LuxSilverText,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            if (recycledBooks.isEmpty()) {
                EmptyStateView(
                    icon = Icons.Default.DeleteSweep,
                    title = stringResource(R.string.recycle_empty),
                    description = "Los audiolibros eliminados aparecerán aquí durante 30 días antes de borrarse definitivamente.",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(recycledBooks, key = { it.id }) { book ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("recycled_item_${book.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCoverImage(
                                    coverPath = book.coverPath,
                                    title = book.title,
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(6.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = book.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LuxSilverText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Restore Button
                                IconButton(
                                    onClick = { viewModel.restoreBook(book.id) },
                                    modifier = Modifier.testTag("restore_btn_${book.id}")
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = stringResource(R.string.restore),
                                        tint = LuxGoldAccent
                                    )
                                }

                                // Delete Permanently Button
                                IconButton(
                                    onClick = { bookToDeletePermanently = book },
                                    modifier = Modifier.testTag("delete_perm_btn_${book.id}")
                                ) {
                                    Icon(
                                        Icons.Default.DeleteForever,
                                        contentDescription = stringResource(R.string.recycle_delete_permanent),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirmDialog = false },
            title = { Text(stringResource(R.string.recycle_empty_btn)) },
            text = { Text(stringResource(R.string.recycle_empty_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.emptyRecycle(context)
                        showEmptyConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_empty_recycle_btn")
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    bookToDeletePermanently?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDeletePermanently = null },
            title = { Text(stringResource(R.string.recycle_delete_permanent)) },
            text = { Text(stringResource(R.string.recycle_delete_permanent_confirm, book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePermanently(context, book.id)
                        bookToDeletePermanently = null
                    },
                    modifier = Modifier.testTag("confirm_delete_permanently_btn")
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDeletePermanently = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

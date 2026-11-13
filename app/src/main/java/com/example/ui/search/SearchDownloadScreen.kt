package com.example.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.engine.DownloadTaskUi
import com.example.engine.PublicBookSearchEngine
import com.example.ui.components.EmptyStateView
import com.example.ui.effects.LuxAnimatedProgressBar
import com.example.ui.effects.LuxProcessingLottie
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText

@Composable
fun SearchDownloadScreen(
    searchEngine: PublicBookSearchEngine,
    onOpenVaultTexts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by searchEngine.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current
    var currentTab by remember { mutableIntStateOf(0) } // 0 = Buscar, 1 = Descargas

    LaunchedEffect(state.lastMessage) {
        state.lastMessage?.let {
            snackbarHostState.showSnackbar(it)
            searchEngine.clearMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = LuxGoldAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[currentTab]),
                        color = LuxGoldAccent,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.tab_search),
                            fontWeight = if (currentTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentTab == 0) LuxGoldBright else LuxSilverText
                        )
                    }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = {
                        Text(
                            text = "${stringResource(R.string.tab_downloads)} (${state.downloads.size})",
                            fontWeight = if (currentTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (currentTab == 1) LuxGoldBright else LuxSilverText
                        )
                    }
                )
            }

            if (currentTab == 0) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = state.query,
                                onValueChange = { searchEngine.setQuery(it) },
                                placeholder = { Text("Título, autor o tema (ej. Quijote, Poe)…") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboard?.hide()
                                    searchEngine.search()
                                }),
                                trailingIcon = {
                                    if (state.query.isNotEmpty()) {
                                        IconButton(onClick = { searchEngine.setQuery("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f).testTag("search_books_input")
                            )

                            Button(
                                onClick = {
                                    keyboard?.hide()
                                    searchEngine.search()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(54.dp).testTag("search_books_go_btn")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.surface)
                            }
                        }
                    }

                    // Format chips & Language chips
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(listOf("EPUB", "TXT", "HTML")) { fmt ->
                                FilterChip(
                                    selected = fmt in state.selectedFormats,
                                    onClick = { searchEngine.toggleFormat(fmt) },
                                    label = { Text(fmt, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                        selectedLabelColor = LuxGoldBright
                                    )
                                )
                            }
                            item { Spacer(modifier = Modifier.width(4.dp)) }
                            items(listOf("es" to "Español", "en" to "Inglés", "all" to "Todos")) { (code, label) ->
                                FilterChip(
                                    selected = state.selectedLanguage == code,
                                    onClick = {
                                        searchEngine.setLanguage(code)
                                        searchEngine.search()
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                        selectedLabelColor = LuxGoldBright
                                    )
                                )
                            }
                        }
                    }

                    // Source filter chips + Wi-Fi only switch
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(listOf("Gutenberg", "Internet Archive", "Wikisource")) { src ->
                                    FilterChip(
                                        selected = src in state.selectedSources,
                                        onClick = { searchEngine.toggleSource(src) },
                                        label = { Text(src, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = LuxGoldAccent.copy(alpha = 0.2f),
                                            selectedLabelColor = LuxGoldBright
                                        )
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = state.wifiOnly,
                                    onCheckedChange = { searchEngine.setWifiOnly(it) }
                                )
                            }
                        }
                    }

                    if (state.isSearching) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LuxProcessingLottie(size = 32.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Buscando en bibliotecas públicas…", color = LuxSilverText)
                            }
                        }
                    } else if (state.searchError != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = state.searchError ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = { searchEngine.search() }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reintentar", tint = LuxGoldAccent)
                                    }
                                }
                            }
                        }
                    } else if (state.results.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Default.Search,
                                title = "Sin resultados",
                                description = "Prueba con otro título, autor o habilita más fuentes arriba."
                            )
                        }
                    } else {
                        items(state.results, key = { it.remoteId + it.format }) { result ->
                            val existingDl = state.downloads.firstOrNull { it.result.remoteId == result.remoteId }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Surface(
                                                color = LuxGoldAccent.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = result.format,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = LuxGoldBright,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text(
                                                text = "${result.source} • ${result.language.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = LuxGoldAccent
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = result.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = result.author,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = LuxSilverText,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (existingDl?.isCompleted == true) {
                                        OutlinedButton(onClick = onOpenVaultTexts, shape = RoundedCornerShape(8.dp)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldBright)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Bóveda", fontSize = 11.sp, color = LuxGoldBright)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                searchEngine.startDownload(result)
                                                currentTab = 1
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(stringResource(R.string.download_btn), fontSize = 12.sp, color = MaterialTheme.colorScheme.surface)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Descargas Tab
                if (state.downloads.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(R.string.downloads_empty_title),
                        description = stringResource(R.string.downloads_empty_desc),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.downloads, key = { it.id }) { task ->
                            DownloadTaskCard(
                                task = task,
                                onTogglePause = { searchEngine.togglePauseDownload(task.id) },
                                onCancel = { searchEngine.cancelDownload(task.id) },
                                onOpenVault = onOpenVaultTexts
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskUi,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onOpenVault: () -> Unit
) {
    val kbDone = task.downloadedBytes / 1024
    val sizeText = if (task.totalBytes > 0) {
        "${kbDone} KB / ${task.totalBytes / 1024} KB"
    } else {
        "$kbDone KB descargados"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (!task.isCompleted && !task.isPaused && task.errorMessage == null) {
                    LuxProcessingLottie(size = 24.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.result.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            task.errorMessage != null -> "Error: ${task.errorMessage}"
                            task.isCompleted -> "Completado y guardado en Bóveda → Textos"
                            task.isPaused -> "En pausa • $sizeText"
                            else -> "Descargando (${task.result.format}) • $sizeText"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.errorMessage != null) MaterialTheme.colorScheme.error else LuxGoldAccent
                    )
                }

                if (task.isCompleted) {
                    OutlinedButton(onClick = onOpenVault, shape = RoundedCornerShape(8.dp)) {
                        Text("Ir a Bóveda", fontSize = 11.sp, color = LuxGoldBright)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = LuxGoldBright)
                    }
                } else {
                    Row {
                        IconButton(onClick = onTogglePause, modifier = Modifier.size(34.dp)) {
                            Icon(
                                imageVector = if (task.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (task.isPaused) "Reanudar" else "Pausar",
                                tint = LuxGoldAccent
                            )
                        }
                        IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LuxAnimatedProgressBar(
                progress = task.progress,
                isError = task.errorMessage != null
            )
        }
    }
}

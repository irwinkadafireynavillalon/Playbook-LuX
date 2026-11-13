package com.example.ui.player

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.R
import com.example.playback.AudioOutputDeviceUi
import com.example.playback.SleepTimerMode
import com.example.ui.components.BookCoverImage
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverBright
import com.example.ui.theme.LuxSilverText
import com.example.util.TimeUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val waveformBars by viewModel.waveformBars.collectAsStateWithLifecycle()
    val chapterParagraphs by viewModel.chapterParagraphs.collectAsStateWithLifecycle()

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showChaptersSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showTextViewerSheet by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }
    var audioOutputs by remember { mutableStateOf<List<AudioOutputDeviceUi>>(emptyList()) }
    var lyricsFontSizeSp by remember { mutableFloatStateOf(16f) }

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragSliderValue by remember { mutableFloatStateOf(0f) }

    val book = uiState.book
    val currentChapter = uiState.chapters.getOrNull(uiState.currentChapterIndex)
    val chapterDuration = uiState.chapterDurationMs.coerceAtLeast(1L)
    val currentPosition = if (isDraggingSlider) {
        (dragSliderValue * chapterDuration).toLong()
    } else {
        uiState.currentPositionMs.coerceIn(0L, chapterDuration)
    }
    val sliderProgress = (currentPosition.toFloat() / chapterDuration.toFloat()).coerceIn(0f, 1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = book?.title ?: stringResource(R.string.player_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("player_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    if (book != null) {
                        IconButton(
                            onClick = {
                                audioOutputs = viewModel.getAvailableAudioOutputs()
                                showAudioOutputSheet = true
                            },
                            modifier = Modifier.testTag("player_output_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.BluetoothAudio,
                                contentDescription = "Salida de audio",
                                tint = LuxSilverText
                            )
                        }
                        IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.testTag("player_fav_btn")) {
                            Icon(
                                imageVector = if (book.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (book.favorite) LuxGoldAccent else LuxSilverText
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        modifier = modifier
    ) { padding ->
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay ningún audiolibro en reproducción", color = LuxSilverText)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 500.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                uiState.errorMessage?.let { errorMsg ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.dismissError() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Ocultar",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Cover Art with 2.5D gyro/touch tilt + horizontal swipe-to-skip chapter
                BookCoverImage(
                    coverPath = book.coverPath,
                    title = book.title,
                    modifier = Modifier.size(236.dp),
                    shape = RoundedCornerShape(20.dp),
                    enable25DTilt = true,
                    interactiveTouchTilt = true,
                    onHorizontalSwipe = { direction ->
                        if (direction > 0) viewModel.nextChapter() else viewModel.previousChapter()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Desliza la portada para cambiar de capítulo",
                    style = MaterialTheme.typography.labelSmall,
                    color = LuxSilverText.copy(alpha = 0.55f),
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Titles
                Text(
                    text = currentChapter?.title ?: "Capítulo ${uiState.currentChapterIndex + 1}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = LuxSilverBright,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${book.title} • ${book.author}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LuxSilverText,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time zero-permission PCM Audio Waveform Visualizer
                WaveformVisualizerBar(
                    bars = waveformBars,
                    isPlaying = uiState.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .testTag("player_waveform_visualizer")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Draggable Slider (P1)
                Slider(
                    value = if (isDraggingSlider) dragSliderValue else sliderProgress,
                    onValueChange = {
                        isDraggingSlider = true
                        dragSliderValue = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (dragSliderValue * chapterDuration).toLong()
                        viewModel.seekTo(targetMs)
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = LuxGoldAccent,
                        activeTrackColor = LuxGoldAccent,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_progress_slider")
                )

                val remainingMs = (chapterDuration - currentPosition).coerceAtLeast(0L)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = TimeUtils.formatMs(currentPosition),
                        style = MaterialTheme.typography.labelMedium,
                        color = LuxSilverText
                    )
                    Text(
                        text = "Cap. ${uiState.currentChapterIndex + 1}/${uiState.chapters.size.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuxGoldAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "-${TimeUtils.formatMs(remainingMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LuxSilverText
                    )
                }

                // Active A-B Loop indicator banner if set
                if (uiState.abLoopStartMs != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val startStr = TimeUtils.formatMs(uiState.abLoopStartMs ?: 0L)
                    val endStr = uiState.abLoopEndMs?.let { TimeUtils.formatMs(it) } ?: "toca B para fijar"
                    Surface(
                        color = LuxGoldAccent.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { viewModel.clearAbLoop() }
                            .testTag("player_ab_loop_pill")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bucle A-B: $startStr → $endStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuxGoldBright,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.Close, contentDescription = "Quitar bucle A-B", tint = LuxGoldBright, modifier = Modifier.size(14.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Secondary Music-Player Strip: Shuffle, Repeat, A-B Loop, Queue, Text Viewer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.testTag("player_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Aleatorio",
                            tint = if (uiState.shuffleEnabled) LuxGoldBright else LuxSilverText.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.cycleRepeatMode() },
                        modifier = Modifier.testTag("player_repeat_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Modo de repetición",
                            tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) LuxGoldBright else LuxSilverText.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    FilterChip(
                        selected = uiState.abLoopStartMs != null,
                        onClick = { viewModel.toggleAbLoopPoint() },
                        label = {
                            val label = when {
                                uiState.abLoopEndMs != null -> "A-B ✓"
                                uiState.abLoopStartMs != null -> "Fijar B"
                                else -> "A-B"
                            }
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                            selectedLabelColor = LuxGoldBright
                        ),
                        modifier = Modifier.height(30.dp).testTag("player_ab_btn")
                    )

                    IconButton(
                        onClick = { showTextViewerSheet = true },
                        modifier = Modifier.testTag("player_lyrics_text_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Seguir texto",
                            tint = if (chapterParagraphs.isNotEmpty()) LuxGoldBright else LuxSilverText.copy(alpha = 0.65f),
                            modifier = Modifier.size(21.dp)
                        )
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("player_queue_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.queueEntries.size > 1) {
                                    Badge(containerColor = LuxGoldAccent) {
                                        Text("${uiState.queueEntries.size}", color = Color.Black, fontSize = 9.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Cola de reproducción",
                                tint = LuxSilverBright,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Primary Transport Controls
                val configuredJumpMs = uiState.jumpSeconds * 1000L
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.previousChapter() },
                        modifier = Modifier.testTag("player_prev_chapter_btn")
                    ) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_prev_chapter), tint = LuxSilverBright)
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(-30000L) },
                        modifier = Modifier.testTag("player_rewind_30_btn")
                    ) {
                        Icon(Icons.Default.Replay30, contentDescription = stringResource(R.string.player_rewind_30), tint = LuxSilverText)
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(-configuredJumpMs) },
                        modifier = Modifier.testTag("player_rewind_15_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (uiState.jumpSeconds >= 30) Icons.Default.Replay30 else Icons.Default.Replay10,
                                contentDescription = "Retroceder ${uiState.jumpSeconds}s",
                                tint = LuxSilverBright
                            )
                            Text("${uiState.jumpSeconds}s", fontSize = 9.sp, color = LuxSilverText)
                        }
                    }

                    FilledIconButton(
                        onClick = { viewModel.togglePlay() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = LuxGoldAccent,
                            contentColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .size(68.dp)
                            .testTag("player_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (uiState.isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(configuredJumpMs) },
                        modifier = Modifier.testTag("player_forward_15_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (uiState.jumpSeconds >= 30) Icons.Default.Forward30 else Icons.Default.Forward10,
                                contentDescription = "Adelantar ${uiState.jumpSeconds}s",
                                tint = LuxSilverBright
                            )
                            Text("${uiState.jumpSeconds}s", fontSize = 9.sp, color = LuxSilverText)
                        }
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(30000L) },
                        modifier = Modifier.testTag("player_forward_30_btn")
                    ) {
                        Icon(Icons.Default.Forward30, contentDescription = stringResource(R.string.player_forward_30), tint = LuxSilverText)
                    }

                    IconButton(
                        onClick = { viewModel.nextChapter() },
                        modifier = Modifier.testTag("player_next_chapter_btn")
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_next_chapter), tint = LuxSilverBright)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Tools Row: Speed, Sleep Timer, Bookmarks, Chapters
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSpeedDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = LuxGoldAccent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("player_speed_btn")
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "${"%.2f".format(uiState.speed)}x", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showSleepTimerDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (uiState.sleepTimerMode != SleepTimerMode.OFF) LuxGoldBright else LuxSilverText
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("player_timer_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (uiState.sleepTimerMode != SleepTimerMode.OFF) {
                                    Badge(containerColor = LuxGoldAccent) {
                                        val badgeText = uiState.sleepTimerRemainingSeconds?.let { sec ->
                                            "${sec / 60}m"
                                        } ?: "cap"
                                        Text(badgeText, color = Color.Black, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = stringResource(R.string.player_sleep_timer), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.player_sleep_timer), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showBookmarksDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = LuxSilverBright
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("player_bookmarks_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (bookmarks.isNotEmpty()) {
                                    Badge(containerColor = LuxGoldAccent) {
                                        Text("${bookmarks.size}", color = Color.Black, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = stringResource(R.string.player_bookmarks), modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.player_bookmarks), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { showChaptersSheet = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = LuxGoldBright
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("player_chapters_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.edit_chapters), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Capítulos (${uiState.chapters.size})", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // BottomSheet: Chapters list
    if (showChaptersSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showChaptersSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Capítulos (${uiState.chapters.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    itemsIndexed(uiState.chapters, key = { idx, ch -> "${idx}_${ch.id}" }) { idx, chapter ->
                        val isCurrent = idx == uiState.currentChapterIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    viewModel.jumpToChapter(idx)
                                    showChaptersSheet = false
                                }
                                .testTag("player_sheet_chapter_${chapter.id}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) LuxGoldAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.Headphones else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isCurrent) LuxGoldBright else LuxSilverText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = chapter.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) LuxGoldBright else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = TimeUtils.formatMs(chapter.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LuxSilverText
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // BottomSheet: Live Playback Queue ("En cola") with Reorder & Remove
    if (showQueueSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    text = "Cola de reproducción (${uiState.queueEntries.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    itemsIndexed(uiState.queueEntries, key = { _, entry -> entry.uid }) { idx, entry ->
                        val isCurrent = idx == uiState.currentChapterIndex
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { viewModel.jumpToChapter(idx) }
                                .testTag("queue_item_${idx}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrent) LuxGoldAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Default.Headphones else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = if (isCurrent) LuxGoldBright else LuxSilverText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.chapter.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrent) LuxGoldBright else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${entry.book.title} • ${TimeUtils.formatMs(entry.chapter.durationMs)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuxSilverText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.moveQueueItem(idx, idx - 1) },
                                    enabled = idx > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.moveQueueItem(idx, idx + 1) },
                                    enabled = idx < uiState.queueEntries.lastIndex,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { viewModel.removeQueueItem(idx) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar de la cola", tint = LuxSilverText, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // BottomSheet: Synchronized Source Text ("Seguir texto / Letras")
    if (showTextViewerSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val totalChars = chapterParagraphs.sumOf { it.length }.coerceAtLeast(1)
        val targetCharPos = (sliderProgress * totalChars).toInt()
        var runningChars = 0
        var activeParaIdx = 0
        for (i in chapterParagraphs.indices) {
            if (targetCharPos >= runningChars) activeParaIdx = i
            runningChars += chapterParagraphs[i].length
        }

        val listState = rememberLazyListState()
        LaunchedEffect(showTextViewerSheet, activeParaIdx) {
            if (chapterParagraphs.isNotEmpty() && activeParaIdx in chapterParagraphs.indices) {
                runCatching { listState.animateScrollToItem(activeParaIdx) }
            }
        }

        ModalBottomSheet(
            onDismissRequest = { showTextViewerSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentChapter?.title ?: "Texto sincronizado",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LuxGoldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = { lyricsFontSizeSp = (lyricsFontSizeSp - 2f).coerceAtLeast(12f) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("A-", fontSize = 11.sp) }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = { lyricsFontSizeSp = (lyricsFontSizeSp + 2f).coerceAtMost(24f) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("A+", fontSize = 11.sp) }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (chapterParagraphs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Este audiolibro no tiene texto vinculado disponible. Los nuevos libros convertidos desde Textos incluyen lectura sincronizada automáticamente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = LuxSilverText,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(chapterParagraphs) { idx, para ->
                            val isActive = idx == activeParaIdx
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isActive) LuxGoldAccent.copy(alpha = 0.14f) else Color.Transparent)
                                    .clickable {
                                        val estRatio = chapterParagraphs.take(idx).sumOf { it.length }.toFloat() / totalChars.toFloat()
                                        viewModel.seekTo((estRatio * chapterDuration).toLong())
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = para,
                                    fontSize = lyricsFontSizeSp.sp,
                                    lineHeight = (lyricsFontSizeSp * 1.45f).sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isActive) LuxGoldBright else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // BottomSheet: Quick Audio Output Switcher
    if (showAudioOutputSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAudioOutputSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Dispositivos de salida de audio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright
                )
                audioOutputs.forEach { dev ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (dev.isActiveRoute) LuxGoldAccent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    dev.isBluetooth -> Icons.Default.BluetoothAudio
                                    dev.isWired -> Icons.Default.Headphones
                                    else -> Icons.Default.Speaker
                                },
                                contentDescription = null,
                                tint = if (dev.isActiveRoute) LuxGoldBright else LuxSilverText
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dev.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(dev.typeLabel, style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            }
                            if (dev.isActiveRoute) {
                                Icon(Icons.Default.CheckCircle, contentDescription = "Activo", tint = LuxGoldBright, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BluetoothAudio, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Abrir ajustes de Bluetooth del sistema", color = LuxGoldAccent)
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }

    if (showSpeedDialog) {
        SpeedDialog(
            currentSpeed = uiState.speed,
            onDismiss = { showSpeedDialog = false },
            onSelectSpeed = { speed ->
                viewModel.setSpeed(speed)
                showSpeedDialog = false
            }
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            currentMode = uiState.sleepTimerMode,
            onDismiss = { showSleepTimerDialog = false },
            onSelectMode = { mode ->
                viewModel.setSleepTimer(mode)
                showSleepTimerDialog = false
            }
        )
    }

    if (showBookmarksDialog) {
        BookmarksDialog(
            currentPosMs = uiState.currentPositionMs,
            bookmarks = bookmarks,
            onDismiss = { showBookmarksDialog = false },
            onAddBookmark = { note ->
                viewModel.addBookmark(note)
            },
            onSelectBookmark = { bookmark ->
                viewModel.jumpToBookmark(bookmark)
                showBookmarksDialog = false
            },
            onDeleteBookmark = { bookmarkId ->
                viewModel.deleteBookmark(bookmarkId)
            }
        )
    }
}

@Composable
private fun WaveformVisualizerBar(
    bars: List<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { idx, rawLevel ->
            val target = if (isPlaying) rawLevel.coerceIn(0.12f, 1f) else 0.14f
            val animatedFrac by animateFloatAsState(
                targetValue = target,
                animationSpec = tween(durationMillis = 90),
                label = "waveBar_$idx"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(animatedFrac)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(LuxGoldBright, LuxGoldAccent)
                        )
                    )
            )
        }
    }
}

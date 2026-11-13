package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.playback.PlaybackUiState
import com.example.ui.effects.rememberMetallicGoldBrush
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxSilverText
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    uiState: PlaybackUiState,
    onTogglePlay: () -> Unit,
    onRewindJump: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPreviousChapter: () -> Unit = {},
    onNextChapter: () -> Unit = {},
    onDismissStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val book = uiState.book ?: return
    val currentChapter = uiState.chapters.getOrNull(uiState.currentChapterIndex)
    val progress = if (uiState.chapterDurationMs > 0) {
        (uiState.currentPositionMs.toFloat() / uiState.chapterDurationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f
    val goldBrush = rememberMetallicGoldBrush(animated = uiState.isPlaying)

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .offset { IntOffset(dragX.roundToInt().coerceIn(-80, 80), dragY.roundToInt().coerceIn(0, 80)) }
            .pointerInput(book.id, uiState.currentChapterIndex) {
                detectDragGestures(
                    onDragEnd = {
                        if (dragY > 55f && dragY > abs(dragX)) {
                            onDismissStop()
                        } else if (abs(dragX) > 90f && abs(dragX) > abs(dragY) * 1.3f) {
                            if (dragX < 0f) onNextChapter() else onPreviousChapter()
                        }
                        dragX = 0f
                        dragY = 0f
                    },
                    onDragCancel = {
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragX += dragAmount.x
                        dragY = (dragY + dragAmount.y).coerceAtLeast(0f)
                    }
                )
            }
            .clickable(onClick = onOpenPlayer)
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // 2dp animated metallic gold shimmer progress line along top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(goldBrush)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BookCoverImage(
                    coverPath = book.coverPath,
                    title = book.title,
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(4.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentChapter?.title ?: book.author,
                        style = MaterialTheme.typography.labelSmall,
                        color = LuxSilverText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onRewindJump,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("mini_rewind_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.jumpSeconds >= 30) Icons.Default.Replay30 else Icons.Default.Replay10,
                        contentDescription = "Retroceder ${uiState.jumpSeconds}s",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("mini_play_pause_btn")
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pausar" else "Reproducir",
                        tint = LuxGoldAccent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

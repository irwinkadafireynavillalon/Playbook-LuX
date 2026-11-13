package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.Book
import com.example.data.local.entity.ListeningStat
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText
import com.example.util.TimeUtils
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun ListeningStatsDialog(
    totalListenedSeconds: Long,
    recentStats: List<ListeningStat>,
    recentBooks: List<Book>,
    onDismiss: () -> Unit,
    onPlayBook: ((Book) -> Unit)? = null,
    onOpenBookDetail: ((Book) -> Unit)? = null
) {
    val today = remember { LocalDate.now() }
    val statsByDate = remember(recentStats) { recentStats.associateBy { it.dateKey } }
    val todaySeconds = statsByDate[today.toString()]?.totalSeconds ?: 0L
    val totalSessions = remember(recentStats) { recentStats.sumOf { it.sessionsCount } }

    val last7Days = remember(today, statsByDate) {
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val stat = statsByDate[date.toString()]
            DayBarData(
                label = shortSpanishDay(date.dayOfWeek),
                minutes = ((stat?.totalSeconds ?: 0L) / 60L).toInt(),
                seconds = stat?.totalSeconds ?: 0L,
                isToday = offset == 0
            )
        }
    }
    val maxDaySeconds = remember(last7Days) {
        last7Days.maxOfOrNull { it.seconds }?.coerceAtLeast(60L) ?: 60L
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = LuxGoldBright)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.stats_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("listening_stats_dialog"),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Summary metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatMetricCard(
                        label = stringResource(R.string.stats_total_time),
                        value = formatMinutesOrHours(totalListenedSeconds),
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = stringResource(R.string.stats_today_time),
                        value = formatMinutesOrHours(todaySeconds),
                        modifier = Modifier.weight(1f)
                    )
                    StatMetricCard(
                        label = stringResource(R.string.stats_sessions),
                        value = totalSessions.toString(),
                        modifier = Modifier.weight(0.8f)
                    )
                }

                // 7-day bar chart
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = stringResource(R.string.stats_last_7_days),
                            style = MaterialTheme.typography.labelMedium,
                            color = LuxGoldBright,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(104.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            last7Days.forEach { day ->
                                val ratio = (day.seconds.toFloat() / maxDaySeconds.toFloat()).coerceIn(0f, 1f)
                                val barFraction = if (day.seconds > 0L) ratio.coerceAtLeast(0.12f) else 0.05f
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    if (day.minutes > 0) {
                                        Text(
                                            text = "${day.minutes}m",
                                            fontSize = 9.sp,
                                            color = if (day.isToday) LuxGoldBright else LuxSilverText,
                                            maxLines = 1
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .width(18.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .fillMaxHeight(barFraction)
                                                .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                                .background(
                                                    if (day.seconds > 0L) {
                                                        Brush.verticalGradient(
                                                            listOf(LuxGoldBright, LuxGoldAccent.copy(alpha = 0.7f))
                                                        )
                                                    } else {
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                                                            )
                                                        )
                                                    }
                                                )
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = day.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (day.isToday) LuxGoldBright else LuxSilverText
                                    )
                                }
                            }
                        }
                    }
                }

                // Recently played audiobooks
                Text(
                    text = stringResource(R.string.stats_recent_books),
                    style = MaterialTheme.typography.labelMedium,
                    color = LuxGoldBright,
                    fontWeight = FontWeight.SemiBold
                )

                if (recentBooks.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Headphones, contentDescription = null, tint = LuxSilverText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.stats_recent_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = LuxSilverText
                        )
                    }
                } else {
                    recentBooks.take(5).forEach { book ->
                        val progressPct = if (book.durationMs > 0L) {
                            ((book.lastPosMs * 100L) / book.durationMs).toInt().coerceIn(0, 100)
                        } else 0
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = onOpenBookDetail != null || onPlayBook != null) {
                                    onOpenBookDetail?.invoke(book) ?: onPlayBook?.invoke(book)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BookCoverImage(
                                    coverPath = book.coverPath,
                                    title = book.title,
                                    modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = book.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${book.author} • $progressPct% (${TimeUtils.formatDurationHuman(book.durationMs)})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LuxSilverText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (onPlayBook != null) {
                                    IconButton(
                                        onClick = {
                                            onPlayBook(book)
                                            onDismiss()
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Reproducir",
                                            tint = LuxGoldBright,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok), color = LuxGoldBright)
            }
        }
    )
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = LuxGoldBright,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LuxSilverText,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}

private data class DayBarData(
    val label: String,
    val minutes: Int,
    val seconds: Long,
    val isToday: Boolean
)

private fun shortSpanishDay(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
    DayOfWeek.MONDAY -> "L"
    DayOfWeek.TUESDAY -> "M"
    DayOfWeek.WEDNESDAY -> "X"
    DayOfWeek.THURSDAY -> "J"
    DayOfWeek.FRIDAY -> "V"
    DayOfWeek.SATURDAY -> "S"
    DayOfWeek.SUNDAY -> "D"
}

private fun formatMinutesOrHours(totalSeconds: Long): String {
    val totalMins = (totalSeconds / 60L).coerceAtLeast(0L)
    val hours = totalMins / 60L
    val mins = totalMins % 60L
    return when {
        hours > 0L -> "${hours}h ${mins}m"
        totalSeconds in 1L..59L -> "<1m"
        else -> "${mins}m"
    }
}

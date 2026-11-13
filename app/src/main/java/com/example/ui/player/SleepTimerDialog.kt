package com.example.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.playback.SleepTimerMode
import com.example.ui.theme.LuxGoldAccent

@Composable
fun SleepTimerDialog(
    currentMode: SleepTimerMode,
    onDismiss: () -> Unit,
    onSelectMode: (SleepTimerMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.player_sleep_timer),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_off),
                    isSelected = currentMode == SleepTimerMode.OFF,
                    onClick = { onSelectMode(SleepTimerMode.OFF) }
                )
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_minutes, 15),
                    isSelected = currentMode == SleepTimerMode.MINUTES_15,
                    onClick = { onSelectMode(SleepTimerMode.MINUTES_15) }
                )
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_minutes, 30),
                    isSelected = currentMode == SleepTimerMode.MINUTES_30,
                    onClick = { onSelectMode(SleepTimerMode.MINUTES_30) }
                )
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_minutes, 45),
                    isSelected = currentMode == SleepTimerMode.MINUTES_45,
                    onClick = { onSelectMode(SleepTimerMode.MINUTES_45) }
                )
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_minutes, 60),
                    isSelected = currentMode == SleepTimerMode.MINUTES_60,
                    onClick = { onSelectMode(SleepTimerMode.MINUTES_60) }
                )
                SleepTimerOption(
                    title = stringResource(R.string.sleep_timer_end_of_chapter),
                    isSelected = currentMode == SleepTimerMode.END_OF_CHAPTER,
                    onClick = { onSelectMode(SleepTimerMode.END_OF_CHAPTER) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("sleep_timer_close_btn")) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun SleepTimerOption(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = isSelected, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) LuxGoldAccent else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
        )
    }
}

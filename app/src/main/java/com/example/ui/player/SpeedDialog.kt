package com.example.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.ui.theme.LuxGoldAccent

val AVAILABLE_SPEEDS = listOf(
    0.50f, 0.75f, 1.00f, 1.25f, 1.50f, 1.75f, 2.00f, 2.25f, 2.50f, 2.75f, 3.00f
)

@Composable
fun SpeedDialog(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onSelectSpeed: (Float) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.player_speed),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(AVAILABLE_SPEEDS) { speed ->
                    val isSelected = kotlin.math.abs(speed - currentSpeed) < 0.01f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSpeed(speed) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelectSpeed(speed) }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${"%.2f".format(speed)}x" + if (speed == 1.0f) " (Normal)" else "",
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) LuxGoldAccent else androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("speed_dialog_close_btn")) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

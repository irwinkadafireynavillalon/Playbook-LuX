package com.example.ui.translator

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.local.repository.TextItemRepository
import com.example.engine.LocalTranslatorEngine
import com.example.ui.effects.LuxAnimatedProgressBar
import com.example.ui.effects.LuxGoldButton
import com.example.ui.effects.LuxProcessingLottie
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText

private val translatorLanguages = listOf(
    "es" to "Español",
    "en" to "Inglés",
    "fr" to "Francés",
    "de" to "Alemán",
    "it" to "Italiano",
    "pt" to "Portugués"
)

@Composable
fun TranslatorScreen(
    translatorEngine: LocalTranslatorEngine,
    textItemRepository: TextItemRepository,
    initialTextId: String? = null,
    onConvertTranslatedText: (String) -> Unit,
    onOpenVaultTexts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by translatorEngine.state.collectAsStateWithLifecycle()
    val texts by textItemRepository.allTextItems.collectAsStateWithLifecycle(initialValue = emptyList())

    var showTextMenu by remember { mutableStateOf(false) }
    var showSourceMenu by remember { mutableStateOf(false) }
    var showTargetMenu by remember { mutableStateOf(false) }

    LaunchedEffect(initialTextId, texts) {
        if (texts.isNotEmpty()) {
            val targetItem = initialTextId?.let { id -> texts.firstOrNull { it.id == id } }
            if (targetItem != null && state.selectedTextItem?.id != targetItem.id) {
                translatorEngine.selectTextItem(targetItem)
            } else if (state.selectedTextItem == null) {
                translatorEngine.selectTextItem(texts.first())
            }
        }
    }

    val stepLabels = listOf(
        stringResource(R.string.translator_step_extract),
        stringResource(R.string.translator_step_detect),
        stringResource(R.string.translator_step_translate),
        stringResource(R.string.translator_step_clean),
        stringResource(R.string.translator_step_chapters)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Distinct Module Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = LuxGoldAccent.copy(alpha = 0.18f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Translate, contentDescription = null, tint = LuxGoldBright)
                }
            }
            Column {
                Text(
                    text = "Traductor en el dispositivo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LuxGoldBright
                )
                Text(
                    text = "Detección automática y traducción neuronal offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = LuxSilverText
                )
            }
        }

        // Selector de texto de la Bóveda
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isRunning && texts.isNotEmpty()) { showTextMenu = true }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.translator_select_text), style = MaterialTheme.typography.labelSmall, color = LuxGoldAccent)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.selectedTextItem?.let { "${it.title} (${it.format} • ${it.wordCount} pal.)" }
                                ?: "No hay textos en Bóveda. Importa o descarga uno primero.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (texts.isNotEmpty()) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Elegir texto", tint = LuxGoldAccent)
                    }
                }

                DropdownMenu(
                    expanded = showTextMenu,
                    onDismissRequest = { showTextMenu = false }
                ) {
                    texts.forEach { textItem ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${textItem.title} (${textItem.format} • ${textItem.detectedLang.uppercase()})",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                translatorEngine.selectTextItem(textItem)
                                showTextMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Idioma origen → Idioma destino
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isRunning) { showSourceMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.translator_source_lang), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            Text(
                                text = translatorLanguages.firstOrNull { it.first == state.detectedSourceLang }?.second ?: state.detectedSourceLang.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LuxGoldBright
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LuxGoldAccent)
                    }
                }
                DropdownMenu(expanded = showSourceMenu, onDismissRequest = { showSourceMenu = false }) {
                    translatorLanguages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                translatorEngine.setSourceLang(code)
                                showSourceMenu = false
                            }
                        )
                    }
                }
            }

            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = LuxGoldAccent)

            Box(modifier = Modifier.weight(1f)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !state.isRunning) { showTargetMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.translator_target_lang), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            Text(
                                text = translatorLanguages.firstOrNull { it.first == state.targetLang }?.second ?: state.targetLang.uppercase(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = LuxGoldBright
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LuxGoldAccent)
                    }
                }
                DropdownMenu(expanded = showTargetMenu, onDismissRequest = { showTargetMenu = false }) {
                    translatorLanguages.forEach { (code, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                translatorEngine.setTargetLang(code)
                                showTargetMenu = false
                            }
                        )
                    }
                }
            }
        }

        // 5-Step Progress Pipeline
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.isRunning && !state.isPaused) {
                            LuxProcessingLottie(size = 24.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(stringResource(R.string.translator_steps_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                    }
                    if (state.isRunning || state.progress > 0f) {
                        Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = LuxGoldAccent, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(state.statusText, style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                Spacer(modifier = Modifier.height(8.dp))

                LuxAnimatedProgressBar(
                    progress = state.progress,
                    isError = state.errorMessage != null
                )

                Spacer(modifier = Modifier.height(10.dp))

                stepLabels.forEachIndexed { idx, label ->
                    val isDone = state.currentStep > idx || state.currentStep == 4 && state.progress >= 1f
                    val isCurrent = state.currentStep == idx && state.isRunning
                    Row(
                        modifier = Modifier.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Check,
                            contentDescription = null,
                            tint = when {
                                isDone -> LuxGoldBright
                                isCurrent -> LuxGoldAccent
                                else -> LuxSilverText.copy(alpha = 0.3f)
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isCurrent || isDone) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent || isDone) MaterialTheme.colorScheme.onSurface else LuxSilverText.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }

        // Error card
        if (state.errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Action Controls: Start / Pause / Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LuxGoldButton(
                enabled = state.selectedTextItem != null,
                onClick = { translatorEngine.startOrTogglePause() },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(48.dp).testTag("translator_start_btn")
            ) {
                Icon(
                    imageVector = if (state.isRunning && !state.isPaused) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        state.isRunning && state.isPaused -> "Reanudar"
                        state.isRunning -> stringResource(R.string.translator_btn_pause)
                        else -> stringResource(R.string.translator_btn_start)
                    },
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.isRunning) {
                OutlinedButton(
                    onClick = { translatorEngine.cancelTranslation() },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.translator_btn_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Completion CTA: Convert translated text to audio!
        state.completedTextItemId?.let { newTextId ->
            Card(
                colors = CardDefaults.cardColors(containerColor = LuxGoldAccent.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¡Traducción lista en tu Bóveda!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = LuxGoldBright
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onConvertTranslatedText(newTextId) },
                            colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Convertir a audio", fontSize = 12.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenVaultTexts,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ver en Bóveda", fontSize = 12.sp, color = LuxGoldBright)
                        }
                    }
                }
            }
        }

        // Real Text Preview Card
        if (state.previewOriginal.isNotBlank() || state.previewTranslated.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.translator_preview_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                    if (state.previewOriginal.isNotBlank()) {
                        Text("Original:", style = MaterialTheme.typography.labelSmall, color = LuxGoldAccent)
                        Text(state.previewOriginal, style = MaterialTheme.typography.bodySmall, color = LuxSilverText)
                    }
                    if (state.previewTranslated.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text("Traducido:", style = MaterialTheme.typography.labelSmall, color = LuxGoldBright)
                        Text(state.previewTranslated, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

package com.example.ui.converter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.datastore.LoquendoServerConfig
import com.example.data.datastore.SettingsStore
import com.example.data.local.entity.TextItem
import com.example.data.local.repository.TextItemRepository
import com.example.engine.TtsConverterEngine
import com.example.engine.TtsEngineMode
import com.example.engine.TtsVoiceOption
import com.example.ui.effects.LuxAnimatedProgressBar
import com.example.ui.effects.LuxGoldButton
import com.example.ui.effects.LuxProcessingLottie
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverText
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val audioLanguages = listOf(
    "es-ES" to "Español (España)",
    "es-MX" to "Español (México)",
    "es-US" to "Español (Latino)",
    "en-US" to "Inglés (EE.UU.)",
    "fr-FR" to "Francés",
    "pt-BR" to "Portugués (Brasil)",
    "de-DE" to "Alemán",
    "it-IT" to "Italiano"
)

@Composable
fun ConverterScreen(
    ttsEngine: TtsConverterEngine,
    textItemRepository: TextItemRepository,
    settingsStore: SettingsStore,
    initialTextId: String? = null,
    onOpenBookDetail: (String) -> Unit,
    onOpenVaultAudiobooks: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by ttsEngine.state.collectAsStateWithLifecycle()
    val texts by textItemRepository.allTextItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedLoquendoConfig by settingsStore.loquendoConfig.collectAsStateWithLifecycle(initialValue = LoquendoServerConfig())
    val snackbarHostState = remember { SnackbarHostState() }

    var engineMode by remember { mutableStateOf(TtsEngineMode.LOCAL_DEVICE) }
    var selectedText by remember { mutableStateOf<TextItem?>(null) }
    var selectedLangTag by remember { mutableStateOf("es-ES") }
    var selectedVoice by remember { mutableStateOf<TtsVoiceOption?>(null) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var pauseMs by remember { mutableIntStateOf(450) }

    // Loquendo server state
    var loquendoUrl by remember { mutableStateOf("") }
    var loquendoApiKey by remember { mutableStateOf("") }
    var loquendoMethod by remember { mutableStateOf("POST_JSON") }
    var loquendoVoice by remember { mutableStateOf("Jorge") }
    var loquendoCustomVoice by remember { mutableStateOf("") }
    var loquendoInitialized by remember { mutableStateOf(false) }

    var showTextMenu by remember { mutableStateOf(false) }
    var showLangMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showLoquendoVoiceMenu by remember { mutableStateOf(false) }

    LaunchedEffect(savedLoquendoConfig) {
        if (!loquendoInitialized && (savedLoquendoConfig.serverUrl.isNotBlank() || savedLoquendoConfig.voice != "Jorge")) {
            loquendoUrl = savedLoquendoConfig.serverUrl
            loquendoApiKey = savedLoquendoConfig.apiKey
            loquendoMethod = savedLoquendoConfig.method
            val isPreset = TtsConverterEngine.LOQUENDO_PRESETS.any { it.id == savedLoquendoConfig.voice }
            if (isPreset) {
                loquendoVoice = savedLoquendoConfig.voice
            } else {
                loquendoVoice = "CUSTOM"
                loquendoCustomVoice = savedLoquendoConfig.voice
            }
            loquendoInitialized = true
        }
    }

    LaunchedEffect(initialTextId, texts) {
        if (texts.isNotEmpty()) {
            val requested = initialTextId?.let { id -> texts.firstOrNull { it.id == id } }
            if (requested != null) {
                selectedText = requested
                selectedLangTag = mapSimpleLangToTag(requested.detectedLang)
            } else if (selectedText == null) {
                selectedText = texts.first()
                selectedLangTag = mapSimpleLangToTag(texts.first().detectedLang)
            }
        }
    }

    val matchingVoices = remember(state.availableVoices, selectedLangTag) {
        val langPrefix = selectedLangTag.substringBefore("-").lowercase()
        val filtered = state.availableVoices.filter { it.languageTag.lowercase().startsWith(langPrefix) }
        filtered.ifEmpty { state.availableVoices }
    }

    LaunchedEffect(matchingVoices, selectedLangTag) {
        if (selectedVoice == null || matchingVoices.none { it.id == selectedVoice?.id }) {
            selectedVoice = matchingVoices.firstOrNull()
        }
    }

    LaunchedEffect(state.lastMessage) {
        state.lastMessage?.let {
            snackbarHostState.showSnackbar(it)
            ttsEngine.clearMessage()
        }
    }

    val activeLoquendoVoiceName = if (loquendoVoice == "CUSTOM") {
        loquendoCustomVoice.trim().ifBlank { "Jorge" }
    } else loquendoVoice

    val wordCount = selectedText?.wordCount?.coerceAtLeast(1) ?: 0
    val estMinutes = if (wordCount > 0) ((wordCount / (155f * speed)).roundToInt()).coerceAtLeast(1) else 0
    val estSizeMb = (estMinutes * 2.6f).let { (it * 10).roundToInt() / 10f }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
                        Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = LuxGoldBright)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Convertidor Texto → Audio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LuxGoldBright
                    )
                    Text(
                        text = if (engineMode == TtsEngineMode.LOQUENDO_SERVER) {
                            "Síntesis remota Loquendo vía servidor HTTP"
                        } else if (state.ttsInitialized) {
                            "Motor del teléfono listo (${state.availableVoices.size} voces)"
                        } else {
                            state.ttsError ?: "Inicializando motor TTS del sistema…"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (engineMode == TtsEngineMode.LOCAL_DEVICE && state.ttsError != null) MaterialTheme.colorScheme.error else LuxSilverText
                    )
                }
            }

            // Engine Mode Selector: Local vs Loquendo Server
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = engineMode == TtsEngineMode.LOCAL_DEVICE,
                    onClick = { engineMode = TtsEngineMode.LOCAL_DEVICE },
                    label = { Text(stringResource(R.string.converter_mode_local), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                        selectedLabelColor = LuxGoldBright
                    ),
                    modifier = Modifier.weight(1f).testTag("converter_mode_local")
                )

                FilterChip(
                    selected = engineMode == TtsEngineMode.LOQUENDO_SERVER,
                    onClick = { engineMode = TtsEngineMode.LOQUENDO_SERVER },
                    label = { Text(stringResource(R.string.converter_mode_loquendo), fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                        selectedLabelColor = LuxGoldBright
                    ),
                    modifier = Modifier.weight(1f).testTag("converter_mode_loquendo")
                )
            }

            // Text selector card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = texts.isNotEmpty()) { showTextMenu = true }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.converter_select_text), style = MaterialTheme.typography.labelSmall, color = LuxGoldAccent)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = selectedText?.let { "${it.title} (${it.format} • ${it.wordCount} palabras)" }
                                    ?: "No tienes textos en la Bóveda. Importa o descarga uno primero.",
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

                    DropdownMenu(expanded = showTextMenu, onDismissRequest = { showTextMenu = false }) {
                        texts.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.title} (${item.format})", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = {
                                    selectedText = item
                                    selectedLangTag = mapSimpleLangToTag(item.detectedLang)
                                    showTextMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Configuration Card: Loquendo Server OR Local Device TTS
            AnimatedVisibility(visible = engineMode == TtsEngineMode.LOQUENDO_SERVER) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.converter_loquendo_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = LuxGoldBright
                        )

                        OutlinedTextField(
                            value = loquendoUrl,
                            onValueChange = { loquendoUrl = it },
                            label = { Text(stringResource(R.string.converter_loquendo_url_label)) },
                            placeholder = { Text(stringResource(R.string.converter_loquendo_url_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().testTag("loquendo_server_url_input")
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = loquendoMethod == "POST_JSON",
                                onClick = { loquendoMethod = "POST_JSON" },
                                label = { Text("POST JSON", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                )
                            )
                            FilterChip(
                                selected = loquendoMethod == "GET_QUERY",
                                onClick = { loquendoMethod = "GET_QUERY" },
                                label = { Text("GET URL", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                )
                            )
                        }

                        OutlinedTextField(
                            value = loquendoApiKey,
                            onValueChange = { loquendoApiKey = it },
                            label = { Text(stringResource(R.string.converter_loquendo_token_label)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Loquendo Voice Dropdown
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLoquendoVoiceMenu = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(stringResource(R.string.converter_loquendo_voice_label), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                                    Text(
                                        text = if (loquendoVoice == "CUSTOM") {
                                            "Personalizada: $activeLoquendoVoiceName"
                                        } else {
                                            TtsConverterEngine.LOQUENDO_PRESETS.firstOrNull { it.id == loquendoVoice }?.displayName ?: loquendoVoice
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LuxGoldBright
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LuxGoldAccent)
                            }
                            DropdownMenu(expanded = showLoquendoVoiceMenu, onDismissRequest = { showLoquendoVoiceMenu = false }) {
                                TtsConverterEngine.LOQUENDO_PRESETS.forEach { preset ->
                                    DropdownMenuItem(
                                        text = { Text(preset.displayName) },
                                        onClick = {
                                            loquendoVoice = preset.id
                                            selectedLangTag = preset.languageTag
                                            showLoquendoVoiceMenu = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Personalizada (escribir nombre)…") },
                                    onClick = {
                                        loquendoVoice = "CUSTOM"
                                        showLoquendoVoiceMenu = false
                                    }
                                )
                            }
                        }

                        if (loquendoVoice == "CUSTOM") {
                            OutlinedTextField(
                                value = loquendoCustomVoice,
                                onValueChange = { loquendoCustomVoice = it },
                                label = { Text("Nombre exacto de la voz en el servidor") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        OutlinedButton(
                            enabled = !state.isTestingServer && loquendoUrl.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    settingsStore.setLoquendoConfig(
                                        serverUrl = loquendoUrl,
                                        apiKey = loquendoApiKey,
                                        method = loquendoMethod,
                                        voice = activeLoquendoVoiceName
                                    )
                                }
                                ttsEngine.previewLoquendoServer(
                                    serverUrl = loquendoUrl,
                                    apiKey = loquendoApiKey,
                                    method = loquendoMethod,
                                    voiceName = activeLoquendoVoiceName,
                                    languageTag = selectedLangTag,
                                    speed = speed,
                                    pitch = pitch
                                )
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("loquendo_test_btn")
                        ) {
                            if (state.isTestingServer) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LuxGoldBright)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Probando servidor…", fontSize = 12.sp, color = LuxGoldBright)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldBright)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.converter_loquendo_test_btn), fontSize = 12.sp, color = LuxGoldBright)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = engineMode == TtsEngineMode.LOCAL_DEVICE) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLangMenu = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(stringResource(R.string.converter_audio_lang), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                                    Text(
                                        text = audioLanguages.firstOrNull { it.first == selectedLangTag }?.second ?: selectedLangTag,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = LuxGoldBright
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LuxGoldAccent)
                            }
                            DropdownMenu(expanded = showLangMenu, onDismissRequest = { showLangMenu = false }) {
                                audioLanguages.forEach { (tag, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            selectedLangTag = tag
                                            showLangMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = matchingVoices.isNotEmpty()) { showVoiceMenu = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(stringResource(R.string.converter_select_voice), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                                    Text(
                                        text = selectedVoice?.displayName ?: "Voz predeterminada del sistema",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = LuxGoldAccent)
                            }
                            DropdownMenu(expanded = showVoiceMenu, onDismissRequest = { showVoiceMenu = false }) {
                                matchingVoices.take(20).forEach { voice ->
                                    DropdownMenuItem(
                                        text = {
                                            Text("${voice.displayName} • ${voice.localeDisplay}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        },
                                        onClick = {
                                            selectedVoice = voice
                                            showVoiceMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                enabled = state.ttsInitialized,
                                onClick = {
                                    ttsEngine.previewVoice(
                                        voiceId = selectedVoice?.id,
                                        languageTag = selectedLangTag,
                                        speed = speed,
                                        pitch = pitch
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldBright)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Probar voz", fontSize = 12.sp, color = LuxGoldBright)
                            }

                            OutlinedButton(
                                onClick = { ttsEngine.openInstallTtsVoices(context) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = LuxGoldAccent)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Más voces", fontSize = 12.sp, color = LuxGoldAccent)
                            }
                        }
                    }
                }
            }

            // Synthesis Parameters Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.converter_speed), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("${(speed * 100).roundToInt() / 100f}x", style = MaterialTheme.typography.bodySmall, color = LuxGoldBright, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = speed,
                        onValueChange = { speed = it },
                        valueRange = 0.6f..2.0f,
                        colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.converter_pitch), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("${(pitch * 100).roundToInt() / 100f}", style = MaterialTheme.typography.bodySmall, color = LuxGoldBright, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent)
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.converter_pause_paragraph), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("$pauseMs ms", style = MaterialTheme.typography.bodySmall, color = LuxGoldBright, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = pauseMs.toFloat(),
                        onValueChange = { pauseMs = it.toInt() },
                        valueRange = 150f..1200f,
                        colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent)
                    )
                }
            }

            // Summary Card
            if (selectedText != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(stringResource(R.string.converter_est_duration), style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            Text("~${estMinutes} min", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Palabras", style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            Text("$wordCount", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Tamaño est.", style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
                            Text("~$estSizeMb MB", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                        }
                    }
                }
            }

            val canStart = selectedText != null && if (engineMode == TtsEngineMode.LOCAL_DEVICE) {
                state.ttsInitialized
            } else {
                loquendoUrl.isNotBlank()
            }

            // Start Conversion Button (2.5D metallic gold CTA)
            LuxGoldButton(
                enabled = canStart,
                onClick = {
                    val item = selectedText ?: return@LuxGoldButton
                    if (engineMode == TtsEngineMode.LOQUENDO_SERVER) {
                        scope.launch {
                            settingsStore.setLoquendoConfig(
                                serverUrl = loquendoUrl,
                                apiKey = loquendoApiKey,
                                method = loquendoMethod,
                                voice = activeLoquendoVoiceName
                            )
                        }
                    }
                    ttsEngine.enqueueConversion(
                        textItem = item,
                        languageTag = selectedLangTag,
                        voiceId = if (engineMode == TtsEngineMode.LOQUENDO_SERVER) activeLoquendoVoiceName else selectedVoice?.id,
                        speed = speed,
                        pitch = pitch,
                        paragraphPauseMs = pauseMs,
                        engineMode = engineMode,
                        loquendoUrl = loquendoUrl,
                        loquendoApiKey = loquendoApiKey,
                        loquendoMethod = loquendoMethod
                    )
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("converter_start_btn")
            ) {
                Icon(
                    imageVector = if (engineMode == TtsEngineMode.LOQUENDO_SERVER) Icons.Default.CloudQueue else Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (engineMode == TtsEngineMode.LOQUENDO_SERVER) {
                        stringResource(R.string.converter_loquendo_btn_start)
                    } else {
                        stringResource(R.string.converter_btn_start)
                    },
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold
                )
            }

            // Last Completed Audiobook Banner
            state.lastCompletedBookId?.let { bookId ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = LuxGoldAccent.copy(alpha = 0.16f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "¡Audiolibro generado y guardado en tu Bóveda!",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = LuxGoldBright
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { onOpenBookDetail(bookId) },
                                colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Headphones, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.surface)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Abrir audiolibro", fontSize = 12.sp, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onOpenVaultAudiobooks,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Ir a Bóveda", fontSize = 12.sp, color = LuxGoldBright)
                            }
                        }
                    }
                }
            }

            // Real Active & Pending Queue
            if (state.queue.isNotEmpty()) {
                val activeCount = state.queue.count { it.isActive }
                val waitingCount = (state.queue.size - activeCount).coerceAtLeast(0)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.converter_queue_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                    Text("$activeCount activo • $waitingCount en espera", style = MaterialTheme.typography.labelSmall, color = LuxGoldAccent)
                }

                state.queue.forEachIndexed { idx, job ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (job.isActive && !job.isPaused) {
                                    LuxProcessingLottie(size = 26.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = job.textItem.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (job.error != null) "Error: ${job.error}" else "${job.statusText} • ${job.timeRemainingText}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (job.error != null) MaterialTheme.colorScheme.error else LuxSilverText
                                    )
                                }

                                if (job.isActive) {
                                    IconButton(onClick = { ttsEngine.togglePauseActive() }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            imageVector = if (job.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                            contentDescription = "Pausar o reanudar",
                                            tint = LuxGoldAccent
                                        )
                                    }
                                } else if (job.isPaused || job.error != null) {
                                    IconButton(onClick = { ttsEngine.resumeJob(job.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Reanudar conversión",
                                            tint = LuxGoldBright
                                        )
                                    }
                                } else {
                                    IconButton(
                                        enabled = idx > 1,
                                        onClick = { ttsEngine.moveQueueItem(job.id, -1) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Subir", modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        enabled = idx < state.queue.lastIndex,
                                        onClick = { ttsEngine.moveQueueItem(job.id, 1) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar", modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(onClick = { ttsEngine.cancelJob(job.id) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancelar", tint = MaterialTheme.colorScheme.error)
                                }
                            }

                            if (job.isActive || job.isPaused || job.progress > 0f) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LuxAnimatedProgressBar(
                                    progress = job.progress,
                                    isError = job.error != null
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

private fun mapSimpleLangToTag(code: String): String {
    return when (code.lowercase()) {
        "en" -> "en-US"
        "fr" -> "fr-FR"
        "de" -> "de-DE"
        "it" -> "it-IT"
        "pt" -> "pt-BR"
        else -> "es-ES"
    }
}

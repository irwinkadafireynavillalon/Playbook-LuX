package com.example.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.R
import com.example.data.datastore.SettingsStore
import com.example.playback.AudioEffectsManager
import com.example.ui.components.ListeningStatsDialog
import com.example.ui.effects.LocalLuxReduceEffects
import com.example.ui.placeholders.PhaseBadge
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.LuxGoldAccent
import com.example.ui.theme.LuxGoldBright
import com.example.ui.theme.LuxSilverBright
import com.example.ui.theme.LuxSilverText
import kotlin.math.abs
import kotlin.math.roundToInt

private val EQ_BAND_LABELS = listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecycle: () -> Unit,
    onNavigateToIntroduction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val userReduceEffects by viewModel.reduceEffects.collectAsStateWithLifecycle()
    val effectiveReduceEffects = LocalLuxReduceEffects.current
    val playerSettings by viewModel.playerSettings.collectAsStateWithLifecycle()
    val recycledBooks by viewModel.recycledBooks.collectAsStateWithLifecycle()
    val recentlyPlayedBooks by viewModel.recentlyPlayedBooks.collectAsStateWithLifecycle()
    val totalListenedSeconds by viewModel.totalListenedSeconds.collectAsStateWithLifecycle()
    val recentListeningStats by viewModel.recentListeningStats.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val isCreatingTestBook by viewModel.isCreatingTestBook.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showCleanCacheConfirm by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.updatePlayerSettings { it.copy(resumeOnBluetooth = true) }
        }
    }

    val isIgnoringBatteryOpt = remember(context) {
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    val isLoudnessEnhancerSupported = remember {
        AudioEffectsManager.isLoudnessEnhancerSupported()
    }
    val isBassBoostSupported = remember {
        AudioEffectsManager.isBassBoostSupported()
    }
    val isVirtualizerSupported = remember {
        AudioEffectsManager.isVirtualizerSupported()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // SECCIÓN 1: APARIENCIA / TEMA (T1 + Fase 2 Reducir efectos)
            SectionHeader(stringResource(R.string.settings_section_appearance))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ThemeRadioItem(
                        label = stringResource(R.string.theme_dark),
                        selected = themeMode == AppThemeMode.DARK,
                        onSelect = { viewModel.setThemeMode(AppThemeMode.DARK) },
                        testTag = "theme_dark_opt"
                    )
                    ThemeRadioItem(
                        label = stringResource(R.string.theme_light),
                        selected = themeMode == AppThemeMode.LIGHT,
                        onSelect = { viewModel.setThemeMode(AppThemeMode.LIGHT) },
                        testTag = "theme_light_opt"
                    )
                    ThemeRadioItem(
                        label = stringResource(R.string.theme_system),
                        selected = themeMode == AppThemeMode.SYSTEM,
                        onSelect = { viewModel.setThemeMode(AppThemeMode.SYSTEM) },
                        testTag = "theme_system_opt"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingsSwitchRow(
                        title = "Reducir efectos (2.5D y shaders)",
                        subtitle = if (effectiveReduceEffects && !userReduceEffects) {
                            "Activo automáticamente por ahorro de batería o movimiento del sistema"
                        } else {
                            "Desactiva inclinación 2.5D, brillos metálicos y animaciones intensas"
                        },
                        checked = userReduceEffects || effectiveReduceEffects,
                        onCheckedChange = { checked ->
                            viewModel.setReduceEffects(checked)
                        },
                        testTag = "settings_reduce_effects_switch"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 2: REPRODUCTOR Y ECUALIZADOR (REAL Y ACTIVO)
            SectionHeader("Reproductor y Ecualizador")
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Velocidad predeterminada (0.5x - 3.0x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Velocidad predeterminada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = "${((playerSettings.defaultSpeed * 20).roundToInt() / 20f)}x",
                            color = LuxGoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = playerSettings.defaultSpeed,
                        onValueChange = { raw ->
                            val snapped = ((raw * 20).roundToInt() / 20f).coerceIn(0.5f, 3.0f)
                            viewModel.updatePlayerSettings { it.copy(defaultSpeed = snapped) }
                        },
                        valueRange = 0.5f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent),
                        modifier = Modifier.testTag("settings_default_speed_slider")
                    )

                    // Intervalo de salto (10 / 15 / 30 s)
                    Text("Salto al retroceder / adelantar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30).forEach { secs ->
                            FilterChip(
                                selected = playerSettings.jumpSeconds == secs,
                                onClick = {
                                    viewModel.updatePlayerSettings { it.copy(jumpSeconds = secs) }
                                },
                                label = { Text("${secs}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                ),
                                modifier = Modifier.testTag("settings_jump_${secs}_chip")
                            )
                        }
                    }

                    // Retroceso automático al reanudar (0 - 5 s)
                    Text("Retroceder al reanudar tras pausa", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 2, 3, 5).forEach { secs ->
                            FilterChip(
                                selected = playerSettings.autoRewindSeconds == secs,
                                onClick = {
                                    viewModel.updatePlayerSettings { it.copy(autoRewindSeconds = secs) }
                                },
                                label = { Text(if (secs == 0) "No" else "${secs}s") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSwitchRow(
                        title = "Saltar silencios",
                        subtitle = "Acelera o omite pausas largas sin voz durante la narración",
                        checked = playerSettings.skipSilence,
                        onCheckedChange = { enabled ->
                            viewModel.updatePlayerSettings { it.copy(skipSilence = enabled) }
                        },
                        testTag = "settings_skip_silence_switch"
                    )

                    SettingsSwitchRow(
                        title = "Normalizar volumen de voz",
                        subtitle = if (isLoudnessEnhancerSupported) {
                            "Nivela capítulos a -16 LUFS y refuerza la reproducción con LoudnessEnhancer"
                        } else {
                            "Nivela capítulos a -16 LUFS al convertir"
                        },
                        checked = playerSettings.normalizeVolume,
                        onCheckedChange = { enabled ->
                            viewModel.updatePlayerSettings { it.copy(normalizeVolume = enabled) }
                        },
                        testTag = "settings_normalize_switch"
                    )

                    if (isLoudnessEnhancerSupported) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 8.dp, end = 4.dp, bottom = 4.dp)
                                .testTag("settings_normalize_gain_container")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Ganancia de refuerzo (LoudnessEnhancer)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (playerSettings.normalizeVolume) LuxSilverText else LuxSilverText.copy(alpha = 0.55f)
                                )
                                Text(
                                    text = "+${"%.1f".format(playerSettings.normalizeGainDb)} dB",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (playerSettings.normalizeVolume) LuxGoldBright else LuxSilverText.copy(alpha = 0.55f)
                                )
                            }
                            Slider(
                                value = playerSettings.normalizeGainDb,
                                onValueChange = { raw ->
                                    val snapped = ((raw * 10f).roundToInt() / 10f).coerceIn(0f, 6f)
                                    viewModel.updatePlayerSettings { it.copy(normalizeGainDb = snapped) }
                                },
                                enabled = playerSettings.normalizeVolume,
                                valueRange = 0f..6f,
                                colors = SliderDefaults.colors(
                                    thumbColor = LuxGoldBright,
                                    activeTrackColor = LuxGoldAccent
                                ),
                                modifier = Modifier.testTag("settings_normalize_gain_slider")
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Continuar al siguiente capítulo",
                        subtitle = "Avanza automáticamente al terminar cada capítulo",
                        checked = playerSettings.autoNextChapter,
                        onCheckedChange = { enabled ->
                            viewModel.updatePlayerSettings { it.copy(autoNextChapter = enabled) }
                        },
                        testTag = "settings_auto_next_switch"
                    )

                    SettingsSwitchRow(
                        title = "Reanudar al conectar Bluetooth",
                        subtitle = "Reanuda el audiolibro cargado al conectar auriculares Bluetooth",
                        leadingIcon = Icons.Default.BluetoothAudio,
                        checked = playerSettings.resumeOnBluetooth,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                            ) {
                                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            } else {
                                viewModel.updatePlayerSettings { it.copy(resumeOnBluetooth = enabled) }
                            }
                        },
                        testTag = "settings_bluetooth_resume_switch"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Ecualizador 5 bandas + presets
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Equalizer, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ecualizador de audio (5 bandas)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = LuxGoldBright)
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsStore.EQ_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = playerSettings.eqPreset == preset.id,
                                onClick = { viewModel.applyEqPreset(preset.id) },
                                label = { Text(preset.name, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                ),
                                modifier = Modifier.testTag("settings_eq_preset_${preset.id}")
                            )
                        }
                    }

                    EQ_BAND_LABELS.forEachIndexed { idx, freqLabel ->
                        val bandDb = (playerSettings.eqBands.getOrElse(idx) { 0 }) / 100f
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = freqLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = LuxSilverText,
                                modifier = Modifier.width(52.dp)
                            )
                            Slider(
                                value = playerSettings.eqBands.getOrElse(idx) { 0 }.toFloat(),
                                onValueChange = { viewModel.updateEqBand(idx, it.roundToInt()) },
                                valueRange = -1200f..1200f,
                                colors = SliderDefaults.colors(thumbColor = LuxGoldAccent, activeTrackColor = LuxGoldAccent),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${if (bandDb >= 0f) "+" else ""}${"%.1f".format(bandDb)} dB",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuxGoldBright,
                                modifier = Modifier.width(54.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Procesamiento avanzado estilo reproductor de música (Mono, Balance L/R, Fundidos, Graves y Virtualizer)
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_mono_audio_title),
                        subtitle = stringResource(R.string.settings_mono_audio_desc),
                        leadingIcon = Icons.Default.GraphicEq,
                        checked = playerSettings.monoAudio,
                        onCheckedChange = { enabled ->
                            viewModel.updatePlayerSettings { it.copy(monoAudio = enabled) }
                        },
                        testTag = "settings_mono_audio_switch"
                    )

                    // Stereo Balance L/R (-1..+1)
                    val pan = playerSettings.balancePan
                    val panLabel = when {
                        abs(pan) < 0.04f -> "Centro"
                        pan < 0f -> "Izq ${(-pan * 100).roundToInt()}%"
                        else -> "Der ${(pan * 100).roundToInt()}%"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.settings_balance_pan), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        }
                        Text(
                            text = panLabel,
                            color = LuxGoldBright,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    viewModel.updatePlayerSettings { it.copy(balancePan = 0f) }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Slider(
                        value = pan,
                        onValueChange = { raw ->
                            val snapped = if (abs(raw) < 0.06f) 0f else ((raw * 20f).roundToInt() / 20f).coerceIn(-1f, 1f)
                            viewModel.updatePlayerSettings { it.copy(balancePan = snapped) }
                        },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent),
                        modifier = Modifier.testTag("settings_balance_pan_slider")
                    )

                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_fade_pause_title),
                        subtitle = stringResource(R.string.settings_fade_pause_desc),
                        checked = playerSettings.fadeOnPauseResume,
                        onCheckedChange = { enabled ->
                            viewModel.updatePlayerSettings { it.copy(fadeOnPauseResume = enabled) }
                        },
                        testTag = "settings_fade_pause_switch"
                    )

                    Text(
                        text = stringResource(R.string.settings_crossfade_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 500, 1000, 2000, 3000).forEach { ms ->
                            val chipLabel = when (ms) {
                                0 -> "No"
                                500 -> "0.5s"
                                else -> "${ms / 1000}s"
                            }
                            FilterChip(
                                selected = playerSettings.crossfadeMs == ms,
                                onClick = {
                                    viewModel.updatePlayerSettings { it.copy(crossfadeMs = ms) }
                                },
                                label = { Text(chipLabel) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LuxGoldAccent.copy(alpha = 0.25f),
                                    selectedLabelColor = LuxGoldBright
                                ),
                                modifier = Modifier.testTag("settings_crossfade_${ms}_chip")
                            )
                        }
                    }

                    if (isBassBoostSupported) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_bass_boost),
                                style = MaterialTheme.typography.bodySmall,
                                color = LuxSilverText
                            )
                            Text(
                                text = "${(playerSettings.bassBoostStrength / 10)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = LuxGoldBright
                            )
                        }
                        Slider(
                            value = playerSettings.bassBoostStrength.toFloat(),
                            onValueChange = { raw ->
                                viewModel.updatePlayerSettings {
                                    it.copy(bassBoostStrength = raw.roundToInt().coerceIn(0, 1000))
                                }
                            },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent),
                            modifier = Modifier.testTag("settings_bass_boost_slider")
                        )
                    }

                    if (isVirtualizerSupported) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SurroundSound, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.settings_virtualizer),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LuxSilverText
                                )
                            }
                            Text(
                                text = "${(playerSettings.virtualizerStrength / 10)}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = LuxGoldBright
                            )
                        }
                        Slider(
                            value = playerSettings.virtualizerStrength.toFloat(),
                            onValueChange = { raw ->
                                viewModel.updatePlayerSettings {
                                    it.copy(virtualizerStrength = raw.roundToInt().coerceIn(0, 1000))
                                }
                            },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(thumbColor = LuxGoldBright, activeTrackColor = LuxGoldAccent),
                            modifier = Modifier.testTag("settings_virtualizer_slider")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 3: HERRAMIENTAS (Almacenamiento, Reciclaje, Crear libro de prueba debug)
            SectionHeader(stringResource(R.string.settings_section_tools))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Historial y estadísticas de escucha
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStatsDialog = true }
                            .padding(14.dp)
                            .testTag("settings_stats_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.stats_dialog_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            val totalMins = totalListenedSeconds / 60L
                            Text(
                                text = if (totalMins > 0L) "$totalMins min acumulados • ${recentlyPlayedBooks.size} recientes"
                                else stringResource(R.string.settings_stats_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = LuxSilverText
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = LuxSilverText, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Papelera de reciclaje (R1)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToRecycle)
                            .padding(14.dp)
                            .testTag("settings_recycle_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.recycle_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (recycledBooks.isEmpty()) stringResource(R.string.recycle_empty)
                                else "${recycledBooks.size} audiolibros en papelera",
                                style = MaterialTheme.typography.bodySmall,
                                color = LuxSilverText
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = LuxSilverText, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Limpieza de memoria caché
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCleanCacheConfirm = true }
                            .padding(14.dp)
                            .testTag("settings_cache_clean_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_tools_cache_clean), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.settings_tools_cache_clean_desc), style = MaterialTheme.typography.bodySmall, color = LuxSilverText)
                        }
                    }

                    // Botón "Crear libro de prueba" (B9) - SOLO EN BUILD DEBUG
                    if (BuildConfig.DEBUG) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCreatingTestBook) {
                                        viewModel.createTestBook(context)
                                    }
                                }
                                .padding(14.dp)
                                .testTag("btn_create_test_book"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = LuxGoldBright)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_tools_test_book_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = LuxGoldBright
                                )
                                Text(
                                    text = if (isCreatingTestBook) "Generando audio Opus y portada…" else stringResource(R.string.settings_tools_test_book_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = LuxSilverText
                                )
                            }
                            if (isCreatingTestBook) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = LuxGoldAccent)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null, tint = LuxGoldAccent)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 4: MOTORES Y PROCESAMIENTO
            SectionHeader(stringResource(R.string.settings_section_engines))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DisabledSettingsRow(
                        icon = Icons.Default.RecordVoiceOver,
                        title = stringResource(R.string.settings_tts_engine),
                        subtitle = "Motor del teléfono (Opus/OGG) + Loquendo vía servidor (Activo en Convertidor)",
                        badge = "Activo"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DisabledSettingsRow(
                        icon = Icons.Default.Translate,
                        title = "Traductor sin conexión",
                        subtitle = "Modelos locales Google ML Kit (Activo en Traductor)",
                        badge = "Activo"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DisabledSettingsRow(
                        icon = Icons.Default.SdStorage,
                        title = "Catálogo de dominio público",
                        subtitle = "Gutenberg, Internet Archive y Wikisource (Activo en Buscar)",
                        badge = "Activo"
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 5: SISTEMA Y PRIVACIDAD
            SectionHeader(stringResource(R.string.settings_section_system))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // Notificaciones y permisos (activo e interactivo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    runCatching { context.startActivity(intent) }
                                }
                            }
                            .padding(14.dp)
                            .testTag("settings_notifications_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.settings_notifications_permissions), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Canales de audio, descargas, traducción y conversión", style = MaterialTheme.typography.bodySmall, color = LuxSilverText)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = LuxSilverText, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Optimización de batería (activo e interactivo)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                openBatteryOptimizationSettings(context)
                            }
                            .padding(14.dp)
                            .testTag("settings_battery_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Segundo plano y batería", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                text = if (isIgnoringBatteryOpt) "Sin restricciones de batería activo" else "Permitir ejecución sin cortes al apagar pantalla",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isIgnoringBatteryOpt) LuxGoldBright else LuxSilverText
                            )
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = LuxSilverText, modifier = Modifier.size(14.dp))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    DisabledSettingsRow(
                        icon = Icons.Default.Wifi,
                        title = stringResource(R.string.settings_connectivity),
                        subtitle = "Interruptor Solo Wi-Fi disponible en la pestaña Buscar",
                        badge = "Activo"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DisabledSettingsRow(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.settings_language),
                        subtitle = "Español (predeterminado)"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DisabledSettingsRow(
                        icon = Icons.Default.Fingerprint,
                        title = stringResource(R.string.settings_security),
                        subtitle = "Protección biométrica de tu Bóveda",
                        badge = stringResource(R.string.coming_soon)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECCIÓN 6: ACERCA DE PLAY LUX Y INTRODUCCIÓN
            SectionHeader(stringResource(R.string.settings_about_title))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = LuxGoldAccent)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Play LuX v1.0", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = LuxSilverBright)
                            Text(stringResource(R.string.settings_about_desc), style = MaterialTheme.typography.bodySmall, color = LuxSilverText)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onNavigateToIntroduction,
                        modifier = Modifier.fillMaxWidth().testTag("view_intro_btn"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(stringResource(R.string.settings_about_intro_btn), color = LuxGoldAccent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Dialog: Clean cache confirmation
    if (showCleanCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanCacheConfirm = false },
            title = { Text(stringResource(R.string.settings_tools_cache_clean)) },
            text = { Text(stringResource(R.string.settings_tools_cache_clean_confirm)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearCache(context)
                        showCleanCacheConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LuxGoldAccent)
                ) {
                    Text(stringResource(R.string.ok), color = MaterialTheme.colorScheme.surface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanCacheConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showStatsDialog) {
        ListeningStatsDialog(
            totalListenedSeconds = totalListenedSeconds,
            recentStats = recentListeningStats,
            recentBooks = recentlyPlayedBooks,
            onDismiss = { showStatsDialog = false }
        )
    }
}

fun openBatteryOptimizationSettings(context: Context) {
    val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    val opened = runCatching { context.startActivity(requestIntent) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    leadingIcon: ImageVector? = null,
    testTag: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .let { mod -> if (testTag != null) mod.testTag(testTag) else mod },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = LuxGoldAccent, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = LuxSilverText)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = LuxGoldAccent
            )
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = LuxGoldAccent,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ThemeRadioItem(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(selectedColor = LuxGoldAccent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) LuxGoldBright else LuxSilverText
        )
    }
}

@Composable
private fun DisabledSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = LuxSilverText.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LuxSilverText.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    PhaseBadge(badge)
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = LuxSilverText.copy(alpha = 0.5f)
            )
        }
    }
}


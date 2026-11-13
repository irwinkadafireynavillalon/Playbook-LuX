# ARCHITECTURE - Play LuX

## 1. Visión General de la Arquitectura
Play LuX está construido con una arquitectura nativa, limpia y modular en Android, siguiendo los principios de **Clean Architecture** y **MVVM** (Model-View-ViewModel), utilizando 100% Kotlin y Jetpack Compose. La aplicación se ejecuta de forma completamente local en el dispositivo (sandbox privada en `filesDir/` y `cacheDir/`), sin cuentas de usuario, sin Firebase, sin Google Sign-In ni llamadas a APIs en la nube.

```
┌───────────────────────────────────────────────────────────┐
│                    Capa de Presentación                   │
│   Jetpack Compose + Material 3 + Navigation Compose       │
│                                                           │
│   TopBar (56 dp + statusBarsPadding): [Logo] + 5 Módulos +⋮│
│   (Bóveda: Audiolibros / Textos, Listas, Buscar/Descargas,│
│    Traductor, Convertidor)                                │
│   MiniPlayer (56 dp + navigationBarsPadding, progreso 2dp)│
│   BookDetailScreen / PlayerScreen / SettingsScreen        │
│   RecycleScreen / OnboardingScreen (HorizontalPager 4p)   │
└────────────┬─────────────────────────────┬────────────────┘
             │                             │
             ▼                             ▼
┌──────────────────────────┐  ┌─────────────────────────────┐
│  ViewModels y Motores    │  │     Capa de Reproducción    │
│  Vault, Player, Settings │  │  Media3 PlaybackService     │
│  TtsConverterEngine (TTS)│◄─┤  (MediaSessionService +     │
│  LocalTranslatorEngine   │  │   ExoPlayer + AudioFocus)   │
│  PublicBookSearchEngine  │  └─────────────────────────────┘
└────────────┬─────────────┘
             │
             ▼
┌───────────────────────────────────────────────────────────┐
│                    Capa de Dominio y Datos                │
│  Inyección Manual: AppContainer en PlayLuxApplication      │
│  Extracción de Archivos SAF/ZIP: TextFileExtractor        │
│  Repositorios: BookRepository, RecycleRepository,         │
│                FolderRepository, TextItemRepository,      │
│                PlaylistRepository                         │
│  Persistencia Local: Room Database (playlux.db, v2) + KSP │
│    - Tablas: books, parts, chapters, bookmarks, folders,  │
│              recycle_entries, text_items, playlists,      │
│              playlist_items                               │
│  Preferencias: DataStore Preferences (SettingsStore)      │
│  Trabajos en segundo plano: WorkManager                   │
│                             (RecycleCleanupWorker)        │
│  Almacenamiento Sandbox: filesDir/books/ y filesDir/texts/│
└───────────────────────────────────────────────────────────┘
```

---

## 2. Componentes Clave

### A. Capa de Presentación
- **MainActivity**: Actividad única con integración de `SplashScreen` API (`installSplashScreen()`), `enableEdgeToEdge()`, lectura reactiva de `themeMode`, `reduceEffects` (`LocalLuxReduceEffects` respetando ahorro de batería y `ANIMATOR_DURATION_SCALE`), `onboardingCompleted` y `LuxSplashOverlay` (`<= 1.5s`).
- **Navegación**: Gestionada por `NavHost` con rutas seguras en `Screen`:
  - Fila superior unificada de 56 dp: Logo Play LuX animado 2.5D (24 dp) a la izquierda, 5 módulos principales (`Screen.Vault`, `Screen.Playlists`, `Screen.Search`, `Screen.Translator`, `Screen.Converter`) con escala de profundidad en el módulo activo, más el botón `⋮` para acceder a Ajustes. Sin barra de navegación inferior.
  - Pantallas secundarias a pantalla completa con barra superior propia: Detalle de audiolibro (`Screen.BookDetail/{bookId}`), Reproductor a pantalla completa (`Screen.Player` con lista de capítulos y tiempo restante), Ajustes (`Screen.Settings`), Reciclaje (`Screen.Recycle`), Onboarding (`Screen.Onboarding`).
- **Capa Visual 2.5D (`ui/effects/Lux25DEffects.kt`)**: Portadas con inclinación 2.5D (`graphicsLayer` por giroscopio `Sensor.TYPE_ROTATION_VECTOR` con ciclo de vida limpio + arrastre táctil directo en Reproductor y Detalle), botones y barra de progreso con brillo metálico oro (AGSL `RuntimeShader` en Android 13+ y `linearGradient` en Android 10–12) e indicadores Lottie locales (`anim_lux_loader.json`). Controlada por el interruptor "Reducir efectos" en Ajustes → Apariencia.
- **Material 3 Theming**: Paleta personalizada de morado profundo (`#1A1030`), morado primario (`#6A3FA0`), plata (`#C9CED6`) y acentos en oro de lujo (`#D4AF37`), con soporte para modo oscuro, claro y seguir al sistema.

### B. Capa de Reproducción, Conversión y Notificaciones
- **PlaybackService**: Servicio de primer plano (`MediaSessionService` con `foregroundServiceType="mediaPlayback"`).
  - Configurado con `AudioAttributes` de tipo voz (`CONTENT_TYPE_SPEECH`, `USAGE_MEDIA`) y `C.WAKE_MODE_LOCAL`.
  - Audio Focus automático, `handleAudioBecomingNoisy = true`, salto de silencios, reanudación al conectar Bluetooth (`AudioDeviceCallback` + `BLUETOOTH_CONNECT`), y efectos de audio (`AudioEffectsManager`: `Equalizer` de 5 bandas y `LoudnessEnhancer` de `0–6 dB` con verificación de soporte de dispositivo y liberación limpia en `onDestroy()`).
  - `DefaultMediaNotificationProvider` en `playback_channel` con controles en pantalla de bloqueo, carátula y botones de salto dinámico (`±jumpSeconds`).
- **PlaybackConnection**: Controlador en memoria que conecta la UI con la sesión, emite `StateFlow<PlaybackUiState>`, aplica velocidad por defecto, retroceso automático al reanudar tras pausa (`0–5 s`), checkpoints cada 5 s y temporizador con fundido gradual.
- **Conversión a Audiolibro (`engine/TtsEngine.kt`, `TtsConverterEngine.kt`, `OpusAudioEncoder.kt`, `ConversionService.kt`)**:
  - Interfaz común `TtsEngine` implementada por `LocalAndroidTtsEngine` (fragmentación estricta `< getMaxSpeechInputLength()`) y `LoquendoServerTtsEngine`, lista para añadir Piper en fases siguientes.
  - `OpusAudioEncoder` une segmentos PCM, aplica normalización por capítulo (`≈ -16 LUFS` con filtro K-weighting y tope de pico `-1 dBFS` sin recortes), guarda `appliedGainDb`, `measuredLufs` y `peakDbfs` en `filesDir/books/<id>/metadata.json`, remuestrea a 48 kHz y codifica cada capítulo a **Opus/OGG** (API 29+) con fallback AAC/M4A, eliminando todos los WAV temporales.
  - `ConversionService` (`foregroundServiceType="dataSync"`) incluye manejo `onTimeout` (límite de 6h en Android 15) y checkpoints incrementales por capítulo para reanudar automáticamente desde el último capítulo completado sin perder progreso.
- **Canales de Notificación (`util/NotificationChannels.kt`)**: 4 canales silenciosos creados al arranque (`playback_channel`, `downloads_channel`, `translation_channel`, `conversion_channel`).

### C. Capa de Datos y Persistencia Local (Room v2 + DataStore)
- **AppDatabase (v2)**:
  - `Book`, `Part`, `Chapter`, `Bookmark`, `Folder`, `RecycleEntry`, `TextItem` (exclusivamente texto: `EPUB`, `TXT`, `PDF`, `DOCX`, `HTML`, `MD`), `Playlist` y `PlaylistItem`.
- **SettingsStore**: persistencia en DataStore de tema, `reduceEffects`, `PlayerSettings` (velocidad, salto 10/15/30 s, retroceso al reanudar, saltar silencios, normalizador único con `normalizeGainDb` `0..6 dB`, presets y 5 bandas de EQ, Bluetooth auto-resume, siguiente capítulo), vista cuadrícula/lista, estado del onboarding y configuración Loquendo.
- **Almacenamiento Seguro**:
  - Estricto uso de la sandbox privada de la app (`filesDir/books/<id>/` + `metadata.json`, `filesDir/conversion_staging/` y `filesDir/texts/`) sin permisos amplios de almacenamiento compartido.


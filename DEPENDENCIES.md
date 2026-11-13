# DEPENDENCIES - Play LuX

Mapeo detallado de cada dependencia declarada en `app/build.gradle.kts` (100% libre de Firebase, Gemini, Google Services y claves de nube), su versión en `libs.versions.toml` y los archivos donde se utiliza activamente.

| Librería | Versión | Archivos Donde Se Usa | Estado |
|---|---|---|---|
| `androidx.compose:compose-bom` | 2024.09.00 | `app/build.gradle.kts` | Activa |
| `androidx.compose.material3:material3` | BOM | `ui/theme/Theme.kt`<br>`ui/MainRootScreen.kt`<br>`ui/vault/VaultScreen.kt`<br>`ui/player/PlayerScreen.kt`<br>`ui/settings/SettingsScreen.kt`<br>`ui/components/*`<br>`ui/placeholders/*` | Activa |
| `androidx.compose.ui:ui` | BOM | En todos los composables de UI | Activa |
| `androidx.compose.ui:ui-tooling-preview` | BOM | Vistas previas en componentes Compose | Activa |
| `androidx.compose.material:material-icons-extended` | BOM | `ui/MainRootScreen.kt`<br>`ui/vault/VaultScreen.kt`<br>`ui/player/PlayerScreen.kt`<br>`ui/placeholders/*` | Activa |
| `androidx.activity:activity-compose` | 1.10.1 | `MainActivity.kt`<br>`ui/onboarding/OnboardingScreen.kt`<br>`ui/vault/VaultScreen.kt` | Activa |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.8.7 | `ui/MainRootScreen.kt` | Activa |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.7 | `MainActivity.kt`<br>`ui/vault/VaultScreen.kt`<br>`ui/player/PlayerScreen.kt`<br>`ui/settings/SettingsScreen.kt` | Activa |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | `di/AppContainer.kt`<br>`playback/PlaybackConnection.kt`<br>`util/TestAudioGenerator.kt`<br>`util/CoverGenerator.kt` | Activa |
| `androidx.room:room-runtime` | 2.7.0 | `data/local/AppDatabase.kt` | Activa |
| `androidx.room:room-ktx` | 2.7.0 | `data/local/dao/*.kt`<br>`data/local/repository/*.kt` | Activa |
| `androidx.room:room-compiler` (KSP) | 2.7.0 | `app/build.gradle.kts` (ksp) | Activa |
| `androidx.media3:media3-exoplayer` | 1.5.1 | `playback/PlaybackService.kt`<br>`playback/PlaybackConnection.kt` | Activa |
| `androidx.media3:media3-session` | 1.5.1 | `playback/PlaybackService.kt`<br>`playback/PlaybackConnection.kt` | Activa |
| `androidx.media3:media3-common` | 1.5.1 | `playback/PlaybackService.kt`<br>`playback/PlaybackConnection.kt` | Activa |
| `androidx.media3:media3-ui` | 1.5.1 | `playback/PlaybackService.kt` | Activa |
| `androidx.work:work-runtime-ktx` | 2.10.0 | `work/RecycleCleanupWorker.kt`<br>`PlayLuxApplication.kt` | Activa |
| `androidx.datastore:datastore-preferences` | 1.1.7 | `data/datastore/SettingsStore.kt` | Activa |
| `io.coil-kt:coil-compose` | 2.7.0 | `ui/components/CoverImage.kt` | Activa |
| `androidx.palette:palette-ktx` | 1.0.0 | `util/CoverGenerator.kt`<br>`playback/PlaybackConnection.kt` | Activa |
| `androidx.navigation:navigation-compose` | 2.8.9 | `ui/navigation/NavRoutes.kt`<br>`ui/MainRootScreen.kt` | Activa |
| `androidx.core:core-splashscreen` | 1.0.1 | `MainActivity.kt`<br>`res/values/themes.xml` | Activa |
| `com.squareup.okhttp3:okhttp` | 4.10.0 | `engine/PublicBookSearchEngine.kt`<br>`engine/TtsEngine.kt`<br>`engine/TtsConverterEngine.kt` | Activa |
| `com.google.mlkit:translate` | 17.0.3 | `engine/LocalTranslatorEngine.kt` | Activa |
| `com.google.mlkit:language-id` | 17.0.6 | `engine/LocalTranslatorEngine.kt` | Activa |
| `com.airbnb.android:lottie-compose` | 6.6.0 | `ui/effects/Lux25DEffects.kt`<br>`res/raw/anim_lux_loader.json` | Activa |

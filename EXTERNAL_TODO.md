# EXTERNAL_TODO - Play LuX

Este documento define el contrato técnico exacto, las firmas de interfaz Kotlin, el comportamiento esperado y la estrategia de prueba para cada componente y motor que se implementará en fases posteriores o fuera del entorno de AI Studio (por ejemplo, librerías nativas C++/NDK de Piper o eSpeak NG).

---

## 1. TtsEngine (Síntesis de Voz en el Dispositivo)

### Propósito
Motor de síntesis de voz en el dispositivo para convertir fragmentos de texto preparado a audio PCM sin conexión a internet.
En producción utilizará modelos ligeros de **Piper** (ONNX / C++) y **eSpeak NG** como alternativa de bajo consumo.

### Contrato de la Interfaz
```kotlin
package com.example.engine.tts

import kotlinx.coroutines.flow.Flow

data class VoiceInfo(
    val id: String,
    val name: String,
    val lang: String,       // Código BCP-47 (ej. "es-ES", "es-MX")
    val quality: String,    // "low", "medium", "high"
    val sampleRate: Int = 22050
)

interface TtsEngine {
    /**
     * Devuelve la lista de voces locales instaladas en la sandbox.
     */
    suspend fun getAvailableVoices(): List<VoiceInfo>

    /**
     * Sintetiza un fragmento de texto a muestras PCM de 16-bit mono.
     * Emite fragmentos de ByteArray (chunks PCM) de forma reactiva por Flow
     * para permitir codificación simultánea en streaming sin saturar la RAM.
     */
    fun synthesize(
        textChunk: String,
        voiceId: String,
        speed: Float = 1.0f
    ): Flow<ByteArray>
}
```

### Implementación Stub de Desarrollo
- Emite silencio PCM o tonos de audio con una duración proporcional a las palabras (~150 palabras/minuto).

### Cómo Probarlo
1. Probar que `synthesize("Hola mundo", "es_ES-davefx-medium")` emite flujos de bytes múltiplos de 2 (16 bits).
2. Verificar que la velocidad altera la tasa de emisión sin distorsión armónica.
3. Comprobar que cancelar la corrutina detiene la emisión de audio de inmediato.

---

## 2. AudioEncoder (Codificador de Audio PCM a Opus/OGG y AAC/M4A)

### Propósito
Codificar el flujo continuo de audio PCM mono de 16 bits proveniente de `TtsEngine` hacia archivos comprimidos ligeros (Opus/OGG nativo en Android API 29+ con `MediaCodec` y `MediaMuxer`, o fallback a AAC/M4A) en `filesDir/books/<id>/`.

### Contrato de la Interfaz
```kotlin
package com.example.engine.audio

import java.io.File
import kotlinx.coroutines.flow.Flow

enum class AudioCodecFormat {
    OPUS_OGG,
    AAC_M4A
}

interface AudioEncoder {
    /**
     * Recibe el Flow de PCM y codifica en tiempo real directamente al archivo de salida en disco.
     * @return Duración real del archivo en milisegundos.
     */
    suspend fun encodeToFile(
        pcmFlow: Flow<ByteArray>,
        sampleRate: Int = 22050,
        bitrateKbps: Int = 32,
        format: AudioCodecFormat = AudioCodecFormat.OPUS_OGG,
        outputFile: File
    ): Long
}
```

### Cómo Probarlo
1. Alimentar con un flujo sintético de 5 segundos de PCM y verificar que el archivo `.ogg` o `.m4a` resultante se puede reproducir con `ExoPlayer`.
2. Verificar que los encabezados del contenedor (Ogg/Opus o MP4/AAC) no quedan truncados si se interrumpe la codificación.

---

## 3. TranslationEngine (Traductor en el Dispositivo)

### Propósito
Traducir el texto preparado cuando el idioma de origen del documento difiere del idioma deseado para el audio (por ejemplo, libro en inglés para escuchar en español). Debe utilizar modelos offline de **ML Kit Translate**.

### Contrato de la Interfaz
```kotlin
package com.example.engine.translate

interface TranslationEngine {
    /**
     * Verifica si el paquete del par de idiomas está descargado localmente.
     */
    suspend fun isModelDownloaded(sourceLang: String, targetLang: String): Boolean

    /**
     * Descarga el modelo offline únicamente por conexión Wi-Fi.
     */
    suspend fun downloadModel(sourceLang: String, targetLang: String): Boolean

    /**
     * Traduce un fragmento de texto conservando la estructura de puntuación.
     */
    suspend fun translateText(
        text: String,
        sourceLang: String,
        targetLang: String
    ): Result<String>
}
```

### Cómo Probarlo
1. Probar la detección del idioma con texto de prueba en español, inglés y francés.
2. Comprobar el comportamiento cuando el modelo no está descargado (debe reportar un error legible sin crashear).

---

## 4. VoiceCatalog (Catálogo Remoto de Voces y Temas)

### Propósito
Descargar modelos de voz y temas visuales desde un catálogo remoto en formato JSON. Solo descarga datos (archivos `.onnx`, `.onnx.json` y `.json`), **nunca** código ejecutable ni `.dex` / `.so`.

### Contrato de la Interfaz
```kotlin
package com.example.catalog

import java.io.File

data class RemoteVoiceEntry(
    val id: String,
    val name: String,
    val lang: String,
    val sizeBytes: Long,
    val downloadUrl: String,
    val sha256: String
)

data class RemoteCatalog(
    val version: Int,
    val voices: List<RemoteVoiceEntry>
)

interface VoiceCatalog {
    /**
     * Descarga y parsea el catálogo remoto configurado en un único endpoint URL.
     */
    suspend fun fetchCatalog(): Result<RemoteCatalog>

    /**
     * Descarga la voz en segundo plano a filesDir/voices/<id>/,
     * verificando rigurosamente el hash SHA-256 antes de darla por válida.
     */
    suspend fun downloadVoice(
        entry: RemoteVoiceEntry,
        destinationDir: File,
        onProgress: (percent: Int) -> Unit
    ): Result<File>
}
```

### Cómo Probarlo
1. Probar la validación de integridad SHA-256: si el hash difiere, el archivo temporal debe ser descartado de inmediato.
2. Verificar que se restrinja la descarga si no hay conexión Wi-Fi activa (según ajustes del usuario).

---

## 5. SearchSource (Buscador en Fuentes Legales de Dominio Público)

### Propósito
Buscar libros de dominio público en **Project Gutenberg**, **Internet Archive** y **Wikisource**, obteniendo metadatos, sinopsis, portada y enlace de descarga de archivos (EPUB, TXT, HTML).

### Contrato de la Interfaz
```kotlin
package com.example.search

data class PublicDomainBook(
    val sourceId: String,
    val title: String,
    val author: String,
    val language: String,
    val coverUrl: String?,
    val downloadUrl: String,
    val format: String, // "epub", "txt", "pdf"
    val sourceName: String // "Project Gutenberg", etc.
)

interface SearchSource {
    val sourceName: String

    suspend fun searchBooks(query: String, page: Int = 1): Result<List<PublicDomainBook>>

    suspend fun downloadBookFile(book: PublicDomainBook, targetFile: java.io.File): Result<Unit>
}
```

### Cómo Probarlo
1. Realizar una búsqueda de prueba por autor ("Cervantes") y comprobar el parseo de resultados.
2. Descargar un EPUB de prueba en la sandbox y verificar que no sobrepasa el límite de 20 MB.

---

## 6. Formato de Exportación e Importación .lux

### Propósito
Compartir audiolibros completos empaquetados en un único archivo `.lux` (archivo ZIP que contiene `metadata.json`, los capítulos en formato Opus/OGG sin recomprimir y la portada).

### Contrato de la Interfaz
```kotlin
package com.example.packager

import java.io.File

data class LuxBookMetadata(
    val id: String,
    val title: String,
    val author: String,
    val lang: String,
    val durationMs: Long,
    val chapters: List<String>
)

interface LuxPackager {
    suspend fun exportToLux(bookId: String, outputFile: File): Result<File>
    suspend fun importFromLux(luxFile: File): Result<String> // Devuelve el bookId importado
}
```

### Cómo Probarlo
1. Exportar el libro de prueba a un `.lux`.
2. Inspeccionar el archivo con un descompresor ZIP estándar y comprobar que los archivos de audio conservan su checksum original sin re-compresión.
3. Importar el `.lux` en una instancia limpia de la app y verificar que se reproduce de inmediato.

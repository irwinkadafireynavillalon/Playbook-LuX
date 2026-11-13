package com.example.util

data class SyntaxIssue(
    val chapterIndex: Int,
    val chapterTitle: String,
    val category: String,
    val description: String,
    val fixedCount: Int = 0
)

data class SyntaxReviewReport(
    val totalIssuesDetected: Int,
    val totalAutoFixed: Int,
    val fixedHyphens: Int,
    val fixedLineBreaks: Int,
    val fixedPunctuation: Int,
    val fixedEncodingOrSpacing: Int,
    val issues: List<SyntaxIssue>,
    val warnings: List<String>,
    val reviewedChapters: List<ExtractedChapter>
) {
    fun buildCompactSummary(): String {
        val parts = mutableListOf<String>()
        if (totalAutoFixed > 0) parts += "$totalAutoFixed correcciones automáticas"
        if (fixedHyphens > 0) parts += "$fixedHyphens guiones unidos"
        if (fixedLineBreaks > 0) parts += "$fixedLineBreaks saltos de línea reparados"
        if (fixedPunctuation > 0) parts += "$fixedPunctuation signos ajustados"
        if (warnings.isNotEmpty()) parts += "${warnings.size} avisos lógicos"
        return if (parts.isEmpty()) {
            "Texto limpio y con buena sintaxis."
        } else {
            parts.joinToString(" • ")
        }
    }
}

/**
 * Revisor sintáctico, ortográfico y de lógica estructural 100% en el dispositivo.
 * Corrige cortes de PDF/OCR, guiones rotos, mojibake, espaciado, puntuación española/inglesa
 * y detecta anomalías de lógica antes/después de traducir y antes de convertir a voz.
 */
object TextSyntaxReviewer {

    private val MOJIBAKE_MAP = listOf(
        "Ã¡" to "á", "Ã©" to "é", "Ã­" to "í", "Ã³" to "ó", "Ãº" to "ú",
        "Ã±" to "ñ", "Ã¼" to "ü", "Ã\u0081" to "Á", "Ã\u0089" to "É",
        "Ã\u008D" to "Í", "Ã\u0093" to "Ó", "Ã\u009A" to "Ú", "Ã\u0091" to "Ñ",
        "Â¿" to "¿", "Â¡" to "¡", "â€œ" to "\"", "â€\u009D" to "\"",
        "â€™" to "'", "â€”" to "—", "â€“" to "–", "Â " to " "
    )

    fun reviewChapters(
        chapters: List<ExtractedChapter>,
        languageCode: String = "es"
    ): SyntaxReviewReport {
        if (chapters.isEmpty()) {
            return SyntaxReviewReport(
                totalIssuesDetected = 1,
                totalAutoFixed = 0,
                fixedHyphens = 0,
                fixedLineBreaks = 0,
                fixedPunctuation = 0,
                fixedEncodingOrSpacing = 0,
                issues = emptyList(),
                warnings = listOf("El documento no contiene capítulos ni párrafos legibles."),
                reviewedChapters = emptyList()
            )
        }

        val issues = mutableListOf<SyntaxIssue>()
        val warnings = mutableListOf<String>()
        val cleaned = mutableListOf<ExtractedChapter>()

        var totalHyphens = 0
        var totalLineBreaks = 0
        var totalPunctuation = 0
        var totalEncodingSpacing = 0

        val isSpanish = languageCode.lowercase().startsWith("es")
        var previousFingerprint = ""

        chapters.forEachIndexed { idx, ch ->
            val chLabel = ch.title.ifBlank { "Capítulo ${idx + 1}" }
            var text = ch.content.replace("\r\n", "\n").replace("\r", "\n")

            // 1. Encoding / invisible control characters cleanup
            var encFixes = 0
            for ((bad, good) in MOJIBAKE_MAP) {
                if (text.contains(bad)) {
                    val occurrences = text.windowed(bad.length).count { it == bad }
                    encFixes += occurrences
                    text = text.replace(bad, good)
                }
            }
            val cleanedInvisible = text
                .replace("\uFEFF", "")
                .replace("\u00AD", "")
                .replace("\u200B", "")
                .replace("\uFFFD", "")
                .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "")
            if (cleanedInvisible != text) {
                encFixes += 1
                text = cleanedInvisible
            }

            val beforeMultiSpace = text
            text = text.replace(Regex("[ \\t]{2,}"), " ")
                .replace(Regex("(?m)^ +| +$"), "")
                .replace(Regex("\\n{3,}"), "\n\n")
            if (text != beforeMultiSpace) {
                encFixes += 1
            }

            if (encFixes > 0) {
                totalEncodingSpacing += encFixes
                issues += SyntaxIssue(
                    chapterIndex = idx,
                    chapterTitle = chLabel,
                    category = "Codificación y espacios",
                    description = "Se limpiaron $encFixes caracteres extraños o espacios múltiples.",
                    fixedCount = encFixes
                )
            }

            // 2. Join broken hyphenated words across PDF/OCR line wraps
            val hyphenRegex = Regex("([A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,})-\\s*\\n\\s*([a-záéíóúüñ]{2,})")
            val hyphenMatches = hyphenRegex.findAll(text).count()
            if (hyphenMatches > 0) {
                text = hyphenRegex.replace(text, "$1$2")
                totalHyphens += hyphenMatches
                issues += SyntaxIssue(
                    chapterIndex = idx,
                    chapterTitle = chLabel,
                    category = "Guiones cortados",
                    description = "Se unieron $hyphenMatches palabras divididas por salto de línea.",
                    fixedCount = hyphenMatches
                )
            }

            // 3. Repair mid-sentence line wraps while preserving paragraph breaks and dialogue dashes
            val wrapRegex = Regex("([a-zA-ZáéíóúüñÁÉÍÓÚÜÑ,;:])\\n(?![\\n\\-—•*#\\d])([a-záéíóúüñ¿¡\"«(])")
            val wrapMatches = wrapRegex.findAll(text).count()
            if (wrapMatches > 0) {
                text = wrapRegex.replace(text, "$1 $2")
                totalLineBreaks += wrapMatches
                issues += SyntaxIssue(
                    chapterIndex = idx,
                    chapterTitle = chLabel,
                    category = "Saltos de línea",
                    description = "Se repararon $wrapMatches cortes de frase heredados de PDF/OCR.",
                    fixedCount = wrapMatches
                )
            }

            // 4. Punctuation & Spanish/general capitalization fixes
            var punctFixes = 0
            val spaceBeforePunct = Regex(" +([,.;:!?])")
            val sbpCount = spaceBeforePunct.findAll(text).count()
            if (sbpCount > 0) {
                text = spaceBeforePunct.replace(text, "$1")
                punctFixes += sbpCount
            }

            val missingSpaceAfterPunct = Regex("([,;:!?])([A-Za-zÁÉÍÓÚÜÑáéíóúüñ])")
            val msCount = missingSpaceAfterPunct.findAll(text).count()
            if (msCount > 0) {
                text = missingSpaceAfterPunct.replace(text, "$1 $2")
                punctFixes += msCount
            }

            // Period followed immediately by capitalized word (avoid breaking decimals or URLs)
            val missingSpaceAfterPeriod = Regex("([a-záéíóúüñ])\\.([A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ])")
            val mspCount = missingSpaceAfterPeriod.findAll(text).count()
            if (mspCount > 0) {
                text = missingSpaceAfterPeriod.replace(text, "$1. $2")
                punctFixes += mspCount
            }

            // Fix spaces right after inverted Spanish openers ("¿ hola" -> "¿hola")
            val spanishOpenerSpace = Regex("([¿¡])\\s+")
            val sosCount = spanishOpenerSpace.findAll(text).count()
            if (sosCount > 0) {
                text = spanishOpenerSpace.replace(text, "$1")
                punctFixes += sosCount
            }

            // Collapse duplicate commas or double periods (preserving "...")
            val dupComma = Regex(",{2,}")
            if (dupComma.containsMatchIn(text)) {
                text = dupComma.replace(text, ",")
                punctFixes++
            }
            val doubleDot = Regex("(?<!\\.)\\.\\.(?!\\.)")
            if (doubleDot.containsMatchIn(text)) {
                text = doubleDot.replace(text, ".")
                punctFixes++
            }

            if (punctFixes > 0) {
                totalPunctuation += punctFixes
                issues += SyntaxIssue(
                    chapterIndex = idx,
                    chapterTitle = chLabel,
                    category = "Puntuación y sintaxis",
                    description = "Se ajustaron $punctFixes signos de puntuación y espaciado.",
                    fixedCount = punctFixes
                )
            }

            // Clean title whitespace as well
            val cleanTitle = chLabel.replace(Regex("\\s+"), " ").trim()
            val finalContent = text.trim()

            // 5. Logical & structural diagnostics (warnings requiring human awareness)
            val wordCount = TextFileExtractor.countWords(finalContent)
            if (wordCount == 0) {
                warnings += "$cleanTitle está vacío tras limpiar caracteres."
            } else if (wordCount < 6) {
                warnings += "$cleanTitle es muy corto ($wordCount palabras); verifica si falta contenido."
            }

            val openParens = finalContent.count { it == '(' }
            val closeParens = finalContent.count { it == ')' }
            if (openParens != closeParens) {
                warnings += "$cleanTitle tiene paréntesis desbalanceados ($openParens abiertos / $closeParens cerrados)."
            }

            val quoteCount = finalContent.count { it == '"' }
            if (quoteCount % 2 != 0) {
                warnings += "$cleanTitle tiene comillas dobles sin cerrar."
            }

            if (isSpanish) {
                val closeQ = finalContent.count { it == '?' }
                val openQ = finalContent.count { it == '¿' }
                if (closeQ > openQ + 1) {
                    warnings += "$cleanTitle tiene $closeQ cierres '?' pero solo $openQ aperturas '¿' (posible falla de OCR/traducción)."
                }
            }

            val fingerprint = finalContent.take(140).lowercase()
            if (fingerprint.length > 40 && fingerprint == previousFingerprint) {
                warnings += "$cleanTitle parece duplicar el inicio del capítulo anterior."
            }
            previousFingerprint = fingerprint

            cleaned += ExtractedChapter(title = cleanTitle, content = finalContent)
        }

        val totalAutoFixed = totalHyphens + totalLineBreaks + totalPunctuation + totalEncodingSpacing
        val totalDetected = totalAutoFixed + warnings.size

        return SyntaxReviewReport(
            totalIssuesDetected = totalDetected,
            totalAutoFixed = totalAutoFixed,
            fixedHyphens = totalHyphens,
            fixedLineBreaks = totalLineBreaks,
            fixedPunctuation = totalPunctuation,
            fixedEncodingOrSpacing = totalEncodingSpacing,
            issues = issues,
            warnings = warnings.distinct(),
            reviewedChapters = cleaned
        )
    }
}

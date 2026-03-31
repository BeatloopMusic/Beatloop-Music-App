package com.beatloop.music.domain.recommendation

import com.beatloop.music.data.model.SongItem

object RecommendationContentRules {
    const val MAX_TRACK_DURATION_MS: Long = 10L * 60L * 1000L

    private val liveWordRegex = Regex("\\blive\\b", RegexOption.IGNORE_CASE)

    val supportedLanguages: List<String> = listOf(
        "Hindi",
        "Telugu",
        "Tamil",
        "Kannada",
        "Malayalam",
        "Punjabi",
        "Bengali",
        "Marathi",
        "Gujarati",
        "Odia",
        "English",
        "Spanish",
        "Korean",
        "Japanese",
        "Arabic",
        "French",
        "Portuguese",
        "Turkish"
    )

    private val languageCodeToName: Map<String, String> = mapOf(
        "en" to "English",
        "hi" to "Hindi",
        "te" to "Telugu",
        "ta" to "Tamil",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "pa" to "Punjabi",
        "bn" to "Bengali",
        "mr" to "Marathi",
        "gu" to "Gujarati",
        "or" to "Odia",
        "es" to "Spanish",
        "ko" to "Korean",
        "ja" to "Japanese",
        "ar" to "Arabic",
        "fr" to "French",
        "pt" to "Portuguese",
        "tr" to "Turkish"
    )

    private val languageAliasToName: Map<String, String> = mapOf(
        "hindi" to "Hindi",
        "bollywood" to "Hindi",
        "telugu" to "Telugu",
        "tollywood" to "Telugu",
        "tamil" to "Tamil",
        "kollywood" to "Tamil",
        "kannada" to "Kannada",
        "malayalam" to "Malayalam",
        "punjabi" to "Punjabi",
        "bengali" to "Bengali",
        "marathi" to "Marathi",
        "gujarati" to "Gujarati",
        "odia" to "Odia",
        "english" to "English",
        "spanish" to "Spanish",
        "korean" to "Korean",
        "k pop" to "Korean",
        "kpop" to "Korean",
        "japanese" to "Japanese",
        "j pop" to "Japanese",
        "jpop" to "Japanese",
        "arabic" to "Arabic",
        "french" to "French",
        "portuguese" to "Portuguese",
        "turkish" to "Turkish"
    )

    fun normalizeLanguage(value: String?): String? {
        val raw = value?.trim()?.lowercase().orEmpty()
        if (raw.isBlank() || raw == "none") return null

        languageCodeToName[raw]?.let { return it }
        languageAliasToName[raw]?.let { return it }

        val titleCase = raw
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { first ->
                    if (first.isLowerCase()) first.titlecase() else first.toString()
                }
            }

        return if (supportedLanguages.contains(titleCase)) titleCase else null
    }

    fun normalizeLanguages(values: Collection<String>): Set<String> {
        return values.mapNotNull(::normalizeLanguage).toSet()
    }

    fun inferLanguagesFromSong(song: SongItem): Set<String> {
        return inferLanguagesFromText("${song.title} ${song.artistsText}")
    }

    fun inferLanguagesFromText(value: String?): Set<String> {
        val raw = value?.lowercase().orEmpty()
        if (raw.isBlank()) return emptySet()

        val normalized = raw.replace(Regex("[^a-z0-9\\s]"), " ")
        return languageAliasToName.entries
            .asSequence()
            .filter { (token, _) -> containsToken(normalized, token) }
            .map { it.value }
            .toSet()
    }

    fun isDurationAllowed(durationMs: Long?): Boolean {
        return durationMs == null || durationMs <= MAX_TRACK_DURATION_MS
    }

    fun isTitleAllowed(title: String): Boolean {
        return !liveWordRegex.containsMatchIn(title)
    }

    fun isTrackAllowed(song: SongItem): Boolean {
        return isTitleAllowed(song.title) && isDurationAllowed(song.duration)
    }

    fun matchesAllowedLanguages(
        song: SongItem,
        allowedLanguages: Set<String>,
        allowUnknownLanguage: Boolean
    ): Boolean {
        if (allowedLanguages.isEmpty()) return true
        val normalizedAllowed = normalizeLanguages(allowedLanguages)
        if (normalizedAllowed.isEmpty()) return true

        val inferred = inferLanguagesFromSong(song)
        if (inferred.isEmpty()) return allowUnknownLanguage

        return inferred.any { normalizedAllowed.contains(it) }
    }

    private fun containsToken(text: String, token: String): Boolean {
        val pattern = Regex("(^|\\s)${Regex.escape(token)}(\\s|$)")
        return pattern.containsMatchIn(text)
    }
}

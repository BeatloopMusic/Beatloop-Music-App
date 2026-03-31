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

    val supportedCountries: List<String> = listOf(
        "IN",
        "US",
        "GB",
        "KR",
        "JP",
        "ES",
        "FR",
        "BR",
        "TR",
        "AE"
    )

    private val countryCodeToName: Map<String, String> = mapOf(
        "IN" to "India",
        "US" to "United States",
        "GB" to "United Kingdom",
        "KR" to "South Korea",
        "JP" to "Japan",
        "ES" to "Spain",
        "FR" to "France",
        "BR" to "Brazil",
        "TR" to "Turkey",
        "AE" to "United Arab Emirates"
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

    private val languageNameToCountry: Map<String, String> = mapOf(
        "Hindi" to "IN",
        "Telugu" to "IN",
        "Tamil" to "IN",
        "Kannada" to "IN",
        "Malayalam" to "IN",
        "Punjabi" to "IN",
        "Bengali" to "IN",
        "Marathi" to "IN",
        "Gujarati" to "IN",
        "Odia" to "IN",
        "English" to "US",
        "Spanish" to "ES",
        "Korean" to "KR",
        "Japanese" to "JP",
        "Arabic" to "AE",
        "French" to "FR",
        "Portuguese" to "BR",
        "Turkish" to "TR"
    )

    private val countryAliasToCode: Map<String, String> = mapOf(
        "india" to "IN",
        "indian" to "IN",
        "bollywood" to "IN",
        "tollywood" to "IN",
        "kollywood" to "IN",
        "desi" to "IN",
        "united states" to "US",
        "usa" to "US",
        "american" to "US",
        "hollywood" to "US",
        "united kingdom" to "GB",
        "uk" to "GB",
        "british" to "GB",
        "south korea" to "KR",
        "korea" to "KR",
        "korean" to "KR",
        "k pop" to "KR",
        "kpop" to "KR",
        "japan" to "JP",
        "japanese" to "JP",
        "j pop" to "JP",
        "jpop" to "JP",
        "spain" to "ES",
        "spanish" to "ES",
        "france" to "FR",
        "french" to "FR",
        "brazil" to "BR",
        "brazilian" to "BR",
        "turkey" to "TR",
        "turkish" to "TR",
        "uae" to "AE",
        "emirati" to "AE",
        "dubai" to "AE"
    )

    private val countryToLanguages: Map<String, Set<String>> =
        languageNameToCountry.entries
            .groupBy(keySelector = { it.value }, valueTransform = { it.key })
            .mapValues { (_, values) -> values.toSet() }

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

    fun normalizeCountry(value: String?): String? {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank() || raw.equals("none", ignoreCase = true)) return null

        val code = raw.uppercase()
        if (countryCodeToName.containsKey(code)) return code

        val normalized = raw
            .lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return countryAliasToCode[normalized]
    }

    fun normalizeCountries(values: Collection<String>): Set<String> {
        return values.mapNotNull(::normalizeCountry).toSet()
    }

    fun countryDisplayName(countryCode: String): String {
        return countryCodeToName[countryCode.uppercase()] ?: countryCode.uppercase()
    }

    fun languagesForCountry(countryCode: String?): Set<String> {
        val normalized = normalizeCountry(countryCode) ?: return emptySet()
        return countryToLanguages[normalized].orEmpty()
    }

    fun languagesForCountries(countryCodes: Set<String>): Set<String> {
        return normalizeCountries(countryCodes)
            .flatMap { country -> countryToLanguages[country].orEmpty() }
            .toSet()
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

    fun inferCountriesFromSong(song: SongItem): Set<String> {
        return inferCountriesFromText("${song.title} ${song.artistsText}")
    }

    fun inferCountriesFromText(value: String?): Set<String> {
        val raw = value?.lowercase().orEmpty()
        if (raw.isBlank()) return emptySet()

        val normalized = raw.replace(Regex("[^a-z0-9\\s]"), " ")

        val aliasCountries = countryAliasToCode.entries
            .asSequence()
            .filter { (token, _) -> containsToken(normalized, token) }
            .map { it.value }
            .toSet()

        val inferredFromLanguage = inferLanguagesFromText(value)
            .mapNotNull { language -> languageNameToCountry[language] }
            .toSet()

        return aliasCountries + inferredFromLanguage
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

    fun matchesAllowedCountries(
        song: SongItem,
        allowedCountries: Set<String>,
        allowUnknownCountry: Boolean
    ): Boolean {
        if (allowedCountries.isEmpty()) return true
        val normalizedAllowed = normalizeCountries(allowedCountries)
        if (normalizedAllowed.isEmpty()) return true

        val inferred = inferCountriesFromSong(song)
        if (inferred.isEmpty()) return allowUnknownCountry

        return inferred.any { normalizedAllowed.contains(it) }
    }

    private fun containsToken(text: String, token: String): Boolean {
        val pattern = Regex("(^|\\s)${Regex.escape(token)}(\\s|$)")
        return pattern.containsMatchIn(text)
    }
}

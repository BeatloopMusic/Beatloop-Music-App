package com.beatloop.music.data.model

import kotlinx.serialization.Serializable

/**
 * SponsorBlock segment data
 */
@Serializable
data class SponsorBlockSegment(
    val segment: List<Double>,
    val UUID: String,
    val category: String,
    val videoDuration: Double? = null,
    val actionType: String = "skip"
) {
    val startTime: Long get() = (segment.getOrNull(0) ?: 0.0).toLong() * 1000
    val endTime: Long get() = (segment.getOrNull(1) ?: 0.0).toLong() * 1000
}

/**
 * SponsorBlock categories
 */
enum class SponsorBlockCategory(val value: String, val displayName: String) {
    SPONSOR("sponsor", "Sponsor"),
    SELFPROMO("selfpromo", "Self Promotion"),
    INTERACTION("interaction", "Interaction Reminder"),
    INTRO("intro", "Intro"),
    OUTRO("outro", "Outro"),
    PREVIEW("preview", "Preview/Recap"),
    MUSIC_OFFTOPIC("music_offtopic", "Non-Music Section"),
    FILLER("filler", "Filler"),
    POI_HIGHLIGHT("poi_highlight", "Highlight")
}

/**
 * SponsorBlock settings
 */
data class SponsorBlockSettings(
    val enabled: Boolean = true,
    val skipSponsor: Boolean = true,
    val skipSelfPromo: Boolean = true,
    val skipIntro: Boolean = true,
    val skipOutro: Boolean = true,
    val skipInteraction: Boolean = false,
    val skipPreview: Boolean = false,
    val skipFiller: Boolean = false,
    val showToast: Boolean = true
) {
    fun getEnabledCategories(): List<SponsorBlockCategory> {
        val categories = mutableListOf<SponsorBlockCategory>()
        if (skipSponsor) categories.add(SponsorBlockCategory.SPONSOR)
        if (skipSelfPromo) categories.add(SponsorBlockCategory.SELFPROMO)
        if (skipIntro) categories.add(SponsorBlockCategory.INTRO)
        if (skipOutro) categories.add(SponsorBlockCategory.OUTRO)
        if (skipInteraction) categories.add(SponsorBlockCategory.INTERACTION)
        if (skipPreview) categories.add(SponsorBlockCategory.PREVIEW)
        if (skipFiller) categories.add(SponsorBlockCategory.FILLER)
        return categories
    }
    
    fun getCategoryString(): String {
        return getEnabledCategories().joinToString(",") { "\"${it.value}\"" }
    }
}

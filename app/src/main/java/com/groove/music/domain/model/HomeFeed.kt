package com.groove.music.domain.model

import com.groove.music.domain.model.Song

/**
 * Home Feed domain models.
 *
 * HomeFeed is assembled by GetHomeFeedUseCase from local Room data only.
 * No network is required. This is the local personalization layer, separate
 * from the online Discovery layer (RecommendationRepository).
 */

enum class HomeFeedSectionType {
    RECENTLY_PLAYED,
    MOST_PLAYED,
    OFFLINE_PICKS,
    FORGOTTEN_FAVORITES,
    RECOMMENDED_FOR_YOU,
    HABIT_MIX
}

data class HomeFeedSection(
    val type: HomeFeedSectionType,
    val title: String,
    val songs: List<Song>
) {
    val isEmpty: Boolean get() = songs.isEmpty()
}

data class HomeFeed(
    val sections: List<HomeFeedSection>
) {
    /** Returns only non-empty sections — ready for UI rendering. */
    val visibleSections: List<HomeFeedSection> get() = sections.filter { !it.isEmpty }
}

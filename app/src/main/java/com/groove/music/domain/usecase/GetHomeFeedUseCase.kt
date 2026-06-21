package com.groove.music.domain.usecase

import com.groove.music.domain.model.HomeFeed
import com.groove.music.domain.model.HomeFeedSection
import com.groove.music.domain.model.HomeFeedSectionType
import com.groove.music.domain.recommendation.LocalRecommendationEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

private const val SECTION_LIMIT = 10

/**
 * Assembles the full Home Feed from local Room data.
 *
 * All 6 sections are fetched in parallel using coroutineScope + async.
 * Network is NOT required — this is powered entirely by LocalRecommendationEngine.
 *
 * Sections with zero songs are included in the model but filtered out
 * by [HomeFeed.visibleSections] in the UI layer.
 */
class GetHomeFeedUseCase @Inject constructor(
    private val engine: LocalRecommendationEngine
) {
    suspend operator fun invoke(): HomeFeed = coroutineScope {
        val recentlyPlayed    = async { engine.getRecentlyPlayed(SECTION_LIMIT) }
        val mostPlayed        = async { engine.getMostPlayed(SECTION_LIMIT) }
        val offlinePicks      = async { engine.getOfflinePicks(SECTION_LIMIT) }
        val forgottenFavorites= async { engine.getForgottenFavorites(SECTION_LIMIT) }
        val recommendedForYou = async { engine.getRecommendedForYou(SECTION_LIMIT) }
        val habitMix          = async { engine.getHabitMix(SECTION_LIMIT) }

        HomeFeed(
            sections = listOf(
                HomeFeedSection(
                    type  = HomeFeedSectionType.RECENTLY_PLAYED,
                    title = "Recently Played",
                    songs = recentlyPlayed.await()
                ),
                HomeFeedSection(
                    type  = HomeFeedSectionType.RECOMMENDED_FOR_YOU,
                    title = "Recommended For You",
                    songs = recommendedForYou.await()
                ),
                HomeFeedSection(
                    type  = HomeFeedSectionType.MOST_PLAYED,
                    title = "Most Played",
                    songs = mostPlayed.await()
                ),
                HomeFeedSection(
                    type  = HomeFeedSectionType.HABIT_MIX,
                    title = "Your Habit Mix",
                    songs = habitMix.await()
                ),
                HomeFeedSection(
                    type  = HomeFeedSectionType.OFFLINE_PICKS,
                    title = "Offline Picks",
                    songs = offlinePicks.await()
                ),
                HomeFeedSection(
                    type  = HomeFeedSectionType.FORGOTTEN_FAVORITES,
                    title = "Forgotten Favorites",
                    songs = forgottenFavorites.await()
                )
            )
        )
    }
}

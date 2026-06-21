package com.groove.music.di

import com.groove.music.data.recommendation.RecommendationRepositoryImpl
import com.groove.music.data.repository.DownloadRepositoryImpl
import com.groove.music.data.repository.SpotifyRepositoryImpl
import com.groove.music.domain.recommendation.RecommendationRepository
import com.groove.music.domain.repository.DownloadRepository
import com.groove.music.domain.repository.SpotifyRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Phase 2 repository interface bindings.
 *
 * NOTE: SongRepository and PlaylistRepository are concrete @Singleton classes
 * annotated with @Inject and are injected directly — they do NOT need @Binds here.
 * Only interface-backed repos need binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSpotifyRepository(
        impl: SpotifyRepositoryImpl
    ): SpotifyRepository

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(
        impl: DownloadRepositoryImpl
    ): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindRecommendationRepository(
        impl: RecommendationRepositoryImpl
    ): RecommendationRepository
}

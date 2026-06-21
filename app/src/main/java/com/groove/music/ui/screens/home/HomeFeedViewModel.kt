package com.groove.music.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.groove.music.domain.model.HomeFeed
import com.groove.music.domain.usecase.GetHomeFeedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeFeedState {
    object Loading : HomeFeedState()
    data class Ready(val feed: HomeFeed) : HomeFeedState()
    object Empty : HomeFeedState()
    data class Error(val message: String) : HomeFeedState()
}

/**
 * ViewModel for the Home screen.
 *
 * Loads the local Home Feed on launch and exposes it as a StateFlow.
 * The feed is built from Room data only (LocalRecommendationEngine) — no network needed.
 *
 * Refreshing re-runs GetHomeFeedUseCase (called from pull-to-refresh or after playback).
 */
@HiltViewModel
class HomeFeedViewModel @Inject constructor(
    private val getHomeFeedUseCase: GetHomeFeedUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<HomeFeedState>(HomeFeedState.Loading)
    val state: StateFlow<HomeFeedState> = _state.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _state.value = HomeFeedState.Loading
            try {
                val feed = getHomeFeedUseCase()
                _state.value = if (feed.visibleSections.isEmpty()) {
                    HomeFeedState.Empty
                } else {
                    HomeFeedState.Ready(feed)
                }
            } catch (e: Exception) {
                _state.value = HomeFeedState.Error(e.message ?: "Failed to load feed")
            }
        }
    }
}

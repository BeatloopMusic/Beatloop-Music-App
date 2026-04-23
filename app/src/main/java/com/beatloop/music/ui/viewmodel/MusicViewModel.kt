package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.domain.localrecommendation.RecommendationContext
import com.beatloop.music.domain.localrecommendation.RecommendationResult
import com.beatloop.music.localfirst.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class MusicViewModel @Inject constructor(
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val recommendationContext = MutableStateFlow(defaultContext())

    val recommendations: StateFlow<List<RecommendationResult>> = recommendationContext
        .flatMapLatest { context ->
            musicRepository.observeRecommendations(context = context)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onAppOpen(isWorkout: Boolean = false, isFocusMode: Boolean = false) {
        recommendationContext.value = defaultContext(
            isWorkout = isWorkout,
            isFocusMode = isFocusMode
        )
    }

    fun onSongCompletion(songId: String, listenDuration: Long) {
        if (songId.isBlank()) return
        viewModelScope.launch {
            musicRepository.recordSongCompletion(
                songId = songId,
                listenDuration = listenDuration
            )
        }
    }

    fun onSongSkip(songId: String, listenDuration: Long) {
        if (songId.isBlank()) return
        viewModelScope.launch {
            musicRepository.recordSongSkip(
                songId = songId,
                listenDuration = listenDuration
            )
        }
    }

    fun setWorkoutMode(enabled: Boolean) {
        recommendationContext.value = recommendationContext.value.copy(isWorkout = enabled)
    }

    fun setFocusMode(enabled: Boolean) {
        recommendationContext.value = recommendationContext.value.copy(isFocusMode = enabled)
    }

    fun refreshHourContext() {
        recommendationContext.value = recommendationContext.value.copy(hourOfDay = currentHour())
    }

    private fun defaultContext(
        isWorkout: Boolean = false,
        isFocusMode: Boolean = false
    ): RecommendationContext {
        return RecommendationContext(
            hourOfDay = currentHour(),
            isWorkout = isWorkout,
            isFocusMode = isFocusMode
        )
    }

    private fun currentHour(): Int {
        return Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    }
}

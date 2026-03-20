package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppEntryState(
    val onboardingCompleted: Boolean? = null
)

@HiltViewModel
class AppEntryViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {

    val state: StateFlow<AppEntryState> = preferencesManager.onboardingCompleted
        .map { completed ->
            AppEntryState(onboardingCompleted = completed)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppEntryState(onboardingCompleted = null)
        )
}

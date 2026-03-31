package com.beatloop.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.preferences.PreferencesManager
import com.beatloop.music.data.preferences.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AppThemeState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = true,
    val amoledBlackEnabled: Boolean = false
)

@HiltViewModel
class AppThemeViewModel @Inject constructor(
    preferencesManager: PreferencesManager
) : ViewModel() {

    val themeState: StateFlow<AppThemeState> = combine(
        preferencesManager.themeMode,
        preferencesManager.dynamicColorEnabled,
        preferencesManager.pureBlackEnabled
    ) { themeMode, dynamicColorEnabled, amoledBlackEnabled ->
        AppThemeState(
            themeMode = themeMode,
            dynamicColorEnabled = dynamicColorEnabled,
            amoledBlackEnabled = amoledBlackEnabled
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppThemeState()
    )
}

package com.beatloop.music.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.beatloop.music.data.preferences.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStep {
    Languages,
    Singers,
    Lyricists,
    Directors
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Languages,
    val selectedLanguages: Set<String> = emptySet(),
    val selectedSingers: Set<String> = emptySet(),
    val selectedLyricists: Set<String> = emptySet(),
    val selectedDirectors: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun toggleLanguage(language: String) {
        _uiState.update { state ->
            val updated = state.selectedLanguages.toMutableSet()
            if (!updated.add(language)) {
                updated.remove(language)
            }
            state.copy(
                selectedLanguages = updated,
                errorMessage = null,
                selectedSingers = state.selectedSingers.filterTo(mutableSetOf()) { singer ->
                    OnboardingCatalog.singersForLanguages(updated).any { it.name == singer }
                },
                selectedLyricists = state.selectedLyricists.filterTo(mutableSetOf()) { lyricist ->
                    OnboardingCatalog.lyricistsForLanguages(updated).any { it.name == lyricist }
                },
                selectedDirectors = state.selectedDirectors.filterTo(mutableSetOf()) { director ->
                    OnboardingCatalog.musicDirectorsForLanguages(updated).any { it.name == director }
                }
            )
        }
    }

    fun toggleSinger(name: String) {
        _uiState.update { state ->
            val updated = state.selectedSingers.toMutableSet()
            if (!updated.add(name)) {
                updated.remove(name)
            }
            state.copy(selectedSingers = updated, errorMessage = null)
        }
    }

    fun toggleLyricist(name: String) {
        _uiState.update { state ->
            val updated = state.selectedLyricists.toMutableSet()
            if (name == "None") {
                if (updated.contains("None")) {
                    updated.remove("None")
                } else {
                    updated.clear()
                    updated.add("None")
                }
            } else {
                updated.remove("None")
                if (!updated.add(name)) {
                    updated.remove(name)
                }
            }
            state.copy(selectedLyricists = updated, errorMessage = null)
        }
    }

    fun toggleDirector(name: String) {
        _uiState.update { state ->
            val updated = state.selectedDirectors.toMutableSet()
            if (!updated.add(name)) {
                updated.remove(name)
            }
            state.copy(selectedDirectors = updated, errorMessage = null)
        }
    }

    fun goBack() {
        _uiState.update { state ->
            val previous = when (state.step) {
                OnboardingStep.Languages -> OnboardingStep.Languages
                OnboardingStep.Singers -> OnboardingStep.Languages
                OnboardingStep.Lyricists -> OnboardingStep.Singers
                OnboardingStep.Directors -> OnboardingStep.Lyricists
            }
            state.copy(step = previous, errorMessage = null)
        }
    }

    fun proceed(onComplete: () -> Unit) {
        val state = _uiState.value
        when (state.step) {
            OnboardingStep.Languages -> {
                if (state.selectedLanguages.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Please select at least one language") }
                    return
                }
                _uiState.update { it.copy(step = OnboardingStep.Singers, errorMessage = null) }
            }

            OnboardingStep.Singers -> {
                if (state.selectedSingers.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Please select at least one singer") }
                    return
                }
                _uiState.update { it.copy(step = OnboardingStep.Lyricists, errorMessage = null) }
            }

            OnboardingStep.Lyricists -> {
                if (state.selectedLyricists.isEmpty()) {
                    _uiState.update {
                        it.copy(errorMessage = "Select at least one lyricist or choose None")
                    }
                    return
                }
                _uiState.update { it.copy(step = OnboardingStep.Directors, errorMessage = null) }
            }

            OnboardingStep.Directors -> {
                if (state.selectedDirectors.isEmpty()) {
                    _uiState.update { it.copy(errorMessage = "Please select at least one music director") }
                    return
                }
                savePreferences(onComplete)
            }
        }
    }

    private fun savePreferences(onComplete: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            preferencesManager.saveOnboardingPreferences(
                languages = state.selectedLanguages,
                singers = state.selectedSingers,
                lyricists = state.selectedLyricists,
                musicDirectors = state.selectedDirectors
            )
            _uiState.update { it.copy(isSaving = false) }
            onComplete()
        }
    }
}

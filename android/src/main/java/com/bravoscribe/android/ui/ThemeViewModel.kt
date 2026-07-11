package com.bravoscribe.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bravoscribe.android.data.local.datastore.ThemeDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeDataStore: ThemeDataStore,
) : ViewModel() {

    val isDark: StateFlow<Boolean> = themeDataStore.isDark
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun toggleTheme() {
        viewModelScope.launch { themeDataStore.setDark(!isDark.value) }
    }
}

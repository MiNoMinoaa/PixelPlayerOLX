package com.minoppol.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.data.update.AppUpdateChecker
import com.minoppol.music.data.update.AppUpdateInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateChecker: AppUpdateChecker,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private var checking = false

    fun checkForUpdates(currentVersion: String) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            _updateInfo.value = updateChecker.checkForUpdate(currentVersion)
        }
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }
}

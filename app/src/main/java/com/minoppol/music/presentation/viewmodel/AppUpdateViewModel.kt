package com.minoppol.music.presentation.viewmodel

import android.content.Context
import com.minoppol.music.R
import com.minoppol.music.data.update.AppUpdateChecker
import com.minoppol.music.data.update.AppUpdateInfo
import com.minoppol.music.data.update.UpdateCheckResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateChecker: AppUpdateChecker,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _showDialog = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showDialog: SharedFlow<Unit> = _showDialog.asSharedFlow()

    private var checking = false

    fun checkForUpdates(currentVersion: String) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(currentVersion)
            checking = false
            if (result is UpdateCheckResult.UpdateAvailable) {
                _updateInfo.value = result.info
            }
        }
    }

    fun checkForUpdatesManual(currentVersion: String) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            val result = updateChecker.checkForUpdate(currentVersion)
            checking = false
            when (result) {
                is UpdateCheckResult.UpdateAvailable -> {
                    _updateInfo.value = result.info
                    _showDialog.tryEmit(Unit)
                }
                is UpdateCheckResult.UpToDate -> _toast.tryEmit(context.getString(R.string.update_check_uptodate))
                is UpdateCheckResult.Ignored -> _toast.tryEmit(context.getString(R.string.update_check_ignored))
                is UpdateCheckResult.Error -> _toast.tryEmit(result.message)
            }
        }
    }

    fun ignoreVersion(version: String) {
        updateChecker.ignoreVersion(version)
        _updateInfo.value = null
    }

    fun dismissUpdate() {
        _updateInfo.value = null
    }
}

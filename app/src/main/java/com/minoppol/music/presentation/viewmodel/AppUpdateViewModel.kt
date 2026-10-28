package com.minoppol.music.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minoppol.music.R
import com.minoppol.music.data.preferences.UserPreferencesRepository
import com.minoppol.music.data.update.AppUpdateChecker
import com.minoppol.music.data.update.AppUpdateInfo
import com.minoppol.music.data.update.AppUpdateInstaller
import com.minoppol.music.data.update.UpdateCheckResult
import com.minoppol.music.data.update.UpdateInstallStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val updateChecker: AppUpdateChecker,
    private val updateInstaller: AppUpdateInstaller,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    val installStatus: StateFlow<UpdateInstallStatus> = updateInstaller.status

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    private val _showDialog = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showDialog: SharedFlow<Unit> = _showDialog.asSharedFlow()

    private var checking = false

    /**
     * The quiet startup check. Skipped entirely when the auto-check-updates setting is off;
     * a manual check from the about screen always runs.
     */
    fun checkForUpdates(currentVersion: String) {
        if (checking) return
        checking = true
        viewModelScope.launch {
            val enabled = userPreferencesRepository.autoCheckUpdatesFlow.first()
            if (enabled) {
                val result = updateChecker.checkForUpdate(currentVersion)
                if (result is UpdateCheckResult.UpdateAvailable) {
                    _updateInfo.value = result.info
                }
            }
            checking = false
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

    fun startInstall() {
        _updateInfo.value?.let { updateInstaller.startDownload(it) }
    }

    fun stopInstall() {
        updateInstaller.cancel()
    }

    fun resumeInstall() {
        updateInstaller.resumeInstall()
    }

    fun openInstallConfirmation() {
        updateInstaller.openPendingConfirmation()
    }

    fun dismissInstallError() {
        updateInstaller.dismiss()
    }

    fun canRequestPackageInstalls(): Boolean = updateInstaller.canRequestPackageInstalls()
}

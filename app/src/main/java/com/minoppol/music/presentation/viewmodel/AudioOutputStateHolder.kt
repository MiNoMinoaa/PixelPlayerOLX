package com.minoppol.music.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.minoppol.music.R
import com.minoppol.music.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Kind of local audio output, used for icons and labels in the output picker. */
enum class AudioOutputKind { PhoneSpeaker, Wired, Usb, Bluetooth, HearingAid, Other }

data class AudioOutputDevice(
    val id: Int,
    val name: String,
    val kind: AudioOutputKind,
    val isActive: Boolean,
)

@HiltViewModel
class AudioOutputStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val engine: DualPlayerEngine,
) : ViewModel() {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _outputs = MutableStateFlow<List<AudioOutputDevice>>(emptyList())
    val outputs: StateFlow<List<AudioOutputDevice>> = _outputs.asStateFlow()

    private var preferredDeviceId: Int? = null
    private var pickerActive = false
    private var callbackRegistered = false

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (removedDevices?.any { it.id == preferredDeviceId } == true) {
                clearPreferredDevice()
            }
            refresh()
        }
    }

    fun setPickerActive(active: Boolean) {
        pickerActive = active
        updateDeviceCallback()
        refresh()
    }

    fun selectOutput(deviceId: Int) {
        val device = mediaOutputs().firstOrNull { it.id == deviceId } ?: return
        val systemRoute = systemMediaRoute()
        if (systemRoute != null && sameEndpoint(systemRoute, device)) {
            clearPreferredDevice()
        } else {
            preferredDeviceId = device.id
            engine.setPreferredAudioDevice(device)
            updateDeviceCallback()
        }
        refresh()
    }

    private fun refresh() {
        val devices = mediaOutputs()
        val preferred = devices.firstOrNull { it.id == preferredDeviceId }
        if (preferredDeviceId != null && preferred == null) {
            clearPreferredDevice()
        }
        val active = preferred
            ?: systemMediaRoute()?.let { route -> devices.firstOrNull { sameEndpoint(it, route) } }
            ?: devices.maxByOrNull { it.routePriority() }
        _outputs.value = devices
            .distinctBy { endpointKey(it) }
            .sortedWith(
                compareByDescending<AudioDeviceInfo> { it.routePriority() }
                    .thenBy { it.productName.toString() }
            )
            .map { device ->
                AudioOutputDevice(
                    id = device.id,
                    name = displayName(device),
                    kind = device.kind(),
                    isActive = active != null && sameEndpoint(device, active),
                )
            }
    }

    private fun clearPreferredDevice() {
        if (preferredDeviceId == null) return
        preferredDeviceId = null
        engine.setPreferredAudioDevice(null)
        updateDeviceCallback()
    }

    private fun updateDeviceCallback() {
        val shouldListen = pickerActive || preferredDeviceId != null
        if (shouldListen && !callbackRegistered) {
            audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
            callbackRegistered = true
        } else if (!shouldListen && callbackRegistered) {
            runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
            callbackRegistered = false
        }
    }

    private fun mediaOutputs(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink && it.type in MEDIA_OUTPUT_TYPES }

    private fun systemMediaRoute(): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val route = runCatching { audioManager.getAudioDevicesForAttributes(attributes) }
            .getOrNull()
            ?.firstOrNull()
            ?: return null
        return mediaOutputs().firstOrNull { it.type == route.type && it.address == route.address }
    }

    private fun displayName(device: AudioDeviceInfo): String = when (device.kind()) {
        AudioOutputKind.PhoneSpeaker -> context.getString(R.string.player_audio_output_this_phone)
        AudioOutputKind.Wired -> context.getString(R.string.player_audio_output_wired)
        else -> device.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: context.getString(R.string.player_audio_output_unknown_device)
    }

    private fun sameEndpoint(a: AudioDeviceInfo, b: AudioDeviceInfo): Boolean = endpointKey(a) == endpointKey(b)

    private fun endpointKey(device: AudioDeviceInfo): String =
        device.address?.takeIf { it.isNotBlank() }?.let { "${device.kind()}:$it" }
            ?: "${device.type}:${device.productName}"

    private fun AudioDeviceInfo.kind(): AudioOutputKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioOutputKind.PhoneSpeaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioOutputKind.Wired
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_LINE_DIGITAL,
        AudioDeviceInfo.TYPE_DOCK -> AudioOutputKind.Usb
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioOutputKind.Bluetooth
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputKind.HearingAid
        else -> AudioOutputKind.Other
    }

    private fun AudioDeviceInfo.routePriority(): Int = when (kind()) {
        AudioOutputKind.Bluetooth, AudioOutputKind.HearingAid -> 4
        AudioOutputKind.Usb -> 3
        AudioOutputKind.Wired -> 2
        AudioOutputKind.Other -> 1
        AudioOutputKind.PhoneSpeaker -> 0
    }

    private companion object {
        val MEDIA_OUTPUT_TYPES = setOf(
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_DOCK,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}

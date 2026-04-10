package com.autoclicker.claude.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class TapCommand {
    data class StartProfile(val profile: TapProfile) : TapCommand()
    data class QuickStart(
        val points: List<ClickPoint>,
        val settings: DefaultSettings,
        val mode: ClickMode
    ) : TapCommand()
    data object Stop : TapCommand()
    data object Pause : TapCommand()
    data object Resume : TapCommand()
    data class EnterPickMode(val multiPick: Boolean = false) : TapCommand()
    data object ExitPickMode : TapCommand()
    data object EnterRecordMode : TapCommand()
    data object ShowProfilePicker : TapCommand()
}

data class PickResult(val x: Float, val y: Float)

object CommandBus {
    private val _commands = MutableSharedFlow<TapCommand>(extraBufferCapacity = 10)
    val commands = _commands.asSharedFlow()

    private val _pickResults = MutableSharedFlow<PickResult>(extraBufferCapacity = 10)
    val pickResults = _pickResults.asSharedFlow()

    private val _recordingResults = MutableSharedFlow<List<ClickPoint>>(extraBufferCapacity = 5)
    val recordingResults = _recordingResults.asSharedFlow()

    private val _runState = MutableStateFlow(RunState.IDLE)
    val runState: StateFlow<RunState> = _runState.asStateFlow()

    private val _stats = MutableStateFlow(ExecutionStats())
    val stats: StateFlow<ExecutionStats> = _stats.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected: StateFlow<Boolean> = _serviceConnected.asStateFlow()

    private val _pickModeActive = MutableStateFlow(false)
    val pickModeActive: StateFlow<Boolean> = _pickModeActive.asStateFlow()

    private val _volumeTriggerEnabled = MutableStateFlow(false)
    val volumeTriggerEnabled: StateFlow<Boolean> = _volumeTriggerEnabled.asStateFlow()

    private val _bubbleEnabled = MutableStateFlow(false)
    val bubbleEnabled: StateFlow<Boolean> = _bubbleEnabled.asStateFlow()

    private val _lastProfile = MutableStateFlow<TapProfile?>(null)
    val lastProfile: StateFlow<TapProfile?> = _lastProfile.asStateFlow()

    private val _liveIntervalOverride = MutableStateFlow(0L)
    val liveIntervalOverride: StateFlow<Long> = _liveIntervalOverride.asStateFlow()

    private val _defaultSettings = MutableStateFlow(DefaultSettings())
    val defaultSettingsState: StateFlow<DefaultSettings> = _defaultSettings.asStateFlow()

    private val _uiActive = MutableStateFlow(false)
    val uiActive: StateFlow<Boolean> = _uiActive.asStateFlow()

    fun send(command: TapCommand) { _commands.tryEmit(command) }
    fun emitPickResult(x: Float, y: Float) { _pickResults.tryEmit(PickResult(x, y)) }
    fun emitRecordingResult(steps: List<ClickPoint>) { _recordingResults.tryEmit(steps) }
    fun updateStats(stats: ExecutionStats) { _stats.value = stats }
    fun setServiceConnected(value: Boolean) { _serviceConnected.value = value }
    fun setPickModeActive(value: Boolean) { _pickModeActive.value = value }
    fun setVolumeTriggerEnabled(value: Boolean) { _volumeTriggerEnabled.value = value }
    fun setBubbleEnabled(value: Boolean) { _bubbleEnabled.value = value }
    fun setLastProfile(profile: TapProfile?) { _lastProfile.value = profile }
    fun updateInterval(ms: Long) { _liveIntervalOverride.value = ms }
    fun setDefaultSettings(settings: DefaultSettings) { _defaultSettings.value = settings }
    fun setUiActive(active: Boolean) { _uiActive.value = active }

    fun setRunState(state: RunState) {
        _runState.value = state
        if (state == RunState.IDLE) _liveIntervalOverride.value = 0L // reset override on stop
    }
}

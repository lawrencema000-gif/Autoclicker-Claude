package com.autoclicker.claude.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.autoclicker.claude.data.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ProfileRepository(app)

    val profiles = repo.profiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val defaultSettings = repo.defaultSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultSettings())
    val onboardingComplete = repo.onboardingComplete.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val homeTipSeen = repo.homeTipSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val bubbleTipSeen = repo.bubbleTipSeen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissHomeTip() { viewModelScope.launch { repo.setHomeTipSeen() } }
    fun dismissBubbleTip() { viewModelScope.launch { repo.setBubbleTipSeen() } }
    fun clearHistory() {
        _history.value = emptyList()
        viewModelScope.launch { repo.clearHistory() }
    }

    val runState = CommandBus.runState
    val stats = CommandBus.stats
    val serviceConnected = CommandBus.serviceConnected
    val pickModeActive = CommandBus.pickModeActive

    private val _selectedMode = MutableStateFlow(ClickMode.SINGLE_POINT)
    val selectedMode: StateFlow<ClickMode> = _selectedMode.asStateFlow()

    private val _quickStartPoints = MutableStateFlow<List<ClickPoint>>(emptyList())
    val quickStartPoints: StateFlow<List<ClickPoint>> = _quickStartPoints.asStateFlow()

    private val _editingProfile = MutableStateFlow<TapProfile?>(null)
    val editingProfile: StateFlow<TapProfile?> = _editingProfile.asStateFlow()

    // True while a re-pick was initiated from the profile editor, so pick results
    // route into the edited profile's steps instead of the quick-start flow.
    private var editPickActive = false

    private val _patternConfig = MutableStateFlow(PatternConfig())
    val patternConfig: StateFlow<PatternConfig> = _patternConfig.asStateFlow()

    private val _customPatternPoints = MutableStateFlow<List<ClickPoint>>(emptyList())
    val customPatternPoints: StateFlow<List<ClickPoint>> = _customPatternPoints.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    // The most recently finished run, shown as a "Finished: N taps" card on Home
    // until the user starts another run or dismisses it.
    private val _justFinishedRun = MutableStateFlow<HistoryEntry?>(null)
    val justFinishedRun: StateFlow<HistoryEntry?> = _justFinishedRun.asStateFlow()
    fun dismissJustFinished() { _justFinishedRun.value = null }

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        CommandBus.setUiActive(true)

        // Restore session state from DataStore
        viewModelScope.launch {
            repo.getSessionMode().first().let { _selectedMode.value = it }
            repo.getSessionPatternConfig().first().let { _patternConfig.value = it }
            repo.getSessionCustomPoints().first().let { _customPatternPoints.value = it }
            repo.getLastProfile().first()?.let { CommandBus.setLastProfile(it) }
            // Restore Quick Controls toggles so they survive app restart.
            CommandBus.setBubbleEnabled(repo.bubbleEnabled.first())
            CommandBus.setVolumeTriggerEnabled(repo.volumeTriggerEnabled.first())
            CommandBus.setPauseOnTouchEnabled(repo.pauseOnTouchEnabled.first())
        }

        // Persist Quick Controls toggles whenever they change.
        viewModelScope.launch { CommandBus.bubbleEnabled.collect { repo.setBubbleEnabled(it) } }
        viewModelScope.launch { CommandBus.volumeTriggerEnabled.collect { repo.setVolumeTriggerEnabled(it) } }
        viewModelScope.launch { CommandBus.pauseOnTouchEnabled.collect { repo.setPauseOnTouchEnabled(it) } }

        // Auto-save session state with debounce
        viewModelScope.launch {
            combine(_selectedMode, _patternConfig, _customPatternPoints) { mode, config, points ->
                Triple(mode, config, points)
            }.debounce(500).collect { (mode, config, points) ->
                repo.saveSessionState(mode, config, points)
            }
        }

        // Push default settings to CommandBus so service has them
        viewModelScope.launch {
            defaultSettings.collect { settings ->
                CommandBus.setDefaultSettings(settings)
            }
        }

        // Auto-complete onboarding when service connects
        viewModelScope.launch {
            serviceConnected.collect { connected ->
                if (connected && !onboardingComplete.value) {
                    repo.setOnboardingComplete(true)
                }
            }
        }

        // Collect pick results for quick start, custom pattern, or editor re-pick
        viewModelScope.launch {
            CommandBus.pickResults.collect { result ->
                when {
                    editPickActive && _editingProfile.value != null -> {
                        // Re-pick initiated from the editor: append to the edited profile.
                        val editing = _editingProfile.value ?: return@collect
                        val point = ClickPoint(
                            x = result.x,
                            y = result.y,
                            delayBefore = defaultSettings.value.intervalMs,
                            holdDuration = defaultSettings.value.holdDurationMs,
                            order = editing.steps.size
                        )
                        _editingProfile.value = editing.copy(steps = editing.steps + point)
                    }
                    _selectedMode.value == ClickMode.PATTERN_MODE &&
                        _patternConfig.value.type == PatternType.CUSTOM -> {
                        addCustomPatternPoint(ClickPoint(x = result.x, y = result.y))
                    }
                    else -> {
                        val point = ClickPoint(
                            x = result.x,
                            y = result.y,
                            delayBefore = defaultSettings.value.intervalMs,
                            holdDuration = defaultSettings.value.holdDurationMs
                        )
                        _quickStartPoints.value = _quickStartPoints.value + point
                    }
                }
            }
        }

        // Pick-edit collector: handle remove-at-index and clear-all from the
        // pick overlay so the in-flight quickStart / custom-pattern lists stay
        // in sync with what the user sees on screen.
        viewModelScope.launch {
            CommandBus.pickEdits.collect { edit ->
                val isEditPick = editPickActive && _editingProfile.value != null
                val isCustomPattern = _selectedMode.value == ClickMode.PATTERN_MODE &&
                    _patternConfig.value.type == PatternType.CUSTOM
                when (edit) {
                    is PickEdit.Remove -> when {
                        isEditPick -> {
                            val editing = _editingProfile.value ?: return@collect
                            if (edit.index in editing.steps.indices) {
                                val updated = editing.steps.toMutableList().apply { removeAt(edit.index) }
                                _editingProfile.value = editing.copy(steps = updated.mapIndexed { i, p -> p.copy(order = i) })
                            }
                        }
                        isCustomPattern -> {
                            val current = _customPatternPoints.value
                            if (edit.index in current.indices) {
                                val updated = current.toMutableList().apply { removeAt(edit.index) }
                                _customPatternPoints.value = updated.mapIndexed { i, p -> p.copy(order = i) }
                            }
                        }
                        else -> {
                            val current = _quickStartPoints.value
                            if (edit.index in current.indices) {
                                _quickStartPoints.value = current.toMutableList().apply { removeAt(edit.index) }
                            }
                        }
                    }
                    PickEdit.ClearAll -> when {
                        isEditPick -> _editingProfile.value = _editingProfile.value?.copy(steps = emptyList())
                        isCustomPattern -> _customPatternPoints.value = emptyList()
                        else -> _quickStartPoints.value = emptyList()
                    }
                }
            }
        }

        // Handle gesture recording results
        viewModelScope.launch {
            CommandBus.recordingResults.collect { steps ->
                if (steps.isNotEmpty()) {
                    val profile = TapProfile(
                        name = "Recorded ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                        mode = ClickMode.MULTI_POINT,
                        steps = steps,
                        intervalMs = defaultSettings.value.intervalMs,
                        antiDetection = defaultSettings.value.antiDetection
                    )
                    repo.addProfile(profile)
                    CommandBus.send(TapCommand.StartProfile(profile))
                }
            }
        }

        // Restore persisted history
        viewModelScope.launch {
            _history.value = repo.history.first()
        }

        // Log to history when a session ends (covers RUNNING→IDLE and PAUSED→IDLE)
        viewModelScope.launch {
            var prevState = RunState.IDLE
            runState.collect { state ->
                if (prevState != RunState.IDLE && state == RunState.IDLE) {
                    val s = stats.value
                    if (s.totalTaps > 0) {
                        val entry = HistoryEntry(s.profileName.ifBlank { "Quick Session" }, s.totalTaps, s.elapsedMs)
                        val updated = (listOf(entry) + _history.value).take(50)
                        _history.value = updated
                        repo.saveHistory(updated)
                        _justFinishedRun.value = entry
                    }
                }
                if (state == RunState.RUNNING) _justFinishedRun.value = null
                prevState = state
            }
        }

        // When pick mode ends, start quick session if points exist
        viewModelScope.launch {
            pickModeActive.collect { active ->
                if (!active && editPickActive) {
                    // Editor re-pick just finished; do NOT auto-start or auto-save
                    // a quick session — the points already went into editingProfile.
                    editPickActive = false
                    return@collect
                }
                if (!active && _quickStartPoints.value.isNotEmpty()) {
                    val points = _quickStartPoints.value
                    val settings = defaultSettings.value
                    val mode = _selectedMode.value

                    CommandBus.send(TapCommand.QuickStart(points, settings, mode))

                    // Auto-save as profile with anti-detection settings
                    val profile = TapProfile(
                        name = "Quick ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                        mode = mode,
                        steps = points,
                        intervalMs = settings.intervalMs,
                        antiDetection = settings.antiDetection
                    )
                    repo.addProfile(profile)

                    _quickStartPoints.value = emptyList()
                }
            }
        }
    }

    fun setSelectedMode(mode: ClickMode) {
        _selectedMode.value = mode
    }

    fun setPatternType(type: PatternType) {
        _patternConfig.value = _patternConfig.value.copy(type = type)
    }

    fun updatePatternConfig(config: PatternConfig) {
        _patternConfig.value = config
    }

    fun addCustomPatternPoint(point: ClickPoint) {
        val current = _customPatternPoints.value
        _customPatternPoints.value = current + point.copy(order = current.size)
    }

    fun removeCustomPatternPoint(pointId: String) {
        val current = _customPatternPoints.value.filter { it.id != pointId }
        _customPatternPoints.value = current.mapIndexed { i, p -> p.copy(order = i) }
    }

    fun reorderCustomPatternPoint(fromIndex: Int, toIndex: Int) {
        val current = _customPatternPoints.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _customPatternPoints.value = current.mapIndexed { i, p -> p.copy(order = i) }
        }
    }

    fun clearCustomPatternPoints() {
        _customPatternPoints.value = emptyList()
    }

    fun quickStart() {
        val mode = _selectedMode.value

        if (mode == ClickMode.RECORD_MODE) {
            CommandBus.send(TapCommand.EnterRecordMode)
            return
        }

        if (mode == ClickMode.PATTERN_MODE) {
            val config = _patternConfig.value

            // Custom pattern: enter pick mode if no points yet
            if (config.type == PatternType.CUSTOM && _customPatternPoints.value.isEmpty()) {
                _quickStartPoints.value = emptyList()
                CommandBus.send(TapCommand.EnterPickMode(true))
                return
            }

            // Inject custom points into config if custom type
            val effectiveConfig = if (config.type == PatternType.CUSTOM) {
                config.copy(customPoints = _customPatternPoints.value)
            } else config

            val settings = defaultSettings.value
            val profile = TapProfile(
                name = "Pattern ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                mode = ClickMode.PATTERN_MODE,
                steps = emptyList(),
                intervalMs = settings.intervalMs,
                patternConfig = effectiveConfig,
                antiDetection = settings.antiDetection,
                rules = ClickRule(
                    maxTaps = if (settings.stopCondition == StopCondition.AFTER_TAPS) settings.stopValue else 0,
                    maxDurationMs = if (settings.stopCondition == StopCondition.AFTER_SECONDS) settings.stopValue * 1000L else 0
                )
            )
            CommandBus.send(TapCommand.StartProfile(profile))
            viewModelScope.launch { repo.addProfile(profile) }
            return
        }

        _quickStartPoints.value = emptyList()
        val multi = mode == ClickMode.MULTI_POINT
        CommandBus.send(TapCommand.EnterPickMode(multi))
    }

    fun stopExecution() {
        CommandBus.send(TapCommand.Stop)
        _customPatternPoints.value = emptyList()
    }

    fun startProfile(profile: TapProfile) {
        viewModelScope.launch {
            repo.setLastProfileId(profile.id)
            repo.saveLastProfile(profile)
        }
        CommandBus.send(TapCommand.StartProfile(profile))
    }

    fun completeOnboarding() {
        viewModelScope.launch { repo.setOnboardingComplete(true) }
    }

    // Profile editing
    fun editProfile(profile: TapProfile) {
        _editingProfile.value = profile
    }

    fun cancelEditing() {
        _editingProfile.value = null
    }

    fun updateEditingName(name: String) {
        _editingProfile.value = _editingProfile.value?.copy(name = name.take(50))
    }

    fun updateEditingInterval(interval: Long) {
        _editingProfile.value = _editingProfile.value?.copy(intervalMs = interval.coerceAtLeast(1L))
    }

    fun updateEditingLoopCount(count: Int) {
        _editingProfile.value = _editingProfile.value?.copy(loopCount = count.coerceAtLeast(0))
    }

    fun addStepToEditing(step: ClickPoint) {
        val current = _editingProfile.value ?: return
        _editingProfile.value = current.copy(steps = current.steps + step)
    }

    fun removeStepFromEditing(stepId: String) {
        val current = _editingProfile.value ?: return
        _editingProfile.value = current.copy(steps = current.steps.filter { it.id != stepId })
    }

    fun updateStepInEditing(updated: ClickPoint) {
        val current = _editingProfile.value ?: return
        _editingProfile.value = current.copy(
            steps = current.steps.map { if (it.id == updated.id) updated else it }
        )
    }

    fun rePickEditingPoints() {
        val current = _editingProfile.value ?: return
        _editingProfile.value = current.copy(steps = emptyList())
        editPickActive = true
        // Multi-pick so the user can place several points and press DONE; single
        // mode still works (one point → they press DONE).
        CommandBus.send(TapCommand.EnterPickMode(true))
    }

    fun saveEditingProfile() {
        val profile = _editingProfile.value ?: return
        viewModelScope.launch {
            if (profiles.value.any { it.id == profile.id }) {
                repo.updateProfile(profile)
            } else {
                repo.addProfile(profile)
            }
            com.autoclicker.claude.service.ScheduleManager.reschedule(getApplication(), profile)
            _editingProfile.value = null
        }
    }

    fun setProfileSchedule(profile: TapProfile, schedule: Schedule?) {
        viewModelScope.launch {
            val updated = profile.copy(schedule = schedule)
            repo.updateProfile(updated)
            com.autoclicker.claude.service.ScheduleManager.reschedule(getApplication(), updated)
        }
    }

    // Profile management
    fun deleteProfile(id: String) {
        viewModelScope.launch {
            com.autoclicker.claude.service.ScheduleManager.cancelFor(getApplication(), id)
            repo.deleteProfile(id)
        }
    }

    fun duplicateProfile(id: String) {
        viewModelScope.launch { repo.duplicateProfile(id) }
    }

    fun saveDefaultSettings(settings: DefaultSettings) {
        viewModelScope.launch { repo.saveDefaultSettings(settings) }
    }

    fun exportProfile(profile: TapProfile): String = repo.exportProfileJson(profile)

    fun importProfile(json: String, onResult: (success: Boolean, error: String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _isImporting.value = true
            try {
                repo.importProfileJson(json)
                onResult(true, null)
            } catch (e: IllegalArgumentException) {
                onResult(false, e.message)
            } catch (e: Exception) {
                onResult(false, "Couldn't read this file. Try re-exporting the script.")
            } finally {
                _isImporting.value = false
            }
        }
    }
}

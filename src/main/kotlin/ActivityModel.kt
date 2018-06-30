
import java.util.*

class ActionModel(originalSettings: Settings, val state: ActivityState) {
    private val listeners = HashMap<Any, Listener>()
    private var settings = originalSettings.copy()
    private var updatedSettings = settings

    init {
        originalSettings.addChangeListener(object : Settings.ChangeListener {
            override fun onChange(newSettings: Settings) {
                updatedSettings = newSettings
            }
        })
        state.progress = progressMax
    }

    fun onIdeStartup(time: Time) = state.apply {
        if (mode != ActivityState.Mode.LogStopped) {
            val shouldPostData = Duration.between(lastUpdateTime, time) > settings.interval
            if (shouldPostData) {
                mode = ActivityState.Mode.LogStopped
                lastMode = ActivityState.Mode.LogStopped
                startTime = Time.zero
                progress = Duration.zero
            } else {
                progress = progressSince(time)
            }
        }
    }

    fun onUserSwitchToNextState(time: Time) = state.apply {
        onTimer(time)
        settings = updatedSettings
        var wasManuallyStopped = false
        when (mode) {
            ActivityState.Mode.LogInProgress -> {
                mode = ActivityState.Mode.LogStopped
                progress = progressMax
                wasManuallyStopped = true
//                if (pomodorosTillLongBreak == 0) {
//                    pomodorosTillLongBreak = settings.longBreakFrequency
//                }
            }
            ActivityState.Mode.LogStopped -> {
                mode = ActivityState.Mode.LogInProgress
                startTime = time
            }
        }
        onTimer(time, wasManuallyStopped)
    }

    fun onTimer(time: Time, wasManuallyStopped: Boolean = false) = state.apply {
        when (mode) {
            ActivityState.Mode.LogInProgress -> {
                progress = progressSince(time)
                if (time >= startTime + progressMax) {
                    mode = ActivityState.Mode.LogStopped
                    settings = updatedSettings
                    startTime = time
                    progress = progressSince(time)
                    pomodorosAmount++
                }
            }
            ActivityState.Mode.LogStopped -> {
                progress = progressSince(time)
                if (time >= startTime + progressMax) {
                    settings = updatedSettings
                    if (settings.startNewPomodoroAfterBreak) {
                        mode = ActivityState.Mode.LogInProgress
                        startTime = time
                    } else {
                        mode = ActivityState.Mode.LogStopped
                    }
                    progress = progressMax
                }
            }
        }

        listeners.values.forEach { it.onStateChange(this, wasManuallyStopped) }

        lastMode = mode
        lastUpdateTime = time
    }

    val progressMax: Duration
        get() = when (state.mode) {
            ActivityState.Mode.LogInProgress -> settings.interval
            ActivityState.Mode.LogStopped -> Duration.zero
        }

    val timeLeft: Duration
        get() = progressMax - state.progress

    fun resetPomodoros() {
        state.pomodorosAmount = 0
    }

    fun addListener(key: Any, listener: Listener) {
        listeners.put(key, listener)
    }

    fun removeListener(key: Any) {
        listeners.remove(key)
    }

    private fun progressSince(time: Time): Duration =
        Duration.between(state.startTime, time).capAt(progressMax)

    interface Listener {
        fun onStateChange(state: ActivityState, wasManuallyStopped: Boolean)
    }
}

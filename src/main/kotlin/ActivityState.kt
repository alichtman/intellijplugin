
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient

/**
 * Class for persisting activity state.
 * It is not part of [Settings] class because instances of this class cannot be directly changed by user.
 */
@State(name = "ActivityState", storages = arrayOf(Storage(file = "CS125Activity.state.xml")))
data class ActivityState(
        @Transient var mode: Mode = Mode.LogStopped,
        @OptionTag(nameAttribute = "lastState", converter = ModeConverter::class) var lastMode: Mode = Mode.LogStopped,
        @OptionTag(nameAttribute = "startTime", converter = TimeConverter::class) var startTime: Time = Time.zero,
        @OptionTag(nameAttribute = "lastUpdateTime", converter = TimeConverter::class) var lastUpdateTime: Time = Time.zero,
        @OptionTag(nameAttribute = "pomodorosAmount") var pomodorosAmount: Int = 0,
        @Transient var progress: Duration = Duration.zero,
        var documentSaveActionCount: Int = 0,
        var documentModificationActionCount: Int = 0,
        var mousePressActionCount: Int = 0,
        var visibleContentsChangedCount: Int = 0,
        var projectOpenCount: Int = 0,
        var projectCloseCount: Int = 0,
        var fileSwitchCount: Int = 0

) : PersistentStateComponent<ActivityState> {

    override fun getState() = this

    override fun loadState(persistence: ActivityState) = XmlSerializerUtil.copyBean(persistence, this)

    enum class Mode {
        /** A in progress. */
        LogInProgress,
        /** Pomodoro timer was not started or was stopped during pomodoro or break. */
        LogStopped
    }

    private class TimeConverter : Converter<Time>() {
        override fun toString(mode: Time) = mode.epochMilli.toString()
        override fun fromString(value: String) = Time(epochMilli = value.toLong())
    }

    private class ModeConverter : Converter<Mode>() {
        override fun toString(mode: Mode) = mode.name.toUpperCase()
        override fun fromString(value: String) = when (value.toUpperCase()) {
            "RUN" -> Mode.LogInProgress
            "STOP" -> Mode.LogStopped
            else -> error("Unknown mode: '$value'")
        }
    }
}

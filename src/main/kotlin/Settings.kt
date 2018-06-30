
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.util.*

@State(name = "IntellijLoggerSettings", storages = arrayOf(Storage("intellij-logger.settings.xml")))
data class Settings(
        @OptionTag(nameAttribute = "SessionLengthInMinutes", converter = MinutesConverter::class)
        var interval: Duration = defaultDataPostingInterval,
        @OptionTag(nameAttribute = "breakLengthInMinutes", converter = MinutesConverter::class)
        var durationPassed: Duration = timePassedSinceLastActive,
        @OptionTag(converter = MinutesConverter::class)
        var startNewPomodoroAfterBreak: Boolean = false
) : PersistentStateComponent<Settings> {
    /**
     * If IntelliJ shuts down during pomodoro and then restarts, logging can be continued.
     * This property determines how much time can pass before we consider logging session to be expired.
     * @return timeout in milliseconds
     */
    private val changeListeners = ArrayList<ChangeListener>()

    fun addChangeListener(changeListener: ChangeListener) {
        changeListeners.add(changeListener)
    }

    fun removeChangeListener(changeListener: ChangeListener) {
        changeListeners.remove(changeListener)
    }

    override fun getState() = this

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
        for (changeListener in changeListeners) {
            changeListener.onChange(this.copy())
        }
    }

    class MinutesConverter : Converter<Duration>() {
        override fun toString(mode: Duration) = mode.minutes.toString()
        override fun fromString(value: String) = Duration(minutes = value.toInt())
    }

    interface ChangeListener {
        fun onChange(newSettings: Settings)
    }

    companion object {
        val defaultDataPostingInterval = Duration(minutes = 2)
        val timePassedSinceLastActive = Duration(minutes = -1)

        val instance: Settings
            get() = ServiceManager.getService(Settings::class.java)
    }
}

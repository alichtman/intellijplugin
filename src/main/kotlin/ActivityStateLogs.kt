
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import java.io.Serializable
/**
 * Class for persisting activity state.
 *
 */
@State(name = "ActivityStateLogs", storages = arrayOf(Storage(file = "CS125ActivityStateLogs.state.xml")))
data class ActivityStateLogs(
        @OptionTag(nameAttribute = "username") var username: String = "DEFAULT",
        @OptionTag(nameAttribute = "activityLogs") var activityLogs: ArrayList<ActivityCounter> = ArrayList()
) : PersistentStateComponent<ActivityStateLogs>, Serializable {

    init {
        println("CONSTR PERSISTENT STATE")
    }

    override fun getState(): ActivityStateLogs {
        println("GET PERSISTENT STATE")
        return this
    }

    override fun loadState(persistence: ActivityStateLogs) {
        println("LOAD PERSISTENT STATE")
        XmlSerializerUtil.copyBean(persistence, this)
    }
}


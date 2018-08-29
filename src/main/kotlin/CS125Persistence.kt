import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger

@State(
        name = "CS125Component",
        storages = [(Storage(file = "CS125Component.xml"))]
)
class CS125Persistence : PersistentStateComponent<CS125Persistence.State> {
    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    class State {
        var savedCounters = mutableListOf<CS125Component.Counter>()
        var counterIndex = 0L
    }
    var persistentState = State()
    override fun getState() : State {
        return persistentState
    }
    override fun loadState(state: State) {
        persistentState = state
    }
    companion object {
        fun getInstance(): CS125Persistence {
            return ServiceManager.getService(CS125Persistence::class.java)
        }
    }
}
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.concurrent.timer

@State(
        name = "CS125Component",
        storages = [(Storage(file = "CS125Component.xml"))]
)
class CS125Component :
        ApplicationComponent,
        TypedHandlerDelegate(),
        CaretListener,
        VisibleAreaListener,
        EditorMouseListener,
        SelectionListener,
        ProjectManagerListener,
        PersistentStateComponent<CS125Component.State> {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")
    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    data class Counter(
            var start: Date? = Date(),
            var end: Date? = null,
            var keystrokeCount: Int = 0,
            var caretPositionChangedCount: Int = 0,
            var visibleAreaChangedCount: Int = 0,
            var mousePressedCount: Int = 0,
            var selectionChangedCount: Int = 0
    )
    fun totalCount(counter: Counter): Int {
        return counter.keystrokeCount +
                counter.caretPositionChangedCount +
                counter.visibleAreaChangedCount +
                counter.mousePressedCount +
                counter.visibleAreaChangedCount;
    }

    var currentProjectCounters = mutableMapOf<Project, Counter>()
    var projectMPs = mutableMapOf<Project, String>()

    class State {
        var savedCounters = mutableListOf<Counter>()
    }
    var persistentState = State()
    override fun getState() : State {
        log.info("getState")
        return persistentState
    }
    override fun loadState(state: State) {
        log.info("loadState")
        persistentState = state
    }

    override fun initComponent() {
        log.info("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this);

        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
        }
    }

    override fun disposeComponent() {
        log.info("disposeComponent")
        for ((_, counter) in currentProjectCounters) {
            if (totalCount(counter) > 0) {
                state.savedCounters.add(counter)
            }
        }
    }

    fun rotateCounters() {
        val end = Date()
        for ((project, counter) in currentProjectCounters) {
            if (totalCount(counter) == 0) {
                continue
            }
            counter.end = end
            log.info(counter.toString())
            state.savedCounters.add(counter)
            currentProjectCounters[project] = Counter()
        }
    }

    val STATE_TIMER_PERIOD = 1000L
    private var stateTimer: Timer? = null

    override fun projectOpened(project: Project) {
        log.info("projectOpened")

        val gradeConfigurationFile = File(project.baseDir.path).resolve(File("config/grade.yaml"))
        log.info(gradeConfigurationFile.toString())
        if (!gradeConfigurationFile.exists()) {
            return
        }

        val gradeConfiguration = Yaml().load(Files.newBufferedReader(gradeConfigurationFile.toPath())) as Map<String, String>
        val MPname = gradeConfiguration.get("name")
        if (MPname == null) {
            return
        }
        projectMPs[project] = MPname

        currentProjectCounters[project] = Counter()

        if (currentProjectCounters.size == 1) {
            stateTimer?.cancel();
            stateTimer = timer("edu.illinois.cs.cs125", true,
                    STATE_TIMER_PERIOD, STATE_TIMER_PERIOD, {
                rotateCounters()
            })
        }
    }

    override fun projectClosed(project: Project) {
        log.info("projectClosed")

        if (!(currentProjectCounters.containsKey(project))) {
            return
        }
        currentProjectCounters.remove(project)
        if (currentProjectCounters.size == 0) {
            stateTimer?.cancel();
        }
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        log.info("charTyped")
        currentProjectCounters[project]!!.keystrokeCount++
        return Result.CONTINUE
    }

    override fun caretPositionChanged(caretEvent: CaretEvent) {
        log.info("caretPositionChanged")
        currentProjectCounters[caretEvent.editor.project]!!.caretPositionChangedCount++
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        log.info("visibleAreaChanged")
        currentProjectCounters[visibleAreaEvent.editor.project]!!.visibleAreaChangedCount++
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        log.info("mousePressed")
        currentProjectCounters[editorMouseEvent.editor.project]!!.mousePressedCount++
    }
    override fun mouseClicked(e: EditorMouseEvent) {}
    override fun mouseReleased(e: EditorMouseEvent) {}
    override fun mouseEntered(e: EditorMouseEvent) {}
    override fun mouseExited(e: EditorMouseEvent) {}

    override fun selectionChanged(selectionEvent: SelectionEvent) {
        log.info("selectionChanged")
        currentProjectCounters[selectionEvent.editor.project]!!.selectionChangedCount++
    }

    /**
     * Extracts email from email.txt file in root dir of open project.
     * Returns email if file exists, and returns "" otherwise.
     */
    private fun getEmail(project: Project): String {
        val projectBaseDir = project.baseDir.path
        var emailPath = File( projectBaseDir + File.separator + "email.txt")

        return if (emailPath.exists()) {
            emailPath.readText()
        }
        else {
            ""
        }
    }

    /*
    private fun logEditors(document: Document, editors: Array<Editor>, message: String) {
        if (editors.isEmpty()) {
            return
        }

        val editor = editors[0]
        val project = editor.project

        if (shouldLog(project!!)) {
            val file = FileDocumentManager.getInstance().getFile(document)
            val completeMessage = "${file!!.path}, $message, ${project.basePath},"
            println(completeMessage)
            log.info(completeMessage)
        }

    }

    private fun logEditor(document: Document, editor: Editor, message: String) {
        val project = editor.project

        if (shouldLog(project!!)) {
            val file = FileDocumentManager.getInstance().getFile(document)
            val completeMessage = "${file!!.path}, $message, ${project.basePath}"
            println(completeMessage)
            log.info(completeMessage)
        }
    }
    */
}
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.concurrent.timer
import com.intellij.openapi.progress.ProgressIndicator

class CS125Component :
        ApplicationComponent,
        TypedHandlerDelegate(),
        CaretListener,
        VisibleAreaListener,
        EditorMouseListener,
        SelectionListener,
        DocumentListener,
        ProjectManagerListener {
    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    data class Counter(
            var index: Long = 0,
            var MP: String = "",
            var start: Long = Instant.now().toEpochMilli(),
            var end: Long = 0,
            var keystrokeCount: Int = 0,
            var caretPositionChangedCount: Int = 0,
            var visibleAreaChangedCount: Int = 0,
            var mousePressedCount: Int = 0,
            var selectionChangedCount: Int = 0,
            var documentChangedCount: Int = 0
    )
    private fun totalCount(counter: Counter): Int {
        return counter.keystrokeCount +
                counter.caretPositionChangedCount +
                counter.visibleAreaChangedCount +
                counter.mousePressedCount +
                counter.visibleAreaChangedCount +
                counter.documentChangedCount
    }
    private var currentProjectCounters = mutableMapOf<Project, Counter>()

    data class ProjectInfo(
            var MP: String,
            var email: String = ""
    )
    private var projectInfo = mutableMapOf<Project, ProjectInfo>()

    override fun initComponent() {
        log.info("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this)

        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
        }
    }

    override fun disposeComponent() {
        log.info("disposeComponent")

        val state = CS125Persistence.getInstance().persistentState
        for ((_, counter) in currentProjectCounters) {
            if (totalCount(counter) > 0) {
                state.savedCounters.add(counter)
            }
        }
    }

    var uploadBusy = false
    @Synchronized
    fun uploadCounters(project: Project) {
        log.info("uploadCounters")
        if (uploadBusy) {
            return
        }

        val state = CS125Persistence.getInstance().persistentState

        val startIndex = 0
        val endIndex = state.savedCounters.size

        val uploadingCounters = mutableListOf<Counter>()
        uploadingCounters.addAll(state.savedCounters)

        if (uploadingCounters.isEmpty()) {
            return
        }

        val uploadCounterTask = object: Task.Backgroundable(project,"Uploading...", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                updateEmails()
                try {
                    Thread.sleep(1000)
                } catch (e: Exception) { }
                state.savedCounters.subList(startIndex, endIndex).clear()
                log.info("Upload done")
                uploadBusy = false
            }
        }
        ProgressManager.getInstance().run(uploadCounterTask)
        uploadBusy = true
    }

    @Synchronized
    fun rotateCounters() {
        log.info("rotateCounters")

        val state = CS125Persistence.getInstance().persistentState

        log.info(state.savedCounters.size.toString())

        val end = Instant.now().toEpochMilli()
        for ((project, counter) in currentProjectCounters) {
            if (totalCount(counter) == 0) {
                continue
            }
            counter.end = end
            log.info(counter.toString())
            state.savedCounters.add(counter)
            currentProjectCounters[project] = Counter(state.counterIndex++, projectInfo[project]!!.MP)
            uploadCounters(project)
        }
    }

    private val stateTimerPeriod = 5000L
    private var stateTimer: Timer? = null

    override fun projectOpened(project: Project) {
        log.info("projectOpened")

        val gradeConfigurationFile = File(project.baseDir.path).resolve(File("config/grade.yaml"))
        log.info(gradeConfigurationFile.toString())
        if (!gradeConfigurationFile.exists()) {
            return
        }

        @Suppress("UNCHECKED_CAST")
        val gradeConfiguration = Yaml().load(Files.newBufferedReader(gradeConfigurationFile.toPath())) as Map<String, String>
        val MPname = gradeConfiguration["name"] ?: return

        val state = CS125Persistence.getInstance().persistentState
        projectInfo[project] = ProjectInfo(MPname)
        currentProjectCounters[project] = Counter(state.counterIndex++, MPname)

        if (currentProjectCounters.size == 1) {
            stateTimer?.cancel()
            stateTimer = timer("edu.illinois.cs.cs125", true,
                    stateTimerPeriod, stateTimerPeriod) {
                rotateCounters()
            }
        }
    }

    override fun projectClosed(project: Project) {
        log.info("projectClosed")

        if (!(currentProjectCounters.containsKey(project))) {
            return
        }
        currentProjectCounters.remove(project)
        if (currentProjectCounters.isEmpty()) {
            stateTimer?.cancel()
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

    override fun documentChanged(documentEvent: DocumentEvent) {
        log.info("documentChanged")
        val editors = EditorFactory.getInstance().getEditors(documentEvent.document)
        if (editors.isEmpty()) {
            return
        }
        editors.forEach { editor ->
            currentProjectCounters[editor.project]!!.documentChangedCount++
        }
    }

    private fun updateEmails() {
        for ((project, projectInfo) in projectInfo) {
            val emailFile = File(project.baseDir.path).resolve(File("email.txt"))
            if (!(emailFile.exists())) {
                projectInfo.email = ""
            } else {
                projectInfo.email = emailFile.readText().trim()
            }
        }
    }
}
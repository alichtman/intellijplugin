import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class CS125Component :
        ApplicationComponent,
        TypedActionHandler,
        CaretListener,
        VisibleAreaListener,
        EditorMouseListener,
        ProjectManagerListener {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")
    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    data class Counter(
            var MP: String?,
            var keystrokeCount: Int = 0,
            var caretPositionChangedCount: Int = 0,
            var visibleAreaChangedCount: Int = 0,
            var mousePressedCount: Int = 0
    )
    var projectCounters = mutableMapOf<Project, Counter>()
    var activeProjectCount = 0

    override fun initComponent() {
        log.info("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this);

        ApplicationManager.getApplication().invokeLater {
            EditorActionManager.getInstance().typedAction.setupHandler(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            // restartUploadTimer()
            // EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
        }
    }

    val UPLOAD_TIMER_INITIAL_DELAY = 1000L
    val UPLOAD_TIMER_PERIOD = 10 * 60 * 1000L
    private var uploadTimer: Timer? = null
    private fun restartUploadTimer() {
        uploadTimer?.cancel();
        uploadTimer = timer("edu.illinois.cs.cs125", true,
                UPLOAD_TIMER_INITIAL_DELAY, UPLOAD_TIMER_PERIOD, {
            DataTransfer().handleSubmittingData()
        })
    }

    override fun projectOpened(project: Project) {
        log.info("projectOpened")

        val gradeConfigurationFile = File(project.baseDir.path).resolve(File("config/grade.yaml"))
        log.info(gradeConfigurationFile.toString())
        if (!gradeConfigurationFile.exists()) {
            return
        }

        val gradeConfiguration = Yaml().load(Files.newBufferedReader(gradeConfigurationFile.toPath())) as Map<String, String>
        if (gradeConfiguration.get("name") == null) {
            return
        }

        projectCounters[project] = Counter(gradeConfiguration.get("name"))
        if (activeProjectCount == 0) {
            restartUploadTimer()
        }
        activeProjectCount++;
    }

    override fun projectClosed(project: Project) {
        log.info("projectClosed")

        if (!(projectCounters.containsKey(project))) {
            return
        }

        activeProjectCount--
        if (activeProjectCount == 0) {
            uploadTimer?.cancel();
        }
    }

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        log.info("execute")
        projectCounters[editor.project]!!.keystrokeCount++
    }

    override fun caretPositionChanged(caretEvent: CaretEvent) {
        log.info("caretPositionChanged")
        projectCounters[caretEvent.editor.project]!!.caretPositionChangedCount++
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        log.info("visibleAreaChanged")
        projectCounters[visibleAreaEvent.editor.project]!!.visibleAreaChangedCount++
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        log.info("mousePressed")
        projectCounters[editorMouseEvent.editor.project]!!.mousePressedCount++
    }
    override fun mouseClicked(e: EditorMouseEvent) {}
    override fun mouseReleased(e: EditorMouseEvent) {}
    override fun mouseEntered(e: EditorMouseEvent) {}
    override fun mouseExited(e: EditorMouseEvent) {}

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

    /**
     * Returns true if the file activity should be logged.
     * Checks for the existence of a .cs125 file in the project dir.
     */
    private fun shouldLog(project: Project): Boolean {
        val flagPath = ".cs125"
        val flagFile = File(project.baseDir.path + File.separator + flagPath)
//        println("SHOULD LOG: " + flagFile.path + "? -> " + flagFile.exists())
        return flagFile.exists()
    }

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

    private fun logProjectSwitch(project: Project, author: String, message: String) {
        if (shouldLog(project)) {
            val completeMessage = "${project.basePath}, $message, $author"
            println(completeMessage)
            log.info("$message, $project, $author")
        }
    }

    /**
     * Date utilities
     */

    private fun convertStringToDate(dateStr: String):Date {
        val format = SimpleDateFormat("yyyy-dd-MM HH:mm:ss", Locale.ENGLISH)
        return format.parse(dateStr)
    }

    /**
     * Returns the date of a log line
     */
    private fun extractDateFromLog(line: String): Date {
        var dateStr = line.split(",")[0]
        return convertStringToDate(dateStr)
    }
}
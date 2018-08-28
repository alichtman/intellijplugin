
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.annotations.NotNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.timer

class CS125Component :
        ApplicationComponent,
        DocumentListener,
        VisibleAreaListener,
        EditorMouseListener,
        ProjectManagerListener,
        CaretListener {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")
    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    data class Counter(
            var documentChangedCount: Int = 0,
            var caretPositionChangedCount: Int = 0,
            var visibleAreaChangedCount: Int = 0,
            var mousePressedCount: Int = 0
    )
    var currentCounter = Counter()

    override fun initComponent() {
        ApplicationManager.getApplication().invokeLater {
            log.info("initComponent")
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
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

    override fun documentChanged(documentEvent: DocumentEvent?) {
        log.info("documentChanged")
        currentCounter.documentChangedCount++

        /*
        val msg = "DOCUMENT MODIFIED"
        if (documentEvent != null) {
            logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document), msg)
        }
        */
    }

    override fun caretPositionChanged(e: CaretEvent?) {
        log.info("caretPositionChanged")
        currentCounter.caretPositionChangedCount++
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        log.info("visibleAreaChanged")
        currentCounter.visibleAreaChangedCount++
        //logEditor(visibleAreaEvent.editor.document, visibleAreaEvent.editor, msg)
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        log.info("mousePressed")
        currentCounter.mousePressedCount++
        //logEditor(editorMouseEvent.editor.document, editorMouseEvent.editor, msg)
    }

    /*******************************
     * Project Opened/Closed/Changed
     ******************************/

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



    // TODO: DOES NOT WORK
    override fun projectOpened(project: Project?) {
        val counter = ActivityCounter.getInstance()
        counter.projectOpenCount++

        val author = getEmail(project!!)
        val msg = "PROJECT OPENED"
        logProjectSwitch(project, author, msg)
        println(msg)
    }

    // TODO: DOES NOT WORK
    override fun projectClosed(project: Project?) {
        var counter = ActivityCounter.getInstance()
        counter.projectCloseCount++

        val author = getEmail(project!!)
        val msg = "PROJECT CLOSED"
        logProjectSwitch(project, author, msg)
    }

    // WORKS






    override fun mouseClicked(e: EditorMouseEvent) {}
    override fun mouseReleased(e: EditorMouseEvent) {}
    override fun mouseEntered(e: EditorMouseEvent) {}
    override fun mouseExited(e: EditorMouseEvent) {}

    /***************
     * Logging utils.
     ***************/

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

import com.intellij.AppTopics
import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.util.Function
import org.jetbrains.annotations.NotNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class CS125Component : ApplicationComponent, DocumentListener, VisibleAreaListener, EditorMouseListener, ProjectManagerListener, EventListener, CaretListener {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    /**
     * Init and Destruct, as well as Saving Action
     */

    override fun initComponent() {
        log.info("plugin initialized")

        startScheduluedDataTransfers()

        ApplicationManager.getApplication().invokeLater {
            val connection = ApplicationManager.getApplication().messageBus.connect()

            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerAdapter() {
                // WORKS
                override fun beforeDocumentSaving(document: Document) {
                    val counter = ActivityCounter.getInstance()
                    counter.documentSaveActionCount++

                    val msg = "DOCUMENT SAVED"
                    logEditors(document, EditorFactory.getInstance().getEditors(document), msg)
                }
            })

            connection.subscribe(ProjectTopics.MODULES, object : ModuleListener {
                // TODO: DOES NOT WORK
                override fun moduleAdded(project: Project, module: Module) {
                    val counter = ActivityCounter.getInstance()
                    counter.moduleAddedCount++

                    val msg = "MODULE ADDED"
                    println(msg)
                }

                // TODO: DOES NOT WORK
                override fun modulesRenamed(project: Project, modules: MutableList<Module>, oldNameProvider: Function<Module, String>) {
                    val counter = ActivityCounter.getInstance()
                    counter.moduleRenamed++

                    val msg = "MODULE RENAMED"
                    println(msg)
                }
            })

            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            // TODO: Figure out params for this call.
//            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
        }
    }

    override fun disposeComponent() {
        log.info("plugin shutting down")
    }

    /**
     * Posts data to server on an interval, starting a period of time after this is first called.
     */
    private fun startScheduluedDataTransfers() {
        val timer = Timer(true)

        println("STARTING SCHEDULED DATA TRANSFERS")


        val testPeriod: Long = 1000 * 60/2
//        val fiveMinPeriod: Long = 1000 * 60 * 5

        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                DataTransfer().handleSubmittingData()
            }
        }, testPeriod, testPeriod)
    }


    @NotNull
    override fun getComponentName(): String {
        return "CS125Component"
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

    override fun caretPositionChanged(e: CaretEvent?) {
        val msg = "NOTIFY -- caret position changed"
        println(msg)
    }

    // TODO: DOES NOT WORK
    override fun projectOpened(project: Project?) {
        val counter = ActivityCounter.getInstance()
        counter.projectOpenCount++

        val author = getEmail(project!!)
        val msg = "NOTIFY -- PROJECT OPENED"
        logProjectSwitch(project, author, msg)
        println(msg)
    }

    // TODO: DOES NOT WORK
    override fun projectClosed(project: Project?) {
        var counter = ActivityCounter.getInstance()
        counter.projectCloseCount++

        val author = getEmail(project!!)
        val msg = "NOTIFY -- Project Closed"
        logProjectSwitch(project, author, msg)
    }

    // WORKS
    override fun documentChanged(documentEvent: DocumentEvent?) {
        var counter = ActivityCounter.getInstance()
        counter.documentModificationCount++

        val msg = "NOTIFY -- Document modified"
        if (documentEvent != null) {
            logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document), msg)
        }
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        var counter = ActivityCounter.getInstance()
        counter.visibleContentsChangedCount++

        val msg = "NOTIFY -- Visible area changed"
        logEditor(visibleAreaEvent.editor.document, visibleAreaEvent.editor, msg)
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        val counter = ActivityCounter.getInstance()
        counter.mouseActionCount++

        val msg = "NOTIFY -- Mouse pressed"
        logEditor(editorMouseEvent.editor.document, editorMouseEvent.editor, msg)
    }

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
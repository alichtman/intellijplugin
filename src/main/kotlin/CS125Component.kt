
import com.intellij.AppTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import org.jetbrains.annotations.NotNull
import java.io.File


class CS125Component : ApplicationComponent, DocumentListener, VisibleAreaListener, EditorMouseListener, ProjectManagerListener {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    /**
     * Init and Destruct, as well as Saving Action
     */

    override fun initComponent() {
        log.info("plugin initialized")

        ApplicationManager.getApplication().invokeLater {
            val connection = ApplicationManager.getApplication().messageBus.connect()
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerAdapter() {
                override fun beforeDocumentSaving(document: Document) {
                    var counter = ActionCounter.getInstance()
                    counter.documentSaveActionCount++

                    val msg = "document being saved"
                    logEditors(document, EditorFactory.getInstance().getEditors(document), msg)
                }
            })
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
        }
    }

    override fun disposeComponent() {
        log.info("plugin shutting down")
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

    override fun projectOpened(project: Project?) {
        var counter = ActionCounter.getInstance()
        counter.projectOpenCount++

        val author = getEmail(project!!)
        val msg = "Project Opened"
        logProjectSwitch(project, author, msg)
        println("OPEN COUNT " + counter.projectOpenCount)

    }

    override fun projectClosed(project: Project?) {
        var counter = ActionCounter.getInstance()
        counter.projectCloseCount++

        val author = getEmail(project!!)
        val msg = "Project Closed"
        logProjectSwitch(project, author, msg)
    }

    override fun documentChanged(documentEvent: DocumentEvent) {
        var counter = ActionCounter.getInstance()
        // TODO: Is this a file switch? Or just a doc edit?
        counter.fileSwitchCount++

        val msg = "Document switched"
        logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document), msg)
    }

    override fun beforeDocumentChange(documentEvent: DocumentEvent?) {
        var counter = ActionCounter.getInstance()
        counter.documentModificationActionCount++

        val msg = "Document switched"
        if (documentEvent != null) {
            logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document), msg)
        }

        val dt = CS125DataTransfer()
        var logs: ArrayList<String> = ArrayList()
        logs.add("log1")
        logs.add("log2")
        var stagedLogs = CS125StagedLogs(
                "user",
                logs)

        dt.postDataToServer(stagedLogs)
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        var counter = ActionCounter.getInstance()
        counter.visibleContentsChangedCount++

        val msg = "Visible area changed"
        logEditor(visibleAreaEvent.editor.document, visibleAreaEvent.editor, msg)
        println(counter.visibleContentsChangedCount)
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        var counter = ActionCounter.getInstance()
        counter.mousePressActionCount++

        val msg = "Mouse pressed"
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
}
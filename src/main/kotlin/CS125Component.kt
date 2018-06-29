
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
     * Init and Destruct
     */

    override fun initComponent() {
        log.info("plugin initialized")

        ApplicationManager.getApplication().invokeLater {
            val connection = ApplicationManager.getApplication().messageBus.connect()
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerAdapter() {
                override fun beforeDocumentSaving(document: Document) {
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
        val author = getEmail(project!!)
        val msg = "Project Opened"

        // Make plugin icon disappear if not a CS125 project.
        // Bug: Will not switch between two open projects, one CS125 related and one not.
        when (shouldLog(project)) {
            true -> {
                // TODO: DISPLAY BUTTON
            }
            false -> {
                // TODO: HIDE BUTTON
            }
        }

        logProjectSwitch(project, author, msg)

    }

    override fun projectClosed(project: Project?) {
        val author = getEmail(project!!)
        val msg = "Project Closed"
        logProjectSwitch(project, author, msg)
    }

    override fun documentChanged(documentEvent: DocumentEvent) {
        val msg = "Document switched"
        logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document), msg)
    }

    /*************************
     * Active editing detected.
     *************************/

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        val msg = "Visible area changed"
        logEditor(visibleAreaEvent.editor.document, visibleAreaEvent.editor, msg)
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
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
        val flagPath: String = ".cs125"
        val flagFile: File = File(project.baseDir.path + File.separator + flagPath)
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
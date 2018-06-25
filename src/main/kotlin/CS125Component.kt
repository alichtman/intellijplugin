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
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull

class CS125Component : ApplicationComponent, DocumentListener, VisibleAreaListener, EditorMouseListener {
    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    override fun initComponent() {
        log.info("plugin initialized")

        ApplicationManager.getApplication().invokeLater {
            val connection = ApplicationManager.getApplication().messageBus.connect()
            connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerAdapter() {
                override fun beforeDocumentSaving(document: Document) {
                    log.info("document being saved")
                    logEditors(document, EditorFactory.getInstance().getEditors(document))
                }
            })
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
        }
    }

    @NotNull
    override fun getComponentName(): String {
        return "CS125 Component"
    }

    override fun documentChanged(documentEvent: DocumentEvent) {
        log.info("document changed")
        logEditors(documentEvent.document, EditorFactory.getInstance().getEditors(documentEvent.document))
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        log.info("visible area changed")
        logEditor(visibleAreaEvent.editor.document, visibleAreaEvent.editor)
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        log.info("mouse pressed")
        logEditor(editorMouseEvent.editor.document, editorMouseEvent.editor)
    }

    override fun mouseClicked(e: EditorMouseEvent) {}
    override fun mouseReleased(e: EditorMouseEvent) {}
    override fun mouseEntered(e: EditorMouseEvent) {}
    override fun mouseExited(e: EditorMouseEvent) {}

    override fun disposeComponent() {
        log.info("plugin shutting down")
    }

    /**
     * Returns true if the file activity should be logged.
     * Checks for the existence of a .cs125 file in the project dir.
     */
    private fun shouldLog(baseDir: VirtualFile?): Boolean {
        val fileFlag: String = ".cs125"
        for (file in baseDir?.children!!) {
            if (file.name.contains(fileFlag)) {
                log.info("LOGGING FILE :: TRUE :: " + file.name)
                return true
            }
        }
        log.info("LOGGING FILE :: FALSE")
        return false
    }

    private fun logEditors(document : Document, editors : Array<Editor>) {
        if (editors.isEmpty()) {
            return
        }

        val editor = editors[0]

        if (shouldLog(editor.project?.baseDir)) {
            val file  = FileDocumentManager.getInstance().getFile(document)
            val project = editor.project
            log.info("$file $project")
        }

    }

    private fun logEditor(document : Document, editor : Editor) {

        if (shouldLog(editor.project?.baseDir)) {
            val file = FileDocumentManager.getInstance().getFile(document)
            val project = editor.project
            log.info("$file $project")
        }
    }
}
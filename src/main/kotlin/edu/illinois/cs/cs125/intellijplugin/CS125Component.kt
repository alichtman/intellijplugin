package edu.illinois.cs.cs125.intellijplugin

import com.google.gson.GsonBuilder
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.psi.PsiFile
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.concurrent.timer

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
            var email: String = "",
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
            var email: String
    )
    private var projectInfo = mutableMapOf<Project, ProjectInfo>()

    override fun initComponent() {
        log.trace("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this)

        val state = CS125Persistence.getInstance().persistentState
        log.debug("Loading " + state.savedCounters.size.toString() + " counters")

        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
            uploadCounters()
        }
    }

    override fun disposeComponent() {
        log.trace("disposeComponent")

        val state = CS125Persistence.getInstance().persistentState
        for ((_, counter) in currentProjectCounters) {
            if (totalCount(counter) > 0) {
                state.savedCounters.add(counter)
            }
        }
    }

    var uploadBusy = false
    var lastUploadFailed = false
    var lastUploadAttempt: Long = 0

    @Synchronized
    fun uploadCounters() {
        log.trace("uploadCounters")
        if (uploadBusy) {
            return
        }

        val state = CS125Persistence.getInstance().persistentState

        val startIndex = 0
        val endIndex = state.savedCounters.size

        val uploadingCounters = mutableListOf<Counter>()
        uploadingCounters.addAll(state.savedCounters)

        if (uploadingCounters.isEmpty()) {
            log.trace("No counters to upload")
            return
        }

        val dataContext = try {
            DataManager.getInstance().dataContextFromFocusAsync.blockingGet(100)
        } catch (e: Exception) {
            log.warn("Problem uploading: " + e.toString())
            null
        }
        val project = dataContext?.getData(DataKeys.PROJECT) ?: return

        val uploadCounterTask = object: Task.Backgroundable(project,"Uploading CS 125 logs...", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                val gson = GsonBuilder().create()
                val httpClient = HttpClientBuilder.create().build()

                val countersInJSON = gson.toJson(uploadingCounters)
                val counterPost = HttpPost("https://cs125-reporting.cs.illinois.edu/intellij")
                counterPost.addHeader("content-type", "application/json")
                counterPost.entity = StringEntity(countersInJSON)

                lastUploadFailed = try {
                    httpClient.execute(counterPost)
                    state.savedCounters.subList(startIndex, endIndex).clear()
                    log.info("Upload succeeded")
                    false
                } catch (e: Exception) {
                    log.warn("Upload failed")
                    true
                } finally {
                    uploadBusy = false
                    lastUploadAttempt = Instant.now().toEpochMilli()
                }
            }
        }
        ProgressManager.getInstance().run(uploadCounterTask)
        uploadBusy = true
    }

    private val maxSavedCounters = 3600 // 1 hour of logs
    private val uploadLogCountThreshold = 900 // 15 minutes of logs
    private val shortestUploadWait = 10 * 60 * 1000 // 10 minutes

    @Synchronized
    fun rotateCounters() {
        log.trace("rotateCounters")

        val state = CS125Persistence.getInstance().persistentState

        val end = Instant.now().toEpochMilli()
        for ((project, counter) in currentProjectCounters) {
            if (totalCount(counter) == 0) {
                continue
            }
            counter.end = end
            log.trace("Counter " + counter.toString())
            state.savedCounters.add(counter)
            currentProjectCounters[project] = Counter(
                    state.counterIndex++,
                    projectInfo[project]?.MP ?: "",
                    projectInfo[project]?.email ?: ""
            )
        }

        if (state.savedCounters.size > maxSavedCounters) {
            state.savedCounters.subList(0, maxSavedCounters - state.savedCounters.size).clear()
        }

        val now = Instant.now().toEpochMilli()
        if ((state.savedCounters.size >= uploadLogCountThreshold &&
                        lastUploadFailed &&
                        now - lastUploadAttempt > shortestUploadWait)) {
            uploadCounters()
        }
        if (state.savedCounters.size < uploadLogCountThreshold) {
            log.trace("Not enough counters to upload")
        } else if (lastUploadFailed && now - lastUploadAttempt <= shortestUploadWait) {
            log.trace("Need to wait for longer to retry upload")
        }
    }

    private val stateTimerPeriod = 5000L
    private var stateTimer: Timer? = null

    override fun projectOpened(project: Project) {
        log.trace("projectOpened")

        val gradeConfigurationFile = File(project.baseDir.path).resolve(File("config/grade.yaml"))
        if (!gradeConfigurationFile.exists()) {
            return
        }

        @Suppress("UNCHECKED_CAST")
        val gradeConfiguration = Yaml().load(Files.newBufferedReader(gradeConfigurationFile.toPath())) as Map<String, String>
        val name = gradeConfiguration["name"] ?: return

        val emailFile = File(project.baseDir.path).resolve(File("email.txt"))
        var email = ""
        if (emailFile.exists()) {
            email = emailFile.readText().trim()
        }

        val state = CS125Persistence.getInstance().persistentState
        projectInfo[project] = ProjectInfo(name, email)
        currentProjectCounters[project] = Counter(state.counterIndex++, name, email)

        if (currentProjectCounters.size == 1) {
            stateTimer?.cancel()
            stateTimer = timer("edu.illinois.cs.cs125", true,
                    stateTimerPeriod, stateTimerPeriod) {
                rotateCounters()
            }
        }
    }

    override fun projectClosed(project: Project) {
        log.trace("projectClosed")

        if (!(currentProjectCounters.containsKey(project))) {
            return
        }
        currentProjectCounters.remove(project)
        if (currentProjectCounters.isEmpty()) {
            stateTimer?.cancel()
        }
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        val projectCounter = currentProjectCounters[project] ?: return Result.CONTINUE
        log.trace("charTyped")
        projectCounter.keystrokeCount++
        return Result.CONTINUE
    }

    override fun caretPositionChanged(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretPositionChanged")
        projectCounter.caretPositionChangedCount++
    }

    override fun visibleAreaChanged(visibleAreaEvent: VisibleAreaEvent) {
        val projectCounter = currentProjectCounters[visibleAreaEvent.editor.project] ?: return
        log.trace("visibleAreaChanged")
        projectCounter.visibleAreaChangedCount++
    }

    override fun mousePressed(editorMouseEvent: EditorMouseEvent) {
        val projectCounter = currentProjectCounters[editorMouseEvent.editor.project] ?: return
        log.trace("mousePressed")
        projectCounter.mousePressedCount++
    }
    override fun mouseClicked(e: EditorMouseEvent) {}
    override fun mouseReleased(e: EditorMouseEvent) {}
    override fun mouseEntered(e: EditorMouseEvent) {}
    override fun mouseExited(e: EditorMouseEvent) {}

    override fun selectionChanged(selectionEvent: SelectionEvent) {
        val projectCounter = currentProjectCounters[selectionEvent.editor.project] ?: return
        log.trace("selectionChanged")
        projectCounter.selectionChangedCount++
    }

    override fun documentChanged(documentEvent: DocumentEvent) {
        log.trace("documentChanged")

        val changedFile = FileDocumentManager.getInstance().getFile(documentEvent.document)
        for ((project, info) in projectInfo) {
            val emailPath = File(project.baseDir.path).resolve(File("email.txt")).canonicalPath
            if (changedFile?.canonicalPath.equals(emailPath)) {
                info.email = documentEvent.document.text.trim()
                log.debug("Updated email for project " + info.MP + ": " + info.email)
            }
        }

        val editors = EditorFactory.getInstance().getEditors(documentEvent.document)
        for (editor in editors) {
            val projectCounter = currentProjectCounters[editor.project] ?: continue
            projectCounter.documentChangedCount++
        }
    }
}
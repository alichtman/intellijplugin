package edu.illinois.cs.cs125.intellijplugin

import com.google.gson.GsonBuilder
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiFile
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.HttpClientBuilder
import org.jetbrains.annotations.NotNull
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.net.NetworkInterface
import java.nio.file.Files
import java.time.Instant
import java.util.*
import kotlin.concurrent.timer

class CS125Component :
        ApplicationComponent,
        CaretListener,
        VisibleAreaListener,
        EditorMouseListener,
        SelectionListener,
        DocumentListener,
        ProjectManagerListener,
        CompilationStatusListener,
        FileEditorManagerListener {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    @NotNull
    override fun getComponentName(): String {
        return "CS125 Plugin"
    }

    data class TestCounter(
            var passed: Int = 0,
            var failed: Int = 0,
            var ignored: Int = 0,
            var interrupted: Int = 0
    )
    data class Counter(
            var index: Long = 0,
            var MP: String = "",
            var email: String = "",
            var sentIPAddress: String = "",
            var version: String = "",
            var UUID: String = "",
            var start: Long = Instant.now().toEpochMilli(),
            var end: Long = 0,
            var keystrokeCount: Int = 0,
            var caretAdded: Int = 0,
            var caretRemoved: Int = 0,
            var caretPositionChangedCount: Int = 0,
            var visibleAreaChangedCount: Int = 0,
            var mousePressedCount: Int = 0,
            var selectionChangedCount: Int = 0,
            var documentChangedCount: Int = 0,
            var compileCount: Int = 0,
            var successfulCompileCount: Int = 0,
            var failedCompileCount: Int = 0,
            var compilerErrorCount: Int = 0,
            var compilerWarningCount: Int = 0,
            var gradingCount: Int = 0,
            var totalTestCount: Int = 0,
            var testCounts: MutableMap<String, TestCounter> = mutableMapOf(),
            var fileOpenedCount: Int = 0,
            var fileClosedCount: Int = 0,
            var fileSelectionChangedCount: Int = 0,
            var openFiles: List<FileInfo> = listOf()
    )
    data class FileInfo (
            var path: String = "",
            var lineCount: Int = 0
    )

    private fun totalCount(counter: Counter): Int {
        return counter.keystrokeCount +
                counter.caretAdded +
                counter.caretRemoved +
                counter.caretPositionChangedCount +
                counter.visibleAreaChangedCount +
                counter.mousePressedCount +
                counter.visibleAreaChangedCount +
                counter.documentChangedCount +
                counter.successfulCompileCount +
                counter.failedCompileCount +
                counter.compilerErrorCount +
                counter.compilerWarningCount +
                counter.gradingCount +
                counter.totalTestCount +
                counter.fileOpenedCount +
                counter.fileClosedCount +
                counter.fileSelectionChangedCount
    }
    var currentProjectCounters = mutableMapOf<Project, Counter>()

    data class ProjectInfo(
            var MP: String,
            var email: String,
            var networkAddress: String
    )
    var projectInfo = mutableMapOf<Project, ProjectInfo>()

    private val versionProperties = Properties()
    private var version = ""

    override fun initComponent() {
        log.trace("initComponent")

        val connection = ApplicationManager.getApplication().messageBus.connect()
        connection.subscribe(ProjectManager.TOPIC, this)

        val state = CS125Persistence.getInstance().persistentState
        log.trace("Loading " + state.savedCounters.size.toString() + " counters")

        if (state.UUID == "") {
            state.UUID = UUID.randomUUID().toString()
            if (state.savedCounters.size != 0) {
                log.warn("Must be updating plugin since saved counters exist before UUID is set")
            }
        }
        for (counter in state.savedCounters) {
            if (counter.UUID != state.UUID) {
                log.warn("Altering counter with bad UUID: ${counter.UUID} != ${state.UUID}")
                counter.UUID = state.UUID
            }
        }

        version = try {
            versionProperties.load(this.javaClass.getResourceAsStream("/version.properties"))
            versionProperties.getProperty("version")
        } catch (e: Exception) { "" }

        ApplicationManager.getApplication().invokeLater {
            EditorFactory.getInstance().eventMulticaster.addCaretListener(this)
            EditorFactory.getInstance().eventMulticaster.addVisibleAreaListener(this)
            EditorFactory.getInstance().eventMulticaster.addEditorMouseListener(this)
            EditorFactory.getInstance().eventMulticaster.addSelectionListener(this)
            EditorFactory.getInstance().eventMulticaster.addDocumentListener(this)
        }
    }

    override fun disposeComponent() {
        return
    }

    var uploadBusy = false
    var lastUploadFailed = false
    var lastUploadAttempt: Long = 0
    var lastSuccessfulUpload: Long = 0

    @Synchronized
    fun uploadCounters() {
        log.trace("uploadCounters")
        if (uploadBusy) {
            log.warn("Previous upload still busy")
            return
        }

        val state = CS125Persistence.getInstance().persistentState
        if (state.savedCounters.size == 0) {
            log.trace("No counters to upload")
            return
        }

        if (lastUploadFailed && Instant.now().toEpochMilli() - lastUploadAttempt <= shortestUploadWait) {
            log.trace("Need to wait for longer to retry upload")
            return
        }

        val startIndex = 0
        val endIndex = state.savedCounters.size

        val uploadingCounters = mutableListOf<Counter>()
        uploadingCounters.addAll(state.savedCounters)

        if (uploadingCounters.isEmpty()) {
            log.trace("No counters to upload")
            return
        }

        val project = try {
            ProjectManager.getInstance().openProjects.find { project ->
                val window = WindowManager.getInstance().suggestParentWindow(project)
                window != null && window.isFocused
            } ?: ProjectManager.getInstance().openProjects[0]
        } catch (e: Exception) {
            null
        }

        if (project == null) {
            log.warn("Can't find project in uploadCounters")
            return
        }

        val uploadCounterTask = object: Task.Backgroundable(project,"Uploading CS 125 logs...", false) {
            override fun run(progressIndicator: ProgressIndicator) {
                val now = Instant.now().toEpochMilli()

                val jsonCreator = GsonBuilder().create()
                val httpClient = HttpClientBuilder.create().build()

                val countersInJSON = jsonCreator.toJson(uploadingCounters)
                val counterPost = HttpPost("https://cs125-reporting.cs.illinois.edu/intellij")
                counterPost.addHeader("content-type", "application/json")
                counterPost.entity = StringEntity(countersInJSON)

                lastUploadFailed = try {
                    httpClient.execute(counterPost)
                    state.savedCounters.subList(startIndex, endIndex).clear()
                    log.trace("Upload succeeded")
                    lastSuccessfulUpload = now
                    false
                } catch (e: Exception) {
                    log.warn("Upload failed")
                    true
                } finally {
                    uploadBusy = false
                    lastUploadAttempt = now
                }
            }
        }
        ProgressManager.getInstance().run(uploadCounterTask)
    }

    private val stateTimerPeriodSec = 5
    private val maxSavedCounters = (2 * 60 * 60 / stateTimerPeriodSec) // 2 hours of logs
    private val uploadLogCountThreshold = (15 * 60 / stateTimerPeriodSec) // 15 minutes of logs
    private val shortestUploadWait = 10 * 60 * 1000 // 10 minutes
    private val shortestUploadInterval = 30 * 60 * 1000 // 30 minutes

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

            val fileDocumentManager = FileDocumentManager.getInstance()
            val openFiles: MutableMap<String, FileInfo> = mutableMapOf()
            for (file in FileEditorManager.getInstance(project).openFiles.filterNotNull()) {
                val document = fileDocumentManager.getCachedDocument(file) ?: continue
                openFiles[file.path] = FileInfo(file.path, document.lineCount)
            }
            counter.openFiles = openFiles.values.toList()

            counter.UUID = state.UUID

            log.trace("Counter " + counter.toString())

            state.savedCounters.add(counter)
            currentProjectCounters[project] = Counter(
                    state.counterIndex++,
                    projectInfo[project]?.MP ?: "",
                    projectInfo[project]?.email ?: "",
                    projectInfo[project]?.networkAddress ?: "",
                    version,
                    state.UUID
            )
        }

        if (state.savedCounters.size > maxSavedCounters) {
            state.savedCounters.subList(0, maxSavedCounters - state.savedCounters.size).clear()
        }

        val now = Instant.now().toEpochMilli()
        if (state.savedCounters.size >= uploadLogCountThreshold) {
            uploadCounters()
        } else if (now - lastSuccessfulUpload > shortestUploadInterval) {
            uploadCounters()
        }
    }

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

        val networkAddress = try {
            NetworkInterface.getNetworkInterfaces().toList().flatMap { networkInterface ->
                networkInterface.inetAddresses.toList()
                        .filter { it.address.size == 4 }
                        .filter { !it.isLoopbackAddress }
                        .filter { it.address[0] != 10.toByte() }
                        .map { it.hostAddress }
            }.first()
        } catch (e: Exception) { "" }

        projectInfo[project] = ProjectInfo(name, email, networkAddress)

        val state = CS125Persistence.getInstance().persistentState

        currentProjectCounters[project] = Counter(state.counterIndex++, name, email, networkAddress, version, state.UUID)

        if (currentProjectCounters.size == 1) {
            stateTimer?.cancel()
            stateTimer = timer("edu.illinois.cs.cs125", true,
                    stateTimerPeriodSec * 1000L, stateTimerPeriodSec * 1000L) {
                rotateCounters()
            }
        }
        uploadCounters()

        project.messageBus.connect().subscribe(CompilerTopics.COMPILATION_STATUS, this)
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, this)
    }

    override fun projectClosing(project: Project?) {
        log.trace("projectClosing")

        val currentCounter = currentProjectCounters[project] ?: return
        val state = CS125Persistence.getInstance().persistentState
        if (totalCount(currentCounter) > 0) {
            state.savedCounters.add(currentCounter)
        }
        currentProjectCounters.remove(project)
        if (currentProjectCounters.isEmpty()) {
            stateTimer?.cancel()
        }
        // Force an immediate upload
        lastSuccessfulUpload = 0
        uploadCounters()
        return
    }

    inner class TypedHandler: TypedHandlerDelegate() {
        override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
            val projectCounter = currentProjectCounters[project] ?: return Result.CONTINUE
            log.trace("charTyped")
            projectCounter.keystrokeCount++
            return Result.CONTINUE
        }
    }

    inner class TestStatusHandler : TestStatusListener() {
        override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?) { }
        private fun countTests(abstractTestProxy: AbstractTestProxy, projectCounter: Counter) {
            if (!abstractTestProxy.isLeaf) {
                for (child in abstractTestProxy.children) {
                    countTests(child, projectCounter)
                }
                return
            }
            val name = abstractTestProxy.name
                    .replace("\\.test$".toRegex(), "")
                    .replace(".", "_")
            if (!(projectCounter.testCounts.containsKey(name))) {
                projectCounter.testCounts[name] = TestCounter()
            }
            val testCounter = projectCounter.testCounts[name] ?: return
            when {
                abstractTestProxy.isPassed -> testCounter.passed++
                abstractTestProxy.isDefect -> testCounter.failed++
                abstractTestProxy.isIgnored -> testCounter.ignored++
                abstractTestProxy.isInterrupted -> testCounter.interrupted++
            }
            projectCounter.totalTestCount++
        }
        override fun testSuiteFinished(abstractTestProxy: AbstractTestProxy?, project: Project) {
            if (abstractTestProxy == null) {
                return
            }
            val projectCounter = currentProjectCounters[project] ?: return
            log.trace("testSuiteFinished")
            countTests(abstractTestProxy, projectCounter)
        }
    }

    override fun caretAdded(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretAdded")
        projectCounter.caretAdded++
        return
    }
    override fun caretRemoved(caretEvent: CaretEvent) {
        val projectCounter = currentProjectCounters[caretEvent.editor.project] ?: return
        log.trace("caretRemoved")
        projectCounter.caretRemoved++
        return
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
            try {
                val emailPath = File(project.baseDir.path).resolve(File("email.txt")).canonicalPath
                if (changedFile?.canonicalPath.equals(emailPath)) {
                    info.email = documentEvent.document.text.trim()
                    log.debug("Updated email for project " + info.MP + ": " + info.email)
                }
            } catch (e: Throwable) {}
        }

        val editors = EditorFactory.getInstance().getEditors(documentEvent.document)
        for (editor in editors) {
            val projectCounter = currentProjectCounters[editor.project] ?: continue
            projectCounter.documentChangedCount++
        }
    }

    override fun beforeDocumentChange(event: DocumentEvent?) {
        return
    }

    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        if (aborted) {
            return
        }
        val projectCounter = currentProjectCounters[compileContext.project] ?: return
        log.trace("compilationFinished")
        projectCounter.compileCount++
        if (errors == 0) {
            projectCounter.successfulCompileCount++
        } else {
            projectCounter.failedCompileCount++
        }
        projectCounter.compilerErrorCount += errors
        projectCounter.compilerWarningCount += warnings
    }

    override fun fileOpened(manager: FileEditorManager, file: VirtualFile) {
        val projectCounter = currentProjectCounters[manager.project] ?: return
        log.trace("fileOpened")
        projectCounter.fileOpenedCount++
    }

    override fun fileClosed(manager: FileEditorManager, file: VirtualFile) {
        val projectCounter = currentProjectCounters[manager.project] ?: return
        log.trace("fileClosed")
        projectCounter.fileClosedCount++
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        val projectCounter = currentProjectCounters[event.manager.project] ?: return
        log.trace("fileSelectionChanged")
        projectCounter.fileSelectionChangedCount++
    }
}

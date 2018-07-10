
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project


class CS125GradeAction : AnAction() {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    override fun actionPerformed(e: AnActionEvent) {
        log.info("grading action initiated")
        val project = e.project
        if (project != null) {
            triggerRunConfiguration(project)
        }

    }

    // TODO: Figure out how to put together TestConfiguration
    private fun triggerRunConfiguration(project: Project) {
        val name = "CS125 Test Configuration"
        val testConfiguration = TestConfiguration.getInstance()
        val factory = testConfiguration.getFactory()
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val runSettings: RunnerAndConfigurationSettings = RunManager.getInstance(project).createRunConfiguration(name, factory)
        ProgramRunnerUtil.executeConfiguration(runSettings, executor)
    }


}
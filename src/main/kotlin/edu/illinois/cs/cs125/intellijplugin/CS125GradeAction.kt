package edu.illinois.cs.cs125.intellijplugin

import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class CS125GradeAction : AnAction() {

    private val log = Logger.getInstance("edu.illinois.cs.cs125")

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        log.trace("actionPerformed")
        val project = anActionEvent.project ?: return

        val runManager = RunManager.getInstance(project)

        for (runConfiguration in runManager.allSettings) {
            if (runConfiguration.name.trim().toLowerCase().startsWith("grade")) {
                ProgramRunnerUtil.executeConfiguration(project, runConfiguration, DefaultRunExecutor.getRunExecutorInstance())
                val projectCounter =
                        project.getComponent(CS125Component::class.java)?.currentProjectCounters?.get(project) ?: return
                projectCounter.gradingCount++
            }
        }
    }

    override fun update(anActionEvent: AnActionEvent) {
        log.trace("update")
        val project = anActionEvent.project
        val isVisible = project?.getComponent(CS125Component::class.java)?.projectInfo?.containsKey(project) ?: false
        anActionEvent.presentation.isVisible = isVisible
    }
}
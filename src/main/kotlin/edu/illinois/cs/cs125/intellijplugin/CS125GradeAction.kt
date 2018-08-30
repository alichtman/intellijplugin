package edu.illinois.cs.cs125.intellijplugin

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

    override fun actionPerformed(anActionEvent: AnActionEvent) {
        log.trace("actionPerformed")
        val project = anActionEvent.project ?: return

        val runManager = RunManager.getInstance(project)
        for (runConfiguration in runManager.allSettings) {
            if (runConfiguration.name.trim().toLowerCase().startsWith("grade")) {
                ProgramRunnerUtil.executeConfiguration(runConfiguration, DefaultRunExecutor.getRunExecutorInstance())
            }
        }
    }
}
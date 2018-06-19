import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

class CS125GradeAction : AnAction() {
    val log = Logger.getInstance("edu.illinois.cs.cs125")

    override fun actionPerformed(e: AnActionEvent) {
        log.info("grading action initiated")
    }
}
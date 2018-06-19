import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener

class CS125Component : ApplicationComponent {
    val log = Logger.getInstance("edu.illinois.cs.cs125")

    override fun initComponent() {
        log.info("plugin initialized")
    }
}
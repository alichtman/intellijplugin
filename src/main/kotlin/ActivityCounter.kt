class ActivityCounter constructor() {

    /**
     * Counted by the DocumentListener interface.
     */
    var documentChangedCount: Int = 0

    var documentSaveCount: Int = 0

    var mouseActionCount: Int = 0
    var visibleContentsChangedCount: Int = 0
    var projectOpenCount: Int = 0
    var projectCloseCount: Int = 0
    var fileSwitchCount: Int = 0
    var moduleAddedCount: Int = 0
    var moduleRenamed: Int = 0

    init {
        println("NEW ACTIVITY COUNTER CREATED")
    }

    fun resetVals() {
        documentChangedCount = 0

        documentSaveCount = 0
        mouseActionCount = 0
        visibleContentsChangedCount = 0
        projectOpenCount = 0
        projectCloseCount = 0
        fileSwitchCount = 0
    }

    constructor(another: ActivityCounter) : this() {
        this.documentSaveCount = another.documentSaveCount
        this.documentChangedCount = another.documentChangedCount
        this.mouseActionCount = another.mouseActionCount
        this.visibleContentsChangedCount = another.visibleContentsChangedCount
        this.projectOpenCount = another.projectOpenCount
        this.projectCloseCount = another.projectCloseCount
        this.fileSwitchCount = another.fileSwitchCount
    }

    companion object {
        private var instance: ActivityCounter = ActivityCounter()

        fun getInstance(): ActivityCounter {
            return instance
        }

        fun setInstance(instance: ActivityCounter) {
            this.instance = instance
        }
    }
}

class ActivityCounter private constructor() {

    var documentEditCount: Int = 0
    var documentSaveActionCount: Int = 0
    var documentModificationActionCount: Int = 0
    var mousePressActionCount: Int = 0
    var visibleContentsChangedCount: Int = 0
    var projectOpenCount: Int = 0
    var projectCloseCount: Int = 0
    var fileSwitchCount: Int = 0


    init {
        ++instancesCount
    }

    companion object {
        var instancesCount = 0
        private val instance: ActivityCounter = ActivityCounter()

        @Synchronized
        fun getInstance(): ActivityCounter {
            return instance
        }
    }
}
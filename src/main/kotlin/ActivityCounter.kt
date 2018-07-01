
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
        println("NEW ACTIVTIY COUNTER CREATED")
    }

    companion object {
        private val instance: ActivityCounter = ActivityCounter()

        fun getInstance(): ActivityCounter {
            return instance
        }
    }
}
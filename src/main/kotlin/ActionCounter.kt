
class ActionCounter private constructor() {

    var documentSaveActionCount: Int = 0
    var documentModificationActionCount: Int = 0
    var mousePressActionCount: Int = 0
    var visibleContentsChangedCount: Int = 0
    var projectOpenCount: Int = 0
    var projectCloseCount: Int = 0
    var fileSwitchCount: Int = 0

    init {
        /*
        *  every time init is called increment instance count
        *  just in case somehow we break singleton rule, this will be
        *  called more than once and myInstancesCount > 1 == true
        */
        ++myInstancesCount
    }

    companion object {
        var myInstancesCount = 0
        private val instance: ActionCounter = ActionCounter()

        @Synchronized
        fun getInstance(): ActionCounter {
            return instance
        }
    }
}
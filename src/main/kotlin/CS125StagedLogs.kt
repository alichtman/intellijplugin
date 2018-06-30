import java.io.Serializable


/**
 * Data object that is serialized to be sent to the server.
 */

data class CS125StagedLogs(val username : String = "",
                           val activitySessionLogs : ArrayList<ActivityState> = ArrayList<ActivityState>(0)) : Serializable
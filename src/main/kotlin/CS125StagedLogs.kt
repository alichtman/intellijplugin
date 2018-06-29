import java.io.Serializable


/**
 * Data object that is serialized to be sent to the server.
 */

data class CS125StagedLogs(val username : String = "",
                           val logs : ArrayList<String> = ArrayList<String>(0)) : Serializable
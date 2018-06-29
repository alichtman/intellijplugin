
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList


class CS125Persistence {

    private fun convertStringToDate(dateStr: String):Date {
        val format = SimpleDateFormat("yyyy-dd-MM HH:mm:ss", Locale.ENGLISH)
        return format.parse(dateStr)
    }

    /**
     * Returns the date of a log line
     */
    private fun extractDateFromLog(line: String): Date {
        var dateStr = line.split(",")[0]
        return convertStringToDate(dateStr)
    }


    /**
     * Return an arraylist of all logs that haven't been posted to the server yet.
     */
    fun getNewLogsFromLogFile(logFile: Path, lastDateScraped: Date): ArrayList<String> {
        val logList = String(Files.readAllBytes(logFile)).split("\n")

        // Reverse logList
        var logListReversed = logList.subList(0, logList.size)
        Collections.reverse(logListReversed)

        // Iterate through logs until finding a date that's already been scraped, accumulating logs as we go.
        var unpostedLogs = ArrayList<String>()
        for (line in logListReversed) {
            if (extractDateFromLog(line).after(lastDateScraped) && line.contains("edu.illinois.cs.cs125")) {
                unpostedLogs.add(line)
            } else {
                break
            }
        }

        // Reverse logs again to return them to chronological order.
        unpostedLogs.reverse()
        return unpostedLogs
    }

    // TODO: Instead of logging into idea.log, reroute logs to a file in home directory, and clear file
    // TODO:     incrementally as it's pushed to server.

//    private fun getLogFilePath(): File {
//        return File(System.getProperty("user.home") + "/${Constants.LOGFILE}")
//    }
//
//    /**
//     * Adds log file if it doesn't exist at ~/.cs125.log.
//     */
//    private fun addLogFileSafely() {
//        var dotfile = getLogFilePath()
//        dotfile.createNewFile()
//    }
//
//
//    /**
//     * Appends new data to log file, creating the file if needed.
//     */
//    fun writeDataToLocalFile(path: File, logs: MutableList<String>) {
//        try {
//            // Create new file if it doesn't exist and write to it.
//            if (path.createNewFile()) {
//                println("$path was created!")
//                writeLogsToFile(path, logs)
//            } else { // Read already existing file and append logs to it.
//                println("$path already exists.")
//                var existingLogs: MutableList<String>? = readLogsFromFile(path)
//
//                // Logs exist already, add the new ones at the end.
//                if (existingLogs != null) {
//                    for (logItem: String in logs) {
//                        existingLogs.add(logItem)
//                    }
//
//                } else { // File exists but is empty.
//                    existingLogs = logs
//                }
//
//                writeLogsToFile(path, existingLogs)
//            }
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
}
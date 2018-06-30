
import com.google.gson.GsonBuilder
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


class CS125DataTransfer {

    /**
     * Serializes stagedLogs and returns a JSON object.
     */
    private fun convertDataToJSON(stagedLogs: CS125StagedLogs): String {
        println("Serializing object to JSON...")
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonData: String = gson.toJson(stagedLogs)
        println(jsonData)
        return jsonData
    }

    /**
     * Posts log stagedLogs to server.
     */
    fun postDataToServer(stagedLogs: CS125StagedLogs) {

        println("Posting data to server.")
        println(stagedLogs.logs)

        if (stagedLogs.logs.size == 0) {
            print("Aborted server post because there were no stagedLogs.")
            return
        }

        /**
         * Configure connection.
         * curl -X POST -H "Content-Type: application/json" -d '{"username":"user","logs":["log1","log2"]}' http://127.0.0.1:5000/plugin/api/upload_status
         */

        val url = URL("http://127.0.0.1:5000/plugin/api/upload_status")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 300000
        connection.doOutput = true
        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        try {
            val strJson: String = convertDataToJSON(stagedLogs)

            val out = OutputStreamWriter(connection.outputStream)
            out.write(strJson)
            out.close()

            println("RESPONSE CODE ${connection.responseCode}")
            println("Data: $strJson")
        } catch (e: Exception) {
            print(e)
        }
    }
}
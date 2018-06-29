
import com.google.gson.GsonBuilder
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

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

        if (stagedLogs.logs.size == 0) {
            print("Aborted server post because there was no stagedLogs.")
            return
        }

        val serverURL: String = "ADD SERVER URL HERE"
        val url = URL(serverURL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 300000
        connection.connectTimeout = 300000
        connection.doOutput = true

        val strJson: String = convertDataToJSON(stagedLogs)
        val postData: ByteArray = strJson.toByteArray(StandardCharsets.UTF_8)

        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("Content-length", postData.size.toString())
        connection.setRequestProperty("Content-Type", "application/json")

        try {
            val outputStream = DataOutputStream(connection.outputStream)
            outputStream.write(postData)
            outputStream.flush()
        } catch (e: Exception) {
            print(e)
        }
    }
}
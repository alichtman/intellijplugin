import com.google.gson.GsonBuilder
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DataTransfer {

    private val log: Logger = Logger.getInstance("edu.illinois.cs.cs125")

    /**
     * Read stored data, serialize and transfer it.
     */
    fun submitData() {
        println("SUBMIT DATA CALLED")
        val storedActivityStates = readStoredPersistentData()
        val strJson: String? = convertObjectToJSON(storedActivityStates)
        if (strJson != null) {
            Thread(Runnable {
                try {
                    // Send the data
                    postDataToServer(strJson)
                } catch (e: Exception) {
                    log.warn("DATA TRANSFER ERROR:", e);
                }
            }).start()
        }
        // TODO: ON SUCCESS, wipe whatever state events were sent.
    }

    private fun readStoredPersistentData(): ActivityStateLogs {
        println("READING PERSISTENT DATA")
        var logs = ServiceManager.getService(ActivityStateLogs::class.java)
        print(logs)
        return logs
    }

    /**
     * Serializes stagedLogs and returns a JSON object.
     */
    private fun convertObjectToJSON(stagedLogs: ActivityStateLogs): String? {
        println("Serializing object to JSON...")
        val gson = GsonBuilder().setPrettyPrinting().create()
        val jsonData: String = gson.toJson(stagedLogs)
        println(jsonData)
        return jsonData
    }

    /**
     * Posts JSON data to server.
     * curl -X POST -H "Content-Type: application/json" -d '{"username":"user","activitySessionLogs":["log1","log2"]}' http://127.0.0.1:5000/plugin/api/upload_status
     */
    private fun postDataToServer(strJson: String) {
        println("Posting data to server.")

        val url = URL("http://127.0.0.1:5000/plugin/api/upload_status")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 300000
        connection.doOutput = true
        connection.setRequestProperty("charset", "utf-8")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")

        try {
            val out = OutputStreamWriter(connection.outputStream)
            out.write(strJson)
            out.close()

            println("RESPONSE CODE ${connection.responseCode}")
            println("Data: $strJson")
        } catch (e: Exception) {
            print (e)
        }
    }
}

import com.google.gson.GsonBuilder
import com.intellij.openapi.components.ServiceManager
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DataTransfer {

    /**
     * Read stored data, serialize and transfer it.
     * If successful, clear all locally stored data (both persistent state and current activity counter vals.)
     * If unsuccessful, combine with the data that's already stored locally and clear the current activity counter.
     */
    fun handleSubmittingData() {
        println("SUBMIT DATA CALLED")
        val currentActivityCounter = ActivityCounter.getInstance()
        val storedActivityStates = readStoredPersistentData()
        val updatedActivityState = combineData(currentActivityCounter, storedActivityStates)
        val strJson: String? = convertObjectToJSON(updatedActivityState)
        if (strJson != null) {
            Thread(Runnable {
                var successful = postDataToServer(strJson)
                when (successful) {
                    true -> {
                        println("WIPING PERSISTENT DATA")
                        val emptyData = ActivityLogsPersistence("NON-DEFAULT EMAIL", ArrayList())
                        ServiceManager.getService(ActivityLogsPersistence::class.java).loadState(emptyData)
                    }
                    false -> {
                        println("DATA TRANSFER ERROR")
                        val activityCounterCopy = ActivityCounter(currentActivityCounter)
                        val combinedData: ActivityLogsPersistence = combineData(activityCounterCopy, storedActivityStates)
                        println("STORING NEW DATA PERSISTENTLY")
                        ServiceManager.getService(ActivityLogsPersistence::class.java).loadState(combinedData)
                    }
                }
            }).start().also {
                // Wipe current activity counter
                currentActivityCounter.resetVals()
            }
            }
        }

        /**
         * Append new data to end of Activity Logs.
         */
        private fun combineData(newData: ActivityCounter, oldData: ActivityLogsPersistence): ActivityLogsPersistence {
            println("COMBINED DATA")
            oldData.activityLogs.add(newData)
            return oldData

        }

        private fun readStoredPersistentData(): ActivityLogsPersistence {
            println("READING PERSISTENT DATA")
            var logs = ServiceManager.getService(ActivityLogsPersistence::class.java)
            println(logs)
            return logs
        }

        /**
         * Serializes stagedLogs and returns a JSON object.
         */
        private fun convertObjectToJSON(stagedLogs: ActivityLogsPersistence): String? {
            println("Serializing object to JSON...")

            // TODO: Check out: stagedLogs.serialize()
            val gson = GsonBuilder().setPrettyPrinting().create()
            val jsonData: String = gson.toJson(stagedLogs)
            println(jsonData)
            return jsonData
        }

        /**
         * Posts JSON data to server.
         * curl -X POST -H "Content-Type: application/json" -d '{"username":"user","activitySessionLogs":["log1","log2"]}' http://127.0.0.1:5000/plugin/api/upload_status
         * Returns T/F sucessful
         */
        private fun postDataToServer(strJson: String): Boolean {
            println("Posting data to server.")

            val url = URL("http://127.0.0.1:5000/plugin/api/upload_status")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("charset", "utf-8")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            return try {
                val out = OutputStreamWriter(connection.outputStream)
                out.write(strJson)
                out.close()

                println("RESPONSE CODE ${connection.responseCode}")
                println("Data: $strJson")

                true
            } catch (e: Exception) {
                print(e)
                false
            }
        }
    }
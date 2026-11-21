package vn.herotran.tvremote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Client Firebase Realtime Database qua REST (không cần SDK, chạy được cả máy không có Google Services). */
class TvApi(baseUrl: String) {
    private val base = baseUrl.trim().trimEnd('/')

    private fun request(method: String, path: String, body: String?): String? {
        val conn = URL("$base/$path.json").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.useCaches = false
            if (body != null) {
                val data = body.toByteArray(Charsets.UTF_8)
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(data.size)
                conn.outputStream.use { it.write(data) }
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    /** Trả về JSON thô ("null" nếu chưa có dữ liệu), hoặc null nếu lỗi mạng/HTTP. */
    suspend fun get(path: String): String? = withContext(Dispatchers.IO) { request("GET", path, null) }

    suspend fun put(path: String, json: String): Boolean =
        withContext(Dispatchers.IO) { request("PUT", path, json) != null }
}

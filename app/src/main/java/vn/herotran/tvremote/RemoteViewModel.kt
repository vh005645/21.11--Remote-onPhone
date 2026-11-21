package vn.herotran.tvremote

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.math.abs
import kotlin.math.max

/** Trạng thái TV đọc từ node devices/{id}/status. */
data class TvStatus(
    val online: Boolean,
    val adbConnected: Boolean,
    val lastSeen: Long,
    val volume: Int,
    val muted: Boolean,
    val locked: Boolean,
    val showingImage: Boolean,
    val scheduleAt: Long,
    val scheduleType: String,
)

data class UiState(
    val deviceId: String = "",
    val dbUrl: String = "",
    val status: TvStatus? = null,
    val tvOnline: Boolean = false,
    val netError: Boolean = false,
    val lastResult: String = "",
    val screenshot: Bitmap? = null,
    val screenshotLoading: Boolean = false,
    val selMinutes: Int = 30,
    val selAction: String = "LOCK_NOW",
    val toast: String? = null,
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val DEFAULT_DB_URL = "https://YOUR-PROJECT-default-rtdb.firebaseio.com"
        private const val ONLINE_WINDOW_MS = 100_000L
    }

    private val prefs = app.getSharedPreferences("tv_remote", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        UiState(
            deviceId = prefs.getString("device_id", "") ?: "",
            dbUrl = prefs.getString("db_url", DEFAULT_DB_URL) ?: DEFAULT_DB_URL,
            selMinutes = prefs.getInt("sel_minutes", 30),
            selAction = prefs.getString("sel_action", "LOCK_NOW") ?: "LOCK_NOW",
        )
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var pollJob: Job? = null

    // ------------------------------------------------------------------ polling

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(3000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun urlOk(url: String) = url.startsWith("https://") && !url.contains("YOUR-PROJECT")

    private fun ready(s: UiState) = s.deviceId.isNotBlank() && urlOk(s.dbUrl)

    private suspend fun refresh() {
        val st = _ui.value
        if (!ready(st)) return
        val api = TvApi(st.dbUrl)
        val base = "devices/${st.deviceId}"
        val statusBody = api.get("$base/status")
        if (statusBody == null) {
            _ui.update { it.copy(netError = true) }
            return
        }
        val status = parseStatus(statusBody)
        val result = api.get("$base/response/last_result")
        _ui.update {
            it.copy(
                status = status,
                tvOnline = status?.let { s -> isOnline(s) } ?: false,
                netError = false,
                lastResult = parseString(result),
            )
        }
    }

    private fun isOnline(s: TvStatus): Boolean =
        s.online && s.lastSeen > 0 && abs(System.currentTimeMillis() - s.lastSeen) < ONLINE_WINDOW_MS

    private fun parseStatus(body: String): TvStatus? = try {
        val o = JSONObject(body)
        TvStatus(
            online = o.optBoolean("online", false),
            adbConnected = o.optBoolean("adb_connected", false),
            lastSeen = o.optLong("last_seen", 0),
            volume = o.optInt("volume", 0),
            muted = o.optBoolean("is_muted", false),
            locked = o.optBoolean("is_locked", false),
            showingImage = o.optBoolean("is_showing_image", false),
            scheduleAt = o.optLong("schedule_at", 0),
            scheduleType = o.optString("schedule_type", ""),
        )
    } catch (e: Exception) {
        null // "null" hoặc JSON hỏng
    }

    private fun parseString(body: String?): String {
        if (body == null) return ""
        return try {
            (JSONTokener(body).nextValue() as? String) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    // ------------------------------------------------------------------ cài đặt

    fun saveSettings(deviceId: String, dbUrl: String) {
        val id = deviceId.trim().uppercase()
        val url = dbUrl.trim().trimEnd('/')
        prefs.edit().putString("device_id", id).putString("db_url", url).apply()
        _ui.update { it.copy(deviceId = id, dbUrl = url, status = null, tvOnline = false, lastResult = "") }
        viewModelScope.launch { refresh() }
    }

    fun selectMinutes(m: Int) {
        prefs.edit().putInt("sel_minutes", m).apply()
        _ui.update { it.copy(selMinutes = m) }
    }

    fun selectAction(a: String) {
        prefs.edit().putString("sel_action", a).apply()
        _ui.update { it.copy(selAction = a) }
    }

    fun consumeToast() {
        _ui.update { it.copy(toast = null) }
    }

    private fun toast(msg: String) {
        _ui.update { it.copy(toast = msg) }
    }

    // ------------------------------------------------------------------ gửi lệnh

    private fun commandJson(action: String, payload: String): String =
        JSONObject()
            .put("action", action)
            .put("payload", payload)
            .put("timestamp", System.currentTimeMillis())
            .toString()

    fun send(action: String, payload: String = "", okMsg: String = "Đã gửi lệnh") {
        viewModelScope.launch {
            val st = _ui.value
            if (!ready(st)) {
                toast("Hãy nhập mã TV và URL Firebase (nút Cài đặt)")
                return@launch
            }
            val ok = TvApi(st.dbUrl).put("devices/${st.deviceId}/command", commandJson(action, payload))
            toast(if (ok) okMsg else "Không gửi được lệnh - kiểm tra mạng / URL Firebase")
            if (ok) {
                delay(1500)
                refresh()
            }
        }
    }

    fun lock() = send("LOCK_NOW", okMsg = "Đã gửi lệnh khóa TV")
    fun unlock() = send("UNLOCK", okMsg = "Đã gửi lệnh mở khóa")
    fun powerOff() = send("POWER_OFF", okMsg = "Đã gửi lệnh tắt nguồn TV")
    fun volUp() = send("VOL_UP", okMsg = "Tăng âm lượng")
    fun volDown() = send("VOL_DOWN", okMsg = "Giảm âm lượng")
    fun mute() = send("MUTE", okMsg = "Bật/tắt tiếng")
    fun setVolume(percent: Int) = send("SET_VOL", percent.coerceIn(0, 100).toString(), "Đã đặt âm lượng $percent%")

    fun sendMessage(text: String) {
        val t = text.trim()
        if (t.isEmpty()) {
            toast("Hãy nhập nội dung tin nhắn")
            return
        }
        send("SHOW_MSG", t, "Đã gửi tin nhắn lên TV")
    }

    fun startSchedule() {
        val s = _ui.value
        val payload = JSONObject().put("type", s.selAction).put("minutes", s.selMinutes).toString()
        send("SCHEDULE_ACTION", payload, "Đã gửi lệnh hẹn giờ")
    }

    fun cancelSchedule() = send("CANCEL_SCHEDULE", okMsg = "Đã gửi lệnh hủy hẹn giờ")

    fun hideImage() = send("HIDE_IMAGE", okMsg = "Đã gửi lệnh tắt ảnh")

    // ------------------------------------------------------------------ chụp màn hình

    fun requestScreenshot() {
        viewModelScope.launch {
            val st = _ui.value
            if (!ready(st)) {
                toast("Hãy nhập mã TV và URL Firebase (nút Cài đặt)")
                return@launch
            }
            if (st.screenshotLoading) return@launch
            _ui.update { it.copy(screenshotLoading = true) }
            val api = TvApi(st.dbUrl)
            val base = "devices/${st.deviceId}"
            val baseline = api.get("$base/response/screenshot_time")?.trim()?.toLongOrNull() ?: 0L
            val sent = api.put("$base/command", commandJson("SCREENSHOT", ""))
            if (!sent) {
                _ui.update { it.copy(screenshotLoading = false) }
                toast("Không gửi được lệnh - kiểm tra mạng / URL Firebase")
                return@launch
            }
            var got = false
            for (i in 1..40) {
                delay(1000)
                val t = api.get("$base/response/screenshot_time")?.trim()?.toLongOrNull() ?: 0L
                if (t != 0L && t != baseline) {
                    got = true
                    break
                }
            }
            if (!got) {
                _ui.update { it.copy(screenshotLoading = false) }
                toast("TV không phản hồi chụp màn hình (offline hoặc ADB chưa kết nối)")
                return@launch
            }
            val body = api.get("$base/response/screenshot_base64")
            val bmp = withContext(Dispatchers.Default) { decodeBase64Image(parseString(body)) }
            _ui.update { it.copy(screenshotLoading = false, screenshot = bmp ?: it.screenshot) }
            if (bmp == null) toast("Không đọc được ảnh từ TV")
            refresh()
        }
    }

    private fun decodeBase64Image(b64: String): Bitmap? {
        if (b64.isEmpty()) return null
        return try {
            val raw = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(raw, 0, raw.size)
        } catch (e: Exception) {
            null
        }
    }

    fun saveScreenshot() {
        val bmp = _ui.value.screenshot ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cr = getApplication<Application>().contentResolver
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "tv_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TVRemote")
                }
                val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                if (uri == null) {
                    toast("Không lưu được ảnh")
                } else {
                    cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                    toast("Đã lưu vào Pictures/TVRemote")
                }
            } catch (e: Exception) {
                toast("Không lưu được ảnh: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ gửi ảnh lên TV

    fun sendImage(uri: Uri) {
        viewModelScope.launch {
            toast("Đang xử lý ảnh...")
            val b64 = withContext(Dispatchers.Default) { encodeImage(uri) }
            if (b64 == null) {
                toast("Không đọc được ảnh")
                return@launch
            }
            send("SHOW_IMAGE", b64, "Đã gửi ảnh lên TV")
        }
    }

    private fun encodeImage(uri: Uri): String? {
        return try {
            val cr = getApplication<Application>().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null

            val orientation = cr.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val longest = max(bmp.width, bmp.height)
            val scale = if (longest > 1280) 1280f / longest else 1f
            if (degrees != 0f || scale != 1f) {
                val m = Matrix()
                if (scale != 1f) m.postScale(scale, scale)
                if (degrees != 0f) m.postRotate(degrees)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}

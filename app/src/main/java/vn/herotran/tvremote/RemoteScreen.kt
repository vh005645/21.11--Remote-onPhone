package vn.herotran.tvremote

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val QUICK_MESSAGES = listOf("Tắt TV đi ngủ thôi!", "Ra ăn cơm!", "Học bài đi con!", "Hết giờ xem TV rồi!")

@Composable
fun RemoteScreen(vm: RemoteViewModel) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current

    var showSettings by remember { mutableStateOf(false) }
    var showPowerConfirm by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }
    var messageText by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImage(uri)
    }

    LaunchedEffect(ui.toast) {
        val t = ui.toast
        if (t != null) {
            Toast.makeText(ctx, t, Toast.LENGTH_SHORT).show()
            vm.consumeToast()
        }
    }
    LaunchedEffect(Unit) {
        if (ui.deviceId.isBlank()) showSettings = true
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TopSection(ui, onSettings = { showSettings = true })

            // ---------------- Nguồn & bảo mật
            SectionCard("Nguồn & bảo mật") {
                val st = ui.status
                Text(
                    text = if (st == null) "Chưa có trạng thái"
                    else if (st.locked) "TV đang bị KHÓA" else "TV đang mở",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.lock() }, modifier = Modifier.weight(1f)) { Text("Khóa TV") }
                    OutlinedButton(onClick = { vm.unlock() }, modifier = Modifier.weight(1f)) { Text("Mở khóa TV") }
                }
                Button(
                    onClick = { showPowerConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Tắt nguồn TV") }
            }

            // ---------------- Âm thanh
            SectionCard("Âm thanh") {
                val st = ui.status
                var sliderVal by remember { mutableFloatStateOf(0f) }
                var dragging by remember { mutableStateOf(false) }
                LaunchedEffect(st?.volume) {
                    if (!dragging && st != null) sliderVal = st.volume.toFloat()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { vm.volDown() }, modifier = Modifier.weight(1f)) { Text("Giảm -") }
                    OutlinedButton(onClick = { vm.mute() }, modifier = Modifier.weight(1f)) {
                        Text(if (st?.muted == true) "Bật tiếng" else "Tắt tiếng")
                    }
                    OutlinedButton(onClick = { vm.volUp() }, modifier = Modifier.weight(1f)) { Text("Tăng +") }
                }
                Text("Âm lượng: ${sliderVal.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = sliderVal,
                    onValueChange = {
                        dragging = true
                        sliderVal = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        vm.setVolume(sliderVal.roundToInt())
                    },
                    valueRange = 0f..100f,
                )
            }

            // ---------------- Tin nhắn
            SectionCard("Gửi tin nhắn lên TV") {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nội dung tin nhắn") },
                    maxLines = 3,
                )
                Button(
                    onClick = {
                        vm.sendMessage(messageText)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gửi") }
                QUICK_MESSAGES.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        pair.forEach { m ->
                            OutlinedButton(
                                onClick = { vm.sendMessage(m) },
                                modifier = Modifier.weight(1f),
                            ) { Text(m, fontSize = 13.sp) }
                        }
                    }
                }
            }

            // ---------------- Hẹn giờ
            SectionCard("Hẹn giờ tự động") {
                Text(scheduleText(ui.status), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(15, 30, 45, 60).forEach { m ->
                        if (ui.selMinutes == m) {
                            Button(onClick = { vm.selectMinutes(m) }, modifier = Modifier.weight(1f)) { Text("${m}p") }
                        } else {
                            OutlinedButton(onClick = { vm.selectMinutes(m) }, modifier = Modifier.weight(1f)) { Text("${m}p") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (ui.selAction == "LOCK_NOW") {
                        Button(onClick = { vm.selectAction("LOCK_NOW") }, modifier = Modifier.weight(1f)) { Text("Khóa TV") }
                        OutlinedButton(onClick = { vm.selectAction("POWER_OFF") }, modifier = Modifier.weight(1f)) { Text("Tắt TV") }
                    } else {
                        OutlinedButton(onClick = { vm.selectAction("LOCK_NOW") }, modifier = Modifier.weight(1f)) { Text("Khóa TV") }
                        Button(onClick = { vm.selectAction("POWER_OFF") }, modifier = Modifier.weight(1f)) { Text("Tắt TV") }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { vm.startSchedule() }, modifier = Modifier.weight(1f)) { Text("Bắt đầu hẹn giờ") }
                    OutlinedButton(onClick = { vm.cancelSchedule() }, modifier = Modifier.weight(1f)) { Text("Hủy hẹn giờ") }
                }
            }

            // ---------------- Giám sát màn hình
            SectionCard("Giám sát màn hình TV") {
                Button(
                    onClick = { vm.requestScreenshot() },
                    enabled = !ui.screenshotLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.screenshotLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Đang chụp...")
                    } else {
                        Text("Chụp màn hình TV")
                    }
                }
                val shot = ui.screenshot
                if (shot != null) {
                    Image(
                        bitmap = shot.asImageBitmap(),
                        contentDescription = "Ảnh chụp màn hình TV",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(shot.width.toFloat() / shot.height.toFloat())
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showFullImage = true },
                    )
                    Text("Bấm vào ảnh để xem toàn màn hình / lưu", style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------------- Gửi ảnh lên TV
            SectionCard("Hiển thị ảnh lên TV") {
                if (ui.status?.showingImage == true) {
                    Text("TV đang hiển thị ảnh", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("Chọn & gửi ảnh") }
                    OutlinedButton(onClick = { vm.hideImage() }, modifier = Modifier.weight(1f)) { Text("Tắt ảnh trên TV") }
                }
            }

            // ---------------- Kết quả gần nhất
            if (ui.lastResult.isNotEmpty()) {
                Text(
                    "Phản hồi từ TV: ${ui.lastResult}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------------- Hộp thoại
    if (showPowerConfirm) {
        AlertDialog(
            onDismissRequest = { showPowerConfirm = false },
            title = { Text("Tắt nguồn TV?") },
            text = { Text("TV sẽ tắt hẳn và không thể điều khiển từ xa cho đến khi bật lại bằng tay.") },
            confirmButton = {
                TextButton(onClick = {
                    showPowerConfirm = false
                    vm.powerOff()
                }) { Text("Tắt TV") }
            },
            dismissButton = { TextButton(onClick = { showPowerConfirm = false }) { Text("Hủy") } },
        )
    }

    if (showSettings) {
        SettingsDialog(
            deviceId = ui.deviceId,
            dbUrl = ui.dbUrl,
            onDismiss = { showSettings = false },
            onSave = { id, url ->
                vm.saveSettings(id, url)
                showSettings = false
            },
        )
    }

    val shot = ui.screenshot
    if (showFullImage && shot != null) {
        Dialog(
            onDismissRequest = { showFullImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                Image(
                    bitmap = shot.asImageBitmap(),
                    contentDescription = "Ảnh chụp màn hình TV (toàn màn hình)",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { showFullImage = false },
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { vm.saveScreenshot() }) { Text("Lưu ảnh") }
                    OutlinedButton(onClick = { showFullImage = false }) { Text("Đóng") }
                }
            }
        }
    }
}

@Composable
private fun TopSection(ui: UiState, onSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("TV Remote", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (ui.deviceId.isBlank()) "Chưa nhập mã TV" else "Mã TV: ${ui.deviceId}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = onSettings) { Text("Cài đặt") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = if (ui.tvOnline) "TV Online" else "TV Offline",
                color = if (ui.tvOnline) Color(0xFF2E7D32) else Color(0xFFC62828),
            )
            val adbOk = ui.tvOnline && ui.status?.adbConnected == true
            StatusPill(
                text = if (adbOk) "ADB Ready" else "ADB chưa sẵn sàng",
                color = if (adbOk) Color(0xFF1565C0) else Color(0xFF757575),
            )
            if (ui.netError) {
                StatusPill(text = "Lỗi mạng", color = Color(0xFFEF6C00))
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(50), color = color) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SettingsDialog(
    deviceId: String,
    dbUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var id by remember { mutableStateOf(deviceId) }
    var url by remember { mutableStateOf(dbUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt kết nối") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Mã TV hiển thị trong màn hình cài đặt ẩn trên TV (bấm phím Menu 5 lần).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("Mã TV (device_id)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL Firebase Realtime Database") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(id, url) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

private fun scheduleText(st: TvStatus?): String {
    if (st == null) return "Chưa có trạng thái từ TV"
    val now = System.currentTimeMillis()
    if (st.scheduleAt <= now) return "Không giới hạn thời gian xem TV"
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.scheduleAt))
    val verb = if (st.scheduleType == "POWER_OFF") "tắt" else "khóa"
    val left = ((st.scheduleAt - now) / 60000L).toInt() + 1
    return "$clock sẽ $verb TV (còn khoảng $left phút)"
}

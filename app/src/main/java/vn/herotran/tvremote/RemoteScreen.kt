package vn.herotran.tvremote

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val BtnShape = RoundedCornerShape(8.dp)
private val BtnPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)

private val ColBlue = Color(0xFF1E88E5)
private val ColGreen = Color(0xFF2E9E5B)
private val ColOrange = Color(0xFFEF6C00)
private val ColRed = Color(0xFFD32F2F)
private val ColPurple = Color(0xFF7E57C2)
private val ColTeal = Color(0xFF00897B)

/** Nút chữ nhật bo góc nhẹ: filled = nền đặc, ngược lại = viền màu. */
@Composable
private fun ColorButton(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    filled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val m = modifier.heightIn(min = 48.dp)
    if (filled) {
        Button(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = BtnShape,
            contentPadding = BtnPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = Color.White,
                disabledContainerColor = color.copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.75f),
            ),
            content = content,
        )
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = m,
            enabled = enabled,
            shape = BtnShape,
            contentPadding = BtnPadding,
            border = BorderStroke(1.5.dp, color),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            content = content,
        )
    }
}

@Composable
private fun BlinkText(text: String) {
    val transition = rememberInfiniteTransition(label = "blink")
    val a by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(animation = tween(650), repeatMode = RepeatMode.Reverse),
        label = "blinkAlpha",
    )
    Text(
        text = text,
        maxLines = 1,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.alpha(a),
    )
}

@Composable
private fun BtnText(text: String) {
    Text(
        text = text,
        maxLines = 1,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
    )
}

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
        if (ui.dbUrl.isBlank() || ui.dbUrl.contains("YOUR-PROJECT")) showSettings = true
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

            // ---------------- Giám sát màn hình (đầu trang)
            SectionCard("Giám sát màn hình TV") {
                ColorButton(
                    onClick = { vm.requestScreenshot() },
                    color = ColBlue,
                    enabled = !ui.screenshotLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (ui.screenshotLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        BtnText("Đang chụp...")
                    } else {
                        BtnText("Chụp màn hình TV")
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
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFullImage = true },
                    )
                    Text("Bấm vào ảnh để xem toàn màn hình / lưu", style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---------------- Nguồn & bảo mật
            SectionCard("Nguồn & bảo mật") {
                val st = ui.status
                Text(
                    text = if (st == null) "Chưa có trạng thái"
                    else if (st.locked) "TV đang bị KHÓA" else "TV đang mở",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ColorButton(onClick = { vm.lock() }, color = ColOrange, modifier = Modifier.weight(1f)) { BtnText("Khóa TV") }
                    ColorButton(onClick = { vm.unlock() }, color = ColGreen, modifier = Modifier.weight(1f)) { BtnText("Mở khóa TV") }
                }
                ColorButton(
                    onClick = { showPowerConfirm = true },
                    color = ColRed,
                    modifier = Modifier.fillMaxWidth(),
                ) { BtnText("Tắt nguồn TV") }
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
                    ColorButton(onClick = { vm.volUp() }, color = ColBlue, modifier = Modifier.weight(1f)) { BtnText("+ âm") }
                    ColorButton(onClick = { vm.volDown() }, color = ColBlue, modifier = Modifier.weight(1f)) { BtnText("- âm") }
                    ColorButton(onClick = { vm.mute() }, color = ColPurple, modifier = Modifier.weight(1f)) { BtnText("Mute") }
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
                    colors = SliderDefaults.colors(
                        thumbColor = ColBlue,
                        activeTrackColor = ColBlue,
                    ),
                )
            }

            // ---------------- Tin nhắn
            SectionCard("Gửi tin nhắn lên TV") {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BtnShape,
                    label = { Text("Nội dung tin nhắn") },
                    maxLines = 3,
                )
                ColorButton(
                    onClick = { vm.sendMessage(messageText) },
                    color = ColTeal,
                    modifier = Modifier.fillMaxWidth(),
                ) { BtnText("Gửi") }
            }

            // ---------------- Hẹn giờ
            SectionCard("Hẹn giờ tự động") {
                Text(scheduleText(ui.status), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = ui.minutesText,
                    onValueChange = { vm.setMinutesText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = BtnShape,
                    singleLine = true,
                    enabled = !ui.timerActive,
                    label = { Text("Số phút hẹn giờ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ColorButton(
                        onClick = { vm.selectAction("LOCK_NOW") },
                        color = ColBlue,
                        filled = ui.selAction == "LOCK_NOW",
                        modifier = Modifier.weight(1f),
                    ) { BtnText("Khóa TV") }
                    ColorButton(
                        onClick = { vm.selectAction("POWER_OFF") },
                        color = ColBlue,
                        filled = ui.selAction == "POWER_OFF",
                        modifier = Modifier.weight(1f),
                    ) { BtnText("Tắt TV") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ColorButton(
                        onClick = { if (!ui.timerActive) vm.startSchedule() },
                        color = ColGreen,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (ui.timerActive) BlinkText("Đang hẹn giờ") else BtnText("Bắt đầu hẹn")
                    }
                    ColorButton(onClick = { vm.cancelSchedule() }, color = ColRed, filled = false, modifier = Modifier.weight(1f)) { BtnText("Hủy hẹn giờ") }
                }
            }

            // ---------------- Gửi ảnh lên TV
            SectionCard("Hiển thị ảnh lên TV") {
                if (ui.status?.showingImage == true) {
                    Text("TV đang hiển thị ảnh", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ColorButton(onClick = { picker.launch("image/*") }, color = ColPurple, modifier = Modifier.weight(1f)) { BtnText("Chọn & gửi ảnh") }
                    ColorButton(onClick = { vm.hideImage() }, color = ColRed, filled = false, modifier = Modifier.weight(1f)) { BtnText("Tắt ảnh trên TV") }
                }
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
            dbUrl = ui.dbUrl,
            onDismiss = { showSettings = false },
            onSave = { url ->
                vm.saveSettings(url)
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
                    ColorButton(onClick = { vm.saveScreenshot() }, color = ColGreen) { BtnText("Lưu ảnh") }
                    ColorButton(onClick = { showFullImage = false }, color = Color.White, filled = false) { BtnText("Đóng") }
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
                    if (ui.dbUrl.isBlank() || ui.dbUrl.contains("YOUR-PROJECT")) "Chưa nhập link Firebase" else "Đã cấu hình Firebase",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            ColorButton(onClick = onSettings, color = ColBlue, filled = false) { BtnText("Cài đặt") }
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
        if (ui.lastResult.isNotEmpty()) {
            Text(
                "Phản hồi từ TV: ${ui.lastResult}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
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
    dbUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember { mutableStateOf(dbUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cài đặt kết nối") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Dán link Firebase Realtime Database (cùng link đã đặt trong app TV).",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Link Firebase") },
                    singleLine = true,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url) }) { Text("Lưu") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Đóng") } },
    )
}

private fun scheduleText(st: TvStatus?): String {
    if (st == null) return "Không hẹn giờ"
    val now = System.currentTimeMillis()
    if (st.scheduleAt <= now) return "Không hẹn giờ"
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(st.scheduleAt))
    val verb = if (st.scheduleType == "POWER_OFF") "tắt" else "khóa"
    val left = ((st.scheduleAt - now) / 60000L).toInt() + 1
    return "$clock sẽ $verb TV (còn khoảng $left phút)"
}

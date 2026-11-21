package vn.herotran.tvremote

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFEF6C00),
    background = Color(0xFFF4F6FA),
    surface = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF82B1FF),
    secondary = Color(0xFF4DB6AC),
    tertiary = Color(0xFFFFB74D),
    background = Color(0xFF10141C),
    surface = Color(0xFF1B212C),
)

@Composable
fun TvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

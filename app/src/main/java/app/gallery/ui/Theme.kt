package app.gallery.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.gallery.data.Settings

private val Light = lightColorScheme(
    primary = Color(0xFF3F5BD8), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE2FF), onPrimaryContainer = Color(0xFF00105C),
    secondary = Color(0xFF5B5D72), tertiary = Color(0xFF00897B),
    background = Color(0xFFFBF8FF), surface = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4), surfaceContainerHigh = Color(0xFFE9E7EF), surfaceContainerHighest = Color(0xFFE3E1E9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB8C4FF), onPrimary = Color(0xFF0B2491),
    primaryContainer = Color(0xFF2840B8), onPrimaryContainer = Color(0xFFDDE2FF),
    secondary = Color(0xFFC4C5DD), tertiary = Color(0xFF5EDBC8),
    background = Color(0xFF121318), surface = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13), surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F), surfaceContainerHighest = Color(0xFF34343A),
)

private val type = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
    val mode by Settings.theme.collectAsState()
    val dynamic by Settings.dynamicColor.collectAsState()
    val amoled by Settings.amoled.collectAsState()
    val dark = when (mode) {
        Settings.ThemeMode.System -> isSystemInDarkTheme()
        Settings.ThemeMode.Light -> false
        Settings.ThemeMode.Dark -> true
    }
    val ctx = LocalContext.current
    var scheme: ColorScheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    if (dark && amoled) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0C), surfaceContainer = Color(0xFF111114),
    )
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}

val Caption = TextStyle(fontSize = 12.sp)

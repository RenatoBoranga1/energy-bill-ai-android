package br.com.energybillai.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val CpflBlue = Color(0xFF0057B8)
private val CpflBlueDeep = Color(0xFF073B7A)
private val CpflYellow = Color(0xFFFFC72C)
private val CpflYellowSoft = Color(0xFFFFE07A)
private val WhiteCloud = Color(0xFFF8FBFF)
private val BlueMist = Color(0xFFE9F1FB)
private val Ink = Color(0xFF10243E)
private val DarkSurface = Color(0xFF0F1A2A)
private val DarkCard = Color(0xFF172537)

private val LightColors: ColorScheme = lightColorScheme(
    primary = CpflBlue,
    onPrimary = Color.White,
    primaryContainer = BlueMist,
    onPrimaryContainer = CpflBlueDeep,
    secondary = CpflYellow,
    onSecondary = CpflBlueDeep,
    secondaryContainer = CpflYellowSoft,
    onSecondaryContainer = CpflBlueDeep,
    background = WhiteCloud,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = BlueMist,
    onSurfaceVariant = CpflBlueDeep,
    outline = Color(0xFF9AB1CB),
    error = Color(0xFFC62828),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF7FB8FF),
    onPrimary = CpflBlueDeep,
    primaryContainer = Color(0xFF184E92),
    onPrimaryContainer = Color.White,
    secondary = CpflYellow,
    onSecondary = CpflBlueDeep,
    secondaryContainer = Color(0xFF6B5609),
    onSecondaryContainer = Color.White,
    background = DarkSurface,
    onBackground = Color(0xFFF2F6FA),
    surface = DarkCard,
    onSurface = Color(0xFFF2F6FA),
    surfaceVariant = Color(0xFF223246),
    onSurfaceVariant = Color(0xFFD7E4F5),
    outline = Color(0xFF6983A3),
    error = Color(0xFFFF8A80),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun EnergyBillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

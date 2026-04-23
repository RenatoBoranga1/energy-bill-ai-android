package br.com.energybillai.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BrandBlue = Color(0xFF163E63)
private val BrandBlueDeep = Color(0xFF0B2747)
private val BrandBlueSoft = Color(0xFFDCEBFA)
private val BrandGreen = Color(0xFF18A76E)
private val BrandGreenSoft = Color(0xFFD9F6E7)
private val CloudWhite = Color(0xFFF4F8FC)
private val SurfaceMist = Color(0xFFEAF1F8)
private val InkBlue = Color(0xFF13263D)
private val DarkSurface = Color(0xFF081524)
private val DarkCard = Color(0xFF102133)

private val LightColors: ColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BrandBlueSoft,
    onPrimaryContainer = BrandBlueDeep,
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = BrandGreenSoft,
    onSecondaryContainer = BrandBlueDeep,
    background = CloudWhite,
    onBackground = InkBlue,
    surface = Color.White,
    onSurface = InkBlue,
    surfaceVariant = SurfaceMist,
    onSurfaceVariant = Color(0xFF4F647B),
    outline = Color(0xFFA7B7C9),
    outlineVariant = Color(0xFFD8E3EE),
    surfaceTint = BrandBlue,
    error = Color(0xFFC43D3D),
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF91BCFF),
    onPrimary = BrandBlueDeep,
    primaryContainer = BrandBlue,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4CD39E),
    onSecondary = BrandBlueDeep,
    secondaryContainer = Color(0xFF174A37),
    onSecondaryContainer = Color(0xFFE8FFF5),
    background = DarkSurface,
    onBackground = Color(0xFFF1F6FB),
    surface = DarkCard,
    onSurface = Color(0xFFF1F6FB),
    surfaceVariant = Color(0xFF162A40),
    onSurfaceVariant = Color(0xFFD1DFEE),
    outline = Color(0xFF7288A2),
    outlineVariant = Color(0xFF2B425A),
    surfaceTint = Color(0xFF91BCFF),
    error = Color(0xFFFF8E88),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun EnergyBillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

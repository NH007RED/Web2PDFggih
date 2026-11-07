package com.nhdeveloper.web2pdf.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF4527A0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DDFF),
    onPrimaryContainer = Color(0xFF1B0A5A),
    secondary = Color(0xFF8E4FE0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1E1FF),
    onSecondaryContainer = Color(0xFF2E0B57),
    tertiary = Color(0xFF6A3FB5),
    background = Color(0xFFF6F2FF),
    onBackground = Color(0xFF1C1B22),
    surface = Color.White,
    onSurface = Color(0xFF1C1B22),
    surfaceVariant = Color(0xFFECE6F7),
    onSurfaceVariant = Color(0xFF4A4558),
    outline = Color(0xFF7A7389),
    outlineVariant = Color(0xFFD6CEE6),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCFBCFF),
    onPrimary = Color(0xFF2F1578),
    primaryContainer = Color(0xFF45309A),
    onPrimaryContainer = Color(0xFFE9DEFF),
    secondary = Color(0xFFDDB8FF),
    onSecondary = Color(0xFF42186F),
    secondaryContainer = Color(0xFF5A3A8A),
    onSecondaryContainer = Color(0xFFF3DDFF),
    tertiary = Color(0xFFD0BCFF),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE6E0E9),
    surface = Color(0xFF1E1A26),
    onSurface = Color(0xFFE6E0E9),
    surfaceVariant = Color(0xFF2B2635),
    onSurfaceVariant = Color(0xFFCBC4D6),
    outline = Color(0xFF948DA3),
    outlineVariant = Color(0xFF433E50),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val base = Typography()
private val AppTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun Web2PdfTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colors.background.toArgb()
                window.navigationBarColor = colors.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}

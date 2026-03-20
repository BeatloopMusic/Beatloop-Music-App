package com.beatloop.music.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Beatloop Brand Colors
val BeatloopGreen = Color(0xFF1DB954)
val BeatloopGreenLight = Color(0xFF33D17A)
val BeatloopGreenDark = Color(0xFF0E9E46)

// Dark Theme Colors
private val DarkColorScheme = darkColorScheme(
    primary = BeatloopGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0F3C23),
    onPrimaryContainer = Color(0xFFA9F6C8),
    secondary = Color(0xFF95D6CB),
    onSecondary = Color(0xFF04211B),
    secondaryContainer = Color(0xFF1A3A32),
    onSecondaryContainer = Color(0xFFC8F5EB),
    tertiary = Color(0xFFFFC96A),
    onTertiary = Color.Black,
    background = Color(0xFF0A1010),
    onBackground = Color(0xFFE8F3EF),
    surface = Color(0xFF131A19),
    onSurface = Color(0xFFE8F3EF),
    surfaceVariant = Color(0xFF1F2A28),
    onSurfaceVariant = Color(0xFFB0C0BA),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = Color(0xFF344541),
    inverseSurface = Color(0xFFE3F0EC),
    inverseOnSurface = Color(0xFF111917),
    inversePrimary = BeatloopGreenDark
)

// Light Theme Colors
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0D8F3E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F4CD),
    onPrimaryContainer = Color(0xFF003917),
    secondary = Color(0xFF2F7A6C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7EEE5),
    onSecondaryContainer = Color(0xFF00211B),
    tertiary = Color(0xFF9C5E00),
    onTertiary = Color.White,
    background = Color(0xFFF4FBF8),
    onBackground = Color(0xFF111815),
    surface = Color.White,
    onSurface = Color(0xFF111815),
    surfaceVariant = Color(0xFFE3EFEB),
    onSurfaceVariant = Color(0xFF41514D),
    error = Color(0xFFB00020),
    onError = Color.White,
    outline = Color(0xFF6F807A),
    inverseSurface = Color(0xFF121212),
    inverseOnSurface = Color.White,
    inversePrimary = BeatloopGreen
)

// AMOLED Dark Theme
private val AmoledDarkColorScheme = darkColorScheme(
    primary = BeatloopGreen,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0B2B18),
    onPrimaryContainer = Color(0xFFA3F1C1),
    secondary = Color(0xFF91D2C8),
    onSecondary = Color(0xFF032019),
    secondaryContainer = Color(0xFF112A25),
    onSecondaryContainer = Color(0xFFC2F3E8),
    tertiary = Color(0xFFFFC96A),
    onTertiary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFE6F2EE),
    surface = Color(0xFF090D0C),
    onSurface = Color(0xFFE6F2EE),
    surfaceVariant = Color(0xFF141D1B),
    onSurfaceVariant = Color(0xFFAABBB6),
    error = Color(0xFFCF6679),
    onError = Color.Black,
    outline = Color(0xFF263230),
    inverseSurface = Color(0xFFE0EFEA),
    inverseOnSurface = Color.Black,
    inversePrimary = BeatloopGreenDark
)

@Composable
fun BeatloopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    amoledBlack: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        amoledBlack && darkTheme -> AmoledDarkColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.localllm.chat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// iOS-like colors
val iOSBlue = Color(0xFF007AFF)
val iOSLightGray = Color(0xFFE5E5EA)
val iOSDarkGray = Color(0xFF2C2C2E)
val iOSBackgroundLight = Color(0xFFFFFFFF)
val iOSBackgroundDark = Color(0xFF000000)

private val DarkColorScheme = darkColorScheme(
    primary = iOSBlue,
    onPrimary = Color.White,
    secondaryContainer = iOSDarkGray,
    onSecondaryContainer = Color.White,
    primaryContainer = iOSBlue,
    onPrimaryContainer = Color.White,
    background = iOSBackgroundDark,
    surface = iOSBackgroundDark,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = iOSBlue,
    onPrimary = Color.White,
    secondaryContainer = iOSLightGray,
    onSecondaryContainer = Color.Black,
    primaryContainer = iOSBlue,
    onPrimaryContainer = Color.White,
    background = iOSBackgroundLight,
    surface = iOSBackgroundLight,
    onBackground = Color.Black,
    onSurface = Color.Black
)

val AppTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.4).sp
    )
)

@Composable
fun LocalLLMChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}


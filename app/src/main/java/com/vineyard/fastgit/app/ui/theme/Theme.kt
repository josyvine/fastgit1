package com.vineyard.fastgit.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

private val DarkColorScheme = darkColorScheme(
    primary = GhAccentBlue,
    secondary = GhPrimaryViolet,
    tertiary = GhSuccessGreen,
    background = GhBgDark,
    surface = GhSurfaceDark,
    onPrimary = GhBgDark,
    onSecondary = GhTextPrimaryDark,
    onBackground = GhTextPrimaryDark,
    onSurface = GhTextPrimaryDark,
    outline = GhCardBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = GhAccentBlue,
    secondary = GhPrimaryViolet,
    tertiary = GhSuccessGreen,
    background = GhBgLight,
    surface = GhSurfaceLight,
    onPrimary = GhSurfaceLight,
    onSecondary = GhTextPrimaryLight,
    onBackground = GhTextPrimaryLight,
    onSurface = GhTextPrimaryLight,
    outline = GhCardBorderLight
)

@Composable
fun FastGitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

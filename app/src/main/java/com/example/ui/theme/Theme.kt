package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MintLimePrimary,
    onPrimary = DarkGreenOnPrimary,
    secondary = MintLimeSecondary,
    onSecondary = DarkGreyOnSecondary,
    tertiary = AquaTertiary,
    background = NearBlackBackground,
    onBackground = PureWhiteText,
    surface = LightCharcoalSurface,
    onSurface = PureWhiteText,
    onSurfaceVariant = MutedGreyText,
    outline = BorderOutline
  )

private val LightColorScheme = DarkColorScheme // Unified dark theme

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme
  dynamicColor: Boolean = false, // Force custom dark theme colors
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

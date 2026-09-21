package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class ThemeMode {
  DARK,
  LIGHT,
  SYSTEM
}

private val DarkColorScheme =
  darkColorScheme(
    primary = MintLimePrimary,
    onPrimary = DarkGreenOnPrimary,
    primaryContainer = Color(0xFF003D1A),
    onPrimaryContainer = Color(0xFFB9F6CA),
    secondary = MintLimeSecondary,
    onSecondary = DarkGreyOnSecondary,
    secondaryContainer = Color(0xFF0C3854),
    onSecondaryContainer = Color(0xFFBAE6FD),
    tertiary = AquaTertiary,
    onTertiary = Color(0xFF452B00),
    background = NearBlackBackground,
    onBackground = PureWhiteText,
    surface = LightCharcoalSurface,
    surfaceVariant = Color(0xFF1B222B),
    onSurface = PureWhiteText,
    onSurfaceVariant = MutedGreyText,
    outline = BorderOutline,
    outlineVariant = Color(0xFF182029)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = Color(0xFFD1F2DF),
    onPrimaryContainer = Color(0xFF003822),
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = LightTertiary,
    onTertiary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    surfaceVariant = Color(0xFFEAF1EC),
    onSurface = LightOnSurface,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFCBD5E1)
  )

@Composable
fun MyApplicationTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }

  val context = LocalContext.current
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
  darkColorScheme(
    primary = EditorialPrimary,
    onPrimary = EditorialOnPrimary,
    primaryContainer = EditorialPrimaryContainer,
    onPrimaryContainer = EditorialOnPrimaryContainer,
    secondary = EditorialSecondary,
    onSecondary = EditorialOnSecondary,
    secondaryContainer = EditorialSecondaryContainer,
    onSecondaryContainer = EditorialPrimary,
    tertiary = EditorialTertiary,
    background = EditorialBg,
    onBackground = EditorialTextPrimary,
    surface = EditorialSurface,
    onSurface = EditorialTextPrimary,
    surfaceVariant = EditorialSurfaceVariant,
    onSurfaceVariant = EditorialTextDim,
    outline = EditorialOutline,
    outlineVariant = EditorialOutlineVariant,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = DarkColorScheme, typography = Typography, content = content)
}


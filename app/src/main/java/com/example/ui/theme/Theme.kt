package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = TerracottaLight,
    onPrimary = Color(0xFF3E1200),
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = TerracottaContainer,
    secondary = SageMint,
    onSecondary = Color(0xFF002E21),
    secondaryContainer = SageDarkContainer,
    onSecondaryContainer = SageContainer,
    tertiary = GoldLight,
    onTertiary = Color(0xFF3A2100),
    tertiaryContainer = OchreDarkContainer,
    onTertiaryContainer = OchreContainer,
    background = CharcoalBackground,
    onBackground = CreamInk,
    surface = WorkbenchSurface,
    onSurface = CreamInk,
    surfaceVariant = WorkbenchVariant,
    onSurfaceVariant = WarmGrayInk,
    outline = CharcoalOutline,
    outlineVariant = Color(0xFF322C27)
)

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = OchreTertiary,
    onTertiary = Color.White,
    tertiaryContainer = OchreContainer,
    onTertiaryContainer = OnOchreContainer,
    background = LinenBackground,
    onBackground = EspressoInk,
    surface = IvorySurface,
    onSurface = EspressoInk,
    surfaceVariant = WarmSandVariant,
    onSurfaceVariant = MutedUmber,
    outline = WarmStoneOutline,
    outlineVariant = Color(0xFFECE3D8)
)

val CraftShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun CraftCircleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = CraftShapes,
        content = content
    )
}

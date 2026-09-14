package pl.ruby.lubiechowlabs.autobus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF006B5E)
private val Teal = Color(0xFF325E69)
private val Gold = Color(0xFF785A00)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF2E0),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Teal,
    secondaryContainer = Color(0xFFBCEAF7),
    tertiary = Gold,
    tertiaryContainer = Color(0xFFFFE08B),
    surface = Color(0xFFFAFAF7),
    surfaceVariant = Color(0xFFE1E4DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF80D5C5),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005145),
    secondary = Color(0xFFA0CFDC),
    tertiary = Color(0xFFF1C453),
    surface = Color(0xFF111412),
    surfaceVariant = Color(0xFF414944),
)

@Composable
fun AutoBusTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

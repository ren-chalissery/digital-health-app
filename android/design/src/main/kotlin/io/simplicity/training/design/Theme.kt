package io.simplicity.training.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app icon's green, walked across lightness at a fixed hue of 151 degrees and 50 per cent
 * saturation. Shared verbatim with web (`styles.scss`) and iOS (`Tokens.swift`); changing a value
 * here means changing it in all three.
 *
 * These are the raw steps. Composables should read `MaterialTheme.colorScheme` instead, so that
 * the dark theme keeps working.
 */
object BrandRamp {
    val Step50 = Color(0xFFF0FAF5)
    val Step100 = Color(0xFFDDF4E8)
    val Step200 = Color(0xFFBAE8D2)
    val Step300 = Color(0xFF8CD9B4)
    val Step400 = Color(0xFF57C791)
    val Step500 = Color(0xFF38A872)
    val Step600 = Color(0xFF2B8258)
    val Step700 = Color(0xFF216343)
    val Step800 = Color(0xFF194D34)
    val Step900 = Color(0xFF123624)

    /** The two figures in the icon. Warm emphasis only; neither can carry text on white. */
    val Cream = Color(0xFFF4ECE1)
    val Sand = Color(0xFFE6D6BD)
}

/**
 * A fixed scale, identical to `--space-1` to `--space-7` on web and `Spacing` on iOS. A screen
 * needing a value that is not on the scale should move the scale rather than reach for a literal.
 */
object Spacing {
    val x1: Dp = 4.dp
    val x2: Dp = 8.dp
    val x3: Dp = 12.dp
    val x4: Dp = 16.dp
    val x5: Dp = 24.dp
    val x6: Dp = 32.dp
    val x7: Dp = 48.dp
}

/**
 * Kept separate from [Spacing] because the two are not interchangeable: a control whose radius
 * happens to equal its padding does so by coincidence.
 */
object Radius {
    val Small: Dp = 6.dp
    val Medium: Dp = 10.dp
    val Large: Dp = 16.dp
}

object Layout {
    /** The Material accessibility minimum for anything tappable. */
    val MinimumTapTarget: Dp = 48.dp
}

// `onPrimary` is white in light and near-black in dark, because the primary itself flips from
// step 700 to step 400 — step 700 on a dark surface is 1.6:1 and effectively invisible.
private val LightColours = lightColorScheme(
    primary = BrandRamp.Step700,
    onPrimary = Color.White,
    primaryContainer = BrandRamp.Step100,
    onPrimaryContainer = BrandRamp.Step900,
    secondary = BrandRamp.Step600,
    onSecondary = Color.White,
    secondaryContainer = BrandRamp.Step100,
    onSecondaryContainer = BrandRamp.Step900,
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF16211B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16211B),
    surfaceVariant = Color(0xFFEFF3F0),
    onSurfaceVariant = Color(0xFF5A6B61),
    // 3.1:1 against the surface, which is what the guidelines ask of a control boundary.
    outline = Color(0xFF7C988A),
    outlineVariant = Color(0xFFDCE4DE),
    error = Color(0xFFA9261F),
    onError = Color.White,
    errorContainer = Color(0xFFFBECEB),
    onErrorContainer = Color(0xFF5C1410),
)

private val DarkColours = darkColorScheme(
    primary = BrandRamp.Step400,
    onPrimary = Color(0xFF0F1613),
    primaryContainer = Color(0xFF17301F),
    onPrimaryContainer = BrandRamp.Step100,
    secondary = BrandRamp.Step300,
    onSecondary = Color(0xFF0F1613),
    secondaryContainer = Color(0xFF17301F),
    onSecondaryContainer = BrandRamp.Step100,
    background = Color(0xFF0F1613),
    onBackground = Color(0xFFE8EFEA),
    surface = Color(0xFF16211B),
    onSurface = Color(0xFFE8EFEA),
    surfaceVariant = Color(0xFF1D2A23),
    onSurfaceVariant = Color(0xFF9CB0A4),
    outline = Color(0xFF586F64),
    outlineVariant = Color(0xFF2A3B32),
    error = Color(0xFFF2857E),
    onError = Color(0xFF0F1613),
    errorContainer = Color(0xFF2E1614),
    onErrorContainer = Color(0xFFFBECEB),
)

/**
 * Wraps the application once, at the root.
 *
 * Deliberately no dynamic colour: Material You would repaint a clinical product in whatever the
 * user's wallpaper happens to be, and the whole point of this change is that the three clients
 * look like one product.
 */
@Composable
fun SimplicityTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColours else LightColours,
        content = content,
    )
}

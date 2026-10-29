package it.vfsfitvnm.vimusic.ui.styling

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import it.vfsfitvnm.vimusic.enums.ColorPaletteMode
import it.vfsfitvnm.vimusic.enums.ColorPaletteName

@Immutable
data class ColorPalette(
    val background0: Color,
    val background1: Color,
    val background2: Color,
    val background3: Color,
    val background4: Color,
    val accent: Color,
    val onAccent: Color,
    val red: Color = Color(0xffbf4040),
    val blue: Color = Color(0xff4472cf),
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val isDark: Boolean,
    val iconButtonPlayer: Color,
) {
    companion object : Saver<ColorPalette, List<Any>> {
        override fun restore(value: List<Any>) = when (val accent = value[0] as Int) {
            0 -> DefaultDarkColorPalette
            1 -> DefaultLightColorPalette
            2 -> PureBlackColorPalette
            3 -> ModernBlackColorPalette
            else -> dynamicColorPaletteOf(
                FloatArray(3).apply { ColorUtils.colorToHSL(accent, this) },
                value[1] as Boolean
            )
        }

        override fun SaverScope.save(value: ColorPalette) =
            listOf(
                when {
                    value === DefaultDarkColorPalette -> 0
                    value === DefaultLightColorPalette -> 1
                    value === PureBlackColorPalette -> 2
                    value === ModernBlackColorPalette -> 3
                    else -> value.accent.toArgb()
                },
                value.isDark
            )
    }
}



val DefaultDarkColorPalette = ColorPalette(
    background0 = Color(0xff0E1013),
    background1 = Color(0xff15181D),
    background2 = Color(0xff1E2228),
    background3 = Color(0xff2A2F37),
    background4 = Color(0xff212529),
    text = Color(0xffEEF1F4),
    textSecondary = Color(0xffA8B0BB),
    textDisabled = Color(0xff6D7681),
    iconButtonPlayer = Color(0xffEEF1F4),
    accent = Color(0xFF2DBE64),
    onAccent = Color(0xFF05280F),
    isDark = true
)

val DefaultLightColorPalette = ColorPalette(
    background0 = Color(0xffFBFBFD),
    background1 = Color(0xffF3F5F8),
    background2 = Color(0xffE9ECF1),
    background3 = Color(0xffDFE3E9),
    background4 = Color(0xffEEF0F4),
    text = Color(0xff15171B),
    textSecondary = Color(0xff5A616B),
    textDisabled = Color(0xff9AA1AB),
    iconButtonPlayer = Color(0xff15171B),
    accent = Color(0xFF1B9E52),
    onAccent = Color.White,
    isDark = false
)

val PureBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color.Black,
    background2 = Color.Black,
    background3 = Color(0xff1B1B1B),
    background4 = Color(0xff151515)
    )

val ModernBlackColorPalette = DefaultDarkColorPalette.copy(
    background0 = Color.Black,
    background1 = Color(0xff0A0B0D),
    background2 = Color(0xff16181B),
    background3 = Color(0xff202327),
    background4 = Color(0xff141619)
)


fun colorPaletteOf(
    colorPaletteName: ColorPaletteName,
    colorPaletteMode: ColorPaletteMode,
    isSystemInDarkMode: Boolean
): ColorPalette {
    return when (colorPaletteName) {
        ColorPaletteName.Default, ColorPaletteName.Dynamic -> when (colorPaletteMode) {
            ColorPaletteMode.Light -> DefaultLightColorPalette
            ColorPaletteMode.Dark -> DefaultDarkColorPalette
            ColorPaletteMode.System -> when (isSystemInDarkMode) {
                true -> DefaultDarkColorPalette
                false -> DefaultLightColorPalette
            }
        }
        ColorPaletteName.PureBlack -> PureBlackColorPalette
        ColorPaletteName.ModernBlack -> ModernBlackColorPalette
    }
}

fun dynamicColorPaletteOf(bitmap: Bitmap, isDark: Boolean): ColorPalette? {
    val palette = Palette
        .from(bitmap)
        .maximumColorCount(8)
        .addFilter(if (isDark) ({ _, hsl -> hsl[0] !in 36f..100f }) else null)
        .generate()

    val hsl = if (isDark) {
        palette.dominantSwatch ?: Palette
            .from(bitmap)
            .maximumColorCount(8)
            .generate()
            .dominantSwatch
    } else {
        palette.dominantSwatch
    }?.hsl ?: return null

    return if (hsl[1] < 0.08) {
        val newHsl = palette.swatches
            .map(Palette.Swatch::getHsl)
            .sortedByDescending(FloatArray::component2)
            .find { it[1] != 0f }
            ?: hsl

        dynamicColorPaletteOf(newHsl, isDark)
    } else {
        dynamicColorPaletteOf(hsl, isDark)
    }
}

fun dynamicColorPaletteOf(hsl: FloatArray, isDark: Boolean): ColorPalette {
    val base = colorPaletteOf(
        ColorPaletteName.Dynamic,
        if (isDark) ColorPaletteMode.Dark else ColorPaletteMode.Light,
        false
    )

    val hue = hsl[0]
    val saturation = hsl[1]

    // Tinted neutrals: very low saturation keeps surfaces readable while the
    // lightness steps give real elevation instead of a flat background.
    val background0 = Color.hsl(hue, saturation.coerceAtMost(0.06f), if (isDark) 0.055f else 0.985f)
    val background1 = Color.hsl(hue, saturation.coerceAtMost(0.10f), if (isDark) 0.085f else 0.965f)
    val background2 = Color.hsl(hue, saturation.coerceAtMost(0.14f), if (isDark) 0.125f else 0.925f)
    val background3 = Color.hsl(hue, saturation.coerceAtMost(0.16f), if (isDark) 0.170f else 0.885f)
    val background4 = Color.hsl(hue, saturation.coerceAtMost(0.14f), if (isDark) 0.145f else 0.905f)

    val accent = Color.hsl(hue, saturation.coerceAtMost(0.55f), if (isDark) 0.55f else 0.40f)

    return base.copy(
        background0 = background0,
        background1 = background1,
        background2 = background2,
        background3 = background3,
        background4 = background4,
        accent = accent,
        // keep a legible foreground on the accent whatever its luminance is
        onAccent = if (accent.luminance() > 0.42f) Color(0xFF0B1410) else Color.White,
        text = Color.hsl(hue, saturation.coerceAtMost(0.02f), if (isDark) 0.92f else 0.11f),
        textSecondary = Color.hsl(hue, saturation.coerceAtMost(0.05f), if (isDark) 0.70f else 0.38f),
        textDisabled = Color.hsl(hue, saturation.coerceAtMost(0.05f), if (isDark) 0.45f else 0.62f),
        iconButtonPlayer = Color.hsl(hue, saturation.coerceAtMost(0.02f), if (isDark) 0.92f else 0.11f)
    )
}

inline val ColorPalette.collapsedPlayerProgressBar: Color
    get() = if (this === DefaultDarkColorPalette || this === DefaultLightColorPalette || this === PureBlackColorPalette) {
        text
    } else {
        accent
    }

inline val ColorPalette.favoritesIcon: Color
    get() = if (this === DefaultDarkColorPalette || this === DefaultLightColorPalette || this === PureBlackColorPalette) {
        red
    } else {
        accent
    }

inline val ColorPalette.shimmer: Color
    get() = if (this === DefaultDarkColorPalette || this === DefaultLightColorPalette || this === PureBlackColorPalette) {
        Color(0xff838383)
    } else {
        accent
    }

inline val ColorPalette.primaryButton: Color
    get() = if (this === PureBlackColorPalette) {
        Color(0xFF272727)
    } else {
        background2
    }

inline val ColorPalette.overlay: Color
    get() = PureBlackColorPalette.background0.copy(alpha = 0.5f)

inline val ColorPalette.onOverlay: Color
    get() = PureBlackColorPalette.text

inline val ColorPalette.onOverlayShimmer: Color
    get() = PureBlackColorPalette.shimmer

package com.craftworks.music.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.craftworks.music.managers.settings.AppearanceSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Themes [content] with colors taken from the artwork at [imageUrl] when the
 * "artwork colors" appearance setting is on. Used by the album, artist and playlist details screens.
 * [cacheKey] is the disk cache key the screen uses to load the same image.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ArtworkTheme(
    imageUrl: String?,
    cacheKey: String? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Starts from the last known value so the page doesn't wait for DataStore after the first time.
    val enabled by remember { AppearanceSettingsManager(context).detailsDynamicColorsFlow.onEach { lastEnabled = it } }
        .collectAsStateWithLifecycle(lastEnabled)
    val imageSource = imageUrl?.takeIf { it.isNotBlank() }
    // The page state follows the item rather than its URL: the URL can arrive after the page opened
    // (an artist link only knows the artist id until the artist is loaded).
    val itemKey = cacheKey?.takeIf { it.isNotBlank() } ?: imageSource
    val seedKey = itemKey.takeIf { enabled == true }

    val isTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val baseColorScheme = MaterialTheme.colorScheme
    val baseTvColorScheme = androidx.tv.material3.MaterialTheme.colorScheme
    val basePrimary = if (isTv) baseTvColorScheme.primary else baseColorScheme.primary
    val darkTheme = (if (isTv) baseTvColorScheme.background else baseColorScheme.background).luminance() < 0.5f

    var seed by remember(seedKey) { mutableStateOf(seedKey?.let { seedCache.get(it) }) }
    val animatedSeed = remember(seedKey) { Animatable(seed ?: basePrimary) }

    // The page stays hidden until the artwork colors are known, for at most SEED_WAIT_MS after it opened,
    // so it doesn't flash the app colors first. Colors found later fade in.
    val openedAt = remember(itemKey) { SystemClock.uptimeMillis() }
    var visible by remember(itemKey) { mutableStateOf(enabled == false || seed != null || itemKey == null) }
    LaunchedEffect(itemKey) {
        delay(SEED_WAIT_MS)
        visible = true
    }
    LaunchedEffect(seedKey, enabled) {
        if (enabled == false || seed != null || itemKey == null) visible = true
    }

    LaunchedEffect(seedKey, imageSource) {
        if (seedKey == null || seed != null) return@LaunchedEffect

        val newSeed = extractSeedColor(context, imageSource, cacheKey) ?: return@LaunchedEffect
        seedCache.put(seedKey, newSeed)
        seed = newSeed
        if (visible || SystemClock.uptimeMillis() - openedAt >= SEED_WAIT_MS) {
            visible = true
            animatedSeed.animateTo(newSeed, tween(450))
        } else {
            animatedSeed.snapTo(newSeed)
            visible = true
        }
    }

    val scheme = if (seed != null) remember(animatedSeed.value, darkTheme) { ArtworkScheme(animatedSeed.value, darkTheme) } else null

    // The tree stays the same whether or not artwork colors apply, so the screen keeps its state
    // when the colors arrive or the setting changes.
    val themedContent: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (visible) 1f else 0f }
                .background(scheme?.background ?: Color.Transparent)
        ) {
            content()
        }
    }

    if (isTv) {
        androidx.tv.material3.MaterialTheme(
            colorScheme = scheme?.toTvColorScheme() ?: baseTvColorScheme,
            typography = androidx.tv.material3.MaterialTheme.typography,
            shapes = androidx.tv.material3.MaterialTheme.shapes
        ) {
            MaterialTheme(colorScheme = scheme?.toColorScheme() ?: baseColorScheme, content = themedContent)
        }
    } else {
        MaterialExpressiveTheme(colorScheme = scheme?.toColorScheme() ?: baseColorScheme, content = themedContent)
    }
}

private const val SEED_WAIT_MS = 100L
private const val SEED_BITMAP_SIZE = 128

private val seedCache = LruCache<String, Color>(64)
private var lastEnabled: Boolean? = null

/**
 * Takes the seed color from the thumbnail the lists already loaded: Coil's memory cache first
 * (keyed by the image URL), then its disk cache (keyed by [cacheKey], the item id).
 * Only downloads the artwork when neither has it and an [imageUrl] is known.
 */
private suspend fun extractSeedColor(
    context: Context,
    imageUrl: String?,
    cacheKey: String?
): Color? {
    val loader = context.imageLoader
    val bitmap = imageUrl?.let { memoryCachedBitmap(loader, it) }
        ?: cacheKey?.let { diskCachedBitmap(loader, it) }
        ?: imageUrl?.let { downloadBitmap(context, loader, it, cacheKey) }
        ?: return null

    return withContext(Dispatchers.Default) {
        try {
            val palette = Palette.Builder(bitmap.toSoftware()).generate()

            // Favour colorful swatches that cover a good part of the artwork.
            palette.swatches
                .maxByOrNull { swatch ->
                    val saturation = swatch.hsl[1]
                    val lightness = swatch.hsl[2]
                    val colorfulness = saturation * (1f - kotlin.math.abs(lightness - 0.5f) * 2f)
                    swatch.population * (0.1f + colorfulness)
                }
                ?.rgb
                ?.let { Color(it) }
        } catch (e: Exception) {
            null
        }
    }
}

private fun memoryCachedBitmap(loader: ImageLoader, imageUrl: String): Bitmap? {
    val cache = loader.memoryCache ?: return null
    // The keys also hold the requested size, so pick the smallest bitmap cached for this URL.
    return cache.keys
        .filter { it.key == imageUrl }
        .mapNotNull { cache[it]?.bitmap }
        .minByOrNull { it.width * it.height }
}

private suspend fun diskCachedBitmap(loader: ImageLoader, cacheKey: String): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            loader.diskCache?.openSnapshot(cacheKey)?.use { snapshot ->
                val path = snapshot.data.toFile().path
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)

                var sampleSize = 1
                while (bounds.outWidth / (sampleSize * 2) >= SEED_BITMAP_SIZE &&
                    bounds.outHeight / (sampleSize * 2) >= SEED_BITMAP_SIZE
                ) sampleSize *= 2

                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        } catch (e: Exception) {
            null
        }
    }

private suspend fun downloadBitmap(
    context: Context,
    loader: ImageLoader,
    imageUrl: String,
    cacheKey: String?
): Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .apply { if (cacheKey != null) diskCacheKey(cacheKey) }
        .diskCachePolicy(CachePolicy.READ_ONLY)
        .allowHardware(false)
        .size(SEED_BITMAP_SIZE)
        .build()

    return (loader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
}

// Palette can't read hardware bitmaps, which is what Coil usually keeps in its memory cache.
private fun Bitmap.toSoftware(): Bitmap =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE)
        copy(Bitmap.Config.ARGB_8888, false)
    else this

/**
 * Tonal palettes built from a seed color in OkLCh, laid out like the Material 3 color roles.
 * Tones follow the Material scale (0 = black, 100 = white).
 */
private class ArtworkScheme(seed: Color, private val dark: Boolean) {
    private val primary: TonalPalette
    private val secondary: TonalPalette
    private val tertiary: TonalPalette
    private val neutral: TonalPalette
    private val neutralVariant: TonalPalette

    init {
        val (_, chroma, hue) = toOkLch(seed)
        // Grey artwork keeps a grey scheme, colorful artwork gets a clearly tinted one.
        val primaryChroma = if (chroma < 0.03f) chroma else chroma.coerceIn(0.09f, 0.16f)
        primary = TonalPalette(hue, primaryChroma)
        secondary = TonalPalette(hue, primaryChroma / 3f)
        tertiary = TonalPalette(hue + 60f, primaryChroma * 0.6f)
        neutral = TonalPalette(hue, (primaryChroma / 12f).coerceAtMost(0.012f))
        neutralVariant = TonalPalette(hue, (primaryChroma / 6f).coerceAtMost(0.024f))
    }

    val background: Color get() = neutral.tone(if (dark) 6 else 98)

    fun toColorScheme(): ColorScheme = if (dark) darkColorScheme(
        primary = primary.tone(80),
        onPrimary = primary.tone(20),
        primaryContainer = primary.tone(30),
        onPrimaryContainer = primary.tone(90),
        inversePrimary = primary.tone(40),
        secondary = secondary.tone(80),
        onSecondary = secondary.tone(20),
        secondaryContainer = secondary.tone(30),
        onSecondaryContainer = secondary.tone(90),
        tertiary = tertiary.tone(80),
        onTertiary = tertiary.tone(20),
        tertiaryContainer = tertiary.tone(30),
        onTertiaryContainer = tertiary.tone(90),
        background = neutral.tone(6),
        onBackground = neutral.tone(90),
        surface = neutral.tone(6),
        onSurface = neutral.tone(90),
        surfaceVariant = neutralVariant.tone(30),
        onSurfaceVariant = neutralVariant.tone(80),
        surfaceTint = primary.tone(80),
        inverseSurface = neutral.tone(90),
        inverseOnSurface = neutral.tone(20),
        outline = neutralVariant.tone(60),
        outlineVariant = neutralVariant.tone(30),
        surfaceBright = neutral.tone(24),
        surfaceDim = neutral.tone(6),
        surfaceContainerLowest = neutral.tone(4),
        surfaceContainerLow = neutral.tone(10),
        surfaceContainer = neutral.tone(12),
        surfaceContainerHigh = neutral.tone(17),
        surfaceContainerHighest = neutral.tone(22),
        primaryFixed = primary.tone(90),
        primaryFixedDim = primary.tone(80),
        onPrimaryFixed = primary.tone(10),
        onPrimaryFixedVariant = primary.tone(30),
        secondaryFixed = secondary.tone(90),
        secondaryFixedDim = secondary.tone(80),
        onSecondaryFixed = secondary.tone(10),
        onSecondaryFixedVariant = secondary.tone(30),
        tertiaryFixed = tertiary.tone(90),
        tertiaryFixedDim = tertiary.tone(80),
        onTertiaryFixed = tertiary.tone(10),
        onTertiaryFixedVariant = tertiary.tone(30),
    ) else lightColorScheme(
        primary = primary.tone(40),
        onPrimary = primary.tone(100),
        primaryContainer = primary.tone(90),
        onPrimaryContainer = primary.tone(10),
        inversePrimary = primary.tone(80),
        secondary = secondary.tone(40),
        onSecondary = secondary.tone(100),
        secondaryContainer = secondary.tone(90),
        onSecondaryContainer = secondary.tone(10),
        tertiary = tertiary.tone(40),
        onTertiary = tertiary.tone(100),
        tertiaryContainer = tertiary.tone(90),
        onTertiaryContainer = tertiary.tone(10),
        background = neutral.tone(98),
        onBackground = neutral.tone(10),
        surface = neutral.tone(98),
        onSurface = neutral.tone(10),
        surfaceVariant = neutralVariant.tone(90),
        onSurfaceVariant = neutralVariant.tone(30),
        surfaceTint = primary.tone(40),
        inverseSurface = neutral.tone(20),
        inverseOnSurface = neutral.tone(95),
        outline = neutralVariant.tone(50),
        outlineVariant = neutralVariant.tone(80),
        surfaceBright = neutral.tone(98),
        surfaceDim = neutral.tone(87),
        surfaceContainerLowest = neutral.tone(100),
        surfaceContainerLow = neutral.tone(96),
        surfaceContainer = neutral.tone(94),
        surfaceContainerHigh = neutral.tone(92),
        surfaceContainerHighest = neutral.tone(90),
        primaryFixed = primary.tone(90),
        primaryFixedDim = primary.tone(80),
        onPrimaryFixed = primary.tone(10),
        onPrimaryFixedVariant = primary.tone(30),
        secondaryFixed = secondary.tone(90),
        secondaryFixedDim = secondary.tone(80),
        onSecondaryFixed = secondary.tone(10),
        onSecondaryFixedVariant = secondary.tone(30),
        tertiaryFixed = tertiary.tone(90),
        tertiaryFixedDim = tertiary.tone(80),
        onTertiaryFixed = tertiary.tone(10),
        onTertiaryFixedVariant = tertiary.tone(30),
    )

    fun toTvColorScheme(): androidx.tv.material3.ColorScheme = if (dark) androidx.tv.material3.darkColorScheme(
        primary = primary.tone(80),
        onPrimary = primary.tone(20),
        primaryContainer = primary.tone(30),
        onPrimaryContainer = primary.tone(90),
        inversePrimary = primary.tone(40),
        secondary = secondary.tone(80),
        onSecondary = secondary.tone(20),
        secondaryContainer = secondary.tone(30),
        onSecondaryContainer = secondary.tone(90),
        tertiary = tertiary.tone(80),
        onTertiary = tertiary.tone(20),
        tertiaryContainer = tertiary.tone(30),
        onTertiaryContainer = tertiary.tone(90),
        background = neutral.tone(6),
        onBackground = neutral.tone(90),
        surface = neutral.tone(10),
        onSurface = neutral.tone(90),
        surfaceVariant = neutralVariant.tone(30),
        onSurfaceVariant = neutralVariant.tone(80),
        surfaceTint = primary.tone(80),
        inverseSurface = neutral.tone(90),
        inverseOnSurface = neutral.tone(20),
        border = neutralVariant.tone(60),
        borderVariant = neutralVariant.tone(30),
    ) else androidx.tv.material3.lightColorScheme(
        primary = primary.tone(40),
        onPrimary = primary.tone(100),
        primaryContainer = primary.tone(90),
        onPrimaryContainer = primary.tone(10),
        inversePrimary = primary.tone(80),
        secondary = secondary.tone(40),
        onSecondary = secondary.tone(100),
        secondaryContainer = secondary.tone(90),
        onSecondaryContainer = secondary.tone(10),
        tertiary = tertiary.tone(40),
        onTertiary = tertiary.tone(100),
        tertiaryContainer = tertiary.tone(90),
        onTertiaryContainer = tertiary.tone(10),
        background = neutral.tone(98),
        onBackground = neutral.tone(10),
        surface = neutral.tone(98),
        onSurface = neutral.tone(10),
        surfaceVariant = neutralVariant.tone(90),
        onSurfaceVariant = neutralVariant.tone(30),
        surfaceTint = primary.tone(40),
        inverseSurface = neutral.tone(20),
        inverseOnSurface = neutral.tone(95),
        border = neutralVariant.tone(50),
        borderVariant = neutralVariant.tone(80),
    )
}

private class TonalPalette(private val hue: Float, private val chroma: Float) {
    fun tone(tone: Int): Color = fromOkLch(toneToOkL(tone), chroma, hue)
}

// For greys OkLab lightness is cbrt(Y) and L* = 116 * cbrt(Y) - 16, so L* maps linearly onto OkLab L.
private fun toneToOkL(tone: Int): Float = ((tone + 16f) / 116f).coerceIn(0f, 1f)

private fun toOkLch(color: Color): Triple<Float, Float, Float> {
    val r = srgbToLinear(color.red)
    val g = srgbToLinear(color.green)
    val b = srgbToLinear(color.blue)

    val l = cbrt(0.4122214708f * r + 0.5363282080f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)

    val okL = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
    val okA = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
    val okB = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s

    val chroma = sqrt(okA * okA + okB * okB)
    val hue = Math.toDegrees(atan2(okB, okA).toDouble()).toFloat()
    return Triple(okL, chroma, hue)
}

/** Converts OkLCh to sRGB, lowering the chroma until the color fits in the sRGB gamut. */
private fun fromOkLch(lightness: Float, chroma: Float, hue: Float): Color {
    val radians = Math.toRadians(hue.toDouble())
    var low = 0f
    var high = chroma
    var best = okLabToLinearSrgb(lightness, 0f, 0f)

    okLabToLinearSrgb(lightness, chroma * cos(radians).toFloat(), chroma * sin(radians).toFloat())
        .takeIf { it.inGamut() }
        ?.let { return it.toColor() }

    repeat(12) {
        val mid = (low + high) / 2f
        val rgb = okLabToLinearSrgb(lightness, mid * cos(radians).toFloat(), mid * sin(radians).toFloat())
        if (rgb.inGamut()) {
            best = rgb
            low = mid
        } else {
            high = mid
        }
    }
    return best.toColor()
}

private fun okLabToLinearSrgb(okL: Float, okA: Float, okB: Float): FloatArray {
    val l = (okL + 0.3963377774f * okA + 0.2158037573f * okB).pow(3)
    val m = (okL - 0.1055613458f * okA - 0.0638541728f * okB).pow(3)
    val s = (okL - 0.0894841775f * okA - 1.2914855480f * okB).pow(3)

    return floatArrayOf(
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    )
}

private fun FloatArray.inGamut(): Boolean = all { it in -0.0001f..1.0001f }

private fun FloatArray.toColor(): Color = Color(
    red = linearToSrgb(this[0]),
    green = linearToSrgb(this[1]),
    blue = linearToSrgb(this[2]),
)

private fun srgbToLinear(value: Float): Float =
    if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(value: Float): Float {
    val v = value.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) v * 12.92f else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

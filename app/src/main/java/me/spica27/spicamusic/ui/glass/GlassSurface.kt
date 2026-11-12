/*
 * 玻璃背景链路 + 全屏底图。
 *
 * 对应 Kyant0/AndroidLiquidGlass 的 LayerBackdrop / drawBackdrop 调用链：
 *   参考库：LayerBackdrop { 背景内容 } ... drawBackdrop { blur() + lens() + vibrancy() }
 *   这里：  GlassBackdropLayer() ... LocalGlassHazeState ... glassSurface { hazeBlur + glassOptics }
 *
 * 之前所有玻璃控件都是「半透明色块 + 描边」，没有背景采样，叠在纯黑/纯白
 * 全屏底色上自然一片黑。本文件把这个链条补齐。
 */
package me.spica27.spicamusic.ui.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import me.spica27.spicamusic.R
import me.spica27.spicamusic.artwork.artwork
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.widget.AudioCover

/**
 * 全 App 共用的背景采样源。由 AppScaffold 在「底图 + 页面」的最外层提供，
 * 所有 glassSurface 控件都从它取背景，因此按钮、卡片、开关不再是纯色块。
 */
val LocalGlassHazeState = staticCompositionLocalOf<HazeState?> { null }

/** 亮/暗主题下自动推导的玻璃底色：玻璃是「提亮」而不是「压暗」。 */
object GlassTint {
    fun surface(isDark: Boolean): Color = if (isDark) Color.White.copy(alpha = 0.11f) else Color.White.copy(alpha = 0.52f)

    fun panel(isDark: Boolean): Color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.42f)

    fun control(isDark: Boolean): Color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)
}

/**
 * 一个玻璃控件的完整表面：真实背景模糊采样（有源时）+ Backdrop 光学装饰。
 *
 * 顺序很重要：glassOptics 是外层 draw 节点，hazeBlur 绘制于它的 drawContent()
 * 之内，因此「阴影在底、模糊背景在中、内容与轮廓高光在上」。
 */
@Composable
fun Modifier.glassSurface(
    spec: GlassSpec = GlassSpec.Card,
    shape: Shape = GlassShapes.Medium,
    tint: Color? = null,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val config = LocalLiquidGlassConfig.current
    val haze = LocalGlassHazeState.current
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    if (!config.enabled) {
        return this
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
    }

    val autoTint =
        tint ?: spec.tint.takeIf { it.alpha > 0f } ?: GlassTint.surface(isDark)
    val optics = this.glassOptics(spec = spec, shape = shape, enabled = true)

    return if (haze != null) {
        optics
            .clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(haze),
                style = glassBlurStyle(spec, scheme.surfaceContainerHigh, isDark),
                performanceMode = HazePerformanceMode.Balanced,
                expandLayerBounds = false,
            )
    } else {
        optics
            .clip(shape)
            .background(autoTint)
    }
}

/**
 * 参考库 vibrancy()：饱和度 1.5；再叠一层极淡的提亮底色。
 * 与旧版区别：移除了把面板染灰的深色 tint，并大幅降低采样失败时的回退不透明度，
 * 避免低版本 / 无 Haze 源时整块变黑。
 */
@Composable
internal fun glassBlurStyle(
    spec: GlassSpec,
    fallbackColor: Color,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
): HazeBlurStyle {
    val saturation = spec.vibrancy
    val inverseSaturation = 1f - saturation
    val luminanceRed = 0.213f * inverseSaturation
    val luminanceGreen = 0.715f * inverseSaturation
    val luminanceBlue = 0.072f * inverseSaturation
    return remember(spec, fallbackColor, isDark) {
        val colorEnhancement =
            ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        luminanceRed + saturation,
                        luminanceGreen,
                        luminanceBlue,
                        0f,
                        0f,
                        luminanceRed,
                        luminanceGreen + saturation,
                        luminanceBlue,
                        0f,
                        0f,
                        luminanceRed,
                        luminanceGreen,
                        luminanceBlue + saturation,
                        0f,
                        0f,
                        0f,
                        0f,
                        0f,
                        1f,
                        0f,
                    ),
                ),
            )
        HazeBlurStyle {
            blurRadius(spec.blurRadius)
            noiseFactor(0.02f)
            backgroundColor(
                if (isDark) {
                    Color.White.copy(alpha = 0.10f)
                } else {
                    Color.White.copy(alpha = 0.26f)
                },
            )
            colorEffects(listOf(HazeColorEffect.colorFilter(colorEnhancement)))
            fallbackColorEffect(
                HazeColorEffect.tint(
                    fallbackColor.copy(alpha = if (isDark) 0.26f else 0.18f),
                ),
            )
            alpha(1f)
        }
    }
}

/**
 * 主界面底图：当前播放封面的抽象化大图 + 主题色驱动的极光光斑 + 可读性遮罩。
 *
 * 参考 Kyant0 的 GlassPlaygroundContent / ControlCenterContent：玻璃必须有
 * 色彩丰富、明暗有层次的内容在背后，折射、色散与 vibrancy 才看得出来。
 */
@Composable
fun GlassBackdropLayer(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val base = if (isDark) Color(0xFF080C16) else Color(0xFFE9EEF6)

    val playerViewModel = LocalPlayerViewModel.current
    val mediaItem by playerViewModel.currentMediaItem.collectAsStateWithLifecycle()
    val artwork = mediaItem?.mediaMetadata?.artwork()

    val transition = rememberInfiniteTransition(label = "glassBackdrop")
    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 24000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "backdropPhase",
        )

    val spotA = accent
    val spotB = scheme.tertiary
    val spotC = scheme.secondary

    Box(modifier.fillMaxSize().background(base)) {
        // 1. 真实图像层：有封面用封面，没有时用自带的夜景场景图。
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.55f
                    scaleY = 1.55f
                    alpha = if (isDark) 0.42f else 0.30f
                }.blur(64.dp),
        ) {
            if (artwork != null && !artwork.isEmpty) {
                AudioCover(artwork = artwork, modifier = Modifier.fillMaxSize())
            } else {
                Image(
                    painter = painterResource(R.drawable.sleep_scene_night),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // 2. 极光光斑：缓慢呼吸，给玻璃提供可折射的色彩与明暗边界。
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val a = Offset(w * (0.16f + 0.08f * phase), h * (0.12f + 0.05f * (1f - phase)))
            val b = Offset(w * (0.88f - 0.07f * phase), h * (0.42f + 0.08f * phase))
            val c = Offset(w * (0.42f + 0.12f * phase), h * (0.92f - 0.06f * phase))
            val radius = maxOf(w, h) * 0.78f

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(spotA.copy(alpha = 0.46f), Color.Transparent),
                        center = a,
                        radius = radius,
                    ),
                radius = radius,
                center = a,
                blendMode = BlendMode.Plus,
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(spotB.copy(alpha = 0.36f), Color.Transparent),
                        center = b,
                        radius = radius * 0.92f,
                    ),
                radius = radius * 0.92f,
                center = b,
                blendMode = BlendMode.Plus,
            )
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(spotC.copy(alpha = 0.30f), Color.Transparent),
                        center = c,
                        radius = radius * 0.85f,
                    ),
                radius = radius * 0.85f,
                center = c,
                blendMode = BlendMode.Plus,
            )
        }

        // 3. 可读性遮罩：底部压暗/提亮，保证列表文字与图标仍有对比度。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to Color.Transparent,
                                0.42f to Color.Transparent,
                                1f to
                                    if (isDark) {
                                        Color(0xFF05070E).copy(alpha = 0.62f)
                                    } else {
                                        Color.White.copy(alpha = 0.38f)
                                    },
                            ),
                    ),
                ),
        )
    }
}

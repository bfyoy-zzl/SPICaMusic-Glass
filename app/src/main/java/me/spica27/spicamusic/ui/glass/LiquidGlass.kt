/*
 * 全 App 统一的液态玻璃入口。
 *
 * 兼容层：保留原有 liquidGlassSource / liquidGlass(variant) 的签名，
 * 让所有既有调用点自动切换到 Backdrop 风格；
 * 新增 liquidGlassSurface(spec) 供重做后的控件直接使用。
 *
 * 玻璃参数见 GlassSpec.kt，光学绘制见 GlassRender.kt，
 * 二者均以 Kyant0/AndroidLiquidGlass 为参考。
 */
package me.spica27.spicamusic.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.material3
import dev.chrisbanes.haze.hazeSource

@Immutable
data class LiquidGlassConfig(
    val enabled: Boolean = false,
)

val LocalLiquidGlassConfig = staticCompositionLocalOf { LiquidGlassConfig() }

/** 对话框与页面共享模糊状态；未提供时使用不透明背景。 */
val LocalDialogHazeState = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun Modifier.liquidGlassSource(hazeState: HazeState): Modifier =
    if (LocalLiquidGlassConfig.current.enabled) {
        hazeSource(hazeState)
    } else {
        this
    }

/** 旧 variant → 新 GlassSpec 的映射，数值取自参考库的示例控件。 */
enum class LiquidGlassVariant {
    TopBar,
    PlayButton,
    Dialog,
    PopupMenu,
    ;

    internal val spec: GlassSpec
        get() =
            when (this) {
                TopBar -> GlassSpec.Bar
                PlayButton -> GlassSpec.Button
                Dialog -> GlassSpec.Dialog
                PopupMenu -> GlassSpec.Dialog
            }
}

/**
 * 通用玻璃表面。
 *
 * 有 Haze 源时叠加背景采样与模糊（参数来自 [spec]），
 * 没有源或低版本时用半透明底色 + AGSL 光带降级。
 */
@Composable
fun Modifier.liquidGlassSurface(
    hazeState: HazeState?,
    spec: GlassSpec = GlassSpec.Card,
    shape: Shape = GlassShapes.Medium,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val config = LocalLiquidGlassConfig.current
    val optics = glassOptics(spec = spec, shape = shape, enabled = true)
    if (!config.enabled) {
        return optics.clip(shape).background(fallbackColor)
    }
    if (hazeState == null) {
        return optics
            .clip(shape)
            .background(
                GlassTint.surface(MaterialTheme.colorScheme.surface.luminance() < 0.5f),
            )
    }
    return optics
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = glassBlurStyle(spec, fallbackColor),
            performanceMode = HazePerformanceMode.Balanced,
            expandLayerBounds = false,
        )
}

/**
 * 兼容入口：签名与旧版一致，内部改为「Haze 背景采样 + Backdrop 光学装饰」。
 */
@Composable
@OptIn(ExperimentalHazeApi::class)
fun Modifier.liquidGlass(
    hazeState: HazeState,
    variant: LiquidGlassVariant,
    shape: Shape,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
): Modifier {
    val config = LocalLiquidGlassConfig.current
    val spec = variant.spec
    val optics = glassOptics(spec = spec, shape = shape, enabled = config.enabled)

    if (!config.enabled) {
        return clip(shape).backgroundFallback(fallbackColor)
    }

    if ((
            variant == LiquidGlassVariant.Dialog ||
                variant == LiquidGlassVariant.PopupMenu
        ) &&
        shape is RoundedCornerShape
    ) {
        val baseStyle =
            GlassStyle.clear.material3(
                containerColor = fallbackColor,
                tint = fallbackColor.copy(alpha = if (fallbackColor.luminance() < 0.5f) 0.16f else 0.12f),
            )
        val glassStyle =
            remember(baseStyle, shape, spec) {
                baseStyle.then {
                    shape(shape)
                    lightPosition(Alignment.TopStart)
                    optics(
                        // 参考库 Lens: refractionHeight 12~24dp / refractionAmount 24dp
                        blurRadius = spec.blurRadius,
                    )
                }
            }
        return optics.clip(shape).hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = glassStyle,
            performanceMode = HazePerformanceMode.Fixed(.45f),
            expandLayerBounds = true,
        )
    }

    return optics.clip(shape).hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = glassBlurStyle(spec, fallbackColor),
        performanceMode =
            when (variant) {
                LiquidGlassVariant.TopBar,
                LiquidGlassVariant.Dialog,
                LiquidGlassVariant.PopupMenu,
                -> HazePerformanceMode.Balanced
                LiquidGlassVariant.PlayButton -> HazePerformanceMode.Performance
            },
        expandLayerBounds = false,
    )
}

/** 参考库 vibrancy() 与低版本回退统一由 GlassSurface.kt 的 glassBlurStyle 提供。 */

private fun Modifier.backgroundFallback(color: Color): Modifier = background(color)

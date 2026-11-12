/*
 * 液态玻璃参数体系。
 *
 * 默认值逐项对齐 Kyant0/AndroidLiquidGlass (Backdrop)：
 *   effects/Blur.kt, effects/ColorFilter.kt, highlight/Highlight.kt,
 *   highlight/HighlightStyle.kt, shadow/Shadow.kt, shadow/InnerShadow.kt，
 * 以及 catalog/components/LiquidButton / LiquidSlider / LiquidToggle / LiquidBottomTabs
 * 里实际使用的 lens()/blur() 数值。
 */
package me.spica27.spicamusic.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 边缘折射。参考库 Lens.kt：refractionAmount 传入时取负。 */
@Immutable
data class LensSpec(
    val refractionHeight: Dp = 12.dp,
    val refractionAmount: Dp = 24.dp,
    val depthEffect: Boolean = false,
    val chromaticAberration: Boolean = false,
)

/** 轮廓高光。默认 width 0.5dp / blurRadius width/2 / angle 45° / falloff 1。 */
@Immutable
data class HighlightSpec(
    val width: Dp = 0.5.dp,
    val blurRadius: Dp = 0.25.dp,
    val alpha: Float = 1f,
    val color: Color = Color.White.copy(alpha = 0.5f),
    val angleDegrees: Float = 45f,
    val falloff: Float = 1f,
    val ambient: Boolean = false,
    val blendMode: BlendMode = BlendMode.Plus,
)

/** 外阴影。默认 radius 24dp，offsetY = radius / 6，黑色 10%。 */
@Immutable
data class ShadowSpec(
    val radius: Dp = 24.dp,
    val offsetY: Dp = 4.dp,
    val color: Color = Color.Black.copy(alpha = 0.1f),
    val alpha: Float = 1f,
)

/** 内阴影。默认 radius 24dp，offsetY = radius，黑色 15%。 */
@Immutable
data class InnerShadowSpec(
    val radius: Dp = 24.dp,
    val offsetY: Dp = 24.dp,
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
)

/**
 * 一块玻璃的完整规格。
 *
 * @param vibrancy 背景饱和度增强，参考库 vibrancy() 固定 1.5
 * @param tint 玻璃自身的着色（参考库 onDrawSurface 的 drawRect）
 */
@Immutable
data class GlassSpec(
    val blurRadius: Dp = 8.dp,
    val vibrancy: Float = 1.5f,
    val lens: LensSpec = LensSpec(),
    val highlight: HighlightSpec? = HighlightSpec(),
    val shadow: ShadowSpec? = ShadowSpec(),
    val innerShadow: InnerShadowSpec? = null,
    val tint: Color = Color.Transparent,
    val dispersion: Boolean = false,
) {
    companion object {
        /** LiquidButton：vibrancy() + blur(2dp) + lens(12dp, 24dp)。 */
        @Stable
        val Button =
            GlassSpec(
                blurRadius = 2.dp,
                vibrancy = 1.5f,
                lens = LensSpec(refractionHeight = 12.dp, refractionAmount = 24.dp),
                highlight = HighlightSpec(),
                shadow = ShadowSpec(radius = 24.dp, color = Color.Black.copy(alpha = 0.1f)),
            )

        /** LiquidBottomTabs / 大面板：vibrancy() + blur(8dp) + lens(24dp, 24dp)。 */
        @Stable
        val Panel =
            GlassSpec(
                blurRadius = 8.dp,
                vibrancy = 1.5f,
                lens = LensSpec(refractionHeight = 24.dp, refractionAmount = 24.dp),
                highlight = HighlightSpec(),
                shadow = ShadowSpec(radius = 24.dp, color = Color.Black.copy(alpha = 0.1f)),
            )

        /** 悬浮条 / 顶栏：略轻的折射，保留 vibrancy。 */
        @Stable
        val Bar =
            GlassSpec(
                blurRadius = 8.dp,
                vibrancy = 1.4f,
                lens = LensSpec(refractionHeight = 12.dp, refractionAmount = 20.dp),
                highlight = HighlightSpec(),
                shadow = ShadowSpec(radius = 16.dp, color = Color.Black.copy(alpha = 0.1f)),
            )

        /** 卡片 / 列表分组：低折射，避免文字区域被拉扯。 */
        @Stable
        val Card =
            GlassSpec(
                blurRadius = 6.dp,
                vibrancy = 1.25f,
                lens = LensSpec(refractionHeight = 8.dp, refractionAmount = 14.dp),
                highlight = HighlightSpec(width = 0.5.dp, color = Color.White.copy(alpha = 0.38f)),
                shadow = ShadowSpec(radius = 12.dp, color = Color.Black.copy(alpha = 0.08f)),
            )

        /** 对话框：加深的折射带 + 内阴影，参考 ControlCenter / Dialog 示例。 */
        @Stable
        val Dialog =
            GlassSpec(
                blurRadius = 12.dp,
                vibrancy = 1.35f,
                lens =
                    LensSpec(
                        refractionHeight = 16.dp,
                        refractionAmount = 24.dp,
                        depthEffect = true,
                    ),
                highlight = HighlightSpec(),
                shadow = ShadowSpec(radius = 32.dp, color = Color.Black.copy(alpha = 0.14f)),
                innerShadow = InnerShadowSpec(radius = 8.dp, offsetY = 8.dp),
            )

        /** 滑块 / 开关的圆形玻璃拇指：lens(10dp, 14dp, chromaticAberration = true)。 */
        @Stable
        val Thumb =
            GlassSpec(
                blurRadius = 4.dp,
                vibrancy = 1.5f,
                lens =
                    LensSpec(
                        refractionHeight = 10.dp,
                        refractionAmount = 14.dp,
                        chromaticAberration = true,
                    ),
                highlight = HighlightSpec(width = 0.33.dp, blurRadius = 0.17.dp, ambient = true),
                shadow = ShadowSpec(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)),
                dispersion = true,
            )

        /** 开关拇指：lens(5dp, 10dp, chromaticAberration = true)。 */
        @Stable
        val ToggleThumb =
            GlassSpec(
                blurRadius = 4.dp,
                vibrancy = 1.5f,
                lens =
                    LensSpec(
                        refractionHeight = 5.dp,
                        refractionAmount = 10.dp,
                        chromaticAberration = true,
                    ),
                highlight = HighlightSpec(width = 0.33.dp, blurRadius = 0.17.dp, ambient = true),
                shadow = ShadowSpec(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)),
                dispersion = true,
            )

        /** 选中态标签：与 LiquidBottomTabs 的选中胶囊一致。 */
        @Stable
        val SelectedPill =
            GlassSpec(
                blurRadius = 2.dp,
                vibrancy = 1.5f,
                lens =
                    LensSpec(
                        refractionHeight = 10.dp,
                        refractionAmount = 14.dp,
                        chromaticAberration = true,
                    ),
                highlight = HighlightSpec(),
                shadow = ShadowSpec(radius = 12.dp, color = Color.Black.copy(alpha = 0.08f)),
                dispersion = true,
            )
    }
}

/** 形状与 token：参考库大量使用胶囊形状，非胶囊面板用连续大圆角。 */
object GlassShapes {
    val Capsule =
        androidx.compose.foundation.shape
            .RoundedCornerShape(50)
    val Small =
        androidx.compose.foundation.shape
            .RoundedCornerShape(16.dp)
    val Medium =
        androidx.compose.foundation.shape
            .RoundedCornerShape(20.dp)
    val Large =
        androidx.compose.foundation.shape
            .RoundedCornerShape(28.dp)
    val ExtraLarge =
        androidx.compose.foundation.shape
            .RoundedCornerShape(32.dp)
}

/** 与 catalog 示例保持一致的强调色。 */
object GlassColors {
    val AccentLight = Color(0xFF0088FF)
    val AccentDark = Color(0xFF0091FF)
    val ToggleOnLight = Color(0xFF34C759)
    val ToggleOnDark = Color(0xFF30D158)
    val TrackLight = Color(0xFF787878).copy(alpha = 0.2f)
    val TrackDark = Color(0xFF787880).copy(alpha = 0.36f)
    val ContainerLight = Color(0xFFFAFAFA).copy(alpha = 0.4f)
    val ContainerDark = Color(0xFF121212).copy(alpha = 0.4f)
}

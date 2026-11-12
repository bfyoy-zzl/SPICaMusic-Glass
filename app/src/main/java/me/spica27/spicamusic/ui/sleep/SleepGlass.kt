package me.spica27.spicamusic.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** 玻璃面板的圆角：四角都圆，面板整体浮在页面之上，不贴屏幕边。 */
internal val SleepSheetShape = RoundedCornerShape(30.dp)

/** 面板与屏幕左右/底边的间距，让卡片"浮"起来。 */
internal val SleepSheetInset = 12.dp

/** 面板里的小按钮/小卡片用的圆角。 */
internal val SleepGlassShape = RoundedCornerShape(20.dp)

/**
 * 助眠界面里的"薄玻璃"：半透明底 + 顶部高光 + 一圈细亮边。
 *
 * 面板卡片本身走 `liquidGlass`（真模糊），这里负责两件事：
 * 1. 系统关掉液态玻璃时的降级底色；
 * 2. 面板内部的次级元素（预设胶囊、音量卡、圆钮……）——这些不需要各开一层模糊，
 *    一层薄雾加亮边就够，也不会给低端机叠十几层采样。
 *
 * @param alpha 底色不透明度。容器用 0.9 以上保证文字可读，小按钮用 0.06 只做一层薄雾。
 * @param borderAlpha 顶部亮边的强度，越靠近底部越淡，模拟玻璃边缘的折射。
 */
@Composable
internal fun Modifier.sleepGlass(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    alpha: Float = 0.74f,
    borderAlpha: Float = 0.22f,
): Modifier =
    this
        .clip(shape)
        .background(tint.copy(alpha = alpha))
        .background(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.42f to Color.White.copy(alpha = 0.02f),
                1f to Color.Transparent,
            ),
        ).border(
            width = 1.dp,
            brush =
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = borderAlpha),
                    1f to Color.White.copy(alpha = borderAlpha * 0.3f),
                ),
            shape = shape,
        )

/** 圆形玻璃按钮：选中时填主色，否则是一层薄雾 + 亮边。 */
@Composable
internal fun glassCircle(
    selected: Boolean,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
): Modifier {
    val colors = MaterialTheme.colorScheme
    return if (selected) {
        modifier
            .clip(shape)
            .background(colors.primary)
    } else {
        modifier.sleepGlass(
            shape = shape,
            tint = colors.onSurface,
            alpha = 0.06f,
            borderAlpha = 0.18f,
        )
    }
}

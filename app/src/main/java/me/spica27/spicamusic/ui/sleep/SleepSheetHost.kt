@file:Suppress("FunctionName")

package me.spica27.spicamusic.ui.sleep

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.spica27.spicamusic.ui.glass.LiquidGlassVariant
import me.spica27.spicamusic.ui.glass.LocalLiquidGlassConfig
import me.spica27.spicamusic.ui.glass.liquidGlass

/**
 * 页面提供给自己面板的模糊采样源。
 *
 * 面板必须和页面内容处在同一个窗口里，`liquidGlass` 才采得到底下的场景；
 * 没有提供（或系统关了液态玻璃）时面板退化成不透明底。
 */
internal val LocalSleepSheetHaze = staticCompositionLocalOf<HazeState?> { null }

/** 从顶部把手往下拖过这个距离就关闭。 */
private val DismissDragDistance = 110.dp

/** 玻璃底下的墨色强度：太高就看不见背后的场景，太低字又会飘。 */
private const val PanelBaseAlpha = 0.35f

/** 压暗层强度，比之前再重一点，让面板从场景里"浮"出来。 */
private const val ScrimAlpha = 0.5f

/**
 * 助眠面板的统一宿主：底部浮起的玻璃卡片 + 压暗层 + 滑动进出场。
 *
 * 为什么不用 `ModalBottomSheet`：它是独立窗口，拿不到本页的 haze 采样源，
 * 面板只能是"半透明色块"——背后的封面会直接透进来，在暗场景上糊成一片脏色。
 * 换成同窗口的覆盖层之后，面板可以用 `liquidGlass` 真正模糊身后的场景，
 * 和音乐播放页的睡眠定时器是同一套质感。
 *
 * 宿主常驻组合，靠 [visible] 收放，这样关闭动画才播得出来。
 */
@Composable
internal fun SleepSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = LocalSleepSheetHaze.current
    val glassEnabled = LocalLiquidGlassConfig.current.enabled
    val threshold = with(LocalDensity.current) { DismissDragDistance.toPx() }
    // 面板底色跟着外面这一层的主题走：播放页是助眠暗色板，列表页跟随系统。
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHigh

    var dragY by remember { mutableFloatStateOf(0f) }

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = ScrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter =
                slideInVertically(tween(340, easing = EaseOutCubic)) { height -> height } +
                    fadeIn(tween(200)),
            exit = slideOutVertically(tween(260, easing = EaseOutCubic)) { height -> height } + fadeOut(tween(160)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // 只跟手的位移，不加动画：拖动时要跟手指走，松手才回弹。
                        .graphicsLayer { translationY = dragY }
                        .padding(horizontal = SleepSheetInset)
                        .navigationBarsPadding()
                        .padding(bottom = SleepSheetInset)
                        // 玻璃下面再铺一层同色底：模糊采样到的是场景本身，只靠玻璃的淡染色
                        // 面板会有点飘；压一层底之后文字永远读得清，也不至于把背后的场景盖死。
                        // 颜色取自当前主题，所以播放页（暗色板）和列表页（跟随系统）各是各的。
                        .clip(SleepSheetShape)
                        .background(panelColor.copy(alpha = PanelBaseAlpha))
                        .then(
                            if (hazeState != null && glassEnabled) {
                                Modifier.liquidGlass(
                                    hazeState = hazeState,
                                    variant = LiquidGlassVariant.Dialog,
                                    shape = SleepSheetShape,
                                    fallbackColor = panelColor,
                                )
                            } else {
                                Modifier.sleepGlass(shape = SleepSheetShape, tint = panelColor, alpha = 0.97f)
                            },
                        ),
            ) {
                // 把手自己画：容器透明后 M3 默认把手会跟着变淡，而且拖动关闭只绑在把手上，
                // 避免和外层面板的内部滚动抢手势。
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .draggable(
                                orientation = Orientation.Vertical,
                                state =
                                    rememberDraggableState { delta ->
                                        dragY = (dragY + delta).coerceAtLeast(0f)
                                    },
                                onDragStopped = {
                                    if (dragY > threshold) {
                                        dragY = 0f
                                        onDismiss()
                                    } else {
                                        animate(dragY, 0f, animationSpec = tween(220)) { value, _ ->
                                            dragY = value
                                        }
                                    }
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(38.dp, 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), CircleShape),
                    )
                }

                content()
            }
        }
    }
}

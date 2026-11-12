package me.spica27.spicamusic.ui.sleep

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * 助眠界面的专用色板。
 *
 * 为什么要在页面内再套一层主题：助眠播放页的背景永远是程序化生成的夜色（见
 * SleepAmbience），和系统亮暗无关。如果沿用全局主题，白天用浅色模式时，
 * 深色背景上会浮出一块近白的玻璃面板、配深灰文字——既不符合"要睡了"的语境，
 * 对比度也不够（灰字压在灰玻璃上）。
 *
 * 这里把整页锁进一套固定暗色：近墨的底、雾白的字、冰蓝的点缀。
 * 控件（滑杆、圆钮、拨盘）全部走配色角色，不用各处手写 hardcode 颜色，
 * 以后调整只改这一处。
 */

/** 冰蓝点缀，和氛围背景里的高光同色，保证控件和场景是一套。 */
private val AccentIce = Color(0xFFB7D8F2)

private val SleepColorScheme =
    darkColorScheme(
        primary = AccentIce,
        onPrimary = Color(0xFF08151F),
        primaryContainer = Color(0xFF22405A),
        onPrimaryContainer = Color(0xFFD7E9F8),
        secondary = Color(0xFF9FC3DC),
        onSecondary = Color(0xFF08141C),
        secondaryContainer = Color(0xFF1B2C3A),
        onSecondaryContainer = Color(0xFFCFE3F1),
        // 定时环的渐变用 tertiary，偏一点点紫，弧线才不会像一条单调的蓝条。
        tertiary = Color(0xFFC0C4E8),
        onTertiary = Color(0xFF0D1020),
        tertiaryContainer = Color(0xFF2A2F4A),
        onTertiaryContainer = Color(0xFFDCE0F6),
        // 停止 = 唯一需要"提醒"的动作，用灰粉而不是纯红，暗底下不刺眼。
        error = Color(0xFFE7A3A3),
        onError = Color(0xFF2A0F10),
        errorContainer = Color(0xFF3B1D1E),
        onErrorContainer = Color(0xFFF6D2D2),
        background = Color(0xFF05090D),
        onBackground = Color(0xFFE6EDF2),
        surface = Color(0xFF080F15),
        onSurface = Color(0xFFE6EDF2),
        surfaceVariant = Color(0xFF141E27),
        onSurfaceVariant = Color(0xFF9BABB8),
        surfaceTint = AccentIce,
        inverseSurface = Color(0xFFE6EDF2),
        inverseOnSurface = Color(0xFF11181E),
        inversePrimary = Color(0xFF2F5D82),
        outline = Color(0xFF3D4B57),
        outlineVariant = Color(0xFF232F39),
        scrim = Color(0xFF000000),
        surfaceBright = Color(0xFF1A242D),
        surfaceDim = Color(0xFF080F15),
        surfaceContainerLowest = Color(0xFF04080C),
        surfaceContainerLow = Color(0xFF0B1319),
        surfaceContainer = Color(0xFF0E171E),
        surfaceContainerHigh = Color(0xFF131D25),
        surfaceContainerHighest = Color(0xFF18232C),
    )

/**
 * 把一段界面锁进助眠的暗色板。
 *
 * 只换配色角色，字体和形状沿用外层主题——助眠界面没有独立的设计语言，
 * 尺寸和字阶要和 app 其它页面保持一致。
 */
@Composable
internal fun SleepTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SleepColorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

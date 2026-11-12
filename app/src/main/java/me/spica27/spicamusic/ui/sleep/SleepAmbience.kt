@file:Suppress("MagicNumber")

package me.spica27.spicamusic.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/*
 * 助眠播放页的"氛围背景"。
 *
 * 全部用 Canvas 现画，不引入任何图片或第三方粒子库：
 * 1. 场景只有 5 个，效果是固定的几套程序化图案，比塞 5 段视频省几百兆；
 * 2. 深色场景下大面积的柔和渐变比位图更不容易出现色带；
 * 3. 帧循环只写一个 FloatState，读取发生在 draw 阶段，因此每帧只重绘、不重组。
 *
 * 视角的"3D 感"来自三层：极远的底色渐变、随深度改变大小/速度/透明度的粒子、
 * 以及最外面压住四周的黑色渐隐。近处粒子大而快，远处小而慢，人眼会自然读出纵深。
 */

/** 一圈黑色渐隐的强度，保证顶部标题和底部按钮在任何场景上都读得清。 */
private const val VIGNETTE = 1f

private const val TAU = (PI * 2.0).toFloat()

/** 助眠页统一的高光色：雾蓝，比主题主色更冷更淡，贴合暗色氛围。 */
internal val SleepAccent = Color(0xFFB7D8F2)

private enum class AmbienceKind { RAIN, FOREST, FIRE, OCEAN, NIGHT, DREAM }

private class Palette(
    val top: Color,
    val bottom: Color,
)

/**
 * 一颗粒子的静态属性。
 *
 * [depth] 是"离镜头多近"，0 远 1 近；[drift] 是一个稳定的随机数，
 * 用来让每颗粒子的横向偏移/漂浮高度各不相同，避免出现整齐的队列感。
 */
private class Mote(
    val x: Float,
    val y: Float,
    val depth: Float,
    val phase: Float,
    val drift: Float,
)

private class AmbienceScene(
    val kind: AmbienceKind,
    val motes: List<Mote>,
)

private fun motes(
    count: Int,
    seed: Int,
): List<Mote> {
    val random = Random(seed)
    return List(count) {
        Mote(
            x = random.nextFloat(),
            y = random.nextFloat(),
            depth = 0.15f + random.nextFloat() * 0.85f,
            phase = random.nextFloat() * TAU,
            drift = random.nextFloat(),
        )
    }
}

private fun ambienceKind(key: String): AmbienceKind =
    when (key) {
        "rain" -> AmbienceKind.RAIN
        "forest" -> AmbienceKind.FOREST
        "fireplace" -> AmbienceKind.FIRE
        "ocean" -> AmbienceKind.OCEAN
        "summer_night" -> AmbienceKind.NIGHT
        else -> AmbienceKind.DREAM
    }

private fun paletteOf(key: String): Palette =
    when (key) {
        "rain" -> Palette(Color(0xFF0C1B25), Color(0xFF03080C))
        "forest" -> Palette(Color(0xFF06160F), Color(0xFF020705))
        "fireplace" -> Palette(Color(0xFF160A06), Color(0xFF070302))
        "ocean" -> Palette(Color(0xFF04141F), Color(0xFF010609))
        "summer_night" -> Palette(Color(0xFF050A18), Color(0xFF01020A))
        else -> Palette(Color(0xFF0A0716), Color(0xFF03020A))
    }

/** 粒子数按场景的信息密度分配：雨的粒子最多，星夜次之，自选最省。 */
private fun buildScene(kind: AmbienceKind): AmbienceScene =
    when (kind) {
        AmbienceKind.RAIN -> AmbienceScene(kind, motes(count = 110, seed = 17))
        AmbienceKind.FOREST -> AmbienceScene(kind, motes(count = 34, seed = 41))
        AmbienceKind.FIRE -> AmbienceScene(kind, motes(count = 46, seed = 73))
        AmbienceKind.OCEAN -> AmbienceScene(kind, motes(count = 40, seed = 97))
        AmbienceKind.NIGHT -> AmbienceScene(kind, motes(count = 130, seed = 131))
        AmbienceKind.DREAM -> AmbienceScene(kind, motes(count = 44, seed = 211))
    }

/**
 * 场景氛围背景。
 *
 * @param sceneKey 场景稳定标识（rain / forest / fireplace / ocean / summer_night），
 *   其余值走"自选"的中性梦境效果。
 * @param animated 传 false 时只画静止的第一帧，用于"减少动态效果"或页面不在前台时省电。
 */
@Composable
internal fun SleepAmbience(
    sceneKey: String,
    modifier: Modifier = Modifier,
    animated: Boolean = true,
) {
    val kind = remember(sceneKey) { ambienceKind(sceneKey) }
    val palette = remember(sceneKey) { paletteOf(sceneKey) }
    val scene = remember(kind) { buildScene(kind) }

    // 只在 draw 阶段读取：写这个状态只会让 Canvas 重绘，不会触发重组。
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animated, kind) {
        if (!animated) {
            clock = 0f
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame -> clock = (frame - start) / 1000f }
        }
    }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(palette.top, palette.bottom)))
        when (scene.kind) {
            AmbienceKind.RAIN -> drawRain(scene.motes, clock)
            AmbienceKind.FOREST -> drawForest(scene.motes, clock)
            AmbienceKind.FIRE -> drawFire(scene.motes, clock)
            AmbienceKind.OCEAN -> drawOcean(scene.motes, clock)
            AmbienceKind.NIGHT -> drawNight(scene.motes, clock)
            AmbienceKind.DREAM -> drawDream(scene.motes, clock)
        }
        drawVignette()
    }
}

/** 夏雨：斜向雨丝 + 远处雨幕。近处的雨丝更长、更快、更实。 */
private fun DrawScope.drawRain(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()

    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF82B8D8).copy(alpha = 0.06f),
            0.5f to Color.Transparent,
            1f to Color(0xFF3D6E8C).copy(alpha = 0.07f),
        ),
    )

    motes.forEach { mote ->
        val speed = 0.32f + mote.depth * 0.78f
        // 让雨丝在屏幕外就开始下落、在屏幕外结束，避免出现"顶部突然冒出"。
        val head = wrap(mote.y + t * speed * 0.55f, 1.25f)
        val y = (head - 0.12f) * h
        val x = (mote.x - head * 0.05f) * w
        if (x < -32f || x > w + 32f) return@forEach
        val length = (12f + mote.depth * 54f) * unit
        drawLine(
            color = Color(0xFFD6ECFA).copy(alpha = 0.035f + 0.145f * mote.depth),
            start = Offset(x, y),
            end = Offset(x - length * 0.18f, y + length),
            strokeWidth = (0.55f + mote.depth * 0.9f) * unit,
            cap = StrokeCap.Round,
        )
    }
}

/** 森林：树冠间的青绿色雾光缓慢游动，萤火虫边呼吸边向上飘。 */
private fun DrawScope.drawForest(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()

    repeat(3) { i ->
        val cx = (0.24f + 0.26f * i + 0.06f * sin(t * 0.11f + i * 1.7f)) * w
        val cy = (0.34f + 0.15f * i + 0.04f * cos(t * 0.09f + i)) * h
        softGlow(cx, cy, h * 0.44f, Color(0xFF68C9A0), 0.055f)
    }

    motes.forEach { mote ->
        val x = (mote.x + sin(t * 0.16f + mote.phase) * 0.04f) * w
        val y = wrap(mote.y - t * 0.011f * (0.35f + mote.depth), 1f) * h
        val pulse = 0.28f + 0.72f * (0.5f + 0.5f * sin(t * 1.5f + mote.phase * 1.9f))
        val radius = (1.5f + mote.depth * 3.2f) * unit
        softGlow(x, y, radius * 6f, Color(0xFFBDF08C), 0.065f * pulse)
        drawCircle(
            color = Color(0xFFEAFBC0).copy(alpha = 0.55f * pulse),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/** 炉火：底部一团随呼吸明暗的橙色光，火星贴着它往上散开。 */
private fun DrawScope.drawFire(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()

    // 两个不同频率的正弦叠加，火光才不像机械的灯泡在闪。
    val flicker = 0.86f + 0.1f * sin(t * 2.3f) + 0.045f * sin(t * 5.7f + 1.2f)

    drawRect(
        Brush.radialGradient(
            colors =
                listOf(
                    Color(0xFFFF8A3D).copy(alpha = 0.28f * flicker),
                    Color(0xFFE8501F).copy(alpha = 0.11f * flicker),
                    Color.Transparent,
                ),
            center = Offset(w * 0.5f, h * 1.04f),
            radius = h * 0.96f,
        ),
    )

    motes.forEach { mote ->
        val rise = wrap(mote.y + t * (0.028f + mote.depth * 0.05f), 1f)
        val y = (1.02f - rise) * h
        // 越往上散得越开，像被热气顶着走。
        val spread = 0.55f + rise * 0.75f
        val sway = sin(t * 0.9f + mote.phase) * 0.018f
        val x = (0.5f + (mote.x - 0.5f) * spread + sway) * w
        val fade = (1f - rise).coerceIn(0f, 1f)
        val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 3.1f + mote.phase * 2.4f))
        drawCircle(
            color = Color(0xFFFFB070).copy(alpha = 0.6f * fade * pulse),
            radius = (0.8f + mote.depth * 2f) * unit,
            center = Offset(x, y),
        )
    }
}

/** 海洋：几层相位不同的横向波，浪尖上零星的碎光。 */
private fun DrawScope.drawOcean(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()

    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.42f to Color(0xFF2E7FA6).copy(alpha = 0.10f),
            0.8f to Color(0xFF0E3A50).copy(alpha = 0.17f),
            1f to Color.Transparent,
        ),
    )

    val layers = 5
    val steps = 48
    repeat(layers) { i ->
        val phase = t * (0.16f + i * 0.05f) + i * 0.9f
        val baseline = h * (0.46f + i * 0.115f)
        val amplitude = h * (0.010f + i * 0.004f)
        val path =
            Path().apply {
                moveTo(-2f, h + 2f)
                for (step in 0..steps) {
                    val x = w * step / steps
                    val y = baseline + sin(x / w * (2.6f + i * 0.7f) * TAU + phase) * amplitude
                    lineTo(x, y)
                }
                lineTo(w + 2f, h + 2f)
                close()
            }
        drawPath(path, Color(0xFF66C6EA).copy(alpha = 0.03f + i * 0.018f))
    }

    motes.take(26).forEach { mote ->
        val x = (mote.x + sin(t * 0.2f + mote.phase) * 0.02f) * w
        val base = h * (0.5f + mote.drift * 0.42f)
        val y = base + sin(x / w * TAU + t * (0.6f + mote.depth)) * h * 0.012f
        val blink = 0.5f + 0.5f * sin(t * 1.3f + mote.phase * 3f)
        drawCircle(
            color = Color(0xFFDFF6FF).copy(alpha = 0.08f + 0.2f * blink * mote.depth),
            radius = (0.7f + mote.depth * 1.5f) * unit,
            center = Offset(x, y),
        )
    }
}

/** 夏夜：上半屏的星空 + 下半屏的萤火，两层速度不同以拉开纵深。 */
private fun DrawScope.drawNight(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()

    softGlow(w * 0.22f, h * 0.16f, h * 0.52f, Color(0xFF5C6BC0), 0.10f)
    softGlow(w * 0.82f, h * 0.30f, h * 0.44f, Color(0xFF8E5AC8), 0.07f)

    motes.take(96).forEach { mote ->
        val x = mote.x * w
        val y = mote.y * 0.72f * h
        val twinkle = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * (0.7f + mote.depth) + mote.phase * 4f))
        val radius = (0.5f + mote.depth * 1.3f) * unit
        if (mote.depth > 0.82f) softGlow(x, y, radius * 7f, Color(0xFFCBD8FF), 0.10f * twinkle)
        drawCircle(
            color = Color(0xFFE8EEFF).copy(alpha = 0.32f + 0.5f * twinkle),
            radius = radius,
            center = Offset(x, y),
        )
    }

    motes.drop(96).forEach { mote ->
        val x = (mote.x + sin(t * 0.13f + mote.phase) * 0.05f) * w
        val y = (0.55f + mote.drift * 0.4f + sin(t * 0.21f + mote.phase) * 0.03f) * h
        val pulse = 0.3f + 0.7f * (0.5f + 0.5f * sin(t * 1.1f + mote.phase * 2.2f))
        val radius = (1.3f + mote.depth * 1.8f) * unit
        softGlow(x, y, radius * 8f, Color(0xFFA8E063), 0.085f * pulse)
        drawCircle(
            color = Color(0xFFD8F59B).copy(alpha = 0.55f * pulse),
            radius = radius,
            center = Offset(x, y),
        )
    }
}

/** 自选：没有固定意象，用三团缓慢绕行的冷色光 + 稀疏星点，保持"梦境"的中性感。 */
private fun DrawScope.drawDream(
    motes: List<Mote>,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val unit = 1.dp.toPx()
    val tints = listOf(Color(0xFF6C7BE0), Color(0xFF9A6BD8), Color(0xFF4FA8C8))

    repeat(3) { i ->
        val angle = t * (0.09f + i * 0.03f) + i * 2.1f
        val cx = (0.5f + cos(angle) * 0.26f) * w
        val cy = (0.46f + sin(angle * 0.8f) * 0.16f) * h
        softGlow(cx, cy, h * (0.33f + i * 0.05f), tints[i], 0.10f)
    }

    motes.forEach { mote ->
        val x = (mote.x + sin(t * 0.09f + mote.phase) * 0.03f) * w
        val y = wrap(mote.y - t * 0.006f * (0.3f + mote.depth), 1f) * h
        val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 0.8f + mote.phase * 3f))
        drawCircle(
            color = Color(0xFFE9D8FF).copy(alpha = 0.08f + 0.35f * mote.depth * pulse),
            radius = (0.6f + mote.depth * 1.2f) * unit,
            center = Offset(x, y),
        )
    }

    // 一条斜向的极淡光带，避免纯色块显得平。
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, Color(0xFF8FB4E8).copy(alpha = 0.05f), Color.Transparent),
            start = Offset(0f, h * 0.15f),
            end = Offset(w, h * 0.85f),
        ),
    )
}

/** 一团没有硬边的光。粒子用它做外发光，比直接叠 alpha 更柔和。 */
private fun DrawScope.softGlow(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    alpha: Float,
) {
    if (radius <= 0f || alpha <= 0f) return
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.32f), Color.Transparent),
                center = Offset(cx, cy),
                radius = radius,
            ),
        radius = radius,
        center = Offset(cx, cy),
    )
}

/**
 * 四周黑色渐隐。
 *
 * 两层叠起来：径向压掉四角，竖向再压一次上下边缘——顶部要放标题和拖拽条，
 * 底部要放三颗按钮，只有纵向渐变压得够狠，白色文字才不会糊在亮部上。
 */
private fun DrawScope.drawVignette() {
    val w = size.width
    val h = size.height

    drawRect(
        Brush.radialGradient(
            colorStops =
                arrayOf(
                    0f to Color.Transparent,
                    0.52f to Color.Transparent,
                    0.78f to Color.Black.copy(alpha = 0.34f * VIGNETTE),
                    1f to Color.Black.copy(alpha = 0.9f * VIGNETTE),
                ),
            center = Offset(w * 0.5f, h * 0.44f),
            radius = max(w, h) * 0.78f,
        ),
    )
    drawRect(
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0f to Color.Black.copy(alpha = 0.5f * VIGNETTE),
                    0.2f to Color.Transparent,
                    0.78f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.8f * VIGNETTE),
                ),
        ),
    )
}

private fun wrap(
    value: Float,
    span: Float,
): Float {
    val remainder = value % span
    return if (remainder < 0f) remainder + span else remainder
}

/*
 * 液态玻璃的光学装饰层。
 *
 * 逻辑来源 Kyant0/AndroidLiquidGlass (Backdrop)：
 *  - 圆角矩形 SDF / circleMap 边缘折射曲线 / 定向高光 / 7 段色散权重
 *  - highlight/HighlightModifier.kt 的「描边 + 模糊 + Plus 混合」
 *  - shadow/ShadowModifier.kt 的「离屏图层 + Clear 挖空」
 *
 * 关键修复：参考库用 GraphicsLayer(CompositingStrategy.Offscreen) 隔离阴影层，
 * 因为 BlendMode.Clear 会清空「当前画布上的所有像素」。若直接在
 * onDrawWithContent 里 Clear，会把玻璃底下已经画好的背景、底图一起清成透明，
 * 深色主题下就表现为「控件中心全黑、只有边缘还有一圈光」。
 * 这里用 canvas.saveLayer() 达到同样的隔离效果。
 *
 * 本层只负责光学装饰，背景（真实采样或半透明底色）由 glassSurface 提供，
 * 因此前景文字永远保持清晰，且 API 29~32 可以平滑降级。
 */
package me.spica27.spicamusic.ui.glass

import android.annotation.SuppressLint
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI

private val SupportsAgsl: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 给任意形状叠加玻璃光学效果：外阴影 → 内容 → 内阴影 → 色散光带 → 定向高光。
 *
 * 不绘制任何不透明/深色填充；背景交由 [glassSurface] 或 Haze 提供。
 */
@Composable
fun Modifier.glassOptics(
    spec: GlassSpec = GlassSpec.Card,
    shape: Shape = GlassShapes.Medium,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this

    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val radii = cornerRadii(shape, size, this, layoutDirection)
        val widthPx = size.width
        val heightPx = size.height
        val edgeWidthPx =
            spec.lens.refractionHeight
                .toPx()
                .coerceAtLeast(1f)

        val shadowRadiusPx = spec.shadow?.radius?.toPx() ?: 0f
        val shadowOffsetPx = spec.shadow?.offsetY?.toPx() ?: 0f
        val layerPad = shadowRadiusPx * 2f + 2f
        val shadowLayerBounds =
            Rect(
                left = -layerPad,
                top = -layerPad,
                right = widthPx + layerPad,
                bottom = heightPx + layerPad + shadowOffsetPx,
            )

        val shadowPaint =
            spec.shadow?.let { shadow ->
                Paint().apply {
                    color = shadow.color
                    alpha = shadow.alpha
                    style = PaintingStyle.Fill
                    val radiusPx = shadow.radius.toPx()
                    if (radiusPx > 0f) {
                        asFrameworkPaint().maskFilter =
                            BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
                    }
                }
            }
        val clearPaint =
            Paint().apply {
                blendMode = BlendMode.Clear
                style = PaintingStyle.Fill
            }
        // 每一层 SaveLayer 都需要一个全新的 Paint，不能复用带 shader/maskFilter 的实例。
        val layerPaint = Paint()

        val highlightSpec = spec.highlight
        val highlightPaint =
            if (highlightSpec != null && highlightSpec.width.value > 0f) {
                Paint().apply {
                    style = PaintingStyle.Stroke
                    color = highlightSpec.color
                    alpha = highlightSpec.alpha
                    blendMode = highlightSpec.blendMode
                    strokeWidth =
                        (highlightSpec.width.toPx().coerceAtMost(size.minDimension / 2f)) * 2f
                    val blurPx = highlightSpec.blurRadius.toPx()
                    if (blurPx > 0f) {
                        asFrameworkPaint().maskFilter =
                            BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                    }
                    if (SupportsAgsl) {
                        asFrameworkPaint().shader =
                            createHighlightShader(
                                widthPx = widthPx,
                                heightPx = heightPx,
                                radii = radii,
                                spec = highlightSpec,
                            )
                    }
                }
            } else {
                null
            }

        val dispersionPaint =
            if (SupportsAgsl && spec.dispersion && highlightSpec != null) {
                Paint().apply {
                    style = PaintingStyle.Stroke
                    blendMode = BlendMode.Plus
                    strokeWidth =
                        (highlightSpec.width.toPx() * 2.5f).coerceAtMost(size.minDimension / 2f)
                    asFrameworkPaint().shader =
                        createDispersionShader(
                            widthPx = widthPx,
                            heightPx = heightPx,
                            radii = radii,
                            angleDegrees = highlightSpec.angleDegrees,
                            falloff = highlightSpec.falloff,
                            edgeWidthPx = edgeWidthPx,
                            dispersion = 0.65f,
                            intensity = 0.55f,
                        )
                }
            } else {
                null
            }

        val innerShadowPaint =
            spec.innerShadow?.let { inner ->
                Paint().apply {
                    style = PaintingStyle.Stroke
                    color = inner.color.copy(alpha = inner.color.alpha * 0.55f)
                    alpha = inner.alpha
                    strokeWidth = inner.radius.toPx().coerceAtLeast(1f) * 2f
                    val blurPx = inner.radius.toPx()
                    if (blurPx > 0f) {
                        asFrameworkPaint().maskFilter =
                            BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
                    }
                }
            }

        onDrawWithContent {
            // 外阴影必须在独立离屏层里挖空中心，否则 Clear 会清掉底下的背景/底图。
            if (shadowPaint != null) {
                val canvas = drawContext.canvas
                canvas.saveLayer(shadowLayerBounds, layerPaint)
                canvas.translate(0f, shadowOffsetPx)
                canvas.drawOutline(outline, shadowPaint)
                canvas.translate(0f, -shadowOffsetPx)
                canvas.drawOutline(outline, clearPaint)
                canvas.restore()
            }

            drawContent()

            // 下面三层都裁剪在轮廓内，避免光带溢出到相邻控件。
            if (innerShadowPaint != null) {
                val canvas = drawContext.canvas
                canvas.save()
                clipOutline(canvas, outline)
                canvas.drawOutline(outline, innerShadowPaint)
                canvas.restore()
            }

            dispersionPaint?.let { paint ->
                val canvas = drawContext.canvas
                canvas.save()
                clipOutline(canvas, outline)
                canvas.drawOutline(outline, paint)
                canvas.restore()
            }
            highlightPaint?.let { paint ->
                val canvas = drawContext.canvas
                canvas.save()
                clipOutline(canvas, outline)
                canvas.drawOutline(outline, paint)
                canvas.restore()
            }
        }
    }
}

/** 半透明玻璃面板：用于没有 Haze 源的独立控件。 */
@Composable
fun Modifier.glassPanel(
    spec: GlassSpec = GlassSpec.Card,
    shape: Shape = GlassShapes.Medium,
    tint: Color = Color.Transparent,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    return this.glassSurface(
        spec = spec,
        shape = shape,
        tint = tint.takeIf { it.alpha > 0f },
    )
}

@SuppressLint("NewApi")
private fun createHighlightShader(
    widthPx: Float,
    heightPx: Float,
    radii: FloatArray,
    spec: HighlightSpec,
): android.graphics.RuntimeShader =
    android.graphics
        .RuntimeShader(
            if (spec.ambient) AmbientHighlightShaderSource else HighlightShaderSource,
        ).apply {
            setFloatUniform("size", widthPx, heightPx)
            setFloatUniform("cornerRadii", radii)
            setFloatUniform("angle", spec.angleDegrees * (PI / 180f).toFloat())
            setFloatUniform("falloff", spec.falloff)
            if (!spec.ambient) {
                // 保留高光自身的 alpha，避免把边缘烧成纯白。
                setColorUniform("color", spec.color.toArgb())
            }
        }

@SuppressLint("NewApi")
private fun createDispersionShader(
    widthPx: Float,
    heightPx: Float,
    radii: FloatArray,
    angleDegrees: Float,
    falloff: Float,
    edgeWidthPx: Float,
    dispersion: Float,
    intensity: Float,
): android.graphics.RuntimeShader =
    android.graphics.RuntimeShader(DispersionEdgeShaderSource).apply {
        setFloatUniform("size", widthPx, heightPx)
        setFloatUniform("cornerRadii", radii)
        setFloatUniform("angle", angleDegrees * (PI / 180f).toFloat())
        setFloatUniform("falloff", falloff)
        setFloatUniform("edgeWidth", edgeWidthPx)
        setFloatUniform("dispersion", dispersion)
        setFloatUniform("intensity", intensity)
    }

/** 按 左上/右上/右下/左下 输出四角半径，供 AGSL 的 radiusAt 使用。 */
private fun cornerRadii(
    shape: Shape,
    size: Size,
    density: Density,
    layoutDirection: LayoutDirection,
): FloatArray {
    val maxRadius = size.minDimension / 2f
    val cornerShape = shape as? RoundedCornerShape
    if (cornerShape == null) return FloatArray(4) { 0f }
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val topLeft =
        (if (isLtr) cornerShape.topStart else cornerShape.topEnd).toPx(size, density)
    val topRight =
        (if (isLtr) cornerShape.topEnd else cornerShape.topStart).toPx(size, density)
    val bottomRight =
        (if (isLtr) cornerShape.bottomEnd else cornerShape.bottomStart).toPx(size, density)
    val bottomLeft =
        (if (isLtr) cornerShape.bottomStart else cornerShape.bottomEnd).toPx(size, density)
    return floatArrayOf(
        topLeft.coerceAtMost(maxRadius),
        topRight.coerceAtMost(maxRadius),
        bottomRight.coerceAtMost(maxRadius),
        bottomLeft.coerceAtMost(maxRadius),
    )
}

private fun clipOutline(
    canvas: androidx.compose.ui.graphics.Canvas,
    outline: Outline,
) {
    when (outline) {
        is Outline.Rectangle -> canvas.clipRect(outline.rect)
        is Outline.Rounded -> {
            val path =
                androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(outline.roundRect)
                }
            canvas.clipPath(path)
        }
        is Outline.Generic -> canvas.clipPath(outline.path)
    }
}

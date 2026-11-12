/*
 * AGSL 玻璃渲染着色器。
 *
 * 移植自 Kyant0/AndroidLiquidGlass (Backdrop, kmp 分支)
 *   backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt
 * 版权 Copyright 2025 Kyant，Apache-2.0。
 *
 * 保留原实现的 SDF、circleMap 边缘折射曲线、7 次采样色散与法线高光公式，
 * 只把 uniform 的注入方式改成 Android 平台原生 RuntimeShader。
 */
package me.spica27.spicamusic.ui.glass

/** 圆角矩形 SDF 与梯度；四个角半径按 左上/右上/右下/左下 传入。 */
internal const val RoundedRectSdf: String = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}
"""

/**
 * 边缘折射：中心区域保持原样，靠近轮廓的 refractionHeight 像素带内按 SDF 梯度
 * 把背景采样坐标推开 refractionAmount 像素。
 */
internal val RefractionShaderSource: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;

$RoundedRectSdf

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    return content.eval(refractedCoord);
}
"""

/**
 * 折射 + 色散：在折射坐标上做 7 次偏移采样（红/橙/黄/绿/青/蓝/紫），
 * 色散强度随坐标的 x*y 乘积变化，越靠近四角越明显。
 */
internal val DispersionShaderSource: String = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$RoundedRectSdf

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""

/** 定向高光：沿 angle 方向的法线分量做 falloff 次幂，只在轮廓上发亮。 */
internal val HighlightShaderSource: String = """
uniform float2 size;
uniform float4 cornerRadii;
layout(color) uniform half4 color;
uniform float angle;
uniform float falloff;

$RoundedRectSdf

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    return color * intensity;
}
"""

/** 环境高光：不依赖颜色，输出灰度轮廓光。 */
internal val AmbientHighlightShaderSource: String = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;

$RoundedRectSdf

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float d = dot(grad, normal);
    float intensity = pow(abs(d), falloff);
    float t = step(0.0, d);
    return half4(t, t, t, 1.0) * intensity;
}
"""

/**
 * 轮廓色散光带。
 *
 * 复用参考库的 SDF + circleMap 边缘折射曲线与 7 段光谱权重
 * (red/orange/yellow/green/cyan/blue/purple，系数 3.5 / 7.0 / 3.0)，
 * 但采样对象从「背景 content」换成「高光强度」：这样即使没有 RenderEffect
 * 输入，边缘依然能呈现与参考实现一致的彩色折射带。
 */
internal val DispersionEdgeShaderSource: String = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float angle;
uniform float falloff;
uniform float edgeWidth;
uniform float dispersion;
uniform float intensity;

$RoundedRectSdf

float spectrum(float2 grad, float2 normal, float shift) {
    float d = dot(grad, normal + float2(shift, -shift));
    return pow(abs(d), falloff);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = coord - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    float band = clamp(1.0 - (-sd / edgeWidth), 0.0, 1.0);
    if (band <= 0.0) return half4(0.0);

    float curve = circleMap(band);
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = gradSdRoundedRect(centeredCoord, halfSize, gradRadius);
    float2 normal = float2(cos(angle), sin(angle));
    float spread = dispersion * curve;

    float red = spectrum(grad, normal, spread);
    float orange = spectrum(grad, normal, spread * (2.0 / 3.0));
    float yellow = spectrum(grad, normal, spread * (1.0 / 3.0));
    float green = spectrum(grad, normal, 0.0);
    float cyan = spectrum(grad, normal, -spread * (1.0 / 3.0));
    float blue = spectrum(grad, normal, -spread * (2.0 / 3.0));
    float purple = spectrum(grad, normal, -spread);

    half3 color = half3(0.0);
    color.r += red / 3.5 + orange / 3.5 + yellow / 3.5 + purple / 7.0;
    color.g += orange / 7.0 + yellow / 3.5 + green / 3.5 + cyan / 3.5;
    color.b += cyan / 3.0 + blue / 3.0 + purple / 3.0;
    return half4(color * curve * intensity, curve * intensity);
}
"""

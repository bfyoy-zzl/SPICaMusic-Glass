@file:Suppress("FunctionName", "MagicNumber")

package me.spica27.spicamusic.ui.sleep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.sleep.domain.SleepCountdown
import me.spica27.spicamusic.ui.glass.IconButton
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.clickHighlight
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** 倒计时环直径，和音乐播放页的睡眠定时器同尺寸。 */
private val DialSize = 176.dp

/** 环描边宽度。 */
private val DialStroke = 9.dp

/** 一排五档，和音乐播放页保持一致：15 / 30 / 45 / 60 / 90。 */
private val PresetMinutes = listOf(15, 30, 45, 60, 90)

/** 自定义拨盘的范围与步进（分钟）。 */
private const val CUSTOM_MIN_MINUTES = 5
private const val CUSTOM_MAX_MINUTES = 240
private const val CUSTOM_STEP_MINUTES = 5
private const val CUSTOM_DEFAULT_MINUTES = 20

private val CUSTOM_RANGE_MINUTES = (CUSTOM_MAX_MINUTES - CUSTOM_MIN_MINUTES).toFloat()

/** 圆心死区：手指离圆心太近时不换算角度，避免一按就跳一大截。 */
private const val DIAL_DEAD_ZONE_RATIO = 0.15f

/**
 * 助眠定时面板。
 *
 * 视觉直接对齐音乐播放页的睡眠定时器：一枚会呼吸的倒计时环 + 一排预设胶囊 + 可拨的自定义时长。
 * 数据换成了助眠自己的 [SleepCountdown]，提交时按"分钟"下发。
 *
 * 之所以不共用音乐那份实现：它绑定 `SleepTimerState` 与 `PlayerViewModel`，
 * 而助眠的定时器在另一个引擎里；共用需要抽出一个中间模型，收益不抵风险。
 */
@Composable
internal fun SleepTimerSheet(
    visible: Boolean,
    countdown: SleepCountdown?,
    pendingMinutes: Int,
    onSetMinutes: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var customMinutes by rememberSaveable { mutableIntStateOf(CUSTOM_DEFAULT_MINUTES) }

    // 收起来就退回预设视图：下次打开不会停在上次的拨盘状态里。
    LaunchedEffect(visible) { if (!visible) editing = false }

    // 正在走的定时器取整到分钟；没在走时，未播放前选好的时长也算"当前选中"。
    val activeMinutes =
        countdown
            ?.let { (it.totalMs / 60_000L).toInt() }
            ?: pendingMinutes.takeIf { it > 0 }

    fun beginEditing() {
        customMinutes = activeMinutes?.let { snapMinutes(it.toFloat()) } ?: CUSTOM_DEFAULT_MINUTES
        editing = true
    }

    SleepSheetHost(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Large)
                    .padding(top = Spacing.ExtraSmall, bottom = Spacing.Large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DialHeader(
                editing = editing,
                onBack = { editing = false },
                onDismiss = onDismiss,
            )

            DialHero(
                countdown = countdown,
                editing = editing,
                customMinutes = customMinutes,
                onCustomMinutesChange = { customMinutes = it },
            )

            Spacer(Modifier.height(Spacing.Large))

            AnimatedContent(
                targetState = editing,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using
                        SizeTransform(clip = false) { _, _ -> tween(260) }
                },
                label = "sleepTimerMode",
            ) { isEditing ->
                if (isEditing) {
                    StartButton(
                        minutes = customMinutes,
                        onClick = { onSetMinutes(customMinutes) },
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PresetRow(
                            activeMinutes = activeMinutes,
                            onSelect = onSetMinutes,
                        )
                        CustomDurationRow(
                            activeMinutes = activeMinutes,
                            onClick = ::beginEditing,
                        )
                        if (countdown != null) {
                            CancelRow(onClick = onCancel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialHeader(
    editing: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedVisibility(
            visible = editing,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
            exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180)),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text =
                if (editing) {
                    stringResource(R.string.settings_sleep_timer_custom)
                } else {
                    stringResource(R.string.sleep_timer_sheet_title)
                },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cancel),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 倒计时环 + 两侧步进按钮 + 一句提示。 */
@Composable
private fun DialHero(
    countdown: SleepCountdown?,
    editing: Boolean,
    customMinutes: Int,
    onCustomMinutesChange: (Int) -> Unit,
) {
    val sweepTarget =
        when {
            editing -> customFractionOf(customMinutes)
            countdown != null -> (countdown.remainingMs.toFloat() / countdown.totalMs).coerceIn(0f, 1f)
            else -> 0f
        }
    val sweep by animateFloatAsState(
        targetValue = sweepTarget,
        animationSpec = tween(durationMillis = 600),
        label = "sleepTimerSweep",
    )

    val hint =
        when {
            editing -> stringResource(R.string.settings_sleep_timer_custom_hint)
            countdown?.isFading == true -> stringResource(R.string.settings_sleep_timer_fading)
            countdown != null -> stringResource(R.string.settings_sleep_timer_preset_subtitle)
            else -> stringResource(R.string.settings_sleep_timer_dialog_subtitle)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StepButton(
                visible = editing,
                icon = Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.settings_sleep_timer_step_decrease, CUSTOM_STEP_MINUTES),
                enabled = customMinutes > CUSTOM_MIN_MINUTES,
                onClick = { onCustomMinutesChange(customMinutes - CUSTOM_STEP_MINUTES) },
            )
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                TimerDial(
                    sweep = sweep,
                    glowActive = editing || countdown != null,
                    knob = editing,
                    modifier =
                        if (editing) {
                            Modifier.ringDial(minutes = customMinutes, onMinutesChange = onCustomMinutesChange)
                        } else {
                            Modifier
                        },
                ) {
                    DialCenter(
                        countdown = countdown,
                        editing = editing,
                        customMinutes = customMinutes,
                    )
                }
            }
            StepButton(
                visible = editing,
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.settings_sleep_timer_step_increase, CUSTOM_STEP_MINUTES),
                enabled = customMinutes < CUSTOM_MAX_MINUTES,
                onClick = { onCustomMinutesChange(customMinutes + CUSTOM_STEP_MINUTES) },
            )
        }
        Spacer(Modifier.height(Spacing.Medium))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** 环心读数：编辑时显示待设定值，计时中显示剩余，空闲显示月亮。 */
@Composable
private fun DialCenter(
    countdown: SleepCountdown?,
    editing: Boolean,
    customMinutes: Int,
) {
    when {
        editing ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = customMinutes.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.settings_sleep_timer_unit_minutes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }

        countdown != null ->
            Text(
                text = formatRemaining(countdown.remainingMs),
                style = MaterialTheme.typography.headlineLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )

        else ->
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                modifier = Modifier.size(42.dp),
            )
    }
}

/**
 * 倒计时环。
 *
 * 画法和音乐播放页一致：底层一圈呼吸光晕撑出景深，中层灰色轨道，
 * 上层是沿周长从主色扫到第三色的渐变弧，弧头压一个亮点告诉人"走到哪儿了"。
 */
@Composable
private fun TimerDial(
    sweep: Float,
    glowActive: Boolean,
    knob: Boolean,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val ringAccent = MaterialTheme.colorScheme.tertiary
    // 轨道跟着 onSurface 走：浅色面板上是浅灰，暗色面板上是淡白，两边都不会消失。
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    val headCoreColor = Color.White

    val transition = rememberInfiniteTransition(label = "sleepDialGlow")
    val breathing =
        transition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.24f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 2200, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "sleepDialGlowAlpha",
        )
    val glow = breathing.value * if (glowActive) 1f else 0.45f

    Box(modifier = modifier.size(DialSize), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = DialStroke.toPx()
            val inset = stroke / 2f
            val diameter = size.minDimension - stroke
            val center = Offset(size.minDimension / 2f, size.minDimension / 2f)

            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to ringColor.copy(alpha = glow),
                        0.62f to ringColor.copy(alpha = glow * 0.45f),
                        1f to Color.Transparent,
                    ),
                radius = size.minDimension / 2f,
                center = center,
            )
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )

            val fraction = sweep.coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawArc(
                    brush =
                        Brush.sweepGradient(
                            0f to ringColor,
                            0.5f to ringAccent,
                            1f to ringColor,
                            center = center,
                        ),
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(diameter, diameter),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }

            if (fraction <= 0f && !knob) return@Canvas

            val headRadians = (-90f + 360f * fraction) * PI.toFloat() / 180f
            val head =
                Offset(
                    x = center.x + (diameter / 2f) * cos(headRadians),
                    y = center.y + (diameter / 2f) * sin(headRadians),
                )
            val haloRadius = if (knob) stroke * 2.1f else stroke * 1.5f
            val coreRadius = if (knob) stroke * 0.62f else stroke * 0.3f
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0f to ringColor.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                        center = head,
                        radius = haloRadius,
                    ),
                radius = haloRadius,
                center = head,
            )
            drawCircle(color = headCoreColor, radius = coreRadius, center = head)
            if (knob) {
                drawCircle(
                    color = ringColor,
                    radius = coreRadius,
                    center = head,
                    style = Stroke(width = stroke * 0.22f),
                )
            }
        }
        centerContent()
    }
}

/** 环两侧的步进按钮：拨盘给手感，它们给精度。 */
@Composable
private fun StepButton(
    visible: Boolean,
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + expandHorizontally(tween(220)),
        exit = fadeOut(tween(120)) + shrinkHorizontally(tween(180)),
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .sleepGlass(
                        shape = CircleShape,
                        tint = MaterialTheme.colorScheme.onSurface,
                        alpha = 0.06f,
                        borderAlpha = 0.18f,
                    ).clickHighlight(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 把环变成可拖的拨盘。 */
@Composable
private fun Modifier.ringDial(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentMinutes = rememberUpdatedState(minutes)
    val currentCallback = rememberUpdatedState(onMinutesChange)

    return pointerInput(Unit) {
        var lastAngle = 0f
        var raw = 0f

        detectDragGestures(
            onDragStart = { offset ->
                val ringCenter = Offset(size.width / 2f, size.height / 2f)
                lastAngle = angleAt(offset, ringCenter)
                raw = currentMinutes.value.toFloat()
            },
        ) { change, _ ->
            // 吃掉事件，否则纵向分量会被面板的下拉关闭抢走。
            change.consume()

            val ringCenter = Offset(size.width / 2f, size.height / 2f)
            val angle = angleAt(change.position, ringCenter)
            val radius = (change.position - ringCenter).getDistance()

            if (radius >= minOf(size.width, size.height) * DIAL_DEAD_ZONE_RATIO) {
                var delta = angle - lastAngle
                if (delta > 180f) delta -= 360f
                if (delta < -180f) delta += 360f
                raw =
                    (raw + delta / 360f * CUSTOM_RANGE_MINUTES)
                        .coerceIn(CUSTOM_MIN_MINUTES.toFloat(), CUSTOM_MAX_MINUTES.toFloat())
                val snapped = snapMinutes(raw)
                if (snapped != currentMinutes.value) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    currentCallback.value(snapped)
                }
            }
            // 死区里也要更新基准，否则手指移回来会突跳一大截。
            lastAngle = angle
        }
    }
}

/** 五枚预设胶囊。选中态填主色并轻微涨起，和音乐播放页的预设完全同款手感。 */
@Composable
private fun PresetRow(
    activeMinutes: Int?,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        PresetMinutes.forEach { minutes ->
            GlassPreset(
                minutes = minutes,
                selected = activeMinutes == minutes,
                onClick = { onSelect(minutes) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GlassPreset(
    minutes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val label = stringResource(R.string.settings_sleep_timer_minutes, minutes)

    val containerColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sleepPresetContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sleepPresetContent",
    )
    val scale by animateFloatAsState(
        targetValue =
            when {
                pressed -> 0.94f
                selected -> 1.04f
                else -> 1f
            },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "sleepPresetScale",
    )
    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(SleepGlassShape)
                .background(containerColor)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.sleepGlass(shape = SleepGlassShape, alpha = 0f, borderAlpha = 0.16f)
                    },
                ).clickHighlight(
                    enabled = true,
                    interactionSource = interactionSource,
                    onClick = onClick,
                ).padding(vertical = Spacing.Medium)
                .clearAndSetSemantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = minutes.toString(),
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.settings_sleep_timer_unit_minutes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.72f),
            maxLines = 1,
        )
    }
}

/** 自定义时长入口。 */
@Composable
private fun CustomDurationRow(
    activeMinutes: Int?,
    onClick: () -> Unit,
) {
    // 只把"不是预设档位"的值当成自定义，避免和上面那排胶囊同时高亮。
    val customActive = activeMinutes?.takeIf { it !in PresetMinutes }
    val active = customActive != null
    val contentColor =
        if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .padding(top = Spacing.Small)
                .fillMaxWidth()
                .then(
                    if (active) {
                        Modifier
                            .clip(SleepGlassShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f))
                    } else {
                        Modifier.sleepGlass(
                            shape = SleepGlassShape,
                            tint = MaterialTheme.colorScheme.onSurface,
                            alpha = 0.06f,
                            borderAlpha = 0.16f,
                        )
                    },
                ).clickHighlight(onClick = onClick)
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Tune,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(Spacing.Medium))
        Text(
            text = stringResource(R.string.settings_sleep_timer_custom),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (customActive != null) {
            Text(
                text = stringResource(R.string.settings_sleep_timer_minutes, customActive),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
            Spacer(Modifier.width(Spacing.ExtraSmall))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 自定义模式的提交按钮。 */
@Composable
private fun StartButton(
    minutes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(SleepGlassShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickHighlight(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Bedtime,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.Small))
        Text(
            text = stringResource(R.string.settings_sleep_timer_start),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Spacer(Modifier.width(Spacing.ExtraSmall))
        Text(
            text = stringResource(R.string.settings_sleep_timer_minutes, minutes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
        )
    }
}

/** 定时进行中才出现的取消入口。 */
@Composable
private fun CancelRow(onClick: () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
    ) {
        Row(
            modifier =
                Modifier
                    .padding(top = Spacing.Medium)
                    .fillMaxWidth()
                    .height(52.dp)
                    .sleepGlass(
                        shape = SleepGlassShape,
                        tint = MaterialTheme.colorScheme.onSurface,
                        alpha = 0.06f,
                        borderAlpha = 0.14f,
                    ).clickHighlight(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.Small))
            Text(
                text = stringResource(R.string.settings_sleep_timer_cancel),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 把任意分钟值吸附到 5 分钟一档。 */
private fun snapMinutes(minutes: Float): Int {
    val clamped = minutes.coerceIn(CUSTOM_MIN_MINUTES.toFloat(), CUSTOM_MAX_MINUTES.toFloat())
    val steps = ((clamped - CUSTOM_MIN_MINUTES) / CUSTOM_STEP_MINUTES).roundToInt()
    return (CUSTOM_MIN_MINUTES + steps * CUSTOM_STEP_MINUTES).coerceIn(CUSTOM_MIN_MINUTES, CUSTOM_MAX_MINUTES)
}

/** 分钟值在环上占的比例。 */
private fun customFractionOf(minutes: Int): Float = ((minutes - CUSTOM_MIN_MINUTES) / CUSTOM_RANGE_MINUTES).coerceIn(0f, 1f)

/** 触点相对圆心的极角，单位度。 */
private fun angleAt(
    position: Offset,
    center: Offset,
): Float = Math.toDegrees(atan2((position.y - center.y).toDouble(), (position.x - center.x).toDouble())).toFloat()

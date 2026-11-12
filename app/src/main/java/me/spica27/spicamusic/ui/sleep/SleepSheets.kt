@file:Suppress("FunctionName")

package me.spica27.spicamusic.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.sleep.domain.SleepDefaults
import me.spica27.spicamusic.feature.sleep.domain.SleepPlaybackState
import me.spica27.spicamusic.feature.sleep.domain.SleepStem
import me.spica27.spicamusic.ui.glass.TextButton
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.clickHighlight

/**
 * 混音面板：总音量 + 每条连续层的滑杆 + 停止。
 *
 * 每条音轨一张玻璃卡，卡片之间留缝——比一列排开的滑杆更容易分辨"这一条属于谁"。
 */
@Composable
internal fun SleepMixSheet(
    visible: Boolean,
    playback: SleepPlaybackState,
    onMasterVolumeChange: (Float) -> Unit,
    onTrackVolumeChange: (Long, Float) -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    SleepSheetHost(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    // 标题离把手再让开一点，不然看着像贴在卡片顶边上。
                    .padding(horizontal = Spacing.Large)
                    .padding(top = Spacing.Medium, bottom = Spacing.Large),
        ) {
            Text(
                text = playback.sceneName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.sleep_mix_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(Spacing.Large))

            VolumeCard(
                label = stringResource(R.string.sleep_master_volume),
                volume = playback.masterVolume,
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                emphasized = true,
                onVolumeChange = onMasterVolumeChange,
            )
            playback.tracks.forEach { track ->
                VolumeCard(
                    label = track.name,
                    volume = track.volume,
                    icon = null,
                    onVolumeChange = { onTrackVolumeChange(track.stemId, it) },
                )
            }

            Spacer(Modifier.height(Spacing.Large))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .sleepGlass(
                            shape = SleepGlassShape,
                            tint = MaterialTheme.colorScheme.error,
                            alpha = 0.10f,
                            borderAlpha = 0.20f,
                        ).clickHighlight(onClick = onStop),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.sleep_stop),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.Small),
                )
            }
        }
    }
}

/** 一条音量：玻璃小卡 + 名称 + 百分比 + 滑杆。总音量那张用主色描边强调。 */
@Composable
private fun VolumeCard(
    label: String,
    volume: Float,
    icon: ImageVector?,
    onVolumeChange: (Float) -> Unit,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.Small)
                .then(
                    Modifier.sleepGlass(
                        shape = SleepGlassShape,
                        // 用 onSurface 而不是白色：亮色页面上是淡淡一层灰，暗色面板上是淡淡一层白。
                        tint = if (emphasized) colors.primary else colors.onSurface,
                        alpha = if (emphasized) 0.10f else 0.05f,
                        borderAlpha = if (emphasized) 0.24f else 0.15f,
                    ),
                ).padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (emphasized) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.size(Spacing.ExtraSmall))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
                    // 音轨名统一走"近白"，只有百分比退到次要色——暗底上先读名字再看数值。
                    color =
                        if (emphasized) {
                            colors.primary
                        } else {
                            colors.onSurface.copy(alpha = 0.88f)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = if (emphasized) colors.primary else colors.onSurfaceVariant,
            )
        }
        SleepVolumeSlider(value = volume, onValueChange = onVolumeChange)
    }
}

/**
 * 音量滑杆。
 *
 * 不用 M3 的 [Slider]：它默认的轨道有 16dp 高、两侧还带内边距，五条叠在一起
 * 像一排粗蓝条，暗色玻璃上非常抢。这里自绘一条 4dp 细轨 + 一颗小圆点，
 * 手感和系统滑杆一致（横向拖拽 / 点按跳转），纵向滑动仍然交给面板滚动。
 */
@Composable
private fun SleepVolumeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    // 圆点要在面板上跳出来：暗面板用近白，亮面板用近黑。
    val thumbColor =
        if (colors.surface.luminance() < 0.5f) {
            Color.White.copy(alpha = 0.90f)
        } else {
            colors.onSurface.copy(alpha = 0.82f)
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(28.dp)
                .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }.pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onValueChange((offset.x / size.width).coerceIn(0f, 1f))
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onValueChange((change.position.x / size.width).coerceIn(0f, 1f))
                        },
                    )
                },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = 4.dp.toPx()
            val thumbRadius = 7.dp.toPx()
            val usable = (size.width - thumbRadius * 2).coerceAtLeast(1f)
            val progressX = thumbRadius + usable * value.coerceIn(0f, 1f)

            drawRoundRect(
                color = colors.onSurface.copy(alpha = 0.13f),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            drawRoundRect(
                brush =
                    Brush.horizontalGradient(
                        colors = listOf(accent.copy(alpha = 0.45f), accent),
                        startX = 0f,
                        endX = progressX.coerceAtLeast(1f),
                    ),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(progressX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f),
            )
            // 一圈同色光晕把圆点从轨道里"托"出来，最后压一颗近白实心。
            drawCircle(color = accent.copy(alpha = 0.20f), radius = thumbRadius * 2f, center = Offset(progressX, centerY))
            drawCircle(color = thumbColor, radius = thumbRadius, center = Offset(progressX, centerY))
        }
    }
}

/**
 * 自选混音：从长循环素材里挑最多 4 层叠加。
 *
 * 挑选用和定时面板一致的圆形玻璃按钮——两处都是"点一个圆"的选择动作，
 * 交互形状统一后，用户不用为同一类操作重新学习。
 * 选择结果由外部持有并落库，所以关掉面板再打开，还是上次那一套。
 */
@Composable
internal fun SleepCustomSheet(
    visible: Boolean,
    stems: List<SleepStem>,
    selectedIds: List<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxTracks = SleepDefaults.MAX_CUSTOM_TRACKS
    val atLimit = selectedIds.size >= maxTracks

    SleepSheetHost(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.Large)
                    .padding(top = Spacing.Medium, bottom = Spacing.Large),
        ) {
            Text(
                text = stringResource(R.string.sleep_custom),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text =
                    if (selectedIds.isEmpty()) {
                        stringResource(R.string.sleep_custom_hint, maxTracks)
                    } else {
                        stringResource(R.string.sleep_custom_selected, selectedIds.size, maxTracks)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(Spacing.ExtraLarge))

            // 素材固定十几条，用 Column + Row 手动分行，圆的大小只由列宽决定，不依赖懒加载测量。
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                stems.chunked(CUSTOM_COLUMNS).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                        row.forEach { stem ->
                            StemButton(
                                stem = stem,
                                selected = stem.id in selectedIds,
                                enabled = stem.id in selectedIds || !atLimit,
                                onClick = { onToggle(stem.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 最后一行不满时补空位，保证圆的大小和上面几行一致。
                        repeat(CUSTOM_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.ExtraLarge))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onClear, enabled = selectedIds.isNotEmpty()) {
                    Text(stringResource(R.string.sleep_custom_clear))
                }
                Spacer(Modifier.weight(1f))
                Row(
                    modifier =
                        Modifier
                            .height(48.dp)
                            .sleepGlass(
                                shape = SleepGlassShape,
                                tint = MaterialTheme.colorScheme.primary,
                                alpha = 0.92f,
                                borderAlpha = 0.3f,
                            ).clickHighlight(enabled = selectedIds.isNotEmpty(), onClick = onPlay)
                            .padding(horizontal = Spacing.Large),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text =
                            if (selectedIds.isEmpty()) {
                                stringResource(R.string.sleep_play)
                            } else {
                                stringResource(R.string.sleep_custom_play, selectedIds.size)
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color =
                            if (selectedIds.isNotEmpty()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                            },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private const val CUSTOM_COLUMNS = 4

/** 自选面板里的层按钮：圆形玻璃，选中后填充主色。 */
@Composable
private fun StemButton(
    stem: SleepStem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor =
        when {
            selected -> colors.onPrimary
            enabled -> colors.onSurface
            else -> colors.onSurfaceVariant.copy(alpha = 0.45f)
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .then(glassCircle(selected = selected))
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, colors.onSurface.copy(alpha = 0.14f), CircleShape)
                    },
                ).clickHighlight(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stem.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@file:Suppress("FunctionName")

package me.spica27.spicamusic.ui.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.landscapist.image.LandscapistImage
import dev.chrisbanes.haze.rememberHazeState
import me.spica27.spicamusic.R
import me.spica27.spicamusic.feature.sleep.domain.SleepCountdown
import me.spica27.spicamusic.feature.sleep.domain.SleepDefaults
import me.spica27.spicamusic.feature.sleep.domain.SleepPlaybackState
import me.spica27.spicamusic.feature.sleep.domain.SleepScene
import me.spica27.spicamusic.ui.glass.IconButton
import me.spica27.spicamusic.ui.glass.Surface
import me.spica27.spicamusic.ui.glass.liquidGlassSource
import me.spica27.spicamusic.ui.theme.LayoutTokens
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.clickHighlight
import me.spica27.spicamusic.ui.widget.rememberIOSOverScrollEffect
import org.koin.compose.viewmodel.koinActivityViewModel

/** 场景卡片圆角，跟播放页的大封面保持一致的手感。 */
private val CardShape = RoundedCornerShape(22.dp)

/** 底部常驻定时条的高度预算，让网格滚到底时不会被它压住。 */
private val DockReserve = 96.dp

/** 场景网格列数：5 个场景 + 自选正好 6 张卡，两行排满、一屏看完。 */
private const val SCENE_COLUMNS = 3

@Composable
fun SleepPage(bottomContentPadding: Dp = 0.dp) {
    val viewModel: SleepViewModel = koinActivityViewModel()
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val pendingMinutes by viewModel.pendingTimerMinutes.collectAsStateWithLifecycle()
    val customPick by viewModel.customPick.collectAsStateWithLifecycle()

    var showTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showMixSheet by rememberSaveable { mutableStateOf(false) }
    var showCustomSheet by rememberSaveable { mutableStateOf(false) }

    // 面板要和页面同窗口才能模糊身后的内容，所以采样源由页面自己提供。
    val hazeState = rememberHazeState()

    val gutter = LayoutTokens.MusicHeaderHorizontalPadding
    // 和曲库/音乐页共用同一套刊头留白，四个一级页面的标题才会落在同一条基线上。
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    CompositionLocalProvider(LocalSleepSheetHaze provides hazeState) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().liquidGlassSource(hazeState)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(SCENE_COLUMNS),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding =
                        PaddingValues(
                            start = gutter,
                            end = gutter,
                            top = statusBarTop + 56.dp,
                            bottom = bottomContentPadding + DockReserve,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    overscrollEffect = rememberIOSOverScrollEffect(Orientation.Vertical),
                ) {
                    item(key = "masthead", span = { GridItemSpan(maxLineSpan) }) {
                        SleepMasthead(
                            playback = playback,
                            sceneCount = scenes.size,
                            modifier = Modifier.padding(top = Spacing.Large),
                        )
                    }

                    items(items = scenes, key = { it.key }) { scene ->
                        SceneCard(
                            scene = scene,
                            isCurrent = playback.isPlaying && playback.sceneId == scene.id,
                            onClick = { viewModel.toggleScene(scene) },
                        )
                    }

                    item(key = "custom") {
                        CustomCard(
                            pickedCount = customPick.size,
                            isPlaying = playback.isPlaying && playback.sceneId == SleepDefaults.CUSTOM_SCENE_ID,
                            onClick = { showCustomSheet = true },
                        )
                    }
                }

                SleepDock(
                    playing = playback.isPlaying,
                    countdown = countdown,
                    pendingMinutes = pendingMinutes,
                    onOpenTimer = { showTimerSheet = true },
                    onOpenMix = { showMixSheet = true },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = gutter)
                            .padding(bottom = bottomContentPadding + Spacing.Medium),
                )
            }

            SleepTimerSheet(
                visible = showTimerSheet,
                countdown = countdown,
                pendingMinutes = pendingMinutes,
                onSetMinutes = { minutes ->
                    viewModel.setTimerMinutes(minutes)
                    // 选完就收起面板：底部那行会立刻显示新的剩余时间，比留在面板里更直接。
                    showTimerSheet = false
                },
                onCancel = {
                    viewModel.setTimerMinutes(0)
                    viewModel.clearPendingTimer()
                    showTimerSheet = false
                },
                onDismiss = { showTimerSheet = false },
            )

            SleepMixSheet(
                visible = showMixSheet,
                playback = playback,
                onMasterVolumeChange = viewModel::setMasterVolume,
                onTrackVolumeChange = viewModel::setTrackVolume,
                onStop = {
                    viewModel.stop()
                    showMixSheet = false
                },
                onDismiss = { showMixSheet = false },
            )

            SleepCustomSheet(
                visible = showCustomSheet,
                stems = viewModel.customStems,
                selectedIds = customPick,
                onToggle = viewModel::toggleCustomStem,
                onClear = viewModel::clearCustomPick,
                onPlay = {
                    viewModel.playPickedCustom()
                    showCustomSheet = false
                },
                onDismiss = { showCustomSheet = false },
            )
        }
    }
}

/** 页面顶部：大标题 + 一行状态，沿用曲库页的刊头做法。 */
@Composable
private fun SleepMasthead(
    playback: SleepPlaybackState,
    sceneCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.sleep_scene_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                if (playback.isPlaying) {
                    stringResource(R.string.sleep_now_playing, playback.sceneName, playback.tracks.size)
                } else {
                    stringResource(R.string.sleep_scene_subtitle, sceneCount)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** 场景卡：封面铺满、底部压暗、名字居中，和参考排版的占位一致。 */
@Composable
private fun SceneCard(
    scene: SleepScene,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = remember(scene.key) { sleepSceneArt(scene.key) }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CardShape)
                .clickHighlight(onClick = onClick)
                .then(
                    if (isCurrent) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CardShape)
                    } else {
                        Modifier
                    },
                ),
    ) {
        if (art != null) {
            // 交给项目统一的图片加载器：它会按控件尺寸降采样，1080×1080 的原图不会整张进内存。
            LandscapistImage(
                imageModel = { art },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.20f)))
        }

        // 压暗整张图，保证白字在亮部也读得清；参考图里文字是居中的。
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black.copy(alpha = 0.10f),
                                Color.Black.copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.52f),
                            ),
                    ),
                ),
        )

        Text(
            text = scene.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = Spacing.Medium),
        )

        if (isCurrent) {
            // 3 列后卡片只有 100dp 左右，放不下"正在播放"文字角标，改成一个圆形图标；
            // 具体在播什么，底部常驻条里已经写着场景名。
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.Small)
                        .size(22.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = stringResource(R.string.sleep_playing),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(5.dp),
                )
            }
        }
    }
}

/**
 * "自选"卡：和第 5 个场景并列占第 6 格（第二行第三个），大小完全一致。
 *
 * 没有封面，用容器色 + 图标表示这是一个动作而不是场景；右上角小圆标是"上次挑了几层"，
 * 不占文字位，卡片尺寸不会因为它变化。
 */
@Composable
private fun CustomCard(
    pickedCount: Int,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sleepGlass(shape = CardShape, alpha = 0.52f, borderAlpha = 0.2f)
                .then(if (isPlaying) Modifier.border(2.dp, colors.primary, CardShape) else Modifier)
                .clickHighlight(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .sleepGlass(shape = CircleShape, tint = colors.surfaceContainerHighest, alpha = 0.5f, borderAlpha = 0.2f),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = stringResource(R.string.sleep_custom),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }

        if (pickedCount > 0) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.Small)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pickedCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimary,
                )
            }
        }
    }
}

/**
 * 底部常驻的定时入口。
 *
 * 播放中不再重复显示"场景名 + 停止"——那部分已经由底部悬浮播放条承担（还能点开播放页），
 * 这里只保留定时，外加一个直通混音面板的小按钮，避免两条并排的信息互相打架。
 */
@Composable
private fun SleepDock(
    playing: Boolean,
    countdown: SleepCountdown?,
    pendingMinutes: Int,
    onOpenTimer: () -> Unit,
    onOpenMix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // 玻璃底：和底部导航同一套语言，让常驻条浮在封面墙上面而不是"贴"在上面。
                .sleepGlass(shape = RoundedCornerShape(24.dp), alpha = 0.74f),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickHighlight(onClick = onOpenTimer)
                    .padding(start = Spacing.Large, end = Spacing.Small)
                    .heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.sleep_timer),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text =
                    when {
                        countdown != null && countdown.isFading ->
                            stringResource(R.string.sleep_timer_fading, formatRemaining(countdown.remainingMs))
                        countdown != null ->
                            stringResource(R.string.sleep_timer_remaining, formatRemaining(countdown.remainingMs))
                        // 未播放时选时长只是暂存，必须说清楚，否则用户会以为已经开始倒计时。
                        pendingMinutes > 0 -> stringResource(R.string.sleep_timer_pending_short, pendingMinutes)
                        !playing -> stringResource(R.string.sleep_timer_hint_idle)
                        else -> stringResource(R.string.sleep_timer_none)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (countdown != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playing) {
                IconButton(onClick = onOpenMix, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.sleep_mix),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                Spacer(Modifier.padding(end = Spacing.Small))
            }
        }
    }
}

internal fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

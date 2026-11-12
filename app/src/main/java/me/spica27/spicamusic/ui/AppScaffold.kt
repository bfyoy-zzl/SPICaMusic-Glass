package me.spica27.spicamusic.ui

import android.app.Activity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.skydoves.landscapist.core.Landscapist
import com.skydoves.landscapist.image.LocalLandscapist
import dev.chrisbanes.haze.rememberHazeState
import me.spica27.spicamusic.common.entity.ThemeColorStyle
import me.spica27.spicamusic.core.preferences.PreferencesManager
import me.spica27.spicamusic.ui.adaptive.LocalPaneInfo
import me.spica27.spicamusic.ui.adaptive.LocalShellInsets
import me.spica27.spicamusic.ui.adaptive.LocalWindowLayout
import me.spica27.spicamusic.ui.adaptive.PaneInfo
import me.spica27.spicamusic.ui.adaptive.ShellInsets
import me.spica27.spicamusic.ui.adaptive.rememberWindowLayout
import me.spica27.spicamusic.ui.glass.GlassBackdropLayer
import me.spica27.spicamusic.ui.glass.LiquidGlassConfig
import me.spica27.spicamusic.ui.glass.LocalDialogHazeState
import me.spica27.spicamusic.ui.glass.LocalGlassHazeState
import me.spica27.spicamusic.ui.glass.LocalLiquidGlassConfig
import me.spica27.spicamusic.ui.glass.liquidGlassSource
import me.spica27.spicamusic.ui.navigation.LocalBackStack
import me.spica27.spicamusic.ui.navigation.MotionDialogSceneStrategy
import me.spica27.spicamusic.ui.navigation.Route
import me.spica27.spicamusic.ui.navigation.appEntryProvider
import me.spica27.spicamusic.ui.navigation.rememberAppNavigator
import me.spica27.spicamusic.ui.player.LocalPlayerViewModel
import me.spica27.spicamusic.ui.player.PlayerViewModel
import me.spica27.spicamusic.ui.theme.SPICaMusicTheme
import me.spica27.spicamusic.utils.Nav3Transitions
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

@Composable
fun AppScaffold() {
    val preferencesManager = koinInject<PreferencesManager>()

    val isDarkMode by
        preferencesManager
            .getBoolean(PreferencesManager.Keys.DARK_MODE)
            .collectAsStateWithLifecycle(false)

    val liquidGlassEnabled by
        preferencesManager
            // 实验副本默认开启液态玻璃；用户仍可在设置里关闭。
            .getBoolean(PreferencesManager.Keys.LIQUID_GLASS_ENABLED, true)
            .collectAsStateWithLifecycle(true)

    val themeColorStyleValue by
        preferencesManager
            .getString(PreferencesManager.Keys.THEME_COLOR_STYLE, ThemeColorStyle.Textured.value)
            .collectAsStateWithLifecycle(ThemeColorStyle.Textured.value)

    val playerViewModel: PlayerViewModel = koinActivityViewModel()
    val color by playerViewModel.playerThemeColor.collectAsStateWithLifecycle()
    val keepScreenOn by
        preferencesManager
            .getBoolean(PreferencesManager.Keys.KEEP_SCREEN_ON)
            .collectAsStateWithLifecycle(false)
    val isPlaying by playerViewModel.isPlaying.collectAsStateWithLifecycle()

    KeepScreenOnEffect(enabled = keepScreenOn && isPlaying)

    val view = LocalView.current
    LaunchedEffect(isDarkMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkMode
    }

    val navigator = rememberAppNavigator()
    val dialogStrategy = remember { MotionDialogSceneStrategy<Route>() }
    val sceneStrategies = remember(dialogStrategy) { listOf(dialogStrategy) }
    val saveableStateDecorator = rememberSaveableStateHolderNavEntryDecorator<Route>()
    val viewModelStoreDecorator = rememberViewModelStoreNavEntryDecorator<Route>()
    val entryDecorators =
        remember(saveableStateDecorator, viewModelStoreDecorator) {
            listOf(saveableStateDecorator, viewModelStoreDecorator)
        }
    val entryProvider = appEntryProvider()
    val dialogHazeState = rememberHazeState()

    val windowLayout = rememberWindowLayout()

    SPICaMusicTheme(
        darkTheme = isDarkMode,
        themeColor = color,
        themeColorStyle = ThemeColorStyle.fromString(themeColorStyleValue),
    ) {
        CompositionLocalProvider(
            LocalLiquidGlassConfig provides LiquidGlassConfig(enabled = liquidGlassEnabled),
            LocalDialogHazeState provides dialogHazeState,
            LocalGlassHazeState provides dialogHazeState,
            LocalPlayerViewModel provides playerViewModel,
            LocalBackStack provides navigator,
            LocalLandscapist provides Landscapist.getInstance(),
            LocalWindowLayout provides windowLayout,
            LocalShellInsets provides ShellInsets(),
            LocalPaneInfo provides PaneInfo(),
        ) {
            // 背景层单独挂 Haze 源：玻璃只采样底图，不会采样到玻璃自身。
            // 对应参考库 LayerBackdrop { 背景内容 } + drawBackdrop { blur/lens/vibrancy }。
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .liquidGlassSource(dialogHazeState),
                ) {
                    GlassBackdropLayer()
                }
                SharedTransitionLayout {
                    NavDisplay(
                        modifier = Modifier.fillMaxSize(),
                        backStack = navigator.entries,
                        onBack = { navigator.removeLastOrNull() },
                        entryDecorators = entryDecorators,
                        sceneStrategies = sceneStrategies,
                        entryProvider = entryProvider,
                        transitionSpec = {
                            Nav3Transitions.iosStyle()
                        },
                        popTransitionSpec = {
                            Nav3Transitions.iosStylePop()
                        },
                        sharedTransitionScope = this,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current

    DisposableEffect(view, enabled) {
        val previous = view.keepScreenOn
        view.keepScreenOn = enabled

        onDispose {
            view.keepScreenOn = previous
        }
    }
}

package me.spica27.spicamusic.ui.sleep

import androidx.annotation.DrawableRes
import me.spica27.spicamusic.R

/**
 * 场景封面。
 *
 * 封面放在 `res/drawable-nodpi/` 而不是 assets：这里只有固定几张，
 * 走资源 id 可以让图片加载器按控件尺寸降采样，1080×1080 的原图不会整张进内存。
 * 新增场景时在这里补一行，缺图会回退成纯色块。
 */
@DrawableRes
internal fun sleepSceneArt(key: String): Int? =
    when (key) {
        "rain" -> R.drawable.sleep_scene_rain
        "forest" -> R.drawable.sleep_scene_forest
        "fireplace" -> R.drawable.sleep_scene_fire
        "ocean" -> R.drawable.sleep_scene_ocean
        "summer_night" -> R.drawable.sleep_scene_night
        else -> null
    }

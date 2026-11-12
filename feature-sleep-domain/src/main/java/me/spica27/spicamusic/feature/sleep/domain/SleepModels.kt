package me.spica27.spicamusic.feature.sleep.domain

/** 助眠音轨的类别，决定界面图标与后续的默认增益策略。 */
enum class SleepStemKind {
    NOISE,
    RAIN,
    FIRE,
    WIND,
    WATER,
    MUSIC,
    ANIMAL,
    CLOCK,
    OTHER,
    ;

    companion object {
        fun fromName(value: String): SleepStemKind = entries.firstOrNull { it.name == value } ?: OTHER
    }
}

/**
 * 音层的播放方式。
 *
 * - [LINE]：一条首尾相接的循环素材，始终在播，构成声音的"底噪"。例如雨声、溪流、炉火。
 * - [POINT]：一串短片段，按随机间隔挑一个播一次，用来制造偶发的细节。
 *   例如雷声、鸟叫、翻书声——这类声音如果也做成循环，几秒就会听出重复。
 */
enum class SleepLayerKind {
    LINE,
    POINT,
    ;

    companion object {
        fun fromName(value: String): SleepLayerKind = entries.firstOrNull { it.name == value } ?: LINE
    }
}

/**
 * 一条可独立播放的助眠音轨（stem）。
 *
 * [calibratedGain] 是素材侧的增益：既做响度补偿（雨声比钢琴曲响十几 dB），
 * 也承担多层叠加时的余量控制。运行时实际音量 =
 * 总音量 × [calibratedGain] × 用户滑杆 × 定时器渐弱系数。
 */
data class SleepStem(
    val id: Long,
    val name: String,
    val kind: SleepStemKind,
    val layer: SleepLayerKind = SleepLayerKind.LINE,
    /** [SleepLayerKind.LINE] 用的循环素材；[SleepLayerKind.POINT] 时为空。 */
    val assetPath: String = "",
    /** [SleepLayerKind.POINT] 的候选片段，每次随机挑一个。 */
    val clipPaths: List<String> = emptyList(),
    /** [SleepLayerKind.POINT] 的平均播放间隔。 */
    val intervalMs: Long = 0L,
    val calibratedGain: Float = 1f,
)

/** 一个助眠场景：若干音轨的组合，例如"夏雨"= 河流 + 夏雨 + 雨打树叶 + 偶发雷声。 */
data class SleepScene(
    val id: Long,
    val key: String,
    val name: String,
    val description: String,
    val stems: List<SleepStem>,
) {
    /** 混音面板里可调滑杆的音层（点状层是偶发音效，不给单独滑杆）。 */
    val lineStems: List<SleepStem> get() = stems.filter { it.layer == SleepLayerKind.LINE }
}

/** 单条音轨的用户可调状态。 */
data class SleepTrackState(
    val stemId: Long,
    val name: String,
    val kind: SleepStemKind,
    /** 用户滑杆音量，0..1。 */
    val volume: Float,
)

/** 混音播放器的整体状态。 */
data class SleepPlaybackState(
    val isPlaying: Boolean = false,
    val sceneId: Long? = null,
    val sceneName: String = "",
    /** 场景的稳定标识，用来取封面；自选场景为空串。 */
    val sceneKey: String = "",
    val masterVolume: Float = 0.8f,
    val tracks: List<SleepTrackState> = emptyList(),
)

/**
 * 定时器倒计时。[fadeOutMs] 为最后的渐弱窗口，期间音量线性降到 0，
 * 到点后再暂停——直接静音会把人从浅睡里惊醒。
 */
data class SleepCountdown(
    val totalMs: Long,
    val remainingMs: Long,
    val fadeOutMs: Long,
) {
    val isFading: Boolean get() = fadeOutMs > 0L && remainingMs <= fadeOutMs

    /** 当前渐弱系数，1 表示未进入渐弱。 */
    val fadeScale: Float
        get() = if (!isFading || fadeOutMs <= 0L) 1f else (remainingMs.toFloat() / fadeOutMs).coerceIn(0f, 1f)
}

object SleepDefaults {
    /** 场景启动淡入时长：突然爆发的雨声会把人吓醒。 */
    const val FADE_IN_MS: Long = 4_000L

    /**
     * 播放中切换场景的淡入时长。
     *
     * 不能沿用 4 秒：那段时间里旧场景已经停了、新场景还几乎没声，听感是"滑过去半天才有声音"。
     * 900ms 足够盖住硬切的爆音，又让人立刻听到换成了什么。
     */
    const val SWITCH_FADE_IN_MS: Long = 900L

    /** 手动停止时的渐弱时长。 */
    const val FADE_OUT_MS: Long = 1_200L

    /** 定时结束前的最长渐弱窗口，短定时按四分之一时长收缩。 */
    const val MAX_TIMER_FADE_MS: Long = 30_000L

    /** 同一场景里连续层的上限。素材本身已按层数做过增益衰减，再加层只会糊成一片。 */
    const val MAX_LINE_TRACKS: Int = 4

    /** "自选"混音最多同时叠加的层数。 */
    const val MAX_CUSTOM_TRACKS: Int = 4

    /** 自选场景没有数据库记录，用一个固定负数作为会话内的 id。 */
    const val CUSTOM_SCENE_ID: Long = -1L

    /** 定时器面板的预设（分钟）。 */
    val TIMER_PRESETS: List<Int> = (1..20).map { it * 5 }
}

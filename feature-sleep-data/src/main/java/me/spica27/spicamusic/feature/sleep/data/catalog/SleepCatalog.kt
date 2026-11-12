package me.spica27.spicamusic.feature.sleep.data.catalog

import me.spica27.spicamusic.feature.sleep.domain.SleepLayerKind
import me.spica27.spicamusic.feature.sleep.domain.SleepStem
import me.spica27.spicamusic.feature.sleep.domain.SleepStemKind

/**
 * 内置助眠场景。
 *
 * 素材放在 `feature-sleep-data/src/main/assets/sleep/audio/`，来源是作者此前一版助眠 App 的
 * 素材库：14 段约 3 分钟的**长循环**构成底噪，其余几十段 1~30 秒的短片段按随机间隔点缀，
 * 避免"几秒就听出重复"的机械感。
 *
 * 每条音层的增益在这里手工给定，含义是：多层叠加时既要不糊、也要不削波。
 * 一个场景的连续层增益之和大致控制在 1.3 以内——噪声类素材互不相关，
 * 叠加后的有效电平约为各层平方和开方，这个量级听起来刚好，不会爆。
 *
 * 换素材：把文件丢进 `assets/sleep/audio/`，在下面加一行即可，数据库和界面都不用改。
 */
internal object SleepCatalog {
    private const val AUDIO_DIR = "sleep/audio/"

    private fun clip(name: String) = AUDIO_DIR + name + ".mp3"

    /** 连续层：一段循环素材。 */
    private fun line(
        name: String,
        kind: SleepStemKind,
        gain: Float,
    ) = StemSeed(
        name = name,
        kind = kind,
        layer = SleepLayerKind.LINE,
        assetPath = clip(name),
        calibratedGain = gain,
    )

    /** 点状层：一组短片段，按 [intervalMs] 的平均间隔随机播放其中一个。 */
    private fun point(
        name: String,
        kind: SleepStemKind,
        gain: Float,
        intervalMs: Long,
        vararg clips: String,
    ) = StemSeed(
        name = name,
        kind = kind,
        layer = SleepLayerKind.POINT,
        clipPaths = clips.map { clip(it) },
        intervalMs = intervalMs,
        calibratedGain = gain,
    )

    data class StemSeed(
        val name: String,
        val kind: SleepStemKind,
        val layer: SleepLayerKind,
        val assetPath: String = "",
        val clipPaths: List<String> = emptyList(),
        val intervalMs: Long = 0L,
        val calibratedGain: Float,
    )

    data class SceneSeed(
        val key: String,
        val name: String,
        val description: String,
        val stems: List<StemSeed>,
    )

    val scenes: List<SceneSeed> =
        listOf(
            SceneSeed(
                key = "rain",
                name = "夏雨",
                description = "六月与夏雨的邂逅",
                stems =
                    listOf(
                        line("夏雨", SleepStemKind.RAIN, 0.62f),
                        line("河流", SleepStemKind.WATER, 0.55f),
                        line("雨打树叶", SleepStemKind.RAIN, 0.45f),
                        point("雷", SleepStemKind.OTHER, 0.85f, 42_000L, "雷1", "雷2", "雷3"),
                        point(
                            "雨滴",
                            SleepStemKind.RAIN,
                            0.35f,
                            11_000L,
                            "雨滴1",
                            "雨滴2",
                            "雨滴3",
                            "雨滴4",
                            "雨滴5",
                            "雨滴6",
                            "雨滴7",
                            "雨滴8",
                        ),
                        point("青蛙", SleepStemKind.ANIMAL, 0.28f, 26_000L, "青蛙1", "青蛙2", "青蛙3", "青蛙4", "青蛙5"),
                    ),
            ),
            SceneSeed(
                key = "forest",
                name = "森林",
                description = "把你的秘密藏进森林",
                stems =
                    listOf(
                        line("森林", SleepStemKind.WIND, 0.6f),
                        line("溪流", SleepStemKind.WATER, 0.52f),
                        line("鸟鸣", SleepStemKind.ANIMAL, 0.4f),
                        point(
                            "鸟叫",
                            SleepStemKind.ANIMAL,
                            0.36f,
                            14_000L,
                            "鸟1",
                            "鸟2",
                            "鸟3",
                            "鸟4",
                            "鸟5",
                            "鸟6",
                            "鸟7",
                            "鸟8",
                            "鸟9",
                            "鸟10",
                        ),
                        point(
                            "树叶声",
                            SleepStemKind.WIND,
                            0.3f,
                            9_000L,
                            "风吹树叶1",
                            "风吹树叶2",
                            "风吹树叶3",
                            "风吹树叶4",
                            "风吹树叶5",
                        ),
                        point("虫鸣", SleepStemKind.ANIMAL, 0.26f, 30_000L, "庄稼地1", "庄稼地2", "庄稼地3", "庄稼地4", "庄稼地5"),
                    ),
            ),
            SceneSeed(
                key = "fireplace",
                name = "炉火",
                description = "温暖冬日的闲暇时光",
                stems =
                    listOf(
                        line("炉火", SleepStemKind.FIRE, 0.62f),
                        line("窗外的风", SleepStemKind.WIND, 0.4f),
                        line("钢琴", SleepStemKind.MUSIC, 0.42f),
                        line("时钟", SleepStemKind.CLOCK, 0.26f),
                        point("火焰噼啪", SleepStemKind.FIRE, 0.6f, 16_000L, "火焰啪啪1", "火焰啪啪2", "火焰啪啪3", "火焰啪啪4"),
                        point("翻书", SleepStemKind.OTHER, 0.34f, 34_000L, "翻书1", "翻书2", "翻书3", "翻书4", "翻书5"),
                    ),
            ),
            SceneSeed(
                key = "ocean",
                name = "海洋",
                description = "海边漫步的气息",
                stems =
                    listOf(
                        line("近海", SleepStemKind.WATER, 0.58f),
                        line("远海", SleepStemKind.WATER, 0.5f),
                        line("空气", SleepStemKind.WIND, 0.3f),
                        point("海浪", SleepStemKind.WATER, 0.5f, 11_000L, "海浪1", "海浪2", "海浪3", "海浪4", "海浪5", "海浪6"),
                        point("海鸥", SleepStemKind.ANIMAL, 0.4f, 22_000L, "海鸥1", "海鸥2", "海鸥3", "海鸥4", "海鸥5"),
                        point("船笛", SleepStemKind.OTHER, 0.42f, 75_000L, "船笛1", "船笛2"),
                    ),
            ),
            SceneSeed(
                key = "summer_night",
                name = "夏夜",
                description = "宁静夏夜的虫鸣",
                stems =
                    listOf(
                        line("细雨虫鸣", SleepStemKind.ANIMAL, 0.52f),
                        line("窗外的风", SleepStemKind.WIND, 0.44f),
                        line("河流", SleepStemKind.WATER, 0.38f),
                        line("空气", SleepStemKind.WIND, 0.28f),
                        point("青蛙", SleepStemKind.ANIMAL, 0.4f, 24_000L, "青蛙1", "青蛙2", "青蛙3", "青蛙4", "青蛙5"),
                    ),
            ),
        )

    /**
     * "自选"可用的单层素材。
     *
     * id 用负数且与数据库无关：自选是会话内的临时场景，不落库，
     * 负数可以保证不会和数据库里的正数 stemId 撞在一起（音量映射表以 id 为键）。
     */
    val customStems: List<SleepStem> =
        listOf(
            "夏雨" to SleepStemKind.RAIN,
            "细雨虫鸣" to SleepStemKind.ANIMAL,
            "森林" to SleepStemKind.WIND,
            "溪流" to SleepStemKind.WATER,
            "河流" to SleepStemKind.WATER,
            "雨打树叶" to SleepStemKind.RAIN,
            "鸟鸣" to SleepStemKind.ANIMAL,
            "炉火" to SleepStemKind.FIRE,
            "钢琴" to SleepStemKind.MUSIC,
            "时钟" to SleepStemKind.CLOCK,
            "窗外的风" to SleepStemKind.WIND,
            "近海" to SleepStemKind.WATER,
            "远海" to SleepStemKind.WATER,
            "空气" to SleepStemKind.WIND,
        ).mapIndexed { index, (name, kind) ->
            SleepStem(
                id = CUSTOM_STEM_ID_BASE - index,
                name = name,
                kind = kind,
                layer = SleepLayerKind.LINE,
                assetPath = clip(name),
                calibratedGain = CUSTOM_STEM_GAIN,
            )
        }

    /** 自选时每层的基准增益：最多叠 4 层，留足余量。 */
    private const val CUSTOM_STEM_GAIN = 0.55f

    private const val CUSTOM_STEM_ID_BASE = -1000L
}

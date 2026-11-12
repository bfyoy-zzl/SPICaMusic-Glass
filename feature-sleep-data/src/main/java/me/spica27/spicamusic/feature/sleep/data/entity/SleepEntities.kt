package me.spica27.spicamusic.feature.sleep.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "SleepScene")
data class SleepSceneEntity(
    @PrimaryKey(autoGenerate = true)
    val sceneId: Long? = null,
    /** 稳定标识，用于内置场景识别与后续迁移。 */
    val sceneKey: String,
    val name: String,
    val description: String,
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "SleepStem",
    foreignKeys = [
        ForeignKey(
            entity = SleepSceneEntity::class,
            parentColumns = ["sceneId"],
            childColumns = ["sceneId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sceneId")],
)
data class SleepStemEntity(
    @PrimaryKey(autoGenerate = true)
    val stemId: Long? = null,
    val sceneId: Long,
    val name: String,
    /** [me.spica27.spicamusic.feature.sleep.domain.SleepStemKind] 的名称。 */
    val kind: String,
    /** [me.spica27.spicamusic.feature.sleep.domain.SleepLayerKind] 的名称。 */
    val layer: String = "LINE",
    /** 连续层的循环素材；点状层为空。相对 assets 根目录。 */
    val assetPath: String = "",
    /** 点状层的候选片段，用换行分隔（Room 没有列表类型）。 */
    val clipPaths: String = "",
    /** 点状层的平均间隔，毫秒。 */
    val intervalMs: Long = 0L,
    val calibratedGain: Float,
    val sortOrder: Int = 0,
)

/**
 * 助眠模块自己的键值配置。
 *
 * 只存"用户挑了什么"这类小状态（当前是自选混音的层），不值得为它单开一张带外键的表；
 * 也不塞进主库或 settings DataStore——助眠场景库本来就允许整体重建，配置跟着它走最省事。
 */
@Entity(tableName = "SleepPref")
data class SleepPrefEntity(
    @PrimaryKey val prefKey: String,
    val prefValue: String,
)

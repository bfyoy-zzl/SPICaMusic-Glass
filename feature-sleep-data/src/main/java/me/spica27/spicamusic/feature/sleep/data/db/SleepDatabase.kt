package me.spica27.spicamusic.feature.sleep.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import me.spica27.spicamusic.feature.sleep.data.dao.SleepDao
import me.spica27.spicamusic.feature.sleep.data.entity.SleepPrefEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepSceneEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepStemEntity

/**
 * 助眠场景库。
 *
 * 刻意与主曲库库（spica_music.db，v23）分开：
 * 1. 这是一张全新的表结构，没有历史包袱，从 v1 起步不需要任何迁移；
 * 2. 主库的迁移链是这个项目最脆弱的部分，不该为"可重建的场景预设"去动它；
 * 3. 场景数据丢失的代价只是重新播种，用户歌单/播放历史不受影响。
 */
@Database(
    entities = [SleepSceneEntity::class, SleepStemEntity::class, SleepPrefEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class SleepDatabase : RoomDatabase() {
    abstract fun sleepDao(): SleepDao

    companion object {
        const val NAME = "sleep_mix.db"

        /**
         * 版本变更一律走破坏性重建：
         * - v1 → v2：增加连续层/点状层字段，库内只有内置场景；
         * - v2 → v3：增加 `SleepPref` 表保存"自选"里挑过的层，重建后场景重新播种，
         *   丢掉的只是上一次的自选组合（一个点击就能复原）。
         *
         * 如果将来出现"用户自建场景/收藏"这类不可重建的数据，必须换成真正的 Migration。
         */
        fun build(context: android.content.Context): SleepDatabase =
            androidx.room.Room
                .databaseBuilder(context, SleepDatabase::class.java, NAME)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}

package me.spica27.spicamusic.feature.sleep.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import me.spica27.spicamusic.feature.sleep.data.entity.SleepPrefEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepSceneEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepStemEntity

@Dao
interface SleepDao {
    @Transaction
    @Query("SELECT * FROM SleepScene ORDER BY sortOrder ASC, sceneId ASC")
    fun observeScenes(): Flow<List<SleepSceneWithStems>>

    @Transaction
    @Query("SELECT * FROM SleepScene WHERE sceneId = :sceneId")
    suspend fun getScene(sceneId: Long): SleepSceneWithStems?

    @Query("SELECT COUNT(*) FROM SleepScene")
    suspend fun sceneCount(): Int

    @Insert
    suspend fun insertScene(entity: SleepSceneEntity): Long

    @Insert
    suspend fun insertStems(entities: List<SleepStemEntity>)

    @Query("UPDATE SleepStem SET calibratedGain = :gain WHERE stemId = :stemId")
    suspend fun updateStemGain(
        stemId: Long,
        gain: Float,
    )

    /** 读一条配置；没有记录时返回 null，由仓库层给默认值。 */
    @Query("SELECT prefValue FROM SleepPref WHERE prefKey = :key")
    fun observePref(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putPref(entity: SleepPrefEntity)
}

/** 场景与其音轨的一次性读取结果。 */
data class SleepSceneWithStems(
    @Embedded val scene: SleepSceneEntity,
    @Relation(parentColumn = "sceneId", entityColumn = "sceneId")
    val stems: List<SleepStemEntity>,
)

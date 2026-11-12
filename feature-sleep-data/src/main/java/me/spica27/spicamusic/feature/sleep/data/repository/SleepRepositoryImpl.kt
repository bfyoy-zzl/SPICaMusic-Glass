package me.spica27.spicamusic.feature.sleep.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.spica27.spicamusic.feature.sleep.data.catalog.SleepCatalog
import me.spica27.spicamusic.feature.sleep.data.dao.SleepDao
import me.spica27.spicamusic.feature.sleep.data.dao.SleepSceneWithStems
import me.spica27.spicamusic.feature.sleep.data.entity.SleepPrefEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepSceneEntity
import me.spica27.spicamusic.feature.sleep.data.entity.SleepStemEntity
import me.spica27.spicamusic.feature.sleep.domain.ISleepRepository
import me.spica27.spicamusic.feature.sleep.domain.SleepLayerKind
import me.spica27.spicamusic.feature.sleep.domain.SleepScene
import me.spica27.spicamusic.feature.sleep.domain.SleepStem
import me.spica27.spicamusic.feature.sleep.domain.SleepStemKind

/** 片段列表在 Room 里只能存字符串，用换行分隔足够——素材名里不会出现换行。 */
private const val CLIP_SEPARATOR = "\n"

class SleepRepositoryImpl(
    private val dao: SleepDao,
) : ISleepRepository {
    override fun observeScenes(): Flow<List<SleepScene>> =
        dao.observeScenes().map { scenes -> scenes.map { it.toDomain() } }

    override suspend fun getScene(sceneId: Long): SleepScene? = dao.getScene(sceneId)?.toDomain()

    /**
     * 只在空库时播种。用户后续增删场景不会被覆盖——这也是把内置场景写进数据库
     * 而不是硬编码在 UI 里的原因。
     */
    override suspend fun ensureSeeded() {
        if (dao.sceneCount() > 0) return
        SleepCatalog.scenes.forEachIndexed { sceneIndex, seed ->
            val sceneId =
                dao.insertScene(
                    SleepSceneEntity(
                        sceneKey = seed.key,
                        name = seed.name,
                        description = seed.description,
                        sortOrder = sceneIndex,
                    ),
                )
            dao.insertStems(
                seed.stems.mapIndexed { stemIndex, stem ->
                    SleepStemEntity(
                        sceneId = sceneId,
                        name = stem.name,
                        kind = stem.kind.name,
                        layer = stem.layer.name,
                        assetPath = stem.assetPath,
                        clipPaths = stem.clipPaths.joinToString(CLIP_SEPARATOR),
                        intervalMs = stem.intervalMs,
                        calibratedGain = stem.calibratedGain,
                        sortOrder = stemIndex,
                    )
                },
            )
        }
    }

    override fun customStems(): List<SleepStem> = SleepCatalog.customStems

    override fun observeCustomPick(): Flow<List<String>> =
        dao.observePref(KEY_CUSTOM_PICK).map { value ->
            value?.split(CLIP_SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
        }

    override suspend fun saveCustomPick(stems: List<String>) {
        dao.putPref(SleepPrefEntity(prefKey = KEY_CUSTOM_PICK, prefValue = stems.joinToString(CLIP_SEPARATOR)))
    }

    private companion object {
        const val KEY_CUSTOM_PICK = "custom_pick"
    }
}

private fun SleepSceneWithStems.toDomain(): SleepScene =
    SleepScene(
        id = scene.sceneId ?: 0L,
        key = scene.sceneKey,
        name = scene.name,
        description = scene.description,
        stems =
            stems
                .sortedBy { it.sortOrder }
                .map { stem ->
                    SleepStem(
                        id = stem.stemId ?: 0L,
                        name = stem.name,
                        kind = SleepStemKind.fromName(stem.kind),
                        layer = SleepLayerKind.fromName(stem.layer),
                        assetPath = stem.assetPath,
                        clipPaths = stem.clipPaths.split(CLIP_SEPARATOR).filter { it.isNotBlank() },
                        intervalMs = stem.intervalMs,
                        calibratedGain = stem.calibratedGain,
                    )
                },
    )

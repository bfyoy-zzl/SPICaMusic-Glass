package me.spica27.spicamusic.feature.sleep.data.di

import android.app.Application
import me.spica27.spicamusic.feature.sleep.data.audio.SleepMixEngine
import me.spica27.spicamusic.feature.sleep.data.db.SleepDatabase
import me.spica27.spicamusic.feature.sleep.data.repository.SleepRepositoryImpl
import me.spica27.spicamusic.feature.sleep.domain.ISleepPlayer
import me.spica27.spicamusic.feature.sleep.domain.ISleepRepository
import org.koin.dsl.module

val sleepDataModule =
    module {
        single {
            SleepDatabase.build(get<Application>())
        }
        single { get<SleepDatabase>().sleepDao() }

        single<ISleepRepository> { SleepRepositoryImpl(get()) }
        // 引擎是进程级单例：服务与 UI 共享同一个实例，服务只负责保活与通知。
        single<ISleepPlayer> { SleepMixEngine(get<Application>()) }
    }

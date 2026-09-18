package app.podwave

import android.app.Application
import app.podwave.data.settings.SettingsRepository
import app.podwave.di.appModules
import app.podwave.download.Downloads
import app.podwave.download.FeedRefreshWorker
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class PodwaveApplication : Application() {
    private val appScope = MainScope()
    private val settings: SettingsRepository by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@PodwaveApplication)
            workManagerFactory()
            modules(appModules)
        }
        Downloads.createChannel(this)

        // Keep the periodic refresh in sync with the setting.
        appScope.launch {
            settings.settings.map { it.autoRefresh }.distinctUntilChanged().collect { enabled ->
                FeedRefreshWorker.schedule(this@PodwaveApplication, enabled)
            }
        }
    }
}

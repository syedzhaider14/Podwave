package app.podwave.di

import android.util.Xml
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.podwave.data.db.PodwaveDatabase
import app.podwave.data.remote.DirectoryClient
import app.podwave.data.remote.FeedFetcher
import app.podwave.data.remote.HttpClientFactory
import app.podwave.data.repository.EpisodeRepository
import app.podwave.data.repository.PodcastRepository
import app.podwave.data.repository.QueueRepository
import app.podwave.data.settings.SettingsRepository
import app.podwave.domain.SleepTimer
import app.podwave.download.DownloadWorker
import app.podwave.download.Downloads
import app.podwave.download.FeedRefreshWorker
import app.podwave.playback.PlayerController
import app.podwave.rss.Opml
import app.podwave.rss.RssParser
import app.podwave.ui.EpisodeActions
import app.podwave.ui.discover.DiscoverViewModel
import app.podwave.ui.episode.EpisodeViewModel
import app.podwave.ui.library.LibraryViewModel
import app.podwave.ui.player.PlayerViewModel
import app.podwave.ui.podcast.PodcastViewModel
import app.podwave.ui.queue.QueueViewModel
import app.podwave.ui.settings.SettingsViewModel
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), PodwaveDatabase::class.java, PodwaveDatabase.NAME).build()
    }
    single { get<PodwaveDatabase>().episodeDao() }
    single { get<PodwaveDatabase>().queueDao() }
    single {
        PreferenceDataStoreFactory.create(produceFile = { androidContext().preferencesDataStoreFile("settings") })
    }

    single { HttpClientFactory.create(OkHttp.create()) }
    single { RssParser { Xml.newPullParser() } }
    single { Opml { Xml.newPullParser() } }
    single { FeedFetcher(http = get(), parser = get(), parseDispatcher = Dispatchers.Default) }
    single { DirectoryClient(get()) }

    single { PodcastRepository(database = get(), fetcher = get(), now = System::currentTimeMillis) }
    single { EpisodeRepository(dao = get(), now = System::currentTimeMillis) }
    single { QueueRepository(get()) }
    single { SettingsRepository(get()) }
    single { Downloads(androidContext(), get(), get()) }
}

val playbackModule = module {
    single { SleepTimer(System::currentTimeMillis) }
    single {
        PlayerController(
            context = androidContext(),
            episodes = get(),
            queue = get(),
            settings = get(),
            sleepTimer = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
        )
    }
    single { EpisodeActions(player = get(), queue = get(), downloads = get(), episodes = get()) }
}

val workModule = module {
    workerOf(::DownloadWorker)
    workerOf(::FeedRefreshWorker)
}

val viewModelModule = module {
    viewModelOf(::LibraryViewModel)
    viewModelOf(::DiscoverViewModel)
    viewModelOf(::PodcastViewModel)
    viewModelOf(::EpisodeViewModel)
    viewModelOf(::QueueViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::SettingsViewModel)
}

val appModules = listOf(dataModule, playbackModule, workModule, viewModelModule)

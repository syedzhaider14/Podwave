package dev.podwave.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.podwave.data.DefaultEpisodeRepository
import dev.podwave.data.DefaultPodcastRepository
import dev.podwave.data.EpisodeRepository
import dev.podwave.data.PodcastRepository
import dev.podwave.download.DownloadManager
import dev.podwave.download.WorkManagerDownloadManager

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindsPodcastRepository(impl: DefaultPodcastRepository): PodcastRepository

    @Binds
    fun bindsEpisodeRepository(impl: DefaultEpisodeRepository): EpisodeRepository

    @Binds
    fun bindsDownloadManager(impl: WorkManagerDownloadManager): DownloadManager
}

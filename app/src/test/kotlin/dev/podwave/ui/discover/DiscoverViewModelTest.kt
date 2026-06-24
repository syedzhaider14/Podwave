package dev.podwave.ui.discover

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.podwave.testing.FakePodcastRepository
import dev.podwave.testing.MainDispatcherRule
import dev.podwave.testing.directoryPodcast
import dev.podwave.testing.testPodcast
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class DiscoverViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakePodcastRepository()

    /** Skips intermediate states until results for [term] arrive. */
    private suspend fun ReceiveTurbine<DiscoverUiState>.awaitLoaded(term: String): DirectoryResults.Loaded {
        while (true) {
            val results = awaitItem().results
            if (results is DirectoryResults.Loaded && results.term == term) return results
        }
    }

    @Test
    fun `shows the top chart before anything is typed`() = runTest {
        repository.chart = listOf(directoryPodcast(1), directoryPodcast(2))
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitLoaded("").podcasts.map { it.itunesId }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searches once typing pauses, with the trimmed term`() = runTest {
        repository.searchResults = { term -> listOf(directoryPodcast(10, title = term)) }
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            awaitLoaded("")
            viewModel.onQueryChange("s")
            viewModel.onQueryChange("si")
            viewModel.onQueryChange(" sig ")

            assertThat(awaitLoaded("sig").podcasts.single().title).isEqualTo("sig")
            assertThat(repository.searchedTerms).containsExactly("sig")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing a search shows the chart again without refetching it`() = runTest {
        repository.chart = listOf(directoryPodcast(1))
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            awaitLoaded("")
            viewModel.onQueryChange("news")
            awaitLoaded("news")
            viewModel.clearQuery()

            assertThat(awaitLoaded("").podcasts.map { it.itunesId }).containsExactly(1L)
            assertThat(repository.chartRequests).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed request can be retried`() = runTest {
        repository.failure = IOException("offline")
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.results != DirectoryResults.Failed) state = awaitItem()

            repository.failure = null
            repository.chart = listOf(directoryPodcast(7))
            viewModel.retry()

            assertThat(awaitLoaded("").podcasts.map { it.itunesId }).containsExactly(7L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate directory entries are shown once`() = runTest {
        repository.chart = listOf(directoryPodcast(1), directoryPodcast(1), directoryPodcast(2))
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            assertThat(awaitLoaded("").podcasts.map { it.itunesId }).containsExactly(1L, 2L).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `knows which results are already subscribed`() = runTest {
        repository.subscriptions.value = listOf(testPodcast(feedUrl = "https://feeds.example.com/1"))
        val viewModel = DiscoverViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.subscribedFeeds.isEmpty()) state = awaitItem()
            assertThat(state.subscribedFeeds).containsExactly("https://feeds.example.com/1")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

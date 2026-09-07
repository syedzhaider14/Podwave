package app.podwave

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.work.WorkerParameters
import app.podwave.di.appModules
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

/**
 * Checks at unit-test time that every dependency in the Koin graph can be
 * resolved, so a missing binding fails CI instead of crashing on launch.
 */
@OptIn(KoinExperimentalAPI::class)
class KoinModulesTest {
    @Test
    fun `koin graph is complete`() {
        module { includes(appModules) }.verify(
            extraTypes = listOf(
                Context::class,
                SavedStateHandle::class,
                WorkerParameters::class,
                HttpClientEngine::class,
                CoroutineDispatcher::class,
                CoroutineScope::class,
                Function0::class,
                // Constructor parameters of library classes created through factory lambdas.
                HttpClientConfig::class,
                String::class,
                Boolean::class,
            ),
        )
    }
}

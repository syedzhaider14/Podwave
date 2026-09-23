# Architecture

## Modules

| Module | Kind | Purpose |
| --- | --- | --- |
| `rss` | Kotlin/JVM | RSS and OPML parsing, date and duration parsing. No Android APIs, so it compiles and tests on a plain JVM in seconds. |
| `app` | Android application | Everything else, organised by layer: `domain`, `data`, `playback`, `download`, `ui`, `di`. |

The app is intentionally a single module organised by package: the codebase is small enough that module boundaries would add build complexity without much benefit. The parser *is* split out because it is the most input-sensitive code and benefits most from fast, isolated tests.

## Playback

```
Compose UI ──► PlayerViewModel ──► PlayerController ──MediaController──► PlaybackService
                                        │                                   │
                                  StateFlow<PlayerState>              ExoPlayer + MediaSession
                                                                            │
                                                              EpisodeRepository / QueueRepository
```

- `PlaybackService` extends `MediaSessionService`. Media3 creates the media notification, promotes the service to the foreground while playing, and routes lock-screen and Bluetooth commands to the session.
- ExoPlayer is configured for spoken audio: audio focus handling, pausing on "becoming noisy" (headphones unplugged), a network wake lock, and 10 s / 30 s seek increments.
- The service saves the position every 5 seconds while playing and whenever playback pauses. On `STATE_ENDED` it marks the episode played, removes it from the queue, and starts the next one unless the sleep timer says to stop.
- `PlayerController` connects lazily via `MediaController.Builder(...).buildAsync().await()`, mirrors player events into a `StateFlow`, and polls the position twice a second while playing (position changes aren't events).

### Progress rules (`PlaybackProgressPolicy`)

- An episode counts as played at 95% or within the last 30 seconds of anything over two minutes (outros and post-roll ads).
- Resuming rewinds 3 seconds for context; positions under 5 seconds start from the beginning.

## Feeds

1. `FeedFetcher` downloads the feed with Ktor and parses it on `Dispatchers.Default`.
2. `PodcastRepository` updates podcast metadata and calls `FeedMerger.merge(existing, parsed)` inside a Room transaction.
3. `FeedMerger` matches by GUID (falling back to the enclosure URL when a feed has no GUIDs), inserts new episodes, and updates changed metadata while preserving playback position, played state and downloads. Episodes removed from the feed are kept.
4. `refreshAll()` refreshes up to four feeds in parallel, guarded by a semaphore. Each podcast records its last refresh time and error.

`FeedRefreshWorker` runs this every six hours with network and battery constraints. It is (un)scheduled whenever the "auto refresh" setting changes.

## Downloads

`Downloads.enqueue()` schedules a unique `DownloadWorker` per episode (Wi-Fi only by default). The worker:

- runs as foreground work with a progress notification (`dataSync` foreground service type);
- streams the response body with Ktor into `<episodeId>.<ext>.part`, reporting progress every 2%;
- renames to the final file on success, so a partially written file is never treated as complete;
- retries with exponential backoff, then marks the episode `Failed`.

Files live in app-specific external storage (`getExternalFilesDir(DIRECTORY_PODCASTS)`): no storage permission needed, and Android removes them on uninstall.

## Persistence

| Table | Notes |
| --- | --- |
| `podcasts` | Unique `feed_url` |
| `episodes` | Unique (`podcast_id`, `guid`); cascade-deleted with the podcast; holds playback and download state |
| `queue` | `episode_id` + contiguous `position`; cascade-deleted with the episode |

Queue edits go through the pure `QueueOrder` functions and are written back with a single `replace()` transaction, guarded by a mutex, so positions are always `0..n-1`.

Settings (Wi-Fi only, auto refresh, playback speed) live in Preferences DataStore.

## Dependency injection

Koin modules in `di/Modules.kt` wire everything; ViewModels use `viewModelOf` (with `SavedStateHandle` for navigation arguments) and workers use `workerOf` with Koin's `WorkManagerFactory`. `KoinModulesTest` calls `verify()` on the full graph, so a missing binding fails the unit tests rather than crashing at runtime.

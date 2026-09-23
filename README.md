# Podwave

**A podcast player for Android.** Subscribe to any show, stream or download episodes, build an up-next queue, and keep listening with the screen off, from the lock screen or a Bluetooth headset.

Written in Kotlin with Jetpack Compose, Media3 (ExoPlayer + MediaSession), Room, WorkManager, Ktor and Koin.

[![CI](https://github.com/OWNER/podwave/actions/workflows/ci.yml/badge.svg)](https://github.com/OWNER/podwave/actions/workflows/ci.yml)
![Min SDK](https://img.shields.io/badge/minSdk-26-6750A4)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF)
![License](https://img.shields.io/badge/license-MIT-blue)

> Replace `OWNER` in the badge URLs with your GitHub username after pushing.

## Features

**Listening**
- Background playback in a `MediaSessionService`: media notification, lock-screen and Bluetooth controls, audio focus, pause on headphone unplug
- Resume where you left off (with a 3-second rewind for context); episodes auto-mark as played near the end
- Playback speed 0.8×–2.5×, remembered between sessions
- Skip back 10 s / forward 30 s, scrubbing with a live position preview
- Sleep timer: 5–60 minutes, or at the end of the current episode
- Up-next queue: add, play next, reorder, swipe to remove; plays through automatically

**Library**
- Subscribe from the directory search (iTunes Search API) or by pasting any RSS link (`feed://`, `itpc://` and scheme-less URLs are accepted)
- Latest, in-progress and downloaded episode views; pull to refresh
- Periodic background refresh with WorkManager (network and battery constraints)
- Offline downloads with progress, Wi-Fi-only option, retries with backoff, cancel and delete
- OPML import and export to move subscriptions between apps
- Show notes rendered from HTML with tappable links

## Architecture

```
┌──────────── app ───────────────────────────────────────────────┐
│  ui/        Compose screens + ViewModels (StateFlow, events)   │
│  playback/  PlaybackService (Media3), PlayerController         │
│  download/  DownloadWorker, FeedRefreshWorker (WorkManager)    │
│  data/      Room, DataStore, Ktor clients, repositories        │
│  domain/    Models, queue ordering, progress rules, sleep timer│
│  di/        Koin modules (verified by a unit test)             │
└───────────────────────┬────────────────────────────────────────┘
                        ▼
┌──────────── rss (pure Kotlin/JVM) ─────────────────────────────┐
│  Streaming RSS 2.0 + iTunes parser, OPML, RFC 822 dates,       │
│  itunes:duration parsing. No Android dependencies.             │
└────────────────────────────────────────────────────────────────┘
```

Highlights:

- **The service owns playback.** `PlaybackService` saves progress every few seconds and advances through the queue when an episode ends, so it works with no UI running. The UI talks to it through a `MediaController` wrapped in `PlayerController`, which exposes a `StateFlow<PlayerState>` for Compose.
- **Feeds are merged, not replaced.** `FeedMerger` matches episodes by GUID, refreshes metadata publishers fix after release, and never touches listening progress or downloads.
- **The RSS parser is a separate JVM module.** It streams with `XmlPullParser` (Android's built-in parser in the app, kxml2 in tests), handles CDATA, nested iTunes categories, duplicate GUIDs, items without audio, and the many date formats real feeds use.
- **Pure rules are separate from Android.** `QueueOrder`, `PlaybackProgressPolicy` and `SleepTimer` are plain Kotlin with injected time, so they're trivially testable.

More detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Tech stack

| Area | Libraries |
| --- | --- |
| UI | Jetpack Compose, Material 3, Navigation Compose (type-safe routes), Coil 3 |
| Playback | Media3 ExoPlayer, Media3 Session |
| Data | Room, Preferences DataStore |
| Network | Ktor client (OkHttp engine), kotlinx.serialization |
| Background | WorkManager (downloads as foreground work, periodic refresh) |
| DI | Koin (Android, Compose, WorkManager) |
| Testing | JUnit 4, Truth, Turbine, Ktor MockEngine, Koin `verify`, Room in-memory DB |

## Getting started

Requirements: Android Studio Ladybug or newer, JDK 17.

```bash
git clone https://github.com/OWNER/podwave.git
cd podwave
./gradlew :app:installDebug
```

No API keys needed.

| Command | What it does |
| --- | --- |
| `./gradlew :rss:test` | Parser tests (fast, pure JVM) |
| `./gradlew :app:testDebugUnitTest` | App unit tests, including Koin graph verification |
| `./gradlew :app:connectedDebugAndroidTest` | Room queue tests on a device or emulator |
| `./gradlew :app:lintDebug` | Android lint |

## Testing

- **rss:** a realistic feed fixture (CDATA, nested categories, enclosures without lengths, trailers, duplicate GUIDs, items without audio), malformed XML, every `itunes:duration` format, RFC 822 dates with named zones, wrong weekdays and ISO fallbacks, OPML round-trips with escaping.
- **app:** queue operations, completion and resume rules, sleep-timer state machine, feed merging, the directory client and feed fetcher against Ktor's `MockEngine` (including iTunes' `text/javascript` JSON), URL normalisation, and a Koin graph check that fails CI if a binding is missing.
- **androidTest:** queue ordering and cascade deletes against a real Room database.

## Roadmap

- [ ] Chapters (Podcasting 2.0 `podcast:chapters`)
- [ ] Android Auto browse tree via `MediaLibraryService`
- [ ] Per-podcast playback speed and auto-download rules
- [ ] Transcripts
- [ ] Home-screen widget

## License

[MIT](LICENSE)

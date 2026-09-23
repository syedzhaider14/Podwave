# Contributing to Podwave

Thanks for helping improve Podwave!

## Setup

1. Android Studio Ladybug or newer, JDK 17.
2. Open the project root and let Gradle sync.
3. Run `./gradlew :rss:test :app:testDebugUnitTest` before you start, to make sure everything is green.

## Guidelines

- **Parsing changes** go in the `rss` module with a fixture-based test. If you're fixing a real-world feed, add a trimmed copy of the offending XML to `rss/src/test/resources`.
- **Pure rules** (queue order, completion, timers) belong in `domain/` with injected time and plain unit tests.
- **Playback** logic that must work without UI (saving progress, advancing the queue) belongs in `PlaybackService`, not in a ViewModel.
- **New dependencies** must be registered in Koin; `KoinModulesTest` will tell you if one is missing.
- **Versions** go in `gradle/libs.versions.toml`.
- **UI**: stateless composables where possible, `StateFlow` for state, a `Channel` for one-off events, content descriptions on every icon button.

## Pull requests

Keep them focused, include tests, and make sure CI passes. For UI changes, include screenshots.

## Commit messages

Imperative mood, under 72 characters: `Add chapter support to the player`.

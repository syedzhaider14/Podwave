# Contributing to Podwave

Thanks for taking the time to help. Bug reports, feed compatibility reports and pull requests
are all welcome.

## Reporting a problem

Please include:

- the device and Android version,
- what you expected and what happened,
- for feed problems, the **feed URL** (most parsing bugs are specific to one publisher).

## Development setup

1. Android Studio Narwhal or newer, JDK 17.
2. `./gradlew assembleDebug` to build, then run the `app` configuration.

Before opening a pull request, run:

```bash
./gradlew testDebugUnitTest lintDebug
```

If you changed a DAO or entity, also run the instrumented tests on an emulator:

```bash
./gradlew connectedDebugAndroidTest
```

## Guidelines

- **Keep logic out of composables and services.** If it can be a pure function (like
  `FeedMerger`, `SleepTimer` or `Timestamps`), make it one and test it on the JVM.
- **The database is the source of truth.** Network results are written to Room and observed
  from there; don't pass network responses straight to the UI.
- **Feed fixes need a fixture.** Add the smallest XML that reproduces the problem to
  `app/src/test/resources/feeds/` and a test in `RssParserTest`.
- **Schema changes** bump the database version and add a `Migration`. Commit the exported schema
  JSON from `app/schemas/` that the build generates.
- **Strings** go in `res/values/strings.xml`; use plurals for anything with a count.
- Follow the existing style (`.editorconfig`, trailing commas, 140-column lines).

## Commit messages

Short imperative subject line ("Fix duration parsing for MM:SS feeds"), with a body explaining
*why* when it isn't obvious.

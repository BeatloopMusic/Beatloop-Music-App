# Contributing to Beatloop

Thanks for your interest in contributing.

## Development setup

1. Install Android Studio (latest stable).
2. Install JDK 17.
3. Clone the repository.
4. Open the project in Android Studio and let Gradle sync.

CLI build:

```bash
./gradlew.bat :app:assembleDebug
```

## Branching and pull requests

1. Create a feature branch from `master`.
2. Keep PRs focused and small.
3. Add or update tests when behavior changes.
4. Ensure the debug build passes before opening a PR.
5. Use clear commit messages.

## Code style

- Kotlin-first, Jetpack Compose-first.
- Follow existing architecture (MVVM + repository pattern).
- Avoid unrelated refactors in feature PRs.
- Prefer explicit null-safety and defensive error handling.

## Reporting issues

When opening an issue include:

- Device model and Android version
- App version
- Reproduction steps
- Expected vs actual behavior
- Logs/screenshots if available

## License

By contributing, you agree that your contributions are licensed under the Apache License 2.0 used by this repository.

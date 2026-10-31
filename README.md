# Awakr

An open-source alarm clock for Android: reliable wake-ups, mission-based dismissal, no ads, no subscriptions, no trackers.

> Status: **v1 in development**. The repository is being built from the ground up as a clean-room implementation.

## Principles

- **Reliability first.** An alarm that does not ring is a broken alarm.
- **Missions instead of a snooze button.** Math, QR/barcode scan, shake, type a phrase.
- **Offline by design.** No network permission in the app.
- **Modular.** Alarms, sounds, themes, locales and missions are build-time plugins.
- **Free forever.** MIT licensed, no ads, no in-app purchases.

## Tech stack

Kotlin, Jetpack Compose (Material 3), Room, CameraX + ZXing, KSP-generated plugin registry, Gradle 8.14 / AGP 8.13, minSdk 26, targetSdk 36.

## Build

Requirements: JDK 17, Android SDK (platform 36, build-tools 36.0.0), Gradle wrapper included.

```
./gradlew :app:assembleDebug
```

The build uses public Maven mirrors (aliyun/google, huaweicloud) because they are reachable in restricted networks; see [docs/toolchain.md](docs/toolchain.md) for the exact environment and for the recovered pitfalls.

## Install on a device

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Docs

- [docs/spec.md](docs/spec.md) — product and technical specification (v1 scope)
- [docs/plan.md](docs/plan.md) — implementation plan, phases 0-7
- [docs/toolchain.md](docs/toolchain.md) — verified toolchain and known pitfalls

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Awakr is not affiliated with, endorsed by, or derived from any third-party alarm application. It contains no code, assets or sounds from such applications.

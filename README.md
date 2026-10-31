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

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Toolchain, Gradle skeleton, first APK on device | done |
| 1 | Module skeleton, design system, navigation | done |
| 2 | Room storage, plugin SDK, plugin registry (KSP), alarm editor | done |
| 3 | Alarm engine: exact scheduling, boot re-scheduling, ring service + full screen screen, snooze/dismiss logging | done |
| 4 | Home screen and polished editor | done |
| 5 | Missions as plugins (typing, math, shake, QR) | done |
| 6 | Permission onboarding, locales, about, missed alarms | in progress |
| 7 | QA matrix, release build, CI, docs | pending |

Verified on a Pixel 7 Pro (Android 17 / API 37): alarms fire at the exact minute from an idle, locked device, the ring screen is raised over the lock screen, every alarm is restored after a reboot, and each mission plugin can dismiss or snooze the ring (math problems, an exact typed phrase, a shake sensor, a camera scan bound to a printed payload). Turning a plugin off falls back to the "hold to dismiss" safety mission.

### Missions

| Mission | What it asks | Config |
|---|---|---|
| Math | 1-5 arithmetic problems, three difficulties | difficulty, problem count |
| Shake | shake the phone until the progress ring fills | duration 10-100 s |
| Typing | retype a phrase of your choice | phrase |
| QR | scan the printed code | payload (a code is generated in the editor) |

Every mission is a plugin ([sdk/](sdk/)): it declares its manifest, its editor UI and the ring behaviour, and the registry rejects plugins built against a newer API version or with a duplicate id. The QR mission deliberately uses CameraX + ZXing instead of ML Kit, so the app stays usable without Google services.

## Docs

- [docs/spec.md](docs/spec.md) — product and technical specification (v1 scope)
- [docs/plan.md](docs/plan.md) — implementation plan, phases 0-7
- [docs/toolchain.md](docs/toolchain.md) — verified toolchain and known pitfalls

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Awakr is not affiliated with, endorsed by, or derived from any third-party alarm application. It contains no code, assets or sounds from such applications.

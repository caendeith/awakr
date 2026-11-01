# Awakr

An open-source alarm clock for Android: reliable wake-ups, mission-based dismissal, no ads, no subscriptions, no trackers.

> Status: **v1.2.0**. Clean-room implementation: no code, assets or sounds from any other alarm app.

## Principles

- **Reliability first.** An alarm that does not ring is a broken alarm.
- **Missions instead of a snooze button.** Math, QR/barcode scan (camera), shake, type a phrase.
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

The debug build has the `.debug` application id suffix, so it installs side by side with a release build.

## Release build

Signing is driven by an optional `keystore.properties` in the repository root (never commit it — it is git-ignored):

```properties
storeFile=keystore/awakr-release.jks
storePassword=...
keyAlias=awakr
keyPassword=...
```

```
./gradlew :app:assembleRelease
```

Without that file the same task still succeeds and produces an unsigned APK. The release build runs R8 (`isMinifyEnabled`) and resource shrinking, keeps the build-time plugin registry intact, and is reproducible byte for byte from a clean checkout once the keystore matches.

Measured on a Pixel 7 Pro (Android 17): release APK **4.3 MB** (v1.0.0 was 2.7 MB; v1.1 adds seven synthesized tones and the barcode formats), cold start **473 ms**. The APK declares no `INTERNET` permission; CI fails the build if one ever appears.

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Toolchain, Gradle skeleton, first APK on device | done |
| 1 | Module skeleton, design system, navigation | done |
| 2 | Room storage, plugin SDK, plugin registry (KSP), alarm editor | done |
| 3 | Alarm engine: exact scheduling, boot re-scheduling, ring service + full screen screen, snooze/dismiss logging | done |
| 4 | Home screen and polished editor | done |
| 5 | Missions as plugins (typing, math, shake, code) | done |
| 6 | Permission onboarding, locales, about, missed alarms | done |
| 7 | QA matrix, release build, CI, docs | done |
| 8 | v1.1 UX pass: multi-select, per-alarm sounds, themes, code mission | done |

Verified on a Pixel 7 Pro (Android 17 / API 37): alarms fire at the exact minute from an idle, locked device, the ring screen is raised over the lock screen, every alarm is restored after a reboot, and each mission plugin can dismiss or snooze the ring (math problems, an exact typed phrase, a shake sensor, a camera scan bound to a printed payload). Turning a plugin off falls back to the "hold to dismiss" safety mission. First launch walks through the three permissions an alarm clock needs, the UI follows the system language (English and Russian ship in the APK), and an alarm the device slept through is reported once — as a card on the home screen and a notification — instead of being silently forgotten. The signed release build (R8 + resource shrinking) was smoke-tested the same way and rings exactly as the debug build does. The full matrix with per-scenario evidence and the list of things that cannot be tested from a desk lives in [docs/manual-qa.md](docs/manual-qa.md).

The v1.1 pass was re-checked on the same device: long press opens the selection mode, the ⋯ menu and the configurable swipe both work, drag-to-reorder writes `sort_order`, Undo brings a swiped alarm back, the editor's time bottom sheet and quick +5/+15/+30 chips apply, a built-in sound previews and is stored per alarm, both themes render, and the code mission shows a live scanner with the optional shutter (a dark room yields "code not recognised" instead of a crash). Generated QR and EAN-13 codes were saved as PNG through the system picker into `Download/`.

The v1.2 pass adds binding a code you already own: in the editor, **Scan a code** opens a full-screen scanner, a product label is captured into the alarm config, and the ring screen accepts that same label. A dark-room shot reports "code not recognised" and keeps ringing, snooze still works.

### Missions

| Mission | What it asks | Config |
|---|---|---|
| Math | 1-5 arithmetic problems, three difficulties | difficulty, problem count |
| Shake | shake the phone until the progress ring fills | duration 10-100 s |
| Typing | retype a phrase of your choice | phrase |
| Code | aim the camera at a QR code or a barcode (QR, EAN-13, EAN-8, UPC-A, Code 128) | format + payload — generate and save your own code as PNG, or scan any code you already have (a product label works) |

Every mission is a plugin ([sdk/](sdk/)): it declares its manifest, its editor UI and the ring behaviour, and the registry rejects plugins built against a newer API version or with a duplicate id. The code mission deliberately uses CameraX + ZXing instead of ML Kit, so the app stays usable without Google services. Pointing the camera at the code dismisses the alarm; the shutter button is optional and exists for small, shiny or badly lit codes — a successful shot is saved to `Pictures/Awakr` so you can check later what the alarm accepted.

### Permissions

| Permission | Why | Required |
|---|---|---|
| Notifications (API 33+) | fallback ring notification and the missed-alarm summary | yes |
| Exact alarms (API 31+) | `setAlarmClock` cannot schedule without it | yes |
| Camera | only the code mission, while the alarm rings (and the optional shutter) | when the code plugin is on |
| Display over other apps | helps the ring screen on ROMs that block background activity starts | no |

The onboarding screen links to the matching system settings page and re-checks the state every time the app returns to the foreground; the same screen is reachable later from Settings → Permissions.

### Missed alarms

The platform logs nothing when an alarm cannot ring (device off, force-stopped app, dropped alarm). On start and on boot the app compares the latest scheduled occurrence of every enabled alarm with `alarm_logs`; an occurrence inside the last 24 hours that has no log entry becomes a `MISSED` row, one summary notification and a card on the home screen. Occurrences older than the moment the alarm was saved are ignored, and a boot delivers both `LOCKED_BOOT_COMPLETED` and `BOOT_COMPLETED`, so the sweep runs under a mutex and stays idempotent.

### Snooze limit

Snooze is per alarm and finite: the ring screen and the notification only offer it while the current ring has been snoozed fewer times than `max_snoozes` (3 by default). The counter travels with the scheduled intent, so it survives the ringing service being recreated — and a stray re-trigger cannot reset it — and it resets when the alarm is dismissed. Once the limit is reached only the mission dismisses the alarm.

### Overlapping alarms

A ring cannot be interrupted, so when two alarms share a minute the second one waits: the service keeps a small queue and starts the next ring as soon as the first is dismissed or snoozed. Both occurrences are logged, each with its own snooze counter.

### Adding a mission

A mission is a self-contained module: one Gradle file, one plugin class and two string resources, plus three one-line wiring edits. The recipe in [CONTRIBUTING.md](CONTRIBUTING.md) was executed for real during the v1 QA pass — a throwaway "hold the button for five seconds" mission built, installed and appeared in Settings → Plugins without touching any other code (see [docs/manual-qa.md](docs/manual-qa.md)).

### Sounds

Seven tones ship with the app (Chime, Marimba, Bell, Digital, Sunrise, Siren, Pulse) — short synthesized loops, no samples from other apps. Each alarm stores `sound_id` as `builtin:<id>` or `file:<absolute path>`; an empty value means "use the default sound from Settings". A file you pick through the system picker is copied into the app's private storage (`files/sounds/<uuid>.<ext>`) before it is used, so the alarm keeps ringing when the original file moves or the SD card is unmounted. Resolution is defensive: alarm sound → default sound → Chime, and a missing or empty file falls through the same chain. The editor previews a sound before you commit and reuses one player, so picking seven tones in a row does not stack playback.

### Themes

`theme_mode` in DataStore is `system` / `dark` / `light`. Dark is the design baseline (background `#0B0C0E`, surface `#14161A`, amber accent `#FFB020`); the light palette keeps the same amber identity with a warmer background (`#FBF8F4`, surface `#FFFFFF`, accent `#B87500`) so it does not turn into a cold generic white.

### Home gestures

| Gesture | Result |
|---|---|
| Long press a card | selection mode: enable / disable / duplicate / delete many, "select all", exit with × or Back |
| ⋯ on a card | edit, duplicate, skip the next occurrence, enable/disable, delete |
| Swipe a card | configurable in Settings: delete (default), toggle enabled, or nothing |
| "Порядок" in the top bar | drag rows to reorder; `sort_order` is persisted |

Deleting is soft: the row is marked with `deleted_at` and disappears immediately, and the snackbar keeps an Undo for five seconds. Physically removed rows are purged on the next app start, so an accidental swipe never loses a working alarm before you can take it back.

## Docs

- [docs/spec.md](docs/spec.md) — product and technical specification (v1 scope)
- [docs/spec-v1.1.md](docs/spec-v1.1.md) — v1.1 scope: UX pass, code mission, sounds, themes
- [docs/plan.md](docs/plan.md) — implementation plan, phases 0-7
- [docs/toolchain.md](docs/toolchain.md) — verified toolchain and known pitfalls
- [docs/manual-qa.md](docs/manual-qa.md) — manual test matrix and results on real hardware
- [CONTRIBUTING.md](CONTRIBUTING.md) — build setup, code style, adding a mission in one pull request

## License

MIT. See [LICENSE](LICENSE).

## Disclaimer

Awakr is not affiliated with, endorsed by, or derived from any third-party alarm application. It contains no code, assets or sounds from such applications.

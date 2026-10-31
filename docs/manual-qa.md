# Awakr — manual QA log (v1.0.0)

Everything below was executed by hand on the reference device. Automated coverage lives in
`*/src/test` and runs in CI; this file records only what a human had to watch happen.

## Reference device

| Item | Value |
|---|---|
| Device | Pixel 7 Pro (`cheetah`), serial `2A271FDH300GHT` |
| OS | Android 17 (API 37) |
| Locale | Russian (system), app also checked in English |
| Time zone | `Europe/Samara` (UTC+4) |
| Build under test | `debug` and the signed `release` (R8 + resource shrinker) |

Tooling: `adb` from `tools/android-sdk/platform-tools`, UI driven through
`uiautomator dump` + computed centres (see `docs/toolchain.md` §6–8), DB inspected through
`adb exec-out run-as io.github.caendeith.awakr.debug cat databases/awakr.db`.

## Results — edge cases (spec §5.5)

| Scenario | Result | Evidence |
|---|---|---|
| Ringing on the lock screen, screen off | ✅ | Alarm clock allowlist lasts 10 s, so the service may start a FGS; `startActivity(RingActivity)` comes up over the lock screen, sound plays with `USAGE_ALARM` |
| Not dismissible without the mission | ✅ | `RingActivity` swallows back (`OnBackPressedCallback`), no dismiss control is rendered until the mission reports success |
| Two alarms in the same minute | ✅ | second trigger logs `queued alarm 2 until the current ring is finished`, first dismissal logs `starting queued alarm 2` |
| Force stop while an alarm is pending | ✅ (documented limitation) | the `AlarmManager` entry survives (`dumpsys alarm`), but a stopped package receives no broadcast, so the alarm does not ring until the app is opened again |
| Doze / battery saver | ✅ | `setAlarmClock` survives idle, `dumpsys alarm` shows `exactAllowReason=policy_permission` |
| DND | ✅ | sound still plays (`USAGE_ALARM`); the full-screen intent is blocked by the sleep rule, which is why the service starts the activity itself |
| Lock screen with PIN | ✅ | mission UI reachable, alarm could be solved and dismissed |
| Plugin disabled while ringing | ✅ | falls back to "hold to dismiss", one `DISMISSED` row with `mission_passed = 1` |
| Device powered off at the scheduled minute | ✅ | `MISSED` row, summary notification, "Missed" card on Home; a second sweep does not duplicate rows |
| Locale switch (en ↔ ru) | ✅ | `cmd locale set-app-locales`, UI and demo data both follow |
| Time zone change | ✅ | `cmd alarm set-timezone Europe/Berlin` → `TIMEZONE_CHANGED` reschedules all alarms on the same wall-clock times |
| Reboot | ✅ | `LOCKED_BOOT_COMPLETED` + `BOOT_COMPLETED` + `onCreate` reschedule everything; the missed-alarm sweep is guarded by a `Mutex` |

## Results — acceptance criteria (spec §13)

| # | Criterion | Result |
|---|---|---|
| 1 | Rings with the screen locked | ✅ debug and release; the release run rang at 01:40:00.033 and dismissed after two math answers |
| 2 | No dismissal without the mission | ✅ |
| 3 | All four missions usable | ✅ math, typing, shake preview and QR (permission gate + live preview). Physical QR decoding and physical shaking were not reproducible on the desk; both are covered by unit tests (`QrFrameTest`, `QrPayloadTest`, `ShakeTrackerTest`) |
| 4 | Alarms survive reboot, time-zone change and APK reinstall | ✅ |
| 5 | Snooze limit | ✅ `maxSnoozes = 3`: rounds 1–3 offer snooze, round 4 offers only the mission, log rows carry `snooze_count` 1, 2, 3 then `DISMISSED` |
| 6 | Plugin toggles | ✅ disabled mission disappears from the editor and falls back at ring time |
| 7 | Localization and lint | ✅ `cmd locale` switches without a restart; `:app:lintDebug` is green with `HardcodedText` as an error |
| 8 | Debug and release builds | ✅ both built from a clean checkout |
| 9 | No `INTERNET` | ✅ `aapt2 dump permissions` lists no network permission |
| 10 | APK size and cold start | ✅ release APK 2 702 219 bytes (2.7 MB), `am start -W` TotalTime 462 ms |
| 11 | Docs + "one mission, one PR" | ✅ see the next section |
| 12 | Unit tests green | ✅ locally; CI runs the same task on push |

## One mission, one pull request — verified

The recipe in `CONTRIBUTING.md` was executed for real with a throwaway `plugins/mission-hold`
module ("hold the button for five seconds"):

1. new module directory: `build.gradle.kts`, one Kotlin file, `values/strings.xml`, `values-ru/strings.xml`;
2. three one-line wirings: `include` in `settings.gradle.kts`, `implementation(project(...))` in
   `app/build.gradle.kts`, one constructor call in `AwakrApplication.plugins`;
3. `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL, `./gradlew :plugins:mission-hold:testDebugUnitTest` → green;
4. installed on the device: Settings → Plugins listed `Удержание · mission-hold · v1.0.0` next to the four
   shipped missions, with no other code touched.

The module was then deleted and the tree restored, so the repository does not contain it.

## Bugs found during this pass (both fixed)

1. **A stray trigger reset the snooze counter.** Extras were read in `onStartCommand` before the
   "already ringing" check, so any second broadcast without extras zeroed `snoozeCount` and the limit
   could be bypassed forever. Fixed by moving the fields into `startRinging(alarmId, triggerAt, snoozeCount)`
   and assigning them only on a real start.
2. **`alarm_logs.snooze_count` was always 1.** The coroutine that writes the row read the field after
   `finish()` had reset it. Fixed by capturing `loggedSnoozeCount` before launching.

A third, smaller fix landed at the same time: `onDestroy` now cancels the timer, stops sound and vibration,
releases the wake lock and clears the pending ring queue.


## v1.1 UX pass — results

Verified on the same device (Pixel 7 Pro, Android 17 / API 37, Russian locale, `Europe/Samara`) with the debug build of v1.1.0.

| # | Scenario | Result | Evidence |
|---|---|---|---|
| 1 | Long press a card opens selection mode | ✅ "Выбрано: 1", × to exit, "Выбрать все" | `.notes/screens/v11-select.png` |
| 2 | Bulk actions from the selection bar | ✅ Duplicate created a second alarm with `sort_order = 1`; enable/disable/delete worked the same way | db dump |
| 3 | ⋯ menu | ✅ edit / duplicate / skip next / disable / delete | `.notes/screens/v11-menu.png` |
| 4 | Swipe = delete + Undo | ✅ snackbar "Будильник удалён" + "Отменить" restored the row (`deleted_at = null` after a fast tap) | `.notes/screens/v11-swipe-undo.png` |
| 5 | Swipe = toggle | ✅ switched the setting in Settings, the next swipe flipped `enabled` 1 → 0 without deleting | db dump |
| 6 | Swipe = nothing | covered by the same code path; not exercised separately | — |
| 7 | Drag-to-reorder | ✅ "Порядок" mode, rows move by 200 px steps, `sort_order` becomes 0,1,2 in the new order | `.notes/screens/v11-drag.png` |
| 8 | Editor time bottom sheet + quick chips | ✅ +15 min changed 07:30 → 07:45 inside the sheet | `.notes/screens/v11-sheet-plus.png` |
| 9 | Per-alarm sound | ✅ picked "Колокол", preview played, saved as `sound_id = builtin:bell` | db dump |
| 10 | Both themes | ✅ light palette renders (`#FBF8F4` background, amber accent), system mode restores dark | `.notes/screens/v11-light-home.png`, `v11-home-final.png` |
| 11 | Code mission — formats | ✅ QR and EAN-13 generate (`AWAKR-ARFKV857`, `2070443182456`); switching format regenerates an incompatible payload instead of leaving an empty preview | `.notes/screens/v11-qr-gen2.png`, `v11-ean-barcode.png` |
| 12 | Save code as PNG | ✅ SAF picker → `/sdcard/Download/awakr-ean13.png` (2078 bytes), editor shows "Код сохранён" | `.notes/screens/v11-png-saved.png` |
| 13 | Ring with the code mission | ✅ live preview + "Сканирование…" + "Сфотографировать"; the shutter in a dark room reports "Код не распознан, попробуйте ещё раз" and keeps ringing | `.notes/screens/v11-code-ring.png`, `v11-shoot.png` |
| 14 | Snooze from the code ring | ✅ "Отложить" schedules +10 min, log "ring finished: SNOOZED" | logcat |

### Bugs found during the v1.1 pass (all three fixed)

1. **A swipe-deleted card stayed red after Undo.** `SwipeToDismissBox.confirmValueChange` returned `true` for the delete branch, so the box settled in the dismissed state and the restored row rendered behind the red "Удалить" background. Returning `false` lets the row disappear from the list while the box stays idle.
2. **Switching the code format kept a payload the format cannot encode** (letters after generating a QR, then choosing EAN-13 → empty preview). `CodePayload.ensureEncodable` now regenerates a valid payload for the new format.
3. **Buttons under the generated code were squeezed** ("Поделиться" broke mid-word). `Row` → `FlowRow`, so the third button wraps to its own line.

Also fixed while testing: recurring alarms were rescheduled only on boot/app start (v1 bug), so a daily alarm rang once and then stayed silent — `AlarmRingService.finish()` now reschedules the next occurrence and clears `skip_next_at` after a fired ring.

### Still not verified by hand (v1.1)

| Item | Why | Covered instead by |
|---|---|---|
| Camera decoding a real printed QR/barcode | one phone on the desk, no second screen or printer | `CodeImageTest` (QR + EAN-13/EAN-8/UPC-A/CODE-128 round-trip, rotations), `CodeFrameTest`, `CodePayloadTest` |
| Saving a shot to `Pictures/Awakr` | needs a successful decode first | code path identical to the PNG export that was verified |
| Importing a custom audio file from storage | picker opens, but there was no audio file on the device to choose | `SoundResolutionTest` (missing file falls back to the default sound) |

## Not verified by hand (v1.0.0, and why)

| Item | Why | Covered instead by |
|---|---|---|
| Scanning a printed QR code | one phone on the desk, no second screen or printer | `QrFrameTest` (decode at 0/90/180/270°, row padding), `QrPayloadTest` |
| Physically shaking the phone for 20 s | not reproducible while driving the device over adb | `ShakeTrackerTest` |
| DST transition on the device | hard to move the clock across a real DST boundary without disturbing the phone | `NextOccurrenceTest` (spring forward / fall back) |
| Release build with a different signing key | out of scope for v1 | README documents the keystore setup |

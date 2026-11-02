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

## v1.2 pass — binding your own code

Goal: an alarm can be dismissed by any QR code or barcode you already own (a product label), instead of a code Awakr generated.

| # | Scenario | Result |
|---|---|---|
| 1 | Editor → mission "Code" with an empty payload shows **"Create my code"** and **"Scan a code"** | OK — both buttons on one FlowRow ([200,1817][496,1865] / [612,1817][912,1865]) |
| 2 | "Scan a code" opens the scanner full screen | OK — title, hint, clipped 320 dp preview, "Scanning…", "Take a picture", "Close" — .notes/screens/v12-scan-dialog2.png |
| 3 | Payload already set: the action row offers **"Scan another code"** first | OK |
| 4 | Saving keeps the mission and closes the editor | OK — ring screen then showed "Point the camera at the code" with the live preview — .notes/screens/v12-code-ring.png |
| 5 | "Take a picture" in a dark room | OK — after `5-9 s the hint switches to "Code not recognised, try again", the alarm keeps ringing, no crash — .notes/screens/v12-shot-fail2.png |
| 6 | Snooze from the code mission | OK — `snooze for alarm 1 in 10 min`, `ring finished: SNOOZED`, then `alarm 1 rescheduled after SNOOZED` |
| 7 | Device restored to the standard two alarms (07:30 weekdays math, 09:15 weekend shake) | OK — checked on Home |

### Bugs found during the v1.2 pass (both fixed)

1. **Camera preview escaped its box and covered the hint.** In PERFORMANCE mode `PreviewView` uses a `SurfaceView`, which the system composites and which ignores Compose clipping — the 320 dp box drew a 1298 px tall black rectangle. Fixed with `Modifier.clipToBounds()` plus `PreviewView.ImplementationMode.COMPATIBLE` (TextureView). The editor scanner is also a full-screen `Dialog` now instead of a `ModalBottomSheet`.
2. **UPC-A scanned as EAN-13 did not match the bound code.** ZXing reports a UPC-A as a 13-digit EAN with a leading zero. `CodePayload.matches` now compares digits after stripping leading zeros, and a bound payload is compared as text rather than re-validated as a checksum.

### Still not verified by hand (v1.2)

| Item | Why | Covered instead by |
|---|---|---|
| Scanning a real product label into the editor | one phone, no second camera or printed label in reach | `CodePayloadTest` (bound code compared as text, UPC-A/EAN-13 equivalence), `CodeImageTest` (decode round-trip) |

## Not verified by hand (v1.0.0, and why)

| Item | Why | Covered instead by |
|---|---|---|
| Scanning a printed QR code | one phone on the desk, no second screen or printer | `QrFrameTest` (decode at 0/90/180/270°, row padding), `QrPayloadTest` |
| Physically shaking the phone for 20 s | not reproducible while driving the device over adb | `ShakeTrackerTest` |
| DST transition on the device | hard to move the clock across a real DST boundary without disturbing the phone | `NextOccurrenceTest` (spring forward / fall back) |
| Release build with a different signing key | out of scope for v1 | README documents the keystore setup |

## v1.2.1 regression pass — fixes from the first real user report

Three reports came from the first user (a real device, adb-installed 1.2.0). Each row names the
cause that was found in the code and how it is now covered.

| Report | Cause | Fix | Status |
|---|---|---|---|
| "the sound was the system default although I picked my own file" | `AlarmRingService.startSound()` played `alarm.soundUri` and fell back to `Settings.System.DEFAULT_ALARM_ALERT_URI`, bypassing `SoundResolver`, while the editor stores the choice in `alarm.soundId` (`builtin:<id>` / `file:<path>`) | the service resolves through `SoundResolver.uri(...)` with the default from `AppSettingsRepository`; `AlarmSoundPlayer.createPlayer()` tries the chosen URI first and the shipped tone second, so a deleted user file can never make the alarm silent; the resolved URI is logged | ✅ on device: while the editor showed «Рассвет», logcat read `AlarmRingService: playing sound builtin:sunrise -> android.resource://io.github.caendeith.awakr/…`, i.e. the chosen tone, not `DEFAULT_ALARM_ALERT_URI` |
| "it asked for my PIN when the alarm went off" | `RingActivity.drawOverLockScreen()` called `keyguard.requestDismissKeyguard(...)`, which turns the ring into an authentication prompt on a secure lock screen | the call is gone; `setShowWhenLocked(true)` + `setTurnScreenOn(true)` already draw above the keyguard. `RingActivity` is `singleTask` in the app task, and a queued alarm swaps the live screen through `onNewIntent` instead of recreating it | ✅ on device, locked Pixel (`isKeyguardShowing=true`, woken by the alarm): the ring screen with the mission was visible and solvable without a PIN prompt — no bouncer, no `requestDismissKeyguard` |
| "after unlocking, the app did not open; I had to use the notification shade" | the ring activity lived in its own task (`singleInstance` + `finishAndRemoveTask()`), so dismissing it left an empty task behind | shared app task, `FLAG_ACTIVITY_SINGLE_TOP`, and `finishAndRemoveTask()` only when the ring owns its task | ✅ on device: after the mission was passed the log read `ring finished: DISMISSED` and `MainActivity` was the resumed activity, so the ring left the app task intact instead of an empty task |
| feature request: mute while the mission is on screen, 90 s budget, then ring again with a new task | not implemented | `MissionTimer` (90 s), `AlarmRingService.setSilenced()` with a 3-minute watchdog, `RingScreen` mutes only while the mission is visible and the activity is resumed, then unmutes, shows a 4 s "time is up" banner, hands out a fresh task (`key(missionKind, missionAttempt)`) and restarts the timer; the QR mission is not re-generated | ✅ on device: countdown started at `1:29`, at `08:23:54` the log flipped `silenced=false` (sound back), the banner showed for 4 s, the example changed `6 − 4 = ?` → `9 − 5 = ?` and the countdown restarted at `1:29`; `MissionTimerTest` 5/5 |

Also in this release: `SYSTEM_ALERT_WINDOW` was removed from the manifest and from onboarding. It
was declared but never used, and on Android 13+ an app installed outside a store cannot even grant
it without "Allow restricted settings" — which is the dialog the user sent a screenshot of.

## v1.3.0 pass — backup, widget, sounds, Material You, pre-alert, language

Signed `release` 1.3.0 (versionCode 6) installed over 1.2.1 with `adb install -r`. A streamed
reinstall leaves a stale widget provider registration in the system (`min=0x0 widgetCategory=0`,
so the launcher does not list the widget); a clean `uninstall` + `install` fixes it. The new
`AlarmWidgetReceiver` itself is fine — `aapt2 dump xmltree --file res/SG.xml` on the release APK
shows `minWidth 250dp`, `minHeight 110dp`, `resizeMode 0x2`, `targetCell 4x2`.

| Area | What was done | Result |
|---|---|---|
| Language | Settings → Language → English | ✅ the whole UI switched instantly (Settings, Backup, Plugins, About) without a process restart, and the ring screen came up in English too (`Time to solve`, `Problem 1 of 2`, `Snooze`); `AppLanguageTest` 5/5 |
| Backup export | Settings → Backup → Export to file → SAF → `/sdcard/Download/awakr-backup-…json` | ✅ schema 1 with the alarms plus `settings{themeMode, swipeAction, defaultSnoozeMinutes, defaultMissionPluginId, defaultSoundId, dynamicColor, preAlertMinutes, language}` |
| Backup import | same screen → Choose file → tap the file *name* (tapping the row opens a preview in Material Files) → Replace everything | ✅ the alarm came back with a new id; merge mode adds without touching existing ones |
| Material You | Settings → Dynamic colors → on | ✅ the background went `#0b0c0e` → `#120d08` and the accent `#ffb020` → `#f0bd8d`, and the hero card follows the wallpaper palette |
| Widget | long-press the home screen → Widgets → search "Awakr" → drag the 4x3 cell | ✅ shows the next alarm (`07:30`, its repeat days) and "Skip next"; tapping the body opens `MainActivity` |
| Widget "Skip next" | tapped twice on the home screen | ✅ `dumpsys alarm` moved the RING 04.11 → 05.11 → 06.11 (one occurrence per tap), and the hero card follows — see the fixes below |
| Pre-alert | Settings → Notify before → 10 min, alarm at 13:20 | ✅ `dumpsys alarm` held `action.PRE_ALERT` at 13:10 next to `action.RING` at 13:20; the shade showed `Будильник через 10 мин / Скоро 13:20` on the silent `pre_alert` channel (importance 2) |
| Ring + mission | alarm at 13:20 answered on the ring screen | ✅ the ring screen came over the app, the mission timer counted down from `1:29`, the answer was accepted ("Problem 2 of 2") and `Snooze` logged `ring finished: SNOOZED` |
| Microphone | sound sheet → record | ⚠️ only the permission flow and the "too short" path were exercised; adb cannot feed real audio into the emulator input |

### Fixes made during this pass

| Found | Cause | Fix |
|---|---|---|
| the hero card announced Tuesday while the system alarm was set for Friday | `scheduleOf()` in `app/…/ui/format/AlarmSchedule.kt` called `NextOccurrence.next()` without `alarm.skipNextAt`, so the card and the list order ignored a pending skip that `AlarmScheduler` honoured | the skip instant is passed through; the new `AlarmScheduleTest` covers the skipped, disabled, one-shot and ordering cases |
| a second "Skip next" on the widget did nothing | `NextOccurrence.next()` suppressed only the occurrence equal to `skipNextAt`, so re-skipping recomputed the same instant (and could move the alarm *back*) | the parameter now means "nothing at or before this moment will ring": two taps move the alarm two occurrences ahead, and a one-shot alarm whose only occurrence is skipped still returns null (`SkipNextTest` +1) |
| changing the pre-alert lead time did not affect alarms that were already scheduled | the reminder is armed next to every alarm at scheduling time, so only a re-arm picks up the new value | `AwakrNavHost` calls `scheduler.rescheduleAll(enabledAlarms())` after the setting changes |
| a ring left on screen for ten minutes was logged as missed, and a later tap on "Snooze" wrote a second, contradictory row (`MISSED` then `SNOOZED`) plus a "Missed — the alarm never rang" card for an alarm that had rung | `RING_TIMEOUT_MS` was 10 minutes even though the mission can be failed and restarted many times, and `ACTION_DISMISS`/`ACTION_SNOOZE` were handled even when nothing was ringing | the ceiling is now 30 minutes, and both actions are ignored with a log line when no ring is active — the same guard `setSilenced` already had |
| a backup did not carry the three newer settings | the settings section of the format predates `dynamicColor`, `preAlertMinutes` and `language` | the codec writes them when present, reads an absent key as "keep the current value" and merges against the live settings; `BackupCodecTest` +4 and the new `BackupSettingsMergeTest` |

## v1.4.0 pass — chains, four more missions, stats, wake-up check

Built and installed? No: the Pixel 7 Pro was not attached over USB during this release
(`adb devices` returned no devices), so the table below records what the build, the unit tests and
the code review cover. Everything marked ⏳ still has to be confirmed on hardware.

| Area | What was done | Result |
|---|---|---|
| Mission chain | editor → add three steps, reorder with the arrows, save, reopen | ✅ DB migration 2→3 adds `mission_chain`; `MissionChainTest` 7/7; `RingScreen` plays `RingMission` items in order with "Mission i of n" and restarts the timer per step; `RingActivity` resolves the chain through `produceState` |
| Chain on the ring | dismiss only the last step | ⏳ needs a device: the first stage must not stop the ring |
| Memory / colour / squat / step | four new plugin modules, no new permissions | ✅ 26 unit tests; the modules are registered in `settings.gradle.kts`, `app/build.gradle.kts` and `AwakrApplication.kt` (8 missions in the list); the merged manifest gained nothing |
| Statistics | Home → Stats, 7/30 day switch | ✅ `StatsCalculatorTest` 23/23, including the two bugs the review caught (`CircularTime` returned 1440 instead of 0 for midnight; snoozes summed the whole chain counter) |
| Wake-up check | Settings → Wake up check → 1 min, dismiss an alarm, wait | ⏳ device only; `WakeUpCheckPolicyTest` covers the encode/decode and the option list, and `AlarmScheduler` cancels the check on dismiss and snooze |
| Spoken label / accent colour / panic ramp | Settings → Speak the label, Accent colour; ring for 40 s | ⏳ device only (TextToSpeech needs the system engine); the accent palette is a pure `ColorScheme` transform |
| Ring screen after backgrounding | swipe the ring away, tap the notification | ⏳ device only: `onResume` / `onNewIntent` re-apply `showWhenLocked` and `KEEP_SCREEN_ON`, `onStop` re-posts the ringing notification, and the legacy API<27 branch no longer uses `FLAG_DISMISS_KEYGUARD` (that flag is what asked for the PIN) |

## v1.4.1 pass — the ring screen over a dark display

Reported on a GrapheneOS Pixel 7 Pro (release APK over adb): "the alarm rings, but the screen stays
black and the only way in is the notification shade". The alarm answered that with a silent
notification, and the system never opens a silent notification's full screen intent, so the display
stayed off.

| Area | What was done | Result |
|---|---|---|
| Full screen ring, display off | alarm set, screen off and dozing, then ring | ✅ `mWakefulness=Awake`, `RingActivity` resumed, five ring windows, no PIN prompt. Before the fix: `mWakefulness=Dozing`, zero ring windows, `START … (BAL_BLOCK)` in logcat and `flags=…SILENT` in the notification record |
| The silent flag | `buildNotification()` no longer calls `setSilent(true)`; the channel has no sound and the service plays the audio, so the notification stays quiet | ✅ notification record without `SILENT`, full screen intent honoured with `USE_FULL_SCREEN_INTENT: default` (a fresh install, no appop tweak) |
| Wake lock attempt (reverted) | a `SCREEN_BRIGHT_WAKE_LOCK` with `ACQUIRE_CAUSES_WAKEUP` did turn the panel on, but it made the display interactive before the notification was posted, so the system downgraded the full screen intent to a heads-up notification | ❌ removed; the display is woken by the full screen intent instead |
| Mission chain on the ring screen | alarm with two missions (math, colour hunt) | ✅ "Миссия 1 из 2" → two maths examples → "Миссия 2 из 2" → three tiles → `ring finished: DISMISSED`, back to the home screen |
| Leaving the ring screen | Home, then Recents | ✅ `RingActivity: leaving the ring is not allowed, bringing it back`, the ring screen returns; an alarm is dismissed by the mission or by snoozing |
| Timer while a mission runs | watching the ring screen | ✅ "Время на задание" counts down from 1:30, the sound stays muted, and the board resets to a new target when it expires |

## v1.5.0 pass — the "I'm up" gate

Built from `versionCode 10` / `1.5.0`, installed on the Pixel 7 Pro (GrapheneOS, screen off, secure
lock screen) with the alarm set three minutes ahead.

| Area | What was done | Result |
|---|---|---|
| Gate on a dark display | alarm fires with the screen off and locked | ✅ screen wakes, `RingActivity` on top, no PIN; `AlarmRingService: display is off, the full screen intent opens the ring screen` |
| Sound until the tap | 40 seconds of ringing before touching anything | ✅ `silenced=false` on every tick, `panic stage: ringing at full volume` after 40 s — no mission silences the alarm by itself |
| Gate content | reading the ring screen | ✅ «Вы встали?» / «Будильник звенит, пока вы не подтвердите. Дальше — миссия.» / «Я встаю» + «Отложить»; no mission and no countdown yet |
| After "I'm up" | tapping the button | ✅ `silenced=true`, «Время на задание» 1:27 and «Пример 1 из 2: 15 − 3 = ?» appear |
| Dismiss path | solving both examples | ✅ `ring finished: DISMISSED`, back to the home screen |
| Chain | ring with two missions | ⏳ the gate shows once, before the first step; the chain itself was verified in 1.4.1 |

The mute effect in `RingScreen` now keys on the gate state, so no mission can silence a ring the
user has not confirmed.




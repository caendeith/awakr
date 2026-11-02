# FAQ and troubleshooting

Real questions from real users, with the fix. If your case is not here, open an issue:
<https://github.com/caendeith/awakr/issues>.

**Version note.** Entries marked **fixed in 1.2.1** need an update: the APK on
[GitHub Releases](https://github.com/caendeith/awakr/releases/latest) or the F-Droid repository (see
[Installing from F-Droid](#installing-from-f-droid)).

## The alarm asked for my PIN or pattern before I could dismiss it

**Fixed in 1.2.1.** In 1.2.0 the ring screen tried to *dismiss* the keyguard, so on a phone with a
secure lock screen Android showed the "enter your PIN/pattern" prompt first. Alarms must not ask for
the password: the ring screen is drawn *above* the keyguard, exactly like the system alarm clock.
From 1.2.1 the screen simply appears over the lock screen.

Until you update, the workaround is to unlock the phone: the alarm is already ringing, the prompt is
only the authentication dialog in front of it.

## The alarm played the system default sound, not the one I picked

**Fixed in 1.2.1.** 1.2.0 read a legacy field for the sound and therefore fell back to the phone's
default alarm sound for every alarm. From 1.2.1 the sound stored on the alarm (a built-in tone or a
file you imported) is the one that plays. If the file you imported is gone (you deleted it, or you
reinstalled the app), the alarm still rings: it falls back to the default sound instead of staying
silent.

## After I unlocked the phone, the alarm screen was gone and I had to open it from the notification

**Fixed in 1.2.1.** Same root cause as the PIN prompt: the ring activity was asking the keyguard to
go away and ended up behind it. From 1.2.1 the ring screen stays in front before and after unlocking,
and the full-screen intent brings it back if you leave it.

## "Access denied for this app" / «Доступ для приложения запрещен» when I turn on "Display over other apps"

This is an Android 13+ protection, not an Awakr error. Apps installed outside a store (adb, a file
manager, F-Droid, our own repository) are put in *restricted settings*: sensitive toggles such as
"Display over other apps" (Поверх других приложений) cannot be switched on until you allow them
explicitly for that app. The dialog you get has only a "Close" button, and the text warns that the
app may "work incorrectly" without the access.

How to allow it:

1. Open **Settings → Apps → Awakr** (Настройки → Приложения → Awakr).
2. Tap the three-dot menu in the top right corner.
3. Choose **Allow restricted settings** (Разрешить ограниченные настройки) and confirm with your
   screen lock.
4. Go back to **Display over other apps** (Поверх других приложений) and switch the toggle on — it
   now works.

The menu entry only appears after you have tried to use the restricted capability once, and on some
vendor ROMs the path differs (Samsung: *Apps → Awakr → ⋮ → Allow restricted settings*; Xiaomi/HyperOS
keeps the same entry inside app info).

**From 1.2.1 Awakr does not need this permission at all.** The ring screen is raised with the
full-screen intent and `setShowWhenLocked`, which is how the system alarm works; the overlay
permission was declared but never used, so it was removed from the manifest. After updating you can
switch it off.

## Nothing rings at all

Check in this order — each item is a real reason an alarm stays silent:

1. **Notifications are allowed** (Android 13+): *Settings → Apps → Awakr → Notifications*. Denied
   notifications also hide the fallback ring notification.
2. **Exact alarms are allowed** (Android 12+): *Settings → Apps → Awakr → Alarms & reminders*
   (Будильники и напоминания). Without it Android may delay the alarm until the phone wakes for
   something else. Awakr asks for this during onboarding.
3. **Battery optimisation is off for Awakr.** Most Chinese OEM ROMs (Xiaomi, realme, OPPO, vivo,
   OnePlus, Samsung "Sleeping apps") kill background apps aggressively. Set Awakr to *Unrestricted*
   (Без ограничений) in the app's battery settings; on MIUI also enable *Autostart*.
4. **The alarm volume is not at zero** and Do Not Disturb is not blocking alarms: *Settings → Sound →
   Do Not Disturb → Alarms* must be allowed (Awakr plays on the alarm stream, so a media-only mute
   does not silence it).
5. **The alarm is actually enabled** and its day mask includes today: the home screen shows the next
   ring time under the alarm time.

## The alarm rings late

Almost always the exact-alarm permission or battery optimisation — see the checklist above. Awakr
schedules with `setAlarmClock`, which the system treats as the highest-priority alarm and shows in
the status bar; if you do not see the alarm icon in the status bar, the alarm was not accepted by the
system and the permission list is the place to look.

## While I solve the mission the sound is silent — is that a bug?

No, that is the design (from 1.2.1). While a mission is on screen the alarm is muted and a countdown
shows how long you have. If you do not finish in time, the sound comes back, the task is replaced with
a new one and the countdown restarts — the same "you stalled, try again" loop Alarmy uses. Passing the
mission stops the sound completely.

## Can I use my own sound?

Yes. Editor → sound card → **Import** (Импорт), pick any audio file. The file is copied into Awakr's
private storage, so it keeps working after the original is deleted; uninstalling the app removes the
copy. The alarm always rings even if the copy is missing (default sound fallback).

## I see a plugin list but no button to add a plugin

By design. Missions are plugins in the source tree, not downloads: each one is a small Gradle
module compiled into the APK. Awakr deliberately has no way to load code at runtime — that would
need the `INTERNET` permission, would be rejected by Google Play, and could break the F-Droid
recipe. The Plugins screen therefore only lets you enable or disable what is already built in.

Writing your own mission is still supported and takes one module — the guide is in
[docs/plugins.md](plugins.md). Runtime-loaded plugins are on the roadmap for a later version.

## Does Awakr need internet access?

No. The app has no `INTERNET` permission at all — CI fails the build if it ever appears in the merged
manifest. There are no analytics, no ads, no crash reporting, no accounts.

## Installing from F-Droid

Awakr publishes a self-hosted F-Droid repository:

1. Install the F-Droid client from <https://f-droid.org>.
2. *Settings → Repositories → Add repository* and paste
   `https://caendeith.github.io/awakr/fdroid/repo`.
3. Refresh, search for "Awakr" and install.

Updates then arrive through F-Droid. The official f-droid.org listing is a separate process (a merge
request against `fdroiddata`), see [docs/fdroid.md](fdroid.md).

## Can I update over an older install?

Only if both builds are signed with the same key. The APK on GitHub Releases and the one in our F-Droid
repository share the release key, so updates work. Debug builds (`.debug` suffix) are a separate app
and cannot be updated over a release build — uninstall one first.

## How do I know which version I have?

*Settings → About* (Настройки → О приложении) shows the version. The F-Droid repository and GitHub
Releases always carry the newest one.

## Something else is broken

Open an issue with:

- the version from *Settings → About*,
- the phone model and Android version,
- what the alarm did (rang late, did not ring, dismissed by itself, sound cut off),
- whether the alarm shows in the status bar as a scheduled `setAlarmClock` alarm,
- ideally a screen recording of the ring screen.

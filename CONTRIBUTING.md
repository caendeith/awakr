# Contributing to Awakr

Thanks for taking a look. Awakr is a small, deliberately boring codebase: it does one thing
(a reliable alarm clock) and it does it offline. Contributions that keep it that way are welcome.

Before you start, two rules that are not negotiable:

1. **No network.** Awakr must never declare the `INTERNET` permission. A pull request that adds it
   is rejected without discussion.
2. **Clean room.** Do not copy code, assets, sounds or strings from other alarm applications.
   Everything is written from scratch and released under MIT.

For anything bigger than a typo, open an issue first so we do not duplicate work.

## Development environment

- JDK 17 (project uses `jvmToolchain(17)`).
- Android SDK: platform 36, build-tools 36.0.0, platform-tools.
- No Android Studio is required, but the project is a plain Gradle build and imports fine.

The build resolves dependencies from public Maven mirrors (aliyun/google, huaweicloud) configured in
`settings.gradle.kts`, because that is the only setup that works from restricted networks. If your
network reaches Maven Central directly you may add `google()` and `mavenCentral()` **after** the
mirrors, never instead of them. See [docs/toolchain.md](docs/toolchain.md) for the full list of
version constraints and pitfalls (for example: core-ktx 1.19 and Compose BOM 2026.09 require
compileSdk 37 and AGP 9, so the project stays one generation behind on purpose).

Release signing is optional locally: create `keystore.properties` in the repository root with
`storeFile`, `storePassword`, `keyAlias`, `keyPassword` and put the keystore next to it. Without that
file `assembleRelease` still works and produces an unsigned APK. Never commit a keystore or its
passwords.

## Everyday commands

```bash
./gradlew test                      # unit tests of every module
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:lintDebug            # must finish with 0 errors
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n io.github.caendeith.awakr.debug/io.github.caendeith.awakr.MainActivity
```

On Windows with Git Bash the wrapper needs the proxy variables cleared before the JVM starts, and
`JAVA_HOME`/`ANDROID_HOME` set:

```bash
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy
export JAVA_HOME="C:/Program Files/Java/jdk-17"
export ANDROID_HOME="D:/path/to/android-sdk"
```

## Repository layout

| Module | Responsibility |
|---|---|
| `:app` | UI, navigation, alarm service and receivers, application wiring |
| `:core:time` | next-occurrence calculation, DST-safe day math |
| `:core:alarm` | alarm model, repeat-day bit masks |
| `:core:audio` | sound playback (audio focus, `USAGE_ALARM`) |
| `:core:notify` | notification channels |
| `:core:perm` | permission policy per API level |
| `:core:ui` | design system (colors, type, buttons, cards) |
| `:data` | Room database, DataStore settings, repositories |
| `:sdk` | the public plugin SDK (`MissionPlugin`, `PluginManifest`, `SettingField`) |
| `:plugin-runtime` | registry, settings codec, permission checks |
| `:plugin-codegen` | code generation scaffolding for the registry |
| `:plugins:mission-*` | mission plugins: math, QR, shake, typing |

## Adding a mission in one pull request

A mission is a self-contained Gradle module. There is no service locator and no runtime class
path scanning: the registry is generated at build time, so a new module plus one line of wiring is
enough.

1. **Copy a module.** Take `:plugins:mission-typing` (the smallest one) and rename it:

   ```
   plugins/mission-hold/build.gradle.kts
   plugins/mission-hold/src/main/kotlin/io/github/caendeith/awakr/plugins/mission/hold/HoldMissionPlugin.kt
   plugins/mission-hold/src/main/res/values/strings.xml
   plugins/mission-hold/src/main/res/values-ru/strings.xml
   ```

   Set `namespace = "io.github.caendeith.awakr.plugins.mission.hold"` and keep `minSdk = 26`,
   `compileSdk = 36`, `jvmToolchain(17)`. Depend on `implementation(project(":sdk"))` and the Compose
   artifacts only; a mission never depends on `:app`, `:data` or another plugin.

2. **Write the plugin.** The whole contract is three members:

   ```kotlin
   class HoldMissionPlugin : MissionPlugin {
       override val manifest = PluginManifest(
           id = "mission-hold",                       // stable, persisted in alarms.mission_plugin_id
           name = StringRes(R.string.plugin_hold_name),
           version = "1.0.0",
           capabilities = setOf(Capability.MISSION),
           // requiredPermissions = listOf(Manifest.permission.CAMERA)  // only if you really need one
       )

       override val kind: String = "hold"             // stable, persisted per alarm

       @Composable
       override fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit) { /* settings */ }

       @Composable
       override fun Mission(config: JsonObject, onSolved: () -> Unit) { /* the task itself */ }
   }
   ```

   Rules for the two composables: they render *content only* — the host owns the ring screen chrome,
   spacing, the dismiss/snooze buttons and theming, so read colors from `MaterialTheme`. Call
   `onSolved()` exactly once, and only when the task is genuinely completed. Persist user choices by
   calling `onChange(config.with("key", value))` immediately; the host stores the JSON blob per alarm.
   If your mission needs a permission, declare it in `requiredPermissions` **and** in
   `app/src/main/AndroidManifest.xml` — the registry reports the mismatch as a plugin issue otherwise.

3. **Register the module.** Two lines and one list:

   - `settings.gradle.kts`: `include(":plugins:mission-hold")`
   - `app/build.gradle.kts`: `implementation(project(":plugins:mission-hold"))`
   - `app/src/main/kotlin/io/github/caendeith/awakr/AwakrApplication.kt`: append
     `HoldMissionPlugin()` to the list passed as `plugins =` in `PluginRegistry.build(...)`.

4. **Add strings** to `values/strings.xml` and `values-ru/strings.xml` of the new module. Lint fails
   the build on hardcoded text, so every user-visible string goes through `stringResource`.

5. **Add a unit test** for the pure logic (generator, matching, math). Keep the composable thin: if
   the interesting part is in a plain class, put that class in its own file and test it. Tests run on
   the JVM, so no Robolectric and no device; add `testOptions { unitTests.isReturnDefaultValues = true }`
   to the module and `testImplementation(libs.junit)`.

6. **Verify on a device.** Build, install, create an alarm, pick the mission, save, trigger the alarm:

   ```bash
   adb shell am broadcast -a io.github.caendeith.awakr.action.RING \
     -n io.github.caendeith.awakr.debug/io.github.caendeith.awakr.alarm.AlarmReceiver --el alarm_id <id>
   ```

   The broadcast only works while the app is in the foreground (it is a debug convenience; the
   receiver is exported in the debug manifest only). Solve the mission, then confirm the log line
   `ring finished: DISMISSED` and a row with `mission_passed = 1` in `alarm_logs`.

That is the whole ceremony: one module, three wiring lines, strings in two languages, one test.

## Code style

- Kotlin official style, 4-space indent, no wildcard imports.
- Composable functions are `PascalCase` and stateless where possible; state is hoisted to the caller.
- No reflection, no dynamic class loading: plugins are resolved at build time by design.
- All user-facing strings go to resources; `lint.xml` treats `HardcodedText` as an error.
- Keep the dependency surface small. New libraries need a reason in the pull request.

## Tests and lint

Both must be green before a pull request is reviewed:

```bash
./gradlew test :app:lintDebug
```

Unit tests cover pure logic: repeat-day masks, next occurrence across DST, missed-alarm detection,
settings codec, plugin registry validation, math generation, shake tracking, QR frame rotation and
payload matching. Anything that needs a phone is listed in [docs/manual-qa.md](docs/manual-qa.md).

## Translations

The app ships English (default) and Russian. To add a language, copy `app/src/main/res/values/strings.xml`
to `values-<tag>/strings.xml` and translate; the same for every `plugins/mission-*/src/main/res`.
`app_name` stays untranslatable. Partially translated locales are fine — missing keys fall back to
English — but keep the placeholders (`%1$s`, `%1$d`) intact.

## Commits and pull requests

- Commit messages follow Conventional Commits: `feat:`, `fix:`, `docs:`, `test:`, `chore:`, `refactor:`.
- One logical change per commit; describe *why* in the body, not just what.
- In the pull request, state what you tested and on which device / API level. "Compiles" is not a
  test for an alarm clock.

## License

Awakr is MIT licensed. By contributing you agree that your contribution is released under the same
terms (see [LICENSE](LICENSE)).

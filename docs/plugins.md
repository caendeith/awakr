# Writing a mission plugin

Awakr is built as a small core plus plugins, but the plugins are **compiled into the APK at
build time**. There is no "add plugin" button, no plugin store and no downloading of code at
runtime — by design:

- Google Play forbids loading executable code that was not shipped with the app, so a runtime
  plugin loader would keep Awakr out of mainstream stores and break the F-Droid recipe.
- Awakr has no `INTERNET` permission at all; a plugin store would need one.
- Build-time plugins are type-checked, R8-safe and need no reflection, so a broken plugin is a
  compile error rather than a crash on a ringing alarm.

What the plugin system buys you is isolation and replaceability *in source*: a mission is one
Gradle module with a tiny stable SDK, wired in one line. Writing your own takes about an hour.

## What a mission is

A mission implements `io.github.caendeith.awakr.sdk.MissionPlugin`
(`sdk/src/main/kotlin/io/github/caendeith/awakr/sdk/MissionPlugin.kt`):

```kotlin
@AwakrApi
interface MissionPlugin : AwakrPlugin {
    /** Stable identifier persisted in alarms.mission_plugin_id, e.g. "math". */
    val kind: String

    /** Settings shown inside the alarm editor; onChange persists what you return. */
    @Composable fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit)

    /** The task shown while the alarm rings. Call onSolved exactly once. */
    @Composable fun Mission(config: JsonObject, onSolved: () -> Unit)

    /** Validates and migrates config; the result is what gets stored. */
    fun validate(config: JsonObject): JsonObject = config
}
```

`AwakrPlugin` only adds `manifest: PluginManifest`:

```kotlin
PluginManifest(
    id = "typing",                       // unique, stable
    name = StringRes(R.string.plugin_typing_name),
    version = "1.0.0",
    capabilities = setOf(Capability.MISSION),
    apiVersion = AwakrApiVersion.CURRENT,
    requiredPermissions = emptyList(),    // must already be declared by the app
    settings = listOf(                    // rendered automatically by SettingsForm
        SettingField.Slider("words", title = StringRes(...), min = 1f, max = 6f, step = 1f),
        SettingField.Toggle("caseSensitive", title = StringRes(...)),
    ),
)
```

Supported setting widgets: `Toggle`, `Slider`, `Select`, `TextInput`, `Info`. The host renders
them and stores the JSON blob; you never draw a settings screen yourself.

## Step by step

1. Copy an existing module, e.g. `plugins/mission-typing`, to `plugins/mission-mine`.
2. Register it in `settings.gradle.kts`:

   ```kotlin
   include(":plugins:mission-mine")
   ```

3. Its `build.gradle.kts` needs only `:sdk`, Compose and coroutines — copy the block from
   `plugins/mission-typing/build.gradle.kts`.
4. Implement the three members. Keep `kind` short and never change it: it is stored in the
   database of every alarm that uses your mission.
5. Add your strings to the module's `res/values/strings.xml` (and `values-ru` if you can), and
   reference them through `StringRes`.
6. Register the plugin instance in `app/src/main/kotlin/io/github/caendeith/awakr/AwakrApplication.kt`
   (`PluginRegistry.build(plugins = listOf(..., MyMissionPlugin()))`).
7. Add `implementation(project(":plugins:mission-mine"))` to `app/build.gradle.kts`.
8. Build and install:

   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Rules the host relies on

- `Mission` must call `onSolved()` exactly once, and only after the task is really solved. The
  host owns the ring screen, the 90 s mission timer, muting and the snooze button.
- `validate` must be pure and must tolerate junk: it receives whatever JSON was stored, including
  data written by an older version of your plugin.
- A mission that throws is reported in Settings → Plugins as a plugin issue; the alarm then falls
  back to the "hold for 5 seconds" mission, so a broken plugin never leaves you without a wake-up.
- Do not ask for permissions the app does not declare. `requiredPermissions` is checked against the
  merged manifest at startup; a mismatch shows up as a plugin issue instead of a silent denial.

## Sharing your mission

Open a pull request against https://github.com/caendeith/awakr — a mission module is a
self-contained directory plus two one-line registrations. If you would rather keep it to
yourself, fork the repository and build your own APK; everything in Awakr is MIT-licensed.

Runtime-loaded plugins (a mission in a separate APK) are on the roadmap for a later version,
together with a separate build flavour, because they cannot ship in the store builds.

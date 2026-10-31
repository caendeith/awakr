# Toolchain (recorded: 2026-10-31)

Проверено сборкой `:app:assembleDebug` на Windows, JDK 17.0.6 (Temurin/Oracle 17), без Android Studio.

## Что установлено локально (в `tools/`, в git не попадает)

| Компонент | Версия / путь |
|---|---|
| Android SDK root | `tools/android-sdk` (`local.properties: sdk.dir`) |
| cmdline-tools | 23.0 (`commandlinetools-win-16111833_latest.zip`) — **не используется**, SDK-пакеты распакованы напрямую |
| platform-tools (adb) | 37.0.1 |
| platforms | android-36 (`platform-36_r02.zip`) |
| build-tools | 36.0.0 (`build-tools_r36_windows.zip`) |
| Gradle | 8.14.6 (`mirrors.cloud.tencent.com/gradle/`, wrapper distributionUrl тоже на зеркало) |
| Licenses | `tools/android-sdk/licenses/android-sdk-license` записан вручную (sdkmanager не смог: новый CLI качает `android-cli.exe` через прокси → 403) |

## Реальные версии, на которых собралось

| Компонент | Версия |
|---|---|
| AGP | 8.13.2 |
| Gradle | 8.14.6 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.5 |
| Compose BOM | 2026.03.01 |
| core-ktx | 1.17.0 |
| activity-compose | 1.11.0 |
| lifecycle | 2.9.4 |
| navigation-compose | 2.9.8 |
| work-runtime-ktx | 2.10.5 |
| datastore-preferences | 1.1.7 |
| room | 2.8.5 |
| camera (CameraX) | 1.5.3 |
| zxing core | 3.5.4 |
| compileSdk / targetSdk | 36 / 36 |
| buildToolsVersion | 36.0.0 |
| minSdk | 26 |
| JDK | 17 |

## Подводные камни (важно для воспроизведения)

1. **Максимальные androidx-версии не подходят.** `core-ktx 1.19.x`, Compose BOM `2026.09.00` (ui 1.12.1) требуют `compileSdk 37` **и AGP 9.1+**. Матрица спеки (AGP 8.13.2 / compileSdk 36) совместима только с предыдущим поколением androidx — отсюда пины выше. При переходе на AGP 9.x + compileSdk 37 — обновлять всё сразу.
2. **Зеркала maven.** `repo1.maven.org` / `repo.maven.apache.org` / `services.gradle.org` недоступны в этой сети. Работают: `maven.aliyun.com/repository/google` (только android/com.android/androidx группы — фильтр `content { includeGroupByRegex }`), `repo.huaweicloud.com/repository/maven`, `maven.aliyun.com/repository/public`.
3. **aliyun google отдаёт 502 на не-android артефакты** (например KSP) — именно поэтому в `settings.gradle.kts` у google-зеркала стоит group-фильтр, иначе плагин KSP «не находится».
4. **Shell-прокси** (`HTTP_PROXY=http://127.0.0.1:55469`) отдаёт 403 на CONNECT; Java/Gradle через него не работают. Все сборки запускать с `unset HTTP_PROXY HTTPS_PROXY ALL_PROXY` (прямой доступ к зеркалам есть).
5. **sdkmanager не юзабелен** — распаковывать официальные zip из `dl.google.com/android/repository/` напрямую: `platform-36_r02.zip` → `platforms/android-36`, `build-tools_r36_windows.zip` → `build-tools/36.0.0` (внутри папка `android-16` — переименовать), `platform-tools_r37.0.1-win.zip` → `platform-tools/`.
6. **Лицензии.** Без `licenses/android-sdk-license` Gradle падает на «licences have not been accepted». Файл создан вручную со стандартными SHA1.
7. **buildToolsVersion** зафиксирован в `app/build.gradle.kts` (`36.0.0`), иначе AGP хочет 35.0.0 и пытается его скачать.

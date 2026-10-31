# Awakr — спецификация v1 (будильник с миссиями, плагинная архитектура)

- Статус: **черновик на утверждение** (после утверждения — заморожен для v1)
- Дата: 2026-10-31
- Лицензия: **MIT**
- Имя: **Awakr** · репозиторий: https://github.com/caendeith/awakr
- Application ID: `io.github.caendeith.awakr` (выбран один раз и замораживается до релиза)
- ID плагинов: `io.github.caendeith.awakr.mission.<kind>`, `...sound.<id>`, `...theme.<id>`, `...locale.<code>`

---

## 1. Цель и не-цели

### 1.1 Цель
Собственное open-source приложение-будильник с миссиями на выключение, функционально сопоставимое с Alarmy, но:
- **без рекламы, аналитики, аккаунтов и подписки** — всё бесплатно и локально;
- **свой дизайн, свой код, свои ассеты** (clean-room: ни строки декомпилированного кода, ни одного ассета из оригинального APK);
- **расширяемое плагинами** (по образцу DSH: манифест, capabilities, автодискавери, вкл/выкл, декларативная схема настроек);
- локализуемое: базово **en + ru**, добавление языков — штатный сценарий.

### 1.2 Не-цели v1
- Сон/трекинг сна, звуки для засыпания, wake-up check, статистика — **v2/v3** (§11); архитектура под них заложена.
- Облако, синхронизация, аккаунт, соцфичи — **не будут** реализованы никогда (не-цель продукта).
- Публикация в Google Play. F-Droid — возможна позже (архитектура FOSS-совместима).

### 1.3 Целевые устройства
- Личный **Pixel 7 Pro, Android 17** — основная проверка.
- `minSdk 26` (Android 8.0), `targetSdk 36` — поддержка «недавно старых» версий через проверки API.
- Никаких GMS-зависимостей (QR — ZXing, не ML Kit): работает на де-гугленных устройствах и совместимо с F-Droid.

---

## 2. Требования

### 2.1 Функциональные (FR, v1)
| # | Требование | Приёмка |
|---|---|---|
| FR-1 | CRUD будильника: время, дни недели (битмаска), метка, звук, вибро, нарастание громкости | Переживает перезапуск |
| FR-2 | Точное срабатывание при выключенном/заблокированном экране и в Doze | ≤ 2 с от заданного, проверено на устройстве |
| FR-3 | Full-screen экран звонка поверх локскрина с миссией | Показывается при заблокированном экране |
| FR-4 | Выключение только после миссии; back/home/свайп не выключают | Обход через системную навигацию невозможен |
| FR-5 | 4 миссии: математика, QR/штрихкод, тряска, ввод текста | Каждая настраивается при создании |
| FR-6 | Snooze с лимитом повторов; нарастание громкости | Настройки применяются |
| FR-7 | Перепланирование после перезагрузки, смены времени/пояса, обновления | Будильник не теряется |
| FR-8 | Список будильников: ближайший с обратным отсчётом, быстрый вкл/выкл | Корректно отображается |
| FR-9 | Свои звуки: системные рингтоны + встроенный CC0-набор | Выбранный звук играет |
| FR-10 | en + ru, переключение языка в настройках (per-app locale) | Весь UI локализован, хардкода нет |
| FR-11 | Плагины: список, вкл/выкл, экран настроек из схемы | Отключённая миссия не предлагается в редакторе |
| FR-12 | Онбординг разрешений с пояснениями и статусами | Все пункты проходимы |
| FR-13 | About: версия, MIT, OSS-библиотеки | Открывается |

### 2.2 Нефункциональные (NFR)
- **Надёжность важнее фич**: будильник не должен «тихо не сработать».
- **APK** ~5–8 МБ (звуковые ассеты — лениво/опционально).
- **Холодный старт** < 1.5 с, **старт звонка** < 300 мс.
- **Ноль сетевых разрешений** в манифесте v1 (проверка в CI).
- **Приватность**: без аналитики, crash-репортинга и аккаунтов.
- **Доступность**: touch target ≥ 48dp, контраст WCAG AA, масштаб шрифта до 1.3×, TalkBack-метки.
- **Энергия**: никаких постоянных foreground-сервисов и wake-lock'ов вне звонка.

---

## 3. Плагинная архитектура

### 3.1 Принцип (по образцу DSH)
Плагин = отдельный Gradle-модуль + `PluginManifest` + контракт из стабильного SDK. Плагин объявляет capabilities и схему настроек; хост обнаруживает, валидирует, показывает в UI и может отключать в рантайме.

**Разделение «ядро / плагин»:** плагины расширяют поведение (миссии, звуки, темы, языки). Всё, от чего зависит надёжность (движок будильников, сервис звонка, БД, базовый UI), живёт в host — плагин не может сломать срабатывание.

### 3.2 Граф модулей
```
app/                       host: навигация, экраны, DI-контейнер, инициализация плагинов
core/time/                 next-occurrence, зоны/DST
core/alarm/                AlarmScheduler, AlarmManager, receivers, AlarmRingService
core/audio/                MediaPlayer, audio focus, ramp, вибро
core/notify/               каналы, full-screen intent
core/perm/                 разрешения по API-уровню
core/ui/                   тема, токены, компоненты, типографика
data/                      Room (alarms, logs, plugin_state), DataStore
sdk/                       публичный API плагинов (semver, @AwakrApi)
plugin-runtime/            PluginRegistry, PluginHost, валидация, рендер схемы настроек
plugin-codegen/            KSP-процессор: генерация реестра (без рефлексии)
plugins/mission-math/      «математика»
plugins/mission-qr/        «QR/штрихкод» (+ генератор QR для печати)
plugins/mission-shake/     «тряска»
plugins/mission-typing/    «ввод текста»
plugins/sound-default/     встроенный CC0-набор звуков
plugins/theme-default/     тема (светлая/тёмная/динамическая)
plugins/locale-ru/         русские строки (языковой пак)
```

### 3.3 Манифест плагина
```kotlin
@AwakrApi(since = 1)
interface AwakrPlugin { val manifest: PluginManifest }

data class PluginManifest(
    val id: String,                        // "io.github.caendeith.awakr.mission.math"
    val name: StringRes,
    val version: String,                   // semver плагина
    val apiVersion: Int,                   // версия SDK
    val capabilities: Set<Capability>,     // MISSION / SOUND_PACK / THEME / LOCALE
    val requiredPermissions: List<String>, // напр. CAMERA для QR
    val settings: SettingsSchema,
)
```

### 3.4 Точки расширения
| Capability | Контракт | Что даёт |
|---|---|---|
| `MISSION` | `MissionPlugin`: editor + ring composable + валидатор конфига | задание на выключение |
| `SOUND_PACK` | `SoundPackPlugin`: `SoundDescriptor` (uri/asset, длительность, нормализация) | звуки будильника |
| `THEME` | `ThemePlugin`: цветовые схемы, типографика | оформление |
| `LOCALE` | `LocalePackPlugin`: код языка + строки | новые языки без правки ядра |

```kotlin
@AwakrApi(since = 1)
interface MissionPlugin : AwakrPlugin {
    val kind: String                                        // "math"
    @Composable fun Editor(config: JsonObject, onChange: (JsonObject) -> Unit)
    @Composable fun Mission(config: JsonObject, onSolved: () -> Unit)
    fun validate(config: JsonObject): JsonObject             // валидация и миграция
}
```

### 3.5 Обнаружение и реестр
- **KSP-процессор** `plugin-codegen` генерирует `GeneratedPluginRegistry` — список фабрик. Без рефлексии, R8-безопасно.
- `PluginRegistry` при старте: фильтр по `apiVersion`, дедуп по `id`, проверка разрешений, применение `plugin_state.enabled`.
- Дубликат `id` / несовместимый `apiVersion` → плагин не грузится, видимая запись в `Settings → Плагины`, приложение не падает.
- Runtime-загрузка внешних плагин-APK в v1 **не поддерживается** (решение владельца: build-time), вынесена в v3: dynamic code loading запрещён в Play/F-Droid.

### 3.6 Декларативная схема настроек
```kotlin
sealed interface SettingField {
    val key: String; val title: StringRes
    data class Toggle(...) : SettingField
    data class Slider(val min: Float, val max: Float, val step: Float, val unit: String?) : SettingField
    data class Select(val options: List<Pair<String, StringRes>>) : SettingField
    data class TextInput(val maxLength: Int, val pattern: String?) : SettingField
    data class Info(val body: StringRes) : SettingField
}
```
Форму рендерит хост; значения — в `plugin_state.settings_json`.

### 3.7 Стабильность SDK
- `:sdk` — semver; ломающие изменения только с ростом `apiVersion`.
- Публичные типы помечены `@AwakrApi`, остальное `internal`.
- CI: binary-compatibility-validator.
- `CONTRIBUTING.md`: «как добавить миссию за 1 PR» + шаблон модуля.

---

## 4. Данные

### 4.1 Room (v1)
```sql
alarms(id INTEGER PK AUTOINCREMENT, hour INT, minute INT, days_mask INT, enabled INT,
       label TEXT, sound_uri TEXT, sound_pack_id TEXT, ramp_seconds INT, volume_start INT,
       vibrate INT, snooze_enabled INT, snooze_minutes INT, max_snoozes INT,
       mission_plugin_id TEXT, mission_config TEXT, created_at INT, updated_at INT)

alarm_logs(id INTEGER PK, alarm_id INT, scheduled_at INT, fired_at INT, dismissed_at INT,
           mission_plugin_id TEXT, mission_passed INT, snooze_count INT, outcome TEXT)
           -- outcome: DISMISSED | SNOOZED | MISSED | ERROR

plugin_state(plugin_id TEXT PK, enabled INT, settings_json TEXT, last_error TEXT)
```
- Миграции аддитивные, `autoMigrations`, экспорт схем в `schemas/`.
- `days_mask`: биты 0..6 = Mon..Sun (задокументировано в `Days.kt`).

### 4.2 DataStore
`locale`, `theme_mode`, `dynamic_color`, `accent`, `default_sound`, `onboarding_done`, `ring_fullscreen`, `snooze_defaults`.

---

## 5. Движок будильников

### 5.1 Планирование
- `AlarmScheduler` (интерфейс) → `AlarmManagerScheduler`.
- Основной вызов: `setAlarmClock(AlarmClockInfo(triggerAt, showIntent))` — переживает Doze, виден в статус-баре.
  **Проверено на Android 17 (API 37): `setAlarmClock` требует `USE_EXACT_ALARM` или `SCHEDULE_EXACT_ALARM`** — без них бросается `SecurityException: Caller ... needs to hold android.permission.SCHEDULE_EXACT_ALARM or android.permission.USE_EXACT_ALARM to set exact alarms`. Первоначальное утверждение спеки («не требует») было неверным. Реализация: объявлены оба разрешения, при отказе — fallback на `setAndAllowWhileIdle` (неточный, но срабатывает).
- Snooze: `setExactAndAllowWhileIdle(now + snoozeMinutes)`.
- `PendingIntent`: `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`, requestCode = `alarmId`, action = `ACTION_ALARM_FIRE`.
- После срабатывания сразу планируется следующее, если есть будущие дни; иначе будильник помечается «без даты» в UI.

Расчёт (`core/time`):
1. `ZonedDateTime.now(zone)`; для смещений 0..7 дней: `LocalDate.plusDays` → фильтр по `days_mask` (для одноразового — ближайшее `hour:minute` в будущем) → `LocalDateTime.atZone(zone)`.
2. DST-дыра (несуществующее локальное время) → сдвиг вперёд; явный тест.
3. DST-дубль → срабатывание один раз по раннему offset; явный тест.
4. Найденный момент ≤ now → следующий день.

### 5.2 Перепланирование
`BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` → пересчёт всех будильников. Плюс `WorkManager`-страховка раз в сутки (без сети) для самовосстановления.

### 5.3 Звонок
```
AlarmReceiver → AlarmRingService (foreground, type=mediaPlayback)
   ├─ MediaPlayer: AudioAttributes(USAGE_ALARM), audio focus, ramp 15%→100%
   ├─ Vibrator: паттерн из настроек
   ├─ startActivity(RingActivity) напрямую + FullScreenIntent-уведомление (showWhenLocked, turnScreenOn, singleInstance)
   └─ WakeLock (PARTIAL) на время звонка, с таймаутом-предохранителем
```
- `RingActivity`: блокирует back, immersive, держит экран, показывает время, «Отложить» (если разрешено и лимит не исчерпан) и карточку миссии.
- **Проверено на Android 17: полагаться на FSI нельзя.** При DND-режиме «Время сна» (`zen_mode=1`, правило EVERY_NIGHT, ZenPolicy `fullScreenIntent=disallow`) и `appop USE_FULL_SCREEN_INTENT: default` полноэкранный intent не поднимает активность. Поэтому сервис сам вызывает `startActivity(ringActivityIntent)` (разрешено: платформа даёт allowlist на 10 с при срабатывании alarm-clock — `tempAllowListReason reasonCode:ALARM_MANAGER_ALARM_CLOCK`); FSI-уведомление остаётся как второй путь. Для этого объявлен `SYSTEM_ALERT_WINDOW` (запрос в онбординге).
- Успех → `stopSelf()`, лог `DISMISSED`, планирование следующего.
- Лимит snooze исчерпан → кнопка скрыта, миссия обязательна.
- Плагин миссии отключён/упал при звонке → fallback-миссия «удерживай 5 секунд» (в host, не плагин) + `ERROR` в лог. **Будильник не должен застрять без выхода.**

### 5.4 Разрешения
| Разрешение / API | 26–30 | 31–32 | 33 | 34+ |
|---|---|---|---|---|
| `USE_EXACT_ALARM` | — (нет ограничения на точные будильники) | объявить (clock-приложениям выдаётся автоматически) | да | да |
| `SCHEDULE_EXACT_ALARM` | — | объявить как второй путь к точным будильникам | проверить | проверить |
| `USE_FULL_SCREEN_INTENT` | — | — | — | объявить (auto-grant для alarm-приложений) |
| `POST_NOTIFICATIONS` | — | — | запрос | запрос |
| `CAMERA` | запрос (плагин QR) | запрос | запрос | запрос |
| `RECEIVE_BOOT_COMPLETED`, `VIBRATE`, `WAKE_LOCK`, `DISABLE_KEYGUARD` | обычные | | | |
| Игнор оптимизации батареи | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, только с объяснением | | | |

`INTERNET` **не объявляется**; CI проверяет merged manifest.

### 5.5 Крайние случаи (обязательные тесты)
| Сценарий | Ожидание |
|---|---|
| Перезагрузка между планированием и временем | срабатывает по расписанию |
| Смена часового пояса | пересчёт без «фантомных» срабатываний |
| DST-переход | см. §5.1 |
| Два будильника в одну минуту | второй ждёт закрытия первого (очередь в сервисе) |
| Force-stop приложения | **проверено: `am force-stop` снимает все зарегистрированные будильники** → явное предупреждение в UI и онбординге, после force-stop перепланирование не произойдёт |
| Doze / экономия батареи | срабатывает (`setAlarmClock`) |
| DND | срабатывает (`USAGE_ALARM`) |
| Локскрин с PIN | миссия доступна |
| Плагин отключён при активном будильнике | fallback-миссия + `ERROR` в лог |
| Устройство было выключено | `MISSED` в логе, уведомление (по настройке) |

---

## 6. Миссии v1

| Миссия | Параметры (схема настроек) | Поведение |
|---|---|---|
| **Математика** | `difficulty` (easy/medium/hard), `count` 1–5 | 1 — сложение/вычитание до 20; 2 — + умножение до 12; 3 — + деление без остатка и трёхзначные. Крупная клавиатура, ошибка → новый пример, счётчик решённых |
| **QR/штрихкод** | `payload` (строка вида `AWAKR-XXXXXXXX`) | В редакторе: сгенерировать код и показать его для печати (кнопка «Создать мой код»/«Создать другой код»). Утром: CameraX + ZXing `MultiFormatReader`, совпадение с payload → успех; пустой payload принимает любой код. Без GMS и сети |
| **Тряска** | `seconds` 10–100 (шаг 5) | Акселерометр, magnitude с дебаунсом 120 мс, прогресс-кольцо, вибро-отклик |
| **Ввод текста** | `phrase` (пусто → фраза по умолчанию из ресурсов) | Совпадение без учёта регистра и пробелов по краям; ошибка подсвечивается красной обводкой |

Все миссии — плагины. Набор фраз ≥ 20 на язык (en/ru).

---

## 7. UI / UX

### 7.1 Экраны
1. **Home** — hero-карточка ближайшего будильника (время + обратный отсчёт), список со свитчами, FAB «+».
2. **Alarm Editor** — пикер времени, дни, метка, звук (+ превью), нарастание, вибро, snooze, «Миссия» (из реестра плагинов).
3. **Ring** — время, дата, карточка миссии, snooze (если доступен), индикатор попыток.
4. **Mission screens** — из плагинов, в общей рамке (прогресс, подсказка).
5. **Settings** — язык, тема, звук по умолчанию, разрешения со статусами, **Плагины**, About.
6. **Plugin detail** — из `PluginManifest`: имя, версия, capabilities, схема настроек, тумблер.

### 7.2 Визуальный стиль
- Material 3 Expressive, **тёмная по умолчанию**, светлая по системе.
- Палитра (тёмная): фон `#0B0C0E`, поверхность `#14161A`, контейнер `#1B1E24`, акцент **янтарный** `#FFB020`, on-accent `#241800`, ошибка `#FF6B6B`.
- Радиусы: карточки 28dp, кнопки 20dp, чипы 999dp. Сетка 4/8/12/16/24/32.
- Цифры времени моноширинные (tnum), 56–72sp, вес 300–400.
- Motion: пружины (damping 0.8), появление карточек fade+slide 180 мс, пульсация акцента на Ring.
- Dynamic Color (Material You) — тумблер.
- Иконка: своя векторная (абстрактный восход/«будильник-точка»), без сходства с Alarmy.

---

## 8. Локализация
- `values/strings.xml` (en, default) + `values-ru/strings.xml`.
- **Ноль строк в коде**: lint `HardcodedText` как ошибка в CI.
- Плюрализация через `plurals` (минуты, попытки).
- Per-app language: `LocaleManager` (33+) + `AppCompatDelegate` fallback (26–32).
- Форматы дат/времени по локали; тумблер 24/12 ч.
- Языковые паки — capability `LOCALE`; ru поставляется отдельным модулем как демонстрация механики.

---

## 9. Сборка, подпись, поставка

### 9.1 Матрица версий (проверяется шагом 0 — toolchain spike)
| Компонент | Основной | Резерв |
|---|---|---|
Проверенные значения (фактическая сборка, см. [toolchain.md](toolchain.md)); в скобках — резерв:

| Компонент | Проверено | Резерв |
|---|---|---|
| Gradle | 8.14.6 (зеркало tencent) | 8.14.x |
| AGP | 8.13.2 | — |
| Kotlin | 2.2.21 | — |
| KSP | 2.2.21-2.0.5 | — |
| Compose BOM | 2026.03.01 | — |
| Room | 2.8.5 | 2.8.2 |
| CameraX | 1.5.3 | — |
| ZXing core | 3.5.4 | 3.5.2 |
| compileSdk / targetSdk | 36 | 35 |
| minSdk | 26 | 28 |

Почему не новее: `core-ktx` 1.19.x и Compose BOM 2026.09.00 требуют compileSdk 37 и AGP ≥ 9.1.0 — с AGP 8.13.2 это hard error, поэтому проект живёт на предыдущем поколении androidx.

Репозитории: `google()` (dl.google.com — доступен), `maven.aliyun.com/repository/public|google`, `repo.huaweicloud.com` — зеркала **Maven Central, который в этой сети заблокирован**.

### 9.2 Android SDK
`commandlinetools-win` (dl.google.com) → `sdkmanager`: `platform-tools`, `platforms;android-36`, `build-tools;36.0.0`. Всё в `tools/android-sdk` (в `.gitignore`), `local.properties` → `sdk.dir`.

### 9.3 Подпись и установка
- `keytool` → `keystore/awakr-release.jks` (**не** в git).
- `assembleRelease` → `app-release.apk` → `adb install -r` на Pixel 7 Pro.
- `assembleDebug` работает без keystore; release-подпись — по инструкции README.

### 9.4 CI (GitHub Actions)
- `./gradlew test lint assembleDebug` на каждый PR.
- Проверки: нет `INTERNET` в merged manifest; нет хардкода строк; API-check `:sdk`; сборка всех плагин-модулей.
- Nightly unsigned APK как артефакт.

---

## 10. Тестирование
| Уровень | Что | Инструмент |
|---|---|---|
| Unit | next-occurrence (обычные дни, DST-дыра/дубль, одноразовый, days_mask), генератор математики, валидаторы миссий | JUnit5, параметризованные |
| Unit | реестр плагинов: дубликаты, apiVersion, отключение | JUnit5 |
| DAO | CRUD и миграции | Room testing |
| UI | editor→сохранение; ring→math; отключённый плагин→fallback | Compose test rule |
| E2E | матрица §5.5 на Pixel 7 Pro | `docs/manual-qa.md` |
| Статика | lint, api-check, (опц.) detekt | Gradle |

Правило: **каждый баг «не сработало» получает регрессионный тест**.

---

## 11. Дорожная карта
- **v1** (эта спека): ядро + 4 миссии + 2 языка + плагинная система + онбординг.
- **v2**: звуковые паки (импорт mp3), таймер сна и звуки для засыпания, темы-плагины, виджеты (Glance), бэкап/восстановление JSON, предуведомление «через 30 минут».
- **v3**: локальная статистика и графики, wake-up check, шагомер/приседания (акселерометр), фото-миссия (TFLite, локальная модель), память/пазл, экспериментальная runtime-загрузка плагин-APK (флейвор `sideload`, проверка подписи, вне сторов).
- **v4**: Wear OS companion (опционально).

---

## 12. Риски и меры
| Риск | Вер. | Мера |
|---|---|---|
| Android 17 (API 37) новее доступного `compileSdk 36` | средняя | targetSdk 36, тест на живом устройстве, без API 37 |
| Maven Central заблокирован | высокая | зеркала aliyun/huawei (проверено) |
| Первый билд ~1.5–2 ГБ загрузок | высокая | шаг 0 отдельным этапом, кэш Gradle |
| Vendor-ограничения full-screen intent | средняя | Pixel — референс; деградация: полный экран уведомления + громкий звук |
| Force-stop → будильник не сработает | высокая | предупреждение в UI + онбординг + `WorkManager`-страховка |
| Рост числа плагинных модулей замедляет сборку | средняя | configuration cache, параллельная сборка, изоляция `:sdk` |
| Юридические претензии | низкая | clean-room, свой нейминг/ассеты/иконка, «Alarmy» нигде не упоминается в метаданных, MIT |
| QR-миссия: камера + разрешения | средняя | ZXing + CameraX, отдельный тест на устройстве |

---

## 13. Критерии приёмки v1
1. Будильник срабатывает на Pixel 7 Pro при заблокированном экране: звук играет, экран включается.
2. Не выключается без прохождения миссии; back/home/свайп не помогают.
3. Все 4 миссии работают минимум в одном сценарии каждая.
4. После перезагрузки, смены пояса и переустановки APK будильники на месте.
5. Snooze с лимитом работает; после лимита — только миссия.
6. Плагины: отключённая миссия исчезает из редактора; отключённая при звонке → fallback.
7. en/ru переключаются без перезапуска; lint по хардкоду чист.
8. `assembleDebug` и `assembleRelease` собираются с нуля по README.
9. Нет `INTERNET` в merged manifest; нет рекламных/аналитических зависимостей.
10. APK ≤ 10 МБ, холодный старт < 1.5 с.
11. README/CONTRIBUTING/LICENSE (MIT) на месте; «добавь миссию за 1 PR» проверено на практике.
12. Юнит-тесты next-occurrence (включая DST) и генератора математики зелёные в CI.

---

## 14. Открытые вопросы
1. **Звуки**: CC0-набор (Freesound) или синтезированные свои тона?
2. **Шрифт**: системный Roboto или подключить OFL-шрифт (Inter/Manrope) для «своего» вида?
3. **Ветка по умолчанию** и защита `main` в репозитории `caendeith/awakr` (правила ревью) — нужно ли.

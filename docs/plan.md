# План реализации Awakr v1

Основание: [spec.md](spec.md) (утверждена 2026-10-31). Лицензия MIT, репозиторий https://github.com/caendeith/awakr (пустой).

## Принципы

1. **Надёжность > фичи.** Ни один этап не считается готовым, пока будильник не проверен на устройстве.
2. **Каждый этап закрывается проверяемым состоянием** (команда + ожидаемый результат), а не «написал код».
3. **Коммиты** — conventional (feat:/fix:/chore:), по одному логическому изменению.
4. **Push — только с разрешения владельца.** Локальные коммиты делаю сам.
5. **Toolchain фиксируется в версиях** через version catalog (gradle/libs.versions.toml), а не «что скачалось».

## Обзор этапов

| Этап | Содержание | Оценка | Проверка на устройстве |
|---|---|---|---|
| 0 | Тулчейн, Gradle-скелет, hello-world APK | 30–60 мин | да |
| 1 | Каркас репозитория, модули, дизайн-система | 1–1.5 ч | скриншот |
| 2 | Данные (Room/DataStore), SDK плагинов, KSP-реестр | 1.5–2 ч | нет |
| 3 | Движок будильников: время, scheduler, сервис, ринг | 2 ч | **да, обязательно** |
| 4 | Home + редактор будильника | 1–1.5 ч | да |
| 5 | 4 миссии-плагина + экран плагинов | 2–3 ч | да |
| 6 | Онбординг разрешений, i18n, About, пропущенные | 1 ч | да |
| 7 | QA-матрица, release-подпись, CI, README, push | 2 ч | да |

---

## Этап 0 — Toolchain spike

**Задачи**
1. Скачать commandlinetools-win с dl.google.com в tools/android-sdk; sdkmanager --licenses (accept), установить platform-tools, platforms;android-36, build-tools;36.0.0.
2. Скачать Gradle 8.14.x с mirrors.cloud.tencent.com/gradle/, распаковать в tools/gradle, сгенерировать wrapper (distributionUrl → зеркало).
3. settings.gradle.kts: pluginManagement/dependencyResolutionManagement с google(), maven.aliyun.com/repository/public, maven.aliyun.com/repository/google, repo.huaweicloud.com/repository/maven.
4. gradle/libs.versions.toml по матрице §9.1 спеки.
5. Минимальное приложение: MainActivity + Compose «Awakr», тема по умолчанию.
6. local.properties → sdk.dir.

**Проверка (done)**
- ./gradlew :app:assembleDebug — успех с нуля.
- adb devices видит Pixel 7 Pro; adb install -r app-debug.apk — приложение запускается на телефоне.
- Версии из матрицы подтверждены (или зафиксированы реально собравшиеся, с записью в спеку).

**Риски:** несовместимость AGP/Kotlin/KSP → откат по «Резерву» из §9.1; медленная сеть → качаем параллельно и кэшируем.

---

## Этап 1 — Каркас репозитория и дизайн-система

**Задачи**
1. git init в корне; .gitignore (xapk, tools/, .gradle, build/, keystore/); LICENSE (MIT), README.md (EN, краткий), CONTRIBUTING.md.
2. Справочные материалы (xapk, распакованное) → _reference/ (в .gitignore).
3. Модули: :app, :core:ui, :core:time, :core:alarm, :core:audio, :core:notify, :core:perm, :data, :sdk, :plugin-runtime, :plugin-codegen.
4. :core:ui — цветовая схема (тёмная/светлая), типографика, spacing-токены, AwakrTheme, базовые компоненты (AwakrCard, TimeDisplay, PrimaryButton).
5. Навигация (Navigation-Compose): home, editor/{id}, ring/{id}, settings, plugins, about.
6. Заглушки экранов с реальными токенами.

**Проверка:** на устройстве — главный экран в финальном визуальном языке; скриншот приложен к отчёту.

---

## Этап 2 — Данные и SDK плагинов

**Задачи**
1. Room: AlarmEntity, AlarmLogEntity, PluginStateEntity, DAO, БД версии 1, экспорт схем.
2. AlarmRepository (Flow-списки, CRUD), DataStore-обёртка PrefsRepository.
3. :sdk — AwakrPlugin, PluginManifest, Capability, MissionPlugin, SoundPackPlugin, ThemePlugin, LocalePackPlugin, SettingField, StringRes, @AwakrApi.
4. :plugin-codegen — KSP-процессор → GeneratedPluginRegistry (список фабрик по аннотации @AwakrPluginModule).
5. :plugin-runtime — PluginRegistry (фильтр по apiVersion, дедуп, состояние вкл/выкл, кэш), рендер SettingsSchema.
6. Тесты: реестр (дубликаты, apiVersion, enabled), DAO CRUD.

**Проверка:** ./gradlew test зелёный; в Settings → Плагины пустой список (плагинов ещё нет).

---

## Этап 3 — Движок будильников (критический)

**Задачи**
1. :core:time — NextOccurrence.next(alarm, now, zone) + тесты: обычные дни, дни недели, одноразовый, DST-дыра, DST-дубль, «только что прошло», days_mask=0.
2. :core:alarm — AlarmScheduler, AlarmManagerScheduler (setAlarmClock, snooze через setExactAndAllowWhileIdle), AlarmReceiver, RescheduleReceiver (boot/time/timezone/update/exact-alarm-permission), WorkManager-страховка.
3. :core:audio — AlarmPlayer (MediaPlayer, USAGE_ALARM, audio focus, ramp, вибро).
4. :core:notify — каналы, full-screen intent, «следующий будильник».
5. AlarmRingService (foreground mediaPlayback, wakelock с таймаутом).
6. RingActivity: showWhenLocked/turnScreenOn, immersive, блок back, fallback-миссия «удерживай 5 секунд», snooze с лимитом.
7. Тесты: сервис не «застревает» (fallback работает без плагинов).

**Проверка (обязательно на устройстве):** создать тестовый будильник → заблокировать телефон → ожидание → срабатывание, звук, экран, fallback-миссия, выключение.

---

## Этап 4 — Home и редактор

**Задачи**
1. Home: hero-карточка ближайшего (обратный отсчёт раз в секунду), список, свитчи, удаление с undo.
2. Editor: пикер времени, дни (чипы Mon..Sun), метка, звук (системный рингтон + превью), ramp, вибро, snooze + лимит, выбор миссии из реестра плагинов.
3. Валидация: без дней = одноразовый; предупреждение «нет разрешения».
4. Сохранение → перепланирование через AlarmScheduler.

**Проверка:** создание/редактирование/удаление на устройстве; будильник срабатывает по заданному.

---

## Этап 5 — Миссии (4 плагина) и экран плагинов

Порядок: mission-typing (проще всех) → mission-math → mission-shake → mission-qr (камера/разрешения — самое рискованное в конце).

**Задачи на каждую миссию**
1. build.gradle.kts модуля (зависит только от :sdk и :core:ui).
2. XxxPlugin + PluginManifest (id, capabilities, requiredPermissions, schema).
3. Editor-composable (настройка конфига) + Mission-composable (экран звонка) + validate().
4. Модульные тесты логики (генератор примеров, парсер фразы, счётчик тряски, сравнение payload).
5. Регистрация через KSP; появление в редакторе и в Settings → Плагины.

**QR-специфика:** CameraX Preview + ImageAnalysis, ZXing MultiFormatReader на YuvImage; mode=PRINT → генерация QR (QRCodeWriter) и сохранение PNG в Pictures/Awakr; обработка отказа в CAMERA.

**Проверка:** каждая миссия блокирует выключение; отключение плагина убирает его из редактора; отключение во время звонка → fallback + ERROR в логе.

---

## Этап 6 — Онбординг, локализация, About

**Задачи**
1. Онбординг разрешений: экраны со статусами (exact alarm / full-screen intent / уведомления / батарея), кнопки «Открыть настройки».
2. i18n: values/strings.xml (en) + :plugins:locale-ru; per-app locale; lint HardcodedText = error.
3. About: версия, MIT, список OSS-зависимостей, ссылка на репозиторий.
4. Пропущенные будильники: MISSED в логе + уведомление.
5. Динамическая тема (Material You) — тумблер.

**Проверка:** переключение en↔ru без перезапуска; lint чист; About корректен.

---

## Этап 7 — Стабилизация и релиз

**Задачи**
1. Прогон матрицы §5.5 спеки на Pixel 7 Pro (docs/manual-qa.md с отметками).
2. Фиксы по результатам + регрессионные тесты.
3. Keystore (keystore/awakr-release.jks, в .gitignore) → assembleRelease → установка.
4. CI: .github/workflows/android.yml (test + lint + assembleDebug; проверка отсутствия INTERNET).
5. README (EN) с инструкцией сборки с нуля, CONTRIBUTING («миссия за 1 PR»), docs/manual-qa.md.
6. Push в caendeith/awakr — **после разрешения**; тег v1.0.0.

**Проверка:** чек-лист §13 спеки — все 12 пунктов закрыты.

---

## Параллелизация (режим B)

После этапа 3 :sdk замораживается, и миссии этапа 5 можно делать параллельно:
- teammate 1: mission-typing + mission-math
- teammate 2: mission-shake
- teammate 3: mission-qr (самый долгий)
- Lead: Home/Editor/экран плагинов + ревью

Условие: у каждого teammate свой набор модулей (дизъюнктные пути), :sdk не меняется; после — Lead ревьюит diff и гоняет сборку/тесты.

## Что может пойти не так

| Риск | Реакция |
|---|---|
| Матрица версий не собирается | перебор по «Резерву», фиксация факта в спеке, +40 мин |
| adb не видит телефон | проверка USB-режима/отладки; при блокере — отдаю APK на ручную установку |
| Full-screen intent не показывается на Android 17 | диагностика через adb shell dumpsys notification; fallback: полноэкранное уведомление высокого приоритета |
| QR-камера ведёт себя иначе | отдельная отладка; при неудаче QR переносится в v2, остальные 3 миссии едут |

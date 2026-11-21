# Acceptance Criteria — StudioGhibliLauncher v1.0.0

## Автоматизована перевірка на хості (Build.ps1)
- [ ] `testDebugUnitTest`: усі unit-тести (каталог, налаштування, фізика та HUD мінігри, адаптивні сітки, оновлення, життєвий цикл, регресії фокусу й контекстного меню, Robolectric UI-рендери) проходять без помилок.
- [ ] `lintDebug`: 0 помилок Android Lint (`abortOnError true`).
- [ ] `assembleDebug`: зібрано `releases/GhibliValley-1.0.0-debug.apk`, перевірено `aapt dump badging` (`package: name='ua.ghibli.tv.valley'`, `versionCode='1'`, `versionName='1.0.0'`), записано `releases/build-manifest.json` із SHA-256.
- [ ] Візуальний огляд відрендерених PNG у `verification/native-ui/` (963x541, 1366x768, 1920x1080, Scenes, Spirits, Settings, Library, Ambient, Valley Runner, Context Menu): відсутність кліпінгу, накладань, білих прямокутників або артефактів чужих тем.
- [ ] Нульовий аудит запозичень: жодних згадок інших франшиз чи старих кольорів у `app/src/main`.

## Межі перевірки (потрібна ручна перевірка на фізичному Android TV)
Наступні пункти **не підтверджуються** Robolectric/Skia і мають бути перевірені окремо на реальному телевізорі:
- Реальна плавність GPU (FPS) анімацій частинок, кросфейду сцен, стрибків Сусуватарі та мінігри.
- Звучання динаміків телевізора, баланс гучності музики та ефектів, відсутність тріску при швидкій навігації.
- Відгук фізичного IR/Bluetooth TV-пульта (включно з довгим OK та перехопленням HOME / Google TV Accessibility redirect).
- Фактичне споживання RAM (PSS по всіх процесах у matched-сценаріях).

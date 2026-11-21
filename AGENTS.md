# StudioGhibliLauncher (`Ghibli · Вітер над долиною`)

Нативний Android TV лаунчер у стилі Studio Ghibli, побудований на Android Views + hardware-accelerated Custom Canvas.

## Ідентичність проєкту

- Назва: `Ghibli · Вітер над долиною`
- Package / namespace: `ua.ghibli.tv.launcher`
- Application ID: `ua.ghibli.tv.valley`
- Репозиторій оновлень: `oktirus/StudioGhibliLauncher`
- Основна гілка розробки: `ghibli-v1`

## Архітектурні інваріанти

1. **Повний native Android Views + Custom Canvas**: жодного WebView/Chromium.
2. **Видимий D-pad фокус = ціль OK**: cold HOME відкриває фокус на першій картці улюблених (або на «Усі застосунки», якщо список порожній); автоматичний фокус при вході не програє focus-звук.
3. **8 живописних краєвидів та 6 духів навігації**:
   - Краєвиди (`CatalogData.SCENES`): `totoro_forest`, `bathhouse_dusk`, `howl_meadow`, `kiki_coast`, `mononoke_cedars`, `laputa_sky`, `ponyo_cliff`, `winter_train`.
   - Духи фокусу (`CatalogData.TECHNIQUES`): `soot_sprites`, `calcifer`, `kodama`, `wind_petal`, `aetherium`, `paper_birds`.
   - У галереї «Духи та магія» (`SpiritsPanel`) усі 6 прев'ю статичні (не запускають 60 fps анімації).
4. **Чистий життєвий цикл і пам'ять**:
   - `onPause()` зупиняє музику, фонові частинки, фокус-анімації, мінігру й таймери.
   - `onTrimMemory()` очищує кеші (`clearCache()`) без термінального знищення (`release()`) активних пікерів.
   - Stale async-результати (декодування шпалер, іконок, інвентарю застосунків) відкидаються за поколіннями (`generation` / `token`).
5. **Межі доказів**: Robolectric / Skia-рендери перевіряють верстку, фокус і життєвий цикл на хості, але не замінюють перевірку на фізичному Android TV (GPU FPS, динаміки, реальний пульт, PSS RAM).

## Команди збірки та перевірки

```powershell
# Повна перевірка (unit tests + Robolectric UI renders + lint + debug APK)
pwsh -NoProfile -File Build.ps1

# Перезбірка асетів (шпалери, мініатюри, медальйони, пентатонічне аудіо)
python tools/build_assets.py
```

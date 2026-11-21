# Memory — StudioGhibliLauncher

## Архітектурні рішення

- Проєкт створено з нуля під пакетом `ua.ghibli.tv.launcher` / `applicationId = "ua.ghibli.tv.valley"`, використовуючи лише інженерні патерни нативного рендеру Android Views + Custom Canvas (без WebView і без запозичення коду чи стилістики з інших папок).
- Усі 8 сцен (`1920x1080` JPG + `420x236` WebP мініатюри) та 4 прозорі спрайти духів (`soot-idle`, `soot-jump`, `calcifer`, `kodama`) згенеровані спеціально для цього лаунчера в акварельній телевізійній стилістиці Studio Ghibli; решта 3 ефекти (`wind_petal`, `aetherium`, `paper_birds`) реалізовані процедурно на hardware Canvas у `SpiritRenderer.java`.
- Скрипт `tools/build_assets.py` використовує лише стандартну бібліотеку Python + Pillow (без NumPy), автоматично прибирає темні letterbox-смуги та синтезує 3 пентатонічні WAV-треки, 13 UI/фокус/підтверджувальних звуків і 6 SFX для мінігри.
- `Build.ps1` використовує ASCII staging у `$env:LOCALAPPDATA\GhibliLauncherBuild`, оскільки робочий шлях містить кирилицю.

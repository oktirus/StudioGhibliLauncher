# Memory — StudioGhibliLauncher

## Архітектурні рішення

- Проєкт створено з нуля під пакетом `ua.ghibli.tv.launcher` / `applicationId = "ua.ghibli.tv.valley"`, використовуючи лише інженерні патерни нативного рендеру Android Views + Custom Canvas (без WebView і без запозичення коду чи стилістики з інших папок).
- Усі 8 сцен (`1920x1080` JPG + `420x236` WebP мініатюри) та 4 прозорі спрайти духів (`soot-idle`, `soot-jump`, `calcifer`, `kodama`) згенеровані спеціально для цього лаунчера в стилістиці Studio Ghibli; решта 3 ефекти (`wind_petal`, `aetherium`, `paper_birds`) реалізовані процедурно на hardware Canvas у `SpiritRenderer.java`.
- **Аудіо:** замінено на 22 автентичні файли — 3 офіційні OST-композиції Joe Hisaishi (*Totoro*, *Spirited Away*, *Howl's Moving Castle* з Archive.org) + 19 натуральних UI/game SFX (Freesound CC0, без синтезованих біпів).
- **Сусуватарі:** перегенеровано єдиним style-locked 1×2 аркушем на chroma-key `#00FF00` (`chroma_key_green_with_despill` + `shared_scale` нормалізація в `tools/build_assets.py`), що повністю прибрало білі плями між руками й морфінг при стрибку; оформлено в переносний скіл `chroma-sprite-cutout`.
- `Build.ps1` використовує ASCII staging у `$env:LOCALAPPDATA\GhibliLauncherBuild`, оскільки робочий шлях містить кирилицю.

## Схвалений стиль іконок (v2 · Free-Form Studio Ghibli Cel Props)

- **Що НЕ зайшло:**
  - v0: 3D-дерев’яні RPG-рамки з кутовими листочками (занадто «АІшні»).
  - v1: плоскі приглушені гуашеві квадрати з подвійною рамкою (виглядали як тьмяні плоскі наліпки).
- **Що ЗАЙШЛО (схвалено користувачем на живому Lenovo TV):**
  - **Вільні вирізані 2D cel-предмети Studio Ghibli 1990-х** без жодних квадратних плиток/рамок/підкладок, із упевненим темно-сепійним контуром (`#241C18`), живими природними cel-кольорами, 2-тоновим затіненням і гострими cel-відблисками (повна специфікація: `docs/ghibli-art-style-guide.md`, узагальнена методика: `../KnowledgeBase/freeform-cel-prop-icons.md`).
  - **Генерація:** чистий веб-чат Gemini через Orca CLI (`orca tab create --url "https://gemini.google.com/app"`, завантаження в оригінальному розмірі `~2752×1536`), style-locked горизонтальні аркуші `1×3` або `1×4` із візуальним style-reference схваленого аркуша `art/originals/icon_sheets/ghibli_props_test3_v2.jpg`.
  - **Chroma-key за палітрою партії:**
    - `#FF00FF` (чиста маджента) + аналітична un-premultiplication у `chroma_key_magenta_with_despill()` — для іконок із зеленим, блакитним, жовтим, мідним, темно-синім; ідеально зберігає напівпрозоре скло з білими cel-відблисками (як у `Lampa`).
    - `#00FF00` (чистий зелений) + аналітичний `chroma_key_green_with_despill()` — для партій із червоними/рожевими/пурпуровими іконками (`YouTube`, `Netflix`, `Plex` тощо), щоб червоно-рожеві пігменти не втратили насиченість.

## Міні-гра «Політ крізь долину» (v3 · 3 герої з Тоторо, 4-кадровий біг Contact/Passing та безшовне оточення)

- **Безшовний фон без дзеркалення:** `ghibli-valley.webp` (`1920×540`) та `ghibli-ground.webp` (`1280×164` RGBA) збираються через косинусний wraparound crossfade (`make_horizontally_seamless_rgb` / `make_horizontally_seamless_ground` у `tools/build_assets.py`), без дзеркального шва по центру. Земля згенерована з `#FF00FF` зверху, тому травинки, конюшина й ромашки мають живий вирізаний силует поверх долини.
- **3 ігрові герої (Сусуватарі, Кодама, Тоторо) зі справжнім 4-кадровим чергуванням ніг:**
  - Чому попередній 3×2 цикл виглядав «із застряглою задньою ногою»: він мав лише два широкі кроки (`[0, 1, 2, 1]`) без проміжної фази **Passing** (перехрещення ніг під тулубом).
  - У v3.1 усі троє героїв мають справжній 4-кадровий цикл `[Contact A, Passing A, Contact B (протилежна нога й рука попереду), Passing B, Jump, Idle]` (`1536×256`) + 2-кадрове присідання `slide` (`512×256`) зі спільним `shared_scale` і вирівнюванням по базовій лінії стоп. Для Тоторо (`extract_totoro_4x2_alternating_sheet()` у `tools/build_assets.py`) збережено оригінальні голову, листочок, хвіст і кремовий животик (через маску `belly_sacred` з `MaxFilter(11)`), а обидві ноги й обидві руки тепер повністю чергуються по колу (`Contact A → Passing A → Contact B → Passing B`) із темпом `8.8` і підскоком `-6.5` у `RunnerPhysics.poseFor()`.
  - Перемикання героїв на TV: стрілки `←/→` (`cycleHero(±1)`) або вкладки `Сусуватарі / Кодама / Тоторо`; кожен герой має окремий рекорд (`runner_best_soot`, `runner_best_kodama`, `runner_best_totoro`).
- **Ручно намальовані cel-перешкоди:** 1×4 аркуш на `#FF00FF` (замшілий пеньок, кам'яний ліхтар Торо, 2-кадровий паперовий птах оріґамі), виділений через 2D BFS connected components у `extract_runner_obstacles()` (захист від перекриття X-проєкцій даху ліхтаря й коріння пенька).
- **High-DPI фікс хедера:** у `ValleyRunnerView.renderHeader()` зміщення підписів `Відстань` / `Рекорд` переведено з сирих px на `GhibliTheme.dp(...)`, що прибрало наїзд тексту на цифри на живому Lenovo TV.


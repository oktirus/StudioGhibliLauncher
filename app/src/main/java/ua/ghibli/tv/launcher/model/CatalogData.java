package ua.ghibli.tv.launcher.model;

import java.util.*;

/**
 * Curated Studio Ghibli scenes, poetic quotes, spirit focus effects,
 * original ambient tracks, focus cues, and TV app metadata.
 */
public class CatalogData {

    public static int parseColor(String hex) {
        if (hex == null || !hex.startsWith("#")) return 0xFFFFFFFF;
        String clean = hex.substring(1);
        if (clean.length() == 6) {
            return 0xFF000000 | (int) Long.parseLong(clean, 16);
        } else if (clean.length() == 8) {
            return (int) Long.parseLong(clean, 16);
        }
        return 0xFFFFFFFF;
    }

    public static final String DEFAULT_SCENE_ID = "totoro_forest";
    public static final String DEFAULT_TECHNIQUE_ID = "soot_sprites";
    public static final String DEFAULT_MUSIC_ID = "meadow_morning";
    public static final String DEFAULT_HIGHLIGHT_SOUND_ID = "soot_sprites";

    public static final List<Scene> SCENES = Arrays.asList(
        new Scene("totoro_forest", "Камфорний ліс", "Тиша під кроною", "Мій сусід Тоторо · літній полудень", "#94c684",
            false, "Подих<br><em>лісу.</em>", "Старе камфорне дерево береже спокій долини.<br>У тіні листя завжди знайдеться місце для дива.",
            "scenes/scene-totoro_forest.jpg", "scenes/thumb-totoro_forest.webp", "leaf",
            new int[]{parseColor("#b9d89c"), parseColor("#e8c878")}),
        new Scene("bathhouse_dusk", "Купальні у сутінках", "Світло ліхтарів", "Віднесені привидами · міст над рікою", "#e8b858",
            false, "Вогні<br><em>сутінків.</em>", "Коли вечір опускається на воду, спалахують ліхтарі.<br>Кожна подорож повертає до справжнього імені.",
            "scenes/scene-bathhouse_dusk.jpg", "scenes/thumb-bathhouse_dusk.webp", "ember",
            new int[]{parseColor("#f2c879"), parseColor("#d96b4b")}),
        new Scene("howl_meadow", "Луг мандрівного замку", "Серед хмар і трав", "Мандрівний замок · альпійська долина", "#b8d69a",
            false, "Вітер<br><em>свободи.</em>", "Над квітучими пагорбами пливуть літні хмари.<br>Серце завжди знає дорогу, куди рушати далі.",
            "scenes/scene-howl_meadow.jpg", "scenes/thumb-howl_meadow.webp", "petal",
            new int[]{parseColor("#f6eed8"), parseColor("#a8d08d")}),
        new Scene("kiki_coast", "Приморська пекарня", "Назустріч морю", "Служба доставки Кікі · політ над затокою", "#d96b4b",
            false, "Політ<br><em>над морем.</em>", "Солоний вітер здіймає вітрила й настрій.<br>Свій шлях починається з першого сміливого кроку.",
            "scenes/scene-kiki_coast.jpg", "scenes/thumb-kiki_coast.webp", "wind",
            new int[]{parseColor("#f6eed8"), parseColor("#8fc7df")}),
        new Scene("mononoke_cedars", "Праліс кедрів", "Голос джерела", "Принцеса Мононоке · духи кодама", "#8fd4a8",
            true, "Дух<br><em>пралісу.</em>", "Крізь древні кедри пробивається золоте проміння.<br>Ліс живий, доки його слухають із повагою.",
            "scenes/scene-mononoke_cedars.jpg", "scenes/thumb-mononoke_cedars.webp", "spore",
            new int[]{parseColor("#c6f2d4"), parseColor("#8fd4a8")}),
        new Scene("laputa_sky", "Острів у хмарах", "Сад високо в небі", "Небесний замок Лапута · небесний сад", "#8dc6e8",
            true, "Сад<br><em>у хмарах.</em>", "Серед білих веж із хмар квітне давній сад.<br>Навіть камінь пам’ятає тепло добрих рук.",
            "scenes/scene-laputa_sky.jpg", "scenes/thumb-laputa_sky.webp", "petal",
            new int[]{parseColor("#f6eed8"), parseColor("#b5daf0")}),
        new Scene("ponyo_cliff", "Будинок на скелі", "Берег теплої хвилі", "Рибка Поньо · бухта на заході", "#6dc2be",
            true, "Світло<br><em>на скелі.</em>", "Будиночок над хвилями зустрічає кожен світанок.<br>Тепле вікно на скелі завжди чекає на тебе.",
            "scenes/scene-ponyo_cliff.jpg", "scenes/thumb-ponyo_cliff.webp", "wind",
            new int[]{parseColor("#d8f0ec"), parseColor("#e8b858")}),
        new Scene("winter_train", "Потяг крізь тишу", "Вода й сніжинки", "Віднесені привидами · зимові сутінки", "#b8cde0",
            true, "Шлях<br><em>по воді.</em>", "Самотній вагон ковзає дзеркалом тихої води.<br>У спокої вечора народжуються найтепліші спогади.",
            "scenes/scene-winter_train.jpg", "scenes/thumb-winter_train.webp", "snow",
            new int[]{parseColor("#f0f5fa"), parseColor("#f2c879")})
    );

    public static final Map<String, Quote> QUOTES = new HashMap<>();
    static {
        QUOTES.put("totoro_forest", new Quote("totoro", "Мій сусід Тоторо", "Дерева й люди колись були найкращими друзями."));
        QUOTES.put("bathhouse_dusk", new Quote("chihiro", "Віднесені привидами", "Те, що сталося одного разу, ніколи не забувається назавжди."));
        QUOTES.put("howl_meadow", new Quote("sophie", "Мандрівний замок", "Серце — важкий тягар, але саме воно зігріває дорогу."));
        QUOTES.put("kiki_coast", new Quote("kiki", "Служба доставки Кікі", "Ми самі знаходимо своє натхнення — іноді воно поруч із вітром."));
        QUOTES.put("mononoke_cedars", new Quote("ashitaka", "Принцеса Мононоке", "Дивись на світ очима, не затьмареними гнівом."));
        QUOTES.put("laputa_sky", new Quote("sheeta", "Небесний замок Лапута", "Хай якою великою буде сила, без землі під ногами не вижити."));
        QUOTES.put("ponyo_cliff", new Quote("sosuke", "Рибка Поньо", "Не хвилюйся, я завжди берегтиму світло на нашому березі."));
        QUOTES.put("winter_train", new Quote("train", "Потяг над водою", "Навіть у тиші між станціями триває дивовижна подорож."));
    }

    public static final List<Technique> TECHNIQUES = Arrays.asList(
        new Technique(DEFAULT_TECHNIQUE_ID, "Сусуватарі", "Пилові духи · компейто", parseColor("#e8b858"), "soot_sprites",
            "Грайливі пилові духи перестрибують між іконками, несучи зірочки компейто."),
        new Technique("calcifer", "Полум’я Кальцифера", "Мандрівний замок", parseColor("#f29d4b"), "calcifer",
            "Теплий вінок живого полум’я вогнища з легкими іскрами навколо картки."),
        new Technique("kodama", "Сяйво Кодама", "Принцеса Мононоке", parseColor("#a4e8be"), "kodama",
            "Лагідне смарагдове сяйво лісових духів та світлячків пралісу."),
        new Technique("wind_petal", "Вітер долини", "Літній бриз і пелюстки", parseColor("#94c684"), "wind_petal",
            "М’які потоки весняного вітру з пелюстками та листям камфори."),
        new Technique("aetherium", "Кристал Лапути", "Небесний камінь", parseColor("#8fd8ff"), "aetherium",
            "Сяюче кільце давнього небесного кристала з золотими лініями."),
        new Technique("paper_birds", "Паперові птахи", "Віднесені привидами", parseColor("#f6eed8"), "paper_birds",
            "Зграйка білих паперових пташок оріґамі, що кружляють навколо вибору.")
    );

    public static final List<MusicTrack> MUSIC_TRACKS = Arrays.asList(
        new MusicTrack("meadow_morning", "Ранкова долина (скринька)", "audio/music-meadow_morning.wav"),
        new MusicTrack("lantern_dusk", "Вогні сутінків (калімба)", "audio/music-lantern_dusk.wav"),
        new MusicTrack("sky_garden", "Небесний сад (флейта)", "audio/music-sky_garden.wav")
    );

    public static final List<HighlightSound> HIGHLIGHT_SOUNDS = Arrays.asList(
        new HighlightSound("soot_sprites", "Дзвіночок компейто", "audio/ghibli-soot_sprites-focus.wav"),
        new HighlightSound("calcifer", "Іскра вогнища", "audio/ghibli-calcifer-focus.wav"),
        new HighlightSound("kodama", "Шепіт кодама", "audio/ghibli-kodama-focus.wav"),
        new HighlightSound("wind_petal", "Подих вітру", "audio/ghibli-wind_petal-focus.wav"),
        new HighlightSound("aetherium", "Резонанс кристала", "audio/ghibli-aetherium-focus.wav"),
        new HighlightSound("paper_birds", "Помах паперу", "audio/ghibli-paper_birds-focus.wav")
    );

    public static class AppDefinition {
        public final String pkg;
        public final String name;
        public final String kind;
        public final String description;
        public final List<String> aliases;

        public AppDefinition(String pkg, String name, String kind, String description, String... aliases) {
            this.pkg = pkg;
            this.name = name;
            this.kind = kind;
            this.description = description;
            this.aliases = Arrays.asList(aliases);
        }
    }

    public static final List<AppDefinition> KNOWN_APPS = Arrays.asList(
        new AppDefinition("preview.smarttube", "SmartTube", "smarttube", "Відео без поспіху", "com.teamsmart.videomanager.tv", "com.liskovsoft.videomanager"),
        new AppDefinition("preview.lampa", "Lampa", "lampa", "Кіно на цей вечір", "top.rootu.lampa", "top.rootintech.lampa"),
        new AppDefinition("preview.uakino", "UAKino", "uakino", "Кіно українською", "ua.kino"),
        new AppDefinition("preview.netflix", "Netflix", "netflix", "Ще одна історія"),
        new AppDefinition("preview.kodi", "Kodi", "kodi", "Медіатека", "org.xbmc.kodi"),
        new AppDefinition("preview.spotify", "Spotify", "spotify", "Музика та подкасти"),
        new AppDefinition("preview.crunchyroll", "Crunchyroll", "crunchyroll", "Світ аніме"),
        new AppDefinition("preview.vlc", "VLC", "vlc", "Відеоплеєр"),
        new AppDefinition("preview.plex", "Plex", "plex", "Домашнє кіно"),
        new AppDefinition("preview.youtube", "YouTube", "youtube", "Відео та трансляції", "com.google.android.youtube.tv"),
        new AppDefinition("preview.tvbro", "TV Bro", "tvbro", "Браузер для телевізора", "com.phlox.tvwebbrowser", "browser"),
        new AppDefinition("preview.kyivstar", "Київстар ТБ", "kyivstar", "Телебачення та кіно", "kyivstar", "київстар", "ua.kyivstar.tv", "com.kyivstar.tv.androidtv"),
        new AppDefinition("preview.megogo", "MEGOGO", "megogo", "Кіно, канали та спорт", "com.megogo.application", "megogo"),
        new AppDefinition("preview.sweettv", "SWEET.TV", "sweettv", "Канали та кіно українською", "sweet.tv", "sweet tv", "tv.sweet"),
        new AppDefinition("preview.primevideo", "Prime Video", "primevideo", "Фільми та серіали", "prime video", "com.amazon.amazonvideo.livingroom"),
        new AppDefinition("preview.disneyplus", "Disney+", "disneyplus", "Історії та анімація", "disney", "com.disney.disneyplus"),
        new AppDefinition("preview.jellyfin", "Jellyfin", "jellyfin", "Особиста медіатека", "org.jellyfin.androidtv", "jellyfin"),
        new AppDefinition("preview.sendfiles", "Send Files to TV", "sendfiles", "Передавання файлів", "send files", "com.yablio.sendfilestotv"),
        new AppDefinition("preview.xplore", "X-plore", "xplore", "Файловий менеджер", "x-plore", "com.lonelycatgames.xplore", "com.lonelycatgames.Xplore", "com.android.documentsui", "файли", "files", "documents", "document")
    );

    public static String matchKind(String pkg, String label) {
        String query = (label + " " + pkg).toLowerCase(Locale.ROOT);
        for (int i = KNOWN_APPS.size() - 1; i >= 0; i--) {
            AppDefinition def = KNOWN_APPS.get(i);
            if (query.contains(def.kind.toLowerCase(Locale.ROOT))) return def.kind;
            for (String alias : def.aliases) {
                if (query.contains(alias.toLowerCase(Locale.ROOT))) return def.kind;
            }
        }
        return "";
    }

    public static String matchDescription(String pkg, String label) {
        String query = (label + " " + pkg).toLowerCase(Locale.ROOT);
        for (int i = KNOWN_APPS.size() - 1; i >= 0; i--) {
            AppDefinition def = KNOWN_APPS.get(i);
            if (query.contains(def.kind.toLowerCase(Locale.ROOT))) return def.description;
            for (String alias : def.aliases) {
                if (query.contains(alias.toLowerCase(Locale.ROOT))) return def.description;
            }
        }
        return "Встановлений застосунок";
    }
}

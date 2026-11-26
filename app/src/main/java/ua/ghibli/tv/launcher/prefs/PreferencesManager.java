package ua.ghibli.tv.launcher.prefs;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;

import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.HighlightSound;
import ua.ghibli.tv.launcher.model.MusicTrack;
import ua.ghibli.tv.launcher.model.Scene;
import ua.ghibli.tv.launcher.model.Technique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PreferencesManager {
    public static final String PREF_NAME = "ghibli_valley_preferences_v1";

    public static final String KEY_SCENE = "scene";
    public static final String KEY_AUDIO = "audio";
    public static final String KEY_AMBIENCE = "ambience";
    public static final String KEY_VOLUME = "volume";
    public static final String KEY_MUSIC_VOLUME = "musicVolume";
    public static final String KEY_MUSIC = "music";
    public static final String KEY_MUSIC_ROTATION = "musicRotation";
    public static final String KEY_SHOW_RESOURCES = "showResources";
    public static final String KEY_TECHNIQUE = "technique";
    public static final String KEY_HIGHLIGHT_SOUND = "highlightSound";
    public static final String KEY_MOTION = "motion";
    public static final String KEY_ROTATION = "rotation";
    public static final String KEY_IDLE = "idle";
    public static final String KEY_PINS = "pins";

    public static final String KEY_RUNNER_SOOT = "runner_best_soot";
    public static final String KEY_RUNNER_KODAMA = "runner_best_kodama";
    public static final String KEY_RUNNER_TOTORO = "runner_best_totoro";
    public static final String KEY_RUNNER_HERO = "runner_last_hero";

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public PreferencesManager(SharedPreferences sharedPreferences) {
        this.prefs = sharedPreferences;
    }

    public String getScene() {
        String val = prefs.getString(KEY_SCENE, CatalogData.DEFAULT_SCENE_ID);
        if (val == null || !isValidScene(val)) return CatalogData.DEFAULT_SCENE_ID;
        return val;
    }

    private boolean isValidScene(String id) {
        for (Scene sc : CatalogData.SCENES) {
            if (sc.getId().equals(id)) return true;
        }
        return false;
    }

    public void setScene(String scene) {
        prefs.edit().putString(KEY_SCENE, scene).apply();
    }

    public boolean isAudioEnabled() {
        return prefs.getBoolean(KEY_AUDIO, true);
    }

    public void setAudioEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUDIO, enabled).apply();
    }

    public boolean isAmbienceEnabled() {
        return prefs.getBoolean(KEY_AMBIENCE, true);
    }

    public void setAmbienceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AMBIENCE, enabled).apply();
    }

    public float getVolume() {
        return prefs.getFloat(KEY_VOLUME, 0.45f);
    }

    public void setVolume(float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        prefs.edit().putFloat(KEY_VOLUME, clamped).apply();
    }

    public float getMusicVolume() {
        return prefs.getFloat(KEY_MUSIC_VOLUME, 0.25f);
    }

    public void setMusicVolume(float volume) {
        float clamped = Math.max(0f, Math.min(1f, volume));
        prefs.edit().putFloat(KEY_MUSIC_VOLUME, clamped).apply();
    }

    public String getMusicTrack() {
        String val = prefs.getString(KEY_MUSIC, CatalogData.DEFAULT_MUSIC_ID);
        for (MusicTrack mt : CatalogData.MUSIC_TRACKS) {
            if (mt.getId().equals(val)) return val;
        }
        return CatalogData.DEFAULT_MUSIC_ID;
    }

    public void setMusicTrack(String trackId) {
        prefs.edit().putString(KEY_MUSIC, trackId).apply();
    }

    public boolean isMusicRotationEnabled() {
        return prefs.getBoolean(KEY_MUSIC_ROTATION, false);
    }

    public void setMusicRotationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MUSIC_ROTATION, enabled).apply();
    }

    public boolean shouldShowResources() {
        return prefs.getBoolean(KEY_SHOW_RESOURCES, false);
    }

    public void setShowResources(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_RESOURCES, show).apply();
    }

    public String getTechnique() {
        String val = prefs.getString(KEY_TECHNIQUE, CatalogData.DEFAULT_TECHNIQUE_ID);
        for (Technique t : CatalogData.TECHNIQUES) {
            if (t.getId().equals(val)) return val;
        }
        return CatalogData.DEFAULT_TECHNIQUE_ID;
    }

    public void setTechnique(String technique) {
        prefs.edit().putString(KEY_TECHNIQUE, technique).apply();
    }

    public String getHighlightSound() {
        String val = prefs.getString(KEY_HIGHLIGHT_SOUND, CatalogData.DEFAULT_HIGHLIGHT_SOUND_ID);
        for (HighlightSound h : CatalogData.HIGHLIGHT_SOUNDS) {
            if (h.getId().equals(val)) return val;
        }
        return CatalogData.DEFAULT_HIGHLIGHT_SOUND_ID;
    }

    public void setHighlightSound(String sound) {
        prefs.edit().putString(KEY_HIGHLIGHT_SOUND, sound).apply();
    }

    public boolean isMotionEnabled() {
        return prefs.getBoolean(KEY_MOTION, true);
    }

    public void setMotionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_MOTION, enabled).apply();
    }

    public int getRotationSeconds() {
        int r = prefs.getInt(KEY_ROTATION, 60);
        if (r != 0 && r != 60 && r != 180 && r != 600) r = 0;
        return r;
    }

    public void setRotationSeconds(int seconds) {
        prefs.edit().putInt(KEY_ROTATION, seconds).apply();
    }

    public int getIdleSeconds() {
        int i = prefs.getInt(KEY_IDLE, 900);
        if (i != 0 && i != 300 && i != 900) i = 0;
        return i;
    }

    public void setIdleSeconds(int seconds) {
        prefs.edit().putInt(KEY_IDLE, seconds).apply();
    }

    public List<String> getPins() {
        String raw = prefs.getString(KEY_PINS, null);
        if (raw == null) return null;
        try {
            JSONArray arr = new JSONArray(raw);
            List<String> list = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.getString(i));
            }
            return list;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    public void setPins(List<String> pins) {
        if (pins == null) {
            prefs.edit().remove(KEY_PINS).apply();
            return;
        }
        JSONArray arr = new JSONArray();
        for (String p : pins) {
            arr.put(p);
        }
        prefs.edit().putString(KEY_PINS, arr.toString()).apply();
    }

    public int getRunnerBest(String hero) {
        if ("kodama".equals(hero)) {
            return prefs.getInt(KEY_RUNNER_KODAMA, 0);
        }
        if ("totoro".equals(hero)) {
            return prefs.getInt(KEY_RUNNER_TOTORO, 0);
        }
        return prefs.getInt(KEY_RUNNER_SOOT, 0);
    }

    public void setRunnerBest(String hero, int score) {
        String key = "kodama".equals(hero) ? KEY_RUNNER_KODAMA
                : "totoro".equals(hero) ? KEY_RUNNER_TOTORO
                : KEY_RUNNER_SOOT;
        int current = prefs.getInt(key, 0);
        if (score > current) {
            prefs.edit().putInt(key, score).apply();
        }
    }

    public String getRunnerHero() {
        String hero = prefs.getString(KEY_RUNNER_HERO, "soot");
        if ("kodama".equals(hero) || "totoro".equals(hero)) return hero;
        return "soot";
    }

    public void setRunnerHero(String hero) {
        String safe = ("kodama".equals(hero) || "totoro".equals(hero)) ? hero : "soot";
        prefs.edit().putString(KEY_RUNNER_HERO, safe).apply();
    }
}

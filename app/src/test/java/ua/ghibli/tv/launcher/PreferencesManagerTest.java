package ua.ghibli.tv.launcher;

import android.content.SharedPreferences;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PreferencesManagerTest {

    private static class MockSharedPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public String getString(String key, String defValue) {
            Object v = values.get(key);
            return v instanceof String ? (String) v : defValue;
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return defValues; }
        @Override public int getInt(String key, int defValue) {
            Object v = values.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }
        @Override public long getLong(String key, long defValue) { return defValue; }
        @Override public float getFloat(String key, float defValue) {
            Object v = values.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = values.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { return new MockEditor(this); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}

        private static class MockEditor implements Editor {
            private final MockSharedPreferences parent;
            private final Map<String, Object> temp = new HashMap<>();
            private final Set<String> removed = new HashSet<>();

            MockEditor(MockSharedPreferences parent) { this.parent = parent; }
            @Override public Editor putString(String key, String value) { temp.put(key, value); return this; }
            @Override public Editor putStringSet(String key, Set<String> values) { return this; }
            @Override public Editor putInt(String key, int value) { temp.put(key, value); return this; }
            @Override public Editor putLong(String key, long value) { return this; }
            @Override public Editor putFloat(String key, float value) { temp.put(key, value); return this; }
            @Override public Editor putBoolean(String key, boolean value) { temp.put(key, value); return this; }
            @Override public Editor remove(String key) { removed.add(key); return this; }
            @Override public Editor clear() { temp.clear(); removed.addAll(parent.values.keySet()); return this; }
            @Override public boolean commit() { apply(); return true; }
            @Override public void apply() {
                for (String r : removed) parent.values.remove(r);
                parent.values.putAll(temp);
            }
        }
    }

    private PreferencesManager pm;

    @Before
    public void setUp() {
        pm = new PreferencesManager(new MockSharedPreferences());
    }

    @Test
    public void testFreshInstallDefaults() {
        Assert.assertEquals(CatalogData.DEFAULT_SCENE_ID, pm.getScene());
        Assert.assertTrue(pm.isAudioEnabled());
        Assert.assertTrue(pm.isAmbienceEnabled());
        Assert.assertEquals(0.45f, pm.getVolume(), 0.001f);
        Assert.assertEquals(0.25f, pm.getMusicVolume(), 0.001f);
        Assert.assertEquals(CatalogData.DEFAULT_MUSIC_ID, pm.getMusicTrack());
        Assert.assertEquals(CatalogData.DEFAULT_TECHNIQUE_ID, pm.getTechnique());
        Assert.assertEquals(CatalogData.DEFAULT_HIGHLIGHT_SOUND_ID, pm.getHighlightSound());
        Assert.assertTrue(pm.isMotionEnabled());
        Assert.assertEquals(60, pm.getRotationSeconds());
        Assert.assertEquals(900, pm.getIdleSeconds());
        Assert.assertNull(pm.getPins());
        Assert.assertEquals("soot", pm.getRunnerHero());
    }

    @Test
    public void testSavedSettingsSurviveReopen() {
        MockSharedPreferences saved = new MockSharedPreferences();
        PreferencesManager previous = new PreferencesManager(saved);
        previous.setMotionEnabled(false);
        previous.setAudioEnabled(false);
        previous.setAmbienceEnabled(false);
        previous.setRotationSeconds(0);
        previous.setIdleSeconds(300);
        previous.setTechnique("calcifer");
        previous.setScene("laputa_sky");
        previous.setRunnerHero("kodama");
        previous.setRunnerBest("kodama", 420);
        Map<String, ?> before = saved.getAll();

        PreferencesManager updated = new PreferencesManager(saved);
        Assert.assertFalse(updated.isMotionEnabled());
        Assert.assertFalse(updated.isAudioEnabled());
        Assert.assertFalse(updated.isAmbienceEnabled());
        Assert.assertEquals(0, updated.getRotationSeconds());
        Assert.assertEquals(300, updated.getIdleSeconds());
        Assert.assertEquals("calcifer", updated.getTechnique());
        Assert.assertEquals("laputa_sky", updated.getScene());
        Assert.assertEquals("kodama", updated.getRunnerHero());
        Assert.assertEquals(420, updated.getRunnerBest("kodama"));
        Assert.assertEquals(before, saved.getAll());
    }

    @Test
    public void testImportFromJsonClampsAndValidates() {
        String json = "{" +
                "\"scene\":\"kiki_coast\"," +
                "\"audio\":true," +
                "\"ambience\":false," +
                "\"volume\":1.8," +
                "\"musicVolume\":-0.2," +
                "\"music\":\"lantern_dusk\"," +
                "\"technique\":\"kodama\"," +
                "\"highlightSound\":\"aetherium\"," +
                "\"motion\":false," +
                "\"rotation\":180," +
                "\"idle\":300," +
                "\"pins\":[\"pkg.a\",\"pkg.b\"]" +
                "}";
        Assert.assertTrue(pm.importFromJson(json));
        Assert.assertEquals("kiki_coast", pm.getScene());
        Assert.assertTrue(pm.isAudioEnabled());
        Assert.assertFalse(pm.isAmbienceEnabled());
        Assert.assertEquals(1.0f, pm.getVolume(), 0.001f);
        Assert.assertEquals(0.0f, pm.getMusicVolume(), 0.001f);
        Assert.assertEquals("lantern_dusk", pm.getMusicTrack());
        Assert.assertEquals("kodama", pm.getTechnique());
        Assert.assertEquals("aetherium", pm.getHighlightSound());
        Assert.assertFalse(pm.isMotionEnabled());
        Assert.assertEquals(180, pm.getRotationSeconds());
        Assert.assertEquals(300, pm.getIdleSeconds());
        List<String> pins = pm.getPins();
        Assert.assertNotNull(pins);
        Assert.assertEquals(List.of("pkg.a", "pkg.b"), pins);
    }

    @Test
    public void testInvalidIdsFallBackToDefaults() {
        pm.setScene("nonexistent");
        pm.setTechnique("nonexistent");
        pm.setMusicTrack("nonexistent");
        pm.setHighlightSound("nonexistent");
        pm.setRunnerHero("unknown");
        Assert.assertEquals(CatalogData.DEFAULT_SCENE_ID, pm.getScene());
        Assert.assertEquals(CatalogData.DEFAULT_TECHNIQUE_ID, pm.getTechnique());
        Assert.assertEquals(CatalogData.DEFAULT_MUSIC_ID, pm.getMusicTrack());
        Assert.assertEquals(CatalogData.DEFAULT_HIGHLIGHT_SOUND_ID, pm.getHighlightSound());
        Assert.assertEquals("soot", pm.getRunnerHero());
    }
}

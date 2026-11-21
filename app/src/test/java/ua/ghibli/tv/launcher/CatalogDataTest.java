package ua.ghibli.tv.launcher;

import org.junit.Assert;
import org.junit.Test;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.Scene;
import ua.ghibli.tv.launcher.model.Technique;

import java.util.HashSet;
import java.util.Set;

public class CatalogDataTest {

    @Test
    public void testEightGhibliScenes() {
        Assert.assertEquals(8, CatalogData.SCENES.size());
        Set<String> sceneIds = new HashSet<>();
        for (Scene s : CatalogData.SCENES) {
            sceneIds.add(s.getId());
            Assert.assertNotNull(s.getName());
            Assert.assertNotNull(s.getImageAsset());
            Assert.assertNotNull(s.getThumbAsset());
            Assert.assertNotNull("Кожна сцена повинна мати цитату", CatalogData.QUOTES.get(s.getId()));
        }
        Assert.assertEquals(8, sceneIds.size());
        Assert.assertTrue(sceneIds.contains("totoro_forest"));
        Assert.assertTrue(sceneIds.contains("bathhouse_dusk"));
        Assert.assertTrue(sceneIds.contains("howl_meadow"));
        Assert.assertTrue(sceneIds.contains("kiki_coast"));
        Assert.assertTrue(sceneIds.contains("mononoke_cedars"));
        Assert.assertTrue(sceneIds.contains("laputa_sky"));
        Assert.assertTrue(sceneIds.contains("ponyo_cliff"));
        Assert.assertTrue(sceneIds.contains("winter_train"));
    }

    @Test
    public void testSixGhibliSpirits() {
        Assert.assertEquals(6, CatalogData.TECHNIQUES.size());
        Assert.assertEquals("soot_sprites", CatalogData.TECHNIQUES.get(0).getId());
        Assert.assertEquals("calcifer", CatalogData.TECHNIQUES.get(1).getId());
        Assert.assertEquals("kodama", CatalogData.TECHNIQUES.get(2).getId());
        Set<String> ids = new HashSet<>();
        for (Technique t : CatalogData.TECHNIQUES) {
            ids.add(t.getId());
            Assert.assertNotNull(t.getName());
            Assert.assertNotNull(t.getCue());
            Assert.assertNotNull(t.getDescription());
        }
        String[] required = new String[]{
                "soot_sprites", "calcifer", "kodama", "wind_petal", "aetherium", "paper_birds"
        };
        for (String req : required) {
            Assert.assertTrue("Дух " + req + " має бути присутній", ids.contains(req));
        }
    }

    @Test
    public void testMusicTracksAndHighlightSounds() {
        Assert.assertEquals(3, CatalogData.MUSIC_TRACKS.size());
        Assert.assertEquals(6, CatalogData.HIGHLIGHT_SOUNDS.size());
    }

    @Test
    public void testAppAliasMatching() {
        Assert.assertEquals("youtube", CatalogData.matchKind("com.google.android.youtube.tv", "YouTube on TV"));
        Assert.assertEquals("smarttube", CatalogData.matchKind("com.teamsmart.videomanager.tv", "SmartTube"));
        Assert.assertEquals("lampa", CatalogData.matchKind("top.rootintech.lampa", "Lampa"));
        Assert.assertEquals("uakino", CatalogData.matchKind("ua.kino.client", "UAKino"));
        Assert.assertEquals("tvbro", CatalogData.matchKind("com.phlox.tvwebbrowser", "Browser"));
        Assert.assertEquals("kyivstar", CatalogData.matchKind("ua.kyivstar.tv", "Київстар ТБ"));
        Assert.assertEquals("sweettv", CatalogData.matchKind("tv.sweet.player", "SWEET.TV"));
        Assert.assertEquals("primevideo", CatalogData.matchKind("com.amazon.amazonvideo.livingroom", "Prime Video"));
    }
}

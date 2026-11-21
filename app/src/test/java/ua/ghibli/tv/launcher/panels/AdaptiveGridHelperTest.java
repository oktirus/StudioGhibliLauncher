package ua.ghibli.tv.launcher.panels;

import org.junit.Assert;
import org.junit.Test;

public class AdaptiveGridHelperTest {

    @Test
    public void compactHighDensityViewportFitsEvenBelowPreferredMinimums() {
        for (int width : new int[]{180, 240, 294, 390, 768, 1366, 1920}) {
            AdaptiveGridHelper.LayoutResult result = AdaptiveGridHelper.compute(width, 360, 480, 18, 2, 8);
            Assert.assertTrue(result.columnCount * (result.cardWidthPx + 36) <= width);
            Assert.assertTrue(result.cardWidthPx > 0);
        }
    }

    @Test
    public void testComputeFallbackOnNonPositiveWidth() {
        AdaptiveGridHelper.LayoutResult res = AdaptiveGridHelper.compute(0, 150, 250, 10, 2, 6);
        Assert.assertEquals(2, res.columnCount);
        Assert.assertEquals(150, res.cardWidthPx);
    }

    @Test
    public void testComputeScenesAndSpiritsGridAcrossResolutions() {
        AdaptiveGridHelper.LayoutResult r390 = AdaptiveGridHelper.compute(390, 180, 320, 6, 1, 6);
        Assert.assertEquals(2, r390.columnCount);
        Assert.assertEquals(183, r390.cardWidthPx);

        AdaptiveGridHelper.LayoutResult r1280 = AdaptiveGridHelper.compute(1280, 180, 320, 6, 1, 6);
        Assert.assertEquals(6, r1280.columnCount);
        Assert.assertEquals(201, r1280.cardWidthPx);

        AdaptiveGridHelper.LayoutResult r960 = AdaptiveGridHelper.compute(960, 240, 420, 8, 1, 4);
        Assert.assertEquals(3, r960.columnCount);
        Assert.assertEquals(304, r960.cardWidthPx);
    }
}

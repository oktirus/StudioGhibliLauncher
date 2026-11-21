package ua.ghibli.tv.launcher.game;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RunnerHudLayoutTest {

    private RunnerHudLayout hud;

    @Before
    public void setUp() {
        hud = new RunnerHudLayout();
    }

    @Test
    public void testPauseCenterResolvesToPauseAt1366And1920() {
        for (int[] dim : new int[][]{{1366, 768}, {1920, 1080}}) {
            int w = dim[0], h = dim[1];
            hud.update(w, h);
            float pauseCenter = hud.pauseBounds.centerX();
            Assert.assertEquals(RunnerHudLayout.Action.PAUSE_TOGGLE, hud.resolveAction(pauseCenter, 40f, h));
            Assert.assertEquals(RunnerHudLayout.Action.EXIT, hud.resolveAction(hud.exitBounds.centerX(), 40f, h));
        }
    }

    @Test
    public void testBoundariesAndGapsAt963() {
        int w = 963, h = 540;
        hud.update(w, h);
        Assert.assertEquals(RunnerHudLayout.Action.EXIT, hud.resolveAction(hud.exitBounds.left, 40f, h));
        Assert.assertEquals(RunnerHudLayout.Action.EXIT, hud.resolveAction(hud.exitBounds.right, 40f, h));
        Assert.assertEquals(RunnerHudLayout.Action.PAUSE_TOGGLE, hud.resolveAction(hud.pauseBounds.left, 40f, h));
        Assert.assertEquals(RunnerHudLayout.Action.PAUSE_TOGGLE, hud.resolveAction(hud.pauseBounds.right, 40f, h));
        float gapX = (hud.pauseBounds.right + hud.exitBounds.left) / 2f;
        Assert.assertEquals(RunnerHudLayout.Action.NONE, hud.resolveAction(gapX, 40f, h));
    }

    @Test
    public void testCompactVsWideTransitionAndNoOverlapAcrossWidths() {
        int[] testWidths = new int[]{390, 480, 599, 600, 640, 665, 666, 719, 720, 963, 1366, 1920};
        for (int w : testWidths) {
            hud.update(w, 540);
            Assert.assertTrue(hud.exitBounds.width() > 0);
            Assert.assertTrue(hud.pauseBounds.width() > 0);
            Assert.assertTrue(hud.heroBounds.width() > 0);
            Assert.assertTrue(hud.pauseBounds.right < hud.exitBounds.left);
            Assert.assertTrue(hud.heroBounds.right < hud.pauseBounds.left);
            if (w < 720) {
                Assert.assertTrue(hud.isCompact);
                Assert.assertTrue(hud.scoreBounds.top >= hud.exitBounds.bottom);
            } else {
                Assert.assertFalse(hud.isCompact);
                Assert.assertTrue(hud.heroBounds.left >= hud.scoreBounds.right);
            }
        }
    }

    @Test
    public void testOffViewAndGameplayTouchZones() {
        hud.update(1366, 768);
        Assert.assertEquals(RunnerHudLayout.Action.NONE, hud.resolveAction(-10f, 40f, 1366, 768));
        Assert.assertEquals(RunnerHudLayout.Action.NONE, hud.resolveAction(1400f, 40f, 1366, 768));
        Assert.assertEquals(RunnerHudLayout.Action.NONE, hud.resolveAction(500f, 40f, 0, 768));
        Assert.assertEquals(RunnerHudLayout.Action.HERO_TOGGLE,
                hud.resolveAction(hud.heroBounds.centerX(), hud.heroBounds.centerY(), 768));
        Assert.assertEquals(RunnerHudLayout.Action.CROUCH, hud.resolveAction(300f, 600f, 768));
        Assert.assertEquals(RunnerHudLayout.Action.JUMP, hud.resolveAction(300f, 300f, 768));
    }
}

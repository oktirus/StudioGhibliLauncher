package ua.ghibli.tv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.panels.LibraryPanel;
import ua.ghibli.tv.launcher.panels.SettingsPanel;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.views.SpiritRenderer;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;

/** Software Skia rendering of production Studio Ghibli Views; not a live TV/GPU acceptance test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w1366dp-h768dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeUiRenderTest {

    @Test
    @Config(qualifiers = "w963dp-h541dp-land-mdpi")
    public void renderUpdateNoticeOnCompactTv() throws Exception {
        renderUpdateNotice(963, 541);
    }

    @Test
    @Config(qualifiers = "w1920dp-h1080dp-land-mdpi")
    public void renderUpdateNoticeOnWideTv() throws Exception {
        renderUpdateNotice(1920, 1080);
    }

    private void renderUpdateNotice(int width, int height) throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        var app = RuntimeEnvironment.getApplication();
        app.getSharedPreferences("launcher_updates", android.content.Context.MODE_PRIVATE).edit()
                .putLong("available_code", 999).putString("available_name", "1.1.0").apply();
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity = controller.get();
            flush(activity, "appManager", "executor");
            flush(activity, "backgroundView", "bgExecutor");
            capture(activity, "update-available-home-" + width, width, height);
            android.widget.TextView notice = NativeLifecycleTest.field(activity, "updateNotice");
            View settings = NativeLifecycleTest.field(activity, "settingsBtn");
            assertEquals(View.VISIBLE, notice.getVisibility());
            assertEquals("Нова версія 1.1.0", notice.getText().toString());
            assertEquals(0, notice.getLayout().getEllipsisCount(0));
            assertTrue(notice.getRight() < settings.getLeft());
            assertFalse(notice.isFocusable());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test
    @Config(qualifiers = "w963dp-h541dp-land-mdpi")
    public void renderPrimaryLauncherDefaultsAndAbout() throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        PreferencesManager prefs = new PreferencesManager(RuntimeEnvironment.getApplication());
        assertTrue(prefs.isAudioEnabled());
        assertTrue(prefs.isMotionEnabled());
        assertTrue(prefs.isAmbienceEnabled());
        assertEquals(60, prefs.getRotationSeconds());
        assertEquals(900, prefs.getIdleSeconds());
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity = controller.get();
            flush(activity, "appManager", "executor");
            flush(activity, "backgroundView", "bgExecutor");
            flush(activity, "dockView", "iconExecutor");
            flush(activity, "spiritRenderer", "decodeExecutor");
            capture(activity, "primary-defaults-home-963", 963, 541);

            NativeLifecycleTest.call(activity, "showAboutDialog");
            android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
                    .idleFor(java.time.Duration.ofMillis(16));
            captureView(dialog.getWindow().getDecorView(), "primary-about-963", 600, 500);
            dialog.dismiss();

            NativeLifecycleTest.call(activity, "openSettings");
            capture(activity, "primary-defaults-settings-963", 963, 541);
            activity.closeActivePanel();

            NativeLifecycleTest.call(activity, "openSpirits");
            flush(activity, "spiritRenderer", "decodeExecutor");
            capture(activity, "primary-spirits-order-963", 963, 541);
            Object spiritsPanel = NativeLifecycleTest.field(activity, "spiritsPanel");
            GridLayout grid = NativeLifecycleTest.field(spiritsPanel, "grid");
            ViewGroup firstCard = (ViewGroup) grid.getChildAt(0);
            assertEquals("Сусуватарі",
                    ((android.widget.TextView) firstCard.getChildAt(1)).getText().toString());
            assertTrue(firstCard.hasFocus());
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void renderFocusedLibraryEffects() throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        new PreferencesManager(RuntimeEnvironment.getApplication()).setMotionEnabled(false);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity = controller.get();
            flush(activity, "appManager", "executor");
            flush(activity, "backgroundView", "bgExecutor");
            NativeLifecycleTest.call(activity, "openLibrary");
            layout(activity, 1366, 768);
            LibraryPanel panel = NativeLifecycleTest.field(activity, "libraryPanel");
            AppManager apps = NativeLifecycleTest.field(activity, "appManager");
            View card = panel.findCardByPackage(apps.getAllApps().get(0).getPackageName());
            assertTrue(card.requestFocusFromTouch());
            for (String spirit : new String[]{"calcifer", "soot_sprites"}) {
                panel.setTechnique(spirit);
                SpiritRenderer renderer = NativeLifecycleTest.field(activity, "spiritRenderer");
                for (String plate : SpiritRenderer.platesFor(spirit)) renderer.ensureBitmap(plate);
                flush(activity, "spiritRenderer", "decodeExecutor");
                capture(activity, "library-focus-" + spirit, 1366, 768);
            }
        } finally { controller.pause().stop().destroy(); }
    }

    @Test public void renderHomeAndAllMainPanels() throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        PreferencesManager prefs = new PreferencesManager(RuntimeEnvironment.getApplication());
        prefs.setMotionEnabled(false);
        prefs.setAudioEnabled(false);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity = controller.get();
            flush(activity, "appManager", "executor");
            flush(activity, "backgroundView", "bgExecutor");
            flush(activity, "dockView", "iconExecutor");
            flush(activity, "spiritRenderer", "decodeExecutor");
            capture(activity, "home-1366", 1366, 768);
            for (String[] panel : new String[][]{
                    {"openLibrary", "library-1366", "libraryPanel"},
                    {"openScenes", "scenes-1366", "scenesPanel"},
                    {"openSpirits", "spirits-1366", "spiritsPanel"},
                    {"openGame", "game-1366", "runnerGameView"},
                    {"openSettings", "settings-1366", "settingsPanel"}}) {
                NativeLifecycleTest.call(activity, panel[0]);
                layout(activity, 1366, 768);
                if ("scenesPanel".equals(panel[2])) flush(activity, panel[2], "thumbExecutor");
                if ("spiritsPanel".equals(panel[2])) flush(activity, "spiritRenderer", "decodeExecutor");
                if ("runnerGameView".equals(panel[2])) flush(activity, panel[2], "bitmapExecutor");
                flush(activity, "dockView", "iconExecutor");
                capture(activity, panel[1], 1366, 768);
                if ("runnerGameView".equals(panel[2])) {
                    ua.ghibli.tv.launcher.views.ValleyRunnerView game = NativeLifecycleTest.field(activity, "runnerGameView");
                    ua.ghibli.tv.launcher.game.RunnerPhysics rp = NativeLifecycleTest.field(game, "runner");
                    rp.start();
                    rp.distance = 620.0;
                    rp.elapsed = 1.45;
                    rp.obstacles.clear();
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(1, "stump", 520.0, 394.0, 62.0, 46.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(2, "stone", 820.0, 368.0, 42.0, 72.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(3, "paper_bird", 1040.0, 344.0, 46.0, 34.0));
                    capture(activity, "game-running-soot-1366", 1366, 768);

                    game.setHero("kodama");
                    flush(activity, panel[2], "bitmapExecutor");
                    rp.start();
                    rp.distance = 980.0;
                    rp.elapsed = 2.15;
                    rp.y = 82.0;
                    rp.vy = -210.0;
                    rp.obstacles.clear();
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(4, "stump", 380.0, 394.0, 62.0, 46.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(5, "paper_bird", 670.0, 344.0, 46.0, 34.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(6, "stone", 990.0, 368.0, 42.0, 72.0));
                    capture(activity, "game-running-kodama-1366", 1366, 768);

                    game.setHero("totoro");
                    flush(activity, panel[2], "bitmapExecutor");
                    rp.start();
                    rp.distance = 1420.0;
                    rp.elapsed = 1.68;
                    rp.obstacles.clear();
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(7, "stump", 490.0, 394.0, 62.0, 46.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(8, "stone", 790.0, 368.0, 42.0, 72.0));
                    rp.obstacles.add(new ua.ghibli.tv.launcher.game.RunnerPhysics.Obstacle(9, "paper_bird", 1020.0, 344.0, 46.0, 34.0));
                    capture(activity, "game-running-totoro-1366", 1366, 768);
                }
                activity.closeActivePanel();
            }
            capture(activity, "home-1920", 1920, 1080);
        } finally {
            controller.pause().stop().destroy();
        }
    }

    @Test public void pickerCardsFitCompactAndTvWidths() throws Exception {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        new PreferencesManager(RuntimeEnvironment.getApplication()).setMotionEnabled(false);
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        try {
            MainActivity activity = controller.get();
            flush(activity, "appManager", "executor");
            for (String[] panel : new String[][]{{"openScenes", "scenesPanel"}, {"openSpirits", "spiritsPanel"},
                    {"openLibrary", "libraryPanel"}}) {
                NativeLifecycleTest.call(activity, panel[0]);
                if ("scenesPanel".equals(panel[1])) flush(activity, panel[1], "thumbExecutor");
                if ("spiritsPanel".equals(panel[1])) flush(activity, "spiritRenderer", "decodeExecutor");
                flush(activity, "dockView", "iconExecutor");
                for (int width : new int[]{390, 1280, 1920}) {
                    layout(activity, width, 768);
                    View picker = NativeLifecycleTest.field(activity, panel[1]);
                    GridLayout grid = NativeLifecycleTest.field(picker, "grid");
                    capture(activity, panel[1] + "-" + width, width, 768);
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        View card = grid.getChildAt(i);
                        assertTrue("Card left within grid at " + width, card.getLeft() >= 0);
                        assertTrue("Card " + i + " right=" + card.getRight() + "; grid=" + grid.getWidth(),
                                card.getRight() <= grid.getWidth());
                    }
                }
                activity.closeActivePanel();
            }
        } finally {
            controller.pause().stop().destroy();
        }
    }

    private static void flush(MainActivity activity, String owner, String executor) throws Exception {
        Object view = NativeLifecycleTest.field(activity, owner);
        NativeLifecycleTest.drain((ExecutorService) NativeLifecycleTest.field(view, executor));
    }

    private static View layout(MainActivity activity, int width, int height) {
        View root = activity.getWindow().getDecorView();
        for (int pass = 0; pass < 2; pass++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, width, height);
        }
        return root;
    }

    private static void capture(MainActivity activity, String name, int width, int height) throws Exception {
        View root = layout(activity, width, height);
        captureView(root, name, width, height);
    }

    private static void captureView(View root, String name, int width, int height) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, width, height);
        invalidateTree(root);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/reports/native-ui");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        }
        assertNotEquals("Software rendering must contain visible content", bitmap.getPixel(0, 0), bitmap.getPixel(width / 2, height / 2));
        bitmap.recycle();
    }

    private static void invalidateTree(View view) {
        view.invalidate();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) invalidateTree(group.getChildAt(i));
        }
    }
}

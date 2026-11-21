package ua.ghibli.tv.launcher;

import android.content.ComponentCallbacks2;
import android.graphics.Bitmap;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import android.widget.GridLayout;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.game.RunnerPhysics;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.panels.LibraryPanel;
import ua.ghibli.tv.launcher.panels.ScenesPanel;
import ua.ghibli.tv.launcher.panels.SpiritsPanel;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.views.SpiritRenderer;
import ua.ghibli.tv.launcher.views.ValleyBackgroundView;
import ua.ghibli.tv.launcher.views.ValleyRunnerView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Executes production Activity and Views under Robolectric. Not a physical TV GPU/audio test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w1366dp-h768dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class NativeLifecycleTest {
    private ActivityController<MainActivity> controller;

    @Before public void pauseDisplayClock() {
        org.robolectric.shadows.ShadowChoreographer.setPaused(true);
        org.robolectric.shadows.ShadowLog.stream = System.err;
    }

    private MainActivity start() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        MainActivity activity = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(1366, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1366, 768);
        return activity;
    }

    @After public void destroy() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void visibleScenePickerSurvivesRepeatedTrimAndResume() throws Exception {
        MainActivity activity = start();
        call(activity, "openScenes");
        ScenesPanel panel = field(activity, "scenesPanel");
        ExecutorService loader = field(panel, "thumbExecutor");
        for (int i = 0; i < 2; i++) {
            activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
            assertFalse("Trim must not permanently dispose an active picker", loader.isShutdown());
            GridLayout grid = field(panel, "grid");
            assertEquals(8, grid.getChildCount());
            controller.pause().resume();
            assertEquals(8, grid.getChildCount());
        }
    }

    @Test public void searchDoesNotLoseFocusWhenMatchingCardRemains() throws Exception {
        MainActivity activity = start();
        call(activity, "openLibrary");
        LibraryPanel panel = field(activity, "libraryPanel");
        AppManager apps = field(activity, "appManager");
        AppItem app = apps.getAllApps().get(0);
        View card = panel.findCardByPackage(app.getPackageName());
        assertNotNull(card);
        card.requestFocus();
        EditText search = field(panel, "searchInput");
        search.requestFocus();
        assertTrue(search.hasFocus());
        search.setText(app.getName());
        assertTrue("Typing must not move focus back into the grid", search.hasFocus());
    }

    @Test public void spiritsPickerRemainsStaticAndReusableAfterTrim() throws Exception {
        MainActivity activity = start();
        call(activity, "openSpirits");
        SpiritsPanel panel = field(activity, "spiritsPanel");
        SpiritRenderer renderer = field(activity, "spiritRenderer");
        ExecutorService decoder = field(renderer, "decodeExecutor");
        drain(decoder);
        GridLayout grid = field(panel, "grid");
        assertEquals(6, grid.getChildCount());
        Map<String, Bitmap> previews = field(panel, "previewBitmaps");
        Bitmap before = previews.get("wind_petal");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        assertSame("A settled picker does not regenerate animation frames", before, previews.get("wind_petal"));
        activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
        drain(decoder);
        assertEquals(6, grid.getChildCount());
        assertTrue((Boolean) field(panel, "isListenerRegistered"));
        assertFalse(decoder.isShutdown());
        activity.closeActivePanel();
        Map<String, Bitmap> plates = field(renderer, "bitmaps");
        PreferencesManager prefs = field(activity, "prefs");
        assertTrue(SpiritRenderer.platesFor(prefs.getTechnique()).containsAll(plates.keySet()));
        call(activity, "openSpirits");
        SpiritsPanel reopened = field(activity, "spiritsPanel");
        assertNotSame(panel, reopened);
        assertEquals(6, ((GridLayout) field(reopened, "grid")).getChildCount());
    }

    @Test public void obsoleteInventoryDoesNotPublishOrReviveDestroyedOwner() throws Exception {
        MainActivity activity = start();
        AppManager manager = field(activity, "appManager");
        ExecutorService loader = field(manager, "executor");
        drain(loader);
        AtomicInteger old = new AtomicInteger();
        AtomicInteger current = new AtomicInteger();
        manager.reloadAppsAsync(old::incrementAndGet);
        awaitWorker(loader);
        manager.reloadAppsAsync(current::incrementAndGet);
        drain(loader);
        assertEquals(0, old.get());
        assertEquals(1, current.get());
        AtomicInteger disposed = new AtomicInteger();
        manager.reloadAppsAsync(disposed::incrementAndGet);
        awaitWorker(loader);
        manager.release();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0, disposed.get());
        assertTrue(manager.getAllApps().isEmpty());
    }

    @Test public void gameLoadsOnlyActiveHeroAndExposesAccessibleActions() throws Exception {
        MainActivity activity = start();
        call(activity, "openGame");
        ValleyRunnerView game = field(activity, "runnerGameView");
        ExecutorService loader = field(game, "bitmapExecutor");
        drain(loader);
        Map<String, Bitmap> images = field(game, "bitmaps");
        assertEquals(Set.of("valley", "soot-motion", "soot-slide"), images.keySet());
        assertTrue(game.performAccessibilityAction(R.id.runner_hero, null));
        drain(loader);
        assertEquals(Set.of("valley", "kodama-motion", "kodama-slide"), images.keySet());
        AccessibilityNodeInfo node = game.createAccessibilityNodeInfo();
        assertTrue(node.getActionList().stream().anyMatch(a -> a.getId() == R.id.runner_jump));
        assertTrue(game.performAccessibilityAction(R.id.runner_jump, null));
        RunnerPhysics physics = field(game, "runner");
        assertEquals("running", physics.status);
        physics.distance = 12340;
        activity.closeActivePanel();
        PreferencesManager prefs = field(activity, "prefs");
        assertEquals(1234, prefs.getRunnerBest("kodama"));
        assertTrue(images.isEmpty());
        assertTrue(loader.isShutdown());
    }

    @Test public void backgroundPauseFinishesTransitionAndDetachCanReload() throws Exception {
        MainActivity activity = start();
        ValleyBackgroundView background = field(activity, "backgroundView");
        ExecutorService loader = field(background, "bgExecutor");
        drain(loader);
        Bitmap incoming = Bitmap.createBitmap(4, 4, Bitmap.Config.RGB_565);
        Method apply = ValleyBackgroundView.class.getDeclaredMethod("applyNewSceneBitmap", Bitmap.class, boolean.class);
        apply.setAccessible(true);
        apply.invoke(background, incoming, true);
        assertTrue((Boolean) field(background, "isCrossfading"));
        controller.pause();
        assertFalse((Boolean) field(background, "isCrossfading"));
        assertSame(incoming, field(background, "currentBitmap"));
        assertNull(field(background, "nextBitmap"));
        controller.resume();
        ViewGroup parent = (ViewGroup) background.getParent();
        int index = parent.indexOfChild(background);
        ViewGroup.LayoutParams lp = background.getLayoutParams();
        parent.removeView(background);
        assertFalse(loader.isShutdown());
        parent.addView(background, index, lp);
        drain(loader);
        assertNotNull(field(background, "currentBitmap"));
    }

    @Test public void headerFocusRestoresWithoutDockAura() throws Exception {
        controller = Robolectric.buildActivity(MainActivity.class).create().start();
        MainActivity activity = controller.get();
        ExecutorService loader = field(field(activity, "appManager"), "executor");
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch allowReload = new CountDownLatch(1);
        loader.submit(() -> {
            workerStarted.countDown();
            allowReload.await();
            return null;
        });

        View button = field(activity, "sceneChoiceBtn");
        try {
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
            controller.resume().visible();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
            View root = activity.getWindow().getDecorView();
            root.measure(View.MeasureSpec.makeMeasureSpec(1366, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1366, 768);
            assertTrue(button.isAttachedToWindow());
            assertFalse((Boolean) field(activity, "appInventoryReady"));
            assertTrue(button.requestFocusFromTouch());
            assertSame(button, root.findFocus());
            call(activity, "openScenes");
            assertSame(button, field(activity, "lastFocusedView"));
            activity.closeActivePanel();
            assertSame(button, root.findFocus());
        } finally {
            allowReload.countDown();
        }
        drain(loader);
        assertSame(button, activity.getWindow().getDecorView().findFocus());
        Object focus = field(activity, "spiritFocusView");
        assertNull(field(focus, "targetView"));
    }

    static void drain(ExecutorService executor) throws Exception {
        awaitWorker(executor);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
    }

    static void awaitWorker(ExecutorService executor) throws Exception {
        if (!(executor instanceof ThreadPoolExecutor)) {
            executor.submit(() -> {}).get(5, TimeUnit.SECONDS);
            return;
        }
        ThreadPoolExecutor pool = (ThreadPoolExecutor) executor;
        FutureTask<Void> barrier = new FutureTask<>(() -> null);
        assertTrue(pool.getQueue().offer(barrier, 5, TimeUnit.SECONDS));
        pool.prestartAllCoreThreads();
        barrier.get(5, TimeUnit.SECONDS);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (pool.getActiveCount() != 0 || !pool.getQueue().isEmpty()) {
            if (System.nanoTime() > deadline) fail("Background worker did not settle after barrier");
            Thread.sleep(2);
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(owner);
    }

    static void call(Object owner, String name) throws Exception {
        Method m = owner.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(owner);
    }
}

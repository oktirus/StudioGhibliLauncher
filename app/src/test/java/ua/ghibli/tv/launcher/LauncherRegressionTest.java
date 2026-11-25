package ua.ghibli.tv.launcher;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowSystemClock;
import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.audio.AudioController;
import ua.ghibli.tv.launcher.game.RunnerCue;
import ua.ghibli.tv.launcher.game.RunnerPhysics;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.panels.LibraryPanel;
import ua.ghibli.tv.launcher.panels.SettingsPanel;
import ua.ghibli.tv.launcher.panels.SideSheetContainer;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.views.DockView;
import ua.ghibli.tv.launcher.views.GhibliSeekBar;
import ua.ghibli.tv.launcher.views.GhibliSwitch;
import ua.ghibli.tv.launcher.views.SpiritFocusView;
import ua.ghibli.tv.launcher.views.SpiritRenderer;
import ua.ghibli.tv.launcher.views.ValleyBackgroundView;
import ua.ghibli.tv.launcher.views.ValleyRunnerView;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;
import static ua.ghibli.tv.launcher.NativeLifecycleTest.*;

/** User actions against production Studio Ghibli Views; software rendering is not a live TV/GPU/audio test. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w1366dp-h768dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherRegressionTest {
    private ActivityController<MainActivity> controller;

    private MainActivity start() throws Exception {
        ShadowChoreographer.setPaused(true);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        MainActivity activity = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        AppManager apps = field(activity, "appManager");
        drain((ExecutorService) field(apps, "executor"));
        layout(activity);
        return activity;
    }

    @After public void destroy() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void menuFromHomeOpensSettings() throws Exception {
        MainActivity activity = start();
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
        assertTrue(field(activity, "activePanel") instanceof SettingsPanel);
    }

    @Test public void homeIntentClosesCurrentPanel() throws Exception {
        MainActivity activity = start();
        call(activity, "openGame");
        controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        assertNull("Home leaves the game", field(activity, "activePanel"));
    }

    @Test public void runnerPlaysEachGameplayCueOnceAndStopsOnExit() throws Exception {
        MainActivity activity = start();
        ((AudioController) field(activity, "audioController")).release();
        RecordingAudio audio = new RecordingAudio(activity, field(activity, "prefs"));
        org.robolectric.util.ReflectionHelpers.setField(activity, "audioController", audio);
        call(activity, "openGame");
        layout(activity);
        assertEquals(1, audio.runnerPreparations);
        assertTrue(audio.runnerSounds.isEmpty());

        ValleyRunnerView game = field(activity, "runnerGameView");
        assertTrue(game.isShown());
        RunnerPhysics runner = field(game, "runner");
        Runnable tick = field(game, "tickRunnable");
        game.jump();
        game.jump();
        game.jump();
        assertEquals(List.of(RunnerCue.START, RunnerCue.JUMP), audio.runnerSounds);

        runner.y = 0.1;
        runner.vy = 500;
        ShadowSystemClock.advanceBy(Duration.ofMillis(16));
        tick.run();
        assertEquals(0.0, runner.y, 0.0);

        game.crouch();
        game.crouch();
        runner.obstacles.add(new RunnerPhysics.Obstacle(1, "stump", 112, 390, 64, 48));
        ShadowSystemClock.advanceBy(Duration.ofMillis(16));
        tick.run();
        runner.obstacles.add(new RunnerPhysics.Obstacle(2, "stone", 185, 366, 40, 74));
        ShadowSystemClock.advanceBy(Duration.ofMillis(16));
        tick.run();
        tick.run();
        assertEquals(List.of(RunnerCue.START, RunnerCue.JUMP, RunnerCue.LAND,
                RunnerCue.SLIDE, RunnerCue.CLEAR, RunnerCue.HIT), audio.runnerSounds);
        int stops = audio.runnerStops;
        activity.closeActivePanel();
        assertTrue(audio.runnerStops > stops);
    }

    @Test public void firstOpenFocusesFirstAppWithoutDelayedInput() throws Exception {
        ShadowChoreographer.setPaused(true);
        controller = Robolectric.buildActivity(MainActivity.class).create().start();
        MainActivity activity = controller.get();
        View gameBtn = field(activity, "gameBtn");
        assertTrue(gameBtn.requestFocusFromTouch());
        controller.resume().visible();
        drain((ExecutorService) field(field(activity, "appManager"), "executor"));
        layout(activity);
        DockView dock = field(activity, "dockView");
        assertSame(dock.getFirstCard(), focused(activity));
        assertSame(dock.getFirstCard(), field(field(activity, "spiritFocusView"), "targetView"));
    }

    @Test public void brandHeaderIsNotFocusableAndTopNavigationSkipsIt() throws Exception {
        MainActivity activity = start();
        View brandHeader = field(activity, "brandHeader");
        View sceneChoiceBtn = field(activity, "sceneChoiceBtn");
        View gameBtn = field(activity, "gameBtn");
        View settingsBtn = field(activity, "settingsBtn");
        DockView dock = field(activity, "dockView");

        assertFalse("Brand title must not be focusable", brandHeader.isFocusable());
        assertFalse("Brand title must not be clickable", brandHeader.isClickable());
        assertFalse("Brand title must reject focus requests", brandHeader.requestFocus());

        assertEquals(gameBtn.getId(), sceneChoiceBtn.getNextFocusUpId());
        assertEquals(sceneChoiceBtn.getId(), gameBtn.getNextFocusLeftId());
        assertEquals(settingsBtn.getId(), gameBtn.getNextFocusRightId());

        assertTrue(gameBtn.requestFocusFromTouch());
        assertSame(gameBtn, focused(activity));
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
        assertNotSame(brandHeader, focused(activity));
        assertSame(dock.getFirstCard(), focused(activity));
    }

    @Test public void homeEntryAndDockRebuildStayQuietUntilRemoteMovesFocus() throws Exception {
        ShadowChoreographer.setPaused(true);
        controller = Robolectric.buildActivity(MainActivity.class).create().start();
        MainActivity activity = controller.get();
        ((AudioController) field(activity, "audioController")).release();
        RecordingAudio audio = new RecordingAudio(activity, field(activity, "prefs"));
        org.robolectric.util.ReflectionHelpers.setField(activity, "audioController", audio);
        controller.resume().visible();
        drain((ExecutorService) field(field(activity, "appManager"), "executor"));
        layout(activity);

        DockView dock = field(activity, "dockView");
        assertSame(dock.getFirstCard(), focused(activity));
        assertEquals("Automatic initial focus must be quiet", 0, audio.focusSounds);
        View next = ((ViewGroup) dock.getFirstCard().getParent()).getChildAt(1);
        assertTrue(activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)));
        assertSame(next, focused(activity));
        assertEquals(1, audio.focusSounds);

        controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        dock.render();
        layout(activity);
        assertSame(dock.getFirstCard(), focused(activity));
        assertEquals("Restoring or rebuilding Home must be quiet", 1, audio.focusSounds);
        activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals("Edge press must not fire focus sound", 1, audio.focusSounds);
    }

    @Test public void homeIntentFocusesFirstAppAndEmptyPinsFocusesArchive() throws Exception {
        MainActivity activity = start();
        DockView dock = field(activity, "dockView");
        ViewGroup cards = (ViewGroup) dock.getFirstCard().getParent();
        assertTrue(cards.getChildAt(1).requestFocusFromTouch());
        call(activity, "openSettings");
        controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        assertSame(dock.getFirstCard(), focused(activity));

        PreferencesManager prefs = field(activity, "prefs");
        prefs.setPins(List.of());
        dock.render();
        controller.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        assertSame(dock.getArchiveCard(), focused(activity));
    }

    @Test public void ordinaryResumePreservesRememberedApp() throws Exception {
        MainActivity activity = start();
        DockView dock = field(activity, "dockView");
        ViewGroup cards = (ViewGroup) dock.getFirstCard().getParent();
        View second = cards.getChildAt(1);
        assertTrue(second.requestFocusFromTouch());
        String packageName = ((AppItem) second.getTag()).getPackageName();
        controller.pause().resume();
        drain((ExecutorService) field(field(activity, "appManager"), "executor"));
        layout(activity);
        assertSame(dock.findCardByPackage(packageName), focused(activity));
    }

    @Test public void sootSpriteStartsHopAtMeasuredPerchAfterRelayout() throws Exception {
        MainActivity activity = start();
        android.widget.FrameLayout root = field(activity, "rootLayout");
        android.widget.FrameLayout row = new android.widget.FrameLayout(activity);
        View first = new View(activity);
        View next = new View(activity);
        android.widget.FrameLayout.LayoutParams firstParams = new android.widget.FrameLayout.LayoutParams(100, 100);
        firstParams.leftMargin = 450;
        row.addView(first, firstParams);
        android.widget.FrameLayout.LayoutParams nextParams = new android.widget.FrameLayout.LayoutParams(100, 100);
        nextParams.leftMargin = 180;
        row.addView(next, nextParams);
        SpiritFocusView aura = new SpiritFocusView(activity, field(activity, "spiritRenderer"));
        root.addView(row, new android.widget.FrameLayout.LayoutParams(-1, -1));
        root.addView(aura, new android.widget.FrameLayout.LayoutParams(-1, -1));
        layout(activity);
        aura.setTargetView(first);
        firstParams.leftMargin = 60;
        first.setLayoutParams(firstParams);
        layout(activity);
        Bitmap frame = Bitmap.createBitmap(aura.getWidth(), aura.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(frame);
            aura.draw(canvas);
            aura.setTargetView(null);
            aura.setTargetView(next);
            ShadowSystemClock.advanceBy(Duration.ofMillis(16));
            aura.draw(canvas);

            SpiritRenderer.SootState soot = field(aura, "sootState");
            assertTrue("Moving to the next card starts a hop", soot.isJumping);
            assertEquals("Hop starts at visible perch", 135.2f, soot.fromPos.x, 1.5f);
            assertEquals("Hop ends at second card", 255.2f, soot.toPos.x, 1.5f);
        } finally {
            root.removeView(aura);
            root.removeView(row);
            frame.recycle();
        }
    }

    @Test public void aboutDialogCreditsOktirusAndGhibliWithoutLegacyLeakage() throws Exception {
        MainActivity activity = start();
        call(activity, "showAboutDialog");
        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        try {
            TextView message = dialog.findViewById(android.R.id.message);
            String text = message.getText().toString();
            assertTrue(text.contains("Розробник: Oktirus"));
            assertTrue(text.contains("Studio Ghibli"));
            assertFalse(text.toLowerCase().contains("naruto"));
            assertFalse(text.toLowerCase().contains("konoha"));
        } finally { dialog.dismiss(); }
    }

    @Test public void motionOffKeepsWindPetalFrameStatic() throws Exception {
        MainActivity activity = start();
        SpiritFocusView focus = field(activity, "spiritFocusView");
        DockView dock = field(activity, "dockView");
        focus.setTechnique("wind_petal");
        focus.setMotionEnabled(false);
        focus.setTargetView(dock.getFirstCard());
        Bitmap first = Bitmap.createBitmap(1366, 768, Bitmap.Config.ARGB_8888);
        Bitmap second = Bitmap.createBitmap(1366, 768, Bitmap.Config.ARGB_8888);
        try {
            for (int i = 0; i < 50; i++) { focus.invalidate(); focus.draw(new Canvas(first)); }
            first.eraseColor(0);
            focus.invalidate();
            focus.draw(new Canvas(first));
            ShadowSystemClock.advanceBy(Duration.ofSeconds(1));
            focus.invalidate();
            focus.draw(new Canvas(second));
            assertTrue("Motion off freezes the spirit effect", first.sameAs(second));
        } finally { first.recycle(); second.recycle(); }
    }

    @Test public void initialPinsAndRecoveryWithInstalledPackages() throws Exception {
        MainActivity activity = start();
        AppManager apps = field(activity, "appManager");
        PreferencesManager prefs = field(activity, "prefs");
        prefs.setPins(null);
        AppItem vlc = new AppItem("org.videolan.vlc", "VLC", "vlc", "", null);
        publish(apps, List.of(vlc));
        assertEquals(List.of("org.videolan.vlc"), prefs.getPins());

        prefs.setPins(List.of("preview.smarttube", "preview.netflix"));
        publish(apps, List.of(vlc));
        assertEquals(List.of("org.videolan.vlc"), prefs.getPins());

        prefs.setPins(List.of());
        publish(apps, List.of(vlc));
        assertTrue(prefs.getPins().isEmpty());
    }

    @Test public void visibleModalKeepsFocusWhenDockRefreshes() throws Exception {
        MainActivity activity = start();
        call(activity, "openSettings");
        layout(activity);
        SettingsPanel panel = field(activity, "settingsPanel");
        SideSheetContainer sheet = field(panel, "sheetContainer");
        assertTrue(sheet.getCloseButton().requestFocusFromTouch());
        DockView dock = field(activity, "dockView");
        dock.render();
        assertTrue(inside(focused(activity), panel));
        assertFalse(dock.getFirstCard().requestFocus());
    }

    @Test public void slidersRespondToTouch() throws Exception {
        GhibliSeekBar slider = new GhibliSeekBar(start());
        slider.layout(0, 0, 200, 24);
        tap(slider, 180, 12);
        assertTrue(slider.getProgress() > 45);
        tap(slider, 0, 12);
        assertEquals(0, slider.getProgress());
        tap(slider, 200, 12);
        assertEquals(100, slider.getProgress());
    }

    @Test public void tapPauseDoesNotExitRunner() throws Exception {
        MainActivity activity = start();
        call(activity, "openGame");
        layout(activity);
        ValleyRunnerView game = field(activity, "runnerGameView");
        game.startGame();
        Bitmap bmp = Bitmap.createBitmap(game.getWidth(), game.getHeight(), Bitmap.Config.ARGB_8888);
        try { game.draw(new Canvas(bmp)); } finally { bmp.recycle(); }
        RectF pauseRect = field(game, "pauseButton");
        tap(game, pauseRect.centerX(), pauseRect.centerY());
        assertSame(game, field(activity, "activePanel"));
        RunnerPhysics physics = field(game, "runner");
        assertEquals("paused", physics.status);
        tap(game, pauseRect.centerX(), pauseRect.centerY());
        assertEquals("running", physics.status);
    }

    @Test public void audioToggleAndSpiritPreviewWork() throws Exception {
        MainActivity activity = start();
        PreferencesManager prefs = field(activity, "prefs");
        prefs.setAudioEnabled(false);
        prefs.setTechnique("calcifer");
        RecordingAudio audio = new RecordingAudio(activity, prefs);
        try {
            SettingsPanel panel = new SettingsPanel(activity, prefs, audio);
            LinearLayout content = field(panel, "content");
            GhibliSwitch toggle = (GhibliSwitch) ((ViewGroup) content.getChildAt(3)).getChildAt(1);
            audio.musicUpdates = 0;
            toggle.performClick();
            assertTrue(prefs.isAudioEnabled());
            assertTrue(audio.musicUpdates > 0);
            toggle.performClick();
            assertFalse(prefs.isAudioEnabled());

            findText(panel, "Перевірити духа:").performClick();
            assertTrue(prefs.isAudioEnabled());
            assertEquals("calcifer", audio.previewTechnique);
            GhibliSwitch sw = field(panel, "audioSwitch");
            assertTrue(sw.isChecked());
        } finally { audio.release(); }
    }

    @Test public void libraryFocusTargetsSpiritAuraAndPausesCleanly() throws Exception {
        MainActivity activity = start();
        call(activity, "openLibrary");
        layout(activity);
        LibraryPanel panel = field(activity, "libraryPanel");
        AppManager apps = field(activity, "appManager");
        View card = panel.findCardByPackage(apps.getAllApps().get(0).getPackageName());
        assertTrue(card.requestFocusFromTouch());
        SpiritFocusView aura = findView(panel, SpiritFocusView.class);
        assertNotNull(aura);
        assertSame(card, aura.getTargetView());
        controller.pause();
        assertTrue(aura.isPaused());
    }

    @Test public void unusedFocusLayerAndPausedDecorDoNotInvalidate() throws Exception {
        MainActivity activity = start();
        SpiritRenderer renderer = new SpiritRenderer(activity);
        CountingFocus layer = new CountingFocus(activity, renderer);
        CountingBackground background = new CountingBackground(activity);
        ViewGroup root = field(activity, "rootLayout");
        root.addView(layer, new ViewGroup.LayoutParams(100, 100));
        root.addView(background, new ViewGroup.LayoutParams(100, 100));
        background.setDecorPaused(true);
        layer.invalidations = 0;
        background.invalidations = 0;
        try {
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));
            assertEquals(0, layer.invalidations);
            assertEquals(0, background.invalidations);
        } finally {
            root.removeView(layer);
            root.removeView(background);
            renderer.release();
            background.release();
        }
    }

    private static View layout(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        for (int i = 0; i < 2; i++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(1366, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(768, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 1366, 768);
        }
        root.getViewTreeObserver().dispatchOnGlobalLayout();
        return root;
    }

    private static View focused(MainActivity activity) throws Exception {
        return ((View) field(activity, "rootLayout")).findFocus();
    }

    private static boolean inside(View view, View ancestor) {
        while (view != null) {
            if (view == ancestor) return true;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return false;
    }

    private static void tap(View view, float x, float y) {
        long time = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(time, time + 16, action, x, y, 0);
            try { view.dispatchTouchEvent(event); } finally { event.recycle(); }
        }
    }

    private static <T extends View> T findView(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findView(group.getChildAt(i), type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findText(View view, String prefix) {
        if (view instanceof TextView && ((TextView) view).getText().toString().startsWith(prefix)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), prefix);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void publish(AppManager apps, List<AppItem> inventory) throws Exception {
        Map<String, AppItem> byPackage = new LinkedHashMap<>();
        for (AppItem item : inventory) byPackage.put(item.getPackageName(), item);
        Method method = AppManager.class.getDeclaredMethod("publishReload", long.class, Map.class,
                List.class, AppManager.OnAppsReloadedListener.class);
        method.setAccessible(true);
        method.invoke(apps, ((Long) field(apps, "reloadGeneration")).longValue(), byPackage,
                new ArrayList<>(inventory), null);
    }

    private static class RecordingAudio extends AudioController {
        int musicUpdates;
        int focusSounds;
        int runnerPreparations;
        int runnerStops;
        final List<RunnerCue> runnerSounds = new ArrayList<>();
        String previewTechnique;
        RecordingAudio(Context context, PreferencesManager prefs) { super(context, prefs); }
        @Override public synchronized void updateMusic() { musicUpdates++; }
        @Override public synchronized void playUiSound() {}
        @Override public synchronized void playFocusSound() { focusSounds++; }
        @Override public synchronized void prepareRunnerSounds() { runnerPreparations++; }
        @Override public synchronized void playRunnerSound(RunnerCue cue) { runnerSounds.add(cue); }
        @Override public synchronized void stopRunnerSounds() { runnerStops++; }
        @Override public synchronized boolean playTechniquePreview(String id) { previewTechnique = id; return true; }
    }

    private static class CountingFocus extends SpiritFocusView {
        int invalidations;
        CountingFocus(Context context, SpiritRenderer renderer) { super(context, renderer); }
        @Override public void invalidate() { invalidations++; super.invalidate(); }
    }

    private static class CountingBackground extends ValleyBackgroundView {
        int invalidations;
        CountingBackground(Context context) { super(context); }
        @Override public void invalidate() { invalidations++; super.invalidate(); }
    }
}

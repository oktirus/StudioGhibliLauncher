package ua.ghibli.tv.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
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
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.Scene;
import ua.ghibli.tv.launcher.panels.SettingsPanel;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.views.GhibliSeekBar;
import ua.ghibli.tv.launcher.views.GhibliSwitch;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import static org.junit.Assert.*;
import static ua.ghibli.tv.launcher.NativeLifecycleTest.*;

/** Compact TV logical resolution regression checks (963x541). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w963dp-h541dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LauncherDisplayRegressionTest {
    private ActivityController<MainActivity> controller;

    private MainActivity start() throws Exception {
        ShadowChoreographer.setPaused(true);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        MainActivity activity = controller.get();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
        drain((ExecutorService) field(field(activity, "appManager"), "executor"));
        drain((ExecutorService) field(field(activity, "backgroundView"), "bgExecutor"));
        layout(activity);
        return activity;
    }

    @After public void destroy() {
        if (controller != null) controller.pause().stop().destroy();
    }

    @Test public void ambientShowsWallpaperWithoutHomeTextOrDock() throws Exception {
        MainActivity activity = start();
        View home = field(activity, "mainShell");
        call(activity, "enterAmbient");
        capture(activity, "ambient-963");
        assertFalse("Home labels, clock and icons must be hidden during ambient", home.isShown());
        assertTrue(((View) field(activity, "backgroundView")).isShown());
        assertTrue(((View) field(activity, "ambientOverlay")).isShown());
        call(activity, "exitAmbient");
        assertTrue("Home restored after exiting ambient", home.isShown());
    }

    @Test public void settingsControlsHighlightTheirWholeRowWhenFocused() throws Exception {
        MainActivity activity = start();
        call(activity, "openSettings");
        layout(activity);
        SettingsPanel panel = field(activity, "settingsPanel");
        ViewGroup content = field(panel, "content");
        View otherFocus = content.getChildAt(0);
        int checked = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            View row = content.getChildAt(i);
            View control = findControl(row);
            if (control == null) continue;
            assertTrue(otherFocus.requestFocusFromTouch());
            int normal = rowPixel(row);
            assertTrue(control.requestFocusFromTouch());
            assertNotEquals("Visible row focus for " + control.getClass().getSimpleName(), normal, rowPixel(row));
            checked++;
        }
        assertEquals("Three toggles, two sliders, four selectors", 9, checked);
        capture(activity, "settings-focus-963");
    }

    @Test public void dropdownKeyboardSelectionHasVisibleBackground() throws Exception {
        MainActivity activity = start();
        call(activity, "openSettings");
        SettingsPanel panel = field(activity, "settingsPanel");
        ViewGroup content = field(panel, "content");
        Spinner spinner = (Spinner) findControl(content.getChildAt(1));
        View item = spinner.getAdapter().getDropDownView(0, null, content);
        item.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST));
        item.layout(0, 0, item.getMeasuredWidth(), item.getMeasuredHeight());
        item.setSelected(false);
        int normal = rowPixel(item);
        item.setActivated(true);
        assertEquals("Committed value must not compete with the D-pad cursor", normal, rowPixel(item));
        item.setSelected(true);
        assertNotEquals("D-pad selected popup option must be highlighted", normal, rowPixel(item));
    }

    @Test public void wallpaperButtonAndCaptionFitAtCompactTvLogicalSize() throws Exception {
        MainActivity activity = start();
        View button = field(activity, "sceneChoiceBtn");
        PreferencesManager prefs = field(activity, "prefs");
        for (Scene scene : CatalogData.SCENES) {
            prefs.setScene(scene.getId());
            java.lang.reflect.Method apply = MainActivity.class.getDeclaredMethod("applyScene", String.class, boolean.class);
            apply.setAccessible(true);
            apply.invoke(activity, scene.getId(), false);
            drain((ExecutorService) field(field(activity, "backgroundView"), "bgExecutor"));
            layout(activity);
            if (scene == CatalogData.SCENES.get(0)) capture(activity, "wallpaper-caption-963");
            View parent = (View) button.getParent();
            assertTrue("Wallpaper button fully inside hero for " + scene.getName()
                            + ": " + button.getBottom() + "/" + parent.getHeight(),
                    button.getTop() >= 0 && button.getBottom() <= parent.getHeight());
            TextView label = field(button, "labelView");
            assertTrue("Caption retains its text height for " + scene.getName(),
                    label.getHeight() >= label.getLayout().getHeight());
        }
        capture(activity, "wallpaper-caption-963");
    }

    private static View findControl(View view) {
        if (view instanceof GhibliSwitch || view instanceof GhibliSeekBar || view instanceof Spinner) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findControl(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static int rowPixel(View row) {
        Bitmap bitmap = Bitmap.createBitmap(row.getWidth(), row.getHeight(), Bitmap.Config.ARGB_8888);
        row.invalidate();
        row.draw(new Canvas(bitmap));
        int color = bitmap.getPixel(4, row.getHeight() / 2);
        bitmap.recycle();
        return color;
    }

    private static View layout(MainActivity activity) {
        View root = activity.getWindow().getDecorView();
        for (int pass = 0; pass < 3; pass++) {
            root.measure(View.MeasureSpec.makeMeasureSpec(963, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(541, View.MeasureSpec.EXACTLY));
            root.layout(0, 0, 963, 541);
        }
        return root;
    }

    private static void capture(MainActivity activity, String name) throws Exception {
        View root = layout(activity);
        Bitmap bitmap = Bitmap.createBitmap(963, 541, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/reports/native-ui");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream stream = new FileOutputStream(new File(dir, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally { bitmap.recycle(); }
    }
}

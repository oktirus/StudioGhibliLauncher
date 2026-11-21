package ua.ghibli.tv.launcher.apps;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.List;
import ua.ghibli.tv.launcher.MainActivity;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 31)
@LooperMode(LooperMode.Mode.PAUSED)
public class HomeRedirectServiceTest {
    private static final String STOCK = "com.google.android.apps.tv.launcherx";
    private static final String STOCK_HOME = STOCK + ".home.HomeActivity";
    private ServiceController<HomeRedirectService> controller;
    private HomeRedirectService service;

    @Before public void setup() {
        controller = Robolectric.buildService(HomeRedirectService.class).create();
        service = controller.get();
        setResolvedHome(STOCK, STOCK_HOME);
    }

    @After public void destroy() {
        controller.destroy();
    }

    @Test public void opensGhibliOnceAsAnAppWhenStockHomeAppears() {
        window(STOCK, STOCK_HOME);
        window(STOCK, STOCK_HOME);
        idle();
        Intent launch = Shadows.shadowOf(service).getNextStartedActivity();
        assertNotNull(launch);
        assertEquals(new ComponentName(service, MainActivity.class), launch.getComponent());
        assertEquals(HomeRedirectService.ACTION_REDIRECTED_HOME, launch.getAction());
        assertFalse(launch.hasCategory(Intent.CATEGORY_HOME));
        window(STOCK, STOCK_HOME);
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void repeatedHomeAfterGhibliIsVisibleRedirectsDuringDuplicateWindow() {
        window(STOCK, STOCK_HOME);
        idle();
        assertNotNull(Shadows.shadowOf(service).getNextStartedActivity());
        window(service.getPackageName(), MainActivity.class.getName());
        idle();
        window(STOCK, STOCK_HOME);
        idle();
        assertNotNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void alreadyComingBackToGhibliCancelsPendingRedirect() {
        long stockAppearedAt = SystemClock.uptimeMillis();
        window(STOCK, STOCK_HOME);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        window(service.getPackageName(), MainActivity.class.getName());
        window(STOCK, STOCK_HOME, stockAppearedAt);
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void unrelatedWindowsAndStockProfileChooserDoNotRedirect() {
        window("another.app", STOCK_HOME);
        window(STOCK, STOCK + ".dashboard.DashboardActivity");
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
        window(STOCK, STOCK_HOME);
        window(STOCK, STOCK + ".profile.chooser.ProfileChooserActivity");
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void stockAppearingStillRedirectsWhenGhibliIsSelectedAsHome() {
        window(STOCK, STOCK_HOME);
        setResolvedHome(service.getPackageName(), MainActivity.class.getName());
        idle();
        assertNotNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void anotherSelectedLauncherIsLeftAlone() {
        window(STOCK, STOCK_HOME);
        setResolvedHome("another.launcher", "another.launcher.Home");
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    @Test public void unbindingCancelsPendingRedirect() {
        window(STOCK, STOCK_HOME);
        service.onUnbind(new Intent());
        idle();
        assertNull(Shadows.shadowOf(service).getNextStartedActivity());
    }

    private void setResolvedHome(String packageName, String activityName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = activityName;
        Shadows.shadowOf(service.getPackageManager()).setResolveInfosForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), List.of(info));
    }

    private void window(String packageName, String className) {
        window(packageName, className, SystemClock.uptimeMillis());
    }

    private void window(String packageName, String className, long eventTime) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
        event.setPackageName(packageName);
        event.setClassName(className);
        event.setEventTime(eventTime);
        service.onAccessibilityEvent(event);
    }

    private void idle() {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
    }
}

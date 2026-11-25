package ua.ghibli.tv.launcher;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowRoleManager;
import ua.ghibli.tv.launcher.apps.HomeRedirectService;
import ua.ghibli.tv.launcher.apps.HomeScreenSetup;
import ua.ghibli.tv.launcher.audio.AudioController;
import ua.ghibli.tv.launcher.panels.SettingsPanel;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class HomeScreenSetupTest {
    private static final ComponentName STOCK_HOME = new ComponentName("com.google.android.apps.tv.launcherx",
            "com.google.android.apps.tv.launcherx.home.HomeActivity");
    private ActivityController<TestActivity> controller;
    private TestActivity activity;
    private HomeScreenSetup setup;

    public static class TestActivity extends Activity {
        final List<Intent> opened = new ArrayList<>();
        final List<String> blocked = new ArrayList<>();
        int lastRequestCode;
        boolean windowFocused;
        @Override public boolean hasWindowFocus() { return windowFocused; }
        @Override public void startActivity(Intent intent) {
            if (blocked.contains(intent.getAction())) throw new ActivityNotFoundException(intent.getAction());
            opened.add(intent);
        }
        @Override public void startActivityForResult(Intent intent, int code) {
            lastRequestCode = code;
            startActivity(intent);
        }
    }

    @Before public void start() {
        controller = Robolectric.buildActivity(TestActivity.class).setup().visible();
        activity = controller.get();
        activity.getSharedPreferences("home_screen_setup", Context.MODE_PRIVATE).edit().clear().commit();
        setup = new HomeScreenSetup(activity);
        homePackage("another.launcher");
    }

    @After public void stop() {
        setup.close();
        controller.pause().stop().destroy();
    }

    private void homePackage(String name) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = name;
        info.activityInfo.name = name + ".HomeActivity";
        Shadows.shadowOf(activity.getPackageManager()).setResolveInfosForIntent(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), List.of(info));
    }

    @Test public void firstRunOffersChoiceOnceAndLaterDoesNotOpenSystemUi() {
        setup.showFirstRunPrompt();
        AlertDialog prompt = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(prompt);
        assertTrue(prompt.isShowing());
        prompt.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertFalse(prompt.isShowing());
        assertTrue(activity.opened.isEmpty());
        new HomeScreenSetup(activity).showFirstRunPrompt();
        assertSame(prompt, ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test public void firstRunPositiveButtonOpensHomeChoice() {
        setup.showFirstRunPrompt();
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Settings.ACTION_HOME_SETTINGS, activity.opened.get(0).getAction());
    }

    @Test public void defaultLauncherDoesNotShowFirstRunSuggestion() {
        homePackage(activity.getPackageName());
        assertTrue(setup.isDefaultHome());
        setup.showFirstRunPrompt();
        assertNull(ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test @Config(sdk = {28, 35}) public void homeSettingsFallbackWorksWhenRoleUnavailable() {
        setup.openSelection();
        assertEquals(Settings.ACTION_HOME_SETTINGS, activity.opened.get(0).getAction());
    }

    @Test public void missingHomeSettingsFallsBackToDefaultApps() {
        activity.blocked.add(Settings.ACTION_HOME_SETTINGS);
        setup.openSelection();
        assertEquals(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, activity.opened.get(0).getAction());
    }

    @Test public void missingSystemChoosersShowsInstructionsWithoutCrashing() {
        activity.blocked.add(Settings.ACTION_HOME_SETTINGS);
        activity.blocked.add(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
        setup.openSelection();
        assertTrue(activity.opened.isEmpty());
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("домашнього екрана"));
    }

    @Test @Config(sdk = 35) public void availableHomeRoleUsesSystemConsentRequest() {
        RoleManager manager = activity.getSystemService(RoleManager.class);
        ShadowRoleManager roles = Shadows.shadowOf(manager);
        roles.addAvailableRole(RoleManager.ROLE_HOME);
        setup.openSelection();
        assertEquals("android.app.role.action.REQUEST_ROLE", activity.opened.get(0).getAction());
        assertEquals(RoleManager.ROLE_HOME, activity.opened.get(0).getStringExtra("android.intent.extra.ROLE_NAME"));
    }

    @Test public void settingsRowReportsStatusAndCanOpenChoiceAgain() {
        PreferencesManager prefs = new PreferencesManager(activity);
        AudioController audio = new AudioController(activity, prefs);
        try {
            SettingsPanel panel = new SettingsPanel(activity, prefs, audio);
            View group = findText(panel, "Домашній екран");
            assertNotNull(group);
            ((View) group.getParent()).performClick();
            View row = findText(panel, "Основний лаунчер");
            assertNotNull(row);
            panel.setHomeScreenActive(true);
            assertNotNull(findText(panel, "Ghibli · вибрано"));
            panel.setHomeScreenActive(false);
            assertNotNull(findText(panel, "Зробити основним"));
            panel.setOnSettingsActionListener(new SettingsPanel.OnSettingsActionListener() {
                public void onHomeScreenRequested() { setup.openSelection(); }
                public void onHomeRedirectRequested() { setup.showRedirectSettings(); }
                public void onUpdatesRequested() {}
                public void onResourcesChanged() {}
                public void onSpiritsRequested() {}
                public void onAmbientRequested() {}
                public void onSystemSettingsRequested(String target) {}
                public void onRefreshAppsRequested() {}
                public void onAboutRequested() {}
                public void onCloseRequested() {}
            });
            ((View) row.getParent()).performClick();
            assertEquals(Settings.ACTION_HOME_SETTINGS, activity.opened.get(0).getAction());
            View redirectRow = findText(panel, "Ghibli після Home");
            assertNotNull(redirectRow);
            panel.setHomeRedirectEnabled(true);
            assertNotNull(findText(panel, "Дозволено"));
            ((View) redirectRow.getParent()).performClick();
            AlertDialog redirect = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(redirect.isShowing());
            redirect.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, activity.opened.get(1).getAction());
        } finally { audio.release(); }
    }

    @Test public void focusReturnFromTvSettingsShowsInstructionsEvenWhenGhibliIsSelected() {
        setup.openSelection();
        homePackage(activity.getPackageName());
        registerStockHome();
        activity.windowFocused = true;
        setup.onWindowFocus();
        AlertDialog result = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(result);
        assertTrue(result.isShowing());
        assertEquals(activity.getString(R.string.home_selection_check_google_tv), Shadows.shadowOf(result).getMessage());
        clickPositive(result);
        assertEquals(activity.getString(R.string.home_redirect_setup),
                Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getMessage());
    }

    @Test public void returningFromHomeSettingsAfterRecreationReadsResultOnce() {
        setup.openSelection();
        recreateSetup();
        setup.onResume();
        AlertDialog result = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(result);
        assertTrue(result.isShowing());
        result.dismiss();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        setup.onResume();
        assertFalse(result.isShowing());
        assertSame(result, ShadowAlertDialog.getLatestAlertDialog());
    }

    @Test public void enabledRedirectIsVerifiedByReturningThroughService() {
        homePackage(activity.getPackageName());
        registerStockHome();
        setup.showRedirectSettings();
        clickPositive(ShadowAlertDialog.getLatestAlertDialog());
        enableRedirect();
        setup.onWindowFocus();
        AlertDialog test = ShadowAlertDialog.getLatestAlertDialog();
        clickPositive(test);
        assertTrue(activity.opened.get(1).hasCategory(Intent.CATEGORY_HOME));
        assertEquals(STOCK_HOME, activity.opened.get(1).getComponent());
        setup.onRedirectedHome();
        activity.windowFocused = true;
        setup.onWindowFocus();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        assertEquals(activity.getString(R.string.home_redirect_test_passed),
                Shadows.shadowOf(ShadowAlertDialog.getLatestAlertDialog()).getTitle());
    }

    private void recreateSetup() {
        Bundle state = new Bundle();
        setup.saveState(state);
        setup.close();
        controller.recreate();
        activity = controller.get();
        setup = new HomeScreenSetup(activity, state);
        homePackage("another.launcher");
    }

    private void enableRedirect() {
        AccessibilityServiceInfo service = new AccessibilityServiceInfo();
        ResolveInfo info = new ResolveInfo();
        info.serviceInfo = new ServiceInfo();
        info.serviceInfo.packageName = activity.getPackageName();
        info.serviceInfo.name = HomeRedirectService.class.getName();
        org.robolectric.util.ReflectionHelpers.setField(service, "mResolveInfo", info);
        Shadows.shadowOf(activity.getSystemService(AccessibilityManager.class))
                .setEnabledAccessibilityServiceList(List.of(service));
    }

    private void registerStockHome() {
        ActivityInfo info = new ActivityInfo();
        info.packageName = STOCK_HOME.getPackageName();
        info.name = STOCK_HOME.getClassName();
        info.exported = true;
        Shadows.shadowOf(activity.getPackageManager()).addOrUpdateActivity(info);
    }

    private static void clickPositive(AlertDialog dialog) {
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private static View findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}

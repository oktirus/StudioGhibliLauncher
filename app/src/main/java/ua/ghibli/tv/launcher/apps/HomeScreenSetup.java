package ua.ghibli.tv.launcher.apps;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import ua.ghibli.tv.launcher.R;

/** User-approved HOME selection, with a one-time suggestion after first launch. */
public final class HomeScreenSetup {
    private static final String TAG = "HomeScreenSetup";
    private static final String PROMPT_SHOWN = "prompt_shown";
    private static final int REQUEST_HOME_ROLE = 501;
    private static final String STATE_AWAITING_ROLE = "homeSetup.awaitingRole";
    private static final String STATE_AWAITING_SETTINGS = "homeSetup.awaitingSettings";
    private static final String STATE_AWAITING_REDIRECT_ENABLE = "homeSetup.awaitingRedirectEnable";
    private static final String STATE_REDIRECT_TEST_DEADLINE = "homeSetup.redirectTestDeadline";
    private static final long REDIRECT_TEST_TIMEOUT_MS = 3_000;
    private final Activity activity;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable redirectTestTimeout = this::reportRedirectTestTimeout;
    private AlertDialog prompt;
    private boolean awaitingRoleResult;
    private boolean awaitingSettingsResult;
    private boolean awaitingRedirectEnable;
    private long redirectTestDeadline;

    public HomeScreenSetup(Activity activity) {
        this(activity, null);
    }

    public HomeScreenSetup(Activity activity, Bundle savedState) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("home_screen_setup", Context.MODE_PRIVATE);
        if (savedState != null) {
            awaitingRoleResult = savedState.getBoolean(STATE_AWAITING_ROLE);
            awaitingSettingsResult = savedState.getBoolean(STATE_AWAITING_SETTINGS);
            awaitingRedirectEnable = savedState.getBoolean(STATE_AWAITING_REDIRECT_ENABLE);
            redirectTestDeadline = savedState.getLong(STATE_REDIRECT_TEST_DEADLINE);
        }
    }

    public boolean isDefaultHome() {
        return activity.getPackageName().equals(resolvedHomePackage(activity));
    }

    static String resolvedHomePackage(Context context) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = context.getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null && resolved.activityInfo != null
                ? resolved.activityInfo.packageName : null;
    }

    public void showRedirectSettings() {
        boolean enabled = HomeRedirectService.isEnabled(activity);
        boolean supported = HomeRedirectService.canConfigure(activity);
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.home_redirect_title)
                .setNegativeButton(android.R.string.cancel, null);
        if (enabled && supported) {
            dialog.setMessage(R.string.home_redirect_test_help)
                    .setPositiveButton(R.string.home_redirect_test, (d, which) -> startRedirectTest())
                    .setNeutralButton(R.string.home_redirect_accessibility, (d, which) -> openAccessibilitySettings());
        } else {
            dialog.setMessage(supported ? R.string.home_redirect_setup : R.string.home_redirect_unsupported)
                    .setPositiveButton(R.string.home_redirect_open_settings, (d, which) -> openAccessibilitySettings());
        }
        showPrompt(dialog);
    }

    private void openAccessibilitySettings() {
        awaitingRedirectEnable = !HomeRedirectService.isEnabled(activity) && HomeRedirectService.canConfigure(activity);
        try {
            activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException error) {
            awaitingRedirectEnable = false;
            showRedirectFailure(R.string.home_redirect_settings_unavailable);
        }
    }

    private void startRedirectTest() {
        if (!HomeRedirectService.isEnabled(activity)) {
            showRedirectFailure(R.string.home_redirect_not_enabled);
            return;
        }
        if (!HomeRedirectService.canConfigure(activity)) {
            showRedirectFailure(R.string.home_redirect_unsupported);
            return;
        }
        redirectTestDeadline = SystemClock.uptimeMillis() + REDIRECT_TEST_TIMEOUT_MS;
        try {
            activity.startActivity(HomeRedirectService.stockHomeIntent());
            scheduleRedirectTestTimeout();
        } catch (ActivityNotFoundException | SecurityException error) {
            redirectTestDeadline = 0;
            showRedirectFailure(R.string.home_redirect_test_failed);
        }
    }

    public void onRedirectedHome() {
        if (redirectTestDeadline == 0) return;
        redirectTestDeadline = 0;
        mainHandler.removeCallbacks(redirectTestTimeout);
        showPrompt(new AlertDialog.Builder(activity)
                .setTitle(R.string.home_redirect_test_passed)
                .setMessage(R.string.home_redirect_test_passed_help)
                .setPositiveButton(android.R.string.ok, null));
    }

    public void onWindowFocus() {
        reportSettingsReturn();
        if (redirectTestDeadline != 0) scheduleRedirectTestTimeout();
    }

    private void scheduleRedirectTestTimeout() {
        mainHandler.removeCallbacks(redirectTestTimeout);
        long remaining = redirectTestDeadline - SystemClock.uptimeMillis();
        mainHandler.postDelayed(redirectTestTimeout, Math.max(0L, remaining));
    }

    private void reportRedirectTestTimeout() {
        if (redirectTestDeadline == 0 || !activity.hasWindowFocus()) return;
        redirectTestDeadline = 0;
        showRedirectFailure(R.string.home_redirect_test_failed);
    }

    private void showRedirectFailure(int message) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.home_redirect_failure_title)
                .setMessage(message)
                .setNegativeButton("Пізніше", null);
        if (message == R.string.home_redirect_test_failed && HomeRedirectService.canConfigure(activity)) {
            dialog.setPositiveButton(R.string.home_redirect_test, (d, which) -> startRedirectTest())
                    .setNeutralButton(R.string.home_redirect_accessibility, (d, which) -> openAccessibilitySettings());
        } else {
            dialog.setPositiveButton(R.string.home_redirect_open_settings, (d, which) -> openAccessibilitySettings());
        }
        showPrompt(dialog);
    }

    private void showPrompt(AlertDialog.Builder builder) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (prompt != null) prompt.dismiss();
        prompt = builder.create();
        prompt.setOnDismissListener(dialog -> {
            if (prompt == dialog) prompt = null;
        });
        prompt.show();
    }

    public void saveState(Bundle outState) {
        outState.putBoolean(STATE_AWAITING_ROLE, awaitingRoleResult);
        outState.putBoolean(STATE_AWAITING_SETTINGS, awaitingSettingsResult);
        outState.putBoolean(STATE_AWAITING_REDIRECT_ENABLE, awaitingRedirectEnable);
        outState.putLong(STATE_REDIRECT_TEST_DEADLINE, redirectTestDeadline);
    }

    public void showFirstRunPrompt() {
        if (activity.isFinishing() || activity.isDestroyed() || isPromptShowing()
                || preferences.getBoolean(PROMPT_SHOWN, false)) return;
        if (isDefaultHome()) {
            preferences.edit().putBoolean(PROMPT_SHOWN, true).apply();
            return;
        }
        showPrompt(new AlertDialog.Builder(activity)
                .setTitle("Зробити Ghibli домашнім екраном?")
                .setMessage("У системному списку обери «Ghibli · Вітер над долиною» як домашній екран. "
                        + "Якщо з’явиться вибір «Лише раз» або «Завжди», обери «Завжди». "
                        + "Після повернення перевіримо, чи застосувався вибір.")
                .setPositiveButton("Обрати", (dialog, which) -> openSelection())
                .setNegativeButton("Пізніше", null));
        preferences.edit().putBoolean(PROMPT_SHOWN, true).apply();
    }

    public void openSelection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roles = activity.getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)
                    && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    awaitingRoleResult = true;
                    activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE);
                    return;
                } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException error) {
                    awaitingRoleResult = false;
                    Log.w(TAG, "HOME role request unavailable; trying system settings", error);
                }
            }
        }
        openSystemSettings();
    }

    private void openSystemSettings() {
        for (String action : new String[]{Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS}) {
            awaitingSettingsResult = true;
            try {
                activity.startActivity(new Intent(action));
                return;
            } catch (ActivityNotFoundException | SecurityException error) {
                awaitingSettingsResult = false;
                Log.w(TAG, "System settings unavailable: " + action, error);
            }
        }
        Toast.makeText(activity, "Не вдалося відкрити вибір домашнього екрана. "
                + "Перевірте його в системних налаштуваннях телевізора.", Toast.LENGTH_LONG).show();
    }

    public boolean onActivityResult(int requestCode) {
        if (requestCode != REQUEST_HOME_ROLE || !awaitingRoleResult) return false;
        awaitingRoleResult = false;
        showSelectionResult();
        return true;
    }

    public void onResume() {
        reportSettingsReturn();
    }

    private void reportSettingsReturn() {
        if (awaitingSettingsResult) {
            awaitingSettingsResult = false;
            showSelectionResult();
        }
        if (awaitingRedirectEnable) {
            awaitingRedirectEnable = false;
            if (HomeRedirectService.isEnabled(activity)) showRedirectSettings();
            else showRedirectFailure(R.string.home_redirect_not_enabled);
        }
    }

    private void showSelectionResult() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        if (isDefaultHome()) {
            boolean canRedirect = HomeRedirectService.canConfigure(activity);
            AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.home_selection_saved)
                    .setMessage(canRedirect ? R.string.home_selection_check_google_tv : R.string.home_selection_check)
                    .setNegativeButton(android.R.string.ok, null);
            if (canRedirect) {
                dialog.setPositiveButton(R.string.home_redirect_title, (d, which) -> showRedirectSettings());
            }
            showPrompt(dialog);
            return;
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(activity)
                .setTitle("Домашній екран ще не змінено")
                .setNegativeButton("Пізніше", null);
        if (HomeRedirectService.canConfigure(activity)) {
            dialog.setMessage(R.string.home_redirect_offer)
                    .setPositiveButton(R.string.home_redirect_title, (d, which) -> showRedirectSettings())
                    .setNeutralButton("Налаштування HOME", (d, which) -> openSystemSettings());
        } else {
            dialog.setMessage("Android поки не відкриває Ghibli як домашній екран. "
                            + "У налаштуваннях домашнього екрана обери «Ghibli · Вітер над долиною». "
                            + "Після вибору перевір кнопку Home. Якщо вибір не зберігається, "
                            + "потрібно перевірити обмеження конкретної прошивки телевізора.")
                    .setPositiveButton("Налаштування HOME", (d, which) -> openSystemSettings());
        }
        showPrompt(dialog);
    }

    public void close() {
        awaitingRoleResult = false;
        awaitingSettingsResult = false;
        awaitingRedirectEnable = false;
        redirectTestDeadline = 0;
        mainHandler.removeCallbacks(redirectTestTimeout);
        if (prompt != null) prompt.dismiss();
    }

    public boolean isPromptShowing() {
        return prompt != null && prompt.isShowing();
    }
}

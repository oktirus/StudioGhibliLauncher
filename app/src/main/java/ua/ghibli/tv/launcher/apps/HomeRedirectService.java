package ua.ghibli.tv.launcher.apps;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import ua.ghibli.tv.launcher.MainActivity;

/** Opt-in redirect when Google TV brings its stock Home activity to the foreground. */
public final class HomeRedirectService extends AccessibilityService {
    public static final String ACTION_REDIRECTED_HOME =
            "ua.ghibli.tv.valley.action.REDIRECTED_HOME";
    private static final String GOOGLE_TV = "com.google.android.apps.tv.launcherx";
    private static final String GOOGLE_HOME = GOOGLE_TV + ".home.HomeActivity";
    private static final long REDIRECT_DELAY_MS = 180;
    private static final long DUPLICATE_WINDOW_MS = 1_000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable redirect = this::openGhibliValley;
    private long lastWindowEventAt;
    private long lastRedirectAt = -DUPLICATE_WINDOW_MS;

    public static boolean isEnabled(Context context) {
        AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        if (manager == null) return false;
        for (AccessibilityServiceInfo configured :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            ServiceInfo service = configured.getResolveInfo().serviceInfo;
            if (context.getPackageName().equals(service.packageName)
                    && HomeRedirectService.class.getName().equals(service.name)) return true;
        }
        return false;
    }

    static Intent stockHomeIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setComponent(new ComponentName(GOOGLE_TV, GOOGLE_HOME))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    static boolean canConfigure(Context context) {
        String selectedHome = HomeScreenSetup.resolvedHomePackage(context);
        return GOOGLE_TV.equals(selectedHome) || (context.getPackageName().equals(selectedHome)
                && context.getPackageManager().resolveActivity(stockHomeIntent(), 0) != null);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        boolean stockWindow = TextUtils.equals(GOOGLE_TV, event.getPackageName());
        boolean ghibliHome = TextUtils.equals(getPackageName(), event.getPackageName())
                && TextUtils.equals(MainActivity.class.getName(), event.getClassName());
        long eventTime = event.getEventTime();
        if ((!stockWindow && !ghibliHome) || eventTime < lastWindowEventAt) return;
        lastWindowEventAt = eventTime;

        if (ghibliHome) {
            if (eventTime >= lastRedirectAt) {
                mainHandler.removeCallbacks(redirect);
                lastRedirectAt = -DUPLICATE_WINDOW_MS;
            }
            return;
        }
        mainHandler.removeCallbacks(redirect);
        if (TextUtils.equals(GOOGLE_HOME, event.getClassName())
                && SystemClock.uptimeMillis() - lastRedirectAt >= DUPLICATE_WINDOW_MS) {
            mainHandler.postDelayed(redirect, REDIRECT_DELAY_MS);
        }
    }

    private void openGhibliValley() {
        String selectedHome = HomeScreenSetup.resolvedHomePackage(this);
        if (!GOOGLE_TV.equals(selectedHome) && !getPackageName().equals(selectedHome)) return;
        lastRedirectAt = SystemClock.uptimeMillis();
        try {
            startActivity(new Intent(this, MainActivity.class)
                    .setAction(ACTION_REDIRECTED_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException error) {
            Log.w("HomeRedirect", "Could not open Studio Ghibli launcher", error);
        }
    }

    @Override public void onInterrupt() {
        mainHandler.removeCallbacks(redirect);
    }

    @Override public boolean onUnbind(Intent intent) {
        mainHandler.removeCallbacks(redirect);
        return super.onUnbind(intent);
    }

    @Override public void onDestroy() {
        mainHandler.removeCallbacks(redirect);
        super.onDestroy();
    }
}

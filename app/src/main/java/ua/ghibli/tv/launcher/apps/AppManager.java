package ua.ghibli.tv.launcher.apps;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;

import java.text.Collator;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AppManager {
    public interface OnAppsReloadedListener {
        void onAppsReloaded();
    }

    private final Context context;
    private final PreferencesManager prefsManager;
    private final Map<String, AppItem> allAppsMap = new LinkedHashMap<>();
    private final List<AppItem> sortedAppsList = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1), new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile long reloadGeneration = 0;
    private volatile boolean disposed;

    public AppManager(Context context, PreferencesManager prefsManager) {
        this.context = context.getApplicationContext();
        this.prefsManager = prefsManager;
        for (CatalogData.AppDefinition def : CatalogData.KNOWN_APPS) {
            allAppsMap.put(def.pkg, new AppItem(def.pkg, def.name, def.kind, def.description, null));
        }
        sortedAppsList.addAll(allAppsMap.values());
    }

    public void reloadApps() {
        reloadAppsAsync(null);
    }

    public void reloadAppsAsync(OnAppsReloadedListener listener) {
        final long gen;
        synchronized (this) {
            if (disposed) return;
            gen = ++reloadGeneration;
        }
        try {
            executor.execute(() -> {
                if (disposed || gen != reloadGeneration) return;
                try {
                    doReload(gen, listener);
                } catch (RuntimeException error) {
                    android.util.Log.w("GhibliApps", "App inventory reload failed", error);
                }
            });
        } catch (RejectedExecutionException ignored) {
            // Activity disposal can race with a request; no stale UI callback is allowed.
        }
    }

    private void doReload(long gen, OnAppsReloadedListener listener) {
        PackageManager pm = context.getPackageManager();
        String currentPkg = context.getPackageName();

        Map<String, ResolveInfo> resolved = new TreeMap<>();
        for (String category : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent intent = new Intent(Intent.ACTION_MAIN).addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            for (ResolveInfo info : list) {
                if (info.activityInfo == null) continue;
                String pkg = info.activityInfo.packageName;
                if (!pkg.equals(currentPkg) && info.activityInfo.exported
                        && info.activityInfo.enabled && info.activityInfo.applicationInfo != null
                        && info.activityInfo.applicationInfo.enabled
                        && !resolved.containsKey(pkg)) {
                    resolved.put(pkg, info);
                }
            }
        }

        Map<String, AppItem> newAppsMap = new LinkedHashMap<>();
        for (Map.Entry<String, ResolveInfo> entry : resolved.entrySet()) {
            if (disposed || gen != reloadGeneration) return;
            String pkg = entry.getKey();
            ResolveInfo info = entry.getValue();
            CharSequence label = info.loadLabel(pm);
            String name = label != null ? label.toString() : pkg;
            String kind = CatalogData.matchKind(pkg, name);
            Drawable icon = null;
            try {
                if (kind == null || kind.isEmpty()) {
                    icon = info.loadIcon(pm);
                }
            } catch (Exception ignored) {}
            String desc = CatalogData.matchDescription(pkg, name);
            newAppsMap.put(pkg, new AppItem(pkg, name, kind, desc, icon));
        }

        if (newAppsMap.isEmpty()) {
            for (CatalogData.AppDefinition def : CatalogData.KNOWN_APPS) {
                newAppsMap.put(def.pkg, new AppItem(def.pkg, def.name, def.kind, def.description, null));
            }
        }

        List<AppItem> sortedList = new ArrayList<>(newAppsMap.values());
        Collator ukCollator = Collator.getInstance(new Locale("uk", "UA"));
        Collections.sort(sortedList, (a, b) -> ukCollator.compare(a.getName(), b.getName()));

        synchronized (this) {
            if (disposed || gen != reloadGeneration) return;
            mainHandler.post(() -> publishReload(gen, newAppsMap, sortedList, listener));
        }
    }

    private synchronized void publishReload(long gen, Map<String, AppItem> newAppsMap,
            List<AppItem> sortedList, OnAppsReloadedListener listener) {
        if (disposed || gen != reloadGeneration) return;
        List<String> pins = prefsManager.getPins();
        if (pins != null) {
            List<String> repaired = new ArrayList<>(pins);
            if (repaired.removeIf(pkg -> pkg.startsWith("preview.") && !newAppsMap.containsKey(pkg))) {
                pins = repaired.isEmpty() ? null : repaired;
                if (pins != null) prefsManager.setPins(pins);
            }
        }
        if (pins == null) {
            pins = new ArrayList<>();
            String[] preferredKinds = {
                "uakino", "lampa", "kodi",
                "smarttube", "youtube", "megogo", "sweettv", "kyivstar", "jellyfin",
                "xplore", "netflix", "spotify", "crunchyroll", "vlc", "plex"
            };
            for (String prefKind : preferredKinds) {
                for (AppItem app : newAppsMap.values()) {
                    if (prefKind.equals(app.getKind()) && !pins.contains(app.getPackageName())) {
                        pins.add(app.getPackageName());
                        break;
                    }
                }
            }
            if (pins.size() < 6) {
                for (AppItem app : newAppsMap.values()) {
                    if (!pins.contains(app.getPackageName())) {
                        pins.add(app.getPackageName());
                        if (pins.size() >= 6) break;
                    }
                }
            }
            prefsManager.setPins(pins);
        }

        Set<String> pinSet = new HashSet<>(pins);
        for (AppItem app : newAppsMap.values()) {
            app.setPinned(pinSet.contains(app.getPackageName()));
        }

        allAppsMap.clear();
        allAppsMap.putAll(newAppsMap);
        sortedAppsList.clear();
        sortedAppsList.addAll(sortedList);
        if (listener != null) listener.onAppsReloaded();
    }

    public synchronized void release() {
        disposed = true;
        reloadGeneration++;
        mainHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        allAppsMap.clear();
        sortedAppsList.clear();
    }

    public synchronized List<AppItem> getAllApps() {
        return new ArrayList<>(sortedAppsList);
    }

    public synchronized AppItem getApp(String packageName) {
        return allAppsMap.get(packageName);
    }

    public synchronized List<AppItem> getPinnedApps() {
        List<String> pins = prefsManager.getPins();
        if (pins == null) return Collections.emptyList();
        List<AppItem> result = new ArrayList<>();
        for (String pkg : pins) {
            AppItem app = allAppsMap.get(pkg);
            if (app != null) result.add(app);
        }
        return result;
    }

    public synchronized List<AppItem> getDockApps() {
        List<AppItem> pinned = getPinnedApps();
        if (pinned.size() > 6) {
            return new ArrayList<>(pinned.subList(0, 6));
        }
        return pinned;
    }

    public synchronized boolean togglePin(String packageName) {
        List<String> pins = prefsManager.getPins();
        if (pins == null) pins = new ArrayList<>();
        else pins = new ArrayList<>(pins);

        boolean nowPinned;
        if (pins.contains(packageName)) {
            pins.remove(packageName);
            nowPinned = false;
        } else {
            pins.add(packageName);
            nowPinned = true;
        }
        prefsManager.setPins(pins);

        AppItem item = allAppsMap.get(packageName);
        if (item != null) item.setPinned(nowPinned);

        return nowPinned;
    }

    public synchronized boolean moveFavorite(String packageName, int direction) {
        List<String> pins = prefsManager.getPins();
        if (pins == null) return false;
        pins = new ArrayList<>(pins);

        List<AppItem> visible = getPinnedApps();
        int index = -1;
        for (int i = 0; i < visible.size(); i++) {
            if (packageName.equals(visible.get(i).getPackageName())) { index = i; break; }
        }
        int target = index + direction;
        if (index < 0 || target < 0 || target >= visible.size()) return false;

        Collections.swap(pins, pins.indexOf(packageName),
                pins.indexOf(visible.get(target).getPackageName()));
        prefsManager.setPins(pins);
        return true;
    }

    public void launchApp(String packageName) {
        mainHandler.post(() -> {
            PackageManager pm = context.getPackageManager();
            Intent launch = pm.getLeanbackLaunchIntentForPackage(packageName);
            if (launch == null) launch = pm.getLaunchIntentForPackage(packageName);
            try {
                if (launch == null) {
                    Toast.makeText(context, "Запуск " + packageName + " (демо)", Toast.LENGTH_SHORT).show();
                    return;
                }
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launch);
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(context, "Не вдалося відкрити застосунок", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void openSettings(String target) {
        mainHandler.post(() -> {
            String action = "wifi".equals(target) ?
                    android.provider.Settings.ACTION_WIFI_SETTINGS :
                    android.provider.Settings.ACTION_SETTINGS;
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(context, "Системні налаштування недоступні", Toast.LENGTH_SHORT).show();
            }
        });
    }
}

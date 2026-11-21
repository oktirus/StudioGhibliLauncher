package ua.ghibli.tv.launcher.updates;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** All UI state is main-thread confined; each worker request has its own cancellation token. */
public final class UpdateController {
    private static final String LAST_CHECK = "last_successful_check";
    private static final String AVAILABLE_CODE = "available_code";
    private static final String AVAILABLE_NAME = "available_name";
    private final Activity activity;
    private final BooleanSupplier canPrompt;
    private final Consumer<String> onAvailability;
    private final Supplier<GitHubReleaseClient> clients;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final android.content.SharedPreferences state;
    private GitHubReleaseClient request;
    private AlertDialog dialog;
    private ReleaseUpdate readyUpdate;
    private File readyApk;
    private boolean waitingForPermission;
    private boolean closed;
    private boolean busy;
    private long generation;

    public UpdateController(Activity activity, BooleanSupplier canPrompt, Consumer<String> onAvailability) {
        this(activity, canPrompt, onAvailability, GitHubReleaseClient::new);
    }

    UpdateController(Activity activity, BooleanSupplier canPrompt, Consumer<String> onAvailability,
                     Supplier<GitHubReleaseClient> clients) {
        this.activity = activity;
        this.canPrompt = canPrompt;
        this.onAvailability = onAvailability;
        this.clients = clients;
        state = activity.getSharedPreferences("launcher_updates", Context.MODE_PRIVATE);
    }

    public boolean isShowing() { return dialog != null && dialog.isShowing(); }

    public void restoreAvailability() {
        try {
            long available = state.getLong(AVAILABLE_CODE, 0);
            if (available == 0) return;
            if (available > installedVersionCode()) {
                onAvailability.accept(state.getString(AVAILABLE_NAME, null));
            } else {
                state.edit().remove(AVAILABLE_CODE).remove(AVAILABLE_NAME).apply();
                onAvailability.accept(null);
            }
        } catch (PackageManager.NameNotFoundException error) {
            android.util.Log.w("GhibliUpdate", "Cannot read the installed version", error);
        }
    }

    private long installedVersionCode() throws PackageManager.NameNotFoundException {
        return UpdateInstaller.versionCode(activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0));
    }

    public void checkManually() {
        if (closed || busy) return;
        if (readyUpdate != null && readyApk != null && readyApk.isFile()) { showReady(); return; }
        check(false);
    }

    public void checkAutomatically() {
        long now = System.currentTimeMillis();
        long previous = state.getLong(LAST_CHECK, 0);
        if (!closed && !busy && !isShowing() && (now < previous || now - previous >= 24L * 60 * 60 * 1000)) {
            check(true);
        }
    }

    private void check(boolean silent) {
        long token = beginRequest();
        GitHubReleaseClient client = request;
        if (!silent) showProgress("Перевірка оновлень…");
        worker.execute(() -> {
            try {
                ReleaseUpdate update = client.latest();
                long current = installedVersionCode();
                main.post(() -> {
                    if (!active(token)) return;
                    busy = false;
                    dismissDialog();
                    boolean available = update.versionCode > current;
                    android.content.SharedPreferences.Editor preferences =
                            state.edit().putLong(LAST_CHECK, System.currentTimeMillis());
                    if (available) {
                        preferences.putLong(AVAILABLE_CODE, update.versionCode).putString(AVAILABLE_NAME, update.versionName);
                    } else {
                        preferences.remove(AVAILABLE_CODE).remove(AVAILABLE_NAME);
                    }
                    preferences.apply();
                    onAvailability.accept(available ? update.versionName : null);
                    if (silent && !canPrompt.getAsBoolean()) return;
                    if (available) showAvailable(update);
                    else if (!silent) message("Оновлення", "Встановлено найновішу версію Ghibli.");
                });
            } catch (Exception error) { reportError(token, error, silent); }
        });
    }

    private long beginRequest() {
        dismissDialog();
        if (request != null) request.cancel();
        request = clients.get();
        busy = true;
        return ++generation;
    }

    private boolean active(long token) { return !closed && token == generation && !activity.isFinishing() && !activity.isDestroyed(); }

    private void showAvailable(ReleaseUpdate update) {
        dialog = new AlertDialog.Builder(activity).setTitle("Доступно Ghibli " + update.versionName)
                .setMessage(update.notes + "\n\nРозмір: " + (update.bytes / 1024 / 1024) + " МіБ. Дані лаунчера збережуться.")
                .setPositiveButton("Завантажити", (d, w) -> download(update))
                .setNegativeButton("Пізніше", null).create();
        dialog.show();
    }

    private void download(ReleaseUpdate update) {
        long token = beginRequest();
        GitHubReleaseClient client = request;
        showProgress("Завантаження оновлення…");
        worker.execute(() -> {
            try {
                File apk = client.download(update, new File(activity.getCacheDir(), "updates"), percent -> main.post(() -> {
                    if (active(token) && dialog != null) dialog.setMessage("Завантаження оновлення: " + percent + "%");
                }));
                UpdateInstaller.verifyPackage(activity, apk, update);
                main.post(() -> {
                    if (!active(token)) return;
                    busy = false;
                    readyUpdate = update;
                    readyApk = apk;
                    dismissDialog();
                    showReady();
                });
            } catch (Exception error) { reportError(token, error, false); }
        });
    }

    private void showReady() {
        dismissDialog();
        dialog = new AlertDialog.Builder(activity).setTitle("Оновлення готове")
                .setMessage("Ghibli " + readyUpdate.versionName + " завантажено та перевірено. Android попросить підтвердити встановлення.")
                .setPositiveButton("Встановити", (d, w) -> requestInstall())
                .setNegativeButton("Пізніше", null).create();
        dialog.show();
    }

    private void requestInstall() {
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            waitingForPermission = true;
            try {
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
            } catch (ActivityNotFoundException | SecurityException error) {
                waitingForPermission = false;
                message("Потрібен дозвіл", "Дозвольте встановлення оновлень із Ghibli в системних налаштуваннях телевізора.");
            }
            return;
        }
        install();
    }

    public void onResume() {
        if (!waitingForPermission || closed) return;
        waitingForPermission = false;
        if (activity.getPackageManager().canRequestPackageInstalls()) showReady();
        else Toast.makeText(activity, "Дозвіл не надано. Поточна версія працює далі.", Toast.LENGTH_LONG).show();
    }

    private void install() {
        long token = beginRequest();
        GitHubReleaseClient client = request;
        ReleaseUpdate update = readyUpdate;
        File apk = readyApk;
        showProgress("Передавання оновлення Android…");
        worker.execute(() -> {
            try {
                UpdateInstaller.install(activity, apk, update, client::isCancelled);
                main.post(() -> { if (active(token)) { busy = false; dismissDialog(); } });
            } catch (Exception error) { reportError(token, error, false); }
        });
    }

    private void showProgress(String text) {
        dialog = new AlertDialog.Builder(activity).setTitle("Оновлення Ghibli").setMessage(text)
                .setNegativeButton("Скасувати", (d, w) -> cancel()).create();
        dialog.setOnCancelListener(d -> cancel());
        dialog.show();
    }

    private void reportError(long token, Exception error, boolean silent) {
        main.post(() -> {
            if (!active(token)) return;
            busy = false;
            dismissDialog();
            readyUpdate = null;
            readyApk = null;
            String detail = error.getMessage();
            if (!silent) message("Оновлення не виконано", error instanceof java.io.IOException
                    && detail != null && !detail.contains("http") ? detail
                    : "Не вдалося перевірити або встановити оновлення. Спробуйте пізніше.");
            android.util.Log.w("GhibliUpdate", "Update request failed: " + error.getClass().getSimpleName());
        });
    }

    private void message(String title, String message) {
        dismissDialog();
        dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("Зрозуміло", null).create();
        dialog.show();
    }

    private void dismissDialog() { if (dialog != null) { dialog.dismiss(); dialog = null; } }

    public void cancel() {
        generation++;
        busy = false;
        if (request != null) request.cancel();
        dismissDialog();
    }

    public void close() {
        closed = true;
        cancel();
        main.removeCallbacksAndMessages(null);
        worker.shutdownNow();
    }
}

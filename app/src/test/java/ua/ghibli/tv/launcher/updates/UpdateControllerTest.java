package ua.ghibli.tv.launcher.updates;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Looper;
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
import org.robolectric.shadows.ShadowAlertDialog;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w963dp-h541dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UpdateControllerTest {
    private ActivityController<Activity> activity;
    private UpdateController updates;
    private final AtomicInteger clients = new AtomicInteger();
    private final AtomicBoolean canPrompt = new AtomicBoolean(true);
    private String availableVersion;

    @Before public void setup() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().visible();
        Activity installedApp = activity.get();
        PackageInfo installed = installedApp.getPackageManager().getPackageInfo(installedApp.getPackageName(), 0);
        installed.versionCode = 2;
        installed.versionName = "1.1.0";
        Shadows.shadowOf(installedApp.getPackageManager()).installPackage(installed);
        byte[] release = UpdateFixtures.release().toString().getBytes(StandardCharsets.UTF_8);
        byte[] manifest = UpdateFixtures.manifest().toString().getBytes(StandardCharsets.UTF_8);
        updates = new UpdateController(activity.get(), canPrompt::get, version -> availableVersion = version, () -> {
            clients.incrementAndGet();
            return new GitHubReleaseClient(url -> new UpdateFixtures.Response(url,
                    url.toString().equals(UpdateFixtures.API) ? release : manifest, 200));
        });
    }

    @After public void close() {
        if (updates != null) updates.close();
        activity.pause().stop().destroy();
    }

    private void finishWorker() throws Exception {
        Field field = UpdateController.class.getDeclaredField("worker");
        field.setAccessible(true);
        ((ExecutorService) field.get(updates)).submit(() -> {}).get(10, TimeUnit.SECONDS);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test public void manualCheckShowsOfferAfterResponseWithoutInstalling() throws Exception {
        updates.checkManually();
        finishWorker();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        assertTrue(Shadows.shadowOf(dialog).getTitle().toString().contains("1.2.0"));
        assertEquals("Завантажити", dialog.getButton(DialogInterface.BUTTON_POSITIVE).getText().toString());
        assertTrue(activity.get().getPackageManager().getPackageInstaller().getAllSessions().isEmpty());
        captureDialog(dialog, "updater-offer-963");
    }

    @Test public void cancelDiscardsLateResponseAndClosesProgress() throws Exception {
        updates.close();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch respond = new CountDownLatch(1);
        byte[] body = UpdateFixtures.release().toString().getBytes(StandardCharsets.UTF_8);
        updates = new UpdateController(activity.get(), canPrompt::get, version -> availableVersion = version,
                () -> new GitHubReleaseClient(url -> {
            entered.countDown();
            try {
                if (!respond.await(10, TimeUnit.SECONDS)) throw new java.io.IOException("Test response timed out");
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Test interrupted", error);
            }
            return new UpdateFixtures.Response(url, body, 200);
        }));
        try {
            updates.checkManually();
            assertTrue(entered.await(10, TimeUnit.SECONDS));
            captureDialog(ShadowAlertDialog.getLatestAlertDialog(), "updater-checking-963");
            updates.cancel();
        } finally { respond.countDown(); }
        finishWorker();
        assertFalse(updates.isShowing());
        assertNull(availableVersion);
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().isShowing());
    }

    @Test public void automaticCheckIsDailyEvenWithAPanelOpen() throws Exception {
        canPrompt.set(false);
        updates.checkAutomatically();
        finishWorker();
        assertEquals(1, clients.get());
        assertFalse(updates.isShowing());
        assertEquals("1.2.0", availableVersion);
        canPrompt.set(true);
        updates.checkAutomatically();
        finishWorker();
        updates.checkAutomatically();
        assertEquals(1, clients.get());
    }

    @Test public void silentResponseDoesNotInterruptNewlyOpenedModal() throws Exception {
        updates.checkAutomatically();
        canPrompt.set(false);
        finishWorker();
        assertFalse(updates.isShowing());
        assertEquals("1.2.0", availableVersion);
    }

    @Test public void noticeSurvivesReopenAndClearsOnceUpdateIsInstalled() throws Exception {
        canPrompt.set(false);
        updates.checkAutomatically();
        finishWorker();
        assertEquals("1.2.0", availableVersion);
        updates.close();
        availableVersion = null;
        updates = new UpdateController(activity.get(), canPrompt::get, version -> availableVersion = version);
        updates.restoreAvailability();
        assertEquals("1.2.0", availableVersion);

        Activity launcher = activity.get();
        PackageInfo installed = launcher.getPackageManager().getPackageInfo(launcher.getPackageName(), 0);
        installed.versionCode = 3;
        Shadows.shadowOf(launcher.getPackageManager()).installPackage(installed);
        updates.restoreAvailability();
        assertNull(availableVersion);
    }

    private void captureDialog(AlertDialog dialog, String name) throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));
        android.view.View view = dialog.getWindow().getDecorView();
        view.measure(android.view.View.MeasureSpec.makeMeasureSpec(620, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(360, android.view.View.MeasureSpec.EXACTLY));
        view.layout(0, 0, 620, 360);
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(620, 360, android.graphics.Bitmap.Config.ARGB_8888);
        try {
            view.draw(new android.graphics.Canvas(bitmap));
            java.io.File file = new java.io.File("build/reports/native-ui/" + name + ".png");
            file.getParentFile().mkdirs();
            try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output));
            }
        } finally { bitmap.recycle(); }
    }
}

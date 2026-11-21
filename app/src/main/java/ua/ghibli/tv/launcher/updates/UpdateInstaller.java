package ua.ghibli.tv.launcher.updates;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import java.io.*;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Uses Android's installer; never clears data or changes the user's HOME. */
public final class UpdateInstaller {
    private UpdateInstaller() {}

    public static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    @SuppressWarnings("deprecation")
    public static void verifyPackage(Context context, File apk, ReleaseUpdate update) throws IOException {
        PackageManager manager = context.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo installed;
        try { installed = manager.getPackageInfo(context.getPackageName(), flags); }
        catch (PackageManager.NameNotFoundException error) { throw new IOException("Не вдалося перевірити встановлену версію.", error); }
        if (archive == null || !context.getPackageName().equals(archive.packageName)
                || versionCode(archive) != update.versionCode || versionCode(archive) <= versionCode(installed)) {
            throw new IOException("APK має інший застосунок або невідповідну версію.");
        }
        Set<String> actual = signingCertificates(archive);
        if (actual.isEmpty() || !actual.equals(signingCertificates(installed))) {
            throw new IOException("Підпис APK відрізняється від установленого лаунчера. Встановлення скасовано.");
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signingCertificates(PackageInfo info) {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28
                ? (info.signingInfo == null ? null : info.signingInfo.getApkContentsSigners()) : info.signatures;
        Set<String> result = new HashSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                result.add(GitHubReleaseClient.hex(GitHubReleaseClient.sha256().digest(signature.toByteArray())));
            }
        }
        return result;
    }

    public static void install(Context context, File apk, ReleaseUpdate update, BooleanSupplier cancelled) throws IOException {
        verifyPackage(context, apk, update);
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        params.setSize(update.bytes);
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
        int sessionId = installer.createSession(params);
        boolean committed = false;
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            MessageDigest digest = GitHubReleaseClient.sha256();
            long bytes = 0;
            try (InputStream input = new FileInputStream(apk); OutputStream output = session.openWrite("base.apk", 0, update.bytes)) {
                byte[] buffer = new byte[32768];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new IOException("Операцію скасовано.");
                    bytes += count;
                    if (bytes > update.bytes) throw new IOException("Розмір APK змінився.");
                    digest.update(buffer, 0, count);
                    output.write(buffer, 0, count);
                }
                session.fsync(output);
            }
            GitHubReleaseClient.verifyDownload(update, bytes, digest.digest());
            if (cancelled.getAsBoolean()) throw new IOException("Операцію скасовано.");
            Intent result = new Intent(context, InstallResultReceiver.class)
                    .setAction(context.getPackageName() + ".INSTALL_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent callback = PendingIntent.getBroadcast(context, sessionId, result, flags);
            session.commit(callback.getIntentSender());
            committed = true;
        } finally {
            if (!committed) installer.abandonSession(sessionId);
        }
    }
}

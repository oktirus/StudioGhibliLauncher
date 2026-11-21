package ua.ghibli.tv.launcher.updates;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

/** Explicit PendingIntent target: the system supplies the confirmation Activity. */
public final class InstallResultReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                try {
                    context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (ActivityNotFoundException | SecurityException error) {
                    android.util.Log.w("GhibliUpdate", "Installer confirmation unavailable", error);
                }
            }
            Toast.makeText(context, "Телевізор не надав вікна підтвердження встановлення.", Toast.LENGTH_LONG).show();
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            Toast.makeText(context, "Лаунчер Ghibli оновлено.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "Оновлення не встановлено. Поточний лаунчер і його дані збережено.", Toast.LENGTH_LONG).show();
        }
    }
}

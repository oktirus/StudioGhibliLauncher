package ua.ghibli.tv.launcher.views;

import android.os.Debug;
import android.os.Handler;
import android.os.Process;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Samples the launcher process while the optional Home display is active. */
public final class LauncherResourceMonitor {
    private final TextView label;
    private final Handler mainHandler;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sampling;
    private int generation;

    public LauncherResourceMonitor(TextView label, Handler mainHandler) {
        this.label = label;
        this.mainHandler = mainHandler;
    }

    public void setActive(boolean active) {
        if (active == (sampling != null)) return;
        int sampleGeneration = ++generation;
        if (!active) {
            sampling.cancel(false);
            sampling = null;
            label.setVisibility(View.GONE);
            return;
        }
        label.setText("CPU … · RAM …");
        label.setVisibility(View.VISIBLE);
        sampling = worker.scheduleWithFixedDelay(new Runnable() {
            private long previousTime = SystemClock.elapsedRealtime();
            private long previousCpu = Process.getElapsedCpuTime();

            @Override public void run() {
                long wall = SystemClock.elapsedRealtime();
                long cpu = Process.getElapsedCpuTime();
                int cores = Runtime.getRuntime().availableProcessors();
                long interval = wall - previousTime;
                String cpuText = interval > 0 ? String.format(Locale.ROOT, "%.1f%%",
                        100.0 * Math.max(0L, cpu - previousCpu) / interval / cores) : "…";
                previousTime = wall;
                previousCpu = cpu;
                long pssKiB = Debug.getPss();
                String ramText = pssKiB > 0 ? Math.round(pssKiB / 1024.0) + " МіБ" : "…";
                String text = "CPU " + cpuText + " · RAM " + ramText;
                mainHandler.post(() -> {
                    if (sampleGeneration == generation) label.setText(text);
                });
            }
        }, 1, 3, TimeUnit.SECONDS);
    }

    public void close() {
        setActive(false);
        worker.shutdownNow();
    }
}

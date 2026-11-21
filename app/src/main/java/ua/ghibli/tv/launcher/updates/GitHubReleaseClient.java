package ua.ghibli.tv.launcher.updates;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** One cancellable request chain; never buffers an APK in memory. */
public final class GitHubReleaseClient {
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }
    private final ConnectionFactory connections;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile HttpURLConnection activeConnection;

    public boolean isCancelled() { return cancelled.get(); }

    public GitHubReleaseClient() { this(url -> (HttpURLConnection) url.openConnection()); }
    GitHubReleaseClient(ConnectionFactory connections) { this.connections = connections; }

    public void cancel() {
        cancelled.set(true);
        HttpURLConnection connection = activeConnection;
        if (connection != null) connection.disconnect();
    }

    public ReleaseUpdate latest() throws IOException, JSONException {
        JSONObject release = readJson("https://api.github.com/repos/" + ReleaseUpdate.REPOSITORY + "/releases/latest");
        return ReleaseUpdate.parse(release, readJson(ReleaseUpdate.manifestUrl(release)));
    }

    private JSONObject readJson(String url) throws IOException, JSONException {
        HttpURLConnection connection = open(url);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled();
                if (bytes.size() + count > 128 * 1024) throw new IOException("Завеликий опис оновлення.");
                bytes.write(buffer, 0, count);
            }
            return new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); activeConnection = null; }
    }

    public File download(ReleaseUpdate update, File directory, IntConsumer progress) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Не вдалося створити теку оновлень.");
        if (directory.getUsableSpace() < update.bytes + 5L * 1024 * 1024) throw new IOException("Недостатньо вільного місця.");
        File part = new File(directory, "candidate.apk.part");
        File complete = new File(directory, "candidate.apk");
        HttpURLConnection connection = open(update.apkUrl);
        boolean finished = false;
        try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(part)) {
            long length = connection.getContentLengthLong();
            if (length > 0 && length != update.bytes) throw new IOException("Сервер повідомив інший розмір APK.");
            MessageDigest digest = sha256();
            byte[] buffer = new byte[32768];
            long total = 0;
            int count, lastPercent = -1;
            while ((count = input.read(buffer)) != -1) {
                checkCancelled();
                total += count;
                if (total > update.bytes) throw new IOException("Завантажений APK перевищує очікуваний розмір.");
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                int percent = (int) (total * 100 / update.bytes);
                if (percent != lastPercent) { progress.accept(percent); lastPercent = percent; }
            }
            output.getFD().sync();
            verifyDownload(update, total, digest.digest());
            checkCancelled();
            finished = true;
        } finally {
            connection.disconnect(); activeConnection = null;
            if (!finished) Files.deleteIfExists(part.toPath());
        }
        Files.move(part.toPath(), complete.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return complete;
    }

    static void verifyDownload(ReleaseUpdate update, long bytes, byte[] digest) throws IOException {
        if (bytes != update.bytes || !hex(digest).equals(update.sha256)) {
            throw new IOException("Перевірка цілісності APK не пройшла. Встановлення скасовано.");
        }
    }

    public static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) text.append(String.format(Locale.ROOT, "%02x", value & 255));
        return text.toString();
    }

    static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private void checkCancelled() throws IOException {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new IOException("Операцію скасовано.");
    }

    private HttpURLConnection open(String address) throws IOException {
        URL url;
        try { url = URI.create(address).toURL(); }
        catch (IllegalArgumentException error) { throw new IOException("Некоректна адреса.", error); }
        for (int redirect = 0; redirect < 6; redirect++) {
            checkCancelled();
            String host = url.getHost();
            if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null
                    || !("github.com".equals(host) || "api.github.com".equals(host)
                    || host.endsWith(".githubusercontent.com"))) {
                throw new IOException("Завантаження дозволено лише через захищені адреси GitHub.");
            }
            HttpURLConnection connection = connections.open(url);
            activeConnection = connection;
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(20000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Oktirus-GhibliValley-Updater");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            int code;
            try { code = connection.getResponseCode(); }
            catch (IOException error) { connection.disconnect(); activeConnection = null; throw error; }
            if (code >= 300 && code <= 399) {
                String location = connection.getHeaderField("Location");
                connection.disconnect(); activeConnection = null;
                if (location == null) throw new IOException("Відсутня адреса перенаправлення.");
                url = new URL(url, location);
                continue;
            }
            if (code != 200) {
                connection.disconnect(); activeConnection = null;
                if (code == 404) throw new IOException("Оновлення ще не опубліковано.");
                if (code == 403 || code == 429) throw new IOException("GitHub тимчасово обмежив перевірки. Спробуйте пізніше.");
                throw new IOException("Не вдалося отримати оновлення (HTTP " + code + ").");
            }
            return connection;
        }
        throw new IOException("Забагато перенаправлень.");
    }
}

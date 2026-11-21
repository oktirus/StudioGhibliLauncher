package ua.ghibli.tv.launcher.updates;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;

/** The update manifest is paired with assets from one published GitHub release of StudioGhibliLauncher. */
public final class ReleaseUpdate {
    public static final String REPOSITORY = "oktirus/StudioGhibliLauncher";
    public static final String APPLICATION_ID = "ua.ghibli.tv.valley";
    public static final long MAX_APK_BYTES = 128L * 1024 * 1024;
    public final long versionCode;
    public final String versionName;
    public final String sha256;
    public final long bytes;
    public final String apkUrl;
    public final String notes;

    private ReleaseUpdate(long code, String name, String hash, long bytes, String url, String notes) {
        this.versionCode = code;
        this.versionName = name;
        this.sha256 = hash;
        this.bytes = bytes;
        this.apkUrl = url;
        this.notes = notes;
    }

    public static String manifestUrl(JSONObject release) throws IOException, JSONException {
        if (release.optBoolean("draft", false) || release.optBoolean("prerelease", false)) {
            throw new IOException("Не знайдено стабільного випуску.");
        }
        return assetUrl(release, "update.json", -1);
    }

    public static ReleaseUpdate parse(JSONObject release, JSONObject manifest) throws IOException, JSONException {
        manifestUrl(release);
        if (manifest.getInt("schemaVersion") != 1
                || !APPLICATION_ID.equals(manifest.getString("applicationId"))) {
            throw new IOException("Опис оновлення не відповідає цьому лаунчеру.");
        }
        long code = manifest.getLong("versionCode");
        long bytes = manifest.getLong("bytes");
        String name = manifest.getString("versionName").trim();
        String hash = manifest.getString("sha256").toLowerCase(Locale.ROOT);
        String apkName = manifest.getString("apkName");
        if (code <= 0 || code > Integer.MAX_VALUE || bytes <= 0 || bytes > MAX_APK_BYTES
                || name.isEmpty() || name.length() > 80 || !hash.matches("[0-9a-f]{64}")
                || !apkName.matches("[A-Za-z0-9._-]+\\.apk")) {
            throw new IOException("Некоректні дані випуску.");
        }
        String notes = manifest.optString("notes", "");
        if (notes.length() > 1200) notes = notes.substring(0, 1200);
        return new ReleaseUpdate(code, name, hash, bytes, assetUrl(release, apkName, bytes), notes);
    }

    private static String assetUrl(JSONObject release, String name, long expectedBytes) throws JSONException, IOException {
        JSONArray assets = release.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!name.equals(asset.optString("name"))) continue;
            String url = asset.getString("browser_download_url");
            if (expectedBytes > 0 && asset.getLong("size") != expectedBytes) {
                throw new IOException("Розмір APK не збігається з описом випуску.");
            }
            try {
                URI uri = URI.create(url);
                if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                        || uri.getUserInfo() != null || uri.getPort() != -1
                        || !uri.getPath().startsWith("/" + REPOSITORY + "/releases/download/")) {
                    throw new IOException("Неприпустима адреса файлу оновлення.");
                }
            } catch (IllegalArgumentException error) {
                throw new IOException("Некоректна адреса файлу.", error);
            }
            return url;
        }
        throw new IOException("Випуск ще не містить файлу " + name + ".");
    }
}

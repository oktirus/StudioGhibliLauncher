package ua.ghibli.tv.launcher.updates;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class UpdateFixtures {
    static final String API = "https://api.github.com/repos/oktirus/StudioGhibliLauncher/releases/latest";
    static final String BASE = "https://github.com/oktirus/StudioGhibliLauncher/releases/download/v1.2.0/";
    static final byte[] APK = "fake Studio Ghibli APK bytes for bounded stream tests".getBytes(StandardCharsets.UTF_8);

    static JSONObject manifest() throws Exception {
        return new JSONObject()
                .put("schemaVersion", 1)
                .put("applicationId", ReleaseUpdate.APPLICATION_ID)
                .put("versionCode", 3)
                .put("versionName", "1.2.0")
                .put("apkName", "GhibliValley.apk")
                .put("bytes", APK.length)
                .put("sha256", GitHubReleaseClient.hex(GitHubReleaseClient.sha256().digest(APK)))
                .put("notes", "Оновлена Долина Ghibli");
    }

    static JSONObject release() throws Exception {
        return new JSONObject()
                .put("draft", false)
                .put("prerelease", false)
                .put("assets", new JSONArray()
                        .put(new JSONObject().put("name", "update.json").put("browser_download_url", BASE + "update.json"))
                        .put(new JSONObject().put("name", "GhibliValley.apk").put("size", APK.length)
                                .put("browser_download_url", BASE + "GhibliValley.apk")));
    }

    static ReleaseUpdate update() throws Exception {
        return ReleaseUpdate.parse(release(), manifest());
    }

    static final class Response extends HttpURLConnection {
        final byte[] body;
        final int status;
        String location;
        boolean disconnected;

        Response(URL url, byte[] body, int status) {
            super(url);
            this.body = body;
            this.status = status;
        }

        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public int getResponseCode() { return status; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? location : null; }
    }
}

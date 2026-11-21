package ua.ghibli.tv.launcher.updates;

import org.json.JSONObject;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;

public class ReleaseUpdateTest {
    @Test public void pairsManifestAndAssetByNameSizeAndRepository() throws Exception {
        ReleaseUpdate update = UpdateFixtures.update();
        assertEquals(3, update.versionCode);
        assertEquals(UpdateFixtures.BASE + "GhibliValley.apk", update.apkUrl);
        assertEquals(UpdateFixtures.APK.length, update.bytes);
    }

    @Test public void rejectsOtherPackageSchemaOversizeAndInvalidDigest() throws Exception {
        for (JSONObject bad : new JSONObject[]{
                UpdateFixtures.manifest().put("applicationId", "another.package"),
                UpdateFixtures.manifest().put("schemaVersion", 2),
                UpdateFixtures.manifest().put("bytes", ReleaseUpdate.MAX_APK_BYTES + 1),
                UpdateFixtures.manifest().put("versionCode", -1),
                UpdateFixtures.manifest().put("sha256", "not-a-hash"),
                UpdateFixtures.manifest().put("apkName", "../bad.apk")}) {
            assertThrows(IOException.class, () -> ReleaseUpdate.parse(UpdateFixtures.release(), bad));
        }
    }

    @Test public void rejectsDraftPrereleaseMissingAssetAndSizeMismatch() throws Exception {
        assertThrows(IOException.class, () -> ReleaseUpdate.parse(UpdateFixtures.release().put("draft", true), UpdateFixtures.manifest()));
        assertThrows(IOException.class, () -> ReleaseUpdate.manifestUrl(UpdateFixtures.release().put("prerelease", true)));
        assertThrows(IOException.class, () -> ReleaseUpdate.parse(UpdateFixtures.release(), UpdateFixtures.manifest().put("apkName", "missing.apk")));
        assertThrows(IOException.class, () -> ReleaseUpdate.parse(UpdateFixtures.release(), UpdateFixtures.manifest().put("bytes", 10)));
    }

    @Test public void rejectsHttpForeignRepoUserInfoAndPort() throws Exception {
        for (String url : new String[]{
                "http://github.com/oktirus/StudioGhibliLauncher/releases/download/v1/update.json",
                "https://example.com/update.json",
                "https://github.com/another/repo/releases/download/v1/update.json",
                "https://user@github.com/oktirus/StudioGhibliLauncher/releases/download/v1/update.json",
                "https://github.com:444/oktirus/StudioGhibliLauncher/releases/download/v1/update.json"}) {
            JSONObject release = UpdateFixtures.release();
            release.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url);
            assertThrows(IOException.class, () -> ReleaseUpdate.manifestUrl(release));
        }
    }
}

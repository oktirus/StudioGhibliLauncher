package ua.ghibli.tv.launcher.updates;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class GitHubReleaseClientTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void retrievesPublishedReleaseAndSeparateManifest() throws Exception {
        byte[] release = UpdateFixtures.release().toString().getBytes(StandardCharsets.UTF_8);
        byte[] manifest = UpdateFixtures.manifest().toString().getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        GitHubReleaseClient client = new GitHubReleaseClient(url -> {
            requests.incrementAndGet();
            return new UpdateFixtures.Response(url, url.toString().equals(UpdateFixtures.API) ? release : manifest, 200);
        });
        assertEquals(3, client.latest().versionCode);
        assertEquals(2, requests.get());
    }

    @Test public void downloadsExactBytesAndRemovesPartialFile() throws Exception {
        GitHubReleaseClient client = new GitHubReleaseClient(url -> new UpdateFixtures.Response(url, UpdateFixtures.APK, 200));
        File directory = temporary.newFolder();
        AtomicInteger percent = new AtomicInteger();
        File apk = client.download(UpdateFixtures.update(), directory, percent::set);
        assertArrayEquals(UpdateFixtures.APK, Files.readAllBytes(apk.toPath()));
        assertEquals(100, percent.get());
        assertFalse(new File(directory, "candidate.apk.part").exists());
    }

    @Test public void corruptedHashOrLengthNeverProducesInstallableCandidate() throws Exception {
        byte[] corrupt = UpdateFixtures.APK.clone();
        corrupt[0] ^= 1;
        for (byte[] body : new byte[][]{corrupt, new byte[1]}) {
            File directory = temporary.newFolder();
            GitHubReleaseClient client = new GitHubReleaseClient(url -> new UpdateFixtures.Response(url, body, 200));
            assertThrows(IOException.class, () -> client.download(UpdateFixtures.update(), directory, p -> {}));
            assertFalse(new File(directory, "candidate.apk").exists());
            assertFalse(new File(directory, "candidate.apk.part").exists());
        }
    }

    @Test public void refusesUntrustedRedirectAndBoundedMetadata() throws Exception {
        GitHubReleaseClient redirected = new GitHubReleaseClient(url -> {
            UpdateFixtures.Response response = new UpdateFixtures.Response(url, new byte[0], 302);
            response.location = "http://example.com/update.json";
            return response;
        });
        assertThrows(IOException.class, redirected::latest);
        GitHubReleaseClient oversized = new GitHubReleaseClient(url -> new UpdateFixtures.Response(url, new byte[128 * 1024 + 1], 200));
        assertThrows(IOException.class, oversized::latest);
    }

    @Test public void cancellationAvoidsNetworkAnd404IsUnderstandable() throws Exception {
        GitHubReleaseClient cancelled = new GitHubReleaseClient(url -> { fail("Cancelled request must not connect"); return null; });
        cancelled.cancel();
        assertThrows(IOException.class, cancelled::latest);
        GitHubReleaseClient missing = new GitHubReleaseClient(url -> new UpdateFixtures.Response(url, new byte[0], 404));
        assertTrue(assertThrows(IOException.class, missing::latest).getMessage().contains("не опубліковано"));
    }
}

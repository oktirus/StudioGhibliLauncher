package ua.ghibli.tv.launcher.updates;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateInstallerTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Context context;
    private File apk;
    private PackageInfo archive;

    @Before public void setup() throws Exception {
        context = RuntimeEnvironment.getApplication();
        PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        installed.versionCode = 2;
        installed.signingInfo = signing("same-key");
        Shadows.shadowOf(context.getPackageManager()).installPackage(installed);
        apk = temporary.newFile("candidate.apk");
        Files.write(apk.toPath(), UpdateFixtures.APK);
        archive = new PackageInfo();
        archive.packageName = context.getPackageName();
        archive.versionCode = 3;
        archive.signingInfo = signing("same-key");
        Shadows.shadowOf(context.getPackageManager()).setPackageArchiveInfo(apk.getAbsolutePath(), archive);
    }

    private static SigningInfo signing(String key) {
        SigningInfo info = new SigningInfo();
        Shadows.shadowOf(info).setSignatures(new Signature[]{new Signature(key.getBytes(StandardCharsets.UTF_8))});
        return info;
    }

    @Test public void acceptsMatchingPackageVersionAndInstalledSigningKey() throws Exception {
        UpdateInstaller.verifyPackage(context, apk, UpdateFixtures.update());
    }

    @Test public void rejectsWrongPackageOrVersion() throws Exception {
        archive.packageName = "another.package";
        assertThrows(IOException.class, () -> UpdateInstaller.verifyPackage(context, apk, UpdateFixtures.update()));
        archive.packageName = context.getPackageName();
        archive.versionCode = 2;
        assertThrows(IOException.class, () -> UpdateInstaller.verifyPackage(context, apk, UpdateFixtures.update()));
    }

    @Test public void rejectsMissingOrForeignSigningKey() throws Exception {
        archive.signingInfo = signing("other-key");
        assertThrows(IOException.class, () -> UpdateInstaller.verifyPackage(context, apk, UpdateFixtures.update()));
        archive.signingInfo = null;
        assertThrows(IOException.class, () -> UpdateInstaller.verifyPackage(context, apk, UpdateFixtures.update()));
    }

    @Test public void cancellationAbandonsSessionBeforeCommit() throws Exception {
        assertThrows(IOException.class, () -> UpdateInstaller.install(context, apk, UpdateFixtures.update(), () -> true));
        assertTrue(context.getPackageManager().getPackageInstaller().getAllSessions().isEmpty());
    }
}

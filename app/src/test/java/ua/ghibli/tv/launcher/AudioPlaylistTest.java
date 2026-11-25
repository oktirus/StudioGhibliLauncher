package ua.ghibli.tv.launcher;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Looper;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowMediaPlayer;

import java.time.Duration;

import ua.ghibli.tv.launcher.audio.AudioController;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;

import static org.junit.Assert.*;
import static ua.ghibli.tv.launcher.NativeLifecycleTest.field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
@LooperMode(LooperMode.Mode.PAUSED)
public class AudioPlaylistTest {
    @Test public void playlistCrossfadesWithoutOverwritingTheSelectedTrack() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(12000, 0));
        PreferencesManager prefs = new PreferencesManager(context);
        String first = CatalogData.MUSIC_TRACKS.get(0).getId();
        String second = CatalogData.MUSIC_TRACKS.get(1).getId();
        prefs.setMusicTrack(first);
        prefs.setMusicRotationEnabled(true);
        AudioController audio = new AudioController(context, prefs);
        try {
            audio.updateMusic();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1200));
            MediaPlayer original = field(audio, "musicPlayer");
            assertTrue(original.isPlaying());
            assertEquals(7800L, audio.millisecondsUntilNextTrack(), 80L);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(9150));
            MediaPlayer next = field(audio, "musicPlayer");
            assertNotSame(original, next);
            assertSame(original, field(audio, "overlapPlayer"));
            assertEquals(second, field(audio, "currentMusicTrackId"));
            assertTrue(audio.millisecondsUntilNextTrack() < 9000L);
            assertEquals(first, prefs.getMusicTrack());
            assertTrue(next.isPlaying());
            float volume = prefs.getMusicVolume();
            assertTrue(Shadows.shadowOf(original).getLeftVolume() < volume);
            assertTrue(Shadows.shadowOf(next).getLeftVolume() > 0f);
            assertTrue(Shadows.shadowOf(next).getLeftVolume() < volume);

            prefs.setMusicVolume(0.4f);
            audio.updateMusic();
            assertSame("A volume edit does not restart the selected first song", next, field(audio, "musicPlayer"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
            assertEquals(0.4f, Shadows.shadowOf(next).getLeftVolume(), 0.001f);
            assertNull(field(audio, "overlapPlayer"));

            prefs.setMusicRotationEnabled(false);
            audio.updateMusic();
            assertEquals(-1L, audio.millisecondsUntilNextTrack());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1500));
            assertEquals(first, field(audio, "currentMusicTrackId"));
            assertEquals(first, prefs.getMusicTrack());
        } finally {
            audio.release();
            ShadowMediaPlayer.resetStaticState();
        }
    }
}

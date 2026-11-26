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

import android.media.SoundPool;
import ua.ghibli.tv.launcher.game.RunnerCue;
import java.util.Map;

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

    @Test public void unknownDurationReturnsUnavailableCountdown() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        ShadowMediaPlayer.setMediaInfoProvider(source -> new ShadowMediaPlayer.MediaInfo(12000, 0));
        PreferencesManager prefs = new PreferencesManager(context);
        prefs.setMusicRotationEnabled(true);
        AudioController audio = new AudioController(context, prefs);
        try {
            audio.updateMusic();
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            assertEquals(AudioController.MusicState.PLAYING, audio.getMusicState());
            MediaPlayer original = field(audio, "musicPlayer");
            try {
                org.robolectric.util.ReflectionHelpers.setField(audio, "musicPlayer", new MediaPlayer() {
                    @Override public int getDuration() { return 0; }
                });
                assertEquals(-1L, audio.millisecondsUntilNextTrack());
                org.robolectric.util.ReflectionHelpers.setField(audio, "musicPlayer", new MediaPlayer() {
                    @Override public int getDuration() { return -1; }
                });
                assertEquals(-1L, audio.millisecondsUntilNextTrack());
            } finally {
                org.robolectric.util.ReflectionHelpers.setField(audio, "musicPlayer", original);
            }
        } finally {
            audio.release();
            ShadowMediaPlayer.resetStaticState();
        }
    }

    @Test public void delayedRunnerStartCuePlaysWhenLoadedWithinWindowAndCancelsOnStopOrExpiry() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        PreferencesManager prefs = new PreferencesManager(context);
        prefs.setAudioEnabled(true);
        java.util.List<RunnerCue> played = new java.util.ArrayList<>();
        AudioController audio = new AudioController(context, prefs) {
            @Override public synchronized void playRunnerSound(RunnerCue cue) {
                super.playRunnerSound(cue);
                Map<String, Integer> map = org.robolectric.util.ReflectionHelpers.getField(this, "soundMap");
                Map<Integer, Boolean> loaded = org.robolectric.util.ReflectionHelpers.getField(this, "loadedSounds");
                Integer id = map.get("runner_" + cue.assetName);
                if (id != null && Boolean.TRUE.equals(loaded.get(id))) played.add(cue);
            }
        };
        try {
            audio.prepareRunnerSounds();
            SoundPool pool = org.robolectric.util.ReflectionHelpers.getField(audio, "soundPool");
            SoundPool.OnLoadCompleteListener listener = org.robolectric.util.ReflectionHelpers.getField(Shadows.shadowOf(pool), "listener");
            assertNotNull(listener);
            Map<String, Integer> soundMap = org.robolectric.util.ReflectionHelpers.getField(audio, "soundMap");
            Map<Integer, Boolean> loadedSounds = org.robolectric.util.ReflectionHelpers.getField(audio, "loadedSounds");
            Integer startSample = soundMap.get("runner_" + RunnerCue.START.assetName);
            assertNotNull(startSample);

            // Unloaded JUMP cue does not queue a pending start cue.
            audio.playRunnerSound(RunnerCue.JUMP);
            assertNull(org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            assertTrue(played.isEmpty());

            // Unloaded START cue queues and plays once onLoadComplete fires within 350 ms.
            audio.playRunnerSound(RunnerCue.START);
            assertEquals(RunnerCue.START, org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            assertTrue(played.isEmpty());
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(200));
            listener.onLoadComplete(pool, startSample, 0);
            assertNull(org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            assertEquals(java.util.List.of(RunnerCue.START), played);

            // stopRunnerSounds() cancels pending START cue before load completes.
            played.clear();
            audio.stopRunnerSounds();
            loadedSounds.put(startSample, false);
            audio.playRunnerSound(RunnerCue.START);
            assertEquals(RunnerCue.START, org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            audio.stopRunnerSounds();
            assertNull(org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            listener.onLoadComplete(pool, startSample, 0);
            assertTrue(played.isEmpty());

            // Expired START cue (> 350 ms) is dropped when load completes late.
            loadedSounds.put(startSample, false);
            audio.playRunnerSound(RunnerCue.START);
            assertEquals(RunnerCue.START, org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400));
            listener.onLoadComplete(pool, startSample, 0);
            assertNull(org.robolectric.util.ReflectionHelpers.getField(audio, "pendingRunnerCue"));
            assertTrue(played.isEmpty());
        } finally {
            audio.release();
        }
    }
}

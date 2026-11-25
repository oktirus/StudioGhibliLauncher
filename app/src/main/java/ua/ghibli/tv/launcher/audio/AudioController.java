package ua.ghibli.tv.launcher.audio;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import ua.ghibli.tv.launcher.game.RunnerCue;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.HighlightSound;
import ua.ghibli.tv.launcher.model.MusicTrack;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class AudioController {
    public enum MusicState {
        IDLE, PREPARING, PREPARED, PLAYING, PAUSED, STOPPED, ERROR
    }

    private final Context context;
    private final PreferencesManager prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final java.util.Set<MediaPlayer> ownedPlayers = new java.util.HashSet<>();
    private final Map<MediaPlayer, java.util.List<Runnable>> fadeCallbacks = new HashMap<>();
    private boolean disposed;

    private SoundPool soundPool;
    private final Map<String, Integer> soundMap = new HashMap<>();
    private final Map<Integer, Boolean> loadedSounds = new HashMap<>();

    private MediaPlayer musicPlayer;
    private MusicState musicState = MusicState.IDLE;
    private MediaPlayer overlapPlayer;
    private MusicState overlapState = MusicState.STOPPED;
    private MediaPlayer pendingMusicPlayer;
    private String currentMusicTrackId = null;
    private String musicSelection;
    private boolean rotateMusic;
    private float musicVolumeFactor = 1f;
    private boolean isMusicPaused = false;
    private long musicGeneration = 0;
    private Runnable loopScheduleRunnable = null;

    private MediaPlayer confirmPlayer;
    private MediaPlayer previewPlayer;
    private Runnable previewTimeout;
    private long previewGeneration;
    private Runnable pendingLaunchRunnable;
    private Runnable failsafeRunnable;
    private boolean isLaunching = false;
    private long launchGeneration = 0;

    private int activeFocusStreamId = 0;
    private final Map<RunnerCue, Integer> runnerStreams = new EnumMap<>(RunnerCue.class);
    private RunnerCue pendingRunnerCue;
    private long pendingRunnerCueUntil;

    public AudioController(Context context, PreferencesManager prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        initSoundPool();
    }

    private void initSoundPool() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();

        soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
            synchronized (AudioController.this) {
                if (disposed || status != 0) return;
                loadedSounds.put(sampleId, true);
                RunnerCue cue = pendingRunnerCue;
                if (cue != null && Integer.valueOf(sampleId).equals(soundMap.get("runner_" + cue.assetName))) {
                    pendingRunnerCue = null;
                    if (SystemClock.uptimeMillis() <= pendingRunnerCueUntil) playRunnerSound(cue);
                }
            }
        });

        loadSound("ui", "audio/ghibli-ui-chime.wav");
        for (HighlightSound hs : CatalogData.HIGHLIGHT_SOUNDS) {
            loadSound(hs.getId(), hs.getCueAsset());
        }
    }

    private synchronized void loadSound(String key, String assetPath) {
        try (AssetFileDescriptor afd = context.getAssets().openFd(assetPath)) {
            int soundId = soundPool.load(afd, 1);
            soundMap.put(key, soundId);
            loadedSounds.put(soundId, false);
        } catch (IOException ignored) {}
    }

    public synchronized void playUiSound() {
        if (disposed || !prefs.isAudioEnabled()) return;
        Integer soundId = soundMap.get("ui");
        if (soundId != null && Boolean.TRUE.equals(loadedSounds.get(soundId))) {
            float vol = prefs.getVolume() * 0.45f;
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        }
    }

    public synchronized void playFocusSound() {
        if (disposed || !prefs.isAudioEnabled() || isLaunching) return;
        String soundKey = prefs.getHighlightSound();
        Integer soundId = soundMap.get(soundKey);
        if (soundId == null) {
            loadSound(soundKey, "audio/ghibli-soot_sprites-focus.wav");
            soundId = soundMap.get(soundKey);
        }

        if (soundId != null && Boolean.TRUE.equals(loadedSounds.get(soundId))) {
            if (activeFocusStreamId != 0) {
                soundPool.stop(activeFocusStreamId);
            }
            float vol = prefs.getVolume() * 0.7f;
            activeFocusStreamId = soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        }
    }

    public synchronized void prepareRunnerSounds() {
        if (disposed || !prefs.isAudioEnabled()) return;
        for (RunnerCue cue : RunnerCue.values()) {
            String key = "runner_" + cue.assetName;
            if (!soundMap.containsKey(key)) loadSound(key, "audio/runner/" + cue.assetName + ".wav");
        }
    }

    public synchronized void playRunnerSound(RunnerCue cue) {
        if (disposed || !prefs.isAudioEnabled()) return;
        Integer sample = soundMap.get("runner_" + cue.assetName);
        if (sample == null) return;
        if (!Boolean.TRUE.equals(loadedSounds.get(sample))) {
            if (cue == RunnerCue.START) {
                pendingRunnerCue = cue;
                pendingRunnerCueUntil = SystemClock.uptimeMillis() + 350;
            }
            return;
        }
        Integer active = runnerStreams.remove(cue);
        if (active != null) soundPool.stop(active);
        float volume = prefs.getVolume() * 0.65f;
        int stream = soundPool.play(sample, volume, volume, 1, 0, 1.0f);
        if (stream != 0) runnerStreams.put(cue, stream);
    }

    public synchronized void stopRunnerSounds() {
        pendingRunnerCue = null;
        if (soundPool != null) {
            for (int stream : runnerStreams.values()) soundPool.stop(stream);
        }
        runnerStreams.clear();
    }

    private String resolveMusicAsset(String trackId) {
        for (MusicTrack track : CatalogData.MUSIC_TRACKS) {
            if (track.getId().equals(trackId)) return track.getAssetPath();
        }
        return "audio/music-" + trackId + ".ogg";
    }

    public synchronized MusicState getMusicState() {
        return musicState;
    }

    public synchronized long millisecondsUntilNextTrack() {
        if (!rotateMusic || musicState != MusicState.PLAYING || musicPlayer == null || isMusicPaused) return -1L;
        try {
            int duration = musicPlayer.getDuration();
            return Math.max(0, duration - overlapDuration(duration) - musicPlayer.getCurrentPosition());
        } catch (IllegalStateException unavailable) {
            return -1L;
        }
    }

    private static int overlapDuration(int duration) {
        return duration > 8000 ? Math.min(3500, duration / 4) : 0;
    }

    public synchronized void updateMusic() {
        if (disposed) return;
        if (!prefs.isAudioEnabled() || !prefs.isAmbienceEnabled()) {
            stopMusic();
            return;
        }

        if (isMusicPaused) return;

        String selectedTrack = prefs.getMusicTrack();
        boolean rotationEnabled = prefs.isMusicRotationEnabled();
        boolean playlistChanged = rotationEnabled != rotateMusic;
        boolean selectionChanged = !selectedTrack.equals(musicSelection);
        String trackId = selectionChanged || !rotationEnabled || currentMusicTrackId == null
                ? selectedTrack : currentMusicTrackId;
        musicSelection = selectedTrack;
        rotateMusic = rotationEnabled;
        float targetVol = prefs.getMusicVolume();

        if (musicPlayer != null && musicState != MusicState.ERROR && trackId.equals(currentMusicTrackId)) {
            if (musicState == MusicState.PLAYING) {
                if (!fadeCallbacks.containsKey(musicPlayer)) {
                    try {
                        musicPlayer.setVolume(targetVol, targetVol);
                    } catch (Exception ignored) {}
                }
                if (playlistChanged) {
                    safeReleasePlayer(pendingMusicPlayer);
                    pendingMusicPlayer = null;
                    scheduleLoopOverlap(musicPlayer, musicGeneration);
                }
            } else if (musicState == MusicState.PAUSED && !isMusicPaused) {
                resumeMusic();
            }
            return;
        }

        if (musicPlayer != null && musicState == MusicState.PLAYING) {
            cancelFades(musicPlayer);
            safeReleasePlayer(pendingMusicPlayer);
            pendingMusicPlayer = null;
            safeReleasePlayer(overlapPlayer);
            overlapPlayer = musicPlayer;
            overlapState = MusicState.PLAYING;
            musicPlayer = null;
            musicState = MusicState.IDLE;
            startFadeOut(overlapPlayer, 1200, musicVolumeFactor);
        } else {
            stopMusic();
        }

        startTrack(trackId);
    }

    private void startTrack(String trackId) {
        if (loopScheduleRunnable != null) {
            mainHandler.removeCallbacks(loopScheduleRunnable);
            loopScheduleRunnable = null;
        }
        currentMusicTrackId = trackId;
        final long currentGen = ++musicGeneration;
        musicState = MusicState.PREPARING;
        String assetPath = resolveMusicAsset(trackId);

        safeReleasePlayer(pendingMusicPlayer);
        pendingMusicPlayer = null;

        MediaPlayer mp = null;
        try (AssetFileDescriptor afd = context.getAssets().openFd(assetPath)) {
            mp = new MediaPlayer();
            ownedPlayers.add(mp);
            musicPlayer = mp;
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setLooping(false);
            mp.setVolume(0.0f, 0.0f);

            final MediaPlayer playerInstance = mp;
            playerInstance.setOnPreparedListener(player -> {
                synchronized (AudioController.this) {
                    if (currentGen != musicGeneration || player != musicPlayer) {
                        safeReleasePlayer(player);
                        if (musicPlayer == player) musicPlayer = null;
                        return;
                    }
                    musicState = MusicState.PREPARED;
                    if (isMusicPaused || !prefs.isAudioEnabled() || !prefs.isAmbienceEnabled()) {
                        musicState = MusicState.PAUSED;
                        return;
                    }
                    try {
                        player.start();
                        musicState = MusicState.PLAYING;
                        startFadeIn(player, currentGen, 1200);
                        scheduleLoopOverlap(player, currentGen);
                    } catch (Exception e) {
                        if (loopScheduleRunnable != null) {
                            mainHandler.removeCallbacks(loopScheduleRunnable);
                            loopScheduleRunnable = null;
                        }
                        safeReleasePlayer(player);
                        musicPlayer = null;
                        musicState = MusicState.ERROR;
                    }
                }
            });

            playerInstance.setOnErrorListener((player, what, extra) -> {
                synchronized (AudioController.this) {
                    if (currentGen == musicGeneration && player == musicPlayer) {
                        if (loopScheduleRunnable != null) {
                            mainHandler.removeCallbacks(loopScheduleRunnable);
                            loopScheduleRunnable = null;
                        }
                        safeReleasePlayer(player);
                        musicPlayer = null;
                        musicState = MusicState.ERROR;
                    } else {
                        safeReleasePlayer(player);
                    }
                }
                return true;
            });

            playerInstance.setOnCompletionListener(player -> {
                synchronized (AudioController.this) {
                    finishMusicTrack(player, currentGen);
                }
            });

            playerInstance.prepareAsync();
        } catch (Exception e) {
            if (loopScheduleRunnable != null) {
                mainHandler.removeCallbacks(loopScheduleRunnable);
                loopScheduleRunnable = null;
            }
            if (mp != null) {
                safeReleasePlayer(mp);
            }
            musicPlayer = null;
            musicState = MusicState.ERROR;
        }
    }

    private void scheduleLoopOverlap(MediaPlayer mp, long gen) {
        if (loopScheduleRunnable != null) {
            mainHandler.removeCallbacks(loopScheduleRunnable);
            loopScheduleRunnable = null;
        }
        try {
            int duration = mp.getDuration();
            final int fadeMs = overlapDuration(duration);
            if (fadeMs == 0) return;
            int triggerMs = duration - fadeMs;
            int currentPos = 0;
            try { currentPos = mp.getCurrentPosition(); } catch (Exception ignored) {}
            long delay = Math.max(50, triggerMs - currentPos);

            loopScheduleRunnable = () -> {
                synchronized (AudioController.this) {
                    if (gen != musicGeneration || mp != musicPlayer || musicState != MusicState.PLAYING || isMusicPaused) {
                        return;
                    }
                    triggerLoopOverlap(followingTrack(), fadeMs, gen);
                }
            };
            mainHandler.postDelayed(loopScheduleRunnable, delay);
        } catch (Exception ignored) {}
    }

    private String followingTrack() {
        if (!rotateMusic) return currentMusicTrackId;
        for (int i = 0; i < CatalogData.MUSIC_TRACKS.size(); i++) {
            if (CatalogData.MUSIC_TRACKS.get(i).getId().equals(currentMusicTrackId)) {
                return CatalogData.MUSIC_TRACKS.get((i + 1) % CatalogData.MUSIC_TRACKS.size()).getId();
            }
        }
        return musicSelection;
    }

    private void finishMusicTrack(MediaPlayer player, long gen) {
        if (gen != musicGeneration || player != musicPlayer) return;
        String nextTrack = followingTrack();
        if (loopScheduleRunnable != null) {
            mainHandler.removeCallbacks(loopScheduleRunnable);
            loopScheduleRunnable = null;
        }
        safeReleasePlayer(player);
        musicPlayer = null;
        musicState = MusicState.IDLE;
        if (!disposed && !isMusicPaused && prefs.isAudioEnabled() && prefs.isAmbienceEnabled()) {
            startTrack(nextTrack);
        }
    }

    private void triggerLoopOverlap(String trackId, int fadeMs, long parentGen) {
        if (disposed || isMusicPaused || trackId == null) return;
        String assetPath = resolveMusicAsset(trackId);

        safeReleasePlayer(pendingMusicPlayer);
        pendingMusicPlayer = null;
        safeReleasePlayer(overlapPlayer);
        overlapPlayer = null;
        overlapState = MusicState.STOPPED;

        MediaPlayer nextMp = null;
        try (AssetFileDescriptor afd = context.getAssets().openFd(assetPath)) {
            nextMp = new MediaPlayer();
            ownedPlayers.add(nextMp);
            pendingMusicPlayer = nextMp;
            nextMp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            nextMp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            nextMp.setLooping(false);
            nextMp.setVolume(0f, 0f);

            final MediaPlayer candidate = nextMp;
            candidate.setOnPreparedListener(player -> {
                synchronized (AudioController.this) {
                    if (parentGen != musicGeneration || player != pendingMusicPlayer || isMusicPaused || musicState != MusicState.PLAYING) {
                        safeReleasePlayer(player);
                        if (pendingMusicPlayer == player) pendingMusicPlayer = null;
                        return;
                    }
                    pendingMusicPlayer = null;
                    try {
                        int remaining = musicPlayer.getDuration() - musicPlayer.getCurrentPosition();
                        int transitionMs = Math.max(180, Math.min(fadeMs, remaining));
                        player.start();
                        safeReleasePlayer(overlapPlayer);
                        overlapPlayer = musicPlayer;
                        overlapState = MusicState.PLAYING;
                        startFadeOut(overlapPlayer, transitionMs, musicVolumeFactor);

                        musicPlayer = player;
                        currentMusicTrackId = trackId;
                        musicState = MusicState.PLAYING;
                        startFadeIn(player, parentGen, transitionMs);
                        scheduleLoopOverlap(player, parentGen);
                    } catch (Exception e) {
                        safeReleasePlayer(player);
                        if (musicPlayer == player) {
                            musicPlayer = null;
                            musicState = MusicState.ERROR;
                        }
                    }
                }
            });

            candidate.setOnErrorListener((player, what, extra) -> {
                synchronized (AudioController.this) {
                    if (parentGen == musicGeneration) {
                        if (player == musicPlayer) {
                            if (loopScheduleRunnable != null) {
                                mainHandler.removeCallbacks(loopScheduleRunnable);
                                loopScheduleRunnable = null;
                            }
                            safeReleasePlayer(player);
                            musicPlayer = null;
                            musicState = MusicState.ERROR;
                        } else if (player == pendingMusicPlayer) {
                            safeReleasePlayer(player);
                            pendingMusicPlayer = null;
                        } else if (player == overlapPlayer) {
                            safeReleasePlayer(player);
                            overlapPlayer = null;
                            overlapState = MusicState.STOPPED;
                        } else {
                            safeReleasePlayer(player);
                        }
                    } else {
                        safeReleasePlayer(player);
                    }
                }
                return true;
            });

            candidate.setOnCompletionListener(player -> {
                synchronized (AudioController.this) {
                    if (parentGen == musicGeneration) {
                        if (player == musicPlayer) {
                            finishMusicTrack(player, parentGen);
                        } else if (player == overlapPlayer) {
                            safeReleasePlayer(player);
                            overlapPlayer = null;
                            overlapState = MusicState.STOPPED;
                        }
                    }
                }
            });

            candidate.prepareAsync();
        } catch (Exception e) {
            if (nextMp != null) {
                safeReleasePlayer(nextMp);
                if (pendingMusicPlayer == nextMp) pendingMusicPlayer = null;
            }
        }
    }

    private void startFadeIn(MediaPlayer mp, long gen, int durationMs) {
        cancelFades(mp);
        musicVolumeFactor = 0f;
        java.util.List<Runnable> jobs = new java.util.ArrayList<>();
        fadeCallbacks.put(mp, jobs);
        final int steps = Math.max(8, durationMs / 80);
        for (int i = 1; i <= steps; i++) {
            final float factor = (float) i / steps;
            final boolean last = i == steps;
            Runnable job = () -> {
                synchronized (AudioController.this) {
                    if (!disposed && ownedPlayers.contains(mp) && gen == musicGeneration && !isMusicPaused && mp == musicPlayer && musicState == MusicState.PLAYING) {
                        musicVolumeFactor = factor;
                        float v = prefs.getMusicVolume() * factor;
                        try { mp.setVolume(v, v); } catch (Exception ignored) {}
                    }
                    if (last && fadeCallbacks.get(mp) == jobs) fadeCallbacks.remove(mp);
                }
            };
            jobs.add(job);
            mainHandler.postDelayed(job, Math.max(20, (long) durationMs * i / steps));
        }
    }

    private void startFadeOut(MediaPlayer mp, int durationMs, float initialFactor) {
        if (mp == null) return;
        cancelFades(mp);
        java.util.List<Runnable> jobs = new java.util.ArrayList<>();
        fadeCallbacks.put(mp, jobs);
        final int steps = Math.max(8, durationMs / 80);
        for (int i = 1; i <= steps; i++) {
            final float factor = initialFactor * (1.0f - ((float) i / steps));
            final boolean isLast = (i == steps);
            Runnable job = () -> {
                synchronized (AudioController.this) {
                    if (disposed || isMusicPaused || mp != overlapPlayer || !ownedPlayers.contains(mp)) return;
                    try {
                        float currentDesired = prefs.getMusicVolume();
                        float v = currentDesired * factor;
                        mp.setVolume(v, v);
                        if (isLast) {
                            safeReleasePlayer(mp);
                            overlapPlayer = null;
                            overlapState = MusicState.STOPPED;
                        }
                    } catch (Exception e) {
                        safeReleasePlayer(mp);
                        if (overlapPlayer == mp) {
                            overlapPlayer = null;
                            overlapState = MusicState.STOPPED;
                        }
                    }
                }
            };
            jobs.add(job);
            mainHandler.postDelayed(job, Math.max(20, (long) durationMs * i / steps));
        }
    }

    private void cancelFades(MediaPlayer player) {
        java.util.List<Runnable> jobs = fadeCallbacks.remove(player);
        if (jobs != null) for (Runnable job : jobs) mainHandler.removeCallbacks(job);
    }

    public synchronized void pauseMusic() {
        stopTechniquePreview();
        isMusicPaused = true;
        if (loopScheduleRunnable != null) {
            mainHandler.removeCallbacks(loopScheduleRunnable);
            loopScheduleRunnable = null;
        }
        safeReleasePlayer(pendingMusicPlayer);
        pendingMusicPlayer = null;
        cancelFades(musicPlayer);

        if (musicState == MusicState.PLAYING && musicPlayer != null) {
            try {
                musicPlayer.pause();
            } catch (Exception ignored) {}
            musicState = MusicState.PAUSED;
        }
        safeReleasePlayer(overlapPlayer);
        overlapPlayer = null;
        overlapState = MusicState.STOPPED;
    }

    public synchronized void resumeMusic() {
        if (disposed) return;
        isMusicPaused = false;
        if (!prefs.isAudioEnabled() || !prefs.isAmbienceEnabled()) {
            stopMusic();
            return;
        }

        if (!prefs.getMusicTrack().equals(musicSelection) || prefs.isMusicRotationEnabled() != rotateMusic) {
            updateMusic();
            return;
        }

        if (musicState == MusicState.PAUSED && musicPlayer != null) {
            try {
                musicPlayer.start();
                musicState = MusicState.PLAYING;
                musicVolumeFactor = 1f;
                float vol = prefs.getMusicVolume();
                musicPlayer.setVolume(vol, vol);
                scheduleLoopOverlap(musicPlayer, musicGeneration);
            } catch (Exception e) {
                if (loopScheduleRunnable != null) {
                    mainHandler.removeCallbacks(loopScheduleRunnable);
                    loopScheduleRunnable = null;
                }
                safeReleasePlayer(musicPlayer);
                musicPlayer = null;
                musicState = MusicState.ERROR;
            }
        } else if (musicState == MusicState.IDLE || musicState == MusicState.STOPPED || musicState == MusicState.ERROR || musicPlayer == null) {
            updateMusic();
        }
    }

    public synchronized void stopMusic() {
        musicGeneration++;
        if (loopScheduleRunnable != null) {
            mainHandler.removeCallbacks(loopScheduleRunnable);
            loopScheduleRunnable = null;
        }
        safeReleasePlayer(pendingMusicPlayer);
        pendingMusicPlayer = null;

        safeReleasePlayer(musicPlayer);
        musicPlayer = null;
        musicState = MusicState.STOPPED;

        safeReleasePlayer(overlapPlayer);
        overlapPlayer = null;
        overlapState = MusicState.STOPPED;

        currentMusicTrackId = null;
    }

    private void safeReleasePlayer(MediaPlayer mp) {
        if (mp == null || !ownedPlayers.remove(mp)) return;
        cancelFades(mp);
        try {
            mp.setOnPreparedListener(null);
            mp.setOnCompletionListener(null);
            mp.setOnErrorListener(null);
            try {
                mp.stop();
            } catch (Exception ignored) {}
        } finally {
            try {
                mp.release();
            } catch (Exception ignored) {}
        }
    }

    private String confirmCueAsset(String techniqueId) {
        return "audio/ghibli-" + techniqueId + "-confirm.wav";
    }

    public synchronized boolean playTechniquePreview(String techniqueId) {
        stopTechniquePreview();
        if (disposed || !prefs.isAudioEnabled() || isLaunching || isMusicPaused) return false;
        final long generation = previewGeneration;
        try (AssetFileDescriptor afd = context.getAssets().openFd(confirmCueAsset(techniqueId))) {
            MediaPlayer player = new MediaPlayer();
            ownedPlayers.add(player);
            previewPlayer = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.setVolume(prefs.getVolume(), prefs.getVolume());
            player.setOnPreparedListener(mp -> {
                synchronized (AudioController.this) {
                    if (generation != previewGeneration || mp != previewPlayer || disposed) return;
                    try {
                        if (prefs.isAudioEnabled()) mp.start();
                        else stopTechniquePreview();
                    } catch (IllegalStateException error) {
                        stopTechniquePreview();
                    }
                }
            });
            player.setOnCompletionListener(mp -> {
                synchronized (AudioController.this) {
                    if (mp == previewPlayer) stopTechniquePreview();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                synchronized (AudioController.this) {
                    if (mp == previewPlayer) stopTechniquePreview();
                }
                return true;
            });
            previewTimeout = () -> {
                synchronized (AudioController.this) {
                    if (generation == previewGeneration) stopTechniquePreview();
                }
            };
            mainHandler.postDelayed(previewTimeout, 8000);
            player.prepareAsync();
            return true;
        } catch (IOException | IllegalStateException error) {
            android.util.Log.w("GhibliAudio", "Spirit preview could not be played", error);
            stopTechniquePreview();
            return false;
        }
    }

    public synchronized void stopTechniquePreview() {
        previewGeneration++;
        if (previewTimeout != null) mainHandler.removeCallbacks(previewTimeout);
        previewTimeout = null;
        safeReleasePlayer(previewPlayer);
        previewPlayer = null;
    }

    public synchronized boolean launchWithTechnique(String techniqueId, Runnable onLaunch) {
        if (disposed || isLaunching) return false;
        stopTechniquePreview();

        if (!prefs.isAudioEnabled()) {
            onLaunch.run();
            return true;
        }

        isLaunching = true;
        pendingLaunchRunnable = onLaunch;
        cleanupConfirmPlayer(true);

        final long currentLaunchGen = ++launchGeneration;
        String cueAsset = confirmCueAsset(techniqueId);

        failsafeRunnable = () -> {
            synchronized (AudioController.this) {
                if (currentLaunchGen == launchGeneration && isLaunching) {
                    cleanupConfirmPlayer(true);
                    if (pendingLaunchRunnable != null) {
                        Runnable action = pendingLaunchRunnable;
                        pendingLaunchRunnable = null;
                        isLaunching = false;
                        action.run();
                    }
                }
            }
        };
        mainHandler.postDelayed(failsafeRunnable, 8000);

        try (AssetFileDescriptor afd = context.getAssets().openFd(cueAsset)) {
            MediaPlayer player = new MediaPlayer();
            ownedPlayers.add(player);
            confirmPlayer = player;
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());

            float vol = prefs.getVolume();
            player.setVolume(vol, vol);

            player.setOnPreparedListener(mp -> {
                synchronized (AudioController.this) {
                    if (currentLaunchGen == launchGeneration && isLaunching && mp == confirmPlayer) {
                        try {
                            mp.start();
                        } catch (Exception e) {
                            triggerFailsafeLaunch(currentLaunchGen);
                        }
                    }
                }
            });

            player.setOnCompletionListener(mp -> {
                synchronized (AudioController.this) {
                    if (currentLaunchGen == launchGeneration && isLaunching) {
                        cleanupConfirmPlayer(true);
                        if (pendingLaunchRunnable != null) {
                            Runnable action = pendingLaunchRunnable;
                            pendingLaunchRunnable = null;
                            isLaunching = false;
                            action.run();
                        }
                    }
                }
            });

            player.setOnErrorListener((mp, what, extra) -> {
                synchronized (AudioController.this) {
                    if (currentLaunchGen == launchGeneration && isLaunching) {
                        triggerFailsafeLaunch(currentLaunchGen);
                    }
                }
                return true;
            });

            player.prepareAsync();
        } catch (Exception e) {
            triggerFailsafeLaunch(currentLaunchGen);
        }

        return true;
    }

    private synchronized void triggerFailsafeLaunch(long currentLaunchGen) {
        if (currentLaunchGen == launchGeneration && isLaunching) {
            cleanupConfirmPlayer(true);
            if (pendingLaunchRunnable != null) {
                Runnable action = pendingLaunchRunnable;
                pendingLaunchRunnable = null;
                isLaunching = false;
                action.run();
            }
        }
    }

    public synchronized boolean cancelPendingLaunch() {
        if (!isLaunching) return false;
        launchGeneration++;
        isLaunching = false;
        pendingLaunchRunnable = null;
        cleanupConfirmPlayer(true);
        return true;
    }

    public synchronized boolean isLaunching() {
        return isLaunching;
    }

    private void cleanupConfirmPlayer(boolean cancelFailsafe) {
        if (cancelFailsafe && failsafeRunnable != null) {
            mainHandler.removeCallbacks(failsafeRunnable);
            failsafeRunnable = null;
        }
        if (confirmPlayer != null) {
            safeReleasePlayer(confirmPlayer);
            confirmPlayer = null;
        }
    }

    public synchronized void release() {
        disposed = true;
        stopTechniquePreview();
        stopMusic();
        cancelPendingLaunch();
        stopRunnerSounds();
        if (soundPool != null) {
            try {
                soundPool.release();
            } catch (Exception ignored) {}
            soundPool = null;
        }
    }
}

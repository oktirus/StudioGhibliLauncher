package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import ua.ghibli.tv.launcher.R;
import ua.ghibli.tv.launcher.game.RunnerCue;
import ua.ghibli.tv.launcher.game.RunnerPhysics;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Studio Ghibli Valley Runner ("Політ крізь долину").
 * Fixed 1280x540 virtual stage with honey-gold wooden border, Caveat state banners,
 * Soot Sprite, Kodama & Totoro heroes, mossy stumps, stone lanterns, and origami paper birds.
 */
public class ValleyRunnerView extends View {
    private final PreferencesManager prefs;
    private final RunnerPhysics runner;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Map<String, Bitmap> bitmaps = new HashMap<>();
    private final java.util.Set<String> pendingBitmaps = new java.util.HashSet<>();
    private final java.util.concurrent.ThreadPoolExecutor bitmapExecutor = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final Handler bitmapHandler = new Handler(Looper.getMainLooper());
    private volatile long bitmapGeneration;
    private volatile boolean disposed;
    private final Path birdPath = new Path();
    private final Path stageClipPath = new Path();
    private final Rect srcRect = new Rect();
    private final RectF dstRect = new RectF();
    private final RectF stageRect = new RectF();
    private final RectF sootTab = new RectF();
    private final RectF kodamaTab = new RectF();
    private final RectF totoroTab = new RectF();
    private final RectF pauseButton = new RectF();
    private final RectF closeButton = new RectF();
    private final RectF slideButton = new RectF();
    private final RectF jumpButton = new RectF();

    private Runnable onExitListener;
    private Consumer<RunnerCue> onSoundListener;
    private Runnable onStopSounds;

    private long lastTime = 0;
    private double landSquash = 0.0;
    private boolean isPausedByLifecycle = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if ("running".equals(runner.status) && !isPausedByLifecycle && isAttachedToWindow()
                    && getWindowVisibility() == VISIBLE && isShown()) {
                long now = SystemClock.uptimeMillis();
                double dt = lastTime > 0 ? (now - lastTime) / 1000.0 : 0.016;
                if (dt > 0.05) dt = 0.05;
                lastTime = now;

                if (landSquash > 0.0) {
                    landSquash = Math.max(0.0, landSquash - dt);
                }

                boolean airborne = runner.y > 0.0;
                int clearedBefore = runner.cleared;
                runner.step(dt);
                if ("over".equals(runner.status)) {
                    saveBestScore();
                    stopSounds();
                    playSound(RunnerCue.HIT);
                } else {
                    if (airborne && runner.y == 0.0) {
                        landSquash = 0.14;
                        playSound(RunnerCue.LAND);
                    }
                    if (runner.cleared > clearedBefore) playSound(RunnerCue.CLEAR);
                }
                invalidate();
                if ("running".equals(runner.status)) postDelayed(this, 16);
            }
        }
    };

    public ValleyRunnerView(Context context, PreferencesManager prefs) {
        super(context);
        this.prefs = prefs;
        String hero = prefs.getRunnerHero();
        if (!"kodama".equals(hero) && !"totoro".equals(hero)) hero = "soot";
        this.runner = new RunnerPhysics(hero);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(GhibliTheme.dp(context, 2f));
        framePaint.setColor(Color.parseColor("#88e8b858"));
        loadBitmaps();
    }

    public void setOnExitListener(Runnable listener) {
        this.onExitListener = listener;
    }

    public void setSoundCallbacks(Consumer<RunnerCue> play, Runnable stop) {
        this.onSoundListener = play;
        this.onStopSounds = stop;
    }

    private void playSound(RunnerCue cue) {
        if (!disposed && !isPausedByLifecycle && isAttachedToWindow() && onSoundListener != null) {
            onSoundListener.accept(cue);
        }
    }

    private void stopSounds() {
        if (onStopSounds != null) onStopSounds.run();
    }

    private void loadBitmaps() {
        loadBitmapAsync("valley", "game/ghibli-valley.webp");
        loadBitmapAsync("ground", "game/ghibli-ground.webp");
        loadBitmapAsync("obs-stump", "game/obs-stump.webp");
        loadBitmapAsync("obs-stone", "game/obs-stone.webp");
        loadBitmapAsync("obs-bird-up", "game/obs-bird-up.webp");
        loadBitmapAsync("obs-bird-down", "game/obs-bird-down.webp");
        loadHeroBitmaps(runner.hero);
    }

    private void loadHeroBitmaps(String hero) {
        loadBitmapAsync(hero + "-motion", "game/game-" + hero + "-motion.webp");
        loadBitmapAsync(hero + "-slide", "game/game-" + hero + "-slide.webp");
    }

    private void unloadHeroBitmaps(String hero) {
        Bitmap motion = bitmaps.remove(hero + "-motion");
        if (motion != null && !motion.isRecycled()) motion.recycle();
        Bitmap slide = bitmaps.remove(hero + "-slide");
        if (slide != null && !slide.isRecycled()) slide.recycle();
    }

    private void loadBitmapAsync(final String key, final String assetPath) {
        if (disposed || bitmapExecutor.isShutdown() || bitmaps.containsKey(key)) return;
        synchronized (pendingBitmaps) {
            if (!pendingBitmaps.add(key)) return;
        }

        final long gen = bitmapGeneration;
        bitmapExecutor.execute(() -> {
            Bitmap bmp = null;
            try (InputStream is = getContext().getAssets().open(assetPath)) {
                bmp = BitmapFactory.decodeStream(is);
            } catch (Exception ignored) {}

            final Bitmap finalBmp = bmp;
            bitmapHandler.post(() -> {
                synchronized (pendingBitmaps) {
                    pendingBitmaps.remove(key);
                }
                if (disposed || gen != bitmapGeneration || finalBmp == null) {
                    if (finalBmp != null) finalBmp.recycle();
                    return;
                }
                bitmaps.put(key, finalBmp);
                invalidate();
            });
        });
    }

    public void startGame() {
        if ("running".equals(runner.status)) return;
        runner.start();
        lastTime = SystemClock.uptimeMillis();
        removeCallbacks(tickRunnable);
        post(tickRunnable);
        playSound(RunnerCue.START);
        invalidate();
    }

    public void pauseGame() {
        if ("running".equals(runner.status)) {
            runner.status = "paused";
            removeCallbacks(tickRunnable);
            invalidate();
        }
        stopSounds();
    }

    public void togglePause() {
        if ("running".equals(runner.status)) pauseGame();
        else startGame();
    }

    public void jump() {
        if ("ready".equals(runner.status) || "over".equals(runner.status)) {
            startGame();
            return;
        }
        if ("paused".equals(runner.status)) {
            startGame();
            return;
        }
        boolean fromGround = runner.y == 0.0;
        if (runner.jump() && fromGround) playSound(RunnerCue.JUMP);
        invalidate();
    }

    public void crouch() {
        if ("running".equals(runner.status)) {
            boolean startsMove = runner.y > 0.0 ? runner.vy < 400.0 : runner.duck == 0.0;
            runner.crouch();
            if (startsMove) playSound(RunnerCue.SLIDE);
            invalidate();
        }
    }

    public void setHero(String hero) {
        saveBestScore();
        if (!"kodama".equals(hero) && !"totoro".equals(hero)) hero = "soot";
        if (hero.equals(runner.hero)) return;
        stopSounds();
        String oldHero = runner.hero;
        bitmapGeneration++;
        bitmapExecutor.getQueue().clear();
        synchronized (pendingBitmaps) {
            pendingBitmaps.clear();
        }
        runner.setHero(hero);
        runner.reset();
        unloadHeroBitmaps(oldHero);
        loadBitmaps();
        prefs.setRunnerHero(hero);
        invalidate();
    }

    public void cycleHero(int delta) {
        String[] order = new String[]{"soot", "kodama", "totoro"};
        int idx = 0;
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(runner.hero)) {
                idx = i;
                break;
            }
        }
        int next = (idx + delta + order.length) % order.length;
        setHero(order[next]);
    }

    public void toggleHero() {
        cycleHero(1);
    }

    private void saveBestScore() {
        int score = runner.getScore();
        prefs.setRunnerBest(runner.hero, score);
    }

    public void onPauseLifecycle() {
        isPausedByLifecycle = true;
        pauseGame();
        clearCache();
    }

    public void onResumeLifecycle() {
        if (disposed) return;
        isPausedByLifecycle = false;
        if (bitmaps.isEmpty()) {
            loadBitmaps();
        }
        if ("running".equals(runner.status)) {
            runner.status = "paused";
        }
        invalidate();
    }

    public void release() {
        saveBestScore();
        stopSounds();
        onSoundListener = null;
        onStopSounds = null;
        isPausedByLifecycle = true;
        disposed = true;
        clearCache();
        bitmapExecutor.shutdownNow();
    }

    private void clearCache() {
        bitmapGeneration++;
        bitmapExecutor.getQueue().clear();
        synchronized (pendingBitmaps) {
            pendingBitmaps.clear();
        }
        removeCallbacks(tickRunnable);
        for (Bitmap bmp : bitmaps.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        bitmaps.clear();
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tickRunnable);
        stopSounds();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
            saveBestScore();
            if (onExitListener != null) onExitListener.run();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_P) {
            if (event.getRepeatCount() == 0) {
                togglePause();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if ("ready".equals(runner.status) || "over".equals(runner.status)) {
                cycleHero(keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_SPACE) {
            jump();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            crouch();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateControlBounds(w, h);
    }

    private void updateControlBounds(int w, int h) {
        float tabY = GhibliTheme.dp(getContext(), 52);
        float tabBottom = tabY + GhibliTheme.dp(getContext(), 28);
        sootTab.set(GhibliTheme.dp(getContext(), 24), tabY,
                GhibliTheme.dp(getContext(), 132), tabBottom);
        kodamaTab.set(GhibliTheme.dp(getContext(), 140), tabY,
                GhibliTheme.dp(getContext(), 236), tabBottom);
        totoroTab.set(GhibliTheme.dp(getContext(), 244), tabY,
                GhibliTheme.dp(getContext(), 340), tabBottom);
        pauseButton.set(w - GhibliTheme.dp(getContext(), 106), tabY,
                w - GhibliTheme.dp(getContext(), 26), tabBottom);
        closeButton.set(w - GhibliTheme.dp(getContext(), 36), GhibliTheme.dp(getContext(), 4),
                w, GhibliTheme.dp(getContext(), 44));
        float footY = h - GhibliTheme.dp(getContext(), 18);
        float footTop = footY - GhibliTheme.dp(getContext(), 16);
        float footBottom = footTop + GhibliTheme.dp(getContext(), 24);
        slideButton.set(w - GhibliTheme.dp(getContext(), 196), footTop,
                w - GhibliTheme.dp(getContext(), 112), footBottom);
        jumpButton.set(w - GhibliTheme.dp(getContext(), 102), footTop,
                w - GhibliTheme.dp(getContext(), 20), footBottom);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            performClick();
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            requestFocus();
            float x = event.getX();
            float y = event.getY();

            if (closeButton.contains(x, y)) {
                saveBestScore();
                if (onExitListener != null) onExitListener.run();
            } else if (pauseButton.contains(x, y)) {
                togglePause();
            } else if (sootTab.contains(x, y) || kodamaTab.contains(x, y) || totoroTab.contains(x, y)) {
                if ("ready".equals(runner.status) || "over".equals(runner.status)) {
                    String picked = sootTab.contains(x, y) ? "soot" : kodamaTab.contains(x, y) ? "kodama" : "totoro";
                    setHero(picked);
                }
            } else if (slideButton.contains(x, y)) {
                crouch();
            } else if (jumpButton.contains(x, y) || stageRect.contains(x, y)) {
                jump();
            }
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription("Політ крізь долину · " + runner.getHeroDef().name);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.runner_jump, "Стрибок"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.runner_slide, "Присідання"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.runner_pause,
                "running".equals(runner.status) ? "Пауза" : "Грати"));
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.runner_exit, "Вийти з гри"));
        if ("ready".equals(runner.status) || "over".equals(runner.status)) {
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.runner_hero, "Змінити духа"));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == R.id.runner_jump || action == AccessibilityNodeInfo.ACTION_CLICK) jump();
        else if (action == R.id.runner_slide) crouch();
        else if (action == R.id.runner_pause) togglePause();
        else if (action == R.id.runner_hero) toggleHero();
        else if (action == R.id.runner_exit) {
            saveBestScore();
            if (onExitListener != null) onExitListener.run();
        } else return super.performAccessibilityAction(action, arguments);
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        updateControlBounds(w, h);

        paint.reset();
        paint.setAntiAlias(true);
        paint.setColor(GhibliTheme.COLOR_BG);
        canvas.drawRect(0, 0, w, h, paint);

        renderHeader(canvas, w);

        float topH = GhibliTheme.dp(getContext(), 88);
        float bottomH = GhibliTheme.dp(getContext(), 56);
        float availH = h - topH - bottomH;
        float availW = w - GhibliTheme.dp(getContext(), 48);

        float stageW = availW;
        float stageH = stageW * (540f / 1280f);
        if (stageH > availH) {
            stageH = availH;
            stageW = stageH * (1280f / 540f);
        }

        float stageLeft = (w - stageW) / 2f;
        float stageTop = topH + (availH - stageH) / 2f;
        stageRect.set(stageLeft, stageTop, stageLeft + stageW, stageTop + stageH);

        canvas.save();
        stageClipPath.reset();
        float corner = GhibliTheme.dp(getContext(), 12);
        stageClipPath.addRoundRect(stageRect, corner, corner, Path.Direction.CW);
        canvas.clipPath(stageClipPath);
        canvas.translate(stageLeft, stageTop);
        float scale = stageW / (float) RunnerPhysics.W;
        canvas.scale(scale, scale);

        renderGameContent(canvas);

        if (!"running".equals(runner.status)) {
            renderStageOverlay(canvas);
        }

        canvas.restore();

        canvas.drawRoundRect(stageRect, corner, corner, framePaint);

        renderFooter(canvas, w, h);
    }

    private void renderHeader(Canvas canvas, int w) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setTypeface(GhibliTheme.getCaveatBold(getContext()));
        paint.setTextSize(GhibliTheme.dp(getContext(), 32));
        paint.setColor(GhibliTheme.COLOR_PAPER);
        canvas.drawText("Політ крізь долину", GhibliTheme.dp(getContext(), 24), GhibliTheme.dp(getContext(), 40), paint);

        boolean isSoot = "soot".equals(runner.hero);
        boolean isKodama = "kodama".equals(runner.hero);
        boolean isTotoro = "totoro".equals(runner.hero);
        float tabY = GhibliTheme.dp(getContext(), 52);

        drawHeroTab(canvas, sootTab.left, sootTab.top, sootTab.width(), sootTab.height(), "Сусуватарі", isSoot);
        drawHeroTab(canvas, kodamaTab.left, kodamaTab.top, kodamaTab.width(), kodamaTab.height(), "Кодама", isKodama);
        drawHeroTab(canvas, totoroTab.left, totoroTab.top, totoroTab.width(), totoroTab.height(), "Тоторо", isTotoro);

        paint.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        paint.setTextSize(GhibliTheme.dp(getContext(), 11));
        paint.setColor(GhibliTheme.COLOR_MUTED);
        canvas.drawText("Відстань", w - GhibliTheme.dp(getContext(), 330), tabY + GhibliTheme.dp(getContext(), 7), paint);
        canvas.drawText("Рекорд", w - GhibliTheme.dp(getContext(), 210), tabY + GhibliTheme.dp(getContext(), 7), paint);

        paint.setTextSize(GhibliTheme.dp(getContext(), 18));
        paint.setColor(GhibliTheme.COLOR_PAPER);
        String scoreStr = String.format(java.util.Locale.ROOT, "%05d", runner.getScore());
        String bestStr = String.format(java.util.Locale.ROOT, "%05d", Math.max(runner.getScore(), prefs.getRunnerBest(runner.hero)));
        canvas.drawText(scoreStr, w - GhibliTheme.dp(getContext(), 330), tabY + GhibliTheme.dp(getContext(), 26), paint);
        canvas.drawText(bestStr, w - GhibliTheme.dp(getContext(), 210), tabY + GhibliTheme.dp(getContext(), 26), paint);

        drawPillButton(canvas, pauseButton.left, pauseButton.top, pauseButton.width(), pauseButton.height(),
                "running".equals(runner.status) ? "⏸ Пауза" : "▶ Старт");

        paint.setColor(GhibliTheme.COLOR_PAPER);
        paint.setStrokeWidth(2f);
        paint.setStyle(Paint.Style.STROKE);
        float cx = closeButton.centerX();
        float cy = closeButton.centerY();
        float s = GhibliTheme.dp(getContext(), 6);
        canvas.drawLine(cx - s, cy - s, cx + s, cy + s, paint);
        canvas.drawLine(cx - s, cy + s, cx + s, cy - s, paint);
    }

    private void drawHeroTab(Canvas canvas, float x, float y, float w, float h, String text, boolean active) {
        float r = GhibliTheme.dp(getContext(), 10);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? GhibliTheme.COLOR_ACCENT : GhibliTheme.COLOR_BUTTON_BG);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(active ? GhibliTheme.COLOR_PAPER : Color.parseColor("#55e8b858"));
        paint.setStrokeWidth(1.5f);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(active ? GhibliTheme.COLOR_INK : GhibliTheme.COLOR_PAPER);
        paint.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        paint.setTextSize(GhibliTheme.dp(getContext(), 12));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x + w / 2f, y + h * 0.68f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawPillButton(Canvas canvas, float x, float y, float w, float h, String text) {
        float r = GhibliTheme.dp(getContext(), 10);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(GhibliTheme.COLOR_BUTTON_BG);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(Color.parseColor("#55e8b858"));
        paint.setStrokeWidth(1f);
        canvas.drawRoundRect(x, y, x + w, y + h, r, r, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(GhibliTheme.COLOR_PAPER);
        paint.setTypeface(GhibliTheme.getNunito(getContext()));
        paint.setTextSize(GhibliTheme.dp(getContext(), 11));
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, x + w / 2f, y + h * 0.66f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void renderStageOverlay(Canvas canvas) {
        paint.reset();
        paint.setAntiAlias(true);

        // Soft sunlit-friendly veil so the Ghibli valley, wildflower trail, and hero remain lively
        paint.setColor(Color.parseColor("#4a122019"));
        canvas.drawRect(0, 0, (float) RunnerPhysics.W, (float) RunnerPhysics.H, paint);

        // Centered storybook parchment-forest plaque for crisp contrast on TV
        float cardW = 580f;
        float cardH = 292f;
        float cardX = ((float) RunnerPhysics.W - cardW) / 2f;
        float cardY = 122f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#b814241c"));
        canvas.drawRoundRect(cardX, cardY, cardX + cardW, cardY + cardH, 22f, 22f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.parseColor("#99e8b858"));
        canvas.drawRoundRect(cardX, cardY, cardX + cardW, cardY + cardH, 22f, 22f, paint);
        paint.setStyle(Paint.Style.FILL);

        String subtitle;
        String title;
        String desc;
        String cta;

        if ("over".equals(runner.status)) {
            subtitle = "Вітер кличе в нову подорож.";
            title = "Прогулянку завершено.";
            desc = "Пройдено: " + runner.getScore() + " м · Рекорд: " + prefs.getRunnerBest(runner.hero) + " м";
            cta = "OK · Спробувати ще";
        } else if ("paused".equals(runner.status)) {
            subtitle = "Тиха зупинка серед трав.";
            title = "Перепочинок у долині.";
            desc = "Натисни OK або P, щоб продовжити подорож.";
            cta = "OK · Продовжити";
        } else {
            RunnerPhysics.HeroDef heroDef = runner.getHeroDef();
            subtitle = heroDef.name + " · " + heroDef.title;
            title = "Долина кличе.";
            desc = "Перестрибуй замшілі пеньки та кам’яні ліхтарі.\nПрисідай під паперовими птахами оріґамі.";
            cta = "OK · Рушай";
        }

        paint.setTypeface(GhibliTheme.getNunito(getContext()));
        paint.setColor(GhibliTheme.COLOR_GOLD);
        paint.setTextSize(17f);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(subtitle, (float) RunnerPhysics.W / 2f, 166f, paint);

        paint.setTypeface(GhibliTheme.getCaveatBold(getContext()));
        paint.setColor(GhibliTheme.COLOR_PAPER);
        paint.setTextSize(66f);
        paint.setShadowLayer(16f, 0, 3f, Color.parseColor("#99000000"));
        canvas.drawText(title, (float) RunnerPhysics.W / 2f, 236f, paint);

        paint.clearShadowLayer();
        paint.setTypeface(GhibliTheme.getNunito(getContext()));
        paint.setColor(GhibliTheme.COLOR_MUTED_LIGHT);
        paint.setTextSize(15f);
        String[] lines = desc.split("\n");
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], (float) RunnerPhysics.W / 2f, 282f + i * 24f, paint);
        }

        float btnW = 216f;
        float btnH = 46f;
        float btnX = ((float) RunnerPhysics.W - btnW) / 2f;
        float btnY = 342f;

        paint.setColor(GhibliTheme.COLOR_ACCENT);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(btnX, btnY, btnX + btnW, btnY + btnH, 14f, 14f, paint);

        paint.setColor(GhibliTheme.COLOR_INK);
        paint.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        paint.setTextSize(16f);
        canvas.drawText(cta, (float) RunnerPhysics.W / 2f, btnY + 29f, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    private void renderFooter(Canvas canvas, int w, int h) {
        float footY = h - GhibliTheme.dp(getContext(), 18);
        paint.setTypeface(GhibliTheme.getNunito(getContext()));
        paint.setTextSize(GhibliTheme.dp(getContext(), 12));
        paint.setColor(GhibliTheme.COLOR_MUTED);
        canvas.drawText("Обери духа долини (←/→) та рушай у путь.", GhibliTheme.dp(getContext(), 24), footY, paint);

        drawPillButton(canvas, slideButton.left, slideButton.top, slideButton.width(), slideButton.height(), "↓ Присісти");
        drawPillButton(canvas, jumpButton.left, jumpButton.top, jumpButton.width(), jumpButton.height(), "↑ Стрибок");
    }

    private void renderGameContent(Canvas canvas) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        // 1. Seamless parallax Studio Ghibli alpine valley panorama (cosine-blended, zero mirroring)
        Bitmap bg = bitmaps.get("valley");
        if (bg != null && !bg.isRecycled()) {
            float bgH = (float) RunnerPhysics.GROUND + 16f;
            float bgW = bgH * ((float) bg.getWidth() / Math.max(1f, (float) bg.getHeight()));
            float scroll = (float) ((runner.distance * 0.22) % bgW);
            dstRect.set(-scroll, 0f, bgW - scroll, bgH);
            canvas.drawBitmap(bg, null, dstRect, paint);
            dstRect.set(bgW - scroll - 1.5f, 0f, bgW * 2f - scroll - 1.5f, bgH);
            canvas.drawBitmap(bg, null, dstRect, paint);
        } else {
            paint.setColor(Color.parseColor("#5c9bc4"));
            canvas.drawRect(0, 0, (float) RunnerPhysics.W, (float) RunnerPhysics.H, paint);
        }

        // 2. Drifting valley wind dandelion spores & flower petals
        paint.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 10; k++) {
            double speedFactor = 0.36 + (k % 3) * 0.14;
            float px = (float) (((k * 167.0 - runner.distance * speedFactor - runner.elapsed * (18.0 + k * 3.0))
                    % RunnerPhysics.W + RunnerPhysics.W) % RunnerPhysics.W);
            float py = (float) (58.0 + (k * 37) % 280 + Math.sin(runner.elapsed * (1.6 + (k % 3) * 0.4) + k * 1.9) * 14.0);
            paint.setColor(k % 3 == 0 ? Color.parseColor("#fff6d4") : Color.parseColor("#ffffff"));
            paint.setAlpha(125 + (k % 3) * 32);
            float rad = 2.0f + (k % 3) * 1.1f;
            canvas.drawCircle(px, py, rad, paint);
        }
        paint.setAlpha(255);

        // 3. Seamless hand-painted Studio Ghibli meadow ground strip (cutout grass/flowers + sunlit trail + soil)
        Bitmap groundBmp = bitmaps.get("ground");
        if (groundBmp != null && !groundBmp.isRecycled()) {
            float gW = (float) RunnerPhysics.W;
            float gTop = (float) RunnerPhysics.GROUND - 44f;
            float gBot = (float) RunnerPhysics.H;
            float gScroll = (float) (runner.distance % gW);
            dstRect.set(-gScroll, gTop, gW - gScroll, gBot);
            canvas.drawBitmap(groundBmp, null, dstRect, paint);
            dstRect.set(gW - gScroll - 1.5f, gTop, gW * 2f - gScroll - 1.5f, gBot);
            canvas.drawBitmap(groundBmp, null, dstRect, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#7a5e3c"));
            canvas.drawRect(0, (float) RunnerPhysics.GROUND, (float) RunnerPhysics.W, (float) RunnerPhysics.H, paint);
            paint.setColor(Color.parseColor("#6ea662"));
            canvas.drawRect(0, (float) RunnerPhysics.GROUND - 4, (float) RunnerPhysics.W, (float) RunnerPhysics.GROUND + 8, paint);
        }

        // 4. Obstacles
        for (RunnerPhysics.Obstacle o : runner.obstacles) {
            renderObstacle(canvas, o);
        }

        // 5. Dynamic altitude-scaled hero contact shadow on the sunlit trail
        boolean isKodama = "kodama".equals(runner.hero);
        boolean isTotoro = "totoro".equals(runner.hero);
        float airFactor = Math.max(0f, Math.min(1f, (float) (runner.y / 180.0)));
        float shadowScale = 1f - airFactor * 0.52f;
        float shadowW = (isTotoro ? 44f : isKodama ? 36f : 34f) * shadowScale;
        float shadowH = (isTotoro ? 10f : 8.5f) * shadowScale;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.parseColor("#241c14"));
        paint.setAlpha((int) (96f * (1f - airFactor * 0.62f)));
        dstRect.set(214f - shadowW, (float) RunnerPhysics.GROUND - shadowH * 0.35f,
                214f + shadowW, (float) RunnerPhysics.GROUND + shadowH);
        canvas.drawOval(dstRect, paint);
        paint.setAlpha(255);

        // 6. Hero sprite (6-frame hand-drawn 1990s Ghibli cel animation strip + 2-frame crouch strip)
        RunnerPhysics.Pose pose = RunnerPhysics.poseFor(runner);
        boolean isSlide = "slide".equals(pose.mode);
        String bmpKey = runner.hero + (isSlide ? "-slide" : "-motion");
        Bitmap heroBmp = bitmaps.get(bmpKey);

        if (heroBmp != null && !heroBmp.isRecycled()) {
            int bw = heroBmp.getWidth();
            int bh = heroBmp.getHeight();
            if (bw >= bh * 2 && bh > 0) {
                int frameCount = Math.max(1, bw / bh);
                int frameW = bw / frameCount;
                int frameIdx = isSlide
                        ? ((pose.frame % frameCount) + frameCount) % frameCount
                        : Math.max(0, Math.min(frameCount - 1, pose.frame));
                srcRect.set(frameIdx * frameW, 0, (frameIdx + 1) * frameW, bh);
            } else {
                srcRect.set(0, 0, bw, bh);
            }

            // Shared square draw size across run/jump/idle/slide because all poses share one normalized scale!
            float drawSize = isTotoro ? 124f : isKodama ? 114f : 106f;
            float drawX = 214f;
            float drawY = (float) (RunnerPhysics.GROUND - runner.y + pose.hop * 0.65);

            float sx = 1f;
            float sy = 1f;
            if (landSquash > 0.0 && runner.y <= 0.5) {
                float sq = (float) Math.sin((landSquash / 0.14) * Math.PI);
                sx = 1f + sq * 0.12f;
                sy = 1f - sq * 0.11f;
            } else if ("jump".equals(pose.mode)) {
                float stretch = Math.max(-0.06f, Math.min(0.08f, (float) (-runner.vy / 810.0) * 0.07f));
                sx = 1f - stretch * 0.5f;
                sy = 1f + stretch;
            }

            canvas.save();
            canvas.translate(drawX, drawY);
            canvas.scale(sx, sy);
            if ("jump".equals(pose.mode)) {
                float airTilt = Math.max(-8f, Math.min(9f, (float) (runner.vy * 0.011)));
                canvas.rotate(airTilt, 0f, -drawSize * 0.45f);
            } else if ("run".equals(pose.mode)) {
                float runLean = isTotoro ? 1.8f : isKodama ? 2.2f : 2.8f;
                canvas.rotate(runLean, 0f, -drawSize * 0.35f);
            }

            dstRect.set(-drawSize / 2f, -drawSize + 8f, drawSize / 2f, 8f);
            paint.reset();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setAlpha(255);
            canvas.drawBitmap(heroBmp, srcRect, dstRect, paint);
            canvas.restore();
        }

        // 7. Cozy trail dust & tiny grass flecks kicked up while running
        if ("running".equals(runner.status) && runner.y <= 1.0) {
            paint.setStyle(Paint.Style.FILL);
            for (int k = 0; k < 5; k++) {
                double age = (runner.elapsed * 2.4 + k * 0.21) % 1.0;
                float px = (float) (186.0 - age * 58.0);
                float py = (float) (RunnerPhysics.GROUND - 2.0 - Math.sin(age * Math.PI) * 14.0);
                paint.setColor(k % 2 == 0 ? Color.parseColor("#f2e3b8") : Color.parseColor("#c8dda0"));
                paint.setAlpha((int) ((1.0 - age) * 150.0));
                canvas.drawCircle(px, py, (float) (2.5 + age * 4.0), paint);
            }
            paint.setAlpha(255);
        }
    }

    private void renderObstacle(Canvas canvas, RunnerPhysics.Obstacle o) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);

        if ("paper_bird".equals(o.kind)) {
            boolean wingsUp = ((int) Math.floor(runner.elapsed * 7.5 + o.id)) % 2 == 0;
            Bitmap birdBmp = bitmaps.get(wingsUp ? "obs-bird-up" : "obs-bird-down");
            float cx = (float) (o.x + o.w / 2.0);
            float cy = (float) (o.y + o.h / 2.0 + Math.sin(runner.elapsed * 8.5 + o.id) * 5.0);

            // Subtle shadow on the meadow trail far below the gliding paper bird
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#241c14"));
            paint.setAlpha(42);
            dstRect.set(cx - 24f, (float) RunnerPhysics.GROUND - 2f, cx + 24f, (float) RunnerPhysics.GROUND + 6f);
            canvas.drawOval(dstRect, paint);
            paint.setAlpha(255);

            if (birdBmp != null && !birdBmp.isRecycled()) {
                canvas.save();
                canvas.translate(cx, cy);
                float pitch = (float) (Math.cos(runner.elapsed * 8.5 + o.id) * 5.0);
                canvas.rotate(pitch);
                float sz = 78f;
                dstRect.set(-sz / 2f, -sz / 2f, sz / 2f, sz / 2f);
                canvas.drawBitmap(birdBmp, null, dstRect, paint);
                canvas.restore();
                return;
            }

            // Procedural fallback while bitmap loads
            canvas.save();
            canvas.translate(cx, cy);
            float wing = (float) (Math.sin(runner.elapsed * 15.0 + o.id) * 9.0);
            birdPath.reset();
            birdPath.moveTo(-24f, 0f);
            birdPath.lineTo(-2f, -15f - wing * 0.4f);
            birdPath.lineTo(14f, -3f);
            birdPath.lineTo(24f, 1f);
            birdPath.lineTo(8f, 9f + wing * 0.35f);
            birdPath.lineTo(-4f, 4f);
            birdPath.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GhibliTheme.COLOR_PAPER);
            canvas.drawPath(birdPath, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.5f);
            paint.setColor(Color.parseColor("#88b3a078"));
            canvas.drawPath(birdPath, paint);
            canvas.restore();
        } else if ("stump".equals(o.kind)) {
            float cx = (float) (o.x + o.w / 2.0);
            // Soft ground contact shadow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#241c14"));
            paint.setAlpha(82);
            dstRect.set(cx - 42f, (float) RunnerPhysics.GROUND - 2f, cx + 42f, (float) RunnerPhysics.GROUND + 10f);
            canvas.drawOval(dstRect, paint);
            paint.setAlpha(255);

            Bitmap stumpBmp = bitmaps.get("obs-stump");
            if (stumpBmp != null && !stumpBmp.isRecycled()) {
                float sz = 84f;
                dstRect.set(cx - sz / 2f, (float) RunnerPhysics.GROUND - sz + 11f, cx + sz / 2f, (float) RunnerPhysics.GROUND + 11f);
                canvas.drawBitmap(stumpBmp, null, dstRect, paint);
                return;
            }

            paint.setColor(Color.parseColor("#6b4c30"));
            canvas.drawRoundRect((float) o.x, (float) (o.y + 6), (float) (o.x + o.w), (float) (o.y + o.h), 8f, 8f, paint);
            paint.setColor(Color.parseColor("#c29b63"));
            dstRect.set((float) o.x + 2, (float) o.y, (float) (o.x + o.w - 2), (float) (o.y + 14));
            canvas.drawOval(dstRect, paint);
        } else {
            float cx = (float) (o.x + o.w / 2.0);
            // Soft ground contact shadow
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.parseColor("#241c14"));
            paint.setAlpha(82);
            dstRect.set(cx - 32f, (float) RunnerPhysics.GROUND - 2f, cx + 32f, (float) RunnerPhysics.GROUND + 10f);
            canvas.drawOval(dstRect, paint);
            paint.setAlpha(255);

            Bitmap stoneBmp = bitmaps.get("obs-stone");
            if (stoneBmp != null && !stoneBmp.isRecycled()) {
                float sz = 92f;
                float top = (float) RunnerPhysics.GROUND - sz + 9f;
                // Gentle warm cel-flame halo behind the lantern chamber
                float pulse = 0.85f + 0.15f * (float) Math.sin(runner.elapsed * 7.0 + o.id);
                paint.setColor(Color.parseColor("#ffd466"));
                paint.setAlpha((int) (68 * pulse));
                canvas.drawCircle(cx, top + sz * 0.40f, 18f * pulse, paint);
                paint.setAlpha(255);

                dstRect.set(cx - sz / 2f, top, cx + sz / 2f, (float) RunnerPhysics.GROUND + 9f);
                canvas.drawBitmap(stoneBmp, null, dstRect, paint);
                return;
            }

            paint.setColor(Color.parseColor("#7a8478"));
            canvas.drawRoundRect((float) (o.x + 10), (float) (o.y + 20), (float) (o.x + o.w - 10), (float) (o.y + o.h), 4f, 4f, paint);
            paint.setColor(Color.parseColor("#929c8f"));
            canvas.drawRoundRect((float) (o.x + 4), (float) (o.y + 10), (float) (o.x + o.w - 4), (float) (o.y + 30), 4f, 4f, paint);
            paint.setColor(GhibliTheme.COLOR_ACCENT);
            canvas.drawRoundRect((float) (o.x + 11), (float) (o.y + 15), (float) (o.x + o.w - 11), (float) (o.y + 25), 3f, 3f, paint);
            paint.setColor(Color.parseColor("#606b5e"));
            canvas.drawRoundRect((float) o.x, (float) o.y + 2, (float) (o.x + o.w), (float) (o.y + 12), 5f, 5f, paint);
        }
    }
}

package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import ua.ghibli.tv.launcher.model.Scene;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Full-bleed Studio Ghibli wallpaper canvas with warm botanical readability scrims,
 * 1.6s scene crossfade (works even with decorative motion disabled), clamped parallax,
 * and gentle Ghibli atmospheric particles (camphor leaves, meadow petals, lantern embers,
 * glowing forest spores, sea wind wisps, rain, and twilight snow).
 */
public class ValleyBackgroundView extends View {
    private static class Particle {
        float x, y, size, phase, speed;
    }

    private final List<Particle> particles = new ArrayList<>(30);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leafPath = new Path();
    private final RectF dstRect = new RectF();

    private LinearGradient leftScrim;
    private LinearGradient bottomScrim;
    private int lastW = 0, lastH = 0;

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;
    private long crossfadeStartTime = 0;
    private boolean isCrossfading = false;

    private final java.util.concurrent.ThreadPoolExecutor bgExecutor = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile long bgGeneration = 0;
    private volatile boolean disposed;
    private boolean activityPaused;

    private Scene currentScene;
    private boolean isMotionEnabled = true;
    private boolean isDecorPaused = false;
    private long lastFrameTime = 0;
    private long animationStartTime = 0;
    private long decorElapsedMs = 0;

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canRenderFrames() || !isMotionEnabled || isDecorPaused) return;
            invalidate();
            postOnAnimation(this);
        }
    };

    private final Runnable crossfadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canRenderFrames()) return;
            if (isCrossfading) {
                invalidate();
                postOnAnimation(this);
            }
        }
    };

    public ValleyBackgroundView(Context context) {
        super(context);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        initParticles();
    }

    private void initParticles() {
        particles.clear();
        for (int i = 0; i < 30; i++) {
            Particle p = new Particle();
            p.x = (float) ((i * 0.137 % 1.0) * 1920);
            p.y = (float) ((i * 0.283 % 1.0) * 1080);
            p.size = 3.2f + (i % 4) * 1.6f;
            p.phase = (float) (i * 1.7);
            p.speed = 14f + (i % 6) * 4.5f;
            particles.add(p);
        }
    }

    public void setScene(Scene scene, boolean animate) {
        if (disposed) return;
        this.currentScene = scene;
        if (scene == null) return;

        final long gen = ++bgGeneration;
        final String assetPath = scene.getImageAsset();
        bgExecutor.getQueue().clear();

        bgExecutor.execute(() -> {
            if (disposed || gen != bgGeneration) return;
            Bitmap decoded = loadSceneBitmap(assetPath);
            if (decoded != null) {
                mainHandler.post(() -> {
                    synchronized (ValleyBackgroundView.this) {
                        if (disposed || gen != bgGeneration) {
                            decoded.recycle();
                            return;
                        }
                        applyNewSceneBitmap(decoded, animate);
                    }
                });
            }
        });
    }

    private void applyNewSceneBitmap(Bitmap newBmp, boolean animate) {
        if (!animate || currentBitmap == null || activityPaused) {
            if (currentBitmap != null && currentBitmap != newBmp) currentBitmap.recycle();
            if (nextBitmap != null && nextBitmap != newBmp) nextBitmap.recycle();
            currentBitmap = newBmp;
            nextBitmap = null;
            isCrossfading = false;
            removeCallbacks(crossfadeRunnable);
            invalidate();
            return;
        }

        if (nextBitmap != null && nextBitmap != newBmp) nextBitmap.recycle();
        nextBitmap = newBmp;
        isCrossfading = true;
        crossfadeStartTime = SystemClock.uptimeMillis();
        removeCallbacks(crossfadeRunnable);
        postOnAnimation(crossfadeRunnable);
        invalidate();
    }

    private Bitmap loadSceneBitmap(String assetPath) {
        try (InputStream is = getContext().getAssets().open(assetPath)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeStream(is, null, opts);
        } catch (Exception e) {
            return null;
        }
    }

    public void setMotionEnabled(boolean enabled) {
        this.isMotionEnabled = enabled;
        checkAnimationState();
    }

    public void setDecorPaused(boolean paused) {
        this.isDecorPaused = paused;
        checkAnimationState();
    }

    public void pause() {
        activityPaused = true;
        removeCallbacks(animationRunnable);
        removeCallbacks(crossfadeRunnable);
        finishCrossfade();
    }

    public void resume() {
        activityPaused = false;
        checkAnimationState();
    }

    private boolean canRenderFrames() {
        return !disposed && !activityPaused && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && isShown();
    }

    private void finishCrossfade() {
        if (nextBitmap != null) {
            if (currentBitmap != null && currentBitmap != nextBitmap) currentBitmap.recycle();
            currentBitmap = nextBitmap;
            nextBitmap = null;
        }
        isCrossfading = false;
    }

    private void checkAnimationState() {
        removeCallbacks(animationRunnable);
        if (canRenderFrames() && isMotionEnabled && !isDecorPaused) {
            lastFrameTime = SystemClock.uptimeMillis();
            postOnAnimation(animationRunnable);
        } else {
            invalidate();
        }
        removeCallbacks(crossfadeRunnable);
        if (isCrossfading && canRenderFrames()) postOnAnimation(crossfadeRunnable);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!disposed && currentBitmap == null && currentScene != null) setScene(currentScene, false);
        checkAnimationState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(animationRunnable);
        removeCallbacks(crossfadeRunnable);
        bgGeneration++;
        bgExecutor.getQueue().clear();
        isCrossfading = false;
        if (currentBitmap != null) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
        if (nextBitmap != null) {
            nextBitmap.recycle();
            nextBitmap = null;
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        checkAnimationState();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        checkAnimationState();
    }

    public void release() {
        disposed = true;
        bgGeneration++;
        pause();
        bgExecutor.shutdownNow();
        if (currentBitmap != null) currentBitmap.recycle();
        currentBitmap = null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        long now = SystemClock.uptimeMillis();
        if (animationStartTime == 0) animationStartTime = now;
        if (isMotionEnabled && !isDecorPaused) decorElapsedMs = now - animationStartTime;
        float dt = lastFrameTime > 0 ? (now - lastFrameTime) / 1000f : 0.016f;
        if (dt > 0.05f) dt = 0.05f;
        lastFrameTime = now;

        // 1. Painted Ghibli wallpaper with smooth 1.6s crossfade & subtle clamped parallax
        if (isCrossfading && nextBitmap != null) {
            float progress = (now - crossfadeStartTime) / 1600f;
            if (progress >= 1.0f) {
                if (currentBitmap != null && currentBitmap != nextBitmap) currentBitmap.recycle();
                currentBitmap = nextBitmap;
                nextBitmap = null;
                isCrossfading = false;
                removeCallbacks(crossfadeRunnable);
                drawScaledBitmap(canvas, currentBitmap, w, h, 255, decorElapsedMs);
            } else {
                drawScaledBitmap(canvas, currentBitmap, w, h, 255, decorElapsedMs);
                drawScaledBitmap(canvas, nextBitmap, w, h, (int) (progress * 255), decorElapsedMs);
            }
        } else if (currentBitmap != null) {
            drawScaledBitmap(canvas, currentBitmap, w, h, 255, decorElapsedMs);
        } else {
            canvas.drawColor(GhibliTheme.COLOR_BG);
        }

        // 2. Warm forest-canopy readability scrims (left typography safe zone + bottom dock zone)
        if (w != lastW || h != lastH || leftScrim == null) {
            lastW = w;
            lastH = h;
            leftScrim = new LinearGradient(
                    0, 0, w, 0,
                    new int[]{Color.parseColor("#cc13211b"), Color.parseColor("#7a13211b"), Color.TRANSPARENT},
                    new float[]{0.0f, 0.34f, 0.65f}, Shader.TileMode.CLAMP
            );
            bottomScrim = new LinearGradient(
                    0, h, 0, h * 0.38f,
                    new int[]{Color.parseColor("#ec13211b"), Color.parseColor("#bf13211b"), Color.TRANSPARENT},
                    new float[]{0.0f, 0.36f, 1.0f}, Shader.TileMode.CLAMP
            );
        }
        paint.reset();
        paint.setShader(leftScrim);
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(bottomScrim);
        canvas.drawRect(0, h * 0.38f, w, h, paint);
        paint.setShader(null);

        // 3. Atmospheric Studio Ghibli particles
        if (isMotionEnabled && currentScene != null) {
            String pMode = currentScene.getParticles();
            int[] palette = currentScene.getPalette();
            int color1 = palette != null && palette.length > 0 ? palette[0] : GhibliTheme.COLOR_PAPER;
            int color2 = palette != null && palette.length > 1 ? palette[1] : color1;

            paint.setStyle(Paint.Style.FILL);
            double elapsedSec = decorElapsedMs / 1000.0;

            for (int i = 0; i < particles.size(); i++) {
                Particle p = particles.get(i);
                if (!isDecorPaused) {
                    if ("ember".equals(pMode) || "spore".equals(pMode)) {
                        p.x += (float) (Math.sin(elapsedSec * 0.9 + p.phase) * dt * 18f);
                        p.y -= dt * (14f + p.speed * 0.6f);
                    } else if ("rain".equals(pMode)) {
                        p.x -= p.speed * dt * 1.4f;
                        p.y += dt * (190f + p.speed * 4f);
                    } else if ("snow".equals(pMode)) {
                        p.x -= p.speed * dt * 0.8f;
                        p.y += dt * (22f + p.speed * 0.5f);
                    } else if ("wind".equals(pMode)) {
                        p.x -= p.speed * dt * 3.4f;
                        p.y += (float) (Math.sin(elapsedSec * 1.4 + p.phase) * dt * 16f);
                    } else {
                        // leaf or petal gentle valley breeze
                        p.x -= p.speed * dt * 1.8f;
                        p.y += (float) (Math.sin(elapsedSec * 0.8 + p.phase) * dt * 16f + dt * 7f);
                    }

                    if (p.x < -30) {
                        p.x = w + 25;
                        p.y = (float) ((p.phase * 0.111 % 1.0) * h);
                    } else if (p.x > w + 35) {
                        p.x = -20;
                    }
                    if (p.y > h + 25) p.y = -20;
                    if (p.y < -25) p.y = h + 20;
                }

                int col = (i % 2 == 0) ? color1 : color2;
                int alpha = (int) ((0.24 + Math.sin(elapsedSec * 1.1 + p.phase) * 0.14) * 255);
                paint.setColor(col);
                paint.setAlpha(Math.max(20, Math.min(220, alpha)));

                canvas.save();
                canvas.translate(p.x, p.y);

                if ("rain".equals(pMode)) {
                    canvas.rotate(-10f);
                    canvas.drawRect(0, 0, 1.5f, 11f + p.size, paint);
                } else if ("snow".equals(pMode)) {
                    canvas.drawCircle(0, 0, p.size * 0.72f, paint);
                } else if ("ember".equals(pMode) || "spore".equals(pMode)) {
                    // Soft firefly/lantern spore glow halo + crisp center
                    int savedA = paint.getAlpha();
                    paint.setAlpha(savedA / 3);
                    canvas.drawCircle(0, 0, p.size * 1.5f, paint);
                    paint.setAlpha(savedA);
                    canvas.drawCircle(0, 0, p.size * 0.55f, paint);
                } else if ("wind".equals(pMode)) {
                    strokePaint.setColor(col);
                    strokePaint.setAlpha(Math.max(18, alpha / 2));
                    strokePaint.setStrokeWidth(1.6f);
                    canvas.drawLine(-p.size * 3.0f, 0, p.size * 3.0f, (float) Math.sin(p.phase) * 3f, strokePaint);
                } else if ("petal".equals(pMode)) {
                    float rot = (float) ((Math.sin(elapsedSec * 0.9 + p.phase) * 65.0) % 360.0);
                    canvas.rotate(rot);
                    canvas.drawOval(-p.size * 0.9f, -p.size * 0.5f, p.size * 0.9f, p.size * 0.5f, paint);
                } else {
                    // Camphor leaf shape
                    float rot = (float) ((Math.sin(elapsedSec * 0.75 + p.phase) * 75.0) % 360.0);
                    canvas.rotate(rot);
                    leafPath.reset();
                    leafPath.moveTo(-p.size, 0);
                    leafPath.quadTo(0, -p.size * 0.9f, p.size, 0);
                    leafPath.quadTo(0, p.size * 0.9f, -p.size, 0);
                    canvas.drawPath(leafPath, paint);
                }
                canvas.restore();
            }
        }
    }

    private void drawScaledBitmap(Canvas canvas, Bitmap bmp, int w, int h, int alpha, long elapsedMs) {
        if (bmp == null || bmp.isRecycled()) return;
        paint.reset();
        paint.setAlpha(alpha);
        paint.setFilterBitmap(true);

        float scale = Math.max((float) w / bmp.getWidth(), (float) h / bmp.getHeight()) * 1.035f;
        float scaledW = bmp.getWidth() * scale;
        float scaledH = bmp.getHeight() * scale;

        float extra = (scaledW - w) / 2f;
        float parallaxX = isMotionEnabled ? (float) (Math.sin(elapsedMs / 8500.0) * extra * 0.5f) : 0f;
        float left = (w - scaledW) / 2f + parallaxX;
        float top = (h - scaledH) / 2f;

        dstRect.set(left, top, left + scaledW, top + scaledH);
        canvas.drawBitmap(bmp, null, dstRect, paint);
    }
}

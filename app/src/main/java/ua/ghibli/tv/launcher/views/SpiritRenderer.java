package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.*;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

import java.io.InputStream;
import java.util.*;

/**
 * Hardware-accelerated Studio Ghibli Focus Spirit Renderer.
 * Supports 6 cohesive Ghibli focus effects:
 *  - soot_sprites: Hopping Soot Sprites (Susuwatari) with pastel konpeito star candies (raster + hop physics)
 *  - calcifer: Warm hearth flame wreath with gentle live mesh sway & sparks (raster + mesh)
 *  - kodama: Luminous emerald woodland spirit wreath with orbiting firefly spores (raster + particles)
 *  - wind_petal: Swirling summer breeze ribbons & fluttering camphor leaves/petals (procedural)
 *  - aetherium: Rotating Laputa sky-crystal sigil with golden runes (procedural)
 *  - paper_birds: Orbiting flock of white origami shikigami paper birds (procedural)
 *
 * Uses monotonic relative double time, modulo angle wrap before float, reusable hot-path Paint/Path,
 * bounded lazy bitmap cache, reusable clearCache() vs terminal release(), and static gallery previews.
 */
public class SpiritRenderer {
    private static final double TAU = Math.PI * 2.0;

    public interface OnBitmapLoadedListener {
        void onBitmapLoaded(String name);
    }

    public static class SootState {
        public PointF currentPos = new PointF(0, 0);
        public PointF fromPos = new PointF(0, 0);
        public PointF toPos = new PointF(0, 0);
        public long startTime = 0;
        public long duration = 420;
        public int direction = 1;
        public boolean isJumping = false;
        public long landTime = 0;
    }

    private final Context context;
    private final Map<String, Bitmap> bitmaps = new HashMap<>();
    private final Map<String, Long> requestTokens = new HashMap<>();
    private long cacheGeneration = 0;
    private long nextRequestToken = 0;
    private boolean isDisposed = false;

    private final java.util.concurrent.ThreadPoolExecutor decodeExecutor = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final List<OnBitmapLoadedListener> loadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tempPath = new Path();
    private final RectF dstRect = new RectF();
    private final RectF tempOval = new RectF();

    private static final int MESH_W = 1;
    private static final int MESH_H = 14;
    private final float[] meshVerts = new float[(MESH_W + 1) * (MESH_H + 1) * 2];

    private final float density;
    private final SootState defaultSootState = new SootState();
    private Bitmap samplePreviewIcon;

    private static final int[] KONPEITO_COLORS = {
        Color.parseColor("#f7b2c4"),
        Color.parseColor("#f7df8b"),
        Color.parseColor("#a4e2c6"),
        Color.parseColor("#a6d4f2")
    };

    public SpiritRenderer(Context context) {
        this.context = context.getApplicationContext();
        float d = context.getResources().getDisplayMetrics().density;
        this.density = d > 0 ? d : 1.0f;
    }

    private float dp(float px) {
        return Math.max(1.0f, px * (density / 1.5f));
    }

    public void addOnBitmapLoadedListener(OnBitmapLoadedListener listener) {
        if (listener != null) loadListeners.add(listener);
    }

    public void removeOnBitmapLoadedListener(OnBitmapLoadedListener listener) {
        loadListeners.remove(listener);
    }

    public static Set<String> platesFor(String spiritId) {
        if ("soot_sprites".equals(spiritId)) {
            return new HashSet<>(Arrays.asList("soot-idle", "soot-jump"));
        }
        if ("calcifer".equals(spiritId) || "kodama".equals(spiritId)) {
            return Collections.singleton(spiritId);
        }
        return Collections.emptySet();
    }

    public synchronized Bitmap ensureBitmap(String name) {
        Bitmap bmp = bitmaps.get(name);
        if (bmp != null && !bmp.isRecycled()) return bmp;
        if (isDisposed) return null;

        if (!requestTokens.containsKey(name)) {
            final long token = ++nextRequestToken;
            requestTokens.put(name, token);
            final long gen = cacheGeneration;

            decodeExecutor.execute(() -> {
                synchronized (SpiritRenderer.this) {
                    Long activeToken = requestTokens.get(name);
                    if (isDisposed || gen != cacheGeneration || activeToken == null || activeToken != token) {
                        return;
                    }
                }
                Bitmap loaded = decodePlateFromAssets(name);
                mainHandler.post(() -> commitDecodedBitmap(name, token, gen, loaded));
            });
        }
        return null;
    }

    private Bitmap decodePlateFromAssets(String name) {
        try (InputStream is = context.getAssets().open("effects/effect-" + name + ".webp")) {
            return BitmapFactory.decodeStream(is);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void commitDecodedBitmap(String name, long token, long gen, Bitmap loaded) {
        boolean shouldRecycle = false;
        boolean notify = false;

        synchronized (this) {
            Long activeToken = requestTokens.get(name);
            if (!isDisposed && gen == cacheGeneration && activeToken != null && activeToken == token) {
                requestTokens.remove(name);
                if (loaded != null && !loaded.isRecycled()) {
                    Bitmap existing = bitmaps.get(name);
                    if (existing != null && !existing.isRecycled()) {
                        shouldRecycle = true;
                    } else {
                        bitmaps.put(name, loaded);
                        notify = true;
                    }
                }
            } else {
                shouldRecycle = true;
                if (activeToken != null && activeToken == token) requestTokens.remove(name);
            }
        }

        if (shouldRecycle && loaded != null && !loaded.isRecycled()) loaded.recycle();
        if (notify) {
            for (OnBitmapLoadedListener listener : loadListeners) {
                try { listener.onBitmapLoaded(name); } catch (Exception ignored) {}
            }
        }
    }

    public synchronized Bitmap getBitmap(String name) {
        Bitmap bmp = bitmaps.get(name);
        return (bmp != null && !bmp.isRecycled()) ? bmp : null;
    }

    public synchronized void trimMemory(Set<String> keep) {
        cacheGeneration++;
        requestTokens.clear();
        decodeExecutor.getQueue().clear();
        Iterator<Map.Entry<String, Bitmap>> it = bitmaps.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bitmap> entry = it.next();
            if (!keep.contains(entry.getKey())) {
                Bitmap bmp = entry.getValue();
                if (bmp != null && !bmp.isRecycled()) bmp.recycle();
                it.remove();
            }
        }
    }

    public synchronized void clearCache() {
        cacheGeneration++;
        requestTokens.clear();
        decodeExecutor.getQueue().clear();
        for (Bitmap bmp : bitmaps.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        bitmaps.clear();
        if (samplePreviewIcon != null && !samplePreviewIcon.isRecycled()) {
            samplePreviewIcon.recycle();
            samplePreviewIcon = null;
        }
    }

    public synchronized void release() {
        isDisposed = true;
        cacheGeneration++;
        requestTokens.clear();
        loadListeners.clear();
        for (Bitmap bmp : bitmaps.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        bitmaps.clear();
        if (samplePreviewIcon != null && !samplePreviewIcon.isRecycled()) {
            samplePreviewIcon.recycle();
            samplePreviewIcon = null;
        }
        decodeExecutor.shutdownNow();
    }

    public void hopSootTo(SootState state, float cx, float cy, float r, long now) {
        float toX = cx + r * 0.56f;
        float toY = cy - r * 0.42f;
        state.fromPos.set(state.currentPos.x, state.currentPos.y);
        state.toPos.set(toX, toY);
        state.startTime = now;
        state.landTime = 0L;
        double dist = Math.hypot(toX - state.currentPos.x, toY - state.currentPos.y);
        state.duration = (long) Math.min(580, Math.max(300, dist * 1.15));
        if (Math.abs(toX - state.currentPos.x) > 4f) {
            state.direction = toX >= state.currentPos.x ? 1 : -1;
        }
        state.isJumping = true;
    }

    public void drawTechnique(Canvas canvas, String id, float cx, float cy, float r,
                              long timeMs, float burstLife, boolean isPreview) {
        drawTechnique(canvas, id, cx, cy, r, timeMs, burstLife, isPreview, defaultSootState);
    }

    public void drawTechnique(Canvas canvas, String id, float cx, float cy, float r,
                              long timeMs, float burstLife, boolean isPreview, SootState sootState) {
        double t = timeMs / 1000.0;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setAlpha(255);

        if ("soot_sprites".equals(id)) {
            drawSootSprites(canvas, cx, cy, r, timeMs, isPreview, sootState);
        } else if ("calcifer".equals(id)) {
            drawCalcifer(canvas, cx, cy, r, t, burstLife, isPreview);
        } else if ("kodama".equals(id)) {
            drawKodama(canvas, cx, cy, r, t, burstLife, isPreview);
        } else if ("wind_petal".equals(id)) {
            drawWindPetal(canvas, cx, cy, r, t, burstLife);
        } else if ("aetherium".equals(id)) {
            drawAetherium(canvas, cx, cy, r, t, burstLife);
        } else if ("paper_birds".equals(id)) {
            drawPaperBirds(canvas, cx, cy, r, t, burstLife);
        } else {
            drawWindPetal(canvas, cx, cy, r, t, burstLife);
        }
    }

    private void drawSootSprites(Canvas canvas, float cx, float cy, float r, long now,
                                 boolean isPreview, SootState state) {
        double t = now / 1000.0;
        float restX = cx + r * 0.56f;
        float restY = cy - r * 0.42f;

        // Soft warm konpeito glow ring behind the focused card
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(GhibliTheme.COLOR_ACCENT);
        strokePaint.setAlpha(125);
        strokePaint.setStrokeWidth(dp(2.2f));
        canvas.drawCircle(cx, cy, r * 1.12f, strokePaint);

        float drawX = restX;
        float drawY = restY;
        float shadowX = restX;
        float shadowY = restY;
        float shadowScale = 1f;
        int shadowAlpha = 0x45;
        boolean jumping = false;
        int dir = (state != null && state.direction < 0 && !isPreview) ? -1 : 1;
        float scaleX = 1f;
        float scaleY = 1f;
        float tiltDeg = 0f;

        if (!isPreview && state != null && state.isJumping) {
            float progress = Math.min(1f, (float) (now - state.startTime) / Math.max(1L, state.duration));
            if (progress >= 1f) {
                state.isJumping = false;
                state.landTime = now;
                state.currentPos.set(restX, restY);
                drawX = restX;
                drawY = restY;
                shadowX = restX;
                shadowY = restY;
            } else {
                jumping = true;
                dir = state.direction >= 0 ? 1 : -1;
                float ease = 1f - (float) Math.pow(1f - progress, 2.1);
                float baseX = state.fromPos.x + (state.toPos.x - state.fromPos.x) * ease;
                float baseY = state.fromPos.y + (state.toPos.y - state.fromPos.y) * ease;
                float dist = (float) Math.hypot(state.toPos.x - state.fromPos.x, state.toPos.y - state.fromPos.y);
                float arcHeight = Math.min(r * 1.32f, Math.max(r * 0.62f, dist * 0.26f));
                float arcPhase = (float) Math.sin(progress * Math.PI);
                drawX = baseX;
                drawY = baseY - arcPhase * arcHeight;
                shadowX = baseX;
                shadowY = baseY;
                shadowScale = 1f - 0.42f * arcPhase;
                shadowAlpha = Math.max(18, Math.round(0x45 * (1f - 0.58f * arcPhase)));
                state.currentPos.set(drawX, drawY);

                // Smooth takeoff stretch -> mid-air poise -> gentle descent pitch
                if (progress < 0.22f) {
                    float k = (float) Math.sin((progress / 0.22f) * Math.PI);
                    scaleX = 1f - 0.05f * k;
                    scaleY = 1f + 0.07f * k;
                } else {
                    scaleX = 1.04f;
                    scaleY = 0.97f;
                }
                tiltDeg = (float) (-Math.cos(progress * Math.PI) * 11.0);
            }
        } else if (state != null) {
            state.currentPos.set(restX, restY);
            // Soft damped landing squash-and-stretch
            if (!isPreview && state.landTime > 0 && now - state.landTime < 240) {
                float p = (now - state.landTime) / 240f;
                float bounce = (float) (Math.sin(p * Math.PI) * Math.exp(-p * 1.4) * 0.14);
                scaleX = 1f + bounce;
                scaleY = 1f - bounce;
            } else if (!isPreview) {
                // Gentle idle breathing synchronized with hover bob
                float breath = (float) Math.sin(t * 2.8);
                scaleX = 1f + breath * 0.018f;
                scaleY = 1f - breath * 0.018f;
            }
            float bob = isPreview ? 0f : (float) (Math.sin(t * 2.8) * dp(2.4f));
            drawY = restY + bob;
            shadowY = restY;
            shadowScale = isPreview ? 1f : (1f - (bob / Math.max(1f, dp(24f))));
        }

        Bitmap bmp = jumping ? ensureBitmap("soot-jump") : ensureBitmap("soot-idle");
        if (bmp == null) bmp = ensureBitmap("soot-idle");

        // Unified sprite size so idle and jump bodies match seamlessly with zero size pop
        float spriteSize = r * 1.48f;
        // Grounded shadow beneath soot sprite using dedicated shadowPaint
        shadowPaint.setColor((shadowAlpha << 24) | 0x0b1611);
        float shRx = spriteSize * 0.26f * shadowScale;
        float shRy = spriteSize * 0.065f * shadowScale;
        tempOval.set(shadowX - shRx, shadowY + spriteSize * 0.34f - shRy,
                shadowX + shRx, shadowY + spriteSize * 0.34f + shRy);
        canvas.drawOval(tempOval, shadowPaint);

        if (bmp != null && !bmp.isRecycled()) {
            canvas.save();
            canvas.translate(drawX, drawY);
            canvas.scale(dir * scaleX, scaleY);
            if (tiltDeg != 0f) {
                canvas.rotate(tiltDeg);
            }
            paint.setAlpha(255);
            dstRect.set(-spriteSize / 2f, -spriteSize / 2f, spriteSize / 2f, spriteSize / 2f);
            canvas.drawBitmap(bmp, null, dstRect, paint);
            canvas.restore();
        } else {
            // Clean procedural fallback while async decode finishes
            fillPaint.setColor(Color.parseColor("#1c2020"));
            fillPaint.setAlpha(240);
            canvas.drawCircle(drawX, drawY, r * 0.42f, fillPaint);
            fillPaint.setColor(Color.WHITE);
            canvas.drawCircle(drawX - r * 0.12f, drawY - r * 0.05f, r * 0.11f, fillPaint);
            canvas.drawCircle(drawX + r * 0.12f, drawY - r * 0.05f, r * 0.11f, fillPaint);
        }

        // Orbiting pastel konpeito star candies
        int starCount = 4;
        for (int i = 0; i < starCount; i++) {
            double ang = (isPreview ? 0.5 : t * 0.85) + i * (TAU / starCount);
            float sx = cx + (float) (Math.cos(ang) * r * 1.18f);
            float sy = cy + (float) (Math.sin(ang) * r * 1.18f);
            fillPaint.setColor(KONPEITO_COLORS[i % KONPEITO_COLORS.length]);
            fillPaint.setAlpha(215);
            canvas.drawCircle(sx, sy, dp(3.6f), fillPaint);
        }
    }

    private void drawCalcifer(Canvas canvas, float cx, float cy, float r, double t, float burstLife, boolean isPreview) {
        Bitmap bmp = ensureBitmap("calcifer");
        float pulse = isPreview ? 1f : (1f + (float) Math.sin(t * 3.8) * 0.03f + burstLife * 0.07f);
        float size = r * 2.52f * pulse;

        if (bmp != null && !bmp.isRecycled()) {
            canvas.save();
            canvas.translate(cx, cy);
            float rotDeg = isPreview ? 0f : (float) ((t * 14.0) % 360.0);
            canvas.rotate(rotDeg);
            paint.setAlpha(255);
            if (isPreview) {
                dstRect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
                canvas.drawBitmap(bmp, null, dstRect, paint);
            } else {
                drawSwayMesh(canvas, bmp, size, t, 2.4, 0.011f);
            }
            canvas.restore();
        } else {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.parseColor("#f29d4b"));
            strokePaint.setStrokeWidth(dp(8f));
            strokePaint.setAlpha(185);
            canvas.drawCircle(cx, cy, r * 1.05f, strokePaint);
        }

        // Floating hearth embers
        for (int i = 0; i < 6; i++) {
            double ang = (isPreview ? 0.2 : t * 1.1) + i * (TAU / 6.0);
            float dist = r * (1.16f + (float) Math.sin(t * 2.5 + i * 1.4) * 0.08f);
            float ex = cx + (float) (Math.cos(ang) * dist);
            float ey = cy + (float) (Math.sin(ang) * dist);
            fillPaint.setColor(i % 2 == 0 ? Color.parseColor("#ffe29a") : Color.parseColor("#f28b42"));
            fillPaint.setAlpha(210);
            canvas.drawCircle(ex, ey, dp(2.8f + (i % 2)), fillPaint);
        }
    }

    private void drawKodama(Canvas canvas, float cx, float cy, float r, double t, float burstLife, boolean isPreview) {
        Bitmap bmp = ensureBitmap("kodama");
        float pulse = isPreview ? 1f : (1f + (float) Math.sin(t * 2.1) * 0.025f + burstLife * 0.06f);
        float size = r * 2.58f * pulse;

        if (bmp != null && !bmp.isRecycled()) {
            canvas.save();
            canvas.translate(cx, cy);
            float swayDeg = isPreview ? 0f : (float) (Math.sin(t * 1.2) * 5.0);
            canvas.rotate(swayDeg);
            paint.setAlpha(255);
            dstRect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
            canvas.drawBitmap(bmp, null, dstRect, paint);
            canvas.restore();
        } else {
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setColor(Color.parseColor("#a4e8be"));
            strokePaint.setStrokeWidth(dp(6f));
            strokePaint.setAlpha(180);
            canvas.drawCircle(cx, cy, r * 1.06f, strokePaint);
        }

        // Drifting forest fireflies
        for (int i = 0; i < 7; i++) {
            double ang = (isPreview ? 0.3 : t * 0.65) + i * (TAU / 7.0);
            float dist = r * (1.15f + (float) Math.cos(t * 1.9 + i) * 0.07f);
            float fx = cx + (float) (Math.cos(ang) * dist);
            float fy = cy + (float) (Math.sin(ang) * dist);
            fillPaint.setColor(Color.parseColor("#dcfce7"));
            fillPaint.setAlpha((int) (150 + 80 * Math.sin(t * 3.0 + i)));
            canvas.drawCircle(fx, fy, dp(2.6f), fillPaint);
        }
    }

    private void drawWindPetal(Canvas canvas, float cx, float cy, float r, double t, float burstLife) {
        float scale = 1f + burstLife * 0.08f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // Swirling emerald & cream summer wind ribbons
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        for (int ring = 0; ring < 3; ring++) {
            float rad = r * (0.96f + ring * 0.14f);
            float startDeg = (float) (((t * (42.0 + ring * 18.0)) + ring * 120.0) % 360.0);
            strokePaint.setColor(ring == 1 ? GhibliTheme.COLOR_PAPER : GhibliTheme.COLOR_SAGE);
            strokePaint.setAlpha(ring == 1 ? 145 : 185);
            strokePaint.setStrokeWidth(dp(2.6f - ring * 0.4f));
            tempOval.set(-rad, -rad, rad, rad);
            canvas.drawArc(tempOval, startDeg, 115f, false, strokePaint);
            canvas.drawArc(tempOval, (startDeg + 180f) % 360f, 85f, false, strokePaint);
        }

        // Orbiting painted camphor leaves & meadow petals
        for (int i = 0; i < 8; i++) {
            double ang = (t * 0.95) + i * (TAU / 8.0);
            float rad = r * (1.06f + (i % 2) * 0.15f);
            float px = (float) (Math.cos(ang) * rad);
            float py = (float) (Math.sin(ang) * rad);
            canvas.save();
            canvas.translate(px, py);
            float leafRot = (float) ((Math.toDegrees(ang) + 90.0 + Math.sin(t * 2.5 + i) * 22.0) % 360.0);
            canvas.rotate(leafRot);
            fillPaint.setColor(i % 2 == 0 ? Color.parseColor("#a6d696") : Color.parseColor("#f7d6dc"));
            fillPaint.setAlpha(225);
            float sz = dp(5.5f);
            tempPath.reset();
            tempPath.moveTo(-sz, 0);
            tempPath.quadTo(0, -sz * 0.72f, sz, 0);
            tempPath.quadTo(0, sz * 0.72f, -sz, 0);
            canvas.drawPath(tempPath, fillPaint);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawAetherium(Canvas canvas, float cx, float cy, float r, double t, float burstLife) {
        float scale = 1f + burstLife * 0.08f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // Luminous Laputa crystal aura halo
        fillPaint.setColor(Color.parseColor("#6ec8ff"));
        fillPaint.setAlpha(48);
        canvas.drawCircle(0, 0, r * 1.24f, fillPaint);

        // Outer & inner ancient golden rings
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(GhibliTheme.COLOR_GOLD);
        strokePaint.setAlpha(210);
        strokePaint.setStrokeWidth(dp(2.0f));
        canvas.drawCircle(0, 0, r * 1.15f, strokePaint);

        strokePaint.setColor(Color.parseColor("#9be2ff"));
        strokePaint.setAlpha(220);
        strokePaint.setStrokeWidth(dp(2.4f));
        canvas.drawCircle(0, 0, r * 1.00f, strokePaint);

        // Rotating Laputa geometric rune spokes
        float rot1 = (float) ((t * 18.0) % 360.0);
        canvas.save();
        canvas.rotate(rot1);
        strokePaint.setColor(GhibliTheme.COLOR_GOLD);
        strokePaint.setAlpha(195);
        strokePaint.setStrokeWidth(dp(1.6f));
        for (int k = 0; k < 8; k++) {
            canvas.save();
            canvas.rotate(k * 45f);
            canvas.drawLine(r * 1.00f, 0, r * 1.15f, 0, strokePaint);
            if (k % 2 == 0) {
                fillPaint.setColor(Color.parseColor("#bfefff"));
                fillPaint.setAlpha(235);
                tempPath.reset();
                tempPath.moveTo(r * 1.22f, 0);
                tempPath.lineTo(r * 1.15f, -dp(4.2f));
                tempPath.lineTo(r * 1.08f, 0);
                tempPath.lineTo(r * 1.15f, dp(4.2f));
                tempPath.close();
                canvas.drawPath(tempPath, fillPaint);
            }
            canvas.restore();
        }
        canvas.restore();

        // Counter-rotating inner crystal arc
        float rot2 = (float) (((-t * 28.0) % 360.0 + 360.0) % 360.0);
        canvas.save();
        canvas.rotate(rot2);
        strokePaint.setColor(Color.parseColor("#d8f6ff"));
        strokePaint.setAlpha(180);
        strokePaint.setStrokeWidth(dp(1.8f));
        tempOval.set(-r * 1.07f, -r * 1.07f, r * 1.07f, r * 1.07f);
        for (int a = 0; a < 4; a++) {
            canvas.drawArc(tempOval, a * 90f + 12f, 46f, false, strokePaint);
        }
        canvas.restore();

        canvas.restore();
    }

    private void drawPaperBirds(Canvas canvas, float cx, float cy, float r, double t, float burstLife) {
        float scale = 1f + burstLife * 0.08f;
        canvas.save();
        canvas.translate(cx, cy);
        canvas.scale(scale, scale);

        // Soft sky-mist air ring
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(GhibliTheme.COLOR_PAPER);
        strokePaint.setAlpha(95);
        strokePaint.setStrokeWidth(dp(1.8f));
        canvas.drawCircle(0, 0, r * 1.12f, strokePaint);

        // 6 gliding origami shikigami paper birds
        int birds = 6;
        for (int i = 0; i < birds; i++) {
            double ang = (t * 1.05) + i * (TAU / birds);
            float rad = r * (1.08f + (float) Math.sin(t * 2.0 + i * 1.3) * 0.08f);
            float bx = (float) (Math.cos(ang) * rad);
            float by = (float) (Math.sin(ang) * rad);
            float wing = (float) Math.sin(t * 6.5 + i * 1.7) * dp(4.0f);

            canvas.save();
            canvas.translate(bx, by);
            float dirDeg = (float) ((Math.toDegrees(ang) + 90.0) % 360.0);
            canvas.rotate(dirDeg);

            float s = dp(9.5f);
            tempPath.reset();
            tempPath.moveTo(s, 0);
            tempPath.lineTo(-s * 0.45f, -s * 0.85f + wing);
            tempPath.lineTo(-s * 0.15f, 0);
            tempPath.lineTo(-s * 0.45f, s * 0.85f - wing);
            tempPath.close();

            fillPaint.setColor(GhibliTheme.COLOR_PAPER);
            fillPaint.setAlpha(240);
            canvas.drawPath(tempPath, fillPaint);

            strokePaint.setColor(Color.parseColor("#8ea89c"));
            strokePaint.setAlpha(200);
            strokePaint.setStrokeWidth(dp(1.1f));
            canvas.drawPath(tempPath, strokePaint);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawSwayMesh(Canvas canvas, Bitmap bmp, float size, double t, double speed, float amp) {
        for (int row = 0; row <= MESH_H; row++) {
            float yRatio = (float) row / MESH_H;
            float y = -size / 2f + size * yRatio;
            float offset = (float) Math.sin(t * speed + yRatio * 6.5) * size * amp;
            for (int col = 0; col <= MESH_W; col++) {
                float xRatio = (float) col / MESH_W;
                float x = -size / 2f + size * xRatio + offset;
                int idx = 2 * (row * (MESH_W + 1) + col);
                meshVerts[idx] = x;
                meshVerts[idx + 1] = y;
            }
        }
        canvas.drawBitmapMesh(bmp, MESH_W, MESH_H, meshVerts, 0, null, 0, paint);
    }

    public void drawTrail(Canvas canvas, String technique, float ax, float ay, float bx, float by,
                          float life, double t) {
        if (life <= 0f) return;
        float progress = 1f - Math.min(1f, life / 0.45f);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        int col = GhibliTheme.COLOR_ACCENT;
        if ("kodama".equals(technique)) col = Color.parseColor("#a4e8be");
        else if ("aetherium".equals(technique)) col = Color.parseColor("#8fd8ff");
        else if ("wind_petal".equals(technique)) col = GhibliTheme.COLOR_SAGE;
        else if ("paper_birds".equals(technique)) col = GhibliTheme.COLOR_PAPER;

        strokePaint.setColor(col);
        strokePaint.setAlpha((int) (life * 220));
        strokePaint.setStrokeWidth(dp(3.0f));
        float mx = (ax + bx) * 0.5f;
        float my = (ay + by) * 0.5f - dp(14f);
        tempPath.reset();
        tempPath.moveTo(ax, ay);
        tempPath.quadTo(mx, my, bx, by);
        canvas.drawPath(tempPath, strokePaint);

        // Traveling sparkle dot
        float sx = (1 - progress) * (1 - progress) * ax + 2 * (1 - progress) * progress * mx + progress * progress * bx;
        float sy = (1 - progress) * (1 - progress) * ay + 2 * (1 - progress) * progress * my + progress * progress * by;
        fillPaint.setColor(GhibliTheme.COLOR_PAPER);
        fillPaint.setAlpha((int) (life * 255));
        canvas.drawCircle(sx, sy, dp(3.5f), fillPaint);
    }

    /**
     * Renders a deterministic single-frame static preview for SpiritsPanel gallery cards.
     */
    public void drawStaticPreview(Canvas canvas, String id, int width, int height) {
        float cx = width * 0.5f;
        float cy = height * 0.52f;
        float r = Math.min(width, height) * 0.30f;

        // Draw behind-icon spirit effects first
        boolean foregroundMascot = "soot_sprites".equals(id);
        if (!foregroundMascot) {
            drawTechnique(canvas, id, cx, cy, r * 1.18f, 1200L, 0f, true, null);
        }

        // Draw sample Ghibli Lampa medallion icon centered
        Bitmap sample = getSamplePreviewIcon();
        float iconSize = r * 1.72f;
        if (sample != null && !sample.isRecycled()) {
            paint.reset();
            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setAlpha(255);
            dstRect.set(cx - iconSize / 2f, cy - iconSize / 2f, cx + iconSize / 2f, cy + iconSize / 2f);
            canvas.drawBitmap(sample, null, dstRect, paint);
        } else {
            fillPaint.setColor(GhibliTheme.COLOR_CARD_BG);
            fillPaint.setAlpha(255);
            dstRect.set(cx - iconSize / 2f, cy - iconSize / 2f, cx + iconSize / 2f, cy + iconSize / 2f);
            canvas.drawRoundRect(dstRect, dp(12f), dp(12f), fillPaint);
        }

        // Draw foreground Soot Sprites perched cheerfully on top of the sample icon
        if (foregroundMascot) {
            drawTechnique(canvas, id, cx, cy, r * 1.05f, 1200L, 0f, true, null);
        }
    }

    private synchronized Bitmap getSamplePreviewIcon() {
        if (samplePreviewIcon != null && !samplePreviewIcon.isRecycled()) return samplePreviewIcon;
        try (InputStream is = context.getAssets().open("icons/icon-lampa.webp")) {
            samplePreviewIcon = BitmapFactory.decodeStream(is);
        } catch (Exception ignored) {}
        return samplePreviewIcon;
    }
}

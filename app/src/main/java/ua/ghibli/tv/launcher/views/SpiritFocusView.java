package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import ua.ghibli.tv.launcher.model.CatalogData;
import java.util.ArrayList;
import java.util.List;

/**
 * Live focus spirit overlay centered on the focused app card emblem.
 * Runs a lifecycle-gated frame loop when motion is enabled, and draws a crisp static
 * focus indicator when motion is disabled.
 */
public class SpiritFocusView extends View {
    private final SpiritRenderer renderer;
    // Share decoded bitmaps, never another overlay's clock or hop state.
    private final SpiritRenderer.SootState sootState = new SpiritRenderer.SootState();

    private String currentTechnique = CatalogData.DEFAULT_TECHNIQUE_ID;
    private View targetView = null;
    private final int[] targetLocation = new int[2];
    private final int[] overlayLocation = new int[2];

    private boolean isMotionEnabled = true;
    private boolean isActivityPaused = false;
    private boolean isDisposed = false;
    private boolean isListenerRegistered = false;
    private float burstLife = 0f;
    private long lastFrameTime = 0;
    private long animationStartTime = 0;

    private float currentCx = -1f;
    private float currentCy = -1f;
    private float currentR = 0f;
    private float targetCx = -1f;
    private float targetCy = -1f;
    private float targetR = 0f;
    private float currentAlpha = 0f;
    private View lastDrawnSootTarget;
    private float lastSootTargetX;
    private float lastSootTargetY;

    private static class Trail {
        float ax, ay, bx, by;
        float life;
        String technique;
    }
    private final List<Trail> activeTrails = new ArrayList<>();

    private final SpiritRenderer.OnBitmapLoadedListener loadListener = name -> {
        if (name == null) return;
        boolean matches = name.equals(currentTechnique)
                || ("soot_sprites".equals(currentTechnique) && name.startsWith("soot"));
        if (matches) postInvalidate();
    };

    private final Runnable animationRunnable = new Runnable() {
        @Override
        public void run() {
            if (!canRenderFrames() || !needsAnimation()) return;
            invalidate();
            postOnAnimation(this);
        }
    };

    public SpiritFocusView(Context context, SpiritRenderer renderer) {
        super(context);
        this.renderer = renderer;
    }

    private synchronized void registerBitmapListener() {
        if (!isListenerRegistered && renderer != null) {
            renderer.addOnBitmapLoadedListener(loadListener);
            isListenerRegistered = true;
        }
    }

    private synchronized void unregisterBitmapListener() {
        if (isListenerRegistered && renderer != null) {
            renderer.removeOnBitmapLoadedListener(loadListener);
            isListenerRegistered = false;
        }
    }

    public void pause() {
        isActivityPaused = true;
        resetSootTracking();
        removeCallbacks(animationRunnable);
    }

    public void resume() {
        isActivityPaused = false;
        startAnimationLoop();
    }

    public boolean isPaused() {
        return isActivityPaused;
    }

    private void startAnimationLoop() {
        removeCallbacks(animationRunnable);
        if (canRenderFrames() && needsAnimation()) postOnAnimation(animationRunnable);
    }

    private boolean canRenderFrames() {
        return !isDisposed && !isActivityPaused && isAttachedToWindow()
                && getWindowVisibility() == VISIBLE && isShown();
    }

    private boolean hasVisibleTarget() {
        return targetView != null && targetView.isAttachedToWindow() && targetView.isShown();
    }

    private boolean needsAnimation() {
        return isMotionEnabled && (hasVisibleTarget() || currentAlpha > 0.02f || !activeTrails.isEmpty());
    }

    public void setTechnique(String technique) {
        String next = technique != null ? technique : CatalogData.DEFAULT_TECHNIQUE_ID;
        if (!next.equals(currentTechnique)) resetSootTracking();
        currentTechnique = next;
        setElevation("soot_sprites".equals(this.currentTechnique) ? 20f : 5f);
        startAnimationLoop();
        invalidate();
    }

    public void setMotionEnabled(boolean enabled) {
        this.isMotionEnabled = enabled;
        if (!enabled) {
            activeTrails.clear();
            burstLife = 0f;
            resetSootTracking();
        }
        startAnimationLoop();
        invalidate();
    }

    public void triggerBurst() {
        this.burstLife = isMotionEnabled ? 1.0f : 0f;
        startAnimationLoop();
        invalidate();
    }

    public void setTargetView(View view) {
        if (this.targetView == view) return;
        this.targetView = view;
        this.burstLife = isMotionEnabled && view != null ? 1.0f : 0f;

        if (view != null) {
            View emblem = findEmblemView(view);
            if (emblem.getWidth() == 0 || emblem.getHeight() == 0) {
                startAnimationLoop();
                invalidate();
                return;
            }
            emblem.getLocationInWindow(targetLocation);
            getLocationInWindow(overlayLocation);
            float left = targetLocation[0] - overlayLocation[0];
            float top = targetLocation[1] - overlayLocation[1];
            float newTargetX = left + emblem.getWidth() / 2f;
            float newTargetY = top + emblem.getHeight() / 2f;
            float baseR = Math.min(emblem.getWidth(), emblem.getHeight()) * 0.45f;
            float newTargetR = baseR * getSpiritScale(currentTechnique);

            if (currentCx > 0 && currentCy > 0 && isMotionEnabled) {
                float dist = (float) Math.hypot(newTargetX - currentCx, newTargetY - currentCy);
                if (dist > 10f) {
                    Trail t = new Trail();
                    t.ax = currentCx;
                    t.ay = currentCy;
                    t.bx = newTargetX;
                    t.by = newTargetY;
                    t.life = 0.45f;
                    t.technique = currentTechnique;
                    activeTrails.add(t);
                    if (activeTrails.size() > 6) activeTrails.remove(0);
                }
            } else {
                currentCx = newTargetX;
                currentCy = newTargetY;
                currentR = newTargetR;
            }

            targetCx = newTargetX;
            targetCy = newTargetY;
            targetR = newTargetR;
        }

        startAnimationLoop();
        invalidate();
    }

    private void resetSootTracking() {
        lastDrawnSootTarget = null;
        sootState.isJumping = false;
        sootState.landTime = 0L;
    }

    private void trackSootTarget(long now) {
        View previous = lastDrawnSootTarget;
        if (previous != targetView) {
            float dx = Math.abs(targetCx - lastSootTargetX);
            float dy = Math.abs(targetCy - lastSootTargetY);
            boolean nearby = previous != null && previous.isAttachedToWindow()
                    && previous.getParent() == targetView.getParent()
                    && dx <= 1.5f * Math.max(previous.getWidth(), targetView.getWidth())
                    && dy <= 1.5f * Math.max(previous.getHeight(), targetView.getHeight());
            if (nearby && (dx > 10f || dy > 10f)) {
                renderer.hopSootTo(sootState, targetCx, targetCy, targetR, now);
            } else {
                resetSootTracking();
            }
        }
        lastDrawnSootTarget = targetView;
        lastSootTargetX = targetCx;
        lastSootTargetY = targetCy;
    }

    private View findEmblemView(View root) {
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                if (child instanceof ImageView) return child;
            }
        }
        return root;
    }

    public View getTargetView() {
        return targetView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        isDisposed = false;
        registerBitmapListener();
        startAnimationLoop();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(animationRunnable);
        unregisterBitmapListener();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE) startAnimationLoop();
        else removeCallbacks(animationRunnable);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) startAnimationLoop();
        else removeCallbacks(animationRunnable);
    }

    private float getSpiritScale(String id) {
        if ("soot_sprites".equals(id)) return 1.0f;
        if ("calcifer".equals(id) || "kodama".equals(id)) return 1.36f;
        return 1.32f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        long uptime = android.os.SystemClock.uptimeMillis();
        if (animationStartTime == 0) animationStartTime = uptime;
        long now = isMotionEnabled ? uptime - animationStartTime : 0;
        float dt = lastFrameTime > 0 ? (uptime - lastFrameTime) / 1000f : 0.016f;
        if (dt > 0.05f) dt = 0.05f;
        lastFrameTime = uptime;

        if (burstLife > 0f) {
            burstLife -= dt * 2f;
            if (burstLife < 0f) burstLife = 0f;
        }

        if (renderer != null && isMotionEnabled) {
            for (int i = activeTrails.size() - 1; i >= 0; i--) {
                Trail tr = activeTrails.get(i);
                tr.life -= dt;
                if (tr.life <= 0f) activeTrails.remove(i);
                else renderer.drawTrail(canvas, tr.technique, tr.ax, tr.ay, tr.bx, tr.by, tr.life, now / 1000.0);
            }
        }

        boolean visibleTarget = hasVisibleTarget();
        boolean sootActive = "soot_sprites".equals(currentTechnique);
        boolean measuredTarget = false;
        if (visibleTarget) {
            View emblem = findEmblemView(targetView);
            if (emblem.getWidth() > 0 && emblem.getHeight() > 0) {
                emblem.getLocationInWindow(targetLocation);
                getLocationInWindow(overlayLocation);
                float left = targetLocation[0] - overlayLocation[0];
                float top = targetLocation[1] - overlayLocation[1];
                targetCx = left + emblem.getWidth() / 2f;
                targetCy = top + emblem.getHeight() / 2f;
                float baseR = Math.min(emblem.getWidth(), emblem.getHeight()) * 0.45f;
                targetR = baseR * getSpiritScale(currentTechnique);
                measuredTarget = true;
            }
        }
        if (sootActive && isMotionEnabled && renderer != null) {
            if (measuredTarget) trackSootTarget(now);
            else resetSootTracking();
        }

        if (targetCx > 0 && targetCy > 0) {
            if (currentCx < 0 || sootActive || !isMotionEnabled) {
                currentCx = targetCx;
                currentCy = targetCy;
                currentR = targetR;
            } else {
                currentCx += (targetCx - currentCx) * 0.35f;
                currentCy += (targetCy - currentCy) * 0.35f;
                currentR += (targetR - currentR) * 0.35f;
            }

            float targetAlphaVal = visibleTarget ? 1.0f : 0.0f;
            if (isMotionEnabled) currentAlpha += (targetAlphaVal - currentAlpha) * 0.25f;
            else currentAlpha = targetAlphaVal;

            if (currentAlpha > 0.02f && renderer != null) {
                renderer.drawTechnique(canvas, currentTechnique, currentCx, currentCy, currentR,
                        now, burstLife, !isMotionEnabled, sootState);
            }
        }
    }
}

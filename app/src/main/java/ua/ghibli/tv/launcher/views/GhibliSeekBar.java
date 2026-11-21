package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Custom Studio Ghibli volume slider with honey-gold fill, parchment thumb,
 * and D-pad left/right step support.
 */
public class GhibliSeekBar extends View {
    public interface OnProgressChangeListener {
        void onProgressChanged(GhibliSeekBar seekBar, int progress);
    }

    private int max = 100;
    private int progress = 45;
    private OnProgressChangeListener listener;
    private boolean dragging;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF progressRect = new RectF();

    public GhibliSeekBar(Context context) {
        this(context, null);
    }

    public GhibliSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);

        trackPaint.setColor(Color.parseColor("#1d3328"));
        progressPaint.setColor(GhibliTheme.COLOR_ACCENT);
        thumbPaint.setColor(GhibliTheme.COLOR_PAPER);

        focusRingPaint.setStyle(Paint.Style.STROKE);
        focusRingPaint.setStrokeWidth(GhibliTheme.dp(context, 1.5f));
        focusRingPaint.setColor(GhibliTheme.COLOR_ACCENT);
    }

    public void setOnProgressChangeListener(OnProgressChangeListener listener) {
        this.listener = listener;
    }

    public void setMax(int max) {
        this.max = Math.max(1, max);
        invalidate();
    }

    public void setProgress(int progress) {
        int newProgress = Math.max(0, Math.min(max, progress));
        if (this.progress != newProgress) {
            this.progress = newProgress;
            invalidate();
            if (listener != null) listener.onProgressChanged(this, this.progress);
        }
    }

    public int getProgress() {
        return progress;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            setProgress(progress - 5);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            setProgress(progress + 5);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                requestFocus();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    updateFromTouch(event.getX());
                    performClick();
                }
                return true;
            default:
                return false;
        }
    }

    private void updateFromTouch(float x) {
        float inset = GhibliTheme.dp(getContext(), 7);
        float usableWidth = getWidth() - 2 * inset;
        if (usableWidth <= 0) return;
        float fraction = Math.max(0f, Math.min(1f, (x - inset) / usableWidth));
        setProgress(Math.round(fraction * max / 5f) * 5);
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = GhibliTheme.dp(getContext(), 24);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cy = h / 2f;
        float trackH = GhibliTheme.dp(getContext(), 6);
        float thumbR = GhibliTheme.dp(getContext(), 7);

        float left = thumbR;
        float right = w - thumbR;
        float usableW = right - left;

        float ratio = (float) progress / max;
        float thumbX = left + usableW * ratio;

        trackRect.set(left, cy - trackH / 2f, right, cy + trackH / 2f);
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint);

        if (thumbX > left) {
            progressRect.set(left, cy - trackH / 2f, thumbX, cy + trackH / 2f);
            canvas.drawRoundRect(progressRect, trackH / 2f, trackH / 2f, progressPaint);
        }

        canvas.drawCircle(thumbX, cy, thumbR, thumbPaint);

        if (hasFocus()) {
            canvas.drawCircle(thumbX, cy, thumbR + GhibliTheme.dp(getContext(), 3), focusRingPaint);
        }
    }
}

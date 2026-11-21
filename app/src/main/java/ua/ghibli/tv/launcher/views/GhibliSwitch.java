package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Custom Studio Ghibli toggle switch: warm cedar capsule with sunlit honey-gold active state.
 */
public class GhibliSwitch extends View implements Checkable {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(GhibliSwitch view, boolean isChecked);
    }

    private boolean isChecked = false;
    private OnCheckedChangeListener listener;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF focusRect = new RectF();

    private final int trackOffColor = Color.parseColor("#182b22");
    private final int trackOffBorderColor = Color.parseColor("#6694c684");
    private final int thumbOffColor = Color.parseColor("#8fa492");
    private final int thumbOffBorderColor = Color.parseColor("#d6ccb2");

    private final int trackOnColor = Color.parseColor("#3de8b858");
    private final int trackOnBorderColor = GhibliTheme.COLOR_ACCENT;
    private final int thumbOnColor = GhibliTheme.COLOR_ACCENT;
    private final int thumbOnBorderColor = GhibliTheme.COLOR_PAPER;

    public GhibliSwitch(Context context) {
        this(context, null);
    }

    public GhibliSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        trackPaint.setStyle(Paint.Style.FILL);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(GhibliTheme.dp(context, 1));
        thumbPaint.setStyle(Paint.Style.FILL);

        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setStrokeWidth(GhibliTheme.dp(context, 2));
        focusPaint.setColor(GhibliTheme.COLOR_ACCENT);

        setOnClickListener(v -> toggle());
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.isChecked != checked) {
            this.isChecked = checked;
            invalidate();
            if (listener != null) listener.onCheckedChanged(this, checked);
        }
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void toggle() {
        setChecked(!isChecked);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = GhibliTheme.dp(getContext(), 48);
        int h = GhibliTheme.dp(getContext(), 26);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float radius = h / 2f;

        trackRect.set(1f, 1f, w - 1f, h - 1f);
        trackPaint.setColor(isChecked ? trackOnColor : trackOffColor);
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint);
        borderPaint.setColor(isChecked ? trackOnBorderColor : trackOffBorderColor);
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint);

        float thumbRadius = radius - GhibliTheme.dp(getContext(), 3);
        float thumbX = isChecked ? (w - radius) : radius;
        float thumbY = h / 2f;
        thumbPaint.setColor(isChecked ? thumbOnColor : thumbOffColor);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);
        borderPaint.setColor(isChecked ? thumbOnBorderColor : thumbOffBorderColor);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, borderPaint);

        if (hasFocus()) {
            focusRect.set(0, 0, w, h);
            canvas.drawRoundRect(focusRect, radius + 2, radius + 2, focusPaint);
        }
    }
}

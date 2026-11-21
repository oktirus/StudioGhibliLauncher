package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Soft-rounded warm botanical outlined button for Studio Ghibli launcher header and controls.
 */
public class OutlinedGlyphButton extends LinearLayout {
    private final GlyphIconView iconView;
    private final TextView labelView;
    private final RectF borderRect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isFocusedState = false;
    private boolean isPill = false;
    private boolean isBorderless = false;
    private int normalBorderColor = Color.parseColor("#55e8b858");
    private int focusedBorderColor = GhibliTheme.COLOR_ACCENT;
    private int normalBgColor = Color.parseColor("#7a15251d");
    private int focusedBgColor = Color.parseColor("#c2284234");
    private float cornerRadius;

    public OutlinedGlyphButton(Context context) {
        this(context, null);
    }

    public OutlinedGlyphButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);

        cornerRadius = GhibliTheme.dp(context, 12);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(GhibliTheme.dp(context, 1.2f));
        bgPaint.setStyle(Paint.Style.FILL);

        int padH = GhibliTheme.dp(context, 12);
        int padV = GhibliTheme.dp(context, 7);
        setPadding(padH, padV, padH, padV);

        iconView = new GlyphIconView(context);
        int iconSize = GhibliTheme.dp(context, 16);
        addView(iconView, new LayoutParams(iconSize, iconSize));

        labelView = new TextView(context);
        labelView.setTypeface(GhibliTheme.getNunito(context));
        labelView.setTextColor(GhibliTheme.COLOR_PAPER);
        labelView.setTextSize(11.5f);
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(GhibliTheme.dp(context, 8), 0, 0, 0);
        addView(labelView, lp);
        labelView.setVisibility(GONE);

        setOnFocusChangeListener((v, hasFocus) -> {
            isFocusedState = hasFocus;
            invalidate();
        });
    }

    public void setPill(boolean pill) {
        this.isPill = pill;
        invalidate();
    }

    public void setBorderless(boolean borderless) {
        this.isBorderless = borderless;
        if (borderless) {
            int p = GhibliTheme.dp(getContext(), 7);
            setPadding(p, p, p, p);
        }
        invalidate();
    }

    public void setGlyph(GlyphIconView.Glyph glyph) {
        iconView.setGlyph(glyph);
    }

    public void setText(String text) {
        if (text != null && !text.isEmpty()) {
            labelView.setText(text);
            labelView.setVisibility(VISIBLE);
        } else {
            labelView.setVisibility(GONE);
        }
    }

    public void setIconSize(int sizeDp) {
        int px = GhibliTheme.dp(getContext(), sizeDp);
        iconView.setLayoutParams(new LayoutParams(px, px));
    }

    public void setColors(int normalBorder, int focusedBorder, int normalBg, int focusedBg) {
        this.normalBorderColor = normalBorder;
        this.focusedBorderColor = focusedBorder;
        this.normalBgColor = normalBg;
        this.focusedBgColor = focusedBg;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (isBorderless && !isFocusedState) {
            super.onDraw(canvas);
            return;
        }

        float stroke = borderPaint.getStrokeWidth();
        borderRect.set(stroke, stroke, getWidth() - stroke, getHeight() - stroke);
        float r = isPill ? (getHeight() / 2f) : cornerRadius;

        if (!isBorderless || isFocusedState) {
            bgPaint.setColor(isFocusedState ? focusedBgColor : normalBgColor);
            canvas.drawRoundRect(borderRect, r, r, bgPaint);

            borderPaint.setColor(isFocusedState ? focusedBorderColor : normalBorderColor);
            canvas.drawRoundRect(borderRect, r, r, borderPaint);
        }

        super.onDraw(canvas);
    }
}

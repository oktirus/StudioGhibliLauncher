package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Spirit focus effect shortcut pill in the Favorites dock heading:
 * Colored Spirit Dot + Spirit Name + Spark icon.
 */
public class SpiritShortcutButton extends LinearLayout {
    private final View dotView;
    private final TextView nameView;
    private final GlyphIconView sparkIcon;
    private final RectF borderRect = new RectF();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isFocusedState = false;
    private int dotColor = GhibliTheme.COLOR_ACCENT;

    public SpiritShortcutButton(Context context) {
        this(context, null);
    }

    public SpiritShortcutButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setWillNotDraw(false);

        int padH = GhibliTheme.dp(context, 10);
        int padV = GhibliTheme.dp(context, 5);
        setPadding(padH, padV, padH, padV);

        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#6615251d"));

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(GhibliTheme.dp(context, 1.2f));
        borderPaint.setColor(GhibliTheme.COLOR_ACCENT);

        dotView = new View(context) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(dotColor);
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, p);
            }
        };
        int dotSize = GhibliTheme.dp(context, 7);
        addView(dotView, new LayoutParams(dotSize, dotSize));

        nameView = new TextView(context);
        nameView.setTypeface(GhibliTheme.getNunito(context));
        nameView.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        nameView.setTextSize(11.5f);
        LayoutParams nLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nLp.setMargins(GhibliTheme.dp(context, 7), 0, GhibliTheme.dp(context, 7), 0);
        addView(nameView, nLp);

        sparkIcon = new GlyphIconView(context);
        sparkIcon.setGlyph(GlyphIconView.Glyph.SPIRIT_SPARK);
        sparkIcon.setIconColor(GhibliTheme.COLOR_ACCENT);
        int sSize = GhibliTheme.dp(context, 15);
        addView(sparkIcon, new LayoutParams(sSize, sSize));

        setOnFocusChangeListener((v, hasFocus) -> {
            isFocusedState = hasFocus;
            invalidate();
        });
    }

    public void setTechnique(String name, int color) {
        nameView.setText(name);
        this.dotColor = color;
        sparkIcon.setIconColor(color);
        dotView.invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = borderPaint.getStrokeWidth();
        borderRect.set(s, s, getWidth() - s, getHeight() - s);
        float r = getHeight() / 2f;
        if (isFocusedState) {
            bgPaint.setColor(Color.parseColor("#b8284234"));
            canvas.drawRoundRect(borderRect, r, r, bgPaint);
            canvas.drawRoundRect(borderRect, r, r, borderPaint);
        } else {
            bgPaint.setColor(Color.parseColor("#5515251d"));
            canvas.drawRoundRect(borderRect, r, r, bgPaint);
        }
        super.onDraw(canvas);
    }
}

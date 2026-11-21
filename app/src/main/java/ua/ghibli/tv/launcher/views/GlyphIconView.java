package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Handcrafted vector glyphs tailored to the Studio Ghibli botanical & storybook aesthetic.
 */
public class GlyphIconView extends View {
    public enum Glyph {
        GAMEPAD,
        LANTERN_SETTINGS,
        WALLPAPERS,
        SPIRIT_SPARK,
        CLOSE,
        LEAF_EMBLEM,
        ARROW_UP,
        ARROW_DOWN,
        ACORN_SEAL
    }

    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Glyph glyph = Glyph.GAMEPAD;
    private int iconColor = GhibliTheme.COLOR_PAPER;

    public GlyphIconView(Context context) {
        super(context);
        init();
    }

    public GlyphIconView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2f);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setColor(iconColor);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(iconColor);
    }

    public void setGlyph(Glyph glyph) {
        this.glyph = glyph;
        invalidate();
    }

    public void setIconColor(int color) {
        this.iconColor = color;
        strokePaint.setColor(color);
        fillPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.save();
        float scale = Math.min(w, h) / 24f;
        canvas.translate((w - 24f * scale) / 2f, (h - 24f * scale) / 2f);
        canvas.scale(scale, scale);

        strokePaint.setStrokeWidth(1.8f);

        switch (glyph) {
            case GAMEPAD:
                path.reset();
                path.moveTo(7, 7);
                path.lineTo(17, 7);
                path.quadTo(20, 7, 21, 11);
                path.lineTo(22, 18);
                path.quadTo(22, 21, 19, 20);
                path.lineTo(15, 17);
                path.lineTo(9, 17);
                path.lineTo(5, 20);
                path.quadTo(2, 21, 2, 18);
                path.lineTo(3, 11);
                path.quadTo(4, 7, 7, 7);
                path.close();
                canvas.drawPath(path, strokePaint);
                canvas.drawLine(7, 10, 7, 14, strokePaint);
                canvas.drawLine(5, 12, 9, 12, strokePaint);
                canvas.drawCircle(17, 11, 1.2f, fillPaint);
                canvas.drawCircle(19, 13, 1.2f, fillPaint);
                break;

            case LANTERN_SETTINGS:
                // Hanging Ghibli bathhouse lantern / notebook sliders
                canvas.drawLine(12, 2, 12, 5, strokePaint);
                canvas.drawLine(8, 5, 16, 5, strokePaint);
                path.reset();
                path.addRoundRect(5.5f, 5f, 18.5f, 19f, 5.5f, 5.5f, Path.Direction.CW);
                canvas.drawPath(path, strokePaint);
                canvas.drawLine(7, 10, 17, 10, strokePaint);
                canvas.drawLine(7, 14, 17, 14, strokePaint);
                canvas.drawLine(9, 19, 15, 19, strokePaint);
                canvas.drawLine(12, 19, 12, 22, strokePaint);
                break;

            case WALLPAPERS:
                path.reset();
                path.moveTo(3, 16);
                path.lineTo(3, 3);
                path.lineTo(19, 3);
                canvas.drawPath(path, strokePaint);

                path.reset();
                path.addRoundRect(7, 7, 21, 21, 2f, 2f, Path.Direction.CW);
                canvas.drawPath(path, strokePaint);

                canvas.drawCircle(16.5f, 11.5f, 1.5f, strokePaint);
                path.reset();
                path.moveTo(7, 18);
                path.lineTo(11, 14);
                path.lineTo(15, 18);
                path.lineTo(17, 16);
                path.lineTo(21, 20);
                canvas.drawPath(path, strokePaint);
                break;

            case SPIRIT_SPARK:
                // Konpeito / woodland star spark
                path.reset();
                path.moveTo(12, 2.5f);
                path.lineTo(14.6f, 8.8f);
                path.lineTo(21.5f, 10.5f);
                path.lineTo(16.2f, 14.8f);
                path.lineTo(17.8f, 21.5f);
                path.lineTo(12, 17.8f);
                path.lineTo(6.2f, 21.5f);
                path.lineTo(7.8f, 14.8f);
                path.lineTo(2.5f, 10.5f);
                path.lineTo(9.4f, 8.8f);
                path.close();
                canvas.drawPath(path, strokePaint);
                canvas.drawCircle(12, 12.2f, 1.8f, fillPaint);
                break;

            case CLOSE:
                canvas.drawLine(5, 5, 19, 19, strokePaint);
                canvas.drawLine(5, 19, 19, 5, strokePaint);
                break;

            case ARROW_UP:
                canvas.drawLine(12, 19, 12, 5, strokePaint);
                path.reset();
                path.moveTo(6, 11);
                path.lineTo(12, 5);
                path.lineTo(18, 11);
                canvas.drawPath(path, strokePaint);
                break;

            case ARROW_DOWN:
                canvas.drawLine(12, 5, 12, 19, strokePaint);
                path.reset();
                path.moveTo(6, 13);
                path.lineTo(12, 19);
                path.lineTo(18, 13);
                canvas.drawPath(path, strokePaint);
                break;

            case ACORN_SEAL:
            case LEAF_EMBLEM:
                // Camphor leaf with center vein
                path.reset();
                path.moveTo(12, 3);
                path.quadTo(20, 8, 18.5f, 16);
                path.quadTo(15.5f, 20.5f, 12, 20.5f);
                path.quadTo(8.5f, 20.5f, 5.5f, 16);
                path.quadTo(4, 8, 12, 3);
                path.close();
                canvas.drawPath(path, strokePaint);
                canvas.drawLine(12, 6, 12, 22, strokePaint);
                canvas.drawLine(12, 11, 15.5f, 8.5f, strokePaint);
                canvas.drawLine(12, 14.5f, 8.5f, 12f, strokePaint);
                break;
        }

        canvas.restore();
    }
}

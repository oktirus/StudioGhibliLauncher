package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

/**
 * Storybook display title for Studio Ghibli scenes.
 * First line: Caveat Bold in warm parchment cream (#f6eed8).
 * Second line (em): Caveat Bold in sunlit honey gold (#e8b858), gently tilted.
 */
public class HeroTitleView extends View {
    private final Paint line1Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line2Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String line1 = "Подих";
    private String line2 = "лісу.";
    private final Rect bounds1 = new Rect();
    private final Rect bounds2 = new Rect();

    public HeroTitleView(Context context) {
        super(context);
        init();
    }

    public HeroTitleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        android.graphics.Typeface caveat = GhibliTheme.getCaveatBold(getContext());
        line1Paint.setTypeface(caveat);
        line1Paint.setFakeBoldText(false);
        line1Paint.setLetterSpacing(-0.02f);
        line1Paint.setColor(GhibliTheme.COLOR_PAPER);
        line1Paint.setShadowLayer(14f, 0f, 3f, 0x800c1913);

        line2Paint.setTypeface(caveat);
        line2Paint.setFakeBoldText(false);
        line2Paint.setLetterSpacing(-0.02f);
        line2Paint.setColor(GhibliTheme.COLOR_ACCENT);
        line2Paint.setShadowLayer(14f, 0f, 3f, 0x800c1913);
    }

    public void setTitle(String htmlTitle) {
        if (htmlTitle == null || htmlTitle.isEmpty()) {
            line1 = "";
            line2 = "";
        } else {
            String clean = htmlTitle.replace("</em>", "");
            if (clean.contains("<br>")) {
                String[] parts = clean.split("<br>", 2);
                line1 = parts[0].replace("<em>", "").trim();
                line2 = parts[1].replace("<em>", "").trim();
            } else if (clean.contains("<em>")) {
                String[] parts = clean.split("<em>", 2);
                line1 = parts[0].trim();
                line2 = parts[1].trim();
            } else {
                line1 = clean.trim();
                line2 = "";
            }
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float textSize = GhibliTheme.dp(getContext(), 76);
        float lineHeight = line2.isEmpty() ? 1.15f : 1.95f;
        int height = resolveSize((int) (textSize * lineHeight), heightMeasureSpec);
        textSize = Math.min(textSize, height / lineHeight);
        line1Paint.setTextSize(textSize);
        line2Paint.setTextSize(textSize);

        line1Paint.getTextBounds(line1, 0, line1.length(), bounds1);
        if (!line2.isEmpty()) {
            line2Paint.getTextBounds(line2, 0, line2.length(), bounds2);
        } else {
            bounds2.setEmpty();
        }

        int width = (int) (Math.max(bounds1.width(), bounds2.width()) + GhibliTheme.dp(getContext(), 32));
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float textSize = line1Paint.getTextSize();
        float y1 = textSize * 0.88f;
        canvas.drawText(line1, GhibliTheme.dp(getContext(), 4), y1, line1Paint);

        if (!line2.isEmpty()) {
            float y2 = y1 + textSize * 0.86f;
            canvas.save();
            canvas.rotate(-3.5f, GhibliTheme.dp(getContext(), 4), y2);
            canvas.drawText(line2, GhibliTheme.dp(getContext(), 10), y2, line2Paint);
            canvas.restore();
        }
    }
}

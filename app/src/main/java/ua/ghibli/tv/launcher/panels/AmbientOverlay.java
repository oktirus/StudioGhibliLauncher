package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.GlyphIconView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Studio Ghibli Ambient screen ("Тиха мить"): calm clock, date, poetic quote,
 * botanical leaf emblem, and warm dusk scrim so the full artwork breathes.
 */
public class AmbientOverlay extends FrameLayout {
    public interface OnAmbientExitListener {
        void onAmbientExit();
    }

    private final TextView timeView;
    private final TextView dateView;
    private final TextView quoteView;
    private final FrameLayout exitBtn;
    private final Handler timeHandler = new Handler(Looper.getMainLooper());
    private OnAmbientExitListener exitListener;

    private final Runnable timeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTime();
            timeHandler.postDelayed(this, 10000);
        }
    };

    public AmbientOverlay(Context context) {
        super(context);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.parseColor("#a613211b"), Color.parseColor("#2213211b")}
        );
        setBackground(bg);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);

        int padH = GhibliTheme.dp(context, 56);
        int padV = GhibliTheme.dp(context, 40);
        setPadding(padH, padV, padH, padV);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout brandBox = new LinearLayout(context);
        brandBox.setOrientation(LinearLayout.HORIZONTAL);
        brandBox.setGravity(Gravity.CENTER_VERTICAL);

        GlyphIconView leafIcon = new GlyphIconView(context);
        leafIcon.setGlyph(GlyphIconView.Glyph.LEAF_EMBLEM);
        leafIcon.setIconColor(GhibliTheme.COLOR_SAGE);
        int lSize = GhibliTheme.dp(context, 24);
        brandBox.addView(leafIcon, new LinearLayout.LayoutParams(lSize, lSize));

        TextView brandText = new TextView(context);
        brandText.setText("Тихі миті стають теплими спогадами.");
        brandText.setTextColor(GhibliTheme.COLOR_GOLD);
        brandText.setTextSize(12);
        brandText.setTypeface(GhibliTheme.getNunito(context));
        brandText.setPadding(GhibliTheme.dp(context, 10), 0, 0, 0);
        brandBox.addView(brandText);

        layout.addView(brandBox);

        View s1 = new View(context);
        layout.addView(s1, new LinearLayout.LayoutParams(1, 0, 1.0f));

        timeView = new TextView(context);
        timeView.setTextColor(GhibliTheme.COLOR_PAPER);
        timeView.setTextSize(110);
        timeView.setTypeface(GhibliTheme.getCaveatBold(context));
        timeView.setShadowLayer(25f, 0, 8f, Color.parseColor("#99000000"));
        layout.addView(timeView);

        dateView = new TextView(context);
        dateView.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        dateView.setTextSize(16);
        dateView.setTypeface(GhibliTheme.getNunito(context));
        dateView.setPadding(0, GhibliTheme.dp(context, 8), 0, 0);
        layout.addView(dateView);

        quoteView = new TextView(context);
        quoteView.setText("Дерева й люди колись були найкращими друзями.");
        quoteView.setTextColor(GhibliTheme.COLOR_GOLD);
        quoteView.setTextSize(24);
        quoteView.setTypeface(GhibliTheme.getCaveatBold(context));
        quoteView.setPadding(0, GhibliTheme.dp(context, 20), 0, 0);
        layout.addView(quoteView);

        View s2 = new View(context);
        layout.addView(s2, new LinearLayout.LayoutParams(1, 0, 1.0f));

        exitBtn = new FrameLayout(context);
        exitBtn.setFocusable(true);
        exitBtn.setFocusableInTouchMode(true);
        exitBtn.setClickable(true);
        exitBtn.setPadding(GhibliTheme.dp(context, 16), GhibliTheme.dp(context, 9),
                GhibliTheme.dp(context, 16), GhibliTheme.dp(context, 9));

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#551c3127"));
        btnBg.setCornerRadius(GhibliTheme.dp(context, 14));
        btnBg.setStroke(GhibliTheme.dp(context, 1), Color.parseColor("#55e8b858"));
        exitBtn.setBackground(btnBg);

        TextView exitText = new TextView(context);
        exitText.setText("OK / Back — повернутися");
        exitText.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        exitText.setTextSize(12);
        exitText.setTypeface(GhibliTheme.getNunito(context));
        exitBtn.addView(exitText);

        exitBtn.setOnClickListener(v -> exit());
        exitBtn.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? Color.parseColor("#99284234") : Color.parseColor("#551c3127"));
            d.setCornerRadius(GhibliTheme.dp(getContext(), 14));
            d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#55e8b858"));
            v.setBackground(d);
        });

        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layout.addView(exitBtn, bLp);

        addView(layout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        setOnClickListener(v -> exit());
        updateTime();
    }

    public void setQuote(String quote) {
        if (quote != null && !quote.isEmpty()) {
            quoteView.setText(quote);
        }
    }

    public void setOnAmbientExitListener(OnAmbientExitListener listener) {
        this.exitListener = listener;
    }

    public void start() {
        setVisibility(VISIBLE);
        updateTime();
        timeHandler.removeCallbacks(timeRunnable);
        timeHandler.post(timeRunnable);
        post(() -> exitBtn.requestFocus());
    }

    public void stop() {
        setVisibility(GONE);
        timeHandler.removeCallbacks(timeRunnable);
    }

    public void resume() {
        if (getVisibility() == VISIBLE) {
            updateTime();
            timeHandler.removeCallbacks(timeRunnable);
            timeHandler.post(timeRunnable);
            post(() -> exitBtn.requestFocus());
        }
    }

    public void pause() {
        timeHandler.removeCallbacks(timeRunnable);
    }

    private void exit() {
        if (exitListener != null) exitListener.onAmbientExit();
    }

    private void updateTime() {
        Date now = new Date();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", new Locale("uk", "UA"));
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", new Locale("uk", "UA"));
        timeView.setText(timeFmt.format(now));
        dateView.setText(dateFmt.format(now));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateTime();
        timeHandler.postDelayed(timeRunnable, 10000);
        post(() -> exitBtn.requestFocus());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        timeHandler.removeCallbacks(timeRunnable);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_MENU) {
            exit();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

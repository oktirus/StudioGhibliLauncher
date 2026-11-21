package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.theme.GhibliTheme;

public class ToastOverlay extends FrameLayout {
    private final TextView messageView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideRunnable = () -> setVisibility(GONE);

    public ToastOverlay(Context context) {
        super(context);
        setVisibility(GONE);

        messageView = new TextView(context);
        messageView.setTextColor(GhibliTheme.COLOR_INK);
        messageView.setBackground(GhibliTheme.createRoundedBorder(
                GhibliTheme.COLOR_PAPER, GhibliTheme.COLOR_ACCENT, dp(14), dp(1.5f)));
        messageView.setTypeface(GhibliTheme.getNunitoBold(context));
        messageView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        messageView.setPadding(dp(22), dp(12), dp(22), dp(12));
        messageView.setGravity(Gravity.CENTER);

        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(32);
        addView(messageView, lp);
    }

    public void show(String message, long durationMs) {
        handler.removeCallbacks(hideRunnable);
        messageView.setText(message);
        setVisibility(VISIBLE);
        handler.postDelayed(hideRunnable, durationMs);
    }

    public void hide() {
        handler.removeCallbacks(hideRunnable);
        setVisibility(GONE);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }
}

package ua.ghibli.tv.launcher.views;

import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;

import ua.ghibli.tv.launcher.R;

/** Separates a remote OK gesture from regular touch and accessibility clicks. */
public final class TVKeyHelper {
    public interface OnTVCardClickListener {
        void onClick(View v);
        void onLongClick(View v);
    }

    private TVKeyHelper() {}

    public static void cancelPendingPress(View view) {
        if (view != null && view.getTag(R.id.tv_key_cancel_press) instanceof Runnable cancelPress) {
            cancelPress.run();
        }
    }

    public static void attach(View view, OnTVCardClickListener listener) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.setClickable(true);

        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] pendingKey = {KeyEvent.KEYCODE_UNKNOWN};

        final Runnable longPressRunnable = () -> {
            pendingKey[0] = KeyEvent.KEYCODE_UNKNOWN;
            if (listener != null) listener.onLongClick(view);
        };
        final Runnable cancelPress = () -> {
            pendingKey[0] = KeyEvent.KEYCODE_UNKNOWN;
            handler.removeCallbacks(longPressRunnable);
        };
        view.setTag(R.id.tv_key_cancel_press, cancelPress);

        view.setOnClickListener(v -> {
            if (listener != null) listener.onClick(v);
        });

        view.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        cancelPress.run();
                        pendingKey[0] = keyCode;
                        handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    }
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (pendingKey[0] == keyCode) {
                        cancelPress.run();
                        if (!event.isCanceled()) v.performClick();
                    }
                    return true;
                }
            }
            return false;
        });

        final View.OnFocusChangeListener prevFocus = view.getOnFocusChangeListener();
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                cancelPress.run();
            }
            if (prevFocus != null) {
                prevFocus.onFocusChange(v, hasFocus);
            }
        });

        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                cancelPress.run();
            }
        });
    }
}

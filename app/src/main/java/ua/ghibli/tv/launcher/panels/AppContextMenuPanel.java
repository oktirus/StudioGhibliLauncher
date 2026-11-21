package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Studio Ghibli TV Context Menu designed for D-pad remote control.
 */
public class AppContextMenuPanel extends FrameLayout {
    public interface OnContextMenuActionListener {
        void onLaunch(AppItem app);
        void onTogglePin(AppItem app);
        void onMove(AppItem app, int direction);
        void onOpenOrder();
        void onAppInfo(AppItem app);
        void onDismiss();
    }

    private final AppItem app;
    private final boolean isFromDock;
    private final int dockPosition;
    private final int dockCount;
    private final OnContextMenuActionListener listener;
    private View firstFocusableAction;
    private boolean waitingForInitialKeyUp = true;

    public AppContextMenuPanel(Context context, AppItem app, boolean isFromDock,
                               int dockPosition, int dockCount,
                               OnContextMenuActionListener listener) {
        super(context);
        this.app = app;
        this.isFromDock = isFromDock;
        this.dockPosition = dockPosition;
        this.dockCount = dockCount;
        this.listener = listener;

        init();
    }

    private void init() {
        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        setBackgroundColor(Color.parseColor("#B813211b"));

        setOnClickListener(v -> {
            if (listener != null) listener.onDismiss();
        });

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setClickable(true);

        GradientDrawable cardBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{GhibliTheme.COLOR_PANEL_START, GhibliTheme.COLOR_PANEL_END}
        );
        cardBg.setCornerRadius(GhibliTheme.dp(getContext(), 16));
        cardBg.setStroke(GhibliTheme.dp(getContext(), 2), GhibliTheme.COLOR_ACCENT);

        int padH = GhibliTheme.dp(getContext(), 24);
        int padV = GhibliTheme.dp(getContext(), 20);
        card.setPadding(padH, padV, padH, padV);

        int dialogWidth = Math.min(
                GhibliTheme.dp(getContext(), 460),
                (int) (getResources().getDisplayMetrics().widthPixels * 0.85f)
        );
        ScrollView cardScroll = new ScrollView(getContext());
        cardScroll.setBackground(cardBg);
        cardScroll.setFocusable(false);
        cardScroll.setSmoothScrollingEnabled(false);
        LayoutParams cardLp = new LayoutParams(dialogWidth, LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        cardLp.topMargin = GhibliTheme.dp(getContext(), 16);
        cardLp.bottomMargin = cardLp.topMargin;
        addView(cardScroll, cardLp);
        cardScroll.addView(card, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, GhibliTheme.dp(getContext(), 14));

        ImageView iconView = new ImageView(getContext());
        int iconSize = GhibliTheme.dp(getContext(), 52);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bindAppIcon(iconView, app);
        header.addView(iconView);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(GhibliTheme.dp(getContext(), 14), 0, 0, 0);

        TextView titleView = new TextView(getContext());
        titleView.setText(app.getName());
        titleView.setTextColor(GhibliTheme.COLOR_PAPER);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleView);

        TextView statusView = new TextView(getContext());
        String statusText;
        if (isFromDock) {
            statusText = "★ Головний екран (позиція " + (dockPosition + 1) + " з " + dockCount + ")";
        } else if (app.isPinned()) {
            statusText = "★ В улюбленому";
        } else {
            statusText = "✦ У каталозі застосунків";
        }
        statusView.setText(statusText);
        statusView.setTextColor(app.isPinned() ? GhibliTheme.COLOR_ACCENT : GhibliTheme.COLOR_MUTED);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        statusView.setTypeface(GhibliTheme.getNunito(getContext()));
        textCol.addView(statusView);

        header.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        card.addView(header);

        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#44e8b858"));
        card.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, GhibliTheme.dp(getContext(), 1)));

        LinearLayout actionList = new LinearLayout(getContext());
        actionList.setOrientation(LinearLayout.VERTICAL);
        actionList.setPadding(0, GhibliTheme.dp(getContext(), 10), 0, 0);
        card.addView(actionList);

        List<View> actionButtons = new ArrayList<>();

        View btnLaunch = createActionButton("▶  Відкрити застосунок", () -> {
            if (listener != null) listener.onLaunch(app);
        });
        actionButtons.add(btnLaunch);
        actionList.addView(btnLaunch);
        firstFocusableAction = btnLaunch;

        if (isFromDock && dockPosition > 0) {
            View btnMoveLeft = createActionButton("◀  Перемістити ліворуч", () -> {
                if (listener != null) listener.onMove(app, -1);
            });
            actionButtons.add(btnMoveLeft);
            actionList.addView(btnMoveLeft);
        }

        if (isFromDock && dockPosition < dockCount - 1) {
            View btnMoveRight = createActionButton("▶  Перемістити праворуч", () -> {
                if (listener != null) listener.onMove(app, 1);
            });
            actionButtons.add(btnMoveRight);
            actionList.addView(btnMoveRight);
        }

        String pinLabel = app.isPinned() ? "☆  Прибрати з улюбленого" : "★  Додати до улюбленого";
        View btnPin = createActionButton(pinLabel, () -> {
            if (listener != null) listener.onTogglePin(app);
        });
        actionButtons.add(btnPin);
        actionList.addView(btnPin);

        if (app.isPinned()) {
            View btnOrder = createActionButton("↕  Змінити порядок улюбленого", () -> {
                if (listener != null) listener.onOpenOrder();
            });
            actionButtons.add(btnOrder);
            actionList.addView(btnOrder);
        }

        View btnInfo = createActionButton("⚙  Налаштування застосунку", () -> {
            if (listener != null) listener.onAppInfo(app);
        });
        actionButtons.add(btnInfo);
        actionList.addView(btnInfo);

        View btnCancel = createActionButton("✕  Закрити меню", () -> {
            if (listener != null) listener.onDismiss();
        });
        actionButtons.add(btnCancel);
        actionList.addView(btnCancel);

        for (int i = 0; i < actionButtons.size(); i++) {
            actionButtons.get(i).setId(View.generateViewId());
        }
        for (int i = 0; i < actionButtons.size(); i++) {
            View btn = actionButtons.get(i);
            if (i > 0) {
                btn.setNextFocusUpId(actionButtons.get(i - 1).getId());
            } else {
                btn.setNextFocusUpId(btn.getId());
            }
            if (i < actionButtons.size() - 1) {
                btn.setNextFocusDownId(actionButtons.get(i + 1).getId());
            } else {
                btn.setNextFocusDownId(btn.getId());
            }
            btn.setNextFocusLeftId(btn.getId());
            btn.setNextFocusRightId(btn.getId());
        }

        setFocusableInTouchMode(true);
        setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)) {
                if (listener != null) listener.onDismiss();
                return true;
            }
            return false;
        });

        waitingForInitialKeyUp = true;

        post(() -> {
            if (firstFocusableAction != null) {
                firstFocusableAction.requestFocus();
            }
        });
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (waitingForInitialKeyUp) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    waitingForInitialKeyUp = false;
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private View createActionButton(String text, Runnable onClick) {
        TextView btn = new TextView(getContext());
        btn.setText(text);
        btn.setTextColor(GhibliTheme.COLOR_PAPER);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        btn.setGravity(Gravity.CENTER_VERTICAL);
        btn.setPadding(
                GhibliTheme.dp(getContext(), 16),
                GhibliTheme.dp(getContext(), 10),
                GhibliTheme.dp(getContext(), 16),
                GhibliTheme.dp(getContext(), 10)
        );

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, GhibliTheme.dp(getContext(), 4), 0, GhibliTheme.dp(getContext(), 4));
        btn.setLayoutParams(lp);

        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        btn.setClickable(true);

        GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(GhibliTheme.COLOR_CARD_BG);
        normalBg.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        normalBg.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#3394c684"));
        btn.setBackground(normalBg);

        btn.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) btn.setPressed(false);
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? GhibliTheme.COLOR_CARD_ACTIVE : GhibliTheme.COLOR_CARD_BG);
            d.setCornerRadius(GhibliTheme.dp(getContext(), 10));
            d.setStroke(
                    GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#3394c684")
            );
            btn.setBackground(d);
            btn.setTextColor(hasFocus ? GhibliTheme.COLOR_ACCENT : GhibliTheme.COLOR_PAPER);
            btn.setScaleX(hasFocus ? 1.02f : 1.0f);
            btn.setScaleY(hasFocus ? 1.02f : 1.0f);
        });

        btn.setOnClickListener(v -> {
            if (!waitingForInitialKeyUp) {
                onClick.run();
            }
        });

        btn.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                if (waitingForInitialKeyUp) {
                    if (event.getAction() == KeyEvent.ACTION_UP) {
                        waitingForInitialKeyUp = false;
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) btn.setPressed(true);
                    return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    boolean wasPressed = btn.isPressed();
                    btn.setPressed(false);
                    if (wasPressed && !event.isCanceled()) onClick.run();
                    return true;
                }
            }
            return false;
        });

        return btn;
    }

    private void bindAppIcon(ImageView iconView, AppItem app) {
        String kind = app.getKind();
        if (kind != null && !kind.isEmpty()) {
            try (InputStream is = getContext().getAssets().open(app.getAssetPath())) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    iconView.setImageBitmap(bmp);
                    return;
                }
            } catch (Exception ignored) {}
        }
        if (app.getSystemIcon() != null) {
            iconView.setImageDrawable(app.getSystemIcon());
        }
    }
}

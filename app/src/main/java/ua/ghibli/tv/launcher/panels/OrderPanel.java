package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ua.ghibli.tv.launcher.R;
import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.DockView;
import ua.ghibli.tv.launcher.views.GlyphIconView;

import java.io.InputStream;
import java.util.List;

/**
 * Studio Ghibli favorites ordering side sheet.
 */
public class OrderPanel extends FrameLayout {
    public interface OnOrderActionListener {
        void onBackToLibraryRequested();
        void onCloseRequested();
    }

    private final AppManager appManager;
    private final DockView dockView;
    private final SideSheetContainer sheetContainer;
    private final ScrollView scrollView;
    private final LinearLayout content;
    private final LinearLayout listContainer;
    private final TextView countView;

    private OnOrderActionListener listener;
    private String focusTargetPackage = null;
    private boolean focusTargetIsDown = false;

    public OrderPanel(Context context, AppManager appManager, DockView dockView) {
        super(context);
        this.appManager = appManager;
        this.dockView = dockView;

        sheetContainer = new SideSheetContainer(context, false, "Долина Ghibli", "Порядок улюбленого");
        sheetContainer.setOnCloseListener(() -> {
            if (listener != null) listener.onCloseRequested();
        });

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView note = new TextView(context);
        note.setText("Стрілки ↑ та ↓ змінюють позицію застосунку, а ✕ прибирає з улюбленого. Перші шість позицій видно на головному екрані. Зміни зберігаються одразу.");
        note.setTextColor(GhibliTheme.COLOR_MUTED);
        note.setTextSize(11);
        note.setTypeface(GhibliTheme.getNunito(context));
        note.setPadding(0, 0, 0, GhibliTheme.dp(context, 8));
        content.addView(note);

        countView = new TextView(context);
        countView.setTextColor(GhibliTheme.COLOR_GOLD);
        countView.setTextSize(12);
        countView.setTypeface(GhibliTheme.getNunito(context));
        countView.setPadding(0, 0, 0, GhibliTheme.dp(context, 14));
        content.addView(countView);

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(listContainer);

        TextView backLink = new TextView(context);
        backLink.setText("До каталогу застосунків →");
        backLink.setTextColor(GhibliTheme.COLOR_GOLD);
        backLink.setTextSize(13);
        backLink.setTypeface(GhibliTheme.getNunitoBold(context));
        backLink.setFocusable(true);
        backLink.setFocusableInTouchMode(true);
        backLink.setClickable(true);
        backLink.setPadding(0, GhibliTheme.dp(context, 18), 0, GhibliTheme.dp(context, 24));
        backLink.setOnClickListener(v -> {
            if (listener != null) listener.onBackToLibraryRequested();
        });
        backLink.setOnFocusChangeListener((v, hasFocus) ->
                backLink.setTextColor(hasFocus ? GhibliTheme.COLOR_ACCENT : GhibliTheme.COLOR_GOLD));
        content.addView(backLink);

        scrollView.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        sheetContainer.setBodyView(scrollView);
        addView(sheetContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setOnOrderActionListener(OnOrderActionListener listener) {
        this.listener = listener;
    }

    public void renderList() {
        renderOrderList();
    }

    public void renderOrderList() {
        listContainer.removeAllViews();
        List<AppItem> favorites = appManager.getPinnedApps();
        countView.setText("Улюблених застосунків: " + favorites.size() + ".");

        int count = favorites.size();
        View viewToFocus = null;

        for (int i = 0; i < count; i++) {
            AppItem app = favorites.get(i);
            boolean isFirst = (i == 0);
            boolean isLast = (i == count - 1);
            boolean isTopSix = (i < 6);

            LinearLayout row = createOrderRow(app, i + 1, isFirst, isLast, isTopSix);
            listContainer.addView(row);

            if (app.getPackageName().equals(focusTargetPackage)) {
                View preferred = focusTargetIsDown ? row.findViewById(R.id.order_down) : row.findViewById(R.id.order_up);
                if (preferred != null && preferred.isFocusable()) {
                    viewToFocus = preferred;
                } else {
                    View alternate = focusTargetIsDown ? row.findViewById(R.id.order_up) : row.findViewById(R.id.order_down);
                    if (alternate != null && alternate.isFocusable()) {
                        viewToFocus = alternate;
                    } else {
                        viewToFocus = row.findViewById(R.id.order_remove);
                    }
                }
            }
        }

        if (viewToFocus != null) {
            final View fView = viewToFocus;
            post(fView::requestFocus);
        } else if (favorites.isEmpty()) {
            post(() -> sheetContainer.getCloseButton().requestFocus());
        }
        focusTargetPackage = null;
    }

    private LinearLayout createOrderRow(AppItem app, int position, boolean isFirst, boolean isLast, boolean isTopSix) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(GhibliTheme.dp(getContext(), 14), GhibliTheme.dp(getContext(), 10),
                GhibliTheme.dp(getContext(), 14), GhibliTheme.dp(getContext(), 10));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(GhibliTheme.COLOR_CARD_BG);
        gd.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        gd.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#33e8b858"));
        row.setBackground(gd);

        TextView numView = new TextView(getContext());
        numView.setText(String.valueOf(position));
        numView.setTextColor(GhibliTheme.COLOR_MUTED);
        numView.setTextSize(13);
        numView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        numView.setPadding(0, 0, GhibliTheme.dp(getContext(), 14), 0);
        row.addView(numView);

        ImageView iconView = new ImageView(getContext());
        int iconSize = GhibliTheme.dp(getContext(), 36);
        row.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));
        bindRowIcon(iconView, app);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(GhibliTheme.dp(getContext(), 12), 0, GhibliTheme.dp(getContext(), 12), 0);

        TextView titleView = new TextView(getContext());
        titleView.setText(app.getName());
        titleView.setTextColor(GhibliTheme.COLOR_PAPER);
        titleView.setTextSize(13);
        titleView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        textCol.addView(titleView);

        TextView subView = new TextView(getContext());
        subView.setText(isTopSix ? "На головному екрані" : "У каталозі");
        subView.setTextColor(isTopSix ? GhibliTheme.COLOR_SAGE : GhibliTheme.COLOR_MUTED);
        subView.setTextSize(10);
        subView.setTypeface(GhibliTheme.getNunito(getContext()));
        textCol.addView(subView);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        FrameLayout upBtn = createArrowButton(GlyphIconView.Glyph.ARROW_UP, isFirst, () -> {
            focusTargetPackage = app.getPackageName();
            focusTargetIsDown = false;
            appManager.moveFavorite(app.getPackageName(), -1);
            if (dockView != null) dockView.render();
            renderOrderList();
        });
        upBtn.setId(R.id.order_up);
        row.addView(upBtn);

        FrameLayout downBtn = createArrowButton(GlyphIconView.Glyph.ARROW_DOWN, isLast, () -> {
            focusTargetPackage = app.getPackageName();
            focusTargetIsDown = true;
            appManager.moveFavorite(app.getPackageName(), 1);
            if (dockView != null) dockView.render();
            renderOrderList();
        });
        downBtn.setId(R.id.order_down);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                GhibliTheme.dp(getContext(), 36), GhibliTheme.dp(getContext(), 36)
        );
        dLp.setMargins(GhibliTheme.dp(getContext(), 8), 0, 0, 0);
        downBtn.setLayoutParams(dLp);
        row.addView(downBtn);

        FrameLayout removeBtn = createArrowButton(GlyphIconView.Glyph.CLOSE, false, () -> {
            List<AppItem> favorites = appManager.getPinnedApps();
            String nextFocusPkg = null;
            if (favorites != null && favorites.size() > 1) {
                int appIdx = -1;
                for (int i = 0; i < favorites.size(); i++) {
                    if (favorites.get(i).getPackageName().equals(app.getPackageName())) {
                        appIdx = i;
                        break;
                    }
                }
                if (appIdx >= 0) {
                    if (appIdx < favorites.size() - 1) {
                        nextFocusPkg = favorites.get(appIdx + 1).getPackageName();
                    } else if (appIdx > 0) {
                        nextFocusPkg = favorites.get(appIdx - 1).getPackageName();
                    }
                }
            }
            focusTargetPackage = nextFocusPkg;
            focusTargetIsDown = false;
            appManager.togglePin(app.getPackageName());
            if (dockView != null) dockView.render();
            renderOrderList();
        });
        LinearLayout.LayoutParams rmLp = new LinearLayout.LayoutParams(
                GhibliTheme.dp(getContext(), 36), GhibliTheme.dp(getContext(), 36)
        );
        rmLp.setMargins(GhibliTheme.dp(getContext(), 8), 0, 0, 0);
        removeBtn.setLayoutParams(rmLp);
        removeBtn.setId(R.id.order_remove);
        row.addView(removeBtn);

        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rLp.setMargins(0, GhibliTheme.dp(getContext(), 4), 0, GhibliTheme.dp(getContext(), 4));
        row.setLayoutParams(rLp);

        return row;
    }

    private void bindRowIcon(ImageView iconView, AppItem app) {
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

    private FrameLayout createArrowButton(GlyphIconView.Glyph glyph, boolean disabled, Runnable action) {
        FrameLayout btn = new FrameLayout(getContext());
        int size = GhibliTheme.dp(getContext(), 36);
        btn.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        btn.setFocusable(!disabled);
        btn.setFocusableInTouchMode(!disabled);
        btn.setClickable(!disabled);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GhibliTheme.COLOR_BUTTON_BG);
        bg.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        bg.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#44e8b858"));
        btn.setBackground(bg);

        GlyphIconView icon = new GlyphIconView(getContext());
        icon.setGlyph(glyph);
        icon.setIconColor(disabled ? Color.parseColor("#44a4b8a6") : GhibliTheme.COLOR_PAPER);
        int iSize = GhibliTheme.dp(getContext(), 16);
        FrameLayout.LayoutParams iLp = new FrameLayout.LayoutParams(iSize, iSize, Gravity.CENTER);
        btn.addView(icon, iLp);

        if (!disabled) {
            btn.setOnClickListener(v -> action.run());
            btn.setOnFocusChangeListener((v, hasFocus) -> {
                GradientDrawable d = new GradientDrawable();
                d.setColor(hasFocus ? GhibliTheme.COLOR_BUTTON_FOCUS : GhibliTheme.COLOR_BUTTON_BG);
                d.setCornerRadius(GhibliTheme.dp(getContext(), 10));
                d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                        hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#44e8b858"));
                v.setBackground(d);
            });
        } else {
            btn.setAlpha(0.35f);
        }

        return btn;
    }
}

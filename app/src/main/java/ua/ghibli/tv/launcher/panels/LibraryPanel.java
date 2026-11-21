package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.DockView;
import ua.ghibli.tv.launcher.views.SpiritFocusView;
import ua.ghibli.tv.launcher.views.SpiritRenderer;
import ua.ghibli.tv.launcher.views.TVKeyHelper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Studio Ghibli Full App Catalog ("Усі застосунки") with search, filter, and Spirit focus overlay.
 */
public class LibraryPanel extends FrameLayout {
    public interface OnLibraryActionListener {
        void onAppLaunch(AppItem app);
        void onAppContextMenuRequested(View view, AppItem app);
        void onOrderPanelRequested();
        void onCloseRequested();
        void onAppFocused(View view, AppItem app);
    }

    private final AppManager appManager;
    private final DockView dockView;
    private final SideSheetContainer sheetContainer;
    private final ScrollView scrollView;
    private final LinearLayout content;
    private final EditText searchInput;
    private final TextView countView;
    private final Button filterButton;
    private final Button orderButton;
    private final GridLayout grid;
    private final SpiritFocusView focusView;
    private boolean observingFocus;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusListener =
            (oldFocus, newFocus) -> updateAuraTarget(newFocus);
    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::invalidateAura;

    private int computedColumnCount = 4;
    private int computedCardWidth = 0;
    private boolean filterPinnedOnly = false;
    private OnLibraryActionListener listener;

    private final Map<String, Bitmap> iconCache = new HashMap<>();

    public LibraryPanel(Context context, AppManager appManager, DockView dockView, SpiritRenderer spiritRenderer) {
        super(context);
        this.appManager = appManager;
        this.dockView = dockView;

        sheetContainer = new SideSheetContainer(context, true, "Долина Ghibli", "Усі застосунки");
        sheetContainer.setOnCloseListener(() -> {
            if (listener != null) listener.onCloseRequested();
        });

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        searchInput = new EditText(context);
        searchInput.setHint("Знайти застосунок…");
        searchInput.setHintTextColor(GhibliTheme.COLOR_MUTED);
        searchInput.setTextColor(GhibliTheme.COLOR_PAPER);
        searchInput.setTextSize(14);
        searchInput.setTypeface(GhibliTheme.getNunito(context));
        searchInput.setSingleLine(true);
        searchInput.setPadding(GhibliTheme.dp(context, 16), GhibliTheme.dp(context, 12),
                GhibliTheme.dp(context, 16), GhibliTheme.dp(context, 12));

        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(GhibliTheme.COLOR_CARD_BG);
        searchBg.setCornerRadius(GhibliTheme.dp(context, 12));
        searchBg.setStroke(GhibliTheme.dp(context, 1), Color.parseColor("#44e8b858"));
        searchInput.setBackground(searchBg);

        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sLp.setMargins(0, 0, 0, GhibliTheme.dp(context, 16));
        searchInput.setLayoutParams(sLp);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderApps();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        content.addView(searchInput);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(0, 0, 0, GhibliTheme.dp(context, 14));

        countView = new TextView(context);
        countView.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        countView.setTextSize(12);
        countView.setTypeface(GhibliTheme.getNunito(context));
        toolbar.addView(countView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        filterButton = createToolbarButton("Лише улюблене", v -> {
            filterPinnedOnly = !filterPinnedOnly;
            updateFilterButtonState();
            renderApps();
        });
        toolbar.addView(filterButton);

        orderButton = createToolbarButton("Порядок улюбленого", v -> {
            if (listener != null) listener.onOrderPanelRequested();
        });
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, GhibliTheme.dp(context, 34)
        );
        oLp.setMargins(GhibliTheme.dp(context, 10), 0, 0, 0);
        orderButton.setLayoutParams(oLp);
        toolbar.addView(orderButton);

        content.addView(toolbar);

        TextView remoteHint = new TextView(context);
        remoteHint.setText("OK — відкрити  •  Затиснути OK — меню дій та улюбленого");
        remoteHint.setTextColor(GhibliTheme.COLOR_MUTED);
        remoteHint.setTextSize(11);
        remoteHint.setTypeface(GhibliTheme.getNunito(context));
        remoteHint.setPadding(0, 0, 0, GhibliTheme.dp(context, 10));
        content.addView(remoteHint);

        grid = new GridLayout(context);
        grid.setColumnCount(computedColumnCount);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        scrollView.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout body = new FrameLayout(context);
        focusView = new SpiritFocusView(context, spiritRenderer);
        body.addView(focusView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        scrollView.setElevation(10f);
        body.addView(scrollView, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        body.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> invalidateAura());
        sheetContainer.setBodyView(body);
        addView(sheetContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setOnLibraryActionListener(OnLibraryActionListener listener) {
        this.listener = listener;
    }

    public void open() {
        renderGrid();
    }

    public void close() {
        release();
    }

    public void pause() { focusView.pause(); }

    public void resume() {
        focusView.resume();
        renderGrid();
    }

    public void setTechnique(String technique) { focusView.setTechnique(technique); }

    public void setMotionEnabled(boolean enabled) { focusView.setMotionEnabled(enabled); }

    private void updateAuraTarget(View focused) {
        View card = focused;
        while (card != null && card.getParent() != grid) {
            card = card.getParent() instanceof View ? (View) card.getParent() : null;
        }
        focusView.setTargetView(card);
    }

    private void invalidateAura() { focusView.invalidate(); }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!observingFocus) {
            getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
            getViewTreeObserver().addOnScrollChangedListener(scrollListener);
            observingFocus = true;
        }
    }

    private void stopObservingFocus() {
        if (observingFocus && getViewTreeObserver().isAlive()) {
            getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
            getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        }
        observingFocus = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        stopObservingFocus();
        super.onDetachedFromWindow();
    }

    public void renderGrid() {
        renderApps();
    }

    public View findCardByPackage(String packageName) {
        if (packageName == null) return null;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View item = grid.getChildAt(i);
            if (item != null && packageName.equals(item.getTag())) {
                return item;
            }
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w > 0) {
            int sheetW = Math.min(GhibliTheme.dp(getContext(), 1050), (int) (w * 0.95f));
            int pad = GhibliTheme.dp(getContext(), 72);
            int availableWidth = Math.max(100, sheetW - pad);
            int margin = GhibliTheme.dp(getContext(), 6);
            computedColumnCount = 4;
            computedCardWidth = (availableWidth - margin * 2 * computedColumnCount) / computedColumnCount;
            if (computedColumnCount != grid.getColumnCount() || computedCardWidth > 0) {
                PickerGrid.reflowColumns(grid, computedColumnCount);
                applyCardWidths();
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void applyCardWidths() {
        if (computedCardWidth <= 0) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            GridLayout.LayoutParams lp = (GridLayout.LayoutParams) child.getLayoutParams();
            if (lp != null && lp.width != computedCardWidth) {
                lp.width = computedCardWidth;
                child.setLayoutParams(lp);
            }
        }
    }

    private Button createToolbarButton(String text, View.OnClickListener clickListener) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(GhibliTheme.COLOR_PAPER);
        btn.setTextSize(11);
        btn.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        btn.setAllCaps(false);
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);
        btn.setPadding(GhibliTheme.dp(getContext(), 14), 0, GhibliTheme.dp(getContext(), 14), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(GhibliTheme.COLOR_BUTTON_BG);
        bg.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        bg.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#55e8b858"));
        btn.setBackground(bg);

        btn.setOnClickListener(clickListener);
        btn.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? GhibliTheme.COLOR_BUTTON_FOCUS : GhibliTheme.COLOR_BUTTON_BG);
            d.setCornerRadius(GhibliTheme.dp(getContext(), 10));
            d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#55e8b858"));
            v.setBackground(d);
        });

        return btn;
    }

    private void updateFilterButtonState() {
        GradientDrawable bg = new GradientDrawable();
        if (filterPinnedOnly) {
            bg.setColor(GhibliTheme.COLOR_ACCENT);
            filterButton.setTextColor(GhibliTheme.COLOR_INK);
        } else {
            bg.setColor(GhibliTheme.COLOR_BUTTON_BG);
            filterButton.setTextColor(GhibliTheme.COLOR_PAPER);
        }
        bg.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        bg.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#55e8b858"));
        filterButton.setBackground(bg);
    }

    public void renderApps() {
        View focusedCard = grid.getFocusedChild();
        String focusedPackage = focusedCard != null ? (String) focusedCard.getTag() : null;
        focusView.setTargetView(null);
        grid.removeAllViews();
        String query = searchInput.getText().toString().trim().toLowerCase(new Locale("uk", "UA"));

        List<AppItem> allApps = appManager.getAllApps();
        List<AppItem> filtered = new ArrayList<>();

        for (AppItem app : allApps) {
            if (filterPinnedOnly && !app.isPinned()) continue;
            if (!query.isEmpty() && !app.getName().toLowerCase(new Locale("uk", "UA")).contains(query)) {
                continue;
            }
            filtered.add(app);
        }

        countView.setText(filtered.size() + " застосунків");

        int availableWidth = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int sheetWidth = Math.min(GhibliTheme.dp(getContext(), 1050), (int) (availableWidth * 0.95f));
        int netWidth = Math.max(100, sheetWidth - GhibliTheme.dp(getContext(), 72));
        int margin = GhibliTheme.dp(getContext(), 6);
        computedColumnCount = 4;
        computedCardWidth = (netWidth - margin * 2 * computedColumnCount) / computedColumnCount;
        grid.setColumnCount(computedColumnCount);

        if (filtered.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setText("Не знайдено жодного застосунку.");
            empty.setTextColor(GhibliTheme.COLOR_MUTED);
            empty.setTextSize(13);
            empty.setTypeface(GhibliTheme.getNunito(getContext()));
            empty.setPadding(0, GhibliTheme.dp(getContext(), 40), 0, 0);
            empty.setGravity(Gravity.CENTER);
            grid.addView(empty);
            if (focusedPackage != null) sheetContainer.getCloseButton().requestFocus();
            return;
        }

        for (AppItem app : filtered) {
            View card = createLibraryCard(app);
            grid.addView(card);
        }
        if (focusedPackage != null) {
            View replacement = findCardByPackage(focusedPackage);
            if (replacement != null) {
                replacement.requestFocus();
                updateAuraTarget(replacement);
            } else {
                sheetContainer.getCloseButton().requestFocus();
            }
        }
    }

    private View createLibraryCard(AppItem app) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setTag(app.getPackageName());
        card.setPadding(GhibliTheme.dp(getContext(), 10), GhibliTheme.dp(getContext(), 10),
                GhibliTheme.dp(getContext(), 10), GhibliTheme.dp(getContext(), 8));

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = computedCardWidth > 0 ? computedCardWidth : GhibliTheme.dp(getContext(), 180);
        glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        int m = GhibliTheme.dp(getContext(), 6);
        glp.setMargins(m, m, m, m);
        card.setLayoutParams(glp);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.TRANSPARENT);
        bg.setCornerRadius(GhibliTheme.dp(getContext(), 12));
        card.setBackground(bg);

        ImageView iconView = new ImageView(getContext());
        int iconSize = GhibliTheme.dp(getContext(), 80);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bindAppIcon(iconView, app);
        card.addView(iconView);

        TextView nameView = new TextView(getContext());
        nameView.setText(app.getName());
        nameView.setTextColor(GhibliTheme.COLOR_PAPER);
        nameView.setTextSize(13);
        nameView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, GhibliTheme.dp(getContext(), 6), 0, 0);
        card.addView(nameView);

        TextView descView = new TextView(getContext());
        descView.setText(app.getDescription());
        descView.setTextColor(GhibliTheme.COLOR_MUTED);
        descView.setTextSize(10);
        descView.setTypeface(GhibliTheme.getNunito(getContext()));
        descView.setSingleLine(true);
        descView.setEllipsize(TextUtils.TruncateAt.END);
        descView.setGravity(Gravity.CENTER);
        card.addView(descView);

        TextView pinBadge = new TextView(getContext());
        pinBadge.setText(app.isPinned() ? "★ В улюбленому" : "");
        pinBadge.setTextColor(GhibliTheme.COLOR_ACCENT);
        pinBadge.setTextSize(10);
        pinBadge.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        pinBadge.setPadding(0, GhibliTheme.dp(getContext(), 4), 0, 0);
        pinBadge.setFocusable(false);
        pinBadge.setClickable(false);
        card.addView(pinBadge);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.05f : 1.0f);
            v.setScaleY(hasFocus ? 1.05f : 1.0f);
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? Color.parseColor("#33284234") : Color.TRANSPARENT);
            d.setCornerRadius(GhibliTheme.dp(getContext(), 12));
            d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 0),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.TRANSPARENT);
            v.setBackground(d);
            if (hasFocus && listener != null) {
                listener.onAppFocused(v, app);
            }
        });

        TVKeyHelper.attach(card, new TVKeyHelper.OnTVCardClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onAppLaunch(app);
            }

            @Override
            public void onLongClick(View v) {
                if (listener != null) listener.onAppContextMenuRequested(v, app);
            }
        });

        return card;
    }

    private void bindAppIcon(ImageView iconView, AppItem app) {
        String kind = app.getKind();
        if (kind != null && !kind.isEmpty()) {
            String assetPath = app.getAssetPath();
            Bitmap cached = iconCache.get(assetPath);
            if (cached != null) {
                iconView.setImageBitmap(cached);
                return;
            }
            try (InputStream is = getContext().getAssets().open(assetPath)) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    iconCache.put(assetPath, bmp);
                    iconView.setImageBitmap(bmp);
                    return;
                }
            } catch (Exception ignored) {}
        }
        if (app.getSystemIcon() != null) {
            iconView.setImageDrawable(app.getSystemIcon());
        }
    }

    public void release() {
        stopObservingFocus();
        focusView.pause();
        focusView.setTargetView(null);
        grid.removeAllViews();
        for (Bitmap bmp : iconCache.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        iconCache.clear();
    }
}

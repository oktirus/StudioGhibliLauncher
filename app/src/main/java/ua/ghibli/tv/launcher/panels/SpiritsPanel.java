package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.Technique;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.SpiritRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Studio Ghibli Spirit Focus Effect Picker ("Духи та магія").
 * Displays 6 static preview cards rendered on demand without background animation load.
 */
public class SpiritsPanel extends FrameLayout {
    public interface OnSpiritSelectedListener {
        void onSpiritSelected(Technique technique);
        void onCloseRequested();
    }

    private final PreferencesManager prefs;
    private final SpiritRenderer renderer;
    private final SideSheetContainer sheetContainer;
    private final ScrollView scrollView;
    private final GridLayout grid;
    private final Map<String, Bitmap> previewBitmaps = new HashMap<>();
    private final Map<String, ImageView> previewViews = new HashMap<>();

    private int computedColumnCount = 3;
    private int computedCardWidth = 0;
    private boolean isListenerRegistered = false;
    private OnSpiritSelectedListener listener;

    private final SpiritRenderer.OnBitmapLoadedListener bitmapListener = name -> {
        String spiritId = "soot-idle".equals(name) || "soot-jump".equals(name) ? "soot_sprites" : name;
        ImageView iv = previewViews.get(spiritId);
        if (iv != null) {
            Bitmap old = previewBitmaps.remove(spiritId);
            if (old != null && !old.isRecycled()) old.recycle();
            Bitmap updated = getOrCreateStaticPreview(spiritId, iv.getWidth(), iv.getHeight());
            if (updated != null) {
                iv.setImageBitmap(updated);
            }
        }
    };

    public SpiritsPanel(Context context, PreferencesManager prefs, SpiritRenderer renderer) {
        super(context);
        this.prefs = prefs;
        this.renderer = renderer;
        registerListener();

        sheetContainer = new SideSheetContainer(context, true, "Долина Ghibli", "Духи та магія");
        sheetContainer.setOnCloseListener(() -> {
            if (listener != null) listener.onCloseRequested();
        });

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        TextView note = new TextView(context);
        note.setText("Обери духа навігації — він супроводжуватиме кожну обрану іконку. Усі шість прев’ю нижче статичні й не навантажують телевізор.");
        note.setTextColor(GhibliTheme.COLOR_MUTED);
        note.setTextSize(12);
        note.setTypeface(GhibliTheme.getNunito(context));
        note.setPadding(0, 0, 0, GhibliTheme.dp(context, 16));
        content.addView(note);

        grid = new GridLayout(context);
        grid.setColumnCount(computedColumnCount);
        grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
        content.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView footerNote = new TextView(context);
        footerNote.setText("↑↓←→ — порівняти; OK — застосувати. Вибір зберігається автоматично. За вимкнення живого руху ефекти стають спокійними.");
        footerNote.setTextColor(GhibliTheme.COLOR_MUTED);
        footerNote.setTextSize(11);
        footerNote.setTypeface(GhibliTheme.getNunito(context));
        footerNote.setPadding(0, GhibliTheme.dp(context, 20), 0, GhibliTheme.dp(context, 24));
        content.addView(footerNote);

        scrollView.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        sheetContainer.setBodyView(scrollView);
        addView(sheetContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private synchronized void registerListener() {
        if (!isListenerRegistered && renderer != null) {
            renderer.addOnBitmapLoadedListener(bitmapListener);
            isListenerRegistered = true;
        }
    }

    private synchronized void unregisterListener() {
        if (isListenerRegistered && renderer != null) {
            renderer.removeOnBitmapLoadedListener(bitmapListener);
            isListenerRegistered = false;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        registerListener();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        unregisterListener();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        if (w > 0) {
            int sheetW = Math.min(GhibliTheme.dp(getContext(), 1050), (int) (w * 0.95f));
            int pad = GhibliTheme.dp(getContext(), 72);
            int availableWidth = Math.max(100, sheetW - pad);
            int margin = GhibliTheme.dp(getContext(), 6);
            AdaptiveGridHelper.LayoutResult res = AdaptiveGridHelper.compute(
                    availableWidth, GhibliTheme.dp(getContext(), 180), GhibliTheme.dp(getContext(), 320), margin, 1, 3
            );
            if (res.columnCount != computedColumnCount || res.cardWidthPx != computedCardWidth) {
                computedColumnCount = res.columnCount;
                computedCardWidth = res.cardWidthPx;
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

    public void setOnSpiritSelectedListener(OnSpiritSelectedListener listener) {
        this.listener = listener;
    }

    public void renderSpirits() {
        previewViews.clear();
        grid.removeAllViews();
        String currentSpiritId = prefs.getTechnique();

        int availableWidth = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int sheetWidth = Math.min(GhibliTheme.dp(getContext(), 1050), (int) (availableWidth * 0.95f));
        int netWidth = Math.max(100, sheetWidth - GhibliTheme.dp(getContext(), 72));
        int margin = GhibliTheme.dp(getContext(), 6);
        AdaptiveGridHelper.LayoutResult res = AdaptiveGridHelper.compute(
                netWidth, GhibliTheme.dp(getContext(), 180), GhibliTheme.dp(getContext(), 320), margin, 1, 3
        );
        computedColumnCount = res.columnCount;
        computedCardWidth = res.cardWidthPx;
        grid.setColumnCount(computedColumnCount);

        View selectedCard = null;
        View[] cards = new View[CatalogData.TECHNIQUES.size()];
        for (int i = 0; i < CatalogData.TECHNIQUES.size(); i++) {
            Technique spirit = CatalogData.TECHNIQUES.get(i);
            boolean isSel = spirit.getId().equals(currentSpiritId);
            View card = createSpiritCard(spirit, isSel);
            card.setId(View.generateViewId());
            cards[i] = card;
            grid.addView(card);
            if (isSel) selectedCard = card;
        }

        // Dynamic D-pad graph for any grid column count
        for (int i = 0; i < cards.length; i++) {
            int col = i % computedColumnCount;
            if (col < computedColumnCount - 1 && i + 1 < cards.length) {
                cards[i].setNextFocusRightId(cards[i + 1].getId());
            }
            if (col > 0 && i - 1 >= 0) {
                cards[i].setNextFocusLeftId(cards[i - 1].getId());
            }
            if (i + computedColumnCount < cards.length) {
                cards[i].setNextFocusDownId(cards[i + computedColumnCount].getId());
            }
            if (i - computedColumnCount >= 0) {
                cards[i].setNextFocusUpId(cards[i - computedColumnCount].getId());
            }
        }

        if (selectedCard != null) {
            final View fCard = selectedCard;
            fCard.requestFocus();
            post(() -> {
                fCard.requestFocusFromTouch();
                fCard.requestFocus();
            });
        }
    }

    private View createSpiritCard(Technique spirit, boolean isSelected) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setClickable(true);
        card.setPadding(GhibliTheme.dp(getContext(), 14), GhibliTheme.dp(getContext(), 6),
                GhibliTheme.dp(getContext(), 14), GhibliTheme.dp(getContext(), 14));

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = computedCardWidth > 0 ? computedCardWidth : GhibliTheme.dp(getContext(), 260);
        glp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        int m = GhibliTheme.dp(getContext(), 6);
        glp.setMargins(m, m, m, m);
        card.setLayoutParams(glp);

        int cw = glp.width;
        ImageView previewImage = new ImageView(getContext());
        previewViews.put(spirit.getId(), previewImage);
        int prevHeight = GhibliTheme.dp(getContext(), 135);
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, prevHeight
        );
        previewImage.setLayoutParams(prevLp);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewImage.setBackgroundColor(Color.TRANSPARENT);

        Bitmap staticBmp = getOrCreateStaticPreview(spirit.getId(), cw, prevHeight);
        if (staticBmp != null) {
            previewImage.setImageBitmap(staticBmp);
        }
        card.addView(previewImage);

        int spiritColor = spirit.getColor();

        TextView name = new TextView(getContext());
        name.setText(spirit.getName());
        name.setTextColor(spiritColor);
        name.setTextSize(17.5f);
        name.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, GhibliTheme.dp(getContext(), 4), 0, 0);
        card.addView(name);

        TextView owner = new TextView(getContext());
        owner.setText(spirit.getOwner());
        owner.setTextColor(GhibliTheme.COLOR_GOLD);
        owner.setTextSize(11);
        owner.setTypeface(GhibliTheme.getNunito(getContext()));
        owner.setGravity(Gravity.CENTER);
        owner.setPadding(0, GhibliTheme.dp(getContext(), 2), 0, 0);
        card.addView(owner);

        TextView desc = new TextView(getContext());
        desc.setText(spirit.getDescription());
        desc.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        desc.setTextSize(12);
        desc.setTypeface(GhibliTheme.getNunito(getContext()));
        desc.setGravity(Gravity.CENTER);
        desc.setMaxLines(2);
        desc.setPadding(0, GhibliTheme.dp(getContext(), 5), 0, GhibliTheme.dp(getContext(), 10));
        card.addView(desc);

        View divider = new View(getContext());
        divider.setBackgroundColor(Color.parseColor("#33e8b858"));
        card.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, GhibliTheme.dp(getContext(), 1)
        ));

        TextView state = new TextView(getContext());
        state.setText(isSelected ? "Обрано" : "Обрати духа");
        state.setTextColor(isSelected ? spiritColor : GhibliTheme.COLOR_GOLD);
        state.setTextSize(12);
        state.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        state.setGravity(Gravity.CENTER);
        state.setPadding(0, GhibliTheme.dp(getContext(), 9), 0, 0);
        card.addView(state);

        final GradientDrawable normalBg = new GradientDrawable();
        normalBg.setColor(isSelected ? Color.parseColor("#2a4435") : Color.parseColor("#88172920"));
        normalBg.setCornerRadius(GhibliTheme.dp(getContext(), 12));
        normalBg.setStroke(GhibliTheme.dp(getContext(), isSelected ? 2 : 1),
                isSelected ? spiritColor : Color.parseColor("#4494c684"));
        card.setBackground(normalBg);

        card.setOnClickListener(v -> {
            prefs.setTechnique(spirit.getId());
            if (listener != null) listener.onSpiritSelected(spirit);
        });

        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN &&
                    (keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == android.view.KeyEvent.KEYCODE_ENTER
                            || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                card.performClick();
                return true;
            }
            return false;
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            v.setScaleX(hasFocus ? 1.03f : 1.0f);
            v.setScaleY(hasFocus ? 1.03f : 1.0f);
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? Color.parseColor("#2d4a39") : (isSelected ? Color.parseColor("#2a4435") : Color.parseColor("#88172920")));
            d.setCornerRadius(GhibliTheme.dp(getContext(), 12));
            d.setStroke(GhibliTheme.dp(getContext(), 2),
                    hasFocus ? spiritColor : (isSelected ? spiritColor : Color.parseColor("#4494c684")));
            v.setBackground(d);
        });

        return card;
    }

    private Bitmap getOrCreateStaticPreview(String id, int w, int h) {
        if (previewBitmaps.containsKey(id)) {
            return previewBitmaps.get(id);
        }
        int pw = Math.max(320, w);
        int ph = Math.max(210, h);
        Bitmap bmp = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        if (renderer != null) {
            renderer.drawStaticPreview(canvas, id, pw, ph);
        }
        previewBitmaps.put(id, bmp);
        return bmp;
    }

    public void clearCache() {
        previewBitmaps.clear();
        previewViews.clear();
    }

    public void release() {
        unregisterListener();
        for (Bitmap bmp : previewBitmaps.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        previewBitmaps.clear();
        previewViews.clear();
    }
}

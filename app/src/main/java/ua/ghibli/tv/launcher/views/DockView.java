package ua.ghibli.tv.launcher.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.theme.GhibliTheme;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DockView extends HorizontalScrollView {
    public interface OnDockItemClickListener {
        void onAppClick(AppItem app);
        void onAppLongClick(View view, AppItem app, int position);
        void onLibraryClick();
    }

    public interface OnDockItemFocusListener {
        void onItemFocused(View view, AppItem app);
        void onLibraryFocused(View view);
        void onDockFocusCleared();
    }

    private final AppManager appManager;
    private final LinearLayout container;
    private final Map<String, Bitmap> iconCache = new HashMap<>();
    private final Map<String, java.util.List<ImageView>> iconRequests = new HashMap<>();
    private final java.util.concurrent.ExecutorService iconExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final android.os.Handler iconHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean disposed;

    private OnDockItemClickListener clickListener;
    private OnDockItemFocusListener focusListener;
    private int computedCardWidth = 0;

    public DockView(Context context, AppManager appManager) {
        super(context);
        this.appManager = appManager;

        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER);
        container.setPadding(0, 0, 0, 0);

        addView(container, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
    }

    public void setOnDockItemClickListener(OnDockItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnDockItemFocusListener(OnDockItemFocusListener listener) {
        this.focusListener = listener;
    }

    public int computeCardWidth(int availableWidthPx, int itemCount) {
        if (itemCount <= 0) return GhibliTheme.dp(getContext(), 130);
        int marginTotalPx = GhibliTheme.dp(getContext(), 12) * itemCount;
        int netWidth = Math.max(0, availableWidthPx - marginTotalPx);
        int perCard = netWidth / itemCount;

        int minWidth = GhibliTheme.dp(getContext(), 112);
        int maxWidth = GhibliTheme.dp(getContext(), 210);

        return Math.min(maxWidth, Math.max(minWidth, perCard));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0) {
            int count = container.getChildCount();
            int newW = computeCardWidth(w, count);
            if (newW != computedCardWidth) {
                computedCardWidth = newW;
                applyCardWidths(newW);
            }
        }
    }

    private void applyCardWidths(int cardWidth) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            ViewGroup.LayoutParams lp = child.getLayoutParams();
            if (lp != null && lp.width != cardWidth) {
                lp.width = cardWidth;
                child.setLayoutParams(lp);
            }
        }
    }

    public void render() {
        View focusedChild = container.getFocusedChild();
        Object tag = focusedChild != null ? focusedChild.getTag() : null;
        String focusedPackage = tag instanceof AppItem ? ((AppItem) tag).getPackageName() : null;
        boolean focusedArchive = "archive".equals(tag);

        container.removeAllViews();
        List<AppItem> dockApps = appManager.getDockApps();
        int totalItems = dockApps.size() + 1;

        int availableWidth = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        computedCardWidth = computeCardWidth(availableWidth, totalItems);

        View toFocus = null;
        for (AppItem app : dockApps) {
            View card = createAppCard(app, computedCardWidth);
            container.addView(card);
            if (focusedPackage != null && focusedPackage.equals(app.getPackageName())) {
                toFocus = card;
            }
        }

        View archiveCard = createArchiveCard(computedCardWidth);
        container.addView(archiveCard);
        if (focusedArchive) {
            toFocus = archiveCard;
        }

        if (toFocus != null) {
            toFocus.requestFocus();
        } else if (focusedChild != null && container.getChildCount() > 0) {
            container.getChildAt(0).requestFocus();
        }
    }

    public View findCardByPackage(String packageName) {
        if (packageName == null) return null;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof AppItem && packageName.equals(((AppItem) tag).getPackageName())) {
                return child;
            }
        }
        return null;
    }

    public View getArchiveCard() {
        int count = container.getChildCount();
        if (count > 0) {
            View last = container.getChildAt(count - 1);
            if ("archive".equals(last.getTag())) return last;
        }
        return null;
    }

    public View getFirstCard() {
        return container.getChildCount() > 0 ? container.getChildAt(0) : null;
    }

    private View createAppCard(AppItem app, int cardWidth) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(GhibliTheme.dp(getContext(), 8), GhibliTheme.dp(getContext(), 6),
                GhibliTheme.dp(getContext(), 8), GhibliTheme.dp(getContext(), 6));
        card.setTag(app);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(GhibliTheme.dp(getContext(), 6), 0, GhibliTheme.dp(getContext(), 6), 0);
        card.setLayoutParams(lp);

        ImageView iconView = new ImageView(getContext());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                GhibliTheme.dp(getContext(), 116), GhibliTheme.dp(getContext(), 116));
        iconView.setLayoutParams(iconLp);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        bindAppIcon(iconView, app);
        card.addView(iconView);

        TextView nameView = new TextView(getContext());
        nameView.setText(app.getName());
        nameView.setTextColor(GhibliTheme.COLOR_PAPER);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nameView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, GhibliTheme.dp(getContext(), 6), 0, 0);
        card.addView(nameView);

        TextView metaView = new TextView(getContext());
        String metaText = app.getDescription();
        metaView.setText(metaText != null && !metaText.isEmpty() ? metaText : "Застосунок");
        metaView.setTextColor(Color.parseColor("#b4c4b2"));
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        metaView.setTypeface(GhibliTheme.getNunito(getContext()));
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setGravity(Gravity.CENTER);
        metaView.setPadding(0, GhibliTheme.dp(getContext(), 2), 0, 0);
        card.addView(metaView);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            iconView.animate()
                    .scaleX(hasFocus ? 1.05f : 1.0f)
                    .scaleY(hasFocus ? 1.05f : 1.0f)
                    .translationY(hasFocus ? -GhibliTheme.dp(getContext(), 6) : 0f)
                    .setDuration(180)
                    .start();

            nameView.setBackground(null);
            nameView.setTextColor(hasFocus ? Color.WHITE : GhibliTheme.COLOR_PAPER);
            nameView.setPadding(0, GhibliTheme.dp(getContext(), 6), 0, 0);
            metaView.setBackground(null);
            metaView.setTextColor(hasFocus ? GhibliTheme.COLOR_GOLD : Color.parseColor("#b4c4b2"));

            if (focusListener != null) {
                if (hasFocus) focusListener.onItemFocused(v, app);
                else focusListener.onDockFocusCleared();
            }
        });

        TVKeyHelper.attach(card, new TVKeyHelper.OnTVCardClickListener() {
            @Override
            public void onClick(View v) {
                if (clickListener != null) clickListener.onAppClick(app);
            }

            @Override
            public void onLongClick(View v) {
                if (clickListener != null) {
                    List<AppItem> dock = appManager.getDockApps();
                    int pos = dock.indexOf(app);
                    clickListener.onAppLongClick(v, app, pos);
                }
            }
        });

        return card;
    }

    private View createArchiveCard(int cardWidth) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setFocusable(true);
        card.setFocusableInTouchMode(true);
        card.setPadding(GhibliTheme.dp(getContext(), 8), GhibliTheme.dp(getContext(), 6),
                GhibliTheme.dp(getContext(), 8), GhibliTheme.dp(getContext(), 6));
        card.setTag("archive");

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardWidth, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(GhibliTheme.dp(getContext(), 6), 0, GhibliTheme.dp(getContext(), 6), 0);
        card.setLayoutParams(lp);

        ImageView iconView = new ImageView(getContext());
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                GhibliTheme.dp(getContext(), 116), GhibliTheme.dp(getContext(), 116));
        iconView.setLayoutParams(iconLp);
        iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        loadArchiveIcon(iconView);
        card.addView(iconView);

        TextView nameView = new TextView(getContext());
        nameView.setText("Усі застосунки");
        nameView.setTextColor(GhibliTheme.COLOR_PAPER);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        nameView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        nameView.setSingleLine(true);
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, GhibliTheme.dp(getContext(), 6), 0, 0);
        card.addView(nameView);

        TextView metaView = new TextView(getContext());
        metaView.setText("Повний каталог");
        metaView.setTextColor(Color.parseColor("#b4c4b2"));
        metaView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        metaView.setTypeface(GhibliTheme.getNunito(getContext()));
        metaView.setSingleLine(true);
        metaView.setGravity(Gravity.CENTER);
        metaView.setPadding(0, GhibliTheme.dp(getContext(), 2), 0, 0);
        card.addView(metaView);

        card.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onLibraryClick();
        });

        card.setOnFocusChangeListener((v, hasFocus) -> {
            iconView.animate()
                    .scaleX(hasFocus ? 1.05f : 1.0f)
                    .scaleY(hasFocus ? 1.05f : 1.0f)
                    .translationY(hasFocus ? -GhibliTheme.dp(getContext(), 6) : 0f)
                    .setDuration(180)
                    .start();

            nameView.setBackground(null);
            nameView.setTextColor(hasFocus ? Color.WHITE : GhibliTheme.COLOR_PAPER);
            nameView.setPadding(0, GhibliTheme.dp(getContext(), 6), 0, 0);
            metaView.setBackground(null);
            metaView.setTextColor(hasFocus ? GhibliTheme.COLOR_GOLD : Color.parseColor("#b4c4b2"));

            if (focusListener != null) {
                if (hasFocus) focusListener.onLibraryFocused(v);
                else focusListener.onDockFocusCleared();
            }
        });

        return card;
    }

    private void loadArchiveIcon(ImageView iconView) {
        Bitmap cached = iconCache.get("app-archive");
        if (cached != null) {
            iconView.setImageBitmap(cached);
            return;
        }

        iconExecutor.execute(() -> {
            try (InputStream is = getContext().getAssets().open("icons/icon-archive.webp")) {
                Bitmap bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) {
                    synchronized (iconCache) {
                        iconCache.put("app-archive", bmp);
                    }
                    iconHandler.post(() -> {
                        if (!disposed) iconView.setImageBitmap(bmp);
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    private void bindAppIcon(ImageView iconView, AppItem app) {
        String kind = app.getKind();
        String assetPath = (kind != null && !kind.isEmpty()) ? app.getAssetPath() : null;
        if (assetPath != null) {
            Bitmap cached = iconCache.get(assetPath);
            if (cached != null) {
                iconView.setImageBitmap(cached);
                return;
            }

            synchronized (iconRequests) {
                List<ImageView> list = iconRequests.get(assetPath);
                if (list != null) {
                    list.add(iconView);
                    return;
                }
                list = new java.util.ArrayList<>();
                list.add(iconView);
                iconRequests.put(assetPath, list);
            }

            iconExecutor.execute(() -> {
                Bitmap bmp = null;
                try (InputStream is = getContext().getAssets().open(assetPath)) {
                    bmp = BitmapFactory.decodeStream(is);
                } catch (Exception ignored) {}

                final Bitmap finalBmp = bmp;
                iconHandler.post(() -> {
                    if (disposed) return;
                    List<ImageView> targets;
                    synchronized (iconRequests) {
                        targets = iconRequests.remove(assetPath);
                        if (finalBmp != null) iconCache.put(assetPath, finalBmp);
                    }
                    if (targets != null && finalBmp != null) {
                        for (ImageView target : targets) target.setImageBitmap(finalBmp);
                    } else if (targets != null) {
                        Drawable sys = app.getSystemIcon();
                        if (sys != null) {
                            for (ImageView target : targets) target.setImageDrawable(sys);
                        } else {
                            Bitmap monogram = createMonogramBitmap(app.getName());
                            for (ImageView target : targets) target.setImageBitmap(monogram);
                        }
                    }
                });
            });
        } else {
            Drawable sysIcon = app.getSystemIcon();
            if (sysIcon != null) {
                iconView.setImageDrawable(sysIcon);
            } else {
                iconView.setImageBitmap(createMonogramBitmap(app.getName()));
            }
        }
    }

    private Bitmap createMonogramBitmap(String name) {
        int size = GhibliTheme.dp(getContext(), 88);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        p.setColor(GhibliTheme.COLOR_BUTTON_BG);
        float radius = GhibliTheme.dp(getContext(), 18);
        canvas.drawRoundRect(2, 2, size - 2, size - 2, radius, radius, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(GhibliTheme.dp(getContext(), 1.5f));
        p.setColor(GhibliTheme.COLOR_ACCENT);
        canvas.drawRoundRect(2, 2, size - 2, size - 2, radius, radius, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(GhibliTheme.COLOR_PAPER);
        p.setTextSize(size * 0.44f);
        p.setTypeface(GhibliTheme.getCaveatBold(getContext()));
        p.setTextAlign(Paint.Align.CENTER);

        String letter = (name != null && !name.isEmpty()) ? name.substring(0, Math.min(name.length(), 2)) : "?";
        canvas.drawText(letter, size / 2f, size * 0.62f, p);

        return bmp;
    }

    public void release() {
        disposed = true;
        iconHandler.removeCallbacksAndMessages(null);
        iconExecutor.shutdownNow();
        for (Bitmap bmp : iconCache.values()) {
            if (bmp != null && !bmp.isRecycled()) bmp.recycle();
        }
        iconCache.clear();
    }
}

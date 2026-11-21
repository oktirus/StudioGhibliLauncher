package ua.ghibli.tv.launcher.theme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;

/**
 * Studio Ghibli warm forest canopy, aged cedar wood & watercolor parchment design tokens.
 */
public final class GhibliTheme {
    private GhibliTheme() {}

    // Core Studio Ghibli botanical & parchment palette
    public static final int COLOR_BG = Color.parseColor("#13211b");
    public static final int COLOR_INK = Color.parseColor("#1b2e25");
    public static final int COLOR_PAPER = Color.parseColor("#f6eed8");
    public static final int COLOR_ACCENT = Color.parseColor("#e8b858");      // Sunlit honey gold
    public static final int COLOR_TERRACOTTA = Color.parseColor("#d96b4b");  // Warm Kiki/bathhouse vermilion
    public static final int COLOR_GOLD = Color.parseColor("#efd18c");        // Soft lantern parchment gold
    public static final int COLOR_SAGE = Color.parseColor("#94c684");        // Fresh camphor leaf green
    public static final int COLOR_MUTED = Color.parseColor("#a4b8a6");
    public static final int COLOR_MUTED_LIGHT = Color.parseColor("#dcd7c2");
    public static final int COLOR_WHITE = Color.WHITE;

    // Warm forest-cedar surfaces
    public static final int COLOR_PANEL_START = Color.parseColor("#233a2e");
    public static final int COLOR_PANEL_END = Color.parseColor("#15251d");
    public static final int COLOR_CARD_BG = Color.parseColor("#1c3127");
    public static final int COLOR_CARD_ACTIVE = Color.parseColor("#352f1d");
    public static final int COLOR_CARD_BORDER = Color.parseColor("#e8b858");
    public static final int COLOR_BUTTON_BG = Color.parseColor("#21382c");
    public static final int COLOR_BUTTON_FOCUS = Color.parseColor("#335342");
    public static final int COLOR_TRANSLUCENT_FOREST = Color.parseColor("#8813211b");

    private static Typeface nunitoRegular;
    private static Typeface nunitoBold;
    private static Typeface caveatRegular;
    private static Typeface caveatBold;

    public static synchronized Typeface getNunito(Context context) {
        if (nunitoRegular == null) {
            try {
                nunitoRegular = Typeface.createFromAsset(context.getAssets(), "fonts/NunitoSans.ttf");
            } catch (Exception e) {
                nunitoRegular = Typeface.SANS_SERIF;
            }
        }
        return nunitoRegular;
    }

    public static synchronized Typeface getNunitoBold(Context context) {
        if (nunitoBold == null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    nunitoBold = new Typeface.Builder(context.getAssets(), "fonts/NunitoSans.ttf")
                            .setFontVariationSettings("'wght' 700")
                            .build();
                }
            } catch (Exception ignored) {}
            if (nunitoBold == null) {
                try {
                    nunitoBold = Typeface.create(getNunito(context), Typeface.BOLD);
                } catch (Exception e) {
                    nunitoBold = getNunito(context);
                }
            }
        }
        return nunitoBold != null ? nunitoBold : getNunito(context);
    }

    public static synchronized Typeface getCaveat(Context context) {
        if (caveatRegular == null) {
            try {
                caveatRegular = Typeface.createFromAsset(context.getAssets(), "fonts/Caveat.ttf");
            } catch (Exception e) {
                caveatRegular = Typeface.SERIF;
            }
        }
        return caveatRegular;
    }

    public static synchronized Typeface getCaveatBold(Context context) {
        if (caveatBold == null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    caveatBold = new Typeface.Builder(context.getAssets(), "fonts/Caveat.ttf")
                            .setFontVariationSettings("'wght' 700")
                            .build();
                }
            } catch (Exception ignored) {}
            if (caveatBold == null) {
                try {
                    caveatBold = Typeface.create(getCaveat(context), Typeface.BOLD);
                } catch (Exception e) {
                    caveatBold = getCaveat(context);
                }
            }
        }
        return caveatBold != null ? caveatBold : getCaveat(context);
    }

    public static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics()
        );
    }

    public static GradientDrawable createPanelBackground() {
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{COLOR_PANEL_START, COLOR_PANEL_END}
        );
        gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return gd;
    }

    public static GradientDrawable createRoundedBorder(int bgColor, int borderColor, float radiusPx, float strokePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(radiusPx);
        if (strokePx > 0) {
            gd.setStroke((int) strokePx, borderColor);
        }
        return gd;
    }
}

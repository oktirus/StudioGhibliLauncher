package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.GlyphIconView;

/**
 * Studio Ghibli warm forest-cedar side-sheet container with sunlit honey-gold rim.
 */
public class SideSheetContainer extends FrameLayout {
    public interface OnCloseListener {
        void onClose();
    }

    private final boolean isWide;
    private final FrameLayout sheetLayout;
    private final LinearLayout headerLayout;
    private final TextView subtitleView;
    private final TextView titleView;
    private final FrameLayout closeButton;
    private final GlyphIconView closeIcon;
    private final FrameLayout bodyContainer;

    private final Paint scrimPaint = new Paint();
    private final Paint goldRimPaint = new Paint();

    private OnCloseListener closeListener;

    public SideSheetContainer(Context context, boolean isWide, String subtitle, String title) {
        super(context);
        this.isWide = isWide;

        setFocusable(false);
        setFocusableInTouchMode(false);
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        scrimPaint.setColor(Color.parseColor("#9913211b"));
        goldRimPaint.setColor(GhibliTheme.COLOR_ACCENT);
        goldRimPaint.setStrokeWidth(GhibliTheme.dp(context, 2f));
        goldRimPaint.setStyle(Paint.Style.STROKE);

        setOnClickListener(v -> {
            if (closeListener != null) closeListener.onClose();
        });

        sheetLayout = new FrameLayout(context);
        sheetLayout.setClickable(true);

        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{GhibliTheme.COLOR_PANEL_START, GhibliTheme.COLOR_PANEL_END}
        );
        sheetLayout.setBackground(bg);
        sheetLayout.setPadding(
                GhibliTheme.dp(context, 36),
                GhibliTheme.dp(context, 30),
                GhibliTheme.dp(context, 36),
                GhibliTheme.dp(context, 24)
        );

        LinearLayout verticalBox = new LinearLayout(context);
        verticalBox.setOrientation(LinearLayout.VERTICAL);

        headerLayout = new LinearLayout(context);
        headerLayout.setOrientation(LinearLayout.HORIZONTAL);
        headerLayout.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        subtitleView = new TextView(context);
        subtitleView.setText(subtitle != null ? subtitle : "Долина Ghibli");
        subtitleView.setTextColor(GhibliTheme.COLOR_SAGE);
        subtitleView.setTextSize(11);
        subtitleView.setTypeface(GhibliTheme.getNunitoBold(context));
        titleCol.addView(subtitleView);

        titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(GhibliTheme.COLOR_PAPER);
        titleView.setTextSize(34);
        titleView.setTypeface(GhibliTheme.getCaveatBold(context));
        titleCol.addView(titleView);

        headerLayout.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        closeButton = new FrameLayout(context);
        int cbSize = GhibliTheme.dp(context, 40);
        LinearLayout.LayoutParams cbLp = new LinearLayout.LayoutParams(cbSize, cbSize);
        closeButton.setLayoutParams(cbLp);
        closeButton.setFocusable(true);
        closeButton.setFocusableInTouchMode(true);

        GradientDrawable cbBg = new GradientDrawable();
        cbBg.setColor(Color.parseColor("#551c3127"));
        cbBg.setCornerRadius(GhibliTheme.dp(context, 12));
        cbBg.setStroke(GhibliTheme.dp(context, 1), Color.parseColor("#55e8b858"));
        closeButton.setBackground(cbBg);

        closeIcon = new GlyphIconView(context);
        closeIcon.setGlyph(GlyphIconView.Glyph.CLOSE);
        closeIcon.setIconColor(GhibliTheme.COLOR_PAPER);
        int iconSize = GhibliTheme.dp(context, 16);
        FrameLayout.LayoutParams ciLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER);
        closeButton.addView(closeIcon, ciLp);

        closeButton.setOnClickListener(v -> {
            if (closeListener != null) closeListener.onClose();
        });

        closeButton.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? GhibliTheme.COLOR_BUTTON_FOCUS : Color.parseColor("#551c3127"));
            d.setCornerRadius(GhibliTheme.dp(context, 12));
            d.setStroke(GhibliTheme.dp(context, hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#55e8b858"));
            v.setBackground(d);
        });

        headerLayout.addView(closeButton);
        verticalBox.addView(headerLayout);

        bodyContainer = new FrameLayout(context);
        verticalBox.addView(bodyContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f
        ));

        sheetLayout.addView(verticalBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addView(sheetLayout);
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.closeListener = listener;
    }

    public void setBodyView(View view) {
        bodyContainer.removeAllViews();
        if (view != null) {
            bodyContainer.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ));
        }
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public View getCloseButton() {
        return closeButton;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);

        if (w > 0) {
            int sheetW;
            if (isWide) {
                sheetW = Math.min(GhibliTheme.dp(getContext(), 1050), (int) (w * 0.95f));
            } else {
                sheetW = Math.min(GhibliTheme.dp(getContext(), 680), (int) (w * 0.94f));
            }
            LayoutParams lp = (LayoutParams) sheetLayout.getLayoutParams();
            if (lp != null) {
                lp.width = sheetW;
                lp.height = LayoutParams.MATCH_PARENT;
                lp.gravity = Gravity.END;
                sheetLayout.setLayoutParams(lp);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int sheetLeft = sheetLayout.getLeft();
        if (sheetLeft > 0) {
            canvas.drawRect(0, 0, sheetLeft, getHeight(), scrimPaint);
            canvas.drawLine(sheetLeft, 0, sheetLeft, getHeight(), goldRimPaint);
        }
        super.dispatchDraw(canvas);
    }
}

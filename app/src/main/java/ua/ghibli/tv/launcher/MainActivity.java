package ua.ghibli.tv.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import ua.ghibli.tv.launcher.apps.AppManager;
import ua.ghibli.tv.launcher.apps.HomeRedirectService;
import ua.ghibli.tv.launcher.apps.HomeScreenSetup;
import ua.ghibli.tv.launcher.audio.AudioController;
import ua.ghibli.tv.launcher.model.AppItem;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.Quote;
import ua.ghibli.tv.launcher.model.Scene;
import ua.ghibli.tv.launcher.model.Technique;
import ua.ghibli.tv.launcher.panels.AmbientOverlay;
import ua.ghibli.tv.launcher.panels.AppContextMenuPanel;
import ua.ghibli.tv.launcher.panels.LibraryPanel;
import ua.ghibli.tv.launcher.panels.OrderPanel;
import ua.ghibli.tv.launcher.panels.ScenesPanel;
import ua.ghibli.tv.launcher.panels.SettingsPanel;
import ua.ghibli.tv.launcher.panels.SpiritsPanel;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.updates.UpdateController;
import ua.ghibli.tv.launcher.views.DockView;
import ua.ghibli.tv.launcher.views.GlyphIconView;
import ua.ghibli.tv.launcher.views.HeroTitleView;
import ua.ghibli.tv.launcher.views.LauncherResourceMonitor;
import ua.ghibli.tv.launcher.views.OutlinedGlyphButton;
import ua.ghibli.tv.launcher.views.SpiritFocusView;
import ua.ghibli.tv.launcher.views.SpiritRenderer;
import ua.ghibli.tv.launcher.views.SpiritShortcutButton;
import ua.ghibli.tv.launcher.views.TVKeyHelper;
import ua.ghibli.tv.launcher.views.ToastOverlay;
import ua.ghibli.tv.launcher.views.ValleyBackgroundView;
import ua.ghibli.tv.launcher.views.ValleyRunnerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Main Activity for Studio Ghibli TV Launcher ("Ghibli · Вітер над долиною").
 */
public class MainActivity extends Activity {
    private PreferencesManager prefs;
    private AppManager appManager;
    private HomeScreenSetup homeScreenSetup;
    private AudioController audioController;
    private SpiritRenderer spiritRenderer;

    private FrameLayout rootLayout;
    private LinearLayout mainShell;
    private ValleyBackgroundView backgroundView;
    private SpiritFocusView spiritFocusView;
    private DockView dockView;
    private ToastOverlay toastOverlay;
    private AmbientOverlay ambientOverlay;
    private LauncherResourceMonitor resources;

    // UI elements
    private TextView timeView;
    private TextView dateView;
    private TextView updateNotice;
    private HeroTitleView heroTitleView;
    private TextView heroDescView;
    private TextView quoteAuthorView;
    private OutlinedGlyphButton sceneChoiceBtn;
    private SpiritShortcutButton spiritBtn;
    private LinearLayout brandHeader;
    private OutlinedGlyphButton gameBtn;
    private OutlinedGlyphButton settingsBtn;

    // Panels
    private FrameLayout modalContainer;
    private FrameLayout contextMenuContainer;
    private LibraryPanel libraryPanel;
    private OrderPanel orderPanel;
    private ScenesPanel scenesPanel;
    private SpiritsPanel spiritsPanel;
    private SettingsPanel settingsPanel;
    private ValleyRunnerView runnerGameView;
    private View activePanel = null;
    private View lastFocusedView = null;
    private String lastFocusedPackage = null;
    private boolean lastFocusedIsLibrary = false;
    private boolean activityResumed;
    private UpdateController updateController;
    private final Runnable updateCheckRunnable = new Runnable() {
        @Override public void run() {
            if (!activityResumed) return;
            if (updateController != null) updateController.checkAutomatically();
            mainHandler.postDelayed(this, 60_000L);
        }
    };
    private boolean appInventoryReady;
    private boolean focusFirstAppOnHomeEntry = true;
    private ViewTreeObserver.OnGlobalFocusChangeListener focusChangeListener;
    private final ViewTreeObserver.OnGlobalLayoutListener homeEntryLayoutListener = () -> {
        if (focusFirstAppOnHomeEntry) restoreHomeFocus();
    };

    // Timers
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable rotationRunnable;
    private Runnable idleRunnable;
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            updateClock();
            mainHandler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen TV immersive mode
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );

        // 1. Subsystems
        prefs = new PreferencesManager(this);
        appManager = new AppManager(this, prefs);
        homeScreenSetup = new HomeScreenSetup(this, savedInstanceState);
        if (HomeRedirectService.ACTION_REDIRECTED_HOME.equals(getIntent().getAction())) {
            homeScreenSetup.onRedirectedHome();
        }
        updateController = new UpdateController(this, () -> activityResumed && hasWindowFocus()
                && activePanel == null && ambientOverlay != null && ambientOverlay.getVisibility() != View.VISIBLE
                && !isContextMenuOpen()
                && !homeScreenSetup.isPromptShowing() && !audioController.isLaunching()
                && !updateController.isShowing(), this::showAvailableUpdate);
        audioController = new AudioController(this, prefs);
        spiritRenderer = new SpiritRenderer(this);

        // 2. Build UI
        buildLayout();
        updateController.restoreAvailability();

        // 3. Initial state
        applyScene(prefs.getScene(), false);
        applyTechnique(prefs.getTechnique());
        dockView.render();
        wireSpatialNavigation();
        updateClock();
        restoreHomeFocus();

        // 4. Audio & Timers
        audioController.updateMusic();
        setupTimers();
        resetIdleTimer();
    }

    private void buildLayout() {
        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(GhibliTheme.COLOR_BG);

        // Painted Studio Ghibli valley background with particles & parallax
        backgroundView = new ValleyBackgroundView(this);
        backgroundView.setMotionEnabled(prefs.isMotionEnabled());
        rootLayout.addView(backgroundView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Main Home shell
        mainShell = new LinearLayout(this);
        mainShell.setOrientation(LinearLayout.VERTICAL);
        mainShell.setPadding(dp(40), dp(20), dp(40), dp(8));

        LinearLayout topBar = createTopBar();
        mainShell.addView(topBar);

        LinearLayout heroSection = createHeroSection();
        mainShell.addView(heroSection, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f
        ));

        LinearLayout appSection = createAppSection();
        mainShell.addView(appSection);

        LinearLayout footer = createFooter();
        mainShell.addView(footer);

        // Spirit focus overlay (placed behind mainShell for glow spirits, dynamically elevated for Soot Sprites)
        spiritFocusView = new SpiritFocusView(this, spiritRenderer);
        spiritFocusView.setMotionEnabled(prefs.isMotionEnabled());
        spiritFocusView.setTechnique(prefs.getTechnique());
        rootLayout.addView(spiritFocusView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        mainShell.setElevation(10f);
        rootLayout.addView(mainShell, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Modal side sheets / Valley Runner
        modalContainer = new FrameLayout(this);
        modalContainer.setVisibility(View.GONE);
        modalContainer.setElevation(50f);
        rootLayout.addView(modalContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Ambient ("Тиха мить") overlay
        ambientOverlay = new AmbientOverlay(this);
        ambientOverlay.setVisibility(View.GONE);
        ambientOverlay.setElevation(60f);
        ambientOverlay.setOnAmbientExitListener(this::exitAmbient);
        rootLayout.addView(ambientOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // Toast notifications
        toastOverlay = new ToastOverlay(this);
        rootLayout.addView(toastOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        // App Context Menu
        contextMenuContainer = new FrameLayout(this);
        contextMenuContainer.setVisibility(View.GONE);
        contextMenuContainer.setElevation(70f);
        rootLayout.addView(contextMenuContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ));

        setContentView(rootLayout);
        focusChangeListener = (oldFocus, newFocus) -> {
            if (isInside(newFocus, dockView)) {
                spiritFocusView.setTargetView(newFocus);
            } else if (newFocus != null && newFocus != rootLayout) {
                spiritFocusView.setTargetView(null);
            }
            if (activePanel == null && newFocus != null && newFocus != rootLayout
                    && !isInside(newFocus, modalContainer) && !isInside(newFocus, ambientOverlay)
                    && !isInside(newFocus, contextMenuContainer)) {
                rememberHomeFocus(newFocus);
            }
        };
        rootLayout.getViewTreeObserver().addOnGlobalFocusChangeListener(focusChangeListener);
        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(homeEntryLayoutListener);
    }

    private LinearLayout createTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)
        ));

        // Studio Ghibli valley crest + poetic wordmark (purely decorative, non-selectable)
        brandHeader = new LinearLayout(this);
        brandHeader.setOrientation(LinearLayout.HORIZONTAL);
        brandHeader.setGravity(Gravity.CENTER_VERTICAL);
        brandHeader.setFocusable(false);
        brandHeader.setFocusableInTouchMode(false);
        brandHeader.setClickable(false);
        brandHeader.setPadding(dp(6), dp(4), dp(12), dp(4));

        FrameLayout crestBadge = new FrameLayout(this);
        GradientDrawable crestBg = new GradientDrawable();
        crestBg.setShape(GradientDrawable.OVAL);
        crestBg.setColor(Color.parseColor("#cc1a3026"));
        crestBg.setStroke(dp(1), Color.parseColor("#aaebc163"));
        crestBadge.setBackground(crestBg);

        GlyphIconView crestGlyph = new GlyphIconView(this);
        crestGlyph.setGlyph(GlyphIconView.Glyph.ACORN_SEAL);
        crestGlyph.setIconColor(GhibliTheme.COLOR_ACCENT);
        FrameLayout.LayoutParams glyphLp = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER);
        crestBadge.addView(crestGlyph, glyphLp);
        brandHeader.addView(crestBadge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(10), 0, 0, 0);

        TextView brandTitle = new TextView(this);
        brandTitle.setText("Долина Ghibli");
        brandTitle.setTextColor(GhibliTheme.COLOR_PAPER);
        brandTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        brandTitle.setTypeface(GhibliTheme.getCaveatBold(this));
        brandTitle.setShadowLayer(8f, 0f, 2f, Color.parseColor("#aa0b140f"));
        brandCopy.addView(brandTitle);

        TextView brandSub = new TextView(this);
        brandSub.setText("ВІТЕР НАД ДОЛИНОЮ");
        brandSub.setTextColor(GhibliTheme.COLOR_GOLD);
        brandSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9);
        brandSub.setTypeface(GhibliTheme.getNunitoBold(this));
        brandSub.setLetterSpacing(0.12f);
        brandCopy.addView(brandSub);

        brandHeader.addView(brandCopy);
        bar.addView(brandHeader);

        View spacer = new View(this);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1.0f));

        // Top Status (Update pill, Clock, Date, Game & Settings buttons)
        LinearLayout topStatus = new LinearLayout(this);
        topStatus.setOrientation(LinearLayout.HORIZONTAL);
        topStatus.setGravity(Gravity.CENTER_VERTICAL);

        updateNotice = new TextView(this);
        updateNotice.setTextColor(GhibliTheme.COLOR_PAPER);
        updateNotice.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        updateNotice.setTypeface(GhibliTheme.getNunitoBold(this));
        updateNotice.setSingleLine(true);
        updateNotice.setEllipsize(TextUtils.TruncateAt.END);
        updateNotice.setMaxWidth(dp(180));
        updateNotice.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable noticeBackground = new GradientDrawable();
        noticeBackground.setColor(Color.parseColor("#F215251d"));
        noticeBackground.setCornerRadius(dp(12));
        noticeBackground.setStroke(dp(1), GhibliTheme.COLOR_ACCENT);
        updateNotice.setBackground(noticeBackground);
        updateNotice.setVisibility(View.GONE);
        LinearLayout.LayoutParams noticeLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noticeLayout.setMarginEnd(dp(12));
        topStatus.addView(updateNotice, noticeLayout);

        LinearLayout clockLayout = new LinearLayout(this);
        clockLayout.setOrientation(LinearLayout.VERTICAL);
        clockLayout.setGravity(Gravity.END);
        clockLayout.setPadding(0, 0, dp(12), 0);

        timeView = new TextView(this);
        timeView.setTextColor(GhibliTheme.COLOR_PAPER);
        timeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        timeView.setTypeface(GhibliTheme.getNunitoBold(this));
        timeView.setShadowLayer(6f, 0f, 1f, Color.parseColor("#990b140f"));
        clockLayout.addView(timeView);

        dateView = new TextView(this);
        dateView.setTextColor(GhibliTheme.COLOR_MUTED_LIGHT);
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        dateView.setTypeface(GhibliTheme.getNunito(this));
        clockLayout.addView(dateView);

        topStatus.addView(clockLayout);

        // Mini-game button
        gameBtn = new OutlinedGlyphButton(this);
        gameBtn.setBorderless(true);
        gameBtn.setGlyph(GlyphIconView.Glyph.GAMEPAD);
        gameBtn.setIconSize(18);
        gameBtn.setContentDescription("Політ крізь долину — мінігра");
        gameBtn.setOnClickListener(v -> openGame());
        topStatus.addView(gameBtn);

        // Settings lantern button
        settingsBtn = new OutlinedGlyphButton(this);
        settingsBtn.setBorderless(true);
        settingsBtn.setGlyph(GlyphIconView.Glyph.LANTERN_SETTINGS);
        settingsBtn.setIconSize(18);
        settingsBtn.setContentDescription("Налаштування");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        slp.setMargins(dp(6), 0, 0, 0);
        settingsBtn.setLayoutParams(slp);
        settingsBtn.setOnClickListener(v -> openSettings());
        topStatus.addView(settingsBtn);

        bar.addView(topStatus);
        return bar;
    }

    private void showAvailableUpdate(String versionName) {
        updateNotice.setText(versionName == null ? null : getString(R.string.update_available_label, versionName));
        updateNotice.setVisibility(versionName == null ? View.GONE : View.VISIBLE);
    }

    private LinearLayout createHeroSection() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.BOTTOM);
        hero.setPadding(0, 0, 0, dp(8));

        // Keep the caption and scene selector at their natural height on shorter TV viewports.
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        hero.addView(copy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        heroTitleView = new HeroTitleView(this);
        copy.addView(heroTitleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        heroDescView = new TextView(this);
        heroDescView.setTextColor(GhibliTheme.COLOR_PAPER);
        heroDescView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        heroDescView.setTypeface(GhibliTheme.getNunito(this));
        heroDescView.setShadowLayer(8f, 0f, 2f, Color.parseColor("#aa0b140f"));
        heroDescView.setPadding(0, dp(4), 0, 0);
        copy.addView(heroDescView);

        quoteAuthorView = new TextView(this);
        quoteAuthorView.setTextColor(GhibliTheme.COLOR_GOLD);
        quoteAuthorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        quoteAuthorView.setTypeface(GhibliTheme.getNunitoBold(this));
        quoteAuthorView.setShadowLayer(6f, 0f, 1f, Color.parseColor("#990b140f"));
        quoteAuthorView.setPadding(0, dp(2), 0, dp(10));
        copy.addView(quoteAuthorView);

        sceneChoiceBtn = new OutlinedGlyphButton(this);
        sceneChoiceBtn.setPill(true);
        sceneChoiceBtn.setGlyph(GlyphIconView.Glyph.WALLPAPERS);
        sceneChoiceBtn.setOnClickListener(v -> openScenes());
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        sceneChoiceBtn.setLayoutParams(scLp);
        copy.addView(sceneChoiceBtn);

        return hero;
    }

    private LinearLayout createAppSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setPaddingRelative(dp(9), 0, 0, dp(4));

        // Botanical acorn-seal badge aligned with the scene selector icon & label
        FrameLayout sealWrap = new FrameLayout(this);
        GradientDrawable sealBg = new GradientDrawable();
        sealBg.setColor(Color.parseColor("#551c3127"));
        sealBg.setStroke(dp(1), Color.parseColor("#66e8b858"));
        sealBg.setCornerRadius(dp(6));
        sealWrap.setBackground(sealBg);

        GlyphIconView acornIcon = new GlyphIconView(this);
        acornIcon.setGlyph(GlyphIconView.Glyph.ACORN_SEAL);
        acornIcon.setIconColor(GhibliTheme.COLOR_ACCENT);
        int iconSize = dp(14);
        sealWrap.addView(acornIcon, new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER));

        int sealSize = dp(22);
        LinearLayout.LayoutParams sealLp = new LinearLayout.LayoutParams(sealSize, sealSize);
        int labelStart = sceneChoiceBtn.getPaddingStart() + dp(16) + dp(8);
        sealLp.setMarginEnd(labelStart - heading.getPaddingStart() - sealSize);
        heading.addView(sealWrap, sealLp);

        TextView title = new TextView(this);
        title.setText("Улюблені застосунки");
        title.setTextColor(GhibliTheme.COLOR_PAPER);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(GhibliTheme.getNunitoBold(this));
        title.setShadowLayer(6f, 0f, 1f, Color.parseColor("#990b140f"));
        heading.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        spiritBtn = new SpiritShortcutButton(this);
        spiritBtn.setOnClickListener(v -> openSpirits());
        heading.addView(spiritBtn);

        section.addView(heading);

        // Favorite Apps Dock
        dockView = new DockView(this, appManager);
        dockView.setOnDockItemClickListener(new DockView.OnDockItemClickListener() {
            @Override public void onAppClick(AppItem app) { launchApp(app); }
            @Override public void onAppLongClick(View view, AppItem app, int position) {
                showContextMenu(app, true, position, view);
            }
            @Override public void onLibraryClick() { openLibrary(); }
        });
        dockView.setOnDockItemFocusListener(new DockView.OnDockItemFocusListener() {
            @Override public void onItemFocused(View view, AppItem app) {
                if (activePanel != null || (ambientOverlay != null && ambientOverlay.getVisibility() == View.VISIBLE)) return;
                lastFocusedView = view;
                lastFocusedPackage = app != null ? app.getPackageName() : null;
                lastFocusedIsLibrary = false;
                spiritFocusView.setTargetView(view);
                resetIdleTimer();
            }
            @Override public void onLibraryFocused(View view) {
                if (activePanel != null || (ambientOverlay != null && ambientOverlay.getVisibility() == View.VISIBLE)) return;
                lastFocusedView = view;
                lastFocusedPackage = null;
                lastFocusedIsLibrary = true;
                spiritFocusView.setTargetView(view);
                resetIdleTimer();
            }
            @Override public void onDockFocusCleared() {
                spiritFocusView.setTargetView(null);
            }
        });
        section.addView(dockView);

        return section;
    }

    private LinearLayout createFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setPadding(0, dp(14), 0, dp(4));

        TextView guide = new TextView(this);
        guide.setText("↔ обрати  |  OK відкрити  |  ☰ налаштування");
        guide.setTextColor(GhibliTheme.COLOR_MUTED);
        guide.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        guide.setTypeface(GhibliTheme.getNunito(this));
        footer.addView(guide, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView resourceStats = new TextView(this);
        resourceStats.setTextColor(GhibliTheme.COLOR_PAPER);
        resourceStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        resourceStats.setTypeface(GhibliTheme.getNunito(this));
        resourceStats.setSingleLine(true);
        resourceStats.setPaddingRelative(dp(8), 0, dp(12), 0);
        resourceStats.setVisibility(View.GONE);
        footer.addView(resourceStats);
        resources = new LauncherResourceMonitor(resourceStats, mainHandler);

        TextView tag = new TextView(this);
        tag.setText("Долина Ghibli · Вітер змін");
        tag.setTextColor(GhibliTheme.COLOR_ACCENT);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        tag.setTypeface(GhibliTheme.getNunitoBold(this));
        footer.addView(tag);

        return footer;
    }

    public void wireSpatialNavigation() {
        View firstCard = dockView != null ? dockView.getFirstCard() : null;
        View archiveCard = dockView != null ? dockView.getArchiveCard() : null;

        if (sceneChoiceBtn != null && sceneChoiceBtn.getId() == View.NO_ID) sceneChoiceBtn.setId(View.generateViewId());
        if (gameBtn != null && gameBtn.getId() == View.NO_ID) gameBtn.setId(View.generateViewId());
        if (settingsBtn != null && settingsBtn.getId() == View.NO_ID) settingsBtn.setId(View.generateViewId());
        if (spiritBtn != null && spiritBtn.getId() == View.NO_ID) spiritBtn.setId(View.generateViewId());
        if (firstCard != null && firstCard.getId() == View.NO_ID) firstCard.setId(View.generateViewId());
        if (archiveCard != null && archiveCard.getId() == View.NO_ID) archiveCard.setId(View.generateViewId());

        if (sceneChoiceBtn != null) {
            sceneChoiceBtn.setNextFocusLeftId(sceneChoiceBtn.getId());
            if (firstCard != null) {
                firstCard.setNextFocusUpId(sceneChoiceBtn.getId());
                firstCard.setNextFocusLeftId(firstCard.getId());
                sceneChoiceBtn.setNextFocusDownId(firstCard.getId());
            }
            if (gameBtn != null) {
                sceneChoiceBtn.setNextFocusUpId(gameBtn.getId());
                gameBtn.setNextFocusLeftId(sceneChoiceBtn.getId());
            } else {
                sceneChoiceBtn.setNextFocusUpId(sceneChoiceBtn.getId());
            }
            if (spiritBtn != null) {
                sceneChoiceBtn.setNextFocusRightId(spiritBtn.getId());
                spiritBtn.setNextFocusLeftId(sceneChoiceBtn.getId());
            }
        }

        if (gameBtn != null) {
            gameBtn.setNextFocusUpId(gameBtn.getId());
        }

        if (gameBtn != null && settingsBtn != null) {
            gameBtn.setNextFocusRightId(settingsBtn.getId());
            settingsBtn.setNextFocusLeftId(gameBtn.getId());
            settingsBtn.setNextFocusRightId(settingsBtn.getId());
            settingsBtn.setNextFocusUpId(settingsBtn.getId());
        }

        if (spiritBtn != null) {
            spiritBtn.setNextFocusRightId(spiritBtn.getId());
            if (archiveCard != null) {
                archiveCard.setNextFocusUpId(spiritBtn.getId());
                archiveCard.setNextFocusRightId(archiveCard.getId());
                spiritBtn.setNextFocusDownId(archiveCard.getId());
            }
            if (gameBtn != null) {
                spiritBtn.setNextFocusUpId(gameBtn.getId());
                gameBtn.setNextFocusDownId(spiritBtn.getId());
            }
            if (settingsBtn != null) {
                settingsBtn.setNextFocusDownId(spiritBtn.getId());
            }
        }
    }

    private void updateClock() {
        Date now = new Date();
        SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", new Locale("uk", "UA"));
        SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", new Locale("uk", "UA"));
        if (timeView != null) timeView.setText(timeFmt.format(now));
        if (dateView != null) dateView.setText(dateFmt.format(now));
    }

    private void applyScene(String sceneId, boolean animate) {
        Scene scene = CatalogData.SCENES.stream()
                .filter(s -> s.getId().equals(sceneId)).findFirst().orElse(CatalogData.SCENES.get(0));
        prefs.setScene(scene.getId());
        backgroundView.setScene(scene, animate);

        Quote q = CatalogData.QUOTES.get(scene.getId());
        if (q != null) {
            heroTitleView.setTitle(scene.getTitle());
            heroDescView.setText("«" + q.getText() + "»");
            quoteAuthorView.setText(q.getCharacter());
            if (ambientOverlay != null) ambientOverlay.setQuote(q.getText());
        }

        if (sceneChoiceBtn != null) {
            sceneChoiceBtn.setText(scene.getName());
            sceneChoiceBtn.setContentDescription("Обрати краєвид: " + scene.getName());
        }
    }

    private void applyTechnique(String techniqueId) {
        Technique tech = CatalogData.TECHNIQUES.stream()
                .filter(t -> t.getId().equals(techniqueId)).findFirst().orElse(CatalogData.TECHNIQUES.get(0));
        prefs.setTechnique(tech.getId());
        spiritFocusView.setTechnique(tech.getId());
        spiritRenderer.trimMemory(SpiritRenderer.platesFor(tech.getId()));
        if (spiritBtn != null) {
            spiritBtn.setTechnique(tech.getName(), tech.getColor());
        }
    }

    private void launchApp(AppItem app) {
        if (audioController.isLaunching()) return;

        spiritFocusView.triggerBurst();
        if (prefs.isAudioEnabled()) {
            String techId = prefs.getTechnique();
            Technique t = CatalogData.TECHNIQUES.stream()
                    .filter(x -> x.getId().equals(techId)).findFirst().orElse(null);
            String name = t != null ? t.getName() : "Дух долини";
            toastOverlay.show(name + "… Back — скасувати", 8000);
        }

        audioController.launchWithTechnique(prefs.getTechnique(), () -> {
            toastOverlay.hide();
            appManager.launchApp(app.getPackageName());
        });
    }

    // Modal panel management
    private void showPanel(View panel) {
        if (activePanel == panel) return;
        if (activePanel == null) {
            rememberHomeFocus();
            focusFirstAppOnHomeEntry = false;
        }
        closeActivePanel();
        activePanel = panel;
        mainShell.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        modalContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        backgroundView.setDecorPaused(true);
        spiritFocusView.setTargetView(null);
        modalContainer.removeAllViews();
        modalContainer.addView(panel);
        modalContainer.setVisibility(View.VISIBLE);
        panel.requestFocus();
    }

    private boolean isInside(View view, View ancestor) {
        for (View current = view; current != null; ) {
            if (current == ancestor) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void rememberHomeFocus() {
        rememberHomeFocus(rootLayout.findFocus());
    }

    private void rememberHomeFocus(View focused) {
        if (focused == null || isInside(focused, modalContainer) || isInside(focused, ambientOverlay)
                || isInside(focused, contextMenuContainer)) return;
        lastFocusedView = focused;
        Object tag = focused.getTag();
        lastFocusedPackage = tag instanceof AppItem ? ((AppItem) tag).getPackageName() : null;
        lastFocusedIsLibrary = "archive".equals(tag);
    }

    private void restoreHomeFocus() {
        if (!activityResumed || activePanel != null || ambientOverlay.getVisibility() == View.VISIBLE
                || isContextMenuOpen()) return;
        View target = focusFirstAppOnHomeEntry ? dockView.getFirstCard() : null;
        if (!focusFirstAppOnHomeEntry && lastFocusedIsLibrary) {
            target = dockView.getArchiveCard();
        } else if (!focusFirstAppOnHomeEntry && lastFocusedPackage != null) {
            target = dockView.findCardByPackage(lastFocusedPackage);
        }
        if (target == null && lastFocusedView != null && lastFocusedView.isAttachedToWindow()) {
            target = lastFocusedView;
        }
        if (target == null) {
            target = dockView.getFirstCard();
        }
        if (target != null) {
            lastFocusedView = target;
            boolean inDock = isInside(target, dockView);
            spiritFocusView.setTargetView(inDock ? target : null);
            target.requestFocusFromTouch();
            if (target.requestFocus() && appInventoryReady && target.isLaidOut()) focusFirstAppOnHomeEntry = false;
            final View fTarget = target;
            fTarget.post(() -> {
                if (!activityResumed || activePanel != null || ambientOverlay.getVisibility() == View.VISIBLE
                        || !fTarget.isAttachedToWindow() || lastFocusedView != fTarget) return;
                fTarget.requestFocusFromTouch();
                if (fTarget.requestFocus()) {
                    spiritFocusView.setTargetView(isInside(fTarget, dockView) ? fTarget : null);
                    if (appInventoryReady && fTarget.isLaidOut()) focusFirstAppOnHomeEntry = false;
                }
            });
        }
    }

    public void closeActivePanel() {
        closeContextMenu(false);
        if (activePanel != null) {
            View closing = activePanel;
            if (closing instanceof ValleyRunnerView) {
                ((ValleyRunnerView) closing).release();
                runnerGameView = null;
            } else if (closing == scenesPanel) {
                scenesPanel.release();
                scenesPanel = null;
            } else if (closing == spiritsPanel) {
                spiritsPanel.release();
                spiritsPanel = null;
            } else if (closing == libraryPanel) {
                libraryPanel.close();
                libraryPanel = null;
            } else if (closing == orderPanel) {
                orderPanel = null;
            } else if (closing == settingsPanel) {
                settingsPanel = null;
            }
            modalContainer.removeAllViews();
            modalContainer.setVisibility(View.GONE);
            modalContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            activePanel = null;
            mainShell.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            spiritRenderer.trimMemory(SpiritRenderer.platesFor(prefs.getTechnique()));
            backgroundView.setDecorPaused(false);
            backgroundView.setMotionEnabled(prefs.isMotionEnabled());
            spiritFocusView.setMotionEnabled(prefs.isMotionEnabled());
            spiritFocusView.setTechnique(prefs.getTechnique());
            setupTimers();
            resetIdleTimer();
            wireSpatialNavigation();
            restoreHomeFocus();
            updateController.onHomeAvailable();
        }
    }

    private void openLibrary() {
        if (libraryPanel == null) {
            libraryPanel = new LibraryPanel(this, appManager, dockView, spiritRenderer);
            libraryPanel.setOnLibraryActionListener(new LibraryPanel.OnLibraryActionListener() {
                @Override public void onAppLaunch(AppItem app) { launchApp(app); }
                @Override public void onAppContextMenuRequested(View view, AppItem app) {
                    showContextMenu(app, false, -1, view);
                }
                @Override public void onOrderPanelRequested() { openOrder(); }
                @Override public void onCloseRequested() { closeActivePanel(); }
                @Override public void onAppFocused(View view, AppItem app) {
                    audioController.playFocusSound();
                }
            });
        }
        libraryPanel.setTechnique(prefs.getTechnique());
        libraryPanel.setMotionEnabled(prefs.isMotionEnabled());
        showPanel(libraryPanel);
        libraryPanel.open();
        audioController.playUiSound();
    }

    private void openOrder() {
        if (orderPanel == null) {
            orderPanel = new OrderPanel(this, appManager, dockView);
            orderPanel.setOnOrderActionListener(new OrderPanel.OnOrderActionListener() {
                @Override public void onBackToLibraryRequested() { openLibrary(); }
                @Override public void onCloseRequested() { closeActivePanel(); }
            });
        }
        showPanel(orderPanel);
        orderPanel.renderList();
        audioController.playUiSound();
    }

    private void openScenes() {
        if (scenesPanel == null) {
            scenesPanel = new ScenesPanel(this, prefs);
            scenesPanel.setOnSceneSelectedListener(new ScenesPanel.OnSceneSelectedListener() {
                @Override public void onSceneSelected(Scene scene) {
                    applyScene(scene.getId(), true);
                    closeActivePanel();
                }
                @Override public void onCloseRequested() { closeActivePanel(); }
            });
        }
        showPanel(scenesPanel);
        scenesPanel.renderScenes();
        audioController.playUiSound();
    }

    private void openSpirits() {
        if (spiritsPanel == null) {
            spiritsPanel = new SpiritsPanel(this, prefs, spiritRenderer);
            spiritsPanel.setOnSpiritSelectedListener(new SpiritsPanel.OnSpiritSelectedListener() {
                @Override public void onSpiritSelected(Technique technique) {
                    applyTechnique(technique.getId());
                    View first = dockView.getFirstCard();
                    if (first != null) {
                        lastFocusedView = first;
                        lastFocusedPackage = first.getTag() instanceof AppItem ? ((AppItem) first.getTag()).getPackageName() : null;
                        lastFocusedIsLibrary = false;
                    }
                    closeActivePanel();
                    if (first != null) {
                        spiritFocusView.setTargetView(first);
                        first.requestFocusFromTouch();
                        first.requestFocus();
                        first.post(() -> {
                            first.requestFocusFromTouch();
                            first.requestFocus();
                            spiritFocusView.setTargetView(first);
                        });
                    }
                    spiritFocusView.triggerBurst();
                    audioController.playUiSound();
                }
                @Override public void onCloseRequested() { closeActivePanel(); }
            });
        }
        showPanel(spiritsPanel);
        spiritsPanel.renderSpirits();
        audioController.playUiSound();
    }

    private void openSettings() {
        if (settingsPanel == null) {
            settingsPanel = new SettingsPanel(this, prefs, audioController);
            settingsPanel.setOnSettingsActionListener(new SettingsPanel.OnSettingsActionListener() {
                @Override public void onSpiritsRequested() { openSpirits(); }
                @Override public void onAmbientRequested() { enterAmbient(); }
                @Override public void onSystemSettingsRequested(String target) { appManager.openSettings(target); }
                @Override public void onHomeScreenRequested() { homeScreenSetup.openSelection(); }
                @Override public void onHomeRedirectRequested() { homeScreenSetup.showRedirectSettings(); }
                @Override public void onUpdatesRequested() { updateController.checkManually(); }
                @Override public void onRefreshAppsRequested() {
                    appManager.reloadAppsAsync(() -> {
                        if (!activityResumed || isDestroyed()) return;
                        dockView.render();
                        wireSpatialNavigation();
                        toastOverlay.show("Список застосунків оновлено", 2500);
                    });
                }
                @Override public void onAboutRequested() { showAboutDialog(); }
                @Override public void onResourcesChanged() { updateResourceDisplay(); }
                @Override public void onCloseRequested() { closeActivePanel(); }
            });
        }
        showPanel(settingsPanel);
        settingsPanel.updateState();
        updateHomeSettingsStatus();
        settingsPanel.focusInitial();
        audioController.playUiSound();
    }

    private void updateHomeSettingsStatus() {
        if (settingsPanel == null) return;
        settingsPanel.setHomeScreenActive(homeScreenSetup.isDefaultHome());
        settingsPanel.setHomeRedirectEnabled(HomeRedirectService.isEnabled(this));
    }

    private void updateResourceDisplay() {
        resources.setActive(activityResumed && mainShell.getVisibility() == View.VISIBLE
                && prefs.shouldShowResources());
    }

    private void openGame() {
        if (runnerGameView == null) {
            audioController.prepareRunnerSounds();
            runnerGameView = new ValleyRunnerView(this, prefs);
            runnerGameView.setOnExitListener(this::closeActivePanel);
            runnerGameView.setSoundCallbacks(audioController::playRunnerSound, audioController::stopRunnerSounds);
        }
        showPanel(runnerGameView);
        runnerGameView.onResumeLifecycle();
        audioController.playUiSound();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Ghibli · Вітер над долиною")
                .setMessage("Нативний Android TV лаунчер у стилі Studio Ghibli.\n"
                        + "Розробник: Oktirus\n\n"
                        + "Твій теплий домашній екран долини:\n"
                        + "• 8 живописних краєвидів Ghibli з паралаксом і живими частинками\n"
                        + "• 6 духів навігації (Сусуватарі, Полум’я Кальцифера, Сяйво Кодама, Вітер долини, Кристал Лапути, Паперові птахи)\n"
                        + "• Улюблені застосунки з вільними 2D cel-іконками у стилі Studio Ghibli та власним порядком\n"
                        + "• Мінігра «Політ крізь долину» з Сусуватарі, Кодама та Тоторо\n"
                        + "• Оригінальні саундтреки Joe Hisaishi та натуральні звуки духів\n\n"
                        + "Права на твори Studio Ghibli належать їхнім правовласникам. Некомерційний фанатський лаунчер.")
                .setPositiveButton("Зрозуміло", null)
                .show();
    }

    private View lastContextMenuOriginView;

    private boolean isContextMenuOpen() {
        return contextMenuContainer != null && contextMenuContainer.getVisibility() == View.VISIBLE;
    }

    public void showContextMenu(AppItem app, boolean isFromDock, int dockPosition, View originView) {
        if (!activityResumed || isDestroyed() || isFinishing()) return;
        this.lastContextMenuOriginView = originView;
        mainShell.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        if (activePanel != null && modalContainer != null) {
            modalContainer.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        }
        contextMenuContainer.removeAllViews();
        List<AppItem> dockApps = appManager.getDockApps();
        AppContextMenuPanel panel = new AppContextMenuPanel(this, app, isFromDock,
                dockPosition, dockApps.size(), new AppContextMenuPanel.OnContextMenuActionListener() {
            @Override
            public void onLaunch(AppItem a) {
                closeContextMenu();
                launchApp(a);
            }

            @Override
            public void onTogglePin(AppItem a) {
                lastContextMenuOriginView = null;
                closeContextMenu();
                boolean nowPinned = appManager.togglePin(a.getPackageName());
                if (dockView != null) dockView.render();
                wireSpatialNavigation();
                if (libraryPanel != null) libraryPanel.renderApps();
                if (orderPanel != null) orderPanel.renderList();
                audioController.playUiSound();
                String message = "☆ Прибрано з улюбленого";
                if (nowPinned) {
                    boolean visibleOnHome = dockView.findCardByPackage(a.getPackageName()) != null;
                    message = visibleOnHome ? "★ Додано до улюбленого й показано на головному екрані"
                            : "★ Додано до улюбленого · поза головним екраном";
                }
                toastOverlay.show(message, 2500);
                if (activePanel == libraryPanel && libraryPanel != null) {
                    View newCard = libraryPanel.findCardByPackage(a.getPackageName());
                    if (newCard != null) newCard.post(newCard::requestFocus);
                } else if (dockView != null) {
                    View newCard = dockView.findCardByPackage(a.getPackageName());
                    if (newCard != null) newCard.post(newCard::requestFocus);
                    else restoreHomeFocus();
                } else {
                    restoreHomeFocus();
                }
            }

            @Override
            public void onMove(AppItem a, int direction) {
                lastContextMenuOriginView = null;
                closeContextMenu();
                appManager.moveFavorite(a.getPackageName(), direction);
                if (dockView != null) dockView.render();
                wireSpatialNavigation();
                if (orderPanel != null) orderPanel.renderList();
                audioController.playUiSound();
                if (dockView != null) {
                    View newCard = dockView.findCardByPackage(a.getPackageName());
                    if (newCard != null) {
                        newCard.post(newCard::requestFocus);
                    }
                }
            }

            @Override
            public void onOpenOrder() {
                lastContextMenuOriginView = null;
                closeContextMenu();
                openOrder();
            }

            @Override
            public void onAppInfo(AppItem a) {
                closeContextMenu();
                openAppInfo(a);
            }

            @Override
            public void onDismiss() {
                closeContextMenu();
            }
        });

        contextMenuContainer.addView(panel);
        contextMenuContainer.setVisibility(View.VISIBLE);
        audioController.playUiSound();
    }

    public void closeContextMenu() {
        closeContextMenu(true);
    }

    private void closeContextMenu(boolean restoreFocus) {
        if (isContextMenuOpen()) {
            contextMenuContainer.removeAllViews();
            contextMenuContainer.setVisibility(View.GONE);
            if (modalContainer != null) {
                modalContainer.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (activePanel == null) {
                mainShell.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
            if (restoreFocus && lastContextMenuOriginView != null && lastContextMenuOriginView.isAttachedToWindow()) {
                lastContextMenuOriginView.requestFocus();
            } else if (restoreFocus && activePanel == null) {
                restoreHomeFocus();
            }
            lastContextMenuOriginView = null;
        }
    }

    public void openAppInfo(AppItem app) {
        if (app == null) return;
        try {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + app.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не вдалося відкрити налаштування", Toast.LENGTH_SHORT).show();
        }
    }

    private void enterAmbient() {
        if (activePanel == null) rememberHomeFocus();
        closeActivePanel();
        spiritFocusView.setTargetView(null);
        ambientOverlay.start();
        mainShell.setVisibility(View.INVISIBLE);
        updateResourceDisplay();
        mainShell.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        backgroundView.setDecorPaused(true);
    }

    private void exitAmbient() {
        ambientOverlay.stop();
        mainShell.setVisibility(View.VISIBLE);
        updateResourceDisplay();
        mainShell.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        backgroundView.setDecorPaused(false);
        resetIdleTimer();
        restoreHomeFocus();
        updateController.onHomeAvailable();
    }

    private void setupTimers() {
        mainHandler.removeCallbacks(clockRunnable);
        mainHandler.post(clockRunnable);
        setupRotationTimer();
    }

    private void setupRotationTimer() {
        if (rotationRunnable != null) mainHandler.removeCallbacks(rotationRunnable);
        int rotSec = prefs.getRotationSeconds();
        if (rotSec > 0) {
            rotationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (activePanel == null && ambientOverlay.getVisibility() != View.VISIBLE) {
                        int currentIndex = 0;
                        for (int i = 0; i < CatalogData.SCENES.size(); i++) {
                            if (CatalogData.SCENES.get(i).getId().equals(prefs.getScene())) {
                                currentIndex = i;
                                break;
                            }
                        }
                        int nextIndex = (currentIndex + 1) % CatalogData.SCENES.size();
                        applyScene(CatalogData.SCENES.get(nextIndex).getId(), true);
                    }
                    mainHandler.postDelayed(this, rotSec * 1000L);
                }
            };
            mainHandler.postDelayed(rotationRunnable, rotSec * 1000L);
        }
    }

    private void resetIdleTimer() {
        if (idleRunnable != null) mainHandler.removeCallbacks(idleRunnable);
        int idleSec = prefs.getIdleSeconds();
        if (idleSec > 0) {
            idleRunnable = () -> {
                if (activePanel == null && ambientOverlay.getVisibility() != View.VISIBLE
                        && !homeScreenSetup.isPromptShowing() && !updateController.isShowing()) {
                    enterAmbient();
                } else {
                    resetIdleTimer();
                }
            };
            mainHandler.postDelayed(idleRunnable, idleSec * 1000L);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        resetIdleTimer();

        int keyCode = event.getKeyCode();
        boolean back = keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE;
        boolean menu = keyCode == KeyEvent.KEYCODE_MENU;
        if (event.getAction() == KeyEvent.ACTION_DOWN && (back || menu) && event.getRepeatCount() > 0) {
            return true;
        }

        if (isContextMenuOpen()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && (back || menu)) {
                closeContextMenu();
                return true;
            }
            return super.dispatchKeyEvent(event);
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (back) {
                if (audioController.cancelPendingLaunch()) {
                    toastOverlay.hide();
                    updateController.onHomeAvailable();
                    return true;
                }
                if (ambientOverlay.getVisibility() == View.VISIBLE) {
                    exitAmbient();
                    return true;
                }
                if (activePanel != null) {
                    if (activePanel != settingsPanel || !settingsPanel.goBack()) closeActivePanel();
                    return true;
                }
                View firstCard = dockView != null ? dockView.getFirstCard() : null;
                if (firstCard != null && rootLayout.findFocus() != firstCard) {
                    firstCard.requestFocus();
                }
                return true;
            }

            if (menu) {
                if (ambientOverlay.getVisibility() == View.VISIBLE) {
                    exitAmbient();
                    return true;
                }
                if (activePanel instanceof ValleyRunnerView) {
                    return runnerGameView.onKeyDown(keyCode, event);
                }
                if (settingsPanel != null && activePanel == settingsPanel) {
                    closeActivePanel();
                } else {
                    openSettings();
                }
                return true;
            }
        }

        if (event.getAction() != KeyEvent.ACTION_DOWN || !activityResumed || activePanel != null
                || ambientOverlay.getVisibility() == View.VISIBLE) {
            return super.dispatchKeyEvent(event);
        }
        final int direction;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: direction = View.FOCUS_LEFT; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: direction = View.FOCUS_RIGHT; break;
            case KeyEvent.KEYCODE_DPAD_UP: direction = View.FOCUS_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: direction = View.FOCUS_DOWN; break;
            default: return super.dispatchKeyEvent(event);
        }

        // Home intents, layout and inventory refresh can all change focus without navigation input.
        View before = rootLayout.findFocus();
        boolean handled = super.dispatchKeyEvent(event);
        View after = rootLayout.findFocus();
        if (!handled && before != null && after == before) {
            View next = before.focusSearch(direction);
            if (next != before && isInside(next, dockView) && next.requestFocus(direction)) {
                after = next;
                handled = true;
            }
        }
        if (after != before && isInside(after, dockView)) {
            focusFirstAppOnHomeEntry = false;
            audioController.playFocusSound();
        }
        return handled;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean redirected = HomeRedirectService.ACTION_REDIRECTED_HOME.equals(intent.getAction());
        if (redirected) homeScreenSetup.onRedirectedHome();
        if (redirected
                || (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME))) {
            updateController.cancel();
            focusFirstAppOnHomeEntry = true;
            audioController.cancelPendingLaunch();
            audioController.stopTechniquePreview();
            toastOverlay.hide();
            if (ambientOverlay.getVisibility() == View.VISIBLE) exitAmbient();
            closeActivePanel();
            restoreHomeFocus();
            updateController.onHomeAvailable();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) TVKeyHelper.cancelPendingPress(getWindow().getDecorView().findFocus());
        if (hasFocus && homeScreenSetup != null) {
            homeScreenSetup.onWindowFocus();
            updateHomeSettingsStatus();
        }
        if (hasFocus && activityResumed && activePanel == null && !updateController.isShowing()
                && (ambientOverlay == null || ambientOverlay.getVisibility() != View.VISIBLE)) {
            restoreHomeFocus();
            homeScreenSetup.showFirstRunPrompt();
            updateController.onHomeAvailable();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        homeScreenSetup.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (homeScreenSetup != null) homeScreenSetup.onActivityResult(requestCode);
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        updateResourceDisplay();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        );
        appManager.reloadAppsAsync(() -> {
            if (!activityResumed || isDestroyed()) return;
            appInventoryReady = true;
            dockView.render();
            wireSpatialNavigation();
            if (activePanel == null && ambientOverlay.getVisibility() != View.VISIBLE) {
                restoreHomeFocus();
            } else if (activePanel == libraryPanel && libraryPanel != null) {
                libraryPanel.renderGrid();
            }
        });
        dockView.render();
        if (activePanel == null && ambientOverlay.getVisibility() != View.VISIBLE) {
            restoreHomeFocus();
        }
        if (ambientOverlay != null) ambientOverlay.resume();
        backgroundView.resume();
        backgroundView.setDecorPaused(activePanel != null || ambientOverlay.getVisibility() == View.VISIBLE);
        backgroundView.setMotionEnabled(prefs.isMotionEnabled());
        if (spiritFocusView != null) {
            spiritFocusView.setMotionEnabled(prefs.isMotionEnabled());
            spiritFocusView.resume();
        }
        if (libraryPanel != null) libraryPanel.resume();
        if (activePanel instanceof ValleyRunnerView) {
            ((ValleyRunnerView) activePanel).onResumeLifecycle();
        } else if (activePanel == scenesPanel && scenesPanel != null) {
            scenesPanel.renderScenes();
        } else if (activePanel == spiritsPanel && spiritsPanel != null) {
            spiritsPanel.renderSpirits();
        }
        audioController.resumeMusic();
        homeScreenSetup.onResume();
        updateHomeSettingsStatus();
        updateController.onResume();
        updateController.onHomeAvailable();
        mainHandler.removeCallbacks(updateCheckRunnable);
        mainHandler.postDelayed(updateCheckRunnable, 3000);
        setupTimers();
        resetIdleTimer();
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        updateResourceDisplay();
        TVKeyHelper.cancelPendingPress(getWindow().getDecorView().findFocus());
        closeContextMenu(false);
        mainHandler.removeCallbacks(updateCheckRunnable);
        updateController.cancel();
        audioController.cancelPendingLaunch();
        toastOverlay.hide();
        audioController.pauseMusic();
        backgroundView.pause();
        if (spiritFocusView != null) spiritFocusView.pause();
        if (libraryPanel != null) libraryPanel.pause();
        if (runnerGameView != null) runnerGameView.onPauseLifecycle();
        if (ambientOverlay != null) ambientOverlay.pause();
        if (rotationRunnable != null) mainHandler.removeCallbacks(rotationRunnable);
        if (idleRunnable != null) mainHandler.removeCallbacks(idleRunnable);
        mainHandler.removeCallbacks(clockRunnable);
        super.onPause();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            if (spiritsPanel != null) spiritsPanel.clearCache();
            if (scenesPanel != null) scenesPanel.clearCache();
            if (spiritRenderer != null) spiritRenderer.clearCache();
            if (activityResumed) {
                if (activePanel == scenesPanel && scenesPanel != null) scenesPanel.renderScenes();
                if (activePanel == spiritsPanel && spiritsPanel != null) spiritsPanel.renderSpirits();
                if (libraryPanel != null) libraryPanel.resume();
                if (spiritFocusView != null) spiritFocusView.invalidate();
            }
        }
    }

    @Override
    protected void onDestroy() {
        closeContextMenu(false);
        if (resources != null) resources.close();
        mainHandler.removeCallbacksAndMessages(null);
        if (focusChangeListener != null && rootLayout != null && rootLayout.getViewTreeObserver().isAlive()) {
            rootLayout.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChangeListener);
            rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(homeEntryLayoutListener);
        }
        if (runnerGameView != null) runnerGameView.release();
        if (spiritsPanel != null) spiritsPanel.release();
        if (scenesPanel != null) scenesPanel.release();
        if (libraryPanel != null) libraryPanel.close();
        if (dockView != null) dockView.release();
        if (backgroundView != null) backgroundView.release();
        if (ambientOverlay != null) ambientOverlay.pause();
        if (spiritRenderer != null) spiritRenderer.release();
        appManager.release();
        if (homeScreenSetup != null) homeScreenSetup.close();
        if (updateController != null) updateController.close();
        audioController.release();
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()
        );
    }
}

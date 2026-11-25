package ua.ghibli.tv.launcher.panels;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import ua.ghibli.tv.launcher.R;
import ua.ghibli.tv.launcher.audio.AudioController;
import ua.ghibli.tv.launcher.model.CatalogData;
import ua.ghibli.tv.launcher.model.HighlightSound;
import ua.ghibli.tv.launcher.model.MusicTrack;
import ua.ghibli.tv.launcher.model.Technique;
import ua.ghibli.tv.launcher.prefs.PreferencesManager;
import ua.ghibli.tv.launcher.theme.GhibliTheme;
import ua.ghibli.tv.launcher.views.GhibliSeekBar;
import ua.ghibli.tv.launcher.views.GhibliSwitch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Studio Ghibli Settings panel ("Налаштування").
 * Warm cedar right side sheet with honey-gold switches, sliders, dropdowns, and TV navigation.
 */
public class SettingsPanel extends FrameLayout {
    public interface OnSettingsActionListener {
        void onSpiritsRequested();
        void onAmbientRequested();
        void onSystemSettingsRequested(String target);
        void onHomeScreenRequested();
        void onHomeRedirectRequested();
        void onUpdatesRequested();
        void onRefreshAppsRequested();
        void onAboutRequested();
        void onCloseRequested();
    }

    private final PreferencesManager prefs;
    private final AudioController audioController;
    private final SideSheetContainer sheetContainer;
    private final ScrollView scrollView;
    private final LinearLayout content;

    private TextView spiritValueText;
    private Button spiritPreviewButton;
    private TextView homeScreenValueText;
    private TextView homeRedirectValueText;
    private GhibliSwitch motionSwitch;
    private GhibliSwitch audioSwitch;
    private GhibliSwitch musicSwitch;
    private GhibliSeekBar fxVolumeBar;
    private TextView fxVolumeText;
    private GhibliSeekBar musicVolumeBar;
    private TextView musicVolumeText;

    private OnSettingsActionListener listener;

    public SettingsPanel(Context context, PreferencesManager prefs, AudioController audioController) {
        super(context);
        this.prefs = prefs;
        this.audioController = audioController;

        sheetContainer = new SideSheetContainer(context, false, "Долина Ghibli", "Налаштування");
        sheetContainer.setOnCloseListener(() -> {
            if (listener != null) listener.onCloseRequested();
        });

        scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);

        buildSettingsRows(context);

        scrollView.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        sheetContainer.setBodyView(scrollView);
        addView(sheetContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setOnSettingsActionListener(OnSettingsActionListener listener) {
        this.listener = listener;
    }

    private void buildSettingsRows(Context context) {
        // 1. Spirit focus effect picker link
        View spiritRow = createNavigationLink("Духи навігації", getTechniqueName(prefs.getTechnique()), v -> {
            if (listener != null) listener.onSpiritsRequested();
        });
        spiritValueText = spiritRow.findViewById(android.R.id.text2);
        content.addView(spiritRow);

        // 2. Focus sound selector
        Spinner soundSpinner = createStyledSpinner(context);
        setupHighlightSoundSpinner(soundSpinner);
        content.addView(createSpinnerRow("Звук виділення", "Один спільний звук фокусу для всіх застосунків.", soundSpinner));

        // 3. Motion Switch
        motionSwitch = new GhibliSwitch(context);
        content.addView(createSwitchRow("Живий світ", "Листя, пелюстки, паралакс та рухомі духи.",
                motionSwitch, prefs.isMotionEnabled(), (sw, checked) -> {
                    prefs.setMotionEnabled(checked);
                    audioController.playUiSound();
                }));

        // 4. Audio Effects Switch
        audioSwitch = new GhibliSwitch(context);
        content.addView(createSwitchRow("Звуки духів", "Дзвіночки, полум’я, шепіт лісу й звуки мінігри.",
                audioSwitch, prefs.isAudioEnabled(), (sw, checked) -> {
                    prefs.setAudioEnabled(checked);
                    if (!checked) audioController.stopTechniquePreview();
                    audioController.updateMusic();
                    audioController.playUiSound();
                }));

        // 5. Ambient Music Switch
        musicSwitch = new GhibliSwitch(context);
        content.addView(createSwitchRow("Музика долини", "Оригінальні мелодії з аніме Studio Ghibli (Тоторо, Привиди, Замок Хаула).",
                musicSwitch, prefs.isAmbienceEnabled(), (sw, checked) -> {
                    prefs.setAmbienceEnabled(checked);
                    audioController.updateMusic();
                }));

        // 6. FX Volume
        int currentFx = (int) (prefs.getVolume() * 100);
        fxVolumeBar = new GhibliSeekBar(context);
        View fxVolRow = createSliderRow("Гучність ефектів", "Окремий баланс для цього лаунчера.",
                fxVolumeBar, currentFx, (sb, val) -> {
                    prefs.setVolume(val / 100f);
                    if (fxVolumeText != null) fxVolumeText.setText(val + "%");
                });
        fxVolumeText = fxVolRow.findViewById(android.R.id.text2);
        content.addView(fxVolRow);

        // 7. Music Track selector
        Spinner musicSpinner = createStyledSpinner(context);
        setupMusicSpinner(musicSpinner);
        content.addView(createSpinnerRow("Мелодія фону", "Саундтреки Joe Hisaishi з фільмів Studio Ghibli.", musicSpinner));

        // 8. Music Volume
        int currentMusicVol = (int) (prefs.getMusicVolume() * 100);
        musicVolumeBar = new GhibliSeekBar(context);
        View musicVolRow = createSliderRow("Гучність музики", "Окремо від звуків навігації.",
                musicVolumeBar, currentMusicVol, (sb, val) -> {
                    prefs.setMusicVolume(val / 100f);
                    if (musicVolumeText != null) musicVolumeText.setText(val + "%");
                    audioController.updateMusic();
                });
        musicVolumeText = musicVolRow.findViewById(android.R.id.text2);
        content.addView(musicVolRow);

        // 9. Audio preview buttons
        content.addView(createActionButton("Прослухати звук виділення", v -> {
            audioSwitch.setChecked(true);
            audioController.stopTechniquePreview();
            audioController.playFocusSound();
        }));
        spiritPreviewButton = createActionButton("Перевірити духа: " + getTechniqueName(prefs.getTechnique()), v -> {
            audioSwitch.setChecked(true);
            audioController.playTechniquePreview(prefs.getTechnique());
        });
        content.addView(spiritPreviewButton);

        // 10. Rotation & Idle Spinners
        Spinner rotSpinner = createStyledSpinner(context);
        setupRotationSpinner(rotSpinner);
        content.addView(createSpinnerRow("Зміна сцен", "Автоматична ротація 8 краєвидів.", rotSpinner));

        Spinner idleSpinner = createStyledSpinner(context);
        setupIdleSpinner(idleSpinner);
        content.addView(createSpinnerRow("Режим спокою", "Автоматичний перехід у тиху мить.", idleSpinner));

        // 11. System & Utility Links
        View homeRow = createNavigationLink("Домашній екран", "Зробити основним", v -> {
            if (listener != null) listener.onHomeScreenRequested();
        });
        homeScreenValueText = homeRow.findViewById(android.R.id.text2);
        content.addView(homeRow);

        View redirectRow = createNavigationLink(context.getString(R.string.home_redirect_title),
                context.getString(R.string.home_redirect_disabled), v -> {
                    if (listener != null) listener.onHomeRedirectRequested();
                });
        homeRedirectValueText = redirectRow.findViewById(android.R.id.text2);
        content.addView(redirectRow);

        String installedVersion = "Перевірити версію";
        try {
            installedVersion = "v" + context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException error) {
            android.util.Log.w("GhibliSettings", "Installed version unavailable", error);
        }
        content.addView(createNavigationLink("Оновлення Ghibli", installedVersion, v -> {
            if (listener != null) listener.onUpdatesRequested();
        }));
        content.addView(createNavigationLink("Увійти в режим спокою", "Тиха мить", v -> {
            if (listener != null) listener.onAmbientRequested();
        }));
        content.addView(createNavigationLink("Налаштування Android TV", "Система", v -> {
            if (listener != null) listener.onSystemSettingsRequested("system");
        }));
        content.addView(createNavigationLink("Налаштування мережі та Wi-Fi", "Wi-Fi", v -> {
            if (listener != null) listener.onSystemSettingsRequested("wifi");
        }));
        content.addView(createNavigationLink("Оновити список застосунків", "Пересканувати", v -> {
            if (listener != null) listener.onRefreshAppsRequested();
        }));
        content.addView(createNavigationLink("Про Ghibli та ресурси", "Довідка", v -> {
            if (listener != null) listener.onAboutRequested();
        }));

        // Footer note
        TextView note = new TextView(context);
        note.setText("Усі параметри зберігаються автоматично на пристрої. Керування оптимізовано під стандартний пульт D-pad.");
        note.setTextColor(GhibliTheme.COLOR_MUTED);
        note.setTextSize(11);
        note.setTypeface(GhibliTheme.getNunito(context));
        note.setPadding(0, GhibliTheme.dp(context, 20), 0, GhibliTheme.dp(context, 32));
        content.addView(note);
    }

    private View createSwitchRow(String title, String subtitle, GhibliSwitch sw, boolean initial,
                                 GhibliSwitch.OnCheckedChangeListener changeListener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, GhibliTheme.dp(getContext(), 10), 0, GhibliTheme.dp(getContext(), 10));

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title);
        tvTitle.setTextColor(GhibliTheme.COLOR_PAPER);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(getContext());
        tvSub.setText(subtitle);
        tvSub.setTextColor(GhibliTheme.COLOR_MUTED);
        tvSub.setTextSize(11);
        tvSub.setTypeface(GhibliTheme.getNunito(getContext()));
        textCol.addView(tvSub);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        sw.setChecked(initial);
        sw.setOnCheckedChangeListener(changeListener);
        row.addView(sw);
        highlightControlRow(row, sw);

        return row;
    }

    private View createSliderRow(String title, String subtitle, GhibliSeekBar sb, int initial,
                                 GhibliSeekBar.OnProgressChangeListener changeListener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, GhibliTheme.dp(getContext(), 10), 0, GhibliTheme.dp(getContext(), 10));

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title);
        tvTitle.setTextColor(GhibliTheme.COLOR_PAPER);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        textCol.addView(tvTitle);

        TextView tvSub = new TextView(getContext());
        tvSub.setText(subtitle);
        tvSub.setTextColor(GhibliTheme.COLOR_MUTED);
        tvSub.setTextSize(11);
        tvSub.setTypeface(GhibliTheme.getNunito(getContext()));
        textCol.addView(tvSub);

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout volumeControl = new LinearLayout(getContext());
        volumeControl.setOrientation(LinearLayout.HORIZONTAL);
        volumeControl.setGravity(Gravity.CENTER_VERTICAL);

        sb.setMax(100);
        sb.setProgress(initial);
        sb.setOnProgressChangeListener(changeListener);
        volumeControl.addView(sb, new LinearLayout.LayoutParams(
                GhibliTheme.dp(getContext(), 125), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView valView = new TextView(getContext());
        valView.setId(android.R.id.text2);
        valView.setText(initial + "%");
        valView.setTextColor(GhibliTheme.COLOR_ACCENT);
        valView.setTextSize(12);
        valView.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        valView.setPadding(GhibliTheme.dp(getContext(), 9), 0, 0, 0);
        valView.setMinWidth(GhibliTheme.dp(getContext(), 36));
        volumeControl.addView(valView);

        row.addView(volumeControl);
        highlightControlRow(row, sb);
        return row;
    }

    private View createNavigationLink(String title, String val, View.OnClickListener clickListener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setFocusableInTouchMode(true);
        row.setClickable(true);
        row.setPadding(GhibliTheme.dp(getContext(), 12), GhibliTheme.dp(getContext(), 12),
                GhibliTheme.dp(getContext(), 12), GhibliTheme.dp(getContext(), 12));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(GhibliTheme.COLOR_CARD_BG);
        gd.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        gd.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#44e8b858"));
        row.setBackground(gd);

        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextColor(GhibliTheme.COLOR_PAPER);
        tv.setTextSize(14);
        tv.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView tvVal = new TextView(getContext());
        tvVal.setId(android.R.id.text2);
        tvVal.setText(val);
        tvVal.setTextColor(GhibliTheme.COLOR_GOLD);
        tvVal.setTextSize(12);
        tvVal.setTypeface(GhibliTheme.getNunito(getContext()));
        row.addView(tvVal);

        row.setOnClickListener(clickListener);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? GhibliTheme.COLOR_BUTTON_FOCUS : GhibliTheme.COLOR_CARD_BG);
            d.setCornerRadius(GhibliTheme.dp(getContext(), 10));
            d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#44e8b858"));
            v.setBackground(d);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, GhibliTheme.dp(getContext(), 6), 0, GhibliTheme.dp(getContext(), 6));
        row.setLayoutParams(lp);

        return row;
    }

    private Button createActionButton(String text, View.OnClickListener clickListener) {
        Button btn = new Button(getContext());
        btn.setText(text);
        btn.setTextColor(GhibliTheme.COLOR_PAPER);
        btn.setTextSize(13);
        btn.setAllCaps(false);
        btn.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        btn.setFocusable(true);
        btn.setFocusableInTouchMode(true);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(GhibliTheme.COLOR_BUTTON_BG);
        gd.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        gd.setStroke(GhibliTheme.dp(getContext(), 1), Color.parseColor("#55e8b858"));
        btn.setBackground(gd);

        btn.setOnClickListener(clickListener);
        btn.setOnFocusChangeListener((v, hasFocus) -> {
            GradientDrawable d = new GradientDrawable();
            d.setColor(hasFocus ? GhibliTheme.COLOR_BUTTON_FOCUS : GhibliTheme.COLOR_BUTTON_BG);
            d.setCornerRadius(GhibliTheme.dp(getContext(), 10));
            d.setStroke(GhibliTheme.dp(getContext(), hasFocus ? 2 : 1),
                    hasFocus ? GhibliTheme.COLOR_ACCENT : Color.parseColor("#55e8b858"));
            v.setBackground(d);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, GhibliTheme.dp(getContext(), 42)
        );
        lp.setMargins(0, GhibliTheme.dp(getContext(), 6), 0, GhibliTheme.dp(getContext(), 6));
        btn.setLayoutParams(lp);

        return btn;
    }

    private View createSpinnerRow(String title, String subtitle, Spinner spinner) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, GhibliTheme.dp(getContext(), 8), 0, GhibliTheme.dp(getContext(), 8));

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(getContext());
        tvTitle.setText(title);
        tvTitle.setTextColor(GhibliTheme.COLOR_PAPER);
        tvTitle.setTextSize(14);
        tvTitle.setTypeface(GhibliTheme.getNunitoBold(getContext()));
        textCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = new TextView(getContext());
            tvSub.setText(subtitle);
            tvSub.setTextColor(GhibliTheme.COLOR_MUTED);
            tvSub.setTextSize(11);
            tvSub.setTypeface(GhibliTheme.getNunito(getContext()));
            textCol.addView(tvSub);
        }

        row.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        row.addView(spinner);
        highlightControlRow(row, spinner);

        return row;
    }

    private Spinner createStyledSpinner(Context context) {
        Spinner spinner = new Spinner(context, Spinner.MODE_DROPDOWN);
        spinner.setFocusable(true);
        spinner.setFocusableInTouchMode(true);
        spinner.setBackground(focusBackground(GhibliTheme.COLOR_PANEL_END, Color.parseColor("#77e8b858")));
        spinner.setPopupBackgroundDrawable(new ColorDrawable(GhibliTheme.COLOR_PANEL_END));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                GhibliTheme.dp(context, 196), GhibliTheme.dp(context, 38)
        );
        spinner.setLayoutParams(lp);
        return spinner;
    }

    private ArrayAdapter<String> createThemedAdapter(Context context, List<String> items) {
        return new ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(GhibliTheme.COLOR_PAPER);
                    tv.setTextSize(12);
                    tv.setTypeface(GhibliTheme.getNunito(getContext()));
                    tv.setPadding(GhibliTheme.dp(getContext(), 10), 0, GhibliTheme.dp(getContext(), 10), 0);
                    tv.setSingleLine(true);
                    tv.setEllipsize(TextUtils.TruncateAt.END);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    tv.setTextColor(GhibliTheme.COLOR_PAPER);
                    tv.setTextSize(12);
                    tv.setTypeface(GhibliTheme.getNunito(getContext()));
                    tv.setBackground(stateBackground(GhibliTheme.COLOR_PANEL_END, Color.TRANSPARENT,
                            new int[]{android.R.attr.state_focused, android.R.attr.state_selected,
                                    android.R.attr.state_pressed}));
                    tv.setSingleLine(false);
                    tv.setMinHeight(GhibliTheme.dp(getContext(), 44));
                    tv.setPadding(GhibliTheme.dp(getContext(), 12), GhibliTheme.dp(getContext(), 10),
                            GhibliTheme.dp(getContext(), 12), GhibliTheme.dp(getContext(), 10));
                }
                return v;
            }
        };
    }

    private void highlightControlRow(ViewGroup row, View control) {
        int inset = GhibliTheme.dp(getContext(), 10);
        row.setPadding(inset, row.getPaddingTop(), inset, row.getPaddingBottom());
        row.setBackground(focusBackground(Color.TRANSPARENT, Color.TRANSPARENT));
        control.setOnFocusChangeListener((view, focused) -> {
            row.setActivated(focused);
            view.invalidate();
            if (focused && row.getWidth() > 0 && row.getHeight() > 0) {
                row.requestRectangleOnScreen(new android.graphics.Rect(0, 0, row.getWidth(), row.getHeight()), true);
            }
        });
    }

    private StateListDrawable focusBackground(int normalColor, int normalBorderColor) {
        return stateBackground(normalColor, normalBorderColor,
                new int[]{android.R.attr.state_focused, android.R.attr.state_selected,
                        android.R.attr.state_activated, android.R.attr.state_pressed});
    }

    private StateListDrawable stateBackground(int normalColor, int normalBorderColor, int[] highlightedStates) {
        StateListDrawable states = new StateListDrawable();
        for (int state : highlightedStates) {
            states.addState(new int[]{state}, controlBackground(GhibliTheme.COLOR_BUTTON_FOCUS,
                    GhibliTheme.COLOR_ACCENT, 2));
        }
        states.addState(new int[]{}, controlBackground(normalColor, normalBorderColor, 1));
        return states;
    }

    private GradientDrawable controlBackground(int fill, int stroke, int widthDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(GhibliTheme.dp(getContext(), 10));
        background.setStroke(GhibliTheme.dp(getContext(), widthDp), stroke);
        return background;
    }

    private void setupHighlightSoundSpinner(Spinner spinner) {
        List<String> names = new ArrayList<>();
        int selectedIndex = 0;
        String cur = prefs.getHighlightSound();
        for (int i = 0; i < CatalogData.HIGHLIGHT_SOUNDS.size(); i++) {
            HighlightSound hs = CatalogData.HIGHLIGHT_SOUNDS.get(i);
            names.add(hs.getName());
            if (hs.getId().equals(cur)) selectedIndex = i;
        }

        spinner.setAdapter(createThemedAdapter(getContext(), names));
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.setHighlightSound(CatalogData.HIGHLIGHT_SOUNDS.get(pos).getId());
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupMusicSpinner(Spinner spinner) {
        List<String> names = new ArrayList<>();
        int selectedIndex = 0;
        String cur = prefs.getMusicTrack();
        for (int i = 0; i < CatalogData.MUSIC_TRACKS.size(); i++) {
            MusicTrack mt = CatalogData.MUSIC_TRACKS.get(i);
            names.add(mt.getName());
            if (mt.getId().equals(cur)) selectedIndex = i;
        }

        spinner.setAdapter(createThemedAdapter(getContext(), names));
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.setMusicTrack(CatalogData.MUSIC_TRACKS.get(pos).getId());
                audioController.updateMusic();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupRotationSpinner(Spinner spinner) {
        String[] options = {"Вручну", "Кожні 60 секунд", "Кожні 3 хвилини", "Кожні 10 хвилин"};
        int[] seconds = {0, 60, 180, 600};
        int selectedIndex = 0;
        int cur = prefs.getRotationSeconds();
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == cur) selectedIndex = i;
        }

        spinner.setAdapter(createThemedAdapter(getContext(), Arrays.asList(options)));
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.setRotationSeconds(seconds[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupIdleSpinner(Spinner spinner) {
        String[] options = {"Вимкнено", "Через 5 хвилин", "Через 15 хвилин"};
        int[] seconds = {0, 300, 900};
        int selectedIndex = 0;
        int cur = prefs.getIdleSeconds();
        for (int i = 0; i < seconds.length; i++) {
            if (seconds[i] == cur) selectedIndex = i;
        }

        spinner.setAdapter(createThemedAdapter(getContext(), Arrays.asList(options)));
        spinner.setSelection(selectedIndex);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                prefs.setIdleSeconds(seconds[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private String getTechniqueName(String id) {
        for (Technique t : CatalogData.TECHNIQUES) {
            if (t.getId().equals(id)) return t.getName();
        }
        return "Сусуватарі";
    }

    public void updateState() {
        String spiritName = getTechniqueName(prefs.getTechnique());
        if (spiritValueText != null) {
            spiritValueText.setText(spiritName);
        }
        if (spiritPreviewButton != null) {
            spiritPreviewButton.setText("Перевірити духа: " + spiritName);
        }
        if (motionSwitch != null) {
            motionSwitch.setChecked(prefs.isMotionEnabled());
        }
        if (audioSwitch != null) {
            audioSwitch.setChecked(prefs.isAudioEnabled());
        }
        if (musicSwitch != null) {
            musicSwitch.setChecked(prefs.isAmbienceEnabled());
        }
        if (fxVolumeBar != null) {
            fxVolumeBar.setProgress((int) (prefs.getVolume() * 100));
        }
        if (fxVolumeText != null) {
            fxVolumeText.setText(((int) (prefs.getVolume() * 100)) + "%");
        }
        if (musicVolumeBar != null) {
            musicVolumeBar.setProgress((int) (prefs.getMusicVolume() * 100));
        }
        if (musicVolumeText != null) {
            musicVolumeText.setText(((int) (prefs.getMusicVolume() * 100)) + "%");
        }
    }

    public void updateTechnique(String name) {
        if (spiritValueText != null) spiritValueText.setText(name);
        if (spiritPreviewButton != null) spiritPreviewButton.setText("Перевірити духа: " + name);
    }

    public void setHomeScreenActive(boolean active) {
        homeScreenValueText.setText(active ? "Ghibli · вибрано" : "Зробити основним");
    }

    public void setHomeRedirectEnabled(boolean enabled) {
        homeRedirectValueText.setText(enabled ? R.string.home_redirect_enabled : R.string.home_redirect_disabled);
    }

    public void focusInitial() {
        post(() -> {
            if (content != null && content.getChildCount() > 0) {
                View first = content.getChildAt(0);
                if (first != null) {
                    first.requestFocusFromTouch();
                    first.requestFocus();
                }
            }
        });
    }
}

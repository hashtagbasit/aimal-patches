package app.aimal.extension.subtitles;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dedicated, scrollable subtitle settings with a persistent live sample. */
public final class SubtitlePanel {
    private static final ExecutorService downloads = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Dialog dialog;
    private final LinearLayout content;
    private final CaptionView preview;
    private final TextView status;
    private TextView selectedFont;
    private SubtitleStyle style;
    private List<String> fontNames;
    private String fontSearch = "";
    private int page;
    private boolean binding;
    private final boolean tv;

    public static void show(Context context) {
        try { new SubtitlePanel(context, null).dialog.show(); }
        catch (Throwable error) { android.util.Log.e("AimalSubtitles", "Cannot open settings", error); }
    }

    public static Dialog showTv(Context context, Runnable aspect) {
        try {
            Dialog dialog = new SubtitlePanel(context, aspect).dialog;
            dialog.show();
            return dialog;
        } catch (Throwable error) {
            android.util.Log.e("AimalSubtitles", "Cannot open TV settings", error);
            return null;
        }
    }

    private SubtitlePanel(Context context, Runnable aspect) {
        this.context = context;
        tv = aspect != null;
        SubtitleFonts.init(context);
        style = SubtitleSettings.style(context);
        fontNames = SubtitleFonts.installed();
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout shell = column();
        shell.setPadding(dp(16), dp(8), dp(16), dp(8));
        shell.setBackgroundColor(0xFF171A20);
        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Customize subtitles", 20);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (tv) header.addView(button("FIT / STRETCH", aspect));
        Button close = button("Close", () -> dialog.dismiss());
        if (tv) close.setFocusableInTouchMode(true);
        header.addView(close);
        shell.addView(header);
        preview = new CaptionView(context);
        preview.setText("This is how your subtitles will look.\nA second sample line.");
        GradientDrawable scene = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF334B65, 0xFF59776D, 0xFF121820});
        scene.setCornerRadius(dp(8));
        preview.setBackground(scene);
        preview.setContentDescription("Subtitle preview, updated in real time");
        preview.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        shell.addView(preview, new LinearLayout.LayoutParams(-1, Math.max(dp(90), Math.min(dp(170), screenHeight / 4))));
        SubtitleSettings.watch(preview);
        status = label("Changes are saved and applied immediately.", 12);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        shell.addView(status);
        if (tv) {
            Button diagnostic = button("Playback diagnostics", () -> {
                new android.app.AlertDialog.Builder(context).setTitle("Subtitle diagnostics")
                    .setMessage(app.aimal.extension.crunchyroll.TvPlaybackSubtitles.diagnostics())
                    .setPositiveButton("Close", null).show();
            });
            shell.addView(diagnostic);
            focusOutline(diagnostic);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        content = column();
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(shell);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE |
                    (tv ? WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN : 0));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.35f);
            dialog.setOnShowListener(ignored -> {
                window.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(900)),
                    WindowManager.LayoutParams.MATCH_PARENT);
                if (tv) close.requestFocus();
            });
        }
        build();
        if (tv) focusOutline(header);
    }

    private void focusOutline(View view) {
        if (view.isFocusable()) {
            android.graphics.drawable.StateListDrawable background = new android.graphics.drawable.StateListDrawable();
            GradientDrawable focused = new GradientDrawable();
            focused.setColor(0xFF334D50); focused.setCornerRadius(dp(6));
            focused.setStroke(dp(2), 0xFF5ED6D1);
            background.addState(new int[]{android.R.attr.state_focused}, focused);
            if (view.getBackground() != null) background.addState(new int[]{}, view.getBackground());
            view.setBackground(background);
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) focusOutline(group.getChildAt(i));
        }
    }

    private void build() {
        binding = true;
        style = SubtitleSettings.style(context);
        content.removeAllViews();
        toggle("Use custom styling", style.enabled, value -> style.enabled = value);
        section("Typography");
        selectedFont = label("Current font: " + style.fontFamily, 14);
        content.addView(selectedFont);
        int fontStart = content.getChildCount();
        fonts();
        LinearLayout fontControls = column();
        while (content.getChildCount() > fontStart) {
            View child = content.getChildAt(fontStart);
            content.removeViewAt(fontStart);
            fontControls.addView(child);
        }
        fontControls.setVisibility(View.GONE);
        content.addView(button("Choose or import font", () -> fontControls.setVisibility(
                fontControls.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE)));
        content.addView(fontControls);
        number("Size", 8, 200, 1, style.fontSize, "px", value -> style.fontSize = value);
        number("Weight", 100, 900, 10, style.fontWeight, "", value -> style.fontWeight = Math.round(value));
        number("Line height", .5f, 3, .01f, style.lineHeight, "×", value -> style.lineHeight = value);
        number("Letter spacing", -5, 10, .1f, style.letterSpacing, "px", value -> style.letterSpacing = value);
        color("Text color", style.textColor, value -> style.textColor = value);
        toggle("Text shadow", style.textShadow, value -> style.textShadow = value);
        section("Position and width");
        number("Bottom offset", 0, 100, 1, style.bottomOffset, "%", value -> style.bottomOffset = value);
        final LinearLayout widthControls = column();
        toggle("Limit maximum width", style.useCustomMaxWidth, value -> {
            style.useCustomMaxWidth = value;
            widthControls.setVisibility(value ? View.VISIBLE : View.GONE);
        });
        content.addView(widthControls);
        numberInto(widthControls, "Maximum width", 10, 100, 1, style.maxWidth, "%", value -> style.maxWidth = value);
        widthControls.setVisibility(style.useCustomMaxWidth ? View.VISIBLE : View.GONE);
        section("Background");
        color("Background color", style.backgroundColor, value -> style.backgroundColor = value);
        number("Background opacity", 0, 100, 1, style.backgroundOpacity * 100, "%", value -> style.backgroundOpacity = value / 100);
        number("Horizontal padding", 0, 80, 1, style.paddingX, "px", value -> style.paddingX = value);
        number("Vertical padding", 0, 80, 1, style.paddingY, "px", value -> style.paddingY = value);
        number("Corner radius", 0, 80, 1, style.borderRadius, "px", value -> style.borderRadius = value);
        content.addView(button("Reset style", () -> {
            SubtitleSettings.reset(context);
            fontSearch = ""; page = 0;
            build();
        }));
        binding = false;
        if (tv) focusOutline(content);
        preview.invalidate();
    }

    private interface NumberChange { void set(float value); }
    private interface BooleanChange { void set(boolean value); }
    private interface ColorChange { void set(int value); }

    private void changed() {
        if (binding) return;
        SubtitleSettings.changed(context);
        if (selectedFont != null) selectedFont.setText("Current font: " + style.fontFamily);
        preview.invalidate();
        status.setText("Saved · preview and subtitles updated");
    }

    private void number(String title, float min, float max, float step, float value, String unit, NumberChange change) {
        numberInto(content, title, min, max, step, value, unit, change);
    }

    private void numberInto(LinearLayout parent, String title, float min, float max, float step, float value, String unit, NumberChange change) {
        TextView label = label(title + (unit.isEmpty() ? "" : " (" + unit + ")"), 14);
        parent.addView(label);
        LinearLayout row = new LinearLayout(context);
        EditText input = input(format(value, step));
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setContentDescription(title + ", exact value");
        label.setLabelFor(input.getId());
        SeekBar slider = new SeekBar(context);
        slider.setMax(Math.round((max - min) / step));
        slider.setProgress(Math.round((value - min) / step));
        slider.setContentDescription(title);
        row.addView(slider, new LinearLayout.LayoutParams(0, dp(48), 1));
        row.addView(input, new LinearLayout.LayoutParams(dp(86), dp(48)));
        parent.addView(row);
        final boolean[] syncing = {false};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || syncing[0]) return;
                float next = min + progress * step;
                syncing[0] = true; input.setText(format(next, step)); syncing[0] = false;
                change.set(next); changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        input.addTextChangedListener(watcher(text -> {
            if (syncing[0]) return;
            try {
                float next = Float.parseFloat(text.replace(',', '.'));
                if (Float.isNaN(next) || Float.isInfinite(next) || next < min || next > max) {
                    input.setError("Between " + format(min, step) + " and " + format(max, step)); return;
                }
                input.setError(null);
                syncing[0] = true; slider.setProgress(Math.round((next - min) / step)); syncing[0] = false;
                change.set(next); changed();
            } catch (NumberFormatException ignored) { if (!text.isEmpty()) input.setError("Enter a number"); }
        }));
    }

    private void color(String title, int value, ColorChange change) {
        TextView label = label(title + " (#RRGGBB)", 14);
        content.addView(label);
        EditText input = input(String.format(Locale.US, "#%06X", value & 0xFFFFFF));
        input.setContentDescription(title);
        label.setLabelFor(input.getId());
        content.addView(input);
        input.addTextChangedListener(watcher(text -> {
            if (!text.matches("#[0-9a-fA-F]{6}")) { input.setError("Use #RRGGBB, for example #FFFFFF"); return; }
            input.setError(null); change.set(Color.parseColor(text)); changed();
        }));
        LinearLayout swatches = new LinearLayout(context);
        for (String hex : new String[]{"#FFFFFF", "#000000", "#FFD54F", "#80DEEA", "#FFAB91"}) {
            Button swatch = button("●", () -> input.setText(hex));
            swatch.setTextColor(Color.parseColor(hex));
            swatch.setContentDescription(title + " " + hex);
            swatches.addView(swatch, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        content.addView(swatches);
    }

    private void toggle(String title, boolean value, BooleanChange change) {
        Switch control = new Switch(context);
        control.setText(title); control.setTextColor(Color.WHITE); control.setMinHeight(dp(48));
        control.setChecked(value);
        control.setOnCheckedChangeListener((button, checked) -> { change.set(checked); changed(); });
        content.addView(control);
    }

    private void fonts() {
        EditText search = input(fontSearch);
        search.setHint("Search installed or downloaded fonts");
        search.setContentDescription("Search fonts");
        content.addView(search);
        LinearLayout fontList = column();
        content.addView(fontList);
        Runnable render = () -> {
            List<String> filtered = new ArrayList<>();
            for (String name : fontNames) if (name.toLowerCase(Locale.ROOT).contains(fontSearch.toLowerCase(Locale.ROOT))) filtered.add(name);
            int pages = Math.max(1, (filtered.size() + 5) / 6);
            page = Math.max(0, Math.min(page, pages - 1));
            fontList.removeAllViews();
            for (int i = page * 6; i < Math.min(filtered.size(), (page + 1) * 6); i++) {
                String name = filtered.get(i);
                Button sample = button((name.equals(style.fontFamily) ? "✓ " : "") + name + " · Aa", () -> {
                    style.fontFamily = name; changed();
                    // Preserve search and scroll position while selecting a font.
                    for (int j = 0; j < fontList.getChildCount(); j++) {
                        View child = fontList.getChildAt(j);
                        if (child instanceof Button) {
                            Button b = (Button) child;
                            String family = (String) b.getTag();
                            b.setText((family.equals(name) ? "✓ " : "") + family + " · Aa");
                        }
                    }
                });
                sample.setTag(name); sample.setTypeface(SubtitleFonts.resolve(name));
                fontList.addView(sample);
            }
            if (filtered.isEmpty()) fontList.addView(label("No fonts found", 13));
        };
        search.addTextChangedListener(watcher(value -> { fontSearch = value; page = 0; render.run(); }));
        LinearLayout navigation = new LinearLayout(context);
        navigation.addView(button("Previous", () -> { page = Math.max(0, page - 1); render.run(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(button("Next", () -> { page++; render.run(); }), new LinearLayout.LayoutParams(0, dp(48), 1));
        content.addView(navigation); render.run();
        EditText family = input(style.fontFamily);
        family.setContentDescription("Font family name or path to a TTF or OTF font");
        content.addView(label("Font family or local TTF/OTF path", 14)); content.addView(family);
        content.addView(button("Use this font", () -> {
            String next = family.getText().toString().trim();
            if (next.isEmpty()) { family.setError("Enter a font family or path"); return; }
            if (next.startsWith("/") && !new java.io.File(next).isFile()) { family.setError("Cannot read that file"); return; }
            style.fontFamily = next; changed();
        }));
        EditText url = input(style.fontCssUrl);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://…/fonts.css"); url.setContentDescription("HTTPS font CSS URL");
        content.addView(label("Import fonts from CSS", 14)); content.addView(url);
        Button load = button("Load CSS", () -> loadFonts(url.getText().toString().trim()));
        content.addView(load);
        content.addView(button("Load GothamPro from Noir", () -> { url.setText(SubtitleFonts.DEFAULT_CSS); loadFonts(SubtitleFonts.DEFAULT_CSS); }));
        content.addView(label("CSS with TTF/OTF fonts. Fonts are cached for offline use. WOFF/WOFF2 must be converted to TTF/OTF.", 12));
    }

    private boolean loading;
    private void loadFonts(String url) {
        if (loading) return;
        loading = true;
        status.setText("Downloading fonts…");
        Context app = context.getApplicationContext();
        downloads.execute(() -> {
            String message;
            List<String> loaded = null;
            try { loaded = SubtitleFonts.loadCss(app, url); message = "Fonts ready"; }
            catch (Exception error) { message = error.getMessage(); }
            final List<String> result = loaded;
            final String feedback = message;
            new Handler(Looper.getMainLooper()).post(() -> {
                loading = false;
                if (!dialog.isShowing()) return;
                if (result != null) {
                    style.fontCssUrl = url; style.fontFamily = result.get(0);
                    fontNames = SubtitleFonts.installed(); changed(); build();
                }
                status.setText(feedback);
            });
        });
    }

    private interface TextChange { void set(String text); }
    private static TextWatcher watcher(TextChange change) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { change.set(s.toString()); }
            @Override public void afterTextChanged(Editable e) { }
        };
    }
    private void section(String text) { TextView view = label(text, 17); view.setPadding(0, dp(16), 0, dp(8)); content.addView(view); }
    private TextView label(String text, int size) { TextView v = new TextView(context); v.setText(text); v.setTextSize(size); v.setTextColor(0xFFE6E9EF); return v; }
    private EditText input(String value) {
        EditText v = new EditText(context); v.setId(View.generateViewId()); v.setSingleLine(true);
        v.setTextColor(Color.WHITE); v.setHintTextColor(0xFFB0B8C6); v.setText(value); v.setMinHeight(dp(48)); return v;
    }
    private Button button(String title, Runnable action) { Button b = new Button(context); b.setText(title); b.setAllCaps(false); b.setMinHeight(dp(48)); b.setOnClickListener(v -> action.run()); return b; }
    private LinearLayout column() { LinearLayout v = new LinearLayout(context); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static String format(float value, float step) { return step >= 1 ? String.format(Locale.US, "%.0f", value) : String.format(Locale.US, step >= .1f ? "%.1f" : "%.2f", value); }
}

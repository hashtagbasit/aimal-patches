package app.aimal.extension.subtitles;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleFonts {
    public static final String DEFAULT_CSS = "https://raw.githubusercontent.com/neura-neura/noir-player/54eb8d176475186580259686050b8f4bec141aeb/public/vendor/gotham-pro-font/fonts.min.css";
    private static final Map<String, Typeface> cache = new HashMap<>();
    private static final Object imports = new Object();
    private static Context app;
    private SubtitleFonts() { }

    private static final java.util.Set<String> migrations = new java.util.HashSet<>();
    public static synchronized void init(Context context) {
        app = context.getApplicationContext();
        SubtitleStyle style = SubtitleSettings.style(app);
        File directory = new File(app.getFilesDir(), "aimal-fonts");
        String name = style.fontFamily;
        String address = style.fontCssUrl;
        if (address.isEmpty() && name.equals("GothamPro")) address = DEFAULT_CSS;
        if (!address.isEmpty() && new File(directory, fileName(name)).isFile()
                && !new File(directory, fileName(name) + ".weights-v2").isFile() && migrations.add(name)) {
            final String source = address;
            final Context contextApp = app;
            new Thread(() -> {
                try { loadCss(contextApp, source); SubtitleSettings.changed(contextApp); }
                catch (Exception ignored) { /* Keep the old face available offline. */ }
            }, "Aimal-font-upgrade").start();
        }
    }

    public static Typeface resolve(String name) { return resolve(name, 400); }
    public static synchronized Typeface resolve(String name, int weight) {
        String key = name + "#" + weight;
        Typeface result = cache.get(key);
        if (result != null) return result;
        try {
            File directory = app == null ? null : new File(app.getFilesDir(), "aimal-fonts");
            List<Integer> weights = new ArrayList<>();
            String[] files = directory == null ? null : directory.list();
            String prefix = fileName(name) + "-w";
            if (files != null) for (String file : files) if (file.startsWith(prefix)) {
                try { int w = Integer.parseInt(file.substring(prefix.length())); if (w >= 100 && w <= 900) weights.add(w); }
                catch (NumberFormatException ignored) { }
            }
            if (!weights.isEmpty()) {
                int selected = weights.get(0), best = Integer.MAX_VALUE;
                for (int w : weights) {
                    // CSS Fonts matching: 400..500 prefer faces through 500,
                    // then lighter faces, then heavier; others search in one direction first.
                    int rank = weight >= 400 && weight <= 500
                        ? (w >= weight && w <= 500 ? w - weight : w < weight ? 1000 + weight - w : 2000 + w - weight)
                        : weight < 400 ? (w <= weight ? weight - w : 1000 + w - weight)
                        : (w >= weight ? w - weight : 1000 + weight - w);
                    if (rank < best) { best = rank; selected = w; }
                }
                String faceKey = name + "#face" + selected;
                result = cache.get(faceKey);
                if (result == null) {
                    File face = new File(directory, fileName(name) + "-w" + selected);
                    // Some SFNT files label every variant as 400 internally.
                    // Use the CSS style metadata while keeping that face's real outlines.
                    result = android.os.Build.VERSION.SDK_INT >= 26
                        ? new Typeface.Builder(face).setWeight(selected).setItalic(false).build()
                        : Typeface.createFromFile(face);
                    cache.put(faceKey, result);
                }
            } else {
                File saved = directory == null ? null : new File(directory, fileName(name));
                Typeface family = saved != null && saved.isFile() ? Typeface.createFromFile(saved)
                    : name.startsWith("/") ? Typeface.createFromFile(name) : Typeface.create(name, Typeface.NORMAL);
                result = android.os.Build.VERSION.SDK_INT >= 28 ? Typeface.create(family, weight, false)
                    : Typeface.create(family, weight >= 600 ? Typeface.BOLD : Typeface.NORMAL);
            }
        } catch (Exception ignored) { result = Typeface.SANS_SERIF; }
        cache.put(key, result);
        return result;
    }

    public static List<String> installed() {
        TreeSet<String> names = new TreeSet<>();
        Collections.addAll(names, "sans-serif", "sans-serif-condensed", "serif", "monospace");
        try (InputStream input = new FileInputStream("/system/etc/fonts.xml")) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG && (parser.getName().equals("family") || parser.getName().equals("alias"))) {
                    String name = parser.getAttributeValue(null, "name");
                    if (name != null) names.add(name);
                }
            }
        } catch (Exception ignored) { }
        if (app != null) {
            String[] saved = new File(app.getFilesDir(), "aimal-fonts").list();
            if (saved != null) for (String file : saved) {
                if (file.endsWith(".name")) try (InputStream in = new FileInputStream(new File(new File(app.getFilesDir(), "aimal-fonts"), file))) {
                    names.add(new String(read(in, 4096), StandardCharsets.UTF_8));
                } catch (Exception ignored) { }
            }
        }
        return new ArrayList<>(names);
    }

    /** Downloads SFNT fonts, which Android supports natively, off the UI thread. */
    public static List<String> loadCss(Context context, String address) throws Exception {
        synchronized (imports) { return importCss(context, address); }
    }
    private static List<String> importCss(Context context, String address) throws Exception {
        synchronized (SubtitleFonts.class) { app = context.getApplicationContext(); }
        URL cssUrl = new URL(address);
        String css = new String(download(cssUrl, 1024 * 1024), StandardCharsets.UTF_8);
        Matcher faces = Pattern.compile("@font-face\\s*\\{([^}]+)\\}", Pattern.CASE_INSENSITIVE).matcher(css);
        List<String> faceBlocks = new ArrayList<>();
        while (faces.find()) faceBlocks.add(faces.group(1));
        // Import every upright weight; italic faces are not part of the style UI.
        faceBlocks.sort(java.util.Comparator.comparingInt(face ->
                face.matches("(?is).*font-weight\\s*:\\s*400.*") && !face.matches("(?is).*font-style\\s*:\\s*italic.*") ? 0 : 1));
        List<String> names = new ArrayList<>();
        File directory = new File(context.getFilesDir(), "aimal-fonts");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new Exception("Could not create the font cache");
        for (String face : faceBlocks) {
            if (face.matches("(?is).*font-style\\s*:\\s*(italic|oblique).*")) continue;
            if (names.size() >= 64) break;
            Matcher family = Pattern.compile("font-family\\s*:\\s*['\"]?([^;'\"}]+)", Pattern.CASE_INSENSITIVE).matcher(face);
            if (!family.find()) continue;
            String name = family.group(1).trim();
            Matcher weightMatch = Pattern.compile("font-weight\\s*:\\s*([0-9]+|normal|bold)", Pattern.CASE_INSENSITIVE).matcher(face);
            String declared = weightMatch.find() ? weightMatch.group(1) : "400";
            int weight = declared.equalsIgnoreCase("bold") ? 700 : declared.equalsIgnoreCase("normal") ? 400 : Integer.parseInt(declared);
            if (weight < 100 || weight > 900) continue;
            Matcher urls = Pattern.compile("url\\(\\s*['\"]?([^)'\"\\s]+)", Pattern.CASE_INSENSITIVE).matcher(face);
            while (urls.find()) {
                String path = urls.group(1);
                // WOFF/WOFF2/EOT cannot be passed to Android Typeface.
                if (!path.matches("(?i).*\\.(ttf|otf)([?#].*)?")) continue;
                byte[] bytes;
                try { bytes = download(new URL(cssUrl, path), 16 * 1024 * 1024); }
                catch (Exception unavailable) { continue; }
                File temporary = File.createTempFile("font-", ".tmp", directory);
                try {
                    try (FileOutputStream out = new FileOutputStream(temporary)) { out.write(bytes); }
                    Typeface loaded = Typeface.createFromFile(temporary);
                    File target = new File(directory, fileName(name) + "-w" + weight);
                    if (!temporary.renameTo(target)) throw new Exception("Could not save the font");
                    try (FileOutputStream out = new FileOutputStream(new File(directory, fileName(name) + ".name"))) {
                        out.write(name.getBytes(StandardCharsets.UTF_8));
                    }
                    synchronized (SubtitleFonts.class) { cache.clear(); }
                    if (!names.contains(name)) names.add(name);
                } finally { temporary.delete(); }
                break;
            }
        }
        if (names.isEmpty()) throw new Exception("CSS must include TTF or OTF fonts; Android does not support WOFF/WOFF2");
        for (String name : names) new File(directory, fileName(name) + ".weights-v2").createNewFile();
        return names;
    }

    private static String fileName(String name) { return "font-" + Integer.toHexString(name.hashCode()); }
    private static byte[] download(URL url, int limit) throws Exception {
        if (!url.getProtocol().equals("https")) throw new Exception("The font URL must use HTTPS");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10000); connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new Exception("HTTP error " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream()) { return read(in, limit); }
        } finally { connection.disconnect(); }
    }
    private static byte[] read(InputStream in, int limit) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) != -1) {
            if (out.size() + count > limit) throw new Exception("The font exceeds the size limit");
            out.write(buffer, 0, count);
        }
        return out.toByteArray();
    }
}

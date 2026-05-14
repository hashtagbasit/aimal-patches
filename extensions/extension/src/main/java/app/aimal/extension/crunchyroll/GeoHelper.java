package app.aimal.extension.crunchyroll;

import java.util.Locale;

public final class GeoHelper {

    private static final String SPOOFED_COUNTRY = "US";
    private static final Locale SPOOFED_LOCALE = Locale.US;

    public static String getCountryCode(String original) {
        return SPOOFED_COUNTRY;
    }

    public static Locale getLocale(Locale original) {
        return SPOOFED_LOCALE;
    }
}

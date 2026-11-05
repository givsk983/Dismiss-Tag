package io.github.appprivacyprofile;

import android.content.SharedPreferences;

import java.time.ZoneId;
import java.security.SecureRandom;
import java.util.IllformedLocaleException;
import java.util.Locale;
import java.util.regex.Pattern;

final class Profile {
    private static final Pattern PACKAGE_NAME =
            Pattern.compile("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");

    final boolean enabled;
    final boolean maskSim;
    final CarrierProfile carrier;
    final String language;
    final String timeZone;
    final boolean maskFingerprint;
    final boolean maskUsage;
    final String deviceId;

    Profile(boolean enabled, boolean maskSim, String country, String language, String timeZone,
            boolean maskFingerprint, boolean maskUsage, String deviceId) {
        this.enabled = enabled;
        this.maskSim = maskSim;
        this.carrier = CarrierProfile.forCountry(country);
        this.language = normalizeLanguage(language);
        this.timeZone = normalizeTimeZone(timeZone);
        this.maskFingerprint = maskFingerprint;
        this.maskUsage = maskUsage;
        this.deviceId = deviceId.matches("[0-9a-f]{16}") ? deviceId : "";
    }

    static boolean isTargetPackage(String name) {
        return name != null && name.length() <= 220 && PACKAGE_NAME.matcher(name).matches()
                && !BuildConfig.APPLICATION_ID.equals(name);
    }

    static String preferencesName(String packageName) {
        return "profile_" + packageName;
    }

    static Profile read(SharedPreferences preferences) {
        return new Profile(preferences.getBoolean("enabled", false),
                preferences.getBoolean("mask_sim", false), preferences.getString("country", ""),
                preferences.getString("language", ""), preferences.getString("time_zone", ""),
                preferences.getBoolean("mask_fingerprint", false), preferences.getBoolean("mask_usage", false),
                preferences.getString("device_id", ""));
    }

    boolean save(SharedPreferences preferences) {
        return preferences.edit().putBoolean("enabled", enabled).putBoolean("mask_sim", maskSim)
                .putString("country", carrier.country).putString("language", language)
                .putString("time_zone", timeZone).putBoolean("mask_fingerprint", maskFingerprint)
                .putBoolean("mask_usage", maskUsage).putString("device_id", deviceId).commit();
    }

    boolean applies() {
        return enabled && (maskSim || !language.isEmpty() || !timeZone.isEmpty()
                || maskFingerprint || maskUsage);
    }

    static String newDeviceId() {
        return String.format(Locale.ROOT, "%016x", new SecureRandom().nextLong());
    }

    private static String normalizeLanguage(String language) {
        String tag = language.trim().replace('_', '-');
        if (tag.isEmpty()) {
            return "";
        }
        try {
            Locale locale = new Locale.Builder().setLanguageTag(tag).build();
            if (!locale.getLanguage().isEmpty()) {
                return locale.toLanguageTag();
            }
        } catch (IllformedLocaleException ignored) {
            // Display the same message for malformed and language-free tags.
        }
        throw new IllegalArgumentException("请选择有效的语言与地区。");
    }

    private static String normalizeTimeZone(String timeZone) {
        String id = timeZone.trim();
        if (!id.isEmpty() && !ZoneId.getAvailableZoneIds().contains(id)) {
            throw new IllegalArgumentException("请选择有效的时区。");
        }
        return id;
    }
}

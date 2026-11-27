package com.hooman.socialblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Surface;

import java.util.HashSet;
import java.util.Set;

/**
 * چرخش خودکار صفحه فقط برای اپ‌های انتخابی (پیش‌فرض: اپ‌های مسیریابی).
 * با ورود به آن اپ چرخش روشن می‌شود و با خروج، به حالت قبلی (و عمودی) برمی‌گردد.
 * نیاز به مجوز «تغییر تنظیمات سیستم» (WRITE_SETTINGS) دارد.
 */
final class AutoRotate {
    private AutoRotate() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_rotate", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }

    static void setEnabled(Context c, boolean on) {
        sp(c).edit().putBoolean("enabled", on).apply();
        if (!on) restore(c);
    }

    static boolean canWrite(Context c) { return Settings.System.canWrite(c); }

    /** اپ‌های انتخابی؛ بار اول = اپ‌های مسیریابی */
    static Set<String> getApps(Context c) {
        if (!sp(c).getBoolean("init", false)) return new HashSet<>(SmartPause.NAV_APPS);
        return new HashSet<>(sp(c).getStringSet("apps", new HashSet<>()));
    }

    static void setApps(Context c, Set<String> apps) {
        sp(c).edit().putBoolean("init", true).putStringSet("apps", new HashSet<>(apps)).apply();
    }

    private static final Set<String> NEUTRAL = new HashSet<>(java.util.Arrays.asList(
            "com.android.systemui", "android"));

    /** از سرویس دسترسی‌پذیری با هر تغییر اپ جلو صفحه */
    static void onForeground(Context c, String pkg) {
        try {
            if (pkg == null || NEUTRAL.contains(pkg) || pkg.equals(c.getPackageName())) return;
            if (!isEnabled(c) || !canWrite(c)) return;
            if (getApps(c).contains(pkg)) turnOn(c);
            else restore(c);
        } catch (Throwable ignored) {}
    }

    private static void turnOn(Context c) {
        int cur = Settings.System.getInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
        if (cur == 1) return; // قبلاً روشن بوده (یا خودمان روشن کرده‌ایم)
        sp(c).edit().putBoolean("we_turned_on", true).apply();
        Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
    }

    /** فقط اگر خودمان روشن کرده بودیم خاموش کن و صفحه را عمودی کن */
    static void restore(Context c) {
        try {
            if (!sp(c).getBoolean("we_turned_on", false) || !canWrite(c)) return;
            sp(c).edit().putBoolean("we_turned_on", false).apply();
            Settings.System.putInt(c.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
            Settings.System.putInt(c.getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_0);
        } catch (Throwable ignored) {}
    }
}

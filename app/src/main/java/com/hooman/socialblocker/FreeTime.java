package com.hooman.socialblocker;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * «زمان آزاد» (Pro): روزهای انتخابی هفته، در یک بازه ساعت، اپ‌ها و سایت‌های مسدود آزاد می‌شوند.
 * مثال: جمعه و شنبه ۱۰:۰۰ تا ۲۲:۰۰. بازه شبانه (مثلاً ۲۲:۰۰ تا ۰۲:۰۰) هم پشتیبانی می‌شود.
 */
final class FreeTime {
    private FreeTime() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_freetime", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }

    /** بیت (Calendar.DAY_OF_WEEK - 1) برای هر روز. پیش‌فرض: شنبه و یکشنبه */
    static int getDays(Context c) {
        return sp(c).getInt("days", (1 << (Calendar.SATURDAY - 1)) | (1 << (Calendar.SUNDAY - 1)));
    }

    static int getStartMin(Context c) { return sp(c).getInt("start", 10 * 60); }

    static int getEndMin(Context c) { return sp(c).getInt("end", 22 * 60); }

    static void save(Context c, boolean enabled, int days, int start, int end) {
        sp(c).edit().putBoolean("enabled", enabled).putInt("days", days)
                .putInt("start", start).putInt("end", end).apply();
    }

    static boolean hasDay(int days, int calendarDay) {
        return (days & (1 << (calendarDay - 1))) != 0;
    }

    /** آیا الان زمان آزاد است؟ */
    static boolean isNow(Context c) {
        if (!isEnabled(c) || !Pro.isPro(c)) return false;
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        int day = cal.get(Calendar.DAY_OF_WEEK);
        int yesterday = day == Calendar.SUNDAY ? Calendar.SATURDAY : day - 1;
        int days = getDays(c), start = getStartMin(c), end = getEndMin(c);
        if (start == end) return hasDay(days, day); // تمام روز
        if (start < end) return hasDay(days, day) && now >= start && now < end;
        // بازه از نیمه‌شب می‌گذرد: بخش اول امروز، بخش دوم ادامه دیروز
        return (hasDay(days, day) && now >= start) || (hasDay(days, yesterday) && now < end);
    }

    static String fmt(int min) {
        return String.format(java.util.Locale.US, "%02d:%02d", min / 60, min % 60);
    }
}

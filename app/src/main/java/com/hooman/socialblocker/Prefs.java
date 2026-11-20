package com.hooman.socialblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** همه تنظیمات برنامه + هش کردن رمز (PBKDF2 با salt). */
public final class Prefs {
    private Prefs() {}

    private static final String NAME = "sb_prefs";
    private static final String K_BLOCKED = "blocked";
    private static final String K_BLOCKING = "blocking";
    private static final String K_PROTECT = "protect";
    private static final String K_PAUSE = "pause_until";
    private static final String K_SALT = "pw_salt";
    private static final String K_HASH = "pw_hash";
    private static final String K_LANG = "lang";

    /** اپ‌های سوشال که به‌صورت پیش‌فرض تیک می‌خورند. */
    static final String[] DEFAULT_SOCIAL = {
            "com.instagram.android",          // Instagram
            "com.instagram.barcelona",        // Threads
            "com.instagram.lite",
            "com.facebook.katana",            // Facebook
            "com.facebook.lite",
            "com.facebook.orca",              // Messenger
            "com.zhiliaoapp.musically",       // TikTok
            "com.ss.android.ugc.trill",       // TikTok (بعضی کشورها)
            "com.twitter.android",            // X / Twitter
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.pinterest",
            "com.linkedin.android",
            "com.google.android.youtube",
            "com.tumblr",
            "tv.twitch.android.app",
            "com.vkontakte.android",
            "com.bereal.ft",
    };

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    static void initDefaults(Context c) {
        if (!sp(c).contains(K_BLOCKED)) {
            sp(c).edit()
                    .putStringSet(K_BLOCKED, new HashSet<>(Arrays.asList(DEFAULT_SOCIAL)))
                    .putBoolean(K_PROTECT, true)
                    .apply();
        }
    }

    // ---------- لیست اپ‌ها ----------
    static Set<String> getBlocked(Context c) {
        return new HashSet<>(sp(c).getStringSet(K_BLOCKED, new HashSet<>()));
    }

    /** بدون کپی کردن لیست (برای رویدادهای پرتکرار سرویس) */
    static boolean isBlocked(Context c, String pkg) {
        Set<String> s = sp(c).getStringSet(K_BLOCKED, null);
        return s != null && s.contains(pkg);
    }

    static boolean isFwBlocked(Context c, String pkg) {
        Set<String> s = sp(c).getStringSet("fw_blocked", null);
        return s != null && s.contains(pkg);
    }

    static void setBlocked(Context c, Set<String> s) {
        sp(c).edit().putStringSet(K_BLOCKED, new HashSet<>(s)).apply();
    }

    // ---------- وضعیت ----------
    static boolean isBlocking(Context c) { return sp(c).getBoolean(K_BLOCKING, false); }

    static void setBlocking(Context c, boolean on) {
        sp(c).edit().putBoolean(K_BLOCKING, on).putLong(K_PAUSE, 0).apply();
    }

    static boolean isProtect(Context c) { return sp(c).getBoolean(K_PROTECT, true); }

    static void setProtect(Context c, boolean on) { sp(c).edit().putBoolean(K_PROTECT, on).apply(); }

    static long getPauseUntil(Context c) { return sp(c).getLong(K_PAUSE, 0); }

    static void setPauseUntil(Context c, long t) { sp(c).edit().putLong(K_PAUSE, t).apply(); }

    static boolean isPaused(Context c) { return System.currentTimeMillis() < getPauseUntil(c); }

    /** مسدودسازی واقعاً الان اعمال می‌شود؟ */
    static boolean isActive(Context c) { return isBlocking(c) && !isPaused(c); }

    // ---------- فایروال ----------
    static boolean isFwEnabled(Context c) { return sp(c).getBoolean("fw_enabled", false); }

    static void setFwEnabled(Context c, boolean on) { sp(c).edit().putBoolean("fw_enabled", on).commit(); }

    static Set<String> getFwBlocked(Context c) {
        return new HashSet<>(sp(c).getStringSet("fw_blocked", new HashSet<>()));
    }

    static void setFwBlocked(Context c, Set<String> s) {
        sp(c).edit().putStringSet("fw_blocked", new HashSet<>(s)).commit();
    }

    // ---------- برنامه زمانی اینترنت ----------
    /** واحدها: 0=ثانیه 1=دقیقه 2=ساعت 3=روز */
    static final long[] UNIT_MS = {1000L, 60_000L, 3_600_000L, 86_400_000L};

    static boolean isSchedEnabled(Context c) { return sp(c).getBoolean("sched_enabled", false); }

    static int getSchedOnVal(Context c) { return sp(c).getInt("sched_on_val", 10); }
    static int getSchedOnUnit(Context c) { return sp(c).getInt("sched_on_unit", 0); }
    static int getSchedEveryVal(Context c) { return sp(c).getInt("sched_every_val", 1); }
    static int getSchedEveryUnit(Context c) { return sp(c).getInt("sched_every_unit", 1); }

    static long getSchedOnMs(Context c) { return getSchedOnVal(c) * UNIT_MS[getSchedOnUnit(c)]; }
    static long getSchedPeriodMs(Context c) { return getSchedEveryVal(c) * UNIT_MS[getSchedEveryUnit(c)]; }
    static long getSchedAnchor(Context c) { return sp(c).getLong("sched_anchor", 0); }

    /** زمان‌بندی فقط وقتی صفحه خاموش است اعمال شود (پیش‌فرض: بله) */
    static boolean isSchedScreenOffOnly(Context c) { return sp(c).getBoolean("sched_screen_off_only", true); }

    /** ذخیره برنامه؛ شروع بازه‌ها از همین لحظه حساب می‌شود. */
    static void setSchedule(Context c, boolean enabled, int onVal, int onUnit, int everyVal, int everyUnit,
                            boolean screenOffOnly) {
        sp(c).edit()
                .putBoolean("sched_enabled", enabled)
                .putBoolean("sched_screen_off_only", screenOffOnly)
                .putInt("sched_on_val", onVal)
                .putInt("sched_on_unit", onUnit)
                .putInt("sched_every_val", everyVal)
                .putInt("sched_every_unit", everyUnit)
                .putLong("sched_anchor", System.currentTimeMillis())
                .commit();
    }

    // ---------- سهمیه روزانه اپ‌های مسدود ----------
    /** واحدهای سهمیه: 0=دقیقه 1=ساعت */
    static final long[] LIMIT_UNIT_MS = {60_000L, 3_600_000L};

    static boolean isLimitEnabled(Context c) { return sp(c).getBoolean("limit_enabled", false); }
    static int getLimitVal(Context c) { return sp(c).getInt("limit_val", 30); }
    static int getLimitUnit(Context c) { return sp(c).getInt("limit_unit", 0); }
    static long getLimitMs(Context c) { return getLimitVal(c) * LIMIT_UNIT_MS[getLimitUnit(c)]; }

    /** سهمیه‌ای که الان واقعاً اعمال می‌شود (0 = اصلاً اجازه نیست) */
    static long getAllowanceMs(Context c) { return isLimitEnabled(c) ? getLimitMs(c) : 0; }

    static void setLimit(Context c, boolean enabled, int val, int unit) {
        sp(c).edit()
                .putBoolean("limit_enabled", enabled)
                .putInt("limit_val", val)
                .putInt("limit_unit", unit)
                .commit();
    }

    private static String today() { return java.time.LocalDate.now().toString(); }

    /** زمان مصرف‌شده امروز (میلی‌ثانیه)؛ با شروع روز جدید صفر می‌شود. */
    static long getUsedToday(Context c) {
        if (!today().equals(sp(c).getString("usage_day", ""))) return 0;
        return sp(c).getLong("usage_ms", 0);
    }

    static void addUsage(Context c, long ms) {
        if (ms <= 0) return;
        long used = getUsedToday(c) + ms;
        sp(c).edit().putString("usage_day", today()).putLong("usage_ms", used).commit();
    }

    // ---------- مراقبت از چشم: یادآوری استراحت ----------
    static boolean isEyeEnabled(Context c) { return sp(c).getBoolean("eye_enabled", false); }
    static int getEyeIntervalMin(Context c) { return sp(c).getInt("eye_interval_min", 20); }
    static int getEyeBreakSec(Context c) { return sp(c).getInt("eye_break_sec", 20); }
    static boolean isEyeStrict(Context c) { return sp(c).getBoolean("eye_strict", false); }

    static void setEye(Context c, boolean enabled, int intervalMin, int breakSec, boolean strict) {
        sp(c).edit()
                .putBoolean("eye_enabled", enabled)
                .putInt("eye_interval_min", intervalMin)
                .putInt("eye_break_sec", breakSec)
                .putBoolean("eye_strict", strict)
                .commit();
    }

    // ---------- مراقبت از چشم: فیلتر گرم (نور آبی) ----------
    static boolean isFilterEnabled(Context c) { return sp(c).getBoolean("filter_enabled", false); }
    static int getFilterIntensity(Context c) { return sp(c).getInt("filter_intensity", 40); }
    static boolean isFilterNightOnly(Context c) { return sp(c).getBoolean("filter_night_only", false); }
    static int getFilterFrom(Context c) { return sp(c).getInt("filter_from", 21); }
    static int getFilterTo(Context c) { return sp(c).getInt("filter_to", 7); }

    static void setFilter(Context c, boolean enabled, int intensity, boolean nightOnly, int from, int to) {
        sp(c).edit()
                .putBoolean("filter_enabled", enabled)
                .putInt("filter_intensity", intensity)
                .putBoolean("filter_night_only", nightOnly)
                .putInt("filter_from", from)
                .putInt("filter_to", to)
                .commit();
    }

    // ---------- زبان ("" = سیستم، "en"، "fa") ----------
    static String getLanguage(Context c) { return sp(c).getString(K_LANG, ""); }

    static void setLanguage(Context c, String lang) { sp(c).edit().putString(K_LANG, lang).commit(); }

    // ---------- رمز ----------
    static boolean hasPassword(Context c) { return sp(c).contains(K_HASH); }

    static void setPassword(Context c, String pw) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        sp(c).edit()
                .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(K_HASH, hash(pw, salt))
                .apply();
    }

    static boolean checkPassword(Context c, String pw) {
        String saltStr = sp(c).getString(K_SALT, null);
        String stored = sp(c).getString(K_HASH, null);
        if (saltStr == null || stored == null) return false;
        String h = hash(pw, Base64.decode(saltStr, Base64.NO_WRAP));
        return MessageDigest.isEqual(h.getBytes(), stored.getBytes());
    }

    private static String hash(String pw, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pw.toCharArray(), salt, 20000, 256);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return Base64.encodeToString(f.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}

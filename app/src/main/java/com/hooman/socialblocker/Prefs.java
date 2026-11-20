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

    // ---------- قانون فایروال برای هر اپ ----------
    // ترتیب از آزاد به سخت‌گیرانه (برای تشخیص «شل کردن» که رمز می‌خواهد)
    static final int FW_FREE = 0, FW_PERIODIC = 1, FW_IN_USE = 2, FW_BURST = 3, FW_ALWAYS = 4;

    static Set<String> getFwBurst(Context c) {
        return new HashSet<>(sp(c).getStringSet("fw_burst", new HashSet<>()));
    }

    static Set<String> getFwPeriodic(Context c) {
        return new HashSet<>(sp(c).getStringSet("fw_periodic", new HashSet<>()));
    }

    static Set<String> getFwInUse(Context c) {
        return new HashSet<>(sp(c).getStringSet("fw_inuse", new HashSet<>()));
    }

    // ---------- قانون 📱 فقط هنگام استفاده ----------
    /** چند ثانیه بعد از بیرون آمدن از اپ هنوز اینترنت داشته باشد */
    static int getInUseGraceSec(Context c) { return sp(c).getInt("inuse_grace_sec", 20); }

    /** قانون 📱 برای همه اپ‌های بدون قانون هم اعمال شود */
    static boolean isInUseAll(Context c) { return sp(c).getBoolean("inuse_all", false); }

    static void setInUse(Context c, int graceSec, boolean all) {
        SharedPreferences.Editor e = sp(c).edit()
                .putInt("inuse_grace_sec", graceSec)
                .putBoolean("inuse_all", all);
        if (all) e.putBoolean("sched_enabled", false); // فقط یکی از «همه اپ‌ها» می‌تواند روشن باشد
        e.commit();
    }

    static int getFwRule(Context c, String pkg) {
        if (isFwBlocked(c, pkg)) return FW_ALWAYS;
        Set<String> b = sp(c).getStringSet("fw_burst", null);
        if (b != null && b.contains(pkg)) return FW_BURST;
        Set<String> u = sp(c).getStringSet("fw_inuse", null);
        if (u != null && u.contains(pkg)) return FW_IN_USE;
        Set<String> p = sp(c).getStringSet("fw_periodic", null);
        if (p != null && p.contains(pkg)) return FW_PERIODIC;
        return FW_FREE;
    }

    static void setFwRule(Context c, java.util.Collection<String> pkgs, int rule) {
        Set<String> always = getFwBlocked(c), burst = getFwBurst(c), periodic = getFwPeriodic(c),
                inUse = getFwInUse(c);
        always.removeAll(pkgs);
        burst.removeAll(pkgs);
        periodic.removeAll(pkgs);
        inUse.removeAll(pkgs);
        if (rule == FW_ALWAYS) always.addAll(pkgs);
        else if (rule == FW_BURST) burst.addAll(pkgs);
        else if (rule == FW_IN_USE) inUse.addAll(pkgs);
        else if (rule == FW_PERIODIC) periodic.addAll(pkgs);
        sp(c).edit()
                .putStringSet("fw_blocked", always)
                .putStringSet("fw_burst", burst)
                .putStringSet("fw_periodic", periodic)
                .putStringSet("fw_inuse", inUse)
                .commit();
    }

    static boolean isInstalled(Context c, String pkg) {
        try {
            c.getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** تعداد اپ‌های نصب‌شده از یک لیست (برای نمایش) */
    static int countInstalled(Context c, Set<String> pkgs) {
        int n = 0;
        for (String p : pkgs) if (isInstalled(c, p)) n++;
        return n;
    }

    /** اپ‌های پاک‌شده را از قانون‌های فایروال حذف می‌کند. @return true اگر چیزی حذف شد */
    static boolean pruneFwRules(Context c) {
        Set<String> gone = new HashSet<>();
        for (String p : getFwRuled(c)) if (!isInstalled(c, p)) gone.add(p);
        if (gone.isEmpty()) return false;
        setFwRule(c, gone, FW_FREE);
        return true;
    }

    /** همه اپ‌هایی که قانونی دارند (برای مرتب‌سازی لیست) */
    static Set<String> getFwRuled(Context c) {
        Set<String> s = getFwBlocked(c);
        s.addAll(getFwBurst(c));
        s.addAll(getFwPeriodic(c));
        s.addAll(getFwInUse(c));
        return s;
    }

    /** چند ثانیه اینترنت برای گروه «⚡ روشن/خاموش شدن صفحه» */
    static int getBurstSec(Context c) { return sp(c).getInt("fw_burst_sec", 10); }

    static void setBurstSec(Context c, int sec) { sp(c).edit().putInt("fw_burst_sec", sec).commit(); }

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

    /** رفتار قانون ⏱ وقتی صفحه روشن است */
    static final int SCREEN_ON_PERIODIC = 0, SCREEN_ON_FULL = 1, SCREEN_ON_IN_USE = 2;
    /** گزینه‌های «بعد از بیرون آمدن از اپ، تا چند ثانیه هنوز اینترنت داشته باشد» */
    static final int[] GRACE_OPTIONS = {0, 10, 20, 30, 60, 120, 300, 600};

    static int getScreenOnMode(Context c) {
        if (sp(c).contains("sched_screen_on_mode")) return sp(c).getInt("sched_screen_on_mode", SCREEN_ON_FULL);
        return isSchedScreenOffOnly(c) ? SCREEN_ON_FULL : SCREEN_ON_PERIODIC; // سازگاری با نسخه قبل
    }

    static int getGraceSec(Context c) { return sp(c).getInt("fw_grace_sec", 30); }

    /** ذخیره برنامه؛ شروع بازه‌ها از همین لحظه حساب می‌شود. */
    static void setSchedule(Context c, boolean enabled, int onVal, int onUnit, int everyVal, int everyUnit,
                            int screenOnMode, int graceSec) {
        sp(c).edit()
                .putBoolean("sched_enabled", enabled)
                .putBoolean("inuse_all", enabled ? false : isInUseAll(c))
                .putBoolean("sched_screen_off_only", screenOnMode != SCREEN_ON_PERIODIC)
                .putInt("sched_screen_on_mode", screenOnMode)
                .putInt("fw_grace_sec", graceSec)
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
    static boolean isEyeStrict(Context c) { return sp(c).getBoolean("eye_strict", true); }

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

    // ---------- خلاصه نوتیفیکیشن‌ها ----------
    /** فاصله نمایش خلاصه (ساعت) */
    static final int[] DIGEST_HOURS = {1, 2, 3, 4, 6, 12, 24};

    static boolean isDigestEnabled(Context c) { return sp(c).getBoolean("digest_enabled", false); }
    static int getDigestIntervalIdx(Context c) { return sp(c).getInt("digest_interval_idx", 2); }
    static long getDigestIntervalMs(Context c) {
        int i = Math.max(0, Math.min(DIGEST_HOURS.length - 1, getDigestIntervalIdx(c)));
        return DIGEST_HOURS[i] * 3_600_000L;
    }
    /** ساعتی از روز که همه چیز پاک می‌شود */
    static int getDigestClearHour(Context c) { return sp(c).getInt("digest_clear_hour", 23); }
    static long getDigestNext(Context c) { return sp(c).getLong("digest_next", 0); }
    static void setDigestNext(Context c, long t) { sp(c).edit().putLong("digest_next", t).commit(); }

    static void setDigest(Context c, boolean enabled, int intervalIdx, int clearHour) {
        sp(c).edit()
                .putBoolean("digest_enabled", enabled)
                .putInt("digest_interval_idx", intervalIdx)
                .putInt("digest_clear_hour", clearHour)
                .putLong("digest_next", 0)
                .commit();
    }

    /** اپ‌هایی که نوتیفیکیشنشان همیشه مستقیم نشان داده می‌شود */
    static Set<String> getDigestExceptions(Context c) {
        return new HashSet<>(sp(c).getStringSet("digest_exceptions", new HashSet<>()));
    }

    static void setDigestExceptions(Context c, Set<String> s) {
        sp(c).edit().putStringSet("digest_exceptions", new HashSet<>(s)).commit();
    }

    /** ذخیره خام نوتیفیکیشن‌های جمع‌شده (JSON) */
    static String getDigestJson(Context c) { return sp(c).getString("digest_items", "[]"); }
    static void setDigestJson(Context c, String json) { sp(c).edit().putString("digest_items", json).commit(); }

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

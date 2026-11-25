package com.hooman.socialblocker;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * فیلتر فرستنده (Pro): برای یک اپ (مثلاً تلگرام) فقط نوتیفیکیشن افراد/گروه‌های انتخابی نمایش داده می‌شود؛
 * بقیه از نوار اعلان حذف می‌شوند (و اگر خلاصه نوتیفیکیشن روشن باشد، به خلاصه می‌روند).
 * تماس‌ها و نوتیفیکیشن‌های دائمی دست نمی‌خورند.
 */
final class NotifFilter {
    private NotifFilter() {}

    /** پیام‌رسان‌های رایج (برای پیشنهاد اول در لیست) */
    static final String[] MESSENGERS = {
            "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus",
            "org.thunderdog.challegram", "com.whatsapp", "com.whatsapp.w4b", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.instagram.android", "com.viber.voip", "com.discord",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "ir.eitaa.messenger",
            "ir.nasim", "mobi.mmdt.ottplus"
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_nfilter", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }

    static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean("enabled", on).apply(); }

    /** اپ → نام‌های مجاز. ذخیره به صورت "pkg\tname" */
    static Map<String, Set<String>> getRules(Context c) {
        Map<String, Set<String>> m = new HashMap<>();
        for (String e : sp(c).getStringSet("rules", new HashSet<>())) {
            int i = e.indexOf('\t');
            if (i <= 0) continue;
            String pkg = e.substring(0, i);
            Set<String> s = m.get(pkg);
            if (s == null) m.put(pkg, s = new LinkedHashSet<>());
            if (i + 1 < e.length()) s.add(e.substring(i + 1));
        }
        return m;
    }

    static void setRule(Context c, String pkg, Set<String> names) {
        Set<String> all = new HashSet<>();
        for (String e : sp(c).getStringSet("rules", new HashSet<>())) {
            if (!e.startsWith(pkg + "\t")) all.add(e);
        }
        if (names != null) {
            all.add(pkg + "\t"); // اپ حتی بدون نام هم در لیست بماند (= همه مخفی)
            for (String n : names) if (!n.trim().isEmpty()) all.add(pkg + "\t" + n.trim());
        }
        sp(c).edit().putStringSet("rules", all).apply();
    }

    static boolean covers(Context c, String pkg) {
        return isEnabled(c) && Pro.isPro(c) && getRules(c).containsKey(pkg);
    }

    /** فرستنده‌های اخیر هر اپ، برای انتخاب راحت */
    static List<String> recentSenders(Context c, String pkg) {
        String raw = sp(c).getString("recent_" + pkg, "");
        List<String> out = new ArrayList<>();
        for (String s : raw.split("\n")) if (!s.isEmpty()) out.add(s);
        return out;
    }

    private static void remember(Context c, String pkg, String name) {
        if (name == null || name.trim().isEmpty()) return;
        name = name.trim();
        List<String> l = recentSenders(c, pkg);
        l.remove(name);
        l.add(0, name);
        while (l.size() > 40) l.remove(l.size() - 1);
        sp(c).edit().putString("recent_" + pkg, android.text.TextUtils.join("\n", l)).apply();
    }

    /** همه نام‌هایی که یک نوتیفیکیشن به آن‌ها مربوط است (عنوان، گفتگو، فرستنده‌ها) */
    private static List<String> names(Notification n) {
        List<String> out = new ArrayList<>();
        Bundle ex = n.extras;
        if (ex == null) return out;
        add(out, ex.getCharSequence(Notification.EXTRA_TITLE));
        add(out, ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null) {
            for (Parcelable p : msgs) {
                if (!(p instanceof Bundle)) continue;
                Bundle b = (Bundle) p;
                add(out, b.getCharSequence("sender"));
                Object person = b.get("sender_person");
                if (person instanceof android.app.Person) add(out, ((android.app.Person) person).getName());
            }
        }
        return out;
    }

    private static void add(List<String> l, CharSequence s) {
        if (s != null && s.length() > 0) l.add(s.toString());
    }

    private static boolean matches(List<String> names, Set<String> allowed) {
        for (String a : allowed) {
            String al = norm(a);
            if (al.isEmpty()) continue;
            for (String n : names) if (norm(n).contains(al)) return true;
        }
        return false;
    }

    /**
     * یکسان‌سازی برای مقایسه: ي/ی و ك/ک عربی و فارسی، نیم‌فاصله و کاراکترهای نامرئی،
     * اعراب، فاصله‌های اضافه و حروف بزرگ/کوچک.
     */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\u064A': case '\u0649': case '\u06CC': ch = '\u06CC'; break; // ی
                case '\u0643': ch = '\u06A9'; break;                                 // ک
                case '\u0629': ch = '\u0647'; break;                                 // ه
                case '\u0623': case '\u0625': case '\u0622': ch = '\u0627'; break; // ا
                case '\u06F0': case '\u0660': ch = '0'; break;
                default: break;
            }
            if (ch >= '\u06F1' && ch <= '\u06F9') ch = (char) ('1' + (ch - '\u06F1'));
            if (ch >= '\u0661' && ch <= '\u0669') ch = (char) ('1' + (ch - '\u0661'));
            int type = Character.getType(ch);
            // نیم‌فاصله، کاراکترهای جهت متن، اعراب و ایموجی‌ها حذف می‌شوند
            if (type == Character.FORMAT || type == Character.NON_SPACING_MARK
                    || type == Character.SURROGATE || type == Character.OTHER_SYMBOL) continue;
            if (Character.isWhitespace(ch) || ch == '\u00A0') {
                space = b.length() > 0;
                continue;
            }
            if (space) b.append(' ');
            space = false;
            b.append(Character.toLowerCase(ch));
        }
        return b.toString();
    }

    /**
     * @return true اگر این نوتیفیکیشن توسط فیلتر رسیدگی شد (خلاصه نوتیفیکیشن نباید به آن دست بزند)
     */
    static boolean handle(NotificationListenerService s, StatusBarNotification sbn) {
        if (sbn == null || !isEnabled(s) || !Pro.isPro(s)) return false;
        String pkg = sbn.getPackageName();
        Set<String> allowed = getRules(s).get(pkg);
        if (allowed == null) return false;
        Notification n = sbn.getNotification();
        if (n == null) return false;
        if (!sbn.isClearable() || sbn.isOngoing()) return true;
        if (Notification.CATEGORY_CALL.equals(n.category) || n.fullScreenIntent != null) return true; // تماس‌ها

        // خلاصه گروه (Group summary) هرگز حذف نمی‌شود:
        // تلگرام خلاصه را «قبل» از پیام جدید می‌فرستد و حذف آن همه نوتیفیکیشن‌های گروه
        // (از جمله پیام مجاز) را پاک می‌کند و تلگرام آن را «پاک کردن همه» حساب می‌کند.
        // اندروید خلاصه‌ای را که هیچ زیرمجموعه‌ای ندارد خودش نشان نمی‌دهد.
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return true;

        List<String> names = names(n);
        CharSequence t = n.extras != null ? n.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        for (String nm : names) remember(s, pkg, nm); // دقیقاً همان نامی که تلگرام می‌فرستد
        if (matches(names, allowed)) return true; // مجاز: نمایش بده (به خلاصه هم نرود)

        // مخفی: اگر خلاصه نوتیفیکیشن روشن است آنجا ذخیره شود
        if (Prefs.isDigestEnabled(s)) {
            DigestStore.Item it = new DigestStore.Item();
            it.key = sbn.getKey();
            it.pkg = pkg;
            it.title = t != null ? t.toString() : "";
            CharSequence text = n.extras != null ? n.extras.getCharSequence(Notification.EXTRA_TEXT) : null;
            it.text = text != null ? text.toString() : "";
            it.time = sbn.getPostTime();
            if (!it.title.isEmpty() || !it.text.isEmpty()) DigestStore.add(s, it);
        }
        cancel(s, sbn);
        return true;
    }

    private static void cancel(NotificationListenerService s, StatusBarNotification sbn) {
        try {
            s.cancelNotification(sbn.getKey());
        } catch (Exception ignored) {}
    }

    /** وقتی تنظیمات عوض شد: نوتیفیکیشن‌های فعلی دوباره بررسی شوند */
    static void applyToActive(NotificationListenerService s) {
        try {
            StatusBarNotification[] act = s.getActiveNotifications();
            if (act == null) return;
            for (StatusBarNotification o : act) handle(s, o);
        } catch (Exception ignored) {}
    }
}

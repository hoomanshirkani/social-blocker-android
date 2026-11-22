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
            String al = a.toLowerCase(Locale.ROOT);
            for (String n : names) if (n.toLowerCase(Locale.ROOT).contains(al)) return true;
        }
        return false;
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

        boolean summary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (summary) {
            // خلاصه گروه فقط وقتی بماند که پیام مجازی زیرش باشد
            if (!hasAllowedChild(s, pkg, sbn.getGroupKey(), allowed)) cancel(s, sbn);
            return true;
        }

        List<String> names = names(n);
        CharSequence t = n.extras != null ? n.extras.getCharSequence(Notification.EXTRA_TITLE) : null;
        if (t != null) remember(s, pkg, t.toString());
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
        cleanupSummaries(s, pkg, allowed);
        return true;
    }

    private static boolean hasAllowedChild(NotificationListenerService s, String pkg, String group, Set<String> allowed) {
        try {
            StatusBarNotification[] act = s.getActiveNotifications();
            if (act == null) return false;
            for (StatusBarNotification o : act) {
                if (!pkg.equals(o.getPackageName())) continue;
                Notification on = o.getNotification();
                if (on == null || (on.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if (group != null && !group.equals(o.getGroupKey())) continue;
                if (matches(names(on), allowed)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static void cleanupSummaries(NotificationListenerService s, String pkg, Set<String> allowed) {
        try {
            StatusBarNotification[] act = s.getActiveNotifications();
            if (act == null) return;
            for (StatusBarNotification o : act) {
                if (!pkg.equals(o.getPackageName())) continue;
                Notification on = o.getNotification();
                if (on == null || (on.flags & Notification.FLAG_GROUP_SUMMARY) == 0) continue;
                if (!hasAllowedChild(s, pkg, o.getGroupKey(), allowed)) cancel(s, o);
            }
        } catch (Exception ignored) {}
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
            for (StatusBarNotification o : act) {
                if ((o.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) handle(s, o);
            }
            for (StatusBarNotification o : act) {
                if ((o.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) handle(s, o);
            }
        } catch (Exception ignored) {}
    }
}

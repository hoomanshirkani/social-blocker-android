package com.hooman.socialblocker;

import android.app.Notification;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * «مزاحم نشو»: وقتی رانندگی/مسیریابی، تماس یا تایپ پیام در جریان است، استراحت چشم نمی‌آید
 * و فایروال قانون‌های قطع‌کننده (به جز «همیشه مسدود») را موقتاً اعمال نمی‌کند.
 */
final class SmartPause {
    private SmartPause() {}

    static final Set<String> NAV_APPS = new HashSet<>(Arrays.asList(
            "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze",
            "com.google.android.projection.gearhead", // Android Auto
            "com.here.app.maps", "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro",
            "com.sygic.aura", "com.tomtom.gplay.navapp", "com.tomtom.amigo.huawei", "com.generalmagic.magicearth",
            "com.huawei.maps.app", "ru.yandex.yandexnavi", "ru.yandex.yandexmaps", "com.citymapper.app.release",
            "de.bahn.dbnav", "ir.balad", "org.rajman.neshan.traffic.tehran", "com.coyotesystems.android",
            "com.samsung.android.app.smartdrivemode", "com.mapfactor.navigator", "cz.aponia.bor3"
    ));

    static volatile String foreground = "";
    static volatile long lastTyping = 0;

    static final int NONE = 0, CALL = 1, DRIVING = 2, TYPING = 3;

    static void onForeground(String pkg) {
        foreground = pkg;
    }

    /** از رویداد «تغییر متن»: فقط زمان ثبت می‌شود، نه خود متن */
    static void onTyping() {
        lastTyping = SystemClock.elapsedRealtime();
    }

    static boolean inCall(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) return false;
        int m = am.getMode();
        return m == AudioManager.MODE_RINGTONE || m == AudioManager.MODE_IN_CALL
                || m == AudioManager.MODE_IN_COMMUNICATION;
    }

    static boolean driving(Context c) {
        if (NAV_APPS.contains(foreground)) return true;
        UiModeManager um = (UiModeManager) c.getSystemService(Context.UI_MODE_SERVICE);
        if (um != null && um.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR) return true; // Android Auto / حالت ماشین
        // مسیریابی فعال در پس‌زمینه (نوتیفیکیشن دائمی مسیریابی) — اگر دسترسی نوتیفیکیشن داریم
        DigestListener l = DigestListener.instance;
        if (l != null) {
            try {
                StatusBarNotification[] act = l.getActiveNotifications();
                if (act != null) for (StatusBarNotification s : act) {
                    Notification n = s.getNotification();
                    if (n == null) continue;
                    if (Notification.CATEGORY_NAVIGATION.equals(n.category)) return true;
                    if (s.isOngoing() && NAV_APPS.contains(s.getPackageName())) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    static boolean typing() {
        return SystemClock.elapsedRealtime() - lastTyping < 20_000;
    }

    /** دلیل اینکه الان نباید مزاحم شد (NONE = آزاد) */
    static int reason(Context c, boolean includeTyping) {
        if (inCall(c)) return CALL;
        if (driving(c)) return DRIVING;
        if (includeTyping && typing()) return TYPING;
        return NONE;
    }
}

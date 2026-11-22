package com.hooman.socialblocker;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * نوتیفیکیشن‌ها را جمع می‌کند و از نوار اعلان پاک می‌کند تا فقط یک خلاصه دوره‌ای دیده شود.
 * تماس، زنگ ساعت، مسیریابی، پخش رسانه و نوتیفیکیشن‌های دائمی دست نمی‌خورند.
 */
public class DigestListener extends NotificationListenerService {

    static volatile DigestListener instance;

    /** بعد از تغییر فیلتر فرستنده */
    static void filterChanged() {
        DigestListener l = instance;
        if (l != null) NotifFilter.applyToActive(l);
    }

    @Override
    public void onListenerDisconnected() {
        instance = null;
        super.onListenerDisconnected();
    }

    private static final Set<String> KEEP_CATEGORIES = new HashSet<>(Arrays.asList(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STOPWATCH,
            Notification.CATEGORY_REMINDER
    ));

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        DigestScheduler.schedule(this);
        ServiceGuard.check(this);
        instance = this;
        NotifFilter.applyToActive(this);
        // نوتیفیکیشن‌هایی که از قبل در نوار اعلان هستند
        if (!Prefs.isDigestEnabled(this)) return;
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) for (StatusBarNotification sbn : active) collect(sbn);
        } catch (Exception ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (NotifFilter.handle(this, sbn)) return; // فیلتر فرستنده (Pro)
        } catch (Throwable ignored) {}
        if (Prefs.isDigestEnabled(this)) collect(sbn);
    }

    private void collect(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName())) return;
        if (NotifFilter.covers(this, pkg)) return; // این اپ را فیلتر فرستنده مدیریت می‌کند
        if (pkg.equals("android") || pkg.equals("com.android.systemui")) return;
        if (!sbn.isClearable() || sbn.isOngoing()) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;
        if (n.category != null && KEEP_CATEGORIES.contains(n.category)) return;
        if (n.fullScreenIntent != null) return; // تماس‌ها / هشدارهای فوری
        if (Prefs.getDigestExceptions(this).contains(pkg)) return;

        boolean summaryOnly = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (!summaryOnly) {
            Bundle ex = n.extras;
            CharSequence title = ex != null ? ex.getCharSequence(Notification.EXTRA_TITLE) : null;
            CharSequence text = ex != null ? ex.getCharSequence(Notification.EXTRA_TEXT) : null;
            if (ex != null && text == null) text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
            if (title != null || text != null) {
                DigestStore.Item it = new DigestStore.Item();
                it.key = sbn.getKey();
                it.pkg = pkg;
                it.title = title != null ? title.toString() : "";
                it.text = text != null ? text.toString() : "";
                it.time = sbn.getPostTime();
                DigestStore.add(this, it);
            }
        }
        try {
            cancelNotification(sbn.getKey()); // نوار اعلان تمیز بماند
        } catch (Exception ignored) {}
    }
}

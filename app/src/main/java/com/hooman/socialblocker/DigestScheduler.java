package com.hooman.socialblocker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** زمان‌بندی نمایش خلاصه و پاک‌سازی آخر روز، با AlarmManager (بدون مجوز alarm دقیق). */
final class DigestScheduler {
    private DigestScheduler() {}

    static final String ACTION_SUMMARY = "com.hooman.socialblocker.DIGEST_SUMMARY";
    static final String ACTION_CLEAR = "com.hooman.socialblocker.DIGEST_CLEAR";
    private static final String CHANNEL = "digest";
    static final int NOTIF_ID = 4242;

    private static PendingIntent pi(Context c, String action, int req) {
        Intent i = new Intent(c, Receiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, req, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent sum = pi(c, ACTION_SUMMARY, 1), clr = pi(c, ACTION_CLEAR, 2);
        am.cancel(sum);
        am.cancel(clr);
        if (!Prefs.isDigestEnabled(c)) return;

        long now = System.currentTimeMillis();
        long next = Prefs.getDigestNext(c);
        if (next <= now) {
            next = now + Prefs.getDigestIntervalMs(c);
            Prefs.setDigestNext(c, next);
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, sum);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, Prefs.getDigestClearHour(c));
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 0);
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), clr);
    }

    /** یک نوتیفیکیشن خلاصه (اگر چیزی جمع شده باشد). */
    static void postSummary(Context c) {
        List<DigestStore.Item> items = DigestStore.load(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || items.isEmpty()) return;
        Context lc = LocaleHelper.wrap(c);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                lc.getString(R.string.digest_channel), NotificationManager.IMPORTANCE_LOW));

        Map<String, Integer> perApp = new LinkedHashMap<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            String label = label(c, items.get(i).pkg);
            Integer n = perApp.get(label);
            perApp.put(label, n == null ? 1 : n + 1);
        }
        Notification.InboxStyle style = new Notification.InboxStyle();
        StringBuilder line = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : perApp.entrySet()) {
            String s = e.getKey() + " (" + e.getValue() + ")";
            if (shown < 6) style.addLine(s);
            if (line.length() > 0) line.append(" · ");
            line.append(s);
            shown++;
        }
        String title = lc.getString(R.string.digest_notif_title, items.size(), perApp.size());
        style.setBigContentTitle(title);

        Intent open = new Intent(c, DigestActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent content = PendingIntent.getActivity(c, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_digest)
                .setContentTitle(title)
                .setContentText(line.toString())
                .setStyle(style)
                .setContentIntent(content)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {} // مجوز نوتیفیکیشن داده نشده
    }

    static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    static void clearAll(Context c) {
        DigestStore.clear(c);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            if (ACTION_SUMMARY.equals(i.getAction())) {
                postSummary(c);
                Prefs.setDigestNext(c, 0);
            } else if (ACTION_CLEAR.equals(i.getAction())) {
                clearAll(c);
            }
            schedule(c);
        }
    }
}

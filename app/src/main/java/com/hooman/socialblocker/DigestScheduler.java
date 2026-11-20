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

import java.util.ArrayList;
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
    /** حداکثر طول متن داخل نوتیفیکیشن (بقیه با «+N مورد دیگر») */
    private static final int MAX_CHARS = 4500;

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

        // گروه‌بندی بر اساس اپ، جدیدترین اول
        Map<String, List<DigestStore.Item>> byApp = new LinkedHashMap<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            DigestStore.Item it = items.get(i);
            List<DigestStore.Item> l = byApp.get(it.pkg);
            if (l == null) {
                l = new ArrayList<>();
                byApp.put(it.pkg, l);
            }
            l.add(it);
        }

        // کل محتوا داخل خود نوتیفیکیشن (بدون نیاز به باز کردن اپ)
        java.text.DateFormat tf = android.text.format.DateFormat.getTimeFormat(c);
        StringBuilder big = new StringBuilder();
        StringBuilder line = new StringBuilder();
        int hidden = 0;
        for (Map.Entry<String, List<DigestStore.Item>> e : byApp.entrySet()) {
            String label = label(c, e.getKey());
            if (line.length() > 0) line.append(" · ");
            line.append(label).append(" (").append(e.getValue().size()).append(')');
            if (big.length() > MAX_CHARS) {
                hidden += e.getValue().size();
                continue;
            }
            if (big.length() > 0) big.append("\n\n");
            big.append("▪ ").append(label).append(" (").append(e.getValue().size()).append(')');
            for (DigestStore.Item it : e.getValue()) {
                if (big.length() > MAX_CHARS) {
                    hidden++;
                    continue;
                }
                String t = it.title.isEmpty() ? it.text
                        : it.text.isEmpty() ? it.title : it.title + ": " + it.text;
                t = t.replace('\n', ' ').trim();
                if (t.length() > 120) t = t.substring(0, 117) + "…";
                big.append("\n  ").append(tf.format(new java.util.Date(it.time))).append("  ").append(t);
            }
        }
        if (hidden > 0) big.append("\n\n").append(lc.getString(R.string.digest_more, hidden));

        String title = lc.getString(R.string.digest_notif_title, items.size(), byApp.size());
        Notification.Action clear = new Notification.Action.Builder(
                android.graphics.drawable.Icon.createWithResource(c, R.drawable.ic_stat_digest),
                lc.getString(R.string.digest_action_clear), pi(c, ACTION_CLEAR, 4)).build();
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_digest)
                .setContentTitle(title)
                .setContentText(line.toString())
                .setStyle(new Notification.BigTextStyle().setBigContentTitle(title).bigText(big.toString()))
                .addAction(clear)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
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

package com.hooman.socialblocker;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * نگهبان سرویس دسترسی‌پذیری: بعضی گوشی‌ها بعد از ری‌استارت سرویس را خاموش می‌کنند.
 * اگر مجوز WRITE_SECURE_SETTINGS (یک بار با ADB) داده شده باشد، سرویس خودکار دوباره روشن می‌شود؛
 * در غیر این صورت یک نوتیفیکیشن هشدار نمایش داده می‌شود.
 */
final class ServiceGuard {
    private ServiceGuard() {}

    static final String ADB_COMMAND =
            "adb shell pm grant com.hooman.socialblocker android.permission.WRITE_SECURE_SETTINGS";
    private static final String CHANNEL = "alerts";
    private static final int NOTIF_ID = 4343;
    static final String ACTION_CHECK = "com.hooman.socialblocker.GUARD_CHECK";

    static ComponentName component(Context c) {
        return new ComponentName(c, BlockerService.class);
    }

    static boolean isAccessibilityOn(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (s == null) return false;
        ComponentName me = component(c);
        for (String part : s.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(part))) return true;
        }
        return false;
    }

    static boolean canSelfEnable(Context c) {
        return c.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == PackageManager.PERMISSION_GRANTED;
    }

    /** آیا کاربر چیزی را روشن کرده که به سرویس نیاز دارد؟ */
    private static boolean needed(Context c) {
        return Prefs.isBlocking(c) || Prefs.isEyeEnabled(c) || Prefs.isFilterEnabled(c)
                || Prefs.isFwEnabled(c);
    }

    /** اگر لازم است و خاموش است: روشن کن (با مجوز) یا هشدار بده. */
    static void check(Context c) {
        try {
            if (isAccessibilityOn(c) || !needed(c)) {
                cancelWarning(c);
                return;
            }
            if (canSelfEnable(c) && enable(c)) {
                cancelWarning(c);
                return;
            }
            warn(c);
        } catch (Exception ignored) {}
    }

    private static boolean enable(Context c) {
        try {
            String cur = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String me = component(c).flattenToString();
            String next = (cur == null || cur.isEmpty()) ? me : cur + ":" + me;
            Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next);
            Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void warn(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Context lc = LocaleHelper.wrap(c);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL,
                lc.getString(R.string.guard_channel), NotificationManager.IMPORTANCE_HIGH));
        Intent open = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(c, 5, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_digest)
                .setContentTitle(lc.getString(R.string.guard_title))
                .setContentText(lc.getString(R.string.guard_text))
                .setStyle(new Notification.BigTextStyle().bigText(lc.getString(R.string.guard_text)))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) {}
    }

    static void cancelWarning(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIF_ID);
    }

    /** یک بررسی دیگر چند دقیقه بعد (بعد از ری‌استارت سیستم کمی طول می‌کشد) */
    static void checkLater(Context c, long delayMs) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = PendingIntent.getBroadcast(c, 6,
                new Intent(c, BootReceiver.class).setAction(ACTION_CHECK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delayMs, pi);
    }

    /** بعد از ری‌استارت / آپدیت برنامه */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            check(c);
            if (!ACTION_CHECK.equals(i.getAction())) checkLater(c, 60_000);
            DigestScheduler.schedule(c);
        }
    }
}

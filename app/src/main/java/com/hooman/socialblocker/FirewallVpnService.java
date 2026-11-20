package com.hooman.socialblocker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;

import java.io.IOException;
import java.util.Set;

/**
 * فایروال بدون روت با VpnService محلی.
 * ترافیک اپ‌هایی که باید قطع شوند به یک VPN داخلی می‌رود که هیچ‌کس بسته‌هایش را نمی‌خواند
 * (پس بسته‌ها دور ریخته می‌شوند)؛
 * بقیه اپ‌ها مستقیم به اینترنت وصل‌اند. هیچ ترافیکی به سروری فرستاده نمی‌شود.
 *
 * حالت‌ها:
 *  - برنامه زمانی روشن و الان خارج از بازه مجاز → همه اپ‌ها قطع
 *  - در غیر این صورت → فقط اپ‌های تیک‌خورده در لیست فایروال قطع
 */
public class FirewallVpnService extends VpnService {

    static final String ACTION_REFRESH = "com.hooman.socialblocker.FW_REFRESH";
    static final String ACTION_STOP = "com.hooman.socialblocker.FW_STOP";

    /** وضعیت فعلی برای نمایش در UI */
    static volatile boolean running = false;
    static volatile boolean allBlocked = false;
    static volatile long nextChangeAt = 0;
    /** زمان‌بندی روشن است ولی چون صفحه روشن است فعلاً اعمال نمی‌شود */
    static volatile boolean scheduleWaiting = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::apply;
    private ParcelFileDescriptor tun;

    /** با روشن/خاموش شدن صفحه، حالت تونل دوباره محاسبه می‌شود. */
    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (running) apply();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(screenReceiver, f);
    }

    private boolean isScreenOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    /** از هر جای برنامه بعد از تغییر تنظیمات صدا زده می‌شود. */
    static void refresh(Context c) {
        Intent i = new Intent(c, FirewallVpnService.class);
        i.setAction(Prefs.isFwEnabled(c) ? ACTION_REFRESH : ACTION_STOP);
        try {
            c.startService(i);
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action) || !Prefs.isFwEnabled(this)) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        apply();
        return START_STICKY;
    }

    /** محاسبه حالت فعلی، ساختن دوباره تونل، و زمان‌بندی تغییر بعدی. */
    private void apply() {
        handler.removeCallbacks(tick);
        if (!Prefs.isFwEnabled(this)) { shutdown(); stopSelf(); return; }

        long now = System.currentTimeMillis();
        boolean scheduleOn = Prefs.isSchedEnabled(this);
        boolean waiting = scheduleOn && Prefs.isSchedScreenOffOnly(this) && isScreenOn();
        if (waiting) scheduleOn = false; // صفحه روشن است → اینترنت آزاد (فقط لیست اپ‌ها)
        boolean blockAll = false;
        long next = 0;

        if (scheduleOn) {
            long on = Prefs.getSchedOnMs(this);
            long period = Prefs.getSchedPeriodMs(this);
            long anchor = Prefs.getSchedAnchor(this);
            long elapsed = Math.floorMod(now - anchor, period);
            boolean allowed = elapsed < on;
            blockAll = !allowed;
            next = now + (allowed ? on - elapsed : period - elapsed);
        }

        Builder b = new Builder()
                .setSession(getString(R.string.app_name))
                .addAddress("10.111.222.1", 32)
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0);
        try {
            if (blockAll) {
                b.addDisallowedApplication(getPackageName()); // همه به‌جز خود ما
            } else {
                Set<String> blocked = Prefs.getFwBlocked(this);
                int added = 0;
                for (String pkg : blocked) {
                    try {
                        b.addAllowedApplication(pkg);
                        added++;
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
                // لیست خالی یعنی «همه اپ‌ها»؛ پس فقط خود برنامه را داخل VPN می‌گذاریم
                if (added == 0) b.addAllowedApplication(getPackageName());
            }
        } catch (PackageManager.NameNotFoundException ignored) {}

        ParcelFileDescriptor newTun;
        try {
            newTun = b.establish();
        } catch (Exception e) {
            newTun = null;
        }
        if (newTun == null) { // اجازه VPN داده نشده
            shutdown();
            stopSelf();
            return;
        }

        ParcelFileDescriptor old = tun;
        tun = newTun;
        closeQuietly(old);

        running = true;
        allBlocked = blockAll;
        nextChangeAt = next;
        scheduleWaiting = waiting;
        if (next > 0) handler.postDelayed(tick, Math.max(500, next - now));
    }

    private void shutdown() {
        handler.removeCallbacks(tick);
        closeQuietly(tun);
        tun = null;
        running = false;
        allBlocked = false;
        nextChangeAt = 0;
        scheduleWaiting = false;
    }

    private static void closeQuietly(ParcelFileDescriptor p) {
        if (p == null) return;
        try { p.close(); } catch (IOException ignored) {}
    }

    @Override
    public void onRevoke() {
        // کاربر VPN را قطع کرد یا VPN دیگری روشن شد
        shutdown();
        super.onRevoke();
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
        shutdown();
        super.onDestroy();
    }
}

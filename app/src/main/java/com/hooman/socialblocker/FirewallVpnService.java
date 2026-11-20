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
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * فایروال بدون روت با VpnService محلی.
 * ترافیک اپ‌هایی که باید قطع شوند به یک تونل داخلی می‌رود؛ یک رشته (Pump) بسته‌ها را
 * می‌خواند و فوراً «اتصال رد شد» برمی‌گرداند (TCP RST / DNS REFUSED) تا اپ‌ها منتظر
 * timeout نمانند و هنگ نکنند. هیچ ترافیکی به سروری فرستاده نمی‌شود.
 *
 * حالت‌ها:
 *  - زمان‌بندی روشن و الان خارج از بازه مجاز → همه اپ‌ها قطع
 *  - در غیر این صورت → فقط اپ‌های تیک‌خورده در لیست فایروال قطع
 */
public class FirewallVpnService extends VpnService {

    static final String ACTION_REFRESH = "com.hooman.socialblocker.FW_REFRESH";
    static final String ACTION_STOP = "com.hooman.socialblocker.FW_STOP";

    private static final String DNS4 = "10.111.222.2";
    private static final String DNS6 = "fd00:1:fd00:1:fd00:1:fd00:2";

    /** وضعیت فعلی برای نمایش در UI */
    static volatile boolean running = false;
    static volatile boolean allBlocked = false;
    static volatile long nextChangeAt = 0;
    /** زمان‌بندی روشن است ولی چون صفحه روشن است فعلاً اعمال نمی‌شود */
    static volatile boolean scheduleWaiting = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::apply;
    private ParcelFileDescriptor tun;
    private Pump pump;
    /** پیکربندی تونل فعلی؛ اگر عوض نشده باشد تونل دوباره ساخته نمی‌شود */
    private String tunKey = null;

    /** با روشن/خاموش شدن صفحه، حالت دوباره محاسبه می‌شود. */
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

    /** محاسبه حالت فعلی، ساختن تونل فقط در صورت تغییر، و زمان‌بندی تغییر بعدی. */
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

        // کلید پیکربندی: اگر همان قبلی است، تونل را دست نزن (جلوگیری از قطع و وصل بی‌دلیل)
        List<String> pkgs = new ArrayList<>(Prefs.getFwBlocked(this));
        Collections.sort(pkgs);
        String key = blockAll ? "ALL" : "LIST:" + pkgs;

        if (tun == null || !key.equals(tunKey)) {
            ParcelFileDescriptor newTun = build(blockAll, pkgs);
            if (newTun == null) { // اجازه VPN داده نشده یا VPN دیگری فعال است
                shutdown();
                stopSelf();
                return;
            }
            Pump oldPump = pump;
            ParcelFileDescriptor oldTun = tun;
            tun = newTun;
            tunKey = key;
            pump = new Pump(newTun);
            pump.start();
            if (oldPump != null) oldPump.halt();
            closeQuietly(oldTun);
        }

        running = true;
        allBlocked = blockAll;
        nextChangeAt = next;
        scheduleWaiting = waiting;
        if (next > 0) handler.postDelayed(tick, Math.max(500, next - now));
    }

    private ParcelFileDescriptor build(boolean blockAll, List<String> pkgs) {
        Builder b = new Builder()
                .setSession(getString(R.string.app_name))
                .addAddress("10.111.222.1", 32)
                .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                // DNS اپ‌های قطع‌شده هم به تونل می‌آید و فوراً REFUSED می‌گیرد
                .addDnsServer(DNS4)
                .addDnsServer(DNS6)
                .setMtu(1500);
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false);
        try {
            if (blockAll) {
                b.addDisallowedApplication(getPackageName()); // همه به‌جز خود ما
            } else {
                int added = 0;
                for (String pkg : pkgs) {
                    try {
                        b.addAllowedApplication(pkg);
                        added++;
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
                // لیست خالی یعنی «همه اپ‌ها»؛ پس فقط خود برنامه را داخل VPN می‌گذاریم
                if (added == 0) b.addAllowedApplication(getPackageName());
            }
        } catch (PackageManager.NameNotFoundException ignored) {}
        try {
            return b.establish();
        } catch (Exception e) {
            return null;
        }
    }

    private void shutdown() {
        handler.removeCallbacks(tick);
        if (pump != null) pump.halt();
        pump = null;
        closeQuietly(tun);
        tun = null;
        tunKey = null;
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

    /**
     * بسته‌های تونل را می‌خواند و برای TCP/DNS فوراً پاسخ «رد شد» می‌نویسد.
     * با poll (حداکثر ۱ ثانیه) کار می‌کند تا بعد از halt سریع تمام شود و رشته‌ای گیر نکند.
     */
    private static final class Pump extends Thread {
        private final FileDescriptor fd;
        private volatile boolean stopped = false;

        Pump(ParcelFileDescriptor pfd) {
            super("fw-pump");
            setDaemon(true);
            fd = pfd.getFileDescriptor();
        }

        void halt() { stopped = true; }

        @Override
        public void run() {
            byte[] in = new byte[32767];
            byte[] out = new byte[1600];
            StructPollfd pfd = new StructPollfd();
            pfd.fd = fd;
            pfd.events = (short) OsConstants.POLLIN;
            StructPollfd[] set = {pfd};
            while (!stopped) {
                try {
                    pfd.revents = 0;
                    int r = Os.poll(set, 1000);
                    if (stopped) break;
                    if (r <= 0) continue;
                    if ((pfd.revents & (OsConstants.POLLERR | OsConstants.POLLHUP | OsConstants.POLLNVAL)) != 0) break;
                    int n = Os.read(fd, in, 0, in.length);
                    if (n <= 0) continue;
                    int m = TunResponder.respond(in, n, out);
                    if (m > 0) Os.write(fd, out, 0, m);
                } catch (ErrnoException e) {
                    if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EINTR) continue;
                    break; // تونل بسته شد (EBADF و ...)
                } catch (Exception e) {
                    break;
                }
            }
        }
    }
}

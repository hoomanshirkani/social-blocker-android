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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * فایروال بدون روت با VpnService محلی.
 * ترافیک اپ‌هایی که باید قطع شوند به یک تونل داخلی می‌رود؛ یک رشته (Pump) بسته‌ها را
 * می‌خواند و فوراً «اتصال رد شد» برمی‌گرداند (TCP RST / DNS REFUSED) تا اپ‌ها منتظر
 * timeout نمانند و هنگ نکنند. هیچ ترافیکی به سروری فرستاده نمی‌شود.
 *
 * قانون هر اپ:
 *  - 🚫 همیشه قطع
 *  - ⚡ روشن/خاموش شدن صفحه: بعد از هر روشن یا خاموش شدن صفحه، N ثانیه اینترنت؛ بقیه وقت قطع
 *  - ⏱ دوره‌ای: باز به مدت X در هر Y (اختیاری: با صفحه روشن کاملاً آزاد)
 *  - آزاد
 * اگر «زمان‌بندی برای همه اپ‌ها» روشن باشد، اپ‌های بدون قانون هم از قانون دوره‌ای پیروی می‌کنند.
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
    /** وضعیت گروه ⚡ */
    static volatile boolean burstOpen = false;
    static volatile long burstOpenUntil = 0;
    /** وضعیت گروه ⏱ */
    static volatile boolean periodicOpen = true;
    static volatile long periodicNextChange = 0;
    /** اپ‌هایی که همین الان اینترنت ندارند (یا در حالت «همه»، استثناها) */
    private static volatile Set<String> blockedNow = java.util.Collections.emptySet();
    private static volatile Set<String> exemptNow = java.util.Collections.emptySet();

    /** آیا اینترنت این اپ همین الان قطع است؟ */
    static boolean isAppBlockedNow(String pkg) {
        if (!running) return false;
        return allBlocked ? !exemptNow.contains(pkg) : blockedNow.contains(pkg);
    }

    /** پنجره گروه ⚡ تا این زمان (currentTimeMillis) باز است */
    private long burstUntil = 0;

    /** قانون ⏱ در حالت «فقط وقتی اپ باز است» */
    static volatile boolean periodicInUse = false;
    /** قانون 📱 برای همه اپ‌ها فعال است (یعنی allBlocked حالت عادی است، نه «قطع کامل») */
    static volatile boolean inUseAllActive = false;
    private static volatile FirewallVpnService instance = null;
    private String fgPkg = null;                            // اپ جلوی صفحه
    private final Map<String, Long> leftAt = new HashMap<>(); // اپ → زمان بیرون آمدن از آن

    /** BlockerService هر بار که اپ جلوی صفحه عوض شود صدا می‌زند. */
    static void foregroundChanged(String pkg) {
        FirewallVpnService s = instance;
        if (s != null) s.handler.post(() -> s.onForeground(pkg));
    }

    private void onForeground(String pkg) {
        if (pkg.equals(fgPkg)) return;
        long now = System.currentTimeMillis();
        if (fgPkg != null) leftAt.put(fgPkg, now);
        leftAt.remove(pkg);
        fgPkg = pkg;
        if (running) apply(); // اگر چیزی عوض نشده باشد تونل دوباره ساخته نمی‌شود
    }

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
            // هر روشن یا خاموش شدن صفحه → پنجره کوتاه اینترنت برای گروه ⚡
            burstUntil = System.currentTimeMillis() + Prefs.getBurstSec(FirewallVpnService.this) * 1000L;
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                // اپی که با آن کار می‌کردی هم مهلت پس‌زمینه را می‌گیرد
                if (fgPkg != null) leftAt.put(fgPkg, System.currentTimeMillis());
                fgPkg = null;
            }
            if (running) apply();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ServiceGuard.check(this); // این سرویس بعد از ری‌استارت (Always-on) زود اجرا می‌شود
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
        try {
            applyNow();
        } catch (Throwable t) {
            android.util.Log.e("SocialBlocker", "firewall apply failed", t);
        }
    }

    private void applyNow() {
        handler.removeCallbacks(tick);
        if (!Prefs.isFwEnabled(this)) { shutdown(); stopSelf(); return; }

        Prefs.pruneFwRules(this); // اپ‌های پاک‌شده از قانون‌ها حذف می‌شوند
        long now = System.currentTimeMillis();
        nextCandidate = 0;
        Set<String> always = Prefs.getFwBlocked(this);
        // قانون‌های ⏱ 📱 ⚡ و «همه اپ‌ها» جزو Pro هستند؛ بدون Pro نادیده گرفته می‌شوند (= آزاد)
        boolean pro = Pro.isPro(this);
        Set<String> burst = pro ? Prefs.getFwBurst(this) : new TreeSet<>();
        Set<String> periodic = pro ? Prefs.getFwPeriodic(this) : new TreeSet<>();
        Set<String> inUseSet = pro ? Prefs.getFwInUse(this) : new TreeSet<>();
        boolean inUseAll = pro && Prefs.isInUseAll(this);
        boolean periodicAll = pro && !inUseAll && Prefs.isSchedEnabled(this);
        Set<String> ruled = new TreeSet<>(always);
        ruled.addAll(burst);
        ruled.addAll(periodic);
        ruled.addAll(inUseSet);
        boolean screenOn = isScreenOn();

        // ⚡ روشن/خاموش شدن صفحه
        boolean bOpen = now < burstUntil;
        if (bOpen) candidate(burstUntil);

        // ⏱ دوره‌ای
        boolean periodicUsed = periodicAll || !periodic.isEmpty();
        int mode = Prefs.getScreenOnMode(this);
        boolean waiting = periodicUsed && screenOn && mode == Prefs.SCREEN_ON_FULL;
        boolean pInUse = periodicUsed && screenOn && mode == Prefs.SCREEN_ON_IN_USE;
        boolean pOpen = true;
        long pNext = 0;
        if (pInUse) {
            pOpen = false;
        } else if (periodicUsed && !waiting) {
            long on = Prefs.getSchedOnMs(this);
            long period = Prefs.getSchedPeriodMs(this);
            long anchor = Prefs.getSchedAnchor(this);
            long elapsed = Math.floorMod(now - anchor, period);
            pOpen = elapsed < on;
            pNext = now + (pOpen ? on - elapsed : period - elapsed);
            candidate(pNext);
        }
        Set<String> pFg = pInUse ? foregroundAllowed(now, Prefs.getGraceSec(this)) : new TreeSet<>();

        // 📱 فقط هنگام استفاده: اپ جلوی صفحه + اپ‌هایی که هنوز در مهلت پس‌زمینه هستند
        boolean inUseUsed = inUseAll || !inUseSet.isEmpty();
        Set<String> uFg = inUseUsed ? foregroundAllowed(now, Prefs.getInUseGraceSec(this)) : new TreeSet<>();
        pruneLeftAt(now);

        // اپ‌های دارای قانون که همین الان اجازه دارند
        Set<String> allowedRuled = new TreeSet<>();
        if (bOpen) allowedRuled.addAll(burst);
        for (String p : periodic) if (pOpen || pFg.contains(p)) allowedRuled.add(p);
        for (String p : inUseSet) if (uFg.contains(p)) allowedRuled.add(p);

        boolean blockAll = inUseAll || (periodicAll && !pOpen);
        Set<String> blocked = new TreeSet<>();
        Set<String> exempt = new TreeSet<>();
        if (blockAll) {
            // همه قطع، به‌جز این‌ها
            exempt.addAll(allowedRuled);
            Set<String> unruled = new TreeSet<>(inUseAll ? uFg : pFg);
            unruled.removeAll(ruled);
            exempt.addAll(unruled);
            exempt.removeAll(always);
        } else {
            blocked.addAll(ruled);
            blocked.removeAll(allowedRuled);
        }
        long next = nextCandidate;
        boolean inUse = pInUse;

        // کلید پیکربندی: اگر همان قبلی است، تونل را دست نزن (جلوگیری از قطع و وصل بی‌دلیل)
        String key = blockAll ? "ALL-" + exempt : "LIST:" + blocked;

        if (tun == null || !key.equals(tunKey)) {
            ParcelFileDescriptor newTun = build(blockAll, new ArrayList<>(blockAll ? exempt : blocked));
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

        blockedNow = java.util.Collections.unmodifiableSet(blocked);
        exemptNow = java.util.Collections.unmodifiableSet(exempt);
        burstOpen = bOpen;
        burstOpenUntil = bOpen ? burstUntil : 0;
        periodicOpen = pOpen;
        periodicNextChange = pNext;
        periodicInUse = inUse;
        inUseAllActive = inUseAll;
        running = true;
        allBlocked = blockAll;
        nextChangeAt = blockAll && !inUseAll ? pNext : next;
        scheduleWaiting = waiting;
        if (next > 0) handler.postDelayed(tick, Math.max(500, next - now));
    }

    /** نزدیک‌ترین زمانی که وضعیت باید دوباره محاسبه شود */
    private long nextCandidate = 0;

    private void candidate(long t) {
        if (t > 0) nextCandidate = nextCandidate == 0 ? t : Math.min(nextCandidate, t);
    }

    /** اپ جلوی صفحه + اپ‌هایی که کمتر از graceSec پیش از آن‌ها بیرون آمده‌ای */
    private Set<String> foregroundAllowed(long now, int graceSec) {
        Set<String> out = new TreeSet<>();
        if (fgPkg != null) out.add(fgPkg);
        long g = graceSec * 1000L;
        for (Map.Entry<String, Long> e : leftAt.entrySet()) {
            long until = e.getValue() + g;
            if (until > now) {
                out.add(e.getKey());
                candidate(until);
            }
        }
        return out;
    }

    /** ورودی‌های خیلی قدیمی را دور بریز */
    private void pruneLeftAt(long now) {
        Iterator<Map.Entry<String, Long>> it = leftAt.entrySet().iterator();
        while (it.hasNext()) if (now - it.next().getValue() > 15 * 60_000L) it.remove();
    }

    /** blockAll: همه به‌جز خود ما و pkgs؛ در غیر این صورت فقط pkgs داخل تونل (= قطع). */
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
                for (String pkg : pkgs) {
                    try {
                        b.addDisallowedApplication(pkg); // استثنا: پنجره ⚡ باز
                    } catch (PackageManager.NameNotFoundException ignored) {}
                }
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
        burstOpen = false;
        burstOpenUntil = 0;
        periodicOpen = true;
        periodicNextChange = 0;
        periodicInUse = false;
        inUseAllActive = false;
        blockedNow = java.util.Collections.emptySet();
        exemptNow = java.util.Collections.emptySet();
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
        instance = null;
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

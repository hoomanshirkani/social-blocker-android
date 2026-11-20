package com.hooman.socialblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * با AccessibilityService می‌فهمیم کدام اپ جلوی صفحه است.
 * - اپ مسدود + بدون سهمیه روزانه → فوراً بسته می‌شود.
 * - اپ مسدود + سهمیه روزانه → تا تمام شدن سهمیه باز است؛ زمان فقط وقتی
 *   اپ روی صفحه است و صفحه روشن است شمرده می‌شود.
 * - صفحه‌های Settings / نصب‌کننده که نام این برنامه را نشان دهند → بسته می‌شوند.
 */
public class BlockerService extends AccessibilityService {

    static final int REASON_BLOCKED = 0;
    static final int REASON_SETTINGS = 1;
    static final int REASON_LIMIT = 2;

    private static final Set<String> GUARDED = new HashSet<>(Arrays.asList(
            "com.android.settings",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.samsung.android.packageinstaller",
            "com.samsung.accessibility",
            "com.miui.securitycenter",
            "com.coloros.safecenter",
            "com.huawei.systemmanager"
    ));

    /** این‌ها پنجره‌ی «اپ» نیستند (نوار اعلان، کیبورد)؛ جلسه را قطع نمی‌کنند. */
    private final Set<String> ignored = new HashSet<>(Arrays.asList("com.android.systemui"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastGuardCheck = 0;
    private long lastBlock = 0;

    // جلسه استفاده فعلی از یک اپ مسدود (در حالت سهمیه)
    private String sessionPkg = null;
    private long sessionStart = 0;
    private final Runnable limitCheck = this::onLimitTimer;

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                endSession();
            } else {
                // باز شدن قفل: اپ جلوی صفحه را دوباره بررسی کن
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null && root.getPackageName() != null) {
                    handle(root.getPackageName().toString(), true);
                }
            }
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            for (InputMethodInfo info : imm.getEnabledInputMethodList()) ignored.add(info.getPackageName());
        }
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(screenReceiver, f);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        handle(event.getPackageName().toString(),
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private void handle(String pkg, boolean windowChanged) {
        if (ignored.contains(pkg)) return;

        if (pkg.equals(getPackageName())) {
            if (windowChanged) endSession();
            return;
        }
        if (!Prefs.isActive(this)) { // خاموش یا متوقف موقت: چیزی شمرده نمی‌شود
            endSession();
            return;
        }

        // ۱) اپ مسدود
        if (Prefs.getBlocked(this).contains(pkg)) {
            long allowance = Prefs.getAllowanceMs(this);
            if (allowance <= 0) {
                endSession();
                block(pkg, REASON_BLOCKED);
                return;
            }
            long remaining = allowance - usedNow();
            if (remaining <= 0) {
                endSession();
                block(pkg, REASON_LIMIT);
                return;
            }
            if (!pkg.equals(sessionPkg)) {
                endSession();
                startSession(pkg, remaining);
                Context lc = LocaleHelper.wrap(this);
                Toast.makeText(this, lc.getString(R.string.limit_toast, MainActivity.fmt(remaining)),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // ۲) اپ دیگری جلوی صفحه آمد → جلسه تمام
        if (windowChanged) endSession();

        // ۳) محافظت از تنظیمات
        if (Prefs.isProtect(this) && isGuarded(pkg)) {
            long now = SystemClock.uptimeMillis();
            if (!windowChanged && now - lastGuardCheck < 400) return; // throttle
            lastGuardCheck = now;
            if (screenMentionsThisApp()) block(pkg, REASON_SETTINGS);
        }
    }

    // ------------------------------------------------------------------ usage sessions

    private long usedNow() {
        long live = sessionPkg != null ? SystemClock.elapsedRealtime() - sessionStart : 0;
        return Prefs.getUsedToday(this) + live;
    }

    private void startSession(String pkg, long remaining) {
        sessionPkg = pkg;
        sessionStart = SystemClock.elapsedRealtime();
        handler.removeCallbacks(limitCheck);
        handler.postDelayed(limitCheck, Math.max(1000, remaining));
    }

    private void endSession() {
        handler.removeCallbacks(limitCheck);
        if (sessionPkg == null) return;
        Prefs.addUsage(this, SystemClock.elapsedRealtime() - sessionStart);
        sessionPkg = null;
    }

    /** وقتی سهمیه در حین استفاده تمام شود. */
    private void onLimitTimer() {
        String pkg = sessionPkg;
        if (pkg == null) return;
        endSession();
        long remaining = Prefs.getAllowanceMs(this) - Prefs.getUsedToday(this);
        if (!Prefs.isActive(this)) return;
        if (remaining <= 0) block(pkg, REASON_LIMIT);
        else startSession(pkg, remaining); // سهمیه در این فاصله تغییر کرده
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isGuarded(String pkg) {
        return GUARDED.contains(pkg) || pkg.contains("settings") || pkg.contains("packageinstaller");
    }

    private boolean screenMentionsThisApp() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        List<AccessibilityNodeInfo> hits =
                root.findAccessibilityNodeInfosByText(getString(R.string.app_name));
        return hits != null && !hits.isEmpty();
    }

    private void block(String pkg, int reason) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        long now = SystemClock.uptimeMillis();
        if (now - lastBlock < 800) return; // صفحه را پشت‌سرهم باز نکن
        lastBlock = now;

        Intent i = new Intent(this, BlockedActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(BlockedActivity.EXTRA_PKG, pkg);
        i.putExtra(BlockedActivity.EXTRA_REASON, reason);
        try {
            startActivity(i);
        } catch (Exception ignoredEx) {
            // اگر سیستم اجازه نداد، همان برگشت به Home کافی است
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        endSession();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignoredEx) {}
        super.onDestroy();
    }
}

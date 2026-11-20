package com.hooman.socialblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * با AccessibilityService می‌فهمیم کدام اپ جلوی صفحه است.
 * اگر در لیست مسدود باشد → برگشت به Home + نمایش صفحه "مسدود".
 * اگر صفحه‌ای از Settings / نصب‌کننده نام این برنامه را نشان دهد (یعنی کاربر
 * می‌خواهد سرویس را خاموش، Force stop یا Uninstall کند) → همان کار.
 */
public class BlockerService extends AccessibilityService {

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

    private long lastGuardCheck = 0;
    private long lastBlock = 0;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(getPackageName())) return;
        if (!Prefs.isActive(this)) return;

        // ۱) اپ مسدود
        if (Prefs.getBlocked(this).contains(pkg)) {
            block(pkg, false);
            return;
        }

        // ۲) محافظت از تنظیمات
        if (Prefs.isProtect(this) && isGuarded(pkg)) {
            long now = SystemClock.uptimeMillis();
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    && now - lastGuardCheck < 400) {
                return; // throttle
            }
            lastGuardCheck = now;
            if (screenMentionsThisApp()) block(pkg, true);
        }
    }

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

    private void block(String pkg, boolean isSettings) {
        performGlobalAction(GLOBAL_ACTION_HOME);
        long now = SystemClock.uptimeMillis();
        if (now - lastBlock < 800) return; // صفحه را پشت‌سرهم باز نکن
        lastBlock = now;

        Intent i = new Intent(this, BlockedActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(BlockedActivity.EXTRA_PKG, pkg);
        i.putExtra(BlockedActivity.EXTRA_SETTINGS, isSettings);
        try {
            startActivity(i);
        } catch (Exception ignored) {
            // اگر سیستم اجازه نداد، همان برگشت به Home کافی است
        }
    }

    @Override
    public void onInterrupt() {}
}

package com.hooman.socialblocker;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

/**
 * مراقبت از چشم (شبیه Eye Relax):
 *  ۱) یادآوری استراحت: بعد از N دقیقه استفاده با صفحه روشن، صفحه استراحت با شمارش معکوس.
 *     اگر صفحه به اندازه مدت استراحت خاموش بماند، همان استراحت طبیعی حساب می‌شود.
 *  ۲) فیلتر گرم: یک لایه کهربایی شفاف روی کل صفحه (کاهش نور آبی)، اختیاری فقط در شب.
 * داخل BlockerService اجرا می‌شود (لایه Accessibility به مجوز اضافه نیاز ندارد).
 */
final class EyeCare {

    /** زمان استراحت بعدی (System.currentTimeMillis) برای نمایش در UI؛ 0 = ندارد */
    static volatile long nextBreakAt = 0;

    /** استراحت سخت‌گیرانه تا این زمان (elapsedRealtime) قفل است: هیچ صفحه دیگری اجازه ندارد */
    private static volatile long lockUntil = 0;

    static boolean isBreakLocked() { return SystemClock.elapsedRealtime() < lockUntil; }

    static int lockRemainingSec() {
        return (int) Math.max(1, (lockUntil - SystemClock.elapsedRealtime()) / 1000);
    }

    static void unlockBreak() { lockUntil = 0; }

    private final AccessibilityService svc;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable fire = this::fireBreak;
    private final Runnable warn = this::warnBreak;
    private final Runnable filterTick = this::updateFilter;

    private long accumulated = 0;   // زمان استفاده از آخرین استراحت (ms)
    private long activeSince = 0;   // elapsedRealtime شروع بازه فعلی صفحه روشن (0 = خاموش)
    private long screenOffAt = 0;
    private View overlay;

    EyeCare(AccessibilityService svc) {
        this.svc = svc;
    }

    // ------------------------------------------------------------------ break reminders

    void onScreenOn() {
        if (activeSince != 0) return; // از قبل در حال شمارش
        long now = SystemClock.elapsedRealtime();
        if (screenOffAt > 0 && now - screenOffAt >= breakMs()) accumulated = 0; // استراحت طبیعی
        activeSince = now;
        schedule();
    }

    void onScreenOff() {
        long now = SystemClock.elapsedRealtime();
        if (activeSince != 0) accumulated += Math.max(0, now - activeSince);
        activeSince = 0;
        screenOffAt = now;
        cancel();
        nextBreakAt = 0;
    }

    void settingsChanged() {
        accumulated = 0;
        if (activeSince != 0) activeSince = SystemClock.elapsedRealtime();
        schedule();
        updateFilter();
    }

    private long breakMs() { return Prefs.getEyeBreakSec(svc) * 1000L; }

    private void cancel() {
        h.removeCallbacks(fire);
        h.removeCallbacks(warn);
    }

    private void schedule() {
        cancel();
        nextBreakAt = 0;
        if (!Prefs.isEyeEnabled(svc) || activeSince == 0) return;
        long now = SystemClock.elapsedRealtime();
        long used = accumulated + (now - activeSince); // بعد از استراحت می‌تواند منفی باشد
        long delay = Math.max(1000, Prefs.getEyeIntervalMin(svc) * 60_000L - used);
        h.postDelayed(fire, delay);
        if (delay > 15_000) h.postDelayed(warn, delay - 10_000);
        nextBreakAt = System.currentTimeMillis() + delay;
    }

    private void warnBreak() {
        Context lc = LocaleHelper.wrap(svc);
        Toast.makeText(svc, lc.getString(R.string.eye_warn), Toast.LENGTH_LONG).show();
    }

    private void fireBreak() {
        if (!Prefs.isEyeEnabled(svc) || activeSince == 0) return;
        AudioManager am = (AudioManager) svc.getSystemService(Context.AUDIO_SERVICE);
        if (am != null && (am.getMode() == AudioManager.MODE_IN_CALL
                || am.getMode() == AudioManager.MODE_IN_COMMUNICATION)) {
            // وسط تماس مزاحم نشو؛ یک دقیقه بعد دوباره امتحان کن
            h.postDelayed(fire, 60_000);
            nextBreakAt = System.currentTimeMillis() + 60_000;
            return;
        }
        if (Prefs.isEyeStrict(svc)) lockUntil = SystemClock.elapsedRealtime() + breakMs() + 1000;
        Intent i = new Intent(svc, BreakActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        i.putExtra(BreakActivity.EXTRA_SECONDS, Prefs.getEyeBreakSec(svc));
        i.putExtra(BreakActivity.EXTRA_STRICT, Prefs.isEyeStrict(svc));
        try {
            svc.startActivity(i);
        } catch (Exception ignored) {}
        // شمارش دوره بعد از پایان استراحت شروع می‌شود
        accumulated = 0;
        activeSince = SystemClock.elapsedRealtime() + breakMs();
        schedule();
    }

    // ------------------------------------------------------------------ warm filter

    void updateFilter() {
        h.removeCallbacks(filterTick);
        boolean enabled = Prefs.isFilterEnabled(svc);
        boolean nightOnly = Prefs.isFilterNightOnly(svc);
        if (enabled && (!nightOnly || inNight())) showFilter();
        else hideFilter();
        if (enabled && nightOnly) h.postDelayed(filterTick, 60_000); // هر دقیقه ساعت را چک کن
    }

    private boolean inNight() {
        int hour = java.time.LocalTime.now().getHour();
        int from = Prefs.getFilterFrom(svc), to = Prefs.getFilterTo(svc);
        if (from == to) return true;
        return from > to ? (hour >= from || hour < to) : (hour >= from && hour < to);
    }

    private void showFilter() {
        WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;
        int alpha = Math.min(200, Math.max(0, Prefs.getFilterIntensity(svc)) * 2);
        int color = Color.argb(alpha, 255, 140, 30);
        if (overlay == null) {
            View v = new View(svc);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            try {
                wm.addView(v, lp);
                overlay = v;
            } catch (Exception e) {
                return;
            }
        }
        overlay.setBackgroundColor(color);
    }

    private void hideFilter() {
        if (overlay == null) return;
        WindowManager wm = (WindowManager) svc.getSystemService(Context.WINDOW_SERVICE);
        try {
            if (wm != null) wm.removeView(overlay);
        } catch (Exception ignored) {}
        overlay = null;
    }

    void destroy() {
        cancel();
        h.removeCallbacks(filterTick);
        hideFilter();
        nextBreakAt = 0;
    }
}

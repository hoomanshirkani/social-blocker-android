package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * «افشای شفاف» (Prominent Disclosure) طبق سیاست گوگل‌پلی:
 * قبل از درخواست هر مجوز حساس، توضیح می‌دهد چه داده‌ای و چرا استفاده می‌شود و رضایت می‌گیرد.
 * رضایت ذخیره می‌شود تا هر بار پرسیده نشود.
 */
final class Disclosure {
    private Disclosure() {}

    static final String ACCESSIBILITY = "accessibility";
    static final String VPN = "vpn";
    static final String NOTIFICATIONS = "notifications";
    static final String DEVICE_ADMIN = "device_admin";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_consent", Context.MODE_PRIVATE);
    }

    static boolean accepted(Context c, String key) {
        return sp(c).getBoolean(key, false);
    }

    static void show(Activity a, String key, int title, int message, Runnable onAccept) {
        if (accepted(a, key)) {
            onAccept.run();
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(R.string.disc_agree, (d, w) -> {
                    sp(a).edit().putBoolean(key, true).apply();
                    onAccept.run();
                })
                .setNegativeButton(R.string.disc_decline, null)
                .show();
    }
}

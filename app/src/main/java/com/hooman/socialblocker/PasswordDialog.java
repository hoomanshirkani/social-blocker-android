package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * دیالوگ پرسیدن رمز، مشترک بین همه صفحه‌ها.
 * بعد از یک بار وارد کردن درست رمز، تا UNLOCK_MS دیگر رمز پرسیده نمی‌شود.
 */
public final class PasswordDialog {
    private PasswordDialog() {}

    static final long UNLOCK_MS = 5 * 60 * 1000L;
    private static long unlockedUntil = 0;

    static boolean isUnlocked() { return System.currentTimeMillis() < unlockedUntil; }

    static long unlockedRemaining() { return Math.max(0, unlockedUntil - System.currentTimeMillis()); }

    static void lock() { unlockedUntil = 0; }

    static EditText field(Activity a, int hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    static LinearLayout box(Activity a, EditText... fields) {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * a.getResources().getDisplayMetrics().density);
        l.setPadding(p, p / 2, p, 0);
        for (EditText f : fields) l.addView(f);
        return l;
    }

    static void ask(Activity a, Runnable onOk) {
        if (isUnlocked()) {
            onOk.run();
            return;
        }
        EditText input = field(a, R.string.pw_hint);
        new AlertDialog.Builder(a)
                .setTitle(R.string.pw_enter_title)
                .setView(box(a, input))
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (Prefs.checkPassword(a, input.getText().toString())) {
                        unlockedUntil = System.currentTimeMillis() + UNLOCK_MS;
                        Toast.makeText(a, R.string.toast_unlocked, Toast.LENGTH_SHORT).show();
                        onOk.run();
                    }
                    else Toast.makeText(a, R.string.toast_wrong_password, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

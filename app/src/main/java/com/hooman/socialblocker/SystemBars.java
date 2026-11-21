package com.hooman.socialblocker;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.WindowInsets;

/**
 * از اندروید ۱۶ (target 36) نمایش edge-to-edge اجباری است و دیگر نمی‌شود خاموشش کرد؛
 * این کلاس به محتوای هر صفحه به اندازه نوار وضعیت/ناوبری/بریدگی دوربین فاصله می‌دهد
 * تا چیزی زیر نوارها نرود.
 */
final class SystemBars {
    private SystemBars() {}

    static void apply(Activity a) {
        if (Build.VERSION.SDK_INT < 35) return;
        View content = a.findViewById(android.R.id.content);
        if (content == null) return;
        final int l = content.getPaddingLeft(), t = content.getPaddingTop(),
                r = content.getPaddingRight(), b = content.getPaddingBottom();
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets i = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom);
            return insets;
        });
        content.requestApplyInsets();
    }
}

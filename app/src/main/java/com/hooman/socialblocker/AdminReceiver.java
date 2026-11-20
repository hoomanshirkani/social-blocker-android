package com.hooman.socialblocker;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

/** تا وقتی Device Admin فعال است، اندروید اجازه Uninstall نمی‌دهد. */
public class AdminReceiver extends DeviceAdminReceiver {
    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return context.getString(R.string.admin_disable_warning);
    }
}

package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;

public class BlockedActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_REASON = "reason";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);
        findViewById(R.id.homeBtn).setOnClickListener(v -> goHome());
        bind(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        bind(intent);
    }

    private void bind(Intent intent) {
        TextView title = findViewById(R.id.title);
        TextView msg = findViewById(R.id.message);
        int reason = intent.getIntExtra(EXTRA_REASON, BlockerService.REASON_BLOCKED);
        if (reason == BlockerService.REASON_SETTINGS) {
            title.setText(R.string.blocked_settings_title);
            msg.setText(R.string.blocked_settings);
        } else if (reason == BlockerService.REASON_LIMIT) {
            title.setText(R.string.limit_up_title);
            msg.setText(getString(R.string.limit_up_msg, MainActivity.fmt(Prefs.getLimitMs(this))));
        } else {
            title.setText(R.string.blocked_title);
            String pkg = intent.getStringExtra(EXTRA_PKG);
            String name = pkg;
            try {
                PackageManager pm = getPackageManager();
                name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (Exception ignored) {}
            msg.setText(getString(R.string.blocked_msg, name));
        }
    }

    private void goHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome();
    }
}

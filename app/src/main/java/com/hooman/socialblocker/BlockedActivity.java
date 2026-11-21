package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.util.Random;

public class BlockedActivity extends Activity {
    static final String EXTRA_PKG = "pkg";
    static final String EXTRA_REASON = "reason";
    static final String EXTRA_HOST = "host";

    private final Random random = new Random();
    private int lastQuote = -1;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blocked);
        SystemBars.apply(this);
        findViewById(R.id.homeBtn).setOnClickListener(v -> goHome());
        findViewById(R.id.nextQuote).setOnClickListener(v -> showQuote());
        findViewById(R.id.openAppBtn).setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
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
        View openApp = findViewById(R.id.openAppBtn);
        openApp.setVisibility(reason == BlockerService.REASON_SETTINGS
                || reason == BlockerService.REASON_UNINSTALL ? View.VISIBLE : View.GONE);
        if (reason == BlockerService.REASON_SETTINGS) {
            title.setText(R.string.blocked_settings_title);
            msg.setText(R.string.blocked_settings);
        } else if (reason == BlockerService.REASON_UNINSTALL) {
            title.setText(R.string.blocked_uninstall_title);
            msg.setText(R.string.blocked_uninstall);
        } else if (reason == BlockerService.REASON_SITE) {
            title.setText(R.string.blocked_site_title);
            msg.setText(getString(R.string.blocked_site_msg, intent.getStringExtra(EXTRA_HOST)));
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
        showQuote();
    }

    /** یک شعر/پیام تصادفی (غیرتکراری پشت‌سرهم) به فارسی و انگلیسی. */
    private void showQuote() {
        String[] fa = getResources().getStringArray(R.array.quote_fa);
        String[] en = getResources().getStringArray(R.array.quote_en);
        String[] byFa = getResources().getStringArray(R.array.quote_by_fa);
        String[] byEn = getResources().getStringArray(R.array.quote_by_en);
        int n = Math.min(Math.min(fa.length, en.length), Math.min(byFa.length, byEn.length));
        if (n == 0) return;
        int i = random.nextInt(n);
        if (n > 1 && i == lastQuote) i = (i + 1) % n;
        lastQuote = i;

        ((TextView) findViewById(R.id.quoteFa)).setText(fa[i]);
        ((TextView) findViewById(R.id.quoteEn)).setText(en[i]);
        setAuthor(R.id.quoteByFa, byFa[i]);
        setAuthor(R.id.quoteByEn, byEn[i]);
    }

    private void setAuthor(int id, String name) {
        TextView t = findViewById(id);
        t.setText("— " + name);
        t.setVisibility(name.isEmpty() ? View.GONE : View.VISIBLE);
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

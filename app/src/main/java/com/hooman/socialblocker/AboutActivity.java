package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

/** درباره برنامه: نسخه، خلاصه حریم خصوصی، لینک سیاست کامل، Impressum. */
public class AboutActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        SystemBars.apply(this);
        setTitle(R.string.menu_about);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {}
        ((TextView) findViewById(R.id.aboutVersion)).setText(getString(R.string.about_version, version));

        ((TextView) findViewById(R.id.aboutImpressum)).setText(
                getString(R.string.impressum_name) + "\n" + getString(R.string.impressum_address)
                        + "\n" + getString(R.string.impressum_email));

        findViewById(R.id.aboutPrivacyLink).setOnClickListener(v -> open(getString(R.string.privacy_url)));
        findViewById(R.id.aboutSource).setOnClickListener(v -> open(getString(R.string.source_url)));
    }

    private void open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** صفحه Pro: لیست امکانات، خرید یک‌باره، بازیابی خرید */
public class ProActivity extends Activity {

    private TextView title, sub;
    private Button buy;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pro);
        SystemBars.apply(this);
        setTitle(R.string.pro_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        title = findViewById(R.id.proTitle);
        sub = findViewById(R.id.proSub);
        buy = findViewById(R.id.proBuy);
        buy.setOnClickListener(v -> Pro.buy(this));
        findViewById(R.id.proTrial).setOnClickListener(v -> Ads.watchForTrial(this));
        findViewById(R.id.proAdPrivacy).setOnClickListener(v -> Ads.showPrivacyOptions(this));
        findViewById(R.id.proRestore).setOnClickListener(v -> {
            Pro.refresh(this);
            Toast.makeText(this, R.string.pro_restoring, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Pro.setListener(this::bind);
        Pro.refresh(this);
        bind();
    }

    @Override
    protected void onPause() {
        Pro.setListener(null);
        super.onPause();
    }

    private void bind() {
        boolean pro = Pro.isOwned(this);
        long trial = Pro.trialUntil(this) - System.currentTimeMillis();
        title.setText(pro ? R.string.pro_owned_title : R.string.pro_title);
        if (!Pro.gated()) sub.setText(R.string.pro_open_source);
        else if (pro) sub.setText(R.string.pro_owned_sub);
        else if (trial > 0) sub.setText(getString(R.string.pro_trial_left, MainActivity.fmt(trial)));
        else sub.setText(R.string.pro_sub);
        TextView trialBtn = findViewById(R.id.proTrial);
        trialBtn.setVisibility(!pro && Ads.available() ? android.view.View.VISIBLE : android.view.View.GONE);
        trialBtn.setText(trial > 0 ? R.string.ad_watch_extend : R.string.ad_watch);
        findViewById(R.id.proAdPrivacy).setVisibility(Ads.privacyOptionsRequired(this)
                ? android.view.View.VISIBLE : android.view.View.GONE);
        buy.setVisibility(pro ? android.view.View.GONE : android.view.View.VISIBLE);
        findViewById(R.id.proRestore).setVisibility(pro ? android.view.View.GONE : android.view.View.VISIBLE);
        String price = Pro.price();
        buy.setText(price == null ? getString(R.string.pro_buy) : getString(R.string.pro_buy_price, price));
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

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
        boolean pro = Pro.isPro(this);
        title.setText(pro ? R.string.pro_owned_title : R.string.pro_title);
        if (!Pro.gated()) sub.setText(R.string.pro_open_source);
        else sub.setText(pro ? R.string.pro_owned_sub : R.string.pro_sub);
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

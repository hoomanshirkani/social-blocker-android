package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** لیست سایت‌های مسدود در مرورگرها (Pro) */
public class SitesActivity extends Activity {

    private Switch sw;
    private ArrayAdapter<String> adapter;
    private final List<String> items = new ArrayList<>();
    private TextView header;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sites);
        SystemBars.apply(this);
        setTitle(R.string.site_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        sw = findViewById(R.id.siteSwitch);
        header = findViewById(R.id.siteHeader);
        EditText input = findViewById(R.id.siteInput);
        ListView list = findViewById(R.id.siteList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        list.setAdapter(adapter);

        sw.setChecked(SiteBlock.isEnabled(this));
        sw.setOnClickListener(v -> {
            boolean on = sw.isChecked();
            sw.setChecked(!on);
            if (on) {
                Pro.require(this, () -> Disclosure.show(this, Disclosure.ACCESSIBILITY,
                        R.string.disc_acc_title, R.string.disc_acc_msg, () -> {
                            SiteBlock.setEnabled(this, true);
                            sw.setChecked(true);
                        }));
            } else {
                guarded(() -> {
                    SiteBlock.setEnabled(this, false);
                    sw.setChecked(false);
                });
            }
        });

        findViewById(R.id.siteAdd).setOnClickListener(v -> {
            String d = SiteBlock.normalize(input.getText().toString());
            if (d == null) {
                Toast.makeText(this, R.string.site_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            Pro.require(this, () -> {
                add(Arrays.asList(d));
                input.setText("");
            });
        });
        findViewById(R.id.siteSuggest).setOnClickListener(v ->
                Pro.require(this, () -> add(Arrays.asList(SiteBlock.SUGGESTED))));

        list.setOnItemClickListener((p, v, pos, id) -> {
            String d = items.get(pos);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.site_remove_q, d))
                    .setPositiveButton(R.string.site_remove, (dlg, w) -> guarded(() -> {
                        Set<String> s = SiteBlock.getDomains(this);
                        s.remove(d);
                        SiteBlock.setDomains(this, s);
                        reload();
                    }))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
        reload();
    }

    /** شل کردن (حذف سایت / خاموش کردن) وقتی مسدودسازی روشن است → رمز */
    private void guarded(Runnable r) {
        if (Prefs.isActive(this) && SiteBlock.isEnabled(this)) PasswordDialog.ask(this, r);
        else r.run();
    }

    private void add(List<String> ds) {
        Set<String> s = SiteBlock.getDomains(this);
        s.addAll(ds);
        SiteBlock.setDomains(this, s);
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(SiteBlock.getDomains(this));
        adapter.notifyDataSetChanged();
        header.setText(getString(R.string.site_list_header, items.size()));
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

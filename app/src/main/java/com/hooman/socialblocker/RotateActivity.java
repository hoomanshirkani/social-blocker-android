package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** چرخش خودکار صفحه فقط در اپ‌های انتخابی */
public class RotateActivity extends Activity {

    private Switch sw;
    private TextView perm, header;
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rotate);
        SystemBars.apply(this);
        setTitle(R.string.rot_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        sw = findViewById(R.id.rotSwitch);
        perm = findViewById(R.id.rotPerm);
        header = findViewById(R.id.rotHeader);
        ListView list = findViewById(R.id.rotList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);

        sw.setChecked(AutoRotate.isEnabled(this));
        sw.setOnCheckedChangeListener((b, on) -> {
            AutoRotate.setEnabled(this, on);
            if (on && !AutoRotate.canWrite(this)) askPermission();
        });
        perm.setOnClickListener(v -> askPermission());
        findViewById(R.id.rotPick).setOnClickListener(v -> pick());
    }

    @Override
    protected void onResume() {
        super.onResume();
        perm.setVisibility(AutoRotate.canWrite(this) ? View.GONE : View.VISIBLE);
        reload();
    }

    private void askPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    private boolean installed(String pkg) {
        try {
            getPackageManager().getApplicationInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private String label(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    private void reload() {
        rows.clear();
        for (String p : AutoRotate.getApps(this)) if (installed(p)) rows.add(label(p));
        rows.sort(String::compareToIgnoreCase);
        adapter.notifyDataSetChanged();
        header.setText(getString(R.string.rot_header, rows.size()));
    }

    private void pick() {
        Set<String> chosen = AutoRotate.getApps(this);
        AppLoader.load(this, chosen, apps -> {
            CharSequence[] names = new CharSequence[apps.size()];
            boolean[] checked = new boolean[apps.size()];
            for (int i = 0; i < names.length; i++) {
                names[i] = apps.get(i).label;
                checked[i] = chosen.contains(apps.get(i).pkg);
            }
            Set<String> result = new HashSet<>(chosen);
            new AlertDialog.Builder(this)
                    .setTitle(R.string.rot_pick)
                    .setMultiChoiceItems(names, checked, (d, i, on) -> {
                        if (on) result.add(apps.get(i).pkg); else result.remove(apps.get(i).pkg);
                    })
                    .setPositiveButton(R.string.save, (d, w) -> {
                        AutoRotate.setApps(this, result);
                        reload();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

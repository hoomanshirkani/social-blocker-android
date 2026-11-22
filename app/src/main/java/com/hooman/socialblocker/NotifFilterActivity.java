package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** فیلتر فرستنده نوتیفیکیشن: هر اپ → فقط این افراد/گروه‌ها (Pro) */
public class NotifFilterActivity extends Activity {

    private Switch sw;
    private TextView access;
    private final List<String> pkgs = new ArrayList<>();
    private final List<String> rows = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notif_filter);
        SystemBars.apply(this);
        setTitle(R.string.nf_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        sw = findViewById(R.id.nfSwitch);
        access = findViewById(R.id.nfAccess);
        ListView list = findViewById(R.id.nfList);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows);
        list.setAdapter(adapter);

        sw.setChecked(NotifFilter.isEnabled(this));
        sw.setOnClickListener(v -> {
            boolean on = sw.isChecked();
            sw.setChecked(!on);
            if (on) {
                Pro.require(this, () -> {
                    NotifFilter.setEnabled(this, true);
                    sw.setChecked(true);
                    DigestListener.filterChanged();
                });
            } else {
                NotifFilter.setEnabled(this, false);
                sw.setChecked(false);
            }
        });
        access.setOnClickListener(v -> Disclosure.show(this, Disclosure.NOTIFICATIONS,
                R.string.disc_notif_title, R.string.disc_notif_msg, () -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                    } catch (Exception ignored) {}
                }));
        findViewById(R.id.nfAdd).setOnClickListener(v -> Pro.require(this, this::pickApp));
        list.setOnItemClickListener((p, v, pos, id) -> editNames(pkgs.get(pos)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        access.setVisibility(isListenerEnabled() ? View.GONE : View.VISIBLE);
        reload();
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
        pkgs.clear();
        rows.clear();
        for (Map.Entry<String, Set<String>> e : NotifFilter.getRules(this).entrySet()) {
            pkgs.add(e.getKey());
            Set<String> names = e.getValue();
            rows.add(label(e.getKey()) + "\n" + (names.isEmpty()
                    ? getString(R.string.nf_none_allowed)
                    : getString(R.string.nf_only, android.text.TextUtils.join("، ", names))));
        }
        adapter.notifyDataSetChanged();
    }

    /** انتخاب اپ: پیام‌رسان‌های نصب‌شده اول */
    private void pickApp() {
        Set<String> first = new HashSet<>(Arrays.asList(NotifFilter.MESSENGERS));
        AppLoader.load(this, first, apps -> {
            CharSequence[] names = new CharSequence[apps.size()];
            for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
            new AlertDialog.Builder(this)
                    .setTitle(R.string.nf_pick_app)
                    .setItems(names, (d, w) -> editNames(apps.get(w).pkg))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });
    }

    /** انتخاب افراد مجاز: از فرستنده‌های اخیر، یا نوشتن نام */
    private void editNames(String pkg) {
        Set<String> current = NotifFilter.getRules(this).get(pkg);
        final Set<String> chosen = new LinkedHashSet<>(current != null ? current : new LinkedHashSet<>());
        LinkedHashSet<String> options = new LinkedHashSet<>(chosen);
        options.addAll(NotifFilter.recentSenders(this, pkg));
        final String[] opts = options.toArray(new String[0]);
        boolean[] checked = new boolean[opts.length];
        for (int i = 0; i < opts.length; i++) checked[i] = chosen.contains(opts[i]);

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.nf_edit_title, label(pkg)))
                .setPositiveButton(R.string.save, (d, w) -> save(pkg, chosen))
                .setNeutralButton(R.string.nf_type_name, (d, w) -> typeName(pkg, chosen))
                .setNegativeButton(current != null ? R.string.nf_remove_app : R.string.cancel, (d, w) -> {
                    if (current != null) {
                        NotifFilter.setRule(this, pkg, null);
                        reload();
                    }
                });
        if (opts.length > 0) {
            b.setMultiChoiceItems(opts, checked, (d, i, isChecked) -> {
                if (isChecked) chosen.add(opts[i]); else chosen.remove(opts[i]);
            });
        } else {
            b.setMessage(R.string.nf_no_recent);
        }
        b.show();
    }

    private void typeName(String pkg, Set<String> chosen) {
        EditText in = new EditText(this);
        in.setHint(R.string.nf_name_hint);
        in.setSingleLine(true);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(in);
        new AlertDialog.Builder(this)
                .setTitle(R.string.nf_type_name)
                .setMessage(R.string.nf_name_help)
                .setView(box)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String n = in.getText().toString().trim();
                    if (!n.isEmpty()) chosen.add(n);
                    save(pkg, chosen);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void save(String pkg, Set<String> names) {
        NotifFilter.setRule(this, pkg, names);
        reload();
        DigestListener.filterChanged();
    }

    private boolean isListenerEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (s == null) return false;
        ComponentName me = new ComponentName(this, DigestListener.class);
        for (String part : s.split(":")) {
            if (me.equals(ComponentName.unflattenFromString(part))) return true;
        }
        return false;
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

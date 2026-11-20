package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long PAUSE_MS = 15 * 60 * 1000L;

    private TextView statusText;
    private Button toggleBtn, pauseBtn, pwBtn, langBtn, accBtn, adminBtn;
    private CheckBox protectBox;

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> shown = new ArrayList<>();
    private AppAdapter adapter;
    private String filter = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    static final class AppItem {
        String pkg, label; Drawable icon;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Prefs.initDefaults(this);

        statusText = findViewById(R.id.statusText);
        toggleBtn = findViewById(R.id.toggleBtn);
        pauseBtn = findViewById(R.id.pauseBtn);
        pwBtn = findViewById(R.id.pwBtn);
        langBtn = findViewById(R.id.langBtn);
        accBtn = findViewById(R.id.accBtn);
        adminBtn = findViewById(R.id.adminBtn);
        protectBox = findViewById(R.id.protectBox);
        ListView list = findViewById(R.id.appList);
        EditText search = findViewById(R.id.search);

        adapter = new AppAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onAppClicked(shown.get(pos)));

        toggleBtn.setOnClickListener(v -> onToggle());
        pauseBtn.setOnClickListener(v -> onPauseClicked());
        pwBtn.setOnClickListener(v -> askPassword(() -> showSetPasswordDialog(false)));
        accBtn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        adminBtn.setOnClickListener(v -> onAdmin());
        langBtn.setOnClickListener(v -> showLanguageDialog());
        findViewById(R.id.firewallBtn).setOnClickListener(v ->
                startActivity(new Intent(this, FirewallActivity.class)));
        protectBox.setOnClickListener(v -> onProtectClicked());

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                filter = s.toString().trim().toLowerCase(Locale.ROOT);
                applyFilter();
            }
        });

        if (!Prefs.hasPassword(this)) showSetPasswordDialog(true);
        loadApps();
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    // ------------------------------------------------------------------ UI state

    private void refresh() {
        boolean blocking = Prefs.isBlocking(this);
        boolean paused = Prefs.isPaused(this);
        boolean acc = isAccessibilityEnabled();
        boolean admin = isAdminActive();

        StringBuilder sb = new StringBuilder();
        if (!blocking) {
            sb.append(getString(R.string.status_off));
            statusText.setTextColor(getColor(R.color.off_red));
        } else if (paused) {
            long left = (Prefs.getPauseUntil(this) - System.currentTimeMillis()) / 1000;
            sb.append(getString(R.string.status_paused, left / 60, left % 60));
            statusText.setTextColor(getColor(R.color.off_red));
        } else {
            sb.append(getString(R.string.status_on));
            statusText.setTextColor(getColor(R.color.on_green));
        }
        if (blocking && !acc) sb.append(getString(R.string.status_acc_warning));
        statusText.setText(sb);

        toggleBtn.setText(blocking ? R.string.btn_turn_off : R.string.btn_turn_on);

        pauseBtn.setEnabled(blocking);
        pauseBtn.setText(paused ? R.string.btn_resume : R.string.btn_pause);

        accBtn.setText(acc ? R.string.btn_acc_on : R.string.btn_acc_off);
        accBtn.setEnabled(!acc);

        adminBtn.setText(admin ? R.string.btn_admin_on : R.string.btn_admin_off);
        adminBtn.setEnabled(!admin);

        protectBox.setChecked(Prefs.isProtect(this));
    }

    // ------------------------------------------------------------------ actions

    private void onToggle() {
        if (Prefs.isBlocking(this)) {
            askPassword(() -> {
                Prefs.setBlocking(this, false);
                toast(R.string.toast_blocking_off);
                refresh();
            });
        } else {
            if (!isAccessibilityEnabled()) {
                toast(R.string.toast_enable_acc_first);
                return;
            }
            Prefs.setBlocking(this, true);
            toast(R.string.toast_blocking_on);
            refresh();
        }
    }

    private void onPauseClicked() {
        if (!Prefs.isBlocking(this)) return;
        if (Prefs.isPaused(this)) {
            Prefs.setPauseUntil(this, 0); // ادامه دادن رمز نمی‌خواهد
            refresh();
            return;
        }
        askPassword(() -> {
            Prefs.setPauseUntil(this, System.currentTimeMillis() + PAUSE_MS);
            refresh();
        });
    }

    private void onAdmin() {
        if (Prefs.isActive(this) && Prefs.isProtect(this)) {
            toast(R.string.toast_admin_while_on);
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent());
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation));
        startActivity(i);
    }

    private void onProtectClicked() {
        boolean wanted = protectBox.isChecked();
        if (!wanted && Prefs.isBlocking(this)) {
            protectBox.setChecked(true);
            askPassword(() -> { Prefs.setProtect(this, false); refresh(); });
        } else {
            Prefs.setProtect(this, wanted);
        }
    }

    private void onAppClicked(AppItem item) {
        Set<String> blocked = Prefs.getBlocked(this);
        if (blocked.contains(item.pkg)) {
            Runnable remove = () -> {
                Set<String> b = Prefs.getBlocked(this);
                b.remove(item.pkg);
                Prefs.setBlocked(this, b);
                adapter.notifyDataSetChanged();
            };
            // برداشتن از لیست وقتی مسدودسازی روشن است رمز می‌خواهد
            if (Prefs.isBlocking(this)) askPassword(remove); else remove.run();
        } else {
            blocked.add(item.pkg); // اضافه کردن همیشه آزاد است
            Prefs.setBlocked(this, blocked);
            adapter.notifyDataSetChanged();
        }
    }

    // ------------------------------------------------------------------ password dialogs

    private EditText pwField(int hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void askPassword(Runnable onOk) {
        EditText input = pwField(R.string.pw_hint);
        LinearLayout box = padded(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.pw_enter_title)
                .setView(box)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    if (Prefs.checkPassword(this, input.getText().toString())) onOk.run();
                    else toast(R.string.toast_wrong_password);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSetPasswordDialog(boolean firstTime) {
        EditText p1 = pwField(R.string.pw_new_hint);
        EditText p2 = pwField(R.string.pw_repeat_hint);
        LinearLayout box = padded(p1, p2);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(firstTime ? R.string.pw_create_title : R.string.pw_change_title)
                .setMessage(firstTime ? getString(R.string.pw_create_msg) : null)
                .setView(box)
                .setCancelable(!firstTime)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String a = p1.getText().toString();
                    String c = p2.getText().toString();
                    if (a.length() < 4) {
                        toast(R.string.toast_pw_short);
                        showSetPasswordDialog(firstTime);
                    } else if (!a.equals(c)) {
                        toast(R.string.toast_pw_mismatch);
                        showSetPasswordDialog(firstTime);
                    } else {
                        Prefs.setPassword(this, a);
                        toast(R.string.toast_pw_saved);
                    }
                });
        if (!firstTime) b.setNegativeButton(R.string.cancel, null);
        b.show();
    }

    private LinearLayout padded(View... views) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (20 * getResources().getDisplayMetrics().density);
        l.setPadding(p, p / 2, p, 0);
        for (View v : views) l.addView(v);
        return l;
    }

    // ------------------------------------------------------------------ helpers

    private ComponentName adminComponent() { return new ComponentName(this, AdminReceiver.class); }

    private boolean isAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isAdminActive(adminComponent());
    }

    private boolean isAccessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        ComponentName me = new ComponentName(this, BlockerService.class);
        for (String s : enabled.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (me.equals(cn)) return true;
        }
        return false;
    }

    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show(); }

    private void showLanguageDialog() {
        final String[] codes = {"", "en", "fa"};
        String[] names = {getString(R.string.lang_system), "English", "فارسی"};
        int current = 0;
        String lang = Prefs.getLanguage(this);
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(lang)) current = i;
        new AlertDialog.Builder(this)
                .setTitle(R.string.lang_title)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    d.dismiss();
                    if (!codes[which].equals(Prefs.getLanguage(this))) {
                        Prefs.setLanguage(this, codes[which]);
                        recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ app list

    private void loadApps() {
        PackageManager pm = getPackageManager();
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            List<AppItem> result = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                AppItem it = new AppItem();
                it.pkg = pkg;
                it.label = ri.loadLabel(pm).toString();
                it.icon = ri.loadIcon(pm);
                result.add(it);
            }
            Set<String> blocked = Prefs.getBlocked(this);
            result.sort((a, b) -> {
                boolean ba = blocked.contains(a.pkg), bb = blocked.contains(b.pkg);
                if (ba != bb) return ba ? -1 : 1;
                return a.label.compareToIgnoreCase(b.label);
            });
            runOnUiThread(() -> {
                allApps.clear();
                allApps.addAll(result);
                applyFilter();
            });
        }).start();
    }

    private void applyFilter() {
        shown.clear();
        for (AppItem it : allApps) {
            if (filter.isEmpty()
                    || it.label.toLowerCase(Locale.ROOT).contains(filter)
                    || it.pkg.toLowerCase(Locale.ROOT).contains(filter)) {
                shown.add(it);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_app, parent, false);
            AppItem it = shown.get(pos);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(it.icon);
            ((TextView) v.findViewById(R.id.label)).setText(it.label);
            ((TextView) v.findViewById(R.id.pkg)).setText(it.pkg);
            ((CheckBox) v.findViewById(R.id.check)).setChecked(Prefs.getBlocked(MainActivity.this).contains(it.pkg));
            return v;
        }
    }
}

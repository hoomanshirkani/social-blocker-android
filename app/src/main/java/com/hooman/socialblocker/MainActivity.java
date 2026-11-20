package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final long PAUSE_MS = 15 * 60 * 1000L;

    private View statusCard, rowAcc, rowAdmin, rowFw, rowEye;
    private TextView statusIcon, statusTitle, statusSub, accWarning, appsHeader;
    private Button toggleBtn, pauseBtn;
    private Switch protectSwitch, limitSwitch;
    private ProgressBar limitProgress;
    private TextView limitUsed;
    private EditText limitVal;
    private Spinner limitUnit;
    private ListView list;
    private AppLoader.Adapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Prefs.initDefaults(this);

        list = findViewById(R.id.appList);
        View h = getLayoutInflater().inflate(R.layout.main_header, list, false);
        list.addHeaderView(h, null, false);

        statusCard = h.findViewById(R.id.statusCard);
        statusIcon = h.findViewById(R.id.statusIcon);
        statusTitle = h.findViewById(R.id.statusTitle);
        statusSub = h.findViewById(R.id.statusSub);
        toggleBtn = h.findViewById(R.id.toggleBtn);
        pauseBtn = h.findViewById(R.id.pauseBtn);
        accWarning = h.findViewById(R.id.accWarning);
        rowAcc = h.findViewById(R.id.rowAcc);
        rowAdmin = h.findViewById(R.id.rowAdmin);
        rowFw = h.findViewById(R.id.rowFw);
        rowEye = h.findViewById(R.id.rowEye);
        protectSwitch = h.findViewById(R.id.protectSwitch);
        appsHeader = h.findViewById(R.id.appsHeader);
        EditText search = h.findViewById(R.id.search);
        limitSwitch = h.findViewById(R.id.limitSwitch);
        limitProgress = h.findViewById(R.id.limitProgress);
        limitUsed = h.findViewById(R.id.limitUsed);
        limitVal = h.findViewById(R.id.limitVal);
        limitUnit = h.findViewById(R.id.limitUnit);

        ArrayAdapter<CharSequence> units = ArrayAdapter.createFromResource(
                this, R.array.limit_units, android.R.layout.simple_spinner_item);
        units.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        limitUnit.setAdapter(units);
        limitSwitch.setChecked(Prefs.isLimitEnabled(this));
        limitVal.setText(String.valueOf(Prefs.getLimitVal(this)));
        limitUnit.setSelection(Prefs.getLimitUnit(this));
        h.findViewById(R.id.limitSave).setOnClickListener(v -> onSaveLimit());
        // پیش‌تنظیم‌ها فقط فرم را پر می‌کنند؛ با «ذخیره» اعمال می‌شوند
        h.findViewById(R.id.limitP15).setOnClickListener(v -> limitPreset(15, 0));
        h.findViewById(R.id.limitP30).setOnClickListener(v -> limitPreset(30, 0));
        h.findViewById(R.id.limitP60).setOnClickListener(v -> limitPreset(1, 1));
        h.findViewById(R.id.limitP120).setOnClickListener(v -> limitPreset(2, 1));

        adapter = new AppLoader.Adapter(this, pkg -> Prefs.getBlocked(this).contains(pkg));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i >= 0 && i < adapter.getCount()) onAppClicked(adapter.at(i).pkg);
        });

        toggleBtn.setOnClickListener(v -> onToggle());
        pauseBtn.setOnClickListener(v -> onPauseClicked());
        rowAcc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        rowAdmin.setOnClickListener(v -> onAdmin());
        rowFw.setOnClickListener(v -> startActivity(new Intent(this, FirewallActivity.class)));
        rowEye.setOnClickListener(v -> startActivity(new Intent(this, EyeCareActivity.class)));
        protectSwitch.setOnClickListener(v -> onProtectClicked());
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { adapter.setFilter(s.toString()); }
        });

        h.findViewById(R.id.chipSocial).setOnClickListener(v -> addAll(installedSocial()));
        h.findViewById(R.id.chipAll).setOnClickListener(v -> addAll(adapter.shownPkgs()));
        h.findViewById(R.id.chipNone).setOnClickListener(v -> removeAll(adapter.shownPkgs()));

        if (!Prefs.hasPassword(this)) showSetPasswordDialog(true);
        AppLoader.load(this, Prefs.getBlocked(this), adapter::setItems);
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    // ------------------------------------------------------------------ menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_password) {
            PasswordDialog.ask(this, () -> showSetPasswordDialog(false));
            return true;
        } else if (id == R.id.action_lock) {
            PasswordDialog.lock();
            toast(R.string.toast_locked);
            refresh();
            return true;
        } else if (id == R.id.action_language) {
            showLanguageDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ UI state

    private void refresh() {
        boolean blocking = Prefs.isBlocking(this);
        boolean paused = Prefs.isPaused(this);
        boolean acc = isAccessibilityEnabled();
        boolean admin = isAdminActive();
        int count = Prefs.getBlocked(this).size();

        if (!blocking) {
            statusCard.setBackgroundResource(R.drawable.bg_status_off);
            statusIcon.setText("🔓");
            statusTitle.setText(R.string.status_off_title);
            statusSub.setText(R.string.status_off_sub);
            toggleBtn.setText(R.string.btn_turn_on);
            pauseBtn.setVisibility(View.GONE);
        } else if (paused) {
            long left = Prefs.getPauseUntil(this) - System.currentTimeMillis();
            statusCard.setBackgroundResource(R.drawable.bg_status_paused);
            statusIcon.setText("⏸");
            statusTitle.setText(R.string.status_paused_title);
            statusSub.setText(getString(R.string.status_paused_sub, fmt(left)));
            toggleBtn.setText(R.string.btn_turn_off);
            pauseBtn.setText(R.string.btn_resume);
            pauseBtn.setVisibility(View.VISIBLE);
        } else {
            statusCard.setBackgroundResource(R.drawable.bg_status_on);
            statusIcon.setText("🔒");
            statusTitle.setText(R.string.status_on_title);
            statusSub.setText(Prefs.isLimitEnabled(this)
                    ? getString(R.string.status_on_sub_limit, count, fmt(Math.max(0,
                            Prefs.getLimitMs(this) - Prefs.getUsedToday(this))))
                    : getString(R.string.status_on_sub, count));
            toggleBtn.setText(R.string.btn_turn_off);
            pauseBtn.setText(R.string.btn_pause);
            pauseBtn.setVisibility(View.VISIBLE);
        }
        accWarning.setVisibility(blocking && !acc ? View.VISIBLE : View.GONE);
        if (PasswordDialog.isUnlocked()) {
            statusSub.append("\n" + getString(R.string.status_unlocked, fmt(PasswordDialog.unlockedRemaining())));
        }

        setRow(rowAcc, "1", R.string.row_acc_title,
                getString(acc ? R.string.row_done : R.string.row_acc_todo), acc);
        setRow(rowAdmin, "2", R.string.row_admin_title,
                getString(admin ? R.string.row_done : R.string.row_admin_todo), admin);
        boolean fw = Prefs.isFwEnabled(this);
        int fwSub = !fw ? R.string.row_fw_off
                : Prefs.isSchedEnabled(this) ? R.string.row_fw_sched : R.string.row_fw_on;
        setRow(rowFw, "🌐", R.string.row_fw_title, getString(fwSub), fw);

        boolean eye = Prefs.isEyeEnabled(this), filter = Prefs.isFilterEnabled(this);
        String eyeSub;
        if (eye && filter) eyeSub = getString(R.string.row_eye_both, Prefs.getEyeIntervalMin(this));
        else if (eye) eyeSub = getString(R.string.row_eye_breaks, Prefs.getEyeIntervalMin(this));
        else if (filter) eyeSub = getString(R.string.row_eye_filter);
        else eyeSub = getString(R.string.row_eye_off);
        setRow(rowEye, "👁", R.string.row_eye_title, eyeSub, eye || filter);

        protectSwitch.setChecked(Prefs.isProtect(this));

        if (Prefs.isLimitEnabled(this)) {
            long limit = Prefs.getLimitMs(this), used = Math.min(Prefs.getUsedToday(this), limit);
            limitProgress.setVisibility(View.VISIBLE);
            limitProgress.setProgress((int) (used * 1000 / Math.max(1, limit)));
            limitUsed.setText(getString(R.string.limit_used, fmt(used), fmt(limit)));
        } else {
            limitProgress.setVisibility(View.GONE);
            limitUsed.setText(R.string.limit_off_text);
        }
        appsHeader.setText(getString(R.string.apps_header, count));
    }

    private void setRow(View row, String icon, int title, String sub, boolean done) {
        TextView i = row.findViewById(R.id.rowIcon);
        i.setText(done ? "✓" : icon);
        i.setBackgroundResource(done ? R.drawable.bg_icon_done : R.drawable.bg_icon_todo);
        i.setTextColor(getColor(done ? R.color.success : R.color.primary));
        ((TextView) row.findViewById(R.id.rowTitle)).setText(title);
        TextView s = row.findViewById(R.id.rowSub);
        s.setText(sub);
        s.setTextColor(getColor(done ? R.color.success : R.color.text_muted));
    }

    static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    // ------------------------------------------------------------------ actions

    private void onToggle() {
        if (Prefs.isBlocking(this)) {
            PasswordDialog.ask(this, () -> {
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
        PasswordDialog.ask(this, () -> {
            Prefs.setPauseUntil(this, System.currentTimeMillis() + PAUSE_MS);
            refresh();
        });
    }

    private void onAdmin() {
        if (isAdminActive()) return;
        if (Prefs.isActive(this) && Prefs.isProtect(this)) {
            toast(R.string.toast_admin_while_on);
            return;
        }
        Intent i = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent());
        i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_explanation));
        startActivity(i);
    }

    private void limitPreset(int val, int unit) {
        limitVal.setText(String.valueOf(val));
        limitUnit.setSelection(unit);
        limitSwitch.setChecked(true);
    }

    /**
     * دادن زمان بیشتر = شل کردن؛ پس وقتی مسدودسازی روشن است رمز می‌خواهد.
     * (روشن کردن سهمیه هم شل کردن است، چون بدون سهمیه اپ‌ها کاملاً بسته‌اند.)
     */
    private void onSaveLimit() {
        int val;
        try {
            val = Integer.parseInt(limitVal.getText().toString().trim());
        } catch (NumberFormatException e) {
            val = 0;
        }
        boolean enable = limitSwitch.isChecked();
        int unit = Math.max(0, limitUnit.getSelectedItemPosition());
        if (enable && val <= 0) {
            toast(R.string.limit_invalid);
            return;
        }
        final int v = Math.max(val, 1);
        long newAllowance = enable ? v * Prefs.LIMIT_UNIT_MS[unit] : 0;
        Runnable save = () -> {
            Prefs.setLimit(this, enable, v, unit);
            toast(R.string.limit_saved);
            refresh();
        };
        if (Prefs.isBlocking(this) && newAllowance > Prefs.getAllowanceMs(this)) {
            PasswordDialog.ask(this, save);
        } else {
            save.run();
        }
    }

    private void onProtectClicked() {
        boolean wanted = protectSwitch.isChecked();
        if (!wanted && Prefs.isBlocking(this)) {
            protectSwitch.setChecked(true);
            PasswordDialog.ask(this, () -> { Prefs.setProtect(this, false); refresh(); });
        } else {
            Prefs.setProtect(this, wanted);
        }
    }

    private void onAppClicked(String pkg) {
        Set<String> blocked = Prefs.getBlocked(this);
        if (blocked.contains(pkg)) {
            Runnable remove = () -> {
                Set<String> b = Prefs.getBlocked(this);
                b.remove(pkg);
                Prefs.setBlocked(this, b);
                adapter.notifyDataSetChanged();
                refresh();
            };
            // برداشتن از لیست وقتی مسدودسازی روشن است رمز می‌خواهد
            if (Prefs.isBlocking(this)) PasswordDialog.ask(this, remove); else remove.run();
        } else {
            blocked.add(pkg); // اضافه کردن همیشه آزاد است
            Prefs.setBlocked(this, blocked);
            adapter.notifyDataSetChanged();
            refresh();
        }
    }

    // ------------------------------------------------------------------ bulk select

    private java.util.List<String> installedSocial() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String pkg : Prefs.DEFAULT_SOCIAL) {
            try {
                getPackageManager().getApplicationInfo(pkg, 0);
                out.add(pkg);
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** اضافه کردن به لیست همیشه آزاد است. */
    private void addAll(Collection<String> pkgs) {
        Set<String> b = Prefs.getBlocked(this);
        b.addAll(pkgs);
        Prefs.setBlocked(this, b);
        adapter.notifyDataSetChanged();
        refresh();
        Toast.makeText(this, getString(R.string.toast_selected, b.size()), Toast.LENGTH_SHORT).show();
    }

    /** برداشتن از لیست وقتی مسدودسازی روشن است رمز می‌خواهد. */
    private void removeAll(Collection<String> pkgs) {
        Runnable r = () -> {
            Set<String> b = Prefs.getBlocked(this);
            b.removeAll(pkgs);
            Prefs.setBlocked(this, b);
            adapter.notifyDataSetChanged();
            refresh();
        };
        if (Prefs.isBlocking(this)) PasswordDialog.ask(this, r); else r.run();
    }

    // ------------------------------------------------------------------ dialogs

    private void showSetPasswordDialog(boolean firstTime) {
        EditText p1 = PasswordDialog.field(this, R.string.pw_new_hint);
        EditText p2 = PasswordDialog.field(this, R.string.pw_repeat_hint);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(firstTime ? R.string.pw_create_title : R.string.pw_change_title)
                .setMessage(firstTime ? getString(R.string.pw_create_msg) : null)
                .setView(PasswordDialog.box(this, p1, p2))
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
            if (me.equals(ComponentName.unflattenFromString(s))) return true;
        }
        return false;
    }

    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show(); }
}

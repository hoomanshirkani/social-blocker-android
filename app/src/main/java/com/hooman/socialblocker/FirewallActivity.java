package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Set;

/** صفحه فایروال: قطع اینترنت اپ‌ها + برنامه زمانی اینترنت. */
public class FirewallActivity extends Activity {

    private static final int REQ_VPN = 42;

    private View card;
    private TextView icon, title, sub, appsHeader;
    private Switch fwSwitch, schedSwitch;
    private EditText onVal, everyVal;
    private Spinner onUnit, everyUnit;
    private ListView list;
    private AppLoader.Adapter adapter;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override public void run() { refreshStatus(); handler.postDelayed(this, 1000); }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firewall);
        setTitle(R.string.fw_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        list = findViewById(R.id.fwList);
        View h = getLayoutInflater().inflate(R.layout.firewall_header, list, false);
        list.addHeaderView(h, null, false);

        card = h.findViewById(R.id.fwCard);
        icon = h.findViewById(R.id.fwIcon);
        title = h.findViewById(R.id.fwTitle);
        sub = h.findViewById(R.id.fwSub);
        fwSwitch = h.findViewById(R.id.fwSwitch);
        schedSwitch = h.findViewById(R.id.schedSwitch);
        onVal = h.findViewById(R.id.onVal);
        everyVal = h.findViewById(R.id.everyVal);
        onUnit = h.findViewById(R.id.onUnit);
        everyUnit = h.findViewById(R.id.everyUnit);
        appsHeader = h.findViewById(R.id.fwAppsHeader);
        EditText search = h.findViewById(R.id.fwSearch);

        ArrayAdapter<CharSequence> units = ArrayAdapter.createFromResource(
                this, R.array.time_units, android.R.layout.simple_spinner_item);
        units.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        onUnit.setAdapter(units);
        everyUnit.setAdapter(units);

        // مقادیر ذخیره‌شده
        schedSwitch.setChecked(Prefs.isSchedEnabled(this));
        setScheduleFields(Prefs.getSchedOnVal(this), Prefs.getSchedOnUnit(this),
                Prefs.getSchedEveryVal(this), Prefs.getSchedEveryUnit(this));

        fwSwitch.setOnClickListener(v -> onFirewallClicked());
        h.findViewById(R.id.saveSched).setOnClickListener(v -> onSaveSchedule());
        // پیش‌تنظیم‌ها فقط فرم را پر می‌کنند؛ با «ذخیره» اعمال می‌شوند
        h.findViewById(R.id.preset1).setOnClickListener(v -> preset(10, 0, 1, 1));
        h.findViewById(R.id.preset2).setOnClickListener(v -> preset(5, 1, 1, 2));
        h.findViewById(R.id.preset3).setOnClickListener(v -> preset(30, 1, 1, 3));

        adapter = new AppLoader.Adapter(this, pkg -> Prefs.getFwBlocked(this).contains(pkg));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i >= 0 && i < adapter.getCount()) onAppClicked(adapter.at(i).pkg);
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { adapter.setFilter(s.toString()); }
        });
        AppLoader.load(this, Prefs.getFwBlocked(this), adapter::setItems);
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    // ------------------------------------------------------------------ firewall on/off

    private void onFirewallClicked() {
        if (fwSwitch.isChecked()) {
            fwSwitch.setChecked(false);
            Intent consent = VpnService.prepare(this);
            if (consent != null) startActivityForResult(consent, REQ_VPN);
            else enableFirewall();
        } else {
            fwSwitch.setChecked(true);
            PasswordDialog.ask(this, () -> {
                Prefs.setFwEnabled(this, false);
                FirewallVpnService.refresh(this);
                refreshStatus();
            });
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) enableFirewall();
            else toast(R.string.fw_vpn_denied);
        }
    }

    private void enableFirewall() {
        Prefs.setFwEnabled(this, true);
        FirewallVpnService.refresh(this);
        refreshStatus();
    }

    // ------------------------------------------------------------------ schedule

    private void setScheduleFields(int on, int onU, int every, int evU) {
        onVal.setText(String.valueOf(on));
        onUnit.setSelection(onU);
        everyVal.setText(String.valueOf(every));
        everyUnit.setSelection(evU);
    }

    private void preset(int on, int onU, int every, int evU) {
        setScheduleFields(on, onU, every, evU);
        schedSwitch.setChecked(true);
    }

    private void onSaveSchedule() {
        int on = parse(onVal), every = parse(everyVal);
        int onU = onUnit.getSelectedItemPosition(), evU = everyUnit.getSelectedItemPosition();
        long onMs = on * Prefs.UNIT_MS[onU];
        long periodMs = every * Prefs.UNIT_MS[evU];
        boolean enable = schedSwitch.isChecked();

        if (enable && (on <= 0 || every <= 0 || onMs < 5000 || periodMs - onMs < 5000)) {
            toast(R.string.fw_invalid);
            return;
        }

        Runnable save = () -> {
            Prefs.setSchedule(this, enable, Math.max(on, 1), onU, Math.max(every, 1), evU);
            FirewallVpnService.refresh(this);
            toast(Prefs.isFwEnabled(this) || !enable ? R.string.fw_sched_saved : R.string.fw_need_fw);
            refreshStatus();
        };
        // وقتی برنامه زمانی فعال است، هر تغییری (که ممکن است آن را شل کند) رمز می‌خواهد
        if (Prefs.isFwEnabled(this) && Prefs.isSchedEnabled(this)) {
            PasswordDialog.ask(this, save);
        } else {
            save.run();
        }
    }

    private static int parse(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ app list

    private void onAppClicked(String pkg) {
        Set<String> blocked = Prefs.getFwBlocked(this);
        if (blocked.contains(pkg)) {
            Runnable remove = () -> {
                Set<String> b = Prefs.getFwBlocked(this);
                b.remove(pkg);
                Prefs.setFwBlocked(this, b);
                FirewallVpnService.refresh(this);
                adapter.notifyDataSetChanged();
                refreshStatus();
            };
            if (Prefs.isFwEnabled(this)) PasswordDialog.ask(this, remove); else remove.run();
        } else {
            blocked.add(pkg); // قطع کردن همیشه آزاد است
            Prefs.setFwBlocked(this, blocked);
            FirewallVpnService.refresh(this);
            adapter.notifyDataSetChanged();
            refreshStatus();
        }
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        boolean enabled = Prefs.isFwEnabled(this);
        int count = Prefs.getFwBlocked(this).size();
        fwSwitch.setChecked(enabled);
        appsHeader.setText(getString(R.string.fw_apps_header2, count));

        if (!enabled) {
            show(R.drawable.bg_status_off, "🌐", R.string.fw_off_title, getString(R.string.fw_off_sub));
        } else if (!FirewallVpnService.running) {
            show(R.drawable.bg_status_paused, "⚠️", R.string.fw_nr_title, getString(R.string.fw_nr_sub));
        } else if (Prefs.isSchedEnabled(this) && FirewallVpnService.nextChangeAt > 0) {
            String left = MainActivity.fmt(FirewallVpnService.nextChangeAt - System.currentTimeMillis());
            if (FirewallVpnService.allBlocked) {
                show(R.drawable.bg_status_danger, "🚫", R.string.fw_closed_title,
                        getString(R.string.fw_closed_sub, left));
            } else {
                show(R.drawable.bg_status_on, "✅", R.string.fw_open_title,
                        getString(R.string.fw_open_sub, left));
            }
        } else {
            show(R.drawable.bg_status_on, "🛡", R.string.fw_on_title, getString(R.string.fw_on_sub, count));
        }
    }

    private void show(int bg, String emoji, int t, String s) {
        card.setBackgroundResource(bg);
        icon.setText(emoji);
        title.setText(t);
        sub.setText(s);
    }

    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show(); }
}

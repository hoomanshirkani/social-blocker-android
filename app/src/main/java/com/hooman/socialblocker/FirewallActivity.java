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
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Set;

/** صفحه فایروال: قطع اینترنت اپ‌ها + برنامه زمانی اینترنت. */
public class FirewallActivity extends Activity {

    private static final int REQ_VPN = 42;

    private TextView status;
    private CheckBox fwBox, schedBox;
    private EditText onVal, everyVal;
    private Spinner onUnit, everyUnit;
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

        status = findViewById(R.id.fwStatus);
        fwBox = findViewById(R.id.fwEnabled);
        schedBox = findViewById(R.id.schedEnabled);
        onVal = findViewById(R.id.onVal);
        everyVal = findViewById(R.id.everyVal);
        onUnit = findViewById(R.id.onUnit);
        everyUnit = findViewById(R.id.everyUnit);
        ListView list = findViewById(R.id.fwList);
        EditText search = findViewById(R.id.fwSearch);

        ArrayAdapter<CharSequence> units = ArrayAdapter.createFromResource(
                this, R.array.time_units, android.R.layout.simple_spinner_item);
        units.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        onUnit.setAdapter(units);
        everyUnit.setAdapter(units);

        // مقادیر ذخیره‌شده
        fwBox.setChecked(Prefs.isFwEnabled(this));
        schedBox.setChecked(Prefs.isSchedEnabled(this));
        onVal.setText(String.valueOf(Prefs.getSchedOnVal(this)));
        onUnit.setSelection(Prefs.getSchedOnUnit(this));
        everyVal.setText(String.valueOf(Prefs.getSchedEveryVal(this)));
        everyUnit.setSelection(Prefs.getSchedEveryUnit(this));

        fwBox.setOnClickListener(v -> onFirewallClicked());
        findViewById(R.id.saveSched).setOnClickListener(v -> onSaveSchedule());

        adapter = new AppLoader.Adapter(this, pkg -> Prefs.getFwBlocked(this).contains(pkg));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> onAppClicked(adapter.at(pos).pkg));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { adapter.setFilter(s.toString()); }
        });
        AppLoader.load(this, Prefs.getFwBlocked(this), adapter::setItems);
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    // ------------------------------------------------------------------ firewall on/off

    private void onFirewallClicked() {
        if (fwBox.isChecked()) {
            fwBox.setChecked(false);
            Intent consent = VpnService.prepare(this);
            if (consent != null) startActivityForResult(consent, REQ_VPN);
            else enableFirewall();
        } else {
            fwBox.setChecked(true);
            PasswordDialog.ask(this, () -> {
                Prefs.setFwEnabled(this, false);
                FirewallVpnService.refresh(this);
                fwBox.setChecked(false);
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
        fwBox.setChecked(true);
        FirewallVpnService.refresh(this);
    }

    // ------------------------------------------------------------------ schedule

    private void onSaveSchedule() {
        int on = parse(onVal), every = parse(everyVal);
        int onU = onUnit.getSelectedItemPosition(), evU = everyUnit.getSelectedItemPosition();
        long onMs = on * Prefs.UNIT_MS[onU];
        long periodMs = every * Prefs.UNIT_MS[evU];
        boolean enable = schedBox.isChecked();

        if (enable && (on <= 0 || every <= 0 || onMs < 5000 || periodMs - onMs < 5000)) {
            toast(R.string.fw_invalid);
            return;
        }

        Runnable save = () -> {
            Prefs.setSchedule(this, enable, Math.max(on, 1), onU, Math.max(every, 1), evU);
            FirewallVpnService.refresh(this);
            toast(Prefs.isFwEnabled(this) || !enable ? R.string.fw_sched_saved : R.string.fw_need_fw);
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
            };
            if (Prefs.isFwEnabled(this)) PasswordDialog.ask(this, remove); else remove.run();
        } else {
            blocked.add(pkg); // قطع کردن همیشه آزاد است
            Prefs.setFwBlocked(this, blocked);
            FirewallVpnService.refresh(this);
            adapter.notifyDataSetChanged();
        }
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        String text;
        if (!Prefs.isFwEnabled(this)) {
            text = getString(R.string.fw_status_off);
        } else if (!FirewallVpnService.running) {
            text = getString(R.string.fw_status_not_running);
        } else if (Prefs.isSchedEnabled(this) && FirewallVpnService.nextChangeAt > 0) {
            String left = fmt(FirewallVpnService.nextChangeAt - System.currentTimeMillis());
            text = getString(FirewallVpnService.allBlocked
                    ? R.string.fw_status_closed : R.string.fw_status_open, left);
        } else {
            text = getString(R.string.fw_status_on, Prefs.getFwBlocked(this).size());
        }
        status.setText(text);
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.ROOT, "%d:%02d", m, sec);
    }

    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show(); }
}

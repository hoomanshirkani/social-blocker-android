package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * صفحه فایروال: برای هر اپ یک قانون (آزاد / ⏱ دوره‌ای / ⚡ روشن‌وخاموش صفحه / 🚫 همیشه قطع)
 * + تنظیمات قانون‌های ⚡ و ⏱.
 * هر تغییری که اینترنت بیشتری بدهد، وقتی فایروال روشن است رمز می‌خواهد.
 */
public class FirewallActivity extends Activity {

    private static final int REQ_VPN = 42;
    // ایندکس = مقدار Prefs.FW_* (از آزاد به سخت‌گیرانه)
    private static final int[] RULE_LABELS = {
            R.string.rule_free, R.string.rule_periodic, R.string.rule_inuse, R.string.rule_burst, R.string.rule_always};
    private static final int[] RULE_DESCS = {
            R.string.rule_free_desc, R.string.rule_periodic_desc, R.string.rule_inuse_desc,
            R.string.rule_burst_desc, R.string.rule_always_desc};

    private View card;
    private TextView icon, title, sub, appsHeader;
    private Switch fwSwitch, schedSwitch;
    private Spinner screenOnMode, graceSpin, inUseGrace;
    private Switch inUseAllSwitch;
    private View graceRow;
    private EditText onVal, everyVal, burstSec;
    private Spinner onUnit, everyUnit;
    private ListView list;
    private AppLoader.Adapter adapter;
    /** اپ‌های انتخاب‌شده برای تغییر گروهی قانون */
    private final Set<String> selected = new HashSet<>();
    private TextView chipApply;

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
        screenOnMode = h.findViewById(R.id.screenOnMode);
        graceSpin = h.findViewById(R.id.graceSpin);
        graceRow = h.findViewById(R.id.graceRow);
        inUseGrace = h.findViewById(R.id.inUseGrace);
        inUseAllSwitch = h.findViewById(R.id.inUseAllSwitch);
        onVal = h.findViewById(R.id.onVal);
        everyVal = h.findViewById(R.id.everyVal);
        onUnit = h.findViewById(R.id.onUnit);
        everyUnit = h.findViewById(R.id.everyUnit);
        burstSec = h.findViewById(R.id.burstSec);
        appsHeader = h.findViewById(R.id.fwAppsHeader);
        EditText search = h.findViewById(R.id.fwSearch);

        ArrayAdapter<CharSequence> units = ArrayAdapter.createFromResource(
                this, R.array.time_units, android.R.layout.simple_spinner_item);
        units.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        onUnit.setAdapter(units);
        everyUnit.setAdapter(units);

        // مقادیر ذخیره‌شده
        burstSec.setText(String.valueOf(Prefs.getBurstSec(this)));
        schedSwitch.setChecked(Prefs.isSchedEnabled(this));
        ArrayAdapter<CharSequence> modes = ArrayAdapter.createFromResource(
                this, R.array.screen_on_modes, android.R.layout.simple_spinner_item);
        modes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        screenOnMode.setAdapter(modes);
        ArrayAdapter<CharSequence> graces = ArrayAdapter.createFromResource(
                this, R.array.grace_options, android.R.layout.simple_spinner_item);
        graces.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        graceSpin.setAdapter(graces);
        inUseGrace.setAdapter(graces);
        inUseGrace.setSelection(graceIndex(Prefs.getInUseGraceSec(this)));
        inUseAllSwitch.setChecked(Prefs.isInUseAll(this));
        h.findViewById(R.id.inUseSave).setOnClickListener(v -> onSaveInUse());
        screenOnMode.setSelection(Prefs.getScreenOnMode(this));
        graceSpin.setSelection(graceIndex(Prefs.getGraceSec(this)));
        graceRow.setVisibility(Prefs.getScreenOnMode(this) == Prefs.SCREEN_ON_IN_USE ? View.VISIBLE : View.GONE);
        screenOnMode.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                graceRow.setVisibility(pos == Prefs.SCREEN_ON_IN_USE ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        setScheduleFields(Prefs.getSchedOnVal(this), Prefs.getSchedOnUnit(this),
                Prefs.getSchedEveryVal(this), Prefs.getSchedEveryUnit(this));

        fwSwitch.setOnClickListener(v -> onFirewallClicked());
        h.findViewById(R.id.burstSave).setOnClickListener(v -> onSaveBurst());
        h.findViewById(R.id.saveSched).setOnClickListener(v -> onSaveSchedule());
        // پیش‌تنظیم‌ها فقط فرم را پر می‌کنند؛ با «ذخیره» اعمال می‌شوند
        h.findViewById(R.id.preset1).setOnClickListener(v -> setScheduleFields(10, 0, 1, 1));
        h.findViewById(R.id.preset2).setOnClickListener(v -> setScheduleFields(5, 1, 1, 2));
        h.findViewById(R.id.preset3).setOnClickListener(v -> setScheduleFields(30, 1, 1, 3));

        adapter = new AppLoader.Adapter(this, selected::contains);
        adapter.setBadge(pkg -> getString(RULE_LABELS[Prefs.getFwRule(this, pkg)]));
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= adapter.getCount()) return;
            // در حالت انتخاب: ضربه = انتخاب/لغو؛ در غیر این صورت: انتخاب قانون همان اپ
            if (selected.isEmpty()) pickRule(adapter.at(i));
            else toggle(adapter.at(i).pkg);
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            int i = pos - list.getHeaderViewsCount();
            if (i < 0 || i >= adapter.getCount()) return false;
            toggle(adapter.at(i).pkg);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { adapter.setFilter(s.toString()); }
        });
        chipApply = h.findViewById(R.id.fwChipApply);
        chipApply.setOnClickListener(v -> pickRuleForSelected());
        h.findViewById(R.id.fwChipAll).setOnClickListener(v -> {
            selected.addAll(adapter.shownPkgs()); // همه نتایج جستجوی فعلی
            updateSelection();
        });
        h.findViewById(R.id.fwChipNone).setOnClickListener(v -> {
            selected.clear();
            updateSelection();
        });
        updateSelection();
        if (Prefs.pruneFwRules(this)) FirewallVpnService.refresh(this); // اپ‌های پاک‌شده
        AppLoader.load(this, Prefs.getFwRuled(this), adapter::setItems);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Prefs.pruneFwRules(this)) {
            FirewallVpnService.refresh(this);
            adapter.notifyDataSetChanged();
        }
        handler.post(ticker);
    }

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
                Toast.makeText(this, R.string.fw_off_lockdown_hint, Toast.LENGTH_LONG).show();
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

    // ------------------------------------------------------------------ rule ⚡

    private void onSaveBurst() {
        int sec = parse(burstSec);
        if (sec < 3 || sec > 600) {
            toast(R.string.fw_burst_invalid);
            return;
        }
        Runnable save = () -> {
            Prefs.setBurstSec(this, sec);
            FirewallVpnService.refresh(this);
            toast(R.string.fw_sched_saved);
        };
        // وقت بیشتر = شل‌تر → رمز
        if (Prefs.isFwEnabled(this) && sec > Prefs.getBurstSec(this)) PasswordDialog.ask(this, save);
        else save.run();
    }

    // ------------------------------------------------------------------ rule 📱

    private void onSaveInUse() {
        int sec = Prefs.GRACE_OPTIONS[Math.max(0, inUseGrace.getSelectedItemPosition())];
        boolean all = inUseAllSwitch.isChecked();
        Runnable save = () -> {
            boolean hadPeriodicAll = Prefs.isSchedEnabled(this);
            Prefs.setInUse(this, sec, all);
            if (all && hadPeriodicAll) {
                schedSwitch.setChecked(false);
                toast(R.string.fw_inuse_all_replaced);
            } else {
                toast(Prefs.isFwEnabled(this) ? R.string.fw_sched_saved : R.string.fw_need_fw);
            }
            FirewallVpnService.refresh(this);
            refreshStatus();
        };
        // مهلت بیشتر یا خاموش کردن «همه اپ‌ها» = شل‌تر → رمز
        boolean loosens = sec > Prefs.getInUseGraceSec(this) || (Prefs.isInUseAll(this) && !all);
        if (Prefs.isFwEnabled(this) && loosens) PasswordDialog.ask(this, save);
        else save.run();
    }

    // ------------------------------------------------------------------ rule ⏱

    private void setScheduleFields(int on, int onU, int every, int evU) {
        onVal.setText(String.valueOf(on));
        onUnit.setSelection(onU);
        everyVal.setText(String.valueOf(every));
        everyUnit.setSelection(evU);
    }

    private void onSaveSchedule() {
        int on = parse(onVal), every = parse(everyVal);
        int onU = onUnit.getSelectedItemPosition(), evU = everyUnit.getSelectedItemPosition();
        long onMs = on * Prefs.UNIT_MS[onU];
        long periodMs = every * Prefs.UNIT_MS[evU];
        boolean all = schedSwitch.isChecked();
        int mode = Math.max(0, screenOnMode.getSelectedItemPosition());
        int graceSec = Prefs.GRACE_OPTIONS[Math.max(0, graceSpin.getSelectedItemPosition())];

        if (on <= 0 || every <= 0 || onMs < 5000 || periodMs - onMs < 5000) {
            toast(R.string.fw_invalid);
            return;
        }
        Runnable save = () -> {
            Prefs.setSchedule(this, all, on, onU, every, evU, mode, graceSec);
            if (all) inUseAllSwitch.setChecked(false); // فقط یکی از «همه اپ‌ها»
            FirewallVpnService.refresh(this);
            toast(Prefs.isFwEnabled(this) ? R.string.fw_sched_saved : R.string.fw_need_fw);
            refreshStatus();
        };
        // هر تغییری در زمان‌بندی وقتی فایروال روشن است ممکن است آن را شل کند → رمز
        if (Prefs.isFwEnabled(this)) PasswordDialog.ask(this, save);
        else save.run();
    }

    // ------------------------------------------------------------------ per-app rules

    private void pickRule(AppLoader.AppItem item) {
        int current = Prefs.getFwRule(this, item.pkg);
        CharSequence[] names = new CharSequence[RULE_LABELS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(RULE_LABELS[i]) + "\n" + getString(RULE_DESCS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(item.label)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    d.dismiss();
                    if (which != current) setRule(Collections.singletonList(item.pkg), which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ bulk selection

    private void toggle(String pkg) {
        if (!selected.remove(pkg)) selected.add(pkg);
        updateSelection();
    }

    private void updateSelection() {
        int n = selected.size();
        chipApply.setText(n == 0 ? getString(R.string.chip_fw_apply_none) : getString(R.string.chip_fw_apply, n));
        adapter.setSelectionMode(n > 0);
    }

    private void pickRuleForSelected() {
        if (selected.isEmpty()) {
            toast(R.string.fw_select_first);
            return;
        }
        CharSequence[] names = new CharSequence[RULE_LABELS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = getString(RULE_LABELS[i]) + "\n" + getString(RULE_DESCS[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.fw_bulk_title, selected.size()))
                .setItems(names, (d, which) -> {
                    List<String> pkgs = new ArrayList<>(selected);
                    selected.clear();
                    updateSelection();
                    setRule(pkgs, which);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** تغییر قانون؛ اگر برای هر کدام از اپ‌ها شل‌تر شود و فایروال روشن باشد، رمز می‌خواهد. */
    private void setRule(Collection<String> pkgs, int rule) {
        boolean loosens = false;
        for (String p : pkgs) if (Prefs.getFwRule(this, p) > rule) { loosens = true; break; }
        List<String> copy = new ArrayList<>(pkgs);
        Runnable r = () -> {
            Prefs.setFwRule(this, copy, rule);
            FirewallVpnService.refresh(this);
            adapter.notifyDataSetChanged();
            refreshStatus();
        };
        if (loosens && Prefs.isFwEnabled(this)) PasswordDialog.ask(this, r); else r.run();
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        boolean enabled = Prefs.isFwEnabled(this);
        int nAlways = Prefs.getFwBlocked(this).size();
        int nBurst = Prefs.getFwBurst(this).size();
        int nPeriodic = Prefs.getFwPeriodic(this).size();
        int nInUse = Prefs.getFwInUse(this).size();
        boolean inUseAll = Prefs.isInUseAll(this);
        boolean periodicAll = Prefs.isSchedEnabled(this);
        fwSwitch.setChecked(enabled);
        appsHeader.setText(getString(R.string.fw_apps_header3, nAlways + nBurst + nPeriodic + nInUse));

        if (!enabled) {
            show(R.drawable.bg_status_off, "🌐", R.string.fw_off_title, getString(R.string.fw_off_sub));
            return;
        }
        if (!FirewallVpnService.running) {
            show(R.drawable.bg_status_paused, "⚠️", R.string.fw_nr_title, getString(R.string.fw_nr_sub));
            return;
        }
        long now = System.currentTimeMillis();
        if (FirewallVpnService.allBlocked && !FirewallVpnService.periodicInUse
                && !FirewallVpnService.inUseAllActive) {
            show(R.drawable.bg_status_danger, "🚫", R.string.fw_closed_title,
                    getString(R.string.fw_closed_sub, MainActivity.fmt(FirewallVpnService.nextChangeAt - now)));
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (nAlways > 0) line(sb, getString(R.string.fw_st_always, nAlways));
        if (nInUse > 0 || inUseAll) {
            String who = inUseAll ? getString(R.string.fw_st_all) : String.valueOf(nInUse);
            line(sb, getString(R.string.fw_st_inuse, who,
                    getResources().getStringArray(R.array.grace_options)[graceIndex(Prefs.getInUseGraceSec(this))]));
        }
        if (nBurst > 0) {
            line(sb, FirewallVpnService.burstOpen
                    ? getString(R.string.fw_st_burst_open, nBurst,
                            MainActivity.fmt(FirewallVpnService.burstOpenUntil - now))
                    : getString(R.string.fw_st_burst_closed, nBurst));
        }
        if (nPeriodic > 0 || periodicAll) {
            String who = periodicAll ? getString(R.string.fw_st_all) : String.valueOf(nPeriodic);
            if (FirewallVpnService.scheduleWaiting) {
                line(sb, getString(R.string.fw_st_periodic_screen, who));
            } else if (FirewallVpnService.periodicInUse) {
                line(sb, getString(R.string.fw_st_periodic_inuse, who,
                        getResources().getStringArray(R.array.grace_options)[graceIndex(Prefs.getGraceSec(this))]));
            } else {
                String left = MainActivity.fmt(FirewallVpnService.periodicNextChange - now);
                line(sb, getString(FirewallVpnService.periodicOpen
                        ? R.string.fw_st_periodic_open : R.string.fw_st_periodic_closed, who, left));
            }
        }
        if (sb.length() == 0) sb.append(getString(R.string.fw_st_none));
        show(R.drawable.bg_status_on, "🛡", R.string.fw_on_title, sb.toString());
    }

    private static void line(StringBuilder sb, String s) {
        if (sb.length() > 0) sb.append('\n');
        sb.append(s);
    }

    private void show(int bg, String emoji, int t, String s) {
        card.setBackgroundResource(bg);
        icon.setText(emoji);
        title.setText(t);
        sub.setText(s);
    }

    private static int graceIndex(int sec) {
        for (int i = 0; i < Prefs.GRACE_OPTIONS.length; i++) if (Prefs.GRACE_OPTIONS[i] == sec) return i;
        return 2;
    }

    private static int parse(EditText e) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void toast(int res) { Toast.makeText(this, res, Toast.LENGTH_SHORT).show(); }
}

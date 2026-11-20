package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** خلاصه نوتیفیکیشن‌ها: تنظیمات + لیست نوتیفیکیشن‌های جمع‌شده امروز. */
public class DigestActivity extends Activity {

    private View card;
    private TextView title, sub, accessRow, exceptionsRow, listHeader;
    private Switch digestSwitch;
    private Spinner interval, clearHour;
    private LinearLayout list;

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
        setContentView(R.layout.activity_digest);
        setTitle(R.string.digest_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        card = findViewById(R.id.digestCard);
        title = findViewById(R.id.digestTitle);
        sub = findViewById(R.id.digestSub);
        accessRow = findViewById(R.id.accessRow);
        exceptionsRow = findViewById(R.id.exceptionsRow);
        listHeader = findViewById(R.id.digestListHeader);
        digestSwitch = findViewById(R.id.digestSwitch);
        interval = findViewById(R.id.digestInterval);
        clearHour = findViewById(R.id.digestClearHour);
        list = findViewById(R.id.digestList);

        ArrayAdapter<CharSequence> iv = ArrayAdapter.createFromResource(
                this, R.array.digest_intervals, android.R.layout.simple_spinner_item);
        iv.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        interval.setAdapter(iv);
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.ROOT, "%02d:59", i);
        ArrayAdapter<String> ha = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        ha.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        clearHour.setAdapter(ha);

        digestSwitch.setChecked(Prefs.isDigestEnabled(this));
        interval.setSelection(Prefs.getDigestIntervalIdx(this));
        clearHour.setSelection(Prefs.getDigestClearHour(this));

        accessRow.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {}
        });
        exceptionsRow.setOnClickListener(v -> pickExceptions());
        findViewById(R.id.digestSave).setOnClickListener(v -> save());
        findViewById(R.id.digestClearAll).setOnClickListener(v -> {
            DigestScheduler.clearAll(this);
            renderList();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // خلاصه دیده شد → نوتیفیکیشن خلاصه را بردار
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(DigestScheduler.NOTIF_ID);
        accessRow.setVisibility(isListenerEnabled() ? View.GONE : View.VISIBLE);
        exceptionsRow.setText(getString(R.string.digest_exceptions_row, Prefs.getDigestExceptions(this).size()));
        renderList();
        handler.post(ticker);
    }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    // ------------------------------------------------------------------ settings

    private void save() {
        boolean en = digestSwitch.isChecked();
        Prefs.setDigest(this, en, Math.max(0, interval.getSelectedItemPosition()),
                Math.max(0, clearHour.getSelectedItemPosition()));
        DigestScheduler.schedule(this);
        if (en) {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 7);
            }
            if (isListenerEnabled()) {
                // نوتیفیکیشن‌های فعلی هم جمع شوند
                NotificationListenerService.requestRebind(new ComponentName(this, DigestListener.class));
            } else {
                Toast.makeText(this, R.string.digest_access_needed, Toast.LENGTH_LONG).show();
            }
        }
        Toast.makeText(this, R.string.eye_saved_generic, Toast.LENGTH_SHORT).show();
        refreshStatus();
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

    /** اپ‌هایی که نوتیفیکیشنشان همیشه مستقیم می‌آید */
    private void pickExceptions() {
        PackageManager pm = getPackageManager();
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            Map<String, String> apps = new LinkedHashMap<>();
            List<String[]> pairs = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                pairs.add(new String[]{ri.loadLabel(pm).toString(), pkg});
            }
            Collections.sort(pairs, (a, b) -> a[0].compareToIgnoreCase(b[0]));
            for (String[] p : pairs) apps.put(p[1], p[0]);
            runOnUiThread(() -> showExceptionsDialog(apps));
        }).start();
    }

    private void showExceptionsDialog(Map<String, String> apps) {
        List<String> pkgs = new ArrayList<>(apps.keySet());
        CharSequence[] names = new CharSequence[pkgs.size()];
        boolean[] checked = new boolean[pkgs.size()];
        Set<String> ex = Prefs.getDigestExceptions(this);
        for (int i = 0; i < pkgs.size(); i++) {
            names[i] = apps.get(pkgs.get(i));
            checked[i] = ex.contains(pkgs.get(i));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.digest_exceptions_title)
                .setMultiChoiceItems(names, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(R.string.save, (d, w) -> {
                    Set<String> out = new HashSet<>();
                    for (int i = 0; i < pkgs.size(); i++) if (checked[i]) out.add(pkgs.get(i));
                    Prefs.setDigestExceptions(this, out);
                    exceptionsRow.setText(getString(R.string.digest_exceptions_row, out.size()));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ status + list

    private void refreshStatus() {
        int n = DigestStore.load(this).size();
        if (!Prefs.isDigestEnabled(this)) {
            card.setBackgroundResource(R.drawable.bg_status_off);
            title.setText(R.string.digest_off_title);
            sub.setText(R.string.digest_off_sub);
        } else if (!isListenerEnabled()) {
            card.setBackgroundResource(R.drawable.bg_status_paused);
            title.setText(R.string.digest_need_access_title);
            sub.setText(R.string.digest_access_needed);
        } else {
            card.setBackgroundResource(R.drawable.bg_status_on);
            title.setText(getString(R.string.digest_on_title, n));
            long next = Prefs.getDigestNext(this) - System.currentTimeMillis();
            sub.setText(next > 0 ? getString(R.string.digest_on_sub, MainActivity.fmt(next))
                    : getString(R.string.digest_on_sub_soon));
        }
    }

    private void renderList() {
        list.removeAllViews();
        List<DigestStore.Item> items = DigestStore.load(this);
        listHeader.setText(getString(R.string.digest_list_header, items.size()));
        if (items.isEmpty()) {
            list.addView(text(getString(R.string.digest_empty), 14, false, getColor(R.color.text_muted)));
            return;
        }
        // گروه‌بندی بر اساس اپ، جدیدترین اول
        Map<String, List<DigestStore.Item>> byApp = new LinkedHashMap<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            DigestStore.Item it = items.get(i);
            List<DigestStore.Item> l = byApp.get(it.pkg);
            if (l == null) byApp.put(it.pkg, l = new ArrayList<>());
            l.add(it);
        }
        java.text.DateFormat tf = DateFormat.getTimeFormat(this);
        int pad = dp(14);
        for (Map.Entry<String, List<DigestStore.Item>> e : byApp.entrySet()) {
            String pkg = e.getKey();
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundResource(R.drawable.bg_card);
            box.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            box.setLayoutParams(lp);

            box.addView(text(DigestScheduler.label(this, pkg) + "  ·  " + e.getValue().size(),
                    15, true, getColor(R.color.primary)));
            for (DigestStore.Item it : e.getValue()) {
                TextView t = text((it.title.isEmpty() ? "" : it.title + "\n") + it.text
                        + "\n" + tf.format(new Date(it.time)), 14, false, getColor(R.color.text));
                t.setPadding(0, dp(8), 0, 0);
                box.addView(t);
            }
            box.setOnClickListener(v -> {
                Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
                if (launch != null) startActivity(launch);
            });
            list.addView(box);
        }
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

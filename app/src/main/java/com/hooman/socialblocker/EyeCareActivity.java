package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** تنظیمات مراقبت از چشم: یادآوری استراحت + فیلتر گرم. */
public class EyeCareActivity extends Activity {

    private View card;
    private TextView title, sub, strengthLabel;
    private Switch eyeSwitch, strictSwitch, filterSwitch, nightSwitch;
    private EditText interval, breakLen;
    private SeekBar seek;
    private Spinner fromHour, toHour;
    private boolean loading = true;

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
        setContentView(R.layout.activity_eye_care);
        SystemBars.apply(this);
        setTitle(R.string.eye_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        card = findViewById(R.id.eyeCard);
        title = findViewById(R.id.eyeTitle);
        sub = findViewById(R.id.eyeSub);
        eyeSwitch = findViewById(R.id.eyeSwitch);
        strictSwitch = findViewById(R.id.strictSwitch);
        interval = findViewById(R.id.eyeInterval);
        breakLen = findViewById(R.id.eyeBreak);
        filterSwitch = findViewById(R.id.filterSwitch);
        nightSwitch = findViewById(R.id.nightSwitch);
        seek = findViewById(R.id.filterSeek);
        strengthLabel = findViewById(R.id.filterStrengthLabel);
        fromHour = findViewById(R.id.fromHour);
        toHour = findViewById(R.id.toHour);

        // --- استراحت ---
        eyeSwitch.setChecked(Prefs.isEyeEnabled(this));
        strictSwitch.setChecked(Prefs.isEyeStrict(this));
        Switch smart = findViewById(R.id.smartSwitch);
        smart.setChecked(Prefs.isEyeSmart(this));
        smart.setOnCheckedChangeListener((b, on) -> Prefs.setEyeSmart(this, on));
        interval.setText(String.valueOf(Prefs.getEyeIntervalMin(this)));
        breakLen.setText(String.valueOf(Prefs.getEyeBreakSec(this)));
        findViewById(R.id.eyeSave).setOnClickListener(v -> saveBreaks());
        findViewById(R.id.eyeP1).setOnClickListener(v -> preset(20, 20));
        findViewById(R.id.eyeP2).setOnClickListener(v -> preset(45, 300));
        findViewById(R.id.eyeP3).setOnClickListener(v -> preset(60, 600));
        findViewById(R.id.eyeTest).setOnClickListener(v -> {
            Intent i = new Intent(this, BreakActivity.class);
            i.putExtra(BreakActivity.EXTRA_SECONDS, Math.min(parse(breakLen, 20), 60));
            i.putExtra(BreakActivity.EXTRA_STRICT, false);
            startActivity(i);
        });

        // --- فیلتر (فوراً اعمال می‌شود) ---
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) hours[i] = String.format(Locale.ROOT, "%02d:00", i);
        ArrayAdapter<String> ha = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        ha.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fromHour.setAdapter(ha);
        toHour.setAdapter(ha);

        filterSwitch.setChecked(Prefs.isFilterEnabled(this));
        nightSwitch.setChecked(Prefs.isFilterNightOnly(this));
        seek.setProgress(Prefs.getFilterIntensity(this));
        fromHour.setSelection(Prefs.getFilterFrom(this));
        toHour.setSelection(Prefs.getFilterTo(this));
        updateStrengthLabel();

        filterSwitch.setOnCheckedChangeListener((b, c) -> saveFilter());
        nightSwitch.setOnCheckedChangeListener((b, c) -> saveFilter());
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                updateStrengthLabel();
                if (fromUser) saveFilter();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        AdapterView.OnItemSelectedListener hourListener = new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { saveFilter(); }
            public void onNothingSelected(AdapterView<?> p) {}
        };
        fromHour.setOnItemSelectedListener(hourListener);
        toHour.setOnItemSelectedListener(hourListener);
        // Spinner در اولین layout یک بار onItemSelected صدا می‌زند؛ بعد از آن ذخیره فعال شود
        handler.post(() -> loading = false);
    }

    @Override protected void onResume() { super.onResume(); handler.post(ticker); }

    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(ticker); }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }

    // ------------------------------------------------------------------ breaks

    private void preset(int minutes, int seconds) {
        interval.setText(String.valueOf(minutes));
        breakLen.setText(String.valueOf(seconds));
        eyeSwitch.setChecked(true);
    }

    private void saveBreaks() {
        int min = parse(interval, 0), sec = parse(breakLen, 0);
        if (min < 1 || min > 600 || sec < 5 || sec > 3600 || sec >= min * 60) {
            Toast.makeText(this, R.string.eye_invalid, Toast.LENGTH_LONG).show();
            return;
        }
        boolean en = eyeSwitch.isChecked(), strict = strictSwitch.isChecked();
        Runnable save = () -> {
            Prefs.setEye(this, en, min, sec, strict);
            BlockerService.eyeSettingsChanged();
            Toast.makeText(this, R.string.eye_saved, Toast.LENGTH_SHORT).show();
            refreshStatus();
        };
        // شل کردن (خاموش کردن، برداشتن حالت سخت‌گیرانه، فاصله بیشتر، استراحت کوتاه‌تر)
        // وقتی مسدودسازی روشن است رمز می‌خواهد
        boolean wasOn = Prefs.isEyeEnabled(this);
        boolean loosens = wasOn && (!en
                || (Prefs.isEyeStrict(this) && !strict)
                || min > Prefs.getEyeIntervalMin(this)
                || sec < Prefs.getEyeBreakSec(this));
        if (loosens && Prefs.isBlocking(this)) PasswordDialog.ask(this, save);
        else save.run();
    }

    // ------------------------------------------------------------------ filter

    private void updateStrengthLabel() {
        strengthLabel.setText(getString(R.string.filter_strength, seek.getProgress()));
    }

    private void saveFilter() {
        if (loading) return;
        Prefs.setFilter(this, filterSwitch.isChecked(), seek.getProgress(), nightSwitch.isChecked(),
                fromHour.getSelectedItemPosition(), toHour.getSelectedItemPosition());
        BlockerService.eyeSettingsChanged();
    }

    // ------------------------------------------------------------------ status

    private void refreshStatus() {
        if (BlockerService.instance == null) {
            card.setBackgroundResource(R.drawable.bg_status_paused);
            title.setText(R.string.eye_need_acc_title);
            sub.setText(R.string.eye_need_acc_sub);
        } else if (Prefs.isEyeEnabled(this) && EyeCare.nextBreakAt > 0) {
            card.setBackgroundResource(R.drawable.bg_status_on);
            title.setText(R.string.eye_on_title);
            sub.setText(getString(R.string.eye_on_sub,
                    MainActivity.fmt(EyeCare.nextBreakAt - System.currentTimeMillis())));
        } else if (Prefs.isEyeEnabled(this) || Prefs.isFilterEnabled(this)) {
            card.setBackgroundResource(R.drawable.bg_status_on);
            title.setText(R.string.eye_title);
            sub.setText(Prefs.isFilterEnabled(this) ? R.string.eye_filter_only : R.string.eye_on_title);
        } else {
            card.setBackgroundResource(R.drawable.bg_status_off);
            title.setText(R.string.eye_off_title);
            sub.setText(R.string.eye_off_sub);
        }
    }

    private static int parse(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}

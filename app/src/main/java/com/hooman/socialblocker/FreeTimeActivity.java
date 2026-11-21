package com.hooman.socialblocker;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormatSymbols;
import java.util.Calendar;

/** تنظیم «زمان آزاد» بر اساس روزهای هفته (Pro) */
public class FreeTimeActivity extends Activity {

    private Switch sw;
    private TextView status, startBtn, endBtn;
    private final CheckBox[] boxes = new CheckBox[8]; // ایندکس = Calendar.DAY_OF_WEEK
    private int start, end;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_free_time);
        SystemBars.apply(this);
        setTitle(R.string.ft_title);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);

        sw = findViewById(R.id.ftSwitch);
        status = findViewById(R.id.ftStatus);
        startBtn = findViewById(R.id.ftStart);
        endBtn = findViewById(R.id.ftEnd);

        // روزها به ترتیب هفته‌ی محلی (فارسی از شنبه، آلمان از دوشنبه)
        LinearLayout daysBox = findViewById(R.id.ftDays);
        String[] names = new DateFormatSymbols(getResources().getConfiguration().getLocales().get(0)).getWeekdays();
        int first = "fa".equals(getResources().getConfiguration().getLocales().get(0).getLanguage())
                ? Calendar.SATURDAY : Calendar.MONDAY;
        int days = FreeTime.getDays(this);
        for (int k = 0; k < 7; k++) {
            int d = ((first - 1 + k) % 7) + 1;
            CheckBox cb = new CheckBox(this);
            cb.setText(names[d]);
            cb.setTextSize(16);
            cb.setTextColor(getColor(R.color.text));
            cb.setPadding(8, 8, 8, 8);
            cb.setChecked(FreeTime.hasDay(days, d));
            boxes[d] = cb;
            daysBox.addView(cb);
        }

        sw.setChecked(FreeTime.isEnabled(this));
        start = FreeTime.getStartMin(this);
        end = FreeTime.getEndMin(this);
        startBtn.setOnClickListener(v -> pick(true));
        endBtn.setOnClickListener(v -> pick(false));
        findViewById(R.id.ftSave).setOnClickListener(v -> save());
        bind();
    }

    private void pick(boolean isStart) {
        int m = isStart ? start : end;
        new TimePickerDialog(this, (tp, h, min) -> {
            if (isStart) start = h * 60 + min; else end = h * 60 + min;
            bind();
        }, m / 60, m % 60, true).show();
    }

    private void bind() {
        startBtn.setText(FreeTime.fmt(start));
        endBtn.setText(FreeTime.fmt(end));
        boolean now = FreeTime.isNow(this);
        status.setBackgroundResource(now ? R.drawable.bg_status_paused
                : FreeTime.isEnabled(this) ? R.drawable.bg_status_on : R.drawable.bg_status_off);
        status.setText(now
                ? getString(R.string.ft_status_now, FreeTime.fmt(FreeTime.getStartMin(this)), FreeTime.fmt(FreeTime.getEndMin(this)))
                : getString(FreeTime.isEnabled(this) ? R.string.ft_status_on : R.string.ft_status_off));
    }

    private void save() {
        int days = 0;
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            if (boxes[d] != null && boxes[d].isChecked()) days |= 1 << (d - 1);
        }
        boolean enabled = sw.isChecked();
        if (enabled && days == 0) {
            Toast.makeText(this, R.string.ft_pick_day, Toast.LENGTH_SHORT).show();
            return;
        }
        final int fDays = days;
        Runnable doSave = () -> {
            FreeTime.save(this, enabled, fDays, start, end);
            Toast.makeText(this, R.string.fw_sched_saved, Toast.LENGTH_SHORT).show();
            bind();
        };
        Runnable withPassword = () -> {
            // وقتی مسدودسازی روشن است، هر تغییری می‌تواند قانون را شل کند → رمز
            if (Prefs.isActive(this) && (enabled || FreeTime.isEnabled(this))) PasswordDialog.ask(this, doSave);
            else doSave.run();
        };
        if (enabled) Pro.require(this, withPassword); else withPassword.run();
    }

    @Override
    public boolean onNavigateUp() {
        finish();
        return true;
    }
}

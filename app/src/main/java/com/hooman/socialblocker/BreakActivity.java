package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Random;

/** صفحه استراحت چشم: شمارش معکوس + یک تمرین/نکته. در حالت سخت‌گیرانه دکمه رد کردن ندارد. */
public class BreakActivity extends Activity {

    static final String EXTRA_SECONDS = "seconds";
    static final String EXTRA_STRICT = "strict";
    /** وقتی سرویس صفحه را دوباره جلو می‌آورد؛ شمارش معکوس ادامه پیدا می‌کند */
    static final String EXTRA_RESUME = "resume";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private int total, remaining, lastTip = -1;
    private boolean strict;
    private TextView count, tip, title;
    private ProgressBar bar;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            remaining--;
            if (remaining <= 0) {
                done();
                return;
            }
            show();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.wrap(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_break);

        total = Math.max(5, getIntent().getIntExtra(EXTRA_SECONDS, 20));
        strict = getIntent().getBooleanExtra(EXTRA_STRICT, false);
        remaining = total;

        title = findViewById(R.id.breakTitle);
        count = findViewById(R.id.breakCount);
        tip = findViewById(R.id.breakTip);
        bar = findViewById(R.id.breakProgress);
        bar.setMax(total);

        View skip = findViewById(R.id.breakSkip);
        skip.setVisibility(strict ? View.GONE : View.VISIBLE);
        skip.setOnClickListener(v -> finish());
        findViewById(R.id.breakNextTip).setOnClickListener(v -> nextTip());

        nextTip();
        show();
        handler.postDelayed(tick, 1000);
    }

    private void show() {
        count.setText(MainActivity.fmt(remaining * 1000L));
        bar.setProgress(total - remaining);
    }

    private void nextTip() {
        String[] tips = getResources().getStringArray(R.array.eye_tips);
        if (tips.length == 0) return;
        int i = random.nextInt(tips.length);
        if (tips.length > 1 && i == lastTip) i = (i + 1) % tips.length;
        lastTip = i;
        tip.setText(tips[i]);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        // در حال اجرا هستیم؛ شمارش را از نو شروع نکن
    }

    private void done() {
        if (strict) EyeCare.unlockBreak();
        title.setText(R.string.break_done);
        count.setText("✓");
        bar.setProgress(total);
        findViewById(R.id.breakSkip).setVisibility(View.GONE);
        handler.postDelayed(this::finish, 1500);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!strict) finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

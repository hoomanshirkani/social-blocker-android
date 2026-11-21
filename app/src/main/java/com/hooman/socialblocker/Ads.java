package com.hooman.socialblocker;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * تبلیغ ویدئویی جایزه‌دار (Rewarded) — فقط وقتی خود کاربر دکمه «یک تبلیغ ببین» را بزند.
 * بدون بنر، بدون تبلیغ تمام‌صفحه، هرگز روی صفحه مسدود.
 * قبلش فرم رضایت GDPR گوگل (UMP) نمایش داده می‌شود. فقط در نسخه گوگل‌پلی.
 */
final class Ads {
    private Ads() {}

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static boolean loading = false;

    static boolean available() {
        return Pro.gated();
    }

    /** فرم رضایت (اگر لازم باشد) → بارگذاری تبلیغ → نمایش → ۲۴ ساعت Pro */
    static void watchForTrial(Activity a) {
        if (!available() || loading) return;
        ConsentInformation ci = UserMessagingPlatform.getConsentInformation(a);
        ci.requestConsentInfoUpdate(a, new ConsentRequestParameters.Builder().build(),
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(a, formError -> {
                    if (ci.canRequestAds()) load(a);
                    else toast(a, R.string.ad_no_consent);
                }),
                error -> {
                    // اگر سرور رضایت در دسترس نبود ولی قبلاً رضایت داده شده
                    if (ci.canRequestAds()) load(a);
                    else toast(a, R.string.ad_failed);
                });
    }

    private static void load(Activity a) {
        if (a.isFinishing()) return;
        loading = true;
        toast(a, R.string.ad_loading);
        Runnable go = () -> RewardedAd.load(a, BuildConfig.ADMOB_REWARDED_ID, new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        loading = false;
                        if (a.isFinishing()) return;
                        ad.show(a, reward -> {
                            Pro.grantTrial(a);
                            toast(a, R.string.ad_trial_granted);
                        });
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError e) {
                        loading = false;
                        toast(a, R.string.ad_failed);
                    }
                });
        if (initialized.compareAndSet(false, true)) {
            new Thread(() -> {
                MobileAds.initialize(a.getApplicationContext(), status -> {});
                a.runOnUiThread(go);
            }).start();
        } else {
            go.run();
        }
    }

    /** برای تغییر رضایت تبلیغات (لازم طبق GDPR) */
    static boolean privacyOptionsRequired(Activity a) {
        return available() && UserMessagingPlatform.getConsentInformation(a).getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    static void showPrivacyOptions(Activity a) {
        UserMessagingPlatform.showPrivacyOptionsForm(a, e -> {});
    }

    private static void toast(Activity a, int res) {
        a.runOnUiThread(() -> Toast.makeText(a, res, Toast.LENGTH_SHORT).show());
    }
}

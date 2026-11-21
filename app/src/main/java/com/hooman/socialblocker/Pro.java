package com.hooman.socialblocker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/**
 * نسخه Pro (یک بار خرید، از طریق Google Play Billing).
 *
 * فقط در نسخه گوگل‌پلی قفل است (BuildConfig.PRO_GATED). نسخه متن‌باز GitHub همه
 * امکانات را رایگان دارد و اصلاً به Billing وصل نمی‌شود.
 */
final class Pro {
    private Pro() {}

    /** شناسه محصول در Play Console → Monetize → In-app products */
    static final String PRODUCT_ID = "pro_unlock";

    private static BillingClient client;
    private static ProductDetails details;
    private static Runnable listener;
    private static final Handler main = new Handler(Looper.getMainLooper());

    static boolean gated() {
        return BuildConfig.PRO_GATED;
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_pro", Context.MODE_PRIVATE);
    }

    static boolean isPro(Context c) {
        return !gated() || isOwned(c) || trialUntil(c) > System.currentTimeMillis();
    }

    static boolean isOwned(Context c) {
        return !gated() || sp(c).getBoolean("owned", false);
    }

    // ------------------------------------------------------------------ 24h trial (rewarded ad)

    static final long TRIAL_MS = 24 * 60 * 60 * 1000L;

    /** پایان آزمایش رایگان (۰ = ندارد) */
    static long trialUntil(Context c) {
        return gated() ? sp(c).getLong("trial_until", 0) : 0;
    }

    /** بعد از دیدن کامل ویدئوی جایزه‌دار */
    static void grantTrial(Context c) {
        long until = System.currentTimeMillis() + TRIAL_MS;
        sp(c).edit().putLong("trial_until", until).apply();
        FirewallVpnService.refresh(c);
        notifyChanged();
    }

    private static void setOwned(Context c, boolean owned) {
        boolean was = sp(c).getBoolean("owned", false);
        sp(c).edit().putBoolean("owned", owned).apply();
        if (was != owned) {
            FirewallVpnService.refresh(c); // قانون‌های Pro فایروال فعال/غیرفعال می‌شوند
            notifyChanged();
        }
    }

    /** صفحه Pro برای به‌روز شدن (قیمت، وضعیت خرید) */
    static void setListener(Runnable r) {
        listener = r;
    }

    private static void notifyChanged() {
        Runnable r = listener;
        if (r != null) main.post(r);
    }

    static String price() {
        ProductDetails d = details;
        if (d == null || d.getOneTimePurchaseOfferDetails() == null) return null;
        return d.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    // ------------------------------------------------------------------ billing

    private static synchronized BillingClient client(Context c) {
        if (client == null) {
            final Context app = c.getApplicationContext();
            client = BillingClient.newBuilder(app)
                    .setListener((result, purchases) -> {
                        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            handle(app, purchases, false);
                        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                            refresh(app);
                        }
                    })
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
        }
        return client;
    }

    private static void connected(Context c, Runnable then) {
        BillingClient bc = client(c);
        if (bc.isReady()) {
            then.run();
            return;
        }
        bc.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) then.run();
            }

            @Override
            public void onBillingServiceDisconnected() {}
        });
    }

    /** خریدهای قبلی را بازیابی می‌کند (هنگام باز شدن اپ و دکمه «بازیابی خرید») */
    static void refresh(Context c) {
        if (!gated()) return;
        final Context app = c.getApplicationContext();
        try {
            connected(app, () -> {
                client(app).queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                                .setProductType(BillingClient.ProductType.INAPP).build(),
                        (r, list) -> {
                            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) handle(app, list, true);
                        });
                loadDetails(app);
            });
        } catch (Throwable ignored) {}
    }

    private static void loadDetails(Context app) {
        QueryProductDetailsParams p = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        client(app).queryProductDetailsAsync(p, (r, result) -> {
            List<ProductDetails> list = result == null ? null : result.getProductDetailsList();
            if (list != null && !list.isEmpty()) {
                details = list.get(0);
                notifyChanged();
            }
        });
    }

    /** complete=true یعنی لیست کامل خریدهاست (پس نبودن خرید = بازپرداخت شده) */
    private static void handle(Context app, List<Purchase> purchases, boolean complete) {
        boolean owned = false;
        for (Purchase p : purchases) {
            if (!p.getProducts().contains(PRODUCT_ID)) continue;
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            owned = true;
            if (!p.isAcknowledged()) { // بدون تأیید، گوگل بعد از ۳ روز پول را برمی‌گرداند
                client(app).acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.getPurchaseToken()).build(), r -> {});
            }
        }
        if (owned || complete) setOwned(app, owned);
    }

    static void buy(Activity a) {
        if (!gated()) return;
        connected(a, () -> {
            ProductDetails d = details;
            if (d == null) {
                loadDetails(a.getApplicationContext());
                main.post(() -> Toast.show(a, R.string.pro_unavailable));
                return;
            }
            BillingFlowParams params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(
                            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                    .build();
            main.post(() -> client(a).launchBillingFlow(a, params));
        });
    }

    // ------------------------------------------------------------------ gate

    /** اگر Pro است کار را انجام بده؛ وگرنه پیشنهاد خرید Pro. */
    static void require(Activity a, Runnable ifPro) {
        if (isPro(a)) {
            ifPro.run();
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a)
                .setTitle(R.string.pro_needed_title)
                .setMessage(R.string.pro_needed_msg)
                .setPositiveButton(R.string.pro_see, (d, w) -> a.startActivity(new Intent(a, ProActivity.class)))
                .setNegativeButton(R.string.cancel, null);
        if (Ads.available()) b.setNeutralButton(R.string.ad_watch_short, (d, w) -> Ads.watchForTrial(a));
        b.show();
    }

    private static final class Toast {
        static void show(Context c, int res) {
            android.widget.Toast.makeText(c, res, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

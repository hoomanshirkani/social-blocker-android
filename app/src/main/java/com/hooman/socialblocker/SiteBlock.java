package com.hooman.socialblocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * مسدودسازی سایت در مرورگرها (Pro): آدرس نوار URL مرورگرهای شناخته‌شده با Accessibility
 * خوانده می‌شود و اگر دامنه (یا زیردامنه‌اش) در لیست باشد صفحه مسدود نمایش داده می‌شود.
 */
final class SiteBlock {
    private SiteBlock() {}

    static final String[] SUGGESTED = {
            "instagram.com", "tiktok.com", "facebook.com", "x.com", "twitter.com",
            "youtube.com", "reddit.com", "snapchat.com", "pinterest.com", "linkedin.com"
    };

    /** پکیج مرورگر → شناسه(های) نمای نوار آدرس */
    private static final Map<String, String[]> BROWSERS = new HashMap<>();

    static {
        String[] chromium = {"url_bar"};
        for (String p : Arrays.asList("com.android.chrome", "com.chrome.beta", "com.chrome.dev",
                "com.chrome.canary", "com.brave.browser", "com.microsoft.emmx", "com.vivaldi.browser",
                "com.kiwibrowser.browser", "org.chromium.chrome", "com.google.android.apps.chrome")) {
            BROWSERS.put(p, chromium);
        }
        String[] mozilla = {"mozac_browser_toolbar_url_view", "url_bar_title", "display_url"};
        for (String p : Arrays.asList("org.mozilla.firefox", "org.mozilla.firefox_beta",
                "org.mozilla.fenix", "org.mozilla.focus", "org.mozilla.klar")) {
            BROWSERS.put(p, mozilla);
        }
        BROWSERS.put("com.sec.android.app.sbrowser", new String[]{"location_bar_edit_text", "custom_tab_toolbar_url_bar_text"});
        BROWSERS.put("com.sec.android.app.sbrowser.beta", new String[]{"location_bar_edit_text"});
        BROWSERS.put("com.opera.browser", new String[]{"url_field"});
        BROWSERS.put("com.opera.mini.native", new String[]{"url_field"});
        BROWSERS.put("com.duckduckgo.mobile.android", new String[]{"omnibarTextInput"});
        BROWSERS.put("com.yandex.browser", new String[]{"bro_omnibar_address_title_text"});
        BROWSERS.put("com.mi.globalbrowser", new String[]{"url"});
        BROWSERS.put("com.huawei.browser", new String[]{"url_bar"});
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("sb_sites", Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context c) { return sp(c).getBoolean("enabled", false); }

    static void setEnabled(Context c, boolean on) { sp(c).edit().putBoolean("enabled", on).apply(); }

    static Set<String> getDomains(Context c) {
        return new TreeSet<>(sp(c).getStringSet("domains", new HashSet<>()));
    }

    static void setDomains(Context c, Set<String> d) {
        sp(c).edit().putStringSet("domains", new HashSet<>(d)).apply();
    }

    static boolean isBrowser(String pkg) { return BROWSERS.containsKey(pkg); }

    /** ورودی کاربر یا متن نوار آدرس → دامنه تمیز (یا null) */
    static String normalize(String s) {
        if (s == null) return null;
        s = s.trim().toLowerCase(Locale.ROOT);
        int i = s.indexOf("://");
        if (i >= 0) s = s.substring(i + 3);
        for (char ch : new char[]{'/', '?', '#', ' '}) {
            int k = s.indexOf(ch);
            if (k >= 0) s = s.substring(0, k);
        }
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);
        if (s.startsWith("www.")) s = s.substring(4);
        if (s.startsWith("m.")) s = s.substring(2);
        if (!s.contains(".") || s.startsWith(".") || s.endsWith(".")) return null;
        for (int k = 0; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (!(Character.isLetterOrDigit(ch) || ch == '.' || ch == '-')) return null;
        }
        return s;
    }

    static boolean isBlockedHost(Context c, String host) {
        for (String d : getDomains(c)) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    /** دامنه صفحه فعلی مرورگر، یا null اگر پیدا نشد / کاربر در حال تایپ است. */
    static String readHost(AccessibilityNodeInfo root, String pkg) {
        if (root == null) return null;
        String[] ids = BROWSERS.get(pkg);
        if (ids == null) return null;
        for (String id : ids) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + id);
            if (nodes == null) continue;
            for (AccessibilityNodeInfo n : nodes) {
                if (n == null || n.isFocused()) continue; // در حال تایپ آدرس: هنوز مسدود نکن
                CharSequence t = n.getText();
                String host = t == null ? null : normalize(t.toString());
                if (host != null) return host;
            }
        }
        return null;
    }
}

package com.hooman.socialblocker;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** زبان انتخاب‌شده در برنامه را روی Context اعمال می‌کند ("" = زبان سیستم). */
public final class LocaleHelper {
    private LocaleHelper() {}

    static Context wrap(Context base) {
        String lang = Prefs.getLanguage(base);
        if (lang.isEmpty()) return base;
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Configuration conf = new Configuration(base.getResources().getConfiguration());
        conf.setLocale(locale);
        conf.setLayoutDirection(locale);
        return base.createConfigurationContext(conf);
    }
}

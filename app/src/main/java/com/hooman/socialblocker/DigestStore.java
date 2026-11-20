package com.hooman.socialblocker;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** نوتیفیکیشن‌های جمع‌شده (فقط روی خود گوشی، در SharedPreferences). */
final class DigestStore {
    private DigestStore() {}

    private static final int MAX = 500;

    static final class Item {
        String key, pkg, title, text;
        long time;
    }

    static synchronized List<Item> load(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Prefs.getDigestJson(c));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.key = o.optString("k");
                it.pkg = o.optString("p");
                it.title = o.optString("t");
                it.text = o.optString("x");
                it.time = o.optLong("w");
                out.add(it);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("k", it.key);
                o.put("p", it.pkg);
                o.put("t", it.title);
                o.put("x", it.text);
                o.put("w", it.time);
                a.put(o);
            }
        } catch (Exception ignored) {}
        Prefs.setDigestJson(c, a.toString());
    }

    /** اضافه یا به‌روز کردن (پیام‌رسان‌ها یک نوتیفیکیشن را مدام به‌روز می‌کنند → یک ردیف) */
    static synchronized void add(Context c, Item item) {
        List<Item> items = load(c);
        for (int i = items.size() - 1; i >= 0; i--) {
            Item o = items.get(i);
            if (o.key.equals(item.key) && o.title.equals(item.title)) items.remove(i);
        }
        items.add(item);
        while (items.size() > MAX) items.remove(0);
        save(c, items);
    }

    static synchronized void clear(Context c) {
        Prefs.setDigestJson(c, "[]");
    }
}

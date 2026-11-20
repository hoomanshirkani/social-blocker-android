package com.hooman.socialblocker;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** بارگذاری لیست اپ‌های نصب‌شده + Adapter مشترک برای صفحه اصلی و فایروال. */
public final class AppLoader {
    private AppLoader() {}

    static final class AppItem {
        String pkg, label;
        Drawable icon;
    }

    /** در پس‌زمینه لیست را می‌سازد؛ checkedFirst تعیین می‌کند کدام‌ها اول بیایند. */
    static void load(Activity a, Set<String> checkedFirst, Consumer<List<AppItem>> done) {
        PackageManager pm = a.getPackageManager();
        new Thread(() -> {
            Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> infos = pm.queryIntentActivities(main, 0);
            Set<String> seen = new HashSet<>();
            List<AppItem> result = new ArrayList<>();
            for (ResolveInfo ri : infos) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(a.getPackageName()) || !seen.add(pkg)) continue;
                AppItem it = new AppItem();
                it.pkg = pkg;
                it.label = ri.loadLabel(pm).toString();
                it.icon = ri.loadIcon(pm);
                result.add(it);
            }
            result.sort((x, y) -> {
                boolean bx = checkedFirst.contains(x.pkg), by = checkedFirst.contains(y.pkg);
                if (bx != by) return bx ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            });
            a.runOnUiThread(() -> done.accept(result));
        }).start();
    }

    /** Adapter با فیلتر جستجو؛ isChecked هر بار از Prefs خوانده می‌شود. */
    static final class Adapter extends BaseAdapter {
        private final Activity a;
        private final Predicate<String> isChecked;
        private final List<AppItem> all = new ArrayList<>();
        private final List<AppItem> shown = new ArrayList<>();
        private String filter = "";

        Adapter(Activity a, Predicate<String> isChecked) {
            this.a = a;
            this.isChecked = isChecked;
        }

        void setItems(List<AppItem> items) {
            all.clear();
            all.addAll(items);
            apply();
        }

        void setFilter(String f) {
            filter = f.trim().toLowerCase(Locale.ROOT);
            apply();
        }

        AppItem at(int pos) { return shown.get(pos); }

        private void apply() {
            shown.clear();
            for (AppItem it : all) {
                if (filter.isEmpty()
                        || it.label.toLowerCase(Locale.ROOT).contains(filter)
                        || it.pkg.toLowerCase(Locale.ROOT).contains(filter)) {
                    shown.add(it);
                }
            }
            notifyDataSetChanged();
        }

        @Override public int getCount() { return shown.size(); }
        @Override public Object getItem(int i) { return shown.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null) v = a.getLayoutInflater().inflate(R.layout.item_app, parent, false);
            AppItem it = shown.get(pos);
            ((ImageView) v.findViewById(R.id.icon)).setImageDrawable(it.icon);
            ((TextView) v.findViewById(R.id.label)).setText(it.label);
            ((TextView) v.findViewById(R.id.pkg)).setText(it.pkg);
            ((CheckBox) v.findViewById(R.id.check)).setChecked(isChecked.test(it.pkg));
            return v;
        }
    }
}

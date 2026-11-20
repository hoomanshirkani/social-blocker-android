# Social Blocker (اندروید)

اپ‌های سوشال مدیا را بلاک می‌کند؛ خاموش کردنش رمز می‌خواهد.

## ساختن APK
**با Android Studio:** پوشه `SocialBlocker` را با Open باز کنید، بگذارید Gradle Sync تمام شود، بعد
`Build > Build App Bundle(s) / APK(s) > Build APK(s)`.

**از ترمینال** (اگر Android SDK نصب است):
```bash
cd SocialBlocker
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

## راه‌اندازی روی گوشی (به همین ترتیب)
1. برنامه را باز کنید و رمز بسازید.
2. «فعال کردن سرویس دسترسی‌پذیری» → Social Blocker → روشن.
   - اندروید ۱۳ به بعد اگر گزینه خاکستری بود: Settings → Apps → Social Blocker → منوی ⋮ → **Allow restricted settings**، بعد دوباره امتحان کنید.
3. «فعال کردن ضد حذف (Device Admin)» → Activate.
4. اپ‌ها را تیک بزنید (سوشال‌های معروف از قبل تیک خورده‌اند).
5. «روشن کردن مسدودسازی».

## چه چیزی رمز می‌خواهد
- خاموش کردن مسدودسازی
- ۱۵ دقیقه توقف موقت
- برداشتن اپ از لیست (اضافه کردن آزاد است)
- خاموش کردن «محافظت از تنظیمات»
- تغییر رمز

## محافظت در برابر دور زدن
وقتی مسدودسازی روشن است، هر صفحه‌ای از Settings یا نصب‌کننده که اسم «Social Blocker» در آن باشد
(خاموش کردن Accessibility، Force stop، Uninstall، غیرفعال کردن Device Admin) فوراً بسته می‌شود.

## محدودیت‌ها (صادقانه)
- با Safe Mode، ADB یا Factory reset می‌شود دورش زد.
- فقط **اپ‌ها** را می‌بندد، نه نسخه وب (instagram.com در مرورگر). برای آن می‌شود مرورگر را هم بلاک کرد
  یا Private DNS گوشی را روی یک DNS فیلترکننده گذاشت.
- بعضی گوشی‌ها (Xiaomi، Huawei، Samsung) سرویس‌ها را برای صرفه‌جویی باتری می‌کشند؛ در تنظیمات باتری،
  Social Blocker را روی «Unrestricted / No restrictions» بگذارید.

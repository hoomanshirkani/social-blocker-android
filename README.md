# Social Blocker + Eye Protector + Firewall (Android)

**[English](#english) | [فارسی](#فارسی)**

---

<a id="english"></a>
## English

An Android app that blocks social media apps and locks itself behind a password, so you can't simply turn it off when you're tempted. It also includes a no-root firewall and an internet schedule (for example: internet open for only 10 seconds every minute).

### Features
- **App blocking:** blocked apps (Instagram, TikTok, X, Facebook, Snapchat, YouTube, Threads, … preselected) are closed the moment they open, and a "blocked" screen appears.
- **Password lock:** turning blocking off, pausing it for 15 minutes, removing an app from the list, and changing the password all need the password. The password is stored only as a salted PBKDF2 hash.
- **Daily allowance:** optionally allow blocked apps for a limited time per day (e.g. 30 minutes or 1 hour, shared across all blocked apps). Time counts only while a blocked app is on screen; it resets at midnight. Giving yourself more time needs the password.
- **Calm block screen:** instead of jumping to the home screen, a random classical Persian poem (Saadi, Hafez, Khayyam, Ferdowsi, Rumi) or a positive message is shown in Persian and English.
- **Self-protection:** while blocking is on, any Settings or installer screen showing "Social Blocker" (Accessibility toggle, Force stop, Uninstall, Device Admin, VPN) is closed immediately. A Device Admin receiver prevents direct uninstalling.
- **Firewall (no root):** a rule per app — 🚫 always blocked, ⚡ internet only for a few seconds each time the screen turns on/off, ⏱ periodic (e.g. 10 s every minute while the screen is off; while the screen is on: full, or only while you use the app plus a grace time after leaving it — 10 s to 10 min), or free — using a local VPN. Blocked connections are refused immediately (TCP reset / DNS refused / ICMP unreachable), so apps fail fast instead of hanging. No traffic leaves the phone and nothing is sent to any server.
- **Internet schedule:** "open for *N seconds / minutes / hours* every *M seconds / minutes / hours / days*". Outside that window, internet is blocked for **all** apps. Periods start at the moment you save.
- **Eye care (like Eye Relax):** break reminders — after every N minutes of screen use a full-screen break with a countdown and an eye exercise (strict mode: no skip and the break screen is locked until it ends; phone calls are allowed). Warm screen filter to reduce blue light, with adjustable strength and a night-only schedule.
- **Languages:** English and Persian (فارسی), or follow the system language.

### Requirements
- Android 8.0 (API 26) or newer
- To build: Android Studio (or the Android SDK with JDK 17)

### Build
**Android Studio:** `File → Open` → select this folder (the one containing `settings.gradle`) → wait for Gradle Sync → `Build → Generate App Bundles or APKs → Generate APKs`.

**Command line:**
```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Setup on the phone (in this order)
1. Open the app and create a password. Tip: let someone else type it.
2. Tap **Enable accessibility service** → Social Blocker → On.
   - Android 13+: if it is greyed out ("restricted setting"), go to *Settings → Apps → Social Blocker → ⋮ → Allow restricted settings*. Installing with `adb install` or from Android Studio avoids this restriction.
3. Tap **Enable uninstall protection (Device Admin)** → Activate.
4. Optional: open **🌐 Firewall & internet schedule**, turn the firewall on and accept the VPN prompt. Then set *Settings → Network → VPN → Social Blocker → Always-on VPN*.
5. Battery: set Social Blocker to *Unrestricted* (especially on Xiaomi, Samsung and Huawei).
6. Check the apps to block, then tap **Turn on blocking**.

Do steps 2–5 **before** turning blocking on. After that, those Settings screens are locked.

### Download / Releases
Every push to `main` builds the APK on GitHub Actions and publishes it under **Releases** with a new version number — no manual steps.
- `versionCode` = number of commits; `versionName` = last `vX.Y` tag + commits since it (e.g. `1.5.3`).
- To start a new minor/major version, create a tag like `v1.6` or `v2.0` and push it with the commit.
- All builds are signed with the same key (`app/debug.keystore`), so a new APK installs over the old one and keeps your settings. ⚠️ This key is public — use your own private key for a Play Store release.

### Limitations
- Safe Mode, ADB or a factory reset can bypass it. This app is a commitment device, not an MDM.
- It blocks **apps**, not websites in a browser. Block the browser too, or use a filtering Private DNS.
- Android runs only one VPN at a time. While the firewall is on, WireGuard or other VPNs cannot connect.
- Apps cannot toggle the Wi-Fi radio itself on Android 10+, so the schedule blocks internet traffic instead.

### Project structure
| File | Purpose |
|---|---|
| `BlockerService.java` | AccessibilityService: detects the foreground app, blocks apps, protects Settings |
| `FirewallVpnService.java` | Local VPN firewall + schedule |
| `MainActivity.java` / `FirewallActivity.java` | UI |
| `Prefs.java` | Settings and password hashing |
| `res/values*/strings.xml` | English / Persian texts |

### Contributing
Fork the repo, create a branch, and open a pull request. Translations are welcome: add `res/values-<lang>/strings.xml`.

---

<a id="فارسی"></a>
<div dir="rtl">

## فارسی

یک اپ اندروید که اپ‌های سوشال مدیا را مسدود می‌کند و خودش با رمز قفل می‌شود، تا وقتی وسوسه شدید نتوانید راحت خاموشش کنید. فایروال بدون روت و زمان‌بندی اینترنت هم دارد (مثلاً اینترنت فقط ۱۰ ثانیه در هر دقیقه باز باشد).

### امکانات
- **مسدود کردن اپ‌ها:** اپ‌های مسدود (اینستاگرام، تیک‌تاک، X، فیسبوک، اسنپ‌چت، یوتیوب، Threads و … از قبل انتخاب شده‌اند) همان لحظه که باز شوند بسته می‌شوند و یک صفحه‌ی «مسدود است» نمایش داده می‌شود.
- **قفل رمز:** این کارها رمز می‌خواهند: خاموش کردن، ۱۵ دقیقه توقف، برداشتن اپ از لیست و تغییر رمز. رمز فقط به‌صورت هش (PBKDF2 با salt) ذخیره می‌شود.
- **سهمیه روزانه:** اختیاری؛ اپ‌های مسدود هر روز به مدت محدودی باز باشند (مثلاً ۳۰ دقیقه یا ۱ ساعت، مشترک برای همه‌ی اپ‌های مسدود). زمان فقط وقتی شمرده می‌شود که اپ مسدود روی صفحه باز باشد و نیمه‌شب صفر می‌شود. زیاد کردن سهمیه رمز می‌خواهد.
- **صفحه‌ی آرام مسدودسازی:** به‌جای پریدن به صفحه‌ی اصلی، یک شعر کلاسیک فارسی (سعدی، حافظ، خیام، فردوسی، مولانا) یا یک پیام مثبت، به فارسی و انگلیسی نمایش داده می‌شود.
- **محافظت از خود:** وقتی مسدودسازی روشن است، هر صفحه‌ای از تنظیمات یا نصب‌کننده که اسم «Social Blocker» در آن باشد فوراً بسته می‌شود؛ مثل خاموش کردن Accessibility، Force stop، Uninstall، Device Admin و VPN. Device Admin هم جلوی حذف مستقیم را می‌گیرد.
- **فایروال (بدون روت):** یک قانون برای هر اپ: 🚫 همیشه قطع، ⚡ فقط چند ثانیه اینترنت با هر روشن/خاموش شدن صفحه، ⏱ دوره‌ای (مثلاً ۱۰ ثانیه در هر دقیقه وقتی صفحه خاموش است؛ با صفحه روشن: کامل، یا فقط هنگام استفاده از اپ به‌اضافه‌ی مهلت بعد از بیرون آمدن — ۱۰ ثانیه تا ۱۰ دقیقه) یا آزاد؛ هم Wi-Fi و هم دیتا، با یک VPN محلی. اتصال‌های مسدود فوراً رد می‌شوند (TCP reset / DNS refused / ICMP unreachable)، پس اپ‌ها به‌جای هنگ کردن سریع خطا می‌دهند. هیچ ترافیکی از گوشی خارج نمی‌شود و به هیچ سروری فرستاده نمی‌شود.
- **زمان‌بندی اینترنت:** «باز به مدت *N ثانیه/دقیقه/ساعت* در هر *M ثانیه/دقیقه/ساعت/روز*». خارج از این بازه، اینترنت **همه‌ی** اپ‌ها قطع است. شروع دوره‌ها از لحظه‌ی ذخیره حساب می‌شود.
- **مراقبت از چشم (مثل Eye Relax):** یادآوری استراحت؛ بعد از هر N دقیقه استفاده، صفحه استراحت با شمارش معکوس و یک تمرین چشم (حالت سخت‌گیرانه: بدون رد کردن و صفحه استراحت تا پایان قفل است؛ تماس تلفنی آزاد است). فیلتر گرم برای کاهش نور آبی، با شدت قابل تنظیم و زمان‌بندی شبانه.
- **زبان:** انگلیسی و فارسی، یا زبان سیستم.

### پیش‌نیازها
- اندروید ۸ (API 26) یا بالاتر
- برای بیلد: Android Studio، یا Android SDK همراه با JDK 17

### ساختن APK
**Android Studio:** از `File → Open` همین پوشه را باز کنید (پوشه‌ای که `settings.gradle` در آن است). صبر کنید Gradle Sync تمام شود، بعد `Build → Generate App Bundles or APKs → Generate APKs` را بزنید.

**ترمینال:**
```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

### راه‌اندازی روی گوشی (به همین ترتیب)
1. برنامه را باز کنید و رمز بسازید. بهتر است کس دیگری آن را تایپ کند.
2. **فعال کردن سرویس دسترسی‌پذیری** ← Social Blocker ← روشن.
   - اندروید ۱۳ به بعد: اگر گزینه خاکستری بود («restricted setting»)، بروید به *Settings ← Apps ← Social Blocker ← ⋮ ← Allow restricted settings*. اگر با `adb install` یا از Android Studio نصب کنید، این محدودیت پیش نمی‌آید.
3. **فعال کردن ضد حذف (Device Admin)** ← Activate.
4. اختیاری: **🌐 فایروال و زمان‌بندی اینترنت** را باز کنید، فایروال را روشن کنید و مجوز VPN را تأیید کنید. بعد این را تنظیم کنید: *Settings ← Network ← VPN ← Social Blocker ← Always-on VPN*.
5. باتری: Social Blocker را روی *Unrestricted* بگذارید (مخصوصاً در شیائومی، سامسونگ و هواوی).
6. اپ‌ها را تیک بزنید و **روشن کردن مسدودسازی** را بزنید.

مراحل ۲ تا ۵ را **قبل** از روشن کردن مسدودسازی انجام دهید، چون بعد از آن این صفحه‌های تنظیمات قفل می‌شوند.

### دانلود / نسخه‌ها
با هر push به `main`، فایل APK روی GitHub Actions ساخته می‌شود و با شماره‌ی نسخه‌ی جدید در بخش **Releases** منتشر می‌شود؛ بدون هیچ کار دستی.
- `versionCode` = تعداد commitها؛ `versionName` = آخرین تگ `vX.Y` + تعداد commitهای بعد از آن (مثلاً `1.5.3`).
- برای شروع نسخه‌ی اصلی جدید، یک تگ مثل `v1.6` یا `v2.0` بسازید و همراه commit push کنید.
- همه‌ی بیلدها با یک کلید (`app/debug.keystore`) امضا می‌شوند، پس APK جدید روی قبلی نصب می‌شود و تنظیمات می‌ماند. ⚠️ این کلید عمومی است؛ برای Play Store کلید خصوصی خودتان را بسازید.

### محدودیت‌ها
- با Safe Mode، ADB یا Factory reset می‌شود دورش زد. این ابزار برای تعهد به خود است، نه MDM.
- فقط **اپ‌ها** را می‌بندد، نه سایت‌ها را در مرورگر. برای سایت‌ها، مرورگر را هم مسدود کنید یا از یک Private DNS فیلترکننده استفاده کنید.
- اندروید فقط یک VPN را همزمان اجرا می‌کند. وقتی فایروال روشن است، WireGuard یا VPN دیگری نمی‌تواند وصل شود.
- از اندروید ۱۰ به بعد اپ‌ها نمی‌توانند خود Wi-Fi را روشن یا خاموش کنند. برای همین زمان‌بندی، ترافیک اینترنت را قطع می‌کند.

### مشارکت
مخزن را Fork کنید، یک branch بسازید و Pull Request بفرستید. ترجمه به زبان‌های دیگر هم خوشحال‌مان می‌کند: فایل `res/values-<زبان>/strings.xml` را اضافه کنید.

</div>

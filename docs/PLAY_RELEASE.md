# راهنمای انتشار در Google Play / Google Play release guide

این فایل قدم‌به‌قدم کارهایی است که **خودت** باید در Play Console انجام بدهی. کد برنامه برای این‌ها آماده شده است.

---

## ۰. قبل از شروع (آلمان)

- **Gewerbe:** فروش اپ فعالیت تجاری است → ثبت Gewerbe (Kleingewerbe) در Gemeinde و اعلام به Finanzamt (Fragebogen zur steuerlichen Erfassung). با Kleinunternehmerregelung (§19 UStG) معمولاً مالیات بر ارزش افزوده نمی‌گیری. گوگل در اتحادیه اروپا خودش VAT خریدار را پرداخت می‌کند.
- **Arbeitslosengeld:** هر درآمد جانبی و ساعت کار را **قبل از شروع** به Agentur für Arbeit اعلام کن (کمتر از ۱۵ ساعت در هفته؛ ۱۶۵ یورو در ماه آزاد است و بقیه کم می‌شود). این مشاوره حقوقی/مالیاتی نیست؛ برای اطمینان با Agentur و یک Steuerberater چک کن.
- **Impressum:** حساب‌های Play که اپ پولی دارند آدرس عمومی نمایش می‌دهند. اطلاعات واقعی‌ات را در این دو جا بنویس:
  - `app/src/main/res/values/impressum.xml`
  - بالای `PRIVACY.md`

## ۱. حساب Play Console

1. https://play.google.com/console → ثبت‌نام **Personal**، پرداخت ۲۵ دلار، تأیید هویت.
2. برای حساب‌های شخصی جدید: قبل از Production باید یک **Closed test** با **حداقل ۱۲ تستر** به مدت **۱۴ روز پیوسته** اجرا شود.
   - ایمیل Gmail دوستان و آشناها را جمع کن (یا از گروه‌های تست متقابل).
   - تسترها باید opt-in کنند و اپ را از Play نصب کنند.

## ۲. کلید امضای آپلود (فقط خودت، یک بار)

⚠️ کلید و رمزها را خودت بساز و نگه دار. اگر گم شوند می‌شود از گوگل درخواست reset کرد، ولی دردسر دارد.

```bash
keytool -genkeypair -v -keystore upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
# رمز قوی انتخاب کن و جایی امن (Password manager) ذخیره کن
base64 -w0 upload.jks > upload.jks.b64
```

در GitHub: **Settings → Secrets and variables → Actions → New repository secret**

| Secret | مقدار |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | محتوای `upload.jks.b64` |
| `UPLOAD_STORE_PASSWORD` | رمز keystore |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | رمز کلید |

از push بعدی، در Releases علاوه بر APK یک فایل `…-play.aab` هم ساخته می‌شود. **همان AAB را در Play Console آپلود کن.**

APK گیت‌هاب با همان کلید قبلی امضا می‌شود تا نسخه نصب‌شده فعلی‌ات آپدیت شود. نسخه Play با کلید دیگری امضا می‌شود، پس روی نسخه گیت‌هاب نصب نمی‌شود. اول نسخه گیت‌هاب را پاک کن، بعد نسخه Play را نصب کن.

در Play Console گزینه **Play App Signing** را روشن بگذار (پیش‌فرض است).

## ۳. ساخت اپ در Console

- **App name:** `Social Blocker: Focus & Eyes`
- **Default language:** English (en-US)
- **App or game:** App · **Free or paid:** ↓ بخش ۷
- متن‌های فروشگاه آماده‌اند:
  - `fastlane/metadata/android/en-US/`
  - `fastlane/metadata/android/de-DE/`
  - `fastlane/metadata/android/fa-IR/`
- **Privacy policy URL:** `https://github.com/hoomanshirkani/social-blocker-android/blob/main/PRIVACY.md`
- **Category:** Productivity · **Tags:** Digital wellbeing, Focus
- **Graphics:**
  - آیکون 512×512
  - Feature graphic 1024×500
  - حداقل ۲ اسکرین‌شات از گوشی (صفحه اصلی، فایروال، چشم، صفحه مسدود)

## ۴. App content (Policy) — جواب‌های پیشنهادی

### Data safety
- Does your app collect or share any of the required user data types? → **No**
  - طبق تعریف گوگل، داده‌ای که فقط روی گوشی پردازش می‌شود و از آن خارج نمی‌شود «collected» حساب نمی‌شود.
- Is data encrypted in transit? → (چیزی منتقل نمی‌شود)
- Can users request deletion? → داده‌ها با حذف اپ / Clear storage پاک می‌شوند.

### Accessibility API declaration (مهم‌ترین بخش)
- **isAccessibilityTool:** No. ابزار کمکی برای معلولیت نیست؛ اپ تمرکز و Digital wellbeing است.
- **Core feature** (متن پیشنهادی):
  > The app is a digital-wellbeing tool. The Accessibility Service detects which app is in the foreground so that apps the user chose to block are closed, daily time limits are counted, eye-care breaks are shown and the firewall's "only while in use" rule works. Data is processed on the device only and is never collected or shared. A prominent disclosure is shown and consent is requested in the app before the user is sent to Accessibility settings.
- **ویدئوی دمو** (لینک YouTube، Unlisted، زیر ۳ دقیقه):
  1. باز کردن اپ و زدن ردیف «مسدودسازی»، نمایش دیالوگ افشا و زدن «Agree».
  2. روشن کردن سرویس در تنظیمات.
  3. انتخاب اینستاگرام، باز کردنش و نمایش صفحه مسدود با شعر.
  4. تنظیم سهمیه روزانه.
  5. نمایش استراحت چشم.
  6. فایروال با قانون «فقط هنگام استفاده».

⚠️ **ریسک سیاستی:** سیاست گوگل استفاده از Accessibility برای جلوگیری از غیرفعال کردن یا حذف اپ را محدود می‌کند (به جز کنترل والدین و مدیریت سازمانی). به همین خاطر «قفل تنظیمات» حالا پیش‌فرض **خاموش** است، فقط با انتخاب خود کاربر روشن می‌شود و همیشه با رمز کاربر باز می‌شود. اپ‌های مشابه زیادی (مثل AppBlock و Lock Me Out) این «Strict mode» را دارند. با این حال ممکن است بازبین درخواست حذف آن را بدهد. اگر رد شد، سریع‌ترین راه حذف قفل در نسخه Play است.

### VPN declaration (VpnService)
- **Use case:** Device-level firewall / app-specific network blocking.
- توضیح:
  > Local, on-device firewall. Traffic of apps the user selects is routed into the local tunnel and refused. No traffic is forwarded to any remote server, inspected, logged or monetised. Other apps bypass the VPN.
- دیالوگ افشای VPN قبل از درخواست مجوز نمایش داده می‌شود.

### بقیه
- **Device admin:** فقط ضد حذف است، بدون هیچ policy. افشا قبل از فعال‌سازی نمایش داده می‌شود.
- **Notification listener:** برای Digest، با دیالوگ افشا.
- **Content rating (IARC):**
  - Utility/Productivity، بدون خشونت و بدون محتوای کاربران.
  - شعرهای فارسی کلاسیک مشکلی ندارند.
  - نتیجه احتمالی: PEGI 3 / Everyone.
- **Target audience:** 18+ (یا 13+). اپ برای کودکان طراحی نشده است.
- **Ads:** No.
- **Government app / Financial / Health:** No.

## ۵. Target API

`targetSdk 36` (Android 16) تنظیم شده است. از ۳۱ اوت ۲۰۲۶ برای اپ و آپدیت جدید الزامی است.

## ۶. ترتیب انتشار

1. Internal testing: آپلود AAB و تست خودت.
2. Closed testing: ۱۲ تستر و ۱۴ روز.
3. Apply for production: پرسشنامه کوتاه درباره تست.
4. Production: بررسی معمولاً چند روز تا یک هفته طول می‌کشد. اپ‌های دارای Accessibility و VPN ممکن است بیشتر طول بکشند.

## ۷. قیمت‌گذاری — تصمیم مهم

⚠️ **اپی که یک بار رایگان منتشر شود، دیگر هرگز پولی نمی‌شود.** برای پولی کردن باید اپ جدید با package name جدید ساخت.

| مدل | مزیت | عیب |
|---|---|---|
| **پولی (مثلاً ۲٫۹۹ €)** | ساده‌ترین، بدون کد اضافه | نصب کمتر |
| **رایگان + خرید درون‌برنامه (Pro)** | نصب بیشتر | نیاز به Play Billing Library و کد بیشتر (هنوز پیاده نشده) |
| رایگان کامل | بیشترین نصب | بدون درآمد |

کارمزد گوگل در EEA از ۳۰ ژوئن ۲۰۲۶ حدود ۱۰٪ روی اولین ۱ میلیون دلار سالانه است، به اضافه کارمزد پرداخت (Billing). قبل از تعیین قیمت، صفحه Service fees در Play Console را چک کن.

پیشنهاد: اگر سریع می‌خواهی بفروشی، **Paid** انتخاب کن. اگر مدل Freemium می‌خواهی، بگو تا Play Billing را اضافه کنم. این کار باید **قبل از** اولین انتشار تصمیم گرفته شود.

---

## English quick checklist

- [ ] Fill Impressum (`impressum.xml`) and controller block in `PRIVACY.md`
- [ ] Play Console personal account ($25), identity verification
- [ ] Create upload key, add 4 GitHub secrets → AAB appears in Releases
- [ ] Store listing from `fastlane/metadata/android/*`, screenshots, 512 icon, 1024×500 feature graphic
- [ ] Data safety: no data collected/shared
- [ ] Accessibility declaration + demo video; VPN declaration
- [ ] Content rating, target audience 18+, no ads
- [ ] Decide **paid vs free** before the first release (free → paid is impossible)
- [ ] Closed test with ≥12 testers for 14 days → apply for production

# Privacy Policy — Social Blocker + Eye Protector + Firewall

*Last updated: 21 November 2026*

**Controller / Verantwortlicher:** ⟨Vorname Nachname⟩, ⟨Straße Hausnummer⟩, ⟨PLZ Ort⟩, Germany — ⟨E-Mail-Adresse⟩

## Summary
Social Blocker+ works **entirely on your device**. It has no account, no ads, no analytics, no crash reporting and no third-party SDKs. **We do not collect, store on servers, sell or share any personal data.** The app does not even request the internet permission for itself; the only network component is a local VPN that *blocks* traffic.

## What the app processes on your device, and why

| Permission / API | What is processed | Purpose | Leaves the device? |
|---|---|---|---|
| Accessibility Service | Package name of the app on screen; text of Android Settings / package-installer screens | Close blocked apps, count daily time, recognise screens that would disable or uninstall the app while *Lock settings* is on, show the eye-care break screen and warm filter, tell the firewall which app is in use | **No** — processed in memory, not stored |
| VpnService (local firewall) | Network packets of apps **you** chose to block | Refuse their traffic locally so they have no internet | **No** — packets are dropped on the device; nothing is forwarded, inspected or logged |
| Notification access (optional digest) | App name, title, text, time of notifications | Collect them into one summary notification | **No** — stored only in the app's private storage and deleted automatically every day |
| Device admin (optional) | Nothing | Only prevents uninstalling while active | — |
| List of installed apps (QUERY launcher apps) | App names and icons | Let you choose which apps to block / firewall | **No** |

Settings (blocked apps, schedules, usage minutes, a salted PBKDF2 hash of your password) are stored only in the app's private storage. Uninstalling the app deletes all of it.

## Your rights (GDPR)
Because no personal data is transferred to us, we hold no data about you. You can delete all local data at any time by clearing the app's storage or uninstalling it. For questions, contact the e-mail above. You have the right to lodge a complaint with a data protection supervisory authority (in Bavaria: BayLDA).

## Children
The app is not directed at children under 13.

## Changes
Changes to this policy are published in this file in the public source repository.

---

# سیاست حریم خصوصی (فارسی)

این برنامه **کاملاً روی گوشی خودت** کار می‌کند: بدون حساب کاربری، بدون تبلیغ، بدون آمارگیر و بدون کتابخانه‌های شخص ثالث. **هیچ داده شخصی جمع‌آوری، به سرور فرستاده، فروخته یا به اشتراک گذاشته نمی‌شود.**

- **دسترسی‌پذیری:** نام اپ روی صفحه و متن صفحه‌های تنظیمات/نصب، فقط برای بستن اپ‌های مسدود، شمارش زمان، «قفل تنظیمات»، صفحه استراحت چشم و فایروال. ذخیره نمی‌شود.
- **VPN محلی:** ترافیک اپ‌هایی که خودت انتخاب کردی روی گوشی رد می‌شود؛ چیزی فرستاده، بررسی یا ثبت نمی‌شود.
- **نوتیفیکیشن‌ها (اختیاری):** فقط روی گوشی نگه داشته می‌شوند و هر روز خودکار پاک می‌شوند.
- **مدیر دستگاه (اختیاری):** فقط جلوی حذف را می‌گیرد.

با حذف برنامه همه داده‌ها پاک می‌شوند.

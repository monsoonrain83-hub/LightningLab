# ساخت APK در GitHub Actions

این پروژه در حال حاضر Gradle Wrapper ندارد. workflow اضافه‌شده، Gradle 8.7 را روی runner گیت‌هاب آماده می‌کند؛ بنابراین نیازی نیست کاربر Gradle را روی گوشی نصب کند.

## مراحل
1. ZIP کامل پروژه را استخراج کن.
2. پوشه‌ی اصلی `LightningLab-main` را به یک مخزن GitHub منتقل کن (محتویات پوشه، نه ZIP تو در تو).
3. فایل‌ها را روی شاخه‌ی `main` یا `master` push کن.
4. در GitHub وارد زبانه‌ی **Actions** شو و workflow با نام **Android build** را باز کن.
5. پس از پایان اجرای موفق، پایین صفحه در بخش **Artifacts** فایل `lightninglab-debug-apk` را دریافت کن.

## اجرای دستی
در Actions، workflow با نام Android build را انتخاب کن و روی **Run workflow** بزن.

## خروجی و محدودیت
- خروجی فعلی `app-debug.apk` است؛ برای آزمایش و نصب مستقیم، نه انتشار نهایی فروشگاهی.
- این workflow شامل امضای release نیست.
- اگر build یا تست شکست خورد، متن کامل خطاهای Gradle را از بخش Build log نگه دار.
- اضافه‌شدن workflow به معنی build موفق نیست؛ موفقیت فقط بعد از اجرای واقعی Action مشخص می‌شود.

# r10.70 — اصلاحات باگ و یکپارچگی

نسخه: 1.0-r10.70 / versionCode 77. شاخه: fix/r10.70-bugfixes.

## اصلاحات این نسخه

### ۱. waveform صوتی بدون metadata مدت (AudioWaveform.kt)
مشکل: وقتی فرمت صدا KEY_DURATION نداشت، مخرج توزیع binها به max(1,0) سقوط می‌کرد و همهٔ پیک‌ها در آخرین bin جمع می‌شدند. اصلاح: ریاضیات توزیع به شیء خالص WaveformBins منتقل شد؛ بدون expected frames از container، برآورد زندهٔ یکنواخت بر پایهٔ فریم‌های decode‌شده استفاده می‌شود و پیک‌ها در کل بازه پخش می‌شوند.

### ۲. پاک‌سازی تصاویر پیش‌نویس ویدئو (VideoDraftStore.kt)
مشکل: PNG لایه‌های حذف‌شده از پیش‌نویس پاک نمی‌شد و بازیابی می‌توانست تصویر قدیمی/اشتباه را برگرداند. اصلاح: در save() مجموعهٔ شناسه‌های معتبر از project استخراج می‌شود؛ فایل‌های overlay_*.png خارج از مجموعه بعد از نوشتن موفق PNGها و قبل از انتشار manifest حذف می‌شوند.

### ۳. بازیابی مدل (ModelFileRecovery.kt)
مشکل: prepareForImport بکاپ .previous را حتی پس از شکست loadOrRestore حذف می‌کرد. اصلاح: prepareForImport اکنون خروجی Boolean loadOrRestore را برمی‌گرداند و بکاپ فقط بعد از بارگذاری موفق target حذف می‌شود. تست رگرسیون جدید اضافه شد.

### ۴. حذف فایل مرده
فایل خالی و بی‌استفادهٔ detektor.kt از سورس اصلی حذف شد.

### ۵. راهنمای صحیح قرارداد مدل (strings.xml en/fa/ar)
model_invalid و model_none_hint اکنون قرارداد ۱۰ جنس WMO (اصلی) و ۱۱ کلاسهٔ legacy CCSN را به‌درستی توضیح می‌دهند.

### ۶. ارتقای نسخه
versionName به 1.0-r10.70 و versionCode به 77 ارتقا یافت؛ فقط یک‌بار و هماهنگ.

### ۷. اسناد
docs/PHASE2_70_BUGFIXES_FA.md (همین سند) اضافه و سابقهٔ r10.68/r10.69 حفظ شد.

## تست‌ها
- WaveformBinsTest.kt جدید: توزیع با expectedFrames معلوم/نامعلوم و مرزهای bin.
- ModelFileRecoveryTest.kt: تست جدید prepareForImportKeepsBackupWhenRestoreFails.
- تست‌های موجود دست‌نخورده باقی ماندند.

## محدودهٔ تأیید
تست JVM و ساخت APK باید در GitHub Actions روی PR این شاخه سبز شود. آزمون لمسی روی گوشی فیزیکی (waveform روی فایل بدون metadata، بازیابی پیش‌نویس بعد از حذف لایه) همچنان لازم است و با موفقیت CI جایگزین نمی‌شود.

هیچ وزن مدل جدیدی در این نسخه ارائه نشده؛ خروجی heuristic جایگزین تشخیص علمی نیست.

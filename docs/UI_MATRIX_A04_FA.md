# LightningLab — A01 UI Matrix / A04-1 واقعی (r10.77)

## مبنای بررسی
- MainActivity.kt: ناوبری واقعی App(vm: MainVM) و Crossfade بر اساس vm.screen.
- MainVM.kt: موتورهای تحلیل، بازبینی انسانی، state ادیتورها و مدیریت پروژه.
- Screens.kt: ادیتور فعال ویدئو و بسیاری از بخش‌های رابط.
- WeatherScreen.kt و CloudAtlasScreen.kt: صفحات تخصصی.

## ماتریس صفحات

| Screen enum | صفحه | رندر واقعی | مسئول state در A04-1 | اتصال |
|---|---|---|---|---|
| HOME | داشبورد | Home(vm) در Screens.kt | MainVM + AppNavigationState | فعال |
| RESULTS | نتیجهٔ رعدوبرق | Results(vm) در Screens.kt | MainVM + LightningReviewLedger | فعال |
| PHOTO | ویرایشگر عکس | PhotoEditor(vm) در Screens.kt | MainVM | فعال |
| VIDEO | ویرایشگر ویدئو | VideoEditor(vm) در Screens.kt | MainVM + ProjectRepository | فعال |
| WEATHER | آسمان و پدیده‌ها | WeatherScreen(vm) | MainVM | فعال |
| CLOUD_ATLAS | دانشنامهٔ ابرها | CloudAtlasScreen(vm) | MainVM | فعال |
| PREPARE | آماده‌سازی | PrepareScreen(vm) در Screens.kt | MainVM | فعال |
| ABOUT | درباره | About(vm) در Screens.kt | MainVM | فعال |

## استفادهٔ واقعی از کار کلاد
1. از ایدهٔ جداکردن ViewModelهای مستقل به شکل تدریجی استفاده شده است.
2. AppNavigationState مقصد صفحه را با Compose State نگهداری می‌کند و MainVM.screen API سازگار باقی می‌ماند.
3. ProjectRepository و LocalProjectRepository متدهای واقعی read/save/overlay/delete را به VideoDraftStore فعال با AtomicFile وصل می‌کنند؛ MainVM فقط به interface وابسته است.
4. LightningReviewLedger وضعیت رأی انسانی، snapshot، dirty state و شرط پایان ذخیره را جدا کرده، در حالی که LightningReviewSidecar هنوز فایل خصوصی اتمیک را می‌نویسد.
5. آزمون‌های JVM برای تغییر صفحه، رأی، پاک‌کردن رأی، recovery و stale save نوشته شده‌اند.

## اصلاح طرح کلاد
- NoOpLightningScanEngine و NoOpCloudAnalysisEngine عمداً اضافه نشده‌اند؛ موتورهای واقعی Detector و CloudAnalyzer باید بدون ایجاد دومین source of truth مهاجرت کنند.
- ViewModel جدا ولی متصل‌نشده به UI به‌عنوان قابلیت تکمیل‌شده شمرده نمی‌شود.
- ReviewRecord صرفاً در حافظه نباید جایگزین sidecar اتمیک فعلی شود.
- ادعای ریسک رگرسیون صفر با هیچ refactor واقعی قابل دفاع نیست.
- VideoEditorScreen.kt در سورس هنوز placeholder است؛ VideoEditor(vm) داخل Screens.kt مسیر واقعی است.

## برنامهٔ بعدی (A01/A02/A03/A04)
- A01: ماتریس ریزکنترل‌ها، screenshot تست، RTL، دسترس‌پذیری، tablet/landscape و مجوزها.
- A02: benchmark و تست فیزیکی crash/ANR/RAM/battery روی چند دستگاه.
- A03: پروژهٔ چندنسخه‌ای، بازیابی پس از process death، failpoint، backup اتمیک و media relink.
- A04-2: انتقال تدریجی Lightning scan engine به VM مستقل با انطباق event IDs و رأی بازبین.
- A04-3: مهاجرت Cloud به VM مستقل با حفظ import/export و قراردادهای مدل.
- A04-4: جداسازی Photo/Video state و UI با golden tests و حفظ تمام امکانات فعلی.

## معیار نهایی مرحله
تست‌های Python/JVM و ساخت APK روی commit نهایی PR الزامی‌اند؛ تست فیزیکی و regression تصویری هنوز جداگانه لازم است. این refactor وزن مدل AI جدید یا دقت علمی تأییدشده ایجاد نمی‌کند.

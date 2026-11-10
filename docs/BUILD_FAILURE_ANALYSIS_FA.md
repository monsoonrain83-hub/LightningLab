# گزارش عیب‌یابی GitHub Actions — LightningLab

تاریخ بررسی: 2026-11-09
مخزن: https://github.com/monsoonrain83-hub/LightningLab

## نتیجه
چهار اجرای متوالی Build APK با شماره‌های 177 تا 180 در مرحله `:app:compileDebugKotlin` شکست خورده‌اند. مراحل checkout، اعمال update.zip، راه‌اندازی Java 17، نصب Gradle 8.7 و آماده‌سازی Android SDK موفق بوده‌اند. بنابراین علت شکست، تنظیمات SDK یا دریافت وابستگی‌ها نبوده؛ خطاهای Kotlin در سورس آپلودشده باعث توقف شده‌اند.

- [اجرای 177](https://github.com/monsoonrain83-hub/LightningLab/actions/runs/42650511839) — خطای `if must have both main and else branches if used as an expression` در مسیر واردکردن مدل.
- [اجرای 178](https://github.com/monsoonrain83-hub/LightningLab/actions/runs/42653772487) — همان خطای شاخهٔ `if` در مسیر rollback فایل مدل.
- [اجرای 179](https://github.com/monsoonrain83-hub/LightningLab/actions/runs/42655858032) — `mutableLongStateOf` import نشده بود، یک خطای `if` در rollback و یک خطای type inference گزارش شد.
- [اجرای 180](https://github.com/monsoonrain83-hub/LightningLab/actions/runs/42658595375) — همان import ناقص و ابهام در نوع `top?.second` هنگام ساخت نتیجهٔ فریم ویدئو.

## علت‌های دقیق
1. `mutableLongStateOf` استفاده شده بود اما import آن در فایل MainVM.kt وجود نداشت.
2. در rollback واردکردن فایل مدل، چند شرط تک‌خطی مانند `if (hadOld) backup.renameTo(target)` در بلوک‌هایی قرار گرفته بودند که Kotlin آن‌ها را به‌عنوان expression تحلیل می‌کرد؛ نبود شاخهٔ else باعث خطای کامپایل می‌شد.
3. در مسیر تحلیل ویدئو، نوع nullable حاصل از `ranked?.firstOrNull()` به‌صورت ضمنی استنباط می‌شد و استفاده از `top?.second` به ابهام operator می‌رسید. در نسخهٔ قبلی، lambda مربوط به `ImageDecoder.decodeBitmap` نیز به overloadهای Java/SAM متکی بود و به شکل صریح تایپ نشده بود؛ برای حذف هر دو ریسک، نوع Pair و listener صریح شده‌اند.

## اصلاحات r10.41
- import صریح `androidx.compose.runtime.mutableLongStateOf` اضافه شد.
- تمام decodeهای تصویر از helper واحد `decodeSoftwareBitmap` با `ImageDecoder.OnHeaderDecodedListener` صریح عبور می‌کنند.
- شاخه‌های rollback با بلوک‌های صریح و `else { Unit }` بازنویسی شدند.
- تست‌های TimelineMath برای trim با حداقل طول، جابه‌جایی معتبر قطعه و Ripple Shift اضافه شدند.
- قابلیت trim محدودهٔ منبع قطعهٔ انتخابی، جابه‌جایی ترتیب قطعات، Ripple Delete و markerهای قابل seek اضافه شد؛ markerها در ProjectCodec v4 ذخیره می‌شوند.

## هشدارهایی که علت شکست نبودند
- هشدار namespace تکراری TensorFlow Lite؛ build در مرحله Kotlin متوقف شده بود.
- هشدار deprecation مربوط به Node.js 20 در GitHub Actions؛ این هشدار باعث exit code 1 نشده بود.
- پیام strip نشدن `libtensorflowlite_jni.so`؛ کتابخانه بدون strip بسته‌بندی شده بود و علت شکست نبود.

## اعتبارسنجی انجام‌شده برای r10.41
- ZIP سالم است و پس از بسته‌بندی با `ZipFile.testzip()` بررسی می‌شود.
- `Timeline.kt` با kotlinc مستقل کامپایل شد.
- تست دستی helperهای trim/reorder و نگاشت تایم‌لاین با موفقیت اجرا شد.
- اسکن نحوی فایل‌های Kotlin خطای parser مانند `expecting` یا `unexpected tokens` نشان نداد.
- **بیلد کامل Gradle/Android در این محیط اجرا نشده است**؛ Android SDK و Gradle نصب نیستند. پس تا اجرای بعدی GitHub Actions، رفع کامل خطاهای CI قطعی اعلام نمی‌شود.

## اقدام بعدی
آپلود `update.zip` جدید در ریشهٔ مخزن و بررسی اجرای تازهٔ Build APK. اگر build باز هم شکست خورد، باید لاگ همان run بررسی شود؛ این گزارش فقط اجراهای 177 تا 180 را پوشش می‌دهد.

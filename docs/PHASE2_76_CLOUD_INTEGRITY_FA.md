# LightningLab r10.76 — کنترل یکپارچگی و دادهٔ چندناظر ابرها

## مشکل پیشین
Pipeline دادهٔ Montenegro تقسیم زمانی train/validation/test داشت، ولی تصویر با بایت‌های یکسان در روزهای متفاوت ممکن بود وارد splitهای مختلف شود. مانیفست هم هش تصویر را ذخیره نمی‌کرد و تغییر فایل قبل از آموزش بررسی نمی‌شد. این ایراد می‌تواند برآورد دقت ظاهری را افزایش دهد.

## اصلاحات
- هر تصویر استخراج‌شده دارای SHA-256 مستقل در manifest.csv است.
- قبل از انتشار manifest.csv نهایی، فایل manifest.pending.csv با ابزار مستقل validate_montenegro_manifest.py اعتبارسنجی می‌شود.
- فایل‌های دقیقاً تکراری در splitهای مختلف رد می‌شوند. تصویرهای مختلف با نام فایل مشابه و شناسهٔ تصویر تکراری نیز رد می‌شوند.
- خروج از پوشهٔ داده از طریق image_path غیرمجاز است؛ فایل‌های موجود با checksum کنترل می‌شوند.
- توزیع soft-label در CL/CM/CH باید از کدهای معتبر 0..9 یا / تشکیل شود، احتمال منفی یا نامتناهی نداشته باشد و جمع هر سر برچسب‌دار برابر ۱ باشد.
- مانیفست قدیمی فاقد ستون sha256 همچنان می‌تواند با فایل‌های موجود بررسی شود؛ هش واقعی محاسبه و نشت داده رد می‌شود.
- پیش از شروع TensorFlow training همین audit اجرا می‌شود.
- اگر یک head در کل مجموعه test فاقد نمونهٔ برچسب‌دار باشد، به‌جای گزارش NaN برای آن سر هشدار صریح نمایش داده می‌شود.
- قرارداد خروجی مدل به manifest_sha256 و tflite_sha256 و external_site_validated=false مجهز می‌شود.
- شش تست جدید Python با دادهٔ ساختگی برای این مسیر اضافه شده است.

## نحوهٔ اجرا

    python3 tools/cloud_ai/prepare_montenegro_synoptic.py \
       --record-dir /path/to/downloaded_dataset --out data/montenegro

    python3 tools/cloud_ai/validate_montenegro_manifest.py \
       --manifest data/montenegro/manifest.csv \
       --out data/montenegro/integrity_audit.json

    python3 tools/cloud_ai/train_montenegro_synoptic.py \
       --manifest data/montenegro/manifest.csv --out out/cloud

## احتیاط مهم
Montenegro یک دادهٔ دوربین/مکان مشخص در بازهٔ فصلی محدود است. رفع نشت داده با آزمون مستقل مکانی و فصلی فرق دارد. soft-target ثبت‌شده توزیع رأی ناظران است، نه حقیقت قطعی. وزن جدید مدل از این تغییرات به‌تنهایی تولید نشده؛ اجرای training نیاز به فایل‌های واقعی و TensorFlow دارد.

## وضعیت دیگر بخش‌ها
این مرحله هیچ قابلیت سابق Photo Studio، Video Studio و Lightning Intelligence را حذف نمی‌کند. r10.75 معیارهای scientific evaluation برای رعدوبرق و ابرها را اضافه کرده است و این مرحله dataset integrity ابر را در همان مسیر تقویت می‌کند. تست روی Android واقعی و اعتبارسنجی دادهٔ مستقل همچنان ضروری است.

# LightningLab r10.70 (versionCode 77) — رفع باگ‌های واقعی سورس r10.69

اصلاحات این نسخه:

## VIDEO STUDIO
- AudioWaveform: رفع فروپاشی توزیع پیک‌ها در آخرین bin وقتی metadata مدت صدا (KEY_DURATION) موجود نبود. ریاضیات توزیع به شیء خالص `WaveformBins` منتقل شد؛ در نبود expected frames، برآورد زندهٔ یکنواخت بر پایهٔ فریم‌های decode‌شده استفاده می‌شود. تست‌های رگرسیون جدید در `WaveformBinsTest.kt`.
- VideoDraftStore: حذف PNGهای لایه‌هایی که پروژه دیگر ارجاع نمی‌دهد، پیش از انتشار manifest پیش‌نویس؛ جلوگیری از بازگشت تصویر لایهٔ حذف‌شده در بازیابی و جلوگیری از نشت حافظهٔ ذخیره‌سازی.

## MODEL SAFETY
- ModelFileRecovery.prepareForImport: بکاپ `.previous` فقط پس از اعتبارسنجی موفق target حذف می‌شود (قبلاً حتی در شکست restore حذف می‌شد). دو تست رگرسیون جدید در `ModelFileRecoveryTest.kt`.

## UI / GUIDANCE
- strings.xml (en/fa/ar): پیام model_invalid و model_none_hint اکنون قرارداد ۱۰ جنس WMO و ۱۱کلاسهٔ legacy CCSN را به‌درستی توضیح می‌دهند (قبلاً فقط ۱۱کلاسه). about_text به 1.0-r10.70 به‌روز شد.
- فایل خالی مردهٔ detektor.kt با stub مستند جایگزین شد.

## BUILD
- versionName: 1.0-r10.70، versionCode: 77 (افزایش هماهنگ و یک‌باره).

## VALIDATION
- تست‌های JVM و ساخت APK باید در GitHub Actions روی PR این شاخه (fix/r10.70-bugfixes → main) سبز شوند.
- تست روی گوشی فیزیکی برای waveform فایل بدون metadata و بازیابی پیش‌نویس پس از حذف لایه همچنان لازم است و با CI جایگزین نمی‌شود.
- هیچ وزن مدل جدید، دقت ادعایی یا قابلیت علمی جدیدی در این نسخه ادعا نمی‌شود.

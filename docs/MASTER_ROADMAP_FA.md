# LightningLab — نقشه‌راه جامع و وضعیت اجرایی

آخرین نسخهٔ ادغام‌شده در شاخهٔ اصلی: **r10.47 / versionCode 57**.
نسخهٔ کاری r10.48 در شاخهٔ \`feature/srt-subtitles-r10-48\` در حال توسعه است؛ تا سبزشدن تمام unit testها و APK build نباید به main ادغام شود.

## اصول ثابت
- Kotlin + Jetpack Compose، Android، اجرای عادی آفلاین، فارسی/RTL، رابط Glassmorphism با عمق سه‌بعدی کنترل‌شده.
- هیچ قابلیت موجود حذف یا با mock/placeholder جایگزین نشود.
- خروجی heuristic، مدل، و برچسب دستی از هم جدا بمانند؛ امتیاز خام مدل احتمال کالیبره‌شده نیست.
- وزن مدل/داده فقط با provenance و مجوز روشن در مخزن عمومی قرار بگیرد.
- هر قابلیت جدید باید تست داشته باشد؛ build و unit tests در GitHub Actions اجباری‌اند.

## کارهای انجام‌شده و قابل‌اثبات
- r10.46: بازیابی سورس واقعی از artifactهای GitHub Actions، رفع خطاهای Kotlin تایم‌لاین، اصلاح مسیر ثبت سورس و حفظ snapshot بیلد.
- r10.47: دستگیره‌های مستقیم trim چپ/راست برای overlay و effect، drag جابه‌جایی بر اساس عرض کل timeline، تجمیع Undo gesture و حداقل طول ۲۰۰ms برای effect؛ ادغام‌شده در main.
- CI: مرحلهٔ مستقل \`testDebugUnitTest\` پیش از APK اضافه شده و گزارش XML/HTML تست‌ها به‌صورت artifact حتی در شکست آپلود می‌شود.
- SRT import/export: فایل‌های SubRip به کلیپ‌های متنی قابل‌ویرایش تبدیل و از همان لایه‌ها به SRT استاندارد صادر می‌شوند؛ parser/serializer تست‌های مستقل دارند.
- قرارداد طبقه‌بندی ابر: تفکیک ۱۰ جنس طبیعی WMO از کلاس دیتاستی Contrail (Ct)، تفکیک species و varieties، نام‌گذاری supplementary/accessory features.
- سازگاری مدل CCSN کاربر: تشخیص مدل ۱۱کلاسه بر اساس امضای tensor، ترتیب برچسب‌ها و preprocessing مستقل، ارتقای TFLite runtime به ۲.۱۷.۰؛ فایل وزن به علت نامشخص‌بودن مجوز مخزن مبدأ در APK/GitHub bundle قرار نمی‌گیرد و از مسیر Import کاربر بارگذاری می‌شود.

## وضعیت فعلی r10.49
- [x] ورود و خروج فایل SRT در ابزار متن Video Editor؛ cueها به کلیپ‌های قابل‌ویرایش تبدیل می‌شوند و export دوباره SRT می‌سازد
- [x] پشتیبانی parser از comma/dot، چندخطی، BOM، cue settings و entityهای پایه
- [x] تست‌های parser و قرارداد مدل CCSN
- [x] اجرای unit tests در CI و ذخیره گزارش شکست
- [x] اصلاح هم‌ترازی سراسری DayLine؛ تست‌های jitter بدون صاعقه و صاعقه باریک در snapshot قبلی سبز شدند
- [x] افزودن Fade-in/Fade-out مستقل موسیقی و صدای ضبط‌شده با envelope خطی
- [x] ذخیره/بازیابی fadeها در Undo/Redo و ProjectCodec v5؛ پروژه‌های v1-v4 با مقدار پیش‌فرض صفر خوانده می‌شوند
- [ ] سبزشدن تمام unit tests روی آخرین commit r10.49
- [ ] سبزشدن assembleDebug با TFLite 2.17.0، SRT export و AudioProcessor جدید
- [ ] ادغام r10.49 در main و تولید APK نهایی

## Creative Studio — موارد باز
### Video
- تکمیل ادیتور واقعی در مسیر اجرایی؛ بازبینی کامل preview, timeline, playback, trim, split, ripple delete, markers و save/export.
- drag/reorder و multi-select/group برای کلیپ‌های اصلی؛ lock/mute/solo ترک‌ها.
- waveform فقط از PCM واقعی استخراج شود؛ cache و thumbnail واقعی ویدئو.
- fade/volume/pan مستقل برای هر کلیپ صوتی، keyframe و speed ramp/reverse/freeze.
- transitions، masking/chroma key، LUT/color wheels و زیرنویس SRT/SRT export.
- export presets، رزولوشن/FPS/bitrate، preflight، queue، verification و recovery.
- instrumentation و تست دستی روی گوشی برای ویدئوهای طولانی، فایل خراب، لغو و lifecycle.

### Photo
- حفظ ابزارهای فعلی و ممیزی مسیر واقعی export PNG/JPEG.
- بهبود crop/perspective، masks محلی، blend modes، curves/color wheels، RAW/DNG، lens correction، clone/patch/object removal.
- history/undo یکپارچه، presets، histogram و before/after.
- UI نهایی با preview بزرگ، ابزارهای گروه‌بندی‌شده، پنل لایه‌ها و کنترل‌های قابل‌دسترسی.

## Atmospheric Intelligence / Cloud Atlas
- [ ] تست عملی import و inference مدل CCSN روی Android واقعی؛ تأیید tensor names/shapes و latency/RAM.
- [ ] ارزیابی مستقل مدل روی test set بدون leakage/near-duplicate؛ macro-F1، per-class precision/recall، confusion matrix و calibration.
- [ ] baseline جدید ۱۰ جنس WMO با dataset RGB دارای مجوز روشن؛ تفکیک Ct.
- [ ] مدل چندبرچسبی ویژگی‌های ساختاری با annotation متخصص و کلاس unknown/uncertain.
- [ ] segmentation چندکلاسه با ماسک انسانی و mIoU/Dice؛ وزن‌های مستقل و قرارداد runtime مستقل.
- [ ] temporal consensus با timestamp/camera alignment و محدودیت‌های علمی روشن.
- [ ] بازبینی یکپارچه UI داشبورد، Cloud Atlas، AI Recognition و Atmospheric Analysis.

## کیفیت و تحویل
- [ ] تمام JVM tests سبز؛ تست‌های regression برای parser، model contract، DayLine، timeline، codecs و export.
- [ ] Android instrumentation/UI tests و تست دستگاه واقعی؛ حافظه، crash، performance و accessibility.
- [ ] ZIP کامل سورس از همان commit بیلدشده + APK + گزارش فارسی + SHA256.
- [ ] وضعیت هر قابلیت فقط بعد از شواهد build/test/device به Done تغییر کند.

## موانع/وابستگی‌ها
- مدل CCSN واردشده در Library: مجوز مخزن عمومی مبدأ مشخص نیست؛ فایل وزن در repo عمومی bundle نمی‌شود.
- دقت علمی هیچ مدل تا ارزیابی مستقل گزارش نشده است.
- تست روی گوشی فیزیکی از طریق GitHub Actions قابل جایگزینی نیست و باید جداگانه انجام شود.

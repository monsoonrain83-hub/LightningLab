# LightningLab — نقشه‌راه جامع و وضعیت اجرایی

آخرین نسخهٔ ادغام‌شده در main: **1.0-r10.62 / versionCode 69**.
نسخهٔ در حال بررسی: **r10.63 / versionCode 70 در PR #21**.
وضعیت مدل‌ها و قابلیت‌های علمی فقط با تست/ارزیابی مربوط به خودشان Done تلقی می‌شود؛ موفقیت build به‌تنهایی دقت علمی را ثابت نمی‌کند.

## اصول کیفیت
- Kotlin + Jetpack Compose، Android، پردازش اصلی آفلاین، فارسی/RTL، رابط Glassmorphism با عمق کنترل‌شده و دسترس‌پذیری.
- هیچ قابلیت موجود حذف یا با mock/placeholder جایگزین نشود.
- خروجی heuristic، مدل و برچسب دستی از هم جدا بمانند؛ score خام احتمال کالیبره‌شده نیست.
- وزن مدل/داده فقط با provenance و مجوز روشن در مخزن عمومی قرار بگیرد.
- هر قابلیت جدید باید تست داشته باشد؛ JVM/Python tests و APK build در GitHub Actions اجباری‌اند.
- معیار نهایی انتشار: build سبز، regression سبز، بررسی UI و تست دستگاه واقعی.

## نسخه‌ها و تغییرات ادغام‌شده
- **r10.46–r10.49:** بازیابی سورس، اصلاح pipeline بیلد، SRT، مدل‌های ابر، DayLine، fade صوت، waveform cache و پروژه‌های ویدئو.
- **r10.51:** کنترل مستقل mute/solo برای صدای اصلی، موسیقی و voice؛ قفل overlay/effect؛ مرتب‌سازی قطعات؛ گروه overlay؛ هیستوگرام RGB/luma عکس.
- **r10.52:** انتخاب چند قطعهٔ اصلی ویدئو، جابه‌جایی گروه پیوسته و حذف گروهی با Undo؛ تست و APK موفق.
- **r10.53:** منحنی‌های پنج‌نقطه‌ای مستقل R/G/B و RGB مشترک؛ LUT و ذخیره در پروژه.
- **r10.54:** دسته‌بندی نشانه‌های ظاهری صاعقه: کانال باریک، روشن‌شدن گسترده، فلاش موضعی، خوشه چندپالسی و نامعین؛ heuristic، نه تشخیص قطعی فیزیکی.
- **r10.55:** pipeline آماده‌سازی دادهٔ چندناظر WMO از Montenegro dataset، soft-label برای CL/CM/CH، split زمانی و تست‌های Python.
- **r10.56:** نمودار تعاملی drag برای منحنی عکس، ادغام‌شده و CI موفق.
- **r10.57:** runtime و pipeline آموزش مدل سه‌سر WMO برای CL/CM/CH با خروجی [1,3,11]؛ CI JVM/Python/APK موفق. وزن آموزش‌دیده هنوز باید از دادهٔ دارای مجوز تولید و اعتبارسنجی شود.
- **r10.58:** کارت رخداد صاعقه با زمان شروع/پایان، تعداد پالس و گستردگی روشنایی؛ هشدار علمی دربارهٔ محدودیت تعیین CG/IC و قطبیت از RGB.
- **r10.59:** پروفایل‌های خروجی H.264، رزولوشن Source/1080p/720p/480p، bitrate متناسب با تعداد پیکسل، حفظ نسبت تصویر، عدم upscale و ذخیرهٔ انتخاب کاربر؛ تست JVM و APK موفق.
- **r10.60:** آماده‌سازی دیتاست ویدئویی Brixton (CC BY 4.0)، هفت برچسب رخداد، SHA-256، گروه‌بندی نماهای دوربین و محتوای تکراری، split زمانی train/validation/test؛ Python tests و compile، JVM tests و APK موفق.
- **r10.61:** مقایسهٔ لغزشی قبل/بعد در Photo Studio، حفظ مقیاس دو تصویر و عدم تغییر source/history؛ JVM tests و APK build موفق.
- **r10.62 (ادغام‌شده):** pipeline آموزش temporal TFLite برای هفت برچسب Brixton، هشت فریم RGB، MobileNetV3Small، class weighting، گزارش macro-F1/Brier/confusion matrix و قرارداد ثابت مدل؛ هنوز وزن آموزش‌دیده تولید یا روی Android ادغام نشده است.

- **r10.63 در PR #21:** کنترل SHA-256 قبل از آموزش، رد نشت دادهٔ فایل تکراری و مسیر یکسان میان splitها، metadata بازتولیدپذیری و تست رگرسیون. این تغییرات به‌تنهایی وزن مدل یا runtime اندروید اضافه نمی‌کنند.

## Video Studio

### تکمیل‌شده
- [x] preview و seek/playhead مبتنی بر Media3؛ speed، split، trim و reorder قطعات.
- [x] drag/trim مستقیم overlay و effect؛ lock و keyframeهای موجود.
- [x] گروه‌بندی overlay و قطعات اصلی؛ reorder گروه پیوسته و حذف گروهی با Undo.
- [x] waveform PCM/cache؛ SRT import/export؛ mute/solo و fade موسیقی/voice.
- [x] preflight خروجی و H.264/AAC MP4.
- [x] پروفایل‌های export با رزولوشن و bitrate؛ H.264، حفظ نسبت تصویر و عدم upscale.

### باقی‌مانده
- [ ] gain/fade/pan مستقل هر audio clip و waveform per-clip.
- [ ] transitions واقعی بین کلیپ‌ها؛ speed ramp/reverse/freeze.
- [ ] keyframe کامل position/scale/rotation/opacity و audio automation.
- [ ] masking/chroma key، LUT، color wheels و کنترل رنگ پیشرفته.
- [ ] export queue، تخمین حجم، metadata verification، resume و recovery.
- [ ] autosave، بازیابی crash، backup اتمیک و dedup رسانه‌های بزرگ.
- [ ] instrumentation/UI tests و تست دستگاه برای فایل‌های بلند/خراب، cancel و lifecycle.
- [ ] استخراج VideoEditor از Screens.kt به ماژول مستقل؛ VideoEditorScreen.kt فعلاً placeholder است و نباید به‌عنوان editor فعال معرفی شود.

## Photo Studio

### تکمیل‌شده
- [x] نور، رنگ، HSL، crop/rotate، healing، mask، لایه‌های متن/شکل/تصویر.
- [x] هیستوگرام RGB/luma در preview.
- [x] منحنی‌های non-destructive پنج‌نقطه‌ای RGB و نمودار drag مستقل کانال‌ها.

### باقی‌مانده
- [ ] color wheels، local adjustment masks با feather/brush refinement و blend modes.
- [ ] perspective correction، lens profile/correction و RAW/DNG.
- [ ] clone/patch و object removal پیشرفته با کنترل لبه.
- [ ] تأیید و merge مقایسهٔ لغزشی before/after در PR #18؛ histogram دقیق full-resolution برای export و metadata.
- [ ] مدیریت حافظه، preview tile-based و تست عکس‌های بزرگ/RAW و خروجی PNG/JPEG.
- [ ] UI instrumentation، تست لمس، دسترس‌پذیری و اندازه‌های مختلف نمایشگر.

## Cloud Intelligence / Cloud Atlas

### تکمیل‌شده
- [x] runtime ده جنس طبیعی WMO و قرارداد جداگانهٔ مدل ۱۱کلاسه CCSN.
- [x] مدل ویژگی ساختاری/segmentation به‌صورت import اختیاری.
- [x] تحلیل rule-based پوشش، اکتا و بافت با هشدار علمی.
- [x] تحلیل ویدئو، نمونه‌برداری زمانی، consensus و روند پوشش/جنس.
- [x] pipeline دادهٔ Montenegro چندناظر، soft labels و مدل سه‌سر CL/CM/CH در runtime.
- [x] تست‌های آماده‌سازی داده و compile ابزارهای Python در CI.

### باقی‌مانده
- [ ] آموزش وزن‌های نهایی سه‌سر CL/CM/CH و انتشار artifact با checksum/provenance.
- [ ] ارزیابی temporal holdout و آزمون خارجی جغرافیایی؛ macro-F1، per-class precision/recall، confusion matrix، Brier/calibration و توافق متخصصان.
- [ ] دادهٔ چندفصل/چنداقلیم/چنددوربین برای تعمیم؛ Montenegro تک‌مکان و محدودیت فصلی دارد.
- [ ] baseline ده جنس WMO با دادهٔ مجاز و split مستقل؛ جلوگیری از نگاشت اجباری گروه‌های ترکیبی.
- [ ] مدل چندبرچسبی گونه/واریته و ویژگی ساختاری با کلاس unknown/uncertain.
- [ ] segmentation چندکلاسه با ماسک انسانی و mIoU/Dice.
- [ ] آزمون واقعی import/inference روی Android و سنجش latency/RAM/thermal.
- [ ] بازطراحی یکپارچه UI داشبورد، Cloud Atlas و Atmospheric Analysis.

## Lightning Intelligence

### تکمیل‌شده
- [x] decode و scan ویدئو با MediaCodec، تحلیل زمانی و استخراج فریم برتر.
- [x] pipeline آماده‌سازی Brixton: هفت برچسب، گروه‌بندی چند دوربین، SHA-256 و split زمانی؛ CI Python سبز.
- [x] DayLine برای ساختارهای باریک روزانه و جداسازی پالس‌ها.
- [x] دسته‌بندی ظاهری: visible channel، diffuse illumination، localized flash، multi-pulse و uncertain.
- [x] نمایش interval، pulse count، spatial spread و توضیح محدودیت score.

### باقی‌مانده
- [ ] تکمیل CI و merge pipeline آموزش Brixton در PR #19؛ سپس تولید وزن‌های TFLite با اجرای واقعی روی دیتاست.
- [ ] Android import/runtime برای قرارداد [1,8,224,224,3] → [1,7] و نمایش پیش‌بینی مدل در کنار شواهد heuristic.
- [ ] ارزیابی مستقل چنددوربین/چندمکان و بررسی calibration قبل از فعال‌سازی برچسب مدل به‌عنوان نتیجهٔ قابل اتکا.
- [ ] line/branch tracking و segmentation در رزولوشن بالاتر همراه تثبیت دوربین.
- [ ] ردیابی leader/return stroke؛ برچسب CG/IC فقط در صورت شواهد کافی و اعتبارسنجی.
- [ ] مدل ML با دیتاست ویدئویی مجاز و annotation متخصص؛ split مستقل بر اساس کلیپ/دوربین.
- [ ] خروجی گزارش CSV/JSON با timestamp، thumbnail، شواهد و امکان بازبینی/اصلاح دستی.
- [ ] benchmark روز/شب، handheld، rolling shutter، باران، نور خودرو و scene cuts.
- [ ] هیچ برآورد قطبیت/انرژی فیزیکی بدون حسگر مستقل معتبر ارائه نشود.

## کیفیت و انتشار
- [ ] regression کامل JVM/Python روی آخرین commit و بررسی artifactهای تست.
- [ ] Android instrumentation/UI tests، تست دستگاه واقعی، حافظه، crash، performance، accessibility و rotation.
- [ ] ZIP سورس دقیقاً از commit APK، APK، گزارش فارسی و SHA-256.
- [ ] هیچ موردی صرفاً با ثبت TODO یا سند به‌عنوان قابلیت کامل اعلام نشود.

## محدودیت‌ها
- مدل CCSN قدیمی به دلیل مجوز بازتوزیع نامشخص در APK عمومی bundle نمی‌شود.
- runtime مدل سه‌سر بدون وزن آموزش‌دیده، inference واقعی تولید نمی‌کند.
- Montenegro dataset تحت CC BY 4.0 است اما تک‌مکان و محدود به فصل/شرایط مشخص است.
- RGB video به‌تنهایی همیشه امکان تعیین قطعی CG/IC، قطبیت یا مسیر الکتریکی را نمی‌دهد.
- GitHub Actions جای تست واقعی روی گوشی را نمی‌گیرد.

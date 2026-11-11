# LightningLab — نقشه‌راه جامع و وضعیت اجرایی

آخرین نسخهٔ اپ ادغام‌شده در main: **1.0-r10.54 / versionCode 63**.
نسخهٔ در حال توسعه: **r10.56** (نمودار تعاملی منحنی RGB) در PR #11.
آخرین APK تأییدشده از main: r10.54؛ build و JVM tests در GitHub Actions موفق بوده‌اند.

## اصول کیفیت
- Kotlin + Jetpack Compose، Android، اجرای عادی آفلاین، فارسی/RTL، رابط Glassmorphism با عمق سه‌بعدی کنترل‌شده.
- هیچ قابلیت موجود حذف یا با mock/placeholder جایگزین نشود.
- خروجی heuristic، مدل و برچسب دستی از هم جدا بمانند؛ score خام مدل احتمال کالیبره‌شده نیست.
- وزن مدل/داده فقط با provenance و مجوز روشن در مخزن عمومی قرار بگیرد.
- هر قابلیت جدید باید تست داشته باشد؛ JVM tests و APK build در GitHub Actions اجباری‌اند.
- برای مدل‌های هواشناسی، آزمون خارجی مستقل و گزارش per-class لازم است؛ صرف موفقیت build به معنی صحت علمی نیست.

## نسخه‌ها و تغییرات ادغام‌شده
- **r10.46–r10.49:** بازیابی سورس، اصلاح pipeline بیلد، SRT، مدل‌های ابر، DayLine، fade صوت، waveform cache و پروژه‌های ویدئو.
- **r10.51:** کنترل مستقل mute/solo برای صدای اصلی، موسیقی و voice؛ قفل overlay/effect؛ مرتب‌سازی قطعات ویدئو؛ انتخاب و جابه‌جایی گروهی overlay؛ هیستوگرام RGB/luma عکس.
- **r10.52:** انتخاب چند قطعهٔ اصلی ویدئو، جابه‌جایی گروهی قطعات پیوسته و حذف گروهی؛ تست JVM و APK موفق.
- **r10.53:** منحنی‌های پنج‌نقطه‌ای مستقل R/G/B و RGB مشترک؛ LUT 256 سطح؛ ذخیره در PhotoProjectCodec و سازگاری پروژه‌های قبلی؛ تست و APK موفق.
- **r10.54:** دسته‌بندی نشانه‌های ظاهری رخداد رعدوبرق در ویدئو: کانال باریک، روشن‌شدن گسترده، فلاش موضعی، خوشه چندپالسی و نامعین؛ تست و APK موفق. این طبقه‌بندی heuristic است، نه تشخیص قطعی نوع فیزیکی صاعقه.
- **r10.55:** pipeline آماده‌سازی دادهٔ چندناظر WMO از Montenegro dataset با مجوز CC BY 4.0؛ حفظ رأی مستقل مشاهده‌گران به صورت soft-label برای CL/CM/CH؛ split زمانی؛ گزارش entropy و تست‌های Python در CI. این مرحله داده‌سازی است و هنوز مدل آموزش‌دیده یا inference اندروید نیست.
- **r10.56:** نمودار تعاملی drag برای منحنی عکس، در انتظار سبزشدن تست و APK.
- **CI:** تست‌های JVM قبل از APK؛ گزارش‌های تست و source snapshot به‌صورت artifact نگهداری می‌شوند. از r10.55 تست Python ابزارهای داده نیز اجرا می‌شود.

## Creative Studio

### Video Editor
- [x] preview مبتنی بر Media3، seek/playhead، speed، split، trim و segment reorder.
- [x] drag و trim مستقیم overlay/effect، lock، keyframeهای موجود و snap.
- [x] انتخاب گروهی overlay، جابه‌جایی و حذف گروهی با محافظت از کلیپ قفل‌شده.
- [x] انتخاب گروهی قطعات اصلی ویدئو، reorder گروه پیوسته و حذف گروهی.
- [x] waveform PCM با cache؛ SRT import/export؛ mute/solo و fade موسیقی/voice.
- [ ] multi-select گروهی برای قطعات اصلی با drag آزاد، ripple group trim/delete و undo واحد.
- [ ] gain/fade/pan مستقل برای هر کلیپ صوتی و waveform per-clip.
- [ ] transitions واقعی بین کلیپ‌ها؛ speed ramp/reverse/freeze.
- [ ] keyframe کامل position/scale/rotation/opacity و audio automation.
- [ ] masking/chroma key، LUT و color wheels و ابزارهای پیشرفتهٔ رنگ ویدئو.
- [ ] export presets برای resolution/FPS/bitrate، queue، verification، resume و recovery.
- [ ] autosave، بازیابی crash، backup اتمیک و جلوگیری از کپی تکراری رسانه‌های بزرگ.
- [ ] تست instrumentation/UI و دستگاه واقعی برای فایل بلند/خراب، لغو export، lifecycle، حافظه و performance.
- [ ] پاک‌سازی معماری: استخراج VideoEditor از Screens.kt به ماژول مستقل بدون تغییر رفتار؛ VideoEditorScreen.kt فعلاً placeholder است و نباید به‌عنوان editor فعال معرفی شود.

### Photo Editor
- [x] نور، رنگ، HSL، crop/rotate، healing، mask، لایه‌های متن/شکل/تصویر.
- [x] هیستوگرام RGB/luma در preview.
- [x] منحنی‌های non-destructive پنج‌نقطه‌ای RGB، ذخیره در پروژه و LUT سریع.
- [ ] ادغام و تأیید نمودار drag منحنی r10.56.
- [ ] color wheels، local adjustment masks با feather/brush refinement و blend modes.
- [ ] perspective correction، lens profile/correction و RAW/DNG.
- [ ] clone/patch و object removal پیشرفته‌تر با کیفیت مرزی بهتر.
- [ ] before/after slider، histogram دقیق full-resolution برای export، metadata و export presets.
- [ ] مدیریت حافظه، preview tile-based و تست عکس‌های بزرگ/RAW و خروجی PNG/JPEG.
- [ ] UI instrumentation و آزمون لمس/دسترس‌پذیری در اندازه‌های مختلف نمایشگر.

## Atmospheric Intelligence / Cloud Atlas

### آنچه در سورس وجود دارد
- [x] runtime مدل ده جنس طبیعی WMO و قرارداد جداگانهٔ مدل قدیمی ۱۱کلاسه CCSN.
- [x] مدل جنس و مدل ویژگی ساختاری/segmentation به‌صورت import اختیاری.
- [x] تحلیل rule-based پوشش، اکتا و بافت؛ با هشدار روشن که جایگزین طبقه‌بند جنس نیست.
- [x] تحلیل ویدئویی نمونه‌برداری زمانی، consensus و روند تغییر پوشش/جنس.

### داده و مدل — موارد باز
- [x] pipeline CCAiM ده‌کلاسه (۹۱۶ تصویر) برای baseline؛ داده کوچک و نامتوازن است و به‌تنهایی production-ready نیست.
- [x] pipeline آماده‌سازی Montenegro multi-annotator: ۲٬۵۲۲ تصویر، ۱۱٬۱۹۱ annotation، CL/CM/CH، soft labels و split زمانی؛ مجوز CC BY 4.0.
- [ ] آموزش multi-head CL/CM/CH با توزیع رأی مشاهده‌گران و خروجی TFLite [1,3,11]؛ سپس runtime اندروید و UI سه‌سطحی.
- [ ] ارزیابی temporal holdout و آزمون خارجی جغرافیایی؛ macro-F1، per-class precision/recall، confusion matrix، Brier/calibration و مقایسه با توافق متخصصان.
- [ ] گسترش دادهٔ فصلی/جغرافیایی و دوربین‌های متفاوت؛ دیتاست Montenegro تک‌مکان، پاییز/اوایل زمستان و روزانه است.
- [ ] baseline ده جنس WMO با دادهٔ دارای مجوز و split مستقل؛ عدم نگاشت اجباری گروه‌های ترکیبی به جنس‌ها.
- [ ] مدل چندبرچسبی گونه/واریته و ویژگی‌های ساختاری با annotation متخصص و کلاس unknown/uncertain.
- [ ] segmentation چندکلاسه با ماسک انسانی و mIoU/Dice؛ وزن‌های مستقل و قرارداد runtime مستقل.
- [ ] آزمون واقعی import/inference روی Android و سنجش latency/RAM/thermal.
- [ ] بازبینی یکپارچه UI داشبورد، Cloud Atlas، AI Recognition و Atmospheric Analysis.

## Lightning / Thunderstorm Video Analysis
- [x] decode و scan ویدئو با MediaCodec، تحلیل فریم‌های زمانی، فیلتر shake/scene-cut و استخراج بهترین فریم.
- [x] DayLine برای ساختارهای باریک روزانه و جداسازی پالس‌ها.
- [x] دسته‌بندی نشانه‌های تصویری: visible channel، diffuse illumination، localized flash، multi-pulse و uncertain.
- [ ] تشخیص ساختار کانال شاخه‌ای با segmentation/line tracking در رزولوشن بالاتر و تثبیت حرکت دوربین.
- [ ] ردیابی زمانی leader/return-stroke و تفکیک intra-cloud / cloud-to-ground فقط در شرایطی که دادهٔ ویدئویی شواهد کافی داشته باشد.
- [ ] مدل یادگیری ماشین با دیتاست ویدئویی مجاز و annotation متخصص؛ train/validation/test مستقل بر اساس کلیپ/دوربین.
- [ ] برآورد شدت/انرژی فیزیکی یا قطبیت ممنوع است مگر با حسگر/دادهٔ مستقل معتبر؛ score فعلی صرفاً heuristic است.
- [ ] export گزارش رخدادها (CSV/JSON)، thumbnail و timestamp دقیق؛ ابزار بازبینی دستی و اصلاح برچسب.
- [ ] benchmark روی ویدئوهای روز/شب، handheld، rolling shutter، باران، نور خودرو و scene cuts.

## کیفیت و تحویل
- [ ] تمام JVM و Python tests روی آخرین commit سبز؛ regression برای parser، مدل‌ها، DayLine، timeline، codecs، waveform و export.
- [ ] Android instrumentation/UI tests و تست دستگاه واقعی؛ حافظه، crash، performance، accessibility و rotation.
- [ ] ZIP کامل سورس دقیقاً از commit APK + APK + گزارش فارسی + SHA256 برای هر release.
- [ ] وضعیت هر قابلیت فقط بعد از شواهد build/test/device به Done تغییر کند.

## موانع و محدودیت‌های شناخته‌شده
- فایل وزن CCSN واردشده در گذشته مجوز بازتوزیع روشن ندارد؛ در مخزن عمومی/APK bundle نمی‌شود.
- فعلاً مدل وزن‌دار production-ready برای همهٔ جنس‌ها/گونه‌ها همراه APK نیست؛ runtime import به‌تنهایی مدل آموزش‌دیده ایجاد نمی‌کند.
- Montenegro dataset تحت CC BY 4.0 است، اما single-site و محدود به پاییز/اوایل زمستان و روز است؛ برای تعمیم به اقلیم‌ها/دوربین‌های دیگر کافی نیست.
- ویدئوی RGB معمولی همیشه امکان تشخیص قطعی CG/IC، قطبیت یا ساختار الکتریکی را نمی‌دهد.
- GitHub Actions جای تست واقعی روی گوشی را نمی‌گیرد؛ آزمون دستگاه فیزیکی همچنان لازم است.

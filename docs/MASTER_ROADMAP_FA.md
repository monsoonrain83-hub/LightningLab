# LightningLab — نقشه‌راه جامع و وضعیت اجرایی

نسخهٔ مبنا هنگام شروع این فاز: **1.0-r10.74 / versionCode 81**.
نسخهٔ جدید در حال ارزیابی CI: **1.0-r10.75 / versionCode 82**. آموزش واقعی و ادعای دقت به ارزیابی بیرونی نیاز دارد.
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
- **r10.62:** pipeline آموزش temporal TFLite برای هفت برچسب Brixton، هشت فریم RGB، MobileNetV3Small، class weighting، گزارش macro-F1/Brier/confusion matrix و قرارداد ثابت مدل؛ هنوز وزن آموزش‌دیده تولید یا روی Android ادغام نشده است.
- **r10.63 (PR #21):** کنترل SHA-256 قبل از آموزش، رد نشت دادهٔ فایل تکراری و مسیر یکسان میان splitها، metadata بازتولیدپذیری و تست رگرسیون.
- **r10.64:** خروجی CSV/JSON رخدادها با انتخاب مسیر Android، مدل زمانی TFLite اختیاری، import امن و rollback، sampling هشت فریم، نمایش نتیجهٔ مدل مستقل از heuristic و تست‌های JVM. وزن واقعی و اعتبارسنجی میدانی هنوز باقی است.
- **r10.65:** بهینه‌سازی استخراج هشت فریم با retriever مشترک، امکان بررسی تمام رخدادها، کنترل لغو، جلوگیری از تعویض هم‌زمان مدل، افزودن خروجی امتیاز زمانی و schema v2 گزارش.
- **r10.66:** فهرست SHA-256 و اندازهٔ فایل‌های هر پروژه، بررسی سالم‌بودن فایل‌ها پیش از بازگشایی، کنترل مسیرهای ناامن و سازگاری با پروژه‌های قدیمی.
- **r10.67:** بازبینی دستی رخداد صاعقه (تأیید/رد/نامشخص) و ثبت آن مستقل از خروجی مدل در schema v3؛ گزارش CSV عکس و ویدئوی آسمان؛ سه اسلات ظاهر عکس با حفظ preset قبلی؛ تخمین زمان/حجم خروجی ویدئو و کنترل فضای موردنیاز؛ چیدمان واکنش‌گرای خانه برای نمایشگرهای باریک. پژوهش رقبا و طرح جامع UI در `docs/COMPETITIVE_GAP_AND_UX_PLAN_2026_FA.md`.
- **r10.68 (PR #27):** پیش‌نویس خودکار ویدئو با تأخیر ۲٫۵ ثانیه، نگهداری اتمیک متادیتا و تصاویر لایه‌ها، مجوز خواندن پایدار URI و امکان بازیابی/حذف پیش‌نویس. بازیابی متادیتا و تصاویر لایه است، نه پشتیبان کامل رسانه.
- **r10.69 (PR #28):** امنیت و یکپارچگی پروژه‌های Photo Studio: مسیرهای امن، هش SHA-256 پیش از انتشار، رد manifest مخدوش و سازگاری پروژه‌های قدیمی بدون integrity.json.
- **r10.70 (PR #29):** اصلاح اساسی waveform بدون duration با پنجره‌های bounded، نسخه‌بندی تصاویر پیش‌نویس ویدئو پیش از انتشار metadata، rollback مطمئن مدل‌های TFLite، حفظ آلفای PNG هنگام healing؛ ادغام‌شده و CI موفق.
- **r10.71 (PR #30):** ذخیرهٔ اتمیک و خصوصی رأی بازبین رخداد صاعقه، تطبیق محافظه‌کارانهٔ زمان شروع/پایان/فریم برتر در تحلیل مجدد؛ CI موفق.
- **r10.72 (PR #31):** کنترل اولیهٔ کیفیت RGB شامل درصد پیکسل‌های روشن اشباع، تاریک و گرم در عکس و فریم‌های ویدئو؛ CSV نسخهٔ ۲ و هشدار علمی؛ CI موفق.
- **r10.73 (PR #32):** پایداری اسکن صاعقه، callbackهای main thread، جلوگیری از نتایج اسکن قدیمی، محافظت از رأی انسانی، بازهٔ زمانی امن و شاخص‌های بازبینی؛ ادغام و CI موفق.
- **r10.74 (PR #33):** اعتبارسنجی پیش از بازیابی تصاویر/متادیتای پیش‌نویس ویدئو، محدودیت RAM، نمونه‌برداری کامل روشنایی شامل لبه‌های فریم و bounded grid برای ویدئوی عمودی؛ ادغام و CI موفق.
- **r10.75 (فاز Scientific Accuracy):** ابزارهای مستقل precision/recall/F1 و FP/h، گروه‌های ضدنشت، bootstrap گروهی، Brier/ECE، calibration روی validation، ابزار آفلاین برچسب‌گذاری صاعقه، استخراج پیش‌بینی‌های test برای Brixton/WMO/Cloud و ممیزی SHA/مجوز/دامنه/قرارداد مدل؛ آموزش واقعی همچنان مشروط به داده و منابع مستقل است.

## Scientific Accuracy — برنامهٔ قابل اجرا
- [x] تعریف ورودی CSV واقعی و مستقل برای مدل Brixton، جنس ابر و WMO CL/CM/CH؛ معیارهای قابل‌بازتولید و گزارش JSON.
- [x] ارزیابی وجود رخداد صاعقه با تطبیق زمانی یک‌به‌یک و نرخ هشدار کاذب در ساعت ویدئو، جدا از طبقه‌بندی هفت‌برچسبی.
- [x] بازهٔ اطمینان bootstrap گروهی، کنترل نشت داده و برچسب نامشخص WMO.
- [x] ابزار تهیهٔ هش واقعی ویدئو، annotation آفلاین فارسی و تبدیل گزارش خود اپ به نتایج detector.
- [x] کالیبراسیون مستقل validation و ممیزی مدل/مجوز/قرارداد/ارزیابی external در pipeline.
- [x] آماده‌سازی export پیش‌بینی‌های held-out از pipeline آموزش Brixton، ده جنس ابر و CL/CM/CH.
- [ ] دریافت و آموزش واقعی روی دیتاست‌های دارای مجوز به‌علاوهٔ برچسب مستقل برای رویدادهای missed و ویدئوهای منفی.
- [ ] ارزیابی بیرونی چندمکان/چنددوربین و انتخاب threshold تنها از validation.
- [ ] آزمایش میدانی مدل‌های TFLite و اتصال calibration اعتبارسنجی‌شده به runtime؛ فعلاً softmax uncalibrated.
- [ ] تست و گزارش latency/RAM/battery روی دستگاه واقعی و انتشار فقط پس از بازبینی متخصص.

## Video Studio

### تکمیل‌شده
- [x] preview و seek/playhead مبتنی بر Media3؛ speed، split، trim و reorder قطعات.
- [x] drag/trim مستقیم overlay و effect؛ lock و keyframeهای موجود.
- [x] گروه‌بندی overlay و قطعات اصلی؛ reorder گروه پیوسته و حذف گروهی با Undo.
- [x] waveform PCM/cache؛ SRT import/export؛ mute/solo و fade موسیقی/voice.
- [x] preflight خروجی و H.264/AAC MP4.
- [x] پروفایل‌های export با رزولوشن و bitrate؛ H.264، حفظ نسبت تصویر و عدم upscale.
- [x] بررسی یکپارچگی SHA-256 هنگام ذخیره و بازکردن پروژهٔ جدید، با حفظ سازگاری نسخه‌های قدیمی (r10.66).
- [x] پیش‌نویس خودکار ویدئو با نگهداری اتمیک و بازیابی (r10.68؛ متادیتا و تصاویر لایه‌ها، نه پشتیبان کامل رسانه).
- [x] پاک‌سازی تصاویر لایه‌های حذف‌شده از پیش‌نویس و توزیع صحیح waveform بدون metadata مدت (r10.70، PR #29).

### باقی‌مانده
- [ ] gain/fade/pan مستقل هر audio clip و waveform per-clip.
- [ ] transitions واقعی بین کلیپ‌ها؛ speed ramp/reverse/freeze.
- [ ] keyframe کامل position/scale/rotation/opacity و audio automation.
- [ ] masking/chroma key، LUT، color wheels و کنترل رنگ پیشرفته.
- [ ] export queue، تخمین حجم، metadata verification، resume و recovery.
- [ ] autosave دوره‌ای فراتر از پیش‌نویس فعلی، بازیابی crash کامل، backup چندنسخه‌ای و dedup رسانه‌های بزرگ.
- [ ] instrumentation/UI tests و تست دستگاه برای فایل‌های بلند/خراب، cancel و lifecycle.
- [ ] استخراج VideoEditor از Screens.kt به ماژول مستقل؛ VideoEditorScreen.kt فعلاً placeholder است و نباید به‌عنوان editor فعال معرفی شود.

## Photo Studio

### تکمیل‌شده
- [x] نور، رنگ، HSL، crop/rotate، healing، mask، لایه‌های متن/شکل/تصویر.
- [x] هیستوگرام RGB/luma در preview.
- [x] منحنی‌های non-destructive پنج‌نقطه‌ای RGB و نمودار drag مستقل کانال‌ها.
- [x] مقایسهٔ لغزشی before/after (r10.61).
- [x] یکپارچگی SHA-256 پروژه‌های جدید، مسیرهای امن و سازگاری پروژه‌های قدیمی (r10.69).
- [x] سه اسلات ظاهر عکس با حفظ preset قبلی (r10.67).

### باقی‌مانده
- [ ] color wheels، local adjustment masks با feather/brush refinement و blend modes.
- [ ] perspective correction، lens profile/correction و RAW/DNG.
- [ ] clone/patch و object removal پیشرفته با کنترل لبه.
- [ ] histogram دقیق full-resolution برای export و metadata.
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
- [x] راهنمای صحیح قرارداد import مدل در هر سه زبان (r10.70، PR #29).

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
- [x] بازبینی دستی رخداد (تأیید/رد/نامشخص) مستقل از خروجی مدل در schema v3 (r10.67).

### باقی‌مانده
- [ ] تولید وزن‌های TFLite با اجرای واقعی pipeline آموزش Brixton روی دیتاست؛ سپس ادغام در Android.
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

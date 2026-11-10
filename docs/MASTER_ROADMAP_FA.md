# LightningLab — نقشه‌راه جامع و وضعیت اجرایی

آخرین نسخهٔ ادغام‌شده در \`main\`: **r10.49 / versionCode 59**.
نسخهٔ کاری: **r10.51 / versionCode 61** در شاخهٔ \`feature/audio-track-mute-solo-r10-50\`.
تا سبزشدن کامل تست‌ها و APK روی آخرین commit این شاخه، r10.51 نباید merge شود.

## اصول ثابت
- Kotlin + Jetpack Compose، Android، اجرای عادی آفلاین، فارسی/RTL، رابط Glassmorphism با عمق سه‌بعدی کنترل‌شده.
- هیچ قابلیت موجود حذف یا با mock/placeholder جایگزین نشود.
- خروجی heuristic، مدل و برچسب دستی از هم جدا بمانند؛ score خام مدل احتمال کالیبره‌شده نیست.
- وزن مدل/داده فقط با provenance و مجوز روشن در مخزن عمومی قرار بگیرد.
- هر قابلیت جدید باید تست داشته باشد؛ JVM tests و APK build در GitHub Actions اجباری‌اند.

## نسخه‌های ادغام‌شده
- **r10.46:** بازیابی سورس واقعی از artifactهای GitHub Actions، رفع خطاهای Kotlin تایم‌لاین، اصلاح مسیر ثبت سورس و حفظ snapshot بیلد.
- **r10.47:** دستگیره‌های مستقیم trim چپ/راست برای overlay و effect، drag جابه‌جایی بر اساس عرض کل timeline، تجمیع Undo gesture و حداقل طول ۲۰۰ms برای effect.
- **r10.49:** SRT import/export، سازگاری مدل ۱۱کلاسهٔ CCSN با preprocessing مستقل، تفکیک Contrail از جنس‌های WMO، اصلاح DayLine jitter registration، Fade-in/Fade-out موسیقی و voice، ذخیرهٔ پروژه با ProjectCodec v5، cache موج صوتی.
- **CI:** اجرای \`testDebugUnitTest\` پیش از APK؛ گزارش XML/HTML تست‌ها حتی در شکست artifact می‌شود. ZIP workflow دیگر patch قدیمی Kotlin را روی سورس جدید اعمال نمی‌کند و docs-only commitها بیلد بیهوده راه نمی‌اندازند.
- **اعتبار r10.49:** ۹۶ تست JVM، صفر شکست، صفر skipped؛ APK شاخهٔ main ساخته و تأیید شده است.

## وضعیت کاری r10.51
- [x] ورود و خروج SRT؛ cueها به کلیپ‌های متنی قابل‌ویرایش تبدیل و دوباره به SRT صادر می‌شوند.
- [x] parser/serializer تست‌شده: comma/dot، چندخطی، BOM، cue settings، entityها و round-trip.
- [x] مدل ۱۰کلاسهٔ داخلی و مدل CCSN ۱۱کلاسه با امضای tensor و preprocessing جداگانه.
- [x] Contrail (Ct) به‌عنوان برچسب دیتاستی جدا از ۱۰ جنس طبیعی WMO.
- [x] اصلاح هم‌ترازی DayLine؛ تست صحنهٔ jitter بدون صاعقه و نگه‌داشتن صاعقهٔ باریک سبز.
- [x] Fade-in/Fade-out مستقل موسیقی و صدای ضبط‌شده؛ Undo/Redo و ProjectCodec v5.
- [x] mute مستقل موسیقی و voice، mute صدای اصلی و Solo برای سه مسیر؛ ProjectCodec v6.
- [x] قفل overlay/effect برای جلوگیری از drag، trim، حذف و تغییر keyframe؛ ProjectCodec v7.
- [x] drag reorder قطعات اصلی ویدئو؛ لمس کوتاه همچنان انتخاب می‌کند و خط‌کش playhead را جابه‌جا می‌کند.
- [x] انتخاب چندگانه، جابه‌جایی گروهی با snap، حذف گروهی و محافظت در برابر عضو قفل‌شده.
- [x] هیستوگرام RGB/luma در preview ادیتور عکس، محاسبهٔ background از snapshot امن.
- [x] cache موج PCM با LRU محدود و تست‌های eviction.
- [x] snapshotهای قبلی r10.51 (قفل/ترتیب ویدئو) تست و APK build موفق داشته‌اند.
- [ ] سبزشدن تست و APK روی آخرین commit شامل group overlay و histogram.
- [ ] ادغام r10.51 در main و ساخت APK نهایی از commit ادغام‌شده.

## Creative Studio — موارد باز

### Video
- [x] جابه‌جایی و trim مستقیم کلیپ‌های overlay/effect.
- [x] reorder قطعات اصلی ویدئو، lock overlay/effect، multi-select/group برای overlay.
- [x] waveform واقعی PCM و cache موج.
- [x] mute/solo موسیقی، voice و صدای اصلی؛ fade موسیقی/voice.
- [x] زیرنویس SRT import/export.
- [ ] multi-select/group برای قطعات اصلی video و عملیات گروهی trim/delete.
- [ ] gain/fade/pan مستقل برای هر کلیپ صوتی و waveform per-clip cache.
- [ ] transitions واقعی بین کلیپ‌ها؛ speed ramp/reverse/freeze.
- [ ] keyframe کامل برای position/scale/rotation/opacity و audio automation.
- [ ] masking/chroma key، LUT/color wheels و ابزارهای پیشرفتهٔ رنگ ویدئو.
- [ ] export presets با resolution/FPS/bitrate، preflight، queue، verification و recovery.
- [ ] autosave و بازیابی پروژه پس از crash؛ backup اتمیک و جلوگیری از کپی تکراری رسانه‌های بزرگ.
- [ ] instrumentation و تست دستگاه واقعی برای فایل‌های بلند، خراب، لغو export و lifecycle.

### Photo
- [x] ابزارهای نور، رنگ، HSL، crop/rotate، healing، mask و layerهای متن/شکل/تصویر موجود.
- [x] هیستوگرام RGB و luma در preview.
- [ ] curves واقعی با control point و کانال RGB مستقل.
- [ ] color wheels و local adjustment masks با blend modes.
- [ ] perspective correction، lens profile/correction، RAW/DNG.
- [ ] clone/patch و object removal پیشرفته‌تر.
- [ ] before/after slider، histogram دقیق full-resolution برای export و metadata.
- [ ] تست memory/performance روی عکس‌های بزرگ و export PNG/JPEG.

## Atmospheric Intelligence / Cloud Atlas
- [x] runtime داخلی برای ۱۰ جنس طبیعی WMO.
- [x] runtime سازگار با مدل واردشدهٔ CCSN یازده‌کلاسه، شامل Contrail.
- [ ] تست عملی import/inference مدل CCSN روی Android واقعی؛ تأیید tensor names/shapes و latency/RAM.
- [ ] ارزیابی مستقل روی test set بدون leakage/near-duplicate؛ macro-F1، per-class precision/recall، confusion matrix و calibration.
- [ ] baseline جدید ۱۰ جنس WMO با dataset RGB دارای مجوز روشن و split مستقل.
- [ ] مدل چندبرچسبی ویژگی‌های ساختاری با annotation متخصص و کلاس unknown/uncertain.
- [ ] segmentation چندکلاسه با ماسک انسانی و mIoU/Dice؛ وزن‌های مستقل و قرارداد runtime مستقل.
- [ ] temporal consensus با timestamp/camera alignment و محدودیت‌های علمی روشن.
- [ ] بازبینی یکپارچه UI داشبورد، Cloud Atlas، AI Recognition و Atmospheric Analysis.

## کیفیت و تحویل
- [ ] تمام JVM tests روی آخرین commit سبز؛ regression برای parser، model contract، DayLine، timeline، codecs، waveform و export.
- [ ] Android instrumentation/UI tests و تست دستگاه واقعی؛ حافظه، crash، performance و accessibility.
- [ ] ZIP کامل سورس از همان commit بیلدشده + APK + گزارش فارسی + SHA256.
- [ ] وضعیت هر قابلیت فقط بعد از شواهد build/test/device به Done تغییر کند.

## موانع/وابستگی‌ها
- فایل وزن CCSN واردشده: مخزن مبدأ مجوز بازتوزیع روشن ندارد؛ فایل وزن در repo عمومی/APK bundle نمی‌شود و از Import کاربر بارگذاری می‌شود.
- دقت علمی هیچ مدل تا ارزیابی مستقل گزارش نشده است.
- GitHub Actions جای تست واقعی روی گوشی را نمی‌گیرد؛ آزمون دستگاه فیزیکی همچنان لازم است.

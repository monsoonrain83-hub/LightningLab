# ممیزی تطبیقی ادیتورهای عکس و ویدئوی LightningLab

تاریخ بررسی: ۹ نوامبر ۲۰۲۶  
مبنای ممیزی: سورس بستهٔ r10.37، فایل‌های `Screens.kt`، `MainVM.kt`، `PhotoEdit.kt`، `PhotoLayers.kt`، `VideoFx.kt`، `ProjectCodec.kt` و `PhotoProjectCodec.kt`؛ به‌علاوه README و قابلیت‌های اعلام‌شده در مخازن عمومی GitHub. این بررسی ممیزی کد و قابلیت‌های اعلام‌شده است، نه تأیید عملکرد همهٔ پروژه‌های مرجع روی دستگاه.

## ۱. مخازن مرجع بررسی‌شده

| مخزن | حوزه و ارزش الگوبرداری | مجوز/ملاحظه |
|---|---|---|
| [CapCraft for Android](https://github.com/artcraft-android/capcraft-android) | نزدیک‌ترین مرجع ویدئوی موبایل: تایم‌لاین چندمسیره، انتخاب چندکلیپی، pinch zoom، snapping، ripple edit، trim، speed curve، freeze/reverse، mask و chroma key، blend mode، ۸۰+ فیلتر، افکت و transitionهای قابل پیش‌نمایش، متن/فونت/قالب/انیمیشن، کپشن، صدا و preset خروجی تا 4K/60fps | AGPL-3.0-or-later و موتور Rust؛ ایده‌ها قابل الگوبرداری‌اند، ولی انتقال کد/موتور بدون بررسی تعهدات مجوز انجام نمی‌شود. پروژه Beta است. |
| [LightCraft for Android](https://github.com/artcraft-android/lightcraft-android) | مرجع عکس غیرمخرب: RAW، white balance، RGB point curves، color grading wheels، ۸-band color mixer، ماسک‌های brush/linear/radial، preset preview، crop/straighten، قبل/بعد، مدیریت کتابخانه و متادیتا | مخزن MIT OR Apache-2.0 اعلام می‌کند؛ موتور Rust و وابستگی‌های native دارد. قبل از ادغام هر قطعه، مجوز همان فایل/وابستگی بررسی شود. پروژه Beta است. |
| [CheapShot / ClearCut](https://github.com/johncreson/CheapShot-Editor) | مرجع بلوغ workflow: تایم‌لاین چندلایه، snap، گروه‌کردن کلیپ‌ها، multi-select، speed ramp، keyframe با easing، markers، haptic feedback، waveform cache، track collapse/height، shortcut، project migration، recovery، export preflight و گزارش خطای قابل کپی | این مخزن fork از ClearCut است؛ وضعیت و مجوز هر نسخه باید از همان revision بررسی شود. README بسیار گسترده است و برخی امکانات ممکن است وابسته به نسخه باشند. |
| [ActionCut](https://github.com/naveenneog/ActionCut) | الگوی Compose/Media3: playhead ثابت وسط صفحه و حرکت محتوا زیر آن، موتور pure Kotlin و تست‌شده برای split/trim/ripple/move، undo/redo، ابزارهای LUT، خروجی پس‌زمینه با WorkManager و طراحی تیرهٔ مینیمال | مخزن کوچک‌تر و تعداد ستاره‌ها محدود است؛ از معماری/ایده‌ها استفاده شود، نه فرض بلوغ محصول. |
| [Allay Photo Editor](https://github.com/Allay054/Photo-Editor) | مرجع سبک‌تر Kotlin: ابزار annotation، contextual toolbar، انتخاب/جابجایی اشیا، duplicate/delete، gesture history و تاریخچهٔ مرکزی برای crop/filter/transform/layers | مناسب الگوهای UI و تاریخچه؛ جایگزین موتور حرفه‌ای RAW یا compositing نیست. |

## ۲. وضعیت فعلی LightningLab و فاصله‌ها

علامت‌ها: **داریم** = در سورس فعلی دیده شد؛ **نیمه‌کاره** = بخشی از منطق وجود دارد اما workflow کامل نیست؛ **نداریم** = در سورس فعلی پیدا نشد؛ **نیازمند راستی‌آزمایی** = باید روی دستگاه/خروجی واقعی تست شود.

### ادیتور عکس

| قابلیت | وضعیت فعلی | فاصله و کار لازم |
|---|---|---|
| تنظیم نور، کنتراست، هایلایت، سایه، vibrance، warmth، HSL و S-curve | داریم | کنترل‌ها باید histogram و بازخورد عددی/قبل‌بعد داشته باشند؛ S-curve فعلی جای RGB point-curve قابل‌ویرایش را نمی‌گیرد. |
| متن/شکل/تصویر با z-order مشترک، opacity، rotation و undo/redo | داریم | duplicate در این فاز اضافه شد؛ drag مستقیم روی بوم، pinch، handles، lock، group، blend modes و clipping masks باقی است. |
| ماسک قلمی و inversion | داریم | radial/linear gradient، feather handle، mask overlay قابل‌مشاهده، چند ماسک نام‌دار و local adjustment layer نداریم. |
| Healing / inpaint | داریم | clone stamp، patch tool، object selection و content-aware fill حرفه‌ای نداریم؛ کیفیت ترمیم باید روی عکس‌های متنوع benchmark شود. |
| crop و aspect ratio | محدود | crop handles، straighten، grid overlays، perspective correction و نسبت‌های سفارشی کامل نیستند. |
| RAW / DNG، lens profile، highlight recovery | نداریم | نیازمند pipeline مستقل و موتور decode/develop؛ قابلیت ساده‌ای برای افزودن به `Bitmap` نیست. |
| RGB point curves، color wheels، split toning، ۸-band color mixer | نداریم | از LightCraft به‌عنوان مرجع طراحی/ریاضی استفاده شود؛ پیاده‌سازی GPU یا LUT لازم است. |
| preset قابل‌ذخیره و پروژهٔ غیرمخرب | داریم | preview thumbnail، دسته‌بندی، favorite، import/export preset و recovery خودکار پروژه باقی است. |
| مدیریت کتابخانه، جست‌وجو، آلبوم، rating/flag و metadata | نداریم | صفحهٔ Library مستقل نیاز دارد؛ از scope خود ادیتور جدا شود. |
| خروجی با کیفیت/ابعاد/فرمت قابل انتخاب | محدود | JPEG quality، WebP، رزولوشن/resize، metadata policy و تخمین حجم باید اضافه شود. |
| gesture و دسترسی ابزارها | ضعیف‌تر از مرجع | انتخاب لایه با tap روی بوم، drag handles، pinch zoom، pan، ابزار contextual نزدیک انتخاب و landscape workspace نداریم. |

### ادیتور ویدئو

| قابلیت | وضعیت فعلی | فاصله و کار لازم |
|---|---|---|
| چند قطعهٔ ویدئو، trim و reorder ساده، موسیقی و voice track | داریم | track model مستقل با start/end/offset/volume/fades و gapهای قابل‌مشاهده لازم است. |
| waveform موسیقی و voice | داریم | waveform decode فعلی باید cache، downsample چندسطحی و invalidation داشته باشد؛ waveform به‌صورت track قابل‌جابجایی/برش نیست. |
| تایم‌لاین متن/استیکر/تصویر و افکت زمان‌دار | داریم | ردیف‌ها هنوز عمدتاً نمایشگرند؛ drag-to-move/trim، multi-select، grouping و snapping کامل ندارند. |
| keyframe شفافیت و موقعیت عمودی | محدود | position X/Y، scale، rotation، volume، speed و easing curves ندارند. |
| پیش‌نمایش | **بهبود r10.39، هنوز ناقص** | ExoPlayer/TextureView و overlayهای زمان‌دار در preview فعال‌اند؛ اما pipeline فیلتر/رنگ/ترنزیشن هنوز با export مشترک نیست و هم‌ارزی کامل تضمین نمی‌شود. |
| scrub/playhead | **پایه اضافه شد** | seek، playhead دنبال‌کننده و frame-step با گام مبتنی بر metadata اضافه شد؛ playhead ثابت مرکزی و frame-accurate scrubbing هنوز کامل نیست. |
| timeline zoom / snapping / markers | **بخشی اضافه شد** | zoom افقی ۱× تا ۴× با scroll و snap پایه به لبه‌های clip اضافه شد؛ pinch، fit، markerها و نشانگر snap باقی‌اند. |
| split، ripple delete، slip/roll trim، freeze، reverse، speed ramp | **Split پایه اضافه شد** | Split قطعهٔ انتخاب‌شده در playhead و حداقل طول ۲۰۰ms اضافه شد؛ ripple delete، slip/roll trim، freeze، reverse و speed ramp هنوز نداریم. |
| transitions | نداریم | crossfade، wipe، zoom، blur و transition preview بین دو clip لازم است. |
| effects و LUT | محدود | چند filter پایه و effect clip داریم؛ LUT `.cube`، intensity، preset shelves، افکت‌های زمان‌دار متنوع و preview thumbnail نداریم. |
| audio editing | محدود | clip gain و چند volume کلی داریم؛ per-clip fade, pan, ducking, EQ, noise reduction, voice isolation و audio-only/stem export نداریم. |
| captions/subtitles | نداریم | واردکردن SRT، caption clips، و transcription آفلاین با مدل اختیاری لازم است. |
| export | محدود | H.264/AAC هست؛ resolution، fps، bitrate، quality preset، estimated size، preflight، output verification، queue و background worker نداریم. |
| project lifecycle | داریم، دستی | autosave، crash recovery، rename/duplicate/archive، import/export project و نسخه‌بندی مهاجرتی مقاوم‌تر لازم است. |
| history | ناقص | undo/redo قطعات اصلی هست، اما تمام عملیات overlay/effect/audio/clip در یک تاریخچهٔ واحد و پایدار نیستند. |

## ۳. جزئیات UX که باید از مراجع اقتباس شوند

1. **Playhead ثابت در مرکز** و حرکت تایم‌لاین زیر آن؛ لمس و کشیدن برای scrub با frame-step و timecode.
2. **Pinch-to-zoom** به‌همراه دکمه‌های `− / + / Fit` و حفظ نقطهٔ زمانی زیر انگشت هنگام zoom.
3. **Snap مغناطیسی** با نشانگر عمودی هنگام نزدیک‌شدن به ابتدا/انتهای کلیپ، playhead و marker؛ امکان خاموش‌کردن.
4. **Clip handles واقعی** برای trim دوطرفه، drag برای جابه‌جایی، long-press برای multi-select، group/ungroup و ripple delete.
5. **Track header ثابت** با mute/solo/lock/hide، collapse، تغییر ارتفاع و رنگ/نام اختصاصی.
6. **Contextual tool dock**: ابزارهای متناسب با انتخاب جاری ظاهر شوند؛ کاربر برای هر تغییر مجبور به پیمایش پنل طولانی نباشد.
7. **Preview قابل لمس**: در r10.39 انتخاب overlay با tap و نمایش زمان‌دار فعال است؛ drag مستقیم overlay و resize با pinch/handles و rotate دو انگشتی هنوز باقی‌اند. در ادیتور عکس، drag لایهٔ انتخاب‌شده از فهرست لایه‌ها اضافه شد؛ handles و pinch باقی‌اند.
8. **Thumbnail strip و waveform cache**: پیش‌نمایش فریم‌های واقعی clip، cache چندوضوحی و تولید در background.
9. **Before/After واقعی** با نگه‌داشتن یا split slider؛ reset مستقل هر گروه تنظیمات.
10. **Export preflight**: هشدار برای فایل گم‌شده، رزولوشن نامعتبر، صدای بدون منبع، فضای ناکافی و افکتی که backend آن را پشتیبانی نمی‌کند.
11. **Recovery شفاف**: autosave، نسخهٔ پشتیبان، تشخیص پروژهٔ ناقص و گزارش اینکه چه چیزی بازیابی نشده؛ هرگز پروژهٔ ناقص بی‌صدا روی نسخهٔ سالم نوشته نشود.
12. **Haptics و دسترسی‌پذیری** برای گرفتن handle، snap، split و انتخاب؛ هدف لمسی حداقل 48dp، برچسب TalkBack و کنترل‌های با فاصلهٔ مناسب.
13. **Adaptive workspace**: در حالت افقی/تبلت preview و timeline کنار هم؛ در گوشی عمودی preview بالا و timeline/tool dock پایین.

## ۴. تغییرات انجام‌شده در این بسته

- موتور ریاضی تایم‌لاین از پیاده‌سازی پارک‌شده به سورس فعال بازگردانده شد؛ توابع zoom interpolation، clamp، locate، outPosition و split اکنون در مسیر اصلی unit-testable هستند.
- snap پایه به مرز ابتدا/انتها و مرز کلیپ‌های overlay/effect اضافه شد؛ کلید روشن/خاموش «چسبندگی» در هدر تایم‌لاین قرار گرفت.
- duplicate برای لایه‌های متن، شکل و تصویر در ادیتور عکس اضافه شد؛ تصویر duplicate bitmap مستقل می‌گیرد تا تغییر/حذف یک لایه روی دیگری اثر نگذارد.
- تست‌های TimelineMath که به کلاس‌های غایب ارجاع می‌دادند اکنون پیاده‌سازی متناظر دارند و دو تست snapping اضافه شد.

## ۵. نقشهٔ اجرای باقی‌مانده (ترتیب پیشنهادی)

### P0 — صحت و ایمنی تدوین
- ساخت preview مشترک با export برای video (Media3 ExoPlayer + effects/overlay composition یا preview renderer اختصاصی).
- unified video command history با snapshot/command و undo/redo برای تمام editها.
- autosave، recovery، atomic project store، migration و گزارش خرابی/دارایی گمشده.
- export preflight، کنترل کیفیت/رزولوشن/fps/bitrate، verification و خطای مرحله‌ای.

### P1 — تایم‌لاین حرفه‌ای
- playhead ثابت، scrubbing و frame stepping؛ timeline zoom و fit.
- drag/move/trim/split/ripple، snap indicators، markers، multi-select و group.
- video thumbnails و waveform cache؛ track mute/solo/lock/collapse/height.
- clip-level audio gain/fade/pan و keyframeهای کامل با easing.

### P2 — ابزارهای خلاقانه ویدئو
- transitions، speed ramp، reverse، freeze frame، mask/chroma key، LUT `.cube` و effect shelves.
- captions/SRT، text templates/fonts/animations، sound FX browser، audio-only/stem export.

### P3 — عکاسی حرفه‌ای
- canvas gesture editing و contextual handles؛ layer lock/group/duplicate و blend modes.
- local adjustment layers، radial/linear masks، RGB point curves، color wheels، 8-band mixer.
- crop/straighten/perspective، clone/patch/object removal، RAW/DNG و lens correction.
- preset library، before/after slider، histogram، metadata و export presets.

### P4 — تجربه و کیفیت محصول
- landscape/tablet workspace، accessibility/haptics، onboarding کوتاه و قابل replay.
- benchmark زمان preview/export، memory budget، cache policy و تست روی دستگاه‌های ضعیف/قوی.
- UI screenshot tests، instrumentation tests و تست round-trip پروژه/خروجی.

## ۶. سیاست ادغام

- در این مرحله کد پروژه‌های دیگر کپی یا dependency سنگین Rust/FFmpeg اضافه نشده است؛ فقط الگوها و قابلیت‌ها ممیزی شده‌اند.
- CapCraft با AGPL-3.0-or-later منتشر شده است؛ انتقال کد آن می‌تواند تعهدات انتشار متناظر ایجاد کند. LightCraft مجوز MIT/Apache اعلام می‌کند، اما مجوز فایل‌ها و وابستگی‌های native باید جداگانه بررسی شود.
- هر قابلیت باید هم در preview و هم در export کار کند، در پروژه ذخیره/بازیابی شود و تست pure/unit یا device داشته باشد؛ اضافه‌کردن کنترل UI بدون اتصال به موتور، قابلیت محسوب نمی‌شود.


## r10.39 — پیش‌نمایش واقعی‌تر و کنترل مستقیم تدوین

### ادیتور ویدئو
- موتور پیش‌نمایش از `VideoView` به Media3 ExoPlayer منتقل شد؛ PlayerView با TextureView استفاده می‌شود تا لایه‌های Compose روی تصویر ویدئو قابل نمایش باشند.
- لایه‌های متن، استیکر و تصویر زمان‌دار در پیش‌نمایش بر اساس playhead فعال/غیرفعال می‌شوند؛ شفافیت و موقعیت عمودی Keyframeها در preview ارزیابی می‌شوند.
- Playhead با نرخ ۱۰۰ میلی‌ثانیه از وضعیت واقعی Player به‌روزرسانی می‌شود؛ seek با Slider و دکمه‌های یک فریم قبل/بعد اضافه شد. گام فریم از metadata نرخ فریم منبع خوانده می‌شود و در نبود آن ۳۰fps فرض می‌شود.
- سرعت پخش پیش‌نمایش با سرعت تدوین هماهنگ می‌شود.
- seek به موقعیت خروجی، قطعهٔ متناظر را در فهرست segmentها پیدا می‌کند و در صورت نیاز منبع ویدئویی Player را عوض می‌کند.
- زوم تایم‌لاین ۱× تا ۴× و اسکرول افقی برای مشاهدهٔ جزئیات بازه‌ها اضافه شد.
- Split قطعهٔ انتخاب‌شده در موقعیت playhead اضافه شد؛ حداقل طول هر نیمه ۲۰۰ms است.
- متن، استیکر و تصویر تازه در محل playhead درج می‌شوند و طول پیش‌فرض آن‌ها ۵ ثانیه است (یا تا انتهای پروژه اگر کوتاه‌تر باشد).

### ادیتور عکس
- پس از انتخاب لایهٔ متن/شکل/تصویر از فهرست لایه‌ها، می‌توان آن را مستقیماً روی بوم با drag جابه‌جا کرد.
- تغییرات حین drag به‌صورت زنده اعمال می‌شوند و فقط در پایان gesture یک مرحلهٔ Undo ثبت می‌شود؛ تاریخچه با هر حرکت انگشت پر نمی‌شود.
- Duplicate لایه‌های تصویری و شکلی، لایهٔ کپی‌شده را انتخاب می‌کند تا فوراً قابل جابه‌جایی باشد.

### اعتبارسنجی
- منطق pure Kotlin تایم‌لاین با `kotlinc` جداگانه قابل کامپایل است.
- بیلد کامل Android، آزمون UI، پخش دستگاهی، و هم‌ارزی پیکسل‌به‌پیکسل preview/export در این محیط اجرا نشده‌اند.


## ۷. وضعیت واقعی پس از r10.39 و مواردی که هنوز پیاده‌سازی نشده‌اند

این نسخه پیش‌نمایش Media3، نمایش لایه‌های زمان‌دار، seek، frame-step، zoom/scroll تایم‌لاین، split در playhead و drag مستقیم لایه‌های عکس را اضافه می‌کند. با این حال پروژه هنوز هم‌سطح CapCut/LightCraft/ClearCut نیست. موارد عمدهٔ باقیمانده:

- ویدئو: انتقال واقعی clip با drag، trim با دستگیره‌های تایم‌لاین، ripple delete و multi-select/group؛ waveform cache و thumbnail strip؛ marker و track mute/solo/lock؛ transition واقعی بین کلیپ‌ها؛ speed ramp/reverse/freeze؛ LUT و فیلترهای متنوع؛ ماسک/chroma key؛ keyframe کامل X/Y/scale/rotation با easing؛ gain/fade/pan مستقل هر قطعه؛ زیرنویس SRT و transcription آفلاین؛ export preset/bitrate/fps/4K و preflight/verification/queue؛ history یکپارچه و autosave/recovery.
- عکس: ماسک شعاعی/خطی و local adjustment؛ blend modes و clipping؛ RGB curves/color wheels؛ RAW/DNG و اصلاح لنز؛ ابزار clone/patch/object removal دقیق؛ perspective correction؛ histogram، preset browser و Before/After slider؛ کتابخانه و metadata؛ انتخاب/resize/rotate چندلمسی و handles دقیق.
- کیفیت محصول: تست instrumentation و screenshot، تست روی دستگاه‌های مختلف، سنجش حافظه و سرعت رندر، دسترس‌پذیری و چیدمان landscape/tablet.

این‌ها صرفاً ایده نیستند؛ هنوز در سورس فعال کامل نشده‌اند و نباید در معرفی نسخه به‌عنوان قابلیت موجود اعلام شوند. پیش‌نمایش Media3 در r10.39 هنوز همهٔ فیلترهای Export را از همان pipeline مشترک دریافت نمی‌کند؛ overlayها و زمان‌بندی نمایش داده می‌شوند اما تطابق کامل رنگ/افکت/ترنزیشن با خروجی تضمین نشده است.

## r10.40 — تاریخچه، ذخیرهٔ امن و پیش‌بررسی خروجی

- Undo/Redo مرکزی ویدئو اکنون snapshotهای مشترک از segmentها، overlayها، effectها، bitmapهای لایه، انتخاب جاری، تنظیمات transform/color، سرعت و منابع صوتی نگه می‌دارد. محدودیت فعلی تاریخچه ۶۰ وضعیت است.
- کنترل Undo/Redo به نوار بالای ادیتور منتقل شد؛ کنترل‌های موجود تایم‌لاین نیز از همان تاریخچه استفاده می‌کنند.
- تغییر تنظیمات صدا، سرعت، فیلتر، crop، چرخش، flip، روشنایی و کنتراست پس از اتمام gesture یک مرحلهٔ تاریخچه ثبت می‌کند.
- حذف overlay تصویری bitmap را فوری recycle نمی‌کند تا Undo بتواند آن را برگرداند. این کار مصرف حافظه را تا زمان آزادشدن ViewModel افزایش می‌دهد؛ پاک‌سازی هوشمند bitmapهای خارج از پنجرهٔ تاریخچه هنوز باید تکمیل شود.
- ذخیرهٔ ویدئو در پوشهٔ موقت مخفی انجام می‌شود؛ manifest ابتدا تکمیل و سپس کل پوشه به نام نهایی rename می‌شود. پوشه‌های staging قدیمی‌تر از یک روز هنگام بازشدن فهرست پروژه‌ها پاک می‌شوند.
- بدنهٔ overlayهای زمان‌دار روی تایم‌لاین با drag جابه‌جا می‌شود؛ تغییرات زنده ثبت می‌شوند و در پایان حرکت یک نقطهٔ Undo ساخته می‌شود. trim دقیق overlay همچنان با اسلایدرهای شروع/پایان انجام می‌شود.
- Export preflight بازهٔ source segmentها، زمان‌بندی overlay/effect، وجود فایل‌های محلی، ابعاد ویدئو و فضای خالی را بررسی می‌کند.

### مواردی که همچنان انجام نشده‌اند
- autosave دوره‌ای و بازیابی خودکار آخرین نشست هنوز پیاده‌سازی نشده است؛ ذخیرهٔ اتمیک پروژه با autosave تفاوت دارد.
- drag واقعی کلیپ در تایم‌لاین، trim handles، ripple delete، multi-select/group، track mute/solo/lock، markers و thumbnail cache باقی‌اند.
- transitions، speed ramp، reverse/freeze، LUT، chroma key/masking، keyframeهای کامل X/Y/scale/rotation و easing، و کنترل صدای مستقل هر کلیپ باقی‌اند.
- زیرنویس SRT و transcription آفلاین، تنظیمات کامل خروجی 4K/FPS/bitrate و صف export باقی‌اند.
- در عکس: local adjustment masks، RGB curves/color wheels، RAW/DNG، lens correction، perspective, clone/patch، histogram و before/after slider باقی‌اند.
- این نسخه نیز به‌عنوان بیلد کامل Android تأیید نشده؛ پس از آپلود باید GitHub Actions و سپس تست واقعی روی گوشی بررسی شوند.

## ۸. معیارهای محصول تجاری برای تجربهٔ کاربری

در مقایسهٔ قابلیت‌های اعلام‌شدهٔ اپ‌های شناخته‌شده و مستندات رسمی آن‌ها:

| مرجع | الگوی قابل اقتباس | وضعیت LightningLab در r10.40 |
|---|---|---|
| Adobe Premiere mobile | workspace با preview و multi-track timeline، keyframe/easing، clip effects/transitions، audio enhance/fade، captions و export | preview و چند track پایه داریم؛ history و export preflight تقویت شد؛ timeline editing، captions، transitions و audio processing هنوز فاصله دارند. |
| Adobe Lightroom mobile | ماسک‌های مستقل و غیرمخرب، انتخاب Subject/Sky/Background، brush/linear/radial gradient و تنظیم موضعی | brush mask پایه داریم؛ ماسک‌های gradient و انتخاب هوشمند هنوز نداریم. |
| Google Snapseed | RAW، selective adjustments، healing، curves، HSL، perspective، film looks و before/after | ابزارهای پایهٔ نور/HSL/healing و hold-to-compare داریم؛ RAW، selective control points، perspective و film toolset کامل باقی است. |

مستندات رسمی برای پیگیری نسخه‌های بعدی: [Premiere mobile workspace](https://helpx.adobe.com/premiere/mobile/get-started/workspace-overview.html)، [Premiere mobile features](https://helpx.adobe.com/premiere/mobile.html)، [Lightroom masking](https://helpx.adobe.com/lightroom/mobile/masks-and-selective-adjustments/masking-tools.html)، [Snapseed tools](https://support.google.com/snapseed/answer/6183571?hl=en).

این جدول benchmark قابلیت‌هاست، نه ادعای برابری محصول. هدف توسعه، رسیدن به workflow کامل و قابل اتکا است؛ افزودن کنترل ظاهری بدون اتصال به پردازش، ذخیرهٔ پروژه و خروجی، در این ممیزی قابلیت کامل محسوب نمی‌شود.


## r10.41 — تثبیت بیلد و تدوین قطعات
- رفع خطاهای کامپایل گزارش‌شده در اجراهای GitHub Actions: import حالت Long، inference نوع در نتیجهٔ فریم ویدئو، overload صریح ImageDecoder و شاخه‌های rollback فایل مدل.
- Decode تصاویر از یک helper با `ImageDecoder.OnHeaderDecodedListener` صریح انجام می‌شود تا نوع listener در CI مبهم نماند.
- قطعهٔ انتخاب‌شده اکنون In/Out مستقل دارد و با RangeSlider دقیق trim می‌شود؛ تاریخچه پس از پایان حرکت یک بار ثبت می‌شود.
- ترتیب قطعات از طریق کنترل‌های جابه‌جایی قبل/بعد تغییر می‌کند؛ منبع و محدودهٔ زمانی هر قطعه حفظ می‌شود.
- Ripple Delete فاصلهٔ قطعهٔ حذف‌شده را در تایم‌لاین می‌بندد و زمان overlayها، effectها و markerها را جابه‌جا می‌کند.
- نشانگرهای تایم‌لاین قابل افزودن، حذف و seek هستند و در Undo/Redo و ProjectCodec v4 ذخیره می‌شوند؛ خواندن پروژه‌های v1-v3 حفظ شده است.
- موارد بعدی: drag آزاد روی تایم‌لاین، ripple editing، چندانتخابی و گروه‌بندی، markers و waveform per-clip.
- وضعیت بیلد r10.41 تا اجرای GitHub Actions هنوز تأیید نشده است.


## اصلاح بیلد r10.43
- در اجرای GitHub Actions شماره 181، متغیر `sel` داخل بدنهٔ یک `Row` تعریف شده بود اما کنترل‌های برش دقیق خارج از آن `Row` از همان متغیر استفاده می‌کردند.
- تعریف `sel` به scope بیرونی بخش قطعات منتقل شد؛ خطاهای `Unresolved reference: sel` و خطاهای آبشاری type inference رفع شدند.
- این اصلاح بر اساس لاگ واقعی run شماره 181 انجام شده است؛ موفقیت بیلد نسخهٔ جدید تا اجرای GitHub Actions تأیید نشده است.


## r10.44 — بازطراحی چیدمان و سلسله‌مراتب بصری

- صفحهٔ خانه به داشبورد با یک CTA اصلی، چهار کارت فضای کاری و تنظیمات ثانویه تبدیل شد؛ اقدام تشخیص از تکرار در چند نقطه حذف شد.
- هدر و وضعیت on-device فشرده شدند؛ وضعیت پیشرفت فقط هنگام پردازش ظاهر می‌شود.
- نوار پایین به dock کم‌ارتفاع‌تر با نشانگر انتخاب تبدیل شد.
- دسته‌بندی ابزار عکس و ابزارک‌های ویدئو جمع‌وجورتر شدند تا فضای preview بیشتر شود و تعداد ابزار قابل مشاهده در عرض گوشی افزایش یابد؛ همهٔ ابزارها حفظ شده‌اند.
- رشته‌های جدید خانه در سه زبان اضافه شدند.

### باقیماندهٔ UX
- بازطراحی کامل workspace افقی/تبلت و حالت landscape.
- contextual toolbar بر اساس انتخاب جاری و ابزارهای بیشتر با آیکون/برچسب یکپارچه.
- pinch zoom و handles چندلمسی در بوم عکس و ویدئو.
- screenshot/instrumentation tests و بررسی واقعی روی چند اندازهٔ صفحه.

این نسخه فقط مرحلهٔ اول polish رابط کاربری است؛ ادعای برابری کامل با Premiere، CapCut، Lightroom یا Snapseed ندارد.

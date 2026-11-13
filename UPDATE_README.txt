## r10.70 — رفع باگ‌های واقعی سورس r10.69 (versionCode 77)

- AudioWaveform: رفع فروپاشی پیک‌ها در آخرین bin وقتی metadata مدت صدا (KEY_DURATION) موجود نیست؛ ریاضیات توزیع به شیء خالص WaveformBins منتقل شد و تست‌های رگرسیون WaveformBinsTest.kt اضافه شد.
- VideoDraftStore: حذف PNGهای لایه‌هایی که پروژه دیگر ارجاع نمی‌دهد، پیش از انتشار manifest پیش‌نویس؛ جلوگیری از بازیابی تصویر لایهٔ حذف‌شده و نشت حافظهٔ ذخیره‌سازی.
- ModelFileRecovery: بکاپ .previous فقط پس از اعتبارسنجی موفق target حذف می‌شود؛ سه تست رگرسیون جدید.
- strings.xml (en/fa/ar): راهنمای import مدل با قرارداد واقعی هماهنگ شد (۱۰ جنس WMO اصلی + ۱۱کلاسهٔ legacy CCSN)؛ about_text به 1.0-r10.70 به‌روز شد.
- فایل خالی مردهٔ detektor.kt با stub مستند جایگزین شد.
- CI روی PR #29 سبز: تست‌های Python، JVM و ساخت APK موفق. تست گوشی فیزیکی همچنان لازم است.

---

## r10.68–r10.69 — پیش‌نویس خودکار ویدئو و امنیت پروژه‌های عکس (versionCode 75–76)

- r10.68 (PR #27): پیش‌نویس خودکار ویدئو با تأخیر ۲٫۵ ثانیه پس از هر ویرایش ثبت‌شده، نگهداری اتمیک متادیتا و تصاویر لایه‌ها با AtomicFile، مجوز خواندن پایدار URI و امکان بازیابی یا حذف آگاهانهٔ پیش‌نویس. ویدئو/صوت کپی نمی‌شوند؛ بازیابی به باقی‌ماندن فایل اصلی و اعتبار مجوز وابسته است.
- r10.69 (PR #28): کنترل مسیرهای امن و یکپارچگی SHA-256 پروژه‌های جدید Photo Studio پیش از بازکردن یا انتشار؛ رد manifest مخدوش؛ سازگاری پروژه‌های قدیمی بدون integrity.json با بررسی وجود فایل‌های ارجاع‌شده.

---

## r10.66–r10.67 — یکپارچگی پروژه‌ها و بازبینی UX (versionCode 73–74)

- r10.66: فهرست SHA-256 و اندازهٔ فایل‌های هر پروژهٔ ویدئو، بررسی سلامت فایل‌ها پیش از بازگشایی و کنترل مسیرهای ناامن.
- r10.67: بازبینی دستی رخداد صاعقه (تأیید/رد/نامشخص) مستقل از خروجی مدل در schema v3؛ گزارش CSV عکس و ویدئوی آسمان؛ سه اسلات ظاهر عکس؛ تخمین زمان/حجم خروجی ویدئو؛ چیدمان واکنش‌گرای خانه.

---

## r10.62–r10.65 — pipeline آموزش صاعقه و خروجی گزارش (versionCode 69–72)

- r10.62: pipeline آموزش temporal TFLite برای هفت برچسب Brixton، هشت فریم RGB، MobileNetV3Small و گزارش macro-F1/Brier/confusion matrix.
- r10.63 (PR #21): کنترل SHA-256 پیش از آموزش، رد نشت داده و metadata بازتولیدپذیری.
- r10.64: خروجی CSV/JSON رخدادها، import امن مدل زمانی TFLite و sampling هشت فریم.
- r10.65: بهینه‌سازی استخراج فریم با retriever مشترک، کنترل لغو و schema v2 گزارش.

---

## r10.58–r10.61 — کارت رخداد، پروفایل‌های خروجی و دیتاست (versionCode 65–68)

- r10.58: کارت رخداد صاعقه با زمان شروع/پایان، تعداد پالس و گستردگی روشنایی.
- r10.59: پروفایل‌های خروجی H.264 با رزولوشن و bitrate متناسب و عدم upscale.
- r10.60: آماده‌سازی دیتاست ویدئویی Brixton (CC BY 4.0) با هفت برچسب و split زمانی.
- r10.61: مقایسهٔ لغزشی before/after در Photo Studio.

---

## r10.46–r10.57 — بازیابی سورس و زیرساخت مدل‌ها (versionCode 53–64)

- r10.46–r10.49: بازیابی سورس، اصلاح pipeline بیلد، SRT، مدل‌های ابر، DayLine، fade صوت، waveform cache و پروژه‌های ویدئو.
- r10.50: تاریخچهٔ یکپارچهٔ undo/redo ویدئو، انتشار اتمیک پروژه و preflight خروجی.
- r10.51: کنترل مستقل mute/solo صدا، قفل overlay/effect، مرتب‌سازی قطعات و هیستوگرام عکس.
- r10.52: انتخاب چند قطعه، جابه‌جایی گروه پیوسته و حذف گروهی با Undo.
- r10.53: منحنی‌های پنج‌نقطه‌ای R/G/B و RGB مشترک با LUT.
- r10.54: دسته‌بندی نشانه‌های ظاهری صاعقه (heuristic).
- r10.55: pipeline دادهٔ چندناظر Montenegro با soft-label برای CL/CM/CH.
- r10.56: نمودار تعاملی drag برای منحنی عکس.
- r10.57: runtime و pipeline آموزش مدل سه‌سر WMO با خروجی [1,3,11].

---

## r10.45 — تایم‌لاین تعاملی و ویرایش دقیق‌تر ویدئو

- خط‌کش تایم‌لاین اکنون تعاملی است: لمس یا کشیدن روی خط‌کش، playhead را به همان موقعیت می‌برد و پیش‌نمایش ویدئو را seek می‌کند؛ این رفتار با زوم ۱× تا ۴× نیز هماهنگ است.
- موقعیت فعلی و مدت کل پروژه به‌صورت زنده زیر خط‌کش نمایش داده می‌شود؛ playhead با خط تأکیدی مشخص است و نشانگرهای ذخیره‌شده روی خط‌کش tick جداگانه دارند.
- دکمهٔ «تناسب» زوم تایم‌لاین را به ۱× برمی‌گرداند و پیمایش افقی را به ابتدای تایم‌لاین می‌آورد.
- Magnetic snapping هنگام تنظیم بازهٔ لایه‌های متن/تصویر/استیکر و افکت‌ها، علاوه بر مرز کلیپ‌ها، به نشانگرهای کاربر و مرز قطعات ویدئو نیز متصل می‌شود.
- تبدیل مختصات خط‌کش به زمان به تابع مستقل `TimelineMath.timeAtFraction` منتقل شد و برای ابتدا، انتها، مختصات خارج محدوده و مدت نامعتبر تست واحد اضافه شد.
- نسخهٔ برنامه: `1.0-r10.45` / `versionCode 55`.

### اعتبارسنجی
- تست مستقل Kotlin برای `Timeline.kt` انجام می‌شود؛ این تست جایگزین Android Gradle build یا تست لمسی روی دستگاه نیست.
- بیلد کامل Android باید پس از آپلود `update.zip` در GitHub Actions بررسی شود.

---

## r10.44 — بازطراحی تجربهٔ کاربری و چیدمان ابزارها

- صفحهٔ خانه از چیدمان بلند و تکراری به داشبورد اولویت‌محور تبدیل شد: یک اقدام اصلی برای تشخیص، چهار فضای کاری در کارت‌های ثانویه، وضعیت پردازش و تنظیم حساسیت در بخش مستقل.
- هدر خانه کوتاه‌تر شد تا فضای بیشتری برای محتوای اصلی باقی بماند؛ وضعیت پردازش روی دستگاه و انتخاب زبان در دسترس ماند.
- کارت‌های ابزار، نوار ناوبری و دکمه‌های ابزار ادیتورها فشرده‌تر و یکدست‌تر شدند؛ حالت انتخاب‌شده با رنگ تأکیدی و سطح مشخص دیده می‌شود، بدون آنکه ابزارها حذف شوند.
- ردیف دسته‌بندی ابزارهای عکس به سگمنت‌های جمع‌وجورتر تبدیل شد؛ نوار ابزار ویدئو نیز فضای افقی کمتری برای هر ابزار می‌گیرد تا گزینه‌های بیشتری هم‌زمان دیده شوند و باقی ابزارها همچنان با پیمایش افقی در دسترس‌اند.
- متن‌های جدید صفحهٔ خانه برای فارسی، انگلیسی و عربی به منابع ترجمه منتقل شدند.
- نسخهٔ برنامه به `1.0-r10.44` و `versionCode 54` ارتقا یافت.

### کارهای مهم باقی‌مانده

این انتشار بازطراحی تجربهٔ خانه و نوار ابزار است، نه تکمیل همهٔ قابلیت‌های حرفه‌ای. اولویت‌های بعدی همچنان عبارت‌اند از: autosave و crash recovery؛ تاریخچهٔ یکپارچهٔ تمام عملیات؛ drag/trim واقعی کلیپ‌ها، multi-select و گروه‌بندی؛ thumbnail cache و waveform چندسطحی؛ track controls؛ transition و speed ramp؛ keyframe کامل؛ subtitle/SRT؛ presetهای خروجی و صف export؛ و در ادیتور عکس، handles چندلمسی، ماسک شعاعی/خطی، RGB curves، histogram، before/after slider و خروجی قابل تنظیم. هر مورد باید به موتور پردازش، ذخیرهٔ پروژه و خروجی واقعی متصل شود، نه اینکه صرفاً کنترل UI اضافه شود.

LightningLab update r10.41 (versionCode 51) — build repair and video clip editing

Major photo/video editor continuation:

PHOTO EDITOR
- Added editable photo-project persistence: original image, adjustment parameters (including HSL), text/shape/image layers, mask strokes and mask inversion are saved to app-private project storage.
- Imported image layers are copied into each project as PNG assets; loading restores editable layers rather than a flattened export.
- Added a photo-project browser with save, open and delete actions.

VIDEO EDITOR
- Added real decoded audio waveforms for imported music and recorded voice tracks. Waveform peaks are extracted from decoded PCM on an IO worker and rendered as separate MUSIC and VOICE lanes.
- Added editable timed IMAGE overlays with independent timeline clips, position/opacity keyframes and project-owned PNG assets.
- Added timed effect clips: selected colour filters can now be applied to a chosen interval instead of the entire video; effect clips are separate from the base filter setting.
- Export splits the timeline at image, effect and animation boundaries; opacity interpolates between keyframes and vertical anchor changes snap to the nearest keyframe.
- Video project codec upgraded to v3 and persists image assets, effect clips and keyframes while continuing to read v1/v2 projects.

VALIDATION
- Added unit tests for photo-project round trips, video overlay keyframe interpolation and v1 project compatibility.
- ZIP integrity can be checked after packaging. Android Gradle build and instrumentation/device playback tests were not run in this environment.


## r10.37 — تکمیل چرخش لایه‌ها و خروجی افکت‌ها
- چرخش مستقل متن، شکل و تصویر واردشده در ادیتور عکس (۱۸۰- تا ۱۸۰+ درجه).
- ذخیره چرخش در پروژه و بازیابی آن؛ پروژه‌های قدیمی با چرخش صفر باز می‌شوند.
- ذخیره اتمیک پروژه عکس در پوشه موقت و انتشار فقط پس از تکمیل همه فایل‌ها و manifest.
- در ادیتور ویدئو، افکت‌های زمان‌دار هم‌پوشان اکنون همگی به‌ترتیب شناسه اعمال می‌شوند؛ قبلاً فقط آخرین افکت فعال اعمال می‌شد.
- نسخه 1.0-r10.37 / versionCode 47.


LightningLab update r10.38 (versionCode 48) — GitHub editor benchmark pass

PHOTO EDITOR
- Added duplicate actions for text, shape, and imported-image layers. Duplicated image layers own a separate bitmap copy and are offset slightly for immediate visibility.

VIDEO EDITOR
- Restored the pure Kotlin TimelineMath implementation in active source (zoom interpolation, range clamping, source/output mapping, split validation).
- Added magnetic snapping for timed overlay/effect start and end boundaries, including timeline start/end and neighboring clip edges.
- Added a snap on/off control to the timeline header.

RESEARCH / ROADMAP
- Added docs/EDITOR_GITHUB_AUDIT_FA.md with a comparative audit of CapCraft, LightCraft, CheapShot/ClearCut, ActionCut and Allay Photo Editor; a feature-gap matrix for both editors; detailed UX patterns; license cautions; and a prioritized implementation roadmap.

VALIDATION
- Added unit tests for snap proximity and disabled snapping. Pure TimelineMath source compiled separately with kotlinc. Full Android Gradle build and device/UI tests were not run in this environment.


LightningLab update r10.39 (versionCode 49) — live preview and hands-on editing

VIDEO EDITOR
- Replaced the basic VideoView preview with Media3 ExoPlayer / TextureView so Compose overlays can be composited visibly above the video surface.
- Timed text, sticker and image layers are displayed in the preview according to the playhead; opacity and vertical-position keyframes are evaluated for preview.
- Added playhead polling, timeline seek, previous/next frame controls, playback-speed sync and segment-aware source seeking. Frame step uses source frame-rate metadata when available (30fps fallback).
- Added horizontal timeline zoom (1x–4x), Split-at-playhead for selected segments, and insertion of new overlays at the current playhead with a five-second default duration.

PHOTO EDITOR
- Selected text, shape, and image layers can be moved directly on the canvas by dragging. Gesture updates are committed once at drag end, avoiding one undo entry per pointer event.
- Duplicated shape/image layers become selected immediately.

VALIDATION
- Full Android Gradle build and on-device playback/gesture tests were not run in this environment. The Media3 preview and touch behavior must be checked by GitHub Actions and on a real device.

r10.40 (versionCode 50)
- Unified video undo/redo history across segments, timed overlays, effects, audio assignments and major transform/color settings.
- Retain image-overlay bitmap references in history so deleting an overlay remains undoable.
- Atomic video-project publication using a hidden staging directory and completed manifest; stale hidden staging folders are cleaned during project listing.
- Drag-to-move timed overlay clips on the timeline; one undo checkpoint is committed when the gesture ends.
- Export preflight checks segment/overlay/effect timing, missing local sources, dimensions and low free storage before rendering.
- Top-bar undo/redo controls and settings-gesture history commits.


r10.41 (versionCode 51)
- Fixed missing mutableLongStateOf import.
- Centralized image decoding through explicit ImageDecoder.OnHeaderDecodedListener to avoid Kotlin overload inference failures in CI.
- Made model-import rollback branches explicitly Unit-valued.
- Added video segment reorder controls and precise selected-segment In/Out trim with one history commit after gesture.
- Added ripple delete that closes the removed output-time gap and shifts timed overlays, effects and markers consistently.
- Added clickable timeline markers; marker state is included in undo/redo and persisted by ProjectCodec v4 while v1-v3 remain readable.
- Full Android build must be verified by GitHub Actions; Android SDK is not installed in this environment.


## r10.43 — GitHub Actions Kotlin compile fix
- Fixed selected segment variable scope in the video trim editor: the selected `Seg?` is now declared in the enclosing `else` scope and is shared by the segment action row and precision trim controls.
- This resolves the r181 compiler errors at `Screens.kt:856–860` (`Unresolved reference: sel` and cascading Compose delegate/type inference errors).
- Version code: 52.

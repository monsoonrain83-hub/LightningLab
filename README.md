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

# Lightning Lab — Offline Atmospheric Intelligence

Lightning Lab is an Android application built with Kotlin and Jetpack Compose. It combines lightning-video tools, photo/video editing, an offline atmospheric knowledge atlas, and optional on-device TensorFlow Lite analysis.

## Current source version

- `versionName`: `1.0-r10.41`
- `versionCode`: `51`
- Android `minSdk`: 29
- `compileSdk` / `targetSdk`: 34
- Java: 17

## Main areas

- Lightning detection and video analysis.
- Photo and video editing tools.
- Offline Cloud Atlas backed by the bundled SQLite knowledge database.
- Optional TFLite branches for WMO cloud genera, structural cloud features, binary cloud segmentation, and seven-class atmospheric segmentation.
- Temporal aggregation of model outputs for sampled video frames.

## Important model limitation

No trained `.tflite` weights are included in this source package. Until a model is trained on appropriately licensed, expert-reviewed data and imported into the app, model-based image classification is unavailable and fallback image heuristics must not be treated as validated meteorological observations. The Cloud Atlas is an offline reference encyclopedia, not an image classifier.

## Build

The repository includes `.github/workflows/build.yml`. Upload a complete project archive named `update.zip` to the repository root, or manually run the workflow. It applies the source update, runs `testDebugUnitTest`, builds the debug APK, and uploads the APK as a workflow artifact.

## Documentation

- `docs/PROJECT_STATUS.md` — actual source status and known limitations.
- `docs/WEATHER_AI_ARCHITECTURE.md` — model architecture.
- `docs/CLOUD_AI_REPOSITORY_REVIEW_FA.md` — review of external cloud-AI repositories.
- `docs/PHASE2_23_MODEL_IMPORT_SAFETY_FA.md` — latest code changes.
- `tools/cloud_ai/` — cloud-genus and structural-feature data/training tools.
- `tools/cloud_segmentation/` — segmentation datasets, annotation and training tools.


## Latest update — 1.0-r10.24

Model import recovery now restores the last known-good TFLite file after an interrupted or invalid replacement. See `docs/PHASE2_24_MODEL_RECOVERY_FA.md`. This is a reliability improvement; it does not add trained model weights.


## Latest update — 1.0-r10.27
- Reduced the video-backed home hero from 150dp to 124dp to give the main dashboard more room.
- Preserved both bundled storm clips, lifecycle-aware playback, the existing lightning analysis pipeline, editor tools, and unit tests.
- This source package does not include trained cloud-model weights. Android build/device verification must be performed by the repository workflow.

### r10.28 Photo layer foundation
The photo editor now supports independently editable text overlay layers with visibility, ordering, position, size, opacity, and color controls. Layers are composited in preview and export. Raster-image, mask, and effect layer types plus unified layer history remain future work.


### Latest update — 1.0-r10.29
- Added independent rectangle and ellipse overlay layers to the photo editor.
- Shape layers support visibility, width, height, position, opacity, and color; they render in the live preview and final image export.
- Text overlays remain supported and render above shape overlays.
- Android build/device verification is still pending through GitHub Actions.


### Latest update — 1.0-r10.30
- Added independent raster-image overlay layers selected from the Android photo picker.
- Image layers support visibility, ordering, size, position, opacity, and deletion; they render in preview and export.
- Overlay bitmaps are downsampled for editor memory use and released when removed or when the editor ViewModel is cleared.
- Android build/device verification remains pending through GitHub Actions.


## r10.31 — Editable local mask brush
- Added non-destructive local mask strokes with adjustable brush diameter.
- Mask strokes can be undone individually or cleared, and are included in live preview and exported image composition.
- Masking creates transparency; it is not AI object segmentation or content-aware reconstruction.


## r10.33 — Photo editor workflow

This update unifies adjustment and overlay/mask edits in a shared undo/redo history, adds feathered object-healing edges, and allows in-place text-layer editing with bold/regular typography. Export uses a stable snapshot of the mask settings; image overlays remain safe for in-flight rendering when the source photo changes.


## r10.34 — timed video overlay tracks
- Added independent timeline clips for text and stickers with persistent IDs, content, styling, start/end timestamps and track selection.
- Added timeline track blocks, clip deletion, start/end trimming sliders, and applying text/sticker edits to the selected clip.
- Video export now splits source segments at overlay boundaries and applies each text/sticker only during its active output interval, including reordered/trimmed source segments and speed changes.
- Project save/load now round-trips overlay track clips; older project files remain readable and legacy full-length text/sticker overlays still export when no timed clips exist.
- Added a ProjectCodec round-trip test covering multiple timed overlay clips.
- Android Gradle build/tests were not run in this environment (no Gradle executable or wrapper present).


## r10.35 — Unified photo overlay compositing stack
- Text, shape and imported-image layers now share one global z-order instead of being rendered in fixed type groups.
- Reorder controls can move a layer above or below layers of other types; preview and exported image use the same order.
- New layers are inserted at the top; legacy equal-order layers are normalized when the stack is next extended.
- Global layer ordering is part of the existing unified undo/redo snapshots.
- Version: 1.0-r10.35 / versionCode 45. Android build/tests were not run in this environment.

## r10.36 editor update

The photo editor now saves and restores editable projects (source image, adjustments, HSL, text/shape/image layers and masks). The video editor now decodes PCM audio to draw separate real music/voice waveforms and supports opacity/vertical-position keyframes for timed text and sticker overlays. Video project manifests are version 2 and remain backward-compatible with version 1.


### r10.36 — editable media projects and timeline layers

- Photo projects preserve the source image, all non-destructive adjustments (including HSL), masks, text/shape layers and imported image layers.
- Video audio lanes display PCM-decoded waveforms for music and recorded voice separately.
- Video timeline supports independent image overlays and timed filter/effect clips, plus editable opacity/vertical-anchor keyframes for text, sticker and image overlays.
- Video project format v3 persists image assets, effect clips and keyframes and reads earlier v1/v2 manifests.
- Android Gradle build and device export tests still require GitHub Actions/device validation.


## r10.37 — editor finishing pass

- Photo text, shape, and imported-image layers support independent rotation from -180° to +180°. Rotation is rendered in the final composite and persisted in editable photo projects; older manifests default to 0°.
- Photo project saves now write into a hidden temporary folder and only publish the project after all PNG assets and the manifest have been written successfully. Interrupted saves are not shown as valid projects.
- Video export now stacks all simultaneously active time-ranged filter clips in deterministic order instead of silently applying only the last overlapping filter. The base filter is used only when no timed filter is active.


## Editor benchmark and roadmap

See [the Persian comparative GitHub audit](docs/EDITOR_GITHUB_AUDIT_FA.md) for the photo/video editor feature-gap matrix, UX patterns from open-source Android editors, license notes, and the prioritized completion plan.


## r10.39 editor update
- Media3 ExoPlayer preview with timed overlay preview, playhead seek and frame stepping.
- Horizontal timeline zoom, selected-segment split at playhead, and playhead-based overlay insertion.
- Direct drag positioning for selected photo layers with one undo snapshot per gesture.
- See `docs/EDITOR_GITHUB_AUDIT_FA.md` for the comparative editor audit and the still-unimplemented professional features.

## r10.40 — کنترل حرفه‌ای‌تر ویدئو و ایمنی پروژه
- تاریخچهٔ یکپارچهٔ ویرایش ویدئو برای قطعات، لایه‌های متن/استیکر/تصویر، افکت‌ها، تنظیمات تصویر، سرعت و مسیرهای صدا اضافه شد؛ Undo/Redo در نوار بالای ادیتور در دسترس است.
- حذف لایهٔ تصویری دیگر bitmap آن را بلافاصله نابود نمی‌کند؛ snapshotهای تاریخچه می‌توانند تصویر را با Undo بازیابی کنند.
- ذخیرهٔ پروژهٔ ویدئو اکنون ابتدا در پوشهٔ staging مخفی انجام می‌شود و فقط پس از کپی دارایی‌ها و تکمیل manifest به پوشهٔ نهایی منتقل می‌شود؛ پوشه‌های ناقص قدیمی در فهرست پروژه‌ها پاک‌سازی می‌شوند.
- جابه‌جایی مستقیم لایه‌های زمان‌دار روی تایم‌لاین با drag و ثبت یک مرحلهٔ Undo در پایان gesture اضافه شد؛ برش دقیق همچنان از کنترل‌های شروع/پایان انجام می‌شود.
- پیش‌بررسی خروجی، بازهٔ کلیپ‌ها/لایه‌ها/افکت‌ها، فایل‌های منبع، ابعاد و فضای آزاد را قبل از شروع رندر کنترل می‌کند و خطاهای قابل‌اقدام را نشان می‌دهد.
- تغییرات تنظیمات صدا، سرعت، فیلتر، crop، چرخش، flip، روشنایی و کنتراست به تاریخچه متصل شدند.


## r10.41 — اصلاح بیلد و کنترل دقیق قطعات ویدئو
- رفع خطای import مربوط به `mutableLongStateOf`.
- یکسان‌سازی Decode تصاویر با overload صریح `ImageDecoder.OnHeaderDecodedListener` برای حذف ابهام Kotlin در CI.
- صریح‌سازی شاخه‌های rollback فایل مدل برای جلوگیری از خطای `if` به‌عنوان expression.
- امکان جابه‌جایی ترتیب قطعات، برش دقیق In/Out، Ripple Delete برای بستن فاصلهٔ حذف‌شده و نشانگرهای قابل کلیک روی تایم‌لاین.
- نشانگرها در تاریخچهٔ Undo/Redo و پروژهٔ ذخیره‌شده باقی می‌مانند؛ ProjectCodec به نسخهٔ ۴ ارتقا یافت و نسخه‌های ۱ تا ۳ همچنان خوانده می‌شوند.
- نسخه: `1.0-r10.41` / `versionCode 51`.
- بیلد کامل باید با GitHub Actions تأیید شود؛ در این محیط Android SDK نصب نیست.


## r10.43 — GitHub Actions Kotlin compile fix
- Fixed selected segment variable scope in the video trim editor: the selected `Seg?` is now declared in the enclosing `else` scope and is shared by the segment action row and precision trim controls.
- This resolves the r181 compiler errors at `Screens.kt:856–860` (`Unresolved reference: sel` and cascading Compose delegate/type inference errors).
- Version code: 52.

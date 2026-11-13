# LightningLab — آزمایشگاه ارزیابی علمی مستقل (r10.75)

این ابزار فقط روی **دادهٔ واقعی برچسب‌خورده توسط انسان** نتایج علمی محاسبه می‌کند. هیچ وزن مدل، تصویر، ویدئو یا عدد دقت ساختگی در مخزن وجود ندارد. اجرای موفق تست‌های مصنوعی Python تنها درستی **منطق محاسبات** را می‌سنجد، نه عملکرد مدل در طبیعت.

## ۱) طبقه‌بندی زمانی رعدوبرق یا جنس ابر

```bash
python3 tools/scientific_eval/evaluate.py classification \
  --task brixton --input /real-data/model_predictions.csv \
  --out /real-results/lightning_classification.json

python3 tools/scientific_eval/evaluate.py classification \
  --task cloud-genus --input /real-data/cloud_predictions.csv \
  --out /real-results/cloud_genus.json

python3 tools/scientific_eval/evaluate.py classification \
  --task wmo-cl --input /real-data/wmo_cl_predictions.csv \
  --out /real-results/wmo_cl.json
```

پنج نام معتبر task: `brixton`، `cloud-genus`، `wmo-cl`، `wmo-cm`، `wmo-ch`.

هر سطر CSV باید شامل `sample_id,source_group,split,truth,domain,sha256` و به‌ازای تمام کلاس‌ها ستون `p_<class>` باشد. برای نمونهٔ Brixton به ترتیب:
`p_towerupward,p_towerdownward,p_close,p_far,p_unclear,p_unclearbehind,p_CC`.

- `source_group`: یک شناسهٔ مستقل برای رخداد/دوربین/توالی همبسته؛ فریم‌های یک رخداد و کپی‌های تصویری باید یک گروه باشند.
- `split`: یکی از `train`، `validation`، `test`، `external`.
- `domain`: محل/دوربین/اقلیم شناخته‌شده برای تحلیل تعمیم بیرونی (برای مثال `Fars_station01`).
- `sha256`: هش **فایل واقعی منبع**، نه رشتهٔ ساختگی یا هش filename.
- `truth`: برچسب مرجع کارشناس و نه خروجی خود مدل.
- مقادیر همهٔ `p_...` باید متناهی، بین صفر و یک و مجموعشان نزدیک یک باشد.
- برای کد `/` در سرهای WMO، به‌معنای «قابل تعیین نیست» توسط ناظر است و از ارزیابی کلاس‌های قابل تعیین کنار گذاشته می‌شود؛ شمار آن گزارش می‌گردد.

گزارش شامل ماتریس سردرگمی، پشتیبانی هر کلاس، precision/recall/F1، macro-F1 با **تعداد کلاس‌های دارای truth**، Brier چندکلاسه، ECE، بررسی selective prediction و بازهٔ اطمینان Bootstrap با نمونه‌گیری بر اساس **گروه مستقل** است. در نبود داده، کلاس، یا گروه کافی مقدار `null` ارائه می‌شود، نه صفرِ کاذب. آزمون خارجی جدا از test ثبت می‌شود.

**محدودیت مهم:** ECE و Brier نشانهٔ کالیبراسیون‌اند ولی صرف پایین‌بودن آن‌ها برای اثبات اعتبار علمی مدل کافی نیست. آستانهٔ abstention را بر اساس validation انتخاب و سپس روی test/external منجمد کنید؛ انتخاب بهترین آستانه پس از مشاهدهٔ test نشت تصمیم‌گیری است.

## ۲) ارزیابی استخراج رخدادهای واقعی صاعقه از ویدئو

```bash
python3 tools/scientific_eval/evaluate.py events \
 --clips /real-data/clips.csv --truth /real-data/expert_intervals.csv \
 --predictions /real-data/detector_intervals.csv \
 --score-threshold 0 --min-iou 0.3 \
 --out /real-results/lightning_events.json
```

سه فایل CSV:

**clips.csv**: `video_id,source_group,split,domain,duration_ms,sha256`  
**expert_intervals.csv**: `video_id,event_id,start_ms,end_ms`  
**detector_intervals.csv**: `video_id,event_id,start_ms,end_ms,score`

همهٔ بازه‌ها `[start_ms,end_ms)` از ابتدا ویدئوی خام و برحسب میلی‌ثانیه‌اند. مدت‌زمان ویدئو حتی اگر **هیچ فلاش واقعی یا تشخیص‌شده‌ای** نداشته باشد باید ثبت شود؛ کلیپ‌های منفی برای نرخ خطای کاذب ضروری‌اند.

هر تشخیص تنها با یک رخداد مرجع قابل انطباق است (one-to-one با بیشینهٔ IoU و تقدم امتیاز آشکارساز). موارد تکراری به FP تبدیل می‌شوند. گزارش: TP/FP/FN، precision، recall، F1، FP در ساعت ویدئو، خطای میانهٔ زمان شروع، آزمون دامنهٔ بیرونی و بازهٔ اطمینان گروهی.

**توجه به امتیاز آشکارساز:** مقدار `algorithm_score` برنامه **نمرهٔ heuristic بدون کالیبراسیون است و ممکن است از ۱ بیشتر باشد**. این ابزار آن را به احتمال تبدیل نمی‌کند. `--score-threshold` به‌صورت پیش‌فرض صفر است؛ آستانه را تنها با مجموعهٔ validation و قبل از آزمون نهایی تعیین کنید.

برای تبدیل خروجی JSON واقعیِ خود LightningLab (schema v3) به جدول پیش‌بینی رخدادها:

```bash
python3 tools/scientific_eval/from_app_export.py \
  --evidence /real-data/export_lightninglab.json \
  --video-id clip01 --out /real-data/detector_intervals.csv
```

این تبدیل **فقط رخدادهای کشف‌شده را منتقل می‌کند**. برچسب‌های دستی ثبت‌شده روی رخدادهای کشف‌شده، جایگزین فهرست مستقل تمام فلاش‌های واقعی نیستند؛ رخدادهای جامانده را یک یا چند ناظر باید در `expert_intervals.csv` وارد کنند. فلاش تک‌فریمی با `start_ms=end_ms` در خروجی برنامه برای امکان محاسبهٔ overlap به یک بازهٔ قراردادی یک‌میلی‌ثانیه‌ای تبدیل می‌شود؛ این *مدت فیزیکی ثبت‌شده* نیست.

**محدودیت علمی:** این ابزار صرفاً **وجود و زمان‌بندی رخداد تصویری** را اعتبارسنجی می‌کند؛ قطبیت، جریان، CG/IC و نوع الکتریکی را بدون حسگرهای مستقل تأیید نمی‌کند. تقسیم‌بندی یک پدیده به چند پالس باید از قبل در دستورالعمل annotation تعریف شود.

## ۳) راهبرد داده و ضدنشت

1. مجموعهٔ train/validation/test/external را **قبل از آموزش** ثبت و ذخیره کنید؛ گروه‌های همبسته، کلیپ‌های تکراری و هش فایل مشترک نباید به splitهای مختلف بروند.
2. برای هر رده حداقل تعداد نمونه و **تعداد گروه مستقل** را گزارش کنید. ۱۰۰۰ فریمِ یک ویدئو، ۱۰۰۰ مشاهدهٔ مستقل نیست.
3. برچسب‌های نامطمئن را با دو یا سه ناظر ثبت و اختلاف نظر را نگه دارید؛ برای WMO، `/` کد «غیرقابل تعیین» است.
4. برای آزمون بیرونی مجموعه‌هایی از مکان، دوربین، فصل و شرایط تصویربرداری متفاوت انتخاب کنید؛ Brixton تک‌مکان است، Montenegro نیز دامنهٔ محدود دارد.
5. ویدئوهای منفی (نور چراغ خودرو، جوشکاری، scene cut، نور شدید و حرکت دوربین) را اضافه کنید تا نرخ FP/h قابل اندازه‌گیری شود.
6. گزارش JSON تولیدشده را همراه هش مدل، هش داده، قرارداد ورودی/خروجی و اسکریپت اجرای مستقل نگهداری کنید.
7. برای انتشار علمی، قضاوت بازبین و خطای مدل را مستقل نگه دارید؛ برچسب `CONFIRMED` در داخل اپ، به‌تنهایی truth جامع در سطح تمام ویدئو نیست و نمی‌توان از آن sensitivity محاسبه کرد.

## ۴) آموزش هم‌زمان با روال runtime و ممیزی انتشار

در زمان آموزش Brixton، نسخهٔ قدیمی هشت فریم را از **کل کلیپ** انتخاب می‌کرد ولی Android هشت فریم را از حدود ۵۰۰ میلی‌ثانیه قبل از شروع رخداد تا ۵۰۰ میلی‌ثانیه بعد از پایان آن برمی‌دارد. این تفاوت می‌تواند باعث افت دقت شود و باید قابل‌ردگیری باشد.

برای آموزش هم‌راستا با runtime، برچسب‌زن انسانی برای تمام کلیپ‌ها یک CSV با ستون‌های `relative_path,sha256,start_ms,end_ms,reviewer` آماده کند؛ سپس:

```bash
python3 tools/lightning_ai/train_brixton.py \
  --manifest /real-data/brixton_manifest.csv \
  --root /real-data/brixton_clips \
  --event-times /real-data/expert_event_windows.csv \
  --out /real-results/brixton_event_aligned
```

SHA هر ردیف annotation باید دقیقاً با منبع یکسان باشد و همهٔ کلیپ‌ها annotation داشته باشند؛ در غیر این صورت آموزش متوقف می‌شود. بدون `--event-times` مدل فقط پژوهشیِ full-clip است و برای انتشار علمی با runtime فعلی تأیید نمی‌شود. حتی با پنجرهٔ زمانی هم باید تفاوت decoder و انتخاب فریم OpenCV/Android جداگانه آزموده شود.

در خروجی‌های آموزش:
- `tools/lightning_ai/train_brixton.py` فایل `test_predictions.csv` و متادیتای هش‌دار می‌نویسد.
- `tools/train_cloud_model.py` فایل `test_predictions.csv` و `model_metadata.json` می‌نویسد؛ مجوز مجموعهٔ starter تا راستی‌آزمایی با مقدار `UNVERIFIED` ثبت می‌شود.
- `tools/cloud_ai/train_montenegro_synoptic.py` فایل‌های `test_predictions_cl.csv`، `test_predictions_cm.csv`، `test_predictions_ch.csv` و `model_metadata.json` می‌نویسد.

یک **گیت محافظه‌کار** برای بررسی شواهد کافیِ نامزدی مدل افزوده شده:

```bash
python3 tools/scientific_eval/release_audit.py \
  --model /real-results/model.tflite \
  --metadata /real-results/model_metadata.json \
  --evaluation /real-results/external_evaluation.json \
  --task brixton --out /real-results/release_audit.json
```

گیت، هش واقعی مدل، قرارداد کلاس‌ها، مجوز داده، تطبیق پیش‌پردازش، وجود آزمون خارجی مستقل و پشتیبانی کلاس‌ها را بررسی می‌کند. **گذرکردن از گیت صرفاً «نامزد بررسی متخصص» است، نه گواهی دقت عملیاتی.** اگر وزن یا ارزیابی واقعی موجود نباشد، گیت باید ناموفق باشد. به‌طور پیش‌فرض برای هر کلاس حداقل ۵ نمونهٔ آزمون خارجی، در مجموع دست‌کم ۱۰۰ مشاهده از ۳ دامنهٔ خارجی، Macro-F1 حداقل ۰٫۸۰ و ECE حداکثر ۰٫۱۵ می‌خواهد؛ این‌ها **آستانه‌های مهندسی قابل تنظیم** هستند، نه ادعای مقدار واقعی مدل.

## ۵) آزمون کد و محدودیت انتشار

```bash
python3 -m unittest discover -s tools/scientific_eval/tests -v
python3 -m compileall -q tools/scientific_eval
```

اگر فایل‌های مرجع واقعی وجود نداشته باشند، ارزیابی واقعی اجرا نشده و **هیچ عدد دقت قابل گزارش نیست**. آموزش مدل‌ها به TensorFlow، دادهٔ دارای مجوز، GPU/CPU و ارزیابی بیرونی نیاز دارد و در APK/CI فعلی به‌طور خودکار انجام نمی‌شود.

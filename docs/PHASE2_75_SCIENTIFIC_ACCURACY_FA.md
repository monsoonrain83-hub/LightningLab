# LightningLab r10.75 — Scientific Accuracy & Model Release Gates

## هدف
جداسازی «کد مدل کار می‌کند» از «مدل روی دادهٔ مستقل دقت علمی قابل قبول دارد».
این فاز ابزار ارزیابی واقعی و قابل بازتولید ایجاد می‌کند، نه وزن ساختگی یا درصد دقت ادعایی.

## ورودی و خروجی
ابزار: tools/scientific_accuracy/evaluate_predictions.py

هر ردیف JSONL:
- split: train / validation / test
- group_id: شناسه رخداد/کلیپ یکسان در نماهای مشابه
- label: برچسب مرجع (برای صاعقه یکی از هفت برچسب Brixton)
- probabilities: بردار عددی به ترتیب قرارداد مدل، جمع حدود ۱
- sha256: هش فایل (در صورت وجود)
- sample_id, site_id, camera_id: اطلاعات منشأ نمونه
- head: CL/CM/CH برای ارزیابی سر مدل ابرها

## اجرای ارزیابی صاعقه

    python3 tools/lightning_ai/prepare_brixton.py \
      --root /path/to/brixton --out data/brixton_manifest.csv

    python3 tools/lightning_ai/train_brixton.py \
      --manifest data/brixton_manifest.csv --root /path/to/brixton \
      --out out/brixton

    python3 tools/scientific_accuracy/evaluate_predictions.py \
      --task lightning \
      --predictions out/brixton/test_predictions.jsonl \
      --manifest data/brixton_manifest.csv \
      --out out/brixton/scientific_test_metrics.json \
      --bootstrap 500 --seed 2026

فایل test_predictions.jsonl از حلقه ارزیابی test در pipeline ساخته می‌شود و به رخداد/دوربین/هش و کلاس واقعی مرتبط است. تولید آن نیازمند اجرای آموزش واقعی و داشتن فایل‌های دادهٔ دارای مجوز است.

## اجرای ارزیابی سر مستقل ابرها

    python3 tools/scientific_accuracy/evaluate_predictions.py \
      --task cloud-head --head CL \
      --predictions cloud_predictions.jsonl \
      --out cloud_cl_metrics.json

برای دادهٔ چندناظر Montenegro با soft-label، تبدیل به برچسب modal فقط ارزیابی توافق با مُد مشاهده‌گران است، نه حقیقت قطعی. باید علاوه بر این گزارش، معیارهای proper scoring مثل soft cross-entropy و soft Brier و توافق متخصصان گزارش شوند. ابزار حاضر تنها برای هر نمونه با یک label مشخص طراحی شده است.

## کنترل‌ها
1. رد احتمال منفی، NaN/Inf، بردار با طول اشتباه، شناسه نمونه تکراری.
2. رد نشت رخداد یا SHA-256 یکسان بین splitها؛ با --manifest کل مجموعه (نه فقط test) بررسی می‌شود.
3. کنترل عضویت ردیف پیش‌بینی در مانیفست در حالت --manifest.
4. حالت --independent-sites الزام site_id را بررسی می‌کند و مانع تکرار مکان در چند split می‌شود. Brixton تک‌مکان است و نمی‌تواند گواه اعتبارسنجی خارجی باشد.
5. ماتریس آشفتگی، precision/recall/F1 هر کلاس، macro-F1 روی کلاس‌های حاضر، فهرست کلاس‌های غایب.
6. امتیاز Brier چندکلاسه نرمال‌شده بر تعداد کلاس، ECE ده‌بازه‌ای، دقت و coverage پس از امتناع.
7. فاصلهٔ بازنمونه‌گیری گروهی ۹۵٪؛ این فاصله اثبات تعمیم بین اقلیم یا مکان نیست.
8. SHA-256 ورودی پیش‌بینی و مانیفست برای reproducibility.
9. model_is_validated=false: ابزار هیچ آستانه دلخواه را به‌عنوان گواه آماده‌بودن مدل اعلام نمی‌کند.

## تفسیر علمی
- این آمار در سطح کلیپ‌های برچسب‌خورده است؛ Recall رخدادهای کامل ویدئو یا هشدار کاذب در ساعت را اندازه نمی‌گیرد.
- CG/IC، قطبیت، جریان و انرژی از RGB تک‌دوربین به‌تنهایی قابل اثبات قطعی نیست.
- ECE و softmax تضمین کالیبراسیون روی داده‌های بیرونی نیستند.
- مجوزها، bias زمانی و اثر دوربین باید پیش از انتشار وزن گزارش شوند.
- وزن واقعی مدل یا مدل جدیدی در این فاز ساخته نشده است.

## تأیید CI
Workflow پروژه تست‌های Python این ابزار را برای هر PR مربوط به tools اجرا می‌کند. تست‌ها از دادهٔ مصنوعی برای صحت محاسبات و جلوگیری از نشت استفاده می‌کنند؛ بنابراین دقت علمی تشخیص را اندازه نمی‌گیرند.

## کارهای باقی‌مانده
- مدل واقعی و دادهٔ چندمکانِ معتبر برای Lightning و Cloud، همراه متخصص و آزمون مستقل.
- Benchmark روی دستگاه واقعی: latency، RAM، thermal و باتری.
- ارزیابی سازگاری UI/UX، ابزارهای پیشرفته Video و Photo Studio.
- هیچ مورد فوق در این نسخه به‌عنوان تکمیل‌شده معرفی نشده؛ ویژگی‌های قبلی حذف نشده‌اند.

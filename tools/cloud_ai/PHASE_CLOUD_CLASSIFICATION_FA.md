# تکمیل مسیر تشخیص جنس ابر در Lightning Lab

## دامنه نسخه اول
- ورودی: عکس RGB دوربین زمینی/گوشی؛ نه تصویر ماهواره‌ای و نه مادون‌قرمز.
- خروجی: ده جنس اصلی WMO با کدهای `Ac, As, Cb, Cc, Ci, Cs, Cu, Ns, Sc, St`.
- «آسمان صاف»، مه، غبار، دود و برف در نسخه اول به‌عنوان مسئله جداگانه تشخیص وضعیت آسمان/قطعه‌بندی در نظر گرفته می‌شوند؛ نباید به‌زور به یکی از ده جنس ابر نگاشت شوند.
- ویژگی‌هایی مانند incus، mamma، virga، arcus و tuba مدل/سرِ خروجی جداگانه می‌خواهند.

## داده آغازین و محدودیت‌ها
اسکریپت `prepare_ccaim.py` مجموعه `serbekun/CCAiM-CloudsDataset` را دریافت می‌کند. کارت مجموعه مجوز MIT را اعلام می‌کند و ۹۱۶ تصویر در ده کلاس دارد؛ توزیع آن نامتوازن است (برای نمونه Cu=398 و Ns=17). این داده برای خط مبنا و آزمون فنی مناسب است، نه برای ادعای عملکرد عملیاتی. داده‌های دیگر مانند CCSN (۲۵۴۳ تصویر، شامل Ct) و TJNU/GCD (۱۹۰۰۰ تصویر اما هفت گروه/رده ترکیبی) برچسب‌گذاری هم‌ارز ده جنس ندارند و نباید بدون بازبینی متخصص به ده کلاس تبدیل شوند. مجموعه WEBCAM بیش از ۱۵۵۰۰ تصویر و ۹ دسته دارد؛ دسته‌های هواشناسی هوانوردی آن معادل مستقیم ده جنس WMO نیستند.

## اجرای آموزش
روی PC یا Google Colab با Python 3.10/3.11:

```bash
python -m venv .venv
# Windows: .venv\\Scripts\\activate
# Linux/macOS: source .venv/bin/activate
pip install -r tools/cloud_ai/requirements.txt
python tools/cloud_ai/prepare_ccaim.py --out data/clouds/ccaim
python tools/cloud_ai/audit_cloud_dataset.py --data data/clouds/ccaim --out out/cloud_dataset_audit.json
python tools/train_cloud_model.py --data data/clouds/ccaim --out out/clouds
```

خروجی مورد انتظار آموزش: `cloud_model.tflite`, `metrics.txt`, `labels.json`, `split_counts.json` و `best.keras`. فایل TFLite تنها پس از اجرای موفق آموزش ساخته می‌شود. در محیط فعلی TensorFlow نصب نیست؛ بنابراین مدل وزن‌دار/آموزش‌دیده عمداً در این بسته جعل یا درج نشده است.

## نکته حیاتی پیش‌پردازش
در نسخه پیشین اسکریپت، `tf.image.resize_with_pad` به‌کار رفته بود؛ این تابع حاشیه صفر می‌سازد، درحالی‌که اپ اندروید حاشیه خاکستری RGB=128 می‌سازد. این اختلاف اصلاح شد: اکنون تصویر با حفظ نسبت ابعاد تغییر اندازه می‌یابد و padding مرکزی با مقدار 128 ساخته می‌شود تا ورودی آموزش و Android هم‌خوان باشد.

## معیار پذیرش مدل
قبل از استفاده واقعی، فایل `metrics.txt` را بررسی کنید: accuracy کلی به‌تنهایی کافی نیست؛ precision/recall/F1 هر کلاس و confusion matrix باید بررسی شوند. نمونه‌های آزمون نباید در آموزش یا انتخاب مدل استفاده شوند. به علت کوچک بودن و عدم توازن CCAiM، مدل حاصل از آن فقط baseline آزمایشی است. برای نسخه قابل اتکا باید داده بیشتر با برچسب متخصص، تفکیک بر اساس دوربین/روز/مکان و آزمون خارجی مستقل افزوده شود.

## منابع داده و پژوهش
- CCAiM dataset: https://huggingface.co/datasets/serbekun/CCAiM-CloudsDataset (MIT طبق کارت مجموعه؛ ۹۱۶ عکس/۱۰ کلاس)
- CCAiM model card و گزارش عملکرد مدل‌های قبلی: https://huggingface.co/serbekun/CCAiM (گزارش‌شده: Macro-F1 پایین؛ نشان‌دهنده دشواری داده کوچک)
- WEBCAM dataset: https://github.com/MarcusCoteFIT/webcam-ground-based-cloud-image-dataset (۱۵۵۰۰+ عکس، ۹ دسته)
- CCSN database: https://github.com/upuil/CCSN-Database (۱۱ دسته با contrails؛ نیازمند نگاشت بازبینی‌شده)
- TJNU GCD: https://github.com/shuangliutjnu/TJNU-Ground-based-Cloud-Dataset (۱۹۰۰۰ عکس، ۷ گروه ترکیبی؛ نه ده جنس مجزا)
- پژوهش طبقه‌بندی ابر از مشاهدات زمینی: https://doi.org/10.1002/qj.4865


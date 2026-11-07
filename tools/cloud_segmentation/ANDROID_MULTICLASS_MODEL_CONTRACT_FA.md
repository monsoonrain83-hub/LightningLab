# قرارداد مدل چندکلاسه برای Android

فایل `atmosphere_multiclass.tflite` باید دقیقاً ورودی و خروجی زیر را داشته باشد:

- Input: `[1, 256, 256, 3]`, `FLOAT32`, RGB, range 0..255.
- Output: `[1, 256, 256, 7]`, `FLOAT32`, per-pixel softmax scores.
- Channel order: clear_sky, cloud, fog, dust_haze, smoke, snow_ice, other.
- هر هفت امتیاز یک پیکسل باید finite و در بازهٔ ۰ تا ۱ باشند و مجموع آن‌ها در بازهٔ ۰٫۹ تا ۱٫۱ قرار گیرد.

فایل مدل را در برنامه از بخش هواشناسی و دکمهٔ واردکردن مدل چندکلاسه انتخاب کنید. این مدل مستقل از مدل دودویی `cloud_segmentation.tflite` است.

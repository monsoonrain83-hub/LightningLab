# r10.57 — مدل سه‌سر CL/CM/CH

- آموزش MobileNetV3Small با soft-targetهای چندناظر برای کدهای سینوپتیکی WMO در سه تراز پایین/میانی/بالا.
- خروجی TFLite با قرارداد [1,3,11] و ورودی RGB 224×224 با padding خاکستری 128.
- runtime مستقل اندروید، اعتبارسنجی tensorها، import ایمن با backup و نمایش top-3 برای هر تراز.
- metrics: macro-F1 در برابر رأی غالب، soft cross-entropy، Brier، گزارش هر کلاس و confusion matrix.
- مدل هنوز وزن‌دار همراه APK نیست؛ باید در محیط آموزش اجرا و با آزمون خارجی اعتبارسنجی شود.

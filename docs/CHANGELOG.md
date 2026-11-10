## r10.44 — بازطراحی تجربهٔ کاربری و چیدمان ابزارها

- صفحهٔ خانه از چیدمان بلند و تکراری به داشبورد اولویت‌محور تبدیل شد: یک اقدام اصلی برای تشخیص، چهار فضای کاری در کارت‌های ثانویه، وضعیت پردازش و تنظیم حساسیت در بخش مستقل.
- هدر خانه کوتاه‌تر شد تا فضای بیشتری برای محتوای اصلی باقی بماند؛ وضعیت پردازش روی دستگاه و انتخاب زبان در دسترس ماند.
- کارت‌های ابزار، نوار ناوبری و دکمه‌های ابزار ادیتورها فشرده‌تر و یکدست‌تر شدند؛ حالت انتخاب‌شده با رنگ تأکیدی و سطح مشخص دیده می‌شود، بدون آنکه ابزارها حذف شوند.
- ردیف دسته‌بندی ابزارهای عکس به سگمنت‌های جمع‌وجورتر تبدیل شد؛ نوار ابزار ویدئو نیز فضای افقی کمتری برای هر ابزار می‌گیرد تا گزینه‌های بیشتری هم‌زمان دیده شوند و باقی ابزارها همچنان با پیمایش افقی در دسترس‌اند.
- متن‌های جدید صفحهٔ خانه برای فارسی، انگلیسی و عربی به منابع ترجمه منتقل شدند.
- نسخهٔ برنامه به `1.0-r10.44` و `versionCode 54` ارتقا یافت.

### کارهای مهم باقی‌مانده

این انتشار بازطراحی تجربهٔ خانه و نوار ابزار است، نه تکمیل همهٔ قابلیت‌های حرفه‌ای. اولویت‌های بعدی همچنان عبارت‌اند از: autosave و crash recovery؛ تاریخچهٔ یکپارچهٔ تمام عملیات؛ drag/trim واقعی کلیپ‌ها، multi-select و گروه‌بندی؛ thumbnail cache و waveform چندسطحی؛ track controls؛ transition و speed ramp؛ keyframe کامل؛ subtitle/SRT؛ presetهای خروجی و صف export؛ و در ادیتور عکس، handles چندلمسی، ماسک شعاعی/خطی، RGB curves، histogram، before/after slider و خروجی قابل تنظیم. هر مورد باید به موتور پردازش، ذخیرهٔ پروژه و خروجی واقعی متصل شود، نه اینکه صرفاً کنترل UI اضافه شود.

## r10.43
- Fixed Kotlin/JVM setter signature clashes by renaming explicit music and voice URI update methods.


## r10.32 — Feathered and inverse photo masks
- Added non-destructive feathered brush masks with adjustable softness.
- Added inverse mask mode for clearing the area outside painted strokes.
- Wired inverse-mask state into live preview and full-resolution export.
- Existing photo parameter Undo/Redo remains intact; mask strokes retain their dedicated undo/clear controls.

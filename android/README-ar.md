# تطبيق 10thgradeps لأندرويد (Java Native)

تطبيق مستقل (ليس WebView) باسم الحزمة `net.tenthgradeps.app`، يعمل فوق موقعك الحالي
`https://10thgradeps.netlify.app/` بنفس حسابات الموقع وجلساته واشتراكاته. لا يوجد Firebase ولا قاعدة بيانات
جديدة ولا عدّاد زيارات.

## ماذا يستخدم التطبيق من الموقع (Netlify Functions الموجودة فعلًا)
| الوظيفة | الدالة |
|---|---|
| تسجيل الدخول / إنشاء حساب / الخروج / الحساب / كلمة المرور | `student-auth` (`login`, `register`, `logout`, `me`, `forgot`, `update-profile`, `change-password`, `mark-read`, `track-open`) |
| التحقق من Premium على الخادم | `premium-login` |
| ملفات Premium المرفوعة من الإدارة | `premium-list` |
| فتح/تنزيل ملف Premium محمي | `premium-file` (POST `{file:id}`) |
| بحث داخل نصوص Premium | `premium-search` |
| طلب ملف / حل سؤال | `premium-request` |
| طلب اشتراك وحالته | `premium-applications` |
| الملفات المجانية المرفوعة، المخفي، المقفل، الصيانة | `site-files`, `status` |

الجلسة هي نفس كوكي الموقع `10gp_student_session`؛ يلتقطه التطبيق من `Set-Cookie` ويحفظه داخل
`EncryptedSharedPreferences` (AES‑256 ومفتاحه في Android Keystore). لا توجد أي مفاتيح سرية داخل التطبيق،
والخادم هو الذي يقرر Premium في كل مرة.

## فهرس الملفات (الخيار أ)
الملفات الثابتة غير متاحة عبر API، لذلك يولّد السكربت `android/tools/gen-app-catalog.mjs` الملف
`app/src/main/assets/catalog.json` من `index.html` و`premium.html` أثناء كل بناء (قراءة فقط، لا يعدّل الموقع).
معرّفات الملفات المحمية تبقى كما هي، ولا يُضمَّن أي محتوى PDF داخل الـ APK (عناوين ومعرّفات فقط).
> ملف ثابت جديد تضيفه إلى `index.html` أو `premium.html` يظهر في التطبيق بعد **بناء جديد**. الملفات التي تُرفع من لوحة
> الإدارة تظهر مباشرة دون إعادة بناء.

## البناء عبر GitHub Actions
1. فكّ هذا الـ ZIP داخل جذر مستودع الموقع (يضيف المجلد `android/` والملف `.github/workflows/build-android-apk.yml` فقط ولا يعدّل شيئًا موجودًا).
2. ارفع التغييرات (`git add android .github && git commit && git push`).
3. من تبويب **Actions** شغّل **Build Android APK** (أو يعمل تلقائيًا عند الدفع). بعد النجاح نزّل الـ artifact باسم `10thgradeps-apk`.
4. إن فشل البناء نزّل artifact باسم `build-log` وأرسله لي لأصلح الخطأ.

### توقيع ثابت (موصى به)
بدون توقيع ثابت يُوقَّع الـ APK بمفتاح مؤقت يتغيّر في كل بناء، فلا يمكن تحديث نسخة مثبّتة بنسخة أحدث (احذف القديمة أولًا).
لإنشاء مفتاح ثابت مرة واحدة:
```
keytool -genkeypair -v -keystore release.jks -alias tgps -keyalg RSA -keysize 2048 -validity 10000
```
ثم أضف في المستودع (Settings → Secrets and variables → Actions) هذه الأسرار:
`ANDROID_KEYSTORE_BASE64` (ناتج `base64 -w0 release.jks`)، `ANDROID_KEYSTORE_PASSWORD`، `ANDROID_KEY_ALIAS` (= `tgps`)، `ANDROID_KEY_PASSWORD`.
**لا ترفع ملف `release.jks` إلى المستودع واحتفظ بنسخة منه.**

## عبر Termux
```
pkg update && pkg install git nodejs openjdk-17 gh unzip
termux-setup-storage
cd ~/storage/shared/<مجلد-المستودع>        # أو أي مكان فيه نسخة المستودع (git clone)
unzip -o ~/storage/downloads/10thgradeps-android.zip
git add android .github
git commit -m "Add native Android app"
git push
gh auth login                               # مرة واحدة
gh workflow run build-android-apk.yml
gh run watch
gh run download -n 10thgradeps-apk -D ~/storage/downloads/
```
البناء المحلي داخل Termux غير موصى به (أدوات Android SDK لا تعمل جيدًا على ARM)، والبناء الأساسي يتم على GitHub.
للتجربة المحلية فقط: `node android/tools/gen-app-catalog.mjs` يولّد الفهرس.

## المظهر والخط
- أسود وأبيض فقط (بدون أي لون)، بأسلوب iOS: بطاقات مستديرة، عناوين كبيرة، انتقالات انزلاق، تأثير ضغط وارتداد خفيف.
- الصفحة الرئيسية مثل صفحة الموقع: الشعار والعنوان وزر الإعدادات، البحث، أزرار المواد بأغلفة الكتب، تصنيف حسب النوع، وشبكة ملفات من عمودين بصور الملفات.
- الشريط السفلي عائم مستدير (الرئيسية، Premium، المفضلة، التنزيلات، حسابي)، والتبويب المختار يتمدد ويظهر اسمه.
- تبويب Premium يعرض ملفات الاشتراك بنفس الشبكة (يتحقق الخادم من اشتراكك أولًا).
- من **حسابي ← الإعدادات ← المظهر**: أسود (الافتراضي) / أبيض / حسب الجهاز.
- الخط: خط SF الخاص بآبل لا يمكن تضمينه قانونيًا في تطبيق خارجي، لذلك يُنزَّل أثناء البناء الخط المفتوح IBM Plex Sans Arabic (رخصة OFL). إن فشل تنزيله يستخدم التطبيق خط الجهاز.

## السلوك المهم
- **Premium**: يتحقق التطبيق عند التشغيل (مرة كل 10 دقائق كحد أقصى) عبر `premium-login`. اللون ذهبي فقط عند تأكيد الخادم.
- **ملفات Premium**: تُجلب فقط عبر `premium-file`، وتُعرض بعلامة مائية باسم الحساب مع منع لقطات الشاشة، وتُحذف نسخة العرض المؤقتة عند الإغلاق.
- **التنزيل دون إنترنت**: الملفات المجانية تُحفظ عادية. ملفات Premium تُحفظ **مشفّرة** داخل مساحة التطبيق الخاصة، لا تُشارك ولا تُصدَّر، وتُحذف عند تسجيل الخروج،
  وتُفتح دون إنترنت ما دام آخر تحقق ناجح من الخادم خلال 7 أيام ولم ينتهِ الاشتراك. يمكن تعطيل تنزيل Premium بتغيير `PREMIUM_OFFLINE_ENABLED` إلى `false` في `AppConfig.java` (موقعك نفسه لا يوفّر تنزيل Premium، فهذا قرار لك).
- **الدفع**: لا يتم داخل التطبيق؛ يرسل التطبيق الطلب إلى `premium-applications` وتفعّله الإدارة كما في الموقع.
- **الزائر** يتصفح الملفات المجانية دون حساب؛ المفضلة تُحفظ على الجهاز لكل حساب.
- لا يوجد تكبير بالقرص (Pinch)؛ التكبير بأزرار + و − (حتى 3 أضعاف).

## حالة الاختبار (بصدق)
- جرى تشغيل `gen-app-catalog.mjs` فعليًا على ملفات موقعك: 157 ملفًا مجانيًا و48 ملف Premium و9 مواد، ومعرّفات Premium تطابق `lib/catalog.mjs` تمامًا.
- جرى فحص صياغة جميع ملفات الجافا بمترجم JDK وفحص ملفات XML، **لكن لم يُبنَ المشروع ولم يُشغَّل على جهاز أو محاكي** (لا Android SDK ولا إنترنت في بيئة العمل). أول بناء حقيقي سيكون على GitHub Actions، وقد يظهر فيه خطأ أو أكثر نصلحه معًا.

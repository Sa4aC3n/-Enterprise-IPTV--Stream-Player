# دليل النشر الرسمي على Google Play Console 🚀

تطبيق **Enterprise IPTV** مبني ليعمل بنمط **No-Auth / Open Access** (مفتوح بالكامل وبدون أي تسجيل دخول للمستخدم).

---

## 1. توليد حزمة النشر الرسمية (AAB) عبر Android Studio

1. افتح المشروع في **Android Studio**:
   - مسار المشروع: `C:\Users\mohamed.keshka\.gemini\antigravity\scratch\iptv_player_app`
2. من القائمة العلوية اختر:
   **Build** > **Generate Signed Bundle / APK...**
3. اختر **Android App Bundle** ثم انقر **Next**.
4. في نافذة **Key store path**:
   - إذا كان لديك مفتاح مسبق اختره، أو اضغط **Create new...**
   - احفظ المفتاح باسم `upload-keystore.jks` في مكان آمن.
   - حدد كلمة مرور للمفتاح والـ Alias (مثلاً: `upload`).
5. اختر **release** كنوع البناء (Build Variant).
6. انقر **Create**.

ستجد ملف الحزمة الناتج جاهزاً للرفع داخل المسار:
`app/build/outputs/bundle/release/app-release.aab`

---

## 2. إعدادات Google Play Console الهامة (تجنب الرفض)

عند رفع التطبيق في **Google Play Console**، يجب تعبئة الأقسام التالية كما يلي:

### أ. وصول التطبيق (App Access)
- **السؤال:** هل يتطلب التطبيق بيانات تسجيل دخول أو شروط وصول خاصة؟
- **الإجابة:** اختر الخيار الأول:
  `All functionality is available without special access` (جميع الميزات متاحة بدون أي قيود أو تسجيل دخول).

### ب. تصنيف المحتوى (Content Rating)
- اختر فئة التطبيق: **Entertainment / Media Player** (مشغل وسائط وترفيه).

### ج. الجمهور المستهدف والمحتوى (Target Audience)
- حدد الفئة العمرية (مثلاً: 13 سنة فأكثر).

### د. حقوق الملكية الفكرية والسياسات (Policy Declarations)
- التطبيق يُعرّف في الوصف كـ **"مشغل وسائط وقوائم تشغيل M3U" (IPTV & M3U8 Player)**.
- أضف إخلاء المسؤولية المعتمد (Disclaimer) في وصف المتجر:
  > *This application does not provide or host any copyrighted content. It is a media player allowing users to stream publicly available streams or their own custom M3U playlists.*

---

## 3. تحديث القنوات في المستقبل بدون تحديث التطبيق
- ملف القنوات موجود في GitHub كما قمنا بتهيئته عبر `GitHubSyncManager`.
- لتحديث أي قناة لاحقاً، قم بتعديل رابط الـ stream في ملف الـ JSON على GitHub ورفع رقم `"version": 2`. التطبيق في هواتف المستخدمين سيحدث القنوات تلقائياً في الخلفية.

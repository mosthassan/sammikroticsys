# دليل إعداد وتشغيل مشروع SamMikrotik في Android Studio

يحتوي هذا المستند على كافة الخطوات والتعليمات الخاصة بتهيئة وتشغيل وبناء مشروع تطبيق **SamMikrotik** المحاسبي.

---

## 1. متطلبات بيئة التطوير
* **نظام التشغيل:** Linux / macOS / Windows
* **إصدار Android Studio:** Android Studio Ladybug (2024.2.1) أو أحدث
* **إصدار JDK:** JDK 17 أو JDK 21 (موصى به: Eclipse Temurin JDK 21)
* **Android SDK:** API Level 34 (Android 14) أو API Level 35

---

## 2. ملحقات Gradle المضمنة (Gradle Wrapper)
تم توليد وتضمين ملفات مغلّف Gradle الرسمية مسبقاً داخل جذر المشروع:
* `gradlew`: سكربت التشغيل لأنظمة Linux و macOS.
* `gradlew.bat`: سكربت التشغيل لأنظمة Windows.
* `gradle/wrapper/gradle-wrapper.jar`: مكتبة تشغيل المغلّف.
* `gradle/wrapper/gradle-wrapper.properties`: إعدادات وتحديد إصدار Gradle المتوافق.

لتشغيل المهام عبر الطرفية (Terminal):
```bash
# على أنظمة Linux / macOS
chmod +x gradlew
./gradlew assembleDebug

# على أنظمة Windows
gradlew.bat assembleDebug
```

---

## 3. بناء واختبار بيئة التطوير (Debug & Testing)
**لا تتطلب مهام التطوير والاختبارات المحلية أي مفاتيح أو كلمات مرور إطلاقاً.**
يستخدم إصدار `debug` توقيع أندرويد القياسي التلقائي (`~/.android/debug.keystore`) دون الحاجة إلى ملفات إضافية:
* **بناء نسخة التطوير (Debug APK):**
  ```bash
  ./gradlew assembleDebug
  ```
* **تشغيل اختبارات الوحدة واختبارات Robolectric:**
  ```bash
  ./gradlew :app:testDebugUnitTest
  ```

---

## 4. إعداد مفاتيح وتوقيع نسخة الإنتاج (Release Signing)
لأسباب أمنية، لا يتم حفظ أي كلمات مرور أو مسارات لمفاتيح الإنتاج داخل مستودع Git. يتم قراءة إعدادات التوقيع من مصدرين:

### الخيار أ: ملف `keystore.properties` (موصى به محلياً)
أنشئ ملفاً باسم `keystore.properties` في المجلد الرئيسي للمشروع (خارج git، ومضاف إلى `.gitignore`):
```properties
storeFile=/path/to/your/my-upload-key.jks
storePassword=YourKeystorePassword
keyAlias=upload
keyPassword=YourKeyPassword
```

### الخيار ب: متغيرات البيئة (CI/CD Pipelines)
يمكن تمرير القيم عبر متغيرات البيئة:
* `KEYSTORE_PATH`
* `STORE_PASSWORD`
* `KEY_ALIAS` (افتراضياً: `upload`)
* `KEY_PASSWORD`

### سلوك البناء عند غياب إعدادات التوقيع:
* في حال غياب ملف `keystore.properties` أو متغيرات البيئة، تقوم مهمة `bundleRelease` بطباعة رسالة تحذيرية واضحة جداً في الطرفية:
  ```text
  ⚠️  [BUNDLE RELEASE SIGNING WARNING]
  Release signing configuration is incomplete or missing.
  To sign release bundles, define 'keystore.properties' in rootDir or set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD.
  bundleRelease will proceed generating an unsigned release bundle.
  ```
  وتستمر عملية البناء لإنتاج حزمة `AAB` غير موقعة دون التسبب في إيقاف خطوط الإنتاج.

---

## 5. ضغط وتصغير الكود (Minification & R8)
* تم تفعيل التصغير وضغط الموارد في إصدار الإنتاج:
  * `isMinifyEnabled = true`
  * `isShrinkResources = true`
* يحتوي ملف `app/proguard-rules.pro` على قواعد الاستبقاء (18 keep rules) الضرورية لحماية نماذج البيانات وقواعد Room وMoshi وFirebase من الحذف أو التشويه.
* للتحقق من نجاح بناء نسخة الإنتاج:
  ```bash
  ./gradlew assembleRelease
  ```

---

## 6. سياسة وتقارير الأعطال (Firebase Crashlytics)
* **موقوف افتراضياً (Default Disabled):** تم تعطيل جمع بيانات Crashlytics افتراضياً على مستوى الـ Manifest عبر:
  ```xml
  <meta-data
      android:name="firebase_crashlytics_collection_enabled"
      android:value="false" />
  ```
* **بموافقة المستخدم فقط:** يتم تفعيل الخدمة برمجياً فقط بعد موافقة صريحة من المستخدم من خلال إعدادات التطبيق عبر `CrashReportingManager.setConsent(context, true)`.
* **الخصوصية الصارمة (Zero PII):** لا يتم إرسال أي أرقام هواتف، أسماء شركاء، حسابات مالية، أو عناوين IP في أي تقرير أعطال.

# إعداد وتشغيل مشروع "سام ميكروتك" في Android Studio

دليل استيراد وبناء وتشغيل تطبيق **سام ميكروتك (SamMikrotik)** - نظام مالي ومحاسبي وإداري لشبكات الإنترنت وموزعي الميكروتك وفق معايير المحاسبة الدولية (IFRS).

---

## 1. المتطلبات الأساسية للنظام وبيئة التطوير
استناداً إلى وثائق أندرويد الرسمية وتوافق مكونات Gradle وKotlin:
- **نظام التشغيل:** Windows 10/11 (64-bit) أو macOS أو Linux.
- **Android Studio:** إصدار **Android Studio Ladybug (2024.2.1)** أو **Android Studio Meerkat / Otter** كحد أدنى، متوافق مع Android Gradle Plugin (AGP) 9.1+.
- **حزمة تطوير جافا (JDK):** **JDK 21** أو **JDK 17** (مدمج مع Android Studio افتراضياً).
- **إصدار Gradle:** 9.3.1 (محدد في `gradle/wrapper/gradle-wrapper.properties`).
- **إصدار Kotlin:** 2.2.10.
- **الحد الأدنى لـ SDK:** Android 7.0 (API 24).
- **الهدف وCompile SDK:** Android 16 (API 36).

---

## 2. استيراد المشروع من المستودع (Clone & Import)
1. افتح الطرفية (Terminal أو Git Bash) وقم باستنساخ المشروع:
   ```bash
   git clone https://github.com/mosthassan/sammikroticsys.git
   cd sammikroticsys
   ```
2. افتح **Android Studio**.
3. اختر **Open** من شاشة البدء وحدد مجلد المشروع الرئيسي.
4. انتظر حتى يكتمل مزامنة Gradle (Gradle Sync) وتحميل التبعيات.

---

## 3. إعداد وتوليد الـ Gradle Wrapper
إذا فتحت المشروع لأول مرة وطلب Android Studio ملف الـ Wrapper:
- سيقوم Android Studio تلقائياً باستخدام إعدادات `gradle/wrapper/gradle-wrapper.properties` لتحميل وتشغيل Gradle 9.3.1.
- أو يمكنك تشغيل الأمر التالي من الطرفية لتوليد السكربتات التنفيذية:
  ```bash
  gradle wrapper --gradle-version 9.3.1
  ```

---

## 4. الملفات السرية والبيئية الاختيارية
التطبيق مصمم للعمل محلياً بالكامل 100% بدون أي مفاتيح خارجية (Offline-First). الميزات السحابية تتطلب الآتي:

### أ) إعداد Firebase (اختياري للنسخ السحابي ومصادقة Google):
1. قم بإنشاء مشروع في [Firebase Console](https://console.firebase.google.com/).
2. أضف تطبيق Android باسم الحزمة: `com.samtecai.sammikrotic`.
3. قم بتنزيل ملف `google-services.json` وضعه داخل المسار: `app/google-services.json`.
4. أضف بصمات SHA-1 و SHA-256 الخاصة بجهاز التطوير (Debug Keystore) وبصمة الـ Release في لوحة تحكم Firebase لتفعيل تسجيل الدخول عبر Google (Credential Manager).
5. في حال عدم إضافة هذا الملف، سيعمل التطبيق كاملاً محلياً مع ظهور تنبيه أن النسخ السحابي معطل.

### ب) ملف مفاتيح التوقيع للإنتاج (`keystore.properties`):
لإصدار نسخة Release موقعة لمتجر Google Play:
1. انسخ ملف النموذج `keystore.properties.example` إلى `keystore.properties`:
   ```bash
   cp keystore.properties.example keystore.properties
   ```
2. قم بتعديل القيم بداخله لتشير إلى ملف الـ keystore الخاص بك:
   ```properties
   storeFile=../my-release-key.jks
   storePassword=YourPassword
   keyAlias=upload
   keyPassword=YourKeyPassword
   ```

---

## 5. أوامر التشغيل والبناء والاختبار
من الطرفية داخل المشروع:

- **تشغيل حزمة الاختبارات الشاملة (Unit & Robolectric Tests):**
  ```bash
  ./gradlew testDebugUnitTest
  # في بيئة ويندوز:
  gradlew.bat testDebugUnitTest
  ```
- **بناء حزمة تصحيح الأخطاء التجريبية (Debug APK):**
  ```bash
  ./gradlew assembleDebug
  ```
- **بناء حزمة الإنتاج الموقعة لـ Google Play (Release AAB):**
  ```bash
  ./gradlew bundleRelease
  ```

---

## 6. استكشاف الأخطاء الشائعة وحلولها
- **أخطاء ترميز الأرقام في ويندوز (Arabic-Indic numerals في KSP):**
  - تم حلها مسبقاً وتضمين إعدادات إجبار اللغة والترميز الإنجليزي في `gradle.properties` لضمان توليد شفرة Room بالأرقام اللاتينية حصراً (`Locale.US`).
- **Missing google-services.json warning:**
  - تحذير طبيعي وغير معطّل للبناء؛ التطبيق يتخطاه تلقائياً ليعمل في وضع Offline المحلي.

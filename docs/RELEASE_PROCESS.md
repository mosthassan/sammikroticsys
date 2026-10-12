# عملية الإصدار والترقية (Release Process)

دليل إصدار وتحديث نسخ تطبيق **سام ميكروتك (SamMikrotik)**.

---

## 1. إدارة الإصدارات والترقيم (Version Control)
يتم التحكم في رقم الإصدار من مكان واحد حصري في ملف `gradle.properties`:
```properties
VERSION_CODE=1
VERSION_NAME=1.0.0
```
- **VERSION_CODE:** عدد صحيح موجب يتم زيادته بمقدار 1 مع كل حزمة تُرفع إلى Google Play Console.
- **VERSION_NAME:** يتبع نظام ترقيم الإصدارات الدلالي (SemVer: `MAJOR.MINOR.PATCH`).

---

## 2. خطوات إصدار نسخة إنتاج جديدة (Production Release Steps)

1. **التحقق من اجتياز كافة الاختبارات:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
2. **التأكد من سلامة ملف مفاتيح التوقيع (`keystore.properties`):**
   تأكد من وجود الملف وتعيين المفتاح المعتمد.
3. **بناء حزمة Android App Bundle الموقعة والمضغوطة عبر R8:**
   ```bash
   ./gradlew bundleRelease
   ```
4. الحزمة الناتجة ستكون في المسار:
   `app/build/outputs/bundle/release/app-release.aab`
5. رفع الحزمة إلى Google Play Console (مسار الاختبار المغلق ثم الإنتاج).

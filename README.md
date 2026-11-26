# المؤذن | Al-Muezzin

تطبيق أندرويد (Kotlin / Jetpack Compose) لمواقيت الصلاة والأذان واتجاه القبلة والمسبحة والأذكار والقرآن ورمضان،
بالإضافة إلى **لوحة تحكم ويب** ترسل إشعارات نصية أو صوتية إلى كل الأجهزة المسجّلة عبر Firebase Cloud Messaging.

## بنية المشروع

| المسار | الوصف |
|---|---|
| `app/` | تطبيق أندرويد (Compose + Material3 + Retrofit/Moshi + OneSignal + Firebase Messaging) |
| `admin-dashboard/` | لوحة التحكم (HTML/CSS/JS خالص، تُستضاف على Firebase Hosting) |
| `functions/` | Cloud Functions: تسجيل الأجهزة + إرسال الإشعارات + سجل الإرسال |
| `موقع ادارة التطبيق/` | نسخة أقدم من اللوحة تُعمل محلياً على المنفذ 5050 (غير مستخدمة في النشر) |
| `AlMuezzin.apk` | حزمة إصدار جاهزة (موقّعة) |

## لوحة التحكم

`admin-dashboard/` لوحة عربية (RTL) تعمل بـ Firebase JS SDK مباشرة بلا خطوة بناء:

1. **تسجيل الدخول** — Firebase Authentication (بريد + كلمة مرور)؛ الحسابات غير المصرّح بها لا ترى شيئاً.
2. **إرسال إشعار** — نصي (`text`) أو صوتي (`audio`)، مع رفع ملف صوت إلى Firebase Storage أو لصق رابط مباشر.
3. **الأجهزة** — كل الأجهزة التي سجّلت رمز FCM (تظهر لحظة تشغيل التطبيق).
4. **السجل** — تاريخ كل إشعار مُرسل مع عدد المستقبِلين.

### إعداد اللوحة

```bash
cd admin-dashboard
cp firebase-config.example.js firebase-config.js   # ضع بيانات مشروع Firebase (Web app)
```

### تشغيلها محلياً

```bash
npm install -g firebase-tools
firebase login
firebase use <project-id>
firebase emulators:start          # Functions + Firestore + Hosting + Storage
```

### نشرها

```bash
firebase deploy --only hosting,functions,firestore:rules,storage
```

## ربط التطبيق بـ Firebase

قيم Firebase تُحقن في `BuildConfig` عبر Secrets Gradle Plugin، فلا يوجد `google-services.json` داخل المستودع.

انسخ `.env.example` إلى `.env` (المُستبعَد من Git) واملأ:

```ini
FIREBASE_PROJECT_ID=your-project
FIREBASE_APP_ID=1:1234567890:android:abcdef
FIREBASE_API_KEY=AIza...
FIREBASE_SENDER_ID=1234567890
PUSH_BACKEND_URL=https://<region>-<project>.cloudfunctions.net
```

ثم في Google Cloud Console → المشروع → مفاتيح API ← أضف بصمة **SHA-256** لمفتاح الرفع
(المفتاح في `~/.deepai-code/keys/al-muezzin-upload.jks`، والشفرة تُقرأ بالأمر التالي):

```bash
keytool -list -v -keystore ~/.deepai-code/keys/al-muezzin-upload.jks -alias upload
```

إذا لم تُضبط هذه القيم يبقى التطبيق يعمل كما هو (OneSignal) ويتجاهل FCM بأمان.

## بناء حزمة الإصدار

```bash
export JAVA_HOME="/c/Program Files/Microsoft/jdk-21.0.13.102-hotspot"
export ANDROID_HOME='C:\Users\LENOVO\AppData\Local\Android\Sdk'

export KEYSTORE_PATH="$HOME/.deepai-code/keys/al-muezzin-upload.jks"
export STORE_PASSWORD="<من ملف al-muezzin-upload.credentials.txt>"
export KEY_PASSWORD="<نفس القيمة>"

gradle :app:assembleRelease
```

الناتج: `app/build/outputs/apk/release/app-release.apk` (موقّع بمفتاح `upload`).

> المستودع لا يحتوي على `gradlew`؛ استخدم Gradle 9.3.1 أو شغّل `gradle wrapper` لتوليد Wrapper.

## الرفع على Google Play / Firebase App Distribution

```bash
firebase appdistribution:distribute app/build/outputs/apk/release/app-release.apk \
  --app "1:1234567890:android:abcdef" --groups "testers"
```

## ملاحظات

- التطبيق يستعمل OneSignal للتنبيهات المحلية-it，加上 FCM لتنبيهات لوحة التحكم؛ الاثنان يعملان معاً.
- تنبيهات لوحة التحكم الصوتية تُشغَّل عبر `MorningEveningAthkarService.startRemote` الذي يتحقق من الرابط (نوع المحتوى + بصمة الملف) ويشغّل صوتياً احتياطياً محلياً عند الفشل.
- إشعارات لوحة التحكم تُسجَّل في Firestore (`notifications`) والأجهزة في (`devices`).

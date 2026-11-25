# المؤذن | Al-Muezzin

## الموقع المباشر (لوحة إدارة التطبيق)
**الرابط:** https://gamecandor-cpu.github.io/al-muezzin/

> لوحة تحكم ويب مدمجة في `index.html` (Firebase Firestore + Google Auth)، تُدار منها التذكيرات والإشعارات الصوتية للتطبيق.
>
> **التحقق الفعلي من الروابط الصوتية:** زر «🔍 بحث عن الصوت» لا يكتفي بامتداد الملف، بل يفحص نوع المحتوى (`Content-Type`) وبصمة الملف الأولى (MP3/M4A/AAC/OGG/WAV/FLAC/HLS) عبر سلسلة بروكسي، ويميّز الروابط المؤكدة ✓ من المحتملة ⚠️، ويمنع دفع رابط غير صوتي. كذلك يفحص التطبيق الرابط قبل التشغيل (`RemoteAudioVerifier`) ويشغّل صوتاً احتياطياً محلياً عند تعذّر الرابط.

## محتويات المستودع
- `index.html` — نسخة جذرية من لوحة الإدارة (تُقدَّم مباشرة على GitHub Pages).
- `موقع ادارة التطبيق/index.html` — الملف الأصلي للوحة، مع `start-server.mjs` للتشغيل المحلي على المنفذ 5050.
- `app/` — تطبيق Android (Kotlin / Jetpack Compose): مواقيت الصلاة، الأذان، القبلة، المسبحة، الأذكار، رمضان.
- `AlMuezzin.apk` — حزمة تثبيت التطبيق الجاهزة.
- `public/assets/` — صور الموقع.
- `firebase.json` و`firestore.rules` و`firestore.test.js` — إعداد Firebase (المشروع `gen-lang-client-0269726559`).

## ملاحظة تسجيل الدخول في Firebase
عند فتح الموقع من نطاق GitHub Pages، يجب إضافة النطاق `gamecandor-cpu.github.io` في:
Firebase Console → Authentication → Settings → Authorized domains، حتى يعمل تسجيل الدخول Google.

---

## ملاحظة المطوّر (قالب AI Studio الأصلي)

<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/3a090149-baae-41dd-a16b-0031efcd6946

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user push profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      athanEnabled: true,
      athkarAutoEnabled: true,
      subscribedTopics: ["muezzin_announcements"],
      qadaRemaining: 0,
      qadaCompleted: 0,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Authenticated owner can create and partially update UserPushProfile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fcmToken: "sample_fcm_token_abc",
      cityName: "الرياض",
      athanEnabled: true,
      athkarAutoEnabled: true,
      subscribedTopics: ["muezzin_announcements", "religious_occasions"],
      qadaRemaining: 2,
      qadaCompleted: 5,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "updated_fcm_token_xyz",
      qadaRemaining: 1,
      qadaCompleted: 6,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt cannot be overwritten
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Ghost fields / @DocumentId id injection rejected
  await assertFails(
    aliceDb.collection("users").doc("another_doc").set({
      id: "another_doc",
      userId: ALICE_UID,
      athanEnabled: true,
      athkarAutoEnabled: true,
      subscribedTopics: [],
      qadaRemaining: 0,
      qadaCompleted: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  // List query on users collection denied
  await assertFails(aliceDb.collection("users").get());
});

test("Authenticated user cannot read, write, or spoof another user's profile", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      athanEnabled: true,
      athkarAutoEnabled: true,
      subscribedTopics: ["muezzin_announcements"],
      qadaRemaining: 0,
      qadaCompleted: 0,
      createdAt: now,
      updatedAt: now,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).update({
      athanEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      athanEnabled: true,
      athkarAutoEnabled: true,
      subscribedTopics: [],
      qadaRemaining: 0,
      qadaCompleted: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Broadcast reminders allow public read-only, admin create/update/delete, and reject tampering", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Unauthenticated cannot create or delete
  await assertFails(
    unauthDb.collection("broadcast_reminders").doc("rem_1").set({
      authorUid: ALICE_UID,
      title: "تذكير يومي",
      body: "سبحان الله وبحمده",
      reminderKind: "audio",
      persistenceMode: "pinned",
      deliveryMode: "daily",
      scheduledTimeHHmm: "06:30",
      audioUrl: "https://drive.google.com/file/d/12345/view",
      normalizedAudioUrl: "https://drive.google.com/uc?export=download&id=12345",
      audioSourceType: "Google Drive",
      isActive: true,
      triggerNonce: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Authenticated admin Alice can create a valid audio daily pinned reminder
  await assertSucceeds(
    aliceDb.collection("broadcast_reminders").doc("rem_1").set({
      authorUid: ALICE_UID,
      title: "أذكار الصباح من الموقع",
      body: "استمع لأذكار الصباح يومياً",
      reminderKind: "audio",
      persistenceMode: "pinned",
      deliveryMode: "daily",
      scheduledTimeHHmm: "06:30",
      audioUrl: "https://drive.google.com/file/d/12345/view",
      normalizedAudioUrl: "https://drive.google.com/uc?export=download&id=12345",
      audioSourceType: "Google Drive",
      isActive: true,
      triggerNonce: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // App clients (unauthenticated) can read active broadcast reminders
  await assertSucceeds(unauthDb.collection("broadcast_reminders").doc("rem_1").get());
  await assertSucceeds(unauthDb.collection("broadcast_reminders").get());

  // Alice can re-push immediately by incrementing triggerNonce
  await assertSucceeds(
    aliceDb.collection("broadcast_reminders").doc("rem_1").update({
      triggerNonce: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot hijack or delete Alice's reminder
  await assertFails(
    bobDb.collection("broadcast_reminders").doc("rem_1").update({
      title: "تعديل غير مصرح",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(bobDb.collection("broadcast_reminders").doc("rem_1").delete());

  // Non-https audio URL is rejected
  await assertFails(
    aliceDb.collection("broadcast_reminders").doc("rem_bad").set({
      authorUid: ALICE_UID,
      title: "رابط غير آمن",
      body: "",
      reminderKind: "audio",
      persistenceMode: "temporary",
      deliveryMode: "now",
      scheduledTimeHHmm: "12:00",
      audioUrl: "http://insecure.example.com/sound.mp3",
      isActive: true,
      triggerNonce: 1,
      expiresAtMillis: Date.now() + 3600000,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice can delete her own reminder
  await assertSucceeds(aliceDb.collection("broadcast_reminders").doc("rem_1").delete());
});

test("Uploaded device audio assets, chunks, and firestore_chunks broadcast reminders enforce strict validation and ownership", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const assetId = "ast_1732560000_ab12c";

  // Unauthenticated cannot upload assets or chunks
  await assertFails(
    unauthDb.collection("broadcast_audio_assets").doc(assetId).set({
      authorUid: ALICE_UID,
      fileName: "athan_clip.mp3",
      mimeType: "audio/mpeg",
      fileExt: "mp3",
      totalBytes: 4096,
      chunkCount: 1,
      durationSec: 12,
      isReady: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).set({
      authorUid: ALICE_UID,
      assetId,
      chunkIndex: 0,
      dataBase64: "SUQzAw==",
      createdAt: now,
    })
  );

  // Reject non-audio MIME type or oversized (> 10MB) upload
  await assertFails(
    aliceDb.collection("broadcast_audio_assets").doc("ast_bad_mime").set({
      authorUid: ALICE_UID,
      fileName: "script.html",
      mimeType: "text/html",
      fileExt: "html",
      totalBytes: 1024,
      chunkCount: 1,
      durationSec: 0,
      isReady: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    aliceDb.collection("broadcast_audio_assets").doc("ast_too_big").set({
      authorUid: ALICE_UID,
      fileName: "huge.mp3",
      mimeType: "audio/mpeg",
      fileExt: "mp3",
      totalBytes: 15 * 1024 * 1024,
      chunkCount: 25,
      durationSec: 600,
      isReady: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice creates audio asset + chunk, then marks asset ready
  await assertSucceeds(
    aliceDb.collection("broadcast_audio_assets").doc(assetId).set({
      authorUid: ALICE_UID,
      fileName: "athan_clip.mp3",
      mimeType: "audio/mpeg",
      fileExt: "mp3",
      totalBytes: 4096,
      chunkCount: 1,
      durationSec: 15,
      isReady: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).set({
      authorUid: ALICE_UID,
      assetId,
      chunkIndex: 0,
      dataBase64: "SUQzAwAAAAA=",
      createdAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("broadcast_audio_assets").doc(assetId).update({
      isReady: true,
      durationSec: 16,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Chunks are immutable (updates rejected) and Bob cannot tamper/delete Alice's asset or chunks
  await assertFails(
    aliceDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).update({
      dataBase64: "AAAA",
    })
  );
  await assertFails(bobDb.collection("broadcast_audio_assets").doc(assetId).delete());
  await assertFails(bobDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).delete());

  // App clients (unauthenticated) can read audio asset & chunk to play immediately
  await assertSucceeds(unauthDb.collection("broadcast_audio_assets").doc(assetId).get());
  await assertSucceeds(unauthDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).get());

  // Alice creates an immediate broadcast reminder referencing the uploaded audio asset
  await assertSucceeds(
    aliceDb.collection("broadcast_reminders").doc("rem_uploaded_1").set({
      authorUid: ALICE_UID,
      title: "تكبيرات العيد",
      body: "تشغيل فوري عند جميع المستخدمين",
      reminderKind: "audio",
      persistenceMode: "pinned",
      deliveryMode: "now",
      scheduledTimeHHmm: "08:00",
      audioSourceType: "مقطع مرفوع (MP3)",
      audioStorageMode: "firestore_chunks",
      audioAssetId: assetId,
      audioChunkCount: 1,
      audioFileExt: "mp3",
      audioFileName: "athan_clip.mp3",
      audioMimeType: "audio/mpeg",
      isActive: true,
      triggerNonce: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Cleanup works for Alice
  await assertSucceeds(aliceDb.collection("broadcast_reminders").doc("rem_uploaded_1").delete());
  await assertSucceeds(aliceDb.collection("broadcast_audio_chunks").doc(`${assetId}_0`).delete());
  await assertSucceeds(aliceDb.collection("broadcast_audio_assets").doc(assetId).delete());
});

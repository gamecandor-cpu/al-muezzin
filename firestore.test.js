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


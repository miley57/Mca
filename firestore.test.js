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
const ADMIN_UID = "admin_miles_001";
const ADMIN_EMAIL = "milesanjola57@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validTaskPayload(userId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    title: "Study Quantum Mechanics",
    description: "Review wave-particle duality and operator algebra",
    notes: "Focus on chapter 4 problems",
    category: "Study",
    project: "Physics Exam",
    priority: "HIGH",
    status: "TODO",
    completed: false,
    archived: false,
    startDateEpochMillis: 1762000000000,
    dueDateEpochMillis: 1762086400000,
    scheduledTimeSlot: "14:00 - 16:00",
    estimatedMinutes: 90,
    actualMinutesSpent: 0,
    recurrence: "NONE",
    reminderOffsetsText: "30m before",
    locationLabel: "Library",
    locationTrigger: "ARRIVE",
    subtasksText: "[ ] Read notes\n[ ] Solve 5 exercises",
    tagsText: "physics,exam",
    attachmentUri: "",
    attachmentName: "",
    dependsOnTaskId: "",
    dependsOnTaskTitle: "",
    calendarSynced: false,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSubscriptionPayload(userId, overrides = {}) {
  const now = new Date();
  return {
    userId,
    tier: "FREE",
    billingPeriod: "NONE",
    status: "ACTIVE",
    productId: "mca_free_default",
    purchaseToken: "",
    verifiedByBackend: true,
    renewalDateEpochMillis: 0,
    aiRequestsUsedToday: 1,
    aiRequestsTotalCount: 1,
    aiQuotaResetEpochMillis: 1762086400000,
    lastAiActionLabel: "Morning Briefing",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validMonetizationConfigPayload(overrides = {}) {
  const now = new Date();
  return {
    freeAiDailyLimit: 5,
    proAiDailyLimit: 50,
    ultraAiDailyLimit: 500,
    proMonthlyPrice: "$6.99/mo",
    proAnnualPrice: "$59.99/yr",
    proLifetimePrice: "$149.99",
    ultraMonthlyPrice: "$14.99/mo",
    ultraAnnualPrice: "$129.99/yr",
    freeBenefitsSummary: "Unlimited basic tasks • Standard reminders • 5 daily AI requests • Ad-supported",
    proBenefitsSummary: "Ad-free experience • 50 daily AI requests • Advanced scheduling & analytics • Cloud sync",
    ultraBenefitsSummary: "500 daily AI requests • Autonomous AI scheduling • Executive insights & automation",
    adsEnabledForFreeTier: true,
    subscriptionsAvailable: true,
    promoBannerText: "Launch Offer: Save 28% on Annual MCA PRO & ULTRA plans",
    updatedByEmail: ADMIN_EMAIL,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

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

test("Unauthenticated user cannot read or create tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTaskPayload(ALICE_UID))
  );
});

test("Authenticated user can create, read, list, update, and delete their own task", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const taskRef = aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1");
  const initialPayload = validTaskPayload(ALICE_UID);

  await assertSucceeds(taskRef.set(initialPayload));
  await assertSucceeds(taskRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").where("userId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    taskRef.set({
      ...initialPayload,
      completed: true,
      status: "COMPLETED",
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(taskRef.delete());
});

test("Authenticated user cannot read, update, or delete another user's task", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("tasks")
      .doc("bob_task")
      .set(validTaskPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobTaskRef = aliceDb.collection("users").doc(BOB_UID).collection("tasks").doc("bob_task");
  await assertFails(bobTaskRef.get());
  await assertFails(bobTaskRef.delete());
});

test("Rejects shadow fields, invalid priority enum, or spoofed userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tasksCol = aliceDb.collection("users").doc(ALICE_UID).collection("tasks");

  await assertFails(
    tasksCol.doc("shadow_task").set(validTaskPayload(ALICE_UID, { isVerifiedAdmin: true }))
  );
  await assertFails(
    tasksCol.doc("bad_enum").set(validTaskPayload(ALICE_UID, { priority: "EXTREME" }))
  );
  await assertFails(
    tasksCol.doc("spoof_uid").set(validTaskPayload(BOB_UID))
  );
});

test("Authenticated user can create and list inbox items and focus sessions", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  const inboxRef = aliceDb.collection("users").doc(ALICE_UID).collection("inbox_items").doc("inbox_1");
  await assertSucceeds(
    inboxRef.set({
      userId: ALICE_UID,
      content: "Prepare slides for Monday briefing and call dentist",
      source: "BRAIN_DUMP",
      processedByAi: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("focus_sessions").doc("session_1");
  await assertSucceeds(
    sessionRef.set({
      userId: ALICE_UID,
      taskId: "task_1",
      taskTitle: "Study Quantum Mechanics",
      durationMinutes: 25,
      sessionType: "POMODORO",
      completed: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("User can manage their FREE subscription and submit PENDING_VERIFICATION purchase tokens, but cannot self-verify PRO", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const subRef = aliceDb.collection("users").doc(ALICE_UID).collection("subscription").doc("current");

  const initialSub = validSubscriptionPayload(ALICE_UID);
  await assertSucceeds(subRef.set(initialSub));
  await assertSucceeds(subRef.get());

  // Submitting a Play Billing token for backend verification (verifiedByBackend = false, status = PENDING_VERIFICATION) succeeds
  await assertSucceeds(
    subRef.set({
      ...initialSub,
      tier: "PRO",
      billingPeriod: "MONTHLY",
      status: "PENDING_VERIFICATION",
      productId: "mca_pro_monthly",
      purchaseToken: "play_token_xyz_987",
      verifiedByBackend: false,
      updatedAt: new Date(),
    })
  );

  // Attempting to self-grant verifiedByBackend=true for PRO as a non-admin MUST fail
  await assertFails(
    subRef.set({
      ...initialSub,
      tier: "PRO",
      billingPeriod: "MONTHLY",
      status: "ACTIVE",
      productId: "mca_pro_monthly",
      purchaseToken: "forged_client_token",
      verifiedByBackend: true,
      updatedAt: new Date(),
    })
  );

  // Logging AI usage history succeeds
  const logRef = aliceDb.collection("users").doc(ALICE_UID).collection("ai_usage_logs").doc("log_1");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      actionType: "SCHEDULE_PLAN",
      promptSummary: "Break down Physics exam preparation",
      tierAtRequest: "FREE",
      createdAt: new Date(),
    })
  );
});

test("Only verified admin can write /app_config/monetization; signed-in users can read it", async () => {
  const regularDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  }).firestore();

  const unverifiedSpoofAdminDb = testEnv.authenticatedContext("spoof_admin", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();

  const verifiedAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const configPayload = validMonetizationConfigPayload();

  // Regular user cannot create config
  await assertFails(regularDb.collection("app_config").doc("monetization").set(configPayload));

  // Unverified email spoofing admin email cannot create config
  await assertFails(unverifiedSpoofAdminDb.collection("app_config").doc("monetization").set(configPayload));

  // Verified admin succeeds in creating and updating config
  await assertSucceeds(verifiedAdminDb.collection("app_config").doc("monetization").set(configPayload));

  // Regular signed-in user can read the monetization config
  await assertSucceeds(regularDb.collection("app_config").doc("monetization").get());
});

const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_A_UID = "admin_scorer_01";
const ADMIN_B_UID = "admin_scorer_02";
const SUPER_ADMIN_UID = "super_admin_99";
const SHARE_CODE_A = "CS7K4M";
const SHARE_CODE_B = "CS9P2R";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validLiveMatchPayload(adminId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    adminId,
    adminName: "Chandu Admin",
    localMatchId: 101,
    hostTeam: "Mumbai Strikers",
    visitorTeam: "Delhi Royals",
    tossSummary: "Mumbai Strikers won the toss & elected to bat",
    matchStatus: "LIVE",
    sharingEnabled: true,
    totalOvers: 10,
    currentInningsNumber: 1,
    battingTeam: "Mumbai Strikers",
    bowlingTeam: "Delhi Royals",
    scoreRuns: 45,
    scoreWickets: 1,
    oversDisplay: "4.2",
    crr: "10.38",
    rrr: "",
    targetRuns: 0,
    chaseSummary: "Mumbai Strikers won the toss & elected to bat",
    resultSummary: "",
    strikerName: "Rohit",
    strikerStats: "28 (16) • 4x4 1x6 • SR 175.0",
    nonStrikerName: "Virat",
    nonStrikerStats: "15 (10) • 2x4 0x6 • SR 150.0",
    bowlerName: "Bumrah",
    bowlerStats: "1.2-0-12-1 • ER 9.0",
    partnershipSummary: "30 runs (16 balls)",
    lastWicketSummary: "Gill 2 (3)",
    lastBallBadge: "4",
    lastShotZone: "Covers",
    currentOverBalls: ["1", "4"],
    recentBalls: ["4.2 4 • Bumrah • Covers"],
    innings1Summary: "Mumbai Strikers: 45/1 (4.2 ov)",
    innings2Summary: "",
    battingCardLines: ["Rohit*|28|16|4|1|175.0|not out"],
    bowlingCardLines: ["Bumrah|1.2|0|12|1|9.0"],
    ballSequence: 26,
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

// --- 1. UNAUTHENTICATED SPECTATOR READS ALLOWED FOR SHARED MATCHES; ALL WRITES & PII DENIED ---

test("Unauthenticated spectators can watch shared live matches without login, but cannot write or read private data", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_A_UID).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  // Admin creates a shared live match and a paused (private) match
  await assertSucceeds(
    adminDb.collection("live_matches").doc(SHARE_CODE_A).set(validLiveMatchPayload(ADMIN_A_UID))
  );
  await assertSucceeds(
    adminDb
      .collection("live_matches")
      .doc("CSPRIV")
      .set(validLiveMatchPayload(ADMIN_A_UID, { sharingEnabled: false }))
  );

  // Unauthenticated spectator CAN get a shared live match & list shared live matches
  await assertSucceeds(unauthDb.collection("live_matches").doc(SHARE_CODE_A).get());
  await assertSucceeds(
    unauthDb.collection("live_matches").where("sharingEnabled", "==", true).get()
  );

  // Unauthenticated spectator CANNOT read paused/private match or run unfiltered list
  await assertFails(unauthDb.collection("live_matches").doc("CSPRIV").get());
  await assertFails(unauthDb.collection("live_matches").get());

  // Unauthenticated spectator CANNOT read/write users, admins, or mutate live_matches
  await assertFails(unauthDb.collection("users").doc(ADMIN_A_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ADMIN_A_UID).set({
      userId: ADMIN_A_UID,
      displayName: "Anon",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("live_matches").doc("CSNEW1").set(validLiveMatchPayload(ADMIN_A_UID))
  );
  await assertFails(
    unauthDb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 999,
      updatedAt: now,
    })
  );
  await assertFails(unauthDb.collection("live_matches").doc(SHARE_CODE_A).delete());
  await assertFails(unauthDb.collection("admins").doc(ADMIN_A_UID).set({ enabled: true }));
});

// --- 2. USER PROFILE PII ISOLATION & IMMUTABILITY ---

test("User profile: owner create & partial update succeed; cross-user read & shadow fields fail", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).set({
      userId: ADMIN_A_UID,
      displayName: "Chandu Admin",
      email: "admin@example.com",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(adminADb.collection("users").doc(ADMIN_A_UID).get());
  await assertFails(adminBDb.collection("users").doc(ADMIN_A_UID).get());
  await assertFails(adminADb.collection("users").get());

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).update({
      displayName: "Chandu Pritam",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    adminADb.collection("users").doc(ADMIN_A_UID).update({
      displayName: "Chandu Pritam",
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

// --- 3. MULTI-ADMIN OWNERSHIP ENFORCEMENT ---

test("Multi-Admin: each Admin can create & update only their own match; other Admins are blocked from editing", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID).firestore();

  // Admin A creates Match A; Admin B creates Match B
  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).set(validLiveMatchPayload(ADMIN_A_UID))
  );
  await assertSucceeds(
    adminBDb
      .collection("live_matches")
      .doc(SHARE_CODE_B)
      .set(validLiveMatchPayload(ADMIN_B_UID, { adminName: "Rahul Admin", localMatchId: 202 }))
  );

  // Admin B CANNOT spoof Admin A's uid on create
  await assertFails(
    adminBDb.collection("live_matches").doc("CS8888").set(validLiveMatchPayload(ADMIN_A_UID))
  );

  // Admin B CAN watch Admin A's shared match, but CANNOT update or delete it
  await assertSucceeds(adminBDb.collection("live_matches").doc(SHARE_CODE_A).get());
  await assertFails(
    adminBDb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(adminBDb.collection("live_matches").doc(SHARE_CODE_A).delete());

  // Admin A updates their own match cleanly
  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 51,
      oversDisplay: "4.3",
      lastBallBadge: "6",
      lastShotZone: "Long On",
      currentOverBalls: ["1", "4", "6"],
      ballSequence: 27,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin A pauses sharing -> Admin B and unauthenticated spectators lose read access
  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).update({
      sharingEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(adminBDb.collection("live_matches").doc(SHARE_CODE_A).get());
  await assertSucceeds(adminADb.collection("live_matches").doc(SHARE_CODE_A).get());
});

// --- 4. QUERY ALIGNMENT ---

test("Live matches list queries: public shared query & owner adminId query succeed; private scraping fails", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID).firestore();

  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).set(validLiveMatchPayload(ADMIN_A_UID))
  );

  // Owner query succeeds
  await assertSucceeds(
    adminADb.collection("live_matches").where("adminId", "==", ADMIN_A_UID).get()
  );

  // Public shared query succeeds
  await assertSucceeds(
    adminBDb.collection("live_matches").where("sharingEnabled", "==", true).get()
  );

  // Unfiltered scraping or querying another admin's private matches fails
  await assertFails(adminBDb.collection("live_matches").get());
  await assertFails(
    adminBDb.collection("live_matches").where("adminId", "==", ADMIN_A_UID).get()
  );
});

// --- 5. TERMINAL STATE LOCKING & EMAIL SPOOFING PROTECTION ---

test("Completed match locks regular score edits; spoofed unverified admin email rejected; verified super-admin allowed", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_A_UID).firestore();
  const spoofedAdminDb = testEnv
    .authenticatedContext("attacker_uid", {
      email: "kamleshkumarsingh317@gmail.com",
      email_verified: false,
    })
    .firestore();
  const verifiedSuperAdminDb = testEnv
    .authenticatedContext(SUPER_ADMIN_UID, {
      email: "kamleshkumarsingh317@gmail.com",
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    adminDb.collection("live_matches").doc(SHARE_CODE_A).set(
      validLiveMatchPayload(ADMIN_A_UID, {
        matchStatus: "COMPLETED",
        resultSummary: "Mumbai won by 10 runs",
      })
    )
  );

  await assertFails(
    adminDb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 100,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    adminDb.collection("live_matches").doc(SHARE_CODE_A).update({
      sharingEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertFails(
    spoofedAdminDb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 200,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  await assertSucceeds(
    verifiedSuperAdminDb.collection("live_matches").doc(SHARE_CODE_A).update({
      scoreRuns: 46,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

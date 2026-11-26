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
  const adminDb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
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
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();
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
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();

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
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();

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
  const adminDb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
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

// --- 6. FULL INNINGS SCORECARD PAYLOAD VALIDATION ---

test("Full scorecard fields (batting, bowling, extras, FOW, partnerships) sync validly and reject oversized or unauthenticated writes", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const fullPayload = validLiveMatchPayload(ADMIN_A_UID, {
    currentInningsNumber: 2,
    potmSummary: "Rohit (52 off 28)",
    innings1Meta: "Mumbai Strikers|Delhi Royals|142|4|10.0|14.20|true|true",
    innings2Meta: "Delhi Royals|Mumbai Strikers|45|1|4.2|10.38|true|false",
    innings1Extras: "8 (b 1, lb 2, wd 4, nb 1)",
    innings2Extras: "3 (b 0, lb 1, wd 2, nb 0)",
    innings1BattingLines: ["Rohit|52|28|6|2|185.7|c Kohli b Bumrah|false"],
    innings1BowlingLines: ["Bumrah|2.0|0|18|2|9.0|1|0"],
    innings1FowLines: ["85-1 (Rohit, 6.4)"],
    innings1PartnershipLines: ["Rohit|52|28|Gill|30|14|85|42|false"],
    innings2BattingLines: ["Virat *|28|16|4|1|175.0|not out|true"],
    innings2BowlingLines: ["Shami|2.0|0|16|1|8.0|1|0"],
    innings2FowLines: ["12-1 (Warner, 1.3)"],
    innings2PartnershipLines: ["Virat|28|16|Pant|14|7|33|17|true"],
    analyticsLines: [
      "D1|12|30|48|44|8",
      "D2|6|14|16|6|3",
      "W|Start|50.0|0.00||1|false",
      "W|4.2|62.5|10.38|12.10|2|false",
      "O1|1|Bumrah|12|0|12|0|true|4~4~0~0~0~0~0~1~0~0~1;0~0~0~0~0~0~0~0~0~1~2",
      "O2|1|Shami|10|1|10|1|true|W~0~1~0~0~0~0~0~0~1~1;6~6~0~0~0~0~0~0~1~0~2",
      "S1|0.1|Rohit|Bumrah|4|0.750|0.350|Covers|1|0",
      "S2|0.2|Virat|Shami|6|-0.820|0.420|Mid-Wicket|0|1"
    ],
  });

  await assertSucceeds(
    adminDb.collection("live_matches").doc("CSFULL").set(fullPayload)
  );
  await assertSucceeds(
    unauthDb.collection("live_matches").doc("CSFULL").get()
  );

  await assertSucceeds(
    adminDb.collection("live_matches").doc("CSFULL").update({
      innings2Extras: "5 (b 0, lb 1, wd 4, nb 0)",
      innings2FowLines: ["12-1 (Warner, 1.3)", "45-2 (Pant, 4.2)"],
      analyticsLines: [
        "W|Start|50.0|0.00||1|false",
        "W|4.2|55.0|10.38|12.10|2|true",
        "O2|1|Shami|10|1|10|1|true|W~0~1~0~0~0~0~0~0~1~1;6~6~0~0~0~0~0~0~1~0~2",
        "S2|0.2|Virat|Shami|6|-0.820|0.420|Mid-Wicket|0|1"
      ],
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // Oversized extras string must be rejected
  await assertFails(
    adminDb.collection("live_matches").doc("CSFULL").update({
      innings2Extras: "x".repeat(81),
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

// --- 7. COMPRESSED TEAM & PLAYER PROFILE AVATARS ---

test("Admin can save/read/delete compressed team & player avatars under their profile; cross-user & oversized payloads fail", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("TEAM_mumbai_strikers").set({
      ownerUid: ADMIN_A_UID,
      entityType: "TEAM",
      entityName: "Mumbai Strikers",
      teamName: "",
      photoBase64: "AAAA",
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("TEAM_mumbai_strikers").get()
  );
  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("avatars").get()
  );

  // Cross-user read/write denied
  await assertFails(
    adminBDb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("TEAM_mumbai_strikers").get()
  );
  await assertFails(
    adminBDb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("PLAYER_rohit").set({
      ownerUid: ADMIN_B_UID,
      entityType: "PLAYER",
      entityName: "Rohit",
      teamName: "Mumbai Strikers",
      photoBase64: "AAAA",
      updatedAt: now,
    })
  );

  // Oversized photo (>32000 chars) rejected
  await assertFails(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("PLAYER_rohit").set({
      ownerUid: ADMIN_A_UID,
      entityType: "PLAYER",
      entityName: "Rohit",
      teamName: "Mumbai Strikers",
      photoBase64: "A".repeat(32001),
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("avatars").doc("TEAM_mumbai_strikers").delete()
  );
});

// --- 8. EMAIL VERIFICATION GATE FOR LIVE MATCHES ---

test("Unverified Email/Password admin can save profile/avatars locally in user scope, but CANNOT go live until email is verified", async () => {
  const unverifiedUid = "unverified_admin_01";
  const unverifiedDb = testEnv
    .authenticatedContext(unverifiedUid, {
      email: "newadmin@example.com",
      email_verified: false,
    })
    .firestore();
  const verifiedDb = testEnv
    .authenticatedContext(unverifiedUid, {
      email: "newadmin@example.com",
      email_verified: true,
    })
    .firestore();
  const now = new Date(Date.now() - 1000);

  // User profile creation still works while waiting for email verification
  await assertSucceeds(
    unverifiedDb.collection("users").doc(unverifiedUid).set({
      userId: unverifiedUid,
      displayName: "New Admin",
      email: "newadmin@example.com",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Creating a new live_matches broadcast MUST fail while email_verified == false
  await assertFails(
    unverifiedDb.collection("live_matches").doc("CSUNVR").set(validLiveMatchPayload(unverifiedUid))
  );

  // Once email is verified, creating a new live_matches broadcast succeeds
  await assertSucceeds(
    verifiedDb.collection("live_matches").doc("CSUNVR").set(validLiveMatchPayload(unverifiedUid))
  );
});

// --- 9. LIVE MATCH PLAYER CAREER SUBCOLLECTION ---

test("Match admin can publish player career records; unauthenticated spectators can read shared careers while cross-admin & oversized writes fail", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).set(validLiveMatchPayload(ADMIN_A_UID))
  );

  const careerPayload = {
    adminId: ADMIN_A_UID,
    shareCode: SHARE_CODE_A,
    playerName: "Rohit",
    teamName: "Mumbai Strikers",
    role: "Batsman",
    photoBase64: "",
    matchesPlayed: 14,
    battingStatsLine: "14|2|540|320|52|24|92|1|4|3|0|0",
    bowlingStatsLine: "3|30|28|2|0|12|1|0|0|0|1|8",
    recentPerformancesLines: [
      "101|Mumbai Strikers vs Delhi Royals|Delhi Royals|1732500000000|1|52|28|1|6|2|0|0|0|0|Mumbai won"
    ],
    careerShotLines: [
      "0.1|Rohit|Bumrah|4|0.750|0.350|Covers|1|0"
    ],
    updatedAt: now,
  };

  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_rohit_1").set(careerPayload)
  );

  // Unauthenticated spectator can read existing & non-existing player docs when sharingEnabled == true
  await assertSucceeds(
    unauthDb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_rohit_1").get()
  );
  await assertSucceeds(
    unauthDb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_missing_2").get()
  );

  // Cross-admin cannot write into Admin A's match player_careers
  await assertFails(
    adminBDb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_rohit_1").set({
      ...careerPayload,
      adminId: ADMIN_B_UID,
    })
  );

  // Oversized photo rejected
  await assertFails(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_rohit_1").set({
      ...careerPayload,
      photoBase64: "A".repeat(32001),
    })
  );

  // When sharing is paused, unauthenticated spectators can no longer read player_careers
  await assertSucceeds(
    adminADb.collection("live_matches").doc(SHARE_CODE_A).update({
      sharingEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    unauthDb.collection("live_matches").doc(SHARE_CODE_A).collection("player_careers").doc("player_rohit_1").get()
  );
});

// --- 10. ADMIN SAVED TEAMS & SQUAD PLAYERS ---

test("Admin can save/read/delete teams with name, photo, and squad players; cross-user & oversized writes fail", async () => {
  const adminADb = testEnv.authenticatedContext(ADMIN_A_UID, { email: "admin_a@example.com", email_verified: true }).firestore();
  const adminBDb = testEnv.authenticatedContext(ADMIN_B_UID, { email: "admin_b@example.com", email_verified: true }).firestore();
  const now = new Date(Date.now() - 1000);

  const teamPayload = {
    ownerUid: ADMIN_A_UID,
    teamName: "Patna Royals",
    shortCode: "PR",
    photoBase64: "AAAA",
    playerEntries: ["Chandu|All-Rounder", "Rohit|Batter"],
    updatedAt: now,
  };

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").doc("team_patna_royals_1").set(teamPayload)
  );
  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").get()
  );

  // Cross-user read & write rejected
  await assertFails(
    adminBDb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").doc("team_patna_royals_1").get()
  );
  await assertFails(
    adminBDb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").doc("team_hack_2").set({
      ...teamPayload,
      ownerUid: ADMIN_B_UID,
    })
  );

  // Oversized photo rejected
  await assertFails(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").doc("team_patna_royals_1").set({
      ...teamPayload,
      photoBase64: "A".repeat(32001),
    })
  );

  await assertSucceeds(
    adminADb.collection("users").doc(ADMIN_A_UID).collection("saved_teams").doc("team_patna_royals_1").delete()
  );
});





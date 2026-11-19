const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_uid_1";
const ADMIN_EMAIL = "bhagwankibaat4@gmail.com";
const STAFF_UID = "staff_uid_1";
const STAFF_EMAIL = "staff1@gosala.org";
const PUBLIC_UID = "public_uid_1";
const PUBLIC_EMAIL = "devotee1@example.com";
const OTHER_PUBLIC_UID = "public_uid_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("cattle").where("publicApproved", "==", true).get());
  await assertFails(unauthDb.collection("goshalaInfo").doc("public_summary").get());
  await assertFails(unauthDb.collection("financialTransactions").get());
  await assertFails(
    unauthDb.collection("users").doc(PUBLIC_UID).set({
      userId: PUBLIC_UID,
      displayName: "Unauth",
      email: "a@b.com",
      role: "PUBLIC",
      preferredLanguage: "hi",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Role escalation protection: public user cannot self-assign ADMIN/STAFF or spoof unverified admin email", async () => {
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();

  // Public user creating own profile with PUBLIC role succeeds
  await assertSucceeds(
    publicDb.collection("users").doc(PUBLIC_UID).set({
      userId: PUBLIC_UID,
      displayName: "राम भक्त",
      email: PUBLIC_EMAIL,
      role: "PUBLIC",
      preferredLanguage: "hi",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Public user trying to set role = ADMIN on create fails
  await assertFails(
    publicDb.collection("users").doc("spoof_user").set({
      userId: "spoof_user",
      displayName: "Spoof",
      email: PUBLIC_EMAIL,
      role: "ADMIN",
      preferredLanguage: "hi",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Public user trying to update own role to ADMIN fails
  await assertFails(
    publicDb.collection("users").doc(PUBLIC_UID).update({
      role: "ADMIN",
      updatedAt: pastDate(500),
    })
  );

  // Public user cannot add themselves to /admins or /staffAccounts without an admin invite
  await assertFails(
    publicDb.collection("admins").doc(PUBLIC_UID).set({
      uid: PUBLIC_UID,
      email: PUBLIC_EMAIL,
      displayName: "Fake Admin",
      createdAt: pastDate(),
    })
  );
  await assertFails(
    publicDb.collection("staffAccounts").doc(PUBLIC_UID).set({
      uid: PUBLIC_UID,
      email: PUBLIC_EMAIL,
      name: "Fake Staff",
      designation: "Sevak",
      monthlySalary: 8000,
      active: true,
      permissions: { cattle: ["VIEW", "ADD", "EDIT", "DELETE"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Spoofed unverified admin email cannot read financialTransactions
  const spoofedAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofedAdminDb.collection("financialTransactions").get());
});

test("Admin full access + Staff invite claim + Granular Staff CRUD permissions", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const staffDb = testEnv.authenticatedContext(STAFF_UID, {
    email: STAFF_EMAIL,
    email_verified: true,
  }).firestore();

  // 1. Admin creates staff invite granting ONLY cattle VIEW+ADD and feed VIEW+ADD (NO delete, NO finance)
  const invitePerms = {
    cattle: ["VIEW", "ADD"],
    feed: ["VIEW", "ADD"],
  };
  await assertSucceeds(
    adminDb.collection("staffInvites").doc(STAFF_EMAIL).set({
      email: STAFF_EMAIL,
      name: "रमेश सेवक",
      phone: "9876543210",
      designation: "गौ सेवक",
      monthlySalary: 12000,
      permissions: invitePerms,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // 2. Staff claims invite with exact permissions
  await assertSucceeds(
    staffDb.collection("staffAccounts").doc(STAFF_UID).set({
      uid: STAFF_UID,
      email: STAFF_EMAIL,
      name: "रमेश सेवक",
      phone: "9876543210",
      designation: "गौ सेवक",
      monthlySalary: 12000,
      active: true,
      permissions: invitePerms,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Staff cannot claim escalated permissions not in invite
  await assertFails(
    staffDb.collection("staffAccounts").doc(STAFF_UID).update({
      permissions: { finance: ["VIEW", "ADD", "EDIT", "DELETE"] },
      updatedAt: pastDate(500),
    })
  );

  // 3. Staff can ADD cattle, VIEW cattle, but CANNOT EDIT or DELETE cattle, and CANNOT access finance
  await assertSucceeds(
    staffDb.collection("cattle").doc("cow_1").set({
      animalCode: "JBGS-G-001",
      name: "गौरी",
      category: "COW",
      breed: "गिर (Gir)",
      sex: "FEMALE",
      ageText: "4 वर्ष",
      birthDate: "2022-05-10",
      colour: "सफेद व भूरा",
      weightKg: 380.5,
      healthStatus: "HEALTHY",
      entryDate: "2024-01-15",
      shedNumber: "Shed-A1",
      identificationMarks: "माथे पर सफेद तिलक",
      qrPayload: "JBGS:CATTLE:cow_1:JBGS-G-001",
      notes: "शांत स्वभाव",
      publicApproved: true,
      sponsorshipAvailable: true,
      monthlySupportInr: 2100,
      createdBy: STAFF_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(staffDb.collection("cattle").get());
  await assertFails(staffDb.collection("cattle").doc("cow_1").update({ name: "Gauri Updated", updatedAt: pastDate(500) }));
  await assertFails(staffDb.collection("cattle").doc("cow_1").delete());
  await assertFails(staffDb.collection("financialTransactions").get());
  await assertFails(staffDb.collection("farmers").get());

  // 4. Admin can edit, delete cattle, and create financial transaction
  await assertSucceeds(
    adminDb.collection("cattle").doc("cow_1").update({
      weightKg: 390.0,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(
    adminDb.collection("financialTransactions").doc("tx_1").set({
      txType: "EXPENSE",
      category: "FEED_PURCHASE",
      amountInr: 4500,
      paymentMode: "UPI",
      paymentStatus: "COMPLETED",
      partyName: "रामपाल सिंह किसान",
      description: "भूसा खरीद 8 क्विंटल",
      dateStr: "2026-11-18",
      receiptNumber: "INV-001",
      recordedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Public user isolation: only approved cattle, own donations (pending only), and own sponsorships", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("cattle").doc("cow_pub").set({
      animalCode: "JBGS-G-002",
      name: "नंदिनी",
      category: "COW",
      breed: "साहीवाल",
      sex: "FEMALE",
      ageText: "5 वर्ष",
      colour: "लाल-भूरा",
      weightKg: 410,
      healthStatus: "HEALTHY",
      entryDate: "2023-08-01",
      shedNumber: "Shed-B2",
      identificationMarks: "घंटी",
      qrPayload: "JBGS:CATTLE:cow_pub",
      publicApproved: true,
      sponsorshipAvailable: true,
      monthlySupportInr: 3100,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("cattle").doc("cow_priv").set({
      animalCode: "JBGS-G-003",
      name: "कामधेनु",
      category: "COW",
      breed: "थारपारकर",
      sex: "FEMALE",
      ageText: "6 वर्ष",
      colour: "सफेद",
      weightKg: 350,
      healthStatus: "SICK",
      entryDate: "2025-02-01",
      shedNumber: "Shed-ICU",
      identificationMarks: "दाहिने कान पर निशान",
      qrPayload: "JBGS:CATTLE:cow_priv",
      publicApproved: false,
      sponsorshipAvailable: false,
      monthlySupportInr: 0,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("donations").doc("don_other").set({
      donorUid: OTHER_PUBLIC_UID,
      donorName: "Other Donor",
      donorPhone: "9999999999",
      purpose: "GAU_SEVA",
      amountInr: 1100,
      paymentProviderRef: "MANUAL_PENDING",
      paymentStatus: "PENDING_VERIFICATION",
      receiptNumber: "RCPT-OTHER",
      dateStr: "2026-11-18",
      recordedBy: OTHER_PUBLIC_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();

  // Filtered query for publicApproved == true succeeds; unfiltered cattle query fails
  await assertSucceeds(publicDb.collection("cattle").where("publicApproved", "==", true).get());
  await assertFails(publicDb.collection("cattle").get());
  await assertFails(publicDb.collection("cattle").doc("cow_priv").get());
  await assertFails(publicDb.collection("cattle").doc("cow_pub").delete());

  // Public cannot read internal health, feed, staff, finance, farmers, or compost
  await assertFails(publicDb.collection("healthRecords").get());
  await assertFails(publicDb.collection("feedAndOperations").get());
  await assertFails(publicDb.collection("financialTransactions").get());
  await assertFails(publicDb.collection("farmers").get());
  await assertFails(publicDb.collection("compostProduction").get());

  // Public donation MUST start as PENDING_VERIFICATION (cannot self-mark VERIFIED_SUCCESS)
  await assertFails(
    publicDb.collection("donations").doc("don_fake").set({
      donorUid: PUBLIC_UID,
      donorName: "Devotee",
      donorPhone: "9876543210",
      purpose: "GAU_SEVA",
      amountInr: 5100,
      paymentProviderRef: "UNVERIFIED",
      paymentStatus: "VERIFIED_SUCCESS",
      receiptNumber: "RCPT-1",
      dateStr: "2026-11-18",
      recordedBy: PUBLIC_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    publicDb.collection("donations").doc("don_real").set({
      donorUid: PUBLIC_UID,
      donorName: "Devotee",
      donorPhone: "9876543210",
      purpose: "GAU_SEVA",
      amountInr: 5100,
      paymentProviderRef: "UPI_REF_PENDING",
      paymentStatus: "PENDING_VERIFICATION",
      receiptNumber: "RCPT-2",
      dateStr: "2026-11-18",
      recordedBy: PUBLIC_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Public can query only own donations, cannot read another donor's donation
  await assertSucceeds(publicDb.collection("donations").where("donorUid", "==", PUBLIC_UID).get());
  await assertFails(publicDb.collection("donations").get());
  await assertFails(publicDb.collection("donations").doc("don_other").get());

  // Sponsorship MUST have disclaimerAccepted == true and status == PENDING
  await assertFails(
    publicDb.collection("sponsorships").doc("sp_no_disc").set({
      applicantUid: PUBLIC_UID,
      applicantName: "Devotee",
      applicantPhone: "9876543210",
      applicantAddress: "Morena, MP",
      cattleId: "cow_pub",
      cattleName: "नंदिनी",
      animalCode: "JBGS-G-002",
      monthlySupportInr: 3100,
      durationMonths: 6,
      totalSupportInr: 18600,
      status: "PENDING",
      disclaimerAccepted: false,
      dateStr: "2026-11-18",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  await assertSucceeds(
    publicDb.collection("sponsorships").doc("sp_ok").set({
      applicantUid: PUBLIC_UID,
      applicantName: "Devotee",
      applicantPhone: "9876543210",
      applicantAddress: "Morena, MP",
      cattleId: "cow_pub",
      cattleName: "नंदिनी",
      animalCode: "JBGS-G-002",
      monthlySupportInr: 3100,
      durationMonths: 6,
      totalSupportInr: 18600,
      status: "PENDING",
      disclaimerAccepted: true,
      dateStr: "2026-11-18",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Public cannot self-approve sponsorship
  await assertFails(
    publicDb.collection("sponsorships").doc("sp_ok").update({
      status: "APPROVED",
      updatedAt: pastDate(500),
    })
  );
});

test("Admin /admins/{uid} active check: active admin succeeds, inactive admin is denied", async () => {
  const ACTIVE_ADMIN_UID = "trusted_admin_uid";
  const INACTIVE_ADMIN_UID = "disabled_admin_uid";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("admins").doc(ACTIVE_ADMIN_UID).set({
      uid: ACTIVE_ADMIN_UID,
      email: "trust@gosala.org",
      displayName: "Trust Adhyaksh",
      active: true,
      createdAt: pastDate(),
    });
    await db.collection("admins").doc(INACTIVE_ADMIN_UID).set({
      uid: INACTIVE_ADMIN_UID,
      email: "old@gosala.org",
      displayName: "Former Admin",
      active: false,
      createdAt: pastDate(),
    });
  });

  const activeAdminDb = testEnv.authenticatedContext(ACTIVE_ADMIN_UID, {
    email: "trust@gosala.org",
    email_verified: true,
  }).firestore();

  const inactiveAdminDb = testEnv.authenticatedContext(INACTIVE_ADMIN_UID, {
    email: "old@gosala.org",
    email_verified: true,
  }).firestore();

  // Active Admin can read financialTransactions, add & edit cattle, and create staff invites
  await assertSucceeds(activeAdminDb.collection("financialTransactions").get());
  await assertSucceeds(
    activeAdminDb.collection("cattle").doc("admin_cow_1").set({
      animalCode: "JBGS-G-101",
      name: "कामधेनु",
      category: "COW",
      breed: "गिर",
      sex: "FEMALE",
      ageText: "4 वर्ष",
      colour: "सफेद",
      weightKg: 400,
      healthStatus: "HEALTHY",
      entryDate: "2026-11-18",
      shedNumber: "Shed-A1",
      identificationMarks: "तिलक",
      qrPayload: "JBGS:CATTLE:admin_cow_1:JBGS-G-101",
      publicApproved: true,
      sponsorshipAvailable: true,
      monthlySupportInr: 2100,
      createdBy: ACTIVE_ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    activeAdminDb.collection("cattle").doc("admin_cow_1").update({
      weightKg: 415,
      updatedAt: pastDate(500),
    })
  );

  // Inactive Admin (active == false) is denied internal Admin reads & writes
  await assertFails(inactiveAdminDb.collection("financialTransactions").get());
  await assertFails(
    inactiveAdminDb.collection("staffInvites").doc("someone@gosala.org").set({
      email: "someone@gosala.org",
      name: "Test",
      designation: "Sevak",
      monthlySalary: 5000,
      permissions: { cattle: ["VIEW"] },
      createdBy: INACTIVE_ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

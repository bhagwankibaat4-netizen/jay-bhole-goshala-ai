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

test("Edit/Delete enforcement for Finance, Farmers, Compost, Staff + auditLogs role anti-spoofing", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const staffNoEditUid = "staff_view_only";
  const staffWithEditUid = "staff_editor";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffAccounts").doc(staffNoEditUid).set({
      uid: staffNoEditUid,
      email: "viewer@gosala.org",
      name: "View Staff",
      designation: "Sevak",
      monthlySalary: 9000,
      active: true,
      permissions: {
        finance: ["VIEW"],
        farmers: ["VIEW"],
        production: ["VIEW"],
      },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("staffAccounts").doc(staffWithEditUid).set({
      uid: staffWithEditUid,
      email: "editor@gosala.org",
      name: "Editor Staff",
      designation: "Manager",
      monthlySalary: 15000,
      active: true,
      permissions: {
        finance: ["VIEW", "ADD", "EDIT", "DELETE"],
        farmers: ["VIEW", "ADD", "EDIT", "DELETE"],
        production: ["VIEW", "ADD", "EDIT", "DELETE"],
      },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("staffInvites").doc("pending@gosala.org").set({
      email: "pending@gosala.org",
      name: "Pending Sevak",
      phone: "9876543210",
      designation: "Sevak",
      monthlySalary: 8000,
      permissions: { cattle: ["VIEW"] },
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("financialTransactions").doc("tx_edit_1").set({
      txType: "EXPENSE",
      category: "FEED_PURCHASE",
      amountInr: 3000,
      paymentMode: "CASH",
      paymentStatus: "COMPLETED",
      partyName: "रामलाल",
      description: "चारा खरीद",
      dateStr: "2026-11-19",
      receiptNumber: "TX-101",
      recordedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("farmers").doc("farmer_edit_1").set({
      name: "हरि सिंह",
      mobile: "9876543210",
      village: "पोरसा",
      address: "वार्ड 3",
      cooperationType: "BOTH",
      totalPurchasedInr: 1000,
      totalSoldInr: 500,
      outstandingInr: 0,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("compostProduction").doc("compost_edit_1").set({
      batchCode: "CMP-201",
      productType: "VERMICOMPOST",
      quantity: 400,
      unit: "KG",
      rawDungKg: 700,
      productionCostInr: 1000,
      packagingCostInr: 200,
      sellingPricePerUnitInr: 12,
      soldQuantity: 50,
      stockRemaining: 350,
      status: "READY_FOR_SALE",
      dateStr: "2026-11-19",
      notes: "",
      recordedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  const viewStaffDb = testEnv.authenticatedContext(staffNoEditUid, {
    email: "viewer@gosala.org",
    email_verified: true,
  }).firestore();
  const editStaffDb = testEnv.authenticatedContext(staffWithEditUid, {
    email: "editor@gosala.org",
    email_verified: true,
  }).firestore();
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();

  // Public & view-only staff CANNOT update or delete finance, farmers, compost, or staff
  await assertFails(publicDb.collection("financialTransactions").doc("tx_edit_1").update({ amountInr: 1, updatedAt: pastDate(500) }));
  await assertFails(publicDb.collection("financialTransactions").doc("tx_edit_1").delete());
  await assertFails(viewStaffDb.collection("financialTransactions").doc("tx_edit_1").update({ amountInr: 3500, updatedAt: pastDate(500) }));
  await assertFails(viewStaffDb.collection("financialTransactions").doc("tx_edit_1").delete());
  await assertFails(viewStaffDb.collection("farmers").doc("farmer_edit_1").update({ village: "मुरैना", updatedAt: pastDate(500) }));
  await assertFails(viewStaffDb.collection("farmers").doc("farmer_edit_1").delete());
  await assertFails(viewStaffDb.collection("compostProduction").doc("compost_edit_1").update({ sellingPricePerUnitInr: 15, updatedAt: pastDate(500) }));
  await assertFails(viewStaffDb.collection("compostProduction").doc("compost_edit_1").delete());
  await assertFails(editStaffDb.collection("staffAccounts").doc(staffNoEditUid).delete());
  await assertFails(editStaffDb.collection("staffInvites").doc("pending@gosala.org").delete());

  // Authorized staff CAN update & delete finance, farmers, compost
  await assertSucceeds(editStaffDb.collection("financialTransactions").doc("tx_edit_1").update({ amountInr: 3200, updatedAt: pastDate(500) }));
  await assertSucceeds(editStaffDb.collection("farmers").doc("farmer_edit_1").update({ village: "मुरैना", updatedAt: pastDate(500) }));
  await assertSucceeds(editStaffDb.collection("compostProduction").doc("compost_edit_1").update({ sellingPricePerUnitInr: 14, updatedAt: pastDate(500) }));

  // Admin CAN delete finance, farmers, compost, staffAccount, and staffInvite
  await assertSucceeds(adminDb.collection("financialTransactions").doc("tx_edit_1").delete());
  await assertSucceeds(adminDb.collection("farmers").doc("farmer_edit_1").delete());
  await assertSucceeds(adminDb.collection("compostProduction").doc("compost_edit_1").delete());
  await assertSucceeds(adminDb.collection("staffAccounts").doc(staffNoEditUid).delete());
  await assertSucceeds(adminDb.collection("staffInvites").doc("pending@gosala.org").delete());

  // auditLogs role anti-spoofing: public cannot log as ADMIN or STAFF, only PUBLIC
  await assertFails(
    publicDb.collection("auditLogs").doc("log_spoof_admin").set({
      actorUid: PUBLIC_UID,
      actorName: "Spoofer",
      actorRole: "ADMIN",
      action: "DELETE",
      module: "finance",
      entityId: "tx_1",
      summary: "Fake admin log",
      createdAt: pastDate(),
    })
  );
  await assertFails(
    publicDb.collection("auditLogs").doc("log_spoof_staff").set({
      actorUid: PUBLIC_UID,
      actorName: "Spoofer",
      actorRole: "STAFF",
      action: "CREATE",
      module: "cattle",
      entityId: "c1",
      summary: "Fake staff log",
      createdAt: pastDate(),
    })
  );
  await assertSucceeds(
    publicDb.collection("auditLogs").doc("log_pub_ok").set({
      actorUid: PUBLIC_UID,
      actorName: "Devotee",
      actorRole: "PUBLIC",
      action: "CREATE",
      module: "donations",
      entityId: "don_1",
      summary: "Submitted donation",
      createdAt: pastDate(),
    })
  );
  await assertSucceeds(
    adminDb.collection("auditLogs").doc("log_admin_ok").set({
      actorUid: ADMIN_UID,
      actorName: "Admin",
      actorRole: "ADMIN",
      action: "DELETE",
      module: "finance",
      entityId: "tx_edit_1",
      summary: "Deleted transaction",
      createdAt: pastDate(),
    })
  );
});

test("Email/Password staff invite claim with employeeId, inactive invite block, and inactive staff full lockout", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const activeEmpUid = "emp_email_pass_1";
  const activeEmpEmail = "karmachari1@gosala.org";
  const inactiveInviteUid = "emp_inactive_invite";
  const inactiveInviteEmail = "blocked_invite@gosala.org";
  const deactivatedEmpUid = "emp_deactivated";

  // Admin creates active invite with employeeId AND an inactive invite
  await assertSucceeds(
    adminDb.collection("staffInvites").doc(activeEmpEmail).set({
      employeeId: "EMP-101",
      email: activeEmpEmail,
      name: "सुरेश सेवक",
      phone: "9876500001",
      designation: "गौ सेवक",
      monthlySalary: 11000,
      active: true,
      permissions: { feed: ["VIEW", "ADD"], cattle: ["VIEW"] },
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    adminDb.collection("staffInvites").doc(inactiveInviteEmail).set({
      employeeId: "EMP-102",
      email: inactiveInviteEmail,
      name: "निष्क्रिय आमंत्रण",
      phone: "9876500002",
      designation: "सहायक",
      monthlySalary: 9000,
      active: false,
      permissions: { feed: ["VIEW"] },
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Email/Password employee (email_verified: false) can read and claim their active invite
  const empDb = testEnv.authenticatedContext(activeEmpUid, {
    email: activeEmpEmail,
    email_verified: false,
  }).firestore();
  await assertSucceeds(empDb.collection("staffInvites").doc(activeEmpEmail).get());
  await assertSucceeds(
    empDb.collection("staffAccounts").doc(activeEmpUid).set({
      uid: activeEmpUid,
      employeeId: "EMP-101",
      email: activeEmpEmail,
      name: "सुरेश सेवक",
      phone: "9876500001",
      designation: "गौ सेवक",
      monthlySalary: 11000,
      active: true,
      permissions: { feed: ["VIEW", "ADD"], cattle: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Inactive invite CANNOT be claimed into staffAccounts
  const blockedInviteDb = testEnv.authenticatedContext(inactiveInviteUid, {
    email: inactiveInviteEmail,
    email_verified: false,
  }).firestore();
  await assertFails(
    blockedInviteDb.collection("staffAccounts").doc(inactiveInviteUid).set({
      uid: inactiveInviteUid,
      employeeId: "EMP-102",
      email: inactiveInviteEmail,
      name: "निष्क्रिय आमंत्रण",
      phone: "9876500002",
      designation: "सहायक",
      monthlySalary: 9000,
      active: true,
      permissions: { feed: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Seed an inactive staff account and test complete backend lockout
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffAccounts").doc(deactivatedEmpUid).set({
      uid: deactivatedEmpUid,
      employeeId: "EMP-103",
      email: "deactivated@gosala.org",
      name: "बंद खाता सेवक",
      designation: "सेवक",
      monthlySalary: 10000,
      active: false,
      permissions: { cattle: ["VIEW", "ADD"], donations: ["VIEW", "ADD", "EDIT", "DELETE"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("goshalaInfo").doc("public_summary").set({
      totalCattle: 10,
      healthyCattle: 9,
      sickCattle: 1,
      todayFeedKg: 100,
      todayExpenseInr: 1200,
      totalDonationsInr: 25000,
      updatedAt: pastDate(),
    });
  });

  const inactiveStaffDb = testEnv.authenticatedContext(deactivatedEmpUid, {
    email: "deactivated@gosala.org",
    email_verified: true,
  }).firestore();

  // Inactive staff can read own staffAccounts doc (for live client sign-out) but NOTHING else
  await assertSucceeds(inactiveStaffDb.collection("staffAccounts").doc(deactivatedEmpUid).get());
  await assertFails(inactiveStaffDb.collection("goshalaInfo").doc("public_summary").get());
  await assertFails(inactiveStaffDb.collection("cattle").where("publicApproved", "==", true).get());
  await assertFails(inactiveStaffDb.collection("donations").get());
  await assertFails(inactiveStaffDb.collection("sponsorships").get());
  await assertFails(
    inactiveStaffDb.collection("donations").doc("don_inactive_try").set({
      donorUid: deactivatedEmpUid,
      donorName: "Blocked",
      donorPhone: "9876543210",
      purpose: "GAU_SEVA",
      amountInr: 500,
      paymentProviderRef: "REF",
      paymentStatus: "PENDING_VERIFICATION",
      receiptNumber: "RCPT-BLK",
      dateStr: "2026-11-20",
      recordedBy: deactivatedEmpUid,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("Donations & Go-Seva Sponsorship granular Staff CRUD enforcement (VIEW vs ADD/EDIT/DELETE)", async () => {
  const viewOnlyUid = "staff_don_viewer";
  const fullCrudUid = "staff_don_manager";

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffAccounts").doc(viewOnlyUid).set({
      uid: viewOnlyUid,
      employeeId: "EMP-201",
      email: "donviewer@gosala.org",
      name: "केवल देखने वाला सेवक",
      designation: "सहायक",
      monthlySalary: 9500,
      active: true,
      permissions: { donations: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("staffAccounts").doc(fullCrudUid).set({
      uid: fullCrudUid,
      employeeId: "EMP-202",
      email: "donmanager@gosala.org",
      name: "दान प्रबंधक",
      designation: "प्रबंधक",
      monthlySalary: 16000,
      active: true,
      permissions: { donations: ["VIEW", "ADD", "EDIT", "DELETE"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("donations").doc("don_seed_1").set({
      donorUid: PUBLIC_UID,
      donorName: "भक्त राम",
      donorPhone: "9876543210",
      donorCity: "पोरसा",
      purpose: "GAU_SEVA",
      amountInr: 2100,
      paymentProviderRef: "UPI-101",
      paymentStatus: "PENDING_VERIFICATION",
      receiptNumber: "RCPT-SEED-1",
      dateStr: "2026-11-20",
      recordedBy: PUBLIC_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("sponsorships").doc("sp_seed_1").set({
      applicantUid: PUBLIC_UID,
      applicantName: "भक्त श्याम",
      applicantPhone: "9876543211",
      applicantAddress: "मुरैना",
      cattleId: "cow_1",
      cattleName: "गौरी",
      animalCode: "JBGS-G-001",
      monthlySupportInr: 2100,
      durationMonths: 3,
      totalSupportInr: 6300,
      status: "PENDING",
      disclaimerAccepted: true,
      dateStr: "2026-11-20",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  const viewDb = testEnv.authenticatedContext(viewOnlyUid, {
    email: "donviewer@gosala.org",
    email_verified: false,
  }).firestore();
  const fullDb = testEnv.authenticatedContext(fullCrudUid, {
    email: "donmanager@gosala.org",
    email_verified: false,
  }).firestore();

  // VIEW-only staff CAN read donations & sponsorships, CANNOT add/edit/delete either!
  await assertSucceeds(viewDb.collection("donations").get());
  await assertSucceeds(viewDb.collection("sponsorships").get());
  await assertFails(
    viewDb.collection("donations").doc("don_view_fail").set({
      donorUid: viewOnlyUid,
      donorName: "View Staff",
      donorPhone: "9876543210",
      purpose: "GAU_SEVA",
      amountInr: 1100,
      paymentProviderRef: "UPI",
      paymentStatus: "PENDING_VERIFICATION",
      receiptNumber: "RCPT-FAIL",
      dateStr: "2026-11-20",
      recordedBy: viewOnlyUid,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    viewDb.collection("donations").doc("don_seed_1").update({
      paymentStatus: "VERIFIED_SUCCESS",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(viewDb.collection("donations").doc("don_seed_1").delete());
  await assertFails(
    viewDb.collection("sponsorships").doc("sp_view_fail").set({
      applicantUid: viewOnlyUid,
      applicantName: "View Staff",
      applicantPhone: "9876543210",
      applicantAddress: "मुरैना",
      cattleId: "cow_1",
      cattleName: "गौरी",
      animalCode: "JBGS-G-001",
      monthlySupportInr: 2100,
      durationMonths: 1,
      totalSupportInr: 2100,
      status: "PENDING",
      disclaimerAccepted: true,
      dateStr: "2026-11-20",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    viewDb.collection("sponsorships").doc("sp_seed_1").update({
      status: "APPROVED",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(viewDb.collection("sponsorships").doc("sp_seed_1").delete());

  // Full CRUD staff CAN add, edit, and delete donations & sponsorships, but STILL denied admin collections
  await assertSucceeds(
    fullDb.collection("donations").doc("don_mgr_new").set({
      donorUid: fullCrudUid,
      donorName: "दानदाता मोहन",
      donorPhone: "9876543215",
      donorCity: "ग्वालियर",
      purpose: "FODDER",
      amountInr: 5100,
      paymentProviderRef: "CASH-101",
      paymentStatus: "VERIFIED_SUCCESS",
      receiptNumber: "RCPT-MGR-1",
      dateStr: "2026-11-20",
      recordedBy: fullCrudUid,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    fullDb.collection("donations").doc("don_seed_1").update({
      paymentStatus: "VERIFIED_SUCCESS",
      amountInr: 2500,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(fullDb.collection("donations").doc("don_seed_1").delete());

  await assertSucceeds(
    fullDb.collection("sponsorships").doc("sp_mgr_new").set({
      applicantUid: fullCrudUid,
      applicantName: "सेवा परिवार",
      applicantPhone: "9876543222",
      applicantAddress: "पोरसा, मुरैना",
      cattleId: "cow_1",
      cattleName: "गौरी",
      animalCode: "JBGS-G-001",
      monthlySupportInr: 2100,
      durationMonths: 6,
      totalSupportInr: 12600,
      status: "PENDING",
      disclaimerAccepted: true,
      dateStr: "2026-11-20",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    fullDb.collection("sponsorships").doc("sp_seed_1").update({
      status: "APPROVED",
      certificateNumber: "JBGS-CERT-2026",
      reviewedBy: fullCrudUid,
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(fullDb.collection("sponsorships").doc("sp_seed_1").delete());

  // Non-admin staff still denied admin collections
  await assertFails(fullDb.collection("admins").get());
  await assertFails(fullDb.collection("staffAccounts").get());
  await assertFails(fullDb.collection("auditLogs").get());
});

test("Staff UID/Email authorization: own-email query allowed, cross-user denied, mixed-case invite claimed, and Admin pre-syncs existing user UID", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();

  const existingUserUid = "existing_user_uid_501";
  const existingEmail = "preuser@gosala.org";
  const mixedEmail = "MixedStaff@Gosala.org";
  const mixedStaffUid = "mixed_staff_uid_502";

  // Seed existing /users/{uid} (user signed up before Admin added them as staff)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(existingUserUid).set({
      userId: existingUserUid,
      displayName: "पूर्व पंजीकृत कर्मचारी",
      email: existingEmail,
      role: "PUBLIC",
      preferredLanguage: "hi",
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  // Admin creates invite AND immediately syncs /staffAccounts/{existingUserUid} + /users/{uid}.role
  await assertSucceeds(
    adminDb.collection("staffInvites").doc(existingEmail).set({
      employeeId: "EMP-501",
      email: existingEmail,
      name: "पूर्व पंजीकृत कर्मचारी",
      phone: "9876500501",
      designation: "गौ सेवक",
      monthlySalary: 12000,
      active: true,
      permissions: { cattle: ["VIEW", "ADD"], feed: ["VIEW", "ADD"] },
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(adminDb.collection("users").where("email", "==", existingEmail).get());
  await assertSucceeds(
    adminDb.collection("staffAccounts").doc(existingUserUid).set({
      uid: existingUserUid,
      employeeId: "EMP-501",
      email: existingEmail,
      name: "पूर्व पंजीकृत कर्मचारी",
      phone: "9876500501",
      designation: "गौ सेवक",
      monthlySalary: 12000,
      active: true,
      permissions: { cattle: ["VIEW", "ADD"], feed: ["VIEW", "ADD"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    adminDb.collection("users").doc(existingUserUid).update({
      role: "STAFF",
      updatedAt: pastDate(500),
    })
  );

  // Already-registered employee logs in -> immediately reads own /staffAccounts/{uid} and own email query, denied other users
  const preUserDb = testEnv.authenticatedContext(existingUserUid, {
    email: existingEmail,
    email_verified: false,
  }).firestore();
  await assertSucceeds(preUserDb.collection("staffAccounts").doc(existingUserUid).get());
  await assertSucceeds(preUserDb.collection("staffAccounts").where("email", "==", existingEmail).get());
  await assertSucceeds(preUserDb.collection("staffInvites").where("email", "==", existingEmail).get());
  await assertSucceeds(preUserDb.collection("cattle").get());
  await assertFails(preUserDb.collection("staffAccounts").where("email", "==", "someoneelse@gosala.org").get());
  await assertFails(preUserDb.collection("staffInvites").where("email", "==", "someoneelse@gosala.org").get());
  await assertFails(preUserDb.collection("admins").doc(ADMIN_UID).get());
  await assertFails(preUserDb.collection("financialTransactions").get());

  // Mixed-case invite key can be read and claimed cleanly by matching email account
  await assertSucceeds(
    adminDb.collection("staffInvites").doc(mixedEmail).set({
      employeeId: "EMP-502",
      email: mixedEmail,
      name: "केस सेवक",
      phone: "9876500502",
      designation: "सहायक",
      monthlySalary: 11000,
      active: true,
      permissions: { feed: ["VIEW"] },
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  const mixedDb = testEnv.authenticatedContext(mixedStaffUid, {
    email: mixedEmail,
    email_verified: false,
  }).firestore();
  await assertSucceeds(mixedDb.collection("staffInvites").doc(mixedEmail).get());
  await assertSucceeds(
    mixedDb.collection("staffAccounts").doc(mixedStaffUid).set({
      uid: mixedStaffUid,
      employeeId: "EMP-502",
      email: mixedEmail.toLowerCase(),
      name: "केस सेवक",
      phone: "9876500502",
      designation: "सहायक",
      monthlySalary: 11000,
      active: true,
      permissions: { feed: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(mixedDb.collection("feedAndOperations").get());
  await assertFails(mixedDb.collection("admins").get());
});

test("Phase 2 Product Orders & Cow Adoption Payment Verification: strict UID isolation, anti-self-verify, Staff denied, Admin full control", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();
  const otherPublicDb = testEnv.authenticatedContext(OTHER_PUBLIC_UID, {
    email: "other@example.com",
    email_verified: true,
  }).firestore();

  // Seed an active Staff account (must NOT gain Admin rights over productOrders)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("staffAccounts").doc(STAFF_UID).set({
      uid: STAFF_UID,
      employeeId: "EMP-900",
      email: STAFF_EMAIL,
      name: "सामान्य कर्मचारी",
      designation: "गौ सेवक",
      monthlySalary: 10000,
      active: true,
      permissions: { cattle: ["VIEW"], feed: ["VIEW", "ADD"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });
  const staffDb = testEnv.authenticatedContext(STAFF_UID, {
    email: STAFF_EMAIL,
    email_verified: true,
  }).firestore();

  const validOrder = {
    orderNumber: "JBGS-ORD-1001",
    buyerUid: PUBLIC_UID,
    buyerName: "रमेश भक्त",
    buyerPhone: "9876543210",
    buyerAddress: "ग्राम गोपी, पोरसा, मुरैना",
    productId: "compost",
    productName: "जैविक गोबर खाद",
    unitLabel: "50 किग्रा बैग",
    unitPriceInr: 350,
    quantity: 3,
    totalAmountInr: 1050,
    orderStatus: "NEW",
    paymentMode: "UPI",
    paymentStatus: "PAID",
    paymentRef: "UPI123456",
    notes: "सुबह डिलीवरी",
    adminNote: "",
    dateStr: "2026-11-20",
    reviewedBy: "",
    createdAt: pastDate(),
    updatedAt: pastDate(),
  };

  // 1. Public user CANNOT self-mark orderStatus=APPROVED or paymentStatus=VERIFIED or forge buyerUid
  await assertFails(
    publicDb.collection("productOrders").doc("ord_bad_status").set({
      ...validOrder,
      orderStatus: "APPROVED",
    })
  );
  await assertFails(
    publicDb.collection("productOrders").doc("ord_bad_pay").set({
      ...validOrder,
      paymentStatus: "VERIFIED",
    })
  );
  await assertFails(
    publicDb.collection("productOrders").doc("ord_bad_uid").set({
      ...validOrder,
      buyerUid: OTHER_PUBLIC_UID,
    })
  );

  // 2. Public user creates own valid product order
  await assertSucceeds(
    publicDb.collection("productOrders").doc("ord_pub_1").set(validOrder)
  );

  // 3. Public user reads own orders via buyerUid filter; denied unfiltered list and denied updating/deleting
  await assertSucceeds(
    publicDb.collection("productOrders").where("buyerUid", "==", PUBLIC_UID).get()
  );
  await assertFails(publicDb.collection("productOrders").get());
  await assertFails(
    publicDb.collection("productOrders").doc("ord_pub_1").update({
      orderStatus: "COMPLETED",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(publicDb.collection("productOrders").doc("ord_pub_1").delete());

  // 4. Another public user & non-admin Staff CANNOT read or modify Public User's product order
  await assertFails(otherPublicDb.collection("productOrders").doc("ord_pub_1").get());
  await assertFails(
    otherPublicDb.collection("productOrders").where("buyerUid", "==", PUBLIC_UID).get()
  );
  await assertFails(staffDb.collection("productOrders").get());
  await assertFails(staffDb.collection("productOrders").doc("ord_pub_1").get());
  await assertFails(
    staffDb.collection("productOrders").doc("ord_pub_1").update({
      orderStatus: "APPROVED",
      updatedAt: pastDate(500),
    })
  );

  // 5. Admin reads all product orders, updates lifecycle statuses, verifies payment
  await assertSucceeds(adminDb.collection("productOrders").get());
  for (const nextStatus of ["APPROVED", "READY", "DELIVERY", "COMPLETED", "CANCELLED"]) {
    await assertSucceeds(
      adminDb.collection("productOrders").doc("ord_pub_1").update({
        orderStatus: nextStatus,
        paymentStatus: "VERIFIED",
        adminNote: "भुगतान सत्यापित",
        reviewedBy: ADMIN_UID,
        updatedAt: pastDate(400),
      })
    );
  }

  // 6. Enhanced Cow Adoption (sponsorships) with message + paymentStatus anti-self-verify
  const adoptionPayload = {
    applicantUid: PUBLIC_UID,
    applicantName: "रमेश भक्त",
    applicantPhone: "9876543210",
    applicantAddress: "पोरसा, मुरैना",
    cattleId: "cow_1",
    cattleName: "नंदिनी",
    animalCode: "JBGS-G-001",
    monthlySupportInr: 2100,
    durationMonths: 3,
    totalSupportInr: 6300,
    status: "PENDING",
    message: "गौमाता के उत्तम स्वास्थ्य की कामना",
    paymentMode: "UPI",
    paymentStatus: "PAID",
    paymentRef: "UPI998877",
    certificateNumber: "",
    disclaimerAccepted: true,
    dateStr: "2026-11-20",
    reviewedBy: "",
    createdAt: pastDate(),
    updatedAt: pastDate(),
  };

  // Public cannot self-verify sponsorship payment
  await assertFails(
    publicDb.collection("sponsorships").doc("sp_bad_pay").set({
      ...adoptionPayload,
      paymentStatus: "VERIFIED",
    })
  );
  // Public valid adoption with message + PAID succeeds
  await assertSucceeds(
    publicDb.collection("sponsorships").doc("sp_phase2_1").set(adoptionPayload)
  );
  // Other public user cannot read this adoption
  await assertFails(otherPublicDb.collection("sponsorships").doc("sp_phase2_1").get());
  // Admin approves & verifies payment
  await assertSucceeds(
    adminDb.collection("sponsorships").doc("sp_phase2_1").update({
      status: "APPROVED",
      paymentStatus: "VERIFIED",
      certificateNumber: "JBGS-GAU-54321",
      reviewedBy: ADMIN_UID,
      updatedAt: pastDate(400),
    })
  );
});

test("Phase 3 Admin Full Management: /products Admin-only CRUD + Public read + Staff denied, plus Production & Farmer extensions", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffAccounts").doc(STAFF_UID).set({
      uid: STAFF_UID,
      employeeId: "EMP-950",
      email: STAFF_EMAIL,
      name: "सीमित कर्मचारी",
      designation: "गौ सेवक",
      monthlySalary: 11000,
      active: true,
      permissions: { cattle: ["VIEW"], production: ["VIEW"], farmers: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("staffAccounts").doc("inactive_staff_p3").set({
      uid: "inactive_staff_p3",
      employeeId: "EMP-951",
      email: "inactive_p3@gosala.org",
      name: "निष्क्रिय सेवक",
      designation: "गौ सेवक",
      monthlySalary: 10000,
      active: false,
      permissions: { production: ["VIEW", "ADD"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  const staffDb = testEnv.authenticatedContext(STAFF_UID, {
    email: STAFF_EMAIL,
    email_verified: true,
  }).firestore();
  const inactiveStaffDb = testEnv.authenticatedContext("inactive_staff_p3", {
    email: "inactive_p3@gosala.org",
    email_verified: true,
  }).firestore();

  const validProduct = {
    nameHi: "गोबर की लकड़ी (गौ-काष्ठ)",
    nameEn: "Cow Dung Logs (Gau-Kashtha)",
    categoryKey: "FUEL",
    descriptionHi: "वृक्ष संरक्षण हेतु शुद्ध देशी गाय के गोबर से बनी लकड़ी।",
    descriptionEn: "Eco-friendly cow dung logs.",
    priceInr: 180,
    stockQuantity: 40,
    unitLabel: "10 किग्रा बंडल",
    available: true,
    photoKey: "gaukashtha",
    photoUri: "",
    createdBy: ADMIN_UID,
    createdAt: pastDate(),
    updatedAt: pastDate(),
  };

  // 1. Public & Staff CANNOT create products
  await assertFails(
    publicDb.collection("products").doc("prod_pub_fail").set({
      ...validProduct,
      createdBy: PUBLIC_UID,
    })
  );
  await assertFails(
    staffDb.collection("products").doc("prod_staff_fail").set({
      ...validProduct,
      createdBy: STAFF_UID,
    })
  );

  // 2. Admin CAN create, update price/photo/stock, and delete products
  await assertSucceeds(adminDb.collection("products").doc("prod_1").set(validProduct));

  // 3. Signed-in Public & active Staff CAN read products; Unauth & inactive Staff CANNOT
  await assertSucceeds(publicDb.collection("products").get());
  await assertSucceeds(publicDb.collection("products").doc("prod_1").get());
  await assertSucceeds(staffDb.collection("products").get());
  await assertFails(unauthDb.collection("products").get());
  await assertFails(inactiveStaffDb.collection("products").get());

  // 4. Public & Staff CANNOT edit or delete products
  await assertFails(
    publicDb.collection("products").doc("prod_1").update({ priceInr: 1, updatedAt: pastDate(500) })
  );
  await assertFails(publicDb.collection("products").doc("prod_1").delete());
  await assertFails(
    staffDb.collection("products").doc("prod_1").update({ priceInr: 200, updatedAt: pastDate(500) })
  );
  await assertFails(staffDb.collection("products").doc("prod_1").delete());

  // 5. Admin edits price, stock, and photoUri, then deletes
  await assertSucceeds(
    adminDb.collection("products").doc("prod_1").update({
      priceInr: 210,
      stockQuantity: 55,
      photoUri: "content://media/picker/0/item/42",
      updatedAt: pastDate(400),
    })
  );
  await assertSucceeds(adminDb.collection("products").doc("prod_1").delete());

  // 6. Phase 3 Production Management: new product types (KANDE, GAU_KASHTHA, DHOOP, OTHER_PRODUCT) + staffNames
  await assertSucceeds(
    adminDb.collection("compostProduction").doc("prod_batch_kande").set({
      batchCode: "PRD-301",
      productType: "KANDE",
      quantity: 250,
      unit: "नग",
      rawDungKg: 420,
      productionCostInr: 600,
      packagingCostInr: 150,
      sellingPricePerUnitInr: 5,
      soldQuantity: 0,
      stockRemaining: 250,
      status: "READY_FOR_SALE",
      dateStr: "2026-11-20",
      staffNames: "रमेश सेवक, मोहन लाल",
      notes: "धूप में सुखाए गए शुद्ध कंडे",
      recordedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Public & view-only staff CANNOT add/edit/delete production
  await assertFails(
    publicDb.collection("compostProduction").doc("prod_batch_kande").update({
      quantity: 1,
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    staffDb.collection("compostProduction").doc("prod_batch_kande").delete()
  );

  // 7. Phase 3 Farmer Management: suppliesOffered + farmerTransactions.paymentStatus
  await assertSucceeds(
    adminDb.collection("farmers").doc("farmer_p3").set({
      name: "रामदयाल किसान",
      mobile: "9876543299",
      village: "पोरसा",
      address: "ग्राम गोपी",
      suppliesOffered: "गेहूँ का भूसा, हरा चारा (बरसीम), सूखा चारा",
      cooperationType: "BOTH",
      totalPurchasedInr: 0,
      totalSoldInr: 0,
      outstandingInr: 0,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    adminDb.collection("farmerTransactions").doc("ftx_p3").set({
      farmerId: "farmer_p3",
      farmerName: "रामदयाल किसान",
      village: "पोरसा",
      direction: "PURCHASE_FROM_FARMER",
      productName: "गेहूँ का भूसा",
      quantity: 12,
      unit: "QUINTAL",
      qualityGrade: "A-Grade",
      ratePerUnitInr: 550,
      totalAmountInr: 6600,
      paidAmountInr: 4000,
      outstandingInr: 2600,
      invoiceNumber: "FG-301",
      paymentStatus: "PARTIAL",
      dateStr: "2026-11-20",
      notes: "शेष राशि अगले सप्ताह",
      recordedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  // Public & view-only staff CANNOT modify farmer or farmerTransaction
  await assertFails(
    publicDb.collection("farmers").doc("farmer_p3").update({ village: "hack", updatedAt: pastDate(400) })
  );
  await assertFails(
    staffDb.collection("farmerTransactions").doc("ftx_p3").update({ paymentStatus: "PAID", updatedAt: pastDate(400) })
  );
  // Admin CAN update and delete farmerTransaction
  await assertSucceeds(
    adminDb.collection("farmerTransactions").doc("ftx_p3").update({
      paidAmountInr: 6600,
      outstandingInr: 0,
      paymentStatus: "PAID",
      updatedAt: pastDate(300),
    })
  );
  await assertSucceeds(adminDb.collection("farmerTransactions").doc("ftx_p3").delete());
  await assertSucceeds(adminDb.collection("farmers").doc("farmer_p3").delete());
  await assertSucceeds(adminDb.collection("compostProduction").doc("prod_batch_kande").delete());
});

test("Cow Cloud Photos & QR Read-Only Security: Admin writes permanent photo URL & cattlePhotos; Public read-only for approved cows only; Unauth denied", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  const longStorageUrl =
    "https://firebasestorage.googleapis.com/v0/b/gen-lang-client-0821187064.firebasestorage.app/o/cattle_photos%2Fcow_photo_1%2Fphoto_1795363316557.jpg?alt=media&token=9f8e7d6c-5b4a-3210-abcd-ef0123456789";

  // 1. Admin creates approved cow with Firebase Storage HTTPS URL, photoStoragePath, and vaccinationSummary
  await assertSucceeds(
    adminDb.collection("cattle").doc("cow_photo_1").set({
      animalCode: "JBGS-G-701",
      name: "सुरभि गौमाता",
      category: "COW",
      breed: "गिर",
      sex: "FEMALE",
      ageText: "4 वर्ष",
      birthDate: "2022-04-10",
      colour: "सफेद व भूरा",
      weightKg: 420.5,
      healthStatus: "HEALTHY",
      entryDate: "2024-02-15",
      shedNumber: "Shed-A2",
      identificationMarks: "माथे पर चाँद जैसा तिलक",
      qrPayload: "JBGS:CATTLE:cow_photo_1:JBGS-G-701",
      photoUri: longStorageUrl,
      photoStoragePath: "cattle_photos/cow_photo_1/photo_1795363316557.jpg",
      vaccinationSummary: "FMD खुरपका-मुंहपका टीका पूर्ण (आगामी: 2027-05-01)",
      notes: "अत्यंत शांत एवं दुधारू गौमाता",
      publicApproved: true,
      sponsorshipAvailable: true,
      monthlySupportInr: 2500,
      createdBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // 2. Admin writes cloud fallback photo in cattlePhotos for approved & private cows
  await assertSucceeds(
    adminDb.collection("cattlePhotos").doc("cow_photo_1").set({
      cattleId: "cow_photo_1",
      contentType: "image/jpeg",
      imageBase64: "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
      publicApproved: true,
      version: 1795363316557,
      uploadedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
  await assertSucceeds(
    adminDb.collection("cattlePhotos").doc("cow_photo_private").set({
      cattleId: "cow_photo_private",
      contentType: "image/jpeg",
      imageBase64: "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
      publicApproved: false,
      version: 1795363316558,
      uploadedBy: ADMIN_UID,
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // 3. Public User CAN read approved cow + approved cow photo, CANNOT read private cow photo or list all photos
  await assertSucceeds(publicDb.collection("cattle").doc("cow_photo_1").get());
  await assertSucceeds(publicDb.collection("cattlePhotos").doc("cow_photo_1").get());
  await assertFails(publicDb.collection("cattlePhotos").doc("cow_photo_private").get());
  await assertFails(publicDb.collection("cattlePhotos").get());

  // 4. Public User CANNOT create, update, or delete cattle or cattlePhotos
  await assertFails(
    publicDb.collection("cattle").doc("cow_photo_1").update({
      photoUri: "https://evil.example.com/hack.jpg",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    publicDb.collection("cattlePhotos").doc("cow_photo_1").update({
      imageBase64: "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(publicDb.collection("cattlePhotos").doc("cow_photo_1").delete());

  // 5. Unauthenticated user CANNOT read or write cattlePhotos
  await assertFails(unauthDb.collection("cattlePhotos").doc("cow_photo_1").get());

  // 6. Admin updates photo version and deletes cleanly
  await assertSucceeds(
    adminDb.collection("cattlePhotos").doc("cow_photo_1").update({
      version: 1795363399999,
      updatedAt: pastDate(200),
    })
  );
  await assertSucceeds(adminDb.collection("cattlePhotos").doc("cow_photo_1").delete());
  await assertSucceeds(adminDb.collection("cattlePhotos").doc("cow_photo_private").delete());
});

test("13. Notifications & Reminders RBAC: Admin full control, Public published-only read, Staff scoped read, self-only Read/Unread", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
  const publicDb = testEnv.authenticatedContext(PUBLIC_UID, {
    email: PUBLIC_EMAIL,
    email_verified: true,
  }).firestore();
  const staffUid = "staff_notif_uid";
  const staffFinanceUid = "staff_notif_finance";
  const staffDb = testEnv.authenticatedContext(staffUid, {
    email: "sevak_notif@gosala.org",
    email_verified: true,
  }).firestore();
  const staffFinanceDb = testEnv.authenticatedContext(staffFinanceUid, {
    email: "sevak_fin@gosala.org",
    email_verified: true,
  }).firestore();
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("staffAccounts").doc(staffUid).set({
      uid: staffUid,
      email: "sevak_notif@gosala.org",
      name: "सेवक चारा",
      designation: "गौ सेवक",
      monthlySalary: 10000,
      active: true,
      permissions: { feed: ["VIEW", "ADD"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
    await db.collection("staffAccounts").doc(staffFinanceUid).set({
      uid: staffFinanceUid,
      email: "sevak_fin@gosala.org",
      name: "सेवक वित्त",
      designation: "लेखाकार",
      monthlySalary: 15000,
      active: true,
      permissions: { finance: ["VIEW"] },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    });
  });

  // 1. Admin creates published Public notice, unpublished Draft, Staff Feed Reminder, Specific Staff private notice, unpublished Targeted Draft, and Admin-only notice
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_pub_1").set({
      targetRole: "PUBLIC",
      targetUid: "",
      category: "PRODUCTS",
      title: "नए जैविक खाद व कंडे उपलब्ध",
      message: "गौशाला में ताज़ा वर्मी कम्पोस्ट खाद और धूपबत्ती उपलब्ध है।",
      photoUrl: "preset:compost",
      published: true,
      publishDate: "2026-11-22",
      expiryDate: "2026-12-22",
      itemKind: "NOTICE",
      priority: "HIGH",
      assigneeName: "",
      reminderTime: "",
      recurrence: "ONCE",
      targetModule: "",
      createdBy: ADMIN_UID,
      dedupKey: "public_products_1",
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_draft_1").set({
      targetRole: "PUBLIC",
      targetUid: "",
      category: "ANNOUNCEMENTS",
      title: "अप्रकाशित ड्राफ्ट सूचना",
      message: "यह अभी प्रकाशित नहीं है।",
      published: false,
      publishDate: "2026-11-25",
      itemKind: "NOTICE",
      priority: "NORMAL",
      recurrence: "ONCE",
      targetModule: "",
      createdBy: ADMIN_UID,
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_staff_rem_1").set({
      targetRole: "STAFF",
      targetUid: "",
      category: "REMINDERS",
      title: "सुबह चारा वितरण रिमाइंडर",
      message: "सुबह 7 बजे सभी शेड में हरा चारा दें।",
      published: true,
      publishDate: "2026-11-22",
      itemKind: "REMINDER",
      priority: "URGENT",
      assigneeName: "चारा विभाग",
      reminderTime: "07:00",
      recurrence: "DAILY",
      targetModule: "feed",
      createdBy: ADMIN_UID,
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_staff_private_feed").set({
      targetRole: "STAFF",
      targetUid: staffUid,
      category: "REMINDERS",
      title: "निजी स्टाफ ड्यूटी निर्देश",
      message: "केवल सेवक चारा के लिए शेड-3 का निरीक्षण कार्य।",
      published: true,
      publishDate: "2026-11-22",
      itemKind: "REMINDER",
      priority: "HIGH",
      assigneeName: "सेवक चारा",
      reminderTime: "08:30",
      recurrence: "ONCE",
      targetModule: "",
      createdBy: ADMIN_UID,
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_staff_draft_private").set({
      targetRole: "STAFF",
      targetUid: staffUid,
      category: "ANNOUNCEMENTS",
      title: "अप्रकाशित व्यक्तिगत ड्राफ्ट",
      message: "प्रकाशन से पहले लक्षित स्टाफ को भी नहीं दिखना चाहिए।",
      published: false,
      publishDate: "2026-11-22",
      itemKind: "NOTICE",
      priority: "NORMAL",
      recurrence: "ONCE",
      targetModule: "",
      createdBy: ADMIN_UID,
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_admin_only").set({
      targetRole: "ADMIN",
      targetUid: "",
      category: "ANNOUNCEMENTS",
      title: "केवल प्रशासक ऑडिट नोट",
      message: "आंतरिक ऑडिट बैठक।",
      published: true,
      publishDate: "2026-11-22",
      itemKind: "NOTICE",
      priority: "NORMAL",
      recurrence: "ONCE",
      targetModule: "",
      createdBy: ADMIN_UID,
      readByUids: [],
      completedByUids: [],
      createdAt: pastDate(2000),
      updatedAt: pastDate(2000),
    })
  );

  // 2. Public user CAN read & list published broadcast PUBLIC notices; CANNOT read Drafts, STAFF reminders, Specific Staff private notices, or ADMIN notices
  await assertSucceeds(publicDb.collection("notifications").doc("notif_pub_1").get());
  await assertSucceeds(
    publicDb.collection("notifications")
      .where("targetRole", "==", "PUBLIC")
      .where("targetUid", "==", "")
      .where("published", "==", true)
      .get()
  );
  await assertFails(publicDb.collection("notifications").doc("notif_draft_1").get());
  await assertFails(publicDb.collection("notifications").doc("notif_staff_rem_1").get());
  await assertFails(publicDb.collection("notifications").doc("notif_staff_private_feed").get());
  await assertFails(publicDb.collection("notifications").doc("notif_admin_only").get());
  await assertFails(
    publicDb.collection("notifications")
      .where("targetRole", "==", "STAFF")
      .where("targetUid", "==", "")
      .where("published", "==", true)
      .get()
  );

  // 3. Correct Staff (feed) CAN read published feed reminder, own private notice, and published PUBLIC notice; CANNOT read Drafts (even own draft!) or ADMIN notice
  await assertSucceeds(staffDb.collection("notifications").doc("notif_staff_rem_1").get());
  await assertSucceeds(staffDb.collection("notifications").doc("notif_staff_private_feed").get());
  await assertSucceeds(
    staffDb.collection("notifications")
      .where("targetRole", "==", "STAFF")
      .where("targetUid", "==", "")
      .where("targetModule", "==", "feed")
      .where("published", "==", true)
      .get()
  );
  await assertSucceeds(
    staffDb.collection("notifications")
      .where("targetUid", "==", staffUid)
      .where("published", "==", true)
      .get()
  );
  await assertFails(staffDb.collection("notifications").doc("notif_draft_1").get());
  await assertFails(staffDb.collection("notifications").doc("notif_staff_draft_private").get());
  await assertFails(staffDb.collection("notifications").doc("notif_admin_only").get());

  // 4. Wrong Staff (finance-only) CANNOT read feed-module reminder or feed-staff's private notice!
  await assertFails(staffFinanceDb.collection("notifications").doc("notif_staff_rem_1").get());
  await assertFails(staffFinanceDb.collection("notifications").doc("notif_staff_private_feed").get());
  await assertFails(
    staffFinanceDb.collection("notifications")
      .where("targetRole", "==", "STAFF")
      .where("targetUid", "==", "")
      .where("targetModule", "==", "feed")
      .where("published", "==", true)
      .get()
  );

  // Staff CANNOT create PUBLIC notices
  await assertFails(
    staffDb.collection("notifications").doc("staff_illegal_pub").set({
      targetRole: "PUBLIC",
      targetUid: "",
      category: "ANNOUNCEMENTS",
      title: "अनधिकृत सार्वजनिक सूचना",
      message: "स्टाफ सार्वजनिक सूचना नहीं बना सकता",
      published: true,
      readByUids: [],
      createdAt: pastDate(1000),
    })
  );

  // 5. Read/Unread & Staff Action Status: self-only toggle allowed; tampering/editing/deleting denied
  await assertSucceeds(
    publicDb.collection("notifications").doc("notif_pub_1").update({
      readByUids: [PUBLIC_UID],
    })
  );
  await assertSucceeds(
    staffDb.collection("notifications").doc("notif_staff_rem_1").update({
      readByUids: [staffUid],
    })
  );
  await assertSucceeds(
    staffDb.collection("notifications").doc("notif_staff_rem_1").update({
      completedByUids: [staffUid],
    })
  );
  // Staff cannot tamper with another user's completedByUids and public cannot update completedByUids
  await assertFails(
    staffDb.collection("notifications").doc("notif_staff_rem_1").update({
      completedByUids: [staffUid, "other_uid"],
    })
  );
  await assertFails(
    publicDb.collection("notifications").doc("notif_pub_1").update({
      completedByUids: [PUBLIC_UID],
    })
  );
  await assertFails(
    publicDb.collection("notifications").doc("notif_pub_1").update({
      readByUids: [PUBLIC_UID, "some_other_uid"],
    })
  );
  await assertFails(
    publicDb.collection("notifications").doc("notif_pub_1").update({
      title: "हैक किया गया शीर्षक",
    })
  );
  await assertFails(publicDb.collection("notifications").doc("notif_pub_1").delete());

  // 6. Unauthenticated user CANNOT read or write notifications
  await assertFails(unauthDb.collection("notifications").doc("notif_pub_1").get());

  // 7. Admin Publish flow: publishing draft makes it immediately readable by Public User
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_draft_1").update({
      published: true,
      title: "प्रकाशित घोषणा",
      updatedAt: pastDate(500),
    })
  );
  await assertSucceeds(publicDb.collection("notifications").doc("notif_draft_1").get());

  // Admin cleanup delete
  await assertSucceeds(adminDb.collection("notifications").doc("notif_draft_1").delete());
  await assertSucceeds(adminDb.collection("notifications").doc("notif_pub_1").delete());
  await assertSucceeds(adminDb.collection("notifications").doc("notif_staff_rem_1").delete());
  await assertSucceeds(adminDb.collection("notifications").doc("notif_staff_private_feed").delete());
  await assertSucceeds(adminDb.collection("notifications").doc("notif_staff_draft_private").delete());
  await assertSucceeds(adminDb.collection("notifications").doc("notif_admin_only").delete());
});







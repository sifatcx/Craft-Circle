const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_maker_1";
const BOB_UID = "bob_maker_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function futureDate(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

function validUserPayload(uid) {
  return {
    userId: uid,
    displayName: "Alice Woodworker",
    email: "alice@example.com",
    photoUrl: "https://example.com/alice.png",
    bio: "Sharing creative projects & DIY tutorials on CraftCircle.",
    xHandle: "alicecrafts",
    specialties: ["Woodworking", "DIY Tutorials"],
    createdAt: pastDate(5000),
    updatedAt: pastDate(5000),
  };
}

function validProjectPayload(uid, overrides = {}) {
  return {
    authorId: uid,
    authorName: "Alice Woodworker",
    authorPhotoUrl: "",
    authorXHandle: "alicecrafts",
    title: "Hand-Dovetail Walnut Valet Tray",
    summary: "A beginner-friendly guide to cutting crisp hand-cut dovetails in black walnut.",
    category: "Woodworking",
    postType: "TUTORIAL",
    difficulty: "Beginner",
    estimatedTime: "3 hours",
    materials: ["Black walnut board", "Dovetail saw", "Chisel"],
    steps: [
      {
        stepNumber: 1,
        title: "Mark tails",
        instruction: "Use a 1:7 dovetail marker to scribe lines.",
        tip: "Score deeply with a marking knife.",
      },
    ],
    coverPreset: "woodcraft",
    visibility: "public",
    status: "published",
    likesCount: 0,
    commentsCount: 0,
    createdAt: pastDate(5000),
    updatedAt: pastDate(5000),
    ...overrides,
  };
}

function validEventPayload(uid, overrides = {}) {
  return {
    hostId: uid,
    hostName: "Alice Woodworker",
    hostPhotoUrl: "",
    hostXHandle: "alicecrafts",
    title: "Live Walnut Box Joinery Night",
    description: "Bring a chisel and follow along as we cut mitered splines together.",
    category: "Woodworking",
    format: "Live Stream",
    locationOrLink: "https://x.com/i/spaces/craftcircle",
    materialsNeeded: "Walnut scrap, saw, wood glue",
    startTimeMillis: Date.now() + 86400000,
    durationMinutes: 60,
    maxAttendees: 25,
    attendeeCount: 0,
    xHashtag: "#CraftCircleBuild",
    visibility: "public",
    status: "upcoming",
    createdAt: pastDate(5000),
    updatedAt: pastDate(5000),
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

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---
test("Unauthenticated users cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("projects").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("events").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("community_updates").where("visibility", "==", "public").get());
  await assertFails(unauth.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertFails(unauth.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID)));
});

// --- 2. USER PROFILE PII ISOLATION & UPDATE GUARD ---
test("Users can manage own profile but cannot read or modify another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(validUserPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice Artisan",
      bio: "Updated workshop bio",
      xHandle: "alice_diy",
      specialties: ["Woodworking", "Ceramics"],
      updatedAt: pastDate(1000),
    })
  );

  // Bob cannot read Alice's profile (PII email isolation) or write to it
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ bio: "Hijacked" }));

  // Alice cannot inject ghost field or future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Future tamper",
      updatedAt: futureDate(),
    })
  );
});

// --- 3. PROJECTS CRUD, VISIBILITY & QUERY ALIGNMENT ---
test("Projects enforce author ownership, schema validation, and query alignment", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("projects").doc("proj_pub").set(validProjectPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("projects").doc("proj_priv").set(validProjectPayload(ALICE_UID, { visibility: "private" }))
  );

  // Reject @DocumentId `id` field inside payload
  await assertFails(
    aliceDb.collection("projects").doc("proj_bad_id").set({
      id: "proj_bad_id",
      ...validProjectPayload(ALICE_UID),
    })
  );

  // Reject spoofed likesCount != 0 on create
  await assertFails(
    aliceDb.collection("projects").doc("proj_bad_likes").set(validProjectPayload(ALICE_UID, { likesCount: 50 }))
  );

  // Filtered Kotlin queries succeed; unfiltered list query fails
  await assertSucceeds(bobDb.collection("projects").where("visibility", "==", "public").get());
  await assertSucceeds(aliceDb.collection("projects").where("authorId", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("projects").get());

  // Bob cannot read Alice's private draft or edit Alice's public project
  await assertFails(bobDb.collection("projects").doc("proj_priv").get());
  await assertFails(bobDb.collection("projects").doc("proj_pub").update({ title: "Stolen Title", updatedAt: pastDate() }));
  await assertFails(bobDb.collection("projects").doc("proj_pub").delete());
});

// --- 4. ATOMIC LIKES & COMMENTS BATCHES + BOOKMARKS ---
test("Atomic likes, comments, and bookmarks enforce relational integrity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("projects").doc("proj_1").set(validProjectPayload(ALICE_UID, { likes: 0 })));

  // Standalone spoofing of likes/likesCount without like document MUST fail
  await assertFails(bobDb.collection("projects").doc("proj_1").update({ likesCount: 1, likes: 1 }));
  await assertFails(bobDb.collection("projects").doc("proj_1").update({ likes: 1 }));

  // Atomic batch like (+1) updates both likes and likesCount cleanly
  const likeBatch = bobDb.batch();
  likeBatch.set(bobDb.collection("projects").doc("proj_1").collection("likes").doc(BOB_UID), {
    userId: BOB_UID,
    createdAt: pastDate(1000),
  });
  likeBatch.update(bobDb.collection("projects").doc("proj_1"), { likesCount: 1, likes: 1 });
  await assertSucceeds(likeBatch.commit());

  // Atomic batch unlike (-1) updates both likes and likesCount cleanly
  const unlikeBatch = bobDb.batch();
  unlikeBatch.delete(bobDb.collection("projects").doc("proj_1").collection("likes").doc(BOB_UID));
  unlikeBatch.update(bobDb.collection("projects").doc("proj_1"), { likesCount: 0, likes: 0 });
  await assertSucceeds(unlikeBatch.commit());

  // Atomic comment batch (+1 commentsCount + lastCommentId) succeeds
  const commentBatch = bobDb.batch();
  commentBatch.set(bobDb.collection("projects").doc("proj_1").collection("comments").doc("cmt_1"), {
    projectId: "proj_1",
    projectVisibility: "public",
    projectAuthorId: ALICE_UID,
    authorId: BOB_UID,
    authorName: "Bob Potter",
    authorPhotoUrl: "",
    text: "Love the chamfered edges on this walnut box!",
    createdAt: pastDate(1000),
  });
  commentBatch.update(bobDb.collection("projects").doc("proj_1"), {
    commentsCount: 1,
    lastCommentId: "cmt_1",
  });
  await assertSucceeds(commentBatch.commit());

  // Matched comment list query succeeds
  await assertSucceeds(
    bobDb.collection("projects").doc("proj_1").collection("comments").where("projectVisibility", "==", "public").get()
  );

  // Blank text or spoofed authorId comment MUST fail
  await assertFails(
    bobDb.collection("projects").doc("proj_1").collection("comments").doc("cmt_bad").set({
      projectId: "proj_1",
      projectVisibility: "public",
      projectAuthorId: ALICE_UID,
      authorId: ALICE_UID,
      authorName: "Spoofed",
      authorPhotoUrl: "",
      text: "Impersonating Alice",
      createdAt: pastDate(1000),
    })
  );

  // Alice cannot delete Bob's comment
  const aliceDeleteBobComment = aliceDb.batch();
  aliceDeleteBobComment.delete(aliceDb.collection("projects").doc("proj_1").collection("comments").doc("cmt_1"));
  aliceDeleteBobComment.update(aliceDb.collection("projects").doc("proj_1"), {
    commentsCount: 0,
    lastDeletedCommentId: "cmt_1",
  });
  await assertFails(aliceDeleteBobComment.commit());

  // Bob can atomically delete his own comment and decrement commentsCount
  const bobDeleteComment = bobDb.batch();
  bobDeleteComment.delete(bobDb.collection("projects").doc("proj_1").collection("comments").doc("cmt_1"));
  bobDeleteComment.update(bobDb.collection("projects").doc("proj_1"), {
    commentsCount: 0,
    lastDeletedCommentId: "cmt_1",
  });
  await assertSucceeds(bobDeleteComment.commit());

  // User-scoped bookmark succeeds for Bob on Bob's path, fails on Alice's path
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("bookmarks").doc("proj_1").set({
      projectId: "proj_1",
      userId: BOB_UID,
      projectTitle: "Hand-Dovetail Walnut Valet Tray",
      projectCategory: "Woodworking",
      authorName: "Alice Woodworker",
      coverPreset: "woodcraft",
      createdAt: pastDate(1000),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bookmarks").doc("proj_1").set({
      projectId: "proj_1",
      userId: ALICE_UID,
      projectTitle: "Hijack",
      projectCategory: "Woodworking",
      authorName: "Bob",
      coverPreset: "woodcraft",
      createdAt: pastDate(1000),
    })
  );
});

// --- 5. COMMUNITY UPDATES ---
test("Community updates enforce authorId and public list filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("community_updates").doc("upd_1").set({
      authorId: ALICE_UID,
      authorName: "Alice Woodworker",
      authorPhotoUrl: "",
      authorXHandle: "alicecrafts",
      category: "Woodworking",
      message: "Just finished oiling the walnut tray!",
      xIntentShared: true,
      linkedTweetUrl: "https://x.com/alicecrafts/status/12345",
      visibility: "public",
      createdAt: pastDate(1000),
    })
  );

  await assertSucceeds(bobDb.collection("community_updates").where("visibility", "==", "public").get());
  await assertFails(bobDb.collection("community_updates").get());
  await assertFails(bobDb.collection("community_updates").doc("upd_1").delete());
});

// --- 6. SCHEDULED EVENTS, ATOMIC RSVPS & TERMINAL LOCKING ---
test("Maker events enforce atomic RSVPs, capacity, and terminal status lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("events").doc("evt_1").set(validEventPayload(ALICE_UID, { maxAttendees: 1 })));

  // Atomic RSVP batch succeeds for Bob
  const rsvpBatch = bobDb.batch();
  rsvpBatch.set(bobDb.collection("events").doc("evt_1").collection("rsvps").doc(BOB_UID), {
    eventId: "evt_1",
    userId: BOB_UID,
    userName: "Bob Potter",
    createdAt: pastDate(1000),
  });
  rsvpBatch.update(bobDb.collection("events").doc("evt_1"), { attendeeCount: 1 });
  await assertSucceeds(rsvpBatch.commit());

  // Capacity exceeded: Alice cannot RSVP when attendeeCount == maxAttendees (1)
  const overCapBatch = aliceDb.batch();
  overCapBatch.set(aliceDb.collection("events").doc("evt_1").collection("rsvps").doc(ALICE_UID), {
    eventId: "evt_1",
    userId: ALICE_UID,
    userName: "Alice",
    createdAt: pastDate(500),
  });
  overCapBatch.update(aliceDb.collection("events").doc("evt_1"), { attendeeCount: 2 });
  await assertFails(overCapBatch.commit());

  // Terminal locking: once host marks event completed, further edits fail
  await assertSucceeds(
    aliceDb.collection("events").doc("evt_1").update({
      status: "completed",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("events").doc("evt_1").update({
      title: "Post-completion tamper",
      updatedAt: pastDate(100),
    })
  );
});

// --- 7. MAKER DIRECTORY & DIRECT MESSAGING ---
test("Maker directory hides PII and direct messages are strictly participant-isolated", async () => {
  const CHARLIE_UID = "charlie_maker_3";
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  // Maker public directory allows create without email, rejects ghost email field
  await assertSucceeds(
    aliceDb.collection("makers").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Woodworker",
      photoUrl: "",
      bio: "Fine walnut joinery",
      specialties: ["Woodworking"],
      updatedAt: pastDate(2000),
    })
  );
  await assertFails(
    bobDb.collection("makers").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob Potter",
      email: "bob@secret.com",
      photoUrl: "",
      bio: "Wheel-thrown stoneware",
      specialties: ["Ceramics"],
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(bobDb.collection("makers").get());

  // Create deterministic 1-on-1 conversation between Alice and Bob
  const convoId = [ALICE_UID, BOB_UID].sort().join("_");
  const convoRef = aliceDb.collection("conversations").doc(convoId);
  const created = pastDate(3000);
  await assertSucceeds(
    convoRef.set({
      participantIds: [ALICE_UID, BOB_UID],
      participantNames: { [ALICE_UID]: "Alice Woodworker", [BOB_UID]: "Bob Potter" },
      participantPhotos: { [ALICE_UID]: "", [BOB_UID]: "" },
      lastMessageText: "",
      lastSenderId: "",
      lastMessageAt: created,
      createdAt: created,
    })
  );

  // Atomic batch send message + update conversation preview succeeds for Bob
  const msgBatch = bobDb.batch();
  const msgRef = bobDb.collection("conversations").doc(convoId).collection("messages").doc("msg_1");
  msgBatch.set(msgRef, {
    conversationId: convoId,
    participantIds: [ALICE_UID, BOB_UID],
    senderId: BOB_UID,
    senderName: "Bob Potter",
    senderPhotoUrl: "",
    text: "Hey Alice, what finish did you use on the walnut tray?",
    createdAt: pastDate(1000),
  });
  msgBatch.update(bobDb.collection("conversations").doc(convoId), {
    lastMessageText: "Hey Alice, what finish did you use on the walnut tray?",
    lastSenderId: BOB_UID,
    lastMessageAt: pastDate(1000),
  });
  await assertSucceeds(msgBatch.commit());

  // Scoped Kotlin queries succeed for participants
  await assertSucceeds(
    aliceDb.collection("conversations").where("participantIds", "array-contains", ALICE_UID).get()
  );
  await assertSucceeds(
    aliceDb
      .collection("conversations")
      .doc(convoId)
      .collection("messages")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );

  // Outsider Charlie cannot read conversation, list messages unfiltered or for Alice, or spoof a message
  await assertFails(charlieDb.collection("conversations").doc(convoId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convoId).collection("messages").get()
  );
  await assertFails(
    charlieDb
      .collection("conversations")
      .doc(convoId)
      .collection("messages")
      .where("participantIds", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("conversations").doc(convoId).collection("messages").doc("msg_bad").set({
      conversationId: convoId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: CHARLIE_UID,
      senderName: "Charlie",
      senderPhotoUrl: "",
      text: "Intruder message",
      createdAt: pastDate(500),
    })
  );

  // Bob cannot spoof senderId as Alice
  await assertFails(
    bobDb.collection("conversations").doc(convoId).collection("messages").doc("msg_spoof").set({
      conversationId: convoId,
      participantIds: [ALICE_UID, BOB_UID],
      senderId: ALICE_UID,
      senderName: "Alice",
      senderPhotoUrl: "",
      text: "Impersonated",
      createdAt: pastDate(500),
    })
  );
});

// --- 8. CREATOR FOLLOW / UNFOLLOW SUBSCRIPTIONS ---
test("Creator follow and unfollow enforce atomic batch parity, no self-follow, and user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Atomic batch follow succeeds for Bob -> Alice
  const followBatch = bobDb.batch();
  followBatch.set(bobDb.collection("users").doc(BOB_UID).collection("following").doc(ALICE_UID), {
    userId: BOB_UID,
    targetUserId: ALICE_UID,
    targetName: "Alice Woodworker",
    targetPhotoUrl: "",
    createdAt: pastDate(1000),
  });
  followBatch.set(bobDb.collection("makers").doc(ALICE_UID).collection("followers").doc(BOB_UID), {
    followerId: BOB_UID,
    targetUserId: ALICE_UID,
    createdAt: pastDate(1000),
  });
  await assertSucceeds(followBatch.commit());

  // Bob can list his own following list and anyone signed in can read Alice's followers
  await assertSucceeds(bobDb.collection("users").doc(BOB_UID).collection("following").get());
  await assertSucceeds(aliceDb.collection("makers").doc(ALICE_UID).collection("followers").get());

  // Alice cannot read Bob's private following subcollection
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("following").get());

  // Self-follow must be denied
  const selfFollowBatch = aliceDb.batch();
  selfFollowBatch.set(aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(ALICE_UID), {
    userId: ALICE_UID,
    targetUserId: ALICE_UID,
    targetName: "Alice Woodworker",
    targetPhotoUrl: "",
    createdAt: pastDate(1000),
  });
  selfFollowBatch.set(aliceDb.collection("makers").doc(ALICE_UID).collection("followers").doc(ALICE_UID), {
    followerId: ALICE_UID,
    targetUserId: ALICE_UID,
    createdAt: pastDate(1000),
  });
  await assertFails(selfFollowBatch.commit());

  // Standalone write to following without paired follower doc must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("following").doc(BOB_UID).set({
      userId: ALICE_UID,
      targetUserId: BOB_UID,
      targetName: "Bob Potter",
      targetPhotoUrl: "",
      createdAt: pastDate(1000),
    })
  );

  // Atomic unfollow batch succeeds for Bob -> Alice
  const unfollowBatch = bobDb.batch();
  unfollowBatch.delete(bobDb.collection("users").doc(BOB_UID).collection("following").doc(ALICE_UID));
  unfollowBatch.delete(bobDb.collection("makers").doc(ALICE_UID).collection("followers").doc(BOB_UID));
  await assertSucceeds(unfollowBatch.commit());
});



const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp({
  databaseURL:
    "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app",
});

// Mora odgovarati NotificationChannels.java na klijentu.
const CHANNEL_IDS = {
  CHAT: "ch_chat",
  RANKING: "ch_ranking",
  REWARD: "ch_reward",
  OTHER: "ch_other",
};

/**
 * Okida se na svaki novi zapis u notifications/{uid}/{notifId} (Realtime Database).
 * Klijent (Android app) već piše tu kad treba da obavesti drugog korisnika
 * (npr. poziv na prijateljsku partiju). Ova funkcija uzima fcmToken iz
 * users/{uid} (Firestore) i šalje pravi FCM push, koji stiže i kad app
 * uopšte nije pokrenuta na ciljnom uređaju.
 *
 * Šalje I "notification" I "data" payload zajedno: kad je app ugašena ili
 * u pozadini, sistem SAM prikazuje notifikaciju (ne zahteva da budi naš
 * kod), što je jedini pouzdan način da push stigne dok je app potpuno
 * ugašena. Kad je app u prvom planu, onMessageReceived i dalje hvata
 * poruku (Android tad ne prikazuje automatski) i upisuje je u istoriju.
 */
exports.sendNotificationPush = functions
  .region("europe-west1")
  .database.ref("/notifications/{uid}/{notifId}")
  .onCreate(async (snapshot, context) => {
    const uid = context.params.uid;
    const data = snapshot.val();
    if (!data) return null;

    const userDoc = await admin.firestore().collection("users").doc(uid).get();
    const token = userDoc.exists ? userDoc.get("fcmToken") : null;
    if (!token) {
      console.log(`Nema fcmToken za uid=${uid}, preskačem push.`);
      return null;
    }

    const channel = data.channel || "OTHER";
    const title   = data.title || "Slagalica Vrtloga";
    const body    = data.body || "";

    const message = {
      token,
      notification: { title, body },
      data: {
        id: context.params.notifId,
        channel,
        title,
        body,
        action: data.action || "",
      },
      android: {
        priority: "high",
        notification: {
          channelId: CHANNEL_IDS[channel] || CHANNEL_IDS.OTHER,
        },
      },
    };

    try {
      await admin.messaging().send(message);
    } catch (err) {
      console.error(`Slanje push-a uid=${uid} nije uspelo:`, err);
    }
    return null;
  });

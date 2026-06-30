package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.LeagueUtil;
import com.example.slagalicavpl.model.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class UserRepository {

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    public interface ProfileCallback {
        void onLoaded(User user);
        void onError(String message);
    }

    public interface StringCallback {
        void onResult(String value);
        void onError(String message);
    }

    public interface DailyTokenCallback {
        void onClaimed(int tokensGiven);
        void onAlreadyClaimed();
    }

    /** Callback koji se poziva kada se liga promijeni (spec 6d/6e/6f). */
    public interface LeagueChangeCallback {
        void onChanged(int oldLeague, int newLeague);
    }

    public interface RegionRankingCallback {
        void onLoaded(List<RegionEntry> ranking);
        void onError(String message);
    }

    public interface RegionDotsCallback {
        void onLoaded(List<String> uids);
        void onError(String message);
    }

    public interface RegionStatsCallback {
        void onLoaded(int registered, int active, int firstPlaces, int secondPlaces, int thirdPlaces);
        void onError(String message);
    }

    public static class RegionEntry {
        public final String regionId;
        public final int    cycleStars;
        public RegionEntry(String regionId, int cycleStars) {
            this.regionId   = regionId;
            this.cycleStars = cycleStars;
        }
    }

    private static UserRepository instance;
    private final FirebaseAuth      auth;
    private final FirebaseFirestore db;

    private UserRepository() {
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
    }

    public static UserRepository getInstance() {
        if (instance == null) instance = new UserRepository();
        return instance;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    // ── Registracija ─────────────────────────────────────────────────────────

    public void createAuthUser(String email, String password, Callback cb) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener(r -> cb.onSuccess())
            .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void sendVerificationEmail(Callback cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { cb.onError("Korisnik nije prijavljen"); return; }
        u.sendEmailVerification()
         .addOnSuccessListener(r -> cb.onSuccess())
         .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void saveUserProfile(User user, Callback cb) {
        db.collection("users").document(user.uid)
          .set(user)
          .addOnSuccessListener(v -> cb.onSuccess())
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Prijava ───────────────────────────────────────────────────────────────

    public void signIn(String email, String password, Callback cb) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener(r -> cb.onSuccess())
            .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void getEmailByUsername(String username, StringCallback cb) {
        db.collection("users")
          .whereEqualTo("username", username)
          .limit(1)
          .get()
          .addOnSuccessListener((QuerySnapshot qs) -> {
              if (!qs.isEmpty()) {
                  String email = qs.getDocuments().get(0).getString("email");
                  cb.onResult(email);
              } else {
                  cb.onError("Korisničko ime nije pronađeno");
              }
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void logout() {
        auth.signOut();
    }

    // ── Profil ────────────────────────────────────────────────────────────────

    public void loadProfile(String uid, ProfileCallback cb) {
        db.collection("users").document(uid).get()
          .addOnSuccessListener(doc -> {
              if (doc.exists()) cb.onLoaded(doc.toObject(User.class));
              else              cb.onError("Korisnik nije pronađen");
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Promena lozinke ───────────────────────────────────────────────────────

    public void reauthenticate(String email, String oldPassword, Callback cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { cb.onError("Nije prijavljen"); return; }
        AuthCredential cred = EmailAuthProvider.getCredential(email, oldPassword);
        u.reauthenticate(cred)
         .addOnSuccessListener(r -> cb.onSuccess())
         .addOnFailureListener(e -> cb.onError("Pogrešna stara lozinka"));
    }

    public void updatePassword(String newPassword, Callback cb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) { cb.onError("Nije prijavljen"); return; }
        u.updatePassword(newPassword)
         .addOnSuccessListener(r -> cb.onSuccess())
         .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Statistika ────────────────────────────────────────────────────────────

    // Postojeći pozivi (bez callback-a) nastavljaju raditi
    public void incrementStats(String uid, boolean won, int myScore) {
        incrementStats(uid, won, myScore, null);
    }

    public void incrementStats(String uid, boolean won, int myScore, LeagueChangeCallback cb) {
        DocumentReference ref = db.collection("users").document(uid);
        int starsDelta = won ? 10 + (myScore / 40) : -10 + (myScore / 40);
        String currentCycle = currentCycleId();

        db.<long[]>runTransaction(tx -> {
            com.google.firebase.firestore.DocumentSnapshot snap = tx.get(ref);
            long played = safe(snap.getLong("gamesPlayed"));
            long wins   = safe(snap.getLong("gamesWon"));
            long stars  = safe(snap.getLong("stars"));
            long tokens = safe(snap.getLong("tokens"));

            tx.update(ref, "gamesPlayed", played + 1);
            if (won) tx.update(ref, "gamesWon", wins + 1);

            long newStars = Math.max(0, stars + starsDelta);
            tx.update(ref, "stars", newStars);

            long newTokens = tokens + (newStars / 50 - stars / 50);
            if (newTokens != tokens) tx.update(ref, "tokens", Math.max(0, newTokens));

            String storedCycle = snap.getString("cycleId");
            int starsEarned = Math.max(0, starsDelta);
            if (currentCycle.equals(storedCycle)) {
                long cs = safe(snap.getLong("cycleStars"));
                tx.update(ref, "cycleStars", Math.max(0, cs + starsEarned));
            } else {
                tx.update(ref, "cycleId",    currentCycle);
                tx.update(ref, "cycleStars", (long) starsEarned);
            }
            return new long[]{ stars, newStars };
        }).addOnSuccessListener(result -> {
            if (cb == null || result == null) return;
            int oldL = LeagueUtil.getLeague((int) result[0]);
            int newL = LeagueUtil.getLeague((int) result[1]);
            if (oldL != newL) cb.onChanged(oldL, newL);
        });
    }

    public void updateLastSeen(String uid) {
        db.collection("users").document(uid)
          .update("lastSeen", System.currentTimeMillis());
    }

    // ── Region ranking ────────────────────────────────────────────────────────

    public void loadRegionRanking(RegionRankingCallback cb) {
        String cycle = currentCycleId();
        db.collection("users").get()
          .addOnSuccessListener(qs -> {
              Map<String, Integer> sums = new HashMap<>();
              for (QueryDocumentSnapshot doc : qs) {
                  String region = doc.getString("region");
                  if (region == null || region.isEmpty()) continue;
                  // If user's cycleId matches current, take cycleStars; else 0
                  String userCycle = doc.getString("cycleId");
                  int cs = cycle.equals(userCycle)
                          ? (int) safe(doc.getLong("cycleStars")) : 0;
                  sums.put(region, sums.getOrDefault(region, 0) + cs);
              }
              List<RegionEntry> list = new ArrayList<>();
              for (Map.Entry<String, Integer> e : sums.entrySet())
                  list.add(new RegionEntry(e.getKey(), e.getValue()));
              list.sort((a, b) -> b.cycleStars - a.cycleStars);
              cb.onLoaded(list);
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void loadUserDotsForRegion(String regionId, RegionDotsCallback cb) {
        db.collection("users")
          .whereEqualTo("region", regionId)
          .get()
          .addOnSuccessListener(qs -> {
              List<String> uids = new ArrayList<>();
              for (QueryDocumentSnapshot doc : qs) uids.add(doc.getId());
              cb.onLoaded(uids);
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void loadRegionStats(String regionId, RegionStatsCallback cb) {
        long activeThreshold = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
        db.collection("users")
          .whereEqualTo("region", regionId)
          .get()
          .addOnSuccessListener(qs -> {
              final int registered = qs.size();
              int activeCount = 0;
              for (QueryDocumentSnapshot doc : qs) {
                  Long ls = doc.getLong("lastSeen");
                  if (ls != null && ls >= activeThreshold) activeCount++;
              }
              final int active = activeCount;
              db.collection("region_meta").document(regionId).get()
                .addOnSuccessListener(meta -> {
                    int first  = meta.exists() ? (int) safe(meta.getLong("firstPlaces"))  : 0;
                    int second = meta.exists() ? (int) safe(meta.getLong("secondPlaces")) : 0;
                    int third  = meta.exists() ? (int) safe(meta.getLong("thirdPlaces"))  : 0;
                    cb.onLoaded(registered, active, first, second, third);
                })
                .addOnFailureListener(e -> cb.onLoaded(registered, active, 0, 0, 0));
          })
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void saveLastCycleResults(String gold, String silver, String bronze, String cycleId) {
        Map<String, Object> data = new HashMap<>();
        data.put("cycleId",      cycleId);
        data.put("goldRegion",   gold);
        data.put("silverRegion", silver);
        data.put("bronzeRegion", bronze);
        db.collection("app_state").document("last_cycle").set(data);

        // Increment place counters for each region
        if (gold   != null) incrementPlaces(gold,   "firstPlaces");
        if (silver != null) incrementPlaces(silver, "secondPlaces");
        if (bronze != null) incrementPlaces(bronze, "thirdPlaces");
    }

    private void incrementPlaces(String regionId, String field) {
        DocumentReference ref = db.collection("region_meta").document(regionId);
        db.runTransaction(tx -> {
            long v = safe(tx.get(ref).getLong(field));
            Map<String, Object> update = new HashMap<>();
            update.put(field, v + 1);
            tx.set(ref, update, com.google.firebase.firestore.SetOptions.merge());
            return null;
        });
    }

    public void loadLastCycleResults(RegionBorderCallback cb) {
        db.collection("app_state").document("last_cycle").get()
          .addOnSuccessListener(doc -> {
              if (!doc.exists()) { cb.onLoaded(null, null, null, null); return; }
              cb.onLoaded(doc.getString("cycleId"),
                          doc.getString("goldRegion"),
                          doc.getString("silverRegion"),
                          doc.getString("bronzeRegion"));
          })
          .addOnFailureListener(e -> cb.onLoaded(null, null, null, null));
    }

    public interface RegionBorderCallback {
        void onLoaded(String cycleId, String gold, String silver, String bronze);
    }

    public static String currentCycleId() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(new Date());
    }

    private static String previousCycleId() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.MONTH, -1);
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(cal.getTime());
    }

    public void incrementKzz(String uid, boolean correct) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long total = safe(tx.get(ref).getLong("kzzTotal"));
            long corr  = safe(tx.get(ref).getLong("kzzCorrect"));
            tx.update(ref, "kzzTotal", total + 1);
            if (correct) tx.update(ref, "kzzCorrect", corr + 1);
            return null;
        });
    }

    /** Oduzima 1 token. Poziva onError ako nema tokena. */
    public void deductToken(String uid, Callback cb) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long tokens = safe(tx.get(ref).getLong("tokens"));
            if (tokens <= 0) throw new RuntimeException("no_tokens");
            tx.update(ref, "tokens", tokens - 1);
            return null;
        }).addOnSuccessListener(v -> cb.onSuccess())
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Vraća 1 token (poziva se kada korisnik napusti lobby pre nalaženja protivnika). */
    public void refundToken(String uid) {
        db.collection("users").document(uid)
                .update("tokens", com.google.firebase.firestore.FieldValue.increment(1));
    }

    /** Proverava datum poslednjeg bonusa i dodaje 5 tokena ako je novi dan. */
    public void claimDailyTokensIfNeeded(String uid, String today) {
        claimDailyTokensIfNeeded(uid, today, null);
    }

    /**
     * Proverava datum poslednjeg bonusa i dodaje tokene ovisno o ligi (spec 6b):
     *   5 baznih + 1 po ligi (liga 0 → 5, liga 5 → 10).
     */
    public void claimDailyTokensIfNeeded(String uid, String today, DailyTokenCallback cb) {
        DocumentReference ref = db.collection("users").document(uid);
        db.<Integer>runTransaction(tx -> {
            com.google.firebase.firestore.DocumentSnapshot snap = tx.get(ref);
            String last  = snap.getString("lastTokenDate");
            long tokens  = safe(snap.getLong("tokens"));
            long stars   = safe(snap.getLong("stars"));
            if (!today.equals(last)) {
                int bonus = LeagueUtil.getDailyTokens(LeagueUtil.getLeague((int) stars));
                tx.update(ref, "tokens", tokens + bonus);
                tx.update(ref, "lastTokenDate", today);
                return bonus;
            }
            return -1;
        }).addOnSuccessListener(result -> {
            if (cb == null) return;
            if (result != null && result >= 0) cb.onClaimed(result);
            else cb.onAlreadyClaimed();
        }).addOnFailureListener(e -> { /* tiho ignoriši */ });
    }

    /**
     * Primjenjuje kaznu od 30% zvezda ako korisnik nije plasiran u prethodnom
     * ciklusu (spec 6e). Poziva cb ako se liga promijeni.
     */
    public void applyMonthlyPenaltyIfNeeded(String uid, LeagueChangeCallback cb) {
        String prevCycle = previousCycleId();
        DocumentReference ref = db.collection("users").document(uid);
        db.<long[]>runTransaction(tx -> {
            com.google.firebase.firestore.DocumentSnapshot snap = tx.get(ref);
            String lastPenalty = snap.getString("lastPenaltyCycle");
            if (prevCycle.equals(lastPenalty)) return null;

            String lastMonthlyReward = snap.getString("lastMonthlyRewardCycle");
            long stars = safe(snap.getLong("stars"));

            tx.update(ref, "lastPenaltyCycle", prevCycle);
            if (prevCycle.equals(lastMonthlyReward)) return null; // bio plasiran, nema kazne

            long newStars = (long) (stars * 0.70);
            tx.update(ref, "stars", newStars);
            return new long[]{ stars, newStars };
        }).addOnSuccessListener(result -> {
            if (result == null || cb == null) return;
            int oldL = LeagueUtil.getLeague((int) result[0]);
            int newL = LeagueUtil.getLeague((int) result[1]);
            if (oldL != newL) cb.onChanged(oldL, newL);
        });
    }

    /**
     * Isplaćuje nagradu za plasman na rang listi i štiti od dvostruke isplate
     * transakcijom koja čuva ključ ciklusa u korisničkom dokumentu.
     */
    public void claimRankingReward(String uid, boolean weekly, String cycleKey, int tokens, Callback cb) {
        String field = weekly ? "lastWeeklyRewardCycle" : "lastMonthlyRewardCycle";
        DocumentReference ref = db.collection("users").document(uid);
        db.<Boolean>runTransaction(tx -> {
            String lastCycle     = tx.get(ref).getString(field);
            if (cycleKey.equals(lastCycle)) return false;
            long currentTokens   = safe(tx.get(ref).getLong("tokens"));
            tx.update(ref, "tokens", currentTokens + tokens);
            tx.update(ref, field,    cycleKey);
            return true;
        }).addOnSuccessListener(claimed -> {
            if (Boolean.TRUE.equals(claimed)) cb.onSuccess();
            else cb.onError("already_claimed");
        }).addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public void saveAvatarColor(String uid, String hexColor, Callback cb) {
        db.collection("users").document(uid)
          .update("avatarColor", hexColor)
          .addOnSuccessListener(v -> cb.onSuccess())
          .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Čuva FCM registracioni token za push notifikacije na ovom uređaju. */
    public void saveFcmToken(String uid, String token) {
        db.collection("users").document(uid)
          .update("fcmToken", token);
    }

    public void incrementSpojnice(String uid, int connected, int total) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long c = safe(tx.get(ref).getLong("spojniceConnected"));
            long t = safe(tx.get(ref).getLong("spojniceTotal"));
            tx.update(ref, "spojniceConnected", c + connected);
            tx.update(ref, "spojniceTotal", t + total);
            return null;
        });
    }

    public void incrementAsocijacije(String uid, int solved, int total) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long s = safe(tx.get(ref).getLong("asocijacijeSolved"));
            long t = safe(tx.get(ref).getLong("asocijacijeTotal"));
            tx.update(ref, "asocijacijeSolved", s + solved);
            tx.update(ref, "asocijacijeTotal", t + total);
            return null;
        });
    }

    public void incrementSkocko(String uid, boolean solved) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long c = safe(tx.get(ref).getLong("skockoCorrect"));
            long t = safe(tx.get(ref).getLong("skockoTotal"));
            tx.update(ref, "skockoTotal", t + 1);
            if (solved) tx.update(ref, "skockoCorrect", c + 1);
            return null;
        });
    }

    public void incrementKorak(String uid, boolean solved) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long c = safe(tx.get(ref).getLong("korakCorrect"));
            long t = safe(tx.get(ref).getLong("korakTotal"));
            tx.update(ref, "korakTotal", t + 1);
            if (solved) tx.update(ref, "korakCorrect", c + 1);
            return null;
        });
    }

    public void incrementMojBroj(String uid, int correct, int total) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long c = safe(tx.get(ref).getLong("mojBrojCorrect"));
            long t = safe(tx.get(ref).getLong("mojBrojTotal"));
            tx.update(ref, "mojBrojCorrect", c + correct);
            tx.update(ref, "mojBrojTotal", t + total);
            return null;
        });
    }

    public void incrementGameCount(String uid, boolean won) {
        DocumentReference ref = db.collection("users").document(uid);
        db.runTransaction(tx -> {
            long played = safe(tx.get(ref).getLong("gamesPlayed"));
            long wins   = safe(tx.get(ref).getLong("gamesWon"));
            tx.update(ref, "gamesPlayed", played + 1);
            if (won) tx.update(ref, "gamesWon", wins + 1);
            return null;
        });
    }

    public void addStars(String uid, int amount) {
        if (amount <= 0) return;
        db.collection("users").document(uid)
                .update("stars", com.google.firebase.firestore.FieldValue.increment(amount));
    }

    public void addTokens(String uid, int amount) {
        if (amount <= 0) return;
        db.collection("users").document(uid)
                .update("tokens", com.google.firebase.firestore.FieldValue.increment(amount));
    }

    private long safe(Long v) { return v != null ? v : 0L; }
}

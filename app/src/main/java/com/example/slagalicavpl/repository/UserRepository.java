package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.User;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

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
        /** Pozvana kada su tokeni zaista dodati (bio je novi dan). */
        void onClaimed();
        /** Pozvana kada su tokeni već traženi danas. */
        void onAlreadyClaimed();
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

    public void incrementStats(String uid, boolean won, int myScore) {
        DocumentReference ref = db.collection("users").document(uid);
        // pobednik: +10 + floor(score/40); gubitnik: -10 + floor(score/40), ne ispod 0 ukupno
        int starsDelta = won ? 10 + (myScore / 40) : -10 + (myScore / 40);

        db.runTransaction(tx -> {
            long played = safe(tx.get(ref).getLong("gamesPlayed"));
            long wins   = safe(tx.get(ref).getLong("gamesWon"));
            long stars  = safe(tx.get(ref).getLong("stars"));
            long tokens = safe(tx.get(ref).getLong("tokens"));

            tx.update(ref, "gamesPlayed", played + 1);
            if (won) tx.update(ref, "gamesWon", wins + 1);

            long newStars = Math.max(0, stars + starsDelta);
            tx.update(ref, "stars", newStars);

            // svaki puni višekratnik od 50 zvezdi donosi 1 token
            long newTokens = tokens + (newStars / 50 - stars / 50);
            if (newTokens != tokens) tx.update(ref, "tokens", Math.max(0, newTokens));
            return null;
        });
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
     * Proverava datum poslednjeg bonusa i dodaje 5 tokena ako je novi dan.
     * Poziva callback sa informacijom da li su tokeni zaista dodati.
     */
    public void claimDailyTokensIfNeeded(String uid, String today, DailyTokenCallback cb) {
        DocumentReference ref = db.collection("users").document(uid);
        db.<Boolean>runTransaction(tx -> {
            String last   = tx.get(ref).getString("lastTokenDate");
            long tokens   = safe(tx.get(ref).getLong("tokens"));
            if (!today.equals(last)) {
                tx.update(ref, "tokens", tokens + 5);
                tx.update(ref, "lastTokenDate", today);
                return true;
            }
            return false;
        }).addOnSuccessListener(claimed -> {
            if (cb == null) return;
            if (Boolean.TRUE.equals(claimed)) cb.onClaimed();
            else cb.onAlreadyClaimed();
        }).addOnFailureListener(e -> { /* tiho ignoriši */ });
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

    private long safe(Long v) { return v != null ? v : 0L; }
}

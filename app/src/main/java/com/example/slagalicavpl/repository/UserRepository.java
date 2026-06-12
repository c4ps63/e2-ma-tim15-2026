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
        int starsEarned = won
                ? 10 + (myScore / 40)
                : Math.max(0, -10 + (myScore / 40));  // gubitnik: -10 + bonus zvezde

        db.runTransaction(tx -> {
            long played = safe(tx.get(ref).getLong("gamesPlayed"));
            long wins   = safe(tx.get(ref).getLong("gamesWon"));
            long stars  = safe(tx.get(ref).getLong("stars"));
            long tokens = safe(tx.get(ref).getLong("tokens"));

            tx.update(ref, "gamesPlayed", played + 1);
            if (won) tx.update(ref, "gamesWon", wins + 1);

            long newStars = Math.max(0, stars + starsEarned);
            tx.update(ref, "stars", newStars);

            long newTokens = tokens + (newStars / 50 - stars / 50);
            if (newTokens != tokens) tx.update(ref, "tokens", newTokens);
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

    private long safe(Long v) { return v != null ? v : 0L; }
}

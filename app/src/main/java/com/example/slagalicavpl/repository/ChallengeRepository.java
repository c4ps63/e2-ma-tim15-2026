package com.example.slagalicavpl.repository;

import com.example.slagalicavpl.model.Challenge;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class ChallengeRepository {

    public interface Callback {
        void onSuccess();
        void onError(String msg);
    }

    public interface ListCallback {
        void onLoaded(List<Challenge> challenges);
    }

    public interface ChallengeCallback {
        void onLoaded(Challenge challenge);
        void onError(String msg);
    }

    private static final String DB_URL =
            "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";

    private static ChallengeRepository instance;
    private final DatabaseReference challengesRef;
    private final FirebaseFirestore  firestore;

    private ChallengeRepository() {
        challengesRef = FirebaseDatabase.getInstance(DB_URL).getReference("challenges");
        firestore     = FirebaseFirestore.getInstance();
    }

    public static ChallengeRepository getInstance() {
        if (instance == null) instance = new ChallengeRepository();
        return instance;
    }

    // ── Kreiranje ─────────────────────────────────────────────────────────────

    public void createChallenge(Challenge challenge, String creatorName, Callback cb) {
        // Dodaj kreatora kao prvog igrača
        Challenge.ChallengePlayer creator = new Challenge.ChallengePlayer(creatorName);
        challenge.players.put(challenge.creatorUid, creator);

        challengesRef.child(challenge.challengeId)
                .setValue(challenge)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Pridruživanje ─────────────────────────────────────────────────────────

    /**
     * Atomično provjeri slobodno mjesto i pridruži se.
     * cb.onError("full") ako je puno, cb.onError("started") ako je već počelo.
     */
    public void joinChallenge(String challengeId, String uid, String name, Callback cb) {
        DatabaseReference ref = challengesRef.child(challengeId);
        ref.runTransaction(new com.google.firebase.database.Transaction.Handler() {
            @Override
            public com.google.firebase.database.Transaction.Result doTransaction(
                    com.google.firebase.database.MutableData data) {
                Challenge c = data.getValue(Challenge.class);
                if (c == null) return com.google.firebase.database.Transaction.abort();
                if (!"open".equals(c.status))
                    return com.google.firebase.database.Transaction.abort();
                if (c.players != null && c.players.size() >= 4)
                    return com.google.firebase.database.Transaction.abort();
                if (c.players == null) c.players = new java.util.HashMap<>();
                if (c.players.containsKey(uid))
                    return com.google.firebase.database.Transaction.success(data); // već tu
                c.players.put(uid, new Challenge.ChallengePlayer(name));
                data.setValue(c);
                return com.google.firebase.database.Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
                if (!committed || error != null) cb.onError("full");
                else cb.onSuccess();
            }
        });
    }

    // ── Start ─────────────────────────────────────────────────────────────────

    public void startChallenge(String challengeId, Callback cb) {
        challengesRef.child(challengeId).child("status")
                .setValue("in_progress")
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Predaja rezultata ─────────────────────────────────────────────────────

    public void submitScore(String challengeId, String uid, int score, Callback cb) {
        DatabaseReference ref = challengesRef.child(challengeId).child("players").child(uid);
        ref.child("score").setValue(score);
        ref.child("finished").setValue(true)
                .addOnSuccessListener(v -> cb.onSuccess())
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    // ── Slušanje promjena ─────────────────────────────────────────────────────

    public ValueEventListener listenChallenge(String challengeId, ChallengeCallback cb) {
        ValueEventListener l = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                Challenge c = snap.getValue(Challenge.class);
                if (c != null) { c.challengeId = snap.getKey(); cb.onLoaded(c); }
                else cb.onError("not_found");
            }
            @Override public void onCancelled(DatabaseError e) { cb.onError(e.getMessage()); }
        };
        challengesRef.child(challengeId).addValueEventListener(l);
        return l;
    }

    public void removeListener(String challengeId, ValueEventListener l) {
        challengesRef.child(challengeId).removeEventListener(l);
    }

    /** Lista otvorenih izazova za region. */
    public ValueEventListener listenOpenChallenges(String region, ListCallback cb) {
        ValueEventListener l = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                List<Challenge> list = new ArrayList<>();
                for (DataSnapshot child : snap.getChildren()) {
                    Challenge c = child.getValue(Challenge.class);
                    if (c == null) continue;
                    c.challengeId = child.getKey();
                    if (region.equals(c.region) && "open".equals(c.status)) list.add(c);
                }
                cb.onLoaded(list);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        challengesRef.addValueEventListener(l);
        return l;
    }

    public void removeListListener(ValueEventListener l) {
        challengesRef.removeEventListener(l);
    }

    // ── Isplata nagrade ───────────────────────────────────────────────────────

    /**
     * Isplaćuje nagrade po završetku izazova.
     *
     * 1. mjesto: 75% poola (stars i tokens)
     * 2. mjesto: vraća ulog
     * 3., 4.   : gube ulog (već oduzet pri ulasku)
     */
    public void settleChallenge(Challenge challenge) {
        List<String> ranked = rankPlayers(challenge);
        int n     = ranked.size();
        int pool  = challenge.stakeStars  * n;
        int tPool = challenge.stakeTokens * n;

        for (int i = 0; i < ranked.size(); i++) {
            String uid     = ranked.get(i);
            int    dStars  = 0;
            int    dTokens = 0;

            if (i == 0) {
                // 1. mjesto — 75% poola
                dStars  = (int)(pool  * 0.75);
                dTokens = (int)(tPool * 0.75);
            } else if (i == 1) {
                // 2. mjesto — vraća ulog
                dStars  = challenge.stakeStars;
                dTokens = challenge.stakeTokens;
            }
            // ostali dobijaju 0 (već su izgubili ulog)

            applyPrize(uid, dStars, dTokens);
        }
        challengesRef.child(challenge.challengeId).child("status").setValue("finished");
    }

    private List<String> rankPlayers(Challenge c) {
        List<String> uids = new ArrayList<>(c.players.keySet());
        uids.sort((a, b) -> {
            int sa = c.players.get(a) != null ? c.players.get(a).score : 0;
            int sb = c.players.get(b) != null ? c.players.get(b).score : 0;
            return Integer.compare(sb, sa); // descending
        });
        return uids;
    }

    private void applyPrize(String uid, int stars, int tokens) {
        if (stars == 0 && tokens == 0) return;
        DocumentReference ref = firestore.collection("users").document(uid);
        firestore.runTransaction(tx -> {
            long s = safeL(tx.get(ref).getLong("stars"))  + stars;
            long t = safeL(tx.get(ref).getLong("tokens")) + tokens;
            tx.update(ref, "stars",  Math.max(0, s));
            tx.update(ref, "tokens", Math.max(0, t));
            return null;
        });
    }

    private long safeL(Long v) { return v != null ? v : 0L; }
}

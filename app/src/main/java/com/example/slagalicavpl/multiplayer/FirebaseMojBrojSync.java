package com.example.slagalicavpl.multiplayer;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs MojBroj game state over Firebase Realtime Database.
 *
 * Path: /rooms/{roomId}/mojbroj/
 *
 * The "starter" (p1 in round1, p2 in round2) hits STOP for target/tiles.
 * Both players build independently and submit results.
 */
public class FirebaseMojBrojSync {

    public interface StarterListener {
        /** The other player submitted their result. */
        void onOpponentResult(int result);
    }

    public interface FollowerListener {
        /** Starter locked the target — show it and start number spin. */
        void onTargetLocked(int target);
        /** Starter locked the tiles — show them and start building phase. */
        void onTilesLocked(int[] tiles);
        /** The other player submitted their result. */
        void onOpponentResult(int result);
    }

    private final DatabaseReference ref;
    private final String myRole;
    private ValueEventListener listener;

    public FirebaseMojBrojSync(DatabaseReference roomRef, String myRole) {
        this.ref    = roomRef.child("mojbroj");
        this.myRole = myRole;
    }

    // ── Starter writes ────────────────────────────────────────────────────────

    public void writeRoundStart(int round) {
        Map<String, Object> init = new HashMap<>();
        init.put("round", round);
        init.put("targetLocked", false);
        init.put("tilesLocked", false);
        init.put("p1submitted", false);
        init.put("p2submitted", false);
        ref.updateChildren(init);
    }

    public void writeTarget(int target) {
        Map<String, Object> m = new HashMap<>();
        m.put("target", target);
        m.put("targetLocked", true);
        ref.updateChildren(m);
    }

    public void writeTiles(int[] tiles) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < tiles.length; i++) m.put("tile" + i, tiles[i]);
        m.put("tilesLocked", true);
        ref.updateChildren(m);
    }

    public void writeResult(int result) {
        String key = "p1".equals(myRole) ? "p1result" : "p2result";
        String subKey = "p1".equals(myRole) ? "p1submitted" : "p2submitted";
        Map<String, Object> m = new HashMap<>();
        m.put(key, result);
        m.put(subKey, true);
        ref.updateChildren(m);
    }

    // ── Starter listens for opponent result ───────────────────────────────────

    public void listenAsStarter(StarterListener cb) {
        cancelListener();
        String oppKey = "p1".equals(myRole) ? "p2submitted" : "p1submitted";
        String oppRes = "p1".equals(myRole) ? "p2result" : "p1result";
        listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Boolean submitted = snap.child(oppKey).getValue(Boolean.class);
                if (Boolean.TRUE.equals(submitted)) {
                    Object v = snap.child(oppRes).getValue();
                    int result = v instanceof Number ? ((Number) v).intValue() : -1;
                    cb.onOpponentResult(result);
                    cancelListener();
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
    }

    // ── Follower listens ──────────────────────────────────────────────────────

    public void listenAsFollower(int round, FollowerListener cb) {
        cancelListener();
        String oppKey = "p1".equals(myRole) ? "p2submitted" : "p1submitted";
        String oppRes = "p1".equals(myRole) ? "p2result" : "p1result";
        listener = new ValueEventListener() {
            private boolean targetDelivered = false;
            private boolean tilesDelivered  = false;
            private boolean resultDelivered = false;

            @Override
            public void onDataChange(DataSnapshot snap) {
                // Ignore data from a different round
                Integer roundInDb = snap.child("round").getValue(Integer.class);
                if (roundInDb == null || roundInDb != round) return;

                if (!targetDelivered) {
                    Boolean locked = snap.child("targetLocked").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(locked)) {
                        Object v = snap.child("target").getValue();
                        if (v instanceof Number) {
                            targetDelivered = true;
                            cb.onTargetLocked(((Number) v).intValue());
                        }
                    }
                }

                if (targetDelivered && !tilesDelivered) {
                    Boolean locked = snap.child("tilesLocked").getValue(Boolean.class);
                    if (Boolean.TRUE.equals(locked)) {
                        int[] tiles = new int[6];
                        boolean ok = true;
                        for (int i = 0; i < 6; i++) {
                            Object v = snap.child("tile" + i).getValue();
                            if (!(v instanceof Number)) { ok = false; break; }
                            tiles[i] = ((Number) v).intValue();
                        }
                        if (ok) {
                            tilesDelivered = true;
                            cb.onTilesLocked(tiles);
                        }
                    }
                }

                if (!resultDelivered) {
                    Boolean submitted = snap.child(oppKey).getValue(Boolean.class);
                    if (Boolean.TRUE.equals(submitted)) {
                        Object v = snap.child(oppRes).getValue();
                        int result = v instanceof Number ? ((Number) v).intValue() : -1;
                        resultDelivered = true;
                        cb.onOpponentResult(result);
                    }
                }
            }

            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
    }

    public void cancelListener() {
        if (listener != null) {
            ref.removeEventListener(listener);
            listener = null;
        }
    }
}

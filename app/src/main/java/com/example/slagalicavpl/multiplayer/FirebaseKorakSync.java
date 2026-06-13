package com.example.slagalicavpl.multiplayer;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Syncs KorakPoKorak game state over Firebase Realtime Database.
 *
 * Path: /rooms/{roomId}/korak/
 *
 * Active player writes; passive player listens.
 */
public class FirebaseKorakSync {

    public interface PassiveListener {
        /** Called when the active player has chosen a puzzle for this round. */
        void onPuzzleSelected(int puzzleIdx);
        /** Called when the active player advances to a new step. */
        void onStepAdvanced(int stepIndex);
        /** Called when active player's turn ends and steal becomes available. */
        void onStealPhase();
        /** Called when the round ends (before game-over check). */
        void onRoundEnd(int p1pts, int p2pts);
        /** Called when both rounds are done. */
        void onGameOver(int p1pts, int p2pts);
    }

    private final DatabaseReference ref;
    private ValueEventListener listener;

    public FirebaseKorakSync(DatabaseReference roomRef) {
        this.ref = roomRef.child("korak");
    }

    // ── Active player writes ──────────────────────────────────────────────────

    public void writePuzzleIdx(int round, int puzzleIdx) {
        ref.child("puzzle" + round).setValue(puzzleIdx);
        ref.child("round").setValue(round);
        ref.child("step").setValue(0);
        ref.child("phase").setValue("PLAYING");
    }

    public void writeStep(int stepIndex) {
        ref.child("step").setValue(stepIndex);
    }

    public void writeStealPhase() {
        ref.child("phase").setValue("STEAL");
    }

    public void writeRoundEnd(int p1pts, int p2pts) {
        Map<String, Object> update = new HashMap<>();
        update.put("p1pts", p1pts);
        update.put("p2pts", p2pts);
        update.put("phase", "END");
        ref.updateChildren(update);
    }

    public void writeGameOver(int p1pts, int p2pts) {
        Map<String, Object> update = new HashMap<>();
        update.put("p1pts", p1pts);
        update.put("p2pts", p2pts);
        update.put("phase", "GAMEOVER");
        ref.updateChildren(update);
    }

    // ── Passive player listens ────────────────────────────────────────────────

    public void listenPassive(int round, PassiveListener cb) {
        cancelListener();
        listener = new ValueEventListener() {
            private int lastStep = -1;
            private String lastPhase = "";

            @Override
            public void onDataChange(DataSnapshot snap) {
                // Only process state for the current round — ignore data written for other rounds
                Integer roundInDb = snap.child("round").getValue(Integer.class);
                if (roundInDb == null || roundInDb != round) return;

                // Wait until active player has selected the puzzle for this round
                Object puzzleRaw = snap.child("puzzle" + round).getValue();
                if (puzzleRaw == null) return;
                int puzzleIdx = ((Number) puzzleRaw).intValue();
                cb.onPuzzleSelected(puzzleIdx);

                // Step changes
                Object stepRaw = snap.child("step").getValue();
                if (stepRaw != null) {
                    int step = ((Number) stepRaw).intValue();
                    if (step != lastStep) {
                        lastStep = step;
                        cb.onStepAdvanced(step);
                    }
                }

                // Phase changes — only react after seeing "PLAYING" first
                // to avoid triggering END/GAMEOVER from a stale previous phase write
                String phase = snap.child("phase").getValue(String.class);
                if (phase == null) phase = "";
                if (!phase.equals(lastPhase)) {
                    String prev = lastPhase;
                    lastPhase = phase;
                    int p1pts = getInt(snap, "p1pts");
                    int p2pts = getInt(snap, "p2pts");
                    if ("STEAL".equals(phase) && "PLAYING".equals(prev)) {
                        cb.onStealPhase();
                    } else if ("END".equals(phase)) {
                        cb.onRoundEnd(p1pts, p2pts);
                    } else if ("GAMEOVER".equals(phase)) {
                        cb.onGameOver(p1pts, p2pts);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError e) {}
        };
        ref.addValueEventListener(listener);
    }

    public void cancelListener() {
        if (listener != null) {
            ref.removeEventListener(listener);
            listener = null;
        }
    }

    private static int getInt(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}

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
        /** Called each time the active player submits a guess (correct or wrong). */
        void onGuess(String text, boolean correct);
        /** Called when active player's turn ends and steal becomes available. */
        void onStealPhase();
        /** Called when the round ends (before game-over check). */
        void onRoundEnd(int p1pts, int p2pts);
        /** Called when both rounds are done. */
        void onGameOver(int p1pts, int p2pts);
    }

    public interface StealGuessListener {
        /** Called when the passive (stealing) player has submitted their steal attempt. */
        void onStealGuess(String text);
    }

    private final DatabaseReference ref;
    private ValueEventListener listener;
    private ValueEventListener stealGuessListener;

    public FirebaseKorakSync(DatabaseReference roomRef) {
        this.ref = roomRef.child("korak");
    }

    // ── Active player writes ──────────────────────────────────────────────────

    public void writePuzzleIdx(int round, int puzzleIdx) {
        Map<String, Object> update = new HashMap<>();
        update.put("puzzle" + round, puzzleIdx);
        update.put("round", round);
        update.put("step", 0);
        update.put("phase", "PLAYING");
        update.put("p1pts", 0);
        update.put("p2pts", 0);
        update.put("guess", null);
        ref.updateChildren(update);
    }

    public void writeStep(int stepIndex) {
        ref.child("step").setValue(stepIndex);
    }

    public void writeGuess(String text, boolean correct) {
        Map<String, Object> m = new HashMap<>();
        m.put("text",    text);
        m.put("correct", correct);
        m.put("ts",      System.currentTimeMillis());
        ref.child("guess").setValue(m);
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

    // ── Passive player writes their steal attempt back to the active player ────

    public void writeStealGuess(String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("text", text);
        m.put("ts",   System.currentTimeMillis());
        ref.child("stealGuess").setValue(m);
    }

    // ── Active player listens for the steal attempt ─────────────────────────────

    public void listenForStealGuess(StealGuessListener cb) {
        cancelStealGuessListener();
        stealGuessListener = new ValueEventListener() {
            private final long startTs = System.currentTimeMillis();

            @Override
            public void onDataChange(DataSnapshot snap) {
                Object tsRaw = snap.child("ts").getValue();
                long ts = tsRaw instanceof Number ? ((Number) tsRaw).longValue() : 0;
                if (ts <= startTs) return; // ignore guesses left over from a previous round
                String text = snap.child("text").getValue(String.class);
                if (text != null) cb.onStealGuess(text);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        ref.child("stealGuess").addValueEventListener(stealGuessListener);
    }

    public void cancelStealGuessListener() {
        if (stealGuessListener != null) {
            ref.child("stealGuess").removeEventListener(stealGuessListener);
            stealGuessListener = null;
        }
    }

    // ── Passive player listens ────────────────────────────────────────────────

    public void listenPassive(int round, PassiveListener cb) {
        cancelListener();
        listener = new ValueEventListener() {
            private int lastStep = -1;
            private String lastPhase = "";
            private long lastGuesTs = 0;

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

                // Guess changes — show opponent's guesses in real-time
                DataSnapshot gSnap = snap.child("guess");
                if (gSnap.exists()) {
                    Object tsRaw = gSnap.child("ts").getValue();
                    long ts = tsRaw instanceof Number ? ((Number) tsRaw).longValue() : 0;
                    if (ts > lastGuesTs) {
                        lastGuesTs = ts;
                        String gText = gSnap.child("text").getValue(String.class);
                        Boolean gCorrect = gSnap.child("correct").getValue(Boolean.class);
                        if (gText != null && gCorrect != null) cb.onGuess(gText, gCorrect);
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
                    } else if ("END".equals(phase) && "PLAYING".equals(prev)) {
                        cb.onRoundEnd(p1pts, p2pts);
                    } else if ("GAMEOVER".equals(phase) && "PLAYING".equals(prev)) {
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
        cancelStealGuessListener();
    }

    private static int getInt(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}

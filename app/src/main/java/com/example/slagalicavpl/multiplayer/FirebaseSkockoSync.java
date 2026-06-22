package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.game.SkockoEngine;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase sync za Skocko.
 *
 * Path: /rooms/{roomId}/skocko/
 *
 * P1 generiše obe tajne i piše ih:
 *   /skocko/secret1 = [0,2,3,1]
 *   /skocko/secret2 = [5,1,0,4]
 *
 * P2 čita tajne pre nego što engine startuje.
 *
 * Tokom opp faze (R1_BONUS_OPP ili R2_OPP):
 *   - lokalni igrač šalje pokušaje na Firebase
 *   - protivnik čita i emituje callback
 *
 * Pokušaji: /skocko/attempts/{phase}/{idx} = { guess:[...], hits, nears, by }
 */
public class FirebaseSkockoSync implements SkockoSync {

    private final DatabaseReference ref;   // /rooms/{roomId}/skocko
    private final String myRole;
    private final String oppRole;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ValueEventListener activeListener;
    private DatabaseReference  activeRef;

    public FirebaseSkockoSync(DatabaseReference roomRef, String myRole) {
        this.ref     = roomRef.child("skocko");
        this.myRole  = myRole;
        this.oppRole = "p1".equals(myRole) ? "p2" : "p1";
    }

    // ── P1 piše tajne, P2 čita ───────────────────────────────────────────────

    public void writeSecrets(int[] secret1, int[] secret2) {
        Map<String, Object> s1 = new HashMap<>();
        Map<String, Object> s2 = new HashMap<>();
        for (int i = 0; i < secret1.length; i++) {
            s1.put(String.valueOf(i), secret1[i]);
            s2.put(String.valueOf(i), secret2[i]);
        }
        ref.child("secret1").setValue(s1);
        ref.child("secret2").setValue(s2);
    }

    public interface SecretsCallback { void onSecrets(int[] s1, int[] s2); }

    public void readSecrets(SecretsCallback cb) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                DataSnapshot s1snap = snap.child("secret1");
                DataSnapshot s2snap = snap.child("secret2");
                if (!s1snap.exists() || !s2snap.exists()) {
                    handler.postDelayed(() -> readSecrets(cb), 300);
                    return;
                }
                int[] s1 = readCode(s1snap);
                int[] s2 = readCode(s2snap);
                handler.post(() -> cb.onSecrets(s1, s2));
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private int[] readCode(DataSnapshot snap) {
        int[] code = new int[SkockoEngine.CODE_LENGTH];
        for (int i = 0; i < code.length; i++) {
            Object v = snap.child(String.valueOf(i)).getValue();
            code[i] = v instanceof Number ? ((Number) v).intValue() : 0;
        }
        return code;
    }

    // ── Lokalni igrač piše pokušaj (poziva ga fragment) ───────────────────────

    public void writeAttempt(String phaseKey, int idx, int[] guess, int hits, int nears) {
        Map<String, Object> m = new HashMap<>();
        Map<String, Integer> g = new HashMap<>();
        for (int i = 0; i < guess.length; i++) g.put(String.valueOf(i), guess[i]);
        m.put("guess", g);
        m.put("hits",  hits);
        m.put("nears", nears);
        m.put("by",    myRole);
        m.put("idx",   idx);
        ref.child("attempts").child(phaseKey).child(String.valueOf(idx)).setValue(m);
    }

    public void writePhaseDone(String phaseKey, boolean solved) {
        ref.child("done").child(phaseKey).setValue(solved);
    }

    // ── SkockoSync interface ──────────────────────────────────────────────────

    @Override
    public void startOpponentTurn(int[] secret, int maxAttempts, AttemptCallback cb) {
        cancel();
        // Use maxAttempts to identify phase: 1 = bonus steal, MAX = main round.
        // This is safe because each player has exactly one main (6-attempt) and
        // one bonus (1-attempt) opponent phase, regardless of whether round 1 was solved.
        String phaseKey;
        if ("p1".equals(myRole)) {
            phaseKey = (maxAttempts == 1) ? "r1bonus_p2" : "r2main_p2";
        } else {
            phaseKey = (maxAttempts == SkockoEngine.MAX_ATTEMPTS) ? "r1main_p1" : "r2bonus_p1";
        }

        activeRef = ref.child("attempts").child(phaseKey);
        final boolean[] delivered = new boolean[maxAttempts + 1];

        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                for (DataSnapshot attemptSnap : snap.getChildren()) {
                    try {
                        int idx = Integer.parseInt(attemptSnap.getKey());
                        if (idx >= maxAttempts || delivered[idx]) continue;
                        delivered[idx] = true;

                        int[] guess = new int[SkockoEngine.CODE_LENGTH];
                        DataSnapshot gSnap = attemptSnap.child("guess");
                        for (int i = 0; i < guess.length; i++) {
                            Object v = gSnap.child(String.valueOf(i)).getValue();
                            guess[i] = v instanceof Number ? ((Number) v).intValue() : 0;
                        }
                        int hits  = getInt(attemptSnap, "hits");
                        int nears = getInt(attemptSnap, "nears");
                        final int[] finalGuess = guess;
                        handler.post(() -> cb.onOpponentAttempt(idx, finalGuess, hits, nears));
                    } catch (NumberFormatException ignored) {}
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        activeRef.addValueEventListener(activeListener);

        // Pratimo "done" signal
        ref.child("done").child(phaseKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                Object v = snap.getValue();
                if (v instanceof Boolean) {
                    boolean solved = (boolean) v;
                    ref.child("done").child(phaseKey).removeEventListener(this);
                    cancel();
                    handler.post(() -> cb.onOpponentDone(solved));
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    @Override
    public void cancel() {
        if (activeListener != null && activeRef != null) {
            activeRef.removeEventListener(activeListener);
            activeListener = null;
            activeRef = null;
        }
    }

    private static int getInt(DataSnapshot snap, String key) {
        Object v = snap.child(key).getValue();
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }
}

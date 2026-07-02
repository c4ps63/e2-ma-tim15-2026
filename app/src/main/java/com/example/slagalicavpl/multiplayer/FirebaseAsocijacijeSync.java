package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.AsocijacijePuzzle;
import com.example.slagalicavpl.util.StringNormalizer;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Firebase sync za Asocijacije.
 *
 * Path: /rooms/{roomId}/asocijacije/r{round}/actions/{role}_{seq}
 *
 * Svaka akcija dobija jedinstven ključ "{myRole}_{seq}" da nema kolizije
 * između akcija P1 i P2 u istoj rundi. Listener filtrira po "by" polju.
 *
 * Validacija: StringNormalizer.matches() — isti algoritam kao AsocijacijeEngine.
 */
public class FirebaseAsocijacijeSync implements AsocijacijeSync {

    private final DatabaseReference ref;  // /rooms/{roomId}/asocijacije
    private final String myRole;
    private final String oppRole;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ValueEventListener activeListener;
    private DatabaseReference  activeRef;
    private int                seqCounter = 0;

    // Ključevi akcija koje smo već proslijedili engine-u (reset po rundi)
    private final Set<String> deliveredKeys    = new HashSet<>();
    private int               listenedRound    = -1;

    public FirebaseAsocijacijeSync(DatabaseReference roomRef, String myRole) {
        this.ref     = roomRef.child("asocijacije");
        this.myRole  = myRole;
        this.oppRole = "p1".equals(myRole) ? "p2" : "p1";
    }

    // ── Set ID sharing ────────────────────────────────────────────────────────

    public interface SetIdCallback { void onReady(String setId); }

    public void writeSetId(String setId) {
        ref.child("setId").setValue(setId);
    }

    public void readSetId(SetIdCallback cb) {
        ref.child("setId").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) {
                    handler.postDelayed(() -> readSetId(cb), 300);
                    return;
                }
                handler.post(() -> cb.onReady(snap.getValue(String.class)));
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    // ── Pisanje akcija lokalnog igrača ────────────────────────────────────────

    public void writeOpenField(int round, int col, int row) {
        writeAction(round, "openField", col, row, null);
    }

    public void writeGuessColumn(int round, int col, String text) {
        writeAction(round, "guessColumn", col, -1, text);
    }

    public void writeGuessFinal(int round, String text) {
        writeAction(round, "guessFinal", -1, -1, text);
    }

    public void writeDone(int round) {
        writeAction(round, "done", -1, -1, null);
    }

    private void writeAction(int round, String type, int col, int row, String text) {
        // Role-prefixed key sprječava koliziju između P1 i P2 u istoj putanji
        String key = myRole + "_" + String.format("%04d", seqCounter++);
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("by",   myRole);
        m.put("col",  col);
        m.put("row",  row);
        if (text != null) m.put("text", text);
        ref.child("r" + round).child("actions").child(key).setValue(m);
    }

    // ── AsocijacijeSync interface ─────────────────────────────────────────────

    @Override
    public void startOpponentTurn(AsocijacijePuzzle puzzle, boolean[] colSolved,
                                   boolean[][] cellOpened, int round, Callback cb) {
        cancel();
        // Nova runda: resetuj delivered set
        if (round != listenedRound) {
            deliveredKeys.clear();
            listenedRound = round;
        }

        activeRef = ref.child("r" + round).child("actions");
        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                for (DataSnapshot actionSnap : snap.getChildren()) {
                    String key = actionSnap.getKey();
                    if (deliveredKeys.contains(key)) continue;

                    String by = actionSnap.child("by").getValue(String.class);
                    if (!oppRole.equals(by)) continue;

                    String type = actionSnap.child("type").getValue(String.class);
                    if (type == null) continue;

                    deliveredKeys.add(key);
                    processAction(puzzle, actionSnap, type, cb);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        activeRef.addValueEventListener(activeListener);
    }

    private void processAction(AsocijacijePuzzle puzzle, DataSnapshot snap, String type, Callback cb) {
        switch (type) {
            case "openField": {
                Integer col = snap.child("col").getValue(Integer.class);
                Integer row = snap.child("row").getValue(Integer.class);
                if (col != null && row != null)
                    handler.post(() -> cb.onOpponentOpenField(col, row));
                break;
            }
            case "guessColumn": {
                Integer col = snap.child("col").getValue(Integer.class);
                String text = snap.child("text").getValue(String.class);
                if (col != null && text != null) {
                    String solution = puzzle.colSolutions[col];
                    handler.post(() -> cb.onOpponentAttempt(text));
                    handler.postDelayed(() -> {
                        if (StringNormalizer.matches(text, solution))
                            cb.onOpponentGuessColumn(col);
                        else
                            cb.onOpponentDone();
                    }, 1000);
                }
                break;
            }
            case "guessFinal": {
                String text = snap.child("text").getValue(String.class);
                if (text != null) {
                    handler.post(() -> cb.onOpponentAttempt(text));
                    handler.postDelayed(() -> {
                        if (StringNormalizer.matches(text, puzzle.finalSolution))
                            cb.onOpponentGuessFinal();
                        else
                            cb.onOpponentDone();
                    }, 1000);
                }
                break;
            }
            case "done":
                handler.post(cb::onOpponentDone);
                break;
        }
    }

    @Override
    public void cancel() {
        if (activeListener != null && activeRef != null) {
            activeRef.removeEventListener(activeListener);
            activeListener = null;
            activeRef = null;
        }
    }
}

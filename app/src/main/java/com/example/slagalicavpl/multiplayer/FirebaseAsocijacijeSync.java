package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.AsocijacijePuzzle;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Firebase sync za Asocijacije.
 *
 * Path: /rooms/{roomId}/asocijacije/
 *
 * Protokol: akcije se pišu u redosled:
 *   /asocijacije/r{round}/actions/{seq} = { type, by, col, row, text }
 *
 * Lokalni igrač piše svoje akcije. Sync sluša i emituje opp akcije.
 *
 * Redosled igranja:
 *   Runda 1: P1 igra prvi (localFirst = "p1".equals(myRole))
 *   Runda 2: P2 igra prvi
 */
public class FirebaseAsocijacijeSync implements AsocijacijeSync {

    private final DatabaseReference ref;  // /rooms/{roomId}/asocijacije
    private final String myRole;
    private final String oppRole;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ValueEventListener activeListener;
    private DatabaseReference  activeRef;
    private int                seqCounter = 0;

    public FirebaseAsocijacijeSync(DatabaseReference roomRef, String myRole) {
        this.ref     = roomRef.child("asocijacije");
        this.myRole  = myRole;
        this.oppRole = "p1".equals(myRole) ? "p2" : "p1";
    }

    // ── Piše akciju lokalnog igrača ───────────────────────────────────────────

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
        String key = String.format("%04d", seqCounter++);
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
                                   boolean[][] cellOpened, Callback cb) {
        cancel();
        // Slušamo buduće akcije protivnika za tekući round
        // round se ne prenosi — koristimo globalnu putanju koja se briše na start runde
        activeRef = ref.child("oppAction");
        activeListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (!snap.exists()) return;
                String type = snap.child("type").getValue(String.class);
                String by   = snap.child("by").getValue(String.class);
                if (!oppRole.equals(by) || type == null) return;

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
                                if (text.trim().equalsIgnoreCase(solution))
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
                                if (text.trim().equalsIgnoreCase(puzzle.finalSolution))
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
            @Override public void onCancelled(DatabaseError e) {}
        };
        activeRef.addValueEventListener(activeListener);
    }

    /**
     * Piše akciju lokalnog igrača direktno u oppAction node (za protivnika da čita).
     * Ovo poziva FRAGMENT za svaki lokalni potez.
     */
    public void broadcastLocalAction(String type, int col, int row, String text) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("by",   myRole);
        m.put("col",  col);
        m.put("row",  row);
        if (text != null) m.put("text", text);
        m.put("ts", System.currentTimeMillis()); // force update even if same value
        ref.child("oppAction").setValue(m);
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

package com.example.slagalicavpl.multiplayer;

import android.os.Handler;
import android.os.Looper;

import com.example.slagalicavpl.model.ConnectPair;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firebase sync za Spojnice.
 *
 * Firebase paths (relative to /rooms/{roomId}/spojnice/):
 *   slots_r1, slots_r2          — P1 piše oba seta slotova na početku
 *   conn_p1_r1/{leftRow}        — P1-ove konekcije u rundi 1
 *   conn_p2_steal/{leftRow}     — P2-ovo krađenje iz runde 1
 *   conn_p2_r2/{leftRow}        — P2-ove konekcije u rundi 2
 *   conn_p1_steal/{leftRow}     — P1-ovo krađenje iz runde 2
 *   done/{key}                  — signali završetka faze (true)
 *
 * P1 (localStartsFirst=true) faze → startOpponentTurn:
 *   poziv 1 (R1_OPP): gleda conn_p2_steal + done/p2_steal
 *   poziv 2 (R2_OPP): gleda conn_p2_r2   + done/p2_r2
 *
 * P2 (localStartsFirst=false) faze → startOpponentTurn:
 *   poziv 1 (R1_LOCAL passive): gleda conn_p1_r1    + done/p1_r1
 *   poziv 2 (R2_LOCAL passive): gleda conn_p1_steal + done/p1_steal
 */
public class FirebaseSpojniceSync implements SpojniceSync {

    public interface SlotsReadyCallback {
        void onSlots(int[] slots1, int[] slots2);
    }

    private final DatabaseReference ref;   // /rooms/{roomId}/spojnice
    private final String myRole;           // "p1" or "p2"
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ValueEventListener connListener;
    private DatabaseReference  connRef;

    private int oppCallCount = 0;

    public FirebaseSpojniceSync(DatabaseReference roomRef, String myRole) {
        this.ref    = roomRef.child("spojnice");
        this.myRole = myRole;
    }

    // ── Slot sharing (P1 generates + writes both, P2 reads) ──────────────────

    public void writeAllSlots(int[] slots1, int[] slots2) {
        ref.child("slots_r1").setValue(slotsToMap(slots1));
        ref.child("slots_r2").setValue(slotsToMap(slots2));
    }

    public void readAllSlots(SlotsReadyCallback cb) {
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snap) {
                DataSnapshot s1 = snap.child("slots_r1");
                DataSnapshot s2 = snap.child("slots_r2");
                if (!s1.exists() || !s2.exists()) {
                    handler.postDelayed(() -> readAllSlots(cb), 300);
                    return;
                }
                int[] slots1 = readSlots(s1);
                int[] slots2 = readSlots(s2);
                handler.post(() -> cb.onSlots(slots1, slots2));
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private Map<String, Object> slotsToMap(int[] slots) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < slots.length; i++) m.put(String.valueOf(i), slots[i]);
        return m;
    }

    private int[] readSlots(DataSnapshot snap) {
        int[] s = new int[5];
        for (int i = 0; i < 5; i++) {
            Object v = snap.child(String.valueOf(i)).getValue();
            s[i] = v instanceof Number ? ((Number) v).intValue() : i;
        }
        return s;
    }

    // ── Connection write (lokalni igrač piše konekciju tokom aktivne faze) ───

    public void writeConnection(String connKey, int leftRow, int rightRow) {
        ref.child(connKey).child(String.valueOf(leftRow)).setValue(rightRow);
    }

    // ── Done signal (aktivni igrač signalizuje kraj faze) ─────────────────────

    public void writePhaseDone(String key) {
        ref.child("done").child(key).setValue(true);
    }

    // ── P1 inicijalizacija (piše placeholder da P2 zna da je P1 spreman) ─────

    public void initGameIfHost() {
        if ("p1".equals(myRole)) {
            ref.child("hostReady").setValue(true);
        }
    }

    // ── SpojniceSync interface ────────────────────────────────────────────────

    /**
     * Poziva engine kada ulazi u pasivnu fazu (opp igra).
     *
     * Za P1 (localStartsFirst=true):
     *   poziv 1 (R1_OPP) → čita conn_p2_steal + done/p2_steal
     *   poziv 2 (R2_OPP) → čita conn_p2_r2    + done/p2_r2
     *
     * Za P2 (localStartsFirst=false):
     *   poziv 1 (R1_LOCAL passive) → čita conn_p1_r1    + done/p1_r1
     *   poziv 2 (R2_LOCAL passive) → čita conn_p1_steal + done/p1_steal
     */
    @Override
    public void startOpponentTurn(List<ConnectPair> pairs, boolean[] connected,
                                  int[] rightSlots, ConnectCallback cb) {
        cancelConnListener();
        oppCallCount++;

        String connKey, doneKey;
        if ("p1".equals(myRole)) {
            connKey = (oppCallCount == 1) ? "conn_p2_steal" : "conn_p2_r2";
            doneKey = (oppCallCount == 1) ? "p2_steal"      : "p2_r2";
        } else {
            connKey = (oppCallCount == 1) ? "conn_p1_r1"    : "conn_p1_steal";
            doneKey = (oppCallCount == 1) ? "p1_r1"         : "p1_steal";
        }

        connRef = ref.child(connKey);
        // Use a local delivered[] so we don't modify the engine's connected[] prematurely.
        // The engine marks connected[] only inside processConnection after validating the pair.
        final boolean[] delivered = new boolean[5];
        connListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                for (DataSnapshot child : snap.getChildren()) {
                    try {
                        int left = Integer.parseInt(child.getKey());
                        if (left < 0 || left >= 5 || delivered[left]) continue;
                        Object v = child.getValue();
                        int right = v instanceof Number ? ((Number) v).intValue() : left;
                        delivered[left] = true;
                        handler.post(() -> cb.onOpponentConnect(left, right));
                    } catch (NumberFormatException ignored) {}
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        connRef.addValueEventListener(connListener);

        ref.child("done").child(doneKey).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                if (Boolean.TRUE.equals(snap.getValue(Boolean.class))) {
                    ref.child("done").child(doneKey).removeEventListener(this);
                    cancelConnListener();
                    handler.post(cb::onOpponentDone);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    @Override
    public void cancel() {
        cancelConnListener();
    }

    private void cancelConnListener() {
        if (connListener != null && connRef != null) {
            connRef.removeEventListener(connListener);
            connListener = null;
            connRef = null;
        }
    }
}

package com.example.slagalicavpl.repository;

import androidx.annotation.NonNull;

import com.example.slagalicavpl.model.Tournament;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class TournamentRepository {

    private static final String DB_URL =
            "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";

    private static TournamentRepository instance;
    private final DatabaseReference tourRef;
    // Fixed matchmaking node — all players transact here to avoid query race conditions.
    private final DatabaseReference matchRef;

    private TournamentRepository() {
        FirebaseDatabase db = FirebaseDatabase.getInstance(DB_URL);
        tourRef  = db.getReference("tournaments");
        matchRef = db.getReference("matchmaking/room");
    }

    public static synchronized TournamentRepository getInstance() {
        if (instance == null) instance = new TournamentRepository();
        return instance;
    }

    // ── Callback interfaces ───────────────────────────────────────────────────

    public interface JoinCallback {
        void onJoined(String tournamentId, int mySlot);
        void onError(String msg);
    }

    public interface TournamentListener {
        void onChanged(Tournament t);
    }

    // ── Join (transaction on single fixed matchmaking node) ───────────────────

    /**
     * Atomically joins or creates a tournament via a transaction on the shared
     * matchmaking/room node. This eliminates the query-based race condition where
     * all players simultaneously saw 0 results and each created their own tournament.
     */
    public void join(String uid, Tournament.TournamentPlayer player, JoinCallback cb) {
        // Pre-generate a tournament ID. The same ID is reused across retries because
        // it's closed over before the transaction starts.
        String preGenTid = tourRef.push().getKey();
        if (preGenTid == null) { cb.onError("Firebase nije dostupan"); return; }

        matchRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                String status = data.child("status").getValue(String.class);
                Long   count  = data.child("playerCount").getValue(Long.class);
                if (count == null) count = 0L;

                // Create a new slot when: no data, slot is full, or tournament already started
                boolean createNew = (data.getValue() == null)
                        || count <= 0
                        || count >= 4
                        || "starting".equals(status);

                if (createNew) {
                    for (String s : new String[]{"p1", "p2", "p3", "p4"})
                        data.child(s).setValue(null);
                    data.child("tournamentId").setValue(preGenTid);
                    data.child("status").setValue("open");
                    data.child("playerCount").setValue(1);
                    writePlayer(data.child("p1"), uid, player);
                    return Transaction.success(data);
                }

                // Don't double-join
                for (String s : new String[]{"p1", "p2", "p3", "p4"}) {
                    if (uid.equals(data.child(s).child("uid").getValue(String.class)))
                        return Transaction.abort();
                }

                long newCount = count + 1;
                writePlayer(data.child("p" + newCount), uid, player);
                data.child("playerCount").setValue(newCount);
                if (newCount >= 4) data.child("status").setValue("starting");
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot snap) {
                if (!committed || snap == null) {
                    cb.onError("Greška pri pridruživanju turniru");
                    return;
                }
                String tid = snap.child("tournamentId").getValue(String.class);
                if (tid == null) { cb.onError("Greška: nema ID turnira"); return; }

                int  mySlot = findMySlot(snap, uid);
                Long count  = snap.child("playerCount").getValue(Long.class);

                // Only the 4th player creates the stable tournament document used for
                // game coordination (reportWin / final transition).
                if (count != null && count >= 4) {
                    createTournamentDocument(tid, snap);
                }

                cb.onJoined(tid, mySlot);
            }
        });
    }

    private void writePlayer(MutableData slot, String uid, Tournament.TournamentPlayer p) {
        slot.child("uid").setValue(uid);
        slot.child("username").setValue(p.username    != null ? p.username    : "");
        slot.child("avatarColor").setValue(p.avatarColor != null ? p.avatarColor : "#5C85FF");
        slot.child("league").setValue(p.league);
    }

    private int findMySlot(DataSnapshot snap, String uid) {
        for (int i = 1; i <= 4; i++) {
            if (uid.equals(snap.child("p" + i).child("uid").getValue(String.class))) return i;
        }
        return 1;
    }

    private void createTournamentDocument(String tid, DataSnapshot snap) {
        Map<String, Object> t = new HashMap<>();
        t.put("status",      "semifinal");
        t.put("playerCount", 4);
        t.put("semi1Winner", "");
        t.put("semi2Winner", "");
        t.put("finalWinner", "");

        for (String s : new String[]{"p1", "p2", "p3", "p4"}) {
            DataSnapshot ps = snap.child(s);
            String pUid = ps.child("uid").getValue(String.class);
            if (pUid != null) {
                Map<String, Object> pm = new HashMap<>();
                pm.put("uid",         pUid);
                pm.put("username",    ps.child("username").getValue(String.class));
                pm.put("avatarColor", ps.child("avatarColor").getValue(String.class));
                pm.put("league",      ps.child("league").getValue(Long.class));
                t.put(s, pm);
            }
        }

        tourRef.child(tid).setValue(t);
    }

    // ── Listen (matchmaking lobby) ────────────────────────────────────────────

    /** Listens to the shared matchmaking node for lobby slot updates. */
    public ValueEventListener listenMatchmaking(TournamentListener listener) {
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Tournament t = snapshot.getValue(Tournament.class);
                if (t != null) listener.onChanged(t);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        matchRef.addValueEventListener(vel);
        return vel;
    }

    public void removeMatchmakingListener(ValueEventListener vel) {
        if (vel != null) matchRef.removeEventListener(vel);
    }

    // ── Listen (tournament game state) ────────────────────────────────────────

    /** Listens to the stable tournament document for semi/final state changes. */
    public ValueEventListener listen(String tournamentId, TournamentListener listener) {
        ValueEventListener vel = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Tournament t = snapshot.getValue(Tournament.class);
                if (t != null) listener.onChanged(t);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        tourRef.child(tournamentId).addValueEventListener(vel);
        return vel;
    }

    public void removeListener(String tournamentId, ValueEventListener vel) {
        if (vel != null) tourRef.child(tournamentId).removeEventListener(vel);
    }

    // ── Report win ────────────────────────────────────────────────────────────

    public void reportWin(String tournamentId, String phase, String winnerUid) {
        DatabaseReference ref = tourRef.child(tournamentId);
        if ("final".equals(phase)) {
            ref.runTransaction(new Transaction.Handler() {
                @NonNull @Override
                public Transaction.Result doTransaction(@NonNull MutableData data) {
                    data.child("finalWinner").setValue(winnerUid);
                    data.child("status").setValue("done");
                    return Transaction.success(data);
                }
                @Override public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {}
            });
        } else {
            String winField   = phase + "Winner";
            String otherField = "semi1".equals(phase) ? "semi2Winner" : "semi1Winner";
            ref.runTransaction(new Transaction.Handler() {
                @NonNull @Override
                public Transaction.Result doTransaction(@NonNull MutableData data) {
                    data.child(winField).setValue(winnerUid);
                    String other = data.child(otherField).getValue(String.class);
                    if (other != null && !other.isEmpty()) {
                        data.child("status").setValue("final");
                    }
                    return Transaction.success(data);
                }
                @Override public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {}
            });
        }
    }

    // ── Leave (lobby waiting phase only) ─────────────────────────────────────

    public void leave(String tournamentId, String uid, Runnable onDone) {
        matchRef.runTransaction(new Transaction.Handler() {
            @NonNull @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                // Abort if tournament already started or slot belongs to a different tournament
                if ("starting".equals(data.child("status").getValue(String.class)))
                    return Transaction.abort();
                String storedTid = data.child("tournamentId").getValue(String.class);
                if (storedTid == null || !storedTid.equals(tournamentId))
                    return Transaction.abort();
                Long count = data.child("playerCount").getValue(Long.class);
                if (count == null) return Transaction.abort();
                for (String s : new String[]{"p1", "p2", "p3", "p4"}) {
                    if (uid.equals(data.child(s).child("uid").getValue(String.class))) {
                        data.child(s).setValue(null);
                        break;
                    }
                }
                data.child("playerCount").setValue(Math.max(0, count - 1));
                return Transaction.success(data);
            }
            @Override
            public void onComplete(DatabaseError e, boolean c, DataSnapshot s) {
                if (onDone != null) onDone.run();
            }
        });
    }
}

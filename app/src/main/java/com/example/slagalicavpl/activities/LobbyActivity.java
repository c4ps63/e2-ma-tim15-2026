package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Matchmaking ekran.
 *
 * Algoritam (race-condition free):
 *   - Koristimo jedan "waiting slot": /matchmaking/waiting
 *   - Firebase Transaction atomično čita i menja slot:
 *       • Ako je slot prazan  → upišem sebe (ja sam P1, čekam sobu)
 *       • Ako ima nekog drugog → obrišem slot i kreiram sobu (ja sam P2)
 *   - P1 sluša /rooms/ dok se ne pojavi soba sa njegovim uid-om
 *   - P2 kreira sobu i odmah navigira
 */
public class LobbyActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_ID = "roomId";
    public static final String EXTRA_MY_ROLE = "myRole";

    private DatabaseReference waitingRef;
    private DatabaseReference roomsRef;
    private ValueEventListener roomListener;

    private String  myUid;
    private String  myUsername;
    private boolean navigating = false;

    // čuva podatke protivnika pronađene unutar Transaction.doTransaction()
    private volatile String pendingOpponentUid;
    private volatile String pendingOpponentName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        FirebaseUser user = UserRepository.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        myUid      = user.getUid();
        myUsername = user.getEmail() != null ? user.getEmail() : myUid;

        // pokušaj da učitamo pravo korisničko ime
        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override public void onLoaded(com.example.slagalicavpl.model.User u) {
                if (u.username != null) myUsername = u.username;
            }
            @Override public void onError(String msg) {}
        });

        TextView tvStatus = findViewById(R.id.tvLobbyStatus);
        tvStatus.setText("Tražim protivnika...");

        Button btnCancel = findViewById(R.id.btnLobbyCancel);
        btnCancel.setOnClickListener(v -> finish());

        waitingRef = FirebaseDatabase.getInstance("https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app").getReference("matchmaking/waiting");
        roomsRef   = FirebaseDatabase.getInstance("https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app").getReference("rooms");

        cleanupStaleRoomsAndMatch();
    }

    // ── Cleanup starih soba pa matchmaking ────────────────────────────────────

    private void cleanupStaleRoomsAndMatch() {
        roomsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot room : snapshot.getChildren()) {
                    String p1     = room.child("player1Uid").getValue(String.class);
                    String p2     = room.child("player2Uid").getValue(String.class);
                    String status = room.child("status").getValue(String.class);
                    // Označi kao finished sve sobe gde sam ja učesnik i status je još "active"
                    if ("active".equals(status)
                            && (myUid.equals(p1) || myUid.equals(p2))) {
                        room.getRef().child("status").setValue("finished");
                    }
                }
                startMatchmaking();
            }
            @Override
            public void onCancelled(DatabaseError e) {
                startMatchmaking(); // nastavi čak i ako cleanup ne uspe
            }
        });
    }

    // ── Matchmaking ───────────────────────────────────────────────────────────

    private void startMatchmaking() {
        waitingRef.runTransaction(new Transaction.Handler() {

            @Override
            public Transaction.Result doTransaction(MutableData data) {
                if (data.getValue() == null) {
                    // Slot je prazan — upiši sebe (postajemo P1)
                    Map<String, Object> me = new HashMap<>();
                    me.put("uid",      myUid);
                    me.put("username", myUsername);
                    data.setValue(me);
                    return Transaction.success(data);
                }

                // Slot ima nekoga
                Object raw = data.getValue();
                if (!(raw instanceof Map)) {
                    data.setValue(null);
                    return Transaction.success(data);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> existing = (Map<String, Object>) raw;
                String theirUid = (String) existing.get("uid");

                if (myUid.equals(theirUid)) {
                    // Naš vlastiti stari unos — ostavi kako jeste, čekamo
                    return Transaction.success(data);
                }

                // Pronašli smo protivnika! Zapamti ga i obriši slot.
                pendingOpponentUid  = theirUid;
                pendingOpponentName = (String) existing.get("username");
                data.setValue(null);
                return Transaction.success(data);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed,
                                   DataSnapshot currentData) {
                if (error != null || !committed) {
                    runOnUiThread(() ->
                        Toast.makeText(LobbyActivity.this,
                                "Greška pri traženju protivnika, pokušaj ponovo.",
                                Toast.LENGTH_SHORT).show());
                    return;
                }

                if (pendingOpponentUid != null) {
                    // Pronašli smo protivnika u transakciji → kreiramo sobu kao P2
                    createRoomAsP2(pendingOpponentUid, pendingOpponentName);
                } else {
                    // Upisali smo sebe u slot → čekamo kao P1
                    listenForRoomAsP1();
                }
            }
        });
    }

    // ── P2: kreira sobu i navigira ────────────────────────────────────────────

    private void createRoomAsP2(String p1Uid, String p1Name) {
        if (navigating) return;
        navigating = true;

        String roomId = p1Uid + "_" + myUid + "_" + System.currentTimeMillis();

        Map<String, Object> room = new HashMap<>();
        room.put("player1Uid",  p1Uid);
        room.put("player1Name", p1Name != null ? p1Name : p1Uid);
        room.put("player2Uid",  myUid);
        room.put("player2Name", myUsername);
        room.put("status",      "active");

        roomsRef.child(roomId).setValue(room)
            .addOnSuccessListener(v -> startGame(roomId, "p2"))
            .addOnFailureListener(e -> {
                navigating = false;
                Toast.makeText(this, "Greška pri kreiranju sobe", Toast.LENGTH_SHORT).show();
            });
    }

    // ── P1: čeka dok P2 ne kreira sobu ───────────────────────────────────────

    private void listenForRoomAsP1() {
        roomListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (navigating) return;
                for (DataSnapshot room : snapshot.getChildren()) {
                    String p1     = room.child("player1Uid").getValue(String.class);
                    String status = room.child("status").getValue(String.class);
                    if (myUid.equals(p1) && "active".equals(status)) {
                        navigating = true;
                        startGame(room.getKey(), "p1");
                        return;
                    }
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        roomsRef.addValueEventListener(roomListener);
    }

    // ── Navigacija ────────────────────────────────────────────────────────────

    private void startGame(String roomId, String myRole) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(EXTRA_ROOM_ID, roomId);
        intent.putExtra(EXTRA_MY_ROLE, myRole);
        startActivity(intent);
        finish();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomListener != null) roomsRef.removeEventListener(roomListener);

        // Ako nismo ušli u igru, obrišemo sebe iz waiting slota
        if (!navigating) {
            waitingRef.runTransaction(new Transaction.Handler() {
                @Override
                public Transaction.Result doTransaction(MutableData data) {
                    if (data.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> val = (Map<String, Object>) data.getValue();
                        if (myUid.equals(val.get("uid"))) {
                            data.setValue(null); // obriši samo ako smo mi u slotu
                        }
                    }
                    return Transaction.success(data);
                }
                @Override public void onComplete(DatabaseError e, boolean b, DataSnapshot d) {}
            });
        }
    }
}

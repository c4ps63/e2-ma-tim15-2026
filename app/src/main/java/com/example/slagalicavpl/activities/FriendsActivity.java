package com.example.slagalicavpl.activities;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.GameInvite;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.InviteRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Prikazuje listu prijatelja i omogućava slanje/primanje poziva za prijateljsku partiju.
 *
 * Firebase struktura:
 *   friends/{uid}/{friendUid} = true
 *   invites/{receiverUid}/{inviteId} = GameInvite
 */
public class FriendsActivity extends AppCompatActivity {

    private static final String DB_URL =
            "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String NOTIF_CHANNEL = "friend_invite";

    private String myUid;
    private String myUsername;

    private FriendAdapter adapter;
    private final List<User> friends = new ArrayList<>();

    private ValueEventListener inviteListener;
    private DatabaseReference  friendsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) { finish(); return; }
        myUid = fbUser.getUid();

        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override public void onLoaded(User u) {
                myUsername = u.username != null ? u.username : myUid;
            }
            @Override public void onError(String msg) { myUsername = myUid; }
        });

        RecyclerView rv = findViewById(R.id.rvFriends);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendAdapter(friends);
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddFriend).setOnClickListener(v -> showAddFriendDialog());

        friendsRef = FirebaseDatabase.getInstance(DB_URL).getReference("friends").child(myUid);
        loadFriends();
        listenForIncomingInvites();
        createNotifChannel();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inviteListener != null)
            InviteRepository.getInstance().removeListener(myUid, inviteListener);
    }

    // ── Učitavanje prijatelja ─────────────────────────────────────────────────

    private void loadFriends() {
        friendsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                friends.clear();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String friendUid = child.getKey();
                    if (friendUid == null) continue;
                    UserRepository.getInstance().loadProfile(friendUid,
                            new UserRepository.ProfileCallback() {
                        @Override public void onLoaded(User u) {
                            friends.add(u);
                            adapter.notifyDataSetChanged();
                        }
                        @Override public void onError(String msg) {}
                    });
                }
                if (!snapshot.hasChildren()) adapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    // ── Dodavanje prijatelja ──────────────────────────────────────────────────

    private void showAddFriendDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_friend, null);
        EditText etUsername = v.findViewById(R.id.etFriendUsername);
        new AlertDialog.Builder(this)
                .setTitle("Dodaj prijatelja")
                .setView(v)
                .setPositiveButton("Dodaj", (d, w) -> {
                    String username = etUsername.getText().toString().trim();
                    if (username.isEmpty()) return;
                    addFriendByUsername(username);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void addFriendByUsername(String username) {
        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener((QuerySnapshot qs) -> {
                    if (qs.isEmpty()) {
                        Toast.makeText(this, "Korisnik nije pronađen", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    User friend = qs.getDocuments().get(0).toObject(User.class);
                    if (friend == null || myUid.equals(friend.uid)) {
                        Toast.makeText(this, "Neispravan korisnik", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DatabaseReference db = FirebaseDatabase.getInstance(DB_URL).getReference("friends");
                    db.child(myUid).child(friend.uid).setValue(true);
                    db.child(friend.uid).child(myUid).setValue(true);
                    Toast.makeText(this, friend.username + " dodat kao prijatelj!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // ── Slanje poziva ─────────────────────────────────────────────────────────

    private void sendInviteTo(User friend) {
        String inviteId = UUID.randomUUID().toString();
        GameInvite invite = new GameInvite(inviteId, myUid,
                myUsername != null ? myUsername : myUid, friend.uid);

        InviteRepository.getInstance().sendInvite(invite, new InviteRepository.Callback() {
            @Override public void onSuccess() {
                Toast.makeText(FriendsActivity.this,
                        "Poziv poslat " + friend.username, Toast.LENGTH_SHORT).show();
                // push notifikacija ako prijatelj nije u aplikaciji
                sendPushStyleNotif(friend.username, inviteId);
                waitForInviteResponse(inviteId, friend);
            }
            @Override public void onError(String msg) {
                Toast.makeText(FriendsActivity.this, "Greška pri slanju poziva", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void waitForInviteResponse(String inviteId, User friend) {
        DatabaseReference ref = FirebaseDatabase.getInstance(DB_URL)
                .getReference("invites").child(friend.uid).child(inviteId);
        ref.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    ref.removeEventListener(this);
                    InviteRepository.getInstance().deleteInvite(friend.uid, inviteId);
                    launchFriendlyGameAsSender(friend.uid, friend.username, inviteId);
                } else if ("declined".equals(status)) {
                    ref.removeEventListener(this);
                    InviteRepository.getInstance().deleteInvite(friend.uid, inviteId);
                    runOnUiThread(() -> Toast.makeText(FriendsActivity.this,
                            friend.username + " je odbio poziv", Toast.LENGTH_SHORT).show());
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    private void launchFriendlyGameAsSender(String friendUid, String friendName, String inviteId) {
        String roomId = myUid + "_" + friendUid + "_" + inviteId;
        DatabaseReference roomsRef = FirebaseDatabase.getInstance(DB_URL).getReference("rooms");
        Map<String, Object> room = new HashMap<>();
        room.put("player1Uid",  myUid);
        room.put("player1Name", myUsername);
        room.put("player2Uid",  friendUid);
        room.put("player2Name", friendName);
        room.put("status",      "active");
        room.put("friendly",    true);
        roomsRef.child(roomId).setValue(room).addOnSuccessListener(v -> {
            Intent intent = new Intent(this, GameActivity.class);
            intent.putExtra(LobbyActivity.EXTRA_ROOM_ID,     roomId);
            intent.putExtra(LobbyActivity.EXTRA_MY_ROLE,     "p1");
            intent.putExtra(LobbyActivity.EXTRA_IS_FRIENDLY, true);
            startActivity(intent);
        });
    }

    // ── Prijem poziva ─────────────────────────────────────────────────────────

    private void listenForIncomingInvites() {
        inviteListener = InviteRepository.getInstance().listenForInvites(myUid, invites -> {
            for (GameInvite inv : invites) {
                showInviteDialog(inv);
            }
        });
    }

    private void showInviteDialog(GameInvite inv) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Poziv za igru")
                    .setMessage(inv.senderName + " te poziva na prijateljsku partiju!")
                    .setPositiveButton("Prihvati", (d, w) -> acceptInvite(inv))
                    .setNegativeButton("Odbij",    (d, w) -> declineInvite(inv))
                    .setCancelable(false)
                    .show();
        });
    }

    private void acceptInvite(GameInvite inv) {
        InviteRepository.getInstance().respondToInvite(myUid, inv.inviteId, "accepted",
                new InviteRepository.Callback() {
            @Override public void onSuccess() {
                // Soba je već kreirana od strane pošiljaoca — samo se pridruži
                String roomId = inv.senderUid + "_" + myUid + "_" + inv.inviteId;
                Intent intent = new Intent(FriendsActivity.this, GameActivity.class);
                intent.putExtra(LobbyActivity.EXTRA_ROOM_ID,     roomId);
                intent.putExtra(LobbyActivity.EXTRA_MY_ROLE,     "p2");
                intent.putExtra(LobbyActivity.EXTRA_IS_FRIENDLY, true);
                startActivity(intent);
            }
            @Override public void onError(String msg) {}
        });
    }

    private void declineInvite(GameInvite inv) {
        InviteRepository.getInstance().respondToInvite(myUid, inv.inviteId, "declined",
                new InviteRepository.Callback() {
            @Override public void onSuccess() {
                InviteRepository.getInstance().deleteInvite(myUid, inv.inviteId);
            }
            @Override public void onError(String msg) {}
        });
    }

    // ── Sistemska notifikacija (kad prijatelj nije u igri) ────────────────────

    private void sendPushStyleNotif(String friendUsername, String inviteId) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, FriendsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, NOTIF_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Poziv za igru")
                .setContentText(myUsername + " te poziva na prijateljsku partiju!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(inviteId.hashCode(), nb.build());
    }

    private void createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    NOTIF_CHANNEL, "Pozivi za igru", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class FriendAdapter extends RecyclerView.Adapter<FriendAdapter.VH> {
        private final List<User> data;
        FriendAdapter(List<User> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_friend, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            User u = data.get(pos);
            h.tvName.setText(u.username != null ? u.username : "—");
            h.tvStars.setText("⭐ " + u.stars);
            h.btnInvite.setOnClickListener(v -> sendInviteTo(u));
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName, tvStars;
            Button   btnInvite;
            VH(View v) {
                super(v);
                tvName    = v.findViewById(R.id.tvFriendName);
                tvStars   = v.findViewById(R.id.tvFriendStars);
                btnInvite = v.findViewById(R.id.btnInviteFriend);
            }
        }
    }
}

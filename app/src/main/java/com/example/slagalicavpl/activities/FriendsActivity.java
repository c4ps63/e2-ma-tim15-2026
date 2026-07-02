package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.GameInvite;
import com.example.slagalicavpl.model.LeagueUtil;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.InviteRepository;
import com.example.slagalicavpl.repository.RankingRepository;
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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanIntentResult;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lista prijatelja, dodavanje po imenu ili QR kodu (7a/7b),
 * prikaz detalja prijatelja (7c), slanje/primanje poziva s
 * auto-odbijanjem (7d) i otkazivanjem (7e).
 *
 * Firebase struktura:
 *   RTDB  friends/{uid}/{friendUid} = true
 *   RTDB  invites/{receiverUid}/{inviteId} = GameInvite
 *   RTDB  rooms/{roomId} = room data
 */
public class FriendsActivity extends AppCompatActivity {

    private static final String DB_URL    = "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app";
    private static final long   ONLINE_MS = 5 * 60 * 1_000L;

    private String myUid;
    private String myUsername;

    private FriendAdapter      adapter;
    private final List<User>   friends      = new ArrayList<>();
    private final Set<String>  shownInvites = new HashSet<>();

    private ValueEventListener inviteListener;
    private DatabaseReference  friendsRef;

    // Mesečni rang: uid → pozicija (#1, #2, ...)
    private final Map<String, Integer> monthlyRanks = new HashMap<>();

    // Stanje čekanja na odgovor (7e — otkazivanje)
    private AlertDialog        waitingDialog;
    private ValueEventListener responseListener;
    private DatabaseReference  responseRef;

    // QR scanner (4.3.0 ActivityResult API)
    private ActivityResultLauncher<ScanOptions> qrLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Registracija mora biti prije onStart
        qrLauncher = registerForActivityResult(new ScanContract(), this::onQrScanned);

        setContentView(R.layout.activity_friends);

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) { finish(); return; }
        myUid = fbUser.getUid();

        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override public void onLoaded(User u) { myUsername = u.username != null ? u.username : myUid; }
            @Override public void onError(String msg) { myUsername = myUid; }
        });

        RecyclerView rv = findViewById(R.id.rvFriends);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendAdapter(friends);
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        // "+ DODAJ" sada otvara izbor između pretrage, skeniranja i mog QR-a
        findViewById(R.id.btnAddFriend).setOnClickListener(v -> showAddOptions());

        friendsRef = FirebaseDatabase.getInstance(DB_URL).getReference("friends").child(myUid);
        loadFriends();
        loadMonthlyRanks();
        listenForIncomingInvites();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inviteListener != null)
            InviteRepository.getInstance().removeListener(myUid, inviteListener);
    }

    // ── 7a — Lista prijatelja ─────────────────────────────────────────────────

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
                            if (u.uid == null || u.uid.isEmpty()) u.uid = friendUid;
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

    // ── 7c — Mesečni rang ─────────────────────────────────────────────────────

    /** Učitava mesečnu rang listu jednom i mapuje uid → poziciju. */
    private void loadMonthlyRanks() {
        RankingRepository.getInstance().loadOnce(
                "monthly",
                RankingRepository.getCurrentMonthKey(),
                entries -> {
                    monthlyRanks.clear();
                    for (int i = 0; i < entries.size(); i++) {
                        if (entries.get(i).uid != null)
                            monthlyRanks.put(entries.get(i).uid, i + 1);
                    }
                    runOnUiThread(() -> adapter.notifyDataSetChanged());
                });
    }

    // ── 7b — Dodavanje prijatelja ─────────────────────────────────────────────

    /** Prikazuje izbor načina dodavanja. */
    private void showAddOptions() {
        String[] opcije = { "Pretraži po korisničkom imenu", "Skeniraj QR kod", "Moj QR kod" };
        new AlertDialog.Builder(this)
                .setTitle("Dodaj prijatelja")
                .setItems(opcije, (d, which) -> {
                    if      (which == 0) showSearchByNameDialog();
                    else if (which == 1) startQrScan();
                    else                 showMyQrDialog();
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void showSearchByNameDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_friend, null);
        EditText et = v.findViewById(R.id.etFriendUsername);
        new AlertDialog.Builder(this)
                .setTitle("Pretraži po imenu")
                .setView(v)
                .setPositiveButton("Dodaj", (d, w) -> {
                    String uname = et.getText().toString().trim();
                    if (!uname.isEmpty()) addFriendByUsername(uname);
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
                    String friendUid = qs.getDocuments().get(0).getId();
                    User friend = qs.getDocuments().get(0).toObject(User.class);
                    if (friend == null || myUid.equals(friendUid)) {
                        Toast.makeText(this, "Neispravan korisnik", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    persistFriendship(friendUid, friend.username);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Greška: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void persistFriendship(String friendUid, String displayName) {
        DatabaseReference db = FirebaseDatabase.getInstance(DB_URL).getReference("friends");
        db.child(myUid).child(friendUid).setValue(true);
        db.child(friendUid).child(myUid).setValue(true);
        String name = displayName != null ? displayName : "Igrač";
        Toast.makeText(this, name + " dodat kao prijatelj!", Toast.LENGTH_SHORT).show();
    }

    // ── 7b — QR kod ──────────────────────────────────────────────────────────

    /** Prikazuje QR koji enkoduje moj UID — drugi igrač ga skenira da me doda. */
    private void showMyQrDialog() {
        if (myUid == null) return;
        Bitmap qr = generateQrBitmap(myUid, 512);
        if (qr == null) {
            Toast.makeText(this, "Greška pri generisanju QR koda", Toast.LENGTH_SHORT).show();
            return;
        }
        int pad = (int) (getResources().getDisplayMetrics().density * 32);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(qr);
        iv.setPadding(pad, pad, pad, pad);
        new AlertDialog.Builder(this)
                .setTitle("Moj QR kod")
                .setMessage("Pokaži ovaj kod prijatelju da te doda.")
                .setView(iv)
                .setPositiveButton("OK", null)
                .show();
    }

    private void startQrScan() {
        ScanOptions opts = new ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Skeniraj QR kod prijatelja")
                .setOrientationLocked(true)
                .setBeepEnabled(false);
        qrLauncher.launch(opts);
    }

    private void onQrScanned(ScanIntentResult result) {
        if (result.getContents() == null) return;
        String scannedUid = result.getContents().trim();
        if (scannedUid.isEmpty() || scannedUid.equals(myUid)) {
            Toast.makeText(this, "Neispravan QR kod", Toast.LENGTH_SHORT).show();
            return;
        }
        // Provjeri postoji li korisnik u Firestore-u
        FirebaseFirestore.getInstance()
                .collection("users").document(scannedUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        persistFriendship(scannedUid, doc.getString("username"));
                    } else {
                        Toast.makeText(this, "Korisnik nije pronađen", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Bitmap generateQrBitmap(String content, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bmp.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    // ── 7c/7e — Slanje poziva ─────────────────────────────────────────────────

    private void sendInviteTo(User friend) {
        String inviteId = UUID.randomUUID().toString();
        GameInvite invite = new GameInvite(inviteId, myUid,
                myUsername != null ? myUsername : myUid, friend.uid);

        InviteRepository.getInstance().sendInvite(invite, new InviteRepository.Callback() {
            @Override public void onSuccess() {
                Toast.makeText(FriendsActivity.this,
                        "Poziv poslat " + friend.username, Toast.LENGTH_SHORT).show();
                sendPushStyleNotif(friend.uid, inviteId);
                showWaitingDialog(friend, inviteId);
                waitForInviteResponse(inviteId, friend);
            }
            @Override public void onError(String msg) {
                Toast.makeText(FriendsActivity.this, "Greška pri slanju poziva", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 7e — Dialog "Čekam odgovor" s dugmetom Otkaži. */
    private void showWaitingDialog(User friend, String inviteId) {
        waitingDialog = new AlertDialog.Builder(this)
                .setTitle("Poziv poslat")
                .setMessage("Čekam odgovor od " + friend.username + "...")
                .setNegativeButton("Otkaži poziv", (d, w) -> cancelSentInvite(friend.uid, inviteId))
                .setCancelable(false)
                .show();
    }

    private void cancelSentInvite(String friendUid, String inviteId) {
        InviteRepository.getInstance().deleteInvite(friendUid, inviteId);
        detachResponseListener();
        dismissWaitingDialog();
        Toast.makeText(this, "Poziv otkazan", Toast.LENGTH_SHORT).show();
    }

    private void detachResponseListener() {
        if (responseRef != null && responseListener != null) {
            responseRef.removeEventListener(responseListener);
        }
        responseListener = null;
        responseRef      = null;
    }

    private void dismissWaitingDialog() {
        if (waitingDialog != null && waitingDialog.isShowing()) waitingDialog.dismiss();
        waitingDialog = null;
    }

    private void waitForInviteResponse(String inviteId, User friend) {
        responseRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("invites").child(friend.uid).child(inviteId);
        responseListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String status = snapshot.child("status").getValue(String.class);
                if ("accepted".equals(status)) {
                    detachResponseListener();
                    InviteRepository.getInstance().deleteInvite(friend.uid, inviteId);
                    dismissWaitingDialog();
                    launchFriendlyGameAsSender(friend.uid, friend.username, inviteId);
                } else if ("declined".equals(status)) {
                    detachResponseListener();
                    InviteRepository.getInstance().deleteInvite(friend.uid, inviteId);
                    dismissWaitingDialog();
                    runOnUiThread(() -> Toast.makeText(FriendsActivity.this,
                            friend.username + " je odbio poziv", Toast.LENGTH_SHORT).show());
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        responseRef.addValueEventListener(responseListener);
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

    // ── 7d — Prijem poziva s odbrojavanjem ───────────────────────────────────

    private void listenForIncomingInvites() {
        inviteListener = InviteRepository.getInstance().listenForInvites(myUid, invites -> {
            for (GameInvite inv : invites) {
                if (!shownInvites.contains(inv.inviteId)) {
                    shownInvites.add(inv.inviteId);
                    showInviteDialog(inv);
                }
            }
        });
    }

    /** 7d — Dialog s odbrojavanjem 10s za auto-odbijanje. */
    private void showInviteDialog(GameInvite inv) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;

            int dp = (int) getResources().getDisplayMetrics().density;
            TextView tvMsg = new TextView(this);
            tvMsg.setPadding(24 * dp, 16 * dp, 24 * dp, 8 * dp);
            tvMsg.setTextSize(14f);
            tvMsg.setText(inv.senderName + " te poziva na prijateljsku partiju!\n\nAuto-odbijanje za 10s");

            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle("Poziv za igru")
                    .setView(tvMsg)
                    .setPositiveButton("Prihvati", (d, w) -> acceptInvite(inv))
                    .setNegativeButton("Odbij", (d, w) -> {
                        shownInvites.remove(inv.inviteId);
                        declineInvite(inv);
                    })
                    .setCancelable(false)
                    .show();

            new CountDownTimer(10_000, 1_000) {
                public void onTick(long ms) {
                    if (!dlg.isShowing()) { cancel(); return; }
                    tvMsg.setText(inv.senderName + " te poziva na prijateljsku partiju!\n\nAuto-odbijanje za " + (ms / 1000 + 1) + "s");
                }
                public void onFinish() {
                    if (dlg.isShowing()) {
                        dlg.dismiss();
                        declineInvite(inv);
                    }
                }
            }.start();
        });
    }

    private void acceptInvite(GameInvite inv) {
        InviteRepository.getInstance().respondToInvite(myUid, inv.inviteId, "accepted",
                new InviteRepository.Callback() {
            @Override public void onSuccess() {
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

    // ── Notifikacija (kada prijatelj nije u aplikaciji) ───────────────────────

    private void sendPushStyleNotif(String friendUid, String inviteId) {
        Map<String, Object> notif = new HashMap<>();
        notif.put("id",        inviteId);
        notif.put("channel",   "OTHER");
        notif.put("title",     "Poziv za igru");
        notif.put("body",      (myUsername != null ? myUsername : "Prijatelj")
                + " te poziva na prijateljsku partiju!");
        notif.put("action",    "friendly_invite");
        notif.put("timestamp", System.currentTimeMillis());

        FirebaseDatabase.getInstance(DB_URL)
                .getReference("notifications")
                .child(friendUid)
                .child(inviteId)
                .setValue(notif);
    }

    // ── 7c — Adapter (avatar, liga, online status) ────────────────────────────

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

            // Ime
            h.tvName.setText(u.username != null ? u.username : "—");

            // Avatar — inicijal + boja iz profila
            String initial = (u.username != null && !u.username.isEmpty())
                    ? String.valueOf(Character.toUpperCase(u.username.charAt(0))) : "?";
            h.tvAvatar.setText(initial);
            int avatarColor;
            try {
                avatarColor = u.avatarColor != null
                        ? Color.parseColor(u.avatarColor) : Color.parseColor("#5C85FF");
            } catch (Exception e) {
                avatarColor = Color.parseColor("#5C85FF");
            }
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(avatarColor);
            h.viewAvatar.setBackground(circle);

            // Liga
            h.tvLeague.setText(LeagueUtil.getLabel(LeagueUtil.getLeague(u.stars)));

            // Ukupne zvijezde + mesečni rang
            Integer rank = u.uid != null ? monthlyRanks.get(u.uid) : null;
            String rankStr = rank != null ? "#" + rank : "—";
            h.tvInfo.setText("⭐ " + u.stars + "   📅 mes.rang: " + rankStr);

            // Online status: lastSeen unutar 5 minuta
            boolean online = u.lastSeen > 0
                    && (System.currentTimeMillis() - u.lastSeen) < ONLINE_MS;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(online ? Color.parseColor("#4CAF50") : Color.parseColor("#9E9E9E"));
            h.viewOnlineDot.setBackground(dot);

            // POZOVI aktivno samo kada je igrač online
            h.btnInvite.setEnabled(online);
            h.btnInvite.setAlpha(online ? 1f : 0.35f);
            h.btnInvite.setOnClickListener(online ? v -> sendInviteTo(u) : null);
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            View     viewAvatar, viewOnlineDot;
            TextView tvAvatar, tvName, tvLeague, tvInfo;
            Button   btnInvite;
            VH(View v) {
                super(v);
                viewAvatar    = v.findViewById(R.id.viewFriendAvatar);
                tvAvatar      = v.findViewById(R.id.tvFriendAvatar);
                tvName        = v.findViewById(R.id.tvFriendName);
                tvLeague      = v.findViewById(R.id.tvFriendLeague);
                tvInfo        = v.findViewById(R.id.tvFriendInfo);
                viewOnlineDot = v.findViewById(R.id.viewOnlineDot);
                btnInvite     = v.findViewById(R.id.btnInviteFriend);
            }
        }
    }
}

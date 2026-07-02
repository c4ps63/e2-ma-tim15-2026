package com.example.slagalicavpl.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.model.LeagueUtil;
import com.example.slagalicavpl.repository.MissionRepository;
import com.example.slagalicavpl.model.ChatMessage;
import com.example.slagalicavpl.model.GameInvite;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.ChatRepository;
import com.example.slagalicavpl.repository.InviteRepository;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    private String             myUid;
    private ValueEventListener inviteListener;
    private final Set<String>  shownInvites = new HashSet<>();

    private NotificationRepository notifRepo;
    private TextView               tvNotifBadge;

    // Chat listener za notifikacije dok korisnik nije u ChatActivity
    private ListenerRegistration   chatListener;
    private long                   chatListenerStartTime;
    private String                 lastNotifiedChatMsgId = "";
    private String                 userRegion            = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        notifRepo    = NotificationRepository.getInstance(this);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);

        FirebaseUser fu = AuthService.getInstance().getCurrentUser();
        if (fu != null) myUid = fu.getUid();

        claimDailyTokens();
        checkMonthlyPenalty();
        loadUserProfile();
        setupNavigation();
        listenForInvites();
        requestNotificationPermission();

        if (myUid != null) {
            notifRepo.listenRemote(myUid);
            UserRepository.getInstance().updateLastSeen(myUid);
            registerFcmToken();
        }
    }

    /** Upisuje trenutni FCM token u korisnikov profil da bi Cloud Function mogla da mu šalje push. */
    private void registerFcmToken() {
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    if (myUid != null) UserRepository.getInstance().saveFcmToken(myUid, token);
                });
    }

    /** Android 13+ zahteva runtime dozvolu za prikaz sistemskih notifikacija. */
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
        updateNotifBadge();
        updateMissionsProgress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myUid != null && inviteListener != null)
            InviteRepository.getInstance().removeListener(myUid, inviteListener);
        stopChatNotifListener();
        notifRepo.stopRemoteListener();
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    private void listenForInvites() {
        if (myUid == null) return;
        inviteListener = InviteRepository.getInstance().listenForInvites(myUid,
                (List<GameInvite> invites) -> {
                    for (GameInvite inv : invites) showInviteDialog(inv);
                });
    }

    private void showInviteDialog(GameInvite inv) {
        if (isFinishing() || isDestroyed()) return;
        if (shownInvites.contains(inv.inviteId)) return;
        shownInvites.add(inv.inviteId);

        // Sačuvaj poziv kao notifikaciju (bez push-a jer je dialog već vidljiv)
        AppNotification inviteNotif = AppNotification.create(
                AppNotification.Channel.OTHER,
                inv.senderName + " te poziva na partiju!",
                "Primio si poziv za prijateljsku partiju.",
                "friend_invite");
        inviteNotif.id = "invite_" + inv.inviteId;
        notifRepo.addIfAbsent(inviteNotif);
        updateNotifBadge();

        new AlertDialog.Builder(this)
                .setTitle("Poziv za igru")
                .setMessage(inv.senderName + " te poziva na prijateljsku partiju!")
                .setPositiveButton("Prihvati", (d, w) -> acceptInvite(inv))
                .setNegativeButton("Odbij", (d, w) -> {
                    shownInvites.remove(inv.inviteId);
                    declineInvite(inv);
                })
                .setCancelable(false)
                .show();
    }

    private void acceptInvite(GameInvite inv) {
        InviteRepository.getInstance().respondToInvite(myUid, inv.inviteId, "accepted",
                new InviteRepository.Callback() {
            @Override public void onSuccess() {
                String roomId = inv.senderUid + "_" + myUid + "_" + inv.inviteId;
                Intent intent = new Intent(HomeActivity.this, GameActivity.class);
                intent.putExtra(LobbyActivity.EXTRA_ROOM_ID,     roomId);
                intent.putExtra(LobbyActivity.EXTRA_MY_ROLE,     "p2");
                intent.putExtra(LobbyActivity.EXTRA_IS_FRIENDLY, true);
                startActivity(intent);
            }
            @Override public void onError(String msg) {}
        });
    }

    private void declineInvite(GameInvite inv) {
        InviteRepository.getInstance().deleteInvite(myUid, inv.inviteId);
    }

    // ── Chat listener (notifikacije kada nije otvorena ChatActivity) ──────────

    private void startChatNotifListener(String region) {
        stopChatNotifListener();
        chatListenerStartTime = System.currentTimeMillis();
        chatListener = ChatRepository.getInstance().listenMessages(region, messages -> {
            if (ChatActivity.isOpen || messages.isEmpty()) return;
            ChatMessage last = messages.get(messages.size() - 1);
            if (myUid != null && myUid.equals(last.senderId)) return;
            if (last.timestamp <= chatListenerStartTime) return;
            String notifId = last.id != null ? "chat_" + last.id : null;
            if (notifId == null || notifId.equals(lastNotifiedChatMsgId)) return;
            if (notifRepo.containsId(notifId)) return;
            lastNotifiedChatMsgId = notifId;

            AppNotification n = AppNotification.create(
                    AppNotification.Channel.CHAT,
                    last.senderName + ": " + last.text,
                    "Nova poruka u četu regiona",
                    "chat");
            n.id = notifId;
            notifRepo.add(n);
            updateNotifBadge();
        });
    }

    private void stopChatNotifListener() {
        if (chatListener != null) {
            chatListener.remove();
            chatListener = null;
        }
    }

    // ── Profil i dnevni bonus ─────────────────────────────────────────────────

    private void claimDailyTokens() {
        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        UserRepository.getInstance().claimDailyTokensIfNeeded(
                firebaseUser.getUid(), today,
                new UserRepository.DailyTokenCallback() {
                    @Override public void onClaimed(int tokensGiven) {
                        notifRepo.add(AppNotification.create(
                                AppNotification.Channel.REWARD,
                                "+" + tokensGiven + " žetona — dnevni bonus!",
                                "Prijavom danas zaradio si " + tokensGiven + " žetona. Vrati se sutra!",
                                "reward"));
                        updateNotifBadge();
                    }
                    @Override public void onAlreadyClaimed() {}
                });
    }

    private void checkMonthlyPenalty() {
        if (myUid == null) return;
        UserRepository.getInstance().applyMonthlyPenaltyIfNeeded(myUid, (oldLeague, newLeague) -> {
            if (isFinishing() || isDestroyed()) return;
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Pad u ligi!")
                    .setMessage("Nisi se plasirao prošlog mjeseca — izgubio si 30% zvezda.\n"
                            + LeagueUtil.getLabel(oldLeague) + " → " + LeagueUtil.getLabel(newLeague))
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void loadUserProfile() {
        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        UserRepository.getInstance().loadProfile(firebaseUser.getUid(),
                new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                TextView tvTokens     = findViewById(R.id.tvTokens);
                TextView tvStars      = findViewById(R.id.tvStars);
                TextView tvLeague     = findViewById(R.id.tvLeague);
                TextView tvLeagueIcon = findViewById(R.id.tvLeagueIcon);
                TextView tvAvatar     = findViewById(R.id.tvAvatarLetter);

                int league = LeagueUtil.getLeague(u.stars);
                tvTokens.setText(String.valueOf(u.tokens));
                tvStars.setText(String.valueOf(u.stars));
                tvLeague.setText(LeagueUtil.getName(league));
                if (tvLeagueIcon != null) tvLeagueIcon.setText(LeagueUtil.getIcon(league));

                if (u.username != null && !u.username.isEmpty())
                    tvAvatar.setText(String.valueOf(u.username.charAt(0)).toUpperCase());

                // Pokreni chat listener jednom kada znamo region
                if (chatListener == null && u.region != null && !u.region.isEmpty()) {
                    userRegion = u.region;
                    startChatNotifListener(userRegion);
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    // ── Navigacija ────────────────────────────────────────────────────────────

    private void tryStartOnlineGame() {
        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        UserRepository.getInstance().loadProfile(firebaseUser.getUid(),
                new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                if (u.tokens <= 0) {
                    Toast.makeText(HomeActivity.this,
                            "Nemaš tokena! Dobijaš 5 tokena svakog novog dana.",
                            Toast.LENGTH_LONG).show();
                } else {
                    startActivity(new Intent(HomeActivity.this, LobbyActivity.class));
                }
            }
            @Override public void onError(String msg) {
                startActivity(new Intent(HomeActivity.this, LobbyActivity.class));
            }
        });
    }

    private void setupNavigation() {
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        // Zvonce → NotificationsActivity
        findViewById(R.id.btnNotifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        // Glavni CTA -> ranked matchmaking
        findViewById(R.id.btnPlayOnline).setOnClickListener(v -> tryStartOnlineGame());

        // IGRAJ -> bottom sheet
        findViewById(R.id.btnPlay).setOnClickListener(v -> showPlaySheet());

        // Misije
        findViewById(R.id.btnMissions).setOnClickListener(v ->
                startActivity(new Intent(this, MissionsActivity.class)));

        // Donje prečice
        findViewById(R.id.navPrijatelji).setOnClickListener(v ->
                startActivity(new Intent(this, FriendsActivity.class)));
        findViewById(R.id.navRang).setOnClickListener(v ->
                startActivity(new Intent(this, RankingActivity.class)));
        findViewById(R.id.navRegioni).setOnClickListener(v ->
                startActivity(new Intent(this, RegionActivity.class)));
        findViewById(R.id.navCet).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
    }

    private void showPlaySheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View sheet = LayoutInflater.from(this).inflate(R.layout.sheet_play, null);

        sheet.findViewById(R.id.optFriendly).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, FriendsActivity.class));
        });
        sheet.findViewById(R.id.optChallenge).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, ChallengeListActivity.class));
        });
        sheet.findViewById(R.id.optTournament).setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, TournamentLobbyActivity.class));
        });

        dialog.setContentView(sheet);
        dialog.show();
    }

    private void updateNotifBadge() {
        if (tvNotifBadge == null || notifRepo == null) return;
        int count = notifRepo.getUnreadCount();
        if (count > 0) {
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }

    private void updateMissionsProgress() {
        TextView tv = findViewById(R.id.tvMissionsProgress);
        if (tv == null) return;
        int completed = MissionRepository.getInstance(this).getCompletedCount();
        tv.setText(completed + "/4 misija");
    }
}

package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.GameInvite;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.InviteRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HomeActivity extends AppCompatActivity {

    private String              myUid;
    private ValueEventListener  inviteListener;
    private final Set<String>   shownInvites = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        FirebaseUser fu = AuthService.getInstance().getCurrentUser();
        if (fu != null) myUid = fu.getUid();

        claimDailyTokens();
        loadUserProfile();
        setupNavigation();
        listenForInvites();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserProfile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (myUid != null && inviteListener != null)
            InviteRepository.getInstance().removeListener(myUid, inviteListener);
    }

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
        // Delete immediately so the listener never re-delivers this invite
        InviteRepository.getInstance().deleteInvite(myUid, inv.inviteId);
    }

    private void claimDailyTokens() {
        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) return;
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        UserRepository.getInstance().claimDailyTokensIfNeeded(firebaseUser.getUid(), today);
    }

    private void loadUserProfile() {
        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) return;

        UserRepository.getInstance().loadProfile(firebaseUser.getUid(),
                new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                TextView tvTokens = findViewById(R.id.tvTokens);
                TextView tvStars  = findViewById(R.id.tvStars);
                TextView tvLeague = findViewById(R.id.tvLeague);
                TextView tvAvatar = findViewById(R.id.tvAvatarLetter);

                tvTokens.setText(String.valueOf(u.tokens));
                tvStars.setText(String.valueOf(u.stars));
                tvLeague.setText(((u.stars / 100) + 1) + ". LIGA");

                if (u.username != null && !u.username.isEmpty())
                    tvAvatar.setText(String.valueOf(u.username.charAt(0)).toUpperCase());
            }
            @Override public void onError(String msg) {}
        });
    }

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

        // Glavni CTA -> ranked matchmaking (nasumični protivnik).
        findViewById(R.id.btnPlayOnline).setOnClickListener(v -> tryStartOnlineGame());

        // IGRAJ -> bottom sheet sa tri režima (prijateljska / izazov / turnir).
        findViewById(R.id.btnPlay).setOnClickListener(v -> showPlaySheet());

        // Donje prečice.
        findViewById(R.id.navPrijatelji).setOnClickListener(v ->
                startActivity(new Intent(this, FriendsActivity.class)));
        findViewById(R.id.navRang).setOnClickListener(v ->
                Toast.makeText(this, "Rang liste — uskoro", Toast.LENGTH_SHORT).show());
        findViewById(R.id.navRegioni).setOnClickListener(v ->
                Toast.makeText(this, "Regioni — uskoro", Toast.LENGTH_SHORT).show());
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
            Toast.makeText(this, "Turnir — uskoro", Toast.LENGTH_SHORT).show();
        });

        dialog.setContentView(sheet);
        dialog.show();
    }
}

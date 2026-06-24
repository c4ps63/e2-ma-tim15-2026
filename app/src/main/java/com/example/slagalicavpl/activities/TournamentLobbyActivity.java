package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.Tournament;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.TournamentRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;

public class TournamentLobbyActivity extends AppCompatActivity {

    private static final int TOURNAMENT_COST = 3;

    // Slot view groups (avatar circles + labels)
    private TextView[] tvInit, tvUser, tvLg;

    private TextView tvStatus;
    private TextView tvPlayerCount;

    private String  myUid;
    private String  tournamentId;
    private int     mySlot   = -1;
    private boolean launched = false;  // prevent double-launch

    private ValueEventListener tourListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_lobby);

        FirebaseUser fu = AuthService.getInstance().getCurrentUser();
        if (fu == null) { finish(); return; }
        myUid = fu.getUid();

        tvStatus      = findViewById(R.id.tvStatus);
        tvPlayerCount = findViewById(R.id.tvPlayerCount);

        tvInit = new TextView[]{
                findViewById(R.id.tvInit1), findViewById(R.id.tvInit2),
                findViewById(R.id.tvInit3), findViewById(R.id.tvInit4)};
        tvUser = new TextView[]{
                findViewById(R.id.tvUser1), findViewById(R.id.tvUser2),
                findViewById(R.id.tvUser3), findViewById(R.id.tvUser4)};
        tvLg = new TextView[]{
                findViewById(R.id.tvLg1), findViewById(R.id.tvLg2),
                findViewById(R.id.tvLg3), findViewById(R.id.tvLg4)};

        // Style all avatar circles as empty initially
        for (int i = 0; i < 4; i++) styleAvatar(i, null, "?");

        findViewById(R.id.btnBack).setOnClickListener(v -> leaveAndFinish());

        deductTokensAndJoin();
    }

    // ── Token deduction + join ─────────────────────────────────────────────

    private void deductTokensAndJoin() {
        tvStatus.setText("Proveravamo žetone...");
        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                if (u.tokens < TOURNAMENT_COST) {
                    Toast.makeText(TournamentLobbyActivity.this,
                            "Nemaš dovoljno žetona! Potrebno " + TOURNAMENT_COST + " žetona.",
                            Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                // Deduct 3 tokens, then join
                UserRepository.getInstance().deductToken(myUid, new UserRepository.Callback() {
                    @Override public void onSuccess() {
                        UserRepository.getInstance().deductToken(myUid, new UserRepository.Callback() {
                            @Override public void onSuccess() {
                                UserRepository.getInstance().deductToken(myUid, new UserRepository.Callback() {
                                    @Override public void onSuccess() { joinTournament(u); }
                                    @Override public void onError(String m) { onDeductError(); }
                                });
                            }
                            @Override public void onError(String m) { onDeductError(); }
                        });
                    }
                    @Override public void onError(String m) { onDeductError(); }
                });
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(TournamentLobbyActivity.this, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void onDeductError() {
        Toast.makeText(this, "Greška pri oduzimanju žetona.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void joinTournament(User u) {
        tvStatus.setText(getString(R.string.tournament_searching));

        Tournament.TournamentPlayer player = new Tournament.TournamentPlayer();
        player.uid         = myUid;
        player.username    = u.username    != null ? u.username    : "";
        player.avatarColor = u.avatarColor != null ? u.avatarColor : "#5C85FF";
        player.league      = (int)(u.stars / 100) + 1;

        TournamentRepository.getInstance().join(myUid, player, new TournamentRepository.JoinCallback() {
            @Override
            public void onJoined(String tid, int slot) {
                tournamentId = tid;
                mySlot       = slot;
                // Listen to the shared matchmaking node for lobby slot updates.
                // Once all 4 players are in, we switch to the stable tournament doc.
                tourListener = TournamentRepository.getInstance().listenMatchmaking(t -> {
                    if (!isFinishing()) runOnUiThread(() -> onTournamentUpdate(t));
                });
            }
            @Override
            public void onError(String msg) {
                Toast.makeText(TournamentLobbyActivity.this, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // ── Tournament state updates ───────────────────────────────────────────

    private void onTournamentUpdate(Tournament t) {
        int count = t.playerCount;
        tvPlayerCount.setText(count + "/4");
        tvStatus.setText(count < 4
                ? getString(R.string.tournament_searching) + " (" + count + "/4)"
                : getString(R.string.tournament_starting));

        bindSlot(0, t.p1);
        bindSlot(1, t.p2);
        bindSlot(2, t.p3);
        bindSlot(3, t.p4);

        if ("starting".equals(t.status) && !launched) {
            launched = true;
            // Detach immediately so a new group joining can't overwrite our view
            if (tourListener != null) {
                TournamentRepository.getInstance().removeMatchmakingListener(tourListener);
                tourListener = null;
            }
            // Brief 2-second pause to show the full matchup, then launch game
            tvStatus.postDelayed(this::launchGame, 2200);
        }
    }

    private void bindSlot(int idx, Tournament.TournamentPlayer p) {
        if (p == null || p.uid == null) {
            styleAvatar(idx, null, "?");
            tvUser[idx].setText(getString(R.string.tournament_slot_waiting));
            tvLg[idx].setText("—");
        } else {
            String initial = p.username != null && !p.username.isEmpty()
                    ? String.valueOf(p.username.charAt(0)).toUpperCase() : "?";
            styleAvatar(idx, p.avatarColor, initial);
            tvUser[idx].setText(p.username);
            tvUser[idx].setAlpha(1f);
            tvLg[idx].setText(p.league + ". LIGA");
            tvLg[idx].setAlpha(0.6f);
        }
    }

    private void styleAvatar(int idx, String hexColor, String letter) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (hexColor != null) {
            try { gd.setColor(Color.parseColor(hexColor)); }
            catch (Exception e) { gd.setColor(0xFF5C85FF); }
        } else {
            gd.setColor(0x22102341);
        }
        tvInit[idx].setBackground(gd);
        tvInit[idx].setText(letter);
        tvInit[idx].setAlpha(hexColor != null ? 1f : 0.3f);
    }

    // ── Launch game ──────────────────────────────────────────────────────────

    private void launchGame() {
        if (isFinishing() || mySlot < 1) return;
        String phase  = Tournament.phaseForSlot(mySlot);
        String myRole = Tournament.roleForSlot(mySlot);
        String roomId = tournamentId + "_" + phase;

        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(LobbyActivity.EXTRA_ROOM_ID,      roomId);
        i.putExtra(LobbyActivity.EXTRA_MY_ROLE,      myRole);
        i.putExtra(LobbyActivity.EXTRA_IS_FRIENDLY,  false);
        i.putExtra(GameActivity.EXTRA_TOURNAMENT_ID,    tournamentId);
        i.putExtra(GameActivity.EXTRA_TOURNAMENT_PHASE, phase);
        startActivity(i);
        finish();
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    private void leaveAndFinish() {
        if (tournamentId != null && !launched) {
            TournamentRepository.getInstance().leave(tournamentId, myUid, () -> {
                // Refund the 3 tokens
                UserRepository.getInstance().addTokens(myUid, TOURNAMENT_COST);
            });
        }
        finish();
    }

    @Override
    public void onBackPressed() {
        leaveAndFinish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tourListener != null) {
            TournamentRepository.getInstance().removeMatchmakingListener(tourListener);
            tourListener = null;
        }
    }
}

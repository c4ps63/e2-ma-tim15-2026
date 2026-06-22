package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.Challenge;
import com.example.slagalicavpl.repository.ChallengeRepository;
import com.google.firebase.database.ValueEventListener;

/**
 * Čekaonica: prikazuje listu igrača koji su se pridružili izazovu.
 * Prelazi na igru čim kreator pokrene ili se popuni 4 mesta.
 */
public class ChallengeWaitActivity extends AppCompatActivity {

    private String            challengeId;
    private ValueEventListener listener;
    private boolean           launched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_wait);

        challengeId = getIntent().getStringExtra(LobbyActivity.EXTRA_CHALLENGE_ID);
        if (challengeId == null) { finish(); return; }

        Button btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        listener = ChallengeRepository.getInstance().listenChallenge(challengeId,
                new ChallengeRepository.ChallengeCallback() {
            @Override public void onLoaded(Challenge c) {
                updateUI(c);
                if ("in_progress".equals(c.status) && !launched) {
                    launched = true;
                    launchGame();
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null)
            ChallengeRepository.getInstance().removeListener(challengeId, listener);
    }

    private void updateUI(Challenge c) {
        TextView tvStatus = findViewById(R.id.tvWaitStatus);
        if (tvStatus == null) return;
        StringBuilder sb = new StringBuilder("Igrači (" + c.playerCount() + "/4):\n\n");
        if (c.players != null) {
            for (Challenge.ChallengePlayer p : c.players.values())
                sb.append("• ").append(p.name).append("\n");
        }
        sb.append("\nČekam da kreator pokrene igru...");
        tvStatus.setText(sb.toString());
    }

    private void launchGame() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
        finish();
    }
}

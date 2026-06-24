package com.example.slagalicavpl.activities;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.Tournament;
import com.example.slagalicavpl.repository.TournamentRepository;
import com.google.firebase.database.ValueEventListener;

public class TournamentResultActivity extends AppCompatActivity {

    public static final String EXTRA_TOURNAMENT_ID = "t_tournamentId";
    public static final String EXTRA_PHASE         = "t_phase";
    public static final String EXTRA_WON           = "t_won";
    public static final String EXTRA_MY_UID        = "t_myUid";
    public static final String EXTRA_MY_SCORE      = "t_myScore";

    private String  tournamentId;
    private String  phase;
    private boolean won;
    private String  myUid;
    private int     myScore;

    private ValueEventListener tourListener;
    private boolean finalLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tournament_result);

        tournamentId = getIntent().getStringExtra(EXTRA_TOURNAMENT_ID);
        phase        = getIntent().getStringExtra(EXTRA_PHASE);
        won          = getIntent().getBooleanExtra(EXTRA_WON, false);
        myUid        = getIntent().getStringExtra(EXTRA_MY_UID);
        myScore      = getIntent().getIntExtra(EXTRA_MY_SCORE, 0);

        boolean isFinal = "final".equals(phase);

        if (!isFinal && won) {
            showWaitingForFinal();
        } else {
            showResult(isFinal, won);
        }
    }

    // ── Waiting state (semi winner waits for other semi to finish) ──────────

    private void showWaitingForFinal() {
        findViewById(R.id.layoutWaiting).setVisibility(View.VISIBLE);
        findViewById(R.id.layoutResult).setVisibility(View.GONE);

        // Semi-win reward info
        int baseStars = 10 + myScore / 40;
        ((TextView) findViewById(R.id.tvSemiStars)).setText("+" + baseStars + " zvezda");

        // Listen for tournament status to change to "final".
        // Firebase fires immediately with current value on attach, so this handles
        // the case where the other semi finishes before this activity even opens.
        tourListener = TournamentRepository.getInstance().listen(tournamentId, t -> {
            if ("final".equals(t.status) && !finalLaunched
                    && t.semi1Winner != null && !t.semi1Winner.isEmpty()
                    && t.semi2Winner != null && !t.semi2Winner.isEmpty()) {
                finalLaunched = true;
                runOnUiThread(() -> startFinalGame(t));
            }
        });
    }

    private void startFinalGame(Tournament t) {
        // Determine role based on which semi I won.
        // Only the actual semi-final winners should reach this method.
        boolean isSemi1Winner = myUid.equals(t.semi1Winner);
        boolean isSemi2Winner = myUid.equals(t.semi2Winner);
        if (!isSemi1Winner && !isSemi2Winner) {
            // Should never happen: I'm not a finalist, don't enter the final room.
            return;
        }

        String myRole = isSemi1Winner ? "p1" : "p2";
        String roomId = tournamentId + "_final";

        Intent i = new Intent(this, GameActivity.class);
        i.putExtra(LobbyActivity.EXTRA_ROOM_ID,      roomId);
        i.putExtra(LobbyActivity.EXTRA_MY_ROLE,      myRole);
        i.putExtra(LobbyActivity.EXTRA_IS_FRIENDLY,  false);
        i.putExtra(GameActivity.EXTRA_TOURNAMENT_ID,    tournamentId);
        i.putExtra(GameActivity.EXTRA_TOURNAMENT_PHASE, "final");
        startActivity(i);
        finish();
    }

    // ── Result state ─────────────────────────────────────────────────────────

    private void showResult(boolean isFinal, boolean won) {
        View layoutResult = findViewById(R.id.layoutResult);
        layoutResult.setVisibility(View.VISIBLE);
        layoutResult.setAlpha(0f);
        layoutResult.animate().alpha(1f).setDuration(300).start();

        TextView tvEmoji = findViewById(R.id.tvResultEmoji);
        TextView tvTitle = findViewById(R.id.tvResultTitle);
        TextView tvDesc  = findViewById(R.id.tvResultDesc);

        if (!isFinal && !won) {
            // Semi loss
            tvEmoji.setText("😔");
            tvTitle.setText(getString(R.string.tournament_eliminated));
            tvDesc.setText(getString(R.string.tournament_semi_loss_desc));
            findViewById(R.id.rewardCard).setVisibility(View.GONE);
        } else if (isFinal && won) {
            // Final win — grand celebration
            tvEmoji.setText("🏆");
            tvTitle.setText(getString(R.string.tournament_champion));
            tvDesc.setText(getString(R.string.tournament_final_win_desc));
            showRewardCard(3, 10 + myScore / 40 + 10);
            animateWin(tvEmoji);
        } else {
            // Final loss
            tvEmoji.setText("😤");
            tvTitle.setText(getString(R.string.tournament_final_loss));
            tvDesc.setText(String.format(getString(R.string.tournament_final_loss_desc), myScore));
            animateLoss(layoutResult);
            findViewById(R.id.rewardCard).setVisibility(View.GONE);
        }

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }

    private void showRewardCard(int tokens, int stars) {
        View rewardCard = findViewById(R.id.rewardCard);
        rewardCard.setVisibility(View.VISIBLE);
        rewardCard.setAlpha(0f);
        rewardCard.setTranslationY(40f);
        rewardCard.animate().alpha(1f).translationY(0f).setStartDelay(600).setDuration(400).start();

        ((TextView) findViewById(R.id.tvRewardTokens)).setText("+" + tokens + " žetona");
        ((TextView) findViewById(R.id.tvRewardStars)).setText("+" + stars + " zvezda");
    }

    // ── Animations ────────────────────────────────────────────────────────────

    private void animateWin(View target) {
        target.setScaleX(0.2f);
        target.setScaleY(0.2f);
        target.setAlpha(0f);
        target.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator(2.5f))
                .start();
    }

    private void animateLoss(View target) {
        // Horizontal shake
        ObjectAnimator shake = ObjectAnimator.ofFloat(target, "translationX",
                0f, -18f, 18f, -12f, 12f, -6f, 6f, 0f);
        shake.setDuration(500);
        shake.setStartDelay(200);
        shake.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tournamentId != null && tourListener != null)
            TournamentRepository.getInstance().removeListener(tournamentId, tourListener);
    }

    @Override
    public void onBackPressed() {
        // Only allow back when showing the result (not while waiting for final)
        View layoutResult = findViewById(R.id.layoutResult);
        if (layoutResult.getVisibility() == View.VISIBLE) {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
        }
        finish();
    }
}

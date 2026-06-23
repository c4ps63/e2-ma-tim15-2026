package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;

public class RankingRewardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking_reward);

        int    rank       = getIntent().getIntExtra("rank", 1);
        int    tokens     = getIntent().getIntExtra("tokens", 1);
        String cycleLabel = getIntent().getStringExtra("cycleLabel");
        if (cycleLabel == null) cycleLabel = "nedeljnoj";

        TextView     tvMedal      = findViewById(R.id.tvMedal);
        TextView     tvPlacement  = findViewById(R.id.tvPlacement);
        TextView     tvTokenReward= findViewById(R.id.tvTokenReward);
        LinearLayout tokenCard    = findViewById(R.id.tokenCard);
        Button       btnClaim     = findViewById(R.id.btnClaim);

        // Medalja
        String medal = rank == 1 ? "🥇" : rank == 2 ? "🥈" : rank == 3 ? "🥉" : "🏅";
        tvMedal.setText(medal);

        // Tekst plasmana
        tvPlacement.setText(rank + ". mesto na " + cycleLabel + " rang listi");

        // Nagrada
        tvTokenReward.setText("+" + tokens + " " + pluralZeton(tokens));

        // Animacija ulaska medalje
        tvMedal.setScaleX(0.2f);
        tvMedal.setScaleY(0.2f);
        tvMedal.setAlpha(0f);
        tvMedal.animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(700)
                .setInterpolator(new OvershootInterpolator(2f))
                .start();

        // Animacija kartice s tokenom
        tokenCard.setAlpha(0f);
        tokenCard.setTranslationY(40f);
        tokenCard.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(500)
                .setDuration(450)
                .start();

        btnClaim.setOnClickListener(v -> finish());
    }

    private String pluralZeton(int n) {
        if (n == 1) return "žeton";
        if (n >= 2 && n <= 4) return "žetona";
        return "žetona";
    }
}

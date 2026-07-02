package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;

/** Prikazuje konačan rezultat obične (netarnirske, ne-izazovske) partije pre izlaska. */
public class GameResultActivity extends AppCompatActivity {

    public static final String EXTRA_MY_SCORE  = "r_myScore";
    public static final String EXTRA_OPP_SCORE = "r_oppScore";
    public static final String EXTRA_MY_NAME   = "r_myName";
    public static final String EXTRA_OPP_NAME  = "r_oppName";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_result);

        int myScore  = getIntent().getIntExtra(EXTRA_MY_SCORE, 0);
        int oppScore = getIntent().getIntExtra(EXTRA_OPP_SCORE, 0);
        String myName  = getIntent().getStringExtra(EXTRA_MY_NAME);
        String oppName = getIntent().getStringExtra(EXTRA_OPP_NAME);

        TextView tvEmoji = findViewById(R.id.tvResultEmoji);
        TextView tvTitle = findViewById(R.id.tvResultTitle);

        if (myScore > oppScore) {
            tvEmoji.setText("🏆");
            tvTitle.setText("POBEDA!");
        } else if (myScore < oppScore) {
            tvEmoji.setText("😤");
            tvTitle.setText("PORAZ");
        } else {
            tvEmoji.setText("🤝");
            tvTitle.setText("NEREŠENO");
        }

        ((TextView) findViewById(R.id.tvMyScore)).setText(String.valueOf(myScore));
        ((TextView) findViewById(R.id.tvOppScore)).setText(String.valueOf(oppScore));
        ((TextView) findViewById(R.id.tvMyName)).setText(
                myName == null || myName.isEmpty() ? "TI" : myName);
        ((TextView) findViewById(R.id.tvOppName)).setText(
                oppName == null || oppName.isEmpty() ? "PROTIVNIK" : oppName);

        Button btnRematch = findViewById(R.id.btnRematch);
        Button btnHome    = findViewById(R.id.btnHome);

        btnRematch.setOnClickListener(v -> {
            startActivity(new Intent(this, LobbyActivity.class));
            finish();
        });
        btnHome.setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        Intent i = new Intent(this, HomeActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }
}

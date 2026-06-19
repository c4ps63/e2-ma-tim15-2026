package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        loadUserProfile();
        setupNavigation();
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

    private void setupNavigation() {
        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        Button btnOnline = findViewById(R.id.btnPlayOnline);
        Button btnChat   = findViewById(R.id.btnChat);
        btnOnline.setOnClickListener(v -> startActivity(new Intent(this, LobbyActivity.class)));
        btnChat.setOnClickListener(v   -> startActivity(new Intent(this, ChatActivity.class)));

        findViewById(R.id.navSvet).setOnClickListener(v ->
                startActivity(new Intent(this, LobbyActivity.class)));
        findViewById(R.id.btnPlayOffline).setOnClickListener(v ->
                startActivity(new Intent(this, GameActivity.class)));
        findViewById(R.id.navRang).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
        findViewById(R.id.navCet).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        findViewById(R.id.navProfil).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }
}

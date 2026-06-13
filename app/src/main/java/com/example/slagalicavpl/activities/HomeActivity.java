package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;

public class HomeActivity extends AppCompatActivity {

    private TextView tvNotifBadge;
    private NotificationRepository notifRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        notifRepo    = NotificationRepository.getInstance(this);
        tvNotifBadge = findViewById(R.id.tvNotifBadge);

        // Pozdravna poruka sa korisničkim imenom
        FirebaseUser user = AuthService.getInstance().getCurrentUser();
        if (user != null) {
            String uid = user.getUid();
            UserRepository.getInstance().loadProfile(uid, new UserRepository.ProfileCallback() {
                @Override
                public void onLoaded(com.example.slagalicavpl.model.User u) {
                    TextView tv = findViewById(R.id.tvHomeGreeting);
                    if (tv != null && u.username != null)
                        tv.setText("Dobrodošao, " + u.username + "!");
                }
                @Override public void onError(String msg) {}
            });
        }

        Button btnOnline  = findViewById(R.id.btnPlayOnline);
        Button btnOffline = findViewById(R.id.btnPlayOffline);
        Button btnProfile = findViewById(R.id.btnProfile);
        Button btnNotif   = findViewById(R.id.btnNotifications);

        btnOnline.setOnClickListener(v ->
                startActivity(new Intent(this, LobbyActivity.class)));

        btnOffline.setOnClickListener(v ->
                startActivity(new Intent(this, GameActivity.class)));

        btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        btnNotif.setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Ažuriramo badge svaki put kad se vratimo na Home
        updateNotifBadge();
    }

    private void updateNotifBadge() {
        int count = notifRepo.getUnreadCount();
        if (count > 0) {
            tvNotifBadge.setVisibility(View.VISIBLE);
            tvNotifBadge.setText(count > 9 ? "9+" : String.valueOf(count));
        } else {
            tvNotifBadge.setVisibility(View.GONE);
        }
    }
}

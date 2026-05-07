package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import com.example.slagalicavpl.activities.ProfileActivity;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;

public class LoginActivity extends AppCompatActivity {

    private Button btnLogin;
    private Button btnGoRegister;
    private TextView tvForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnLogin = findViewById(R.id.btnLogin);
        btnGoRegister = findViewById(R.id.btnGoRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        btnLogin.setOnClickListener(v -> RetroButtonAnimation.flash(btnLogin, () -> {
            // TODO KT2: validate credentials via Firebase Auth
            Intent intent = new Intent(this, GameActivity.class);
            startActivity(intent);
        }));

        btnGoRegister.setOnClickListener(v -> RetroButtonAnimation.flash(btnGoRegister, () -> {
            startActivity(new Intent(this, RegisterActivity.class));
        }));

        tvForgotPassword.setOnClickListener(v -> {
            // TODO KT2: show reset password dialog
        });

        TextView tvProfileDemo = findViewById(R.id.tvProfileDemo);
        tvProfileDemo.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }
}

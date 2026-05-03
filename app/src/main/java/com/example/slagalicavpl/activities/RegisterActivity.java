package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;

public class RegisterActivity extends AppCompatActivity {

    private ImageButton btnBack;
    private Button btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        btnBack = findViewById(R.id.btnBack);
        btnRegister = findViewById(R.id.btnRegister);

        btnBack.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> RetroButtonAnimation.flash(btnRegister, () -> {
            // TODO KT2: validate fields and register via Firebase Auth
            finish();
        }));
    }
}

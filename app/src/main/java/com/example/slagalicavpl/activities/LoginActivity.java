package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;
import com.example.slagalicavpl.service.AuthService;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmailOrUsername;
    private EditText etPassword;
    private Button   btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ako je već ulogovan i email potvrđen — idi direktno u lobby
        if (AuthService.getInstance().isEmailVerified()) {
            goToLobby();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmailOrUsername = findViewById(R.id.etEmailOrUsername);
        etPassword        = findViewById(R.id.etPassword);
        btnLogin          = findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(v ->
                RetroButtonAnimation.flash(btnLogin, this::attemptLogin));

        findViewById(R.id.btnGoRegister).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        findViewById(R.id.btnGuest).setOnClickListener(v ->
                startActivity(new Intent(this, GameActivity.class)));

        findViewById(R.id.tvForgotPassword).setOnClickListener(v ->
                Toast.makeText(this,
                        "Kontaktiraj tim za podršku ili pokušaj reset lozinke u profilu.",
                        Toast.LENGTH_LONG).show());

        // demo link profila (samo za razvoj)
        TextView tvProfileDemo = findViewById(R.id.tvProfileDemo);
        tvProfileDemo.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
    }

    private void attemptLogin() {
        String input    = etEmailOrUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        btnLogin.setEnabled(false);

        AuthService.getInstance().login(input, password, new AuthService.Callback() {
            @Override
            public void onSuccess(String msg) {
                goToLobby();
            }
            @Override
            public void onError(String msg) {
                btnLogin.setEnabled(true);
                Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToLobby() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }
}

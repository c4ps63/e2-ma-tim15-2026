package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.RetroButtonAnimation;
import com.example.slagalicavpl.model.SerbiaRegions;
import com.example.slagalicavpl.service.AuthService;

public class RegisterActivity extends AppCompatActivity {

    private EditText    etEmail;
    private EditText    etUsername;
    private EditText    etPassword;
    private EditText    etPasswordConfirm;
    private Spinner     spinnerRegion;
    private Button      btnRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etEmail          = findViewById(R.id.etEmail);
        etUsername       = findViewById(R.id.etUsername);
        etPassword       = findViewById(R.id.etPassword);
        etPasswordConfirm= findViewById(R.id.etPasswordConfirm);
        spinnerRegion    = findViewById(R.id.spinnerRegion);
        btnRegister      = findViewById(R.id.btnRegister);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Popuni spinner regionima iz SerbiaRegions (sinhronizovano sa strings.xml)
        String[] regionNames = getResources().getStringArray(R.array.regions);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, regionNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRegion.setAdapter(adapter);

        btnRegister.setOnClickListener(v ->
                RetroButtonAnimation.flash(btnRegister, this::attemptRegister));
    }

    private void attemptRegister() {
        String email    = etEmail.getText().toString().trim();
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirm  = etPasswordConfirm.getText().toString();
        // Čuvamo regionId (npr. "vojvodina"), ne display name
        String regionDisplay = spinnerRegion.getSelectedItem() != null
                               ? spinnerRegion.getSelectedItem().toString() : "";
        String region = SerbiaRegions.idFromDisplayName(regionDisplay);

        btnRegister.setEnabled(false);

        AuthService.getInstance().register(email, password, confirm, username, region,
                new AuthService.Callback() {
            @Override
            public void onSuccess(String msg) {
                Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
                // Vraća na Login — korisnik mora potvrditi email pre logovanja
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
            }
            @Override
            public void onError(String msg) {
                btnRegister.setEnabled(true);
                Toast.makeText(RegisterActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }
}

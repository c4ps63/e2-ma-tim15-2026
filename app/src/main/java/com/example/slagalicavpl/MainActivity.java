package com.example.slagalicavpl;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.activities.LoginActivity;
import com.example.slagalicavpl.notification.NotificationChannels;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Kreiraj notification kanale jednom pri pokretanju aplikacije
        NotificationChannels.createAll(this);
        // Redirect to LoginActivity; MainActivity is kept only as launcher entry point
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}

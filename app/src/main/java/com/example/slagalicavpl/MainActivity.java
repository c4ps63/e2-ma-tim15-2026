package com.example.slagalicavpl;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.activities.LoginActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Redirect to LoginActivity; MainActivity is kept only as launcher entry point
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}

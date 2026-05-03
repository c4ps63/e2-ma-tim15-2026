package com.example.slagalicavpl.activities;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.fragments.KorakPoKorakFragment;
import com.example.slagalicavpl.activities.fragments.MojBrojFragment;

public class GameActivity extends AppCompatActivity {

    // Game order per spec: Ko zna zna, Spojnice, Asocijacije, Skočko, Korak po korak, Moj broj
    // For GUI demo: shows Korak po Korak (game 5) then Moj Broj (game 6)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new KorakPoKorakFragment())
                    .commit();
        }
    }

    /** Called by KorakPoKorakFragment when round is over — shows next game. */
    public void showMojBroj() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new MojBrojFragment())
                .addToBackStack(null)
                .commit();
    }
}

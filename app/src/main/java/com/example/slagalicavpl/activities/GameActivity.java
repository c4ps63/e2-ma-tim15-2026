package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.fragments.KoZnaZnaFragment;
import com.example.slagalicavpl.activities.fragments.SpojniceFragment;
import com.example.slagalicavpl.activities.fragments.AsocijacijeFragment;
import com.example.slagalicavpl.activities.fragments.SkockoFragment;
import com.example.slagalicavpl.activities.fragments.KorakPoKorakFragment;
import com.example.slagalicavpl.activities.fragments.MojBrojFragment;

public class GameActivity extends AppCompatActivity {

    // Game order per spec: Ko zna zna, Spojnice, Asocijacije, Skočko, Korak po korak, Moj broj
    // For GUI demo: shows Korak po Korak (game 5) then Moj Broj (game 6)

    private int totalP1 = 0;
    private int totalP2 = 0;

    public void addScores(int p1, int p2) {
        totalP1 += p1;
        totalP2 += p2;
    }

    public int getP1Total() { return totalP1; }
    public int getP2Total() { return totalP2; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Sakrij status bar — ceo ekran za igru
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_game);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new KoZnaZnaFragment())
                    .commit();
        }
    }

    public void showSpojnice() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new SpojniceFragment())
                .addToBackStack(null)
                .commit();
    }

    public void showAsocijacije() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AsocijacijeFragment())
                .addToBackStack(null)
                .commit();
    }

    public void showSkocko() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new SkockoFragment())
                .addToBackStack(null)
                .commit();
    }

    public void showKorakPoKorak() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new KorakPoKorakFragment())
                .addToBackStack(null)
                .commit();
    }

    public void showMojBroj() {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new MojBrojFragment())
                .addToBackStack(null)
                .commit();
    }
}

package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.fragments.KoZnaZnaFragment;
import com.example.slagalicavpl.activities.fragments.SpojniceFragment;
import com.example.slagalicavpl.activities.fragments.AsocijacijeFragment;
import com.example.slagalicavpl.activities.fragments.SkockoFragment;
import com.example.slagalicavpl.activities.fragments.KorakPoKorakFragment;
import com.example.slagalicavpl.activities.fragments.MojBrojFragment;
import com.example.slagalicavpl.multiplayer.FirebaseKoZnaZnaSync;
import com.example.slagalicavpl.multiplayer.KoZnaZnaSync;
import com.example.slagalicavpl.multiplayer.LocalKoZnaZnaSync;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class GameActivity extends AppCompatActivity {

    // Redosled igara — mora biti isti na oba uređaja
    private static final String NAV_SPOJNICE    = "spojnice";
    private static final String NAV_ASOCIJACIJE = "asocijacije";
    private static final String NAV_SKOCKO      = "skocko";
    private static final String NAV_KORAK       = "korak";
    private static final String NAV_MOJBROJ     = "mojbroj";
    private static final String NAV_DONE        = "done";

    private int totalP1 = 0;
    private int totalP2 = 0;

    private String            roomId;
    private String            myRole;
    private DatabaseReference roomRef;

    private String            lastNav = "";   // sprečava duplu navigaciju
    private ValueEventListener navListener;

    public void addScores(int p1, int p2) {
        totalP1 += p1;
        totalP2 += p2;
    }

    public int getP1Total() { return totalP1; }
    public int getP2Total() { return totalP2; }

    public KoZnaZnaSync getKoZnaZnaSync() {
        if (roomRef != null) return new FirebaseKoZnaZnaSync(roomRef, myRole);
        return new LocalKoZnaZnaSync();
    }

    public DatabaseReference getRoomRef() { return roomRef; }
    public String  getMyRole()     { return myRole != null ? myRole : "p1"; }
    public boolean isMultiplayer() { return roomRef != null; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_game);

        roomId = getIntent().getStringExtra(LobbyActivity.EXTRA_ROOM_ID);
        myRole = getIntent().getStringExtra(LobbyActivity.EXTRA_MY_ROLE);
        if (roomId != null) {
            roomRef = FirebaseDatabase.getInstance(
                    "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("rooms").child(roomId);
            startNavListener();
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new KoZnaZnaFragment())
                    .commit();
        }
    }

    // ── Firebase navigation listener ──────────────────────────────────────────

    private void startNavListener() {
        navListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String nav = snapshot.getValue(String.class);
                if (nav == null || nav.equals(lastNav)) return;
                lastNav = nav;
                applyNav(nav);
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        roomRef.child("nav").addValueEventListener(navListener);
    }

    private void applyNav(String nav) {
        switch (nav) {
            case NAV_SPOJNICE:    doShow(new SpojniceFragment());    break;
            case NAV_ASOCIJACIJE: doShow(new AsocijacijeFragment()); break;
            case NAV_SKOCKO:      doShow(new SkockoFragment());      break;
            case NAV_KORAK:       doShow(new KorakPoKorakFragment());break;
            case NAV_MOJBROJ:     doShow(new MojBrojFragment());     break;
            case NAV_DONE:        finish();                           break;
        }
    }

    private void doShow(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .addToBackStack(null).commit();
    }

    // ── Navigacione metode koje pozivaju fragmenti ────────────────────────────
    // Svaki fragment zove ovu metodu; u multiplayer-u to piše u Firebase
    // i oba uređaja dobijaju notifikaciju.

    private void navigateTo(String navKey) {
        if (roomRef != null) {
            // Piše u Firebase — listener applyNav će biti pozvan na oba uređaja
            roomRef.child("nav").setValue(navKey);
        } else {
            // Offline — direktna navigacija
            lastNav = navKey;
            applyNav(navKey);
        }
    }

    public void showSpojnice()    { navigateTo(NAV_SPOJNICE); }
    public void showAsocijacije() { navigateTo(NAV_ASOCIJACIJE); }
    public void showSkocko()      { navigateTo(NAV_SKOCKO); }
    public void showKorakPoKorak(){ navigateTo(NAV_KORAK); }
    public void showMojBroj()     { navigateTo(NAV_MOJBROJ); }
    public void finishGame()      { navigateTo(NAV_DONE); }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomRef != null) {
            if (navListener != null) roomRef.child("nav").removeEventListener(navListener);
            roomRef.child("status").setValue("finished");
        }
    }
}

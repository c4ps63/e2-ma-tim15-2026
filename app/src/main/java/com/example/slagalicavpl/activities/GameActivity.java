package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;

import android.widget.Toast;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.activities.fragments.KoZnaZnaFragment;
import com.example.slagalicavpl.activities.fragments.SpojniceFragment;
import com.example.slagalicavpl.activities.fragments.AsocijacijeFragment;
import com.example.slagalicavpl.activities.fragments.SkockoFragment;
import com.example.slagalicavpl.activities.fragments.KorakPoKorakFragment;
import com.example.slagalicavpl.activities.fragments.MojBrojFragment;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.model.Challenge;
import com.example.slagalicavpl.multiplayer.FirebaseKoZnaZnaSync;
import com.example.slagalicavpl.repository.ChallengeRepository;
import com.example.slagalicavpl.multiplayer.KoZnaZnaSync;
import com.example.slagalicavpl.multiplayer.LocalKoZnaZnaSync;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

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
    private boolean           isFriendly   = false;
    private String            challengeId  = null;  // non-null = challenge mode
    private DatabaseReference roomRef;

    private String            lastNav = "";
    private ValueEventListener navListener;

    // disconnect
    private ValueEventListener disconnectListener;
    private boolean            opponentDisconnected = false;

    // Avatar podaci
    private char myInitial    = '?';
    private int  myColor      = 0xFF5C85FF;
    private char oppInitial   = '?';
    private int  oppColor     = 0xFFFF6B6B;
    private ValueEventListener avatarListener;

    public void addScores(int p1, int p2) {
        totalP1 += p1;
        totalP2 += p2;
    }

    public int getP1Total() { return totalP1; }
    public int getP2Total() { return totalP2; }

    public boolean isChallengeMode() { return challengeId != null; }

    public KoZnaZnaSync getKoZnaZnaSync() {
        if (roomRef != null) return new FirebaseKoZnaZnaSync(roomRef, myRole);
        if (challengeId != null) return new com.example.slagalicavpl.multiplayer.SoloKoZnaZnaSync();
        return new LocalKoZnaZnaSync();
    }

    public DatabaseReference getRoomRef()          { return roomRef; }
    public String  getMyRole()                     { return myRole != null ? myRole : "p1"; }
    public boolean isMultiplayer()                 { return roomRef != null; }
    public boolean isFriendlyGame()                { return isFriendly; }
    public boolean isOpponentDisconnected()        { return opponentDisconnected; }

    /** Skor trenutnog igrača (bez obzira na rolu). */
    public int getMyScore() {
        return "p1".equals(getMyRole()) ? totalP1 : totalP2;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_game);

        roomId      = getIntent().getStringExtra(LobbyActivity.EXTRA_ROOM_ID);
        myRole      = getIntent().getStringExtra(LobbyActivity.EXTRA_MY_ROLE);
        isFriendly  = getIntent().getBooleanExtra(LobbyActivity.EXTRA_IS_FRIENDLY, false);
        challengeId = getIntent().getStringExtra(LobbyActivity.EXTRA_CHALLENGE_ID);
        if (roomId != null) {
            roomRef = FirebaseDatabase.getInstance(
                    "https://slagalica-vrtlogalica-default-rtdb.europe-west1.firebasedatabase.app")
                    .getReference("rooms").child(roomId);
            startNavListener();
            startDisconnectListener();
        }

        loadMyAvatar();

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new KoZnaZnaFragment())
                    .commit();
        }
    }

    // ── Avatar učitavanje i sinhronizacija ────────────────────────────────────

    private void loadMyAvatar() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;
        UserRepository.getInstance().loadProfile(fbUser.getUid(), new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User u) {
                if (u.username != null && !u.username.isEmpty())
                    myInitial = Character.toUpperCase(u.username.charAt(0));
                if (u.avatarColor != null) {
                    try { myColor = Color.parseColor(u.avatarColor); }
                    catch (Exception ignored) {}
                }
                if (roomRef != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("initial", String.valueOf(myInitial));
                    data.put("color",   String.format("#%06X", 0xFFFFFF & myColor));
                    roomRef.child("avatars").child(getMyRole()).setValue(data);
                    listenForOpponentAvatar();
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    private void listenForOpponentAvatar() {
        String oppRole = "p1".equals(myRole) ? "p2" : "p1";
        avatarListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snap) {
                String initial = snap.child("initial").getValue(String.class);
                String color   = snap.child("color").getValue(String.class);
                if (initial != null && !initial.isEmpty()) oppInitial = initial.charAt(0);
                if (color != null) {
                    try { oppColor = Color.parseColor(color); }
                    catch (Exception ignored) {}
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        roomRef.child("avatars").child(oppRole).addValueEventListener(avatarListener);
    }

    public void applyAvatarsToHud(View rootView) {
        boolean iAmP1 = "p1".equals(getMyRole());
        char av1Char  = iAmP1 ? myInitial  : oppInitial;
        int  av1Color = iAmP1 ? myColor    : oppColor;
        char av2Char  = iAmP1 ? oppInitial : myInitial;
        int  av2Color = iAmP1 ? oppColor   : myColor;

        TextView av1 = rootView.findViewById(R.id.p1_avatar);
        TextView av2 = rootView.findViewById(R.id.p2_avatar);

        if (av1 != null) {
            av1.setText(String.valueOf(av1Char));
            Drawable d = DrawableCompat.wrap(av1.getBackground()).mutate();
            DrawableCompat.setTint(d, av1Color);
            av1.setBackground(d);
        }
        if (av2 != null) {
            av2.setText(String.valueOf(av2Char));
            Drawable d = DrawableCompat.wrap(av2.getBackground()).mutate();
            DrawableCompat.setTint(d, av2Color);
            av2.setBackground(d);
        }
    }

    // ── Disconnect handling ───────────────────────────────────────────────────

    private void startDisconnectListener() {
        String oppRole = "p1".equals(myRole) ? "p2" : "p1";
        disconnectListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Boolean disconnected = snapshot.getValue(Boolean.class);
                if (Boolean.TRUE.equals(disconnected) && !opponentDisconnected) {
                    opponentDisconnected = true;
                    // protivnik je napustio — automatska pobeda
                    if (!isFriendly) {
                        String uid = com.google.firebase.auth.FirebaseAuth.getInstance()
                                .getCurrentUser() != null
                                ? com.google.firebase.auth.FirebaseAuth.getInstance()
                                        .getCurrentUser().getUid() : null;
                        if (uid != null) {
                            UserRepository.getInstance().incrementStats(uid, true, getMyScore());
                        }
                    }
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        roomRef.child("disconnected").child(oppRole).addValueEventListener(disconnectListener);
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

    private void navigateTo(String navKey) {
        if (roomRef != null) {
            roomRef.child("nav").setValue(navKey);
        } else {
            lastNav = navKey;
            applyNav(navKey);
        }
    }

    /**
     * Barrier metoda: u online modu čeka da oba igrača signalizuju spremnost
     * pre nego što navigira na sledeću igru.
     * U offline modu navigira direktno.
     */
    public void signalReady(String navKey) {
        if (roomRef == null) {
            lastNav = navKey;
            applyNav(navKey);
            return;
        }
        // Upiši svoju spremnost
        roomRef.child("ready").child(myRole).setValue(navKey);

        // P1 koordinira — sluša dok oba nisu spremna
        if (!"p1".equals(myRole)) return;

        roomRef.child("ready").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                String p1nav = snapshot.child("p1").getValue(String.class);
                String p2nav = snapshot.child("p2").getValue(String.class);
                if (navKey.equals(p1nav) && navKey.equals(p2nav)) {
                    roomRef.child("ready").removeEventListener(this);
                    navigateTo(navKey);
                }
            }
            @Override public void onCancelled(DatabaseError e) {}
        });
    }

    public void showSpojnice()    { signalReady(NAV_SPOJNICE); }
    public void showAsocijacije() { signalReady(NAV_ASOCIJACIJE); }
    public void showSkocko()      { signalReady(NAV_SKOCKO); }
    public void showKorakPoKorak(){ signalReady(NAV_KORAK); }
    public void showMojBroj()     { signalReady(NAV_MOJBROJ); }

    public void finishGame() {
        if (challengeId != null) {
            // Challenge mode: preda rezultat pa čeka ostale
            submitChallengeScore();
        } else {
            signalReady(NAV_DONE);
        }
    }

    private void submitChallengeScore() {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) { finish(); return; }

        int myScore = getMyScore();
        ChallengeRepository.getInstance().submitScore(challengeId, uid, myScore,
                new ChallengeRepository.Callback() {
            @Override public void onSuccess() {
                openChallengeResult();
            }
            @Override public void onError(String msg) {
                Toast.makeText(GameActivity.this,
                        "Greška pri predaji rezultata", Toast.LENGTH_SHORT).show();
                openChallengeResult();
            }
        });
    }

    private void openChallengeResult() {
        Intent intent = new Intent(this, ChallengeResultActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
        finish();
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (roomRef != null) {
            if (navListener != null) roomRef.child("nav").removeEventListener(navListener);
            if (avatarListener != null) {
                String oppRole = "p1".equals(myRole) ? "p2" : "p1";
                roomRef.child("avatars").child(oppRole).removeEventListener(avatarListener);
            }
            if (disconnectListener != null) {
                String oppRole = "p1".equals(myRole) ? "p2" : "p1";
                roomRef.child("disconnected").child(oppRole).removeEventListener(disconnectListener);
            }
            // objavi protivniku da smo mi napustili igru
            roomRef.child("disconnected").child(getMyRole()).setValue(true);
            roomRef.child("status").setValue("finished");
        }
    }
}

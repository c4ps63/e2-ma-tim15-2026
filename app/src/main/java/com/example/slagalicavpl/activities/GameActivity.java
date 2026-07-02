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
import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.model.LeagueUtil;
import com.example.slagalicavpl.repository.NotificationRepository;
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
import com.example.slagalicavpl.repository.MissionRepository;
import com.example.slagalicavpl.repository.RankingRepository;
import com.example.slagalicavpl.repository.TournamentRepository;
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
    public static final String EXTRA_TOURNAMENT_ID    = "tournamentId";
    public static final String EXTRA_TOURNAMENT_PHASE = "tournamentPhase";

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
    private boolean           isFriendly     = false;
    private String            challengeId    = null;  // non-null = challenge mode
    private String            tournamentId   = null;  // non-null = tournament mode
    private String            tournamentPhase = null; // "semi1", "semi2", "final"
    private DatabaseReference roomRef;

    private String            lastNav = "";
    private ValueEventListener navListener;

    // disconnect
    private ValueEventListener disconnectListener;
    private boolean            opponentDisconnected = false;

    // Avatar podaci
    private char   myInitial    = '?';
    private int    myColor      = 0xFF5C85FF;
    private String myUsername   = "";
    private String myRegion     = "";
    private int    myLeague     = 1;
    private char   oppInitial  = '?';
    private int    oppColor    = 0xFFFF6B6B;
    private String oppUsername = "";
    private ValueEventListener avatarListener;

    private final java.util.List<View> hudViews = new java.util.ArrayList<>();

    public void addScores(int p1, int p2) {
        totalP1 += p1;
        totalP2 += p2;
    }

    public void addMyOppScores(int myScore, int oppScore) {
        if ("p1".equals(getMyRole())) { totalP1 += myScore; totalP2 += oppScore; }
        else                          { totalP1 += oppScore; totalP2 += myScore; }
    }

    public int getP1Total() { return totalP1; }
    public int getP2Total() { return totalP2; }
    public int getMyTotal()  { return "p1".equals(getMyRole()) ? totalP1 : totalP2; }
    public int getOppTotal() { return "p1".equals(getMyRole()) ? totalP2 : totalP1; }

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

        roomId          = getIntent().getStringExtra(LobbyActivity.EXTRA_ROOM_ID);
        myRole          = getIntent().getStringExtra(LobbyActivity.EXTRA_MY_ROLE);
        isFriendly      = getIntent().getBooleanExtra(LobbyActivity.EXTRA_IS_FRIENDLY, false);
        challengeId     = getIntent().getStringExtra(LobbyActivity.EXTRA_CHALLENGE_ID);
        tournamentId    = getIntent().getStringExtra(EXTRA_TOURNAMENT_ID);
        tournamentPhase = getIntent().getStringExtra(EXTRA_TOURNAMENT_PHASE);
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
                myUsername = u.username != null ? u.username : "";
                myRegion   = u.region   != null ? u.region   : "";
                myLeague   = LeagueUtil.getLeague(u.stars);
                if (roomRef != null) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("initial",   String.valueOf(myInitial));
                    data.put("color",     String.format("#%06X", 0xFFFFFF & myColor));
                    data.put("username",  myUsername);
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
                String uname = snap.child("username").getValue(String.class);
                if (uname != null) oppUsername = uname;
                runOnUiThread(() -> {
                    for (View v : hudViews) applyAvatarsToHud(v);
                });
            }
            @Override public void onCancelled(DatabaseError e) {}
        };
        roomRef.child("avatars").child(oppRole).addValueEventListener(avatarListener);
    }

    public void registerHudView(View v) {
        if (!hudViews.contains(v)) hudViews.add(v);
        applyAvatarsToHud(v);
    }

    public void applyAvatarsToHud(View rootView) {
        // p1_slot = uvijek JA, p2_slot = uvijek PROTIVNIK
        char av1Char  = myInitial;
        int  av1Color = myColor;
        char av2Char  = oppInitial;
        int  av2Color = oppColor;
        String p1Name = myUsername;
        String p2Name = oppUsername;

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

        TextView name1 = rootView.findViewById(R.id.p1_name);
        TextView name2 = rootView.findViewById(R.id.p2_name);
        if (name1 != null) name1.setText(p1Name.isEmpty() ? "TI" : p1Name);
        if (name2 != null) name2.setText(p2Name.isEmpty() ? "PROTIVNIK" : p2Name);
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
                            UserRepository.getInstance().incrementStats(uid, true, getMyScore(),
                                    (oldL, newL) -> showLeagueChangeToast(oldL, newL));
                            RankingRepository.getInstance()
                                    .updateEntry(uid, myUsername, myRegion, myLeague, 10);
                            MissionRepository.tryComplete(GameActivity.this, uid,
                                    MissionRepository.MISSION_WIN_GAME);
                            NotificationRepository.getInstance(GameActivity.this)
                                    .add(AppNotification.create(
                                            AppNotification.Channel.RANKING,
                                            "Pobeda! Protivnik se odjavio.",
                                            "Protivnik je napustio partiju. Pobeda je tvoja!",
                                            "ranking"));
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
            case NAV_DONE:
                if (tournamentId != null) {
                    handleTournamentEnd();
                } else {
                    maybeNotifyGameResult();
                    checkGameMissions();
                    finish();
                }
                break;
        }
    }

    private void maybeNotifyGameResult() {
        if (isFriendly || challengeId != null || roomRef == null) return;
        boolean iAmP1 = "p1".equals(getMyRole());
        boolean won   = iAmP1 ? (totalP1 > totalP2) : (totalP2 > totalP1);
        String title  = won ? "Pobeda! Odlično odigravanje!" : "Partija završena";
        String body   = "Tvoj rezultat: " + getMyScore() + " bodova.";
        AppNotification.Channel ch = won
                ? AppNotification.Channel.REWARD
                : AppNotification.Channel.RANKING;
        NotificationRepository.getInstance(this)
                .add(AppNotification.create(ch, title, body, "ranking"));

        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) {
            // Pobeda → dodaj zvezde; poraz → registruj prisustvo sa 0 (da igrač bude vidljiv)
            int starGain = won ? 10 + getMyScore() / 40 : 0;
            RankingRepository.getInstance()
                    .updateEntry(fbUser.getUid(), myUsername, myRegion, myLeague, starGain);
        }
    }

    private void handleTournamentEnd() {
        boolean iAmP1    = "p1".equals(getMyRole());
        boolean won      = iAmP1 ? (totalP1 > totalP2) : (totalP2 > totalP1);
        boolean isFinal  = "final".equals(tournamentPhase);
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = fbUser != null ? fbUser.getUid() : null;

        if (uid != null) {
            if (won) {
                // Regular win rewards (stars + milestone tokens) + tournament bonus
                UserRepository.getInstance().incrementStats(uid, true, getMyScore(),
                        (oldL, newL) -> showLeagueChangeToast(oldL, newL));
                UserRepository.getInstance().addTokens(uid, isFinal ? 3 : 2);
                if (isFinal) UserRepository.getInstance().addStars(uid, 10); // final bonus stars
                RankingRepository.getInstance()
                        .updateEntry(uid, myUsername, myRegion, myLeague, 10);
                MissionRepository.tryComplete(this, uid,
                        MissionRepository.MISSION_WIN_TOURNAMENT);
                TournamentRepository.getInstance()
                        .reportWin(tournamentId, tournamentPhase, uid);
            } else if (isFinal) {
                // Final loss: regular rules (negative stars, counts as played)
                UserRepository.getInstance().incrementStats(uid, false, getMyScore(),
                        (oldL, newL) -> showLeagueChangeToast(oldL, newL));
            } else {
                // Semi loss: only count the game, no star penalty per spec
                UserRepository.getInstance().incrementGameCount(uid, false);
            }
        }

        Intent i = new Intent(this, TournamentResultActivity.class);
        i.putExtra(TournamentResultActivity.EXTRA_TOURNAMENT_ID, tournamentId);
        i.putExtra(TournamentResultActivity.EXTRA_PHASE, tournamentPhase);
        i.putExtra(TournamentResultActivity.EXTRA_WON, won);
        i.putExtra(TournamentResultActivity.EXTRA_MY_UID, uid != null ? uid : "");
        i.putExtra(TournamentResultActivity.EXTRA_MY_SCORE, getMyScore());
        startActivity(i);
        finish();
    }

    private void checkGameMissions() {
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser == null) return;
        String uid = fbUser.getUid();
        if (isFriendly) {
            MissionRepository.tryComplete(this, uid, MissionRepository.MISSION_PLAY_FRIENDLY);
        } else if (challengeId == null && roomRef != null) {
            boolean iAmP1 = "p1".equals(getMyRole());
            boolean won   = iAmP1 ? (totalP1 > totalP2) : (totalP2 > totalP1);
            if (won) MissionRepository.tryComplete(this, uid, MissionRepository.MISSION_WIN_GAME);
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

    public void showLeagueChangeToast(int oldLeague, int newLeague) {
        String msg = newLeague > oldLeague
                ? "Napredovao si u ligu: " + LeagueUtil.getLabel(newLeague) + "!"
                : "Pao si u ligu: " + LeagueUtil.getLabel(newLeague);
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        NotificationRepository.getInstance(this).add(AppNotification.create(
                AppNotification.Channel.REWARD,
                newLeague > oldLeague ? "Liga napredak!" : "Pad u ligi",
                LeagueUtil.getLabel(oldLeague) + " → " + LeagueUtil.getLabel(newLeague),
                "ranking"));
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

package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.Challenge;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.ChallengeRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChallengeListActivity extends AppCompatActivity {

    private String myUid;
    private String myUsername;
    private String myRegion;
    private int    myStars;
    private int    myTokens;

    private ChallengeAdapter  adapter;
    private final List<Challenge> challenges = new ArrayList<>();
    private ValueEventListener listListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_list);

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu == null) { finish(); return; }
        myUid = fu.getUid();

        RecyclerView rv = findViewById(R.id.rvChallenges);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChallengeAdapter(challenges);
        rv.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCreateChallenge).setOnClickListener(v -> showCreateDialog());

        loadProfileThenListen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listListener != null)
            ChallengeRepository.getInstance().removeListListener(listListener);
    }

    // ── Učitaj profil → onda sluša izazove za region ──────────────────────────

    private void loadProfileThenListen() {
        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override public void onLoaded(User u) {
                myUsername = u.username != null ? u.username : myUid;
                myRegion   = u.region   != null ? u.region   : "global";
                myStars    = u.stars;
                myTokens   = u.tokens;
                startListening();
            }
            @Override public void onError(String msg) {
                myUsername = myUid; myRegion = "global";
                startListening();
            }
        });
    }

    private void startListening() {
        listListener = ChallengeRepository.getInstance()
                .listenOpenChallenges(myRegion, list -> runOnUiThread(() -> {
                    challenges.clear();
                    challenges.addAll(list);
                    adapter.notifyDataSetChanged();
                    TextView tvEmpty = findViewById(R.id.tvNoChallenges);
                    if (tvEmpty != null)
                        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                }));
    }

    // ── Kreiranje izazova ─────────────────────────────────────────────────────

    private void showCreateDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_create_challenge, null);
        NumberPicker npStars  = v.findViewById(R.id.npStars);
        NumberPicker npTokens = v.findViewById(R.id.npTokens);
        npStars.setMinValue(1);  npStars.setMaxValue(10);  npStars.setValue(5);
        npTokens.setMinValue(0); npTokens.setMaxValue(2);  npTokens.setValue(1);

        new AlertDialog.Builder(this)
                .setTitle("Postavi izazov")
                .setView(v)
                .setPositiveButton("Postavi", (d, w) -> {
                    int stars  = npStars.getValue();
                    int tokens = npTokens.getValue();
                    tryCreateChallenge(stars, tokens);
                })
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void tryCreateChallenge(int stakeStars, int stakeTokens) {
        if (myStars < stakeStars) {
            Toast.makeText(this, "Nemaš dovoljno zvezdi!", Toast.LENGTH_SHORT).show(); return;
        }
        if (myTokens < stakeTokens) {
            Toast.makeText(this, "Nemaš dovoljno tokena!", Toast.LENGTH_SHORT).show(); return;
        }

        // Oduzmi ulog od kreatora
        deductStake(stakeStars, stakeTokens, () -> {
            String id = UUID.randomUUID().toString();
            Challenge c = new Challenge(id, myUid, myUsername, myRegion, stakeStars, stakeTokens);
            ChallengeRepository.getInstance().createChallenge(c, myUsername,
                    new ChallengeRepository.Callback() {
                @Override public void onSuccess() {
                    Toast.makeText(ChallengeListActivity.this,
                            "Izazov postavljen!", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(String msg) {
                    Toast.makeText(ChallengeListActivity.this,
                            "Greška: " + msg, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // ── Pridruživanje izazovu ─────────────────────────────────────────────────

    private void tryJoinChallenge(Challenge c) {
        if (c.players != null && c.players.containsKey(myUid)) {
            // Već sam unutra — otvori igru ako je počela
            if ("in_progress".equals(c.status)) launchGame(c.challengeId);
            else Toast.makeText(this, "Čekamo ostale igrače...", Toast.LENGTH_SHORT).show();
            return;
        }
        if (myStars < c.stakeStars) {
            Toast.makeText(this, "Nemaš dovoljno zvezdi!", Toast.LENGTH_SHORT).show(); return;
        }
        if (myTokens < c.stakeTokens) {
            Toast.makeText(this, "Nemaš dovoljno tokena!", Toast.LENGTH_SHORT).show(); return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Prihvati izazov")
                .setMessage("Ulog: " + c.stakeStars + " ⭐ i " + c.stakeTokens + " 🪙\n"
                        + "Ulažeš isti iznos. Da li prihvataš?")
                .setPositiveButton("Prihvati", (d, w) ->
                        deductStake(c.stakeStars, c.stakeTokens, () -> joinAndMaybeStart(c)))
                .setNegativeButton("Otkaži", null)
                .show();
    }

    private void joinAndMaybeStart(Challenge c) {
        ChallengeRepository.getInstance().joinChallenge(c.challengeId, myUid, myUsername,
                new ChallengeRepository.Callback() {
            @Override public void onSuccess() {
                // Jedan čitat za provjeru popunjenosti — odmah ukloniti listener
                com.google.firebase.database.ValueEventListener[] holder = new com.google.firebase.database.ValueEventListener[1];
                holder[0] = ChallengeRepository.getInstance().listenChallenge(c.challengeId,
                        new ChallengeRepository.ChallengeCallback() {
                    @Override public void onLoaded(Challenge updated) {
                        ChallengeRepository.getInstance().removeListener(c.challengeId, holder[0]);
                        if (updated.isFull()) {
                            ChallengeRepository.getInstance().startChallenge(
                                    c.challengeId, new ChallengeRepository.Callback() {
                                @Override public void onSuccess() { launchGame(c.challengeId); }
                                @Override public void onError(String m) { launchGame(c.challengeId); }
                            });
                        } else {
                            Toast.makeText(ChallengeListActivity.this,
                                    "Pridružen! Čekamo ostale...", Toast.LENGTH_SHORT).show();
                            openWaitingScreen(c.challengeId);
                        }
                    }
                    @Override public void onError(String msg) {}
                });
            }
            @Override public void onError(String msg) {
                Toast.makeText(ChallengeListActivity.this,
                        "Izazov je pun ili je već počeo.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openWaitingScreen(String challengeId) {
        Intent intent = new Intent(this, ChallengeWaitActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
    }

    private void launchGame(String challengeId) {
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(LobbyActivity.EXTRA_CHALLENGE_ID, challengeId);
        startActivity(intent);
    }

    // ── Start od strane kreatora ──────────────────────────────────────────────

    private void creatorStart(Challenge c) {
        if (c.playerCount() < 2) {
            Toast.makeText(this, "Potrebno je najmanje 2 igrača!", Toast.LENGTH_SHORT).show();
            return;
        }
        ChallengeRepository.getInstance().startChallenge(c.challengeId,
                new ChallengeRepository.Callback() {
            @Override public void onSuccess() { launchGame(c.challengeId); }
            @Override public void onError(String msg) {
                Toast.makeText(ChallengeListActivity.this, "Greška", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Oduzimanje uloga (Firestore transakcija) ──────────────────────────────

    private void deductStake(int stars, int tokens, Runnable onSuccess) {
        FirebaseFirestore.getInstance().runTransaction(tx -> {
            var ref = FirebaseFirestore.getInstance().collection("users").document(myUid);
            long s = safeL(tx.get(ref).getLong("stars"));
            long t = safeL(tx.get(ref).getLong("tokens"));
            if (s < stars || t < tokens) throw new RuntimeException("insufficient");
            tx.update(ref, "stars",  s - stars);
            tx.update(ref, "tokens", t - tokens);
            return null;
        }).addOnSuccessListener(v -> {
            myStars  -= stars;
            myTokens -= tokens;
            onSuccess.run();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Nemaš dovoljno resursa!", Toast.LENGTH_SHORT).show());
    }

    private long safeL(Long v) { return v != null ? v : 0L; }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private class ChallengeAdapter extends RecyclerView.Adapter<ChallengeAdapter.VH> {
        private final List<Challenge> data;
        ChallengeAdapter(List<Challenge> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            Challenge c = data.get(pos);
            h.tvCreator.setText(c.creatorName);
            h.tvStake.setText("⭐ " + c.stakeStars + "   🪙 " + c.stakeTokens);
            h.tvPlayers.setText(c.playerCount() + "/4 igrača");

            boolean isMine   = myUid.equals(c.creatorUid);
            boolean isMember = c.players != null && c.players.containsKey(myUid);

            if (isMine) {
                h.btnAction.setText("START");
                h.btnAction.setOnClickListener(v -> creatorStart(c));
            } else if (isMember) {
                h.btnAction.setText("ČEKAM...");
                h.btnAction.setEnabled(false);
            } else {
                h.btnAction.setText("PRIHVATI");
                h.btnAction.setEnabled(!c.isFull());
                h.btnAction.setOnClickListener(v -> tryJoinChallenge(c));
            }
        }

        @Override public int getItemCount() { return data.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvCreator, tvStake, tvPlayers;
            Button   btnAction;
            VH(View v) {
                super(v);
                tvCreator  = v.findViewById(R.id.tvChallengeCreator);
                tvStake    = v.findViewById(R.id.tvChallengeStake);
                tvPlayers  = v.findViewById(R.id.tvChallengePlayers);
                btnAction  = v.findViewById(R.id.btnChallengeAction);
            }
        }
    }
}

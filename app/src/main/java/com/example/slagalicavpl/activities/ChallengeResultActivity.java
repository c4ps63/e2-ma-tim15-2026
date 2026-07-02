package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.model.Challenge;
import com.example.slagalicavpl.repository.ChallengeRepository;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChallengeResultActivity extends AppCompatActivity {

    private String            challengeId;
    private ValueEventListener listener;
    private boolean           settled = false;

    private String  myUid;
    private TextView tvMyResult;
    private ResultAdapter adapter;
    private final List<RankEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_challenge_result);

        challengeId = getIntent().getStringExtra(LobbyActivity.EXTRA_CHALLENGE_ID);
        myUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";

        tvMyResult = findViewById(R.id.tvMyResult);
        Button btnDone = findViewById(R.id.btnDone);
        if (btnDone != null) btnDone.setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvResults);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultAdapter(entries);
        rv.setAdapter(adapter);

        if (challengeId == null) { finish(); return; }
        listenForResults();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null)
            ChallengeRepository.getInstance().removeListener(challengeId, listener);
    }

    private void listenForResults() {
        listener = ChallengeRepository.getInstance().listenChallenge(challengeId,
                new ChallengeRepository.ChallengeCallback() {
            @Override public void onLoaded(Challenge c) {
                boolean allDone = allFinished(c);
                buildLeaderboard(c);
                if (allDone && !settled) {
                    settled = true;
                    ChallengeRepository.getInstance().settleChallenge(c);
                    sendChallengeResultNotification(c);
                }
            }
            @Override public void onError(String msg) {}
        });
    }

    private void sendChallengeResultNotification(Challenge c) {
        if (c.players == null || myUid.isEmpty()) return;
        Challenge.ChallengePlayer me = c.players.get(myUid);
        if (me == null) return;

        // Odredi rank igrača
        long rank = c.players.values().stream()
                .filter(p -> p.score > me.score).count() + 1;
        int n = c.players.size();

        String title, body;
        AppNotification.Channel ch;
        if (rank == 1) {
            int prize = (int)(c.stakeStars * n * 0.75);
            title = "Pobedio si izazov! +" + prize + " ⭐";
            body  = "Zauzeo si 1. mesto od " + n + " igrača sa " + me.score + " bodova.";
            ch    = AppNotification.Channel.REWARD;
        } else {
            title = rank + ". mesto u izazovu";
            body  = "Završio si na " + rank + ". mestu od " + n + " sa " + me.score + " bodova.";
            ch    = AppNotification.Channel.RANKING;
        }
        NotificationRepository.getInstance(this)
                .add(AppNotification.create(ch, title, body, "ranking"));
    }

    private boolean allFinished(Challenge c) {
        if (c.players == null) return false;
        for (Challenge.ChallengePlayer p : c.players.values())
            if (!p.finished) return false;
        return !c.players.isEmpty();
    }

    private void buildLeaderboard(Challenge c) {
        if (c.players == null) return;
        boolean allDone = allFinished(c);
        int n    = c.players.size();
        int pool = c.stakeStars * n;

        List<Map.Entry<String, Challenge.ChallengePlayer>> list =
                new ArrayList<>(c.players.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue().score, a.getValue().score));

        entries.clear();
        for (int i = 0; i < list.size(); i++) {
            String                    uid    = list.get(i).getKey();
            Challenge.ChallengePlayer player = list.get(i).getValue();
            String statusLabel;
            if (!allDone) {
                // Rezultat i plasman se ne mogu objaviti dok svi igrači ne završe partiju
                statusLabel = player.finished ? "ZAVRŠIO" : "IGRA…";
            } else if (i == 0) {
                statusLabel = "+" + (int) (pool * 0.75) + " ⭐  (75% poola)";
            } else if (i == 1) {
                statusLabel = "±0 ⭐  (vraćen ulog)";
            } else {
                statusLabel = "-" + c.stakeStars + " ⭐";
            }
            entries.add(new RankEntry(i + 1, player.name, player.score, statusLabel,
                    uid.equals(myUid), allDone));
        }
        adapter.notifyDataSetChanged();

        // Prikaži moj rezultat — konačan plasman tek kad SVI igrači završe
        for (RankEntry e : entries) {
            if (e.isMe) {
                if (allDone) {
                    String status = e.rank == 1 ? "🥇 POBEDNIK!" : e.rank == 2 ? "🥈 2. MESTO" : "💸 Izgubio si";
                    tvMyResult.setText(status + " · " + e.scoreLabel);
                } else {
                    tvMyResult.setText("Tvoj rezultat: " + e.score + " bodova · čekamo ostale igrače...");
                }
                break;
            }
        }

        // Ako još čekamo igrače, prikaži status
        TextView tvWaiting = findViewById(R.id.tvWaiting);
        if (tvWaiting != null) {
            tvWaiting.setVisibility(allDone ? View.GONE : View.VISIBLE);
            if (!allDone) {
                long done = c.players.values().stream().filter(p -> p.finished).count();
                tvWaiting.setText("Čekamo " + (n - done) + "/" + n + " igrača da završe...");
            }
        }
    }

    // ── Model ─────────────────────────────────────────────────────────────────

    static class RankEntry {
        int     rank;
        String  name;
        int     score;
        String  scoreLabel;
        boolean isMe;
        boolean finalResult; // true only once every participant has finished
        RankEntry(int rank, String name, int score, String scoreLabel, boolean isMe, boolean finalResult) {
            this.rank = rank; this.name = name; this.score = score;
            this.scoreLabel = scoreLabel; this.isMe = isMe; this.finalResult = finalResult;
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final List<RankEntry> data;
        ResultAdapter(List<RankEntry> data) { this.data = data; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_challenge_result, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            RankEntry e = data.get(pos);
            if (e.finalResult) {
                String medal = e.rank == 1 ? "🥇" : e.rank == 2 ? "🥈" : e.rank == 3 ? "🥉" : "  ";
                h.tvRank.setText(medal + " " + e.rank + ".");
            } else {
                // Plasman još nije konačan dok svi igrači ne završe — bez medalje
                h.tvRank.setText(e.rank + ".");
            }
            h.tvName.setText(e.name + (e.isMe ? " (ti)" : ""));
            h.tvScore.setText(e.score + " bodova");
            h.tvPrize.setText(e.scoreLabel);
            h.itemView.setAlpha(e.isMe ? 1f : 0.85f);
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvName, tvScore, tvPrize;
            VH(View v) {
                super(v);
                tvRank  = v.findViewById(R.id.tvResultRank);
                tvName  = v.findViewById(R.id.tvResultName);
                tvScore = v.findViewById(R.id.tvResultScore);
                tvPrize = v.findViewById(R.id.tvResultPrize);
            }
        }
    }
}

package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.slagalicavpl.model.RankingEntry;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.example.slagalicavpl.repository.RankingRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RankingActivity extends AppCompatActivity {

    private String myUid = "";

    private Button   btnWeekly;
    private Button   btnMonthly;
    private TextView tvCycleRange;
    private RecyclerView rvRanking;
    private TextView tvEmpty;

    private boolean showWeekly = true;

    private RankingAdapter    adapter;
    private ValueEventListener activeListener;
    private String             activeType;
    private String             activeCycleKey;

    private final Handler  refreshHandler  = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
        if (fu != null) myUid = fu.getUid();

        btnWeekly    = findViewById(R.id.btnWeekly);
        btnMonthly   = findViewById(R.id.btnMonthly);
        tvCycleRange = findViewById(R.id.tvCycleRange);
        rvRanking    = findViewById(R.id.rvRanking);
        tvEmpty      = findViewById(R.id.tvEmpty);

        adapter = new RankingAdapter(myUid);
        rvRanking.setLayoutManager(new LinearLayoutManager(this));
        rvRanking.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnWeekly.setOnClickListener(v -> setTab(true));
        btnMonthly.setOnClickListener(v -> setTab(false));

        checkPreviousCycleRewards();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setTab(showWeekly);
        scheduleRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacksAndMessages(null);
        detachListener();
    }

    // ── Tabovi ───────────────────────────────────────────────────────────────

    private void setTab(boolean weekly) {
        showWeekly = weekly;
        btnWeekly.setBackgroundResource(weekly ? R.drawable.btn_cartoon_yellow : R.drawable.card_paper);
        btnMonthly.setBackgroundResource(weekly ? R.drawable.card_paper : R.drawable.btn_cartoon_yellow);

        String key   = weekly ? RankingRepository.getCurrentWeekKey()  : RankingRepository.getCurrentMonthKey();
        String range = weekly ? RankingRepository.weekDateRange(key)   : RankingRepository.monthDateRange(key);
        tvCycleRange.setText(range);

        detachListener();
        activeType     = weekly ? "weekly" : "monthly";
        activeCycleKey = key;
        activeListener = RankingRepository.getInstance().listenEntries(activeType, activeCycleKey,
                this::applyList);
    }

    private void scheduleRefresh() {
        refreshHandler.postDelayed(() -> {
            reloadCurrentTab();
            scheduleRefresh();
        }, 120_000L);
    }

    private void reloadCurrentTab() {
        detachListener();
        activeListener = RankingRepository.getInstance().listenEntries(activeType, activeCycleKey,
                this::applyList);
    }

    private void detachListener() {
        if (activeListener != null) {
            RankingRepository.getInstance().removeListener(activeType, activeCycleKey, activeListener);
            activeListener = null;
        }
    }

    private void applyList(List<RankingEntry> entries) {
        adapter.setData(entries);
        boolean empty = entries.isEmpty();
        rvRanking.setVisibility(empty ? View.GONE  : View.VISIBLE);
        tvEmpty.setVisibility(empty   ? View.VISIBLE : View.GONE);
    }

    // ── Provera nagrada za prethodni ciklus ───────────────────────────────────

    private void checkPreviousCycleRewards() {
        if (myUid.isEmpty()) return;

        // Nedeljni
        String prevWeek = RankingRepository.getPreviousWeekKey();
        RankingRepository.getInstance().loadOnce("weekly", prevWeek, entries -> {
            int rank   = findMyRank(myUid, entries);
            int tokens = RankingRepository.weeklyTokenReward(rank);
            if (tokens > 0) {
                UserRepository.getInstance().claimRankingReward(myUid, true, prevWeek, tokens,
                        new UserRepository.Callback() {
                            @Override public void onSuccess() {
                                launchRewardScreen(rank, tokens, "nedeljnoj");
                                sendRankingNotif(rank, tokens, "nedeljnoj");
                            }
                            @Override public void onError(String msg) {}
                        });
            }
        });

        // Mesečni
        String prevMonth = RankingRepository.getPreviousMonthKey();
        RankingRepository.getInstance().loadOnce("monthly", prevMonth, entries -> {
            int rank   = findMyRank(myUid, entries);
            int tokens = RankingRepository.monthlyTokenReward(rank);
            if (tokens > 0) {
                UserRepository.getInstance().claimRankingReward(myUid, false, prevMonth, tokens,
                        new UserRepository.Callback() {
                            @Override public void onSuccess() {
                                launchRewardScreen(rank, tokens, "mesečnoj");
                                sendRankingNotif(rank, tokens, "mesečnoj");
                            }
                            @Override public void onError(String msg) {}
                        });
            }
        });
    }

    private int findMyRank(String uid, List<RankingEntry> sorted) {
        for (int i = 0; i < sorted.size(); i++) {
            if (uid.equals(sorted.get(i).uid)) return i + 1;
        }
        return sorted.size() + 1;
    }

    private void launchRewardScreen(int rank, int tokens, String cycleLabel) {
        Intent i = new Intent(this, RankingRewardActivity.class);
        i.putExtra("rank",       rank);
        i.putExtra("tokens",     tokens);
        i.putExtra("cycleLabel", cycleLabel);
        startActivity(i);
    }

    private void sendRankingNotif(int rank, int tokens, String cycleLabel) {
        NotificationRepository notifRepo = NotificationRepository.getInstance(this);
        String title = "Nagrada za " + rank + ". mesto!";
        String body  = "Osvajač " + rank + ". mesta na " + cycleLabel
                + " rang listi dobija +" + tokens + " žetona.";
        notifRepo.add(AppNotification.create(AppNotification.Channel.REWARD, title, body, "ranking"));
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.VH> {

        private final List<RankingEntry> items = new ArrayList<>();
        private final String             myUid;

        RankingAdapter(String myUid) {
            this.myUid = myUid;
        }

        void setData(List<RankingEntry> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ranking, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            RankingEntry e    = items.get(pos);
            int          rank = pos + 1;
            boolean      isMe = myUid != null && myUid.equals(e.uid);

            // Rank chip
            String rankText;
            int    chipColor;
            if      (rank == 1) { rankText = "🥇"; chipColor = 0xFFFFD700; }
            else if (rank == 2) { rankText = "🥈"; chipColor = 0xFFC0C0C0; }
            else if (rank == 3) { rankText = "🥉"; chipColor = 0xFFCD853F; }
            else                { rankText = String.valueOf(rank); chipColor = 0xFFDDD6C1; }

            h.tvRank.setText(rankText);
            h.tvRank.setTextSize(rank <= 3 ? 18 : 13);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            gd.setColor(chipColor);
            h.tvRank.setBackground(gd);

            // Username
            h.tvUsername.setText(isMe ? e.username + " (ti)" : e.username);

            // Liga
            h.tvLeague.setText("Liga " + (e.league > 0 ? e.league : 1));

            // Zvezde
            h.tvStars.setText(String.valueOf(Math.max(0, e.stars)));

            // Isticanje tekućeg igrača
            h.itemView.setAlpha(isMe ? 1f : 0.88f);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvRank, tvUsername, tvLeague, tvStars;
            VH(View v) {
                super(v);
                tvRank     = v.findViewById(R.id.tvRank);
                tvUsername = v.findViewById(R.id.tvUsername);
                tvLeague   = v.findViewById(R.id.tvLeague);
                tvStars    = v.findViewById(R.id.tvStars);
            }
        }
    }
}

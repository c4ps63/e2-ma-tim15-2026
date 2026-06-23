package com.example.slagalicavpl.activities;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.DailyMission;
import com.example.slagalicavpl.repository.MissionRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MissionsActivity extends AppCompatActivity {

    private MissionRepository repo;
    private MissionAdapter    adapter;
    private TextView          tvProgress;
    private TextView          tvBonusStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_missions);

        repo = MissionRepository.getInstance(this);

        RecyclerView rv = findViewById(R.id.rvMissions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MissionAdapter();
        rv.setAdapter(adapter);

        tvProgress    = findViewById(R.id.tvProgress);
        tvBonusStatus = findViewById(R.id.tvBonusStatus);

        TextView tvDate = findViewById(R.id.tvMissionsDate);
        tvDate.setText(new SimpleDateFormat("d. MMM yyyy.", new Locale("sr")).format(new Date()));

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        adapter.setData(repo.getAll());
        int completed = repo.getCompletedCount();
        tvProgress.setText(completed + "/4 misija");
        if (repo.isBonusClaimed()) {
            tvBonusStatus.setVisibility(View.VISIBLE);
        } else {
            tvBonusStatus.setVisibility(View.GONE);
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.VH> {

        private final List<DailyMission> items = new ArrayList<>();

        void setData(List<DailyMission> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_mission, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            DailyMission m = items.get(pos);
            h.tvTitle.setText(m.title);

            GradientDrawable gd = new GradientDrawable();
            gd.setShape(GradientDrawable.OVAL);
            if (m.completed) {
                gd.setColor(0xFF21C87A);   // green
                h.tvCheck.setText("✓");
                h.tvTitle.setAlpha(1f);
            } else {
                gd.setColor(0x22102341);   // ink faint circle
                h.tvCheck.setText("");
                h.tvTitle.setAlpha(0.45f);
            }
            h.tvCheck.setBackground(gd);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvCheck, tvTitle;
            VH(View v) {
                super(v);
                tvCheck = v.findViewById(R.id.tvCheck);
                tvTitle = v.findViewById(R.id.tvMissionTitle);
            }
        }
    }
}

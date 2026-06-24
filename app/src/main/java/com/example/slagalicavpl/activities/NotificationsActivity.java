package com.example.slagalicavpl.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.notification.NotificationAdapter;
import com.example.slagalicavpl.repository.NotificationRepository;

import java.util.List;

public class NotificationsActivity extends AppCompatActivity
        implements NotificationAdapter.Listener {

    private NotificationRepository repo;
    private NotificationAdapter    adapter;

    private TextView tvBadge;
    private Button   btnFilterAll;
    private Button   btnFilterUnread;

    private boolean showOnlyUnread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        repo    = NotificationRepository.getInstance(this);
        adapter = new NotificationAdapter(this);

        // Bind views
        RecyclerView rv = findViewById(R.id.rvNotifications);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        tvBadge       = findViewById(R.id.tvUnreadBadge);
        btnFilterAll    = findViewById(R.id.btnFilterAll);
        btnFilterUnread = findViewById(R.id.btnFilterUnread);

        // Dugmad
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnMarkAllRead).setOnClickListener(v -> markAllRead());

        btnFilterAll.setOnClickListener(v -> setFilter(false));
        btnFilterUnread.setOnClickListener(v -> setFilter(true));

        // Inicijalni prikaz
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ── NotificationAdapter.Listener ─────────────────────────────────────────

    @Override
    public void onMarkRead(AppNotification n) {
        repo.markRead(n.id);
        adapter.markRead(n.id);
        updateBadge();
        // Ako je aktivan filter "nepročitane", ukloni iz liste
        if (showOnlyUnread) refresh();
    }

    @Override
    public void onAction(AppNotification n) {
        repo.markRead(n.id);
        adapter.markRead(n.id);
        updateBadge();

        Intent intent = null;
        switch (n.action != null ? n.action : "") {
            case "chat":
                intent = new Intent(this, ChatActivity.class);
                break;
            case "reward":
                intent = new Intent(this, ProfileActivity.class);
                break;
            case "friend_invite":
                intent = new Intent(this, FriendsActivity.class);
                break;
            case "ranking":
                intent = new Intent(this, RankingActivity.class);
                break;
        }
        if (intent != null) startActivity(intent);

        if (showOnlyUnread) refresh();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setFilter(boolean onlyUnread) {
        showOnlyUnread = onlyUnread;
        // Vizuelni feedback na filter dugmadima
        btnFilterAll.setBackgroundResource(
                onlyUnread ? R.drawable.card_paper : R.drawable.btn_cartoon_yellow);
        btnFilterUnread.setBackgroundResource(
                onlyUnread ? R.drawable.btn_cartoon_yellow : R.drawable.card_paper);
        refresh();
    }

    private void markAllRead() {
        repo.markAllRead();
        refresh();
    }

    private void refresh() {
        List<AppNotification> list = showOnlyUnread ? repo.getUnread() : repo.getAll();
        adapter.setData(list);
        updateBadge();
    }

    private void updateBadge() {
        int count = repo.getUnreadCount();
        if (count > 0) {
            tvBadge.setVisibility(android.view.View.VISIBLE);
            tvBadge.setText(String.valueOf(count));
        } else {
            tvBadge.setVisibility(android.view.View.GONE);
        }
    }
}

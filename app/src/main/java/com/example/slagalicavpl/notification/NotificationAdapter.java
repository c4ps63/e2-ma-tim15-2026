package com.example.slagalicavpl.notification;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.AppNotification;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.VH> {

    public interface Listener {
        void onMarkRead(AppNotification n);
        void onAction(AppNotification n);
    }

    private final List<AppNotification> items = new ArrayList<>();
    private final Listener listener;

    public NotificationAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setData(List<AppNotification> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    /** Pronalazi i ažurira jednu stavku bez reload-a cijele liste. */
    public void markRead(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                items.get(i).read = true;
                notifyItemChanged(i);
                break;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        h.bind(items.get(position), listener);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class VH extends RecyclerView.ViewHolder {

        final View     bar;
        final TextView chip;
        final TextView time;
        final View     dot;
        final TextView title;
        final TextView body;
        final Button   btnAction;

        VH(@NonNull View v) {
            super(v);
            bar       = v.findViewById(R.id.viewChannelBar);
            chip      = v.findViewById(R.id.tvChannelChip);
            time      = v.findViewById(R.id.tvTime);
            dot       = v.findViewById(R.id.viewUnreadDot);
            title     = v.findViewById(R.id.tvTitle);
            body      = v.findViewById(R.id.tvBody);
            btnAction = v.findViewById(R.id.btnAction);
        }

        void bind(AppNotification n, Listener listener) {
            int barColor  = channelColor(n.channel);
            int chipBg    = chipBackground(n.channel);

            bar.setBackgroundColor(barColor);
            chip.setText(n.channel.label());
            chip.setBackgroundResource(chipBg);
            chip.setTextColor(chipTextColor(n.channel));
            time.setText(relativeTime(n.timestamp));
            title.setText(n.title);
            body.setText(n.body);

            // Pročitano / nepročitano
            boolean unread = !n.read;
            dot.setVisibility(unread ? View.VISIBLE : View.GONE);
            itemView.setAlpha(unread ? 1f : 0.55f);

            // Akcija (OTVORI dugme)
            boolean hasAction = unread && n.action != null && !n.action.isEmpty();
            btnAction.setVisibility(hasAction ? View.VISIBLE : View.GONE);
            if (hasAction) btnAction.setOnClickListener(v -> listener.onAction(n));

            // Klik na cijelu karticu → označi kao pročitano
            itemView.setOnClickListener(v -> {
                if (!n.read) listener.onMarkRead(n);
            });
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private static int channelColor(AppNotification.Channel ch) {
            switch (ch) {
                case CHAT:    return Color.parseColor("#7CC6FF");
                case RANKING: return Color.parseColor("#FFD23F");
                case REWARD:  return Color.parseColor("#21C87A");
                default:      return Color.parseColor("#1F4FA8");
            }
        }

        private static int chipBackground(AppNotification.Channel ch) {
            switch (ch) {
                case CHAT:    return R.drawable.btn_cartoon_blue;
                case RANKING: return R.drawable.btn_cartoon_yellow;
                case REWARD:  return R.drawable.btn_cartoon_green;
                default:      return R.drawable.card_paper;
            }
        }

        private static int chipTextColor(AppNotification.Channel ch) {
            return (ch == AppNotification.Channel.RANKING || ch == AppNotification.Channel.OTHER)
                    ? Color.parseColor("#102341")
                    : Color.WHITE;
        }

        private static String relativeTime(long ts) {
            long diff = System.currentTimeMillis() - ts;
            long min  = diff / 60_000;
            long hr   = diff / 3_600_000;
            long day  = diff / 86_400_000;
            if (min  < 1)  return "upravo";
            if (min  < 60) return "pre " + min + " min";
            if (hr   < 24) return "pre " + hr + "h";
            if (day  == 1) return "juče";
            return "pre " + day + " dana";
        }
    }
}

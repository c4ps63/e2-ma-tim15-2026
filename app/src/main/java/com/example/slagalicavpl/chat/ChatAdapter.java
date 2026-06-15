package com.example.slagalicavpl.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MINE  = 0;
    private static final int TYPE_OTHER = 1;

    private final String myUid;
    private final List<ChatMessage> items = new ArrayList<>();
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("dd.MM.  HH:mm", Locale.getDefault());

    public ChatAdapter(String myUid) {
        this.myUid = myUid;
    }

    public void setMessages(List<ChatMessage> messages) {
        items.clear();
        items.addAll(messages);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return myUid.equals(items.get(position).senderId) ? TYPE_MINE : TYPE_OTHER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_MINE) {
            View v = inf.inflate(R.layout.item_chat_mine, parent, false);
            return new MineHolder(v);
        } else {
            View v = inf.inflate(R.layout.item_chat_other, parent, false);
            return new OtherHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage msg = items.get(position);
        String time = fmt.format(new Date(msg.timestamp));
        if (holder instanceof MineHolder) {
            MineHolder h = (MineHolder) holder;
            h.tvText.setText(msg.text);
            h.tvTime.setText(time);
        } else {
            OtherHolder h = (OtherHolder) holder;
            h.tvSender.setText(msg.senderName);
            h.tvText.setText(msg.text);
            h.tvTime.setText(time);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    // ── ViewHolders ───────────────────────────────────────────────────────────

    static class MineHolder extends RecyclerView.ViewHolder {
        TextView tvText, tvTime;
        MineHolder(View v) {
            super(v);
            tvText = v.findViewById(R.id.tvChatText);
            tvTime = v.findViewById(R.id.tvChatTime);
        }
    }

    static class OtherHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvText, tvTime;
        OtherHolder(View v) {
            super(v);
            tvSender = v.findViewById(R.id.tvChatSender);
            tvText   = v.findViewById(R.id.tvChatText);
            tvTime   = v.findViewById(R.id.tvChatTime);
        }
    }
}

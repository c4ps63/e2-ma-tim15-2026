package com.example.slagalicavpl.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.slagalicavpl.R;
import com.example.slagalicavpl.chat.ChatAdapter;
import com.example.slagalicavpl.model.AppNotification;
import com.example.slagalicavpl.model.ChatMessage;
import com.example.slagalicavpl.model.User;
import com.example.slagalicavpl.repository.ChatRepository;
import com.example.slagalicavpl.repository.NotificationRepository;
import com.example.slagalicavpl.repository.UserRepository;
import com.example.slagalicavpl.service.AuthService;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ChatRepository       chatRepo;
    private NotificationRepository notifRepo;
    private ChatAdapter          adapter;
    private ListenerRegistration listener;

    private String myUid;
    private String myUsername;
    private String region;

    // true dok je aktivnost vidljiva korisniku
    private boolean isInForeground = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatRepo  = ChatRepository.getInstance();
        notifRepo = NotificationRepository.getInstance(this);

        FirebaseUser firebaseUser = AuthService.getInstance().getCurrentUser();
        if (firebaseUser == null) { finish(); return; }
        myUid = firebaseUser.getUid();

        RecyclerView rv = findViewById(R.id.rvChat);
        EditText     et = findViewById(R.id.etChatInput);
        Button       btnSend = findViewById(R.id.btnChatSend);
        Button       btnBack = findViewById(R.id.btnChatBack);
        TextView     tvRegion = findViewById(R.id.tvChatRegion);

        btnBack.setOnClickListener(v -> finish());

        // Učitaj profil da dobijemo username i region
        UserRepository.getInstance().loadProfile(myUid, new UserRepository.ProfileCallback() {
            @Override
            public void onLoaded(User user) {
                myUsername = user.username;
                region     = user.region;

                tvRegion.setText("ČET · " + region.toUpperCase());

                adapter = new ChatAdapter(myUid);
                rv.setLayoutManager(new LinearLayoutManager(ChatActivity.this));
                rv.setAdapter(adapter);

                // Real-time listener
                listener = chatRepo.listenMessages(region, messages -> {
                    notifyIncomingIfNeeded(messages);
                    adapter.setMessages(messages);
                    if (!messages.isEmpty()) rv.scrollToPosition(messages.size() - 1);
                });

                btnSend.setOnClickListener(v -> {
                    String text = et.getText().toString().trim();
                    if (TextUtils.isEmpty(text)) return;
                    et.setText("");
                    ChatMessage msg = new ChatMessage(myUid, myUsername, text);
                    chatRepo.sendMessage(region, msg, new UserRepository.Callback() {
                        @Override public void onSuccess() {}
                        @Override public void onError(String message) {
                            Toast.makeText(ChatActivity.this,
                                    "Greška pri slanju: " + message, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }

            @Override
            public void onError(String msg) {
                Toast.makeText(ChatActivity.this, "Greška: " + msg, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Šalje lokalnu notifikaciju ako je pristigla nova poruka od drugog igrača
     * dok korisnik nije na ovom ekranu.
     */
    private void notifyIncomingIfNeeded(List<ChatMessage> messages) {
        if (isInForeground || messages.isEmpty()) return;
        ChatMessage last = messages.get(messages.size() - 1);
        if (myUid.equals(last.senderId)) return;

        // Poruka je od drugog igrača i app nije u fokusu
        AppNotification n = AppNotification.create(
                AppNotification.Channel.CHAT,
                last.senderName + ": " + last.text,
                "Nova poruka u četu regiona",
                "chat");
        notifRepo.add(n);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) listener.remove();
    }
}
